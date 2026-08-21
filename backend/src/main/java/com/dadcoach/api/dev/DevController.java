package com.dadcoach.api.dev;

import com.dadcoach.api.dev.dto.ErrorResponse;
import com.dadcoach.api.dev.dto.FatherListItemDto;
import com.dadcoach.api.dev.dto.FatherStateDetailsDto;
import com.dadcoach.api.dev.dto.MessageDto;
import com.dadcoach.api.dev.dto.PaginatedResponse;
import com.dadcoach.api.dev.dto.TransitionDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for Dev Dashboard debugging endpoints.
 *
 * <p>Provides endpoints for debugging the WhatsApp workflow conversation flow.
 * All endpoints are protected by {@link DevEnvironmentGuard} and will return
 * HTTP 403 Forbidden in production environments.</p>
 *
 * <p>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 5.1</p>
 *
 * @see DevEnvironmentGuard
 * @see DevService
 */
@RestController
@RequestMapping("/api/v1/dev")
public class DevController {

    private static final Logger log = LoggerFactory.getLogger(DevController.class);

    /**
     * Maximum allowed page size for pagination.
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Maximum allowed limit for message queries.
     */
    private static final int MAX_MESSAGE_LIMIT = 200;

    /**
     * Default limit for message queries.
     */
    private static final int DEFAULT_MESSAGE_LIMIT = 50;

    /**
     * Maximum allowed limit for transitions.
     */
    private static final int MAX_TRANSITION_LIMIT = 100;

    /**
     * Default limit for transitions.
     */
    private static final int DEFAULT_TRANSITION_LIMIT = 30;

    /**
     * Default page size for pagination.
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Base URL for fathers endpoint (used in pagination links).
     */
    private static final String FATHERS_BASE_URL = "/api/v1/dev/fathers";

    private final DevEnvironmentGuard environmentGuard;
    private final DevService devService;

    public DevController(DevEnvironmentGuard environmentGuard, DevService devService) {
        this.environmentGuard = environmentGuard;
        this.devService = devService;
    }

    /**
     * GET /api/v1/dev/fathers - List all fathers with pagination and search.
     *
     * <p>Returns a paginated list of fathers with their basic debugging info,
     * including id, display_name, phone, status, current_workflow_state,
     * previous_workflow_state, current_belt, and last_interaction_at.</p>
     *
     * <p>Results are ordered by last_interaction_at descending (most recent first).</p>
     *
     * <p>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5</p>
     *
     * @param search optional search string to filter by phone or display_name (case-insensitive)
     * @param page zero-indexed page number (default 0)
     * @param pageSize number of items per page (default 20, max 100)
     * @return HTTP 200 with paginated response, HTTP 400 if page_size exceeds 100, HTTP 403 in production
     */
    @GetMapping("/fathers")
    public ResponseEntity<?> listFathers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {

        // Requirement 5.1: Check environment access at start of each endpoint
        environmentGuard.requireDevAccess();

        log.debug("Listing fathers: search='{}', page={}, page_size={}", search, page, pageSize);

        // Requirement 1.3: Validate page_size max 100, reject with HTTP 400 if exceeded
        if (pageSize > MAX_PAGE_SIZE) {
            log.debug("Page size {} exceeds maximum {}", pageSize, MAX_PAGE_SIZE);
            return ResponseEntity
                    .badRequest()
                    .body(ErrorResponse.badRequest(
                            "page_size must not exceed " + MAX_PAGE_SIZE));
        }

        // Ensure page_size is at least 1
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }

        // Ensure page is non-negative
        if (page < 0) {
            page = 0;
        }

        // Query fathers with pagination
        Page<FatherListItemDto> fatherPage = devService.listFathers(
                search,
                PageRequest.of(page, pageSize));

