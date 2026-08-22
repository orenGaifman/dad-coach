package com.dadcoach.workflow.message;

import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.workflow.Belt;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable context object carrying all data needed for message generation.
 * 
 * <p>The MessageContext provides structured data to the {@link MessageGenerator},
 * which uses it to generate natural language messages. The MessageGenerator ONLY
 * produces text and does NOT make decisions about state transitions or business logic.</p>
 * 
 * <p>Messages are generated in the father's preferred language (English or Hebrew)
 * using the locale field. The default locale is "en" (English).</p>
 * 
 * <p>Implements Requirement 10.2 from the deterministic-workflow-engine spec:</p>
 * <ul>
 *   <li>Contains the message type (determines template/structure)</li>
 *   <li>Contains required data fields for that message type</li>
 *   <li>Contains target language (locale: "en" or "he")</li>
 * </ul>
 * 
 * <p>Uses the Builder pattern for flexible, readable construction:</p>
 * <pre>{@code
 * MessageContext context = MessageContext.builder()
 *     .messageType(MessageType.WELCOME_GREETING)
 *     .fatherName("David")
 *     .locale("he")
 *     .timezone("Asia/Jerusalem")
 *     .build();
 * }</pre>
 * 
 * @see MessageGenerator
 * @see MessageType
 */
public final class MessageContext {
    
    // ─── Constants ───────────────────────────────────────────────────────────
    
    /** Default locale when not specified. */
    public static final String DEFAULT_LOCALE = "en";
    
    /** Default timezone when not specified. */
    public static final String DEFAULT_TIMEZONE = "Asia/Jerusalem";
    
    /** English locale code. */
    public static final String LOCALE_ENGLISH = "en";
    
    /** Hebrew locale code. */
    public static final String LOCALE_HEBREW = "he";
    
    // ─── Core Fields ─────────────────────────────────────────────────────────
    
    private final MessageType messageType;
    private final String fatherName;
    private final String locale;
    private final String timezone;
    
    // ─── Child Context ───────────────────────────────────────────────────────
    
    private final String childName;
    private final Integer childAge;
    private final Long childId;
    
    // ─── Scheduling Context ──────────────────────────────────────────────────
    
    private final List<AvailableSlot> timeSlots;
    private final Instant scheduledStart;
    private final Instant scheduledEnd;
    private final String scheduledTimeFormatted;
    
    // ─── Progress and Gamification Context ───────────────────────────────────
    
    private final Integer streakCount;
    private final Integer longestStreak;
    private final Integer qualityTimeCount;
    private final Belt currentBelt;
    private final Belt beltEarned;
    private final Integer beltProgressPercentage;
    private final Integer qualityTimesUntilNextBelt;
    
    // ─── Activity Ideas Context ──────────────────────────────────────────────
    
    private final List<ActivityIdea> activityIdeas;
    
    // ─── Dashboard Context ───────────────────────────────────────────────────
    
    private final String dashboardUrl;
    private final Integer weeklyGoalMinutes;
    private final Integer weeklyCompletedMinutes;
    
    // ─── Clarification Context ───────────────────────────────────────────────
    
    private final List<String> validOptions;
    
    // ─── Additional Context ──────────────────────────────────────────────────
    
    private final String completionNotes;
    private final String previousActivity;
    
    /**
     * Private constructor - use {@link #builder()} to create instances.
     */
    private MessageContext(Builder builder) {
        this.messageType = builder.messageType;
        this.fatherName = builder.fatherName;
        this.locale = builder.locale != null ? builder.locale : DEFAULT_LOCALE;
        this.timezone = builder.timezone != null ? builder.timezone : DEFAULT_TIMEZONE;
        
        this.childName = builder.childName;
        this.childAge = builder.childAge;
        this.childId = builder.childId;
        
        this.timeSlots = builder.timeSlots != null 
            ? Collections.unmodifiableList(builder.timeSlots) 
            : Collections.emptyList();
        this.scheduledStart = builder.scheduledStart;
        this.scheduledEnd = builder.scheduledEnd;
        this.scheduledTimeFormatted = builder.scheduledTimeFormatted;
        
        this.streakCount = builder.streakCount;
        this.longestStreak = builder.longestStreak;
        this.qualityTimeCount = builder.qualityTimeCount;
        this.currentBelt = builder.currentBelt;
        this.beltEarned = builder.beltEarned;
        this.beltProgressPercentage = builder.beltProgressPercentage;
        this.qualityTimesUntilNextBelt = builder.qualityTimesUntilNextBelt;
        
        this.activityIdeas = builder.activityIdeas != null 
            ? Collections.unmodifiableList(builder.activityIdeas) 
            : Collections.emptyList();
        
        this.dashboardUrl = builder.dashboardUrl;
        this.weeklyGoalMinutes = builder.weeklyGoalMinutes;
        this.weeklyCompletedMinutes = builder.weeklyCompletedMinutes;
        
        this.validOptions = builder.validOptions != null 
            ? Collections.unmodifiableList(builder.validOptions) 
            : Collections.emptyList();
        
        this.completionNotes = builder.completionNotes;
        this.previousActivity = builder.previousActivity;
    }
    
