package com.dadcoach.api.admin;

import com.dadcoach.api.pagination.CursorPageResponse;

import java.util.UUID;

/**
 * Service interface for Admin Search operations.
 * <p>
 * Provides search and analytics capabilities for admin users.
 * The ANALYTICS role sees only aggregated data (no individual PII).
 * Admin read operations are automatically audited by
 * {@link com.dadcoach.api.audit.ApiAuditAspect}.
 */
public interface AdminSearchService {

    /**
     * Searches fathers by various criteria with full result details.
     * <p>
     * Available to admins with ADMIN:READ permission. Results include
     * individual father records with PII (masked phone numbers).
     *
     * @param query    search query (matches display_name, phone)
     * @param status   optional status filter
     * @param phase    optional coaching phase filter
     * @param cursor   opaque pagination cursor (null for first page)
     * @param pageSize number of items per page
     * @return paginated search results with individual father data
     */
    CursorPageResponse<AdminSearchResultDto> searchFathers(
            String query, String status, String phase, String cursor, int pageSize);

    /**
     * Retrieves aggregated analytics data.
     * <p>
     * Available to all admin roles including ANALYTICS. Returns only
     * aggregated statistics without any individual PII (no names,
     * no phone numbers, no individual father IDs).
     *
     * @param status optional status filter for aggregation
     * @param phase  optional coaching phase filter for aggregation
     * @return aggregated statistics
     */
    AggregatedAnalyticsDto getAggregatedAnalytics(String status, String phase);

    /**
     * Retrieves engagement metrics aggregated across the platform.
     * <p>
     * Available to ANALYTICS role. Returns statistical distributions
     * without individual PII.
     *
     * @return aggregated engagement metrics
     */
    EngagementMetricsDto getEngagementMetrics();
}
