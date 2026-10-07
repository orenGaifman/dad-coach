package com.dadcoach.weeklyplan;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.qualitytime.SessionChildren;
import com.dadcoach.qualitytime.SessionIntervals;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.weeklygoal.WeeklyGoalStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the {@code weekly_plan_context}: the authoritative picture of the father's current
 * Sunday-Saturday week that a coaching agent needs to decide whether to act proactively.
 *
 * <p>Everything time-related is computed here, in the father's own timezone, so the agent never has to
 * derive week boundaries, durations or coverage itself:</p>
 * <ul>
 *   <li><b>coverage</b> - completed + valid planned minutes against the weekly goal. Planned counts only
 *       SCHEDULED sessions of this week that have not ended; cancelled and missed sessions never count,
 *       and a rescheduled session counts once (rescheduling cancels the original). Time where sessions overlap
 *       counts once (the union of their intervals - completed first, then planned, then awaiting), so two
 *       sessions for the same slot never count its minutes twice.</li>
 *   <li><b>session phases</b> - UPCOMING / IN_PROGRESS / AWAITING_CONFIRMATION (ended but still
 *       SCHEDULED, i.e. the father has not said whether it happened) / COMPLETED / CANCELLED / MISSED.</li>
 *   <li><b>previous_week</b> - read straight from the previous week's goal and sessions, so it does not
 *       depend on whether the weekly finalization job has already run.</li>
 * </ul>
 *
 * <p><b>Shape.</b> The Workflow Platform renders provider data into the prompt one level deep, shows a
 * list of more than three items only as a count, and cuts every value at 200 characters. So every
 * collection here is a map of short one-line entries (sessions keyed by local "date time", each line
 * carrying the session id), nested objects hold only scalars, and no top-level value is null.</p>
 */
@Component
public class WeeklyPlanContextBuilder {

    /** Sessions older than this are irrelevant for proactive decisions. */
    static final Duration AWAITING_CONFIRMATION_WINDOW = Duration.ofDays(14);
    /** How far ahead "next session" and "scheduled after this week" look. */
    static final Duration LOOKAHEAD = Duration.ofDays(14);
    static final int MAX_NOTES_LENGTH = 180;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final QualityTimeRepository qualityTimeRepository;
    private final ChildRepository childRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;
    private final WeeklyGoalService weeklyGoalService;
    private final Clock clock;

    private final com.dadcoach.common.DashboardLinks dashboardLinks;

