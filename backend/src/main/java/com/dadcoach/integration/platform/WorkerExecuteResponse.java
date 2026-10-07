package com.dadcoach.integration.platform;

import java.util.Map;

/** {@code metadata.responseOutcome} is GENERATED or SUPPRESSED; a suppressed turn sends nothing. */
public record WorkerExecuteResponse(String instanceId, String currentStateKey, String responseContent, String responseType,
                                    Map<String, Object> metadata, boolean isDuplicate) {

    public boolean suppressed() {
        return metadata != null && "SUPPRESSED".equals(metadata.get("responseOutcome"));
    }
}
