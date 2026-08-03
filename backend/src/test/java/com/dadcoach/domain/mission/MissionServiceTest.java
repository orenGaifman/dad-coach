package com.dadcoach.domain.mission;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.goal.Goal;
import com.dadcoach.domain.goal.GoalRepository;
import com.dadcoach.mission.LegacyMissionStatus;
import static com.dadcoach.mission.LegacyMissionStatus.*;
import com.dadcoach.statemachine.StateMachineEngine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MissionService covering:
 * - Mission creation with single-active-per-child constraint
 * - All state transitions via StateMachineEngine
 * - Outcome rating validation on completion
 * - Retrieval operations
 */
@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private FatherRepository fatherRepository;

    @Mock
    private ChildRepository childRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private StateMachineEngine stateMachineEngine;

    @InjectMocks
    private MissionService missionService;

    private Father father;
    private Child child;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        father.setId(1L);

        child = new Child(father, "Test Child", LocalDate.of(2018, 6, 15));
        child.setId(10L);
    }

    private Mission createTestMission() {
        Mission mission = new Mission(father, child, "Test Mission", "Do something fun",
                "CONNECTION", 2, 20);
        mission.setId(100L);
        return mission;
    }

    // ─── Creation Tests ──────────────────────────────────────────────────

    @Nested
    class CreateMission {

        @Test
        void shouldCreateMissionWithAssignedStatus() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.findById(10L)).thenReturn(Optional.of(child));
            when(missionRepository.countActiveMissionsByChildId(10L)).thenReturn(0L);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> {
                Mission m = inv.getArgument(0);
                m.setId(100L);
                return m;
            });

            Mission result = missionService.createMission(1L, 10L, null,
                    "Test Mission", "Description", "CONNECTION", 2, 20);

            assertThat(result.getStatus()).isEqualTo(ASSIGNED);
            assertThat(result.getTitle()).isEqualTo("Test Mission");
            assertThat(result.getDifficulty()).isEqualTo(2);
            assertThat(result.getEstimatedMinutes()).isEqualTo(20);
            assertThat(result.getCategory()).isEqualTo("CONNECTION");
            verify(missionRepository).save(any(Mission.class));
        }

        @Test
        void shouldLinkGoalWhenGoalIdProvided() {
            Goal goal = mock(Goal.class);

            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.findById(10L)).thenReturn(Optional.of(child));
            when(goalRepository.findById(5L)).thenReturn(Optional.of(goal));
            when(missionRepository.countActiveMissionsByChildId(10L)).thenReturn(0L);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.createMission(1L, 10L, 5L,
                    "Goal Mission", "Description", "COMMUNICATION", 3, 30);

            assertThat(result.getGoal()).isEqualTo(goal);
        }

        @Test
        void shouldRejectCreationWhenChildHasActiveMission() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.findById(10L)).thenReturn(Optional.of(child));
            when(missionRepository.countActiveMissionsByChildId(10L)).thenReturn(1L);

            assertThatThrownBy(() -> missionService.createMission(1L, 10L, null,
                    "Test", "Desc", "CONNECTION", 2, 20))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("SINGLE_ACTIVE_MISSION_PER_CHILD");
        }

        @Test
        void shouldThrowWhenFatherNotFound() {
            when(fatherRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.createMission(999L, 10L, null,
                    "Test", "Desc", "CONNECTION", 2, 20))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Father");
        }

        @Test
        void shouldThrowWhenChildNotFound() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.createMission(1L, 999L, null,
                    "Test", "Desc", "CONNECTION", 2, 20))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Child");
        }

        @Test
        void shouldThrowWhenGoalNotFound() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.findById(10L)).thenReturn(Optional.of(child));
            when(missionRepository.countActiveMissionsByChildId(10L)).thenReturn(0L);
            when(goalRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.createMission(1L, 10L, 999L,
                    "Test", "Desc", "CONNECTION", 2, 20))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Goal");
        }
    }

    // ─── Accept Mission Tests ────────────────────────────────────────────

    @Nested
    class AcceptMission {

        @Test
        void shouldTransitionFromAssignedToAccepted() {
            Mission mission = createTestMission();
            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(ASSIGNED),
                    eq(ACCEPTED), anyString()))
                    .thenReturn(ACCEPTED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.acceptMission(100L);

            assertThat(result.getStatus()).isEqualTo(ACCEPTED);
            assertThat(result.getAcceptedAt()).isNotNull();
            verify(stateMachineEngine).transition(
                    eq("Mission"), eq(100L), eq(ASSIGNED),
                    eq(ACCEPTED), anyString());
        }

        @Test
        void shouldThrowWhenMissionNotFound() {
            when(missionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.acceptMission(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── Start Mission Tests ─────────────────────────────────────────────

    @Nested
    class StartMission {

        @Test
        void shouldTransitionFromAcceptedToInProgress() {
            Mission mission = createTestMission();
            mission.transitionTo(ACCEPTED);

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(ACCEPTED),
                    eq(IN_PROGRESS), anyString()))
                    .thenReturn(IN_PROGRESS);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.startMission(100L);

            assertThat(result.getStatus()).isEqualTo(IN_PROGRESS);
        }
    }

    // ─── Complete Mission Tests ──────────────────────────────────────────

    @Nested
    class CompleteMission {

        @Test
        void shouldTransitionToCompletedAndSetOutcome() {
            Mission mission = createTestMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(IN_PROGRESS),
                    eq(COMPLETED), anyString()))
                    .thenReturn(COMPLETED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.completeMission(100L, 4, "Great bonding time!");

            assertThat(result.getStatus()).isEqualTo(COMPLETED);
            assertThat(result.getOutcomeRating()).isEqualTo(4);
            assertThat(result.getOutcomeNotes()).isEqualTo("Great bonding time!");
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        void shouldRejectRatingBelowOne() {
            assertThatThrownBy(() -> missionService.completeMission(100L, 0, "Notes"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_OUTCOME_RATING");
        }

        @Test
        void shouldRejectRatingAboveFive() {
            assertThatThrownBy(() -> missionService.completeMission(100L, 6, "Notes"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_OUTCOME_RATING");
        }

        @Test
        void shouldAcceptMinimumRating() {
            Mission mission = createTestMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.<LegacyMissionStatus>transition(
                    eq("Mission"), eq(100L), eq(IN_PROGRESS),
                    eq(COMPLETED), anyString()))
                    .thenReturn(COMPLETED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.completeMission(100L, 1, null);

            assertThat(result.getOutcomeRating()).isEqualTo(1);
            assertThat(result.getOutcomeNotes()).isNull();
        }

        @Test
        void shouldAcceptMaximumRating() {
            Mission mission = createTestMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.<LegacyMissionStatus>transition(
                    eq("Mission"), eq(100L), eq(IN_PROGRESS),
                    eq(COMPLETED), anyString()))
                    .thenReturn(COMPLETED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.completeMission(100L, 5, "Perfect!");

            assertThat(result.getOutcomeRating()).isEqualTo(5);
        }
    }

    // ─── Skip Mission Tests ──────────────────────────────────────────────

    @Nested
    class SkipMission {

        @Test
        void shouldTransitionFromAssignedToSkipped() {
            Mission mission = createTestMission();
            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(ASSIGNED),
                    eq(SKIPPED), anyString()))
                    .thenReturn(SKIPPED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.skipMission(100L);

            assertThat(result.getStatus()).isEqualTo(SKIPPED);
        }
    }

    // ─── Expire Mission Tests ────────────────────────────────────────────

    @Nested
    class ExpireMission {

        @Test
        void shouldTransitionFromAssignedToExpired() {
            Mission mission = createTestMission();
            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(ASSIGNED),
                    eq(EXPIRED), anyString()))
                    .thenReturn(EXPIRED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.expireMission(100L);

            assertThat(result.getStatus()).isEqualTo(EXPIRED);
        }

        @Test
        void shouldTransitionFromAcceptedToExpired() {
            Mission mission = createTestMission();
            mission.transitionTo(ACCEPTED);

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(ACCEPTED),
                    eq(EXPIRED), anyString()))
                    .thenReturn(EXPIRED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.expireMission(100L);

            assertThat(result.getStatus()).isEqualTo(EXPIRED);
        }
    }

    // ─── Abandon Mission Tests ───────────────────────────────────────────

    @Nested
    class AbandonMission {

        @Test
        void shouldTransitionFromInProgressToAbandoned() {
            Mission mission = createTestMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(IN_PROGRESS),
                    eq(ABANDONED), anyString()))
                    .thenReturn(ABANDONED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.abandonMission(100L);

            assertThat(result.getStatus()).isEqualTo(ABANDONED);
        }
    }

    // ─── Reflect On Mission Tests ────────────────────────────────────────

    @Nested
    class ReflectOnMission {

        @Test
        void shouldTransitionFromCompletedToReflected() {
            Mission mission = createTestMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);
            mission.transitionTo(COMPLETED);

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(COMPLETED),
                    eq(REFLECTED), anyString()))
                    .thenReturn(REFLECTED);
            when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

            Mission result = missionService.reflectOnMission(100L);

            assertThat(result.getStatus()).isEqualTo(REFLECTED);
        }
    }

    // ─── Retrieval Tests ─────────────────────────────────────────────────

    @Nested
    class Retrieval {

        @Test
        void getMissionShouldReturnMission() {
            Mission mission = createTestMission();
            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));

            Mission result = missionService.getMission(100L);

            assertThat(result).isEqualTo(mission);
        }

        @Test
        void getMissionShouldThrowWhenNotFound() {
            when(missionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.getMission(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Mission");
        }

        @Test
        void getActiveMissionsForChildShouldDelegateToRepository() {
            Mission mission = createTestMission();
            when(missionRepository.findActiveMissionsByChildId(10L))
                    .thenReturn(List.of(mission));

            List<Mission> results = missionService.getActiveMissionsForChild(10L);

            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo(mission);
            verify(missionRepository).findActiveMissionsByChildId(10L);
        }
    }

    // ─── Invalid Transition Tests ────────────────────────────────────────

    @Nested
    class InvalidTransitions {

        @Test
        void shouldNotAllowSkippingAcceptedMission() {
            Mission mission = createTestMission();
            mission.transitionTo(ACCEPTED);

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(ACCEPTED),
                    eq(SKIPPED), anyString()))
                    .thenThrow(new InvalidStateTransitionException("Mission", 100L, "ACCEPTED", "SKIPPED"));

            assertThatThrownBy(() -> missionService.skipMission(100L))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void shouldNotAllowAbandoningAssignedMission() {
            Mission mission = createTestMission();

            when(missionRepository.findById(100L)).thenReturn(Optional.of(mission));
            when(stateMachineEngine.transition(
                    eq("Mission"), eq(100L), eq(ASSIGNED),
                    eq(ABANDONED), anyString()))
                    .thenThrow(new InvalidStateTransitionException("Mission", 100L, "ASSIGNED", "ABANDONED"));

            assertThatThrownBy(() -> missionService.abandonMission(100L))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }
}
