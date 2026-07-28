package com.dadcoach.onboarding.provisioning;

import java.util.List;
import java.util.UUID;

/**
 * Immutable result record returned by the provisioning service after
 * all domain entities are created in a single atomic transaction.
 *
 * @param fatherId     the newly created (or existing) father's ID
 * @param familyId     the newly created family's ID
 * @param childIds     IDs of the children created (may be empty if father has no children)
 * @param goalIds      IDs of the goals created (1-5)
 * @param activationId the activation record ID for the WhatsApp activation flow
 * @param deepLink     the WhatsApp deep link for activation
 */
public record ProvisioningResult(
        Long fatherId,
        UUID familyId,
        List<Long> childIds,
        List<Long> goalIds,
        UUID activationId,
        String deepLink
) {
}
