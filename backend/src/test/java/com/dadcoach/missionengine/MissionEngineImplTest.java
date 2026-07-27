package com.dadcoach.missionengine;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;
import com.dadcoach.father.CoachingPhase;
import com.dadcoach.mission.MissionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionEngineImplTest {

    @Mock
    private MissionRepository missionRepository;
    @Mock
    private FatherRepository fatherRepository;
    @Mock
    private ChildRepository childRepository;

    @InjectMocks
    private MissionEngineImpl missionEngine;

    private Father father;
    private Child child1;
    private Child child2;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        father.setId(1L);
        father.setCoachingPhase(CoachingPhase.FOUNDATION);

        child1 = new Child(father, "Yonatan", LocalDate.of(2018, 3, 15));
        child1.setId(10L);
        child1.setFatherId(1L);

        child2 = new Child(father, "Noam", LocalDate.of(2020, 7, 20));
        child2.setId(11L);
        child2.setFatherId(1L);
    }

    // ─── Difficulty Bounds Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("getDifficultyBounds")
    class DifficultyBoundsTests {

        @Test
        @DisplayName("FOUNDATION phase has bounds [1, 2]")
        void foundationBounds() {
            int[] bounds = missionEngine.getDifficultyBounds(CoachingPhase.FOUNDATION);
            assertThat(bounds).containsExactly(1, 2);
        }

        @Test
        @DisplayName("BUILDING phase has bounds [1, 3]")
        void buildingBounds() {
            int[] bounds = missionEngine.getDifficultyBounds(CoachingPhase.BUILDING);
            assertThat(bounds).containsExactly(1, 3);
        }

        @Test
        @DisplayName("DEEPENING phase has bounds [2, 4]")
        void deepeningBounds() {
            int[] bounds = missionEngine.getDifficultyBounds(CoachingPhase.DEEPENING);
            assertThat(bounds).containsExactly(2, 4);
        }

        @Test
        @DisplayName("MASTERY phase has bounds [2, 5]")
        void masteryBounds() {
            int[] bounds = missionEngine.getDifficultyBounds(CoachingPhase.MASTERY);
            assertThat(bounds).containsExactly(2, 5);
        }
    }

    // ─── clampDifficulty Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("clampDifficulty")
    class ClampDifficultyTests {

        @Test
        @DisplayName("Clamps difficulty below phase min to min")
        void clampsBelowMin() {
            assertThat(missionEngine.clampDifficulty(1, CoachingPhase.DEEPENING)).isEqualTo(2);
        }

        @Test
        @DisplayName("Clamps difficulty above phase max to max")
        void clampsAboveMax() {
            assertThat(missionEngine.clampDifficulty(5, CoachingPhase.FOUNDATION)).isEqualTo(2);
        }

        @Test
        @DisplayName("Does not clamp difficulty within bounds")
        void withinBounds() {
            assertThat(missionEngine.clampDifficulty(2, CoachingPhase.BUILDING)).isEqualTo(2);
        }
    }

    // ─── Difficulty Adaptation Tests ─────────────────────────────────────

    @Nested
    @DisplayName("adaptDifficulty")
    class AdaptDifficultyTests {

        @Test
        @DisplayName("Rating 4-5 increases difficulty by 1, capped at phase max")
        void ratingHighIncreasesDifficulty() {
            father.setCoachingPhase(CoachingPhase.BUILDING); // bounds [1, 3]
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(missionRepository.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            Mission completedMission = createCompletedMission(4);
            when(missionRepository.findRecentCompletedByChildId(10L, 1))
                    .thenReturn(List.of(completedMission));

            int adapted = missionEngine.adaptDifficulty(1L, 10L, 2);
            assertThat(adapted).isEqualTo(3); // 2 + 1 = 3, within [1, 3]
        }

        @Test
        @DisplayName("Rating 4-5 does not exceed phase max")
        void ratingHighCappedAtMax() {
            father.setCoachingPhase(CoachingPhase.FOUNDATION); // bounds [1, 2]
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(missionRepository.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            Mission completedMission = createCompletedMission(5);
            when(missionRepository.findRecentCompletedByChildId(10L, 1))
                    .thenReturn(List.of(completedMission));

            int adapted = missionEngine.adaptDifficulty(1L, 10L, 2);
            assertThat(adapted).isEqualTo(2); // 2 + 1 = 3, capped at 2
        }

        @Test
        @DisplayName("Rating 1-2 decreases difficulty by 1, minimum 1")
        void ratingLowDecreasesDifficulty() {
            father.setCoachingPhase(CoachingPhase.BUILDING); // bounds [1, 3]
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(missionRepository.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            Mission completedMission = createCompletedMission(1);
            when(missionRepository.findRecentCompletedByChildId(10L, 1))
                    .thenReturn(List.of(completedMission));

            int adapted = missionEngine.adaptDifficulty(1L, 10L, 2);
            assertThat(adapted).isEqualTo(1); // 2 - 1 = 1
        }

        @Test
        @DisplayName("Rating 3 keeps difficulty unchanged")
        void ratingNeutralKeepsSame() {
            father.setCoachingPhase(CoachingPhase.BUILDING); // bounds [1, 3]
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(missionRepository.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            Mission completedMission = createCompletedMission(3);
            when(missionRepository.findRecentCompletedByChildId(10L, 1))
                    .thenReturn(List.of(completedMission));

            int adapted = missionEngine.adaptDifficulty(1L, 10L, 2);
            assertThat(adapted).isEqualTo(2); // unchanged
        }

        @Test
        @DisplayName("3 consecutive skips reduces difficulty by 1")
        void threeConsecutiveSkipsReducesDifficulty() {
            father.setCoachingPhase(CoachingPhase.BUILDING); // bounds [1, 3]
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));

            // Create 3 skipped missions
            List<Mission> skippedMissions = List.of(
                    createMissionWithStatus(MissionStatus.SKIPPED),
                    createMissionWithStatus(MissionStatus.SKIPPED),
                    createMissionWithStatus(MissionStatus.SKIPPED)
            );
            when(missionRepository.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(skippedMissions);

            int adapted = missionEngine.adaptDifficulty(1L, 10L, 3);
            assertThat(adapted).isEqualTo(2); // 3 - 1 = 2
        }

        @Test
        @DisplayName("3 consecutive expired reduces difficulty by 1")
        void threeConsecutiveExpiredReducesDifficulty() {
            father.setCoachingPhase(CoachingPhase.MASTERY); // bounds [2, 5]
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));

            List<Mission> expiredMissions = List.of(
                    createMissionWithStatus(MissionStatus.EXPIRED),
                    createMissionWithStatus(MissionStatus.EXPIRED),
                    createMissionWithStatus(MissionStatus.EXPIRED)
            );
            when(missionRepository.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(expiredMissions);

            int adapted = missionEngine.adaptDifficulty(1L, 10L, 3);
            assertThat(adapted).isEqualTo(2); // 3 - 1 = 2, within [2, 5]
        }

        @Test
        @DisplayName("Difficulty never goes below 1 after adaptation")
        void difficultyNeverBelowOne() {
            father.setCoachingPhase(CoachingPhase.BUILDING); // bounds [1, 3]
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(missionRepository.findRecentByChildIdSince(eq(10L), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            Mission completedMission = createCompletedMission(1);
            when(missionRepository.findRecentCompletedByChildId(10L, 1))
                    .thenReturn(List.of(completedMission));

            int adapted = missionEngine.adaptDifficulty(1L, 10L, 1);
            assertThat(adapted).isEqualTo(1); // max(1-1, 1) = 1, within [1, 3]
        }

        @Test
        @DisplayName("Father not found throws ResourceNotFoundException")
        void fatherNotFoundThrows() {
            when(fatherRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionEngine.adaptDifficulty(99L, 10L, 2))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── Category Non-Repetition Tests ───────────────────────────────────

    @Nested
    @DisplayName("validateCategoryNonRepetition")
    class CategoryNonRepetitionTests {

        @Test
        @DisplayName("Returns true when category count is 0 in 7 days")
        void allowsWhenZero() {
            when(missionRepository.countByChildIdAndCategorySince(eq(10L), eq("CONNECTION"), any(Instant.class)))
                    .thenReturn(0L);

            assertThat(missionEngine.validateCategoryNonRepetition(10L, "CONNECTION")).isTrue();
        }

        @Test
        @DisplayName("Returns true when category count is 1 in 7 days")
        void allowsWhenOne() {
            when(missionRepository.countByChildIdAndCategorySince(eq(10L), eq("CONNECTION"), any(Instant.class)))
                    .thenReturn(1L);

            assertThat(missionEngine.validateCategoryNonRepetition(10L, "CONNECTION")).isTrue();
        }

        @Test
        @DisplayName("Returns false when category count is 2 in 7 days")
        void rejectsWhenTwo() {
            when(missionRepository.countByChildIdAndCategorySince(eq(10L), eq("CONNECTION"), any(Instant.class)))
                    .thenReturn(2L);

            assertThat(missionEngine.validateCategoryNonRepetition(10L, "CONNECTION")).isFalse();
        }

        @Test
        @DisplayName("Returns false when category count exceeds 2 in 7 days")
        void rejectsWhenMoreThanTwo() {
            when(missionRepository.countByChildIdAndCategorySince(eq(10L), eq("HEALTH"), any(Instant.class)))
                    .thenReturn(3L);

            assertThat(missionEngine.validateCategoryNonRepetition(10L, "HEALTH")).isFalse();
        }
    }

    // ─── Equitable Distribution Tests ────────────────────────────────────

    @Nested
    @DisplayName("isDistributionEquitable")
    class EquitableDistributionTests {

        @Test
        @DisplayName("Single child is always equitable")
        void singleChildEquitable() {
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1));

            assertThat(missionEngine.isDistributionEquitable(1L, 7)).isTrue();
        }

        @Test
        @DisplayName("No children is always equitable")
        void noChildrenEquitable() {
            when(childRepository.findByFatherId(1L)).thenReturn(Collections.emptyList());

            assertThat(missionEngine.isDistributionEquitable(1L, 7)).isTrue();
        }

        @Test
        @DisplayName("Equal distribution across 2 children is equitable")
        void equalDistributionEquitable() {
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, child2));
            when(missionRepository.countMissionsByChildIdSince(eq(10L), any(Instant.class))).thenReturn(3L);
            when(missionRepository.countMissionsByChildIdSince(eq(11L), any(Instant.class))).thenReturn(3L);

            assertThat(missionEngine.isDistributionEquitable(1L, 7)).isTrue();
        }

        @Test
        @DisplayName("Distribution with one child getting floor(total/N)-1 is equitable")
        void thresholdDistributionEquitable() {
            // Total = 5, N = 2, floor(5/2) - 1 = 1
            // Child1 has 4 missions, child2 has 1 mission — child2 meets threshold
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, child2));
            when(missionRepository.countMissionsByChildIdSince(eq(10L), any(Instant.class))).thenReturn(4L);
            when(missionRepository.countMissionsByChildIdSince(eq(11L), any(Instant.class))).thenReturn(1L);

            assertThat(missionEngine.isDistributionEquitable(1L, 7)).isTrue();
        }

        @Test
        @DisplayName("Distribution below threshold is NOT equitable")
        void belowThresholdNotEquitable() {
            // Total = 6, N = 2, floor(6/2) - 1 = 2
            // Child1 has 5 missions, child2 has 1 mission — child2 below threshold (1 < 2)
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, child2));
            when(missionRepository.countMissionsByChildIdSince(eq(10L), any(Instant.class))).thenReturn(5L);
            when(missionRepository.countMissionsByChildIdSince(eq(11L), any(Instant.class))).thenReturn(1L);

            assertThat(missionEngine.isDistributionEquitable(1L, 7)).isFalse();
        }

        @Test
        @DisplayName("Zero total missions is equitable")
        void zeroTotalMissionsEquitable() {
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, child2));
            when(missionRepository.countMissionsByChildIdSince(eq(10L), any(Instant.class))).thenReturn(0L);
            when(missionRepository.countMissionsByChildIdSince(eq(11L), any(Instant.class))).thenReturn(0L);

            assertThat(missionEngine.isDistributionEquitable(1L, 7)).isTrue();
        }
    }

    // ─── Child Selection Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("selectNextChild")
    class SelectNextChildTests {

        @Test
        @DisplayName("Single child is always selected")
        void singleChildSelected() {
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1));

            Long selected = missionEngine.selectNextChild(1L);
            assertThat(selected).isEqualTo(10L);
        }

        @Test
        @DisplayName("Child with fewer missions in 7 days is selected")
        void fewerMissionsSelected() {
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, child2));
            when(missionRepository.countMissionsByChildIdSince(eq(10L), any(Instant.class))).thenReturn(3L);
            when(missionRepository.countMissionsByChildIdSince(eq(11L), any(Instant.class))).thenReturn(1L);
            when(missionRepository.findMostRecentByChildId(10L)).thenReturn(Collections.emptyList());
            when(missionRepository.findMostRecentByChildId(11L)).thenReturn(Collections.emptyList());

            Long selected = missionEngine.selectNextChild(1L);
            assertThat(selected).isEqualTo(11L); // child2 has fewer missions
        }

        @Test
        @DisplayName("On tie, child with longest time since last mission is selected")
        void tiebreakByLongestSinceLast() {
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, child2));
            when(missionRepository.countMissionsByChildIdSince(eq(10L), any(Instant.class))).thenReturn(2L);
            when(missionRepository.countMissionsByChildIdSince(eq(11L), any(Instant.class))).thenReturn(2L);

            // child1's last mission was 3 days ago, child2's was 1 day ago
            Mission child1LastMission = new Mission(father, child1, "T1", "D1", "C1", 1, 10);
            child1LastMission.setAssignedAt(Instant.now().minus(3, ChronoUnit.DAYS));
            Mission child2LastMission = new Mission(father, child2, "T2", "D2", "C2", 1, 10);
            child2LastMission.setAssignedAt(Instant.now().minus(1, ChronoUnit.DAYS));

            when(missionRepository.findMostRecentByChildId(10L)).thenReturn(List.of(child1LastMission));
            when(missionRepository.findMostRecentByChildId(11L)).thenReturn(List.of(child2LastMission));

            Long selected = missionEngine.selectNextChild(1L);
            assertThat(selected).isEqualTo(10L); // child1 has longer since last mission
        }

        @Test
        @DisplayName("Child with no missions ever is preferred (longest since last = EPOCH)")
        void noMissionsEverPreferred() {
            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, child2));
            when(missionRepository.countMissionsByChildIdSince(eq(10L), any(Instant.class))).thenReturn(0L);
            when(missionRepository.countMissionsByChildIdSince(eq(11L), any(Instant.class))).thenReturn(0L);

            // child1 has no missions, child2 has one recent mission
            when(missionRepository.findMostRecentByChildId(10L)).thenReturn(Collections.emptyList());
            Mission child2Mission = new Mission(father, child2, "T2", "D2", "C2", 1, 10);
            child2Mission.setAssignedAt(Instant.now().minus(1, ChronoUnit.DAYS));
            when(missionRepository.findMostRecentByChildId(11L)).thenReturn(List.of(child2Mission));

            Long selected = missionEngine.selectNextChild(1L);
            assertThat(selected).isEqualTo(10L); // child1 has EPOCH (longest since last)
        }

        @Test
        @DisplayName("Throws when no active children")
        void noActiveChildrenThrows() {
            when(childRepository.findByFatherId(1L)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> missionEngine.selectNextChild(1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("no active children");
        }

        @Test
        @DisplayName("Archived children are excluded from selection")
        void archivedChildrenExcluded() {
            Child archivedChild = new Child(father, "Archived", LocalDate.of(2019, 1, 1));
            archivedChild.setId(12L);
            archivedChild.setFatherId(1L);
            archivedChild.setStatus("ARCHIVED");

            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, archivedChild));
            // Only child1 is active

            Long selected = missionEngine.selectNextChild(1L);
            assertThat(selected).isEqualTo(10L);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Mission createCompletedMission(int rating) {
        Mission mission = new Mission(father, child1, "Test", "Desc", "CONNECTION", 2, 15);
        mission.setId(100L);
        mission.setStatus(MissionStatus.COMPLETED);
        mission.setOutcomeRating(rating);
        mission.setCompletedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return mission;
    }

    private Mission createMissionWithStatus(MissionStatus status) {
        Mission mission = new Mission(father, child1, "Test", "Desc", "CONNECTION", 2, 15);
        mission.setId((long) (Math.random() * 1000));
        mission.setStatus(status);
        mission.setAssignedAt(Instant.now().minus((long) (Math.random() * 7), ChronoUnit.DAYS));
        return mission;
    }
}
