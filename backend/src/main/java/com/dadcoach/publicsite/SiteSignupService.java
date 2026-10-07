package com.dadcoach.publicsite;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Signups from the marketing site's form. Stores a valid one (one row per phone, a repeat updates it) and
 * silently drops the rest: the endpoint answers the same way either way, so a bot learns nothing.
 * Nothing here sends a message or touches the AI; the team follows up from the admin.
 */
@Service
public class SiteSignupService {

    private static final Logger log = LoggerFactory.getLogger(SiteSignupService.class);
    static final int MAX_DAYS = 365;

    /** Why a submission was not stored (logged, never shown to the visitor). */
    public enum Outcome { STORED, HONEYPOT, RATE_LIMITED, INVALID }

    private final JdbcClient jdbc;
    private final SiteSignupRateLimiter limiter;
    private final Clock clock;

    public SiteSignupService(JdbcClient jdbc, SiteSignupRateLimiter limiter, Clock clock) {
        this.jdbc = jdbc;
        this.limiter = limiter;
        this.clock = clock;
    }

    public Outcome submit(String name, String phone, String source, String page, String honeypot, String client) {
        if (honeypot != null && !honeypot.isBlank()) {
            log.info("site signup dropped: honeypot");
            return Outcome.HONEYPOT;
        }
        if (!limiter.tryAcquire(client == null ? "unknown" : client)) {
            log.info("site signup dropped: rate limited");
            return Outcome.RATE_LIMITED;
        }
        String cleanName = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        Optional<String> e164 = IsraeliMobile.toE164(phone);
        if (cleanName.length() < 2 || cleanName.length() > 60 || e164.isEmpty()) {
            log.info("site signup dropped: invalid name or phone");
            return Outcome.INVALID;
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.sql("""
                INSERT INTO site_signup (phone, name, source, page, first_submitted_at, last_submitted_at)
                VALUES (:phone, :name, :source, :page, :now, :now)
                ON CONFLICT (phone) DO UPDATE SET name = EXCLUDED.name, source = EXCLUDED.source, page = EXCLUDED.page,
                    submissions = site_signup.submissions + 1, last_submitted_at = EXCLUDED.last_submitted_at
                """)
                .param("phone", e164.get())
                .param("name", cleanName)
                .param("source", trimTo(source == null || source.isBlank() ? "site" : source.strip(), 20))
                .param("page", page == null ? null : trimTo(page.strip(), 200))
                .param("now", now)
                .update();
        log.info("site signup stored");
        return Outcome.STORED;
    }

    /** Signups whose latest submission is within the last {@code days} days (1-365), newest first. For the admin. */
    public List<SiteSignup> recentSignups(int days) {
        int window = Math.max(1, Math.min(MAX_DAYS, days));
        Instant since = clock.instant().minus(Duration.ofDays(window));
        return jdbc.sql("""
                SELECT id, name, phone, source, page, submissions, first_submitted_at, last_submitted_at
                FROM site_signup WHERE last_submitted_at >= :since ORDER BY last_submitted_at DESC
                """)
                .param("since", Timestamp.from(since))
                .query((rs, n) -> new SiteSignup(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("phone"),
                        rs.getString("source"), rs.getString("page"), rs.getInt("submissions"),
                        rs.getTimestamp("first_submitted_at").toInstant(), rs.getTimestamp("last_submitted_at").toInstant()))
                .list();
    }

    /** Removes a father's signup row by phone (E.164), e.g. on his data-deletion request. @return rows removed */
    public int deleteByPhone(String e164Phone) {
        return jdbc.sql("DELETE FROM site_signup WHERE phone = :phone").param("phone", e164Phone).update();
    }

    private static String trimTo(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
