package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * DTO representing a child associated with a father for the Dev Dashboard.
 *
 * @param id The child's unique identifier
 * @param name The child's name
 * @param birthDate The child's birth date (YYYY-MM-DD format)
 */
public record ChildDto(
    Long id,
    
    String name,
    
    @JsonProperty("birth_date")
    LocalDate birthDate
) {}
