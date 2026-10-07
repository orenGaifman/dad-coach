package com.dadcoach.whatsapp;

/**
 * The routing contract on the shared WhatsApp number (owner, 2026-10-07; platform docs/whatsapp-gateway.md): every
 * Dad Coach button id - interactive reply buttons, list rows, template quick-reply payloads - starts with
 * {@value #PREFIX}. The platform gateway (ROUTES_1_BUTTONPREFIXES=dc:) routes a tap on such a button back to Dad Coach,
 * also after the father switched to another product; an id without it could be routed elsewhere. Permanent.
 */
public final class ButtonIds {

    public static final String PREFIX = "dc:";

    private ButtonIds() {
    }

    /** @return the id, if it carries the prefix and something after it; otherwise an IllegalArgumentException */
    public static String require(String id, String what) {
        if (id == null || !id.startsWith(PREFIX) || id.length() == PREFIX.length()) {
            throw new IllegalArgumentException(what + " id must start with \"" + PREFIX + "\" (shared-number routing): " + id);
        }
        return id;
    }
}
