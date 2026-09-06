package com.dadcoach.integration.platform;

/**
 * Exception thrown when communication with the ai-workflow-platform fails.
 *
 * <p>This exception wraps errors that occur during platform API calls, including:</p>
 * <ul>
 *   <li>Connection failures</li>
 *   <li>HTTP error responses (4xx, 5xx)</li>
 *   <li>Timeout errors</li>
 *   <li>Response parsing errors</li>
 * </ul>
 */
public class PlatformWorkflowException extends RuntimeException {

    public PlatformWorkflowException(String message) {
        super(message);
    }

    public PlatformWorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
