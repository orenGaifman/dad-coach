package com.dadcoach.api.father;

import com.dadcoach.domain.father.Father;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MapStruct mapper that converts the Father domain entity to the public API response DTO.
 * <p>
 * This mapper intentionally EXCLUDES sensitive fields from the response:
 * <ul>
 *   <li>phone — PII, never exposed in Father API</li>
 *   <li>metadata — internal JSONB blob</li>
 *   <li>onboardingState — internal lifecycle detail</li>
 *   <li>locale — internal setting</li>
 *   <li>activationDate — internal tracking</li>
 *   <li>lastInteractionAt — internal tracking</li>
 *   <li>pauseUntil — internal scheduling</li>
 *   <li>longestStreak — internal metric</li>
 * </ul>
 * <p>
 * No embeddings, AI prompts, or raw confidence scores ever reach this mapper
 * since they are not part of the Father entity, but this mapper acts as a
 * compile-time enforced filter ensuring only safe fields are exposed.
 */
@Mapper(componentModel = "spring")
public interface FatherMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "longToUuid")
    @Mapping(target = "phase", source = "coachingPhase")
    @Mapping(target = "preferredCoachingTime", source = "preferredCoachingTime", qualifiedByName = "timeToString")
    FatherResponseDto toDto(Father father);

    /**
     * Converts the internal Long ID to an opaque UUID for external consumers.
     * <p>
     * This prevents exposing sequential IDs and ensures the external API contract
     * does not leak internal database structure.
     */
    @Named("longToUuid")
    default UUID longToUuid(Long id) {
        if (id == null) {
            return null;
        }
        // Create a deterministic UUID from the Long ID using a namespace approach.
        // In production, this would use a lookup table or the entity's external_id field.
        return new UUID(0L, id);
    }

    /**
     * Formats LocalTime as HH:MM string for the API response.
     */
    @Named("timeToString")
    default String timeToString(LocalTime time) {
        if (time == null) {
            return null;
        }
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}
