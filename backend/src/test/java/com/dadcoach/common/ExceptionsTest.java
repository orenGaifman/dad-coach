package com.dadcoach.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    // --- InvalidStateTransitionException ---

    @Test
    void invalidStateTransition_messageContainsAllDetails() {
        var ex = new InvalidStateTransitionException("Father", 42L, "ACTIVE", "ONBOARDING");

        assertThat(ex.getMessage())
                .contains("Father")
                .contains("42")
                .contains("ACTIVE")
                .contains("ONBOARDING");
    }

    @Test
    void invalidStateTransition_gettersReturnConstructorValues() {
        var ex = new InvalidStateTransitionException("Mission", 7L, "ASSIGNED", "COMPLETED");

        assertThat(ex.getEntityType()).isEqualTo("Mission");
        assertThat(ex.getEntityId()).isEqualTo(7L);
        assertThat(ex.getFromState()).isEqualTo("ASSIGNED");
        assertThat(ex.getToState()).isEqualTo("COMPLETED");
    }

    @Test
    void invalidStateTransition_extendsRuntimeException() {
        var ex = new InvalidStateTransitionException("Father", 1L, "A", "B");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    // --- BusinessRuleViolationException ---

    @Test
    void businessRuleViolation_messageContainsRuleNameAndDetail() {
        var ex = new BusinessRuleViolationException(
                "MAX_CHILDREN_EXCEEDED", "A father can have at most 8 children");

        assertThat(ex.getMessage())
                .contains("MAX_CHILDREN_EXCEEDED")
                .contains("A father can have at most 8 children");
    }

    @Test
    void businessRuleViolation_gettersReturnConstructorValues() {
        var ex = new BusinessRuleViolationException(
                "MAX_GOALS_EXCEEDED", "Maximum 5 active goals allowed");

        assertThat(ex.getRuleName()).isEqualTo("MAX_GOALS_EXCEEDED");
        assertThat(ex.getDetail()).isEqualTo("Maximum 5 active goals allowed");
    }

    @Test
    void businessRuleViolation_extendsRuntimeException() {
        var ex = new BusinessRuleViolationException("RULE", "msg");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    // --- ResourceNotFoundException ---

    @Test
    void resourceNotFound_messageContainsEntityTypeAndIdentifier() {
        var ex = new ResourceNotFoundException("Father", 123L);

        assertThat(ex.getMessage())
                .contains("Father")
                .contains("123");
    }

    @Test
    void resourceNotFound_worksWithStringIdentifier() {
        var ex = new ResourceNotFoundException("Father", "+972501234567");

        assertThat(ex.getMessage())
                .contains("Father")
                .contains("+972501234567");
    }

    @Test
    void resourceNotFound_gettersReturnConstructorValues() {
        var ex = new ResourceNotFoundException("Child", 99L);

        assertThat(ex.getEntityType()).isEqualTo("Child");
        assertThat(ex.getIdentifier()).isEqualTo(99L);
    }

    @Test
    void resourceNotFound_extendsRuntimeException() {
        var ex = new ResourceNotFoundException("Entity", 1L);

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
