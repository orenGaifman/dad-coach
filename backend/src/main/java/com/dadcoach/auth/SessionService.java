package com.dadcoach.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side browser sessions behind the {@code DADCOACH_SESSION} cookie (Tair / Big Boss D-075 model). The cookie
 * holds an opaque random token; only its hash is stored. A session expires after {@link #IDLE_TTL} without use; use
 * slides the window (at most one write per {@link #RENEWAL_INTERVAL}). Revocation is immediate. Revoking every
 * session of a person also revokes every sign-in link he holds (D-027: links are reusable, so "log out everywhere",
 * deactivation and deletion must end them too).
 */
@Service
public class SessionService {

    public static final Duration IDLE_TTL = Duration.ofDays(60);
    static final Duration RENEWAL_INTERVAL = Duration.ofDays(1);
    private static final Duration REVOKED_RETENTION = Duration.ofDays(30);
    private static final int USER_AGENT_MAX = 400;

    public record Authenticated(DashboardPrincipal principal, boolean renewed) {
    }

    private final DashboardSessionRepository sessions;
    private final LoginLinkRepository links;
    private final SignInPolicy policy;
    private final Clock clock;

    public SessionService(DashboardSessionRepository sessions, LoginLinkRepository links, SignInPolicy policy, Clock clock) {
        this.sessions = sessions;
        this.links = links;
        this.policy = policy;
        this.clock = clock;
    }

    /** Creates a session and returns the raw cookie token (never stored). */
    @Transactional
    public String issueToken(SignInSubject subject, String userAgent) {
        Instant now = clock.instant();
        if (subject.fatherId() != null) {
            sessions.deleteDeadForFather(subject.fatherId(), now, now.minus(REVOKED_RETENTION));
        }
        if (subject.staffUserId() != null) {
            sessions.deleteDeadForStaff(subject.staffUserId(), now, now.minus(REVOKED_RETENTION));
        }
        String raw = TokenHashing.newRawToken();
        sessions.save(new DashboardSession(subject, TokenHashing.sha256Hex(raw), now, IDLE_TTL, truncate(userAgent)));
        return raw;
    }

    @Transactional
    public Optional<Authenticated> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByTokenHash(TokenHashing.sha256Hex(rawToken))
                .filter(s -> s.isUsableAt(now))
                .flatMap(s -> policy.resolve(s.getId(), s.subject()).map(principal -> {
                    boolean renew = s.getLastSeenAt().plus(RENEWAL_INTERVAL).isBefore(now);
                    if (renew) {
                        s.touch(now, IDLE_TTL);
                    }
                    return new Authenticated(principal, renew);
                }));
    }

    @Transactional
    public void revoke(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessions.findByTokenHash(TokenHashing.sha256Hex(rawToken)).ifPresent(s -> s.revoke(clock.instant(), reason));
    }

    /** Every session of everyone this principal is: logout everywhere. */
    @Transactional
    public int revokeAll(DashboardPrincipal principal, String reason) {
        int n = 0;
        if (principal.fatherId() != null) {
            n += revokeAllForFather(principal.fatherId(), reason);
        }
        if (principal.staffUserId() != null) {
            Instant now = clock.instant();
            n += sessions.revokeAllForStaff(principal.staffUserId(), now, reason);
            links.revokeAllForStaff(principal.staffUserId(), now, reason);
        }
        return n;
    }

    /** Every session and every sign-in link of this father. */
    @Transactional
    public int revokeAllForFather(Long fatherId, String reason) {
        Instant now = clock.instant();
        links.revokeAllForFather(fatherId, now, reason);
        return sessions.revokeAllForFather(fatherId, now, reason);
    }

    private static String truncate(String userAgent) {
        return userAgent == null || userAgent.length() <= USER_AGENT_MAX ? userAgent : userAgent.substring(0, USER_AGENT_MAX);
    }
}
