package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for the quick actions endpoint (GET /api/v1/workspace/quick-actions).
 *
 * <p>Returns a priority-ordered list of contextual action suggestions for the father,
 * computed on-demand based on current state signals.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuickActionsResponse {

    @JsonProperty("actions")
    private final List<QuickActionItem> actions;

    public QuickActionsResponse(List<QuickActionItem> actions) {
        this.actions = actions;
    }

    public List<QuickActionItem> getActions() {
        return actions;
    }

    /**
     * Represents a single quick action item in the response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuickActionItem {

        @JsonProperty("action_id")
        private final UUID actionId;

        @JsonProperty("action_type")
        private final String actionType;

        @JsonProperty("title")
        private final String title;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("priority")
        private final int priority;

        @JsonProperty("action_metadata")
        private final Map<String, String> actionMetadata;

        public QuickActionItem(UUID actionId, String actionType, String title,
                               String description, int priority,
                               Map<String, String> actionMetadata) {
            this.actionId = actionId;
            this.actionType = actionType;
            this.title = title;
            this.description = description;
            this.priority = priority;
            this.actionMetadata = actionMetadata;
        }

        public UUID getActionId() {
            return actionId;
        }

        public String getActionType() {
            return actionType;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public int getPriority() {
            return priority;
        }

        public Map<String, String> getActionMetadata() {
            return actionMetadata;
        }
    }
}
