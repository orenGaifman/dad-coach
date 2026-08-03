package com.dadcoach.qualitytime;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.dto.CompleteQualityTimeResult;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.metrics.WorkflowMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QualityTimeServiceImpl.
 * 
 * Validates: Requirements 7.2, 8.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QualityTimeServiceImpl")
class QualityTimeServiceImplTest {

    @Mock
    private QualityTimeRepository qualityTimeRepository;

    @Mock
    private FatherRepository fatherRepository;

    @Mock
    private ChildRepository childRepository;

    @Mock
    private WorkflowMetrics workflowMetrics;

    private QualityTimeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QualityTimeServiceImpl(qualityTimeRepository, fatherRepository, childRepository, workflowMetrics);
    }

    @Nested
    @DisplayName("completeQualityTime")
    class CompleteQualityTimeTest {

        private Father father;
        private Child child;
        private QualityTime qualityTime;
        private UUID qualityTimeId;

        @BeforeEach
        void setUp() {
            qualityTimeId = UUID.randomUUID();
            
            father = new Father("+1234567890");
            father.setId(1L);
            father.setDisplayName("Test Dad");
            father.setQualityTimeStreak(5);
            father.setQualityTimeLongestStreak(10);
            father.setTotalQualityTimesCompleted(8); // 9th completion -> stay YELLOW
            father.setCurrentBelt(Belt.YELLOW);

            child = new Child(father, "Test Child", LocalDate.of(2020, 1, 15));
            child.setId(1L);

            qualityTime = new QualityTime(
                    father,
                    child,
                    Instant.now().minusSeconds(3600),
                    Instant.now().minusSeconds(1800)
            );
            qualityTime.setId(qualityTimeId);
            qualityTime.setStatus(QualityTimeStatus.SCHEDULED);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when QualityTime not found")
        void throwsWhenQualityTimeNotFound() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.completeQualityTime(qualityTimeId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Quality Time not found");
        }

        @Test
        @DisplayName("throws IllegalStateException when QualityTime not in SCHEDULED status")
        void throwsWhenNotScheduledStatus() {
            qualityTime.setStatus(QualityTimeStatus.COMPLETED);
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            assertThatThrownBy(() -> service.completeQualityTime(qualityTimeId, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot complete Quality Time with status:");
        }

        @Test
        @DisplayName("marks QualityTime as COMPLETED with correct timestamp")
        void marksQualityTimeAsCompleted() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            Instant beforeCompletion = Instant.now();
            service.completeQualityTime(qualityTimeId, "Had a great time!");
            Instant afterCompletion = Instant.now();

            assertThat(qualityTime.getStatus()).isEqualTo(QualityTimeStatus.COMPLETED);
            assertThat(qualityTime.getCompletedAt())
                    .isNotNull()
                    .isAfterOrEqualTo(beforeCompletion)
                    .isBeforeOrEqualTo(afterCompletion);
            assertThat(qualityTime.getCompletionNotes()).isEqualTo("Had a great time!");
        }

        @Test
        @DisplayName("increments father's qualityTimeStreak")
        void incrementsStreak() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            CompleteQualityTimeResult result = service.completeQualityTime(qualityTimeId, null);

            assertThat(father.getQualityTimeStreak()).isEqualTo(6);
            assertThat(result.newStreak()).isEqualTo(6);
            assertThat(result.streakUpdated()).isTrue();
        }

        @Test
        @DisplayName("updates longestStreak when new streak exceeds it")
        void updatesLongestStreakWhenExceeded() {
            father.setQualityTimeStreak(10); // Will become 11, exceeding longest of 10
            father.setQualityTimeLongestStreak(10);

            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            service.completeQualityTime(qualityTimeId, null);

            assertThat(father.getQualityTimeStreak()).isEqualTo(11);
            assertThat(father.getQualityTimeLongestStreak()).isEqualTo(11);
        }

        @Test
        @DisplayName("does not update longestStreak when new streak does not exceed it")
        void doesNotUpdateLongestStreakWhenNotExceeded() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            service.completeQualityTime(qualityTimeId, null);

            assertThat(father.getQualityTimeStreak()).isEqualTo(6);
            assertThat(father.getQualityTimeLongestStreak()).isEqualTo(10); // Unchanged
        }

        @Test
        @DisplayName("increments totalQualityTimesCompleted")
        void incrementsTotalCompleted() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            service.completeQualityTime(qualityTimeId, null);

            assertThat(father.getTotalQualityTimesCompleted()).isEqualTo(9);
        }

        @Test
        @DisplayName("saves QualityTime and Father entities")
        void savesEntities() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            service.completeQualityTime(qualityTimeId, null);

            verify(qualityTimeRepository).save(qualityTime);
            verify(fatherRepository).save(father);
        }

        @Test
        @DisplayName("returns result without belt earned when belt unchanged")
        void returnsResultWithoutBeltEarnedWhenUnchanged() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            CompleteQualityTimeResult result = service.completeQualityTime(qualityTimeId, null);

            assertThat(result.qualityTimeId()).isEqualTo(qualityTimeId);
            assertThat(result.status()).isEqualTo(QualityTimeStatus.COMPLETED);
            assertThat(result.beltEarned()).isNull();
            assertThat(result.currentBelt()).isEqualTo(Belt.YELLOW);
        }

        /**
         * Belt Progression Tests - SACRED Belt System
         * Validates Requirement 8.5
         */
        @Nested
        @DisplayName("Belt progression (SACRED)")
        class BeltProgressionTest {

            @ParameterizedTest(name = "completion #{0} -> {1} belt (earned: {2})")
            @CsvSource({
                    // WHITE -> YELLOW at 3 completions
                    "3, YELLOW, true",
                    // Stay YELLOW
                    "5, YELLOW, false",
                    "9, YELLOW, false",
                    // YELLOW -> ORANGE at 10 completions
                    "10, ORANGE, true",
                    // Stay ORANGE
                    "15, ORANGE, false",
                    "24, ORANGE, false",
                    // ORANGE -> GREEN at 25 completions
                    "25, GREEN, true",
                    // Stay GREEN
                    "35, GREEN, false",
                    "49, GREEN, false",
                    // GREEN -> BLUE at 50 completions
                    "50, BLUE, true",
                    // Stay BLUE
                    "75, BLUE, false",
                    "99, BLUE, false",
                    // BLUE -> BROWN at 100 completions
                    "100, BROWN, true",
                    // Stay BROWN
                    "150, BROWN, false",
                    "199, BROWN, false",
                    // BROWN -> BLACK at 200 completions
                    "200, BLACK, true",
                    // Stay BLACK
                    "300, BLACK, false",
                    "500, BLACK, false"
            })
            @DisplayName("calculates belt correctly")
            void calculatesBeltCorrectly(int completionNumber, Belt expectedBelt, boolean beltEarned) {
                // Set up father at completion N-1
                int previousTotal = completionNumber - 1;
                Belt previousBelt = Belt.fromCompletionCount(previousTotal);
                father.setTotalQualityTimesCompleted(previousTotal);
                father.setCurrentBelt(previousBelt);

                when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

                CompleteQualityTimeResult result = service.completeQualityTime(qualityTimeId, null);

                assertThat(father.getCurrentBelt()).isEqualTo(expectedBelt);
                assertThat(result.currentBelt()).isEqualTo(expectedBelt);
                
                if (beltEarned) {
                    assertThat(result.beltEarned())
                            .as("Belt should be earned at completion %d", completionNumber)
                            .isEqualTo(expectedBelt);
                } else {
                    assertThat(result.beltEarned())
                            .as("No belt should be earned at completion %d", completionNumber)
                            .isNull();
                }
            }

            @Test
            @DisplayName("returns beltEarned when crossing WHITE to YELLOW threshold")
            void returnsBeltEarnedAtYellowThreshold() {
                father.setTotalQualityTimesCompleted(2); // Will become 3 -> YELLOW
                father.setCurrentBelt(Belt.WHITE);

                when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

                CompleteQualityTimeResult result = service.completeQualityTime(qualityTimeId, null);

                assertThat(result.beltEarned()).isEqualTo(Belt.YELLOW);
                assertThat(result.currentBelt()).isEqualTo(Belt.YELLOW);
                assertThat(father.getCurrentBelt()).isEqualTo(Belt.YELLOW);
            }

            @Test
            @DisplayName("returns beltEarned when crossing to BLACK (highest belt)")
            void returnsBeltEarnedAtBlackThreshold() {
                father.setTotalQualityTimesCompleted(199); // Will become 200 -> BLACK
                father.setCurrentBelt(Belt.BROWN);

                when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

                CompleteQualityTimeResult result = service.completeQualityTime(qualityTimeId, null);

                assertThat(result.beltEarned()).isEqualTo(Belt.BLACK);
                assertThat(result.currentBelt()).isEqualTo(Belt.BLACK);
                assertThat(father.getCurrentBelt()).isEqualTo(Belt.BLACK);
            }
        }

        @Test
        @DisplayName("handles null notes gracefully")
        void handlesNullNotes() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            service.completeQualityTime(qualityTimeId, null);

            assertThat(qualityTime.getCompletionNotes()).isNull();
        }

        @Test
        @DisplayName("stores completion notes when provided")
        void storesCompletionNotes() {
            String notes = "We played soccer and had a great time!";
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            service.completeQualityTime(qualityTimeId, notes);

            assertThat(qualityTime.getCompletionNotes()).isEqualTo(notes);
        }

        @Test
        @DisplayName("returns default points awarded")
        void returnsDefaultPointsAwarded() {
            when(qualityTimeRepository.findById(qualityTimeId)).thenReturn(Optional.of(qualityTime));

            CompleteQualityTimeResult result = service.completeQualityTime(qualityTimeId, null);

            assertThat(result.pointsAwarded()).isEqualTo(CompleteQualityTimeResult.DEFAULT_POINTS);
        }
    }
}
