package com.dadcoach.memory.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MemoryCapacityDto.
 *
 * <p>Validates: SPEC-004 Requirement 15 - Capacity Limits
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Capacity calculations are correct</li>
 *   <li>isAtCapacity returns true when count >= maxAllowed</li>
 *   <li>isNearCapacity returns true when usage >= 90%</li>
 *   <li>Available capacity is correctly calculated</li>
 *   <li>Usage percentage is formatted correctly</li>
 * </ul>
 */
@DisplayName("MemoryCapacityDto Tests")
class MemoryCapacityDtoTest {

    private static final UUID FATHER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("Capacity Calculation Tests")
    class CapacityCalculationTests {

        @Test
        @DisplayName("Constructor with default max sets maxAllowed to 500")
        void constructorWithDefaultMax() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 100);

            // Then
            assertThat(capacity.getMaxAllowed()).isEqualTo(500);
        }

        @Test
        @DisplayName("Constructor with custom max sets correct maxAllowed")
        void constructorWithCustomMax() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 50, 100);

            // Then
            assertThat(capacity.getMaxAllowed()).isEqualTo(100);
        }

        @Test
        @DisplayName("Available capacity is maxAllowed minus currentCount")
        void availableCapacityCalculation() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 350);

            // Then
            assertThat(capacity.getAvailableCapacity()).isEqualTo(150);
        }

        @Test
        @DisplayName("Available capacity is zero when at or over capacity")
        void availableCapacityZeroWhenAtCapacity() {
            // When
            MemoryCapacityDto atCapacity = new MemoryCapacityDto(FATHER_ID, 500);
            MemoryCapacityDto overCapacity = new MemoryCapacityDto(FATHER_ID, 550);

            // Then
            assertThat(atCapacity.getAvailableCapacity()).isEqualTo(0);
            assertThat(overCapacity.getAvailableCapacity()).isEqualTo(0);
        }

        @Test
        @DisplayName("Usage percentage is calculated correctly")
        void usagePercentageCalculation() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 250);

            // Then
            assertThat(capacity.getUsagePercentage()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("Usage percentage handles zero maxAllowed")
        void usagePercentageWithZeroMax() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 10, 0);

            // Then
            assertThat(capacity.getUsagePercentage()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Capacity Status Tests")
    class CapacityStatusTests {

        @Test
        @DisplayName("isAtCapacity returns false when under limit")
        void isAtCapacityFalseWhenUnderLimit() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 499);

            // Then
            assertThat(capacity.isAtCapacity()).isFalse();
        }

        @Test
        @DisplayName("isAtCapacity returns true when at limit")
        void isAtCapacityTrueWhenAtLimit() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 500);

            // Then
            assertThat(capacity.isAtCapacity()).isTrue();
        }

        @Test
        @DisplayName("isAtCapacity returns true when over limit")
        void isAtCapacityTrueWhenOverLimit() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 550);

            // Then
            assertThat(capacity.isAtCapacity()).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
            "449, false",  // 89.8% - not near capacity
            "450, true",   // 90% - exactly at threshold
            "475, true",   // 95% - well above threshold
            "500, true"    // 100% - at capacity
        })
        @DisplayName("isNearCapacity follows 90% threshold rule")
        void isNearCapacityThreshold(long count, boolean expected) {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, count);

            // Then
            assertThat(capacity.isNearCapacity()).isEqualTo(expected);
        }

        @Test
        @DisplayName("hasAvailableCapacity returns true when under limit")
        void hasAvailableCapacityTrue() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 400);

            // Then
            assertThat(capacity.hasAvailableCapacity()).isTrue();
        }

        @Test
        @DisplayName("hasAvailableCapacity returns false when at limit")
        void hasAvailableCapacityFalse() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 500);

            // Then
            assertThat(capacity.hasAvailableCapacity()).isFalse();
        }
    }

    @Nested
    @DisplayName("Formatting Tests")
    class FormattingTests {

        @ParameterizedTest
        @CsvSource({
            "0, 0%",
            "250, 50%",
            "375, 75%",
            "500, 100%"
        })
        @DisplayName("getUsagePercentageFormatted returns correct format")
        void usagePercentageFormatted(long count, String expected) {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, count);

            // Then
            assertThat(capacity.getUsagePercentageFormatted()).isEqualTo(expected);
        }

        @Test
        @DisplayName("toString includes all relevant information")
        void toStringFormat() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 350);
            String result = capacity.toString();

            // Then
            assertThat(result).contains("fatherId=" + FATHER_ID);
            assertThat(result).contains("currentCount=350");
            assertThat(result).contains("maxAllowed=500");
            assertThat(result).contains("availableCapacity=150");
            assertThat(result).contains("usagePercentage=70%");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles zero current count")
        void zeroCurrentCount() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 0);

            // Then
            assertThat(capacity.getCurrentCount()).isEqualTo(0);
            assertThat(capacity.getAvailableCapacity()).isEqualTo(500);
            assertThat(capacity.getUsagePercentage()).isEqualTo(0.0);
            assertThat(capacity.isAtCapacity()).isFalse();
            assertThat(capacity.isNearCapacity()).isFalse();
            assertThat(capacity.hasAvailableCapacity()).isTrue();
        }

        @Test
        @DisplayName("Handles exact capacity")
        void exactCapacity() {
            // When
            MemoryCapacityDto capacity = new MemoryCapacityDto(FATHER_ID, 500);

            // Then
            assertThat(capacity.getCurrentCount()).isEqualTo(500);
            assertThat(capacity.getAvailableCapacity()).isEqualTo(0);
            assertThat(capacity.getUsagePercentage()).isEqualTo(1.0);
            assertThat(capacity.isAtCapacity()).isTrue();
            assertThat(capacity.isNearCapacity()).isTrue();
            assertThat(capacity.hasAvailableCapacity()).isFalse();
        }
    }
}
