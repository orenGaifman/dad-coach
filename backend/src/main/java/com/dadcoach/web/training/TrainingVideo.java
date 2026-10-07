package com.dadcoach.web.training;

/**
 * One entry of the training catalog ({@code training/catalog.json}, Big Boss D-171). {@code video} and {@code poster}
 * are paths under the media host; an entry with no file yet is listed for the team only.
 */
public record TrainingVideo(String slug, boolean primary, int order, String title, String description,
                            int durationSeconds, String video, String poster, boolean active) {

    public boolean ready() {
        return active && video != null && !video.isBlank();
    }
}
