package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Generic wrapper DTO supporting partial degradation responses.
 *
 * <p>When all data sources are available, {@code responseStatus} is "complete" and
 * {@code degradedSections} is null. When one or more sources are unavailable,
 * {@code responseStatus} is "partial" and {@code degradedSections} lists the
 * unavailable sources.</p>
 *
 * @param <T> the type of the wrapped data payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartialResponse<T> {

    private final T data;

    @JsonProperty("response_status")
    private final String responseStatus;

    @JsonProperty("degraded_sections")
    private final List<String> degradedSections;

    private final Instant timestamp;

    private PartialResponse(T data, String responseStatus, List<String> degradedSections) {
        this.data = data;
        this.responseStatus = responseStatus;
        this.degradedSections = degradedSections;
        this.timestamp = Instant.now();
    }

    /**
     * Creates a complete response — all data sources were available.
     */
    public static <T> PartialResponse<T> complete(T data) {
        return new PartialResponse<>(data, "complete", null);
    }

    /**
     * Creates a partial response — some data sources were unavailable.
     *
     * @param data             the available data (may have null fields for degraded sections)
     * @param degradedSections the list of section names that could not be loaded
     */
    public static <T> PartialResponse<T> partial(T data, List<String> degradedSections) {
        return new PartialResponse<>(data, "partial", degradedSections);
    }

    public T getData() {
        return data;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public List<String> getDegradedSections() {
        return degradedSections;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
