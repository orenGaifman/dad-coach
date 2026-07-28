package com.dadcoach.onboarding.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed accessor class for structured access to the wizard session data.
 * This is NOT a JPA entity — it is serialized to/from JSON and stored encrypted
 * in the onboarding_sessions.wizard_data column via {@link WizardDataEncryptor}.
 *
 * <p>Fields are populated progressively as the user completes each wizard step:
 * <ul>
 *   <li>LANGUAGE step → language</li>
 *   <li>FATHER_PROFILE step → displayName, phoneNumber, email, timezone</li>
 *   <li>CHILDREN step → children</li>
 *   <li>GOALS step → goals</li>
 *   <li>PREFERENCES step → preferences</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WizardData implements Serializable {

    private String displayName;
    private String phoneNumber;
    private String email;
    private String timezone;
    private String language;
    private List<ChildData> children;
    private List<String> goals;
    private Map<String, Object> preferences;

    public WizardData() {
        this.children = new ArrayList<>();
        this.goals = new ArrayList<>();
        this.preferences = new HashMap<>();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<ChildData> getChildren() {
        return children;
    }

    public void setChildren(List<ChildData> children) {
        this.children = children != null ? children : new ArrayList<>();
    }

    public List<String> getGoals() {
        return goals;
    }

    public void setGoals(List<String> goals) {
        this.goals = goals != null ? goals : new ArrayList<>();
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, Object> preferences) {
        this.preferences = preferences != null ? preferences : new HashMap<>();
    }

    // ─── Nested Child Data ───────────────────────────────────────────────

    /**
     * Represents a single child's data collected during the CHILDREN wizard step.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChildData implements Serializable {
        private String name;
        private String birthDate;
        private String gender;

        public ChildData() {}

        public ChildData(String name, String birthDate, String gender) {
            this.name = name;
            this.birthDate = birthDate;
            this.gender = gender;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBirthDate() {
            return birthDate;
        }

        public void setBirthDate(String birthDate) {
            this.birthDate = birthDate;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }
    }
}
