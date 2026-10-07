package com.dadcoach.integration.platform;

import java.util.Map;
import java.util.UUID;

/**
 * {@code POST /api/v1/worker/execute} body (playbook §10.1). workflowKey is REQUIRED — the caller selects.
 * correlationId is Meta's message id; metadata carries the father's timezone (it seeds a new conversation's
 * {@code workflowContext.timezone}, which INSTANCE-timezone schedules use).
 */
public record WorkerExecuteRequest(String workerKey, String userId, String channelId, String correlationId,
                                   String messageType, String content, Map<String, Object> metadata,
                                   UUID tenantId, String personRef, String personName, String workflowKey) {}
