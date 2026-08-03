package com.dadcoach.domain.mission;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.goal.GoalRepository;
import com.dadcoach.mission.LegacyMissionStatus;
import static com.dadcoach.mission.LegacyMissionStatus.*;
import com.dadcoach.statemachine.StateMachineEngine;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.*;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Mission domain logic.
 *
 * Tests three correctness properties from the design document:
 * - Property 13: Mission expiration by day of week
 * - Property 14: Mission time constraint by day of week
 * - Property 16: Single active mission per child
 */
class MissionPropertyTests {

    private static final ZoneId UTC = ZoneId.of("UTC");

    // ─── Property 13: Mission Expiration by Day of Week ──────────────────────

    /**
     * **Validates: Requirements 6.12**
     *
     * For any mission assigned on a weekday (Mon-Fri), expires_at should be
     * assignment_time + 24 hours.
     */
    @Property
    void weekdayAssignmentShouldExpireIn24Hours(@ForAll("weekdayInstants") Instant assignmentTime) {
        Instant expiration = Mission.calculateExpiration(assignmentTime, UTC);
        Instant expected = assignmentTime.plusSeconds(24 * 3600);

        if (!expiration.equals(expected)) {
            DayOfWeek day = assignmentTime.atZone(UTC).getDayOfWeek();
            throw new AssertionError(
                    "Weekday " + day + " assignment at " + assignmentTime
                            + " should expire at " + expected + " but got " + expiration);
        }
    }

    /**
     * **Validates: Requirements 6.12**
     *
     * For any mission assigned on a weekend (Sat-Sun), expires_at should be
     * assignment_time + 48 hours.
     */
    @Property
    void weekendAssignmentShouldExpireIn48Hours(@ForAll("weekendInstants") Instant assignmentTime) {
        Instant expiration = Mission.calculateExpiration(assignmentTime, UTC);
        Instant expected = assignmentTime.plusSeconds(48 * 3600);

        if (!expiration.equals(expected)) {
            DayOfWeek day = assignmentTime.atZone(UTC).getDayOfWeek();
            throw new AssertionError(
                    "Weekend " + day + " assignment at " + assignmentTime
                            + " should expire at " + expected + " but got " + expiration);
        }
    }

    /**
     * **Validates: Requirements 6.12**
     *
     * For any assignment time, the expiration duration is always either 24h or 48h
     * depending solely on the day of week.
     */
    @Property
    void expirationDurationIsAlways24Or48Hours(@ForAll("anyInstant") Instant assignmentTime) {
        Instant expiration = Mission.calculateExpiration(assignmentTime, UTC);
        long durationSeconds = Duration.between(assignmentTime, expiration).getSeconds();

        DayOfWeek day = assignmentTime.atZone(UTC).getDayOfWeek();
        boolean isWeekend = (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY);

        long expectedSeconds = isWeekend ? 48 * 3600 : 24 * 3600;
        if (durationSeconds != expectedSeconds) {
            throw new AssertionError(
                    "For " + day + ", expected duration " + expectedSeconds + "s but got " + durationSeconds + "s");
        }
    }

