package com.dadcoach.integration.platform;

import java.util.Map;
import java.util.UUID;

/** {@code POST /api/v1/worker/messages/outbound} body (playbook §10.2): a message Dad Coach sent by itself. */
public record RecordOutboundRequest(String workerKey, String userId, String channelId, String correlationId, String content,
                                    Map<String, Object> metadata, UUID tenantId, String personRef, String personName,
                                    String workflowKey) {}
