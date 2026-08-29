package com.dadcoach.workspace.commitment;

import com.dadcoach.common.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts commitment details from conversation messages.
 * 
 * Parses Hebrew and English messages to identify:
 * - Day of week (יום ראשון, Sunday, etc.)
 * - Time (17:00, 5pm, חמש, etc.)
 * - Duration hints (שעה, חצי שעה, etc.)
 * 
 * This is used by the conversation flow to automatically create commitments
 * when a father specifies when they'll spend time with their child.
 */
@Component
public class CommitmentExtractor {

    private static final Logger log = LoggerFactory.getLogger(CommitmentExtractor.class);

    // Hebrew day patterns
    private static final Pattern HEBREW_DAY_PATTERN = Pattern.compile(
            "(יום\\s*)?(ראשון|שני|שלישי|רביעי|חמישי|שישי|שבת)|" +
            "(היום|מחר|מחרתיים)",
            Pattern.UNICODE_CASE
    );

    // English day patterns
    private static final Pattern ENGLISH_DAY_PATTERN = Pattern.compile(
            "(sunday|monday|tuesday|wednesday|thursday|friday|saturday)|" +
            "(today|tomorrow)",
            Pattern.CASE_INSENSITIVE
    );

    // Time patterns (24h, 12h, Hebrew words)
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(\\d{1,2})[:\\.]?(\\d{2})?\\s*(am|pm|בבוקר|בצהריים|בערב|בלילה)?|" +
            "ב(שש|שבע|שמונה|תשע|עשר|אחת עשרה|שתים עשרה|אחת|שתיים|שלוש|ארבע|חמש)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /**
     * Attempts to extract a commitment from a message.
     * 
     * @param message The user's message
     * @param fatherTimezone The father's timezone
     * @return Extracted commitment info, or empty if no commitment found
     */
    public Optional<ExtractedCommitment> extract(String message, String fatherTimezone) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalized = message.toLowerCase().trim();
        ZoneId zone = parseTimezone(fatherTimezone);

        // Try to extract day
        Optional<LocalDate> date = extractDate(normalized, zone);
        if (date.isEmpty()) {
            return Optional.empty();
        }

        // Try to extract time
        Optional<LocalTime> time = extractTime(normalized);
        if (time.isEmpty()) {
            return Optional.empty();
        }

        // Combine date and time
        ZonedDateTime scheduledAt = ZonedDateTime.of(date.get(), time.get(), zone);
        
        // Don't accept times in the past
        if (scheduledAt.toInstant().isBefore(Instant.now())) {
            // If today and time passed, try tomorrow
            if (date.get().equals(LocalDate.now(zone))) {
                scheduledAt = scheduledAt.plusDays(1);
            } else {
                log.debug("Extracted time is in the past: {}", scheduledAt);
                return Optional.empty();
            }
        }

