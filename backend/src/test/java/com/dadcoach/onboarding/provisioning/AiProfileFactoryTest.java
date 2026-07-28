package com.dadcoach.onboarding.provisioning;

import com.dadcoach.onboarding.session.WizardData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiProfileFactoryTest {

    private AiProfileFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AiProfileFactory();
    }

    @Test
    @DisplayName("should build profile with coaching style from preferences")
    void shouldBuildProfileWithCoachingStyle() {
        WizardData data = new WizardData();
        data.setDisplayName("David");
        data.setLanguage("he");
        data.getPreferences().put("coaching_style", "GENTLE");

        AiProfile profile = factory.buildProfile(new UUID(0L, 1L), data);

        assertThat(profile.getCoachingStyle()).isEqualTo("GENTLE");
        assertThat(profile.getLanguage()).isEqualTo("he");
        assertThat(profile.getFatherId()).isEqualTo(new UUID(0L, 1L));
    }

    @Test
    @DisplayName("should default coaching style to BALANCED when not in preferences")
    void shouldDefaultCoachingStyleToBalanced() {
        WizardData data = new WizardData();
        data.setDisplayName("David");
        data.setLanguage("en");

        AiProfile profile = factory.buildProfile(new UUID(0L, 1L), data);

        assertThat(profile.getCoachingStyle()).isEqualTo("BALANCED");
    }

    @Test
    @DisplayName("should build children context with names and ages")
    void shouldBuildChildrenContext() {
        WizardData data = new WizardData();
        data.setDisplayName("David");
        data.setLanguage("he");
        data.setChildren(List.of(
                new WizardData.ChildData("Yoav", "2018-05-15", "male"),
                new WizardData.ChildData("Noa", "2020-11-03", "female")
        ));

        AiProfile profile = factory.buildProfile(new UUID(0L, 1L), data);

        assertThat(profile.getChildrenContext()).contains("Yoav");
        assertThat(profile.getChildrenContext()).contains("Noa");
        assertThat(profile.getChildrenContext()).contains("age");
        assertThat(profile.getChildrenContext()).contains("male");
        assertThat(profile.getChildrenContext()).contains("female");
    }

    @Test
    @DisplayName("should handle empty children list")
    void shouldHandleEmptyChildrenList() {
        WizardData data = new WizardData();
        data.setDisplayName("David");
        data.setLanguage("he");
        data.setChildren(List.of());

        AiProfile profile = factory.buildProfile(new UUID(0L, 1L), data);

        assertThat(profile.getChildrenContext()).isEqualTo("No children registered yet.");
    }

    @Test
    @DisplayName("should build goals context from goal list")
    void shouldBuildGoalsContext() {
        WizardData data = new WizardData();
        data.setDisplayName("David");
        data.setLanguage("he");
        data.setGoals(List.of("Better communication", "More quality time", "Set healthy boundaries"));

        AiProfile profile = factory.buildProfile(new UUID(0L, 1L), data);

        assertThat(profile.getGoalsContext()).contains("Better communication");
        assertThat(profile.getGoalsContext()).contains("More quality time");
        assertThat(profile.getGoalsContext()).contains("Set healthy boundaries");
    }

    @Test
    @DisplayName("should build personality brief with all available information")
    void shouldBuildPersonalityBrief() {
        WizardData data = new WizardData();
        data.setDisplayName("David");
        data.setLanguage("he");
        data.setChildren(List.of(
                new WizardData.ChildData("Yoav", "2018-05-15", "male")
        ));
        data.setGoals(List.of("Connection", "Fun"));
        data.getPreferences().put("coaching_style", "MOTIVATIONAL");

        AiProfile profile = factory.buildProfile(new UUID(0L, 1L), data);

        assertThat(profile.getPersonalityBrief()).contains("David");
        assertThat(profile.getPersonalityBrief()).contains("he");
        assertThat(profile.getPersonalityBrief()).contains("Children: 1");
        assertThat(profile.getPersonalityBrief()).contains("Connection");
        assertThat(profile.getPersonalityBrief()).contains("MOTIVATIONAL");
    }

    @Test
    @DisplayName("should default language to 'he' when null")
    void shouldDefaultLanguageToHebrew() {
        WizardData data = new WizardData();
        data.setDisplayName("David");
        data.setLanguage(null);

        AiProfile profile = factory.buildProfile(new UUID(0L, 1L), data);

        assertThat(profile.getLanguage()).isEqualTo("he");
    }
}
