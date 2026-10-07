package com.dadcoach.web.common;

import java.time.Instant;
import java.util.List;

/** The one error shape of the dashboard APIs (Tair): the SPA chooses its Hebrew text by {@code code}. */
public record ApiError(Instant timestamp, int status, String code, String message, String correlationId,
                       List<FieldProblem> details) {

    public record FieldProblem(String field, String problem) {
    }
}
