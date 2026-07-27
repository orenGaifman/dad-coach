package com.dadcoach.coaching;

import com.dadcoach.father.CoachingPhase;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stateless computation service for engagement score and coaching streak calculations.
 *
 * Engagement formula (Req 9.2):
 * min(100, messages_sent_7d × 2 + missions_completed_7d × 15 + reflections_completed_7d × 10 + min(streak_days, 10))
 *
 * Coaching streak (Req 9.1):
 * Consecutive calendar days (in father's timezone) where the father sent at least one message
 * OR completed at least one mission.
 *
 * Coaching phase (Req 4.2, 4.12):
 * FOUNDATION (days 1-14), BUILDING (days 15-42), DEEPENING (days 43-84), MASTERY (85+).
 * Phase transitions are forward-only.
 */
@Service
public class EngagementService {

    /**
     * Computes the engagement score using the formula:
     * min(100, messages×2 + missions×15 + reflections×10 + min(streak, 10))
     *
     * @param messagesSent7d       number of messages sent in the last 7 days (non-negative)
     * @param missionsCompleted7d  number of missions completed in the last 7 days (non-negative)
     * @param reflectionsCompleted7d number of reflections completed in the last 7 days (non-negative)
     * @param streakDays           current coaching streak in days (non-negative)
     * @return engagement score clamped to [0, 100]
     */
    public int computeEngagementScore(int messagesSent7d, int missionsCompleted7d,
                                      int reflectionsCompleted7d, int streakDays) {
        if (messagesSent7d < 0 || missionsCompleted7d < 0 || reflectionsCompleted7d < 0 || streakDays < 0) {
            throw new IllegalArgumentException("All input values must be non-negative");
        }

        int raw = (messagesSent7d * 2)
                + (missionsCompleted7d * 15)
                + (reflectionsCompleted7d * 10)
                + Math.min(streakDays, 10);

        return Math.min(100, raw);
    }

    /**
     * Computes the coaching streak from a sequence of daily interaction flags.
     * The streak is the count of consecutive true values ending at the current day
     * (last element in the list).
     *
     * @param dailyInteractions list of booleans where each element represents whether
     *                          an interaction occurred on that calendar day, ordered
     *                          from oldest to most recent (current day is last)
     * @return the coaching streak (count of consecutive true values from the end)
     */
    public int computeCoachingStreak(List<Boolean> dailyInteractions) {
        if (dailyInteractions == null || dailyInteractions.isEmpty()) {
            return 0;
        }

        int streak = 0;
        for (int i = dailyInteractions.size() - 1; i >= 0; i--) {
            if (Boolean.TRUE.equals(dailyInteractions.get(i))) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * Computes the coaching phase based on days since activation.
     * Phase transitions are forward-only (cannot regress).
     *
     * @param daysSinceActivation number of days since the father's activation (must be >= 1)
     * @param currentPhase        the father's current coaching phase (for forward-only enforcement)
     * @return the computed coaching phase (never earlier than currentPhase)
     */
    public CoachingPhase computeCoachingPhase(int daysSinceActivation, CoachingPhase currentPhase) {
        if (daysSinceActivation < 1) {
            throw new IllegalArgumentException("Days since activation must be at least 1, got: " + daysSinceActivation);
        }

        CoachingPhase computedPhase = CoachingPhase.forDay(daysSinceActivation);

        // Forward-only: never regress to an earlier phase
        if (currentPhase != null && computedPhase.ordinal() < currentPhase.ordinal()) {
            return currentPhase;
        }

        return computedPhase;
    }
}
