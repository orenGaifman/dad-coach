package com.dadcoach.api.context;

import com.dadcoach.common.PhoneValidator;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.FatherTimezones;
import com.dadcoach.weeklyplan.WeeklyPlanContextBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/context/{providerKey}} - the two providers bound in dad-coach-3 (playbook §12), loaded fresh on
 * every turn: {@code family_context} (profile + children) and {@code weekly_plan_context} (this week in his
 * timezone, {@link WeeklyPlanContextBuilder}). The father is identified only by his WhatsApp number in
 * {@code config.phone} (how the platform's Dad Coach context client sends a channel identity); a numeric user_id is
 * never trusted. An unknown or deleted father is a legitimate empty answer (200, father_found=false), not an error.
 * The envelope {success, data, error_code, error_message} is what the platform's client parses.
 */
@RestController
public class ContextProviderController {

    private static final Logger log = LoggerFactory.getLogger(ContextProviderController.class);
    private static final Pattern CHANNEL_QUALIFIED = Pattern.compile("^([A-Za-z][A-Za-z0-9_.-]*):(.+)$");

    private final FatherRepository fathers;
    private final ChildRepository children;
    private final WeeklyPlanContextBuilder weeklyPlan;

    public ContextProviderController(FatherRepository fathers, ChildRepository children, WeeklyPlanContextBuilder weeklyPlan) {
        this.fathers = fathers;
        this.children = children;
        this.weeklyPlan = weeklyPlan;
    }

    @PostMapping("/api/context/{providerKey}")
    public ContextProviderResponse load(@PathVariable String providerKey, @RequestBody ContextProviderRequest request) {
        Instant started = Instant.now();
        String result = "SUCCESS";
        try {
            Optional<Father> father = lookup(request);
            ContextProviderResponse response = switch (providerKey) {
                case "family_context" -> ContextProviderResponse.success(family(father));
                case "weekly_plan_context" -> ContextProviderResponse.success(weekly(father));
                default -> {
                    result = "PROVIDER_NOT_FOUND";
                    yield ContextProviderResponse.providerNotFound(providerKey);
                }
            };
            if (father.isEmpty() && response.success()) {
                result = "EMPTY";
            }
            return response;
        } catch (RuntimeException e) {
            result = "ERROR";
            log.atError().setMessage("context.provider.failed").setCause(e).addKeyValue("providerKey", providerKey).log();
            return ContextProviderResponse.failure("Context unavailable", "INTERNAL_ERROR");
        } finally {
            log.atInfo().setMessage("context.provider.result")
                    .addKeyValue("providerKey", providerKey)
                    .addKeyValue("result", result)
                    .addKeyValue("durationMs", Duration.between(started, Instant.now()).toMillis())
                    .log();
        }
    }

    Optional<Father> lookup(ContextProviderRequest request) {
        String phone = request.getStringConfig("phone");
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        String identifier = phone.trim();
        Matcher m = CHANNEL_QUALIFIED.matcher(identifier);
        if (m.matches()) {
            identifier = m.group(2);
        }
        String e164 = PhoneValidator.normalizeToE164(identifier);
        return fathers.findByPhone(e164).filter(f -> f.getStatus() != FatherStatus.DELETED);
    }

    private Map<String, Object> family(Optional<Father> found) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (found.isEmpty()) {
            data.put("father_profile", null);
            data.put("children", List.of());
            data.put("has_multiple_children", false);
            data.put("has_google_calendar_connected", false);
            data.put("children_count", 0);
            return data;
        }
        Father father = found.get();
        List<Child> active = children.findByFatherIdAndStatus(father.getId(), "ACTIVE");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("father_id", father.getId());
        profile.put("display_name", father.getDisplayName());
        profile.put("locale", father.getLocale() != null ? father.getLocale() : "he");
        profile.put("timezone", FatherTimezones.of(father).getId());
        profile.put("status", father.getStatus().name());
        data.put("father_profile", profile);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Child child : active) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("child_id", child.getId());
            c.put("name", child.getName());
            c.put("age", child.getAge());
            c.put("gender", child.getGender());
            c.put("interests", child.getInterests() != null ? child.getInterests() : List.of());
            c.put("developmental_bracket", child.getDevelopmentalBracket().name());
            list.add(c);
        }
        data.put("children", list);
        data.put("has_multiple_children", active.size() > 1);
        data.put("has_google_calendar_connected", father.hasGoogleCalendarConfigured());
        data.put("children_count", active.size());
        return data;
    }

    private Map<String, Object> weekly(Optional<Father> found) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("father_found", found.isPresent());
        found.ifPresent(f -> data.putAll(weeklyPlan.build(f)));
        return data;
    }
}
