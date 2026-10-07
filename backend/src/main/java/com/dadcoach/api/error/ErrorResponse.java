package com.dadcoach.api.error;

import java.time.Instant;
import java.util.List;

/** The one error shape every API answer uses, whichever layer rejected the request. */
public record ErrorResponse(Instant timestamp, int status, String code, String message, String correlationId,
                            List<FieldProblem> details) {

    public record FieldProblem(String field, String problem) {}
}
