package com.dadcoach.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Deserialized WhatsApp Cloud API webhook JSON payload.
 * This maps the nested structure from the WhatsApp webhook into
 * a typed Java object hierarchy for safe parsing.
 *
 * WhatsApp webhook structure:
 * object → entry[] → changes[] → value → messages[] | statuses[]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppWebhookPayload(
    String object,
    List<Entry> entry
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
        String id,
        List<Change> changes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(
        Value value,
        String field
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(
        @JsonProperty("messaging_product") String messagingProduct,
        Metadata metadata,
        List<Contact> contacts,
        List<Message> messages,
        List<Status> statuses
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
        @JsonProperty("display_phone_number") String displayPhoneNumber,
        @JsonProperty("phone_number_id") String phoneNumberId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(
        Profile profile,
        @JsonProperty("wa_id") String waId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
        String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
        String from,
        String id,
        String timestamp,
        String type,
        TextBody text,
        MediaBody image,
        MediaBody audio,
        MediaBody video,
        MediaBody document,
        LocationBody location,
        ReactionBody reaction,
        InteractiveReply interactive
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextBody(
        String body
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaBody(
        String id,
        @JsonProperty("mime_type") String mimeType,
        String sha256,
        String caption
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationBody(
        double latitude,
        double longitude,
        String name,
        String address
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReactionBody(
        @JsonProperty("message_id") String messageId,
        String emoji
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InteractiveReply(
        String type,
        @JsonProperty("button_reply") ButtonReply buttonReply,
        @JsonProperty("list_reply") ListReply listReply
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ButtonReply(
        String id,
        String title
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListReply(
        String id,
        String title,
        String description
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(
        String id,
        String status,
        String timestamp,
        @JsonProperty("recipient_id") String recipientId,
        List<Error> errors
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(
        int code,
        String title,
        String message
    ) {}
}
