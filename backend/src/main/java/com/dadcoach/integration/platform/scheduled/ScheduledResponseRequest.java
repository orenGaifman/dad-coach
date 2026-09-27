package com.dadcoach.integration.platform.scheduled;

/**
 * The Workflow Platform's scheduled-response callback body ({@code ScheduledResponseNotifier}'s
 * payload - the same contract Big Boss receives).
 */
public record ScheduledResponseRequest(
        String triggerId,
        String workflowInstanceId,
        String userId,
        String channel,
        String targetStateKey,
        String responseContent) {
}
