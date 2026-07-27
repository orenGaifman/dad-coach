package com.dadcoach.ai;

/**
 * Represents a single message in a conversation history sent to the AI model.
 *
 * @param role    the role of the message sender (e.g., "system", "user", "assistant")
 * @param content the text content of the message
 */
public record AiMessage(
    String role,
    String content
) {
    public static AiMessage system(String content) {
        return new AiMessage("system", content);
    }

    public static AiMessage user(String content) {
        return new AiMessage("user", content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage("assistant", content);
    }
}
