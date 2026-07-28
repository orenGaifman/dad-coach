package com.dadcoach.channel.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for downloading, storing, and retrieving media assets.
 *
 * On inbound messages with media, this service downloads the file from the provider's
 * media URL (WhatsApp Cloud API), stores it as BYTEA in the database with 90-day retention,
 * and returns the media_reference (UUID) to be included in the InboundMessageDto.
 *
 * If the download fails, the message is still delivered without media — text is never blocked.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    /** Maximum download timeout for media files. */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);

    /** Maximum allowed file sizes per MIME type category (in bytes). */
    static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024;       // 5 MB
    static final int MAX_AUDIO_SIZE = 16 * 1024 * 1024;      // 16 MB
    static final int MAX_VIDEO_SIZE = 16 * 1024 * 1024;      // 16 MB
    static final int MAX_DOCUMENT_SIZE = 100 * 1024 * 1024;  // 100 MB

    private final WebClient webClient;
    private final MediaAssetRepository mediaAssetRepository;

    public MediaService(WebClient.Builder webClientBuilder, MediaAssetRepository mediaAssetRepository) {
        this.webClient = webClientBuilder.build();
        this.mediaAssetRepository = mediaAssetRepository;
    }

    /**
     * Downloads media from the given provider URL, validates size limits,
     * stores it, and returns the media asset ID (media_reference).
     *
     * If download fails or the file exceeds size limits, returns Optional.empty()
     * and the message should be delivered without media.
     *
     * @param mediaUrl    the provider media URL to download from
     * @param mimeType    the MIME type of the media (e.g., "image/jpeg")
     * @param fatherId    the father who sent the message
     * @param messageId   the internal message ID this media belongs to
     * @param accessToken the access token for authenticating with the provider API
     * @return the media_reference UUID if successful, empty if download failed
     */
    public Optional<UUID> downloadAndStore(String mediaUrl, String mimeType, UUID fatherId,
                                           UUID messageId, String accessToken) {
        try {
            byte[] content = downloadMedia(mediaUrl, accessToken);

            if (content == null || content.length == 0) {
                log.warn("Media download returned empty content. url={}, messageId={}", mediaUrl, messageId);
                return Optional.empty();
            }

            int maxSize = getMaxSizeForMimeType(mimeType);
            if (content.length > maxSize) {
                log.warn("Media exceeds size limit. mimeType={}, size={}, maxSize={}, messageId={}",
                        mimeType, content.length, maxSize, messageId);
                return Optional.empty();
            }

            MediaAsset asset = new MediaAsset(fatherId, messageId, mimeType, content);
            MediaAsset saved = mediaAssetRepository.save(asset);

            log.info("Media asset stored. id={}, mimeType={}, size={}, fatherId={}, messageId={}",
                    saved.getId(), mimeType, content.length, fatherId, messageId);

            return Optional.of(saved.getId());

        } catch (Exception e) {
            log.error("Media download failed. url={}, messageId={}, error={}",
                    mediaUrl, messageId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Retrieves a stored media asset by its ID (media_reference).
     *
     * @param mediaId the UUID of the media asset
     * @return the media asset if found
     */
    public Optional<MediaAsset> getMediaAsset(UUID mediaId) {
        return mediaAssetRepository.findById(mediaId);
    }

    /**
     * Downloads raw bytes from the provider media URL using WebClient.
     */
    byte[] downloadMedia(String mediaUrl, String accessToken) {
        return webClient.get()
                .uri(mediaUrl)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(DOWNLOAD_TIMEOUT)
                .onErrorResume(e -> {
                    log.error("WebClient download error. url={}, error={}", mediaUrl, e.getMessage());
                    return Mono.empty();
                })
                .block();
    }

    /**
     * Returns the maximum allowed file size in bytes for a given MIME type.
     */
    int getMaxSizeForMimeType(String mimeType) {
        if (mimeType == null) {
            return MAX_DOCUMENT_SIZE; // fallback to largest limit
        }
        String type = mimeType.toLowerCase();
        if (type.startsWith("image/")) {
            return MAX_IMAGE_SIZE;
        } else if (type.startsWith("audio/")) {
            return MAX_AUDIO_SIZE;
        } else if (type.startsWith("video/")) {
            return MAX_VIDEO_SIZE;
        } else {
            return MAX_DOCUMENT_SIZE;
        }
    }
}
