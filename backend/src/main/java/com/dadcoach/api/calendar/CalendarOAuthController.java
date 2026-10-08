package com.dadcoach.api.calendar;

import com.dadcoach.calendar.CalendarLinkSigner;
import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.qualitytime.QualityTimeService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Google Calendar OAuth hops (public in the security config; each verifies an HMAC signature itself):
 * <ol>
 *   <li>{@code GET /api/v1/calendar/connect/{fatherId}?exp&sig} - a link the dashboard gives the signed-in father
 *       ({@link CalendarLinkSigner#connectQuery}); without a valid, unexpired signature nothing happens (404), so
 *       nobody can attach a calendar to someone else's account. Redirects to Google with a signed state.</li>
 *   <li>{@code GET /api/v1/calendar/callback} - Google's redirect; only a valid signed state connects anyone. Ends
 *       on the dashboard ({@code WEB_BASE_URL} + a local path) with calendar_connected / calendar_error.</li>
 * </ol>
 * The calendar is optional (D-007): sessions work without it.
 */
@RestController
public class CalendarOAuthController {

    private static final Logger log = LoggerFactory.getLogger(CalendarOAuthController.class);

    private final GoogleCalendarService calendar;
    private final FatherRepository fathers;
    private final CalendarLinkSigner signer;
    private final QualityTimeService sessions;

    public CalendarOAuthController(GoogleCalendarService calendar, FatherRepository fathers, CalendarLinkSigner signer,
                                   QualityTimeService sessions) {
        this.calendar = calendar;
        this.fathers = fathers;
        this.signer = signer;
        this.sessions = sessions;
    }

    @GetMapping("/api/v1/calendar/connect/{fatherId}")
    public ResponseEntity<Void> connect(@PathVariable long fatherId,
                                        @RequestParam(required = false) Long exp,
                                        @RequestParam(required = false) String sig,
                                        @RequestParam(required = false) String redirectUrl) {
        if (!signer.verifyConnect(fatherId, exp, sig)
                || fathers.findById(fatherId).filter(f -> f.getStatus() != FatherStatus.DELETED).isEmpty()) {
            log.warn("Calendar connect refused: invalid or expired link");
            return ResponseEntity.notFound().build();
        }
        return redirect(calendar.getAuthorizationUrl(fatherId, redirectUrl));
    }

    @GetMapping("/api/v1/calendar/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        Optional<CalendarLinkSigner.State> signed = signer.verifyState(state);
        String back = signed.map(CalendarLinkSigner.State::redirectUrl).orElse(signer.defaultRedirect());
        if (error != null) {
            return redirect(withParam(back, "calendar_error", error));
        }
        if (code == null || signed.isEmpty()) {
            log.warn("Calendar OAuth callback without a code or with an invalid state");
            return redirect(withParam(back, "calendar_error", "missing_params"));
        }
        boolean connected = calendar.handleOAuthCallback(code, signed.get().fatherId());
        log.atInfo().setMessage("calendar.connect.result").addKeyValue("fatherId", signed.get().fatherId())
                .addKeyValue("connected", connected).log();
        if (connected) {
            // Sessions booked while the calendar was off (or its authorization had expired) go in now.
            try {
                sessions.addUpcomingToCalendar(signed.get().fatherId());
            } catch (RuntimeException e) {
                log.warn("Adding upcoming sessions to the calendar failed: {}", e.getMessage());
            }
        }
        return redirect(connected ? withParam(back, "calendar_connected", "true")
                : withParam(back, "calendar_error", "token_exchange_failed"));
    }

    private static ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private static String withParam(String url, String key, String value) {
        return url + (url.contains("?") ? "&" : "?") + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
