package com.dadcoach.domain.conversation;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Interprets single-emoji messages and maps them to semantic intents.
 *
 * <p>Business rule (Requirement 12.17): When a Father's inbound message contains only
 * emojis or media without text, the system interprets common patterns and responds
 * contextually.</p>
 *
 * <p>Supported emoji-to-intent mappings:</p>
 * <ul>
 *   <li>👍 → ACK (acknowledge)</li>
 *   <li>❌ → DECLINE</li>
 *   <li>✅ → CONFIRM</li>
 *   <li>🙏 → THANKS</li>
 *   <li>❤️ → APPRECIATION</li>
 *   <li>🤔 → THINKING / NEED_TIME</li>
 *   <li>😊 → POSITIVE_SENTIMENT</li>
 *   <li>😢 → NEGATIVE_SENTIMENT</li>
 * </ul>
 */
@Component
public class EmojiInterpreter {

    /**
     * Semantic intents that emoji messages can map to.
     */
    public enum Intent {
        ACK,
        DECLINE,
        CONFIRM,
        THANKS,
        APPRECIATION,
        THINKING,
        POSITIVE_SENTIMENT,
        NEGATIVE_SENTIMENT
    }

    private static final Map<String, Intent> EMOJI_TO_INTENT = Map.ofEntries(
            Map.entry("👍", Intent.ACK),
            Map.entry("👍🏻", Intent.ACK),
            Map.entry("👍🏼", Intent.ACK),
            Map.entry("👍🏽", Intent.ACK),
            Map.entry("👍🏾", Intent.ACK),
            Map.entry("👍🏿", Intent.ACK),
            Map.entry("❌", Intent.DECLINE),
            Map.entry("✅", Intent.CONFIRM),
            Map.entry("🙏", Intent.THANKS),
            Map.entry("🙏🏻", Intent.THANKS),
            Map.entry("🙏🏼", Intent.THANKS),
            Map.entry("🙏🏽", Intent.THANKS),
            Map.entry("🙏🏾", Intent.THANKS),
            Map.entry("🙏🏿", Intent.THANKS),
            Map.entry("❤️", Intent.APPRECIATION),
            Map.entry("❤", Intent.APPRECIATION),
            Map.entry("🤔", Intent.THINKING),
            Map.entry("😊", Intent.POSITIVE_SENTIMENT),
            Map.entry("😢", Intent.NEGATIVE_SENTIMENT)
    );

    /**
     * Interprets a message that may contain only emoji(s).
     *
     * <p>Returns the intent if the trimmed message is a single recognized emoji.
     * Returns empty if the message contains non-emoji text or is not recognized.</p>
     *
     * @param message the raw inbound message
     * @return the interpreted intent, or empty if not a recognized emoji-only message
     */
    public Optional<Intent> interpret(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String trimmed = message.trim();

        // Direct lookup
        Intent intent = EMOJI_TO_INTENT.get(trimmed);
        if (intent != null) {
            return Optional.of(intent);
        }

        return Optional.empty();
    }

    /**
     * Checks if a message is an emoji-only message (contains only emoji characters
     * with no alphanumeric text).
     *
     * @param message the message to check
     * @return true if the message contains only emoji/whitespace characters
     */
    public boolean isEmojiOnly(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String trimmed = message.trim();

        // Check each code point — emoji code points are generally in specific ranges
        return trimmed.codePoints().allMatch(this::isEmojiCodePoint);
    }

    /**
     * Checks if a Unicode code point is an emoji-related character.
     * Includes variation selectors and skin tone modifiers.
     */
    private boolean isEmojiCodePoint(int codePoint) {
        // Common emoji ranges
        return (codePoint >= 0x1F600 && codePoint <= 0x1F64F)  // Emoticons
                || (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) // Misc Symbols & Pictographs
                || (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) // Transport & Map
                || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) // Supplemental Symbols
                || (codePoint >= 0x1FA00 && codePoint <= 0x1FA6F) // Chess Symbols
                || (codePoint >= 0x1FA70 && codePoint <= 0x1FAFF) // Symbols Extended-A
                || (codePoint >= 0x2600 && codePoint <= 0x26FF)   // Misc Symbols
                || (codePoint >= 0x2700 && codePoint <= 0x27BF)   // Dingbats
                || (codePoint >= 0x2300 && codePoint <= 0x23FF)   // Misc Technical
                || (codePoint >= 0x2B50 && codePoint <= 0x2B55)   // Stars
                || (codePoint == 0xFE0F)                           // Variation Selector-16
                || (codePoint == 0xFE0E)                           // Variation Selector-15
                || (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF) // Skin tone modifiers
                || (codePoint == 0x200D)                           // Zero-width joiner
                || (codePoint == 0x20E3)                           // Combining enclosing keycap
                || (codePoint >= 0xE0020 && codePoint <= 0xE007F); // Tags
    }
}
