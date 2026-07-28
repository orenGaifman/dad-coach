package com.dadcoach.channel.dto;

/**
 * Priority classification for outbound messages.
 * IMMEDIATE: conversation reply — delivered regardless of quiet hours.
 * SCHEDULED: proactive notification — subject to quiet hours and daily limits.
 */
public enum MessagePriority {
    IMMEDIATE,
    SCHEDULED
}
