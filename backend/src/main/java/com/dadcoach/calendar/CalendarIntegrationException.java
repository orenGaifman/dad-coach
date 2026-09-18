package com.dadcoach.calendar;

/**
 * Raised when a Google Calendar integration operation cannot complete.
 *
 * <p>Carries a {@link CalendarErrorType} so callers can distinguish between fundamentally
 * different situations and surface a clear, actionable message to the user:</p>
 * <ul>
 *   <li>{@link CalendarErrorType#NOT_CONNECTED} — the user has never connected Google Calendar.</li>
 *   <li>{@link CalendarErrorType#RECONNECT_REQUIRED} — the OAuth token is expired/invalid/revoked
 *       and cannot be refreshed; the user must reconnect.</li>
 *   <li>{@link CalendarErrorType#TEMPORARY_FAILURE} — Google Calendar (or the network) failed
 *       transiently; retrying later may succeed.</li>
 *   <li>{@link CalendarErrorType#CONFLICT} — the requested slot conflicts with an existing event.</li>
 * </ul>
 *
 * <p>This is intentionally distinct from input/validation errors: those remain
 * {@code IllegalArgumentException} / {@code IllegalStateException} so the dispatcher can map
 * each class of failure to its own {@code ToolExecutionResponse} error code.</p>
 */
public class CalendarIntegrationException extends RuntimeException {

    /**
     * The category of calendar integration failure.
     */
    public enum CalendarErrorType {
        /** Google Calendar has not been connected for this user. */
        NOT_CONNECTED("CALENDAR_NOT_CONNECTED"),
        /** OAuth token is expired/invalid/revoked; the user must reconnect. */
        RECONNECT_REQUIRED("CALENDAR_RECONNECT_REQUIRED"),
        /** Transient Google Calendar / network failure; safe to retry later. */
        TEMPORARY_FAILURE("CALENDAR_TEMPORARY_FAILURE"),
        /** The requested time slot conflicts with an existing event. */
        CONFLICT("CALENDAR_CONFLICT");

        private final String code;

        CalendarErrorType(String code) {
            this.code = code;
        }

        /** Stable machine-readable error code surfaced to API clients. */
        public String code() {
            return code;
        }
    }

    private final CalendarErrorType errorType;

    public CalendarIntegrationException(CalendarErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public CalendarIntegrationException(CalendarErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public CalendarErrorType getErrorType() {
        return errorType;
    }

    /** Convenience factory for the "not connected" case. */
    public static CalendarIntegrationException notConnected() {
        return new CalendarIntegrationException(
                CalendarErrorType.NOT_CONNECTED,
                "Google Calendar is not connected. Please connect Google Calendar for this user "
                        + "before running this test.");
    }

    /** Convenience factory for the "reconnect required" case. */
    public static CalendarIntegrationException reconnectRequired(Throwable cause) {
        return new CalendarIntegrationException(
                CalendarErrorType.RECONNECT_REQUIRED,
                "Google Calendar authorization is invalid or expired. Please reconnect Google "
                        + "Calendar for this user and try again.",
                cause);
    }

    /** Convenience factory for the "reconnect required" case without a cause. */
    public static CalendarIntegrationException reconnectRequired() {
        return new CalendarIntegrationException(
                CalendarErrorType.RECONNECT_REQUIRED,
                "Google Calendar authorization is invalid or expired. Please reconnect Google "
                        + "Calendar for this user and try again.");
    }

    /** Convenience factory for a transient Google Calendar / network failure. */
    public static CalendarIntegrationException temporaryFailure(String detail, Throwable cause) {
        return new CalendarIntegrationException(
                CalendarErrorType.TEMPORARY_FAILURE,
                "Google Calendar is temporarily unavailable" + (detail != null ? " (" + detail + ")" : "")
                        + ". Please try again shortly.",
                cause);
    }

    /** Convenience factory for a scheduling conflict. */
    public static CalendarIntegrationException conflict(String message) {
        return new CalendarIntegrationException(CalendarErrorType.CONFLICT, message);
    }
}