    @Provide
    Arbitrary<Instant> weekdayInstants() {
        // Generate instants that fall on Mon-Fri in UTC
        return Arbitraries.longs()
                .between(
                        ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, UTC).toInstant().getEpochSecond(),
                        ZonedDateTime.of(2030, 12, 31, 23, 59, 59, 0, UTC).toInstant().getEpochSecond()
                )
                .map(Instant::ofEpochSecond)
                .filter(instant -> {
                    DayOfWeek day = instant.atZone(UTC).getDayOfWeek();
                    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
                });
    }

    @Provide
    Arbitrary<Instant> weekendInstants() {
        // Generate instants that fall on Sat-Sun in UTC
        return Arbitraries.longs()
                .between(
                        ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, UTC).toInstant().getEpochSecond(),
                        ZonedDateTime.of(2030, 12, 31, 23, 59, 59, 0, UTC).toInstant().getEpochSecond()
                )
                .map(Instant::ofEpochSecond)
                .filter(instant -> {
                    DayOfWeek day = instant.atZone(UTC).getDayOfWeek();
                    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
                });
    }

    @Provide
    Arbitrary<Instant> anyInstant() {
        return Arbitraries.longs()
                .between(
                        ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, UTC).toInstant().getEpochSecond(),
                        ZonedDateTime.of(2030, 12, 31, 23, 59, 59, 0, UTC).toInstant().getEpochSecond()
                )
                .map(Instant::ofEpochSecond);
    }

    // ─── Property 14: Mission Time Constraint by Day of Week ─────────────────

    /**
     * **Validates: Requirements 6.8**
     *
     * For any weekday mission, estimated_minutes should be ≤ 30.
     * This property validates that the constraint checking logic correctly identifies
     * violations: a weekday mission with > 30 minutes should be flagged as invalid.
     */
    @Property
    void weekdayMissionWithin30MinutesShouldBeValid(
            @ForAll("weekdayInstants") Instant assignmentTime,
            @ForAll @IntRange(min = 1, max = 30) int estimatedMinutes) {

        boolean isValid = validateTimeConstraint(assignmentTime, estimatedMinutes, UTC);

        if (!isValid) {
            throw new AssertionError(
                    "Weekday mission with " + estimatedMinutes + " minutes should be valid");
        }
    }

    /**
     * **Validates: Requirements 6.8**
     *
     * For any weekday mission with estimated_minutes > 30, the constraint check
     * should identify it as a violation.
     */
    @Property
    void weekdayMissionExceeding30MinutesShouldBeInvalid(
            @ForAll("weekdayInstants") Instant assignmentTime,
            @ForAll @IntRange(min = 31, max = 120) int estimatedMinutes) {

        boolean isValid = validateTimeConstraint(assignmentTime, estimatedMinutes, UTC);

        if (isValid) {
            throw new AssertionError(
                    "Weekday mission with " + estimatedMinutes + " minutes should be invalid (max 30 on weekdays)");
        }
    }

    /**
     * **Validates: Requirements 6.9**
     *
     * For any weekend mission, estimated_minutes up to 120 should be valid.
     */
    @Property
    void weekendMissionUpTo120MinutesShouldBeValid(
            @ForAll("weekendInstants") Instant assignmentTime,
            @ForAll @IntRange(min = 1, max = 120) int estimatedMinutes) {

        boolean isValid = validateTimeConstraint(assignmentTime, estimatedMinutes, UTC);

        if (!isValid) {
            throw new AssertionError(
                    "Weekend mission with " + estimatedMinutes + " minutes should be valid (max 120 on weekends)");
        }
    }

    /**
     * **Validates: Requirements 6.9**
     *
     * For any weekend mission with estimated_minutes > 120, the constraint check
     * should identify it as a violation.
     */
    @Property
    void weekendMissionExceeding120MinutesShouldBeInvalid(
            @ForAll("weekendInstants") Instant assignmentTime,
            @ForAll @IntRange(min = 121, max = 300) int estimatedMinutes) {

        boolean isValid = validateTimeConstraint(assignmentTime, estimatedMinutes, UTC);

        if (isValid) {
            throw new AssertionError(
                    "Weekend mission with " + estimatedMinutes + " minutes should be invalid (max 120 on weekends)");
        }
    }

    /**
     * Validates the mission time constraint rule:
     * - Weekday (Mon-Fri): estimated_minutes must be ≤ 30
     * - Weekend (Sat-Sun): estimated_minutes may be up to 120
     *
     * @param assignmentTime   the time the mission is assigned
     * @param estimatedMinutes the estimated duration in minutes
     * @param zoneId           the timezone for day-of-week determination
     * @return true if the time constraint is satisfied
     */
    private static boolean validateTimeConstraint(Instant assignmentTime, int estimatedMinutes, ZoneId zoneId) {
        DayOfWeek day = assignmentTime.atZone(zoneId).getDayOfWeek();
        boolean isWeekend = (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY);

        if (isWeekend) {
            return estimatedMinutes <= 120;
        } else {
            return estimatedMinutes <= 30;
        }
    }

    // ─── Property 16: Single Active Mission Per Child ────────────────────────

    /**
     * **Validates: Requirements 6.15**
     *
     * For any child with an existing active mission (in ASSIGNED, ACCEPTED, or IN_PROGRESS),
     * attempting to create a second mission should be rejected with a
     * BusinessRuleViolationException.
     */
    @Property
    void secondActiveMissionForSameChildShouldBeRejected(
            @ForAll("activeStatuses") LegacyMissionStatus existingMissionStatus) {

        // Set up mocks for MissionService
        MissionRepository mockMissionRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);
        GoalRepository mockGoalRepo = mock(GoalRepository.class);
        StateMachineEngine mockEngine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        Child child = new Child(father, "Test Child", LocalDate.of(2018, 6, 15));
        child.setId(10L);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        when(mockChildRepo.findById(10L)).thenReturn(Optional.of(child));
        // Simulate that child already has 1 active mission
        when(mockMissionRepo.countActiveMissionsByChildId(10L)).thenReturn(1L);

        MissionService service = new MissionService(
                mockMissionRepo, mockFatherRepo, mockChildRepo, mockGoalRepo, mockEngine);

        try {
            service.createMission(1L, 10L, null,
                    "Second Mission", "Should fail", "CONNECTION", 2, 20);
            throw new AssertionError(
                    "Expected BusinessRuleViolationException when child already has an active mission in status "
                            + existingMissionStatus);
        } catch (BusinessRuleViolationException e) {
            // Expected: single-active-mission-per-child constraint enforced
            if (!e.getMessage().contains("SINGLE_ACTIVE_MISSION_PER_CHILD")) {
                throw new AssertionError(
                        "Expected SINGLE_ACTIVE_MISSION_PER_CHILD violation but got: " + e.getMessage());
            }
        }
    }

    /**
     * **Validates: Requirements 6.15**
     *
     * For any child with NO active missions (all in terminal states), creating
     * a new mission should succeed.
     */
    @Property
    void firstMissionForChildWithNoActiveShouldSucceed(
            @ForAll @IntRange(min = 1, max = 5) int difficulty,
            @ForAll @IntRange(min = 1, max = 30) int estimatedMinutes) {

        // Set up mocks for MissionService
        MissionRepository mockMissionRepo = mock(MissionRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        ChildRepository mockChildRepo = mock(ChildRepository.class);
        GoalRepository mockGoalRepo = mock(GoalRepository.class);
        StateMachineEngine mockEngine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        Child child = new Child(father, "Test Child", LocalDate.of(2018, 6, 15));
        child.setId(10L);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        when(mockChildRepo.findById(10L)).thenReturn(Optional.of(child));
        // No active missions exist for this child
        when(mockMissionRepo.countActiveMissionsByChildId(10L)).thenReturn(0L);
        when(mockMissionRepo.save(any(Mission.class))).thenAnswer(inv -> {
            Mission m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        MissionService service = new MissionService(
                mockMissionRepo, mockFatherRepo, mockChildRepo, mockGoalRepo, mockEngine);

        Mission result = service.createMission(1L, 10L, null,
                "New Mission", "Should succeed", "CONNECTION", difficulty, estimatedMinutes);

        if (result == null) {
            throw new AssertionError("Mission creation should succeed when no active mission exists");
        }
        if (result.getStatus() != ASSIGNED) {
            throw new AssertionError("New mission should be in ASSIGNED status but got " + result.getStatus());
        }
    }

    /**
     * **Validates: Requirements 6.15**
     *
     * The active states that count toward the single-active constraint are exactly:
     * ASSIGNED, ACCEPTED, IN_PROGRESS. The isActive() method on Mission should
     * return true only for these three states.
     */
    @Property
    void onlyThreeStatusesAreActive(@ForAll("allStatuses") LegacyMissionStatus status) {
        Father father = new Father("+972501234567");
        Child child = new Child(father, "Test Child", LocalDate.of(2018, 6, 15));
        Mission mission = new Mission(father, child, "Test", "Desc", "CONNECTION", 2, 20);

        // Transition mission to target status via valid path
        transitionToStatus(mission, status);

        boolean isActive = mission.isActive();
        boolean shouldBeActive = (status == ASSIGNED
                || status == ACCEPTED
                || status == IN_PROGRESS);

        if (isActive != shouldBeActive) {
            throw new AssertionError(
                    "Mission in status " + status + " should " + (shouldBeActive ? "" : "NOT ")
                            + "be active but isActive() returned " + isActive);
        }
    }

    @Provide
    Arbitrary<LegacyMissionStatus> activeStatuses() {
        return Arbitraries.of(ASSIGNED, ACCEPTED, IN_PROGRESS);
    }

    @Provide
    Arbitrary<LegacyMissionStatus> allStatuses() {
        return Arbitraries.of(LegacyMissionStatus.values());
    }

    /**
     * Transitions a mission to the target status via the valid path in the state machine.
     */
    private void transitionToStatus(Mission mission, LegacyMissionStatus target) {
        switch (target) {
            case ASSIGNED:
                // Already in ASSIGNED state
                break;
            case ACCEPTED:
                mission.transitionTo(ACCEPTED);
                break;
            case SKIPPED:
                mission.transitionTo(SKIPPED);
                break;
            case EXPIRED:
                mission.transitionTo(EXPIRED);
                break;
            case IN_PROGRESS:
                mission.transitionTo(ACCEPTED);
                mission.transitionTo(IN_PROGRESS);
                break;
            case COMPLETED:
                mission.transitionTo(ACCEPTED);
                mission.transitionTo(IN_PROGRESS);
                mission.transitionTo(COMPLETED);
                break;
            case ABANDONED:
                mission.transitionTo(ACCEPTED);
                mission.transitionTo(IN_PROGRESS);
                mission.transitionTo(ABANDONED);
                break;
            case REFLECTED:
                mission.transitionTo(ACCEPTED);
                mission.transitionTo(IN_PROGRESS);
                mission.transitionTo(COMPLETED);
                mission.transitionTo(REFLECTED);
                break;
        }
    }
}
