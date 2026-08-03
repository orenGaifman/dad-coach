package com.dadcoach.mission;

/**
 * Exception thrown when mission scheduling fails.
 * 
 * <p>This exception indicates that a mission could not be scheduled after
 * appropriate retry attempts. Common causes include:</p>
 * <ul>
 *   <li>Calendar API failure (for calendar-backed missions)</li>
 *   <li>Database persistence failure</li>
 *   <li>External service unavailability</li>
 * </ul>
 * 
 * <p>This exception should be caught and handled appropriately by the
 * workflow engine, typically by informing the user and remaining in
 * the scheduling state for retry.</p>
 * 
 * @see MissionService#schedule(Long, Long, java.time.Instant, java.time.Duration)
 */
public class MissionSchedulingException extends RuntimeException {

    /**
     * Creates a new MissionSchedulingException with the specified message.
     *
     * @param message the detail message
     */
    public MissionSchedulingException(String message) {
        super(message);
    }

    /**
     * Creates a new MissionSchedulingException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public MissionSchedulingException(String message, Throwable cause) {
        super(message, cause);
    }
}
