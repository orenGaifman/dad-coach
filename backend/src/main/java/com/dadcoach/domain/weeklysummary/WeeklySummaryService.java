package com.dadcoach.domain.weeklysummary;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service layer for WeeklySummary entity operations.
 *
 * <p>Business rules:</p>
 * <ul>
 *   <li>Generated every Monday at 08:00 in father's local timezone</li>
 *   <li>Covers the prior Monday-Sunday period</li>
 *   <li>Excludes fathers with status PAUSED, CHURNED, or DELETED (Property 31)</li>
 *   <li>At most one summary per father per week (UNIQUE constraint)</li>
 * </ul>
 */
@Service
@Transactional
public class WeeklySummaryService {

    /** Statuses that are excluded from weekly summary generation (Property 31). */
    public static final Set<FatherStatus> EXCLUDED_STATUSES = EnumSet.of(
            FatherStatus.PAUSED,
            FatherStatus.CHURNED,
            FatherStatus.DELETED
    );

    private final WeeklySummaryRepository weeklySummaryRepository;
    private final FatherRepository fatherRepository;

    public WeeklySummaryService(WeeklySummaryRepository weeklySummaryRepository,
                                 FatherRepository fatherRepository) {
        this.weeklySummaryRepository = weeklySummaryRepository;
        this.fatherRepository = fatherRepository;
    }

    /**
     * Generates a weekly summary for a father covering the prior Monday-Sunday period.
     *
     * @param fatherId the father ID
     * @param content  the summary content text
     * @return the created WeeklySummary entity
     * @throws BusinessRuleViolationException if father's status is excluded or summary already exists
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     */
    public WeeklySummary generateWeeklySummary(Long fatherId, String content) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        validateEligibility(father);

        LocalDate weekStart = computePriorWeekStart(father);
        LocalDate weekEnd = weekStart.plusDays(6);

        if (weeklySummaryRepository.existsByFatherIdAndWeekStart(fatherId, weekStart)) {
            throw new BusinessRuleViolationException(
                    "WEEKLY_SUMMARY_ALREADY_EXISTS",
                    "Weekly summary already exists for father " + fatherId + " week starting " + weekStart
            );
        }

        WeeklySummary summary = new WeeklySummary(father, weekStart, weekEnd, content);
        summary.setEngagementScore(father.getEngagementScore());
        summary.setCoachingStreak(father.getCoachingStreak());
        return weeklySummaryRepository.save(summary);
    }

    /**
     * Generates a weekly summary for a specific week period (for testing/scheduling).
     *
     * @param fatherId  the father ID
     * @param weekStart the Monday start of the week
     * @param content   the summary content text
     * @return the created WeeklySummary entity
     */
    public WeeklySummary generateWeeklySummaryForWeek(Long fatherId, LocalDate weekStart, String content) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        validateEligibility(father);

        LocalDate weekEnd = weekStart.plusDays(6);

        if (weeklySummaryRepository.existsByFatherIdAndWeekStart(fatherId, weekStart)) {
            throw new BusinessRuleViolationException(
                    "WEEKLY_SUMMARY_ALREADY_EXISTS",
                    "Weekly summary already exists for father " + fatherId + " week starting " + weekStart
            );
        }

        WeeklySummary summary = new WeeklySummary(father, weekStart, weekEnd, content);
        summary.setEngagementScore(father.getEngagementScore());
        summary.setCoachingStreak(father.getCoachingStreak());
        return weeklySummaryRepository.save(summary);
    }

    /**
     * Checks whether a father is eligible for weekly summary generation.
     * Returns false for PAUSED, CHURNED, or DELETED fathers (Property 31).
     *
     * @param father the father to check
     * @return true if eligible
     */
    public static boolean isEligibleForWeeklySummary(Father father) {
        return !EXCLUDED_STATUSES.contains(father.getStatus());
    }

    /**
     * Gets all weekly summaries for a father.
     *
     * @param fatherId the father ID
     * @return list of weekly summaries ordered by most recent week first
     */
    @Transactional(readOnly = true)
    public List<WeeklySummary> getWeeklySummaries(Long fatherId) {
        return weeklySummaryRepository.findByFatherIdOrderByWeekStartDesc(fatherId);
    }

    /**
     * Gets a specific weekly summary for a father and week.
     *
     * @param fatherId  the father ID
     * @param weekStart the Monday start of the week
     * @return the weekly summary if it exists
     */
    @Transactional(readOnly = true)
    public Optional<WeeklySummary> getWeeklySummary(Long fatherId, LocalDate weekStart) {
        return weeklySummaryRepository.findByFatherIdAndWeekStart(fatherId, weekStart);
    }

    /**
     * Marks a weekly summary as delivered.
     *
     * @param summaryId the summary ID
     * @return the updated WeeklySummary
     */
    public WeeklySummary markDelivered(Long summaryId) {
        WeeklySummary summary = weeklySummaryRepository.findById(summaryId)
                .orElseThrow(() -> new ResourceNotFoundException("WeeklySummary", summaryId));
        summary.setDeliveredAt(Instant.now());
        return weeklySummaryRepository.save(summary);
    }

    // ─── Internal helpers ────────────────────────────────────────────────

    private void validateEligibility(Father father) {
        if (!isEligibleForWeeklySummary(father)) {
            throw new BusinessRuleViolationException(
                    "WEEKLY_SUMMARY_EXCLUDED_STATUS",
                    "Father " + father.getId() + " with status " + father.getStatus()
                            + " is excluded from weekly summary generation"
            );
        }
    }

    /**
     * Computes the Monday of the prior week relative to the father's current local date.
     * Weekly summaries cover the prior Monday through Sunday.
     */
    private LocalDate computePriorWeekStart(Father father) {
        ZoneId zone = ZoneId.of(father.getTimezone() != null ? father.getTimezone() : "Asia/Jerusalem");
        LocalDate today = LocalDate.now(zone);
        // Find the Monday of the current week, then go back one week
        LocalDate currentWeekMonday = today.with(DayOfWeek.MONDAY);
        return currentWeekMonday.minusWeeks(1);
    }
}
