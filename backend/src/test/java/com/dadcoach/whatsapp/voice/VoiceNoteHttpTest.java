package com.dadcoach.whatsapp.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/** D-029: the requests to Meta (media) and ElevenLabs (speech to text), against a local HTTP server. */
class VoiceNoteHttpTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String start(String path, HttpHandler handler) throws IOException {
        if (server == null) {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
        }
        server.createContext(path, handler);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, byte[] body, String type) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static WhatsAppMediaDownloader downloader(String base) {
        return new WhatsAppMediaDownloader(WebClient.builder().baseUrl(base).defaultHeader("Authorization", "Bearer wa-token").build(),
                "v25.0");
    }

    @Test
    void theMediaIsLookedUpUnderTheGraphVersionThenDownloadedWithTheWhatsAppToken() throws Exception {
        AtomicReference<String> lookupAuth = new AtomicReference<>();
        AtomicReference<String> fileAuth = new AtomicReference<>();
        String base = start("/v25.0/media-1", exchange -> {
            lookupAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, ("{\"url\":\"http://127.0.0.1:" + exchange.getLocalAddress().getPort()
                    + "/files/v1\",\"mime_type\":\"audio/ogg; codecs=opus\",\"file_size\":3}").getBytes(StandardCharsets.UTF_8),
                    "application/json");
        });
        start("/files/v1", exchange -> {
            fileAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, new byte[] {7, 8, 9}, "audio/ogg");
        });

        WhatsAppMediaDownloader.Media media = downloader(base).download("media-1", 100);

        assertThat(media.bytes()).containsExactly(7, 8, 9);
        assertThat(media.mimeType()).isEqualTo("audio/ogg; codecs=opus");
        assertThat(lookupAuth.get()).isEqualTo("Bearer wa-token");
        assertThat(fileAuth.get()).isEqualTo("Bearer wa-token");
    }

    @Test
    void aNoteOverTheLimitIsNotDownloaded() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        String base = start("/v25.0/media-2", exchange -> respond(exchange, 200, ("{\"url\":\"http://127.0.0.1:"
                + exchange.getLocalAddress().getPort() + "/files/v2\",\"mime_type\":\"audio/ogg\",\"file_size\":5000}")
                .getBytes(StandardCharsets.UTF_8), "application/json"));
        start("/files/v2", exchange -> {
            downloads.incrementAndGet();
            respond(exchange, 200, new byte[5000], "audio/ogg");
        });

        assertThatThrownBy(() -> downloader(base).download("media-2", 100))
                .isInstanceOfSatisfying(VoiceNoteException.class, e -> assertThat(e.isTooLong()).isTrue());
        assertThat(downloads.get()).isZero();
    }

    @Test
    void metaRefusingTheMediaIsASafeCode() throws Exception {
        String base = start("/v25.0/media-3", exchange -> respond(exchange, 404, "{}".getBytes(StandardCharsets.UTF_8), "application/json"));

        assertThatThrownBy(() -> downloader(base).download("media-3", 100))
                .isInstanceOfSatisfying(VoiceNoteException.class, e -> assertThat(e.code()).isEqualTo("META_MEDIA_HTTP_404"));
    }

    @Test
    void metaUnreachableIsASafeCode() {
        assertThatThrownBy(() -> downloader("http://127.0.0.1:1").download("media-4", 100))
                .isInstanceOfSatisfying(VoiceNoteException.class, e -> assertThat(e.code()).isEqualTo("META_MEDIA_UNREACHABLE"));
    }

    private static ElevenLabsTranscriber transcriber(String base) {
        VoiceNoteProperties properties = new VoiceNoteProperties();
        properties.setElevenlabsApiKey("el-key");
        properties.setElevenlabsBaseUrl(base);
        properties.setTimeoutMs(5_000);
        return new ElevenLabsTranscriber(new VoiceNoteConfig().elevenLabsWebClient(properties), properties, new ObjectMapper());
    }

    @Test
    void theNoteIsSentAsAHebrewScribeRequestAndItsWordsAreReturned() throws Exception {
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        String base = start("/v1/speech-to-text", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("xi-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            respond(exchange, 200, "{\"language_code\":\"heb\",\"text\":\" רוצה לקבוע זמן עם נועה \"}".getBytes(StandardCharsets.UTF_8),
                    "application/json");
        });

        String text = transcriber(base).transcribe(new byte[] {1, 2, 3}, "audio/ogg; codecs=opus");

        assertThat(text).isEqualTo("רוצה לקבוע זמן עם נועה");
        assertThat(key.get()).isEqualTo("el-key");
        assertThat(body.get())
                .contains("name=\"model_id\"").contains("scribe_v2")
                .contains("name=\"language_code\"").contains("heb")
                .contains("name=\"tag_audio_events\"")
                .contains("name=\"timestamps_granularity\"")
                .contains("filename=\"voice-note.ogg\"");
    }

    @Test
    void elevenLabsOutOfCreditIsNamedForTheAdmin() throws Exception {
        String base = start("/v1/speech-to-text", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 401, "{\"detail\":{\"status\":\"quota_exceeded\",\"message\":\"...\"}}".getBytes(StandardCharsets.UTF_8),
                    "application/json");
        });

        assertThatThrownBy(() -> transcriber(base).transcribe(new byte[] {1}, "audio/ogg"))
                .isInstanceOfSatisfying(VoiceNoteException.class, e -> assertThat(e.code()).isEqualTo("ELEVENLABS_QUOTA_EXCEEDED"));
    }

    @Test
    void aKeyIdSetInsteadOfTheKeyIsNamedForTheAdmin() throws Exception {
        String base = start("/v1/speech-to-text", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 401, "{\"detail\":{\"status\":\"api_key_id_used_as_api_key\"}}".getBytes(StandardCharsets.UTF_8),
                    "application/json");
        });

        assertThatThrownBy(() -> transcriber(base).transcribe(new byte[] {1}, "audio/ogg"))
                .isInstanceOfSatisfying(VoiceNoteException.class,
                        e -> assertThat(e.code()).isEqualTo("ELEVENLABS_API_KEY_ID_USED_AS_API_KEY"));
    }

    @Test
    void aServerErrorWithoutJsonIsItsHttpStatus() throws Exception {
        String base = start("/v1/speech-to-text", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 503, "busy".getBytes(StandardCharsets.UTF_8), "text/plain");
        });

        assertThatThrownBy(() -> transcriber(base).transcribe(new byte[] {1}, "audio/ogg"))
                .isInstanceOfSatisfying(VoiceNoteException.class, e -> assertThat(e.code()).isEqualTo("ELEVENLABS_HTTP_503"));
    }
}
