package com.dadcoach.integration.platform.timeline;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.FatherTimezones;
import com.dadcoach.integration.platform.WorkflowPlatformClient;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * D-039 (Phase 3.4): tells the platform what Dad Coach actually delivered, so the father's conversation on the platform
 * (the model's history, the admin view, reviews) shows what he saw - not what the model drafted. Behind
 * {@code workflow.platform.delivery-reports} (env PLATFORM_DELIVERY_REPORTS, default off): off, callers keep their
 * pre-Phase-3 records and nothing here is called.
 *
 * <p>Reports never block a delivery. They go out on {@link #LANES} daemon threads; a father's reports always take the same
 * lane, so his arrive in submission order (his message before the product's answer, a card before its turn's outcome)
 * and a report stuck behind one father's turn holds only that lane. Each report gets up to {@link #ATTEMPTS} attempts
 * (connection errors, 5xx, 408, 429; any other 4xx is final) within {@link #BUDGET}. A report tied to a turn
 * (turn-outcome, or outbound with a turnCorrelationId) waits on the platform for the conversation's lock - behind an
 * in-flight turn, up to ~90 s - so it gets {@link #TURN_TIMEOUT}; when it times out its fate is unknown (the platform may
 * still apply it): {@code timeline.report_outcome_unknown}, never retried. Other reports: 10 s, a timeout is retried. A
 * report that does not land is logged {@code timeline.report_failed} - the platform then shows the draft, as before this
 * phase.</p>
 */
@Component
public class TimelineReports {

    private static final Logger log = LoggerFactory.getLogger(TimelineReports.class);
    static final int ATTEMPTS = 3;
    static final int LANES = 4;
    static final int QUEUE_PER_LANE = 250;
    /** Platform turns take up to ~90 s; a turn-tied report waits for the turn's lock. */
    static final Duration TURN_TIMEOUT = Duration.ofSeconds(100);
    /** All attempts of one report, together: one report never holds its lane longer. */
    static final Duration BUDGET = Duration.ofSeconds(115);
    static final String CHANNEL = "whatsapp";

    public static final String AS_IS = "AS_IS";
    public static final String DROPPED = "DROPPED";
    public static final String FAILED = "FAILED";

    /** Kinds of product rows (free text on the platform: the admin view shows it). */
    public static final String KIND_REPLY = "REPLY";
    public static final String KIND_FIXED_LINE = "FIXED_LINE";
    public static final String KIND_BUTTON_REPLY = "BUTTON_REPLY";
    public static final String KIND_READY_ANSWER = "READY_ANSWER";
    public static final String KIND_DASHBOARD_LINK = "DASHBOARD_LINK";
    public static final String KIND_DASHBOARD_NOTE = "DASHBOARD_NOTE";
    public static final String KIND_BELT_PROMOTION = "BELT_PROMOTION";
    public static final String KIND_SCHEDULED = "SCHEDULED";

    /** Who the report is about: the WhatsApp number always; the father when Dad Coach knows him. */
    public record Person(String phone, Long fatherId, String name, String timezone) {

        public static Person of(Father father) {
            return new Person(father.getPhone(), father.getId(), father.getDisplayName(), FatherTimezones.of(father).getId());
        }

        public static Person of(String phone, Optional<Father> father) {
            return father.map(Person::of).orElseGet(() -> new Person(phone, null, null, FatherTimezones.of(null).getId()));
        }
    }

    /** One message Dad Coach sent (or, for a dashboard note, wrote into the history only). */
    public record Part(String correlationId, String content, String turnCorrelationId, boolean replacesDraft,
                       DeliveryResult sent, Map<String, Object> template, List<Map<String, Object>> buttons, String kind,
                       List<String> supersededTurns) {

        /** A product message with its delivery, no turn. */
        public static Part sent(String correlationId, String content, DeliveryResult sent, String kind) {
            return new Part(correlationId, content, null, false, sent, null, null, kind, null);
        }

        /** What was sent instead of the turn's reply (the reply becomes a draft). */
        public static Part replacing(String correlationId, String turnCorrelationId, String content, DeliveryResult sent,
                                     String kind, List<String> supersededTurns) {
            return new Part(correlationId, content, turnCorrelationId, true, sent, null, null, kind, supersededTurns);
        }

        public Part withButtons(List<Map<String, Object>> buttons) {
            return new Part(correlationId, content, turnCorrelationId, replacesDraft, sent, template, buttons, kind,
                    supersededTurns);
        }

        public Part withTemplate(Map<String, Object> template) {
            return new Part(correlationId, content, turnCorrelationId, replacesDraft, sent, template, buttons, kind,
                    supersededTurns);
        }

        public Part forTurn(String turnCorrelationId) {
            return new Part(correlationId, content, turnCorrelationId, replacesDraft, sent, template, buttons, kind,
                    supersededTurns);
        }
    }

    /** {@code POST /api/v1/worker/messages/inbound}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InboundBody(String workerKey, String workflowKey, String userId, String channelId, String correlationId,
                              String content, String messageType, Map<String, Object> structured,
                              Map<String, Object> metadata, UUID tenantId, String personRef, String personName) {
    }

    /** {@code POST /api/v1/worker/messages/outbound} with the Phase 3 fields. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OutboundBody(String workerKey, String userId, String channelId, String correlationId, String content,
                               Map<String, Object> metadata, UUID tenantId, String personRef, String personName,
                               String workflowKey, String turnCorrelationId, Boolean replacesDraft,
                               String providerMessageId, String deliveryStatus, Map<String, Object> template,
                               List<Map<String, Object>> buttons, String kind, List<String> supersededTurns) {
    }

    /** {@code POST /api/v1/worker/messages/turn-outcome}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TurnOutcomeBody(String workerKey, String workflowKey, String userId, String channelId, UUID tenantId,
                                  String turnCorrelationId, String outcome, String providerMessageId,
                                  String deliveryStatus, String reason, List<String> supersededTurns) {
    }

    private final WorkflowPlatformClient platform;
    private final WorkflowPlatformProperties properties;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final List<ThreadPoolExecutor> lanes = java.util.stream.IntStream.range(0, LANES)
            .mapToObj(i -> new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(QUEUE_PER_LANE), runnable -> {
                        Thread t = new Thread(runnable, "timeline-reports-" + i);
                        t.setDaemon(true);
                        return t;
                    })).toList();
    private volatile Duration retryDelay = Duration.ofSeconds(1);
    private volatile Duration turnTimeout = TURN_TIMEOUT;
    private volatile Duration otherTimeout = WorkflowPlatformClient.REPORT_TIMEOUT;
    private volatile Duration budget = BUDGET;

    public TimelineReports(WorkflowPlatformClient platform, WorkflowPlatformProperties properties) {
        this.platform = platform;
        this.properties = properties;
    }

    /** Whether reports are on (the switch, and a configured platform). */
    public boolean enabled() {
        return properties.isDeliveryReports() && platform.isEnabled();
    }

    /** A father's message Dad Coach answered without a turn (recorded before the product's answer). */
    public void inbound(Person person, String correlationId, String content, String messageType, String buttonId,
                        String buttonTitle) {
        try {
            buildInbound(person, correlationId, content, messageType, buttonId, buttonTitle);
        } catch (RuntimeException e) {
            notBuilt(WorkflowPlatformClient.INBOUND_PATH, correlationId, e);
        }
    }

    private void buildInbound(Person person, String correlationId, String content, String messageType, String buttonId,
                              String buttonTitle) {
        Map<String, Object> structured = null;
        if (buttonId != null) {
            structured = new LinkedHashMap<>();
            structured.put("buttonId", buttonId);
            structured.put("title", buttonTitle);
        }
        submit(WorkflowPlatformClient.INBOUND_PATH, correlationId, person.phone(), false, new InboundBody(properties.getWorkerKey(),
                properties.getWorkflowKey(), PersonRefs.whatsappId(person.phone()), CHANNEL, correlationId,
                content == null ? "" : content, messageType, structured, metadata(person), properties.getTenantId(),
                personRef(person), person.name()));
    }

    /** One message Dad Coach sent (delivery fields from {@code part.sent()}; none for a history-only note). */
    public void outbound(Person person, Part part) {
        try {
            buildOutbound(person, part);
        } catch (RuntimeException e) {
            notBuilt(WorkflowPlatformClient.OUTBOUND_PATH, part == null ? null : part.correlationId(), e);
        }
    }

    private void buildOutbound(Person person, Part part) {
        DeliveryResult sent = part.sent();
        String providerId = sent != null && sent.isSuccessful() ? sent.providerMessageId() : null;
        String status = sent == null || !sent.isSuccessful() ? null : sent.isHeld() ? "HELD" : "ACCEPTED";
        submit(WorkflowPlatformClient.OUTBOUND_PATH, part.correlationId(), person.phone(), part.turnCorrelationId() != null,
                new OutboundBody(properties.getWorkerKey(),
                PersonRefs.whatsappId(person.phone()), CHANNEL, part.correlationId(), part.content(), metadata(person),
                properties.getTenantId(), personRef(person), person.name(), properties.getWorkflowKey(),
                part.turnCorrelationId(), part.replacesDraft() ? Boolean.TRUE : null, providerId, status, part.template(),
                part.buttons() == null || part.buttons().isEmpty() ? null : part.buttons(), part.kind(),
                part.supersededTurns() == null || part.supersededTurns().isEmpty() ? null : List.copyOf(part.supersededTurns())));
    }

    /** What happened to a turn's reply: AS_IS (with the send), DROPPED (nothing of it went out), FAILED. */
    public void turnOutcome(Person person, String turnCorrelationId, String outcome, DeliveryResult sent, String reason,
                            List<String> supersededTurns) {
        try {
            buildTurnOutcome(person, turnCorrelationId, outcome, sent, reason, supersededTurns);
        } catch (RuntimeException e) {
            notBuilt(WorkflowPlatformClient.TURN_OUTCOME_PATH, turnCorrelationId, e);
        }
    }

    private void buildTurnOutcome(Person person, String turnCorrelationId, String outcome, DeliveryResult sent,
                                  String reason, List<String> supersededTurns) {
        String providerId = sent != null && sent.isSuccessful() ? sent.providerMessageId() : null;
        String status = sent == null || !sent.isSuccessful() ? null : sent.isHeld() ? "HELD" : "ACCEPTED";
        submit(WorkflowPlatformClient.TURN_OUTCOME_PATH, turnCorrelationId, person.phone(), true, new TurnOutcomeBody(properties.getWorkerKey(),
                properties.getWorkflowKey(), PersonRefs.whatsappId(person.phone()), CHANNEL, properties.getTenantId(),
                turnCorrelationId, outcome, providerId, status, reason,
                supersededTurns == null || supersededTurns.isEmpty() ? null : List.copyOf(supersededTurns)));
    }

    private Map<String, Object> metadata(Person person) {
        return Map.of("timezone", person.timezone());
    }

    private static String personRef(Person person) {
        return person.fatherId() == null ? null : PersonRefs.of(person.fatherId());
    }

    private void notBuilt(String path, String correlationId, RuntimeException e) {
        log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                .addKeyValue("correlationId", correlationId).addKeyValue("error", "NOT_BUILT: " + e.getClass().getSimpleName())
                .log();
    }

    private void submit(String path, String correlationId, String phone, boolean turnTied, Object body) {
        if (!platform.isEnabled()) {
            return;
        }
        ThreadPoolExecutor lane = lanes.get(Math.floorMod(phone == null ? 0 : phone.hashCode(), LANES));
        inFlight.incrementAndGet();
        try {
            lane.execute(() -> {
                try {
                    send(path, correlationId, turnTied, body);
                } catch (RuntimeException e) {
                    log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                            .addKeyValue("correlationId", correlationId).addKeyValue("error", e.getClass().getSimpleName())
                            .log();
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                    .addKeyValue("correlationId", correlationId)
                    .addKeyValue("error", lane.isShutdown() ? "SHUTDOWN" : "QUEUE_FULL").log();
        }
    }

    private void send(String path, String correlationId, boolean turnTied, Object body) {
        long started = System.nanoTime();
        Duration timeout = turnTied ? turnTimeout : otherTimeout;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Duration left = budget.minusNanos(System.nanoTime() - started);
            Duration attemptTimeout = left.compareTo(timeout) < 0 ? left : timeout;
            try {
                platform.report(path, body, attemptTimeout);
                log.atInfo().setMessage("timeline.reported").addKeyValue("path", path)
                        .addKeyValue("correlationId", correlationId).addKeyValue("attempt", attempt).log();
                return;
            } catch (WorkflowPlatformClient.ReportFailure e) {
                if (e.timedOut() && turnTied) {
                    // the platform may still apply it (it waited behind a turn): its fate is unknown, never retried
                    log.atWarn().setMessage("timeline.report_outcome_unknown").addKeyValue("path", path)
                            .addKeyValue("correlationId", correlationId).addKeyValue("attempts", attempt)
                            .addKeyValue("timeoutMs", attemptTimeout.toMillis()).log();
                    return;
                }
                Duration pause = retryDelay.multipliedBy(attempt);
                boolean noTimeLeft = budget.minusNanos(System.nanoTime() - started).compareTo(pause.plusSeconds(1)) < 0;
                if (!e.retryable() || attempt == ATTEMPTS || noTimeLeft) {
                    log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                            .addKeyValue("correlationId", correlationId).addKeyValue("status", e.status())
                            .addKeyValue("error", e.getMessage()).addKeyValue("attempts", attempt).log();
                    return;
                }
                try {
                    Thread.sleep(pause.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                            .addKeyValue("correlationId", correlationId).addKeyValue("error", "INTERRUPTED").log();
                    return;
                }
            }
        }
    }

    /** Waits until every submitted report was sent or given up (tests, shutdown). */
    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (inFlight.get() > 0) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** The pause before attempt n+1 is n x this (1 s by default). */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    /** Timeouts (turn-tied reports, others) and the total per report - tests shorten them. */
    public void setTimeouts(Duration turnTimeout, Duration otherTimeout, Duration budget) {
        this.turnTimeout = turnTimeout;
        this.otherTimeout = otherTimeout;
        this.budget = budget;
    }

    @PreDestroy
    void stop() {
        awaitIdle(Duration.ofSeconds(5));
        lanes.forEach(ThreadPoolExecutor::shutdownNow);
    }
}
