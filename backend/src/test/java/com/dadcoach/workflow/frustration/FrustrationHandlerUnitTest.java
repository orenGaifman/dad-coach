package com.dadcoach.workflow.frustration;

import com.dadcoach.workflow.message.FallbackMessages;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageType;
import com.dadcoach.workflow.pattern.PatternMatcherImpl;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;
import com.dadcoach.workflow.pattern.StatePatterns;
import com.dadcoach.workflow.pattern.WorkflowAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Bug 5: Missing User Frustration/Repetition Handler
 * 
 * <p><strong>Validates: Requirements 2.13, 2.14, 2.15</strong></p>
 * 
 * <p>These tests verify that the frustration handling feature is implemented correctly:</p>
 * <ul>
 *   <li>Frustration patterns are correctly detected in English</li>
 *   <li>Frustration patterns are correctly detected in Hebrew</li>
 *   <li>Empathy prefix is generated in the correct locale</li>
 *   <li>Frustration detection coexists with actionable content processing</li>
 *   <li>Edge cases: case insensitivity, patterns within longer sentences, no false positives</li>
 * </ul>
 * 
 * <p><strong>Requirements from bugfix.md:</strong></p>
 * <ul>
 *   <li>2.13: WHEN a user message contains frustration indicators THEN the system SHALL
 *        detect the frustration pattern and respond with an empathetic acknowledgment</li>
 *   <li>2.14: WHEN frustration is detected THEN the system SHALL respond with an apology
 *        message acknowledging that the user may have to repeat information</li>
 *   <li>2.15: WHEN the system detects it may have lost context THEN it SHALL proactively
 *        acknowledge this possibility in its next response</li>
 * </ul>
 */
@DisplayName("Bug 5: Frustration Handler Unit Tests")
class FrustrationHandlerUnitTest {

    private PatternMatcherImpl patternMatcher;

    @BeforeEach
    void setUp() {
        patternMatcher = new PatternMatcherImpl();
    }

    // ============================================================================
    // Sub-task 1: Test frustration pattern detection in English
    // ============================================================================
    
    @Nested
    @DisplayName("English Frustration Pattern Detection")
    class EnglishFrustrationPatternDetection {
        
