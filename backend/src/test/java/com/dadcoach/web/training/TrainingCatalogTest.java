package com.dadcoach.web.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrainingCatalogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);
    private static final TrainingVideo READY = new TrainingVideo("welcome", true, 0, "ברוך הבא", "", 60, "v1/welcome.mp4", "v1/welcome.jpg", true);
    private static final TrainingVideo NO_FILE = new TrainingVideo("belts", false, 1, "חגורות", "", 40, null, null, true);

    @Test
    @DisplayName("nothing is shown until the media host is set; then only active entries with a file")
    void availability() {
        assertThat(new TrainingCatalog(List.of(READY, NO_FILE), "", "", false, CLOCK).available()).isEmpty();
        assertThat(new TrainingCatalog(List.of(READY, NO_FILE), "https://cdn.example", "", false, CLOCK).available())
                .as("a host without a signing key serves nothing").isEmpty();
        TrainingCatalog lab = new TrainingCatalog(List.of(READY, NO_FILE), "https://cdn.example/", "", true, CLOCK);
        assertThat(lab.available()).extracting(TrainingVideo::slug).containsExactly("welcome");
        assertThat(lab.mediaUrl("v1/welcome.mp4")).isEqualTo("https://cdn.example/v1/welcome.mp4");
    }

    @Test
    @DisplayName("signed addresses: Bunny token + expiry")
    void signed() {
        String url = new TrainingCatalog(List.of(READY), "https://cdn.example", "key", false, CLOCK).mediaUrl("v1/welcome.mp4");
        long expires = Instant.parse("2030-01-01T12:00:00Z").getEpochSecond();
        assertThat(url).isEqualTo("https://cdn.example/v1/welcome.mp4?token="
                + TrainingCatalog.bunnyToken("key", "/v1/welcome.mp4" + expires) + "&expires=" + expires);
        assertThat(url).contains("token=HS256-");
    }

    @Test
    @DisplayName("a bad catalog fails startup")
    void validation() {
        assertThatThrownBy(() -> TrainingCatalog.validated(List.of(READY, READY))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TrainingCatalog.validated(List.of(new TrainingVideo("x", false, 0, "t", "", 1, "https://a/b.mp4", null, true))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TrainingCatalog.validated(List.of(new TrainingVideo("Bad Slug", false, 0, "t", "", 1, null, null, true))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the shipped catalog: valid, one primary, every active video on the CDN path the release uploads")
    void shippedCatalog() throws Exception {
        List<TrainingVideo> shipped;
        try (InputStream in = getClass().getResourceAsStream("/training/catalog.json")) {
            shipped = new ObjectMapper().readValue(in, new TypeReference<List<TrainingVideo>>() { });
        }
        List<TrainingVideo> catalog = TrainingCatalog.validated(shipped);
        assertThat(catalog).filteredOn(TrainingVideo::primary).extracting(TrainingVideo::slug).containsExactly("welcome");
        assertThat(catalog).filteredOn(TrainingVideo::active).isNotEmpty().allSatisfy(v -> {
            assertThat(v.video()).isEqualTo("dad-coach/training/v1/father-" + v.slug() + ".mp4");
            assertThat(v.poster()).isEqualTo("dad-coach/training/v1/father-" + v.slug() + ".jpg");
            assertThat(v.durationSeconds()).isBetween(20, 90);
        });
    }
}
