package com.dadcoach.onboarding;

/**
 * Thrown when a phone number is already registered (duplicate phone detection).
 */
public class PhoneAlreadyRegisteredException extends RuntimeException {
    public PhoneAlreadyRegisteredException(String maskedPhone) {
        super("Phone number ****" + maskedPhone + " is already registered");
    }
}
