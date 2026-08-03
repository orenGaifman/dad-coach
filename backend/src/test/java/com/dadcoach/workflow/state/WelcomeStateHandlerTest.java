package com.dadcoach.workflow.state;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.message.MessageType;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;
import com.dadcoach.workflow.pattern.StatePatterns;
import com.dadcoach.workflow.pattern.WorkflowAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WelcomeStateHandler.
 * 
 * <p>Validates Requirements 4.1, 4.2, 4.3, 4.4, 4.5 from the deterministic-workflow-engine spec:</p>
 * <ul>
 *   <li>4.1: Send exactly one welcome message (handled by entry action)</li>
 *   <li>4.2: Accept AFFIRMATIVE and MORE_INFO patterns in English and Hebrew</li>
 *   <li>4.3: Send clarification with explicit options for unmatched messages</li>
 *   <li>4.4: No AI decision-making - pure pattern matching</li>
 *   <li>4.5: Set welcomed_at timestamp when transitioning out of WELCOME</li>
 * </ul>
 * 
 * <p>The WELCOME state is the initial state for new fathers arriving from WEB-SPEC-007 (Onboarding).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WelcomeStateHandler")
class WelcomeStateHandlerTest {
    
    @Mock
    private MessageGenerator messageGenerator;
    
    @Mock
    private FatherRepository fatherRepository;
    
    private WelcomeStateHandler handler;
    
    private static final UUID FATHER_UUID = UUID.randomUUID();
    private static final Long FATHER_ID = FATHER_UUID.getLeastSignificantBits();
    
    @BeforeEach
    void setUp() {
        handler = new WelcomeStateHandler(messageGenerator, fatherRepository);
    }
    
    @Nested
    @DisplayName("getState()")
    class GetStateTests {
        
        @Test
        @DisplayName("should return WELCOME state")
        void shouldReturnWelcomeState() {
            assertThat(handler.getState()).isEqualTo(WorkflowState.WELCOME);
        }
    }
    
    @Nested
    @DisplayName("getExpectedPatterns()")
    class GetExpectedPatternsTests {
        
        @Test
        @DisplayName("should return WELCOME_PATTERNS from StatePatterns")
        void shouldReturnWelcomePatterns() {
            List<StatePattern> patterns = handler.getExpectedPatterns();
            
            assertThat(patterns).isEqualTo(StatePatterns.WELCOME_PATTERNS);
        }
        
        @Test
        @DisplayName("patterns should include AFFIRMATIVE and MORE_INFO actions")
        void patternsShouldIncludeExpectedActions() {
            List<StatePattern> patterns = handler.getExpectedPatterns();
            
            assertThat(patterns).extracting(StatePattern::action)
                    .contains(
                            WorkflowAction.TRANSITION_TO_SCHEDULE,
                            WorkflowAction.EXPLAIN_AND_REPROMPT
                    );
        }
        
        @Test
        @DisplayName("patterns should contain both English and Hebrew variants")
        void patternsShouldContainBothLanguages() {
            List<StatePattern> patterns = handler.getExpectedPatterns();
            
            List<String> patternNames = patterns.stream()
                    .map(StatePattern::patternName)
                    .toList();
            
            assertThat(patternNames)
                    .contains("AFFIRMATIVE_EN", "AFFIRMATIVE_HE", "MORE_INFO_EN", "MORE_INFO_HE");
        }
    }
    
    @Nested
    @DisplayName("handle() - AFFIRMATIVE (TRANSITION_TO_SCHEDULE)")
    class HandleAffirmativeTests {
        
        @Test
        @DisplayName("should transition to SCHEDULE_QUALITY_TIME on AFFIRMATIVE response")
        void shouldTransitionToScheduleQualityTime() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "yes");
            PatternResult match = PatternResult.of("AFFIRMATIVE_EN", WorkflowAction.TRANSITION_TO_SCHEDULE);
            
            Father father = createTestFather("en");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(fatherRepository.save(any(Father.class))).thenAnswer(inv -> inv.getArgument(0));
            
