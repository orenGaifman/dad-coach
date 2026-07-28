package com.dadcoach.workspace.growth.belt;

import com.dadcoach.workspace.dto.response.BeltProgressionResponse;
import com.dadcoach.workspace.event.BeltLevelUpEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BeltProgressionServiceImpl")
class BeltProgressionServiceImplTest {

    @Mock
    private FatherBeltRepository fatherBeltRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BeltProgressionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BeltProgressionServiceImpl(fatherBeltRepository, eventPublisher);
    }

    private FatherBelt createBelt(UUID fatherId, BeltLevel beltLevel, int score) {
        FatherBelt belt = new FatherBelt(fatherId);
        belt.setBeltLevel(beltLevel);
        belt.setCurrentScore(score);
        return belt;
    }

    @Nested
    @DisplayName("getCurrentBelt")
    class GetCurrentBeltTests {

        @Test
        @DisplayName("returns existing belt record")
        void returnsExistingBelt() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt existing = createBelt(fatherId, BeltLevel.GREEN, 500);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(existing));

            FatherBelt result = service.getCurrentBelt(fatherId);

            assertThat(result).isSameAs(existing);
            verify(fatherBeltRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates default WHITE belt if none exists")
        void createsDefaultWhiteBelt() {
            UUID fatherId = UUID.randomUUID();
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.empty());
            when(fatherBeltRepository.save(any(FatherBelt.class))).thenAnswer(inv -> inv.getArgument(0));

            FatherBelt result = service.getCurrentBelt(fatherId);

            assertThat(result.getFatherId()).isEqualTo(fatherId);
            assertThat(result.getBeltLevel()).isEqualTo(BeltLevel.WHITE);
            assertThat(result.getCurrentScore()).isEqualTo(0);
            verify(fatherBeltRepository).save(any(FatherBelt.class));
        }
    }

    @Nested
    @DisplayName("getProgression")
    class GetProgressionTests {

        @Test
        @DisplayName("returns correct progression for WHITE belt")
        void whiteBeltProgression() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.WHITE, 50);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            BeltProgressionResponse response = service.getProgression(fatherId);

            assertThat(response.getCurrentBelt()).isEqualTo("WHITE");
            assertThat(response.getCurrentScore()).isEqualTo(50);
            assertThat(response.getNextBelt()).isEqualTo("YELLOW");
            assertThat(response.getPointsToNextBelt()).isEqualTo(50); // 100 - 50
            assertThat(response.getProgressPercentageToNextBelt()).isEqualTo(50); // 50/100 * 100
        }

        @Test
        @DisplayName("returns correct progression for BLACK belt (max)")
        void blackBeltProgression() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.BLACK, 1500);
            belt.setBeltEarnedAt(Instant.now());
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            BeltProgressionResponse response = service.getProgression(fatherId);

            assertThat(response.getCurrentBelt()).isEqualTo("BLACK");
            assertThat(response.getCurrentScore()).isEqualTo(1500);
            assertThat(response.getNextBelt()).isNull();
            assertThat(response.getPointsToNextBelt()).isEqualTo(0);
            assertThat(response.getProgressPercentageToNextBelt()).isEqualTo(100);
            assertThat(response.getBeltEarnedAt()).isNotNull();
        }

        @Test
        @DisplayName("returns correct progression at belt threshold boundary")
        void thresholdBoundaryProgression() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.YELLOW, 249);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            BeltProgressionResponse response = service.getProgression(fatherId);

            assertThat(response.getCurrentBelt()).isEqualTo("YELLOW");
            assertThat(response.getNextBelt()).isEqualTo("ORANGE");
            assertThat(response.getPointsToNextBelt()).isEqualTo(1); // 250 - 249
        }
    }

    @Nested
    @DisplayName("evaluatePromotion")
    class EvaluatePromotionTests {

        @Test
        @DisplayName("returns new belt when score crosses threshold upward")
        void promotesWhenScoreCrossesThreshold() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.WHITE, 50);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            Optional<BeltLevel> result = service.evaluatePromotion(fatherId, 100);

            assertThat(result).contains(BeltLevel.YELLOW);
        }

        @Test
        @DisplayName("returns empty when score stays within current belt range")
        void noPromotionWithinSameRange() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.YELLOW, 150);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            Optional<BeltLevel> result = service.evaluatePromotion(fatherId, 200);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("enforces monotonicity — no downgrade when score drops below threshold")
        void noDowngradeOnScoreDrop() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.GREEN, 500);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            // Score dropped to YELLOW range, but belt should NOT downgrade
            Optional<BeltLevel> result = service.evaluatePromotion(fatherId, 200);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("handles multi-level jump (WHITE to GREEN)")
        void multiLevelJump() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.WHITE, 0);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            Optional<BeltLevel> result = service.evaluatePromotion(fatherId, 500);

            assertThat(result).contains(BeltLevel.GREEN);
        }

        @Test
        @DisplayName("returns promotion for father with no belt record when score above WHITE")
        void promotesNewFatherAboveWhite() {
            UUID fatherId = UUID.randomUUID();
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.empty());

            Optional<BeltLevel> result = service.evaluatePromotion(fatherId, 250);

            assertThat(result).contains(BeltLevel.ORANGE);
        }

        @Test
        @DisplayName("returns empty for father with no belt record and score in WHITE range")
        void noPromotionForNewFatherInWhiteRange() {
            UUID fatherId = UUID.randomUUID();
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.empty());

            Optional<BeltLevel> result = service.evaluatePromotion(fatherId, 50);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("BLACK belt never gets promotion")
        void blackBeltNeverPromotes() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.BLACK, 1500);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            Optional<BeltLevel> result = service.evaluatePromotion(fatherId, 2000);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("promoteBelt")
    class PromoteBeltTests {

        @Test
        @DisplayName("updates belt level and emits BeltLevelUpEvent")
        void promotesAndEmitsEvent() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.WHITE, 100);
            when(fatherBeltRepository.findByFatherIdForUpdate(fatherId)).thenReturn(Optional.of(belt));
            when(fatherBeltRepository.save(any(FatherBelt.class))).thenAnswer(inv -> inv.getArgument(0));

            service.promoteBelt(fatherId, BeltLevel.YELLOW);

            assertThat(belt.getBeltLevel()).isEqualTo(BeltLevel.YELLOW);
            assertThat(belt.getBeltEarnedAt()).isNotNull();
            verify(fatherBeltRepository).save(belt);

            ArgumentCaptor<BeltLevelUpEvent> eventCaptor = ArgumentCaptor.forClass(BeltLevelUpEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            BeltLevelUpEvent event = eventCaptor.getValue();
            assertThat(event.getFatherId()).isEqualTo(fatherId);
            assertThat(event.getPreviousBelt()).isEqualTo(BeltLevel.WHITE);
            assertThat(event.getNewBelt()).isEqualTo(BeltLevel.YELLOW);
            assertThat(event.getCurrentScore()).isEqualTo(100);
        }

        @Test
        @DisplayName("throws IllegalStateException on downgrade attempt")
        void throwsOnDowngrade() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.GREEN, 500);
            when(fatherBeltRepository.findByFatherIdForUpdate(fatherId)).thenReturn(Optional.of(belt));

            assertThatThrownBy(() -> service.promoteBelt(fatherId, BeltLevel.YELLOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("monotonicity");

            verify(fatherBeltRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("throws IllegalStateException on same-level assignment")
        void throwsOnSameLevel() {
            UUID fatherId = UUID.randomUUID();
            FatherBelt belt = createBelt(fatherId, BeltLevel.GREEN, 500);
            when(fatherBeltRepository.findByFatherIdForUpdate(fatherId)).thenReturn(Optional.of(belt));

            assertThatThrownBy(() -> service.promoteBelt(fatherId, BeltLevel.GREEN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("monotonicity");
        }

        @Test
        @DisplayName("creates belt record if none exists and promotes above WHITE")
        void createsRecordAndPromotes() {
            UUID fatherId = UUID.randomUUID();
            when(fatherBeltRepository.findByFatherIdForUpdate(fatherId)).thenReturn(Optional.empty());
            when(fatherBeltRepository.save(any(FatherBelt.class))).thenAnswer(inv -> inv.getArgument(0));

            service.promoteBelt(fatherId, BeltLevel.ORANGE);

            ArgumentCaptor<FatherBelt> beltCaptor = ArgumentCaptor.forClass(FatherBelt.class);
            verify(fatherBeltRepository, atLeast(1)).save(beltCaptor.capture());
            FatherBelt saved = beltCaptor.getAllValues().get(beltCaptor.getAllValues().size() - 1);
            assertThat(saved.getBeltLevel()).isEqualTo(BeltLevel.ORANGE);
            assertThat(saved.getBeltEarnedAt()).isNotNull();

            verify(eventPublisher).publishEvent(any(BeltLevelUpEvent.class));
        }
    }
}