    // ─── Factory Methods ─────────────────────────────────────────────────────
    
    /**
     * Creates a new Builder for constructing a MessageContext.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a copy of this MessageContext with the option to modify values.
     * 
     * @return a new Builder pre-populated with this context's values
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.messageType = this.messageType;
        builder.fatherName = this.fatherName;
        builder.locale = this.locale;
        builder.timezone = this.timezone;
        builder.childName = this.childName;
        builder.childAge = this.childAge;
        builder.childId = this.childId;
        builder.timeSlots = this.timeSlots.isEmpty() ? null : List.copyOf(this.timeSlots);
        builder.scheduledStart = this.scheduledStart;
        builder.scheduledEnd = this.scheduledEnd;
        builder.scheduledTimeFormatted = this.scheduledTimeFormatted;
        builder.streakCount = this.streakCount;
        builder.longestStreak = this.longestStreak;
        builder.qualityTimeCount = this.qualityTimeCount;
        builder.currentBelt = this.currentBelt;
        builder.beltEarned = this.beltEarned;
        builder.beltProgressPercentage = this.beltProgressPercentage;
        builder.qualityTimesUntilNextBelt = this.qualityTimesUntilNextBelt;
        builder.activityIdeas = this.activityIdeas.isEmpty() ? null : List.copyOf(this.activityIdeas);
        builder.dashboardUrl = this.dashboardUrl;
        builder.weeklyGoalMinutes = this.weeklyGoalMinutes;
        builder.weeklyCompletedMinutes = this.weeklyCompletedMinutes;
        builder.validOptions = this.validOptions.isEmpty() ? null : List.copyOf(this.validOptions);
        builder.completionNotes = this.completionNotes;
        builder.previousActivity = this.previousActivity;
        return builder;
    }
    
    // ─── Getters ─────────────────────────────────────────────────────────────
    
    /**
     * Returns the message type that determines the template/structure.
     * 
     * @return the message type, or null if not set
     */
    public MessageType getMessageType() {
        return messageType;
    }
    
    /**
     * Returns the father's display name.
     * 
     * @return the father's name, or null if not set
     */
    public String getFatherName() {
        return fatherName;
    }
    
    /**
     * Returns the locale for message generation.
     * Supported values: "en" (English), "he" (Hebrew).
     * 
     * @return the locale code, defaults to "en"
     */
    public String getLocale() {
        return locale;
    }
    
    /**
     * Returns the father's timezone for time formatting.
     * 
     * @return the timezone ID string, defaults to "Asia/Jerusalem"
     */
    public String getTimezone() {
        return timezone;
    }
    
    /**
     * Returns the timezone as a ZoneId for date/time operations.
     * 
     * @return the ZoneId, defaults to Asia/Jerusalem if timezone is invalid
     */
    public ZoneId getTimezoneAsZoneId() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
    
    /**
     * Returns the child's name.
     * 
     * @return the child's name, or null if not set
     */
    public String getChildName() {
        return childName;
    }
    
    /**
     * Returns the child's age in years.
     * 
     * @return the child's age, or null if not set
     */
    public Integer getChildAge() {
        return childAge;
    }
    
    /**
     * Returns the child's ID.
     * 
     * @return the child ID, or null if not set
     */
    public Long getChildId() {
        return childId;
    }
    
