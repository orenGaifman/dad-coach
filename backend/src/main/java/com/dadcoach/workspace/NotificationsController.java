package com.dadcoach.workspace;

import com.dadcoach.workspace.aggregation.NotificationsSummaryService;
import com.dadcoach.workspace.dto.request.MarkNotificationsReadRequest;
import com.dadcoach.workspace.dto.response.NotificationsSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/**
 * REST controller for notification-related workspace endpoints.
 *
 * <p>Provides notification summary with pagination, and endpoints to mark
 * notifications as read (individually or all at once).</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/notifications")
public class NotificationsController {

    private final NotificationsSummaryService notificationsSummaryService;

    public NotificationsController(NotificationsSummaryService notificationsSummaryService) {
        this.notificationsSummaryService = notificationsSummaryService;
    }

    /**
     * Returns notification summary with unread count, total count, and paginated list.
     *
     * @param page      page number (0-based, default 0)
     * @param pageSize  number of notifications per page (default 20, max 100)
     * @param principal the authenticated user
     * @return 200 OK with notifications summary response
     */
    @GetMapping
    public ResponseEntity<NotificationsSummaryResponse> getNotifications(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            Principal principal) {
        UUID fatherId = extractFatherId(principal);
        NotificationsSummaryResponse response = notificationsSummaryService
                .getSummary(fatherId, page, pageSize);
        return ResponseEntity.ok(response);
    }

    /**
     * Marks specific notifications as read.
     *
     * @param request   request body containing notification IDs to mark as read
     * @param principal the authenticated user
     * @return 204 No Content on success
     */
    @PostMapping("/mark-read")
    public ResponseEntity<Void> markAsRead(
            @Valid @RequestBody MarkNotificationsReadRequest request,
            Principal principal) {
        UUID fatherId = extractFatherId(principal);
        notificationsSummaryService.markAsRead(fatherId, request.notificationIds());
        return ResponseEntity.noContent().build();
    }

    /**
     * Marks all unread notifications as read.
     *
     * @param principal the authenticated user
     * @return 204 No Content on success
     */
    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead(Principal principal) {
        UUID fatherId = extractFatherId(principal);
        notificationsSummaryService.markAllRead(fatherId);
        return ResponseEntity.noContent().build();
    }

    private UUID extractFatherId(Principal principal) {
        if (principal == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.fromString(principal.getName());
    }
}
