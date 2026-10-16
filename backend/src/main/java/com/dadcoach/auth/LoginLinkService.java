package com.dadcoach.auth;

import com.dadcoach.channel.delivery.DeliveryResult;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in links (D-005, reusable since D-027). A link is a button on WhatsApp that always works: valid for
 * {@link #LINK_TTL}, not spent on use, ended only by expiry or revocation ("log out everywhere", deactivation,
 * deletion - {@link SessionService}). Every use re-checks that its person may still sign in and opens a normal
 * dashboard session. Only the token's hash is stored, and the link carries the token in the URL fragment, so it never
 * reaches a server log.
 *
 * <p>{@link #requestLink} (the login page) never reveals whether a phone is registered: unknown, invalid,
 * deactivated, deleted and rate-limited all end the same silent way. {@link #sendTo} is the coach's
 * {@code dad_dashboard_link} tool: the father is known, so it says what happened.
 */
@Service
public class LoginLinkService {

    private static final Logger log = LoggerFactory.getLogger(LoginLinkService.class);
    public static final Duration LINK_TTL = Duration.ofDays(365);
    /**
     * D-067: the morning's dashboard button is for that day and week; an older one has expired and the sign-in page
     * takes over.
     */
    public static final Duration MORNING_LINK_TTL = Duration.ofDays(7);
    /** Where a link's token goes: in the fragment after this path (a template's URL button ends with it, D-067). */
    public static final String CONSUME_PATH = "/auth/consume#token=";

    public record IssuedLink(String url, Instant expiresAt) {
    }

    /** D-067: a morning's link - its row and the raw token, which only the message that carries it ever holds. */
    public record MorningLink(UUID id, String token) {
    }

    /** What {@link #sendTo} did: SENT, ALREADY_SENT (his button from minutes ago is right above and keeps working),
     *  RATE_LIMITED, NOT_ALLOWED, FAILED. */
    public enum SendOutcome { SENT, ALREADY_SENT, RATE_LIMITED, NOT_ALLOWED, FAILED }

    /** A button sent this recently is still on his screen: the coach never stacks a second card under it (owner,
     *  2026-10-08: "מה זה הכפתור הזה?" got the same card again). */
    public static final Duration ON_SCREEN = Duration.ofMinutes(10);

    private final LoginLinkRepository links;
    private final SignInPolicy policy;
    private final LoginLinkPlatform platform;
    private final LoginLinkRateLimiter rateLimiter;
    private final Clock clock;
    private final String webBaseUrl;

    public LoginLinkService(LoginLinkRepository links, SignInPolicy policy, LoginLinkPlatform platform,
                            LoginLinkRateLimiter rateLimiter, Clock clock,
                            @Value("${dad-coach.web.base-url:http://localhost:3000}") String webBaseUrl) {
        this.links = links;
        this.policy = policy;
        this.platform = platform;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
    }

    /** "Send me a link": silent whatever happens (the caller always answers the same). */
    @Transactional
    public void requestLink(String rawPhone, String next, String clientAddress) {
        if (!rateLimiter.tryClient(clientAddress)) {
            log.info("auth.login_link.rate_limited scope=client");
            return;
        }
        Optional<String> phone = PhoneInput.normalize(rawPhone);
        if (phone.isEmpty()) {
            return;
        }
        Optional<SignInSubject> subject = policy.forPhone(phone.get());
        if (subject.isEmpty()) {
            return;
        }
        if (!rateLimiter.trySubject(subject.get())) {
            log.info("auth.login_link.rate_limited scope=person");
            return;
        }
        DeliveryResult result = issueAndSend(subject.get(), phone.get(), next, null);
        log.info("auth.login_link.requested delivered={} reason={}", result.isSuccessful(),
                result.isSuccessful() ? "" : result.failureReason());
    }

    /**
     * The coach's dashboard button (D-027): a link for the person who owns this WhatsApp number - father, team member
     * or both - sent to him as a button. The token never leaves Dad Coach except inside that WhatsApp message.
     */
    @Transactional
    public SendOutcome sendTo(String e164) {
        return sendTo(e164, null);
    }

    /** @param turnCorrelationId D-039: the turn this card is part of (the inbound handler's), or null */
    @Transactional
    public SendOutcome sendTo(String e164, String turnCorrelationId) {
        Optional<SignInSubject> subject = policy.forPhone(e164);
        if (subject.isEmpty()) {
            return SendOutcome.NOT_ALLOWED;
        }
        if (subject.get().fatherId() != null
                && links.sentToFatherSince(subject.get().fatherId(), clock.instant().minus(ON_SCREEN))) {
            log.info("auth.login_link.already_on_screen source=coach");
            return SendOutcome.ALREADY_SENT;
        }
        if (!rateLimiter.trySubject(subject.get())) {
            log.info("auth.login_link.rate_limited scope=person source=coach");
            return SendOutcome.RATE_LIMITED;
        }
        DeliveryResult result = issueAndSend(subject.get(), e164, null, turnCorrelationId);
        log.info("auth.login_link.sent source=coach delivered={} reason={}", result.isSuccessful(),
                result.isSuccessful() ? "" : result.failureReason());
        return result.isSuccessful() ? SendOutcome.SENT : SendOutcome.FAILED;
    }

    /** A link for someone who may sign in, without sending it (the ops API: the owner, the lab, e2e tests). */
    @Transactional
    public Optional<IssuedLink> issueFor(String rawPhone) {
        return PhoneInput.normalize(rawPhone).flatMap(policy::forPhone).map(subject -> {
            String raw = TokenHashing.newRawToken();
            LoginLink link = save(subject, raw);
            return new IssuedLink(url(raw, null), link.getExpiresAt());
        });
    }

    /**
     * D-067: the link behind the morning reminder's button, for whoever signs in with this number (as his own dashboard
     * button would be), valid {@link #MORNING_LINK_TTL}; not sent here - the morning carries it. Empty when the number may
     * not sign in (the morning then goes without the button).
     */
    @Transactional
    public Optional<MorningLink> issueMorning(String e164) {
        return policy.forPhone(e164).map(subject -> {
            String raw = TokenHashing.newRawToken();
            Instant now = clock.instant();
            LoginLink link = links.save(new LoginLink(subject, TokenHashing.sha256Hex(raw), now, now.plus(MORNING_LINK_TTL)));
            return new MorningLink(link.getId(), raw);
        });
    }

    /** D-067: a morning link whose message provably never went out: nobody holds its token, so it is ended. */
    @Transactional
    public void revokeUnsent(UUID linkId) {
        links.revoke(linkId, clock.instant(), "NOT_SENT");
    }

    /** The sign-in address of a token (no {@code next}: he lands on his home screen). */
    public String buttonUrl(String token) {
        return url(token, null);
    }

    /** Counts one use of a valid link and returns whose it is; the caller re-checks the sign-in policy. */
    @Transactional
    public Optional<SignInSubject> use(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = TokenHashing.sha256Hex(rawToken);
        if (links.useIfValid(hash, clock.instant()) == 0) {
            return Optional.empty();
        }
        return links.findByTokenHash(hash).map(l -> new SignInSubject(l.getFatherId(), l.getStaffUserId()));
    }

    /**
     * D-045 / D-053: the platform's Send API sends it (and records a father's timeline row) - the only sender; same link,
     * one row.
     */
    private DeliveryResult issueAndSend(SignInSubject subject, String phone, String next, String turnCorrelationId) {
        String raw = TokenHashing.newRawToken();
        Instant now = clock.instant();
        return platform.issueAndSend(new LoginLink(subject, TokenHashing.sha256Hex(raw), now, now.plus(LINK_TTL)),
                subject, phone, url(raw, next), turnCorrelationId);
    }

    private LoginLink save(SignInSubject subject, String raw) {
        Instant now = clock.instant();
        return links.save(new LoginLink(subject, TokenHashing.sha256Hex(raw), now, now.plus(LINK_TTL)));
    }

    private String url(String raw, String next) {
        return webBaseUrl + CONSUME_PATH + raw
                + DashboardPaths.sanitize(next).map(p -> "&next=" + URLEncoder.encode(p, StandardCharsets.UTF_8)).orElse("");
    }
}