    /**
     * Returns the available time slots for scheduling.
     * 
     * @return an immutable list of available slots, never null
     */
    public List<AvailableSlot> getTimeSlots() {
        return timeSlots;
    }
    
    /**
     * Returns the scheduled Quality Time start time.
     * 
     * @return the scheduled start time, or null if not scheduled
     */
    public Instant getScheduledStart() {
        return scheduledStart;
    }
    
    /**
     * Returns the scheduled Quality Time end time.
     * 
     * @return the scheduled end time, or null if not scheduled
     */
    public Instant getScheduledEnd() {
        return scheduledEnd;
    }
    
    /**
     * Returns a pre-formatted scheduled time string for display.
     * 
     * @return the formatted time string, or null if not set
     */
    public String getScheduledTimeFormatted() {
        return scheduledTimeFormatted;
    }
    
    /**
     * Returns the current Quality Time streak count.
     * 
     * @return the streak count, or null if not set
     */
    public Integer getStreakCount() {
        return streakCount;
    }
    
    /**
     * Returns the longest streak ever achieved.
     * 
     * @return the longest streak, or null if not set
     */
    public Integer getLongestStreak() {
        return longestStreak;
    }
    
    /**
     * Returns the total number of completed Quality Times.
     * 
     * @return the total count, or null if not set
     */
    public Integer getQualityTimeCount() {
        return qualityTimeCount;
    }
    
    /**
     * Returns the current belt level.
     * 
     * @return the current belt, or null if not set
     */
    public Belt getCurrentBelt() {
        return currentBelt;
    }
    
    /**
     * Returns a newly earned belt (for celebration messages).
     * 
     * @return the newly earned belt, or null if no belt was earned
     */
    public Belt getBeltEarned() {
        return beltEarned;
    }
    
    /**
     * Returns the progress percentage toward the next belt.
     * 
     * @return progress percentage (0-100), or null if not calculated
     */
    public Integer getBeltProgressPercentage() {
        return beltProgressPercentage;
    }
    
    /**
     * Returns the number of Quality Times needed to reach the next belt.
     * 
     * @return count until next belt, or null if at max belt
     */
    public Integer getQualityTimesUntilNextBelt() {
        return qualityTimesUntilNextBelt;
    }
    
    /**
     * Returns the list of activity ideas.
     * 
     * @return an immutable list of activity ideas, never null
     */
    public List<ActivityIdea> getActivityIdeas() {
        return activityIdeas;
    }
    
    /**
     * Returns the URL to the web dashboard.
     * 
     * @return the dashboard URL, or null if not set
     */
    public String getDashboardUrl() {
        return dashboardUrl;
    }
    
    /**
     * Returns the weekly goal in minutes.
     * 
     * @return the weekly goal, or null if not set
     */
    public Integer getWeeklyGoalMinutes() {
        return weeklyGoalMinutes;
    }
    
    /**
     * Returns the completed minutes this week.
     * 
     * @return completed minutes, or null if not calculated
     */
    public Integer getWeeklyCompletedMinutes() {
        return weeklyCompletedMinutes;
    }
    
    /**
     * Returns the valid options for clarification messages.
     * 
     * @return an immutable list of valid options, never null
     */
    public List<String> getValidOptions() {
        return validOptions;
    }
    
    /**
     * Returns notes provided when completing Quality Time.
     * 
     * @return completion notes, or null if not provided
     */
    public String getCompletionNotes() {
        return completionNotes;
    }
    
    /**
     * Returns the previous activity description (for avoiding repetition).
     * 
     * @return the previous activity, or null if not set
     */
    public String getPreviousActivity() {
        return previousActivity;
    }
    
    // ─── Utility Methods ─────────────────────────────────────────────────────
    
    /**
     * Returns true if the locale is Hebrew.
     * 
     * @return true if locale is "he"
     */
    public boolean isHebrew() {
        return LOCALE_HEBREW.equals(locale);
    }
    
    /**
     * Returns true if the locale is English.
     * 
     * @return true if locale is "en" or default
     */
    public boolean isEnglish() {
        return LOCALE_ENGLISH.equals(locale);
    }
    
    /**
     * Returns true if time slots are available.
     * 
     * @return true if timeSlots is not empty
     */
    public boolean hasTimeSlots() {
        return !timeSlots.isEmpty();
    }
    
