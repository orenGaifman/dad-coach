package com.dadcoach.workflow.state;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageContext.ActivityIdea;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
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
 * Unit tests for ActivityIdeasStateHandler.
 * 
 * Validates Requirements 9.1, 9.2, 9.3, 9.4, 9.6 from the deterministic-workflow-engine spec:
 * - 9.1: ACTIVITY_IDEAS only entered when father explicitly requests ideas
 * - 9.2: On entry, reads child info and generates 3 personalized ideas via AI
 * - 9.3: Ideas formatted as numbered list with title, description, duration
 * - 9.4: Father can select idea by number, request more, or exit
 * - 9.6: After exiting, returns to previous_workflow_state
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityIdeasStateHandler")
class ActivityIdeasStateHandlerTest {
    
    @Mock
    private MessageGenerator messageGenerator;
    
    @Mock
    private SystemStateLoader systemStateLoader;
    
    @Mock
    private FatherRepository fatherRepository;
    
    private ActivityIdeasStateHandler handler;
    
    private static final UUID FATHER_UUID = UUID.randomUUID();
    private static final Long FATHER_ID = FATHER_UUID.getLeastSignificantBits();
    private static final Long CHILD_ID = 10L;
    
    @BeforeEach
    void setUp() {
        handler = new ActivityIdeasStateHandler(
                messageGenerator,
                systemStateLoader,
                fatherRepository);
    }
    
    @Nested
    @DisplayName("getState()")
    class GetStateTests {
        
        @Test
        @DisplayName("should return ACTIVITY_IDEAS state")
        void shouldReturnActivityIdeasState() {
            assertThat(handler.getState()).isEqualTo(WorkflowState.ACTIVITY_IDEAS);
        }
    }
    
    @Nested
    @DisplayName("getExpectedPatterns()")
    class GetExpectedPatternsTests {
        
        @Test
        @DisplayName("should return ACTIVITY_IDEAS_PATTERNS from StatePatterns")
        void shouldReturnActivityIdeasPatterns() {
            List<StatePattern> patterns = handler.getExpectedPatterns();
            
            assertThat(patterns).isEqualTo(StatePatterns.ACTIVITY_IDEAS_PATTERNS);
        }
        
        @Test
        @DisplayName("patterns should include SHOW_IDEA_DETAILS, GENERATE_MORE_IDEAS, and RETURN_TO_PREVIOUS")
        void patternsShouldIncludeExpectedActions() {
            List<StatePattern> patterns = handler.getExpectedPatterns();
            
            assertThat(patterns).extracting(StatePattern::action)
                    .contains(
                            WorkflowAction.SHOW_IDEA_DETAILS,
                            WorkflowAction.GENERATE_MORE_IDEAS,
                            WorkflowAction.RETURN_TO_PREVIOUS
                    );
        }
    }
    
    @Nested
    @DisplayName("onEntry()")
    class OnEntryTests {
        
        @Test
        @DisplayName("should generate 3 activity ideas on entry - Requirement 9.2")
        void shouldGenerateThreeIdeasOnEntry() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Here are 3 activity ideas:\n1. Building Blocks\n2. Outdoor Play\n3. Story Time");
            
            // Act
            StateAction action = handler.onEntry(FATHER_UUID);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
            
