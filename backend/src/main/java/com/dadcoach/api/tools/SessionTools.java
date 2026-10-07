package com.dadcoach.api.tools;

import com.dadcoach.api.error.ValidationException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.dto.CompleteQualityTimeResult;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.systemstate.AvailableSlotFinder;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The quality-time session tools. Booking works with or without Google Calendar (D-007): the session lives in Dad
 * Coach; a calendar event is created only when his calendar is connected.
 */
public final class SessionTools {

    static final int DEFAULT_DURATION_MINUTES = 30;

    private SessionTools() {
    }

    static int duration(Integer minutes) {
        int d = minutes == null ? DEFAULT_DURATION_MINUTES : minutes;
        if (d < 5 || d > 600) {
            throw new ValidationException("duration_minutes must be between 5 and 600");
        }
        return d;
    }

    static Child ownChild(ChildRepository children, Father father, Long childId) {
        if (childId == null) {
            throw new ValidationException("child_id is required");
        }
        return children.findById(childId).filter(c -> father.getId().equals(c.getFatherId()))
                .orElseThrow(() -> new com.dadcoach.api.error.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "NOT_FOUND", "Unknown child_id " + childId + " - use childId from family_context"));
    }

    static void putBooking(Map<String, Object> data, ScheduleQualityTimeResult result) {
        data.put("calendar_event_id", result.calendarEventId());
        data.put("calendar_event_created", result.calendarEventId() != null);
        if (result.calendarError() != null) {
            data.put("calendar_error", result.calendarError());
        }
        data.put("child_name", result.childName());
    }

    @Component
    public static class Schedule implements ToolHandler {
        private final QualityTimeService sessions;
        private final ChildRepository children;
        private final SessionViews views;

        public Schedule(QualityTimeService sessions, ChildRepository children, SessionViews views) {
            this.sessions = sessions;
            this.children = children;
            this.views = views;
        }

        public String toolKey() { return "schedule_quality_time"; }
        public boolean sideEffecting() { return true; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            Father father = actor.requireFather();
            Child child = ownChild(children, father, p.longValue("child_id"));
            Instant start = p.instant("start_time");
            if (start == null) {
                throw new ValidationException("start_time is required");
            }
            ScheduleQualityTimeResult result = sessions.scheduleQualityTime(father.getId(), child.getId(), start,
                    Duration.ofMinutes(duration(p.intValue("duration_minutes"))));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("quality_time_id", result.qualityTimeId().toString());
            putBooking(data, result);
            data.put("start_time", result.startTime().toString());
            data.put("end_time", result.endTime().toString());
            data.put("status", result.status().name());
            views.putSessionTimers(data, father, result.startTime(), result.endTime());
            views.putWeekCoverage(data, father);
            return data;
        }
    }

    @Component
    public static class Reschedule implements ToolHandler {
        private final QualityTimeService sessions;
        private final SessionViews views;

        public Reschedule(QualityTimeService sessions, SessionViews views) {
            this.sessions = sessions;
            this.views = views;
        }

        public String toolKey() { return "reschedule_quality_time"; }
        public boolean sideEffecting() { return true; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            Father father = actor.requireFather();
            QualityTime existing = views.ownSession(father, p.requiredStr("quality_time_id"));
            Instant start = p.instant("new_start_time");
            if (start == null) {
                throw new ValidationException("new_start_time is required");
            }
            Integer minutes = p.intValue("new_duration_minutes");
            int duration = duration(minutes != null ? minutes
                    : (int) Duration.between(existing.getScheduledStart(), existing.getScheduledEnd()).toMinutes());
            // one transaction (the controller's): if the new time is refused, the old session stays as it was
            sessions.cancelQualityTime(existing.getId());
            ScheduleQualityTimeResult result = sessions.scheduleQualityTime(father.getId(), existing.getChildId(), start,
                    Duration.ofMinutes(duration));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("old_quality_time_id", existing.getId().toString());
            data.put("new_quality_time_id", result.qualityTimeId().toString());
            putBooking(data, result);
            data.put("new_start_time", result.startTime().toString());
            data.put("new_end_time", result.endTime().toString());
            data.put("status", result.status().name());
            views.putSessionTimers(data, father, result.startTime(), result.endTime());
            views.putWeekCoverage(data, father);
            return data;
        }
    }

    @Component
    public static class Cancel implements ToolHandler {
        private final QualityTimeService sessions;
        private final SessionViews views;

        public Cancel(QualityTimeService sessions, SessionViews views) {
            this.sessions = sessions;
            this.views = views;
        }

        public String toolKey() { return "cancel_quality_time"; }
        public boolean sideEffecting() { return true; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            Father father = actor.requireFather();
            QualityTime existing = views.ownSession(father, p.requiredStr("quality_time_id"));
            sessions.cancelQualityTime(existing.getId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("quality_time_id", existing.getId().toString());
            data.put("status", "CANCELLED");
            views.putWeekCoverage(data, father);
            return data;
        }
    }

    @Component
    public static class Complete implements ToolHandler {
        private final QualityTimeService sessions;
        private final SessionViews views;

        public Complete(QualityTimeService sessions, SessionViews views) {
            this.sessions = sessions;
            this.views = views;
        }

        public String toolKey() { return "complete_quality_time"; }
        public boolean sideEffecting() { return true; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            Father father = actor.requireFather();
            QualityTime existing = views.ownSession(father, p.requiredStr("quality_time_id"));
            CompleteQualityTimeResult result = sessions.completeQualityTime(existing.getId(), p.str("notes"));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("quality_time_id", result.qualityTimeId().toString());
            data.put("status", result.status().name());
            data.put("new_streak", result.newStreak());
            data.put("current_belt", result.currentBelt().name());
            data.put("points_awarded", result.pointsAwarded());
            if (result.beltEarned() != null) {
                data.put("belt_earned", result.beltEarned().name());
            }
            views.putWeekCoverage(data, father);
            return data;
        }
    }

    @Component
    public static class AvailableSlots implements ToolHandler {
        private final AvailableSlotFinder slots;
        private final SessionViews views;

        public AvailableSlots(AvailableSlotFinder slots, SessionViews views) {
            this.slots = slots;
            this.views = views;
        }

        public String toolKey() { return "show_available_slots"; }
        public boolean sideEffecting() { return false; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            Father father = actor.requireFather();
            Integer days = p.intValue("days_ahead");
            int daysAhead = days == null || days < 1 ? 7 : Math.min(days, 14);
            ZoneId zone = views.zone(father);
            List<Map<String, Object>> list = new ArrayList<>();
            for (AvailableSlot slot : slots.find(father, zone, daysAhead)) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("start_time", slot.startTime().toString());
                s.put("end_time", slot.endTime().toString());
                s.put("duration_minutes", slot.durationMinutes());
                s.put("local_date", slot.startTime().atZone(zone).toLocalDate().toString());
                s.put("weekday", slot.startTime().atZone(zone).getDayOfWeek().name());
                s.put("local_start", slot.startTime().atZone(zone).toLocalTime().withSecond(0).withNano(0).toString());
                s.put("local_end", slot.endTime().atZone(zone).toLocalTime().withSecond(0).withNano(0).toString());
                list.add(s);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("slots", list);
            data.put("calendar_connected", father.hasGoogleCalendarConfigured());
            data.put("timezone", zone.getId());
            data.put("note", father.hasGoogleCalendarConfigured()
                    ? "Free family-time windows, minus his booked sessions and his calendar's busy times."
                    : "Free family-time windows (weekday evenings, weekend mornings/afternoons), minus his booked sessions. "
                            + "No calendar is connected: any time he names can be booked too.");
            return data;
        }
    }
}
