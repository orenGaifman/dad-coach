package com.dadcoach.api.pagination;

/**
 * Represents a cursor-based pagination request.
 * <p>
 * First page is requested without a cursor. Subsequent pages include the cursor
 * token returned in the previous response's {@code next_cursor} field.
 * <p>
 * Page size defaults to 20 and is capped at 100.
 */
public class CursorPageRequest {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final String cursor;
    private final int pageSize;

    private CursorPageRequest(String cursor, int pageSize) {
        this.cursor = cursor;
        this.pageSize = pageSize;
    }

    /**
     * Creates a request for the first page with the default page size.
     */
    public static CursorPageRequest firstPage() {
        return new CursorPageRequest(null, DEFAULT_PAGE_SIZE);
    }

    /**
     * Creates a request for the first page with a custom page size.
     *
     * @param pageSize desired page size (clamped to 1..MAX_PAGE_SIZE)
     */
    public static CursorPageRequest firstPage(int pageSize) {
        return new CursorPageRequest(null, clampPageSize(pageSize));
    }

    /**
     * Creates a pagination request from the provided cursor and page size.
     * <p>
     * If the cursor is null, blank, or invalid, it is treated as a first-page request.
     * Page size is clamped between 1 and {@link #MAX_PAGE_SIZE}.
     *
     * @param cursor   opaque base64-encoded cursor token (nullable)
     * @param pageSize desired page size
     */
    public static CursorPageRequest of(String cursor, Integer pageSize) {
        int size = (pageSize == null) ? DEFAULT_PAGE_SIZE : clampPageSize(pageSize);
        String resolvedCursor = isBlank(cursor) ? null : cursor.trim();
        return new CursorPageRequest(resolvedCursor, size);
    }

    /**
     * Returns the opaque cursor token, or {@code null} if this is a first-page request.
     */
    public String getCursor() {
        return cursor;
    }

    /**
     * Returns the requested page size (always between 1 and MAX_PAGE_SIZE).
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Returns {@code true} if this request has a cursor (i.e., requesting a subsequent page).
     */
    public boolean hasCursor() {
        return cursor != null;
    }

    /**
     * Decodes the cursor into its composite key parts using {@link CursorEncoder}.
     * Returns {@code null} if no cursor is present or cursor is invalid.
     */
    public CursorEncoder.CursorData decodeCursor() {
        if (!hasCursor()) {
            return null;
        }
        return CursorEncoder.decode(cursor);
    }

    private static int clampPageSize(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "CursorPageRequest{" +
                "cursor='" + (cursor != null ? "[opaque]" : "null") + '\'' +
                ", pageSize=" + pageSize +
                '}';
    }
}
