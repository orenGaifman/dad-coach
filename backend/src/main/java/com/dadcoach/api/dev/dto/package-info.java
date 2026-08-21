/**
 * Data Transfer Objects (DTOs) for the Dev Dashboard API.
 * 
 * <p>This package contains all response DTOs used by the Dev API endpoints
 * located at {@code /api/v1/dev/*}. These DTOs are designed for debugging
 * purposes and provide visibility into the workflow state machine.
 * 
 * <h2>Serialization Requirements</h2>
 * <ul>
 *   <li>All timestamp fields ({@link java.time.Instant}) are serialized to ISO 8601 format
 *       with timezone offset (e.g., "2025-01-15T10:30:00+02:00")</li>
 *   <li>All enum fields (WorkflowState, Belt, FatherStatus, Direction) are serialized
 *       as uppercase strings matching the enum constant name</li>
 *   <li>Field names use snake_case in JSON (configured via {@code @JsonProperty})</li>
 *   <li>Null values are excluded from JSON output where appropriate
 *       (configured via {@code @JsonInclude})</li>
 * </ul>
 * 
 * <h2>DTOs Overview</h2>
 * <ul>
 *   <li>{@link com.dadcoach.api.dev.dto.FatherListItemDto} - Father list view for selection</li>
 *   <li>{@link com.dadcoach.api.dev.dto.FatherStateDetailsDto} - Detailed father state with nested workflow and belt info</li>
 *   <li>{@link com.dadcoach.api.dev.dto.MessageDto} - Message log entries</li>
 *   <li>{@link com.dadcoach.api.dev.dto.TransitionDto} - Workflow state transitions</li>
 *   <li>{@link com.dadcoach.api.dev.dto.ChildDto} - Child information</li>
 *   <li>{@link com.dadcoach.api.dev.dto.QualityTimeDto} - Scheduled quality time entries</li>
 *   <li>{@link com.dadcoach.api.dev.dto.PaginatedResponse} - Generic pagination wrapper</li>
 *   <li>{@link com.dadcoach.api.dev.dto.ErrorResponse} - Error response structure</li>
 * </ul>
 * 
 * @see <a href="Requirements 13.1, 13.2, 13.3, 13.4">API Response Serialization Requirements</a>
 */
package com.dadcoach.api.dev.dto;
