package com.dadcoach.api.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link CursorPageResponse}.
 */
class CursorPageResponseTest {

    @Test
    void of_createsResponseWithItemsAndCursor() {
        List<String> items = Arrays.asList("item1", "item2", "item3");
        String nextCursor = CursorEncoder.encode("2024-01-15", "uuid-last");

        CursorPageResponse<String> response = CursorPageResponse.of(items, nextCursor, true);

        assertThat(response.getItems()).hasSize(3);
        assertThat(response.getItems()).containsExactly("item1", "item2", "item3");
        assertThat(response.getNextCursor()).isEqualTo(nextCursor);
        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    void of_lastPage_hasNullCursorAndHasMoreFalse() {
        List<String> items = List.of("only-item");

        CursorPageResponse<String> response = CursorPageResponse.of(items, null, false);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getNextCursor()).isNull();
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    void empty_createsEmptyResponse() {
        CursorPageResponse<String> response = CursorPageResponse.empty();

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getNextCursor()).isNull();
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    void of_withNullItems_returnsEmptyList() {
        CursorPageResponse<String> response = CursorPageResponse.of(null, null, false);

        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void items_areUnmodifiable() {
        List<String> items = Arrays.asList("a", "b");
        CursorPageResponse<String> response = CursorPageResponse.of(items, null, false);

        assertThatThrownBy(() -> response.getItems().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void worksWithGenericTypes() {
        record TestDto(String name, int value) {}

        List<TestDto> items = List.of(new TestDto("alpha", 1), new TestDto("beta", 2));
        CursorPageResponse<TestDto> response = CursorPageResponse.of(items, "next-token", true);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).name()).isEqualTo("alpha");
    }
}
