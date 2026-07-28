package com.dadcoach.api.goal;

import com.dadcoach.goal.GoalCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Validated request body for creating a new parenting goal.
 *
 * <p>Validation rules (from Requirement 7 criteria 4):
 * <ul>
 *   <li>description: 1-500 characters, non-empty</li>
 *   <li>category: one of CONNECTION, COMMUNICATION, DISCIPLINE, EDUCATION, HEALTH,
 *       EMOTIONAL, INDEPENDENCE, FUN, ROUTINE, CUSTOM</li>
 *   <li>priority: integer 1-5</li>
 * </ul>
 */
public class GoalCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 1, max = 500, message = "Description must be between 1 and 500 characters")
    private String description;

    @NotNull(message = "Category is required")
    private GoalCategory category;

    @NotNull(message = "Priority is required")
    @Min(value = 1, message = "Priority must be between 1 and 5")
    @Max(value = 5, message = "Priority must be between 1 and 5")
    private Integer priority;

    public GoalCreateRequest() {
    }

    public GoalCreateRequest(String title, String description, GoalCategory category, Integer priority) {
        this.title = title;
        this.description = description;
        this.category = category;
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

    public GoalCategory getCategory() {
        return category;
    }

    public void setCategory(GoalCategory category) {
        this.category = category;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