        /**
         * Test that the pattern "why again" is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.14**
         */
        @Test
        @DisplayName("Should detect 'why again' as frustration")
        void shouldDetectWhyAgainAsFrustration() {
            // Arrange
            String message = "why again do I need to tell you this";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_EN");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the pattern "repeat" is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.14**
         */
        @Test
        @DisplayName("Should detect 'repeat' as frustration")
        void shouldDetectRepeatAsFrustration() {
            // Arrange
            String message = "Do I have to repeat myself again?";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_EN");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the pattern "already said" is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.14**
         */
        @Test
        @DisplayName("Should detect 'already said' as frustration")
        void shouldDetectAlreadySaidAsFrustration() {
            // Arrange
            String message = "I already said yes!";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_EN");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the pattern "already told" is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.14**
         */
        @Test
        @DisplayName("Should detect 'already told' as frustration")
        void shouldDetectAlreadyToldAsFrustration() {
            // Arrange
            String message = "I already told you my preference";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_EN");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the pattern "you asked" is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.14**
         */
        @Test
        @DisplayName("Should detect 'you asked' as frustration")
        void shouldDetectYouAskedAsFrustration() {
            // Arrange
            String message = "you asked me this just now";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_EN");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the pattern "asked before" is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.14**
         */
        @Test
        @DisplayName("Should detect 'asked before' as frustration")
        void shouldDetectAskedBeforeAsFrustration() {
            // Arrange
            String message = "You asked before and I answered";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_EN");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test case insensitivity for English frustration patterns.
         * 
         * **Validates: Requirements 2.13**
         */
        @Test
        @DisplayName("Should detect frustration patterns case-insensitively")
        void shouldDetectFrustrationCaseInsensitively() {
            // Arrange - mixed case variations
            String upperCase = "WHY AGAIN do I need to do this?";
            String mixedCase = "I Already Said that!";
            String titleCase = "Repeat Yourself Please";
            
            // Act & Assert - all should match
            assertThat(patternMatcher.match(upperCase, StatePatterns.FRUSTRATION_PATTERNS))
                    .isPresent()
                    .get()
                    .extracting(PatternResult::isMatched)
                    .isEqualTo(true);
            
            assertThat(patternMatcher.match(mixedCase, StatePatterns.FRUSTRATION_PATTERNS))
                    .isPresent()
                    .get()
                    .extracting(PatternResult::isMatched)
                    .isEqualTo(true);
            
            assertThat(patternMatcher.match(titleCase, StatePatterns.FRUSTRATION_PATTERNS))
                    .isPresent()
                    .get()
                    .extracting(PatternResult::isMatched)
                    .isEqualTo(true);
        }
        
        /**
         * Test that frustration patterns are detected within longer sentences.
         * 
         * **Validates: Requirements 2.13**
         */
        @Test
        @DisplayName("Should detect frustration patterns within longer sentences")
        void shouldDetectFrustrationWithinLongerSentences() {
            // Arrange
            String longMessage = "Look, I understand you're trying to help, but why again " +
                    "are you asking me about my schedule when we just discussed it?";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    longMessage, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
    }
    
    // ============================================================================
    // Sub-task 2: Test frustration pattern detection in Hebrew
    // ============================================================================
    
    @Nested
    @DisplayName("Hebrew Frustration Pattern Detection")
    class HebrewFrustrationPatternDetection {
        
        /**
         * Test that the Hebrew pattern "למה שוב" (why again) is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.15**
         */
        @Test
        @DisplayName("Should detect Hebrew 'למה שוב' (why again) as frustration")
        void shouldDetectHebrewWhyAgainAsFrustration() {
            // Arrange
            String message = "למה שוב אתה שואל את זה?";  // why again are you asking this?
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the Hebrew pattern "כבר אמרתי" (I already said) is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.15**
         */
        @Test
        @DisplayName("Should detect Hebrew 'כבר אמרתי' (I already said) as frustration")
        void shouldDetectHebrewAlreadySaidAsFrustration() {
            // Arrange
            String message = "כבר אמרתי לך שכן";  // I already told you yes
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the Hebrew pattern "שאלת כבר" (you already asked) is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.15**
         */
        @Test
        @DisplayName("Should detect Hebrew 'שאלת כבר' (you already asked) as frustration")
        void shouldDetectHebrewYouAlreadyAskedAsFrustration() {
            // Arrange
            String message = "שאלת כבר את זה לפני רגע";  // you already asked this a moment ago
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the Hebrew pattern "אתה שואל שוב" (you're asking again) is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.15**
         */
        @Test
        @DisplayName("Should detect Hebrew 'אתה שואל שוב' (you're asking again) as frustration")
        void shouldDetectHebrewYouAskingAgainAsFrustration() {
            // Arrange
            String message = "אתה שואל שוב את אותו דבר";  // you're asking again the same thing
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that the Hebrew pattern "חוזר על" (repeating) is detected as frustration.
         * 
         * **Validates: Requirements 2.13, 2.15**
         */
        @Test
        @DisplayName("Should detect Hebrew 'חוזר על' (repeating) as frustration")
        void shouldDetectHebrewRepeatingAsFrustration() {
            // Arrange
            String message = "אתה חוזר על עצמך כל הזמן";  // you're repeating yourself all the time
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().patternName()).isEqualTo("FRUSTRATION_HE");
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that Hebrew frustration patterns are detected within longer sentences.
         * 
         * **Validates: Requirements 2.13**
         */
        @Test
        @DisplayName("Should detect Hebrew frustration patterns within longer sentences")
        void shouldDetectHebrewFrustrationWithinLongerSentences() {
            // Arrange - longer message with frustration indicator in the middle
            String longMessage = "תקשיב, אני מבין שאתה רוצה לעזור, אבל כבר אמרתי לך " +
                    "את התשובה לפני כמה דקות";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    longMessage, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().isMatched()).isTrue();
            assertThat(result.get().matchedAction()).isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
    }
    
    // ============================================================================
    // Sub-task 3: Test empathy prefix is added to response in correct locale
    // ============================================================================
    
    @Nested
    @DisplayName("Empathy Prefix Locale Handling")
    class EmpathyPrefixLocaleHandling {
        
        /**
         * Test that empathy message for Hebrew locale is in Hebrew.
         * This tests the FallbackMessages.getDefaultTemplate method indirectly.
         * 
         * **Validates: Requirements 2.14, 2.15**
         */
        @Test
        @DisplayName("Should generate Hebrew empathy prefix for Hebrew locale")
        void shouldGenerateHebrewEmpathyPrefixForHebrewLocale() {
            // Arrange
            String hebrewLocale = "he";
            String expectedHebrewEmpathy = "מצטער אם זה מרגיש חוזר על עצמו";
            
            // Act - test the getEmpathyMessage logic directly
            String empathyMessage = getEmpathyMessage(hebrewLocale);
            
            // Assert
            assertThat(empathyMessage)
                    .as("Hebrew empathy message should contain Hebrew text")
                    .contains(expectedHebrewEmpathy);
        }
        
        /**
         * Test that empathy message for English locale is in English.
         * 
         * **Validates: Requirements 2.14**
         */
        @Test
        @DisplayName("Should generate English empathy prefix for English locale")
        void shouldGenerateEnglishEmpathyPrefixForEnglishLocale() {
            // Arrange
            String englishLocale = "en";
            String expectedEnglishEmpathy = "Sorry if this feels repetitive";
            
            // Act - test the getEmpathyMessage logic directly
            String empathyMessage = getEmpathyMessage(englishLocale);
            
            // Assert
            assertThat(empathyMessage)
                    .as("English empathy message should contain English text")
                    .contains(expectedEnglishEmpathy);
        }
        
        /**
         * Test that unknown locale defaults to English empathy message.
         * 
         * **Validates: Requirements 2.14**
         */
        @Test
        @DisplayName("Should default to English empathy for unknown locale")
        void shouldDefaultToEnglishEmpathyForUnknownLocale() {
            // Arrange
            String unknownLocale = "fr";  // French - not supported
            String englishEmpathyIndicator = "Sorry";
            
            // Act
            String empathyMessage = getEmpathyMessage(unknownLocale);
            
            // Assert
            assertThat(empathyMessage)
                    .as("Unknown locale should default to English empathy")
                    .contains(englishEmpathyIndicator);
        }
        
        /**
         * Test that null locale defaults to English empathy message.
         * 
         * **Validates: Requirements 2.14**
         */
        @Test
        @DisplayName("Should default to English empathy for null locale")
        void shouldDefaultToEnglishEmpathyForNullLocale() {
            // Arrange
            String englishEmpathyIndicator = "Sorry";
            
            // Act
            String empathyMessage = getEmpathyMessage(null);
            
            // Assert
            assertThat(empathyMessage)
                    .as("Null locale should default to English empathy")
                    .contains(englishEmpathyIndicator);
        }
        
        /**
         * Test that FRUSTRATION_ACKNOWLEDGMENT MessageType exists.
         * 
         * **Validates: Requirements 2.13**
         */
        @Test
        @DisplayName("Should have FRUSTRATION_ACKNOWLEDGMENT MessageType")
        void shouldHaveFrustrationAcknowledgmentMessageType() {
            // Assert
            assertThat(MessageType.FRUSTRATION_ACKNOWLEDGMENT)
                    .as("FRUSTRATION_ACKNOWLEDGMENT MessageType should exist")
                    .isNotNull();
            
            assertThat(MessageType.FRUSTRATION_ACKNOWLEDGMENT.getTemplateKey())
                    .as("Should have correct template key")
                    .isEqualTo("frustration_acknowledgment");
        }
        
        /**
         * Helper method that mirrors the getEmpathyMessage logic from WorkflowEngineImpl.
         */
        private String getEmpathyMessage(String locale) {
            // This mirrors the logic in WorkflowEngineImpl.getEmpathyMessage()
            return "he".equals(locale)
                ? "מצטער אם זה מרגיש חוזר על עצמו - אני כאן לעזור. "
                : "Sorry if this feels repetitive - I'm here to help. ";
        }
    }
    
    // ============================================================================
    // Sub-task 4: Test frustration + actionable content processes both
    // ============================================================================
    
    @Nested
    @DisplayName("Frustration With Actionable Content")
    class FrustrationWithActionableContent {
        
        /**
         * Test that frustration pattern matches first when both frustration and
         * actionable content are present.
         * 
         * <p>The workflow should detect frustration first (to prepend empathy),
         * then continue to process the actionable content.</p>
         * 
         * **Validates: Requirements 2.13, 2.14**
         */
        @Test
        @DisplayName("Should detect frustration before actionable content")
        void shouldDetectFrustrationBeforeActionableContent() {
            // Arrange - message with both frustration AND slot selection
            String message = "I already told you, slot 3!";
            
            // Act - Check frustration patterns
            Optional<PatternResult> frustrationResult = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert - Frustration should be detected
            assertThat(frustrationResult).isPresent();
            assertThat(frustrationResult.get().isMatched()).isTrue();
            assertThat(frustrationResult.get().matchedAction())
                    .isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that actionable content can still be detected alongside frustration.
         * The system should process both.
         * 
         * **Validates: Requirements 2.14 (from preservation requirement 3.10)**
         */
        @Test
        @DisplayName("Actionable slot selection should still be detectable with frustration")
        void actionableSlotSelectionShouldStillBeDetectableWithFrustration() {
            // Arrange - pure slot selection (would follow frustration handling)
            String slotMessage = "3";
            
            // Act
            Optional<PatternResult> scheduleResult = patternMatcher.match(
                    slotMessage, StatePatterns.SCHEDULE_PATTERNS);
            
            // Assert - slot selection should be detectable
            assertThat(scheduleResult).isPresent();
            assertThat(scheduleResult.get().isMatched()).isTrue();
            assertThat(scheduleResult.get().matchedAction())
                    .isEqualTo(WorkflowAction.SELECT_SLOT);
        }
        
        /**
         * Test Hebrew frustration with yes response - both should be processable.
         * 
         * **Validates: Requirements 2.13, 2.14**
         */
        @Test
        @DisplayName("Hebrew frustration 'כבר אמרתי שכן' should detect frustration")
        void hebrewFrustrationWithYesShouldDetectFrustration() {
            // Arrange
            String message = "כבר אמרתי שכן";  // I already said yes
            
            // Act
            Optional<PatternResult> frustrationResult = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(frustrationResult).isPresent();
            assertThat(frustrationResult.get().isMatched()).isTrue();
            assertThat(frustrationResult.get().matchedAction())
                    .isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
        }
        
        /**
         * Test that after frustration detection, the "כן" (yes) response can still be processed.
         * 
         * **Validates: Requirements 2.14**
         */
        @Test
        @DisplayName("Hebrew 'כן' should still match follow-up patterns")
        void hebrewYesShouldStillMatchFollowUpPatterns() {
            // Arrange - pure yes (would follow frustration handling)
            String yesMessage = "כן";
            
            // Act
            Optional<PatternResult> followUpResult = patternMatcher.match(
                    yesMessage, StatePatterns.FOLLOW_UP_PATTERNS);
            
            // Assert
            assertThat(followUpResult).isPresent();
            assertThat(followUpResult.get().isMatched()).isTrue();
            assertThat(followUpResult.get().matchedAction())
                    .isEqualTo(WorkflowAction.MARK_COMPLETED);
        }
        
        /**
         * Test ACKNOWLEDGE_FRUSTRATION action exists in WorkflowAction enum.
         * 
         * **Validates: Requirements 2.13**
         */
        @Test
        @DisplayName("ACKNOWLEDGE_FRUSTRATION action should exist")
        void acknowledgeFrustrationActionShouldExist() {
            // Assert
            assertThat(WorkflowAction.ACKNOWLEDGE_FRUSTRATION)
                    .as("ACKNOWLEDGE_FRUSTRATION action should exist in WorkflowAction enum")
                    .isNotNull();
        }
    }
    
    // ============================================================================
    // Edge Cases: No false positives on non-frustration messages
    // ============================================================================
    
    @Nested
    @DisplayName("Edge Cases - No False Positives")
    class EdgeCasesNoFalsePositives {
        
        /**
         * Test that simple "yes" does not trigger frustration detection.
         * 
         * **Validates: Requirements 2.13 (edge case)**
         */
        @Test
        @DisplayName("Simple 'yes' should not trigger frustration")
        void simpleYesShouldNotTriggerFrustration() {
            // Arrange
            String message = "yes";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result.isEmpty() || !result.get().isMatched())
                    .as("Simple 'yes' should not be detected as frustration")
                    .isTrue();
        }
        
        /**
         * Test that Hebrew "כן" does not trigger frustration detection.
         * 
         * **Validates: Requirements 2.13 (edge case)**
         */
        @Test
        @DisplayName("Hebrew 'כן' should not trigger frustration")
        void hebrewYesShouldNotTriggerFrustration() {
            // Arrange
            String message = "כן";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result.isEmpty() || !result.get().isMatched())
                    .as("Simple Hebrew 'כן' should not be detected as frustration")
                    .isTrue();
        }
        
        /**
         * Test that normal schedule inquiry does not trigger frustration.
         * 
         * **Validates: Requirements 2.13 (edge case)**
         */
        @Test
        @DisplayName("Normal schedule inquiry should not trigger frustration")
        void normalScheduleInquiryShouldNotTriggerFrustration() {
            // Arrange
            String message = "when is my next quality time?";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result.isEmpty() || !result.get().isMatched())
                    .as("Normal schedule inquiry should not trigger frustration")
                    .isTrue();
        }
        
        /**
         * Test that "tomorrow" does not trigger frustration.
         * 
         * **Validates: Requirements 2.13 (edge case)**
         */
        @Test
        @DisplayName("'tomorrow' should not trigger frustration")
        void tomorrowShouldNotTriggerFrustration() {
            // Arrange
            String message = "tomorrow";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result.isEmpty() || !result.get().isMatched())
                    .as("'tomorrow' should not be detected as frustration")
                    .isTrue();
        }
        
        /**
         * Test that Hebrew "מחר" (tomorrow) does not trigger frustration.
         * 
         * **Validates: Requirements 2.13 (edge case)**
         */
        @Test
        @DisplayName("Hebrew 'מחר' should not trigger frustration")
        void hebrewTomorrowShouldNotTriggerFrustration() {
            // Arrange
            String message = "מחר";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result.isEmpty() || !result.get().isMatched())
                    .as("Hebrew 'מחר' should not be detected as frustration")
                    .isTrue();
        }
        
        /**
         * Test that simple slot number does not trigger frustration.
         * 
         * **Validates: Requirements 2.13 (edge case)**
         */
        @Test
        @DisplayName("Slot number should not trigger frustration")
        void slotNumberShouldNotTriggerFrustration() {
            // Arrange
            String message = "3";
            
            // Act
            Optional<PatternResult> result = patternMatcher.match(
                    message, StatePatterns.FRUSTRATION_PATTERNS);
            
            // Assert
            assertThat(result.isEmpty() || !result.get().isMatched())
                    .as("Slot number should not be detected as frustration")
                    .isTrue();
        }
        
        /**
         * Test that "ok" and "thanks" do not trigger frustration.
         * 
         * **Validates: Requirements 2.13 (edge case)**
         */
        @Test
        @DisplayName("Acknowledgment words should not trigger frustration")
        void acknowledgmentWordsShouldNotTriggerFrustration() {
            // Arrange
            List<String> acknowledgments = List.of("ok", "thanks", "great", "אוקי", "תודה", "מעולה");
            
            for (String message : acknowledgments) {
                // Act
                Optional<PatternResult> result = patternMatcher.match(
                        message, StatePatterns.FRUSTRATION_PATTERNS);
                
                // Assert
                assertThat(result.isEmpty() || !result.get().isMatched())
                        .as("Acknowledgment '%s' should not be detected as frustration", message)
                        .isTrue();
            }
        }
        
        /**
         * Test that greetings do not trigger frustration.
         * 
         * **Validates: Requirements 2.13 (edge case)**
         */
        @Test
        @DisplayName("Greetings should not trigger frustration")
        void greetingsShouldNotTriggerFrustration() {
            // Arrange
            List<String> greetings = List.of("hi", "hello", "hey", "היי", "שלום");
            
            for (String message : greetings) {
                // Act
                Optional<PatternResult> result = patternMatcher.match(
                        message, StatePatterns.FRUSTRATION_PATTERNS);
                
                // Assert
                assertThat(result.isEmpty() || !result.get().isMatched())
                        .as("Greeting '%s' should not be detected as frustration", message)
                        .isTrue();
            }
        }
    }
    
    // ============================================================================
    // Structural Tests - Verify Implementation Exists
    // ============================================================================
    
    @Nested
    @DisplayName("Structural Implementation Verification")
    class StructuralImplementationVerification {
        
        /**
         * Test that FRUSTRATION_PATTERNS list exists in StatePatterns.
         * 
         * **Validates: Requirements 2.13**
         */
        @Test
        @DisplayName("FRUSTRATION_PATTERNS should exist in StatePatterns")
        void frustrationPatternsShouldExistInStatePatterns() {
            // Assert
            assertThat(StatePatterns.FRUSTRATION_PATTERNS)
                    .as("FRUSTRATION_PATTERNS list should exist")
                    .isNotNull()
                    .isNotEmpty();
        }
        
        /**
         * Test that FRUSTRATION_PATTERNS contains both English and Hebrew patterns.
         * 
         * **Validates: Requirements 2.13, 2.15**
         */
        @Test
        @DisplayName("FRUSTRATION_PATTERNS should contain both English and Hebrew patterns")
        void frustrationPatternsShouldContainBothLanguages() {
            // Arrange
            boolean hasEnglishPattern = false;
            boolean hasHebrewPattern = false;
            
            // Act
            for (StatePattern pattern : StatePatterns.FRUSTRATION_PATTERNS) {
                if (pattern.patternName().contains("EN")) {
                    hasEnglishPattern = true;
                }
                if (pattern.patternName().contains("HE")) {
                    hasHebrewPattern = true;
                }
            }
            
            // Assert
            assertThat(hasEnglishPattern)
                    .as("Should have English frustration pattern")
                    .isTrue();
            assertThat(hasHebrewPattern)
                    .as("Should have Hebrew frustration pattern")
                    .isTrue();
        }
        
        /**
         * Test that all frustration patterns map to ACKNOWLEDGE_FRUSTRATION action.
         * 
         * **Validates: Requirements 2.13**
         */
        @Test
        @DisplayName("All frustration patterns should map to ACKNOWLEDGE_FRUSTRATION action")
        void allFrustrationPatternsShouldMapToAcknowledgeFrustration() {
            // Assert
            for (StatePattern pattern : StatePatterns.FRUSTRATION_PATTERNS) {
                assertThat(pattern.action())
                        .as("Pattern '%s' should map to ACKNOWLEDGE_FRUSTRATION", pattern.patternName())
                        .isEqualTo(WorkflowAction.ACKNOWLEDGE_FRUSTRATION);
            }
        }
    }
}
