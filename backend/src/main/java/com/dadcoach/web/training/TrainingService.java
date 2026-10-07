package com.dadcoach.web.training;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** A father's training library and what he watched: always the catalog, filtered. */
@Service
public class TrainingService {

    public record VideoView(String slug, boolean primary, String title, String description, int durationSeconds,
                            String videoUrl, String posterUrl, boolean started, boolean completed) {
    }

    public record LibraryView(boolean available, List<VideoView> videos) {
    }

    public record AdminVideoView(String slug, boolean primary, int order, String title, String description,
                                 int durationSeconds, boolean active, boolean hasFile) {
    }

    public record AdminView(boolean mediaConfigured, List<AdminVideoView> videos) {
    }

    private final TrainingCatalog catalog;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TrainingService(TrainingCatalog catalog, JdbcTemplate jdbc, Clock clock) {
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Whether the father's "הדרכה" exists at all (the SPA hides it otherwise). */
    public boolean available() {
        return !catalog.available().isEmpty();
    }

    public LibraryView library(long fatherId) {
        Map<String, boolean[]> watched = new HashMap<>();
        jdbc.query("SELECT slug, started_at, completed_at FROM training_progress WHERE father_id = ?",
                rs -> {
                    watched.put(rs.getString("slug"), new boolean[] {rs.getTimestamp("started_at") != null,
                            rs.getTimestamp("completed_at") != null});
                }, fatherId);
        List<VideoView> videos = catalog.available().stream().map(v -> {
            boolean[] w = watched.getOrDefault(v.slug(), new boolean[2]);
            return new VideoView(v.slug(), v.primary(), v.title(), v.description(), v.durationSeconds(),
                    catalog.mediaUrl(v.video()), catalog.mediaUrl(v.poster()), w[0] || w[1], w[1]);
        }).toList();
        return new LibraryView(!videos.isEmpty(), videos);
    }

    /** @return false for a video he cannot see (the controller answers 404). */
    public boolean record(long fatherId, String slug, boolean completed) {
        if (catalog.findAvailable(slug).isEmpty()) {
            return false;
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO training_progress (father_id, slug, started_at, completed_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (father_id, slug) DO UPDATE SET
                  started_at = COALESCE(training_progress.started_at, EXCLUDED.started_at),
                  completed_at = COALESCE(training_progress.completed_at, EXCLUDED.completed_at)""",
                fatherId, slug, now, completed ? now : null);
        return true;
    }

    public AdminView admin() {
        return new AdminView(catalog.mediaConfigured(), catalog.all().stream().map(v -> new AdminVideoView(v.slug(),
                v.primary(), v.order(), v.title(), v.description(), v.durationSeconds(), v.active(),
                v.video() != null && !v.video().isBlank())).toList());
    }
}
