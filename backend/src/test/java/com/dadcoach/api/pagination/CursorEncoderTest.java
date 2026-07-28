package com.dadcoach.api.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Unit tests for {@link CursorEncoder}.
 */
class CursorEncoderTest {

    @Test
    void encode_producesOpaqueBase64Token() {
        String token = CursorEncoder.encode("2024-01-15T10:30:00Z", "550e8400-e29b-41d4-a716-446655440000");

        assertThat(token).isNotBlank();
        // Should not contain plain text (opaque)
        assertThat(token).doesNotContain("2024-01-15");
        assertThat(token).doesNotContain("550e8400");
    }

    @Test
    void decode_roundTrips_withTimestampAndUuid() {
        String sortValue = "2024-01-15T10:30:00Z";
        String id = UUID.randomUUID().toString();

        String token = CursorEncoder.encode(sortValue, id);
        CursorEncoder.CursorData decoded = CursorEncoder.decode(token);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getSortValue()).isEqualTo(sortValue);
        assertThat(decoded.getId()).isEqualTo(id);
    }

    @Test
    void decode_roundTrips_withSortValueContainingSeparator() {
        // Sort value contains '|' — decoder should split on last occurrence
        String sortValue = "value|with|pipes";
        String id = "unique-id-123";

        String token = CursorEncoder.encode(sortValue, id);
        CursorEncoder.CursorData decoded = CursorEncoder.decode(token);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getSortValue()).isEqualTo(sortValue);
        assertThat(decoded.getId()).isEqualTo(id);
    }

    @Test
    void decode_returnsNull_forNullCursor() {
        assertThat(CursorEncoder.decode(null)).isNull();
    }

    @Test
    void decode_returnsNull_forBlankCursor() {
        assertThat(CursorEncoder.decode("")).isNull();
        assertThat(CursorEncoder.decode("   ")).isNull();
    }

    @Test
    void decode_returnsNull_forInvalidBase64() {
        assertThat(CursorEncoder.decode("not-valid-base64!!!")).isNull();
    }

    @Test
    void decode_returnsNull_forBase64WithoutSeparator() {
        // Valid base64 but doesn't contain the separator
        String noSeparator = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("noseparatorhere".getBytes());
        assertThat(CursorEncoder.decode(noSeparator)).isNull();
    }

    @Test
    void decode_returnsNull_forBase64WithEmptyParts() {
        // Separator at start — empty sort value
        String emptySort = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("|some-id".getBytes());
        assertThat(CursorEncoder.decode(emptySort)).isNull();
    }

    @Test
    void encode_throwsException_forNullSortValue() {
        assertThatThrownBy(() -> CursorEncoder.encode(null, "id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sortValue");
    }

    @Test
    void encode_throwsException_forBlankSortValue() {
        assertThatThrownBy(() -> CursorEncoder.encode("  ", "id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sortValue");
    }

    @Test
    void encode_throwsException_forNullId() {
        assertThatThrownBy(() -> CursorEncoder.encode("sort", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void encode_throwsException_forBlankId() {
        assertThatThrownBy(() -> CursorEncoder.encode("sort", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void cursorData_equals_and_hashCode() {
        var data1 = new CursorEncoder.CursorData("2024-01-01", "id-1");
        var data2 = new CursorEncoder.CursorData("2024-01-01", "id-1");
        var data3 = new CursorEncoder.CursorData("2024-01-02", "id-1");

        assertThat(data1).isEqualTo(data2);
        assertThat(data1.hashCode()).isEqualTo(data2.hashCode());
        assertThat(data1).isNotEqualTo(data3);
    }
}
