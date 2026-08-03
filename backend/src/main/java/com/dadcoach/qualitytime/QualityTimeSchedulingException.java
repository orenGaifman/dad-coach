package com.dadcoach.qualitytime;

/**
 * Exception thrown when Quality Time scheduling fails.
 * 
 * <p>This exception is thrown when:</p>
 * <ul>
 *   <li>Google Calendar event creation fails after retry attempts</li>
 *   <li>Calendar API returns an unrecoverable error</li>
 * </ul>
 * 
 * <p>Requirements: 3.6</p>
 */
public class QualityTimeSchedulingException extends RuntimeException {

    /**
     * Creates a new scheduling exception with a message.
     *
     * @param message the error message
     */
    public QualityTimeSchedulingException(String message) {
        super(message);
    }

    /**
     * Creates a new scheduling exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public QualityTimeSchedulingException(String message, Throwable cause) {
        super(message, cause);
    }
}
