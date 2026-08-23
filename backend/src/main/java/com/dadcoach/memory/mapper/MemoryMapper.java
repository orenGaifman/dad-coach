package com.dadcoach.memory.mapper;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryTier;
import com.dadcoach.memory.dto.MemoryDto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * MapStruct mapper for converting between Memory entities and MemoryDto objects.
 *
 * <p>Key mapping behavior:
 * <ul>
 *   <li>Excludes the {@code embedding} vector from DTO (too large, 1536 floats)</li>
 *   <li>Computes the {@code tier} field from {@code importanceScore} using {@link MemoryTier#fromImportanceScore(int)}</li>
 *   <li>Maps all other fields directly between entity and DTO</li>
 * </ul>
 *
 * <p>The tier field is computed via {@link #computeTier(MemoryDto)} which runs after the
 * main mapping to derive the tier from the mapped importanceScore.
 */
@Mapper(componentModel = "spring")
public interface MemoryMapper {

    /**
     * Converts a Memory entity to a MemoryDto.
     *
     * <p>The {@code embedding} field is intentionally excluded from the DTO
     * as it contains a large 1536-dimension vector unsuitable for API responses.
     *
     * <p>The {@code tier} field is computed after mapping via {@link #computeTier(MemoryDto)}.
     *
     * @param memory the Memory entity to convert
     * @return the corresponding MemoryDto, or null if input is null
     */
    @Mapping(target = "tier", ignore = true) // Computed in @AfterMapping
    MemoryDto toDto(Memory memory);

    /**
     * Converts a list of Memory entities to a list of MemoryDto objects.
     *
     * @param memories the list of Memory entities to convert
     * @return the corresponding list of MemoryDto objects
     */
    List<MemoryDto> toDtoList(List<Memory> memories);

    /**
     * Converts a MemoryDto to a Memory entity.
     *
     * <p>Note: The {@code embedding} field on the resulting entity will be null.
     * Embeddings must be generated separately via the EmbeddingService.
     *
     * <p>The {@code tier} field from the DTO is ignored as it is a computed
     * property on the Memory entity (derived from importanceScore).
     *
     * @param dto the MemoryDto to convert
     * @return the corresponding Memory entity, or null if input is null
     */
    @Mapping(target = "embedding", ignore = true) // Embeddings are generated separately
    Memory toEntity(MemoryDto dto);

    /**
     * Converts a list of MemoryDto objects to a list of Memory entities.
     *
     * @param dtos the list of MemoryDto objects to convert
     * @return the corresponding list of Memory entities
     */
    List<Memory> toEntityList(List<MemoryDto> dtos);

    /**
     * After-mapping callback to compute the tier field from importanceScore.
     *
     * <p>The tier is derived using the formula from SPEC-004 Requirement 6:
     * <ul>
     *   <li>Importance 1-3 → SHORT_TERM (90 days expiration)</li>
     *   <li>Importance 4-6 → MEDIUM_TERM (180 days expiration)</li>
     *   <li>Importance 7-10 → LONG_TERM (never expires)</li>
     * </ul>
     *
     * @param dto the MemoryDto being populated
     */
    @AfterMapping
    default void computeTier(@MappingTarget MemoryDto dto) {
        if (dto.getImportanceScore() != null) {
            dto.setTier(MemoryTier.fromImportanceScore(dto.getImportanceScore()));
        }
    }
}
