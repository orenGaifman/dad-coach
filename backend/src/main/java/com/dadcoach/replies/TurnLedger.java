package com.dadcoach.replies;

import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What really changed for a father during one AI turn (D-036, D-2): his sessions, children and this week's goal are
 * read before the turn and again after it, and compared. The reply may confirm only what is in the difference - the
 * tools' own results never leave the platform, but their effect is in the database. Ids and statuses are compared,
 * not timestamps, so a clock never decides.
 */
@Component
public class TurnLedger {

    /** One session as it stood: status and how many children. */
    record SessionState(QualityTimeStatus status, int children) {
    }

    /** The father's state before the turn. */
    public record Snapshot(Long fatherId, Map<UUID, SessionState> sessions, long children, Integer goalHours,
                           Integer nextWeekHours, String profile) {
        static final Snapshot NO_FATHER = new Snapshot(null, Map.of(), 0, null, null, null);
    }

    /** What the turn did. */
    public record Changes(List<QualityTime> booked, List<QualityTime> joined, int cancelled, int completed,
                          boolean childAdded, boolean goalCreated, boolean nextWeekGoalSaved, boolean goalExists,
                          boolean upcomingSession, List<String> bookingReply, List<String> upcomingStarts,
                          boolean profileSaved, CoachReplies.Week week, List<UUID> closed) {

        public Changes(List<QualityTime> booked, List<QualityTime> joined, int cancelled, int completed, boolean childAdded,
                       boolean goalCreated, boolean nextWeekGoalSaved, boolean goalExists, boolean upcomingSession,
                       List<String> bookingReply, List<String> upcomingStarts, boolean profileSaved, CoachReplies.Week week) {
            this(booked, joined, cancelled, completed, childAdded, goalCreated, nextWeekGoalSaved, goalExists,
                    upcomingSession, bookingReply, upcomingStarts, profileSaved, week, List.of());
        }

        /** D-037: the same changes with the booking confirmation rebuilt from the timers the platform confirmed. */
        public Changes withBookingReply(List<String> reply) {
            return new Changes(booked, joined, cancelled, completed, childAdded, goalCreated, nextWeekGoalSaved, goalExists,
                    upcomingSession, reply, upcomingStarts, profileSaved, week, closed);
        }

        public Changes(List<QualityTime> booked, List<QualityTime> joined, int cancelled, int completed, boolean childAdded,
                       boolean goalCreated, boolean nextWeekGoalSaved, boolean goalExists, boolean upcomingSession,
                       List<String> bookingReply, List<String> upcomingStarts) {
            this(booked, joined, cancelled, completed, childAdded, goalCreated, nextWeekGoalSaved, goalExists,
                    upcomingSession, bookingReply, upcomingStarts, false, null);
        }

        public static final Changes NONE = new Changes(List.of(), List.of(), 0, 0, false, false, false, false, false, null,
                List.of());

        public boolean sessionBookedOrMoved() {
            return !booked.isEmpty() || !joined.isEmpty();
        }

        public boolean any() {
            return sessionBookedOrMoved() || cancelled > 0 || completed > 0 || childAdded || goalCreated || nextWeekGoalSaved
                    || profileSaved;
        }
    }

    private final QualityTimeRepository sessions;
    private final ChildRepository children;
    private final WeeklyGoalRepository goals;
    private final WeeklyGoalService goalService;
    private final Clock clock;
    private final com.dadcoach.weeklyplan.WeeklyPlanContextBuilder weeklyPlan;
    private com.dadcoach.domain.father.FatherRepository fathers;

    @org.springframework.beans.factory.annotation.Autowired
    void setFathers(com.dadcoach.domain.father.FatherRepository fathers) {
        this.fathers = fathers;
    }

