package com.dadcoach.workspace;

/**
 * Base runtime exception for all workspace-specific errors.
 *
 * <p>Carries a {@link WorkspaceErrorCode} to enable consistent error response
 * formatting in the {@link WorkspaceExceptionHandler}.</p>
 */
public class WorkspaceException extends RuntimeException {

    private final WorkspaceErrorCode errorCode;

    public WorkspaceException(WorkspaceErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WorkspaceException(WorkspaceErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public WorkspaceErrorCode getErrorCode() {
        return errorCode;
    }
}
