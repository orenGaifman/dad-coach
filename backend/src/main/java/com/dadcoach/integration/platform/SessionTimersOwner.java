package com.dadcoach.integration.platform;

import com.dadcoach.config.SchedulingLanes;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * D-050 (Unified Workflow Phase 5.4, PHASE5_4_SPEC.md §4.4): Dad Coach owns its session timers
 * ({@code DC_SESSION_TIMERS_PRODUCT_OWNED}, default off).
 *
 * <ul>
 *   <li>A session closed outside a WhatsApp turn - confirmed or cancelled on the dashboard, "done" from its button - has its
 *       pending timers cancelled too, after the change commits (before, only a WhatsApp turn cancelled them; the others
 *       fired a turn that then declined). Best effort, as the turn's own cancel.</li>
 *   <li>Every 10 minutes the timers of every upcoming session (starting within 36 h, until its follow-up time) are read
 *       back and the missing ones armed ({@link SessionTimers#ensure}) - a failed arm, a 409 while the father was in another
 *       state, a platform blip heal on their own. Sessions are read right before, so a closed one is never re-armed; a
 *       cancel racing the read can leave a timer whose tool-only / guarded turn then declines it at no model cost.</li>
 * </ul>
 *
 * <p>Never called from inside a turn's tool call (the turn holds the conversation's lock): the dashboard and the button
 * are not turns, and the reconcile runs on its own thread.</p>
 */
@Component
public class SessionTimersOwner {

    /** Unified Workflow 5.4 v3: this job's key on the platform's product-job scheduler. */
    public static final String PLATFORM_JOB = "session_timers";

    private com.dadcoach.platformjobs.PlatformJobs platformJobs;

    /** Optional: hand-built instances in tests tick as before. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPlatformJobs(com.dadcoach.platformjobs.PlatformJobs platformJobs) {
        this.platformJobs = platformJobs;
    }

    /**
     * The body the platform runs for a slot: the tick's own work (same guard, same summary log) without its catch - a
     * failure reaches the platform, which retries the slot.
     */
    public Object platformRun() throws Exception {
        if (!enabled()) {
            return "DISABLED";
        }
        int fathersSeen = reconcile();
        if (fathersSeen > 0) {
            log.atInfo().addKeyValue("fathers", fathersSeen).log("session.timers.reconciled");
        }
        return fathersSeen;
    }

    private static final Logger log = LoggerFactory.getLogger(SessionTimersOwner.class);

    /** How far ahead the reconcile looks; a session further out is armed by its booking turn and by a later pass. */
    static final Duration AHEAD = Duration.ofHours(36);
    /** How far back a still-open session's start may be (a long session's follow-up is still ahead). */
    static final Duration BEHIND = Duration.ofHours(12);
    /** Fathers never reminded by the reconcile. */
    static final java.util.Set<com.dadcoach.father.FatherStatus> SILENT = java.util.EnumSet.of(
            com.dadcoach.father.FatherStatus.DELETED, com.dadcoach.father.FatherStatus.PAUSED, com.dadcoach.father.FatherStatus.CHURNED);

    private final WorkflowPlatformProperties properties;
    private final SessionTimers timers;
    private final QualityTimeRepository sessions;
    private final FatherRepository fathers;
    private final Clock clock;

    public SessionTimersOwner(WorkflowPlatformProperties properties, SessionTimers timers, QualityTimeRepository sessions,
                              FatherRepository fathers, Clock clock) {
        this.properties = properties;
        this.timers = timers;
        this.sessions = sessions;
        this.fathers = fathers;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.isSessionTimersProductOwned();
    }

    /** A session closed outside a turn: its timers go, after the caller's transaction commits (at once without one). */
    public void closedOutsideATurn(Father father, UUID sessionId) {
        if (!enabled() || father == null || sessionId == null) {
            return;
        }
        Runnable cancel = () -> timers.cancel(father, sessionId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cancel.run();
                }
            });
        } else {
            cancel.run();
        }
    }

    @Scheduled(fixedDelayString = "${app.session-timers.reconcile-interval:PT10M}",
            initialDelayString = "${app.session-timers.reconcile-initial-delay:PT3M}",
            scheduler = SchedulingLanes.HOUSEKEEPING)
    public void scheduledReconcile() {
        // Unified Workflow 5.4 v3: the tick goes through PlatformJobs (its locks; nothing when the platform owns the job)
        if (platformJobs != null) {
            platformJobs.tick(PLATFORM_JOB, this::scheduledReconcileHere);
            return;
        }
        scheduledReconcileHere();
    }

    /** The tick's own work, as before 5.4 v3 (PlatformJobs runs it under the job's locks). */
    public void scheduledReconcileHere() {
        if (!enabled()) {
            return;
        }
        try {
            int fathersSeen = reconcile();
            if (fathersSeen > 0) {
                log.atInfo().addKeyValue("fathers", fathersSeen).log("session.timers.reconciled");
            }
        } catch (RuntimeException e) {
            log.warn("Session timer reconcile failed", e);
        }
    }

    /** @return how many fathers' timers were read back */
    public int reconcile() {
        Instant now = clock.instant();
        List<QualityTime> upcoming = sessions.findByStatusAndScheduledStartBetween(QualityTimeStatus.SCHEDULED,
                now.minus(BEHIND), now.plus(AHEAD));
        Map<Long, List<QualityTime>> byFather = new LinkedHashMap<>();
        for (QualityTime qt : upcoming) {
            if (qt.getScheduledEnd() != null
                    && qt.getScheduledEnd().plus(com.dadcoach.weeklyplan.SessionTimerPlanner.FOLLOW_UP_DELAY).isAfter(now)) {
                byFather.computeIfAbsent(qt.getFatherId(), id -> new java.util.ArrayList<>()).add(qt);
            }
        }
        int seen = 0;
        for (Long fatherId : byFather.keySet()) {
            Optional<Father> father = fathers.findById(fatherId);
            // a father who asked to be deleted, was paused or left is never reminded; no phone, nothing to address
            if (father.isEmpty() || father.get().getPhone() == null || SILENT.contains(father.get().getStatus())) {
                continue;
            }
            // re-read right before arming: a session closed since the window query is not armed again
            Instant at = clock.instant();
            List<QualityTime> fresh = sessions.findByFatherIdAndStatus(fatherId, QualityTimeStatus.SCHEDULED).stream()
                    .filter(qt -> qt.getScheduledStart() != null && qt.getScheduledEnd() != null
                            && qt.getScheduledStart().isBefore(at.plus(AHEAD))
                            && qt.getScheduledEnd().plus(com.dadcoach.weeklyplan.SessionTimerPlanner.FOLLOW_UP_DELAY).isAfter(at))
                    .toList();
            if (fresh.isEmpty()) {
                continue;
            }
            try {
                timers.ensure(father.get(), fresh, true);
                seen++;
            } catch (RuntimeException e) {
                log.atWarn().addKeyValue("fatherId", fatherId).addKeyValue("error", e.getClass().getSimpleName())
                        .log("session.timers.reconcile_failed");
            }
        }
        return seen;
    }
}
