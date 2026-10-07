package com.dadcoach.auth;

import java.util.UUID;

/**
 * Who is signed in, re-resolved on every request (a father deactivated or deleted, or a staff user switched off,
 * loses that capability at once). {@code fatherId} set = the father area; {@code staffUserId} set = the admin area.
 */
public record DashboardPrincipal(UUID sessionId, Long fatherId, UUID staffUserId, String displayName) {

    public boolean isFather() {
        return fatherId != null;
    }

    public boolean isAdmin() {
        return staffUserId != null;
    }
}
