package com.dadcoach.whatsapp.voice;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * D-029 (Big Boss D-176): hears a WhatsApp voice note - downloads it from Meta and has ElevenLabs write down its
 * words, which the coach then reads like a typed message. Off when the admin turned it off or no ElevenLabs key is
 * set; then the note is answered as before ("אפשר לכתוב לי במילים?").
 */
@Service
public class VoiceNotes {

    private static final Logger log = LoggerFactory.getLogger(VoiceNotes.class);

    /** What happened to one voice note. */
    public sealed interface Outcome {
        record Heard(String text) implements Outcome {
        }

        /** Voice notes are off: answered as a media message the coach does not read. */
        record Off() implements Outcome {
        }

        record TooLong() implements Outcome {
        }

        /** The note had no words in it. */
        record Silent() implements Outcome {
        }

        record Failed(String code) implements Outcome {
        }
    }

    /** For the admin's integrations screen; never the key, the audio or the words. */
    public record Status(boolean enabled, boolean configured, Instant lastHeardAt, Instant lastFailedAt,
                         String lastSafeErrorSummary) {
    }

    private final VoiceNoteSettings settings;
    private final VoiceNoteProperties properties;
    private final WhatsAppMediaDownloader downloader;
    private final ElevenLabsTranscriber transcriber;

    private volatile Instant lastHeardAt;
    private volatile Instant lastFailedAt;
    private volatile String lastSafeErrorSummary;

    public VoiceNotes(VoiceNoteSettings settings, VoiceNoteProperties properties, WhatsAppMediaDownloader downloader,
                      ElevenLabsTranscriber transcriber) {
        this.settings = settings;
        this.properties = properties;
        this.downloader = downloader;
        this.transcriber = transcriber;
    }

    /** A key is set and the admin has not turned voice notes off. */
    public boolean active() {
        return properties.isConfigured() && settings.enabled();
    }

    public Outcome listen(String mediaId) {
        if (!active()) {
            return new Outcome.Off();
        }
        long started = System.currentTimeMillis();
        try {
            WhatsAppMediaDownloader.Media media = downloader.download(mediaId, properties.getMaxBytes());
            String text = transcriber.transcribe(media.bytes(), media.mimeType());
            lastHeardAt = Instant.now();
            // sizes and timings only, never the words
            log.atInfo().setMessage("voice_note.heard")
                    .addKeyValue("bytes", media.bytes().length)
                    .addKeyValue("mimeType", media.mimeType())
                    .addKeyValue("chars", text.length())
                    .addKeyValue("ms", System.currentTimeMillis() - started)
                    .log();
            return text.isBlank() ? new Outcome.Silent() : new Outcome.Heard(text);
        } catch (VoiceNoteException e) {
            if (e.isTooLong()) {
                log.atInfo().setMessage("voice_note.too_long").addKeyValue("detail", e.getMessage()).log();
                return new Outcome.TooLong();
            }
            failed(e.code(), e.getMessage());
            return new Outcome.Failed(e.code());
        } catch (RuntimeException e) {
            failed("UNEXPECTED", e.getClass().getSimpleName());
            log.atWarn().setMessage("voice_note.unexpected").setCause(e).log();
            return new Outcome.Failed("UNEXPECTED");
        }
    }

    private void failed(String code, String detail) {
        lastFailedAt = Instant.now();
        lastSafeErrorSummary = code;
        log.atWarn().setMessage("voice_note.failed").addKeyValue("code", code).addKeyValue("detail", detail).log();
    }

    public void setEnabled(boolean enabled) {
        settings.setEnabled(enabled);
    }

    public Status status() {
        return new Status(settings.enabled(), properties.isConfigured(), lastHeardAt, lastFailedAt, lastSafeErrorSummary);
    }
}
