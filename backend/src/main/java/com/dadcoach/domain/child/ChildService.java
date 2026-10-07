package com.dadcoach.domain.child;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.domain.father.Father;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A father's children - the one place their rules live, for the add_child tool and the dashboard alike: a name of
 * 1-100 characters, unique (ignoring case) among his active children; a whole-year age 0-25 (stored as an
 * approximate birth date); gender boy/girl/other or none; at most {@value #MAX_CHILDREN} active children.
 */
@Service
@Transactional
public class ChildService {

    static final int MAX_CHILDREN = 8;
    static final int MAX_AGE_YEARS = 25;
    private static final Set<String> GENDERS = Set.of("boy", "girl", "other");

    private final ChildRepository children;
    private final Clock clock;

    public ChildService(ChildRepository children, Clock clock) {
        this.children = children;
        this.clock = clock;
    }

    public Child addChild(Father father, String name, int ageYears, String gender, List<String> interests) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new BusinessRuleViolationException("INVALID_NAME", "The child's name must be 1-100 characters");
        }
        if (ageYears < 0 || ageYears > MAX_AGE_YEARS) {
            throw new BusinessRuleViolationException("INVALID_AGE", "Age must be between 0 and " + MAX_AGE_YEARS);
        }
        String normalizedGender = null;
        if (gender != null && !gender.isBlank()) {
            normalizedGender = gender.trim().toLowerCase(Locale.ROOT);
            if (!GENDERS.contains(normalizedGender)) {
                throw new BusinessRuleViolationException("INVALID_GENDER", "Gender must be boy, girl or other");
            }
        }
        List<Child> active = children.findByFatherIdAndStatus(father.getId(), "ACTIVE");
        if (active.stream().anyMatch(c -> c.getName() != null && c.getName().equalsIgnoreCase(trimmed))) {
            throw new BusinessRuleViolationException("DUPLICATE_CHILD", "Child with this name already exists");
        }
        if (active.size() >= MAX_CHILDREN) {
            throw new BusinessRuleViolationException("MAX_CHILDREN_EXCEEDED", "At most " + MAX_CHILDREN + " children");
        }
        LocalDate today = LocalDate.now(clock);
        Child child = new Child(father, trimmed, today.minus(Period.ofYears(ageYears)));
        child.setGender(normalizedGender);
        if (interests != null && !interests.isEmpty()) {
            child.setInterests(interests);
        }
        return children.save(child);
    }

    public Child archiveChild(Father father, Long childId) {
        Child child = children.findById(childId).filter(c -> father.getId().equals(c.getFatherId()))
                .orElseThrow(() -> new BusinessRuleViolationException("NOT_FOUND", "Unknown child"));
        child.setStatus("ARCHIVED");
        child.setUpdatedAt(Instant.now(clock));
        return children.save(child);
    }

    @Transactional(readOnly = true)
    public List<Child> activeChildren(Long fatherId) {
        return children.findByFatherIdAndStatus(fatherId, "ACTIVE");
    }
}
