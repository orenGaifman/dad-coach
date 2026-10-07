package com.dadcoach.api.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * The platform's tool envelope (snake_case). {@code user_id} is the ONLY trusted actor source: the father's
 * WhatsApp number ("+9725..." as the platform's Dad Coach client sends it, or "whatsapp:+9725..."). Nothing in
 * {@code parameters} or {@code execution_context} is ever trusted for identity.
 */
public record ToolRequest(@JsonProperty("execution_id") String executionId,
                          @JsonProperty("idempotency_key") String idempotencyKey,
                          @JsonProperty("user_id") String userId,
                          @JsonProperty("execution_context") Map<String, Object> executionContext,
                          Map<String, Object> parameters) {

    public String currentStateKey() {
        Object state = executionContext == null ? null : executionContext.get("currentStateKey");
        return state == null ? null : state.toString();
    }
}
