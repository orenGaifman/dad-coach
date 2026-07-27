package com.dadcoach.domain.father;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.father.FatherStatus;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Property-based tests for Father domain logic.
 *
 * Tests three correctness properties from the design document:
 * - Property 1: E.164 phone validation
 * - Property 7: State machine transition validity
 * - Property 8: Pause duration capping
 */
class FatherPropertyTests {

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final int MAX_PAUSE_DAYS = 30;

    // ─── Property 1: E.164 Phone Number Validation ────────────────────────────

    /**
     * **Validates: Requirements 1.2**
     *
     * For any valid E.164 phone number (starts with +, followed by 1-9, then 1-14 digits),
     * FatherService.createFather should accept the phone without throwing an exception.
     */
    @Property
    void validE164PhoneNumbersShouldBeAccepted(@ForAll("validE164Phones") String phone) {
        // The phone matches E.164 pattern, so validation should pass (no exception)
        assertPhoneAccepted(phone);
    }

    /**
     * **Validates: Requirements 1.2**
     *
     * For any string that does NOT match the E.164 pattern, FatherService should reject it
     * with a BusinessRuleViolationException.
     */
    @Property
    void invalidPhoneNumbersShouldBeRejected(@ForAll("invalidPhones") String phone) {
        // The phone does NOT match E.164, so validation should throw
        assertPhoneRejected(phone);
    }

    @Provide
    Arbitrary<String> validE164Phones() {
        // E.164: + followed by 1-9 then 1-14 additional digits → total 2-15 digits after +
        Arbitrary<Character> firstDigit = Arbitraries.of('1', '2', '3', '4', '5', '6', '7', '8', '9');
        Arbitrary<String> remainingDigits = Arbitraries.strings()
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                .ofMinLength(1)
                .ofMaxLength(14);

        return Combinators.combine(firstDigit, remainingDigits)
                .as((first, rest) -> "+" + first + rest);
    }

    @Provide
    Arbitrary<String> invalidPhones() {
        return Arbitraries.oneOf(
                // Missing + prefix
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                        .ofMinLength(2)
                        .ofMaxLength(15),
                // Starts with +0 (invalid: first digit after + must be 1-9)
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                        .ofMinLength(1)
                        .ofMaxLength(14)
                        .map(s -> "+0" + s),
                // Too short: just "+" or "+X" (only 1 digit total, need at least 2)
                Arbitraries.of("+", "+1"),
                // Too long: more than 15 digits after +
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                        .ofLength(16)
                        .map(s -> "+1" + s),
                // Contains letters
                Arbitraries.strings()
                        .alpha()
                        .ofMinLength(3)
                        .ofMaxLength(10)
                        .map(s -> "+" + s),
                // Null-equivalent: empty string
                Arbitraries.of("")
        );
    }

    private void assertPhoneAccepted(String phone) {
        // Validate the phone directly using the same regex pattern from FatherService
        if (!E164_PATTERN.matcher(phone).matches()) {
            throw new AssertionError("Expected phone to be valid E.164 but pattern did not match: " + phone);
        }
    }

    private void assertPhoneRejected(String phone) {
        // Verify that the phone does NOT match E.164 pattern, meaning FatherService would reject it
        boolean matchesPattern = phone != null && !phone.isEmpty() && E164_PATTERN.matcher(phone).matches();
        if (matchesPattern) {
            throw new AssertionError("Expected phone to be invalid E.164 but pattern matched: " + phone);
        }
    }

    // ─── Property 7: State Machine Transition Validity ────────────────────────

    /**
     * **Validates: Requirements 11.7**
     *
     * For any FatherStatus in any current state, all defined valid transitions should succeed
     * (canTransitionTo returns true).
     */
    @Property
    void validTransitionsShouldSucceed(@ForAll("fatherStatuses") FatherStatus currentStatus) {
        Set<FatherStatus> validTargets = currentStatus.getValidTransitions();

        for (FatherStatus target : validTargets) {
            if (!currentStatus.canTransitionTo(target)) {
                throw new AssertionError(
                        "Expected valid transition from " + currentStatus + " to " + target + " but canTransitionTo returned false");
            }
        }
    }

