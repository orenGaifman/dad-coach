package com.dadcoach.publicsite;

import java.time.Instant;
import java.util.UUID;

/** One father who left his name and mobile number on the marketing site (table site_signup). */
public record SiteSignup(UUID id, String name, String phone, String source, String page, int submissions,
                         Instant firstSubmittedAt, Instant lastSubmittedAt) {
}
