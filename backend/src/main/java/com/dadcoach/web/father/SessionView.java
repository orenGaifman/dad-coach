package com.dadcoach.web.father;

import java.time.Instant;

/**
 * One quality-time session as the dashboard shows it. {@code childId} is the session's first child, {@code childIds}
 * all its children and {@code childName} all their names joined in Hebrew ("מטר ונעם"). {@code phase} is the weekly plan's phase (UPCOMING,
 * IN_PROGRESS, AWAITING_CONFIRMATION, COMPLETED, CANCELLED, MISSED); local date/time in the father's timezone.
 */
public record SessionView(
        String id,
        Long childId,
        String childName,
        java.util.List<Long> childIds,
        String phase,
        Instant start,
        Instant end,
        String localDate,
        String weekday,
        String localStart,
        String localEnd,
        int durationMinutes,
        String notes,
        boolean canConfirm,
        boolean canCancel) {
}
