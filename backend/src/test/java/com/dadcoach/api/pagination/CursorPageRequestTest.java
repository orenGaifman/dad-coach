package com.dadcoach.api.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CursorPageRequest}.
 */
class CursorPageRequestTest {

    @Test
    void firstPage_usesDefaultPageSize() {
        CursorPageRequest request = CursorPageRequest.firstPage();

        assertThat(request.getCursor()).isNull();
        assertThat(request.hasCursor()).isFalse();
        assertThat(request.getPageSize()).isEqualTo(20);
    }

    @Test
    void firstPage_withCustomSize() {
        CursorPageRequest request = CursorPageRequest.firstPage(50);

        assertThat(request.getCursor()).isNull();
        assertThat(request.getPageSize()).isEqualTo(50);
    }

    @Test
    void firstPage_clampsExcessiveSize_toMaximum() {
        CursorPageRequest request = CursorPageRequest.firstPage(200);

        assertThat(request.getPageSize()).isEqualTo(100);
    }

    @Test
    void firstPage_clampsNegativeSize_toDefault() {
        CursorPageRequest request = CursorPageRequest.firstPage(-5);

        assertThat(request.getPageSize()).isEqualTo(20);
    }

    @Test
    void firstPage_clampsZeroSize_toDefault() {
        CursorPageRequest request = CursorPageRequest.firstPage(0);

        assertThat(request.getPageSize()).isEqualTo(20);
    }

    @Test
    void of_withCursorAndSize() {
        String cursor = CursorEncoder.encode("2024-01-15T10:30:00Z", "some-uuid");
        CursorPageRequest request = CursorPageRequest.of(cursor, 30);

        assertThat(request.getCursor()).isEqualTo(cursor);
        assertThat(request.hasCursor()).isTrue();
        assertThat(request.getPageSize()).isEqualTo(30);
    }

    @Test
    void of_withNullCursor_treatsAsFirstPage() {
        CursorPageRequest request = CursorPageRequest.of(null, 25);

        assertThat(request.getCursor()).isNull();
        assertThat(request.hasCursor()).isFalse();
        assertThat(request.getPageSize()).isEqualTo(25);
    }

    @Test
    void of_withBlankCursor_treatsAsFirstPage() {
        CursorPageRequest request = CursorPageRequest.of("   ", 25);

        assertThat(request.getCursor()).isNull();
        assertThat(request.hasCursor()).isFalse();
    }

    @Test
    void of_withNullPageSize_usesDefault() {
        CursorPageRequest request = CursorPageRequest.of(null, null);

        assertThat(request.getPageSize()).isEqualTo(20);
    }

    @Test
    void of_clampsPageSize_toMaximum() {
        CursorPageRequest request = CursorPageRequest.of(null, 500);

        assertThat(request.getPageSize()).isEqualTo(100);
    }

    @Test
    void decodeCursor_returnsData_whenCursorPresent() {
        String cursor = CursorEncoder.encode("2024-06-01T00:00:00Z", "uuid-abc");
        CursorPageRequest request = CursorPageRequest.of(cursor, 20);

        CursorEncoder.CursorData data = request.decodeCursor();

        assertThat(data).isNotNull();
        assertThat(data.getSortValue()).isEqualTo("2024-06-01T00:00:00Z");
        assertThat(data.getId()).isEqualTo("uuid-abc");
    }

    @Test
    void decodeCursor_returnsNull_whenNoCursor() {
        CursorPageRequest request = CursorPageRequest.firstPage();

        assertThat(request.decodeCursor()).isNull();
    }

    @Test
    void decodeCursor_returnsNull_forInvalidCursor() {
        CursorPageRequest request = CursorPageRequest.of("invalid-garbage", 20);

        // Invalid cursor is gracefully handled — decodeCursor returns null
        assertThat(request.decodeCursor()).isNull();
    }

    @Test
    void maximumPageSize_is100() {
        assertThat(CursorPageRequest.MAX_PAGE_SIZE).isEqualTo(100);
    }

    @Test
    void defaultPageSize_is20() {
        assertThat(CursorPageRequest.DEFAULT_PAGE_SIZE).isEqualTo(20);
    }
}
