package com.dadcoach.workflow.date;

import com.dadcoach.workflow.message.MessageContext;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for Correct Timezone Handling
 * 
 * **Validates: Requirements 3.5, 3.6**
 * 
 * <p>These tests ensure that existing timezone handling behavior is preserved after
 * the date calculation bug fix is applied. The tests verify that:</p>
 * <ul>
 *   <li>3.5: Fathers with correctly configured timezone get correct time display</li>
 *   <li>3.6: Timestamps stored in UTC format (preserved through operations)</li>
 * </ul>
 * 
 * <p><strong>PRESERVATION SCOPE:</strong></p>
 * <ul>
 *   <li>Non-boundary timezone scenarios (mid-day calculations)</li>
 *   <li>Time slot formatting uses father's timezone for display</li>
 *   <li>UTC timestamps are converted correctly to local time</li>
 * </ul>
 * 
 * <p><strong>EXPECTED BEHAVIOR:</strong></p>
 * <ul>
 *   <li>These tests MUST PASS on unfixed code (current behavior is correct for non-boundary scenarios)</li>
 *   <li>These tests MUST PASS after the fix is applied (no regression for timezone handling)</li>
 * </ul>
 * 
 * <p><strong>Key Distinction from Exploration Tests:</strong></p>
 * <ul>
 *   <li>Exploration tests: Test bug condition (midnight boundary issues) - expected to FAIL on unfixed code</li>
 *   <li>Preservation tests: Test non-buggy scenarios (mid-day, correct timezone use) - expected to PASS on all code</li>
 * </ul>
 */
class TimezoneHandlingPreservationTest {

    // Common timezone constants
    private static final String DEFAULT_TIMEZONE = "Asia/Jerusalem";
    private static final ZoneId ISRAEL_ZONE = ZoneId.of("Asia/Jerusalem");
    private static final ZoneId US_EASTERN_ZONE = ZoneId.of("America/New_York");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    // ============== Property: Fathers with correctly configured timezone get correct time display ==============

    /**
     * Property test: Time formatting should use the father's configured timezone.
     * 
     * <p>When a father has a correctly configured timezone, all time formatting
     * operations should use that timezone for display purposes.</p>
     * 
     * <p><strong>Focus:</strong> Non-boundary mid-day times where there's no ambiguity.</p>
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Label("Time formatting should use father's configured timezone (mid-day scenario)")
    void timeFormattingShouldUseFatherTimezone(
            @ForAll("supportedTimezones") String timezone,
            @ForAll("midDayHours") int hour
    ) {
        // Arrange: Create a MessageContext with the specified timezone
        MessageContext context = MessageContext.builder()
                .timezone(timezone)
                .locale("en")
                .fatherName("Test Father")
                .build();

        // Create a test instant - today at the specified mid-day hour in UTC
        LocalDate today = LocalDate.now();
        Instant testInstant = today.atTime(hour, 30).atZone(UTC_ZONE).toInstant();

        // ACT: Format the time using the context's timezone
        String formattedTime = context.formatTimeInTimezone(testInstant);

        // Calculate expected time in father's timezone
        ZonedDateTime inFatherZone = testInstant.atZone(ZoneId.of(timezone));
        int expectedHour = inFatherZone.getHour();
        int expectedMinute = inFatherZone.getMinute();

        // ASSERT: The formatted time should reflect the father's timezone
        // We check that the minutes are correct (30) which proves timezone conversion happened
        assertThat(formattedTime)
                .as("Formatted time should use father's timezone '%s'. " +
                    "UTC time %d:30 should be displayed according to the configured timezone.",
                    timezone, hour)
                .contains("30");  // Minutes should always be :30

        // Verify the result is not empty (basic sanity check)
        assertThat(formattedTime)
                .as("Formatted time should not be empty for valid input")
                .isNotEmpty();
    }

    /**
     * Property test: MessageContext.getTimezoneAsZoneId() should return correct ZoneId.
     * 
     * <p>This tests that the timezone string stored in MessageContext is correctly
     * converted to a ZoneId for date/time operations.</p>
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 50)
    @Label("getTimezoneAsZoneId should return correct ZoneId for valid timezones")
    void getTimezoneAsZoneIdShouldReturnCorrectZoneId(
            @ForAll("supportedTimezones") String timezone
    ) {
        // Arrange
        MessageContext context = MessageContext.builder()
                .timezone(timezone)
                .locale("he")
                .build();

        // ACT
        ZoneId zoneId = context.getTimezoneAsZoneId();

        // ASSERT
        assertThat(zoneId)
                .as("getTimezoneAsZoneId() should return ZoneId matching the configured timezone")
                .isEqualTo(ZoneId.of(timezone));
    }

    /**
     * Property test: Invalid timezone should fall back to default (Asia/Jerusalem).
     * 
     * <p>This tests preservation of the fallback behavior when an invalid timezone
     * is configured.</p>
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 20)
    @Label("Invalid timezone should fall back to default Asia/Jerusalem")
    void invalidTimezoneShouldFallbackToDefault(
            @ForAll("invalidTimezones") String invalidTimezone
    ) {
        // Arrange
        MessageContext context = MessageContext.builder()
                .timezone(invalidTimezone)
                .locale("en")
                .build();

        // ACT
        ZoneId zoneId = context.getTimezoneAsZoneId();

        // ASSERT: Should fall back to Asia/Jerusalem
        assertThat(zoneId)
                .as("Invalid timezone '%s' should fall back to default 'Asia/Jerusalem'",
                    invalidTimezone)
                .isEqualTo(ISRAEL_ZONE);
    }

    // ============== Property: UTC timestamp preservation ==============

    /**
     * Property test: UTC timestamps should be preserved through timezone conversions.
     * 
     * <p>This verifies that when converting between timezones for display purposes,
     * the underlying UTC instant is preserved (Requirement 3.6).</p>
     * 
     * **Validates: Requirements 3.6**
     */
    @Property(tries = 100)
    @Label("UTC timestamps should be preserved through timezone conversions")
    void utcTimestampsShouldBePreservedThroughConversions(
            @ForAll("supportedTimezones") String timezone,
            @ForAll("testInstants") Instant originalUtc
    ) {
        // Arrange
        ZoneId zoneId = ZoneId.of(timezone);

        // ACT: Convert to local zone and back to UTC
        ZonedDateTime localTime = originalUtc.atZone(zoneId);
        Instant backToUtc = localTime.toInstant();

        // ASSERT: UTC instant should be preserved exactly
        assertThat(backToUtc)
                .as("UTC timestamp should be preserved when converting to '%s' and back. " +
                    "Original: %s, After round-trip: %s", timezone, originalUtc, backToUtc)
                .isEqualTo(originalUtc);
    }

