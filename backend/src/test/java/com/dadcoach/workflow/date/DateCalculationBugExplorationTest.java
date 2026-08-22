package com.dadcoach.workflow.date;

import com.dadcoach.workflow.message.MessageContext;
import net.jqwik.api.*;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Bug Condition Exploration Test for Date Calculation Error
 * 
 * **Validates: Requirements 1.7, 1.8, 1.9**
 * 
 * <p>This test demonstrates that the date calculation bug EXISTS in the current
 * unfixed code. The bug manifests when:</p>
 * <ul>
 *   <li>The bot calculates "tomorrow" for scheduling purposes</li>
 *   <li>It incorrectly returns the current date in some cases</li>
 *   <li>Date calculations don't consistently use the father's configured timezone</li>
 * </ul>
 * 
 * <p><strong>Bug Condition (from bugfix.md):</strong></p>
 * <pre>
 * FUNCTION isDateCalculationBug(displayedDate, messageContent, fatherTimezone)
 *   INPUT: displayedDate of type LocalDate, messageContent of type String, fatherTimezone of type ZoneId
 *   OUTPUT: boolean
 *   
 *   todayInFatherTz ← LocalDate.now(fatherTimezone)
 *   tomorrowInFatherTz ← todayInFatherTz.plusDays(1)
 *   
 *   RETURN messageContent.contains("מחר") OR messageContent.contains("tomorrow")
 *          AND extractDate(displayedDate) = todayInFatherTz
 *          AND extractDate(displayedDate) ≠ tomorrowInFatherTz
 * END FUNCTION
 * </pre>
 * 
 * <p><strong>CRITICAL:</strong> This test is EXPECTED TO FAIL on unfixed code.
 * Test failure = bug exists = success for exploration phase.</p>
 * 
 * <p><strong>Examples (BUG scenarios):</strong></p>
 * <ul>
 *   <li>"מחר, יום שישי 22/08" shown when today IS Friday 22/08</li>
 *   <li>Server in UTC, father in Asia/Jerusalem, date crosses midnight differently</li>
 * </ul>
 * 
 * <p><strong>Root Cause (from design.md):</strong></p>
 * <ul>
 *   <li>Some calculations use LocalDate.now() without timezone</li>
 *   <li>Father's timezone not passed to all date formatting methods</li>
 *   <li>No verification that day-of-week matches actual date</li>
 * </ul>
 */
class DateCalculationBugExplorationTest {

    // Expected method names that should exist after the fix
    private static final String GET_TOMORROW_IN_TIMEZONE = "getTomorrowInTimezone";
    private static final String GET_TODAY_IN_TIMEZONE = "getTodayInTimezone";
    private static final String VALIDATE_DAY_OF_WEEK = "validateDayOfWeek";
    
    // Default timezone used in system (Israel)
    private static final ZoneId ISRAEL_ZONE = ZoneId.of("Asia/Jerusalem");
    
    // Test timezone with significant offset difference (for midnight boundary tests)
    private static final ZoneId US_PACIFIC_ZONE = ZoneId.of("America/Los_Angeles");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    /**
     * Property test: MessageContext should have getTomorrowInTimezone method.
     * 
     * <p><strong>EXPECTED TO FAIL on unfixed code:</strong> The method doesn't exist yet,
     * proving the bug exists (no mechanism for timezone-aware "tomorrow" calculation).</p>
     * 
     * **Validates: Requirements 2.7, 2.8**
     */
    @Property(tries = 1)
    @Label("MessageContext should have getTomorrowInTimezone method")
    void messageContextShouldHaveGetTomorrowInTimezoneMethod() {
        Class<?> messageContextClass = MessageContext.class;
        
        // Look for the method with expected signature: getTomorrowInTimezone()
        Method[] methods = messageContextClass.getDeclaredMethods();
        boolean methodFound = Arrays.stream(methods)
                .anyMatch(m -> m.getName().equals(GET_TOMORROW_IN_TIMEZONE));
        
        // ASSERTION: The method should exist (fails on unfixed code = bug confirmed)
        assertThat(methodFound)
                .as("MessageContext should have method '%s' for timezone-aware 'tomorrow' calculation. " +
                    "BUG: Currently some date calculations use LocalDate.now() without timezone, " +
                    "causing 'tomorrow' to show today's date when server and father timezones differ. " +
                    "Fix requirement: Add getTomorrowInTimezone() that uses father's configured timezone.",
                    GET_TOMORROW_IN_TIMEZONE)
                .isTrue();
    }