        // Build paginated response with _links
        PaginatedResponse<FatherListItemDto> response = PaginatedResponse.of(
                fatherPage.getContent(),
                page,
                pageSize,
                fatherPage.getTotalElements(),
                FATHERS_BASE_URL);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/dev/fathers/{id}/state - Get father state details.
     *
     * <p>Returns detailed state information for a specific father including
     * workflow state, belt info, children, and scheduled quality times.</p>
     *
     * <p>Implements partial data handling: if children or quality time queries fail,
     * returns HTTP 200 with partial data and error indicators rather than
     * failing the entire request.</p>
     *
     * <p>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6</p>
     *
     * @param id the ID of the father to retrieve
     * @return HTTP 200 with FatherStateDetailsDto, HTTP 403 in production, HTTP 404 if father not found
     */
    @GetMapping("/fathers/{id}/state")
    public ResponseEntity<FatherStateDetailsDto> getFatherState(@PathVariable Long id) {

        // Requirement 2.4: Check environment access - 403 takes precedence over 404
        environmentGuard.requireDevAccess();

        log.debug("Getting state for father id={}", id);

        // Requirement 2.5: DevService.getFatherState throws FatherNotFoundException for 404
        FatherStateDetailsDto stateDetails = devService.getFatherState(id);

        return ResponseEntity.ok(stateDetails);
    }

    /**
     * GET /api/v1/dev/fathers/{id}/messages - Get message log for a father.
     *
     * <p>Returns the message log entries for a specific father, ordered by
     * created_at descending (newest first).</p>
     *
     * <p>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6</p>
     *
     * @param id the ID of the father whose messages to retrieve
     * @param limit maximum number of messages to return (default 50, max 200)
     * @param since optional ISO 8601 timestamp to filter messages created after this time
     * @return HTTP 200 with list of MessageDto, HTTP 400 if limit exceeds 200,
     *         HTTP 403 in production, HTTP 404 if father not found
     */
    @GetMapping("/fathers/{id}/messages")
    public ResponseEntity<?> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Instant since) {

        // Requirement 3.5: Check environment access - 403 takes precedence over 404
        environmentGuard.requireDevAccess();

        log.debug("Getting messages for father id={}, limit={}, since={}", id, limit, since);

        // Requirement 3.2: Validate limit max 200, reject with HTTP 400 if exceeded
        if (limit > MAX_MESSAGE_LIMIT) {
            log.debug("Message limit {} exceeds maximum {}", limit, MAX_MESSAGE_LIMIT);
            return ResponseEntity
                    .badRequest()
                    .body(ErrorResponse.badRequest(
                            "limit must not exceed " + MAX_MESSAGE_LIMIT));
        }

        // Ensure limit is at least 1
        if (limit < 1) {
            limit = DEFAULT_MESSAGE_LIMIT;
        }

        // Requirement 3.6: DevService.getMessages throws FatherNotFoundException for 404
        List<MessageDto> messages = devService.getMessages(id, limit, since);

        return ResponseEntity.ok(messages);
    }

    /**
     * GET /api/v1/dev/fathers/{id}/transitions - Get workflow state transitions for a father.
     *
     * <p>Returns the workflow state transition history for a specific father,
     * ordered by created_at descending (newest first).</p>
     *
     * <p>Validates: Requirements 4.1, 4.2, 4.4, 4.5</p>
     *
     * @param id the ID of the father whose transitions to retrieve
     * @param limit maximum number of transitions to return (default 30, max 100)
     * @return HTTP 200 with List of TransitionDto, HTTP 400 if limit exceeds 100, HTTP 403 in production, HTTP 404 if father not found
     */
    @GetMapping("/fathers/{id}/transitions")
    public ResponseEntity<?> getTransitions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int limit) {

        // Requirement 4.4: Check environment access - 403 takes precedence over 404
        environmentGuard.requireDevAccess();

        log.debug("Getting transitions for father id={}, limit={}", id, limit);

        // Requirement 4.2: Validate limit max 100, reject with HTTP 400 if exceeded
        if (limit > MAX_TRANSITION_LIMIT) {
            log.debug("Limit {} exceeds maximum {}", limit, MAX_TRANSITION_LIMIT);
            return ResponseEntity
                    .badRequest()
                    .body(ErrorResponse.badRequest(
                            "limit must not exceed " + MAX_TRANSITION_LIMIT));
        }

        // Ensure limit is at least 1
        if (limit < 1) {
            limit = DEFAULT_TRANSITION_LIMIT;
        }

        // Requirement 4.5: DevService.getTransitions throws FatherNotFoundException for 404
        List<TransitionDto> transitions = devService.getTransitions(id, limit);

        return ResponseEntity.ok(transitions);
    }
}
