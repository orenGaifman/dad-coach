package com.dadcoach.systemstate;

import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.entity.ConversationMessage;
import com.dadcoach.conversation.repository.ConversationMessageRepository;
import com.dadcoach.conversation.repository.ConversationRepository;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SystemStateLoaderImpl.
 * Validates: Requirement 2.1 (Read Before Write - System State Loading)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemStateLoaderImpl")
class SystemStateLoaderImplTest {

    @Mock
    private FatherRepository fatherRepository;

    @Mock
    private ChildRepository childRepository;

    @Mock
    private QualityTimeRepository qualityTimeRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    private SystemStateLoaderImpl systemStateLoader;

    @BeforeEach
    void setUp() {
        systemStateLoader = new SystemStateLoaderImpl(
                fatherRepository,
                childRepository,
                qualityTimeRepository,
                conversationRepository,
                conversationMessageRepository
        );
    }

    @Nested
    @DisplayName("loadState")
    class LoadStateTest {

        @Test
        @DisplayName("throws IllegalArgumentException when fatherId is null")
        void throwsWhenFatherIdIsNull() {
            assertThatThrownBy(() -> systemStateLoader.loadState(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fatherId must not be null");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when father does not exist")
        void throwsWhenFatherNotFound() {
            UUID fatherId = UUID.randomUUID();
            when(fatherRepository.findById(anyLong())).thenReturn(Optional.empty());
            when(fatherRepository.findAll()).thenReturn(List.of());

            assertThatThrownBy(() -> systemStateLoader.loadState(fatherId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Father");
        }

        @Test
        @DisplayName("loads complete system state successfully")
        void loadsCompleteSystemState() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            
            Father father = createTestFather(numericFatherId);
            Child child = createTestChild(numericFatherId, father);
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));
            when(childRepository.findByFatherId(numericFatherId)).thenReturn(List.of(child));
            when(qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(numericFatherId)).thenReturn(List.of());
            when(conversationRepository.findActiveByFatherId(any())).thenReturn(Optional.empty());

            // When
            SystemState state = systemStateLoader.loadState(fatherId);

            // Then
            assertThat(state).isNotNull();
            assertThat(state.fatherProfile()).isNotNull();
            assertThat(state.fatherProfile().displayName()).isEqualTo("Test Father");
            assertThat(state.fatherProfile().locale()).isEqualTo("en");
            assertThat(state.fatherProfile().timezone()).isEqualTo("America/New_York");
            assertThat(state.workflowState()).isEqualTo(WorkflowState.WELCOME);
            assertThat(state.dashboardMetrics()).isNotNull();
            assertThat(state.dashboardMetrics().currentBelt()).isEqualTo(Belt.WHITE);
        }

        @Test
        @DisplayName("loads children information correctly")
        void loadsChildrenInformation() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            
            Father father = createTestFather(numericFatherId);
            Child child1 = createTestChild(numericFatherId, father, "Child One", 5);
            Child child2 = createTestChild(numericFatherId, father, "Child Two", 8);
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));
            when(childRepository.findByFatherId(numericFatherId)).thenReturn(List.of(child1, child2));
            when(qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(numericFatherId)).thenReturn(List.of());
            when(conversationRepository.findActiveByFatherId(any())).thenReturn(Optional.empty());

            // When
            SystemState state = systemStateLoader.loadState(fatherId);

