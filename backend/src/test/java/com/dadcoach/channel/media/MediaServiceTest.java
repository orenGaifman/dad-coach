package com.dadcoach.channel.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MediaService covering download, storage, size validation,
 * and graceful failure handling.
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    private MediaService mediaService;

    private final UUID fatherId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final String accessToken = "test-token";

    @BeforeEach
    void setUp() {
        when(webClientBuilder.build()).thenReturn(webClient);
        mediaService = spy(new MediaService(webClientBuilder, mediaAssetRepository));
    }

    @Nested
    @DisplayName("Successful download and storage")
    class SuccessfulDownloadTests {

        @Test
        @DisplayName("downloads image and stores with correct metadata")
        void downloadAndStore_image_success() {
            byte[] imageBytes = new byte[1024]; // 1KB image
            String mimeType = "image/jpeg";
            String mediaUrl = "https://graph.facebook.com/v18.0/media/123";

            doReturn(imageBytes).when(mediaService).downloadMedia(mediaUrl, accessToken);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, mimeType, fatherId, messageId, accessToken);

            // Result may be empty if the entity ID is null (auto-generated in DB).
            // What matters is that save was called with correct data.
            ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
            verify(mediaAssetRepository).save(captor.capture());
            MediaAsset captured = captor.getValue();
            assertEquals(fatherId, captured.getFatherId());
            assertEquals(messageId, captured.getMessageId());
            assertEquals(mimeType, captured.getMimeType());
            assertEquals(1024, captured.getFileSize());
            assertNotNull(captured.getDownloadedAt());
            assertNotNull(captured.getExpiresAt());
        }

        @Test
        @DisplayName("stores audio file with correct MIME type tracking")
        void downloadAndStore_audio_success() {
            byte[] audioBytes = new byte[2048];
            String mimeType = "audio/ogg";
            String mediaUrl = "https://graph.facebook.com/v18.0/media/456";

            doReturn(audioBytes).when(mediaService).downloadMedia(mediaUrl, accessToken);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, mimeType, fatherId, messageId, accessToken);

            ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
            verify(mediaAssetRepository).save(captor.capture());
            assertEquals("audio/ogg", captor.getValue().getMimeType());
            assertEquals(2048, captor.getValue().getFileSize());
        }
    }

    @Nested
    @DisplayName("Size limit enforcement")
    class SizeLimitTests {

        @Test
        @DisplayName("rejects image exceeding 5MB limit")
        void downloadAndStore_imageOverLimit_returnsEmpty() {
            byte[] oversizedImage = new byte[6 * 1024 * 1024]; // 6MB
            String mediaUrl = "https://graph.facebook.com/v18.0/media/big-image";

            doReturn(oversizedImage).when(mediaService).downloadMedia(mediaUrl, accessToken);

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, "image/png", fatherId, messageId, accessToken);

            assertTrue(result.isEmpty());
            verify(mediaAssetRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects audio exceeding 16MB limit")
        void downloadAndStore_audioOverLimit_returnsEmpty() {
            byte[] oversizedAudio = new byte[17 * 1024 * 1024]; // 17MB
            String mediaUrl = "https://graph.facebook.com/v18.0/media/big-audio";

            doReturn(oversizedAudio).when(mediaService).downloadMedia(mediaUrl, accessToken);

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, "audio/mpeg", fatherId, messageId, accessToken);

            assertTrue(result.isEmpty());
            verify(mediaAssetRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects video exceeding 16MB limit")
        void downloadAndStore_videoOverLimit_returnsEmpty() {
            byte[] oversizedVideo = new byte[17 * 1024 * 1024]; // 17MB
            String mediaUrl = "https://graph.facebook.com/v18.0/media/big-video";

            doReturn(oversizedVideo).when(mediaService).downloadMedia(mediaUrl, accessToken);

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, "video/mp4", fatherId, messageId, accessToken);

            assertTrue(result.isEmpty());
            verify(mediaAssetRepository, never()).save(any());
        }

        @Test
        @DisplayName("accepts document under 100MB limit")
        void downloadAndStore_documentUnderLimit_succeeds() {
            byte[] document = new byte[50 * 1024 * 1024]; // 50MB
            String mediaUrl = "https://graph.facebook.com/v18.0/media/doc";

            doReturn(document).when(mediaService).downloadMedia(mediaUrl, accessToken);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, "application/pdf", fatherId, messageId, accessToken);

            assertTrue(result.isPresent());
            verify(mediaAssetRepository).save(any());
        }
    }

    @Nested
    @DisplayName("Download failure handling — message delivered without media")
    class DownloadFailureTests {

        @Test
        @DisplayName("returns empty when download returns null")
        void downloadAndStore_downloadReturnsNull_returnsEmpty() {
            String mediaUrl = "https://graph.facebook.com/v18.0/media/expired";

            doReturn(null).when(mediaService).downloadMedia(mediaUrl, accessToken);

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, "image/jpeg", fatherId, messageId, accessToken);

            assertTrue(result.isEmpty());
            verify(mediaAssetRepository, never()).save(any());
        }

        @Test
        @DisplayName("returns empty when download returns empty byte array")
        void downloadAndStore_downloadReturnsEmpty_returnsEmpty() {
            String mediaUrl = "https://graph.facebook.com/v18.0/media/empty";

            doReturn(new byte[0]).when(mediaService).downloadMedia(mediaUrl, accessToken);

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, "image/jpeg", fatherId, messageId, accessToken);

            assertTrue(result.isEmpty());
            verify(mediaAssetRepository, never()).save(any());
        }

        @Test
        @DisplayName("returns empty when download throws exception")
        void downloadAndStore_downloadThrows_returnsEmpty() {
            String mediaUrl = "https://graph.facebook.com/v18.0/media/timeout";

            doThrow(new RuntimeException("Connection timeout")).when(mediaService).downloadMedia(mediaUrl, accessToken);

            Optional<UUID> result = mediaService.downloadAndStore(mediaUrl, "image/jpeg", fatherId, messageId, accessToken);

            assertTrue(result.isEmpty());
            verify(mediaAssetRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("MIME type size limit resolution")
    class MimeTypeLimitTests {

        @Test
        @DisplayName("image/* maps to 5MB limit")
        void getMaxSize_image() {
            assertEquals(MediaService.MAX_IMAGE_SIZE, mediaService.getMaxSizeForMimeType("image/jpeg"));
            assertEquals(MediaService.MAX_IMAGE_SIZE, mediaService.getMaxSizeForMimeType("image/png"));
        }

        @Test
        @DisplayName("audio/* maps to 16MB limit")
        void getMaxSize_audio() {
            assertEquals(MediaService.MAX_AUDIO_SIZE, mediaService.getMaxSizeForMimeType("audio/ogg"));
            assertEquals(MediaService.MAX_AUDIO_SIZE, mediaService.getMaxSizeForMimeType("audio/mpeg"));
        }

        @Test
        @DisplayName("video/* maps to 16MB limit")
        void getMaxSize_video() {
            assertEquals(MediaService.MAX_VIDEO_SIZE, mediaService.getMaxSizeForMimeType("video/mp4"));
        }

        @Test
        @DisplayName("application/* and unknown types map to 100MB limit")
        void getMaxSize_document() {
            assertEquals(MediaService.MAX_DOCUMENT_SIZE, mediaService.getMaxSizeForMimeType("application/pdf"));
            assertEquals(MediaService.MAX_DOCUMENT_SIZE, mediaService.getMaxSizeForMimeType("text/plain"));
        }

        @Test
        @DisplayName("null MIME type defaults to document limit")
        void getMaxSize_null() {
            assertEquals(MediaService.MAX_DOCUMENT_SIZE, mediaService.getMaxSizeForMimeType(null));
        }
    }

    @Nested
    @DisplayName("Media retrieval")
    class RetrievalTests {

        @Test
        @DisplayName("retrieves existing media asset by ID")
        void getMediaAsset_found() {
            UUID mediaId = UUID.randomUUID();
            MediaAsset asset = new MediaAsset(fatherId, messageId, "image/jpeg", new byte[100]);
            when(mediaAssetRepository.findById(mediaId)).thenReturn(Optional.of(asset));

            Optional<MediaAsset> result = mediaService.getMediaAsset(mediaId);

            assertTrue(result.isPresent());
            assertEquals("image/jpeg", result.get().getMimeType());
        }

        @Test
        @DisplayName("returns empty for non-existent media ID")
        void getMediaAsset_notFound() {
            UUID mediaId = UUID.randomUUID();
            when(mediaAssetRepository.findById(mediaId)).thenReturn(Optional.empty());

            Optional<MediaAsset> result = mediaService.getMediaAsset(mediaId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("90-day retention — expires_at set at download time")
    class RetentionTests {

        @Test
        @DisplayName("MediaAsset sets expires_at to downloadedAt + 90 days")
        void mediaAsset_setsExpiresAt() {
            MediaAsset asset = new MediaAsset(fatherId, messageId, "image/jpeg", new byte[100]);

            assertNotNull(asset.getDownloadedAt());
            assertNotNull(asset.getExpiresAt());

            long daysBetween = java.time.Duration.between(asset.getDownloadedAt(), asset.getExpiresAt()).toDays();
            assertEquals(90, daysBetween);
        }

        @Test
        @DisplayName("MediaAsset tracks file size from content length")
        void mediaAsset_tracksFileSize() {
            byte[] content = new byte[4096];
            MediaAsset asset = new MediaAsset(fatherId, messageId, "audio/ogg", content);

            assertEquals(4096, asset.getFileSize());
        }
    }
}
