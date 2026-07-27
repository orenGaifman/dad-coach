package com.dadcoach.ai;

/**
 * Thrown when the AI provider is unavailable after all retry attempts have been exhausted.
 */
public class AiProviderUnavailableException extends RuntimeException {

    private final int attemptsExhausted;

    public AiProviderUnavailableException(String message, int attemptsExhausted) {
        super(message);
        this.attemptsExhausted = attemptsExhausted;
    }

    public AiProviderUnavailableException(String message, int attemptsExhausted, Throwable cause) {
        super(message, cause);
        this.attemptsExhausted = attemptsExhausted;
    }

    public int getAttemptsExhausted() {
        return attemptsExhausted;
    }
}
