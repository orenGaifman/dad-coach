package com.dadcoach.integration.platform.dto;

import java.util.List;

/**
 * Response DTO from the ai-workflow-platform workflow execution API.
 *
 * @param success whether the workflow executed successfully
 * @param response the response payload to send to the user
 * @param currentState the current workflow state after execution
 * @param executionId platform execution ID for debugging/tracing
 * @param errorMessage error description if success is false
 * @param errorCode error code if success is false
 */
public record WorkflowExecuteResponse(
        boolean success,
        ResponsePayload response,
        String currentState,
        String executionId,
        String errorMessage,
        String errorCode
) {
    /**
     * The response payload to send to the user.
     *
     * @param type the response type (e.g., "text", "buttons")
     * @param content the response text content
     * @param buttons optional action buttons for interactive messages
     */
    public record ResponsePayload(
            String type,
            String content,
            List<ButtonOption> buttons
    ) {
        /**
         * Creates a simple text response payload.
         *
         * @param content the response text
         * @return a text-only response payload
         */
        public static ResponsePayload text(String content) {
            return new ResponsePayload("text", content, null);
        }

        /**
         * Creates a response with buttons.
         *
         * @param content the response text
         * @param buttons the action buttons
         * @return a response payload with buttons
         */
        public static ResponsePayload withButtons(String content, List<ButtonOption> buttons) {
            return new ResponsePayload("buttons", content, buttons);
        }
    }

    /**
     * A button option for interactive messages.
     *
     * @param id the button identifier (sent back when clicked)
     * @param label the button display label
     */
    public record ButtonOption(
            String id,
            String label
    ) {}

    /**
     * Creates a successful response.
     *
     * @param content the response text
     * @param currentState the current workflow state
     * @param executionId the platform execution ID
     * @return a successful WorkflowExecuteResponse
     */
    public static WorkflowExecuteResponse success(String content, String currentState, String executionId) {
        return new WorkflowExecuteResponse(
                true,
                ResponsePayload.text(content),
                currentState,
                executionId,
                null,
                null
        );
    }

    /**
     * Creates an error response.
     *
     * @param errorMessage the error description
     * @param errorCode the error code
     * @return an error WorkflowExecuteResponse
     */
    public static WorkflowExecuteResponse error(String errorMessage, String errorCode) {
        return new WorkflowExecuteResponse(
                false,
                null,
                null,
                null,
                errorMessage,
                errorCode
        );
    }
}
