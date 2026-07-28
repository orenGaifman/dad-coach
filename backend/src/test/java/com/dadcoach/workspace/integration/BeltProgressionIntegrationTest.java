package com.dadcoach.workspace.integration;

import com.dadcoach.workspace.growth.belt.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for Belt Progression end-to-end.
 *
 * <p>Verifies belt promotion logic including threshold crossing,
 * multi-level jumps, and monotonicity (AD-8).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("15.5 - Belt Progression End-to-End Integration")
class BeltProgressionIntegrationTest {

    @Mock
    private FatherBeltRepository fatherBeltRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BeltProgressionServiceImpl beltProgressionService;

    @BeforeEach
    void setUp() {
        beltProgressionService = new BeltProgressionServiceImpl(fatherBeltRepository, eventPublisher);
    }

    @Test
    @DisplayName("Score 100 points → evaluatePromotion → belt promoted from WHITE to YELLOW")
    void score100_promotesFromWhiteToYellow() {
        // Given
        UUID fatherId = UUID.randomUUID();
        FatherBelt belt = new FatherBelt(fatherId); // starts at WHITE, score 0
        belt.setCurrentScore(100);

        when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));
        when(fatherBeltRepository.save(any(FatherBelt.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When - evaluate promotion
        Optional<BeltLevel> promotionResult = beltProgressionService.evaluatePromotion(fatherId, 100);

        // Then - should suggest YELLOW
        assertThat(promotionResult).isPresent();
        assertThat(promotionResult.get()).isEqualTo(BeltLevel.YELLOW);

        // When - execute promotion
        beltProgressionService.promoteBelt(fatherId, BeltLevel.YELLOW);

        // Then - belt saved with YELLOW
        ArgumentCaptor<FatherBelt> beltCaptor = ArgumentCaptor.forClass(FatherBelt.class);
        verify(fatherBeltRepository, atLeastOnce()).save(beltCaptor.capture());

        FatherBelt savedBelt = beltCaptor.getAllValues().stream()
                .filter(b -> b.getBeltLevel() == BeltLevel.YELLOW)
                .findFirst()
                .orElse(null);
        assertThat(savedBelt).isNotNull();
        assertThat(savedBelt.getBeltLevel()).isEqualTo(BeltLevel.YELLOW);
        assertThat(savedBelt.getBeltEarnedAt()).isNotNull();

        // BeltLevelUpEvent published
        verify(eventPublisher).publishEvent(any(com.dadcoach.workspace.event.BeltLevelUpEvent.class));
    }

    @Test
    @DisplayName("Score 450 → belt jumps from WHITE to GREEN (multi-level)")
    void score450_jumpsFromWhiteToGreen() {
        // Given
        UUID fatherId = UUID.randomUUID();
        FatherBelt belt = new FatherBelt(fatherId); // starts at WHITE, score 0
        belt.setCurrentScore(450);

        when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));
        when(fatherBeltRepository.save(any(FatherBelt.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When - evaluate promotion with score 450
        Optional<BeltLevel> promotionResult = beltProgressionService.evaluatePromotion(fatherId, 450);

        // Then - should jump directly to GREEN (skipping YELLOW and ORANGE)
        assertThat(promotionResult).isPresent();
        assertThat(promotionResult.get()).isEqualTo(BeltLevel.GREEN);

        // Verify the belt for score 450 is indeed GREEN
        BeltLevel beltForScore = BeltThreshold.beltForScore(450);
        assertThat(beltForScore).isEqualTo(BeltLevel.GREEN);

        // Execute the promotion
        beltProgressionService.promoteBelt(fatherId, BeltLevel.GREEN);

        ArgumentCaptor<FatherBelt> beltCaptor = ArgumentCaptor.forClass(FatherBelt.class);
        verify(fatherBeltRepository, atLeastOnce()).save(beltCaptor.capture());

        boolean greenSaved = beltCaptor.getAllValues().stream()
                .anyMatch(b -> b.getBeltLevel() == BeltLevel.GREEN);
        assertThat(greenSaved).isTrue();
    }

    @Test
    @DisplayName("Score below current belt threshold → belt NOT downgraded (monotonicity AD-8)")
    void scoreBelowCurrentThreshold_beltNotDowngraded() {
        // Given - father already has YELLOW belt (requires 100-249), score drops hypothetically
        UUID fatherId = UUID.randomUUID();
        FatherBelt belt = new FatherBelt(fatherId);
        // Simulate already promoted to YELLOW
        belt.setBeltLevel(BeltLevel.YELLOW);
        belt.setCurrentScore(50); // below YELLOW threshold

        when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));
        when(fatherBeltRepository.findByFatherIdForUpdate(fatherId)).thenReturn(Optional.of(belt));

        // When - evaluate with score below current belt
        Optional<BeltLevel> promotionResult = beltProgressionService.evaluatePromotion(fatherId, 50);

        // Then - no promotion (score 50 → WHITE, but current belt is YELLOW → monotonicity)
        assertThat(promotionResult).isEmpty();

        // Also verify promoteBelt refuses to downgrade
        assertThatThrownBy(() -> beltProgressionService.promoteBelt(fatherId, BeltLevel.WHITE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("monotonicity");
    }

    @Test
    @DisplayName("BeltThreshold.beltForScore correctly maps score ranges")
    void beltForScore_correctMappings() {
        // WHITE: 0-99
        assertThat(BeltThreshold.beltForScore(0)).isEqualTo(BeltLevel.WHITE);
        assertThat(BeltThreshold.beltForScore(99)).isEqualTo(BeltLevel.WHITE);

        // YELLOW: 100-249
        assertThat(BeltThreshold.beltForScore(100)).isEqualTo(BeltLevel.YELLOW);
        assertThat(BeltThreshold.beltForScore(249)).isEqualTo(BeltLevel.YELLOW);

        // ORANGE: 250-449
        assertThat(BeltThreshold.beltForScore(250)).isEqualTo(BeltLevel.ORANGE);
        assertThat(BeltThreshold.beltForScore(449)).isEqualTo(BeltLevel.ORANGE);

        // GREEN: 450-699
        assertThat(BeltThreshold.beltForScore(450)).isEqualTo(BeltLevel.GREEN);
        assertThat(BeltThreshold.beltForScore(699)).isEqualTo(BeltLevel.GREEN);

        // BLUE: 700-899
        assertThat(BeltThreshold.beltForScore(700)).isEqualTo(BeltLevel.BLUE);

        // BLACK: 1200+
        assertThat(BeltThreshold.beltForScore(1200)).isEqualTo(BeltLevel.BLACK);
        assertThat(BeltThreshold.beltForScore(5000)).isEqualTo(BeltLevel.BLACK);
    }
}
