package com.dadcoach.systemstate;

import java.time.Instant;
import java.time.Duration;

/**
 * Represents an available time slot for Quality Time scheduling.
 * 
 * <p>Available slots are calculated by analyzing the father's Google Calendar
 * to find gaps of at least 30 minutes within preferred activity hours (6am-10pm).</p>
 * 
 * <p>Implements Requirements 2.3:</p>
 * <ul>
 *   <li>Read the father's Google Calendar for the next 7 days</li>
 *   <li>Identify busy periods from calendar events</li>
 *   <li>Calculate available slots of at least 30 minutes duration</li>
 *   <li>Exclude times outside the father's preferred activity hours</li>
 *   <li>Present slots ordered by proximity to current time</li>
 * </ul>
 * 
 * @param startTime the slot start time (UTC)
 * @param endTime the slot end time (UTC)
 * @param durationMinutes the slot duration in minutes
 * 
 * @see SystemStateLoader#loadAvailableSlots(java.util.UUID, int)
 * @see <a href="Requirements 2.3">Time Slot Calculation</a>
 */
public record AvailableSlot(
    Instant startTime,
    Instant endTime,
    int durationMinutes
) {
    
    /**
     * Minimum slot duration in minutes for Quality Time scheduling.
     */
    public static final int MINIMUM_DURATION_MINUTES = 30;
    
    /**
     * Creates an AvailableSlot with validation.
     * 
     * @throws IllegalArgumentException if startTime is null
     * @throws IllegalArgumentException if endTime is null
     * @throws IllegalArgumentException if endTime is not after startTime
     * @throws IllegalArgumentException if durationMinutes is less than minimum
     */
    public AvailableSlot {
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("endTime must not be null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        if (durationMinutes < MINIMUM_DURATION_MINUTES) {
            throw new IllegalArgumentException(
                "durationMinutes must be at least " + MINIMUM_DURATION_MINUTES
            );
        }
    }
    
    /**
     * Creates an AvailableSlot from start and end times, calculating the duration automatically.
     * 
     * @param startTime the slot start time
     * @param endTime the slot end time
     * @return a new AvailableSlot with calculated duration
     */
    public static AvailableSlot of(Instant startTime, Instant endTime) {
        Duration duration = Duration.between(startTime, endTime);
        return new AvailableSlot(startTime, endTime, (int) duration.toMinutes());
    }
    
    /**
     * Creates an AvailableSlot from a start time and duration.
     * 
     * @param startTime the slot start time
     * @param durationMinutes the slot duration in minutes
     * @return a new AvailableSlot with calculated end time
     */
    public static AvailableSlot ofDuration(Instant startTime, int durationMinutes) {
        Instant endTime = startTime.plus(Duration.ofMinutes(durationMinutes));
        return new AvailableSlot(startTime, endTime, durationMinutes);
    }
    
    /**
     * Returns the actual duration based on start and end times.
     * This may differ from durationMinutes if the slot was created manually.
     * 
     * @return the calculated duration between start and end times
     */
    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }
    
    /**
     * Checks if this slot overlaps with a given time range.
     * 
     * @param otherStart the start of the other time range
     * @param otherEnd the end of the other time range
     * @return true if the ranges overlap
     */
    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        // Two ranges overlap if one starts before the other ends and ends after the other starts
        return startTime.isBefore(otherEnd) && endTime.isAfter(otherStart);
    }
    
    /**
     * Checks if this slot can accommodate a Quality Time event of the given duration.
     * 
     * @param requiredMinutes the required duration in minutes
     * @return true if this slot is long enough
     */
    public boolean canAccommodate(int requiredMinutes) {
        return durationMinutes >= requiredMinutes;
    }
}
