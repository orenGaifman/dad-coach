package com.dadcoach.whatsapp.dto;

import java.time.Instant;

/**
 * Represents a delivery status update extracted from a WhatsApp webhook.
 * Status updates are separate from inbound messages — they track the lifecycle
 * of previously sent outbound messages (sent, delivered, read, failed).
 *
 * @param providerMessageId the WhatsApp message ID (wamid.xxx) this status refers to
 * @param status            delivery status: sent, delivered, read, or failed
 * @param recipientId       the recipient's phone number
 * @param timestamp         when the status event occurred
 * @param errorCode         error code if status is "failed" (null otherwise)
 * @param errorMessage      error message if status is "failed" (null otherwise)
 */
public record StatusUpdateDto(
    String providerMessageId,
    String status,
    String recipientId,
    Instant timestamp,
    Integer errorCode,
    String errorMessage
) {}
