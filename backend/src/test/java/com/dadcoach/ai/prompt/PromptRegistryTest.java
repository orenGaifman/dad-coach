package com.dadcoach.ai.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PromptRegistry: template loading, version retrieval, and A/B assignment.
 */
class PromptRegistryTest {

    private PromptRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PromptRegistry();
    }

    // === Template registration ===

    @Test
    void shouldRegisterTemplate() {
        PromptTemplate template = createTemplate(PromptType.SYSTEM, "1.0.0", true, null);
        registry.register(template);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.getRegisteredTypes()).contains(PromptType.SYSTEM);
    }

    @Test
    void shouldRegisterMultipleVersionsOfSameType() {
        registry.register(createTemplate(PromptType.SYSTEM, "1.0.0", false, null));
        registry.register(createTemplate(PromptType.SYSTEM, "1.1.0", true, null));

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.getAllVersions(PromptType.SYSTEM)).hasSize(2);
    }

    @Test
    void shouldRejectDuplicateVersionAndGroup() {
        registry.register(createTemplate(PromptType.SYSTEM, "1.0.0", true, null));

        assertThatThrownBy(() ->
            registry.register(createTemplate(PromptType.SYSTEM, "1.0.0", false, null))
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("immutable");
    }

    @Test
    void shouldAllowSameVersionDifferentGroups() {
        registry.register(createTemplate(PromptType.SYSTEM, "1.0.0", true, "A"));
        registry.register(createTemplate(PromptType.SYSTEM, "1.0.0", true, "B"));

        assertThat(registry.size()).isEqualTo(2);
    }

    // === Active template retrieval ===

    @Test
    void shouldGetActiveTemplate() {
        registry.register(createTemplate(PromptType.PERSONA, "1.0.0", false, null));
        registry.register(createTemplate(PromptType.PERSONA, "1.1.0", true, null));

        Optional<PromptTemplate> active = registry.getActiveTemplate(PromptType.PERSONA);

        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(PromptVersion.parse("1.1.0"));
    }

    @Test
    void shouldReturnEmptyWhenNoActiveTemplate() {
        registry.register(createTemplate(PromptType.SUMMARY, "1.0.0", false, null));

        Optional<PromptTemplate> active = registry.getActiveTemplate(PromptType.SUMMARY);

        assertThat(active).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnregisteredType() {
        Optional<PromptTemplate> active = registry.getActiveTemplate(PromptType.SAFETY);
        assertThat(active).isEmpty();
    }

    // === A/B test retrieval ===

    @Test
    void shouldReturnGroupSpecificTemplateForFather() {
        registry.register(createTemplate(PromptType.SYSTEM, "1.0.0", true, "A"));
        registry.register(createTemplate(PromptType.SYSTEM, "1.1.0", true, "B"));

        UUID fatherId = UUID.randomUUID();
        String group = AbTestAssigner.assignGroup(fatherId);

        Optional<PromptTemplate> template = registry.getActiveTemplateForFather(PromptType.SYSTEM, fatherId);

        assertThat(template).isPresent();
        assertThat(template.get().abTestGroup()).isEqualTo(group);
    }

    @Test
    void shouldFallBackToDefaultWhenNoGroupTemplate() {
        registry.register(createTemplate(PromptType.SYSTEM, "1.0.0", true, null));

        UUID fatherId = UUID.randomUUID();
        Optional<PromptTemplate> template = registry.getActiveTemplateForFather(PromptType.SYSTEM, fatherId);

        assertThat(template).isPresent();
        assertThat(template.get().abTestGroup()).isNull();
    }

    @Test
    void shouldReturnSameTemplateForSameFatherEveryTime() {
        registry.register(createTemplate(PromptType.SYSTEM, "1.0.0", true, "A"));
        registry.register(createTemplate(PromptType.SYSTEM, "1.1.0", true, "B"));

        UUID fatherId = UUID.randomUUID();
        Optional<PromptTemplate> first = registry.getActiveTemplateForFather(PromptType.SYSTEM, fatherId);
        Optional<PromptTemplate> second = registry.getActiveTemplateForFather(PromptType.SYSTEM, fatherId);
        Optional<PromptTemplate> third = registry.getActiveTemplateForFather(PromptType.SYSTEM, fatherId);

        assertThat(first).isEqualTo(second);
        assertThat(second).isEqualTo(third);
    }

    // === Version listing ===

    @Test
    void shouldListVersionsSortedDescending() {
        registry.register(createTemplate(PromptType.MISSION_GEN, "1.0.0", false, null));
        registry.register(createTemplate(PromptType.MISSION_GEN, "2.0.0", true, null));
        registry.register(createTemplate(PromptType.MISSION_GEN, "1.5.0", false, null));

        var versions = registry.getAllVersions(PromptType.MISSION_GEN);

        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).version()).isEqualTo(PromptVersion.parse("2.0.0"));
        assertThat(versions.get(1).version()).isEqualTo(PromptVersion.parse("1.5.0"));
        assertThat(versions.get(2).version()).isEqualTo(PromptVersion.parse("1.0.0"));
    }

    @Test
    void shouldGetSpecificVersion() {
        registry.register(createTemplate(PromptType.REFLECTION, "1.0.0", false, null));
        registry.register(createTemplate(PromptType.REFLECTION, "2.0.0", true, null));

        Optional<PromptTemplate> v1 = registry.getVersion(PromptType.REFLECTION, PromptVersion.parse("1.0.0"));

        assertThat(v1).isPresent();
        assertThat(v1.get().version()).isEqualTo(PromptVersion.parse("1.0.0"));
    }

    // === YAML loading from resources ===

    @Test
    void shouldLoadTemplatesFromResourcesAtPostConstruct() {
        PromptRegistry resourceRegistry = new PromptRegistry();
        resourceRegistry.loadTemplates();

        // Verify templates were loaded from the YAML files
        assertThat(resourceRegistry.size()).isGreaterThan(0);
        assertThat(resourceRegistry.getRegisteredTypes()).isNotEmpty();
    }

    @Test
    void shouldLoadSystemTemplateFromResources() {
        PromptRegistry resourceRegistry = new PromptRegistry();
        resourceRegistry.loadTemplates();

        Optional<PromptTemplate> system = resourceRegistry.getActiveTemplate(PromptType.SYSTEM);
        assertThat(system).isPresent();
        assertThat(system.get().content()).contains("coaching assistant");
    }

    @Test
    void shouldLoadAllPromptTypesFromResources() {
        PromptRegistry resourceRegistry = new PromptRegistry();
        resourceRegistry.loadTemplates();

        // All 10 YAML files should have loaded
        assertThat(resourceRegistry.getRegisteredTypes()).containsExactlyInAnyOrder(
            PromptType.SYSTEM,
            PromptType.PERSONA,
            PromptType.MISSION_GEN,
            PromptType.REFLECTION,
            PromptType.SUMMARY,
            PromptType.ONBOARDING,
            PromptType.CELEBRATION,
            PromptType.FOLLOW_UP,
            PromptType.INACTIVITY,
            PromptType.SAFETY
        );
    }

    // === Placeholder resolution ===

    @Test
    void shouldResolveTemplateWithPlaceholders() {
        PromptTemplate template = new PromptTemplate(
            PromptType.SYSTEM,
            PromptVersion.parse("1.0.0"),
            "Hello {{father_name}}, your child {{child_name}} is in phase {{phase}}.",
            true,
            null,
            Instant.now()
        );

        String resolved = template.resolve(Map.of(
            "father_name", "Carlos",
            "child_name", "Mateo",
            "phase", "BUILDING"
        ));

        assertThat(resolved).isEqualTo("Hello Carlos, your child Mateo is in phase BUILDING.");
    }

    @Test
    void shouldLeaveUnresolvedPlaceholdersIntact() {
        PromptTemplate template = new PromptTemplate(
            PromptType.SYSTEM,
            PromptVersion.parse("1.0.0"),
            "Hello {{father_name}}, phase: {{phase}}.",
            true,
            null,
            Instant.now()
        );

        String resolved = template.resolve(Map.of("father_name", "Carlos"));

        assertThat(resolved).isEqualTo("Hello Carlos, phase: {{phase}}.");
    }

    @Test
    void shouldResolveTemplateWithNoPlaceholders() {
        PromptTemplate template = new PromptTemplate(
            PromptType.SAFETY,
            PromptVersion.parse("1.0.0"),
            "Simple template with no placeholders.",
            true,
            null,
            Instant.now()
        );

        String resolved = template.resolve(Map.of("unused", "value"));

        assertThat(resolved).isEqualTo("Simple template with no placeholders.");
    }

    // === PromptVersion ===

    @Test
    void shouldParseVersionString() {
        PromptVersion v = PromptVersion.parse("2.3.1");
        assertThat(v.major()).isEqualTo(2);
        assertThat(v.minor()).isEqualTo(3);
        assertThat(v.patch()).isEqualTo(1);
    }

    @Test
    void shouldCompareVersionsCorrectly() {
        PromptVersion v1 = PromptVersion.parse("1.0.0");
        PromptVersion v2 = PromptVersion.parse("1.1.0");
        PromptVersion v3 = PromptVersion.parse("2.0.0");

        assertThat(v1).isLessThan(v2);
        assertThat(v2).isLessThan(v3);
        assertThat(v1).isLessThan(v3);
    }

    @Test
    void shouldRejectInvalidVersionString() {
        assertThatThrownBy(() -> PromptVersion.parse("not.a.version"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> PromptVersion.parse("1.0"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // === AbTestAssigner ===

    @Test
    void shouldAssignDeterministically() {
        UUID fatherId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String group1 = AbTestAssigner.assignGroup(fatherId);
        String group2 = AbTestAssigner.assignGroup(fatherId);
        String group3 = AbTestAssigner.assignGroup(fatherId);

        assertThat(group1).isEqualTo(group2).isEqualTo(group3);
        assertThat(group1).isIn("A", "B");
    }

    @Test
    void shouldRejectNullFatherId() {
        assertThatThrownBy(() -> AbTestAssigner.assignGroup((UUID) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectBlankStringFatherId() {
        assertThatThrownBy(() -> AbTestAssigner.assignGroup("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // === Helper methods ===

    private PromptTemplate createTemplate(PromptType type, String version, boolean isActive, String abGroup) {
        return new PromptTemplate(
            type,
            PromptVersion.parse(version),
            "Template content for %s v%s".formatted(type, version),
            isActive,
            abGroup,
            Instant.now()
        );
    }
}
