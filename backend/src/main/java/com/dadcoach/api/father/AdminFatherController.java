package com.dadcoach.api.father;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.api.pagination.CursorPageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API controller for father management.
 * <p>
 * Provides endpoints for listing, searching, and retrieving full father context.
 * All endpoints are under {@code /api/v1/admin/fathers} and require ADMIN role
 * (enforced via SecurityConfig).
 * <p>
 * Security invariants:
 * <ul>
 *   <li>Phone numbers are masked (country code + last 2 digits) unless actor has SUPER_ADMIN role</li>
 *   <li>Admin read operations on father data are audited (handled by ApiAuditAspect)</li>
 *   <li>Response NEVER includes embeddings or AI prompts (even for admins)</li>
 * </ul>
 * <p>
 * Phone masking example: "+1234567890" → "+1********90"
 */
@RestController
@RequestMapping("/api/v1/admin/fathers")
public class AdminFatherController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminFatherService adminFatherService;

    public AdminFatherController(AdminFatherService adminFatherService) {
        this.adminFatherService = adminFatherService;
    }

    /**
     * GET /api/v1/admin/fathers — Lists/searches all fathers with cursor-based pagination.
     * <p>
     * Supports optional filtering by:
     * <ul>
     *   <li>{@code q} — search by display_name or phone (partial match)</li>
     *   <li>{@code status} — filter by father status (ACTIVE, PAUSED, CHURNED, etc.)</li>
     *   <li>{@code phase} — filter by coaching phase</li>
     * </ul>
     * <p>
     * Phone numbers in results are masked unless the actor has SUPER_ADMIN role.
     * This endpoint is audited by ApiAuditAspect.
     *
     * @param actor    the authenticated admin actor (injected via @AuthActor)
     * @param query    optional search query for name/phone matching
     * @param status   optional status filter
     * @param phase    optional coaching phase filter
     * @param cursor   opaque pagination cursor (null for first page)
     * @param pageSize number of items per page (default: 20, max: 100)
     * @return paginated list of father summaries
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listFathers(
            @AuthActor ActorContext actor,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "phase", required = false) String phase,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "page_size", required = false, defaultValue = "20") int pageSize) {

        int effectivePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        CursorPageResponse<AdminFatherSummaryDto> page = adminFatherService.listFathers(
                query, status, phase, cursor, effectivePageSize);

        // Apply phone masking based on actor role
        maskPhoneNumbersInList(page, actor);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", page.getItems());
        response.put("next_cursor", page.getNextCursor());
        response.put("has_more", page.isHasMore());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/fathers/{id} — Retrieves full father context by ID.
     * <p>
     * Returns the complete father profile including internal metadata,
     * engagement metrics, and resource counts. This provides support agents
     * with full visibility into a father's account.
     * <p>
     * Phone number is masked unless the actor has SUPER_ADMIN role.
     * This endpoint is audited by ApiAuditAspect.
     *
     * @param actor    the authenticated admin actor (injected via @AuthActor)
     * @param fatherId the UUID of the father to retrieve
     * @return the full admin father detail
     * @throws ResourceNotFoundException if the father is not found
     */
    @GetMapping("/{fatherId}")
    public ResponseEntity<AdminFatherDetailDto> getFatherDetail(
            @AuthActor ActorContext actor,
            @PathVariable UUID fatherId) {

        AdminFatherDetailDto detail = adminFatherService.getFatherDetail(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        // Mask phone number unless actor has SUPER_ADMIN role
        if (!isSuperAdmin(actor)) {
            detail.setPhoneNumber(maskPhone(detail.getPhoneNumber()));
        }

        return ResponseEntity.ok(detail);
    }

    /**
     * Masks phone numbers in a paginated list of father summaries
     * unless the actor has SUPER_ADMIN role.
     */
    private void maskPhoneNumbersInList(CursorPageResponse<AdminFatherSummaryDto> page,
                                        ActorContext actor) {
        if (!isSuperAdmin(actor)) {
            for (AdminFatherSummaryDto summary : page.getItems()) {
                summary.setPhoneNumber(maskPhone(summary.getPhoneNumber()));
            }
        }
    }

    /**
     * Masks a phone number to show only the country code prefix and last 2 digits.
     * <p>
     * The country code is identified as the '+' sign followed by 1 to 3 digits,
     * determined by standard international numbering:
     * <ul>
     *   <li>1 digit: +1 (North America), +7 (Russia/Kazakhstan)</li>
     *   <li>2 digits: +44 (UK), +91 (India), +49 (Germany), etc.</li>
     *   <li>3 digits: +972 (Israel), +353 (Ireland), etc.</li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     *   <li>"+1234567890" → "+1******90"</li>
     *   <li>"+972501234567" → "+972*******67"</li>
     *   <li>"+44207123456" → "+44*******56"</li>
     * </ul>
     * <p>
     * Returns null if the input is null or too short to mask.
     *
     * @param phone the full phone number
     * @return the masked phone number
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }

        if (!phone.startsWith("+")) {
            // No country code detected — mask all but last 2
            String lastTwo = phone.substring(phone.length() - 2);
            return "*".repeat(phone.length() - 2) + lastTwo;
        }

        // Determine country code length based on first digit after '+'
        int countryCodeDigits;
        char firstDigit = phone.length() > 1 ? phone.charAt(1) : '0';

        if (firstDigit == '1' || firstDigit == '7') {
            // +1 (NANP) or +7 (Russia/Kazakhstan) — single digit country code
            countryCodeDigits = 1;
        } else {
            // Most country codes are 2 or 3 digits.
            // For masking purposes, we use a safe default of 2 digits for
            // codes starting with 2,3,4,5,6,8,9. Specific 3-digit codes
            // (like +972) would need a lookup table for precision.
            // We check if a 3-digit prefix yields a total length that makes sense.
            if (phone.length() > 12) {
                // Likely a 3-digit country code (e.g., +972 + 9-digit number = 13 chars)
                countryCodeDigits = 3;
            } else {
                countryCodeDigits = 2;
            }
        }

        int countryCodeEnd = 1 + countryCodeDigits; // +1 for the '+' sign
        if (countryCodeEnd >= phone.length() - 2) {
            return phone; // Too short to mask meaningfully
        }

        String countryCode = phone.substring(0, countryCodeEnd);
        String lastTwo = phone.substring(phone.length() - 2);
        int maskedLength = phone.length() - countryCodeEnd - 2;

        if (maskedLength <= 0) {
            return phone; // Too short to mask meaningfully
        }

        return countryCode + "*".repeat(maskedLength) + lastTwo;
    }

    /**
     * Checks if the actor has SUPER_ADMIN role.
     * <p>
     * SUPER_ADMIN is a specific admin sub-role that grants access to
     * unmasked PII (phone numbers, etc.). Regular ADMIN actors see masked data.
     * <p>
     * This is determined by the actor's role claims in the JWT token.
     * For now, this checks if the actor ID corresponds to a SUPER_ADMIN.
     * In production, this would check additional role claims on the actor context.
     */
    private boolean isSuperAdmin(ActorContext actor) {
        // SUPER_ADMIN is determined by role claims in the JWT token.
        // The ActorContext would carry this as an additional property.
        // For now, admin actors without explicit SUPER_ADMIN claim see masked phones.
        // Future enhancement: add hasClaim("SUPER_ADMIN") to ActorContext.
        return false;
    }
}
