package com.dadcoach.conversation.sideeffect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SideEffect Enum Tests")
class SideEffectTest {

    @Test
    @DisplayName("MEMORY_EXTRACTION is mandatory with unlimited retries")
    void memoryExtraction_isMandatory() {
        assertThat(SideEffect.MEMORY_EXTRACTION.isMandatory()).isTrue();
        assertThat(SideEffect.MEMORY_EXTRACTION.getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("EVENT_PUBLISH is mandatory with unlimited retries")
    void eventPublish_isMandatory() {
        assertThat(SideEffect.EVENT_PUBLISH.isMandatory()).isTrue();
        assertThat(SideEffect.EVENT_PUBLISH.getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("METRIC_UPDATE is best-effort with 3 retries")
    void metricUpdate_isBestEffort() {
        assertThat(SideEffect.METRIC_UPDATE.isMandatory()).isFalse();
        assertThat(SideEffect.METRIC_UPDATE.getMaxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("DEFERRED_AI_REGENERATION is best-effort with 3 retries")
    void deferredAiRegeneration_isBestEffort() {
        assertThat(SideEffect.DEFERRED_AI_REGENERATION.isMandatory()).isFalse();
        assertThat(SideEffect.DEFERRED_AI_REGENERATION.getMaxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("MEMORY_INJECTION_TRACKING is best-effort with 3 retries")
    void memoryInjectionTracking_isBestEffort() {
        assertThat(SideEffect.MEMORY_INJECTION_TRACKING.isMandatory()).isFalse();
        assertThat(SideEffect.MEMORY_INJECTION_TRACKING.getMaxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("MEMORY_CONFIRMATION is best-effort with 3 retries")
    void memoryConfirmation_isBestEffort() {
        assertThat(SideEffect.MEMORY_CONFIRMATION.isMandatory()).isFalse();
        assertThat(SideEffect.MEMORY_CONFIRMATION.getMaxRetries()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(SideEffect.class)
    @DisplayName("All enum values have consistent isMandatory/getMaxRetries relationship")
    void allValues_haveConsistentRetryRelationship(SideEffect effect) {
        if (effect.isMandatory()) {
            assertThat(effect.getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
        } else {
            assertThat(effect.getMaxRetries()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("Enum has exactly 6 values")
    void enumHasSixValues() {
        assertThat(SideEffect.values()).hasSize(6);
    }
}
