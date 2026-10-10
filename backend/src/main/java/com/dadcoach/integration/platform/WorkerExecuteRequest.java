package com.dadcoach.integration.platform;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.UUID;

/**
 * {@code POST /api/v1/worker/execute} body (playbook §10.1). workflowKey is REQUIRED — the caller selects.
 * correlationId is Meta's message id; metadata carries the father's timezone (it seeds a new conversation's
 * {@code workflowContext.timezone}, which INSTANCE-timezone schedules use).
 *
 * @param internal D-039: true for a turn whose content is Dad Coach's own instruction (the Hebrew rewrite note), never the
 *                 father's words - the platform keeps its inbound row out of the conversation. Null is never sent.
 */
public record WorkerExecuteRequest(String workerKey, String userId, String channelId, String correlationId,
                                   String messageType, String content, Map<String, Object> metadata,
                                   UUID tenantId, String personRef, String personName, String workflowKey,
                                   @JsonInclude(JsonInclude.Include.NON_NULL) Boolean internal) {

    /** The father's own message (no {@code internal} field on the wire). */
    public WorkerExecuteRequest(String workerKey, String userId, String channelId, String correlationId,
                                String messageType, String content, Map<String, Object> metadata,
                                UUID tenantId, String personRef, String personName, String workflowKey) {
        this(workerKey, userId, channelId, correlationId, messageType, content, metadata, tenantId, personRef, personName,
                workflowKey, null);
    }
}
