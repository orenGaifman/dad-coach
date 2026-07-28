package com.dadcoach.channel.delivery;

/**
 * Delivery status lifecycle for outbound messages.
 * Transitions: PENDING → SENT → DELIVERED → READ
 *              PENDING → FAILED (retries exhausted)
 *              SENT → FAILED (provider reported permanent failure)
 */
public enum DeliveryStatus {
    PENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