            // Then
            assertThat(state.fatherProfile().children()).hasSize(2);
            assertThat(state.fatherProfile().children().get(0).name()).isEqualTo("Child One");
            assertThat(state.fatherProfile().children().get(1).name()).isEqualTo("Child Two");
        }

        @Test
        @DisplayName("loads Quality Time events correctly")
        void loadsQualityTimeEvents() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            
            Father father = createTestFather(numericFatherId);
            Child child = createTestChild(numericFatherId, father);
            QualityTime qualityTime = createTestQualityTime(father, child);
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));
            when(childRepository.findByFatherId(numericFatherId)).thenReturn(List.of(child));
            when(qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(numericFatherId))
                    .thenReturn(List.of(qualityTime));
            when(conversationRepository.findActiveByFatherId(any())).thenReturn(Optional.empty());

            // When
            SystemState state = systemStateLoader.loadState(fatherId);

            // Then
            assertThat(state.qualityTimeEvents()).hasSize(1);
            assertThat(state.qualityTimeEvents().get(0).status()).isEqualTo("SCHEDULED");
        }

        @Test
        @DisplayName("loads dashboard metrics with correct belt calculation")
        void loadsDashboardMetricsWithCorrectBelt() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            
            Father father = createTestFather(numericFatherId);
            father.setCurrentBelt(Belt.YELLOW);
            father.setTotalQualityTimesCompleted(5);
            father.setQualityTimeStreak(3);
            father.setQualityTimeLongestStreak(4);
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));
            when(childRepository.findByFatherId(numericFatherId)).thenReturn(List.of());
            when(qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(numericFatherId)).thenReturn(List.of());
            when(conversationRepository.findActiveByFatherId(any())).thenReturn(Optional.empty());

            // When
            SystemState state = systemStateLoader.loadState(fatherId);

            // Then
            SystemState.DashboardMetrics metrics = state.dashboardMetrics();
            assertThat(metrics.currentBelt()).isEqualTo(Belt.YELLOW);
            assertThat(metrics.currentStreak()).isEqualTo(3);
            assertThat(metrics.longestStreak()).isEqualTo(4);
            assertThat(metrics.totalCompleted()).isEqualTo(5);
            assertThat(metrics.qualityTimesToNextBelt()).isGreaterThan(0);
        }

        @Test
        @DisplayName("loads conversation context when active conversation exists")
        void loadsConversationContext() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            UUID conversationId = UUID.randomUUID();
            
            Father father = createTestFather(numericFatherId);
            Conversation conversation = createTestConversation(fatherId, conversationId);
            ConversationMessage message = createTestConversationMessage(conversationId, "Hello");
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));
            when(childRepository.findByFatherId(numericFatherId)).thenReturn(List.of());
            when(qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(numericFatherId)).thenReturn(List.of());
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.of(conversation));
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of(message));

            // When
            SystemState state = systemStateLoader.loadState(fatherId);

            // Then
            assertThat(state.conversationContext()).hasSize(1);
            assertThat(state.conversationContext().get(0).content()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("returns empty conversation context when no active conversation")
        void returnsEmptyConversationContextWhenNoActiveConversation() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            
            Father father = createTestFather(numericFatherId);
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));
            when(childRepository.findByFatherId(numericFatherId)).thenReturn(List.of());
            when(qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(numericFatherId)).thenReturn(List.of());
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.empty());

            // When
            SystemState state = systemStateLoader.loadState(fatherId);

            // Then
            assertThat(state.conversationContext()).isEmpty();
        }

        @Test
        @DisplayName("defaults to English locale when father locale is null")
        void defaultsToEnglishLocale() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            
            Father father = createTestFather(numericFatherId);
            father.setLocale(null);
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));
            when(childRepository.findByFatherId(numericFatherId)).thenReturn(List.of());
            when(qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(numericFatherId)).thenReturn(List.of());
            when(conversationRepository.findActiveByFatherId(any())).thenReturn(Optional.empty());

            // When
            SystemState state = systemStateLoader.loadState(fatherId);

            // Then
            assertThat(state.fatherProfile().locale()).isEqualTo("en");
        }
    }

    @Nested
    @DisplayName("loadAvailableSlots")
    class LoadAvailableSlotsTest {

        @Test
        @DisplayName("throws IllegalArgumentException when fatherId is null")
        void throwsWhenFatherIdIsNull() {
            assertThatThrownBy(() -> systemStateLoader.loadAvailableSlots(null, 7))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fatherId must not be null");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when daysAhead is less than 1")
        void throwsWhenDaysAheadLessThanOne() {
            UUID fatherId = UUID.randomUUID();
            assertThatThrownBy(() -> systemStateLoader.loadAvailableSlots(fatherId, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("daysAhead must be between 1 and 14");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when daysAhead is greater than 14")
        void throwsWhenDaysAheadGreaterThanMax() {
            UUID fatherId = UUID.randomUUID();
            assertThatThrownBy(() -> systemStateLoader.loadAvailableSlots(fatherId, 15))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("daysAhead must be between 1 and 14");
        }

        @Test
        @DisplayName("returns empty list when Google Calendar is not connected")
        void returnsEmptyWhenCalendarNotConnected() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            
            Father father = createTestFather(numericFatherId);
            father.setGoogleCalendarEnabled(false);
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));

            // When
            List<AvailableSlot> slots = systemStateLoader.loadAvailableSlots(fatherId, 7);

            // Then
            assertThat(slots).isEmpty();
        }

        @Test
        @DisplayName("uses default 7 days when calling overloaded method")
        void usesDefaultDaysAhead() {
            // Given
            UUID fatherId = UUID.randomUUID();
            long numericFatherId = fatherId.getLeastSignificantBits();
            
            Father father = createTestFather(numericFatherId);
            father.setGoogleCalendarEnabled(false);
            
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));

            // When
            List<AvailableSlot> slots = systemStateLoader.loadAvailableSlots(fatherId);

            // Then
            assertThat(slots).isEmpty();
            verify(fatherRepository).findById(numericFatherId);
        }
    }

    // ─── Helper Methods ───────────────────────────────────────────────────

    private Father createTestFather(long fatherId) {
        Father father = new Father("+1234567890");
        try {
            var idField = Father.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(father, fatherId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        father.setDisplayName("Test Father");
        father.setLocale("en");
        father.setTimezone("America/New_York");
        father.setCurrentWorkflowState(WorkflowState.WELCOME);
        father.setCurrentBelt(Belt.WHITE);
        father.setQualityTimeStreak(0);
        father.setQualityTimeLongestStreak(0);
        father.setTotalQualityTimesCompleted(0);
        father.setGoogleCalendarEnabled(false);
        return father;
    }

    private Child createTestChild(long fatherId, Father father) {
        return createTestChild(fatherId, father, "Test Child", 5);
    }

    private Child createTestChild(long fatherId, Father father, String name, int ageInYears) {
        Child child = new Child(father, name, LocalDate.now().minusYears(ageInYears));
        try {
            var idField = Child.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(child, fatherId + 1000);
            
            var fatherIdField = Child.class.getDeclaredField("fatherId");
            fatherIdField.setAccessible(true);
            fatherIdField.set(child, fatherId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return child;
    }

    private QualityTime createTestQualityTime(Father father, Child child) {
        Instant scheduledStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant scheduledEnd = scheduledStart.plus(30, ChronoUnit.MINUTES);
        QualityTime qualityTime = new QualityTime(father, child, scheduledStart, scheduledEnd);
        try {
            var idField = QualityTime.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(qualityTime, UUID.randomUUID());
            
            var childIdField = QualityTime.class.getDeclaredField("childId");
            childIdField.setAccessible(true);
            childIdField.set(qualityTime, child.getId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return qualityTime;
    }

    private Conversation createTestConversation(UUID fatherId, UUID conversationId) {
        Conversation conversation = Conversation.builder()
                .fatherId(fatherId)
                .type("COACHING")
                .status("ACTIVE")
                .build();
        try {
            var idField = Conversation.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(conversation, conversationId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return conversation;
    }

    private ConversationMessage createTestConversationMessage(UUID conversationId, String content) {
        ConversationMessage message = ConversationMessage.builder()
                .conversationId(conversationId)
                .direction("INBOUND")
                .content(content)
                .sequenceNumber(1)
                .build();
        try {
            var idField = ConversationMessage.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(message, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return message;
    }
}
