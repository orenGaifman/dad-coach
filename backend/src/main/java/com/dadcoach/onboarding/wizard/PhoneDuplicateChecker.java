package com.dadcoach.onboarding.wizard;

import com.dadcoach.domain.father.FatherRepository;
import org.springframework.stereotype.Component;

/**
 * Checks whether a phone number is already registered in the fathers table.
 * Used during FATHER_PROFILE step validation to detect duplicate registrations
 * and redirect the user to the login flow instead.
 *
 * <p>When a duplicate is detected, a validation error with code "PHONE_REGISTERED"
 * is returned, which the controller translates to a 409 response with a login redirect.
 */
@Component
public class PhoneDuplicateChecker {

    private final FatherRepository fatherRepository;

    public PhoneDuplicateChecker(FatherRepository fatherRepository) {
        this.fatherRepository = fatherRepository;
    }

    /**
     * Checks if a phone number is already registered.
     *
     * @param phoneNumber the phone number in E.164 format to check
     * @return a {@link StepValidationResult} — failure with PHONE_REGISTERED error code
     *         if the phone is already in use, success otherwise
     */
    public StepValidationResult checkDuplicate(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return StepValidationResult.success();
        }

        boolean exists = fatherRepository.findByPhone(phoneNumber.trim()).isPresent();
        if (exists) {
            return StepValidationResult.failure(java.util.List.of(
                    new FieldError("phone_number", "PHONE_REGISTERED",
                            "This phone number is already registered. Would you like to log in instead?")
            ));
        }

        return StepValidationResult.success();
    }
}
