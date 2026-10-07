package com.dadcoach.api.tools;

import com.dadcoach.api.error.ValidationException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Lenient access to tool parameters (playbook §11.2): a parameter the model omits may arrive as "" or "null", not
 * null; numbers may arrive as strings. A date-time without an offset is read in the father's timezone, never the
 * server's.
 */
public final class ToolParams {

    private final Map<String, Object> values;
    private final ZoneId zone;

    public ToolParams(Map<String, Object> values, ZoneId zone) {
        this.values = values == null ? Map.of() : values;
        this.zone = zone;
    }

    public Map<String, Object> raw() {
        return values;
    }

    public String str(String name) {
        Object v = values.get(name);
        if (v == null) {
            return null;
        }
        String s = v.toString().strip();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    public String requiredStr(String name) {
        String s = str(name);
        if (s == null) {
            throw new ValidationException(name + " is required");
        }
        return s;
    }

    public Long longValue(String name) {
        Object v = values.get(name);
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = str(name);
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ValidationException(name + " must be a whole number");
        }
    }

    public Integer intValue(String name) {
        Long l = longValue(name);
        return l == null ? null : Math.toIntExact(l);
    }

    public Instant instant(String name) {
        String s = str(name);
        if (s == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(s).atZone(zone).toInstant();
            } catch (DateTimeParseException e) {
                throw new ValidationException(name + " must be an ISO-8601 date-time, e.g. 2026-11-03T17:30:00+02:00");
            }
        }
    }

    /** A JSON array of strings, or a comma-separated string; empty when absent. */
    public List<String> strings(String name) {
        Object v = values.get(name);
        List<String> out = new ArrayList<>();
        if (v instanceof Collection<?> items) {
            for (Object item : items) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString().strip());
                }
            }
        } else if (v != null) {
            for (String part : v.toString().split(",")) {
                if (!part.isBlank() && !"null".equals(part.strip())) {
                    out.add(part.strip());
                }
            }
        }
        return out;
    }
}
