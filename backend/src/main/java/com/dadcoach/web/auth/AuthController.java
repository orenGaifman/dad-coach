package com.dadcoach.web.auth;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.auth.DashboardProperties;
import com.dadcoach.auth.DashboardSession;
import com.dadcoach.auth.LoginLinkService;
import com.dadcoach.auth.SessionCookies;
import com.dadcoach.auth.SessionService;
import com.dadcoach.auth.SignInPolicy;
import com.dadcoach.web.common.WebException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** D-005 magic-link sign-in: a short-lived single-use link becomes a long-lived, revocable server session. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RequestLinkRequest(@NotBlank @Size(max = 40) String phone, @Size(max = 200) String next) {
    }

    public record ConsumeLinkRequest(@NotBlank @Size(max = 200) String token) {
    }

    private final LoginLinkService links;
    private final SessionService sessions;
    private final SessionCookies cookies;
    private final SignInPolicy policy;
    private final MeController.Factory me;
    private final DashboardProperties properties;

    public AuthController(LoginLinkService links, SessionService sessions, SessionCookies cookies, SignInPolicy policy,
                          MeController.Factory me, DashboardProperties properties) {
        this.links = links;
        this.sessions = sessions;
        this.cookies = cookies;
        this.policy = policy;
        this.me = me;
        this.properties = properties;
    }

    /**
     * Always 200 with the same body, whatever happened - the answer never reveals whether a number is registered.
     * The body carries Dad Coach's public WhatsApp number: whoever got no link writes to it first (a link outside
     * the 24-hour window needs an approved template) and tries again.
     */
    @PostMapping("/request-link")
    public Map<String, Object> requestLink(@Valid @RequestBody RequestLinkRequest request, HttpServletRequest http) {
        links.requestLink(request.phone(), request.next(), clientAddress(http));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SENT_IF_REGISTERED");
        String number = properties.getWhatsappPublicNumber();
        body.put("whatsappNumber", number == null || number.isBlank() ? null : number.strip());
        return body;
    }

    @PostMapping("/consume-link")
    public MeResponse consumeLink(@Valid @RequestBody ConsumeLinkRequest request, HttpServletRequest http,
                                  HttpServletResponse response) {
        var subject = links.consume(request.token()).orElseThrow(() ->
                new WebException(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN_LINK", "invalid, expired or used link"));
        DashboardPrincipal principal = policy.resolve(null, subject).orElseThrow(() ->
                new WebException(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN_LINK", "this account cannot sign in"));
        // One browser, one session: whatever it was signed in as before is revoked, not orphaned.
        cookies.read(http).ifPresent(previous -> sessions.revoke(previous, DashboardSession.REASON_REPLACED));
        cookies.write(response, sessions.issueToken(subject, http.getHeader(HttpHeaders.USER_AGENT)));
        return me.of(principal);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http, HttpServletResponse response) {
        cookies.read(http).ifPresent(raw -> sessions.revoke(raw, DashboardSession.REASON_LOGOUT));
        cookies.clear(response);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal DashboardPrincipal principal, HttpServletResponse response) {
        sessions.revokeAll(principal, DashboardSession.REASON_LOGOUT_ALL);
        cookies.clear(response);
    }

    /** The browser's address: the first X-Forwarded-For hop (nginx, Render's edge), else the socket's. */
    static String clientAddress(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return http.getRemoteAddr();
    }
}