            when(messageGenerator.generateWithFallback(eq(MessageType.SCHEDULE_SLOTS), any(), anyLong()))
                    .thenReturn("Great! Let's schedule your Quality Time.");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.SCHEDULE_QUALITY_TIME);
            assertThat(action.hasResponse()).isTrue();
        }
        
        @Test
        @DisplayName("should set welcomed_at timestamp when transitioning (Requirement 4.5)")
        void shouldSetWelcomedAtTimestamp() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "ready");
            PatternResult match = PatternResult.of("AFFIRMATIVE_EN", WorkflowAction.TRANSITION_TO_SCHEDULE);
            
            Father father = createTestFather("en");
            assertThat(father.getWelcomedAt()).isNull(); // Initially null
            
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(fatherRepository.save(any(Father.class))).thenAnswer(inv -> inv.getArgument(0));
            when(messageGenerator.generateWithFallback(any(), any(), anyLong())).thenReturn("Message");
            
            Instant beforeHandle = Instant.now();
            
            // Act
            handler.handle(context, match);
            
            Instant afterHandle = Instant.now();
            
            // Assert
            ArgumentCaptor<Father> fatherCaptor = ArgumentCaptor.forClass(Father.class);
            verify(fatherRepository).save(fatherCaptor.capture());
            
            Father savedFather = fatherCaptor.getValue();
            assertThat(savedFather.getWelcomedAt()).isNotNull();
            assertThat(savedFather.getWelcomedAt())
                    .isAfterOrEqualTo(beforeHandle)
                    .isBeforeOrEqualTo(afterHandle);
        }
        
        @Test
        @DisplayName("should persist welcomed_at to database")
        void shouldPersistWelcomedAt() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "יאללה");
            PatternResult match = PatternResult.of("AFFIRMATIVE_HE", WorkflowAction.TRANSITION_TO_SCHEDULE);
            
            Father father = createTestFather("he");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(fatherRepository.save(any(Father.class))).thenAnswer(inv -> inv.getArgument(0));
            when(messageGenerator.generateWithFallback(any(), any(), anyLong())).thenReturn("Message");
            
            // Act
            handler.handle(context, match);
            
            // Assert
            verify(fatherRepository).save(any(Father.class));
        }
        
        @ParameterizedTest
        @CsvSource({
                "yes, en, AFFIRMATIVE_EN",
                "ready, en, AFFIRMATIVE_EN",
                "let's go, en, AFFIRMATIVE_EN",
                "כן, he, AFFIRMATIVE_HE",
                "מוכן, he, AFFIRMATIVE_HE",
                "יאללה, he, AFFIRMATIVE_HE"
        })
        @DisplayName("should transition for various affirmative patterns")
        void shouldTransitionForVariousAffirmativePatterns(String message, String locale, String patternName) {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, message);
            PatternResult match = PatternResult.of(patternName, WorkflowAction.TRANSITION_TO_SCHEDULE);
            
            Father father = createTestFather(locale);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(fatherRepository.save(any(Father.class))).thenAnswer(inv -> inv.getArgument(0));
            when(messageGenerator.generateWithFallback(any(), any(), anyLong())).thenReturn("Message");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.SCHEDULE_QUALITY_TIME);
        }
    }
    
    @Nested
    @DisplayName("handle() - MORE_INFO (EXPLAIN_AND_REPROMPT)")
    class HandleMoreInfoTests {
        
        @Test
        @DisplayName("should respond with explanation and stay in WELCOME state on MORE_INFO")
        void shouldRespondAndStayInWelcomeState() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "tell me more");
            PatternResult match = PatternResult.of("MORE_INFO_EN", WorkflowAction.EXPLAIN_AND_REPROMPT);
            
            Father father = createTestFather("en");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            
            when(messageGenerator.generateWithFallback(eq(MessageType.WELCOME_EXPLAIN), any(), anyLong()))
                    .thenReturn("Dad Coach helps you build stronger bonds...");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.isTransition()).isFalse();
            assertThat(action.hasResponse()).isTrue();
        }
        
        @Test
        @DisplayName("should NOT set welcomed_at timestamp on MORE_INFO response")
        void shouldNotSetWelcomedAtTimestamp() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "explain");
            PatternResult match = PatternResult.of("MORE_INFO_EN", WorkflowAction.EXPLAIN_AND_REPROMPT);
            
            Father father = createTestFather("en");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(messageGenerator.generateWithFallback(any(), any(), anyLong())).thenReturn("Message");
            
            // Act
            handler.handle(context, match);
            
            // Assert - fatherRepository.save() should NOT be called for MORE_INFO
            verify(fatherRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should generate WELCOME_EXPLAIN message type")
        void shouldGenerateWelcomeExplainMessage() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "מה זה");
            PatternResult match = PatternResult.of("MORE_INFO_HE", WorkflowAction.EXPLAIN_AND_REPROMPT);
            
            Father father = createTestFather("he");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(messageGenerator.generateWithFallback(eq(MessageType.WELCOME_EXPLAIN), any(), anyLong()))
                    .thenReturn("דד קואץ' עוזר לך לבנות קשר חזק יותר...");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            verify(messageGenerator).generateWithFallback(eq(MessageType.WELCOME_EXPLAIN), any(), anyLong());
            assertThat(action.hasResponse()).isTrue();
        }
        
        @ParameterizedTest
        @CsvSource({
                "how, en, MORE_INFO_EN",
                "what is, en, MORE_INFO_EN",
                "explain, en, MORE_INFO_EN",
                "tell me more, en, MORE_INFO_EN",
                "איך, he, MORE_INFO_HE",
                "מה זה, he, MORE_INFO_HE",
                "הסבר, he, MORE_INFO_HE",
                "ספר לי עוד, he, MORE_INFO_HE"
        })
        @DisplayName("should respond for various MORE_INFO patterns")
        void shouldRespondForVariousMoreInfoPatterns(String message, String locale, String patternName) {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, message);
            PatternResult match = PatternResult.of(patternName, WorkflowAction.EXPLAIN_AND_REPROMPT);
            
            Father father = createTestFather(locale);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(messageGenerator.generateWithFallback(any(), any(), anyLong())).thenReturn("Explanation message");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.isTransition()).isFalse();
        }
    }
    
    @Nested
    @DisplayName("handleUnmatched()")
    class HandleUnmatchedTests {
        
        @Test
        @DisplayName("should return CLARIFY action for unmatched messages")
        void shouldReturnClarifyAction() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "random gibberish");
            
            Father father = createTestFather("en");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            
            when(messageGenerator.generateWithFallback(eq(MessageType.CLARIFICATION), any(), anyLong()))
                    .thenReturn("I didn't understand. Please say: Ready to schedule or Tell me more");
            
            // Act
            StateAction action = handler.handleUnmatched(context);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.CLARIFY);
            assertThat(action.isTransition()).isFalse();
            assertThat(action.hasResponse()).isTrue();
        }
        
        @Test
        @DisplayName("should provide English clarification options for English locale")
        void shouldProvideClarificationOptionsInEnglish() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "xyz");
            
            Father father = createTestFather("en");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                    .thenReturn("Clarification message");
            
            // Act
            handler.handleUnmatched(context);
            
            // Assert - verify the message context includes English options
            verify(messageGenerator).generateWithFallback(
                    eq(MessageType.CLARIFICATION),
                    argThat(ctx -> {
                        List<String> options = ctx.getValidOptions();
                        return options != null && 
                               options.contains("Ready to schedule") && 
                               options.contains("Tell me more");
                    }),
                    anyLong()
            );
        }
        
        @Test
        @DisplayName("should provide Hebrew clarification options for Hebrew locale")
        void shouldProvideClarificationOptionsInHebrew() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "xyz");
            
            Father father = createTestFather("he");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                    .thenReturn("Clarification message in Hebrew");
            
            // Act
            handler.handleUnmatched(context);
            
            // Assert - verify the message context includes Hebrew options
            verify(messageGenerator).generateWithFallback(
                    eq(MessageType.CLARIFICATION),
                    argThat(ctx -> {
                        List<String> options = ctx.getValidOptions();
                        return options != null && 
                               options.contains("מוכן לתאם") && 
                               options.contains("ספר לי עוד");
                    }),
                    anyLong()
            );
        }
        
        @Test
        @DisplayName("should default to English options when locale is null")
        void shouldDefaultToEnglishOptionsWhenLocaleNull() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "xyz");
            
            Father father = createTestFatherWithNullLocale();
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                    .thenReturn("Clarification message");
            
            // Act
            handler.handleUnmatched(context);
            
            // Assert - should use English options
            verify(messageGenerator).generateWithFallback(
                    eq(MessageType.CLARIFICATION),
                    argThat(ctx -> {
                        List<String> options = ctx.getValidOptions();
                        return options != null && 
                               options.contains("Ready to schedule");
                    }),
                    anyLong()
            );
        }
        
        @Test
        @DisplayName("should throw when context is null")
        void shouldThrowWhenContextIsNull() {
            assertThatThrownBy(() -> handler.handleUnmatched(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("context must not be null");
        }
        
        @Test
        @DisplayName("should throw when father is not found")
        void shouldThrowWhenFatherNotFound() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "hello");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.empty());
            
            // Act & Assert
            assertThatThrownBy(() -> handler.handleUnmatched(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Father not found");
        }
    }
    
    @Nested
    @DisplayName("handle() - edge cases")
    class HandleEdgeCasesTests {
        
        @Test
        @DisplayName("should call handleUnmatched when PatternResult is not matched")
        void shouldCallHandleUnmatchedWhenPatternNotMatched() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "xyz");
            PatternResult noMatch = PatternResult.noMatch();
            
            Father father = createTestFather("en");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(messageGenerator.generateWithFallback(eq(MessageType.CLARIFICATION), any(), anyLong()))
                    .thenReturn("Clarification message");
            
            // Act
            StateAction action = handler.handle(context, noMatch);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.CLARIFY);
        }
        
        @Test
        @DisplayName("should handle unexpected action by calling handleUnmatched")
        void shouldHandleUnexpectedAction() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "test");
            // Create a match with an unexpected action (e.g., one that isn't handled in WELCOME state)
            PatternResult match = PatternResult.of("UNEXPECTED", WorkflowAction.SELECT_SLOT);
            
            Father father = createTestFather("en");
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(messageGenerator.generateWithFallback(eq(MessageType.CLARIFICATION), any(), anyLong()))
                    .thenReturn("Clarification message");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.CLARIFY);
        }
        
        @Test
        @DisplayName("should throw when context is null")
        void shouldThrowWhenContextIsNull() {
            PatternResult match = PatternResult.of("TEST", WorkflowAction.TRANSITION_TO_SCHEDULE);
            
            assertThatThrownBy(() -> handler.handle(null, match))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("context must not be null");
        }
        
        @Test
        @DisplayName("should throw when match is null")
        void shouldThrowWhenMatchIsNull() {
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WELCOME, "test");
            
            assertThatThrownBy(() -> handler.handle(context, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("match must not be null");
        }
    }
    
    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidationTests {
        
        @Test
        @DisplayName("should throw when messageGenerator is null")
        void shouldThrowWhenMessageGeneratorIsNull() {
            assertThatThrownBy(() -> new WelcomeStateHandler(null, fatherRepository))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("messageGenerator must not be null");
        }
        
        @Test
        @DisplayName("should throw when fatherRepository is null")
        void shouldThrowWhenFatherRepositoryIsNull() {
            assertThatThrownBy(() -> new WelcomeStateHandler(messageGenerator, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("fatherRepository must not be null");
        }
    }
    
    // ─── Helper Methods ──────────────────────────────────────────────────────
    
    private Father createTestFather(String locale) {
        Father father = new Father("1234567890");
        father.setId(FATHER_ID);
        father.setDisplayName("David");
        father.setLocale(locale);
        father.setTimezone("Asia/Jerusalem");
        father.setWelcomedAt(null); // Not welcomed yet
        return father;
    }
    
    private Father createTestFatherWithNullLocale() {
        Father father = new Father("1234567890");
        father.setId(FATHER_ID);
        father.setDisplayName("David");
        father.setLocale(null);
        father.setTimezone("Asia/Jerusalem");
        father.setWelcomedAt(null);
        return father;
    }
}
