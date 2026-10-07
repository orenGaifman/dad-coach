package com.dadcoach.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code dadcoach.dashboard.*} (resources/dashboard.properties) - every value comes from the environment. */
@ConfigurationProperties(prefix = "dadcoach.dashboard")
public class DashboardProperties {

    private String opsApiKey = "";
    private boolean cookieSecure = true;
    private String whatsappPublicNumber = "";
    private String platformAdminApiKey = "";
    private final Training training = new Training();

    public static class Training {
        private String mediaBaseUrl = "";
        private String mediaTokenKey = "";
        private boolean mediaUnsigned = false;

        public String getMediaBaseUrl() { return mediaBaseUrl; }
        public void setMediaBaseUrl(String mediaBaseUrl) { this.mediaBaseUrl = mediaBaseUrl; }
        public String getMediaTokenKey() { return mediaTokenKey; }
        public void setMediaTokenKey(String mediaTokenKey) { this.mediaTokenKey = mediaTokenKey; }
        public boolean isMediaUnsigned() { return mediaUnsigned; }
        public void setMediaUnsigned(boolean mediaUnsigned) { this.mediaUnsigned = mediaUnsigned; }
    }

    public String getOpsApiKey() { return opsApiKey; }
    public void setOpsApiKey(String opsApiKey) { this.opsApiKey = opsApiKey; }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
    public String getWhatsappPublicNumber() { return whatsappPublicNumber; }
    public void setWhatsappPublicNumber(String whatsappPublicNumber) { this.whatsappPublicNumber = whatsappPublicNumber; }
    public String getPlatformAdminApiKey() { return platformAdminApiKey; }
    public void setPlatformAdminApiKey(String platformAdminApiKey) { this.platformAdminApiKey = platformAdminApiKey; }
    public Training getTraining() { return training; }
}
