package com.dadcoach.integration.platform;

import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.weeklyplan.ArmedTimers;
import com.dadcoach.weeklyplan.SessionTimerPlanner;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A session's timers as the platform really holds them (D-037). The model arms them in the booking turn
 * (schedule_state_transition); whether it did is known only to the platform. After the turn returns - never inside a
 * tool call, where the turn holds the conversation's lock - every timer {@link SessionTimerPlanner} plans for the
 * session is read back and the missing ones are armed through the platform's worker API. A reply may then promise only
 * the timers the platform confirmed. Nothing here throws: a platform that cannot be reached confirms nothing.
 */
@Component
public class SessionTimers {

    private static final Logger log = LoggerFactory.getLogger(SessionTimers.class);
    public static final String REFERENCE_TYPE = "quality_time";

    /** What the platform confirmed for one session: the keys planned, and those it holds at their planned time. */
    public record Confirmation(UUID sessionId, Set<String> planned, Set<String> confirmed) {

        public boolean complete() {
            return confirmed.containsAll(planned);
        }
    }

    private final WorkflowPlatformClient platform;
    private final WeeklyGoalService weeklyGoals;
    private final Clock clock;

    public SessionTimers(WorkflowPlatformClient platform, WeeklyGoalService weeklyGoals, Clock clock) {
        this.platform = platform;
        this.weeklyGoals = weeklyGoals;
        this.clock = clock;
    }

    /**
     * Every planned timer of these upcoming sessions, confirmed: those already pending at their planned time count, the
     * others are armed now (the platform's dedupe moves one armed at another time). One read for all of them.
     */
    public List<Confirmation> ensure(Father father, List<QualityTime> sessions) {
        Instant now = clock.instant();
        String userId = PersonRefs.whatsappId(father.getPhone());
        Map<UUID, Map<String, String>> plans = new LinkedHashMap<>();
        for (QualityTime qt : sessions) {
            if (qt == null || qt.getStatus() != QualityTimeStatus.SCHEDULED || qt.getScheduledStart() == null
                    || !qt.getScheduledEnd().isAfter(now)) {
                continue;
            }
            plans.put(qt.getId(), SessionTimerPlanner.plan(qt.getScheduledStart(), qt.getScheduledEnd(),
                    weeklyGoals.zoneFor(father), now));
        }
        if (plans.values().stream().allMatch(Map::isEmpty)) {
            return plans.keySet().stream().map(id -> new Confirmation(id, Set.of(), Set.of())).toList();
        }
        Optional<List<WorkflowPlatformClient.PendingTimer>> read = safeRead(userId);
        Map<String, Map<String, Instant>> pending = read.map(SessionTimers::bySession).orElse(Map.of());
        List<Confirmation> out = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Map<String, String>> plan : plans.entrySet()) {
            Map<String, Instant> held = pending.getOrDefault(plan.getKey().toString(), Map.of());
            Set<String> confirmed = new LinkedHashSet<>();
            int armedNow = 0;
            String outcome = read.isPresent() ? "READ" : "READ_FAILED";
            for (Map.Entry<String, String> timer : plan.getValue().entrySet()) {
                Instant at = Instant.parse(timer.getValue());
                if (at.equals(held.get(timer.getKey()))) {
                    confirmed.add(timer.getKey());
                    continue;
                }
                WorkflowPlatformClient.ArmResult result = safeArm(userId, timer.getKey(), at, plan.getKey());
                if (result.armed() && at.equals(result.scheduledAt())) {
                    confirmed.add(timer.getKey());
                    armedNow++;
                } else {
                    outcome = result.outcome().name() + (result.status() > 0 ? "_" + result.status() : "");
                    log.atWarn().setMessage("session.timers.arm_failed").addKeyValue("fatherId", father.getId())
                            .addKeyValue("sessionId", plan.getKey()).addKeyValue("transitionKey", timer.getKey())
                            .addKeyValue("outcome", result.outcome()).addKeyValue("status", result.status())
                            .addKeyValue("error", abbreviate(result.error())).log();
                }
            }
            Confirmation c = new Confirmation(plan.getKey(), plan.getValue().keySet(), confirmed);
            log.atInfo().setMessage("session.timers.confirmed").addKeyValue("fatherId", father.getId())
                    .addKeyValue("sessionId", plan.getKey()).addKeyValue("planned", c.planned())
                    .addKeyValue("confirmed", confirmed).addKeyValue("armedNow", armedNow)
                    .addKeyValue("result", c.complete() ? "ALL_CONFIRMED" : confirmed.isEmpty() ? "NONE_CONFIRMED" : "PARTIAL")
                    .addKeyValue("lastOutcome", outcome).log();
            out.add(c);
        }
        return out;
    }

    /** The timers the platform holds for all his sessions, read once; {@link ArmedTimers#UNKNOWN} when it cannot say. */
    public ArmedTimers armed(Father father) {
        Optional<List<WorkflowPlatformClient.PendingTimer>> read = safeRead(PersonRefs.whatsappId(father.getPhone()));
        return read.map(p -> ArmedTimers.of(bySession(p))).orElse(ArmedTimers.UNKNOWN);
    }

    /** A session closed in this turn (cancelled, moved away from, done): its pending timers go. Best effort. */
    public void cancel(Father father, UUID sessionId) {
        try {
            int n = platform.cancelTimers(PersonRefs.whatsappId(father.getPhone()), REFERENCE_TYPE, sessionId.toString());
            log.atInfo().setMessage("session.timers.cancelled").addKeyValue("fatherId", father.getId())
                    .addKeyValue("sessionId", sessionId).addKeyValue("cancelled", n).log();
        } catch (RuntimeException e) {
            log.atWarn().setMessage("session.timers.cancel_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    private Optional<List<WorkflowPlatformClient.PendingTimer>> safeRead(String userId) {
        try {
            return platform.pendingTimers(userId);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private WorkflowPlatformClient.ArmResult safeArm(String userId, String key, Instant at, UUID sessionId) {
        try {
            return platform.armTimer(userId, key, at, REFERENCE_TYPE, sessionId.toString());
        } catch (RuntimeException e) {
            return new WorkflowPlatformClient.ArmResult(WorkflowPlatformClient.ArmResult.Outcome.UNAVAILABLE, null, 0,
                    e.getClass().getSimpleName());
        }
    }

    private static Map<String, Map<String, Instant>> bySession(List<WorkflowPlatformClient.PendingTimer> pending) {
        Map<String, Map<String, Instant>> out = new HashMap<>();
        for (WorkflowPlatformClient.PendingTimer t : pending) {
            if (REFERENCE_TYPE.equals(t.referenceType()) && t.referenceId() != null && t.transitionKey() != null
                    && t.scheduledAt() != null) {
                out.computeIfAbsent(t.referenceId(), k -> new HashMap<>()).merge(t.transitionKey(), t.scheduledAt(),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        return out;
    }

    private static String abbreviate(String s) {
        return s == null ? null : s.length() > 200 ? s.substring(0, 200) : s;
    }
}