    /**
     * Returns true if a new belt was earned.
     * 
     * @return true if beltEarned is not null
     */
    public boolean hasBeltEarned() {
        return beltEarned != null;
    }
    
    /**
     * Returns true if activity ideas are available.
     * 
     * @return true if activityIdeas is not empty
     */
    public boolean hasActivityIdeas() {
        return !activityIdeas.isEmpty();
    }
    
    /**
     * Returns the father's name or a default if not set.
     * 
     * @param defaultName the default name to use
     * @return the father's name or the default
     */
    public String getFatherNameOrDefault(String defaultName) {
        return fatherName != null ? fatherName : defaultName;
    }
    
    /**
     * Returns the child's name or a default if not set.
     * 
     * @param defaultName the default name to use
     * @return the child's name or the default
     */
    public String getChildNameOrDefault(String defaultName) {
        return childName != null ? childName : defaultName;
    }
    
    // ─── Static Timezone Formatting Utilities ────────────────────────────────
    
    /** Default time format pattern for 12-hour clock. */
    private static final String TIME_FORMAT_12H = "h:mm a";
    
    /** Default time format pattern for 24-hour clock. */
    private static final String TIME_FORMAT_24H = "HH:mm";
    
    /** Default date-time format pattern for 12-hour clock. */
    private static final String DATETIME_FORMAT_12H = "EEEE, MMMM d, h:mm a";
    
    /** Default date-time format pattern for 24-hour clock. */
    private static final String DATETIME_FORMAT_24H = "EEEE, MMMM d, HH:mm";
    
    /**
     * Formats an Instant to a local time string in the specified timezone.
     * 
     * <p>This utility method ensures all time suggestions are formatted in the father's
     * configured timezone (Requirement 5.7). Uses 12-hour format with AM/PM for readability.</p>
     * 
     * <p>Example output: "3:30 PM"</p>
     * 
     * @param time the instant to format
     * @param timezone the timezone ID (e.g., "Asia/Jerusalem", "America/New_York")
     * @param locale the locale for localization (determines language for AM/PM)
     * @return the formatted time string in the specified timezone
     */
    public static String formatTimeInTimezone(Instant time, ZoneId timezone, Locale locale) {
        if (time == null) {
            return "";
        }
        
        ZoneId zone = timezone != null ? timezone : ZoneId.of(DEFAULT_TIMEZONE);
        Locale displayLocale = locale != null ? locale : Locale.ENGLISH;
        
        ZonedDateTime zdt = time.atZone(zone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIME_FORMAT_12H, displayLocale);
        return zdt.format(formatter);
    }
    
    /**
     * Formats an Instant to a local time string using the context's timezone and locale.
     * 
     * <p>This is a convenience method that extracts timezone and locale from the context.</p>
     * 
     * @param time the instant to format
     * @return the formatted time string
     */
    public String formatTimeInTimezone(Instant time) {
        Locale locale = LOCALE_HEBREW.equals(this.locale) 
            ? Locale.forLanguageTag("he-IL") 
            : Locale.ENGLISH;
        return formatTimeInTimezone(time, getTimezoneAsZoneId(), locale);
    }
    
    /**
     * Formats an Instant to a full date-time string in the specified timezone.
     * 
     * <p>Example output: "Monday, January 15, 3:30 PM"</p>
     * 
     * @param time the instant to format
     * @param timezone the timezone ID
     * @param locale the locale for localization
     * @return the formatted date-time string
     */
    public static String formatDateTimeInTimezone(Instant time, ZoneId timezone, Locale locale) {
        if (time == null) {
            return "";
        }
        
        ZoneId zone = timezone != null ? timezone : ZoneId.of(DEFAULT_TIMEZONE);
        Locale displayLocale = locale != null ? locale : Locale.ENGLISH;
        
        ZonedDateTime zdt = time.atZone(zone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATETIME_FORMAT_12H, displayLocale);
        return zdt.format(formatter);
    }
    