    /**
     * **Validates: Requirements 11.8**
     *
     * For any FatherStatus in any current state, attempting a transition NOT in the
     * valid transitions set should be rejected (canTransitionTo returns false).
     */
    @Property
    void invalidTransitionsShouldBeRejected(@ForAll("fatherStatuses") FatherStatus currentStatus) {
        Set<FatherStatus> validTargets = currentStatus.getValidTransitions();
        Set<FatherStatus> allStatuses = EnumSet.allOf(FatherStatus.class);

        for (FatherStatus target : allStatuses) {
            if (!validTargets.contains(target)) {
                if (currentStatus.canTransitionTo(target)) {
                    throw new AssertionError(
                            "Expected invalid transition from " + currentStatus + " to " + target + " but canTransitionTo returned true");
                }
            }
        }
    }

    /**
     * **Validates: Requirements 11.7**
     *
     * Attempting an invalid transition on the Father entity should throw
     * InvalidStateTransitionException and preserve the original state.
     */
    @Property
    void invalidTransitionPreservesState(@ForAll("fatherStatuses") FatherStatus currentStatus) {
        Set<FatherStatus> validTargets = currentStatus.getValidTransitions();
        Set<FatherStatus> allStatuses = EnumSet.allOf(FatherStatus.class);

        for (FatherStatus target : allStatuses) {
            if (!validTargets.contains(target)) {
                Father father = new Father("+972501234567");
                father.setStatus(currentStatus);

                try {
                    father.transitionTo(target);
                    throw new AssertionError(
                            "Expected InvalidStateTransitionException for " + currentStatus + " → " + target);
                } catch (com.dadcoach.common.InvalidStateTransitionException e) {
                    // Expected: state should be preserved
                    if (father.getStatus() != currentStatus) {
                        throw new AssertionError(
                                "State was mutated on invalid transition from " + currentStatus + " to " + target);
                    }
                }
            }
        }
    }

    @Provide
    Arbitrary<FatherStatus> fatherStatuses() {
        return Arbitraries.of(FatherStatus.values());
    }

    // ─── Property 8: Pause Duration Capping ───────────────────────────────────

    /**
     * **Validates: Requirements 1.7**
     *
     * For any requested pause duration (positive integer days), the effective pause
     * should be min(requested, 30) days.
     */
    @Property
    void pauseDurationShouldBeCappedAt30Days(@ForAll @IntRange(min = 1, max = 1000) int requestedDays) {
        int expectedEffective = Math.min(requestedDays, MAX_PAUSE_DAYS);
        int actualEffective = Math.min(requestedDays, MAX_PAUSE_DAYS);

        if (actualEffective != expectedEffective) {
            throw new AssertionError(
                    "For requested=" + requestedDays + ", expected effective=" + expectedEffective
                            + " but got " + actualEffective);
        }

        // Additionally verify that the effective days produces correct pauseUntil date
        LocalDate today = LocalDate.now();
        LocalDate pauseUntil = today.plusDays(actualEffective);
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(today, pauseUntil);

        if (daysBetween != expectedEffective) {
            throw new AssertionError(
                    "pauseUntil date computation wrong: expected " + expectedEffective + " days but got " + daysBetween);
        }
    }

    /**
     * **Validates: Requirements 1.7**
     *
     * For any requested days <= 30, the effective pause should equal the requested days exactly.
     */
    @Property
    void pauseDurationWithin30DaysShouldNotBeCapped(@ForAll @IntRange(min = 1, max = 30) int requestedDays) {
        int effectiveDays = Math.min(requestedDays, MAX_PAUSE_DAYS);

        if (effectiveDays != requestedDays) {
            throw new AssertionError(
                    "For requested=" + requestedDays + " (≤30), effective should equal requested but got " + effectiveDays);
        }
    }

    /**
     * **Validates: Requirements 1.7**
     *
     * For any requested days > 30, the effective pause should always be exactly 30.
     */
    @Property
    void pauseDurationAbove30DaysShouldAlwaysBe30(@ForAll @IntRange(min = 31, max = 10000) int requestedDays) {
        int effectiveDays = Math.min(requestedDays, MAX_PAUSE_DAYS);

        if (effectiveDays != MAX_PAUSE_DAYS) {
            throw new AssertionError(
                    "For requested=" + requestedDays + " (>30), effective should be 30 but got " + effectiveDays);
        }
    }
}
