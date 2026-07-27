package com.dadcoach.domain.conversation;

import com.dadcoach.domain.conversation.EmojiInterpreter.Intent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmojiInterpreter.
 * Verifies emoji-to-intent mapping (Requirement 12.17).
 */
class EmojiInterpreterTest {

    private final EmojiInterpreter interpreter = new EmojiInterpreter();

    @Test
    void thumbsUp_mapsToAck() {
        assertThat(interpreter.interpret("👍")).contains(Intent.ACK);
    }

    @Test
    void crossMark_mapsToDecline() {
        assertThat(interpreter.interpret("❌")).contains(Intent.DECLINE);
    }

    @Test
    void checkMark_mapsToConfirm() {
        assertThat(interpreter.interpret("✅")).contains(Intent.CONFIRM);
    }

    @Test
    void prayerHands_mapsToThanks() {
        assertThat(interpreter.interpret("🙏")).contains(Intent.THANKS);
    }

    @Test
    void heart_mapsToAppreciation() {
        assertThat(interpreter.interpret("❤️")).contains(Intent.APPRECIATION);
    }

    @Test
    void thinkingFace_mapsToThinking() {
        assertThat(interpreter.interpret("🤔")).contains(Intent.THINKING);
    }

    @Test
    void smilingFace_mapsToPositiveSentiment() {
        assertThat(interpreter.interpret("😊")).contains(Intent.POSITIVE_SENTIMENT);
    }

    @Test
    void cryingFace_mapsToNegativeSentiment() {
        assertThat(interpreter.interpret("😢")).contains(Intent.NEGATIVE_SENTIMENT);
    }

    @Test
    void skinToneVariant_mapsCorrectly() {
        assertThat(interpreter.interpret("👍🏽")).contains(Intent.ACK);
        assertThat(interpreter.interpret("🙏🏿")).contains(Intent.THANKS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "hello", "yes 👍", "I'm fine", "123"})
    void nonEmojiMessages_returnEmpty(String message) {
        assertThat(interpreter.interpret(message)).isEmpty();
    }

    @Test
    void whitespaceAroundEmoji_isTrimmed() {
        assertThat(interpreter.interpret("  👍  ")).contains(Intent.ACK);
    }

    @Test
    void unrecognizedEmoji_returnsEmpty() {
        assertThat(interpreter.interpret("🎉")).isEmpty();
    }

    @Test
    void isEmojiOnly_trueForSingleEmoji() {
        assertThat(interpreter.isEmojiOnly("👍")).isTrue();
        assertThat(interpreter.isEmojiOnly("😊")).isTrue();
    }

    @Test
    void isEmojiOnly_falseForTextWithEmoji() {
        assertThat(interpreter.isEmojiOnly("hello 👍")).isFalse();
        assertThat(interpreter.isEmojiOnly("yes")).isFalse();
    }

    @Test
    void isEmojiOnly_falseForNullOrBlank() {
        assertThat(interpreter.isEmojiOnly(null)).isFalse();
        assertThat(interpreter.isEmojiOnly("")).isFalse();
        assertThat(interpreter.isEmojiOnly("   ")).isFalse();
    }
}
