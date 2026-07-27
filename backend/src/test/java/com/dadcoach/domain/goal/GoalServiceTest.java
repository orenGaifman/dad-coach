package com.dadcoach.domain.goal;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.goal.GoalCategory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private FatherRepository fatherRepository;

    @InjectMocks
    private GoalService goalService;

    private Father father;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        father.setId(1L);
    }

    @Nested
    @DisplayName("createGoal")
    class CreateGoal {

        @Test
        @DisplayName("should create a goal with valid inputs")
        void shouldCreateGoalWithValidInputs() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(goalRepository.countActiveByFatherId(1L)).thenReturn(0L);
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
                Goal g = inv.getArgument(0);
                g.setId(10L);
                return g;
            });

            Goal result = goalService.createGoal(1L, "Be more present", "Spend quality time",
                    GoalCategory.CONNECTION, 1);

            assertThat(result.getTitle()).isEqualTo("Be more present");
            assertThat(result.getDescription()).isEqualTo("Spend quality time");
            assertThat(result.getCategory()).isEqualTo(GoalCategory.CONNECTION);
            assertThat(result.getPriority()).isEqualTo(1);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getProgressPercentage()).isEqualTo(0);
            assertThat(result.getEstimatedTotalMissions()).isEqualTo(15); // CONNECTION=15
            assertThat(result.getCompletedRelatedMissions()).isEqualTo(0);
            verify(goalRepository).save(any(Goal.class));
        }

        @Test
        @DisplayName("should set estimatedTotalMissions based on GoalCategory")
        void shouldSetEstimatedMissionsFromCategory() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(goalRepository.countActiveByFatherId(1L)).thenReturn(0L);
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

            Goal result = goalService.createGoal(1L, "Daily routine", null,
                    GoalCategory.ROUTINE, 2);

            assertThat(result.getEstimatedTotalMissions()).isEqualTo(30); // ROUTINE=30
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when father not found")
        void shouldThrowWhenFatherNotFound() {
            when(fatherRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> goalService.createGoal(999L, "Title", null,
                    GoalCategory.FUN, 1))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Father");
        }

        @Test
        @DisplayName("should throw BusinessRuleViolationException when max 5 active goals exceeded")
        void shouldThrowWhenMaxGoalsExceeded() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(goalRepository.countActiveByFatherId(1L)).thenReturn(5L);

            assertThatThrownBy(() -> goalService.createGoal(1L, "Title", null,
                    GoalCategory.FUN, 1))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("MAX_GOALS_EXCEEDED");
        }

        @Test
        @DisplayName("should allow creating goal when father has exactly 4 active goals")
        void shouldAllowCreatingGoalWith4Existing() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(goalRepository.countActiveByFatherId(1L)).thenReturn(4L);
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

            Goal result = goalService.createGoal(1L, "Title", null, GoalCategory.HEALTH, 3);

            assertThat(result).isNotNull();
            assertThat(result.getEstimatedTotalMissions()).isEqualTo(15); // HEALTH=15
        }
    }

    @Nested
    @DisplayName("updateProgress")
    class UpdateProgress {

        @Test
        @DisplayName("should recalculate progress percentage")
        void shouldRecalculateProgress() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.FUN, 1);
            goal.setId(10L);
            goal.setCompletedRelatedMissions(5);
            // FUN has 10 estimated missions, so 5/10 * 100 = 50%

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

            Goal result = goalService.updateProgress(10L);

            assertThat(result.getProgressPercentage()).isEqualTo(50);
        }

        @Test
        @DisplayName("should cap progress at 100")
        void shouldCapProgressAt100() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.FUN, 1);
            goal.setId(10L);
            goal.setCompletedRelatedMissions(15); // 15/10 * 100 = 150 → capped at 100

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

            Goal result = goalService.updateProgress(10L);

            assertThat(result.getProgressPercentage()).isEqualTo(100);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> goalService.updateProgress(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Goal");
        }
    }

    @Nested
    @DisplayName("incrementCompletedMissions")
    class IncrementCompletedMissions {

        @Test
        @DisplayName("should increment completed missions and recalculate progress")
        void shouldIncrementAndRecalculate() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.CONNECTION, 1);
            goal.setId(10L);
            goal.setCompletedRelatedMissions(2);
            // CONNECTION has 15 estimated missions, after increment: 3/15 * 100 = 20%

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

            Goal result = goalService.incrementCompletedMissions(10L);

            assertThat(result.getCompletedRelatedMissions()).isEqualTo(3);
            assertThat(result.getProgressPercentage()).isEqualTo(20);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> goalService.incrementCompletedMissions(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Goal");
        }
    }

    @Nested
    @DisplayName("completeGoal")
    class CompleteGoal {

        @Test
        @DisplayName("should mark goal as COMPLETED with timestamp and 100% progress")
        void shouldCompleteGoal() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.EDUCATION, 2);
            goal.setId(10L);
            goal.setProgressPercentage(80);

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

            Goal result = goalService.completeGoal(10L);

            assertThat(result.getStatus()).isEqualTo("COMPLETED");
            assertThat(result.getProgressPercentage()).isEqualTo(100);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw BusinessRuleViolationException when goal is not ACTIVE")
        void shouldThrowWhenGoalNotActive() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.FUN, 1);
            goal.setId(10L);
            goal.setStatus("COMPLETED");

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

            assertThatThrownBy(() -> goalService.completeGoal(10L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("GOAL_NOT_ACTIVE");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> goalService.completeGoal(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Goal");
        }
    }

    @Nested
    @DisplayName("archiveGoal")
    class ArchiveGoal {

        @Test
        @DisplayName("should set status to ARCHIVED")
        void shouldArchiveGoal() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.DISCIPLINE, 3);
            goal.setId(10L);

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

            Goal result = goalService.archiveGoal(10L);

            assertThat(result.getStatus()).isEqualTo("ARCHIVED");
        }

        @Test
        @DisplayName("should allow archiving a COMPLETED goal")
        void shouldAllowArchivingCompletedGoal() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.FUN, 1);
            goal.setId(10L);
            goal.setStatus("COMPLETED");

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

            Goal result = goalService.archiveGoal(10L);

            assertThat(result.getStatus()).isEqualTo("ARCHIVED");
        }

        @Test
        @DisplayName("should throw when goal is already archived")
        void shouldThrowWhenAlreadyArchived() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.HEALTH, 2);
            goal.setId(10L);
            goal.setStatus("ARCHIVED");

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

            assertThatThrownBy(() -> goalService.archiveGoal(10L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("GOAL_ALREADY_ARCHIVED");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> goalService.archiveGoal(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Goal");
        }
    }

    @Nested
    @DisplayName("getActiveGoals")
    class GetActiveGoals {

        @Test
        @DisplayName("should return active goals ordered by priority")
        void shouldReturnActiveGoalsOrderedByPriority() {
            Goal goal1 = new Goal(father, "Goal 1", GoalCategory.CONNECTION, 2);
            Goal goal2 = new Goal(father, "Goal 2", GoalCategory.FUN, 1);

            when(goalRepository.findTop5ByFatherIdAndStatusOrderByPriorityAsc(1L, "ACTIVE"))
                    .thenReturn(List.of(goal2, goal1));

            List<Goal> result = goalService.getActiveGoals(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPriority()).isEqualTo(1);
            assertThat(result.get(1).getPriority()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list when no active goals")
        void shouldReturnEmptyListWhenNoActiveGoals() {
            when(goalRepository.findTop5ByFatherIdAndStatusOrderByPriorityAsc(1L, "ACTIVE"))
                    .thenReturn(List.of());

            List<Goal> result = goalService.getActiveGoals(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getGoal")
    class GetGoal {

        @Test
        @DisplayName("should return goal when found")
        void shouldReturnGoalWhenFound() {
            Goal goal = new Goal(father, "Test Goal", GoalCategory.EMOTIONAL, 1);
            goal.setId(10L);

            when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

            Goal result = goalService.getGoal(10L);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getTitle()).isEqualTo("Test Goal");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> goalService.getGoal(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Goal");
        }
    }
}