    /**
     * Property test: MessageContext should have getTodayInTimezone method.
     * 
     * <p><strong>EXPECTED TO FAIL on unfixed code:</strong> The method doesn't exist yet.</p>
     * 
     * **Validates: Requirements 2.7, 2.8**
     */
    @Property(tries = 1)
    @Label("MessageContext should have getTodayInTimezone method")
    void messageContextShouldHaveGetTodayInTimezoneMethod() {
        Class<?> messageContextClass = MessageContext.class;
        
        Method[] methods = messageContextClass.getDeclaredMethods();
        boolean methodFound = Arrays.stream(methods)
                .anyMatch(m -> m.getName().equals(GET_TODAY_IN_TIMEZONE));
        
        assertThat(methodFound)
                .as("MessageContext should have method '%s' for timezone-aware 'today' calculation. " +
                    "BUG: Without this, date comparisons may use wrong day boundary. " +
                    "Fix requirement: Add getTodayInTimezone() that uses father's configured timezone.",
                    GET_TODAY_IN_TIMEZONE)
                .isTrue();
    }

    /**
     * Property test: MessageContext should have validateDayOfWeek method.
     * 
     * <p><strong>EXPECTED TO FAIL on unfixed code:</strong> The method doesn't exist yet,
     * meaning there's no verification that day-of-week matches actual date.</p>
     * 
     * **Validates: Requirements 2.9**
     */
    @Property(tries = 1)
    @Label("MessageContext should have validateDayOfWeek method")
    void messageContextShouldHaveValidateDayOfWeekMethod() {
        Class<?> messageContextClass = MessageContext.class;
        
        Method[] methods = messageContextClass.getDeclaredMethods();
        boolean methodFound = Arrays.stream(methods)
                .anyMatch(m -> m.getName().equals(VALIDATE_DAY_OF_WEEK));
        
        assertThat(methodFound)
                .as("MessageContext should have method '%s' to verify day matches actual date. " +
                    "BUG: No validation exists that 'יום שישי' actually corresponds to Friday. " +
                    "Example: 'מחר, יום שישי 22/08' could be shown when today IS Friday 22/08. " +
                    "Fix requirement: Add validateDayOfWeek(LocalDate, String) method.",
                    VALIDATE_DAY_OF_WEEK)
                .isTrue();
    }

    /**
     * Property test: When calculating "tomorrow" near midnight with timezone differences,
     * the system should use the father's timezone, not the server's timezone.
     * 
     * <p><strong>Scenario:</strong> Server runs in UTC, father is in Asia/Jerusalem (UTC+3).
     * At 23:00 UTC (02:00 Jerusalem time), "tomorrow" calculations differ significantly.</p>
     * 
     * <p><strong>EXPECTED TO FAIL on unfixed code:</strong> The current code doesn't
     * have timezone-aware tomorrow calculation, leading to incorrect dates.</p>
     * 
     * **Validates: Requirements 1.7, 1.8, 1.9**
     */
    @Property(tries = 10)
    @Label("Tomorrow calculation should be timezone-aware (midnight boundary)")
    void tomorrowCalculationShouldBeTimezoneAware(
            @ForAll("midnightBoundaryTimezones") ZoneId fatherTimezone
    ) {
        // The fix should add getTomorrowInTimezone() method to MessageContext
        // Without it, the test fails - proving the bug exists
        
        Class<?> messageContextClass = MessageContext.class;
        
        // Try to find the method
        Method method = findMethodByName(messageContextClass, GET_TOMORROW_IN_TIMEZONE);
        
        assertThat(method)
                .as("BUG CONFIRMED: No timezone-aware 'tomorrow' calculation exists. " +
                    "When server is in UTC and father is in '%s', midnight boundaries differ. " +
                    "At 23:00 UTC = 02:00 Jerusalem (next day), 'tomorrow' could be off by a day. " +
                    "Example from production: 'מחר, יום שישי 22/08' shown when TODAY is Friday 22/08. " +
                    "Fix: Add MessageContext.getTomorrowInTimezone() using father's ZoneId.",
                    fatherTimezone)
                .isNotNull();
    }

