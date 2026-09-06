package com.dadcoach.integration.platform.dto;

/**
 * Request DTO for executing a workflow via ai-workflow-platform.
 *
 * @param workflowKey the logical key of the workflow (e.g., "dad-coach")
 * @param userId the external user identifier (e.g., "whatsapp:+972...")
 * @param channel the communication channel (e.g., "whatsapp", "web")
 * @param correlationId unique message ID for idempotency
 * @param message the user's message payload
 */
public record WorkflowExecuteRequest(
        String workflowKey,
        String userId,
        String channel,
        String correlationId,
        MessagePayload message
) {
    /**
     * Creates a new request with validation.
     */
    public WorkflowExecuteRequest {
        if (workflowKey == null || workflowKey.isBlank()) {
            throw new IllegalArgumentException("workflowKey is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }
    }

    /**
     * The message payload from the user.
     *
     * @param type the message type (e.g., "text", "button_reply")
     * @param content the actual message content
     */
    public record MessagePayload(
            String type,
            String content
    ) {
        /**
         * Creates a text message payload.
         *
         * @param content the message text
         * @return a text type message payload
         */
        public static MessagePayload text(String content) {
            return new MessagePayload("text", content);
        }

        /**
         * Creates a button reply message payload.
         *
         * @param buttonId the ID of the clicked button
         * @return a button_reply type message payload
         */
        public static MessagePayload buttonReply(String buttonId) {
            return new MessagePayload("button_reply", buttonId);
        }
    }

    /**
     * Factory method to create a text message request.
     *
     * @param userId the user identifier
     * @param channel the channel
     * @param correlationId the correlation ID
     * @param content the message content
     * @return a WorkflowExecuteRequest for a text message
     */
    public static WorkflowExecuteRequest textMessage(String userId, String channel,
                                                      String correlationId, String content) {
        return new WorkflowExecuteRequest(
                "dad-coach",
                userId,
                channel,
                correlationId,
                MessagePayload.text(content)
        );
    }
}
