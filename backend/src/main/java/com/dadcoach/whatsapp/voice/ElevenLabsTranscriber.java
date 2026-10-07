package com.dadcoach.whatsapp.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * D-027 (Big Boss D-176): ElevenLabs speech to text ({@code POST /v1/speech-to-text}, Scribe). The audio goes to
 * ElevenLabs and only the words come back; Dad Coach keeps neither the audio nor a copy of it.
 */
@Component
public class ElevenLabsTranscriber {

    private final WebClient webClient;
    private final VoiceNoteProperties properties;
    private final ObjectMapper objectMapper;

    public ElevenLabsTranscriber(@Qualifier("elevenLabsWebClient") WebClient elevenLabsWebClient, VoiceNoteProperties properties,
                                 ObjectMapper objectMapper) {
        this.webClient = elevenLabsWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** The words of the note, stripped - empty when there were none. */
    public String transcribe(byte[] audio, String mimeType) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("model_id", properties.getModel());
        if (properties.getLanguageCode() != null && !properties.getLanguageCode().isBlank()) {
            body.part("language_code", properties.getLanguageCode());
        }
        // Words only: no "(צחוק)" sound tags, no per-word timings.
        body.part("tag_audio_events", "false");
        body.part("timestamps_granularity", "none");
        body.part("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "voice-note" + extension(mimeType);
            }
        }).contentType(mediaType(mimeType));
        try {
            JsonNode response = webClient.post()
                    .uri("/v1/speech-to-text")
                    .header("xi-api-key", properties.getElevenlabsApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body.build()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofMillis(properties.getTimeoutMs() + 5_000));
            JsonNode text = response == null ? null : response.get("text");
            return text == null || text.isNull() ? "" : text.asText().strip();
        } catch (WebClientResponseException e) {
            throw new VoiceNoteException(errorCode(e), "ElevenLabs answered " + e.getStatusCode().value());
        } catch (WebClientRequestException e) {
            throw new VoiceNoteException("ELEVENLABS_UNREACHABLE", "ElevenLabs could not be reached");
        } catch (IllegalStateException e) {
            // block() timing out
            throw new VoiceNoteException("ELEVENLABS_TIMEOUT", "ElevenLabs did not answer in time");
        }
    }

    /** ElevenLabs names the cause in {@code detail.status} (e.g. quota_exceeded); else the HTTP status. */
    private String errorCode(WebClientResponseException e) {
        try {
            JsonNode status = objectMapper.readTree(e.getResponseBodyAsString()).path("detail").path("status");
            if (status.isTextual() && status.asText().matches("[a-z_]{1,60}")) {
                return "ELEVENLABS_" + status.asText().toUpperCase();
            }
        } catch (Exception ignored) {
            // not JSON - the HTTP status says enough
        }
        return "ELEVENLABS_HTTP_" + e.getStatusCode().value();
    }

    private static MediaType mediaType(String mimeType) {
        try {
            return mimeType == null || mimeType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mimeType);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String extension(String mimeType) {
        String type = mimeType == null ? "" : mimeType.toLowerCase();
        if (type.contains("ogg") || type.contains("opus")) return ".ogg";
        if (type.contains("mpeg") || type.contains("mp3")) return ".mp3";
        if (type.contains("mp4") || type.contains("m4a") || type.contains("aac")) return ".m4a";
        if (type.contains("amr")) return ".amr";
        if (type.contains("wav")) return ".wav";
        return "";
    }
}
