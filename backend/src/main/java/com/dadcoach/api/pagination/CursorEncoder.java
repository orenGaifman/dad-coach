package com.dadcoach.api.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes and decodes opaque cursor tokens for cursor-based pagination.
 * <p>
 * A cursor is a base64-encoded composite key consisting of a sort field value
 * and a unique identifier (typically a UUID). This composite key ensures stable
 * iteration: new inserts don't affect in-progress pagination because the cursor
 * anchors to a specific sort position + unique id.
 * <p>
 * Format: base64("{sortValue}|{id}")
 * <p>
 * The separator '|' is used internally. Sort values containing '|' are supported
 * because decoding splits only on the last occurrence.
 */
public final class CursorEncoder {

    private static final String SEPARATOR = "|";

    private CursorEncoder() {
        // utility class
    }

    /**
     * Encodes a composite key (sort field value + unique id) into an opaque
     * base64-encoded cursor token.
     *
     * @param sortValue the value of the sort field (e.g., timestamp as ISO string)
     * @param id        the unique identifier (e.g., UUID string)
     * @return opaque base64-encoded cursor string
     * @throws IllegalArgumentException if sortValue or id is null/blank
     */
    public static String encode(String sortValue, String id) {
        if (sortValue == null || sortValue.isBlank()) {
            throw new IllegalArgumentException("sortValue must not be null or blank");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        String raw = sortValue + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque base64-encoded cursor token back into its composite key parts.
     * <p>
     * Returns {@code null} if the cursor is invalid (malformed base64, missing separator,
     * or empty parts). Invalid cursors are handled gracefully by treating the request
     * as a first-page request.
     *
     * @param cursor the opaque cursor token
     * @return the decoded cursor data, or {@code null} if invalid
     */
    public static CursorData decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor.trim());
            String raw = new String(decoded, StandardCharsets.UTF_8);

            // Split on last occurrence of separator to support sort values containing '|'
            int lastSep = raw.lastIndexOf(SEPARATOR);
            if (lastSep <= 0 || lastSep >= raw.length() - 1) {
                return null;
            }

            String sortValue = raw.substring(0, lastSep);
            String id = raw.substring(lastSep + 1);

            if (sortValue.isBlank() || id.isBlank()) {
                return null;
            }

            return new CursorData(sortValue, id);
        } catch (IllegalArgumentException e) {
            // Malformed base64
            return null;
        }
    }

    /**
     * Holds the decoded composite key parts of a cursor.
     */
    public static final class CursorData {

        private final String sortValue;
        private final String id;

        public CursorData(String sortValue, String id) {
            this.sortValue = sortValue;
            this.id = id;
        }

        /**
         * Returns the sort field value (e.g., an ISO timestamp string).
         */
        public String getSortValue() {
            return sortValue;
        }

        /**
         * Returns the unique identifier (e.g., UUID as string).
         */
        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return "CursorData{sortValue='" + sortValue + "', id='" + id + "'}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CursorData that = (CursorData) o;
            return sortValue.equals(that.sortValue) && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return 31 * sortValue.hashCode() + id.hashCode();
        }
    }
}
