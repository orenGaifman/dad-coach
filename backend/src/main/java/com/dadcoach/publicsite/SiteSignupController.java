package com.dadcoach.publicsite;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The marketing site's signup form (site/src/signup-form.js). Public and cross-origin (CORS: SITE_ORIGINS only,
 * see {@link PublicSiteSecurityConfig}). Always 202 for a readable body: stored, honeypot, rate-limited and
 * invalid submissions look the same from outside.
 */
@RestController
public class SiteSignupController {

    /** website = the hidden honeypot field; people leave it empty. */
    public record SignupRequest(String name, String phone, String source, String page, String website) {
    }

    private final SiteSignupService signups;

    public SiteSignupController(SiteSignupService signups) {
        this.signups = signups;
    }

    @PostMapping("/api/public/site-signups")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Boolean> submit(@RequestBody SignupRequest body, HttpServletRequest request) {
        signups.submit(body.name(), body.phone(), body.source(), body.page(), body.website(), clientAddress(request));
        return Map.of("received", true);
    }

    /** Render puts the visitor's address first in X-Forwarded-For. */
    static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
