package com.dadcoach.replies;

import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What the coach already said today, noted in code (D-036; production review 2: today's session repeated in 12 of 19
 * replies - "היי אורן 😊 מזכיר שהיום ב-17:00..." 34 minutes after the morning reminder - and the missing goal pushed in
 * 4 replies in 7 minutes). The weekly plan shows it ({@code sessions_mentioned_today}, {@code goal.asked_today}) and the
 * rules say: a session named today is not named again unless he asks; a missing goal is raised at most once a day.
 * Best effort: a failure here never stops a message.
 */
@Component
public class CoachMentions {

    private static final Logger log = LoggerFactory.getLogger(CoachMentions.class);

    private final QualityTimeRepository sessions;
    private final WeeklyGoalRepository goals;
    private final WeeklyGoalService goalService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CoachMentions(QualityTimeRepository sessions, WeeklyGoalRepository goals, WeeklyGoalService goalService,
                         JdbcTemplate jdbc, Clock clock) {
        this.sessions = sessions;
        this.goals = goals;
        this.goalService = goalService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Sessions a timer's message named (the hour-before names no time). */
    public void sessionsNamed(Father father, java.util.List<java.util.UUID> ids) {
        try {
            LocalDate today = clock.instant().atZone(goalService.zoneFor(father)).toLocalDate();
            for (java.util.UUID id : ids) {
                jdbc.update("UPDATE quality_time SET mentioned_on = ? WHERE id = ? AND father_id = ?", today, id, father.getId());
            }
        } catch (RuntimeException e) {
            log.atWarn().setMessage("coach.mentions.failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    /** A message that went out to him: today's sessions it names by their time, and a missing goal it raises. */
    public void sent(Father father, String text) {
        if (father == null || text == null || text.isBlank()) {
            return;
        }
        try {
            ZoneId zone = goalService.zoneFor(father);
            Instant now = clock.instant();
            LocalDate today = now.atZone(zone).toLocalDate();
            for (QualityTime qt : sessions.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.SCHEDULED)) {
                boolean laterToday = qt.getScheduledEnd().isAfter(now)
                        && qt.getScheduledStart().atZone(zone).toLocalDate().equals(today);
                if (laterToday && text.contains(HebrewWhen.time(qt.getScheduledStart().atZone(zone).toLocalTime()))) {
                    jdbc.update("UPDATE quality_time SET mentioned_on = ? WHERE id = ?", today, qt.getId());
                }
            }
            boolean noGoal = goals.findByFatherIdAndWeekStartDate(father.getId(), goalService.weekStartFor(father, now)).isEmpty();
            if (noGoal && text.contains("יעד")) {
                jdbc.update("UPDATE father SET goal_asked_on = ? WHERE id = ?", today, father.getId());
            }
        } catch (RuntimeException e) {
            log.atWarn().setMessage("coach.mentions.failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }
}
