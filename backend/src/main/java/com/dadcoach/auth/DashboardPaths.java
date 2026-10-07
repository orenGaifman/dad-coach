package com.dadcoach.auth;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The only in-app paths a login link may land on after sign-in (Tair). Anything else is dropped - the link still
 * works and opens the default page - so an open redirect is impossible.
 */
public final class DashboardPaths {

    private static final Pattern SAFE =
            Pattern.compile("^/(home|sessions|children|progress|settings|training|admin)(/[A-Za-z0-9_\\-/]*)?$");
    private static final int MAX_LENGTH = 200;

    private DashboardPaths() {
    }

    public static Optional<String> sanitize(String next) {
        if (next == null || next.length() > MAX_LENGTH || next.contains("//") || !SAFE.matcher(next).matches()) {
            return Optional.empty();
        }
        return Optional.of(next);
    }
}
