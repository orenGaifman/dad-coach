package com.dadcoach.domain.child;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Period;

/**
 * Property-based tests for Child entity computed behaviors.
 * Tests properties #4, #5, and #6 from the design specification.
 * No Spring context needed — tests create Child objects directly.
 */
class ChildPropertyTests {

    // ─── Property #4: Child Age Dynamic Computation ─────────────────────────

    /**
     * **Validates: Requirements 2.3, 2.4**
     *
     * For any valid birth_date (between 0 and 18 years in the past),
     * the computed age should equal the floor of years between birth_date and current date.
     */
    @Property(tries = 500)
    void ageComputationEqualsFloorOfYearsBetweenBirthDateAndNow(
            @ForAll("validBirthDates") LocalDate birthDate) {

        Child child = createChildWithBirthDate(birthDate);

        int expectedAge = Period.between(birthDate, LocalDate.now()).getYears();
        int actualAge = child.getAge();

        assert actualAge == expectedAge :
                "Expected age " + expectedAge + " but got " + actualAge + " for birthDate " + birthDate;
        assert actualAge >= 0 && actualAge <= 18 :
                "Age should be between 0 and 18, got " + actualAge;
    }

    // ─── Property #5: Birthday Detection ────────────────────────────────────

    /**
     * **Validates: Requirements 2.7**
     *
     * For any child with a birth_date, the system should detect an upcoming birthday
     * when the month-day anniversary is within 7 calendar days of the current date
     * (including year wrap-around for late December births with early January anniversaries).
     */
    @Property(tries = 500)
    void birthdayDetectedWhenWithinSevenDays(
            @ForAll("validBirthDates") LocalDate birthDate) {

        Child child = createChildWithBirthDate(birthDate);
        LocalDate today = LocalDate.now();
        MonthDay birthday = MonthDay.from(birthDate);

        // Manually check if birthday is within 7 days
        boolean expectedWithin = false;
        for (int i = 0; i <= 7; i++) {
            LocalDate checkDate = today.plusDays(i);
            if (MonthDay.from(checkDate).equals(birthday)) {
                expectedWithin = true;
                break;
            }
        }

        boolean actualWithin = child.isBirthdayWithin(7);

        assert actualWithin == expectedWithin :
                "Birthday detection mismatch for birthDate " + birthDate +
                        ": expected=" + expectedWithin + ", actual=" + actualWithin;
    }

    /**
     * **Validates: Requirements 2.7**
     *
     * Complement: if a birthday is exactly N days away (N <= 7), isBirthdayWithin(7) returns true.
     * If it is more than 7 days away (and not wrapping), it returns false.
     */
    @Property(tries = 200)
    void birthdayNotDetectedWhenBeyondWindow(
            @ForAll @IntRange(min = 8, max = 360) int daysAhead) {

        // Create a child whose birthday is exactly daysAhead days from today
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(daysAhead);
        MonthDay targetBirthday = MonthDay.from(futureDate);

        // Pick a birth date with that month-day, 5 years ago
        LocalDate birthDate = targetBirthday.atYear(today.getYear() - 5);
        // Ensure the birth date is in the past
        if (birthDate.isAfter(today)) {
            birthDate = birthDate.minusYears(1);
        }

        Child child = createChildWithBirthDate(birthDate);

        // The birthday should NOT be within 7 days since it is daysAhead days away
        boolean result = child.isBirthdayWithin(7);

        assert !result :
                "Birthday should not be detected within 7 days when it is " + daysAhead +
                        " days away. BirthDate: " + birthDate;
    }

    // ─── Property #6: Developmental Age Bracket Classification ──────────────

    /**
     * **Validates: Requirements 12.9**
     *
     * For any child age (0-18), the computed developmental bracket should be:
     * 0-2=INFANT, 3-5=PRESCHOOL, 6-8=EARLY_SCHOOL, 9-11=PRE_TEEN, 12-14=EARLY_TEEN, 15-18=TEENAGER.
     */
    @Property(tries = 200)
    void developmentalBracketMatchesAgeRange(
            @ForAll @IntRange(min = 0, max = 18) int age) {

        // Create a child with a birth date that results in the given age
        LocalDate birthDate = LocalDate.now().minusYears(age).minusDays(1);
        // Adjust to ensure the computed age matches exactly
        // minusYears(age).minusDays(1) ensures the birthday has passed this year
        Child child = createChildWithBirthDate(birthDate);

        // Verify the child's age is what we expect (to validate our test setup)
        int computedAge = child.getAge();

        DevelopmentalBracket expectedBracket = expectedBracketForAge(computedAge);
        DevelopmentalBracket actualBracket = child.getDevelopmentalBracket();

        assert actualBracket == expectedBracket :
                "For age " + computedAge + " expected bracket " + expectedBracket +
                        " but got " + actualBracket;
    }

    /**
     * **Validates: Requirements 12.9**
     *
     * The bracket classification must be exhaustive — every age 0-18 maps to exactly one bracket.
     */
    @Property(tries = 100)
    void everyValidAgeMapsToExactlyOneBracket(
            @ForAll @IntRange(min = 0, max = 18) int age) {

        DevelopmentalBracket bracket = DevelopmentalBracket.fromAge(age);

        assert bracket != null : "Age " + age + " did not map to any bracket";
        assert age >= bracket.getMinAge() && age <= bracket.getMaxAge() :
                "Age " + age + " is outside bracket " + bracket + " range [" +
                        bracket.getMinAge() + ", " + bracket.getMaxAge() + "]";
    }

    // ─── Arbitrary Providers ────────────────────────────────────────────────

    @Provide
    Arbitrary<LocalDate> validBirthDates() {
        LocalDate today = LocalDate.now();
        LocalDate eighteenYearsAgo = today.minusYears(18);
        // Generate dates between 18 years ago and today (inclusive)
        long minEpochDay = eighteenYearsAgo.toEpochDay();
        long maxEpochDay = today.toEpochDay();

        return Arbitraries.longs()
                .between(minEpochDay, maxEpochDay)
                .map(LocalDate::ofEpochDay);
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────

    private Child createChildWithBirthDate(LocalDate birthDate) {
        Child child = new Child(null, "TestChild", birthDate);
        return child;
    }

    private DevelopmentalBracket expectedBracketForAge(int age) {
        if (age >= 0 && age <= 2) return DevelopmentalBracket.INFANT;
        if (age >= 3 && age <= 5) return DevelopmentalBracket.PRESCHOOL;
        if (age >= 6 && age <= 8) return DevelopmentalBracket.EARLY_SCHOOL;
        if (age >= 9 && age <= 11) return DevelopmentalBracket.PRE_TEEN;
        if (age >= 12 && age <= 14) return DevelopmentalBracket.EARLY_TEEN;
        if (age >= 15 && age <= 18) return DevelopmentalBracket.TEENAGER;
        throw new IllegalArgumentException("Age out of range: " + age);
    }
}