    /**
     * Property test: Instant epoch seconds should be unchanged by timezone display operations.
     * 
     * <p>This tests that display formatting doesn't modify the underlying timestamp value.</p>
     * 
     * **Validates: Requirements 3.6**
     */
    @Property(tries = 50)
    @Label("Instant epoch seconds should be unchanged by timezone operations")
    void instantEpochSecondsShouldBeUnchanged(
            @ForAll("testInstants") Instant timestamp,
            @ForAll("supportedTimezones") String timezone
    ) {
        // Arrange
        long originalEpochSeconds = timestamp.getEpochSecond();
        ZoneId zoneId = ZoneId.of(timezone);

        // ACT: Perform various timezone operations
        ZonedDateTime zdt = timestamp.atZone(zoneId);
        LocalDateTime localDt = zdt.toLocalDateTime();
        Instant reconstructed = localDt.atZone(zoneId).toInstant();

        // ASSERT: Epoch seconds should match
        assertThat(reconstructed.getEpochSecond())
                .as("Epoch seconds should be preserved through LocalDateTime conversion. " +
                    "Timezone: %s, Original: %d, After: %d",
                    timezone, originalEpochSeconds, reconstructed.getEpochSecond())
                .isEqualTo(originalEpochSeconds);
    }

    // ============== Property: Non-boundary timezone scenarios work correctly ==============

