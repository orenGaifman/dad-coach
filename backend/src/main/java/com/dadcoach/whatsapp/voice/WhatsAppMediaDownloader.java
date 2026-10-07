package com.dadcoach.whatsapp.voice;

import com.dadcoach.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * D-029 (Big Boss D-176): fetches a voice note from Meta - {@code GET <api-base-url>/<version>/<media-id>} for its
 * address, type and size, then the file itself, both with Dad Coach's WhatsApp access token. The base URL is the
 * configured Graph base ({@code WHATSAPP_API_BASE_URL}), so the QA lab's fake Meta can serve media. A note over the
 * limit is not downloaded.
 */
@Component
public class WhatsAppMediaDownloader {

    public record Media(byte[] bytes, String mimeType) {
    }

    private static final Duration WAIT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final String apiVersion;

    @Autowired
    public WhatsAppMediaDownloader(WebClient.Builder webClientBuilder, WhatsAppProperties properties) {
        this(webClientBuilder.baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                .build(), properties.apiVersion());
    }

    WhatsAppMediaDownloader(WebClient metaClient, String apiVersion) {
        // The file is bytes in memory: room for a note at the limit (a bigger one is refused before download).
        this.webClient = metaClient.mutate()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.apiVersion = apiVersion;
    }

    public Media download(String mediaId, long maxBytes) {
        try {
            JsonNode info = webClient.get().uri("/{version}/{mediaId}", apiVersion, mediaId)
                    .retrieve().bodyToMono(JsonNode.class).block(WAIT);
            String url = info == null ? null : info.path("url").asText(null);
            if (url == null || url.isBlank()) {
                throw new VoiceNoteException("META_MEDIA_NO_URL", "Meta returned no address for the media");
            }
            long size = info.path("file_size").asLong(0);
            if (size > maxBytes) {
                throw VoiceNoteException.tooLong(size);
            }
            byte[] bytes = webClient.get().uri(URI.create(url)).retrieve().bodyToMono(byte[].class).block(WAIT);
            if (bytes == null || bytes.length == 0) {
                throw new VoiceNoteException("META_MEDIA_EMPTY", "Meta returned an empty file");
            }
            if (bytes.length > maxBytes) {
                throw VoiceNoteException.tooLong(bytes.length);
            }
            return new Media(bytes, info.path("mime_type").asText("audio/ogg"));
        } catch (WebClientResponseException e) {
            throw new VoiceNoteException("META_MEDIA_HTTP_" + e.getStatusCode().value(), "Meta answered " + e.getStatusCode().value());
        } catch (WebClientRequestException e) {
            throw new VoiceNoteException("META_MEDIA_UNREACHABLE", "Meta could not be reached");
        } catch (IllegalStateException e) {
            throw new VoiceNoteException("META_MEDIA_TIMEOUT", "Meta did not send the media in time");
        }
    }
}
