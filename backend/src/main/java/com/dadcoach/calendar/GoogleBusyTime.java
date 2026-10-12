package com.dadcoach.calendar;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * D-056: which Google Calendar events take up a father's time - the one rule behind the free-slot suggestions and the
 * calendar-conflict line of a booking. Only a timed event he is really in counts: an all-day event (a birthday, a
 * holiday, "working from home"), an event marked "free" (transparent), a cancelled one, an invitation he declined and
 * Dad Coach's own session events (his sessions are known here, a leftover one is not a meeting) never take time.
 */
public final class GoogleBusyTime {

    /** The private extended property on every event Dad Coach creates. */
    public static final String MARKER = "dadCoach";
    /** The descriptions Dad Coach gave its events before the marker (both locales; Hebrew before and after 2026-10-07). */
    private static final java.util.List<String> OWN_DESCRIPTIONS = java.util.List.of(
            "דאד קואץ׳ - זמן איכות מתוכנן", "Dad Coach - זמן איכות מתוכנן", "Dad Coach scheduled Quality Time");

    private GoogleBusyTime() {
    }

    /** @param item one entry of Google's {@code events.list} {@code items} */
    public static boolean blocksTime(JsonNode item) {
        if (!item.path("start").has("dateTime") || !item.path("end").has("dateTime")) {
            return false;
        }
        if ("transparent".equals(item.path("transparency").asText()) || "cancelled".equals(item.path("status").asText())) {
            return false;
        }
        if (isDadCoachEvent(item)) {
            return false;
        }
        for (JsonNode attendee : item.path("attendees")) {
            if (attendee.path("self").asBoolean(false) && "declined".equals(attendee.path("responseStatus").asText())) {
                return false;
            }
        }
        return true;
    }

    /** An event Dad Coach created: its marker, or (older events) its own description. */
    public static boolean isDadCoachEvent(JsonNode item) {
        if ("1".equals(item.path("extendedProperties").path("private").path(MARKER).asText())) {
            return true;
        }
        String description = item.path("description").asText("");
        return OWN_DESCRIPTIONS.stream().anyMatch(description::startsWith);
    }
}
