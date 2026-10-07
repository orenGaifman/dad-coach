package com.dadcoach.web.father;

import java.util.List;

/**
 * The father's "my week" (בית). Built ONLY from the weekly plan the AI sees (WeeklyPlanContextBuilder) plus his
 * profile - so the home and the coach never disagree. The admin's view-as returns exactly this object.
 */
public record HomeView(
        String name,
        String timezone,
        String today,
        Week week,
        Goal goal,
        Coverage coverage,
        SessionView nextSession,
        List<SessionView> sessionsThisWeek,
        List<SessionView> awaitingConfirmation,
        Progress progress,
        List<ChildName> children,
        boolean calendarConnected,
        String coachWhatsApp) {

    public record Week(String start, String end, int daysLeft) {
    }

    public record Goal(boolean exists, Integer targetHours, Integer targetMinutes, Integer creditedMinutes, String status) {
    }

    public record Coverage(Integer targetMinutes, int completedMinutes, int plannedMinutes, Integer uncoveredMinutes,
                           Boolean covered, int awaitingMinutes) {
    }

    public record Progress(String belt, String nextBelt, int totalCompleted, int beltStartsAt, Integer nextBeltAt,
                           Integer sessionsToNextBelt, int percentToNextBelt, int streakWeeks, int sessionStreak) {
    }

    public record ChildName(Long id, String name) {
    }
}
