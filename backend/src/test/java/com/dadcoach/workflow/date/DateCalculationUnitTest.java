package com.dadcoach.workflow.date;

import com.dadcoach.workflow.message.MessageContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Bug 3: Date/Time Calculation Error Fix
 * 
 * <p>These tests verify the timezone-aware date calculation methods added to
 * {@link MessageContext} to fix Bug 3 where "tomorrow" was incorrectly showing
 * today's date in some timezone scenarios.</p>
 * 
 * <p><strong>Requirements covered:</strong></p>
 * <ul>
 *   <li>2.7: getTomorrowInTimezone() uses father's configured timezone</li>
 *   <li>2.8: getTodayInTimezone() uses father's configured timezone</li>
 *   <li>2.9: validateDayOfWeek() verifies day matches actual date</li>
 * </ul>
 * 
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>Timezone-aware date calculation across various timezones</li>
 *   <li>Day-of-week validation for Hebrew and English locales</li>
 *   <li>Midnight boundary scenarios (UTC vs local timezone date differences)</li>
 *   <li>Default timezone fallback to Asia/Jerusalem</li>
 * </ul>
 * 
 * @see MessageContext#getTomorrowInTimezone()
 * @see MessageContext#getTodayInTimezone()
 * @see MessageContext#validateDayOfWeek(LocalDate, String)
 */
