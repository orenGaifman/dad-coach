package com.dadcoach.onboarding.activation;

import com.dadcoach.onboarding.provisioning.ActivationStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ActivationStatus Transition Validation Tests")
class ActivationStatusTest {

    @Test
    @DisplayName("PENDING can transition to LINK_CLICKED, MESSAGE_SENT, or FAILED")
    void pendingValidTransitions() {
        assertThat(ActivationStatus.PENDING.canTransitionTo(ActivationStatus.LINK_CLICKED)).isTrue();
        assertThat(ActivationStatus.PENDING.canTransitionTo(ActivationStatus.MESSAGE_SENT)).isTrue();
        assertThat(ActivationStatus.PENDING.canTransitionTo(ActivationStatus.FAILED)).isTrue();
        assertThat(ActivationStatus.PENDING.canTransitionTo(ActivationStatus.CONVERSATION_STARTED)).isFalse();
    }

    @Test
    @DisplayName("LINK_CLICKED can transition to MESSAGE_SENT or FAILED")
    void linkClickedValidTransitions() {
        assertThat(ActivationStatus.LINK_CLICKED.canTransitionTo(ActivationStatus.MESSAGE_SENT)).isTrue();
        assertThat(ActivationStatus.LINK_CLICKED.canTransitionTo(ActivationStatus.FAILED)).isTrue();
        assertThat(ActivationStatus.LINK_CLICKED.canTransitionTo(ActivationStatus.PENDING)).isFalse();
        assertThat(ActivationStatus.LINK_CLICKED.canTransitionTo(ActivationStatus.CONVERSATION_STARTED)).isFalse();
    }

    @Test
    @DisplayName("MESSAGE_SENT can transition to CONVERSATION_STARTED or FAILED")
    void messageSentValidTransitions() {
        assertThat(ActivationStatus.MESSAGE_SENT.canTransitionTo(ActivationStatus.CONVERSATION_STARTED)).isTrue();
        assertThat(ActivationStatus.MESSAGE_SENT.canTransitionTo(ActivationStatus.FAILED)).isTrue();
        assertThat(ActivationStatus.MESSAGE_SENT.canTransitionTo(ActivationStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("FAILED can transition to PENDING (retry)")
    void failedCanRetry() {
        assertThat(ActivationStatus.FAILED.canTransitionTo(ActivationStatus.PENDING)).isTrue();
        assertThat(ActivationStatus.FAILED.canTransitionTo(ActivationStatus.LINK_CLICKED)).isFalse();
        assertThat(ActivationStatus.FAILED.canTransitionTo(ActivationStatus.CONVERSATION_STARTED)).isFalse();
    }

    @Test
    @DisplayName("CONVERSATION_STARTED is terminal — no transitions allowed")
    void conversationStartedIsTerminal() {
        assertThat(ActivationStatus.CONVERSATION_STARTED.isTerminal()).isTrue();
        assertThat(ActivationStatus.CONVERSATION_STARTED.getValidTransitions()).isEmpty();
        assertThat(ActivationStatus.CONVERSATION_STARTED.canTransitionTo(ActivationStatus.PENDING)).isFalse();
        assertThat(ActivationStatus.CONVERSATION_STARTED.canTransitionTo(ActivationStatus.FAILED)).isFalse();
    }

    @Test
    @DisplayName("transitionTo returns target status on valid transition")
    void transitionToReturnsTarget() {
        ActivationStatus result = ActivationStatus.PENDING.transitionTo(ActivationStatus.LINK_CLICKED);
        assertThat(result).isEqualTo(ActivationStatus.LINK_CLICKED);
    }

    @Test
    @DisplayName("transitionTo throws IllegalStateException on invalid transition")
    void transitionToThrowsOnInvalid() {
        assertThatThrownBy(() -> ActivationStatus.PENDING.transitionTo(ActivationStatus.CONVERSATION_STARTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid activation status transition: PENDING → CONVERSATION_STARTED");
    }

    @Test
    @DisplayName("only CONVERSATION_STARTED is terminal")
    void onlyConversationStartedIsTerminal() {
        assertThat(ActivationStatus.PENDING.isTerminal()).isFalse();
        assertThat(ActivationStatus.LINK_CLICKED.isTerminal()).isFalse();
        assertThat(ActivationStatus.MESSAGE_SENT.isTerminal()).isFalse();
        assertThat(ActivationStatus.FAILED.isTerminal()).isFalse();
        assertThat(ActivationStatus.CONVERSATION_STARTED.isTerminal()).isTrue();
    }
}
