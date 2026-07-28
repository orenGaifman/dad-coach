package com.dadcoach.workspace.aggregation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only model representing child data needed by the workspace aggregation layer.
 *
 * <p>This is NOT a JPA entity — it is a projection/DTO used to decouple the workspace
 * read layer from the Child domain entity's internal structure.</p>
 */
public record ChildReadModel(
        UUID childId,
        UUID fatherId,
        String name,
        LocalDate birthDate,
        List<String> interests
) {}
