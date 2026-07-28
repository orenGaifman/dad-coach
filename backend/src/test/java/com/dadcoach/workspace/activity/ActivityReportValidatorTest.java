package com.dadcoach.workspace.activity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dadcoach.workspace.WorkspaceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

@DisplayName("ActivityReportValidator")
class ActivityReportValidatorTest {

    private final ActivityReportValidator validator = new ActivityReportValidator();

    @Nested
    @DisplayName("validateQualityTimeReport")
    class QualityTimeValidation {

        @Test
        void shouldAcceptValidDurationAndDate() {
            assertThatCode(() -> validator.validateQualityTimeReport(30, LocalDate.now()))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldAcceptMinimumDuration() {
            assertThatCode(() -> validator.validateQualityTimeReport(15, LocalDate.now()))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldAcceptMaximumDuration() {
            assertThatCode(() -> validator.validateQualityTimeReport(480, LocalDate.now()))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectDurationBelowMinimum() {
            assertThatThrownBy(() -> validator.validateQualityTimeReport(14, LocalDate.now()))
                    .isInstanceOf(WorkspaceException.class)
                    .hasMessageContaining("Duration must be between 15 and 480");
        }

        @Test
        void shouldRejectDurationAboveMaximum() {
            assertThatThrownBy(() -> validator.validateQualityTimeReport(481, LocalDate.now()))
                    .isInstanceOf(WorkspaceException.class)
                    .hasMessageContaining("Duration must be between 15 and 480");
        }

        @Test
        void shouldRejectFutureDate() {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            assertThatThrownBy(() -> validator.validateQualityTimeReport(30, tomorrow))
                    .isInstanceOf(WorkspaceException.class)
                    .hasMessageContaining("cannot be in the future");
        }

        @Test
        void shouldRejectDateMoreThan7DaysInPast() {
            LocalDate eightDaysAgo = LocalDate.now().minusDays(8);
            assertThatThrownBy(() -> validator.validateQualityTimeReport(30, eightDaysAgo))
                    .isInstanceOf(WorkspaceException.class)
                    .hasMessageContaining("more than 7 days in the past");
        }

        @Test
        void shouldAcceptDate7DaysInPast() {
            LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
            assertThatCode(() -> validator.validateQualityTimeReport(30, sevenDaysAgo))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validatePositiveActivityReport")
    class PositiveActivityValidation {

        @Test
        void shouldAcceptTodaysDate() {
            assertThatCode(() -> validator.validatePositiveActivityReport(LocalDate.now()))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectFutureDate() {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            assertThatThrownBy(() -> validator.validatePositiveActivityReport(tomorrow))
                    .isInstanceOf(WorkspaceException.class)
                    .hasMessageContaining("cannot be in the future");
        }

        @Test
        void shouldRejectDateMoreThan7DaysInPast() {
            LocalDate eightDaysAgo = LocalDate.now().minusDays(8);
            assertThatThrownBy(() -> validator.validatePositiveActivityReport(eightDaysAgo))
                    .isInstanceOf(WorkspaceException.class)
                    .hasMessageContaining("more than 7 days in the past");
        }
    }
}
