package com.dadcoach.api.tools;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.api.error.ValidationException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.child.ChildService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.qualitytime.ActivityIdeas;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The father's profile, his children and activity ideas. */
public final class FatherTools {

    private FatherTools() {
    }

    /**
     * Creates the father on his first save (WhatsApp onboarding, D-002) - with his WhatsApp endpoint and open
     * window - or updates his profile. An invalid timezone is never stored.
     */
    @Component
    public static class SaveUserProfile implements ToolHandler {
        private final FatherService fathers;
        private final FatherRepository fatherRepository;

        public SaveUserProfile(FatherService fathers, FatherRepository fatherRepository) {
            this.fathers = fathers;
            this.fatherRepository = fatherRepository;
        }

        public String toolKey() { return "save_user_profile"; }
        public boolean sideEffecting() { return true; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            String displayName = p.str("displayName");
            if (displayName == null) {
                throw new ValidationException("displayName is required");
            }
            if (displayName.length() > 120) {
                throw new ValidationException("displayName is too long (max 120)");
            }
            Father father = actor.father().orElseGet(() -> fathers.findOrCreateFromWhatsApp(actor.phone()));
            father.setDisplayName(displayName);
            String timezone = p.str("timezone");
            if (timezone != null) {
                try {
                    father.setTimezone(ZoneId.of(timezone).getId());
                } catch (DateTimeException e) {
                    throw new ValidationException("timezone must be an IANA zone such as Asia/Jerusalem");
                }
            }
            String locale = p.str("locale");
            if (locale != null && locale.length() <= 10) {
                father.setLocale(locale);
            }
            String time = p.str("preferredCoachingTime");
            if (time != null) {
                try {
                    father.setPreferredCoachingTime(LocalTime.parse(time));
                } catch (DateTimeParseException e) {
                    throw new ValidationException("preferredCoachingTime must be HH:mm");
                }
            }
            father = fatherRepository.save(father);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fatherId", father.getId());
            data.put("displayName", father.getDisplayName());
            data.put("timezone", father.getTimezone());
            data.put("locale", father.getLocale());
            if (father.getPreferredCoachingTime() != null) {
                data.put("preferredCoachingTime", father.getPreferredCoachingTime().toString());
            }
            return data;
        }
    }

    /** Adds a child (ChildService rules); the first child completes onboarding (ACTIVE). */
    @Component
    public static class AddChild implements ToolHandler {
        private final ChildService children;
        private final ChildRepository childRepository;
        private final FatherService fathers;

        public AddChild(ChildService children, ChildRepository childRepository, FatherService fathers) {
            this.children = children;
            this.childRepository = childRepository;
            this.fathers = fathers;
        }

        public String toolKey() { return "add_child"; }
        public boolean sideEffecting() { return true; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            Father father = actor.requireFather();
            String name = p.requiredStr("name");
            Integer age = p.intValue("age");
            if (age == null) {
                throw new ValidationException("age is required");
            }
            Child saved = children.addChild(father, name, age, p.str("gender"), p.strings("interests"));
            fathers.activateIfOnboarding(father);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("childId", saved.getId().toString());
            data.put("name", saved.getName());
            data.put("age", saved.getAge());
            if (saved.getGender() != null) {
                data.put("gender", saved.getGender());
            }
            if (saved.getInterests() != null && !saved.getInterests().isEmpty()) {
                data.put("interests", saved.getInterests());
            }
            data.put("createdAt", saved.getCreatedAt() == null ? null : saved.getCreatedAt().toString());
            data.put("status", "added");
            data.put("childCount", childRepository.countActiveByFatherId(father.getId()));
            return data;
        }
    }

    /** Ideas for a session with one of his children (or a general set without a child). Read-only. */
    @Component
    public static class GetActivityIdeas implements ToolHandler {
        private final ChildRepository childRepository;

        public GetActivityIdeas(ChildRepository childRepository) {
            this.childRepository = childRepository;
        }

        public String toolKey() { return "get_activity_ideas"; }
        public boolean sideEffecting() { return false; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            Father father = actor.requireFather();
            Long childId = p.longValue("child_id");
            int age = 5;
            if (childId != null) {
                Child child = childRepository.findById(childId).filter(c -> father.getId().equals(c.getFatherId()))
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown child_id " + childId));
                age = child.getBirthDate() == null ? 5 : child.getAge();
            }
            List<Map<String, Object>> ideas = new ArrayList<>();
            for (ActivityIdeas.ActivityIdea idea : ActivityIdeas.forChild(age,
                    father.getLocale() == null ? "he" : father.getLocale(), p.str("activity_type"))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", idea.title());
                m.put("description", idea.description());
                m.put("duration_minutes", idea.durationMinutes());
                m.put("indoor", idea.indoor());
                ideas.add(m);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ideas", ideas);
            data.put("child_age", age);
            return data;
        }
    }
}
