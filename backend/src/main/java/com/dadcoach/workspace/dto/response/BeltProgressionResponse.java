package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Response DTO for the belt progression endpoint (GET /api/v1/workspace/growth/belt).
 *
 * <p>Returns the father's current belt level, score, and progress toward the next belt.
 * For fathers at BLACK belt (max level), {@code nextBelt} and {@code nextBeltDescription}
 * are null, {@code pointsToNextBelt} is 0, and {@code progressPercentageToNextBelt} is 100.</p>
 *
 * @see com.dadcoach.workspace.growth.belt.BeltLevel
 * @see com.dadcoach.workspace.growth.belt.BeltProgressionService
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BeltProgressionResponse {

    @JsonProperty("current_belt")
    private final String currentBelt;

    @JsonProperty("current_belt_description")
    private final String currentBeltDescription;

    @JsonProperty("current_score")
    private final int currentScore;

    @JsonProperty("next_belt")
    private final String nextBelt;

    @JsonProperty("next_belt_description")
    private final String nextBeltDescription;

    @JsonProperty("points_to_next_belt")
    private final int pointsToNextBelt;

    @JsonProperty("progress_percentage_to_next_belt")
    private final int progressPercentageToNextBelt;

    @JsonProperty("belt_earned_at")
    private final Instant beltEarnedAt;

    private BeltProgressionResponse(Builder builder) {
        this.currentBelt = builder.currentBelt;
        this.currentBeltDescription = builder.currentBeltDescription;
        this.currentScore = builder.currentScore;
        this.nextBelt = builder.nextBelt;
        this.nextBeltDescription = builder.nextBeltDescription;
        this.pointsToNextBelt = builder.pointsToNextBelt;
        this.progressPercentageToNextBelt = builder.progressPercentageToNextBelt;
        this.beltEarnedAt = builder.beltEarnedAt;
    }

    public String getCurrentBelt() {
        return currentBelt;
    }

    public String getCurrentBeltDescription() {
        return currentBeltDescription;
    }

    public int getCurrentScore() {
        return currentScore;
    }

    public String getNextBelt() {
        return nextBelt;
    }

    public String getNextBeltDescription() {
        return nextBeltDescription;
    }

    public int getPointsToNextBelt() {
        return pointsToNextBelt;
    }

    public int getProgressPercentageToNextBelt() {
        return progressPercentageToNextBelt;
    }

    public Instant getBeltEarnedAt() {
        return beltEarnedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String currentBelt;
        private String currentBeltDescription;
        private int currentScore;
        private String nextBelt;
        private String nextBeltDescription;
        private int pointsToNextBelt;
        private int progressPercentageToNextBelt;
        private Instant beltEarnedAt;

        private Builder() {
        }

        public Builder currentBelt(String currentBelt) {
            this.currentBelt = currentBelt;
            return this;
        }

        public Builder currentBeltDescription(String currentBeltDescription) {
            this.currentBeltDescription = currentBeltDescription;
            return this;
        }

        public Builder currentScore(int currentScore) {
            this.currentScore = currentScore;
            return this;
        }

        public Builder nextBelt(String nextBelt) {
            this.nextBelt = nextBelt;
            return this;
        }

        public Builder nextBeltDescription(String nextBeltDescription) {
            this.nextBeltDescription = nextBeltDescription;
            return this;
        }

        public Builder pointsToNextBelt(int pointsToNextBelt) {
            this.pointsToNextBelt = pointsToNextBelt;
            return this;
        }

        public Builder progressPercentageToNextBelt(int progressPercentageToNextBelt) {
            this.progressPercentageToNextBelt = progressPercentageToNextBelt;
            return this;
        }

        public Builder beltEarnedAt(Instant beltEarnedAt) {
            this.beltEarnedAt = beltEarnedAt;
            return this;
        }

        public BeltProgressionResponse build() {
            return new BeltProgressionResponse(this);
        }
    }
}
