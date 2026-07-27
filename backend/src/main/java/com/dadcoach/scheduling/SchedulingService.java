package com.dadcoach.scheduling;

import com.dadcoach.domain.conversation.Conversation;
import com.dadcoach.domain.father.Father;

import java.time.Instant;
import java.util.List;

/**
 * Timezone-aware job dispatcher for daily coaching, weekly summaries, and maintenance.
 *
 * <p>This service is responsible for finding fathers and conversations that need
 * attention based on the current time, respecting each father's configured timezone.</p>
 */
public interface SchedulingService {

    /**
     * Find all fathers whose daily coaching time is now in their timezone.
     * Matches fathers whose preferred_coaching_time equals the current local hour:minute
     * in their configured timezone.
     *
     * @param now the current UTC instant
     * @return list of fathers due for daily coaching delivery
     */
    List<Father> findFathersDueForDailyCoaching(Instant now);

    /**
     * Find all fathers due for weekly summary (Monday 08:00 local).
     * Only returns ACTIVE fathers on Mondays at 08:00 in their timezone.
     * Excludes fathers with status PAUSED, CHURNED, or DELETED.
     *
     * @param now the current UTC instant
     * @return list of fathers due for weekly summary generation
     */
    List<Father> findFathersDueForWeeklySummary(Instant now);

    /**
     * Find fathers whose inactivity threshold has been crossed.
     * Returns ACTIVE fathers whose last_interaction_at is older than the given number of days.
     *
     * @param inactiveDays the number of days of inactivity to check (e.g., 3, 7, 14, 21)
     * @return list of fathers inactive for at least the specified number of days
     */
    List<Father> findInactiveFathers(int inactiveDays);

    /**
     * Find expired conversations (ACTIVE conversations past their expiration time).
     *
     * @param now the current UTC instant
     * @return list of active conversations that have expired
     */
    List<Conversation> findExpiredConversations(Instant now);
}
