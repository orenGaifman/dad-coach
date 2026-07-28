package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing the father's profile data for the workspace profile view.
 *
 * <p>Phone is masked for security — only the last 4 digits are shown.
 * Days since activation is computed dynamically from the activated_at timestamp.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponse {

    @JsonProperty("display_name")
    private final String displayName;

    /**
     * Masked phone number — shows only last 4 digits (e.g., "***-***-1234").
     */
    private final String phone;

    private final String timezone;

    @JsonProperty("coaching_style")
    private final String coachingStyle;

    @JsonProperty("preferred_coaching_time")
    private final String preferredCoachingTime;

    @JsonProperty("language_preference")
    private final String languagePreference;

    @JsonProperty("coaching_phase")
    private final String coachingPhase;

    @JsonProperty("days_since_activation")
    private final Long daysSinceActivation;

    @JsonProperty("account_status")
    private final String accountStatus;

    private ProfileResponse(Builder builder) {
        this.displayName = builder.displayName;
        this.phone = builder.phone;
        this.timezone = builder.timezone;
        this.coachingStyle = builder.coachingStyle;
        this.preferredCoachingTime = builder.preferredCoachingTime;
        this.languagePreference = builder.languagePreference;
        this.coachingPhase = builder.coachingPhase;
        this.daysSinceActivation = builder.daysSinceActivation;
        this.accountStatus = builder.accountStatus;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCoachingStyle() {
        return coachingStyle;
    }

    public String getPreferredCoachingTime() {
        return preferredCoachingTime;
    }

    public String getLanguagePreference() {
        return languagePreference;
    }

    public String getCoachingPhase() {
        return coachingPhase;
    }

    public Long getDaysSinceActivation() {
        return daysSinceActivation;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    // Builder

    public static class Builder {
        private String displayName;
        private String phone;
        private String timezone;
        private String coachingStyle;
        private String preferredCoachingTime;
        private String languagePreference;
        private String coachingPhase;
        private Long daysSinceActivation;
        private String accountStatus;

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder timezone(String timezone) {
            this.timezone = timezone;
            return this;
        }

        public Builder coachingStyle(String coachingStyle) {
            this.coachingStyle = coachingStyle;
            return this;
        }

        public Builder preferredCoachingTime(String preferredCoachingTime) {
            this.preferredCoachingTime = preferredCoachingTime;
            return this;
        }

        public Builder languagePreference(String languagePreference) {
            this.languagePreference = languagePreference;
            return this;
        }

        public Builder coachingPhase(String coachingPhase) {
            this.coachingPhase = coachingPhase;
            return this;
        }

        public Builder daysSinceActivation(Long daysSinceActivation) {
            this.daysSinceActivation = daysSinceActivation;
            return this;
        }

        public Builder accountStatus(String accountStatus) {
            this.accountStatus = accountStatus;
            return this;
        }

        public ProfileResponse build() {
            return new ProfileResponse(this);
        }
    }
}