            // Verify message generator was called with activity ideas
            ArgumentCaptor<MessageContext> contextCaptor = ArgumentCaptor.forClass(MessageContext.class);
            verify(messageGenerator).generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), contextCaptor.capture(), anyLong());
            
            MessageContext capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getActivityIdeas()).hasSize(3);
        }
        
        @Test
        @DisplayName("should include child name and age in message context")
        void shouldIncludeChildInfoInContext() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Activity ideas for Maya!");
            
            // Act
            handler.onEntry(FATHER_UUID);
            
            // Assert
            ArgumentCaptor<MessageContext> contextCaptor = ArgumentCaptor.forClass(MessageContext.class);
            verify(messageGenerator).generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), contextCaptor.capture(), anyLong());
            
            MessageContext capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getChildName()).isEqualTo("Maya");
            assertThat(capturedContext.getChildAge()).isEqualTo(5);
        }
        
        @Test
        @DisplayName("should generate Hebrew ideas when locale is Hebrew")
        void shouldGenerateHebrewIdeasWhenLocaleIsHebrew() {
            // Arrange
            SystemState systemState = createMockSystemState("he");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("הנה 3 רעיונות לפעילויות!");
            
            // Act
            handler.onEntry(FATHER_UUID);
            
            // Assert
            ArgumentCaptor<MessageContext> contextCaptor = ArgumentCaptor.forClass(MessageContext.class);
            verify(messageGenerator).generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), contextCaptor.capture(), anyLong());
            
            MessageContext capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getLocale()).isEqualTo("he");
        }
    }
    
    @Nested
    @DisplayName("handle() - SHOW_IDEA_DETAILS")
    class HandleShowIdeaDetailsTests {
        
        @Test
        @DisplayName("should show details for idea 1 when father types '1'")
        void shouldShowDetailsForIdea1() {
            // Arrange - first generate ideas
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Ideas");
            handler.onEntry(FATHER_UUID);
            
            // Now handle selection of idea 1
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "1");
            PatternResult match = PatternResult.of("IDEA_NUMBER", WorkflowAction.SHOW_IDEA_DETAILS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
            assertThat(action.isTransition()).isFalse();
            
            // The response should contain the idea title
            String response = action.getResponseMessage().orElse("");
            assertThat(response).contains("Idea 1");
        }
        
        @Test
        @DisplayName("should show details for idea 2 when father types '2'")
        void shouldShowDetailsForIdea2() {
            // Arrange - first generate ideas
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Ideas");
            handler.onEntry(FATHER_UUID);
            
            // Now handle selection of idea 2
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "2");
            PatternResult match = PatternResult.of("IDEA_NUMBER", WorkflowAction.SHOW_IDEA_DETAILS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
            
            String response = action.getResponseMessage().orElse("");
            assertThat(response).contains("Idea 2");
        }
        
        @Test
        @DisplayName("should show details for idea 3 when father types '3'")
        void shouldShowDetailsForIdea3() {
            // Arrange - first generate ideas
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Ideas");
            handler.onEntry(FATHER_UUID);
            
            // Now handle selection of idea 3
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "3");
            PatternResult match = PatternResult.of("IDEA_NUMBER", WorkflowAction.SHOW_IDEA_DETAILS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
            
            String response = action.getResponseMessage().orElse("");
            assertThat(response).contains("Idea 3");
        }
        
        @Test
        @DisplayName("should generate more ideas when no current ideas exist")
        void shouldGenerateIdeasWhenNoCurrentIdeasExist() {
            // Arrange - don't call onEntry, so no ideas exist
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Here are new ideas!");
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "1");
            PatternResult match = PatternResult.of("IDEA_NUMBER", WorkflowAction.SHOW_IDEA_DETAILS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert - should generate new ideas instead of failing
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("handle() - GENERATE_MORE_IDEAS")
    class HandleGenerateMoreIdeasTests {
        
        @Test
        @DisplayName("should generate new ideas when father requests more - Requirement 9.4")
        void shouldGenerateNewIdeasWhenRequested() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Here are more activity ideas!");
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "more");
            PatternResult match = PatternResult.of("MORE_IDEAS", WorkflowAction.GENERATE_MORE_IDEAS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
            assertThat(action.isTransition()).isFalse();
            
            // Verify message generator was called with new ideas
            verify(messageGenerator).generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong());
        }
        
        @Test
        @DisplayName("should work with Hebrew 'עוד' keyword")
        void shouldWorkWithHebrewMoreKeyword() {
            // Arrange
            SystemState systemState = createMockSystemState("he");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("הנה עוד רעיונות!");
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "עוד רעיונות");
            PatternResult match = PatternResult.of("MORE_IDEAS_HE", WorkflowAction.GENERATE_MORE_IDEAS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("handle() - RETURN_TO_PREVIOUS")
    class HandleReturnToPreviousTests {
        
        @Test
        @DisplayName("should return to previous state (WAITING) - Requirement 9.6")
        void shouldReturnToWaitingState() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            Father father = new Father("1234567890");
            father.setId(FATHER_ID);
            father.setPreviousWorkflowState(WorkflowState.WAITING);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "thanks");
            PatternResult match = PatternResult.of("EXIT", WorkflowAction.RETURN_TO_PREVIOUS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.WAITING);
            assertThat(action.hasResponse()).isTrue();
        }
        
        @Test
        @DisplayName("should return to SCHEDULE_QUALITY_TIME if that was previous state")
        void shouldReturnToScheduleIfThatWasPrevious() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            Father father = new Father("1234567890");
            father.setId(FATHER_ID);
            father.setPreviousWorkflowState(WorkflowState.SCHEDULE_QUALITY_TIME);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "done");
            PatternResult match = PatternResult.of("EXIT", WorkflowAction.RETURN_TO_PREVIOUS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.SCHEDULE_QUALITY_TIME);
        }
        
        @Test
        @DisplayName("should default to WAITING if no previous state is stored")
        void shouldDefaultToWaitingIfNoPreviousState() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            Father father = new Father("1234567890");
            father.setId(FATHER_ID);
            father.setPreviousWorkflowState(null);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "thanks");
            PatternResult match = PatternResult.of("EXIT", WorkflowAction.RETURN_TO_PREVIOUS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.WAITING);
        }
        
        @Test
        @DisplayName("should default to WAITING if father not found")
        void shouldDefaultToWaitingIfFatherNotFound() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.empty());
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "thanks");
            PatternResult match = PatternResult.of("EXIT", WorkflowAction.RETURN_TO_PREVIOUS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.WAITING);
        }
        
        @Test
        @DisplayName("should work with Hebrew 'תודה' keyword")
        void shouldWorkWithHebrewThanksKeyword() {
            // Arrange
            SystemState systemState = createMockSystemState("he");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            Father father = new Father("1234567890");
            father.setId(FATHER_ID);
            father.setPreviousWorkflowState(WorkflowState.WAITING);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "תודה");
            PatternResult match = PatternResult.of("EXIT_HE", WorkflowAction.RETURN_TO_PREVIOUS);
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.WAITING);
            
            // Verify Hebrew thanks message
            String response = action.getResponseMessage().orElse("");
            assertThat(response).contains("תודה");
        }
        
        @Test
        @DisplayName("should clean up session data when exiting")
        void shouldCleanUpSessionDataWhenExiting() {
            // Arrange - first generate some ideas
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Ideas");
            handler.onEntry(FATHER_UUID);
            
            Father father = new Father("1234567890");
            father.setId(FATHER_ID);
            father.setPreviousWorkflowState(WorkflowState.WAITING);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "done");
            PatternResult match = PatternResult.of("EXIT", WorkflowAction.RETURN_TO_PREVIOUS);
            
            // Act
            handler.handle(context, match);
            
            // Now try to show idea details - should generate new ideas since session was cleared
            WorkflowContext newContext = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "1");
            PatternResult newMatch = PatternResult.of("IDEA_NUMBER", WorkflowAction.SHOW_IDEA_DETAILS);
            
            StateAction action = handler.handle(newContext, newMatch);
            
            // Assert - since ideas were cleared, new ones should be generated
            verify(messageGenerator, atLeast(2)).generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong());
        }
    }
    
    @Nested
    @DisplayName("handleUnmatched()")
    class HandleUnmatchedTests {
        
        @Test
        @DisplayName("should return clarification with valid options in English")
        void shouldReturnClarificationInEnglish() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "random message");
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            // Act - Per Requirement 11.4, handleUnmatched uses hardcoded messages, NOT AI
            StateAction action = handler.handleUnmatched(context);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.CLARIFY);
            assertThat(action.hasResponse()).isTrue();
            assertThat(action.isTransition()).isFalse();
            // Verify the hardcoded English clarification message
            assertThat(action.getResponseMessage().orElse(""))
                    .contains("Type a number (1-3)")
                    .contains("more")
                    .contains("thanks");
        }
        
        @Test
        @DisplayName("should return clarification with valid options in Hebrew")
        void shouldReturnClarificationInHebrew() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "הודעה אקראית");
            SystemState systemState = createMockSystemState("he");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            // Act - Per Requirement 11.4, handleUnmatched uses hardcoded messages, NOT AI
            StateAction action = handler.handleUnmatched(context);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.CLARIFY);
            assertThat(action.hasResponse()).isTrue();
            // Verify the hardcoded Hebrew clarification message
            assertThat(action.getResponseMessage().orElse(""))
                    .contains("לא הבנתי")
                    .contains("עוד")
                    .contains("תודה");
        }
        
        @Test
        @DisplayName("should throw when context is null")
        void shouldThrowWhenContextIsNull() {
            assertThatThrownBy(() -> handler.handleUnmatched(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    
    @Nested
    @DisplayName("handle() - validation")
    class HandleValidationTests {
        
        @Test
        @DisplayName("should throw when context is null")
        void shouldThrowWhenContextIsNull() {
            PatternResult match = PatternResult.of("TEST", WorkflowAction.SHOW_IDEA_DETAILS);
            
            assertThatThrownBy(() -> handler.handle(null, match))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("should throw when match is null")
        void shouldThrowWhenMatchIsNull() {
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.ACTIVITY_IDEAS, "test");
            
            assertThatThrownBy(() -> handler.handle(context, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    
    @Nested
    @DisplayName("Activity Ideas Generation - Requirement 9.3")
    class ActivityIdeasGenerationTests {
        
        @Test
        @DisplayName("should include at least one indoor and one outdoor idea when possible")
        void shouldIncludeIndoorAndOutdoorIdeas() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Ideas");
            
            // Act
            handler.onEntry(FATHER_UUID);
            
            // Assert
            ArgumentCaptor<MessageContext> contextCaptor = ArgumentCaptor.forClass(MessageContext.class);
            verify(messageGenerator).generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), contextCaptor.capture(), anyLong());
            
            List<ActivityIdea> ideas = contextCaptor.getValue().getActivityIdeas();
            assertThat(ideas).hasSize(3);
            
            // Should have at least one indoor and one outdoor
            boolean hasIndoor = ideas.stream().anyMatch(ActivityIdea::indoor);
            boolean hasOutdoor = ideas.stream().anyMatch(idea -> !idea.indoor());
            
            assertThat(hasIndoor).isTrue();
            assertThat(hasOutdoor).isTrue();
        }
        
        @Test
        @DisplayName("each idea should have title, description, and duration")
        void eachIdeaShouldHaveTitleDescriptionDuration() {
            // Arrange
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Ideas");
            
            // Act
            handler.onEntry(FATHER_UUID);
            
            // Assert
            ArgumentCaptor<MessageContext> contextCaptor = ArgumentCaptor.forClass(MessageContext.class);
            verify(messageGenerator).generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), contextCaptor.capture(), anyLong());
            
            List<ActivityIdea> ideas = contextCaptor.getValue().getActivityIdeas();
            
            for (ActivityIdea idea : ideas) {
                assertThat(idea.title()).isNotNull().isNotEmpty();
                assertThat(idea.description()).isNotNull().isNotEmpty();
                assertThat(idea.durationMinutes()).isPositive();
            }
        }
    }
    
    // ─── Helper Methods ──────────────────────────────────────────────────────
    
    private SystemState createMockSystemState(String locale) {
        SystemState.FatherProfile fatherProfile = new SystemState.FatherProfile(
                FATHER_ID,
                "David",
                "1234567890",
                List.of(new SystemState.ChildInfo(CHILD_ID, "Maya", LocalDate.of(2019, 5, 15), 5, "female", List.of("sports", "drawing"))),
                locale,
                "Asia/Jerusalem",
                null,
                true
        );
        
        SystemState.DashboardMetrics dashboardMetrics = new SystemState.DashboardMetrics(
                Belt.GREEN,
                5, // currentStreak
                10, // longestStreak
                25, // totalCompleted
                List.of(), // recentAchievements
                50, // progressToNextBelt
                25 // qualityTimesToNextBelt
        );
        
        // Include some past quality time events for context
        List<SystemState.QualityTimeEvent> qualityTimeEvents = List.of(
                new SystemState.QualityTimeEvent(
                        UUID.randomUUID(),
                        CHILD_ID,
                        "Maya",
                        Instant.now().minusSeconds(86400 * 2),
                        Instant.now().minusSeconds(86400 * 2 - 1800),
                        "COMPLETED",
                        null,
                        Instant.now().minusSeconds(86400 * 2 - 1800),
                        "We played soccer together"
                )
        );
        
        return new SystemState(
                fatherProfile,
                WorkflowState.ACTIVITY_IDEAS,
                List.of(), // calendarEvents
                qualityTimeEvents,
                dashboardMetrics,
                List.of() // conversationContext
        );
    }
}
