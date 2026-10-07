package com.dadcoach.auth;

import com.dadcoach.channel.delivery.DeliveryResult;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Magic-link sign-in (D-005, Tair's LoginLinkService). {@link #requestLink} never reveals whether a phone is
 * registered: unknown, invalid, deactivated, deleted and rate-limited all end the same silent way. {@link #consume}
 * atomically validates and spends the token. The link carries the token in the URL fragment, so it never reaches a
 * server log.
 */
@Service
public class LoginLinkService {

    private static final Logger log = LoggerFactory.getLogger(LoginLinkService.class);
    public static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    public record IssuedLink(String url, Instant expiresAt) {
    }

    private final LoginLinkRepository links;
    private final SignInPolicy policy;
    private final LoginLinkDelivery delivery;
    private final LoginLinkRateLimiter rateLimiter;
    private final Clock clock;
    private final String webBaseUrl;

    public LoginLinkService(LoginLinkRepository links, SignInPolicy policy, LoginLinkDelivery delivery,
                            LoginLinkRateLimiter rateLimiter, Clock clock,
                            @Value("${dad-coach.web.base-url:http://localhost:3000}") String webBaseUrl) {
        this.links = links;
        this.policy = policy;
        this.delivery = delivery;
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
        String raw = TokenHashing.newRawToken();
        LoginLink link = save(subject.get(), raw);
        DeliveryResult result = delivery.send(subject.get(), phone.get(), url(raw, next));
        link.recordDelivery(result.isSuccessful(), result.failureReason());
        links.save(link);
        log.info("auth.login_link.requested delivered={} reason={}", result.isSuccessful(),
                result.isSuccessful() ? "" : result.failureReason());
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

    /** Spends the token and returns whose it was; the caller re-checks the sign-in policy. */
    @Transactional
    public Optional<SignInSubject> consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = TokenHashing.sha256Hex(rawToken);
        if (links.consumeIfValid(hash, clock.instant()) == 0) {
            return Optional.empty();
        }
        return links.findByTokenHash(hash).map(l -> new SignInSubject(l.getFatherId(), l.getStaffUserId()));
    }

    private LoginLink save(SignInSubject subject, String raw) {
        Instant now = clock.instant();
        return links.save(new LoginLink(subject, TokenHashing.sha256Hex(raw), now, now.plus(TOKEN_TTL)));
    }

    private String url(String raw, String next) {
        return webBaseUrl + "/auth/consume#token=" + raw
                + DashboardPaths.sanitize(next).map(p -> "&next=" + URLEncoder.encode(p, StandardCharsets.UTF_8)).orElse("");
    }
}