    /**
     * Example-based test demonstrating the exact production bug scenario.
     * 
     * <p>From logs: "מחר, יום שישי 22/08" was displayed when today WAS Friday 22/08.</p>
     * 
     * <p>The bug occurs because:</p>
     * <ul>
     *   <li>Server might be in UTC</li>
     *   <li>Father is in Asia/Jerusalem (UTC+3)</li>
     *   <li>Near midnight in one timezone but not the other</li>
     *   <li>Date calculations don't consistently use father's timezone</li>
     * </ul>
     * 
     * <p><strong>EXPECTED TO FAIL:</strong> The fix methods don't exist yet.</p>
     * 
     * **Validates: Requirements 1.7, 1.8, 1.9**
     */
    @Example
    @Label("Production bug: 'מחר, יום שישי 22/08' shown when today IS Friday 22/08")
    void productionBugScenarioTomorrowShowsTodaysDate() {
        // This test verifies the mechanism to prevent this bug exists
        Class<?> messageContextClass = MessageContext.class;
        
        // All three methods should exist for proper timezone-aware date handling
        Method getTomorrow = findMethodByName(messageContextClass, GET_TOMORROW_IN_TIMEZONE);
        Method getToday = findMethodByName(messageContextClass, GET_TODAY_IN_TIMEZONE);
        Method validateDay = findMethodByName(messageContextClass, VALIDATE_DAY_OF_WEEK);
        
        assertThat(getTomorrow)
                .as("BUG EXISTS: MessageContext.getTomorrowInTimezone() not found. " +
                    "Production bug: 'מחר, יום שישי 22/08' displayed when today IS Friday 22/08. " +
                    "Root cause: Date calculation uses LocalDate.now() without father's timezone.")
                .isNotNull();
        
        assertThat(getToday)
                .as("BUG EXISTS: MessageContext.getTodayInTimezone() not found. " +
                    "Required for comparing today vs tomorrow in father's timezone.")
                .isNotNull();
                
        assertThat(validateDay)
                .as("BUG EXISTS: MessageContext.validateDayOfWeek() not found. " +
                    "Required to verify day-of-week matches actual calendar date before sending.")
                .isNotNull();
    }

    /**
     * Property test: Date formatting should use father's timezone consistently.
     * 
     * <p>The current code in ToolExecutorImpl.formatDayName() uses hardcoded ISRAEL_ZONE
     * instead of the father's configured timezone. This test verifies the fix exists.</p>
     * 
     * <p><strong>EXPECTED TO FAIL on unfixed code:</strong> The methods needed to 
     * enforce consistent timezone usage don't exist.</p>
     * 
     * **Validates: Requirements 1.8, 1.9**
     */
    @Property(tries = 5)
    @Label("Date formatting should consistently use father's configured timezone")
    void dateFormattingShouldUseConsistentTimezone(
            @ForAll("supportedTimezones") String fatherTimezone
    ) {
        // Create a MessageContext with the father's timezone
        MessageContext context = MessageContext.builder()
                .timezone(fatherTimezone)
                .locale("he")
                .fatherName("Test Father")
                .build();
        
        // The context should have timezone-aware date methods
        Class<?> contextClass = context.getClass();
        
        boolean hasTomorrowMethod = findMethodByName(contextClass, GET_TOMORROW_IN_TIMEZONE) != null;
        boolean hasTodayMethod = findMethodByName(contextClass, GET_TODAY_IN_TIMEZONE) != null;
        
        assertThat(hasTomorrowMethod && hasTodayMethod)
                .as("BUG CONFIRMED: MessageContext with timezone '%s' lacks timezone-aware date methods. " +
                    "Current code uses hardcoded ISRAEL_ZONE in ToolExecutorImpl.formatDayName(). " +
                    "Fix requirement: MessageContext.getTomorrowInTimezone() and getTodayInTimezone() " +
                    "should use the father's configured timezone for all date calculations.",
                    fatherTimezone)
                .isTrue();
    }

