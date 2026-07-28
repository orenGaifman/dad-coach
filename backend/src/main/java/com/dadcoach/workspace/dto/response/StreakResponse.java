package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Response DTO for the streak endpoint (GET /api/v1/workspace/growth/streak).
 *
 * <p>Returns the father's current streak status including days count, longest
 * streak record, and whether the streak is at risk of breaking today.</p>
 *
 * @see com.dadcoach.workspace.growth.streak.StreakService
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreakResponse {

    @JsonProperty("current_streak_days")
    private final int currentStreakDays;

    @JsonProperty("longest_streak_days")
    private final int longestStreakDays;

    @JsonProperty("streak_start_date")
    private final LocalDate streakStartDate;

    @JsonProperty("last_qualifying_interaction_date")
    private final LocalDate lastQualifyingInteractionDate;

    @JsonProperty("streak_at_risk")
    private final boolean streakAtRisk;

    private StreakResponse(Builder builder) {
        this.currentStreakDays = builder.currentStreakDays;
        this.longestStreakDays = builder.longestStreakDays;
        this.streakStartDate = builder.streakStartDate;
        this.lastQualifyingInteractionDate = builder.lastQualifyingInteractionDate;
        this.streakAtRisk = builder.streakAtRisk;
    }

    public int getCurrentStreakDays() {
        return currentStreakDays;
    }

    public int getLongestStreakDays() {
        return longestStreakDays;
    }

    public LocalDate getStreakStartDate() {
        return streakStartDate;
    }

    public LocalDate getLastQualifyingInteractionDate() {
        return lastQualifyingInteractionDate;
    }

    public boolean isStreakAtRisk() {
        return streakAtRisk;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int currentStreakDays;
        private int longestStreakDays;
        private LocalDate streakStartDate;
        private LocalDate lastQualifyingInteractionDate;
        private boolean streakAtRisk;

        private Builder() {
        }

        public Builder currentStreakDays(int currentStreakDays) {
            this.currentStreakDays = currentStreakDays;
            return this;
        }

        public Builder longestStreakDays(int longestStreakDays) {
            this.longestStreakDays = longestStreakDays;
            return this;
        }

        public Builder streakStartDate(LocalDate streakStartDate) {
            this.streakStartDate = streakStartDate;
            return this;
        }

        public Builder lastQualifyingInteractionDate(LocalDate lastQualifyingInteractionDate) {
            this.lastQualifyingInteractionDate = lastQualifyingInteractionDate;
            return this;
        }

        public Builder streakAtRisk(boolean streakAtRisk) {
            this.streakAtRisk = streakAtRisk;
            return this;
        }

        public StreakResponse build() {
            return new StreakResponse(this);
        }
    }
}
