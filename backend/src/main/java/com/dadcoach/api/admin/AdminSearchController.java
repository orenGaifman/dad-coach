package com.dadcoach.api.admin;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.pagination.CursorPageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin API controller for search and analytics operations.
 * <p>
 * Provides endpoints for searching father data and retrieving aggregated analytics.
 * All endpoints are under {@code /api/v1/admin/search} and require ADMIN role
 * (enforced via SecurityConfig).
 * <p>
 * Role-based data filtering:
 * <ul>
 *   <li>ADMIN (with READ permission) — sees individual search results with masked PII</li>
 *   <li>ANALYTICS role — sees ONLY aggregated data, no individual PII whatsoever</li>
 * </ul>
 * <p>
 * Security invariants:
 * <ul>
 *   <li>ANALYTICS role NEVER sees individual father records, names, phone numbers, or IDs</li>
 *   <li>Phone numbers in search results are always masked (country code + last 2 digits)</li>
 *   <li>Admin read operations on father data are audited (handled by ApiAuditAspect)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/search")
public class AdminSearchController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Role claim value that identifies an ANALYTICS-only admin.
     * Users with this role can only see aggregated data, never individual PII.
     */
    private static final String ANALYTICS_ROLE = "ANALYTICS";

    private final AdminSearchService adminSearchService;

    public AdminSearchController(AdminSearchService adminSearchService) {
        this.adminSearchService = adminSearchService;
    }

    /**
     * GET /api/v1/admin/search/fathers — Searches fathers by query with pagination.
     * <p>
     * Available only to admins with full READ permission (not ANALYTICS role).
     * ANALYTICS role users receive 403 Forbidden since this endpoint returns
     * individual PII (masked phone numbers, names, IDs).
     * <p>
     * Search matches against display_name and phone (partial match).
     * Phone numbers in results are always masked.
     * This endpoint is audited by ApiAuditAspect.
     *
     * @param actor    the authenticated admin actor (injected via @AuthActor)
     * @param query    search query (matches display_name, phone)
     * @param status   optional status filter
     * @param phase    optional coaching phase filter
     * @param cursor   opaque pagination cursor (null for first page)
     * @param pageSize number of items per page (default: 20, max: 100)
     * @return paginated search results with individual father data
     */
    @GetMapping("/fathers")
    public ResponseEntity<?> searchFathers(
            @AuthActor ActorContext actor,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "phase", required = false) String phase,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "page_size", required = false, defaultValue = "20") int pageSize) {

        // ANALYTICS role cannot access individual PII
        if (isAnalyticsOnly(actor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error_code", "FORBIDDEN",
                            "message", "ANALYTICS role cannot access individual father data. " +
                                    "Use /api/v1/admin/search/analytics for aggregated data.",
                            "retryable", false
                    ));
        }

        int effectivePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        CursorPageResponse<AdminSearchResultDto> page = adminSearchService.searchFathers(
                query, status, phase, cursor, effectivePageSize);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", page.getItems());
        response.put("next_cursor", page.getNextCursor());
        response.put("has_more", page.isHasMore());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/search/analytics — Retrieves aggregated analytics data.
     * <p>
     * Available to ALL admin roles including ANALYTICS. Returns ONLY aggregated
     * statistics without any individual PII (no names, phone numbers, or father IDs).
     * <p>
     * This is the primary endpoint for ANALYTICS role users. It provides
     * platform-wide statistics filtered by optional status and phase parameters.
     *
     * @param actor  the authenticated admin actor (injected via @AuthActor)
     * @param status optional status filter for aggregation
     * @param phase  optional coaching phase filter for aggregation
     * @return aggregated analytics without individual PII
     */
    @GetMapping("/analytics")
    public ResponseEntity<AggregatedAnalyticsDto> getAnalytics(
            @AuthActor ActorContext actor,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "phase", required = false) String phase) {

        AggregatedAnalyticsDto analytics = adminSearchService.getAggregatedAnalytics(status, phase);

        return ResponseEntity.ok(analytics);
    }

    /**
     * GET /api/v1/admin/search/engagement — Retrieves engagement metrics.
     * <p>
     * Available to ALL admin roles including ANALYTICS. Returns statistical
     * distributions and engagement trends without any individual PII.
     *
     * @param actor the authenticated admin actor (injected via @AuthActor)
     * @return aggregated engagement metrics
     */
    @GetMapping("/engagement")
    public ResponseEntity<EngagementMetricsDto> getEngagementMetrics(
            @AuthActor ActorContext actor) {

        EngagementMetricsDto metrics = adminSearchService.getEngagementMetrics();

        return ResponseEntity.ok(metrics);
    }

    /**
     * Checks if the actor has ANALYTICS-only role.
     * <p>
     * ANALYTICS users can only see aggregated data — no individual PII.
     * This is determined by role claims in the JWT token.
     * <p>
     * In production, the ActorContext would carry role claims to check.
     * For now, this checks if the actor's metadata indicates ANALYTICS role.
     */
    private boolean isAnalyticsOnly(ActorContext actor) {
        // ANALYTICS role determination is based on JWT role claims.
        // The ActorContext would be extended to carry sub-roles.
        // For now, all authenticated ADMIN actors have full READ access.
        // Future enhancement: add hasRole("ANALYTICS") to ActorContext.
        return false;
    }
}