    public WeeklyPlanContextBuilder(
            QualityTimeRepository qualityTimeRepository,
            ChildRepository childRepository,
            WeeklyGoalRepository weeklyGoalRepository,
            WeeklyGoalService weeklyGoalService,
            Clock clock,
            com.dadcoach.common.DashboardLinks dashboardLinks) {
        this.dashboardLinks = dashboardLinks;
        this.qualityTimeRepository = qualityTimeRepository;
        this.childRepository = childRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.weeklyGoalService = weeklyGoalService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> build(Father father) {
        Instant now = clock.instant();
        ZoneId zone = weeklyGoalService.zoneFor(father);
        ZonedDateTime localNow = now.atZone(zone);

        LocalDate weekStart = weeklyGoalService.weekStartFor(father, now);
        LocalDate weekEnd = weekStart.plusDays(6);
        Instant weekStartInstant = weekStart.atStartOfDay(zone).toInstant();
        Instant weekEndInstant = weekStart.plusDays(7).atStartOfDay(zone).toInstant();
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        Instant previousWeekStartInstant = previousWeekStart.atStartOfDay(zone).toInstant();

        Map<Long, String> childNames = new HashMap<>();
        for (Child child : childRepository.findByFatherIdAndStatus(father.getId(), "ACTIVE")) {
            childNames.put(child.getId(), child.getName());
        }

        // Oldest first, so every list below is chronological.
        List<QualityTime> sessions = new ArrayList<>(
                qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(father.getId()));
        sessions.sort(Comparator.comparing(QualityTime::getScheduledStart));

        List<Map<String, Object>> thisWeek = new ArrayList<>();
        List<Map<String, Object>> sessionsToday = new ArrayList<>();
        List<Map<String, Object>> awaitingConfirmation = new ArrayList<>();
        List<Map<String, Object>> scheduledAfterThisWeek = new ArrayList<>();
        Map<String, Object> nextSession = null;
        List<SessionIntervals.Interval> completedIntervals = new ArrayList<>();
        List<SessionIntervals.Interval> plannedIntervals = new ArrayList<>();
        List<SessionIntervals.Interval> awaitingIntervals = new ArrayList<>();
        List<SessionIntervals.Interval> previousCompletedIntervals = new ArrayList<>();
        int previousCompletedSessions = 0;
        int previousNotCompletedSessions = 0;
        List<String> previousNotes = new ArrayList<>();

        for (QualityTime qt : sessions) {
            Instant start = qt.getScheduledStart();
            Instant end = qt.getScheduledEnd();
            if (start == null || end == null) {
                continue;
            }
            String phase = phaseOf(qt, now);
            int minutes = durationMinutes(qt);
            boolean inThisWeek = !start.isBefore(weekStartInstant) && start.isBefore(weekEndInstant);
            boolean inPreviousWeek = !start.isBefore(previousWeekStartInstant) && start.isBefore(weekStartInstant);
            Map<String, Object> view = sessionView(qt, phase, minutes, zone, now, childNames);

            if (inThisWeek) {
                thisWeek.add(view);
                if (qt.getStatus() == QualityTimeStatus.COMPLETED) {
                    completedIntervals.add(SessionIntervals.of(qt));
                } else if ("UPCOMING".equals(phase) || "IN_PROGRESS".equals(phase)) {
                    plannedIntervals.add(SessionIntervals.of(qt));
                } else if ("AWAITING_CONFIRMATION".equals(phase)) {
                    awaitingIntervals.add(SessionIntervals.of(qt));
                }
            }
            if (inPreviousWeek) {
                if (qt.getStatus() == QualityTimeStatus.COMPLETED) {
                    previousCompletedIntervals.add(SessionIntervals.of(qt));
                    previousCompletedSessions++;
                    if (qt.getCompletionNotes() != null && !qt.getCompletionNotes().isBlank()) {
                        previousNotes.add(truncate(qt.getCompletionNotes()));
                    }
                } else if (qt.getStatus() != QualityTimeStatus.SCHEDULED || "AWAITING_CONFIRMATION".equals(phase)) {
                    previousNotCompletedSessions++;
                }
            }
            boolean scheduledAndNotEnded = "UPCOMING".equals(phase) || "IN_PROGRESS".equals(phase);
            if (scheduledAndNotEnded && start.atZone(zone).toLocalDate().equals(localNow.toLocalDate())) {
                sessionsToday.add(view);
            }
            if (scheduledAndNotEnded && nextSession == null && start.isBefore(now.plus(LOOKAHEAD))) {
                nextSession = view;
            }
            if (scheduledAndNotEnded && !start.isBefore(weekEndInstant) && start.isBefore(now.plus(LOOKAHEAD))) {
                scheduledAfterThisWeek.add(view);
            }
            if ("AWAITING_CONFIRMATION".equals(phase) && end.isAfter(now.minus(AWAITING_CONFIRMATION_WINDOW))) {
                awaitingConfirmation.add(0, view); // most recently ended first
            }
        }

        // Wall-clock minutes, overlaps once: completed, then what planned adds to it, then what awaiting adds.
        List<SessionIntervals.Interval> completedAndPlanned = new ArrayList<>(completedIntervals);
        completedAndPlanned.addAll(plannedIntervals);
        List<SessionIntervals.Interval> all = new ArrayList<>(completedAndPlanned);
        all.addAll(awaitingIntervals);
        int completedFromSessions = SessionIntervals.unionMinutes(completedIntervals);
        int plannedMinutes = SessionIntervals.unionMinutes(completedAndPlanned) - completedFromSessions;
        int awaitingMinutes = SessionIntervals.unionMinutes(all) - completedFromSessions - plannedMinutes;
        int previousCompletedMinutes = SessionIntervals.unionMinutes(previousCompletedIntervals);

        Optional<WeeklyGoal> currentGoal = weeklyGoalRepository.findByFatherIdAndWeekStartDate(father.getId(), weekStart);
        Optional<WeeklyGoal> previousGoal = weeklyGoalRepository.findByFatherIdAndWeekStartDate(father.getId(), previousWeekStart);
        List<WeeklyGoal> allGoals = weeklyGoalRepository.findByFatherIdOrderByWeekStartDateDesc(father.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timezone", zone.getId());

        Map<String, Object> nowView = new LinkedHashMap<>();
        nowView.put("utc", now.toString());
        nowView.put("local_date", localNow.toLocalDate().toString());
        nowView.put("local_time", localNow.format(HH_MM));
        nowView.put("weekday", localNow.getDayOfWeek().name());
        data.put("now", nowView);

        data.put("calendar_connected", father.hasGoogleCalendarConfigured());

        Map<String, Object> week = new LinkedHashMap<>();
        week.put("week_start", weekStart.toString());
        week.put("week_end", weekEnd.toString());
        week.put("days_left_including_today", (int) (weekEnd.toEpochDay() - localNow.toLocalDate().toEpochDay()) + 1);
        data.put("current_week", week);

        data.put("goal", goalView(currentGoal));

        // Completed time is what the goal has actually been credited with (the figure its weekly result is
        // judged on). Without a goal nothing is credited, so the completed sessions are reported instead.
        int completedMinutes = currentGoal.map(WeeklyGoal::getActualMinutes).orElse(completedFromSessions);
        Integer targetMinutes = currentGoal.map(goal -> goal.getTargetHours() * 60).orElse(null);
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("target_minutes", targetMinutes);
        coverage.put("completed_minutes", completedMinutes);
        coverage.put("planned_minutes", plannedMinutes);
        coverage.put("covered_minutes", completedMinutes + plannedMinutes);
        coverage.put("uncovered_minutes",
                targetMinutes == null ? null : Math.max(0, targetMinutes - completedMinutes - plannedMinutes));
        coverage.put("is_covered", targetMinutes == null ? null : completedMinutes + plannedMinutes >= targetMinutes);
        coverage.put("awaiting_confirmation_minutes", awaitingMinutes);
        data.put("coverage", coverage);

        data.put("sessions_this_week", lines(thisWeek));
        data.put("sessions_today", lines(sessionsToday));
        data.put("next_session", nextSession != null ? line(nextSession) : NONE);
        data.put("awaiting_confirmation", lines(awaitingConfirmation));
        data.put("scheduled_after_this_week", lines(scheduledAfterThisWeek));

        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("week_start", previousWeekStart.toString());
        previous.put("week_end", previousWeekStart.plusDays(6).toString());
        previous.put("goal_exists", previousGoal.isPresent());
        previousGoal.ifPresent(goal -> {
            previous.put("goal_status", goal.getStatus().name());
            previous.put("goal_target_minutes", goal.getTargetHours() * 60);
            previous.put("goal_credited_minutes", goal.getActualMinutes());
            previous.put("goal_met", goalMet(goal));
        });
        previous.put("completed_minutes", previousCompletedMinutes);
        previous.put("completed_sessions", previousCompletedSessions);
        previous.put("not_completed_sessions", previousNotCompletedSessions);
        data.put("previous_week", previous);
        Map<String, Object> notes = new LinkedHashMap<>();
        for (int i = 0; i < previousNotes.size(); i++) {
            notes.put("note_" + (i + 1), previousNotes.get(i));
        }
        data.put("previous_week_completion_notes", notes.isEmpty() ? NONE : notes);

        Map<String, Object> history = new LinkedHashMap<>();
        history.put("has_any_goal", !allGoals.isEmpty());
        history.put("weeks_with_goal", allGoals.size());
        allGoals.stream()
                .filter(goal -> goal.getWeekStartDate().isBefore(weekStart))
                .findFirst()
                .ifPresent(goal -> history.put("latest_previous_target_hours", goal.getTargetHours()));
        data.put("goal_history", history);

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("current_belt", father.getCurrentBelt() != null ? father.getCurrentBelt().name() : null);
        progress.put("quality_time_streak", father.getQualityTimeStreak());
        progress.put("total_quality_times_completed", father.getTotalQualityTimesCompleted());
        progress.put("current_streak_weeks", father.getCurrentStreakWeeks());
        data.put("progress", progress);

        data.put("dashboard_url", dashboardLinks.loginUrl());

        return data;
    }

    static final String NONE = "none";

    /** Sessions as "local date time" -> one-line summary, in order; "none" when empty. */
    private static Object lines(List<Map<String, Object>> views) {
        if (views.isEmpty()) {
            return NONE;
        }
        Map<String, Object> lines = new LinkedHashMap<>();
        for (Map<String, Object> view : views) {
            String key = view.get("local_date") + " " + view.get("local_start");
            String unique = key;
            for (int n = 2; lines.containsKey(unique); n++) {
                unique = key + " #" + n;
            }
            lines.put(unique, line(view));
        }
        return lines;
    }

    /** e.g. "UPCOMING | Noa | 60 min | TUESDAY 17:00-18:00 | starts in 300 min | id=..." (well under 200 chars). */
    static String line(Map<String, Object> view) {
        StringBuilder sb = new StringBuilder()
                .append(view.get("phase"))
                .append(" | ").append(shortNames(view.get("child_name")))
                .append(" | ").append(view.get("duration_minutes")).append(" min")
                .append(" | ").append(view.get("weekday")).append(' ').append(view.get("local_date"))
                .append(' ').append(view.get("local_start")).append('-').append(view.get("local_end"));
        if (view.containsKey("starts_in_minutes")) {
            sb.append(" | starts in ").append(view.get("starts_in_minutes")).append(" min");
        }
        if (view.containsKey("ended_minutes_ago")) {
            sb.append(" | ended ").append(view.get("ended_minutes_ago")).append(" min ago");
        }
        return sb.append(" | id=").append(view.get("quality_time_id")).toString();
    }

    /** Keeps the line well under the platform's 200-character cut, so the id at its end always survives. */
    static final int MAX_LINE_NAMES_LENGTH = 60;

    private static String shortNames(Object names) {
        String text = String.valueOf(names);
        return text.length() <= MAX_LINE_NAMES_LENGTH ? text : text.substring(0, MAX_LINE_NAMES_LENGTH - 3) + "...";
    }

    static String phaseOf(QualityTime qt, Instant now) {
        return switch (qt.getStatus()) {
            case COMPLETED -> "COMPLETED";
            case CANCELLED -> "CANCELLED";
            case MISSED -> "MISSED";
            case SCHEDULED -> {
                if (now.isBefore(qt.getScheduledStart())) {
                    yield "UPCOMING";
                }
                yield now.isBefore(qt.getScheduledEnd()) ? "IN_PROGRESS" : "AWAITING_CONFIRMATION";
            }
        };
    }

    private static int durationMinutes(QualityTime qt) {
        return (int) Duration.between(qt.getScheduledStart(), qt.getScheduledEnd()).toMinutes();
    }

    private static Map<String, Object> sessionView(QualityTime qt, String phase, int minutes, ZoneId zone,
                                                   Instant now, Map<Long, String> childNames) {
        ZonedDateTime localStart = qt.getScheduledStart().atZone(zone);
        ZonedDateTime localEnd = qt.getScheduledEnd().atZone(zone);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("quality_time_id", qt.getId().toString());
        view.put("child_id", qt.getChildId());
        view.put("child_ids", qt.getChildIds());
        view.put("child_name", SessionChildren.hebrew(qt, childNames));
        view.put("status", qt.getStatus().name());
        view.put("phase", phase);
        view.put("local_date", localStart.toLocalDate().toString());
        view.put("weekday", localStart.getDayOfWeek().name());
        view.put("local_start", localStart.format(HH_MM));
        view.put("local_end", localEnd.format(HH_MM));
        view.put("duration_minutes", minutes);
        view.put("start_utc", qt.getScheduledStart().toString());
        view.put("end_utc", qt.getScheduledEnd().toString());
        if ("UPCOMING".equals(phase)) {
            view.put("starts_in_minutes", Duration.between(now, qt.getScheduledStart()).toMinutes());
        }
        if ("AWAITING_CONFIRMATION".equals(phase)) {
            view.put("ended_minutes_ago", Duration.between(qt.getScheduledEnd(), now).toMinutes());
        }
        if (qt.getStatus() == QualityTimeStatus.COMPLETED && qt.getCompletionNotes() != null
                && !qt.getCompletionNotes().isBlank()) {
            view.put("notes", truncate(qt.getCompletionNotes()));
        }
        return view;
    }

    private static Map<String, Object> goalView(Optional<WeeklyGoal> goal) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("exists", goal.isPresent());
        goal.ifPresent(g -> {
            view.put("goal_id", g.getId());
            view.put("status", g.getStatus().name());
            view.put("target_hours", g.getTargetHours());
            view.put("target_minutes", g.getTargetHours() * 60);
            view.put("credited_minutes", g.getActualMinutes());
        });
        return view;
    }

    /** Final result when the week was finalized, otherwise the result so far. */
    private static Boolean goalMet(WeeklyGoal goal) {
        if (goal.getStatus() == WeeklyGoalStatus.COMPLETED) {
            return true;
        }
        if (goal.getStatus() == WeeklyGoalStatus.MISSED) {
            return false;
        }
        return goal.getActualMinutes() >= goal.getTargetHours() * 60;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_NOTES_LENGTH ? text : text.substring(0, MAX_NOTES_LENGTH) + "...";
    }
}