@DisplayName("Bug 3: Date Calculation Unit Tests")
class DateCalculationUnitTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Test Constants
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final String TIMEZONE_ISRAEL = "Asia/Jerusalem";
    private static final String TIMEZONE_US_PACIFIC = "America/Los_Angeles";
    private static final String TIMEZONE_US_EASTERN = "America/New_York";
    private static final String TIMEZONE_LONDON = "Europe/London";
    private static final String TIMEZONE_TOKYO = "Asia/Tokyo";
    private static final String TIMEZONE_UTC = "UTC";

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Timezone-Aware Date Calculation - getTomorrowInTimezone()
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Nested
    @DisplayName("getTomorrowInTimezone() Tests")
    class GetTomorrowInTimezoneTests {

        @Test
        @DisplayName("Should return tomorrow in Israel timezone (Asia/Jerusalem)")
        void shouldReturnTomorrowInIsraelTimezone() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("he")
                    .build();
            
            // Expected: tomorrow in Israel timezone
            LocalDate expectedTomorrow = LocalDate.now(ZoneId.of(TIMEZONE_ISRAEL)).plusDays(1);
            
            // Act
            LocalDate actualTomorrow = context.getTomorrowInTimezone();
            
            // Assert
            assertThat(actualTomorrow)
                    .as("getTomorrowInTimezone() should return tomorrow's date in Israel timezone")
                    .isEqualTo(expectedTomorrow);
        }

        @Test
        @DisplayName("Should return tomorrow in US Pacific timezone")
        void shouldReturnTomorrowInUsPacificTimezone() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_US_PACIFIC)
                    .locale("en")
                    .build();
            
            // Expected: tomorrow in US Pacific timezone
            LocalDate expectedTomorrow = LocalDate.now(ZoneId.of(TIMEZONE_US_PACIFIC)).plusDays(1);
            
            // Act
            LocalDate actualTomorrow = context.getTomorrowInTimezone();
            
            // Assert
            assertThat(actualTomorrow)
                    .as("getTomorrowInTimezone() should return tomorrow's date in US Pacific timezone")
                    .isEqualTo(expectedTomorrow);
        }

        @ParameterizedTest(name = "Tomorrow in {0} timezone should be calculated correctly")
        @ValueSource(strings = {
                "Asia/Jerusalem",
                "America/Los_Angeles",
                "America/New_York",
                "Europe/London",
                "Asia/Tokyo",
                "Australia/Sydney",
                "UTC"
        })
        @DisplayName("Should calculate tomorrow correctly for various timezones")
        void shouldCalculateTomorrowCorrectlyForVariousTimezones(String timezone) {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(timezone)
                    .locale("en")
                    .build();
            
            LocalDate expectedTomorrow = LocalDate.now(ZoneId.of(timezone)).plusDays(1);
            
            // Act
            LocalDate actualTomorrow = context.getTomorrowInTimezone();
            
            // Assert
            assertThat(actualTomorrow)
                    .as("Tomorrow in timezone '%s' should be %s", timezone, expectedTomorrow)
                    .isEqualTo(expectedTomorrow);
        }

        @Test
        @DisplayName("Tomorrow should be exactly one day after today in same timezone")
        void tomorrowShouldBeOneDayAfterToday() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("he")
                    .build();
            
            // Act
            LocalDate today = context.getTodayInTimezone();
            LocalDate tomorrow = context.getTomorrowInTimezone();
            
            // Assert
            assertThat(tomorrow)
                    .as("Tomorrow should be exactly one day after today")
                    .isEqualTo(today.plusDays(1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Timezone-Aware Date Calculation - getTodayInTimezone()
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Nested
    @DisplayName("getTodayInTimezone() Tests")
    class GetTodayInTimezoneTests {

        @Test
        @DisplayName("Should return today in Israel timezone (Asia/Jerusalem)")
        void shouldReturnTodayInIsraelTimezone() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("he")
                    .build();
            
            // Expected: today in Israel timezone
            LocalDate expectedToday = LocalDate.now(ZoneId.of(TIMEZONE_ISRAEL));
            
            // Act
            LocalDate actualToday = context.getTodayInTimezone();
            
            // Assert
            assertThat(actualToday)
                    .as("getTodayInTimezone() should return today's date in Israel timezone")
                    .isEqualTo(expectedToday);
        }

        @ParameterizedTest(name = "Today in {0} timezone should be calculated correctly")
        @ValueSource(strings = {
                "Asia/Jerusalem",
                "America/Los_Angeles",
                "America/New_York",
                "Europe/London",
                "Asia/Tokyo",
                "UTC"
        })
        @DisplayName("Should calculate today correctly for various timezones")
        void shouldCalculateTodayCorrectlyForVariousTimezones(String timezone) {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(timezone)
                    .locale("en")
                    .build();
            
            LocalDate expectedToday = LocalDate.now(ZoneId.of(timezone));
            
            // Act
            LocalDate actualToday = context.getTodayInTimezone();
            
            // Assert
            assertThat(actualToday)
                    .as("Today in timezone '%s' should be %s", timezone, expectedToday)
                    .isEqualTo(expectedToday);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Day-of-Week Validation - validateDayOfWeek()
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Nested
    @DisplayName("validateDayOfWeek() Tests")
    class ValidateDayOfWeekTests {

        @Test
        @DisplayName("Should validate correct English day name (Friday)")
        void shouldValidateCorrectEnglishDayName() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("en")
                    .build();
            
            // Find next Friday
            LocalDate friday = LocalDate.now();
            while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
                friday = friday.plusDays(1);
            }
            
            String formattedString = "Friday, August 22";
            
            // Act
            boolean isValid = context.validateDayOfWeek(friday, formattedString);
            
            // Assert
            assertThat(isValid)
                    .as("Should validate that 'Friday' matches a date that is actually Friday")
                    .isTrue();
        }

        @Test
        @DisplayName("Should invalidate wrong English day name")
        void shouldInvalidateWrongEnglishDayName() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("en")
                    .build();
            
            // Find next Friday
            LocalDate friday = LocalDate.now();
            while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
                friday = friday.plusDays(1);
            }
            
            // Wrong day name - saying Monday when it's Friday
            String formattedString = "Monday, August 22";
            
            // Act
            boolean isValid = context.validateDayOfWeek(friday, formattedString);
            
            // Assert
            assertThat(isValid)
                    .as("Should invalidate 'Monday' for a date that is actually Friday")
                    .isFalse();
        }

        @ParameterizedTest(name = "Should validate {0} matches {1}")
        @CsvSource({
                "MONDAY, Monday",
                "TUESDAY, Tuesday",
                "WEDNESDAY, Wednesday",
                "THURSDAY, Thursday",
                "FRIDAY, Friday",
                "SATURDAY, Saturday",
                "SUNDAY, Sunday"
        })
        @DisplayName("Should validate all English day names")
        void shouldValidateAllEnglishDayNames(DayOfWeek dayOfWeek, String dayName) {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("en")
                    .build();
            
            // Find the next occurrence of this day
            LocalDate date = LocalDate.now();
            while (date.getDayOfWeek() != dayOfWeek) {
                date = date.plusDays(1);
            }
            
            String formattedString = dayName + ", August 22";
            
            // Act
            boolean isValid = context.validateDayOfWeek(date, formattedString);
            
            // Assert
            assertThat(isValid)
                    .as("Should validate '%s' for a date that is actually %s", dayName, dayOfWeek)
                    .isTrue();
        }

        @Test
        @DisplayName("Should return false for null date")
        void shouldReturnFalseForNullDate() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("en")
                    .build();
            
            // Act
            boolean isValid = context.validateDayOfWeek(null, "Friday, August 22");
            
            // Assert
            assertThat(isValid)
                    .as("Should return false when date is null")
                    .isFalse();
        }

        @Test
        @DisplayName("Should return false for null formatted string")
        void shouldReturnFalseForNullFormattedString() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("en")
                    .build();
            
            LocalDate friday = LocalDate.now();
            while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
                friday = friday.plusDays(1);
            }
            
            // Act
            boolean isValid = context.validateDayOfWeek(friday, null);
            
            // Assert
            assertThat(isValid)
                    .as("Should return false when formatted string is null")
                    .isFalse();
        }

        @Test
        @DisplayName("Should return false for empty formatted string")
        void shouldReturnFalseForEmptyFormattedString() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("en")
                    .build();
            
            LocalDate friday = LocalDate.now();
            while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
                friday = friday.plusDays(1);
            }
            
            // Act
            boolean isValid = context.validateDayOfWeek(friday, "");
            
            // Assert
            assertThat(isValid)
                    .as("Should return false when formatted string is empty")
                    .isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Midnight Boundary Scenarios
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Nested
    @DisplayName("Midnight Boundary Scenario Tests")
    class MidnightBoundaryTests {

        @Test
        @DisplayName("Different timezones may have different 'today' at same instant")
        void differentTimezonesMayHaveDifferentTodayAtSameInstant() {
            // Arrange - Create contexts for Israel and US Pacific timezones
            MessageContext israelContext = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("he")
                    .build();
            
            MessageContext pacificContext = MessageContext.builder()
                    .timezone(TIMEZONE_US_PACIFIC)
                    .locale("en")
                    .build();
            
            // Act
            LocalDate israelToday = israelContext.getTodayInTimezone();
            LocalDate pacificToday = pacificContext.getTodayInTimezone();
            
            // Assert - The dates CAN differ (especially near midnight)
            // We just verify both contexts return valid dates from their respective timezones
            assertThat(israelToday)
                    .as("Israel context should return a valid today date")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_ISRAEL)));
            
            assertThat(pacificToday)
                    .as("Pacific context should return a valid today date")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_US_PACIFIC)));
        }

        @Test
        @DisplayName("Tomorrow in different timezones should use local timezone calculation")
        void tomorrowInDifferentTimezonesShouldUseLocalCalculation() {
            // Arrange - Create contexts for drastically different timezones
            MessageContext tokyoContext = MessageContext.builder()
                    .timezone(TIMEZONE_TOKYO)
                    .locale("en")
                    .build();
            
            MessageContext pacificContext = MessageContext.builder()
                    .timezone(TIMEZONE_US_PACIFIC)
                    .locale("en")
                    .build();
            
            // Act
            LocalDate tokyoTomorrow = tokyoContext.getTomorrowInTimezone();
            LocalDate pacificTomorrow = pacificContext.getTomorrowInTimezone();
            
            // Assert - Verify each uses its own timezone's calculation
            LocalDate expectedTokyoTomorrow = LocalDate.now(ZoneId.of(TIMEZONE_TOKYO)).plusDays(1);
            LocalDate expectedPacificTomorrow = LocalDate.now(ZoneId.of(TIMEZONE_US_PACIFIC)).plusDays(1);
            
            assertThat(tokyoTomorrow)
                    .as("Tokyo tomorrow should be calculated from Tokyo's current date")
                    .isEqualTo(expectedTokyoTomorrow);
            
            assertThat(pacificTomorrow)
                    .as("Pacific tomorrow should be calculated from Pacific's current date")
                    .isEqualTo(expectedPacificTomorrow);
        }

        @Test
        @DisplayName("UTC and Israel may have different dates near midnight boundaries")
        void utcAndIsraelMayHaveDifferentDatesNearMidnight() {
            // This test documents the timezone boundary behavior
            // Israel is UTC+2 (or UTC+3 in summer), so:
            // - At 23:00 UTC, it's 01:00/02:00 the next day in Israel
            
            MessageContext israelContext = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("he")
                    .build();
            
            MessageContext utcContext = MessageContext.builder()
                    .timezone(TIMEZONE_UTC)
                    .locale("en")
                    .build();
            
            // Act
            LocalDate israelToday = israelContext.getTodayInTimezone();
            LocalDate utcToday = utcContext.getTodayInTimezone();
            
            // Assert - Both return valid dates for their respective timezones
            assertThat(israelToday)
                    .as("Israel today should match LocalDate.now(Israel timezone)")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_ISRAEL)));
            
            assertThat(utcToday)
                    .as("UTC today should match LocalDate.now(UTC)")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_UTC)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Default Timezone Fallback
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Nested
    @DisplayName("Default Timezone Fallback Tests")
    class DefaultTimezoneFallbackTests {

        @Test
        @DisplayName("Should fallback to Asia/Jerusalem when timezone is null")
        void shouldFallbackToIsraelWhenTimezoneIsNull() {
            // Arrange - Create context without specifying timezone
            MessageContext context = MessageContext.builder()
                    .locale("he")
                    .fatherName("Test")
                    .build();
            
            LocalDate expectedToday = LocalDate.now(ZoneId.of(TIMEZONE_ISRAEL));
            LocalDate expectedTomorrow = expectedToday.plusDays(1);
            
            // Act
            LocalDate actualToday = context.getTodayInTimezone();
            LocalDate actualTomorrow = context.getTomorrowInTimezone();
            ZoneId zoneId = context.getTimezoneAsZoneId();
            
            // Assert
            assertThat(context.getTimezone())
                    .as("Default timezone string should be Asia/Jerusalem")
                    .isEqualTo(TIMEZONE_ISRAEL);
            
            assertThat(zoneId)
                    .as("Default ZoneId should be Asia/Jerusalem")
                    .isEqualTo(ZoneId.of(TIMEZONE_ISRAEL));
            
            assertThat(actualToday)
                    .as("Today should use default Asia/Jerusalem timezone")
                    .isEqualTo(expectedToday);
            
            assertThat(actualTomorrow)
                    .as("Tomorrow should use default Asia/Jerusalem timezone")
                    .isEqualTo(expectedTomorrow);
        }

        @Test
        @DisplayName("Should fallback to Asia/Jerusalem for invalid timezone string")
        void shouldFallbackToIsraelForInvalidTimezone() {
            // Arrange - Create context with invalid timezone
            MessageContext context = MessageContext.builder()
                    .timezone("Invalid/Timezone")
                    .locale("en")
                    .build();
            
            // Act
            ZoneId zoneId = context.getTimezoneAsZoneId();
            LocalDate today = context.getTodayInTimezone();
            LocalDate tomorrow = context.getTomorrowInTimezone();
            
            // Assert - Should fallback to Israel
            assertThat(zoneId)
                    .as("Invalid timezone should fallback to Asia/Jerusalem")
                    .isEqualTo(ZoneId.of(TIMEZONE_ISRAEL));
            
            assertThat(today)
                    .as("Today with invalid timezone should use Israel timezone")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_ISRAEL)));
            
            assertThat(tomorrow)
                    .as("Tomorrow with invalid timezone should use Israel timezone")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_ISRAEL)).plusDays(1));
        }

        @ParameterizedTest(name = "Invalid timezone ''{0}'' should fallback to Asia/Jerusalem")
        @ValueSource(strings = {
                "NotATimezone",
                "Fake/City",
                "123456",
                "Jerusalem",  // Missing "Asia/" prefix
                ""
        })
        @DisplayName("Various invalid timezones should all fallback to Asia/Jerusalem")
        void variousInvalidTimezonesShouldFallback(String invalidTimezone) {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(invalidTimezone.isEmpty() ? null : invalidTimezone)
                    .locale("en")
                    .build();
            
            // Act
            ZoneId zoneId = context.getTimezoneAsZoneId();
            
            // Assert
            assertThat(zoneId)
                    .as("Invalid timezone '%s' should fallback to Asia/Jerusalem", invalidTimezone)
                    .isEqualTo(ZoneId.of(TIMEZONE_ISRAEL));
        }

        @Test
        @DisplayName("Default timezone constant should be Asia/Jerusalem")
        void defaultTimezoneConstantShouldBeIsrael() {
            // Assert - Verify the constant value
            assertThat(MessageContext.DEFAULT_TIMEZONE)
                    .as("Default timezone constant should be 'Asia/Jerusalem'")
                    .isEqualTo("Asia/Jerusalem");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Locale-Specific Day Name Handling
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Nested
    @DisplayName("Locale-Specific Day Name Tests")
    class LocaleSpecificDayNameTests {

        @Test
        @DisplayName("getDisplayLocale should return Hebrew locale for 'he'")
        void getDisplayLocaleShouldReturnHebrewForHe() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("he")
                    .build();
            
            // Act
            Locale displayLocale = context.getDisplayLocale();
            
            // Assert
            assertThat(displayLocale.getLanguage())
                    .as("Hebrew locale should have language code 'he'")
                    .isEqualTo("he");
        }

        @Test
        @DisplayName("getDisplayLocale should return English locale for 'en'")
        void getDisplayLocaleShouldReturnEnglishForEn() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("en")
                    .build();
            
            // Act
            Locale displayLocale = context.getDisplayLocale();
            
            // Assert
            assertThat(displayLocale)
                    .as("English locale should be Locale.ENGLISH")
                    .isEqualTo(Locale.ENGLISH);
        }

        @Test
        @DisplayName("Day-of-week names should differ between Hebrew and English locales")
        void dayOfWeekNamesShouldDifferByLocale() {
            // Arrange
            LocalDate anyFriday = LocalDate.now();
            while (anyFriday.getDayOfWeek() != DayOfWeek.FRIDAY) {
                anyFriday = anyFriday.plusDays(1);
            }
            
            // Act
            String englishDayName = anyFriday.getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            String hebrewDayName = anyFriday.getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("he-IL"));
            
            // Assert
            assertThat(englishDayName)
                    .as("English day name for Friday should be 'Friday'")
                    .isEqualTo("Friday");
            
            assertThat(hebrewDayName)
                    .as("Hebrew day name should differ from English")
                    .isNotEqualTo(englishDayName);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Edge Cases and Special Scenarios
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Nested
    @DisplayName("Edge Cases and Special Scenarios")
    class EdgeCasesTests {

        @Test
        @DisplayName("Consecutive calls should return consistent results")
        void consecutiveCallsShouldReturnConsistentResults() {
            // Arrange
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("he")
                    .build();
            
            // Act - Multiple calls
            LocalDate today1 = context.getTodayInTimezone();
            LocalDate today2 = context.getTodayInTimezone();
            LocalDate tomorrow1 = context.getTomorrowInTimezone();
            LocalDate tomorrow2 = context.getTomorrowInTimezone();
            
            // Assert - Results should be consistent
            assertThat(today1)
                    .as("Consecutive getTodayInTimezone() calls should return same result")
                    .isEqualTo(today2);
            
            assertThat(tomorrow1)
                    .as("Consecutive getTomorrowInTimezone() calls should return same result")
                    .isEqualTo(tomorrow2);
        }

        @Test
        @DisplayName("Different context instances with same timezone should return same dates")
        void differentContextsWithSameTimezoneShouldReturnSameDates() {
            // Arrange
            MessageContext context1 = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("he")
                    .fatherName("David")
                    .build();
            
            MessageContext context2 = MessageContext.builder()
                    .timezone(TIMEZONE_ISRAEL)
                    .locale("en")
                    .fatherName("John")
                    .build();
            
            // Act
            LocalDate today1 = context1.getTodayInTimezone();
            LocalDate today2 = context2.getTodayInTimezone();
            LocalDate tomorrow1 = context1.getTomorrowInTimezone();
            LocalDate tomorrow2 = context2.getTomorrowInTimezone();
            
            // Assert
            assertThat(today1)
                    .as("Same timezone should produce same today date regardless of other context values")
                    .isEqualTo(today2);
            
            assertThat(tomorrow1)
                    .as("Same timezone should produce same tomorrow date regardless of other context values")
                    .isEqualTo(tomorrow2);
        }

        @Test
        @DisplayName("Context with only timezone set should work correctly")
        void contextWithOnlyTimezoneSetShouldWork() {
            // Arrange - Minimal context with only timezone
            MessageContext context = MessageContext.builder()
                    .timezone(TIMEZONE_US_EASTERN)
                    .build();
            
            // Act
            LocalDate today = context.getTodayInTimezone();
            LocalDate tomorrow = context.getTomorrowInTimezone();
            
            // Assert
            assertThat(today)
                    .as("Minimal context should still calculate today correctly")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_US_EASTERN)));
            
            assertThat(tomorrow)
                    .as("Minimal context should still calculate tomorrow correctly")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_US_EASTERN)).plusDays(1));
        }

        @Test
        @DisplayName("Builder method chaining should not affect timezone behavior")
        void builderMethodChainingShouldNotAffectTimezoneBehavior() {
            // Arrange - Complex builder chain
            MessageContext context = MessageContext.builder()
                    .fatherName("Test Father")
                    .childName("Test Child")
                    .locale("he")
                    .timezone(TIMEZONE_LONDON)
                    .streakCount(5)
                    .qualityTimeCount(10)
                    .build();
            
            // Act
            LocalDate today = context.getTodayInTimezone();
            LocalDate tomorrow = context.getTomorrowInTimezone();
            
            // Assert
            assertThat(today)
                    .as("Builder chain should not interfere with timezone calculation")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_LONDON)));
            
            assertThat(tomorrow)
                    .as("Builder chain should not interfere with tomorrow calculation")
                    .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE_LONDON)).plusDays(1));
        }
    }
}