    /**
     * Formats a time range (start to end) in the specified timezone.
     * 
     * <p>Example output: "Monday, January 15, 3:30 PM - 4:00 PM"</p>
     * 
     * @param start the start instant
     * @param end the end instant
     * @param timezone the timezone ID
     * @param locale the locale for localization
     * @return the formatted time range string
     */
    public static String formatTimeRangeInTimezone(Instant start, Instant end, ZoneId timezone, Locale locale) {
        if (start == null || end == null) {
            return "";
        }
        
        ZoneId zone = timezone != null ? timezone : ZoneId.of(DEFAULT_TIMEZONE);
        Locale displayLocale = locale != null ? locale : Locale.ENGLISH;
        
        ZonedDateTime startZdt = start.atZone(zone);
        ZonedDateTime endZdt = end.atZone(zone);
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", displayLocale);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT_12H, displayLocale);
        
        String date = startZdt.format(dateFormatter);
        String startTime = startZdt.format(timeFormatter);
        String endTime = endZdt.format(timeFormatter);
        
        return String.format("%s, %s - %s", date, startTime, endTime);
    }
    
    /**
     * Formats a time range using the context's timezone and locale.
     * 
     * @param start the start instant
     * @param end the end instant
     * @return the formatted time range string
     */
    public String formatTimeRangeInTimezone(Instant start, Instant end) {
        Locale locale = LOCALE_HEBREW.equals(this.locale) 
            ? Locale.forLanguageTag("he-IL") 
            : Locale.ENGLISH;
        return formatTimeRangeInTimezone(start, end, getTimezoneAsZoneId(), locale);
    }
    
    /**
     * Formats an AvailableSlot into a human-readable string using the context's timezone.
     * 
     * <p>Example output: "Monday, January 15, 3:30 PM - 5:30 PM (2 hours)"</p>
     * 
     * @param slot the available slot to format
     * @return the formatted slot string
     */
    public String formatSlotInTimezone(AvailableSlot slot) {
        if (slot == null) {
            return "";
        }
        
        String timeRange = formatTimeRangeInTimezone(slot.startTime(), slot.endTime());
        String duration = formatDuration(slot.durationMinutes());
        
        return String.format("%s (%s)", timeRange, duration);
    }
    
    /**
     * Formats a duration in minutes to a human-readable string.
     * 
     * @param durationMinutes the duration in minutes
     * @return formatted duration (e.g., "30 min", "1.5 hours", "2 hours")
     */
    public String formatDuration(int durationMinutes) {
        boolean isHebrew = LOCALE_HEBREW.equals(this.locale);
        
        if (durationMinutes < 60) {
            return isHebrew 
                ? String.format("%d דקות", durationMinutes)
                : String.format("%d min", durationMinutes);
        }
        
        double hours = durationMinutes / 60.0;
        if (hours == Math.floor(hours)) {
            int wholeHours = (int) hours;
            return isHebrew
                ? String.format("%d %s", wholeHours, wholeHours == 1 ? "שעה" : "שעות")
                : String.format("%d %s", wholeHours, wholeHours == 1 ? "hour" : "hours");
        } else {
            return isHebrew
                ? String.format("%.1f שעות", hours)
                : String.format("%.1f hours", hours);
        }
    }
    
    /**
     * Creates a locale-appropriate Locale object from the context's locale string.
     * 
     * @return the Locale object (Hebrew or English)
     */
    public Locale getDisplayLocale() {
        return LOCALE_HEBREW.equals(this.locale) 
            ? Locale.forLanguageTag("he-IL") 
            : Locale.ENGLISH;
    }
    
    // ─── Timezone-Aware Date Calculation Methods ─────────────────────────────
    
    /**
     * Calculates "tomorrow" in the father's timezone.
     * 
     * <p>This method addresses Bug 3: Date Calculation Error, ensuring that
     * "tomorrow" is always computed correctly relative to the father's configured
     * timezone, not the server's timezone.</p>
     * 
     * <p>Example: If it's 11:00 PM on Friday in Asia/Jerusalem but 8:00 PM on Friday
     * in UTC, calling this method with timezone "Asia/Jerusalem" will correctly
     * return Saturday, not Friday.</p>
     * 
     * @return LocalDate representing tomorrow in the context's timezone
     * @see #getTodayInTimezone()
     * @see #validateDayOfWeek(LocalDate, String)
     */
    public LocalDate getTomorrowInTimezone() {
        ZoneId zone = getTimezoneAsZoneId();
        return LocalDate.now(zone).plusDays(1);
    }
    
