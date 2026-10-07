package com.dadcoach.calendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Signs the two values that tie a Google Calendar connection to a father: the {@code /calendar/connect} link and the
 * OAuth {@code state}. Without a signature anyone could start (or finish) the OAuth flow for another father's id and
 * attach their own Google account to him. Both expire after {@link #TTL}.
 *
 * <p>The redirect after the OAuth flow is only ever a path under the web app ({@code dad-coach.web.base-url}).
 */
@Component
public class CalendarLinkSigner {

    static final Duration TTL = Duration.ofMinutes(30);

    private final byte[] key;
    private final String webBaseUrl;
    private final Clock clock;

    public CalendarLinkSigner(@Value("${dad-coach.security.link-secret}") String secret,
                              @Value("${dad-coach.web.base-url:http://localhost:3000}") String webBaseUrl,
                              Optional<Clock> clock) {
        this.key = ("calendar-link:" + secret).getBytes(StandardCharsets.UTF_8);
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    /** Query string ({@code exp=..&sig=..}) for {@code GET /api/v1/calendar/connect/{fatherId}}. */
    public String connectQuery(long fatherId) {
        long exp = clock.instant().plus(TTL).getEpochSecond();
        return "exp=" + exp + "&sig=" + mac("connect|" + fatherId + "|" + exp);
    }

    public boolean verifyConnect(long fatherId, Long exp, String sig) {
        if (exp == null || sig == null || exp < clock.instant().getEpochSecond()) {
            return false;
        }
        return constantTimeEquals(mac("connect|" + fatherId + "|" + exp), sig);
    }

    /** OAuth state: {@code fatherId.exp.path.mac}, path base64url-encoded (may be empty). */
    public String state(long fatherId, String redirectUrl) {
        long exp = clock.instant().plus(TTL).getEpochSecond();
        String path = Base64.getUrlEncoder().withoutPadding().encodeToString(safePath(redirectUrl).getBytes(StandardCharsets.UTF_8));
        String body = fatherId + "." + exp + "." + path;
        return body + "." + mac("state|" + body);
    }

    public Optional<State> verifyState(String state) {
        if (state == null) {
            return Optional.empty();
        }
        String[] parts = state.split("\\.", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        String body = parts[0] + "." + parts[1] + "." + parts[2];
        if (!constantTimeEquals(mac("state|" + body), parts[3])) {
            return Optional.empty();
        }
        try {
            long fatherId = Long.parseLong(parts[0]);
            long exp = Long.parseLong(parts[1]);
            if (exp < clock.instant().getEpochSecond()) {
                return Optional.empty();
            }
            String path = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            return Optional.of(new State(fatherId, webBaseUrl + (path.isEmpty() ? "/dashboard" : path)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String defaultRedirect() {
        return webBaseUrl + "/dashboard";
    }

    /** Only a local path ("/..."), or a full URL under the web app reduced to its path; anything else = default. */
    String safePath(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return "";
        }
        String r = redirectUrl.trim();
        if (r.startsWith(webBaseUrl + "/")) {
            r = r.substring(webBaseUrl.length());
        }
        if (!r.startsWith("/") || r.startsWith("//") || r.contains("\\") || r.contains("@")) {
            return "";
        }
        return r;
    }

    private String mac(String data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public record State(long fatherId, String redirectUrl) {
    }
}
