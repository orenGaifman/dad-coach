package com.dadcoach.onboarding.invitation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationTypeTest {

    @Test
    void singleUse_getDefaultMaxUses_returnsOne() {
        assertThat(InvitationType.SINGLE_USE.getDefaultMaxUses()).isEqualTo(1);
    }

    @Test
    void reusable_getDefaultMaxUses_returnsFifty() {
        assertThat(InvitationType.REUSABLE.getDefaultMaxUses()).isEqualTo(50);
    }

    @Test
    void singleUse_getExpirationDays_returnsSeven() {
        assertThat(InvitationType.SINGLE_USE.getExpirationDays()).isEqualTo(7);
    }

    @Test
    void reusable_getExpirationDays_returnsNinety() {
        assertThat(InvitationType.REUSABLE.getExpirationDays()).isEqualTo(90);
    }

    @Test
    void enumHasAllExpectedValues() {
        assertThat(InvitationType.values()).containsExactly(
                InvitationType.SINGLE_USE,
                InvitationType.REUSABLE);
    }
}
