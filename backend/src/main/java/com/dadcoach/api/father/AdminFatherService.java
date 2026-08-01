package com.dadcoach.api.father;

import com.dadcoach.api.pagination.CursorPageResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Admin Father operations.
 * <p>
 * Provides operations for listing, searching, and retrieving father data
 * with full administrative context. Admin read operations on father data
 * are automatically audited by {@link com.dadcoach.api.audit.ApiAuditAspect}.
 */
public interface AdminFatherService {

    /**
     * Lists all fathers with cursor-based pagination and optional search query.
     * <p>
     * Supports filtering by status, coaching phase, and engagement score range.
     *
     * @param query    optional search term (matches display_name or phone)
     * @param status   optional status filter
     * @param phase    optional coaching phase filter
     * @param cursor   opaque pagination cursor (null for first page)
     * @param pageSize number of items per page
     * @return paginated list of admin father summaries
     */
    CursorPageResponse<AdminFatherSummaryDto> listFathers(
            String query, String status, String phase, String cursor, int pageSize);

    /**
     * Retrieves full father context by ID including internal metadata.
     *
     * @param fatherId the father's UUID
     * @return the full admin father detail, or empty if not found
     */
    Optional<AdminFatherDetailDto> getFatherDetail(UUID fatherId);

    /**
     * Deletes a father and all related data.
     * <p>
     * This is a destructive operation that removes the father and all associated
     * entities (children, goals, preferences, communication endpoints, etc.).
     *
     * @param fatherId the father's internal Long ID
     * @throws com.dadcoach.api.error.ResourceNotFoundException if the father is not found
     */
    void deleteFather(Long fatherId);
}
