package com.dadcoach.onboarding.provisioning;

import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service that creates all domain entities from completed wizard data in a single transaction.
 * Idempotent: returns existing result if phone_number already provisioned.
 */
public interface ProvisioningService {

    /**
     * Creates all domain entities from completed wizard data in a single transaction.
     * Idempotent: returns existing result if phone_number already provisioned.
     *
     * @param sessionId the onboarding session ID with completed wizard data
     * @return the provisioning result containing all created entity IDs and the deep link
     */
    @Transactional
    ProvisioningResult provision(UUID sessionId);
}
