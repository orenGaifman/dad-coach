package com.dadcoach.scheduling;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.reflection.Reflection;
import com.dadcoach.domain.reflection.ReflectionRepository;
import com.dadcoach.domain.reflection.ReflectionService;
import com.dadcoach.domain.reflection.ReflectionType;
import com.dadcoach.domain.weeklysummary.WeeklySummary;
import com.dadcoach.domain.weeklysummary.WeeklySummaryRepository;
import com.dadcoach.domain.weeklysummary.WeeklySummaryService;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.statemachine.StateMachineEngine;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Scheduling, Inactivity, Reflection, and Weekly Summary logic.
 *
 * Tests three correctness properties from the design document:
 * - Property 9: Inactivity-to-churn transition
 * - Property 31: Weekly summary exclusion filter
 * - Property 35: Daily reflection limit
 */
class SchedulingPropertyTests {

    // ═══════════════════════════════════════════════════════════════════════
    // Property 9: Inactivity-to-Churn Transition
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * **Validates: Requirements 1.4, 10.7**
     *
     * For any ACTIVE Father whose last_interaction_at is more than 21 days before
     * the current time, the inactivity checker should transition their status to CHURNED.
     */
    @Property
    void activeFatherInactiveOver21DaysShouldBeChurned(
            @ForAll("inactiveDaysOver21") int inactiveDays,
            @ForAll("validTimezones") String timezone) {

        // Setup
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        StateMachineEngine mockStateMachine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setStatus(FatherStatus.ACTIVE);
        father.setTimezone(timezone);

        Instant now = Instant.now();
        Instant lastInteraction = now.minus(Duration.ofDays(inactiveDays));
        father.setLastInteractionAt(lastInteraction);

        when(mockFatherRepo.findByStatusAndLastInteractionAtBefore(eq(FatherStatus.ACTIVE), any()))
                .thenReturn(List.of(father));
        when(mockFatherRepo.save(any(Father.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mockStateMachine.<FatherStatus>transition(eq("Father"), eq(1L), any(FatherStatus.class),
                any(FatherStatus.class), anyString()))
                .thenReturn(FatherStatus.CHURNED);

        InactivityService service = new InactivityService(mockFatherRepo, mockStateMachine);

        // Act
        List<Father> churned = service.processChurnedFathers(now);

        // Assert
        if (churned.isEmpty()) {
            throw new AssertionError(
                    "Father with " + inactiveDays + " days of inactivity should be churned but wasn't");
        }
        if (churned.get(0).getStatus() != FatherStatus.CHURNED) {
            throw new AssertionError(
                    "Father status should be CHURNED but is " + churned.get(0).getStatus());
        }
    }

    /**
     * **Validates: Requirements 1.4, 10.7**
     *
     * For any ACTIVE Father whose last_interaction_at is less than 21 days before
     * the current time, the father should NOT be churned.
     */
    @Property
    void activeFatherInactiveUnder21DaysShouldNotBeChurned(
            @ForAll @IntRange(min = 0, max = 20) int inactiveDays) {

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setStatus(FatherStatus.ACTIVE);

        Instant now = Instant.now();
        Instant lastInteraction = now.minus(Duration.ofDays(inactiveDays));
        father.setLastInteractionAt(lastInteraction);

        // The isInactiveFor method should return false for under 21 days
        boolean isInactive = SchedulingServiceImpl.isInactiveFor(father, 21, now);

        if (isInactive) {
            throw new AssertionError(
                    "Father with " + inactiveDays + " days of inactivity should NOT be considered "
                            + "inactive for 21 days");
        }
    }

    /**
     * **Validates: Requirements 1.4, 10.7**
     *
     * The inactivity classification should correctly identify the threshold levels:
     * 0 = active, 3 = first check, 7 = second check, 14 = final check, 21 = churned.
     */
    @Property
    void inactivityClassificationShouldMatchThresholds(
            @ForAll @IntRange(min = 0, max = 60) int inactiveDays) {

        int level = InactivityService.classifyInactivityDays(inactiveDays);

        int expectedLevel;
        if (inactiveDays >= 21) {
            expectedLevel = 21;
        } else if (inactiveDays >= 14) {
            expectedLevel = 14;
        } else if (inactiveDays >= 7) {
            expectedLevel = 7;
        } else if (inactiveDays >= 3) {
            expectedLevel = 3;
        } else {
            expectedLevel = 0;
        }

        if (level != expectedLevel) {
            throw new AssertionError(
                    "Inactivity classification for " + inactiveDays + " days should be "
                            + expectedLevel + " but got " + level);
        }
    }

    /**
     * **Validates: Requirements 1.4, 10.7**
     *
     * Only ACTIVE fathers should be eligible for churning. Non-ACTIVE fathers should be skipped.
     */
    @Property
    void onlyActiveFathersShouldBeEligibleForChurn(
            @ForAll("nonActiveStatuses") FatherStatus status) {

        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        StateMachineEngine mockStateMachine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setStatus(status);

        InactivityService service = new InactivityService(mockFatherRepo, mockStateMachine);
        service.churnFather(father);

        // Verify no state machine transition was attempted
        verify(mockStateMachine, never()).<FatherStatus>transition(anyString(), anyLong(), any(FatherStatus.class), any(FatherStatus.class), anyString());
        // Status should remain unchanged
        if (father.getStatus() != status) {
            throw new AssertionError(
                    "Non-ACTIVE father with status " + status
                            + " should not be modified but status became " + father.getStatus());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Property 31: Weekly Summary Exclusion Filter
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * **Validates: Requirements 13.7**
     *
     * For any Father with status in {PAUSED, CHURNED, DELETED}, no weekly summary
     * should be generated. The service should reject the attempt.
     */
    @Property
    void weeklySummaryShouldBeRejectedForExcludedStatuses(
            @ForAll("excludedStatuses") FatherStatus excludedStatus,
            @ForAll("validTimezones") String timezone) {

        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        WeeklySummaryRepository mockSummaryRepo = mock(WeeklySummaryRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setStatus(excludedStatus);
        father.setTimezone(timezone);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));

        WeeklySummaryService service = new WeeklySummaryService(mockSummaryRepo, mockFatherRepo);

        try {
            service.generateWeeklySummaryForWeek(1L, LocalDate.of(2024, 6, 10), "Test summary");
            throw new AssertionError(
                    "Weekly summary generation should be rejected for father with status "
                            + excludedStatus + " but was accepted");
        } catch (BusinessRuleViolationException e) {
            if (!e.getMessage().contains("WEEKLY_SUMMARY_EXCLUDED_STATUS")) {
                throw new AssertionError(
                        "Expected WEEKLY_SUMMARY_EXCLUDED_STATUS error but got: " + e.getMessage());
            }
        }
    }

    /**
     * **Validates: Requirements 13.7**
     *
     * For any Father with status ACTIVE or REACTIVATED, weekly summary generation
     * should be allowed (not rejected by the exclusion filter).
     */
    @Property
    void weeklySummaryShouldBeAllowedForEligibleStatuses(
            @ForAll("eligibleStatuses") FatherStatus eligibleStatus,
            @ForAll("validTimezones") String timezone) {

        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        WeeklySummaryRepository mockSummaryRepo = mock(WeeklySummaryRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setStatus(eligibleStatus);
        father.setTimezone(timezone);
        father.setEngagementScore(50);
        father.setCoachingStreak(5);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        when(mockSummaryRepo.existsByFatherIdAndWeekStart(eq(1L), any())).thenReturn(false);
        when(mockSummaryRepo.save(any(WeeklySummary.class))).thenAnswer(inv -> {
            WeeklySummary ws = inv.getArgument(0);
            ws.setId(100L);
            return ws;
        });

        WeeklySummaryService service = new WeeklySummaryService(mockSummaryRepo, mockFatherRepo);

        WeeklySummary result = service.generateWeeklySummaryForWeek(1L,
                LocalDate.of(2024, 6, 10), "Test summary");

        if (result == null) {
            throw new AssertionError(
                    "Weekly summary should be generated for father with status "
                            + eligibleStatus + " but was null");
        }
    }

    /**
     * **Validates: Requirements 13.7**
     *
     * The isEligibleForWeeklySummary check should correctly identify excluded statuses.
     */
    @Property
    void eligibilityCheckShouldMatchExclusionRules(
            @ForAll("allStatuses") FatherStatus status) {

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setStatus(status);

        boolean eligible = WeeklySummaryService.isEligibleForWeeklySummary(father);

        boolean expectedEligible = !(status == FatherStatus.PAUSED
                || status == FatherStatus.CHURNED
                || status == FatherStatus.DELETED);

        if (eligible != expectedEligible) {
            throw new AssertionError(
                    "isEligibleForWeeklySummary for status " + status
                            + " should be " + expectedEligible + " but got " + eligible);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Property 35: Daily Reflection Limit
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * **Validates: Requirements 18.4**
     *
     * For any Father on any calendar day, at most 1 reflection should be allowed.
     * Attempting to create a second reflection on the same day should be rejected.
     */
    @Property
    void secondReflectionOnSameDayShouldBeRejected(
            @ForAll("reflectionTypes") ReflectionType type,
            @ForAll("validTimezones") String timezone) {

        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ReflectionRepository mockReflectionRepo = mock(ReflectionRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setTimezone(timezone);
        father.setStatus(FatherStatus.ACTIVE);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        // Simulate one reflection already exists today
        when(mockReflectionRepo.countByFatherIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(1);

        ReflectionService service = new ReflectionService(mockReflectionRepo, mockFatherRepo);

        try {
            service.createReflection(1L, type);
            throw new AssertionError(
                    "Second reflection on the same day should be rejected for type " + type
                            + " but was accepted");
        } catch (BusinessRuleViolationException e) {
            if (!e.getMessage().contains("DAILY_REFLECTION_LIMIT_EXCEEDED")) {
                throw new AssertionError(
                        "Expected DAILY_REFLECTION_LIMIT_EXCEEDED but got: " + e.getMessage());
            }
        }
    }

    /**
     * **Validates: Requirements 18.4**
     *
     * For any Father with no reflections today, creating a reflection should succeed.
     */
    @Property
    void firstReflectionOnDayShouldBeAllowed(
            @ForAll("reflectionTypes") ReflectionType type,
            @ForAll("validTimezones") String timezone) {

        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ReflectionRepository mockReflectionRepo = mock(ReflectionRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setTimezone(timezone);
        father.setStatus(FatherStatus.ACTIVE);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        // No reflections exist today
        when(mockReflectionRepo.countByFatherIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(0);
        when(mockReflectionRepo.save(any(Reflection.class))).thenAnswer(inv -> {
            Reflection r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        ReflectionService service = new ReflectionService(mockReflectionRepo, mockFatherRepo);

        Reflection result = service.createReflection(1L, type);

        if (result == null) {
            throw new AssertionError(
                    "First reflection of the day should be allowed for type " + type + " but got null");
        }
    }

    /**
     * **Validates: Requirements 18.4**
     *
     * The daily reflection limit applies regardless of reflection type —
     * if a MISSION reflection was already created today, a WEEKLY reflection
     * should also be rejected.
     */
    @Property
    void dailyReflectionLimitAppliesToAllTypes(
            @ForAll("reflectionTypes") ReflectionType firstType,
            @ForAll("reflectionTypes") ReflectionType secondType,
            @ForAll("validTimezones") String timezone) {

        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ReflectionRepository mockReflectionRepo = mock(ReflectionRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setTimezone(timezone);
        father.setStatus(FatherStatus.ACTIVE);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        // One reflection already exists (regardless of type)
        when(mockReflectionRepo.countByFatherIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(1);

        ReflectionService service = new ReflectionService(mockReflectionRepo, mockFatherRepo);

        try {
            service.createReflection(1L, secondType);
            throw new AssertionError(
                    "After " + firstType + " reflection, attempting " + secondType
                            + " should be rejected but was accepted");
        } catch (BusinessRuleViolationException e) {
            // Expected behavior
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scheduling Service Properties (timezone-aware dispatch)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * **Validates: Requirements 10.16**
     *
     * Fathers should be identified for weekly summary delivery when it is Monday 08:00
     * in their local timezone.
     */
    @Property
    void fatherShouldBeDueForWeeklySummaryOnMondayAt0800(
            @ForAll("validTimezones") String timezone) {

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setTimezone(timezone);

        // Create an Instant that represents Monday at 08:00 in the father's timezone
        ZoneId zone = ZoneId.of(timezone);
        LocalDate monday = LocalDate.of(2024, 6, 17); // A Monday
        ZonedDateTime mondayAt8 = ZonedDateTime.of(monday, LocalTime.of(8, 0), zone);
        Instant now = mondayAt8.toInstant();

        boolean isDue = SchedulingServiceImpl.isDueForWeeklySummary(father, now);

        if (!isDue) {
            throw new AssertionError(
                    "Father in timezone " + timezone + " should be due for weekly summary "
                            + "at Monday 08:00 local time but isDueForWeeklySummary returned false");
        }
    }

    /**
     * **Validates: Requirements 10.16**
     *
     * Fathers should NOT be due for weekly summary on non-Monday days.
     */
    @Property
    void fatherShouldNotBeDueForWeeklySummaryOnNonMonday(
            @ForAll("nonMondayDays") DayOfWeek dayOfWeek,
            @ForAll("validTimezones") String timezone) {

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setTimezone(timezone);

        ZoneId zone = ZoneId.of(timezone);
        // Find a date with the given day of week
        LocalDate date = LocalDate.of(2024, 6, 18); // Tuesday
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        ZonedDateTime at8 = ZonedDateTime.of(date, LocalTime.of(8, 0), zone);
        Instant now = at8.toInstant();

        boolean isDue = SchedulingServiceImpl.isDueForWeeklySummary(father, now);

        if (isDue) {
            throw new AssertionError(
                    "Father should NOT be due for weekly summary on " + dayOfWeek
                            + " but isDueForWeeklySummary returned true");
        }
    }

    /**
     * **Validates: Requirements 5.1**
     *
     * Fathers should be due for daily coaching when it is their preferred time in their timezone.
     */
    @Property
    void fatherShouldBeDueForDailyCoachingAtPreferredTime(
            @ForAll("validTimezones") String timezone,
            @ForAll @IntRange(min = 7, max = 20) int preferredHour) {

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setTimezone(timezone);
        father.setPreferredCoachingTime(LocalTime.of(preferredHour, 0));

        ZoneId zone = ZoneId.of(timezone);
        LocalDate date = LocalDate.of(2024, 6, 15);
        ZonedDateTime atPreferred = ZonedDateTime.of(date, LocalTime.of(preferredHour, 0), zone);
        Instant now = atPreferred.toInstant();

        boolean isDue = SchedulingServiceImpl.isDueForDailyCoaching(father, now);

        if (!isDue) {
            throw new AssertionError(
                    "Father with preferred time " + preferredHour + ":00 in " + timezone
                            + " should be due for daily coaching but isDueForDailyCoaching returned false");
        }
    }

    // ─── Providers ───────────────────────────────────────────────────────

    @Provide
    Arbitrary<Integer> inactiveDaysOver21() {
        return Arbitraries.integers().between(22, 365);
    }

    @Provide
    Arbitrary<String> validTimezones() {
        return Arbitraries.of(
                "Asia/Jerusalem", "America/New_York", "Europe/London",
                "Asia/Tokyo", "Australia/Sydney", "US/Pacific"
        );
    }

    @Provide
    Arbitrary<FatherStatus> excludedStatuses() {
        return Arbitraries.of(FatherStatus.PAUSED, FatherStatus.CHURNED, FatherStatus.DELETED);
    }

    @Provide
    Arbitrary<FatherStatus> eligibleStatuses() {
        return Arbitraries.of(FatherStatus.ACTIVE, FatherStatus.REACTIVATED,
                FatherStatus.NOT_STARTED, FatherStatus.ONBOARDING);
    }

    @Provide
    Arbitrary<FatherStatus> nonActiveStatuses() {
        return Arbitraries.of(FatherStatus.PAUSED, FatherStatus.CHURNED,
                FatherStatus.DELETED, FatherStatus.NOT_STARTED, FatherStatus.ONBOARDING,
                FatherStatus.REACTIVATED);
    }

    @Provide
    Arbitrary<FatherStatus> allStatuses() {
        return Arbitraries.of(FatherStatus.values());
    }

    @Provide
    Arbitrary<ReflectionType> reflectionTypes() {
        return Arbitraries.of(ReflectionType.values());
    }

    @Provide
    Arbitrary<DayOfWeek> nonMondayDays() {
        return Arbitraries.of(
                DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        );
    }
}
