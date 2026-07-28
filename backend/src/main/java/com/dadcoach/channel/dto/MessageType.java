package com.dadcoach.channel.dto;

/**
 * Content classification of a message.
 * Covers all supported inbound and outbound message types.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT,
    LOCATION,
    REACTION,
    INTERACTIVE
}
