package com.dadcoach.web.father;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the session references out of the weekly plan (weekly_plan_context): every session line ends with
 * {@code id=<uuid>} and starts with its phase. Which sessions the home lists, and in which phase, is therefore
 * exactly what the coach sees.
 */
final class WeekTruth {

    record Ref(UUID id, String phase) {
    }

    private WeekTruth() {
    }

    /** A "none" or a map of lines -> the refs, in the plan's order. */
    static List<Ref> refs(Object section) {
        List<Ref> refs = new ArrayList<>();
        if (section instanceof Map<?, ?> lines) {
            for (Object line : lines.values()) {
                ref(line).ifPresent(refs::add);
            }
        } else {
            ref(section).ifPresent(refs::add);
        }
        return refs;
    }

    static java.util.Optional<Ref> ref(Object line) {
        if (!(line instanceof String text)) {
            return java.util.Optional.empty();
        }
        int idAt = text.lastIndexOf("id=");
        int bar = text.indexOf(" | ");
        if (idAt < 0 || bar < 0) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new Ref(UUID.fromString(text.substring(idAt + 3).trim()), text.substring(0, bar).trim()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static Integer integer(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number n ? n.intValue() : null;
    }
}
