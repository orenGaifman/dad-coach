package com.dadcoach.scheduling;

import com.dadcoach.domain.conversation.Conversation;
import com.dadcoach.domain.conversation.ConversationRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SchedulingService} providing timezone-aware job dispatch.
 *
 * <p>This service handles the core scheduling logic for:</p>
 * <ul>
 *   <li>Daily coaching delivery at each father's preferred time in their timezone</li>
 *   <li>Weekly summary generation (Monday 08:00 local time)</li>
 *   <li>Inactivity detection at configurable thresholds (3, 7, 14, 21 days)</li>
 *   <li>Conversation expiration detection</li>
 * </ul>
 *
 * <p>The design uses a "poll and filter" approach: the scheduler runs every minute,
 * queries all eligible fathers, and filters based on their local time. This is
 * sufficient for a single-instance monolith deployment.</p>
 */
@Service
@Transactional(readOnly = true)
public class SchedulingServiceImpl implements SchedulingService {

    private final FatherRepository fatherRepository;
    private final ConversationRepository conversationRepository;

    public SchedulingServiceImpl(FatherRepository fatherRepository,
                                  ConversationRepository conversationRepository) {
        this.fatherRepository = fatherRepository;
        this.conversationRepository = conversationRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Logic:</p>
     * <ol>
     *   <li>Get all ACTIVE fathers</li>
     *   <li>For each father, compute their current local time from their timezone</li>
     *   <li>Return fathers whose local hour:minute matches their preferred_coaching_time</li>
     * </ol>
     */
    @Override
    public List<Father> findFathersDueForDailyCoaching(Instant now) {
        List<Father> activeFathers = fatherRepository.findByStatus(FatherStatus.ACTIVE);
        return activeFathers.stream()
                .filter(father -> isDueForDailyCoaching(father, now))
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Logic:</p>
     * <ol>
     *   <li>Get all ACTIVE fathers</li>
     *   <li>For each father, compute their current local time</li>
     *   <li>Return fathers where it is Monday at 08:00 in their timezone</li>
     * </ol>
     *
     * <p>Excludes PAUSED, CHURNED, and DELETED fathers per Requirement 10.16 / Property 31.</p>
     */
    @Override
    public List<Father> findFathersDueForWeeklySummary(Instant now) {
        List<Father> activeFathers = fatherRepository.findByStatus(FatherStatus.ACTIVE);
        return activeFathers.stream()
                .filter(father -> isDueForWeeklySummary(father, now))
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Finds ACTIVE fathers whose last_interaction_at is before (now - inactiveDays).
     * Fathers with null last_interaction_at are considered inactive since creation.</p>
     */
    @Override
    public List<Father> findInactiveFathers(int inactiveDays) {
        Instant threshold = Instant.now().minus(Duration.ofDays(inactiveDays));
        return fatherRepository.findByStatusAndLastInteractionAtBefore(FatherStatus.ACTIVE, threshold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Conversation> findExpiredConversations(Instant now) {
        return conversationRepository.findExpired(now);
    }

    // ─── Internal helpers ────────────────────────────────────────────────

    /**
     * Checks if a father is due for daily coaching at the given instant.
     * Compares the father's local time (hour and minute) with their preferred coaching time.
     */
    static boolean isDueForDailyCoaching(Father father, Instant now) {
        if (father.getPreferredCoachingTime() == null || father.getTimezone() == null) {
            return false;
        }
        try {
            ZoneId zone = ZoneId.of(father.getTimezone());
            LocalTime localNow = now.atZone(zone).toLocalTime();
            LocalTime preferred = father.getPreferredCoachingTime();
            // Match on hour and minute (ignoring seconds)
            return localNow.getHour() == preferred.getHour()
                    && localNow.getMinute() == preferred.getMinute();
        } catch (DateTimeException e) {
            // Invalid timezone — skip this father
            return false;
        }
    }

    /**
     * Checks if a father is due for weekly summary at the given instant.
     * Weekly summaries are delivered on Monday at 08:00 in the father's local timezone.
     */
    static boolean isDueForWeeklySummary(Father father, Instant now) {
        if (father.getTimezone() == null) {
            return false;
        }
        try {
            ZoneId zone = ZoneId.of(father.getTimezone());
            ZonedDateTime localNow = now.atZone(zone);
            return localNow.getDayOfWeek() == DayOfWeek.MONDAY
                    && localNow.getHour() == 8
                    && localNow.getMinute() == 0;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /**
     * Checks if a father is inactive for at least the given number of days relative to a reference time.
     * A father is considered inactive if last_interaction_at is before (referenceTime - inactiveDays).
     *
     * @param father       the father to check
     * @param inactiveDays the threshold in days
     * @param referenceTime the current time reference
     * @return true if the father has been inactive for at least inactiveDays
     */
    public static boolean isInactiveFor(Father father, int inactiveDays, Instant referenceTime) {
        if (father.getLastInteractionAt() == null) {
            // If no interaction recorded, consider inactive since creation
            return father.getCreatedAt() != null
                    && father.getCreatedAt().plus(Duration.ofDays(inactiveDays)).isBefore(referenceTime);
        }
        Instant threshold = referenceTime.minus(Duration.ofDays(inactiveDays));
        return father.getLastInteractionAt().isBefore(threshold);
    }
}
