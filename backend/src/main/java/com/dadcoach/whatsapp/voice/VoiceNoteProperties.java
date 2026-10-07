package com.dadcoach.whatsapp.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backs {@code dad-coach.voice-notes.*} (D-027, as Big Boss D-176). Without an ElevenLabs key voice notes stay off,
 * whatever the admin chose.
 */
@ConfigurationProperties(prefix = "dad-coach.voice-notes")
public class VoiceNoteProperties {

    private String elevenlabsApiKey;
    private String elevenlabsBaseUrl = "https://api.elevenlabs.io";
    private String model = "scribe_v2";
    private String languageCode = "heb";
    private long maxBytes = 3L * 1024 * 1024;
    private long timeoutMs = 60_000;

    public boolean isConfigured() {
        return elevenlabsApiKey != null && !elevenlabsApiKey.isBlank();
    }

    public String getElevenlabsApiKey() {
        return elevenlabsApiKey;
    }

    public void setElevenlabsApiKey(String elevenlabsApiKey) {
        this.elevenlabsApiKey = elevenlabsApiKey;
    }

    public String getElevenlabsBaseUrl() {
        return elevenlabsBaseUrl;
    }

    public void setElevenlabsBaseUrl(String elevenlabsBaseUrl) {
        this.elevenlabsBaseUrl = elevenlabsBaseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
