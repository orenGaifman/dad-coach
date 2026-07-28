package com.dadcoach.onboarding.invitation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationStatusTest {

    @Test
    void created_canTransitionToSent() {
        assertThat(InvitationStatus.CREATED.canTransitionTo(InvitationStatus.SENT)).isTrue();
    }

    @Test
    void created_canTransitionToExpired() {
        assertThat(InvitationStatus.CREATED.canTransitionTo(InvitationStatus.EXPIRED)).isTrue();
    }

    @Test
    void created_canTransitionToRevoked() {
        assertThat(InvitationStatus.CREATED.canTransitionTo(InvitationStatus.REVOKED)).isTrue();
    }

    @Test
    void created_cannotTransitionToOpened() {
        assertThat(InvitationStatus.CREATED.canTransitionTo(InvitationStatus.OPENED)).isFalse();
    }

    @Test
    void created_cannotTransitionToUsed() {
        assertThat(InvitationStatus.CREATED.canTransitionTo(InvitationStatus.USED)).isFalse();
    }

    @Test
    void sent_canTransitionToOpened() {
        assertThat(InvitationStatus.SENT.canTransitionTo(InvitationStatus.OPENED)).isTrue();
    }

    @Test
    void sent_canTransitionToExpired() {
        assertThat(InvitationStatus.SENT.canTransitionTo(InvitationStatus.EXPIRED)).isTrue();
    }

    @Test
    void sent_canTransitionToRevoked() {
        assertThat(InvitationStatus.SENT.canTransitionTo(InvitationStatus.REVOKED)).isTrue();
    }

    @Test
    void sent_cannotTransitionToUsed() {
        assertThat(InvitationStatus.SENT.canTransitionTo(InvitationStatus.USED)).isFalse();
    }

    @Test
    void opened_canTransitionToUsed() {
        assertThat(InvitationStatus.OPENED.canTransitionTo(InvitationStatus.USED)).isTrue();
    }

    @Test
    void opened_canTransitionToExpired() {
        assertThat(InvitationStatus.OPENED.canTransitionTo(InvitationStatus.EXPIRED)).isTrue();
    }

    @Test
    void opened_canTransitionToRevoked() {
        assertThat(InvitationStatus.OPENED.canTransitionTo(InvitationStatus.REVOKED)).isTrue();
    }

    @Test
    void opened_cannotTransitionToSent() {
        assertThat(InvitationStatus.OPENED.canTransitionTo(InvitationStatus.SENT)).isFalse();
    }

    @Test
    void used_canTransitionToUsedForReusableInvitations() {
        assertThat(InvitationStatus.USED.canTransitionTo(InvitationStatus.USED)).isTrue();
    }

    @Test
    void used_cannotTransitionToExpired() {
        assertThat(InvitationStatus.USED.canTransitionTo(InvitationStatus.EXPIRED)).isFalse();
    }

    @Test
    void expired_hasNoAllowedTransitions() {
        assertThat(InvitationStatus.EXPIRED.getAllowedTransitions()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(InvitationStatus.class)
    void expired_cannotTransitionToAnyStatus(InvitationStatus target) {
        assertThat(InvitationStatus.EXPIRED.canTransitionTo(target)).isFalse();
    }

    @Test
    void revoked_hasNoAllowedTransitions() {
        assertThat(InvitationStatus.REVOKED.getAllowedTransitions()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(InvitationStatus.class)
    void revoked_cannotTransitionToAnyStatus(InvitationStatus target) {
        assertThat(InvitationStatus.REVOKED.canTransitionTo(target)).isFalse();
    }

    @Test
    void expired_isTerminal() {
        assertThat(InvitationStatus.EXPIRED.isTerminal()).isTrue();
    }

    @Test
    void revoked_isTerminal() {
        assertThat(InvitationStatus.REVOKED.isTerminal()).isTrue();
    }

    @Test
    void created_isNotTerminal() {
        assertThat(InvitationStatus.CREATED.isTerminal()).isFalse();
    }

    @Test
    void sent_isNotTerminal() {
        assertThat(InvitationStatus.SENT.isTerminal()).isFalse();
    }

    @Test
    void opened_isNotTerminal() {
        assertThat(InvitationStatus.OPENED.isTerminal()).isFalse();
    }

    @Test
    void used_isNotTerminal() {
        assertThat(InvitationStatus.USED.isTerminal()).isFalse();
    }

    @Test
    void created_getAllowedTransitions_returnsCorrectSet() {
        Set<InvitationStatus> allowed = InvitationStatus.CREATED.getAllowedTransitions();
        assertThat(allowed).containsExactlyInAnyOrder(
                InvitationStatus.SENT, InvitationStatus.EXPIRED, InvitationStatus.REVOKED);
    }

    @Test
    void sent_getAllowedTransitions_returnsCorrectSet() {
        Set<InvitationStatus> allowed = InvitationStatus.SENT.getAllowedTransitions();
        assertThat(allowed).containsExactlyInAnyOrder(
                InvitationStatus.OPENED, InvitationStatus.EXPIRED, InvitationStatus.REVOKED);
    }

    @Test
    void opened_getAllowedTransitions_returnsCorrectSet() {
        Set<InvitationStatus> allowed = InvitationStatus.OPENED.getAllowedTransitions();
        assertThat(allowed).containsExactlyInAnyOrder(
                InvitationStatus.USED, InvitationStatus.EXPIRED, InvitationStatus.REVOKED);
    }

    @Test
    void used_getAllowedTransitions_containsOnlyUsed() {
        Set<InvitationStatus> allowed = InvitationStatus.USED.getAllowedTransitions();
        assertThat(allowed).containsExactly(InvitationStatus.USED);
    }

    @Test
    void enumHasAllExpectedValues() {
        assertThat(InvitationStatus.values()).containsExactly(
                InvitationStatus.CREATED,
                InvitationStatus.SENT,
                InvitationStatus.OPENED,
                InvitationStatus.USED,
                InvitationStatus.EXPIRED,
                InvitationStatus.REVOKED);
    }
}