    /**
     * Calculates "today" in the father's timezone.
     * 
     * <p>This method addresses Bug 3: Date Calculation Error, ensuring that
     * "today" is always computed correctly relative to the father's configured
     * timezone, not the server's timezone.</p>
     * 
     * <p>Example: If it's 1:00 AM on Saturday in Asia/Jerusalem but 10:00 PM on Friday
     * in UTC, calling this method with timezone "Asia/Jerusalem" will correctly
     * return Saturday, not Friday.</p>
     * 
     * @return LocalDate representing today in the context's timezone
     * @see #getTomorrowInTimezone()
     */
    public LocalDate getTodayInTimezone() {
        ZoneId zone = getTimezoneAsZoneId();
        return LocalDate.now(zone);
    }
    
    /**
     * Validates that the day-of-week in a formatted string matches the actual date.
     * 
     * <p>This method is used to verify date formatting correctness before sending
     * messages to users. It ensures the displayed day name (e.g., "Friday", "שישי")
     * actually corresponds to the calendar date being displayed.</p>
     * 
     * <p>This validation helps catch bugs where timezone mismatches cause the
     * wrong day-of-week to be displayed for a date.</p>
     * 
     * @param date the date to validate
     * @param formattedString the formatted string containing the day name
     * @return true if the formatted string contains the correct day name for the date
     */
    public boolean validateDayOfWeek(LocalDate date, String formattedString) {
        if (date == null || formattedString == null || formattedString.isEmpty()) {
            return false;
        }
        
        DayOfWeek actualDay = date.getDayOfWeek();
        // Check if formatted string contains correct day name for locale
        String expectedDayName = actualDay.getDisplayName(TextStyle.FULL, getDisplayLocale());
        return formattedString.contains(expectedDayName);
    }
    
