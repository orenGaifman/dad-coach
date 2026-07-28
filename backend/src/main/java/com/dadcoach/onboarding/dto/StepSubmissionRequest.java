package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for submitting wizard step data.
 * Accepts arbitrary key-value data which is validated by the step-specific validator.
 */
@Schema(description = "Step submission data (schema varies per step)")
public class StepSubmissionRequest {

    private final Map<String, Object> data = new HashMap<>();

    @JsonAnySetter
    public void setField(String key, Object value) {
        data.put(key, value);
    }

    public Map<String, Object> getData() {
        return data;
    }
}
