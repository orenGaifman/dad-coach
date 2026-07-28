package com.dadcoach.workspace.growth.belt;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BeltLevel} enum.
 */
class BeltLevelTest {

    @Test
    void shouldHaveEightBeltLevelsInAscendingOrder() {
        BeltLevel[] levels = BeltLevel.values();
        assertThat(levels).hasSize(8);
        assertThat(levels[0]).isEqualTo(BeltLevel.WHITE);
        assertThat(levels[1]).isEqualTo(BeltLevel.YELLOW);
        assertThat(levels[2]).isEqualTo(BeltLevel.ORANGE);
        assertThat(levels[3]).isEqualTo(BeltLevel.GREEN);
        assertThat(levels[4]).isEqualTo(BeltLevel.BLUE);
        assertThat(levels[5]).isEqualTo(BeltLevel.PURPLE);
        assertThat(levels[6]).isEqualTo(BeltLevel.BROWN);
        assertThat(levels[7]).isEqualTo(BeltLevel.BLACK);
    }

    @Test
    void shouldReturnCorrectDescriptions() {
        assertThat(BeltLevel.WHITE.getDescription()).isEqualTo("Getting Started");
        assertThat(BeltLevel.YELLOW.getDescription()).isEqualTo("Building Habits");
        assertThat(BeltLevel.ORANGE.getDescription()).isEqualTo("Finding Rhythm");
        assertThat(BeltLevel.GREEN.getDescription()).isEqualTo("Growing Strong");
        assertThat(BeltLevel.BLUE.getDescription()).isEqualTo("Deep Connection");
        assertThat(BeltLevel.PURPLE.getDescription()).isEqualTo("Advanced Father");
        assertThat(BeltLevel.BROWN.getDescription()).isEqualTo("Near Mastery");
        assertThat(BeltLevel.BLACK.getDescription()).isEqualTo("Master Father");
    }

    @Test
    void isHigherThan_shouldReturnTrueForHigherBelt() {
        assertThat(BeltLevel.YELLOW.isHigherThan(BeltLevel.WHITE)).isTrue();
        assertThat(BeltLevel.BLACK.isHigherThan(BeltLevel.BROWN)).isTrue();
        assertThat(BeltLevel.GREEN.isHigherThan(BeltLevel.ORANGE)).isTrue();
    }

    @Test
    void isHigherThan_shouldReturnFalseForSameBelt() {
        assertThat(BeltLevel.WHITE.isHigherThan(BeltLevel.WHITE)).isFalse();
        assertThat(BeltLevel.BLACK.isHigherThan(BeltLevel.BLACK)).isFalse();
    }

    @Test
    void isHigherThan_shouldReturnFalseForLowerBelt() {
        assertThat(BeltLevel.WHITE.isHigherThan(BeltLevel.YELLOW)).isFalse();
        assertThat(BeltLevel.BROWN.isHigherThan(BeltLevel.BLACK)).isFalse();
        assertThat(BeltLevel.ORANGE.isHigherThan(BeltLevel.GREEN)).isFalse();
    }

    @Test
    void next_shouldReturnNextBeltForNonMaxLevels() {
        assertThat(BeltLevel.WHITE.next()).isEqualTo(Optional.of(BeltLevel.YELLOW));
        assertThat(BeltLevel.YELLOW.next()).isEqualTo(Optional.of(BeltLevel.ORANGE));
        assertThat(BeltLevel.ORANGE.next()).isEqualTo(Optional.of(BeltLevel.GREEN));
        assertThat(BeltLevel.GREEN.next()).isEqualTo(Optional.of(BeltLevel.BLUE));
        assertThat(BeltLevel.BLUE.next()).isEqualTo(Optional.of(BeltLevel.PURPLE));
        assertThat(BeltLevel.PURPLE.next()).isEqualTo(Optional.of(BeltLevel.BROWN));
        assertThat(BeltLevel.BROWN.next()).isEqualTo(Optional.of(BeltLevel.BLACK));
    }

    @Test
    void next_shouldReturnEmptyForBlack() {
        assertThat(BeltLevel.BLACK.next()).isEmpty();
    }

    @Test
    void isMaxLevel_shouldReturnTrueOnlyForBlack() {
        assertThat(BeltLevel.BLACK.isMaxLevel()).isTrue();

        for (BeltLevel level : BeltLevel.values()) {
            if (level != BeltLevel.BLACK) {
                assertThat(level.isMaxLevel()).isFalse();
            }
        }
    }

    @Test
    void ordinalsShouldReflectAscendingOrder() {
        for (int i = 0; i < BeltLevel.values().length - 1; i++) {
            BeltLevel current = BeltLevel.values()[i];
            BeltLevel nextLevel = BeltLevel.values()[i + 1];
            assertThat(current.ordinal()).isLessThan(nextLevel.ordinal());
            assertThat(nextLevel.isHigherThan(current)).isTrue();
        }
    }
}
