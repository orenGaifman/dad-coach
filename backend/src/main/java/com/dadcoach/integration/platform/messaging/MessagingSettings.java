package com.dadcoach.integration.platform.messaging;

import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * D-045 (Unified Workflow Phase 6.3-DC): who sends each kind of message - Dad Coach itself, or the platform's Send API
 * ({@code POST /api/v1/worker/messages/send}, platform spec PHASE6_2_SEND_API_SPEC §15.2). One switch per kind:
 * {@code LOGIN_LINK} (a father's dashboard link, D-045), {@code SCHEDULED} (the platform's scheduled-response callbacks:
 * session timers, daily check, weekly plan - D-046) and {@code BELT_PROMOTION} (the belt text and its optional image,
 * D-046).
 * <ul>
 *   <li>{@code MESSAGING_MODE_LOGIN_LINK} / {@code MESSAGING_MODE_SCHEDULED} / {@code MESSAGING_MODE_BELT_PROMOTION} =
 *       {@code DIRECT} (default; anything not recognised too): today's path, nothing else is called; {@code SHADOW}:
 *       today's path, then (off the send thread) the platform's dry run of the same request, differences logged - never
 *       a second message; {@code PLATFORM}: the platform sends it.</li>
 *   <li>{@code MESSAGING_DELIVERY_STATUS_ENABLED} (default false): the platform's delivery reports
 *       ({@code POST /api/integration/channel/delivery-status}) are applied; off = 404 and nothing changes (the
 *       platform retries for 24 h).</li>
 *   <li>{@code MESSAGING_RECONCILE_AFTER} (default 5 min): after an ambiguous answer, when Dad Coach asks the platform
 *       by its key whether the message exists (spec v2.5).</li>
 *   <li>{@code MESSAGING_SEND_READ_TIMEOUT_MS} (default 12000 = the platform's wait of 8 s + 4 s, spec §3.1).</li>
 *   <li>{@code MESSAGING_LOOKUP_READ_TIMEOUT_MS} (default 5000): the reconcile job's {@code GET /messages?idempotencyKey}
 *       (a lookup has no wait on the platform). {@code MESSAGING_RECONCILE_PASS_BUDGET} (default PT15S): one reconcile
 *       pass stops taking rows once it has run this long; the rest are taken the next minute (D-047: the job shares the
 *       scheduler thread with every other job while {@code APP_SCHEDULING_LANES} is off).</li>
 * </ul>
 * Rollback of a kind = its switch back to {@code DIRECT}; keep the delivery-status switch on for 48 h after the last
 * PLATFORM send, so messages the platform still owns are reported.
 */
@Component
public class MessagingSettings {

    private static final Logger log = LoggerFactory.getLogger(MessagingSettings.class);

    public enum Mode { DIRECT, SHADOW, PLATFORM }

    public static final String LOGIN_LINK = "LOGIN_LINK";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String BELT_PROMOTION = "BELT_PROMOTION";
    public static final Duration MIN_RECONCILE_AFTER = Duration.ofMinutes(5);

    private volatile Mode loginLink;
    private volatile Mode scheduled;
    private volatile Mode beltPromotion;
    private volatile boolean deliveryStatusEnabled;
    private volatile Duration reconcileAfter;
    private final int connectTimeoutMs;
    private volatile int readTimeoutMs;
    private volatile int lookupReadTimeoutMs = 5000;
    private volatile Duration reconcilePassBudget = Duration.ofSeconds(15);

    /** D-045's switches only (the other kinds DIRECT). */
    public MessagingSettings(String loginLink, boolean deliveryStatusEnabled, Duration reconcileAfter, int connectTimeoutMs,
                             int readTimeoutMs) {
        this(loginLink, "DIRECT", "DIRECT", deliveryStatusEnabled, reconcileAfter, connectTimeoutMs, readTimeoutMs);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MessagingSettings(@Value("${dad-coach.messaging.modes.login-link:DIRECT}") String loginLink,
                             @Value("${dad-coach.messaging.modes.scheduled:DIRECT}") String scheduled,
                             @Value("${dad-coach.messaging.modes.belt-promotion:DIRECT}") String beltPromotion,
                             @Value("${dad-coach.messaging.delivery-status-enabled:false}") boolean deliveryStatusEnabled,
                             @Value("${dad-coach.messaging.reconcile-after:PT5M}") Duration reconcileAfter,
                             @Value("${dad-coach.messaging.connect-timeout-ms:2000}") int connectTimeoutMs,
                             @Value("${dad-coach.messaging.send-read-timeout-ms:12000}") int readTimeoutMs) {
        this.loginLink = parse(LOGIN_LINK, loginLink);
        this.scheduled = parse(SCHEDULED, scheduled);
        this.beltPromotion = parse(BELT_PROMOTION, beltPromotion);
        this.deliveryStatusEnabled = deliveryStatusEnabled;
        this.reconcileAfter = atLeastMinimum(reconcileAfter);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /** Only the three names, in any case; anything else (empty, a typo) is DIRECT - today's path. */
    static Mode parse(String kind, String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.DIRECT;
        }
        try {
            return Mode.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.atWarn().setMessage("messaging.mode_unknown").addKeyValue("kind", kind).addKeyValue("value", raw)
                    .addKeyValue("using", Mode.DIRECT).log();
            return Mode.DIRECT;
        }
    }

    public Mode loginLink() {
        return loginLink;
    }

    public Mode scheduled() {
        return scheduled;
    }

    public Mode beltPromotion() {
        return beltPromotion;
    }

    public boolean isDeliveryStatusEnabled() {
        return deliveryStatusEnabled;
    }

    public Duration reconcileAfter() {
        return reconcileAfter;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }

    public int lookupReadTimeoutMs() {
        return lookupReadTimeoutMs;
    }

    public Duration reconcilePassBudget() {
        return reconcilePassBudget;
    }

    /** D-047: {@code MESSAGING_LOOKUP_READ_TIMEOUT_MS} (default 5000); tests shorten it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLookupReadTimeoutMs(@Value("${dad-coach.messaging.lookup-read-timeout-ms:5000}") int lookupReadTimeoutMs) {
        this.lookupReadTimeoutMs = lookupReadTimeoutMs;
    }

    /** D-047: {@code MESSAGING_RECONCILE_PASS_BUDGET} (default PT15S); tests shorten it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setReconcilePassBudget(@Value("${dad-coach.messaging.reconcile-pass-budget:PT15S}") Duration budget) {
        this.reconcilePassBudget = budget == null || budget.isNegative() || budget.isZero() ? Duration.ofSeconds(15) : budget;
    }

    /** Tests flip the switches on the one cached context. */
    public void setLoginLink(Mode mode) {
        this.loginLink = mode;
    }

    public void setScheduled(Mode mode) {
        this.scheduled = mode;
    }

    public void setBeltPromotion(Mode mode) {
        this.beltPromotion = mode;
    }

    public void setDeliveryStatusEnabled(boolean enabled) {
        this.deliveryStatusEnabled = enabled;
    }

    public void setReconcileAfter(Duration reconcileAfter) {
        this.reconcileAfter = atLeastMinimum(reconcileAfter);
    }

    /**
     * Never below 5 minutes (review L3): "not found" proves nothing was created only when no request can still be in
     * flight on the platform (its wait is at most 12 s, its request timeout 30 s) - spec v2.5.
     */
    static Duration atLeastMinimum(Duration reconcileAfter) {
        if (reconcileAfter == null || reconcileAfter.compareTo(MIN_RECONCILE_AFTER) < 0) {
            log.atWarn().setMessage("messaging.reconcile_after_clamped").addKeyValue("value", reconcileAfter)
                    .addKeyValue("using", MIN_RECONCILE_AFTER).log();
            return MIN_RECONCILE_AFTER;
        }
        return reconcileAfter;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
