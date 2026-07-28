package com.dadcoach.api.goal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Validated request body for updating an existing goal.
 * All fields are optional — only provided fields are updated.
 *
 * <p>Completed goals cannot be modified (enforced by controller).</p>
 */
public class GoalUpdateRequest {

    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;

    @Size(min = 1, max = 500, message = "Description must be between 1 and 500 characters")
    private String description;

    @Min(value = 1, message = "Priority must be between 1 and 5")
    @Max(value = 5, message = "Priority must be between 1 and 5")
    private Integer priority;

    public GoalUpdateRequest() {
    }

    public GoalUpdateRequest(String title, String description, Integer priority) {
        this.title = title;
        this.description = description;
        this.priority = priority;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
