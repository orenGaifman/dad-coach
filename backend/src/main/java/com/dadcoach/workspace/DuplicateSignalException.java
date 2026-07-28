package com.dadcoach.workspace;

/**
 * Thrown when a growth signal has already been recorded for the same source event.
 */
public class DuplicateSignalException extends WorkspaceException {

    private final String sourceEntityId;

    public DuplicateSignalException(String sourceEntityId) {
        super(
                WorkspaceErrorCode.GROWTH_SIGNAL_DUPLICATE,
                WorkspaceErrorCode.GROWTH_SIGNAL_DUPLICATE.formatMessage(sourceEntityId)
        );
        this.sourceEntityId = sourceEntityId;
    }

    public String getSourceEntityId() {
        return sourceEntityId;
    }
}