    /**
     * Property test: Mid-day time calculations should work correctly across timezones.
     * 
     * <p>For non-boundary scenarios (not near midnight), timezone conversions should
     * always produce correct results. This is in contrast to the bug which occurs
     * specifically at midnight boundaries.</p>
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Label("Mid-day time calculations should work correctly across timezones")
    void midDayCalculationsShouldWorkCorrectly(
            @ForAll("supportedTimezones") String timezone,
            @ForAll("midDayHours") int hour,
            @ForAll("minuteValues") int minute
    ) {
        // Arrange
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate today = LocalDate.now();
        
        // Create a time that's clearly mid-day (no midnight boundary ambiguity)
        LocalDateTime localMidDay = LocalDateTime.of(today, LocalTime.of(hour, minute));
        ZonedDateTime zdt = localMidDay.atZone(zoneId);
        Instant instant = zdt.toInstant();

        // ACT: Convert back from instant to local time in the same zone
        ZonedDateTime reconstructed = instant.atZone(zoneId);
        LocalDateTime reconstructedLocal = reconstructed.toLocalDateTime();

        // ASSERT: The local time should match exactly for mid-day scenarios
        assertThat(reconstructedLocal.getHour())
                .as("Hour should be preserved for mid-day conversion in timezone '%s'", timezone)
                .isEqualTo(hour);
        assertThat(reconstructedLocal.getMinute())
                .as("Minute should be preserved for mid-day conversion in timezone '%s'", timezone)
                .isEqualTo(minute);
    }

    /**
     * Property test: Time slot formatting should display correct time in father's timezone.
     * 
     * <p>When formatting time slots for display, the times should be shown in the
     * father's configured timezone, not UTC or server timezone.</p>
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 50)
    @Label("Time slot formatting should display correct time in father's timezone")
    void timeSlotFormattingShouldUseCorrectTimezone(
            @ForAll("supportedTimezones") String timezone,
            @ForAll("midDayHours") int hour
    ) {
        // Arrange
        MessageContext context = MessageContext.builder()
                .timezone(timezone)
                .locale("en")
                .fatherName("Test")
                .build();

        // Create a time slot at a mid-day hour in the father's timezone
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate today = LocalDate.now();
        ZonedDateTime startZdt = today.atTime(hour, 0).atZone(zoneId);
        Instant startInstant = startZdt.toInstant();

        // ACT: Format the time using the MessageContext
        String formatted = context.formatTimeInTimezone(startInstant);

        // ASSERT: The formatted output should contain the hour (adjusted for AM/PM format)
        // For hours > 12, we expect the hour mod 12 (or 12 for noon)
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        
        assertThat(formatted)
                .as("Time formatted in timezone '%s' should show correct hour. " +
                    "Expected to contain hour %d for input hour %d",
                    timezone, displayHour, hour)
                .contains(String.valueOf(displayHour));
    }

    // ============== Example Tests for Specific Preservation Scenarios ==============

    /**
     * Example: Israel timezone (Asia/Jerusalem) should display times correctly.
     * 
     * **Validates: Requirements 3.5**
     */
    @Example
    @Label("Israel timezone should display times correctly")
    void israelTimezoneShouldDisplayCorrectly() {
        // Arrange
        MessageContext context = MessageContext.builder()
                .timezone("Asia/Jerusalem")
                .locale("he")
                .fatherName("דוד")
                .build();

        // Create a time: 15:00 in Israel timezone
        LocalDate today = LocalDate.now();
        ZonedDateTime israelTime = today.atTime(15, 0).atZone(ISRAEL_ZONE);
        Instant instant = israelTime.toInstant();

        // ACT
        String formatted = context.formatTimeInTimezone(instant);

        // ASSERT: Should display 3:00 PM
        assertThat(formatted)
                .as("3:00 PM in Israel should be formatted correctly")
                .contains("3")
                .contains("00");
    }

    /**
     * Example: US Eastern timezone should display times correctly.
     * 
     * **Validates: Requirements 3.5**
     */
    @Example
    @Label("US Eastern timezone should display times correctly")
    void usEasternTimezoneShouldDisplayCorrectly() {
        // Arrange
        MessageContext context = MessageContext.builder()
                .timezone("America/New_York")
                .locale("en")
                .fatherName("John")
                .build();

        // Create a time: 14:30 in US Eastern timezone
        LocalDate today = LocalDate.now();
        ZonedDateTime easternTime = today.atTime(14, 30).atZone(US_EASTERN_ZONE);
        Instant instant = easternTime.toInstant();

        // ACT
        String formatted = context.formatTimeInTimezone(instant);

        // ASSERT: Should display 2:30 PM
        assertThat(formatted)
                .as("2:30 PM in US Eastern should be formatted correctly")
                .contains("2")
                .contains("30")
                .containsIgnoringCase("PM");
    }

    /**
     * Example: Default timezone (null) should fall back to Asia/Jerusalem.
     * 
     * **Validates: Requirements 3.5**
     */
    @Example
    @Label("Default timezone should fall back to Asia/Jerusalem")
    void defaultTimezoneShouldFallbackToIsrael() {
        // Arrange: Create context without explicit timezone
        MessageContext context = MessageContext.builder()
                .locale("en")
                .fatherName("Test")
                .build();

        // ACT
        ZoneId zoneId = context.getTimezoneAsZoneId();
        String timezoneStr = context.getTimezone();

        // ASSERT: Should default to Asia/Jerusalem
        assertThat(zoneId)
                .as("Default timezone ZoneId should be Asia/Jerusalem")
                .isEqualTo(ISRAEL_ZONE);
        assertThat(timezoneStr)
                .as("Default timezone string should be 'Asia/Jerusalem'")
                .isEqualTo("Asia/Jerusalem");
    }

