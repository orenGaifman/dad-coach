package com.dadcoach.conversation.mission;

import com.dadcoach.conversation.ConversationService;
import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildService;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;
import com.dadcoach.domain.mission.MissionService;
import com.dadcoach.mission.LegacyMissionStatus;
import static com.dadcoach.mission.LegacyMissionStatus.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionOrchestratorImpl Unit Tests")
class MissionOrchestratorImplTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private MissionService missionService;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private ChildService childService;

    private MissionOrchestratorImpl missionOrchestrator;

    @BeforeEach
    void setUp() {
        missionOrchestrator = new MissionOrchestratorImpl(
                conversationService, missionService, missionRepository, childService);
    }

    // ─── 9.1: Process GENERATE_MISSION action from AI response ────────────

    @Nested
    @DisplayName("9.1 - Process GENERATE_MISSION action")
    class GenerateMission {

        @Test
        @DisplayName("Generates mission when child has no active mission")
        void generateMission_childWithNoActiveMission_createsMission() {
            UUID fatherId = UUID.randomUUID();
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Conversation conversation = buildActiveConversation(fatherId);

            Child child = buildChild(1L, "Lucas", domainFatherId);
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of(child));
            when(missionRepository.countActiveMissionsByChildId(1L)).thenReturn(0L);

            Mission createdMission = buildMission(100L, "Misión de conexión con Lucas");
            when(missionService.createMission(eq(domainFatherId), eq(1L), isNull(),
                    anyString(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(createdMission);

            Optional<String> result = missionOrchestrator.generateMission(fatherId, conversation);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("Lucas");
            verify(missionService).createMission(eq(domainFatherId), eq(1L), isNull(),
                    anyString(), anyString(), eq("GENERAL"), eq(2), eq(15));
        }

        @Test
        @DisplayName("Returns empty when father ID is null")
        void generateMission_nullFatherId_returnsEmpty() {
            Conversation conversation = buildActiveConversation(UUID.randomUUID());

            Optional<String> result = missionOrchestrator.generateMission(null, conversation);

            assertThat(result).isEmpty();
            verifyNoInteractions(missionService);
        }

        @Test
        @DisplayName("Returns empty when father has no children")
        void generateMission_noChildren_returnsEmpty() {
            UUID fatherId = UUID.randomUUID();
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Conversation conversation = buildActiveConversation(fatherId);

            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());

            Optional<String> result = missionOrchestrator.generateMission(fatherId, conversation);

            assertThat(result).isEmpty();
            verifyNoInteractions(missionService);
        }

        @Test
        @DisplayName("Returns empty when MissionService throws exception")
        void generateMission_serviceThrows_returnsEmpty() {
            UUID fatherId = UUID.randomUUID();
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Conversation conversation = buildActiveConversation(fatherId);

            Child child = buildChild(1L, "Sofia", domainFatherId);
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of(child));
            when(missionRepository.countActiveMissionsByChildId(1L)).thenReturn(0L);
            when(missionService.createMission(anyLong(), anyLong(), any(), anyString(),
                    anyString(), anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB error"));

            Optional<String> result = missionOrchestrator.generateMission(fatherId, conversation);

            assertThat(result).isEmpty();
        }
    }

    // ─── 9.2: Validate mission generation preconditions ───────────────────

    @Nested
    @DisplayName("9.2 - Validate preconditions (no active mission for child)")
    class ValidatePreconditions {

        @Test
        @DisplayName("Skips generation when all children have active missions")
        void generateMission_allChildrenHaveActiveMissions_returnsEmpty() {
            UUID fatherId = UUID.randomUUID();
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Conversation conversation = buildActiveConversation(fatherId);

            Child child1 = buildChild(1L, "Lucas", domainFatherId);
            Child child2 = buildChild(2L, "Sofia", domainFatherId);
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of(child1, child2));
            when(missionRepository.countActiveMissionsByChildId(1L)).thenReturn(1L);
            when(missionRepository.countActiveMissionsByChildId(2L)).thenReturn(1L);

            Optional<String> result = missionOrchestrator.generateMission(fatherId, conversation);

            assertThat(result).isEmpty();
            verifyNoInteractions(missionService);
        }

        @Test
        @DisplayName("Selects second child when first child already has active mission")
        void generateMission_firstChildHasActiveMission_selectsSecond() {
            UUID fatherId = UUID.randomUUID();
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Conversation conversation = buildActiveConversation(fatherId);

            Child child1 = buildChild(1L, "Lucas", domainFatherId);
            Child child2 = buildChild(2L, "Sofia", domainFatherId);
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of(child1, child2));
            when(missionRepository.countActiveMissionsByChildId(1L)).thenReturn(1L);
            when(missionRepository.countActiveMissionsByChildId(2L)).thenReturn(0L);

            Mission createdMission = buildMission(101L, "Misión de conexión con Sofia");
            when(missionService.createMission(eq(domainFatherId), eq(2L), isNull(),
                    anyString(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(createdMission);

            Optional<String> result = missionOrchestrator.generateMission(fatherId, conversation);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("Sofia");
            verify(missionService).createMission(eq(domainFatherId), eq(2L), isNull(),
                    anyString(), anyString(), anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Skips archived children (only considers ACTIVE children)")
        void generateMission_archivedChildren_skipped() {
            UUID fatherId = UUID.randomUUID();
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Conversation conversation = buildActiveConversation(fatherId);

            Child activeChild = buildChild(1L, "Lucas", domainFatherId);
            Child archivedChild = buildChild(2L, "Sofia", domainFatherId);
            archivedChild.setStatus("ARCHIVED");

            when(childService.getChildrenByFather(domainFatherId))
                    .thenReturn(List.of(activeChild, archivedChild));
            when(missionRepository.countActiveMissionsByChildId(1L)).thenReturn(0L);

            Mission createdMission = buildMission(102L, "Misión de conexión con Lucas");
            when(missionService.createMission(eq(domainFatherId), eq(1L), isNull(),
                    anyString(), anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn(createdMission);

            Optional<String> result = missionOrchestrator.generateMission(fatherId, conversation);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("Lucas");
            // Should never check mission count for archived child
            verify(missionRepository, never()).countActiveMissionsByChildId(2L);
        }
    }

    // ─── 9.3: Handle mission acceptance/completion within conversation flow ──

    @Nested
    @DisplayName("9.3 - Mission acceptance/completion/skip within conversation")
    class MissionStateTransitions {

        @Test
        @DisplayName("acceptMission delegates to MissionService")
        void acceptMission_delegatesToService() {
            missionOrchestrator.acceptMission(42L);

            verify(missionService).acceptMission(42L);
        }

        @Test
        @DisplayName("completeMission starts and completes ACCEPTED mission")
        void completeMission_acceptedMission_startsAndCompletes() {
            Mission mission = mock(Mission.class);
            when(mission.getStatus()).thenReturn(ACCEPTED);
            when(missionService.getMission(42L)).thenReturn(mission);

            missionOrchestrator.completeMission(42L);

            verify(missionService).startMission(42L);
            verify(missionService).completeMission(42L, 4, "Completada durante conversación");
        }

        @Test
        @DisplayName("completeMission directly completes IN_PROGRESS mission")
        void completeMission_inProgressMission_completesDirectly() {
            Mission mission = mock(Mission.class);
            when(mission.getStatus()).thenReturn(IN_PROGRESS);
            when(missionService.getMission(42L)).thenReturn(mission);

            missionOrchestrator.completeMission(42L);

            verify(missionService, never()).startMission(anyLong());
            verify(missionService).completeMission(42L, 4, "Completada durante conversación");
        }

        @Test
        @DisplayName("skipMission delegates to MissionService")
        void skipMission_delegatesToService() {
            missionOrchestrator.skipMission(99L);

            verify(missionService).skipMission(99L);
        }
    }

    // ─── 9.4: Delegate to MissionEngine for actual generation ─────────────

    @Nested
    @DisplayName("9.4 - Delegation to MissionService")
    class DelegationToMissionService {

        @Test
        @DisplayName("generateMission passes correct parameters to MissionService")
        void generateMission_passesCorrectParams() {
            UUID fatherId = UUID.randomUUID();
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Conversation conversation = buildActiveConversation(fatherId);

            Child child = buildChild(5L, "Mateo", domainFatherId);
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of(child));
            when(missionRepository.countActiveMissionsByChildId(5L)).thenReturn(0L);

            Mission createdMission = buildMission(200L, "Misión de conexión con Mateo");
            when(missionService.createMission(eq(domainFatherId), eq(5L), isNull(),
                    eq("Misión de conexión con Mateo"),
                    contains("Mateo"),
                    eq("GENERAL"), eq(2), eq(15)))
                    .thenReturn(createdMission);

            missionOrchestrator.generateMission(fatherId, conversation);

            verify(missionService).createMission(
                    eq(domainFatherId),
                    eq(5L),
                    isNull(),
                    eq("Misión de conexión con Mateo"),
                    contains("Mateo"),
                    eq("GENERAL"),
                    eq(2),
                    eq(15)
            );
        }
    }

    // ─── 9.5: Persist mission state changes within same transaction ───────

    @Nested
    @DisplayName("9.5 - State changes within same transaction")
    class TransactionalPersistence {

        @Test
        @DisplayName("closeConversation transitions conversation to COMPLETED with OBJECTIVE_MET")
        void closeConversation_completesWithObjectiveMet() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = mock(Conversation.class);
            when(conversation.getId()).thenReturn(conversationId);

            when(conversationService.completeConversation(conversationId, "OBJECTIVE_MET"))
                    .thenReturn(conversation);

            missionOrchestrator.closeConversation(conversation);

            verify(conversationService).completeConversation(conversationId, "OBJECTIVE_MET");
        }

        @Test
        @DisplayName("closeConversation does not throw when ConversationService fails")
        void closeConversation_serviceThrows_doesNotPropagate() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = mock(Conversation.class);
            when(conversation.getId()).thenReturn(conversationId);

            when(conversationService.completeConversation(conversationId, "OBJECTIVE_MET"))
                    .thenThrow(new IllegalStateException("Already completed"));

            // Should not throw
            assertThatCode(() -> missionOrchestrator.closeConversation(conversation))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("generateMission operates within caller's transaction (no new transaction annotations)")
        void generateMission_noOwnTransactionAnnotation() {
            // Verify the class itself does not declare @Transactional
            // (it relies on the caller's transaction — ConversationOrchestrator's @Transactional)
            assertThat(MissionOrchestratorImpl.class.isAnnotationPresent(
                    org.springframework.transaction.annotation.Transactional.class)).isFalse();
        }
    }

    // ─── Test Helpers ─────────────────────────────────────────────────────

    private Conversation buildActiveConversation(UUID fatherId) {
        return Conversation.builder()
                .fatherId(fatherId)
                .type("DAILY_COACHING")
                .status("ACTIVE")
                .build();
    }

    private Child buildChild(Long id, String name, Long fatherId) {
        Child child = mock(Child.class, withSettings().lenient());
        when(child.getId()).thenReturn(id);
        when(child.getName()).thenReturn(name);
        when(child.getStatus()).thenReturn("ACTIVE");
        return child;
    }

    private Mission buildMission(Long id, String title) {
        Mission mission = mock(Mission.class, withSettings().lenient());
        when(mission.getId()).thenReturn(id);
        when(mission.getTitle()).thenReturn(title);
        when(mission.getStatus()).thenReturn(ASSIGNED);
        return mission;
    }
}