    public TurnLedger(QualityTimeRepository sessions, ChildRepository children, WeeklyGoalRepository goals,
                      WeeklyGoalService goalService, Clock clock,
                      com.dadcoach.weeklyplan.WeeklyPlanContextBuilder weeklyPlan) {
        this.weeklyPlan = weeklyPlan;
        this.sessions = sessions;
        this.children = children;
        this.goals = goals;
        this.goalService = goalService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Snapshot before(Optional<Father> father) {
        if (father.isEmpty()) {
            return Snapshot.NO_FATHER;
        }
        Father f = father.get();
        Map<UUID, SessionState> states = new HashMap<>();
        for (QualityTime qt : sessions.findByFatherIdOrderByScheduledStartDesc(f.getId())) {
            states.put(qt.getId(), new SessionState(qt.getStatus(), qt.getChildIds().size()));
        }
        Optional<WeeklyGoal> goal = currentGoal(f);
        return new Snapshot(f.getId(), states, children.countActiveByFatherId(f.getId()),
                goal.map(WeeklyGoal::getTargetHours).orElse(null), goal.map(WeeklyGoal::getNextWeekTargetHours).orElse(null),
                profile(f));
    }

    @Transactional(readOnly = true)
    public Changes after(Snapshot before, Optional<Father> father) {
        if (father.isEmpty()) {
            return Changes.NONE;
        }
        Father f = fathers.findById(father.get().getId()).orElse(father.get()); // as the turn left it
        List<QualityTime> booked = new ArrayList<>();
        List<QualityTime> joined = new ArrayList<>();
        int cancelled = 0;
        int completed = 0;
        List<UUID> closed = new ArrayList<>();
        boolean upcoming = false;
        List<String> upcomingStarts = new ArrayList<>();
        var now = clock.instant();
        var zone = goalService.zoneFor(f);
        for (QualityTime qt : sessions.findByFatherIdOrderByScheduledStartDesc(f.getId())) {
            SessionState was = before.sessions().get(qt.getId());
            QualityTimeStatus status = qt.getStatus();
            if (status == QualityTimeStatus.SCHEDULED && qt.getScheduledStart().isAfter(now)) {
                upcoming = true;
                upcomingStarts.add(HebrewWhen.time(qt.getScheduledStart().atZone(zone).toLocalTime()));
            }
            if (was == null) {
                if (status == QualityTimeStatus.SCHEDULED || status == QualityTimeStatus.COMPLETED) {
                    booked.add(qt);
                }
                if (status == QualityTimeStatus.COMPLETED) {
                    completed++;
                }
                qt.getChildIds(); // loaded while the transaction is open
                continue;
            }
            if (status != was.status()) {
                if (status == QualityTimeStatus.CANCELLED || status == QualityTimeStatus.MISSED) {
                    cancelled++;
                } else if (status == QualityTimeStatus.COMPLETED) {
                    completed++;
                }
                if (was.status() == QualityTimeStatus.SCHEDULED && status != QualityTimeStatus.SCHEDULED) {
                    closed.add(qt.getId()); // D-037: its timers go (a move closes the old session)
                }
            } else if (qt.getChildIds().size() > was.children()) {
                joined.add(qt);
            }
        }
        Optional<WeeklyGoal> goal = currentGoal(f);
        boolean goalCreated = before.goalHours() == null && goal.isPresent();
        Integer next = goal.map(WeeklyGoal::getNextWeekTargetHours).orElse(null);
        boolean nextSaved = next != null && !next.equals(before.nextWeekHours());
        List<String> bookingReply = null;
        if (booked.size() == 1 && joined.isEmpty() && booked.get(0).getStatus() == QualityTimeStatus.SCHEDULED
                && booked.get(0).getScheduledStart().isAfter(now)) {
            bookingReply = bookingReply(f, booked.get(0), cancelled > 0);
        }
        return new Changes(booked, joined, cancelled, completed, children.countActiveByFatherId(f.getId()) > before.children(),
                goalCreated, nextSaved, goal.isPresent(), upcoming, bookingReply, upcomingStarts,
                before.fatherId() == null || !java.util.Objects.equals(before.profile(), profile(f)),
                weekOf(f), closed);
    }

    /** The same lines schedule_quality_time / reschedule_quality_time returned as their reply. */
    private List<String> bookingReply(Father father, QualityTime qt, boolean moved) {
        var zone = goalService.zoneFor(father);
        String when = HebrewWhen.label(qt.getScheduledStart().atZone(zone), clock.instant().atZone(zone).toLocalDate());
        int minutes = (int) java.time.Duration.between(qt.getScheduledStart(), qt.getScheduledEnd()).toMinutes();
        var timers = com.dadcoach.weeklyplan.SessionTimerPlanner.plan(qt.getScheduledStart(), qt.getScheduledEnd(), zone,
                clock.instant()).keySet();
        CoachReplies.Week week = CoachReplies.Week.of(weeklyPlan.build(father).get("coverage"));
        String names = com.dadcoach.qualitytime.SessionChildren.hebrew(qt);
        return moved ? CoachReplies.moved(when, minutes, names, timers, week)
                : CoachReplies.booked(when, minutes, names, timers, week);
    }

    private CoachReplies.Week weekOf(Father f) {
        try {
            return CoachReplies.Week.of(weeklyPlan.build(f).get("coverage"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** What save_user_profile changes: his name, timezone, preferred time. */
    private static String profile(Father f) {
        return f.getDisplayName() + "|" + f.getTimezone() + "|" + f.getPreferredCoachingTime();
    }

    private Optional<WeeklyGoal> currentGoal(Father father) {
        return goals.findByFatherIdAndWeekStartDate(father.getId(), goalService.weekStartFor(father, clock.instant()));
    }
}
