package com.dadcoach.config;

import com.dadcoach.workflow.Belt;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Configuration for belt images used in promotion notifications.
 * URLs can be configured via application.yml or environment variables.
 * 
 * <p>Example configuration in application.yml:
 * <pre>
 * belt:
 *   images:
 *     base-url: https://your-cdn.com/belt-images
 *     white: white-belt.png
 *     yellow: yellow-belt.png
 *     orange: orange-belt.png
 *     green: green-belt.png
 *     blue: blue-belt.png
 *     brown: brown-belt.png
 *     black: black-belt.png
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "belt.images")
public class BeltImageConfig {

    /**
     * Base URL for belt images. If set, individual image paths are relative to this.
     */
    private String baseUrl = "";

    /**
     * Map of belt name to image path/URL.
     */
    private String white = "";
    private String yellow = "";
    private String orange = "";
    private String green = "";
    private String blue = "";
    private String brown = "";
    private String black = "";

    /**
     * Gets the full image URL for a belt.
     * 
     * @param belt the belt level
     * @return the full URL to the belt image, or empty string if not configured
     */
    public String getImageUrl(Belt belt) {
        String imagePath = switch (belt) {
            case WHITE -> white;
            case YELLOW -> yellow;
            case ORANGE -> orange;
            case GREEN -> green;
            case BLUE -> blue;
            case BROWN -> brown;
            case BLACK -> black;
        };

        if (imagePath == null || imagePath.isBlank()) {
            return "";
        }

        // If the image path is already a full URL, return it as-is
        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }

        // Otherwise, combine with base URL
        if (baseUrl == null || baseUrl.isBlank()) {
            return imagePath;
        }

        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return base + imagePath;
    }

    /**
     * Checks if an image is configured for the given belt.
     * 
     * @param belt the belt level
     * @return true if an image URL is configured
     */
    public boolean hasImage(Belt belt) {
        String url = getImageUrl(belt);
        return url != null && !url.isBlank();
    }

    // Getters and Setters

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getWhite() {
        return white;
    }

    public void setWhite(String white) {
        this.white = white;
    }

    public String getYellow() {
        return yellow;
    }

    public void setYellow(String yellow) {
        this.yellow = yellow;
    }

    public String getOrange() {
        return orange;
    }

    public void setOrange(String orange) {
        this.orange = orange;
    }

    public String getGreen() {
        return green;
    }

    public void setGreen(String green) {
        this.green = green;
    }

    public String getBlue() {
        return blue;
    }

    public void setBlue(String blue) {
        this.blue = blue;
    }

    public String getBrown() {
        return brown;
    }

    public void setBrown(String brown) {
        this.brown = brown;
    }

    public String getBlack() {
        return black;
    }

    public void setBlack(String black) {
        this.black = black;
    }
}