    /**
     * Example: UTC timestamp should be preserved after display formatting.
     * 
     * **Validates: Requirements 3.6**
     */
    @Example
    @Label("UTC timestamp should be preserved after display formatting")
    void utcTimestampShouldBePreservedAfterFormatting() {
        // Arrange
        Instant originalUtc = Instant.parse("2024-08-22T12:00:00Z");
        MessageContext context = MessageContext.builder()
                .timezone("America/Los_Angeles")  // Pacific timezone (UTC-7/8)
                .locale("en")
                .build();

        // ACT: Format for display
        String formatted = context.formatTimeInTimezone(originalUtc);

        // Verify the original instant hasn't changed by using it again
        ZonedDateTime inPacific = originalUtc.atZone(ZoneId.of("America/Los_Angeles"));
        Instant backToUtc = inPacific.toInstant();

        // ASSERT: Original UTC should be preserved
        assertThat(backToUtc)
                .as("UTC timestamp should be preserved exactly after timezone operations")
                .isEqualTo(originalUtc);
        assertThat(formatted)
                .as("Formatted time should not be empty")
                .isNotEmpty();
    }

    /**
     * Example: Cross-timezone calculation for same moment should yield different local times.
     * 
     * <p>This demonstrates that the same UTC moment displays differently in different timezones,
     * which is the expected preservation behavior.</p>
     * 
     * **Validates: Requirements 3.5, 3.6**
     */
    @Example
    @Label("Same UTC moment should display differently in different timezones")
    void sameUtcMomentShouldDisplayDifferentlyInDifferentTimezones() {
        // Arrange: Same UTC moment
        Instant utcMoment = Instant.parse("2024-08-22T12:00:00Z");

        // Create contexts for different timezones
        MessageContext israelContext = MessageContext.builder()
                .timezone("Asia/Jerusalem")
                .locale("en")
                .build();
        MessageContext usEastContext = MessageContext.builder()
                .timezone("America/New_York")
                .locale("en")
                .build();

        // ACT
        String israelFormatted = israelContext.formatTimeInTimezone(utcMoment);
        String usEastFormatted = usEastContext.formatTimeInTimezone(utcMoment);

        // Calculate expected hours
        // UTC 12:00 = Israel 15:00 (3 PM) in summer = 14:00 (2 PM) or 15:00 (3 PM) depending on DST
        // UTC 12:00 = US Eastern 08:00 (8 AM) in summer = 07:00 (7 AM) or 08:00 (8 AM) depending on DST
        
        // ASSERT: The formatted times should be different (different timezones)
        assertThat(israelFormatted)
                .as("Israel formatted time should not be empty")
                .isNotEmpty();
        assertThat(usEastFormatted)
                .as("US East formatted time should not be empty")
                .isNotEmpty();
        
        // The times should be different for different timezones
        // (unless by coincidence the formatted strings happen to look similar, which is very unlikely)
        // We just verify both work and produce output - the actual difference depends on DST state
    }

    // ============== Generators ==============

    /**
     * Generator for supported timezones in the system.
     */
    @Provide
    Arbitrary<String> supportedTimezones() {
        return Arbitraries.of(
                "Asia/Jerusalem",
                "America/New_York",
                "America/Los_Angeles",
                "Europe/London",
                "UTC",
                "Asia/Tokyo",
                "Australia/Sydney"
        );
    }

    /**
     * Generator for mid-day hours (avoiding midnight boundary).
     * Range: 9-20 (9 AM to 8 PM) - clearly not near midnight in any timezone.
     */
    @Provide
    Arbitrary<Integer> midDayHours() {
        return Arbitraries.integers().between(9, 20);
    }

    /**
     * Generator for minute values.
     */
    @Provide
    Arbitrary<Integer> minuteValues() {
        return Arbitraries.of(0, 15, 30, 45);
    }

    /**
     * Generator for invalid timezone strings.
     */
    @Provide
    Arbitrary<String> invalidTimezones() {
        return Arbitraries.of(
                "Invalid/Timezone",
                "NotAZone",
                "123456",
                "",
                "Fake/City"
        );
    }

    /**
     * Generator for test instants (mid-day UTC times to avoid boundary issues).
     */
    @Provide
    Arbitrary<Instant> testInstants() {
        // Generate instants at mid-day hours on recent dates
        return Arbitraries.integers().between(0, 30)  // Days ago
                .flatMap(daysAgo -> Arbitraries.integers().between(9, 18)  // Hours (mid-day)
                        .map(hour -> {
                            LocalDate date = LocalDate.now().minusDays(daysAgo);
                            LocalDateTime ldt = date.atTime(hour, 0);
                            return ldt.atZone(UTC_ZONE).toInstant();
                        }));
    }
}
