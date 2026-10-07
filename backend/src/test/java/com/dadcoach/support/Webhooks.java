package com.dadcoach.support;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Meta webhook bodies and their X-Hub-Signature-256, as Meta signs them. */
public final class Webhooks {

    private Webhooks() {
    }

    public static String sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String envelope(String valueJson) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"1\",\"changes\":[{\"field\":\"messages\",\"value\":"
                + valueJson + "}]}]}";
    }

    public static String text(String fromE164, String id, String body) {
        return envelope("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"from\":\"" + fromE164.substring(1) + "\",\"id\":\"" + id
                + "\",\"timestamp\":\"1793700000\",\"type\":\"text\",\"text\":{\"body\":\"" + body + "\"}}]}");
    }

    public static String audio(String fromE164, String id) {
        return audio(fromE164, id, "media1");
    }

    /** A WhatsApp voice note, as Meta sends it: the file is fetched by its media id (D-029). */
    public static String audio(String fromE164, String id, String mediaId) {
        return envelope("{\"messages\":[{\"from\":\"" + fromE164.substring(1) + "\",\"id\":\"" + id
                + "\",\"timestamp\":\"1793700000\",\"type\":\"audio\",\"audio\":{\"id\":\"" + mediaId
                + "\",\"mime_type\":\"audio/ogg; codecs=opus\",\"voice\":true}}]}");
    }

    public static String image(String fromE164, String id, String caption) {
        return envelope("{\"messages\":[{\"from\":\"" + fromE164.substring(1) + "\",\"id\":\"" + id
                + "\",\"timestamp\":\"1793700000\",\"type\":\"image\",\"image\":{\"id\":\"media2\",\"caption\":\"" + caption + "\"}}]}");
    }

    public static String reaction(String fromE164, String id, String emoji) {
        return envelope("{\"messages\":[{\"from\":\"" + fromE164.substring(1) + "\",\"id\":\"" + id
                + "\",\"timestamp\":\"1793700000\",\"type\":\"reaction\",\"reaction\":{\"message_id\":\"wamid.out\",\"emoji\":\"" + emoji + "\"}}]}");
    }

    public static String status(String id) {
        return envelope("{\"statuses\":[{\"id\":\"" + id + "\",\"status\":\"delivered\",\"timestamp\":\"1793700000\","
                + "\"recipient_id\":\"19995550100\"}]}");
    }
}