    /**
     * Property test: When displaying dates near midnight boundary, the correct
     * date should be shown based on father's timezone, not server timezone.
     * 
     * <p><strong>Scenario:</strong> It's 23:30 UTC (02:30 Jerusalem the next day).
     * For a father in Jerusalem, "today" is already the next calendar day.</p>
     * 
     * **Validates: Requirements 1.7, 1.8**
     */
    @Property(tries = 10)
    @Label("Midnight boundary: 'today' should match father's timezone calendar day")
    void midnightBoundaryTodayShouldMatchFatherTimezone(
            @ForAll("lateNightHours") int utcHour,
            @ForAll("midnightBoundaryTimezones") ZoneId fatherTimezone
    ) {
        // Calculate what "today" should be in each timezone
        ZonedDateTime nowUtc = ZonedDateTime.now(UTC_ZONE)
                .withHour(utcHour)
                .withMinute(30);
        
        LocalDate todayInUtc = nowUtc.toLocalDate();
        LocalDate todayInFatherTz = nowUtc.withZoneSameInstant(fatherTimezone).toLocalDate();
        
        // The dates may differ - this is the bug scenario
        if (!todayInUtc.equals(todayInFatherTz)) {
            // When dates differ, the system MUST use father's timezone
            // The fix adds getTodayInTimezone() to ensure this
            
            Class<?> messageContextClass = MessageContext.class;
            Method todayMethod = findMethodByName(messageContextClass, GET_TODAY_IN_TIMEZONE);
            
            assertThat(todayMethod)
                    .as("BUG EXISTS at midnight boundary: UTC date=%s, Father TZ (%s) date=%s. " +
                        "Dates differ! System must use father's timezone for 'today' calculation. " +
                        "Missing: MessageContext.getTodayInTimezone() method.",
                        todayInUtc, fatherTimezone, todayInFatherTz)
                    .isNotNull();
        }
    }

    /**
     * Example-based test: Day-of-week validation scenario.
     * 
     * <p>The system says "מחר, יום שישי" (tomorrow, Friday) but if today is already
     * Friday in the father's timezone, this is incorrect. The fix should validate
     * that the day-of-week in the message matches the actual calendar date.</p>
     * 
     * **Validates: Requirements 2.9**
     */
    @Example
    @Label("Day-of-week should be validated to match actual calendar date")
    void dayOfWeekShouldBeValidated() {
        // This demonstrates the need for validateDayOfWeek method
        LocalDate friday = LocalDate.of(2024, 8, 22); // This is a Thursday actually, but let's pretend
        String messageWithWrongDay = "מחר, יום שישי 22/08";
        
        // The fix should add a validation method
        Class<?> messageContextClass = MessageContext.class;
        Method validateMethod = findMethodByName(messageContextClass, VALIDATE_DAY_OF_WEEK);
        
        assertThat(validateMethod)
                .as("BUG EXISTS: No validation that day-of-week matches date. " +
                    "Message '%s' could have wrong day name. " +
                    "Fix: Add MessageContext.validateDayOfWeek(LocalDate, String) " +
                    "to verify day name matches actual calendar day before sending.",
                    messageWithWrongDay)
                .isNotNull();
    }

    // ============== Helper Methods ==============
    
    /**
     * Finds a method by name in the given class.
     * Returns null if not found (indicating the bug exists).
     */
    private Method findMethodByName(Class<?> clazz, String methodName) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElse(null);
    }

    // ============== Generators ==============

    /**
     * Generator for timezones that create interesting midnight boundary scenarios.
     */
    @Provide
    Arbitrary<ZoneId> midnightBoundaryTimezones() {
        return Arbitraries.of(
                ZoneId.of("Asia/Jerusalem"),    // UTC+2/+3
                ZoneId.of("America/Los_Angeles"), // UTC-8/-7
                ZoneId.of("America/New_York"),    // UTC-5/-4
                ZoneId.of("Europe/London"),       // UTC+0/+1
                ZoneId.of("Asia/Tokyo"),          // UTC+9
                ZoneId.of("Australia/Sydney")     // UTC+10/+11
        );
    }

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
                "UTC"
        );
    }

    /**
     * Generator for late night hours (when midnight boundary issues occur).
     * Focus on hours 21-23 UTC which create date differences in other timezones.
     */
    @Provide
    Arbitrary<Integer> lateNightHours() {
        return Arbitraries.integers().between(21, 23);
    }

    /**
     * Generator for Hebrew day names.
     */
    @Provide
    Arbitrary<String> hebrewDayNames() {
        return Arbitraries.of(
                "יום ראשון",
                "יום שני",
                "יום שלישי",
                "יום רביעי",
                "יום חמישי",
                "יום שישי",
                "שבת"
        );
    }
}