    // ─── Object Methods ──────────────────────────────────────────────────────
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageContext that = (MessageContext) o;
        return messageType == that.messageType 
            && Objects.equals(fatherName, that.fatherName)
            && Objects.equals(locale, that.locale)
            && Objects.equals(timezone, that.timezone)
            && Objects.equals(childName, that.childName)
            && Objects.equals(childAge, that.childAge)
            && Objects.equals(childId, that.childId)
            && Objects.equals(timeSlots, that.timeSlots)
            && Objects.equals(scheduledStart, that.scheduledStart)
            && Objects.equals(scheduledEnd, that.scheduledEnd)
            && Objects.equals(streakCount, that.streakCount)
            && Objects.equals(qualityTimeCount, that.qualityTimeCount)
            && currentBelt == that.currentBelt
            && beltEarned == that.beltEarned;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messageType, fatherName, locale, timezone, 
            childName, childId, scheduledStart, streakCount, currentBelt);
    }
    
    @Override
    public String toString() {
        return "MessageContext{" +
            "messageType=" + messageType +
            ", fatherName='" + fatherName + '\'' +
            ", locale='" + locale + '\'' +
            ", timezone='" + timezone + '\'' +
            ", childName='" + childName + '\'' +
            ", streakCount=" + streakCount +
            ", currentBelt=" + currentBelt +
            '}';
    }
    
    // ─── Builder ─────────────────────────────────────────────────────────────
    
    /**
     * Builder for creating MessageContext instances.
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * MessageContext context = MessageContext.builder()
     *     .messageType(MessageType.FOLLOW_UP_COMPLETED)
     *     .fatherName("David")
     *     .childName("Maya")
     *     .streakCount(5)
     *     .currentBelt(Belt.GREEN)
     *     .beltEarned(Belt.BLUE)
     *     .locale("he")
     *     .timezone("Asia/Jerusalem")
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        
        private MessageType messageType;
        private String fatherName;
        private String locale;
        private String timezone;
        
        private String childName;
        private Integer childAge;
        private Long childId;
        
        private List<AvailableSlot> timeSlots;
        private Instant scheduledStart;
        private Instant scheduledEnd;
        private String scheduledTimeFormatted;
        
        private Integer streakCount;
        private Integer longestStreak;
        private Integer qualityTimeCount;
        private Belt currentBelt;
        private Belt beltEarned;
        private Integer beltProgressPercentage;
        private Integer qualityTimesUntilNextBelt;
        
        private List<ActivityIdea> activityIdeas;
        
        private String dashboardUrl;
        private Integer weeklyGoalMinutes;
        private Integer weeklyCompletedMinutes;
        
        private List<String> validOptions;
        
        private String completionNotes;
        private String previousActivity;
        
        private Builder() {
        }
        
        // ─── Core Fields ─────────────────────────────────────────────────────
        
        /**
         * Sets the message type.
         * 
         * @param messageType the type of message to generate
         * @return this builder
         */
        public Builder messageType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }
        
        /**
         * Sets the father's display name.
         * 
         * @param fatherName the father's name
         * @return this builder
         */
        public Builder fatherName(String fatherName) {
            this.fatherName = fatherName;
            return this;
        }
        
        /**
         * Sets the locale for message generation.
         * Use "en" for English, "he" for Hebrew.
         * 
         * @param locale the locale code
         * @return this builder
         */
        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }
        
        /**
         * Sets the timezone for time formatting.
         * 
         * @param timezone the timezone ID (e.g., "Asia/Jerusalem", "America/New_York")
         * @return this builder
         */
        public Builder timezone(String timezone) {
            this.timezone = timezone;
            return this;
        }
        
        // ─── Child Context ───────────────────────────────────────────────────
        
        /**
         * Sets the child's name.
         * 
         * @param childName the child's name
         * @return this builder
         */
        public Builder childName(String childName) {
            this.childName = childName;
            return this;
        }
        
        /**
         * Sets the child's age in years.
         * 
         * @param childAge the child's age
         * @return this builder
         */
        public Builder childAge(Integer childAge) {
            this.childAge = childAge;
            return this;
        }
        
        /**
         * Sets the child's ID.
         * 
         * @param childId the child ID
         * @return this builder
         */
        public Builder childId(Long childId) {
            this.childId = childId;
            return this;
        }
        
        // ─── Scheduling Context ──────────────────────────────────────────────
        
        /**
         * Sets the available time slots for scheduling.
         * 
         * @param timeSlots the list of available slots
         * @return this builder
         */
        public Builder timeSlots(List<AvailableSlot> timeSlots) {
            this.timeSlots = timeSlots;
            return this;
        }
        
        /**
         * Sets the scheduled Quality Time start time.
         * 
         * @param scheduledStart the start time
         * @return this builder
         */
        public Builder scheduledStart(Instant scheduledStart) {
            this.scheduledStart = scheduledStart;
            return this;
        }
        
        /**
         * Sets the scheduled Quality Time end time.
         * 
         * @param scheduledEnd the end time
         * @return this builder
         */
        public Builder scheduledEnd(Instant scheduledEnd) {
            this.scheduledEnd = scheduledEnd;
            return this;
        }
        
        /**
         * Sets a pre-formatted scheduled time string for display.
         * 
         * @param scheduledTimeFormatted the formatted time string
         * @return this builder
         */
        public Builder scheduledTimeFormatted(String scheduledTimeFormatted) {
            this.scheduledTimeFormatted = scheduledTimeFormatted;
            return this;
        }
        
        // ─── Progress and Gamification Context ───────────────────────────────
        
        /**
         * Sets the current Quality Time streak count.
         * 
         * @param streakCount the streak count
         * @return this builder
         */
        public Builder streakCount(Integer streakCount) {
            this.streakCount = streakCount;
            return this;
        }
        
        /**
         * Sets the longest streak ever achieved.
         * 
         * @param longestStreak the longest streak
         * @return this builder
         */
        public Builder longestStreak(Integer longestStreak) {
            this.longestStreak = longestStreak;
            return this;
        }
        
        /**
         * Sets the total number of completed Quality Times.
         * 
         * @param qualityTimeCount the total count
         * @return this builder
         */
        public Builder qualityTimeCount(Integer qualityTimeCount) {
            this.qualityTimeCount = qualityTimeCount;
            return this;
        }
        
        /**
         * Sets the current belt level.
         * 
         * @param currentBelt the current belt
         * @return this builder
         */
        public Builder currentBelt(Belt currentBelt) {
            this.currentBelt = currentBelt;
            return this;
        }
        
        /**
         * Sets a newly earned belt (for celebration messages).
         * 
         * @param beltEarned the newly earned belt
         * @return this builder
         */
        public Builder beltEarned(Belt beltEarned) {
            this.beltEarned = beltEarned;
            return this;
        }
        
        /**
         * Sets the progress percentage toward the next belt.
         * 
         * @param beltProgressPercentage progress percentage (0-100)
         * @return this builder
         */
        public Builder beltProgressPercentage(Integer beltProgressPercentage) {
            this.beltProgressPercentage = beltProgressPercentage;
            return this;
        }
        
        /**
         * Sets the number of Quality Times needed to reach the next belt.
         * 
         * @param qualityTimesUntilNextBelt count until next belt
         * @return this builder
         */
        public Builder qualityTimesUntilNextBelt(Integer qualityTimesUntilNextBelt) {
            this.qualityTimesUntilNextBelt = qualityTimesUntilNextBelt;
            return this;
        }
        
        // ─── Activity Ideas Context ──────────────────────────────────────────
        
        /**
         * Sets the list of activity ideas.
         * 
         * @param activityIdeas the activity ideas
         * @return this builder
         */
        public Builder activityIdeas(List<ActivityIdea> activityIdeas) {
            this.activityIdeas = activityIdeas;
            return this;
        }
        
        // ─── Dashboard Context ───────────────────────────────────────────────
        
        /**
         * Sets the URL to the web dashboard.
         * 
         * @param dashboardUrl the dashboard URL
         * @return this builder
         */
        public Builder dashboardUrl(String dashboardUrl) {
            this.dashboardUrl = dashboardUrl;
            return this;
        }
        
        /**
         * Sets the weekly goal in minutes.
         * 
         * @param weeklyGoalMinutes the weekly goal
         * @return this builder
         */
        public Builder weeklyGoalMinutes(Integer weeklyGoalMinutes) {
            this.weeklyGoalMinutes = weeklyGoalMinutes;
            return this;
        }
        
        /**
         * Sets the completed minutes this week.
         * 
         * @param weeklyCompletedMinutes completed minutes
         * @return this builder
         */
        public Builder weeklyCompletedMinutes(Integer weeklyCompletedMinutes) {
            this.weeklyCompletedMinutes = weeklyCompletedMinutes;
            return this;
        }
        
        // ─── Clarification Context ───────────────────────────────────────────
        
        /**
         * Sets the valid options for clarification messages.
         * 
         * @param validOptions the list of valid options
         * @return this builder
         */
        public Builder validOptions(List<String> validOptions) {
            this.validOptions = validOptions;
            return this;
        }
        
        // ─── Additional Context ──────────────────────────────────────────────
        
        /**
         * Sets notes provided when completing Quality Time.
         * 
         * @param completionNotes the completion notes
         * @return this builder
         */
        public Builder completionNotes(String completionNotes) {
            this.completionNotes = completionNotes;
            return this;
        }
        
        /**
         * Sets the previous activity description (for avoiding repetition).
         * 
         * @param previousActivity the previous activity
         * @return this builder
         */
        public Builder previousActivity(String previousActivity) {
            this.previousActivity = previousActivity;
            return this;
        }
        
        // ─── Build ───────────────────────────────────────────────────────────
        
        /**
         * Builds the MessageContext instance.
         * 
         * @return a new MessageContext with the configured values
         */
        public MessageContext build() {
            return new MessageContext(this);
        }
    }
    
    // ─── Activity Idea Record ────────────────────────────────────────────────
    
    /**
     * Represents an activity idea for Quality Time.
     * 
     * @param title brief title of the activity
     * @param description detailed description (2-3 sentences)
     * @param durationMinutes estimated duration in minutes
     * @param indoor whether the activity is indoor (true) or outdoor (false)
     */
    public record ActivityIdea(
        String title,
        String description,
        int durationMinutes,
        boolean indoor
    ) {
        /**
         * Creates an ActivityIdea with validation.
         * 
         * @throws IllegalArgumentException if title or description is null/empty
         * @throws IllegalArgumentException if durationMinutes is not positive
         */
        public ActivityIdea {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title must not be null or empty");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description must not be null or empty");
            }
            if (durationMinutes <= 0) {
                throw new IllegalArgumentException("durationMinutes must be positive");
            }
        }
        
        /**
         * Returns whether this is an outdoor activity.
         * 
         * @return true if outdoor, false if indoor
         */
        public boolean isOutdoor() {
            return !indoor;
        }
    }
}
