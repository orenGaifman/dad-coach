package com.dadcoach.auth;

import java.util.UUID;

/**
 * Whose link or session this is: a father, a staff user, or both (one person who coaches with Dad Coach and is on
 * the team). At least one is set.
 */
public record SignInSubject(Long fatherId, UUID staffUserId) {

    public SignInSubject {
        if (fatherId == null && staffUserId == null) {
            throw new IllegalArgumentException("a sign-in subject needs a father or a staff user");
        }
    }
}
