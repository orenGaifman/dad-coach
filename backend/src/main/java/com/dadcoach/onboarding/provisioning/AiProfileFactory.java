package com.dadcoach.onboarding.provisioning;

import com.dadcoach.common.AppConstants;
import com.dadcoach.onboarding.session.WizardData;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.UUID;

/**
 * Factory that builds an {@link AiProfile} entity from completed wizard data.
 * Extracts coaching style, language, children context (names/ages),
 * goals context, and a personality brief for the Intelligence Layer.
 */
@Component
public class AiProfileFactory {

    /**
     * Builds an AI profile from the completed wizard data.
     *
     * @param fatherId   the father's UUID
     * @param wizardData the completed wizard data
     * @return a new AiProfile entity (not yet persisted)
     */
    public AiProfile buildProfile(UUID fatherId, WizardData wizardData) {
        String coachingStyle = extractCoachingStyle(wizardData);
        String language = wizardData.getLanguage() != null ? wizardData.getLanguage() : AppConstants.DEFAULT_LOCALE;
        String childrenContext = buildChildrenContext(wizardData.getChildren());
        String goalsContext = buildGoalsContext(wizardData.getGoals());
        String personalityBrief = buildPersonalityBrief(wizardData);

        return new AiProfile(fatherId, coachingStyle, language,
                childrenContext, goalsContext, personalityBrief);
    }

    private String extractCoachingStyle(WizardData wizardData) {
        Map<String, Object> preferences = wizardData.getPreferences();
        if (preferences != null && preferences.containsKey("coaching_style")) {
            return String.valueOf(preferences.get("coaching_style"));
        }
        return "BALANCED";
    }

    private String buildChildrenContext(List<WizardData.ChildData> children) {
        if (children == null || children.isEmpty()) {
            return "No children registered yet.";
        }

        return children.stream()
                .map(child -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(child.getName() != null ? child.getName() : "Child");
                    if (child.getBirthDate() != null) {
                        try {
                            LocalDate birthDate = LocalDate.parse(child.getBirthDate());
                            int age = Period.between(birthDate, LocalDate.now()).getYears();
                            sb.append(" (age ").append(age).append(")");
                        } catch (Exception e) {
                            // If birth date is not parseable, skip age
                        }
                    }
                    if (child.getGender() != null) {
                        sb.append(", ").append(child.getGender());
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("; "));
    }

    private String buildGoalsContext(List<String> goals) {
        if (goals == null || goals.isEmpty()) {
            return "No goals specified.";
        }
        return String.join(", ", goals);
    }

    private String buildPersonalityBrief(WizardData wizardData) {
        StringBuilder brief = new StringBuilder();
        brief.append("Father: ").append(wizardData.getDisplayName() != null ? wizardData.getDisplayName() : "Unknown");
        brief.append(". Language: ").append(wizardData.getLanguage() != null ? wizardData.getLanguage() : "he");

        List<WizardData.ChildData> children = wizardData.getChildren();
        if (children != null && !children.isEmpty()) {
            brief.append(". Children: ").append(children.size());
        }

        List<String> goals = wizardData.getGoals();
        if (goals != null && !goals.isEmpty()) {
            brief.append(". Focus areas: ").append(String.join(", ", goals));
        }

        Map<String, Object> preferences = wizardData.getPreferences();
        if (preferences != null && preferences.containsKey("coaching_style")) {
            brief.append(". Coaching style: ").append(preferences.get("coaching_style"));
        }

        return brief.toString();
    }
}
