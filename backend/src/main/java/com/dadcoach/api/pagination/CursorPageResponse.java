package com.dadcoach.api.pagination;

import java.util.Collections;
import java.util.List;

/**
 * Generic response wrapper for cursor-based pagination.
 * <p>
 * Contains the page of items, an opaque cursor for the next page, and a flag
 * indicating whether more items exist beyond this page.
 *
 * @param <T> the type of items in the response
 */
public class CursorPageResponse<T> {

    private final List<T> items;
    private final String nextCursor;
    private final boolean hasMore;

    private CursorPageResponse(List<T> items, String nextCursor, boolean hasMore) {
        this.items = items != null ? Collections.unmodifiableList(items) : Collections.emptyList();
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    /**
     * Creates a response page with items and pagination info.
     *
     * @param items      the items on this page
     * @param nextCursor opaque cursor for the next page, null if no more pages
     * @param hasMore    true if there are more items beyond this page
     * @param <T>        item type
     * @return a new CursorPageResponse
     */
    public static <T> CursorPageResponse<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return new CursorPageResponse<>(items, nextCursor, hasMore);
    }

    /**
     * Creates an empty response (no items, no next page).
     *
     * @param <T> item type
     * @return an empty CursorPageResponse
     */
    public static <T> CursorPageResponse<T> empty() {
        return new CursorPageResponse<>(Collections.emptyList(), null, false);
    }

    /**
     * Returns the list of items on this page. The list is unmodifiable.
     */
    public List<T> getItems() {
        return items;
    }

    /**
     * Returns the opaque cursor token for requesting the next page,
     * or {@code null} if there are no more pages.
     */
    public String getNextCursor() {
        return nextCursor;
    }

    /**
     * Returns {@code true} if there are more items beyond this page.
     */
    public boolean isHasMore() {
        return hasMore;
    }

    @Override
    public String toString() {
        return "CursorPageResponse{" +
                "items.size=" + items.size() +
                ", nextCursor=" + (nextCursor != null ? "[opaque]" : "null") +
                ", hasMore=" + hasMore +
                '}';
    }
}
