package com.dadcoach.workspace;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.workspace.aggregation.NotificationsSummaryService;
import com.dadcoach.workspace.dto.request.MarkNotificationsReadRequest;
import com.dadcoach.workspace.dto.response.NotificationsSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * @param page     page number (0-based, default 0)
     * @param pageSize number of notifications per page (default 20, max 100)
     * @param actor    the authenticated actor context
     * @return 200 OK with notifications summary response
     */
    @GetMapping
    public ResponseEntity<NotificationsSummaryResponse> getNotifications(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        NotificationsSummaryResponse response = notificationsSummaryService
                .getSummary(fatherId, page, pageSize);
        return ResponseEntity.ok(response);
    }

    /**
     * Marks specific notifications as read.
     *
     * @param request request body containing notification IDs to mark as read
     * @param actor   the authenticated actor context
     * @return 204 No Content on success
     */
    @PostMapping("/mark-read")
    public ResponseEntity<Void> markAsRead(
            @Valid @RequestBody MarkNotificationsReadRequest request,
            @AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        notificationsSummaryService.markAsRead(fatherId, request.notificationIds());
        return ResponseEntity.noContent().build();
    }

    /**
     * Marks all unread notifications as read.
     *
     * @param actor the authenticated actor context
     * @return 204 No Content on success
     */
    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        notificationsSummaryService.markAllRead(fatherId);
        return ResponseEntity.noContent().build();
    }
}
