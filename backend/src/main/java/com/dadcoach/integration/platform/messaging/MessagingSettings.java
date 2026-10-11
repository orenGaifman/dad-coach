package com.dadcoach.integration.platform.messaging;

import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * D-045 (Unified Workflow Phase 6.3-DC): who sends each kind of message - Dad Coach itself, or the platform's Send API
 * ({@code POST /api/v1/worker/messages/send}, platform spec PHASE6_2_SEND_API_SPEC §15.2). One switch per kind; only
 * {@code LOGIN_LINK} (a father's dashboard link) exists so far.
 * <ul>
 *   <li>{@code MESSAGING_MODE_LOGIN_LINK} = {@code DIRECT} (default; anything not recognised too): today's path, nothing
 *       else is called; {@code SHADOW}: today's path, then (off the send thread) the platform's dry run of the same
 *       request, differences logged - never a second message; {@code PLATFORM}: the platform sends it.</li>
 *   <li>{@code MESSAGING_DELIVERY_STATUS_ENABLED} (default false): the platform's delivery reports
 *       ({@code POST /api/integration/channel/delivery-status}) are applied; off = 404 and nothing changes (the
 *       platform retries for 24 h).</li>
 *   <li>{@code MESSAGING_RECONCILE_AFTER} (default 5 min): after an ambiguous answer, when Dad Coach asks the platform
 *       by its key whether the message exists (spec v2.5).</li>
 *   <li>{@code MESSAGING_SEND_READ_TIMEOUT_MS} (default 12000 = the platform's wait of 8 s + 4 s, spec §3.1).</li>
 * </ul>
 * Rollback of a kind = its switch back to {@code DIRECT}; keep the delivery-status switch on for 48 h after the last
 * PLATFORM send, so messages the platform still owns are reported.
 */
@Component
public class MessagingSettings {

    private static final Logger log = LoggerFactory.getLogger(MessagingSettings.class);

    public enum Mode { DIRECT, SHADOW, PLATFORM }

    public static final String LOGIN_LINK = "LOGIN_LINK";

    private volatile Mode loginLink;
    private volatile boolean deliveryStatusEnabled;
    private volatile Duration reconcileAfter;
    private final int connectTimeoutMs;
    private volatile int readTimeoutMs;

    public MessagingSettings(@Value("${dad-coach.messaging.modes.login-link:DIRECT}") String loginLink,
                             @Value("${dad-coach.messaging.delivery-status-enabled:false}") boolean deliveryStatusEnabled,
                             @Value("${dad-coach.messaging.reconcile-after:PT5M}") Duration reconcileAfter,
                             @Value("${dad-coach.messaging.connect-timeout-ms:2000}") int connectTimeoutMs,
                             @Value("${dad-coach.messaging.send-read-timeout-ms:12000}") int readTimeoutMs) {
        this.loginLink = parse(LOGIN_LINK, loginLink);
        this.deliveryStatusEnabled = deliveryStatusEnabled;
        this.reconcileAfter = reconcileAfter;
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

    /** Tests flip the switches on the one cached context. */
    public void setLoginLink(Mode mode) {
        this.loginLink = mode;
    }

    public void setDeliveryStatusEnabled(boolean enabled) {
        this.deliveryStatusEnabled = enabled;
    }

    public void setReconcileAfter(Duration reconcileAfter) {
        this.reconcileAfter = reconcileAfter;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
