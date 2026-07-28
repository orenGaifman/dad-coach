package com.dadcoach.workspace;

import com.dadcoach.workspace.dto.response.ActivityFeedResponse;
import com.dadcoach.workspace.feed.ActivityFeedItem;
import com.dadcoach.workspace.feed.ActivityFeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the activity feed endpoint.
 *
 * <p>Provides cursor-based paginated access to the father's activity feed,
 * showing a chronological timeline of significant events (missions, goals,
 * achievements, milestones, etc.).</p>
 *
 * <p>Requirement 6.1, 6.4, 6.6: Activity feed with cursor pagination.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/activity-feed")
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;

    public ActivityFeedController(ActivityFeedService activityFeedService) {
        this.activityFeedService = activityFeedService;
    }

    /**
     * Returns the father's activity feed with cursor-based pagination.
     *
     * @param principal the authenticated user
     * @param cursor    optional cursor (ISO-8601 timestamp from previous page's last item)
     * @param pageSize  optional page size (default 20, max 50)
     * @return 200 OK with paginated feed items
     */
    @GetMapping
    public ResponseEntity<ActivityFeedResponse> getActivityFeed(
            Principal principal,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "page_size", required = false, defaultValue = "20") Integer pageSize) {

        UUID fatherId = extractFatherId(principal);
        Instant cursorInstant = cursor != null ? Instant.parse(cursor) : null;

        ActivityFeedService.FeedPage feedPage = activityFeedService.getFeed(fatherId, cursorInstant, pageSize);

        List<ActivityFeedResponse.FeedItem> items = feedPage.items().stream()
                .map(this::toFeedItemDto)
                .toList();

        String nextCursorStr = feedPage.nextCursor() != null ? feedPage.nextCursor().toString() : null;

        ActivityFeedResponse response = ActivityFeedResponse.builder()
                .items(items)
                .nextCursor(nextCursorStr)
                .hasMore(feedPage.hasMore())
                .build();

        return ResponseEntity.ok(response);
    }

    private ActivityFeedResponse.FeedItem toFeedItemDto(ActivityFeedItem item) {
        return new ActivityFeedResponse.FeedItem(
                item.getFeedItemId(),
                item.getEventType().name(),
                item.getTitle(),
                item.getDescription(),
                item.getRelatedEntityId(),
                item.getRelatedEntityType(),
                item.getEventTimestamp()
        );
    }

    private UUID extractFatherId(Principal principal) {
        if (principal == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.fromString(principal.getName());
    }
}
