package com.dadcoach.onboarding.wizard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepValidationResultTest {

    @Test
    void success_returnsValidResult() {
        StepValidationResult result = StepValidationResult.success();

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void failure_returnsInvalidResultWithErrors() {
        List<FieldError> errors = List.of(
                new FieldError("field1", "REQUIRED", "Field is required"),
                new FieldError("field2", "INVALID_FORMAT", "Invalid format")
        );

        StepValidationResult result = StepValidationResult.failure(errors);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).fieldName()).isEqualTo("field1");
        assertThat(result.getErrors().get(0).errorCode()).isEqualTo("REQUIRED");
    }

    @Test
    void failure_throwsForNullErrors() {
        assertThatThrownBy(() -> StepValidationResult.failure(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failure_throwsForEmptyErrors() {
        assertThatThrownBy(() -> StepValidationResult.failure(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failure_errorsListIsImmutable() {
        List<FieldError> errors = List.of(new FieldError("f", "ERR", "msg"));
        StepValidationResult result = StepValidationResult.failure(errors);

        assertThatThrownBy(() -> result.getErrors().add(new FieldError("x", "Y", "z")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fieldError_recordAccessors() {
        FieldError error = new FieldError("phone_number", "INVALID_FORMAT", "Bad phone");

        assertThat(error.fieldName()).isEqualTo("phone_number");
        assertThat(error.errorCode()).isEqualTo("INVALID_FORMAT");
        assertThat(error.message()).isEqualTo("Bad phone");
    }
}
