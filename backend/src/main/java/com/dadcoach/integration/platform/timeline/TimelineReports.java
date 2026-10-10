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
 * <p>Reports never block a delivery: one daemon thread sends them in submission order (his message before the product's
 * answer, a card before its turn's outcome), each up to {@link #ATTEMPTS} times (connection errors, timeouts, 5xx, 408,
 * 429; any other 4xx is final). A report that does not land is logged {@code timeline.report_failed} - the platform then
 * shows the draft, as before this phase.</p>
 */
@Component
public class TimelineReports {

    private static final Logger log = LoggerFactory.getLogger(TimelineReports.class);
    static final int ATTEMPTS = 3;
    static final int QUEUE = 1000;
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
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(QUEUE), runnable -> {
                Thread t = new Thread(runnable, "timeline-reports");
                t.setDaemon(true);
                return t;
            });
    private volatile Duration retryDelay = Duration.ofSeconds(1);

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
        Map<String, Object> structured = null;
        if (buttonId != null) {
            structured = new LinkedHashMap<>();
            structured.put("buttonId", buttonId);
            structured.put("title", buttonTitle);
        }
        submit(WorkflowPlatformClient.INBOUND_PATH, correlationId, new InboundBody(properties.getWorkerKey(),
                properties.getWorkflowKey(), PersonRefs.whatsappId(person.phone()), CHANNEL, correlationId,
                content == null ? "" : content, messageType, structured, metadata(person), properties.getTenantId(),
                personRef(person), person.name()));
    }

    /** One message Dad Coach sent (delivery fields from {@code part.sent()}; none for a history-only note). */
    public void outbound(Person person, Part part) {
        DeliveryResult sent = part.sent();
        String providerId = sent != null && sent.isSuccessful() ? sent.providerMessageId() : null;
        String status = sent == null || !sent.isSuccessful() ? null : sent.isHeld() ? "HELD" : "ACCEPTED";
        submit(WorkflowPlatformClient.OUTBOUND_PATH, part.correlationId(), new OutboundBody(properties.getWorkerKey(),
                PersonRefs.whatsappId(person.phone()), CHANNEL, part.correlationId(), part.content(), metadata(person),
                properties.getTenantId(), personRef(person), person.name(), properties.getWorkflowKey(),
                part.turnCorrelationId(), part.replacesDraft() ? Boolean.TRUE : null, providerId, status, part.template(),
                part.buttons() == null || part.buttons().isEmpty() ? null : part.buttons(), part.kind(),
                part.supersededTurns() == null || part.supersededTurns().isEmpty() ? null : List.copyOf(part.supersededTurns())));
    }

    /** What happened to a turn's reply: AS_IS (with the send), DROPPED (nothing of it went out), FAILED. */
    public void turnOutcome(Person person, String turnCorrelationId, String outcome, DeliveryResult sent, String reason,
                            List<String> supersededTurns) {
        String providerId = sent != null && sent.isSuccessful() ? sent.providerMessageId() : null;
        String status = sent == null || !sent.isSuccessful() ? null : sent.isHeld() ? "HELD" : "ACCEPTED";
        submit(WorkflowPlatformClient.TURN_OUTCOME_PATH, turnCorrelationId, new TurnOutcomeBody(properties.getWorkerKey(),
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

    private void submit(String path, String correlationId, Object body) {
        if (!platform.isEnabled()) {
            return;
        }
        inFlight.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    send(path, correlationId, body);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                    .addKeyValue("correlationId", correlationId).addKeyValue("error", "QUEUE_FULL").log();
        }
    }

    private void send(String path, String correlationId, Object body) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                platform.report(path, body);
                log.atInfo().setMessage("timeline.reported").addKeyValue("path", path)
                        .addKeyValue("correlationId", correlationId).addKeyValue("attempt", attempt).log();
                return;
            } catch (WorkflowPlatformClient.ReportFailure e) {
                if (!e.retryable() || attempt == ATTEMPTS) {
                    log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                            .addKeyValue("correlationId", correlationId).addKeyValue("status", e.status())
                            .addKeyValue("error", e.getMessage()).addKeyValue("attempts", attempt).log();
                    return;
                }
            } catch (RuntimeException e) {
                log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                        .addKeyValue("correlationId", correlationId).addKeyValue("error", e.getClass().getSimpleName())
                        .addKeyValue("attempts", attempt).log();
                return;
            }
            try {
                Thread.sleep(retryDelay.toMillis() * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.atWarn().setMessage("timeline.report_failed").addKeyValue("path", path)
                        .addKeyValue("correlationId", correlationId).addKeyValue("error", "INTERRUPTED").log();
                return;
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

    @PreDestroy
    void stop() {
        awaitIdle(Duration.ofSeconds(5));
        executor.shutdownNow();
    }
}