        return Optional.of(new ExtractedCommitment(
                scheduledAt.toInstant(),
                date.get(),
                time.get(),
                extractDuration(normalized)
        ));
    }

    /**
     * Checks if a message contains a time commitment.
     * Quick check before running full extraction.
     */
    public boolean containsTimeCommitment(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        
        // Check for common commitment indicators
        return lower.contains("ב-") || // Hebrew time prefix
               lower.contains("בשעה") ||
               lower.contains("at ") ||
               TIME_PATTERN.matcher(lower).find() ||
               HEBREW_DAY_PATTERN.matcher(lower).find() ||
               ENGLISH_DAY_PATTERN.matcher(lower).find();
    }

    private Optional<LocalDate> extractDate(String message, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);

        // Check Hebrew days
        Matcher hebrewMatcher = HEBREW_DAY_PATTERN.matcher(message);
        if (hebrewMatcher.find()) {
            String match = hebrewMatcher.group();
            
            if (match.contains("היום")) {
                return Optional.of(today);
            }
            if (match.contains("מחר")) {
                return Optional.of(today.plusDays(1));
            }
            if (match.contains("מחרתיים")) {
                return Optional.of(today.plusDays(2));
            }
            
            DayOfWeek dow = parseHebrewDay(match);
            if (dow != null) {
                return Optional.of(nextOccurrence(today, dow));
            }
        }

        // Check English days
        Matcher englishMatcher = ENGLISH_DAY_PATTERN.matcher(message);
        if (englishMatcher.find()) {
            String match = englishMatcher.group().toLowerCase();
            
            if (match.equals("today")) {
                return Optional.of(today);
            }
            if (match.equals("tomorrow")) {
                return Optional.of(today.plusDays(1));
            }
            
            DayOfWeek dow = parseEnglishDay(match);
            if (dow != null) {
                return Optional.of(nextOccurrence(today, dow));
            }
        }

        return Optional.empty();
    }

    private Optional<LocalTime> extractTime(String message) {
        Matcher matcher = TIME_PATTERN.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String match = matcher.group();
        
        // Try numeric time (e.g., "17:00", "5pm")
        Pattern numericPattern = Pattern.compile("(\\d{1,2})[:\\.]?(\\d{2})?\\s*(am|pm|בבוקר|בצהריים|בערב|בלילה)?",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher numericMatcher = numericPattern.matcher(match);
        
        if (numericMatcher.find()) {
            int hour = Integer.parseInt(numericMatcher.group(1));
            int minute = numericMatcher.group(2) != null ? Integer.parseInt(numericMatcher.group(2)) : 0;
            String period = numericMatcher.group(3);
            
            // Adjust for AM/PM or Hebrew time-of-day
            if (period != null) {
                String periodLower = period.toLowerCase();
                if (periodLower.equals("pm") || periodLower.contains("ערב") || periodLower.contains("לילה")) {
                    if (hour < 12) hour += 12;
                } else if (periodLower.equals("am") || periodLower.contains("בוקר")) {
                    if (hour == 12) hour = 0;
                } else if (periodLower.contains("צהריים")) {
                    if (hour < 12) hour += 12;
                }
            } else if (hour >= 1 && hour <= 6) {
                // Assume afternoon/evening for small numbers without AM/PM
                hour += 12;
            }
            
            if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
                return Optional.of(LocalTime.of(hour, minute));
            }
        }

        // Try Hebrew word time (e.g., "בחמש")
        LocalTime hebrewTime = parseHebrewWordTime(match);
        if (hebrewTime != null) {
            return Optional.of(hebrewTime);
        }

        return Optional.empty();
    }

    private int extractDuration(String message) {
        if (message.contains("שעה") && !message.contains("חצי")) {
            return 60;
        }
        if (message.contains("חצי שעה") || message.contains("30 דקות") || message.contains("half hour")) {
            return 30;
        }
        if (message.contains("שעתיים") || message.contains("2 hours")) {
            return 120;
        }
        if (message.contains("רבע שעה") || message.contains("15 min")) {
            return 15;
        }
        return 30; // Default
    }

    private DayOfWeek parseHebrewDay(String match) {
        if (match.contains("ראשון")) return DayOfWeek.SUNDAY;
        if (match.contains("שני")) return DayOfWeek.MONDAY;
        if (match.contains("שלישי")) return DayOfWeek.TUESDAY;
        if (match.contains("רביעי")) return DayOfWeek.WEDNESDAY;
        if (match.contains("חמישי")) return DayOfWeek.THURSDAY;
        if (match.contains("שישי")) return DayOfWeek.FRIDAY;
        if (match.contains("שבת")) return DayOfWeek.SATURDAY;
        return null;
    }

    private DayOfWeek parseEnglishDay(String match) {
        return switch (match.toLowerCase()) {
            case "sunday" -> DayOfWeek.SUNDAY;
            case "monday" -> DayOfWeek.MONDAY;
            case "tuesday" -> DayOfWeek.TUESDAY;
            case "wednesday" -> DayOfWeek.WEDNESDAY;
            case "thursday" -> DayOfWeek.THURSDAY;
            case "friday" -> DayOfWeek.FRIDAY;
            case "saturday" -> DayOfWeek.SATURDAY;
            default -> null;
        };
    }

    private LocalTime parseHebrewWordTime(String match) {
        // Map Hebrew number words to hours
        if (match.contains("שש")) return LocalTime.of(18, 0);
        if (match.contains("שבע")) return LocalTime.of(19, 0);
        if (match.contains("שמונה")) return LocalTime.of(20, 0);
        if (match.contains("תשע")) return LocalTime.of(21, 0);
        if (match.contains("עשר") && !match.contains("עשרה")) return LocalTime.of(22, 0);
        if (match.contains("אחת עשרה")) return LocalTime.of(11, 0);
        if (match.contains("שתים עשרה")) return LocalTime.of(12, 0);
        if (match.contains("אחת")) return LocalTime.of(13, 0);
        if (match.contains("שתיים")) return LocalTime.of(14, 0);
        if (match.contains("שלוש")) return LocalTime.of(15, 0);
        if (match.contains("ארבע")) return LocalTime.of(16, 0);
        if (match.contains("חמש")) return LocalTime.of(17, 0);
        return null;
    }

    private LocalDate nextOccurrence(LocalDate from, DayOfWeek dow) {
        LocalDate result = from;
        while (result.getDayOfWeek() != dow) {
            result = result.plusDays(1);
        }
        return result;
    }

    private ZoneId parseTimezone(String timezone) {
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone);
            } catch (Exception e) {
                log.warn("Invalid timezone: {}", timezone);
            }
        }
        return AppConstants.DEFAULT_ZONE_ID;
    }

    /**
     * Result of commitment extraction.
     */
    public record ExtractedCommitment(
            Instant scheduledAt,
            LocalDate date,
            LocalTime time,
            int durationMinutes
    ) {}
}
