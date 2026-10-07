package com.dadcoach.api.tools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Service-key comparison for the tool, context and profile endpoints: constant time, and never true for a blank key. */
public final class ServiceKeys {

    private ServiceKeys() {
    }

    public static boolean matches(String provided, String configured) {
        if (provided == null || configured == null || configured.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), configured.getBytes(StandardCharsets.UTF_8));
    }
}
