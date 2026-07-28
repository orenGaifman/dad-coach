package com.dadcoach.workspace.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing the father's activity feed.
 *
 * <p>Provides methods to record feed items (from domain events), retrieve the feed
 * with cursor-based pagination, and purge expired items (90-day retention).</p>
 *
 * <p>Feed items are written asynchronously from domain event listeners and read
 * via the ActivityFeedController.</p>
 */
@Service
@Transactional
public class ActivityFeedService {

    private static final Logger log = LoggerFactory.getLogger(ActivityFeedService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final long RETENTION_DAYS = 90;

    private final ActivityFeedRepository activityFeedRepository;

    public ActivityFeedService(ActivityFeedRepository activityFeedRepository) {
        this.activityFeedRepository = activityFeedRepository;
    }

    /**
     * Records a new feed item for the given father.
     *
     * @param eventType         the type of event
     * @param fatherId          the father's ID
     * @param title             short human-readable title
     * @param description       optional longer description
     * @param relatedEntityId   optional related entity ID (e.g., mission ID)
     * @param relatedEntityType optional related entity type name
     * @return the persisted feed item
     */
    public ActivityFeedItem recordFeedItem(ActivityFeedEventType eventType,
                                           UUID fatherId,
                                           String title,
                                           String description,
                                           UUID relatedEntityId,
                                           String relatedEntityType) {
        Instant now = Instant.now();
        ActivityFeedItem item = ActivityFeedItem.builder()
                .fatherId(fatherId)
                .eventType(eventType)
                .title(title)
                .description(description)
                .relatedEntityId(relatedEntityId)
                .relatedEntityType(relatedEntityType)
                .eventTimestamp(now)
                .expiresAt(now.plusSeconds(RETENTION_DAYS * 24 * 60 * 60))
                .build();

        ActivityFeedItem saved = activityFeedRepository.save(item);
        log.debug("Recorded feed item {} for father {} (type: {})",
                saved.getFeedItemId(), fatherId, eventType);
        return saved;
    }

    /**
     * Retrieves the activity feed for a father using cursor-based pagination.
     *
     * @param fatherId the father's ID
     * @param cursor   optional cursor (event timestamp of the last item on previous page);
     *                 null for the first page
     * @param pageSize requested page size (clamped between 1 and {@value MAX_PAGE_SIZE})
     * @return a {@link FeedPage} containing items and pagination metadata
     */
    @Transactional(readOnly = true)
    public FeedPage getFeed(UUID fatherId, Instant cursor, Integer pageSize) {
        int size = normalizePageSize(pageSize);
        // Fetch one extra item to determine if there are more pages
        Pageable pageable = PageRequest.of(0, size + 1);

        List<ActivityFeedItem> items;
        if (cursor != null) {
            items = activityFeedRepository.findByFatherIdAndEventTimestampBefore(fatherId, cursor, pageable);
        } else {
            items = activityFeedRepository.findByFatherIdOrderByEventTimestampDesc(fatherId, pageable);
        }

        boolean hasMore = items.size() > size;
        List<ActivityFeedItem> pageItems = hasMore ? items.subList(0, size) : items;

        Instant nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            nextCursor = pageItems.get(pageItems.size() - 1).getEventTimestamp();
        }

        return new FeedPage(pageItems, nextCursor, hasMore);
    }

    /**
     * Purges expired feed items (older than 90 days from their creation).
     *
     * @return number of items deleted
     */
    public int purgeExpiredItems() {
        Instant now = Instant.now();
        int deleted = activityFeedRepository.deleteByExpiresAtBefore(now);
        if (deleted > 0) {
            log.info("Purged {} expired activity feed items", deleted);
        }
        return deleted;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * Immutable value object representing a page of feed items with cursor metadata.
     */
    public record FeedPage(List<ActivityFeedItem> items, Instant nextCursor, boolean hasMore) {
    }
}
