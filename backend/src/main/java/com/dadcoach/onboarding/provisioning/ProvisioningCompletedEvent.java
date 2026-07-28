package com.dadcoach.onboarding.provisioning;

import com.dadcoach.onboarding.session.WizardData;

/**
 * Application event published after a successful provisioning transaction commits.
 * Used to trigger async post-provisioning tasks such as initial memory creation.
 */
public record ProvisioningCompletedEvent(
        Long fatherId,
        WizardData wizardData
) {
}
