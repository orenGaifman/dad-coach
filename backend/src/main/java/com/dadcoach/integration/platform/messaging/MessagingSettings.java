package com.dadcoach.integration.platform.messaging;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How Dad Coach talks to the platform's Send API ({@code POST /api/v1/worker/messages/send}, platform spec
 * PHASE6_2_SEND_API_SPEC). Since D-053 (Unified Workflow 6.4) the platform is the only sender: the per-kind
 * {@code MESSAGING_MODE_*} switches (D-045/D-046/D-048), SHADOW and Dad Coach's own Meta sends are gone. The kinds
 * Dad Coach sends: {@link #LOGIN_LINK} (a dashboard link, father or staff), {@link #SCHEDULED} (the platform's
 * scheduled-response callbacks: session timers, daily check, weekly plan) and the conversational lines {@link #REPLY},
 * {@link #FIXED_LINE} (incl. the "something went wrong" line and the deletion answer), {@link #BUTTON_REPLY},
 * {@link #READY_ANSWER}.
 * <ul>
 *   <li>{@code MESSAGING_DELIVERY_STATUS_ENABLED} (default true since D-053: the platform's reports are the only source
 *       of a message's fate): the platform's delivery reports ({@code POST /api/integration/channel/delivery-status})
 *       are applied; off = 404 and nothing changes (the platform retries for 24 h).</li>
 *   <li>{@code MESSAGING_RECONCILE_AFTER} (default 5 min): after an ambiguous answer, when Dad Coach asks the platform
 *       by its key whether the message exists (spec v2.5).</li>
 *   <li>{@code MESSAGING_SEND_READ_TIMEOUT_MS} (default 12000 = the platform's wait of 8 s + 4 s, spec §3.1).
 *       {@code MESSAGING_REPLY_READ_TIMEOUT_MS} (default 5000, 1000 .. the send timeout): a conversational line's POST -
 *       the father is waiting (D-048).</li>
 *   <li>{@code MESSAGING_LOOKUP_READ_TIMEOUT_MS} (default 5000): the reconcile job's {@code GET /messages?idempotencyKey}.
 *       {@code MESSAGING_RECONCILE_PASS_BUDGET} (default PT15S): one reconcile pass stops taking rows once it has run
 *       this long (D-047).</li>
 *   <li>{@code MESSAGING_NOT_CREATED_REPOST_WAIT} (default PT2S, at most 2 s): a line without a ledger the platform
 *       provably did not create is POSTed once more after this wait, then dropped (D-053).</li>
 * </ul>
 */
@Component
public class MessagingSettings {

    private static final Logger log = LoggerFactory.getLogger(MessagingSettings.class);

    public static final String LOGIN_LINK = "LOGIN_LINK";
    public static final String SCHEDULED = "SCHEDULED";
    /** D-048: the conversational lines. */
    public static final String REPLY = "REPLY";
    public static final String FIXED_LINE = "FIXED_LINE";
    public static final String BUTTON_REPLY = "BUTTON_REPLY";
    public static final String READY_ANSWER = "READY_ANSWER";
    /** Every kind Dad Coach can send (the seed contract test walks these). */
    public static final java.util.List<String> KINDS = java.util.List.of(LOGIN_LINK, SCHEDULED, REPLY, FIXED_LINE,
            BUTTON_REPLY, READY_ANSWER);
    public static final Duration MIN_RECONCILE_AFTER = Duration.ofMinutes(5);
    static final Duration MAX_NOT_CREATED_REPOST_WAIT = Duration.ofSeconds(2);

    private volatile int replyReadTimeoutMs = 5000;
    private volatile boolean deliveryStatusEnabled;
    private volatile Duration reconcileAfter;
    private final int connectTimeoutMs;
    private volatile int readTimeoutMs;
    private volatile int lookupReadTimeoutMs = 5000;
    private volatile Duration reconcilePassBudget = Duration.ofSeconds(15);
    private volatile Duration notCreatedRepostWait = MAX_NOT_CREATED_REPOST_WAIT;

    @org.springframework.beans.factory.annotation.Autowired
    public MessagingSettings(@Value("${dad-coach.messaging.delivery-status-enabled:true}") boolean deliveryStatusEnabled,
                             @Value("${dad-coach.messaging.reconcile-after:PT5M}") Duration reconcileAfter,
                             @Value("${dad-coach.messaging.connect-timeout-ms:2000}") int connectTimeoutMs,
                             @Value("${dad-coach.messaging.send-read-timeout-ms:12000}") int readTimeoutMs) {
        this.deliveryStatusEnabled = deliveryStatusEnabled;
        this.reconcileAfter = atLeastMinimum(reconcileAfter);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * D-048: the read timeout of a conversational line's {@code POST /send} ({@code MESSAGING_REPLY_READ_TIMEOUT_MS},
     * default 5000; between 1000 and the send timeout): the father is waiting for the answer and the turn's thread for
     * the platform's word; a line still unanswered then is on its way or unknown - never sent a second time.
     */
    public int replyReadTimeoutMs() {
        return Math.max(1000, Math.min(replyReadTimeoutMs, readTimeoutMs));
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setReplyReadTimeoutMs(@Value("${dad-coach.messaging.reply-read-timeout-ms:5000}") int replyReadTimeoutMs) {
        this.replyReadTimeoutMs = replyReadTimeoutMs;
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

    /** D-053: the wait before a ledger-less line's one re-POST after "nothing created" (≤ 2 s, spec §4.1). */
    public Duration notCreatedRepostWait() {
        return notCreatedRepostWait;
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

    /** D-053: {@code MESSAGING_NOT_CREATED_REPOST_WAIT} (default PT2S, clamped to 0..2 s); tests shorten it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotCreatedRepostWait(@Value("${dad-coach.messaging.not-created-repost-wait:PT2S}") Duration wait) {
        this.notCreatedRepostWait = wait == null || wait.isNegative() ? MAX_NOT_CREATED_REPOST_WAIT
                : wait.compareTo(MAX_NOT_CREATED_REPOST_WAIT) > 0 ? MAX_NOT_CREATED_REPOST_WAIT : wait;
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
