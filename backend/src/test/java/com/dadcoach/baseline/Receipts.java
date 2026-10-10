package com.dadcoach.baseline;

import com.dadcoach.support.Webhooks;

/**
 * Meta delivery status receipts ({@code value.statuses[]}) as Meta posts them to {@code /webhook/whatsapp}, for any
 * status ({@code sent}, {@code delivered}, {@code read}, {@code failed}) - {@link Webhooks#status} only builds a
 * "delivered" one. A "failed" receipt carries Meta's error (e.g. 131047: more than 24 hours since the person last
 * wrote, so only a template may be sent).
 */
final class Receipts {

    private Receipts() {
    }

    static String of(String wamid, String status, String recipientE164) {
        return Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"15550000000\","
                + "\"phone_number_id\":\"100000000000001\"},\"statuses\":[{\"id\":\"" + wamid + "\",\"status\":\"" + status
                + "\",\"timestamp\":\"1793700100\",\"recipient_id\":\"" + recipientE164.substring(1) + "\"}]}");
    }

    static String failed(String wamid, String recipientE164, int code, String title) {
        return Webhooks.envelope("{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"15550000000\","
                + "\"phone_number_id\":\"100000000000001\"},\"statuses\":[{\"id\":\"" + wamid
                + "\",\"status\":\"failed\",\"timestamp\":\"1793700100\",\"recipient_id\":\"" + recipientE164.substring(1)
                + "\",\"errors\":[{\"code\":" + code + ",\"title\":\"" + title + "\",\"message\":\"" + title
                + "\",\"error_data\":{\"details\":\"" + title + "\"}}]}]}");
    }

    /** Meta's answer to an accepted send, with the wamid the test chooses. */
    static String accepted(String wamid) {
        return "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\""
                + wamid + "\"}]}";
    }
}
