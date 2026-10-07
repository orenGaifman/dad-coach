package com.dadcoach.web.father;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.web.common.WebException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ילדים: the same rules as the AI's add_child tool - a name of 1-100 characters, unique (ignoring case) among his
 * children, an age of 0-25 stored as an approximate birth date (whole years). Editing keeps the same rules.
 */
@Service
public class ChildrenService {

    static final int MAX_CHILDREN = 8;

    public record ChildView(Long id, String name, int age, Integer birthYear) {
    }

    private final ChildRepository children;
    private final WeeklyGoalService weeklyGoals;
    private final Clock clock;

    public ChildrenService(ChildRepository children, WeeklyGoalService weeklyGoals, Clock clock) {
        this.children = children;
        this.weeklyGoals = weeklyGoals;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ChildView> list(Father father) {
        return children.findByFatherIdAndStatus(father.getId(), "ACTIVE").stream()
                .sorted(Comparator.comparing(Child::getBirthDate).thenComparing(Child::getId))
                .map(ChildrenService::view)
                .toList();
    }

    @Transactional
    public ChildView add(Father father, String rawName, int age) {
        String name = validName(rawName);
        validAge(age);
        List<Child> mine = children.findByFatherIdAndStatus(father.getId(), "ACTIVE");
        if (mine.size() >= MAX_CHILDREN) {
            throw WebException.conflict("TOO_MANY_CHILDREN", "at most " + MAX_CHILDREN + " children");
        }
        if (mine.stream().anyMatch(c -> c.getName() != null && c.getName().equalsIgnoreCase(name))) {
            throw WebException.conflict("DUPLICATE_CHILD", "a child with this name exists");
        }
        Child child = new Child(father, name, birthDateFor(father, age));
        return view(children.save(child));
    }

    @Transactional
    public ChildView update(Father father, Long childId, String rawName, Integer age) {
        Child child = children.findById(childId)
                .filter(c -> father.getId().equals(c.getFatherId()) && "ACTIVE".equals(c.getStatus()))
                .orElseThrow(WebException::notFound);
        if (rawName != null) {
            String name = validName(rawName);
            boolean taken = children.findByFatherIdAndStatus(father.getId(), "ACTIVE").stream()
                    .anyMatch(c -> !c.getId().equals(childId) && c.getName() != null && c.getName().equalsIgnoreCase(name));
            if (taken) {
                throw WebException.conflict("DUPLICATE_CHILD", "a child with this name exists");
            }
            child.setName(name);
        }
        if (age != null && age != child.getAge()) {
            validAge(age);
            child.setBirthDate(birthDateFor(father, age));
        }
        return view(children.save(child));
    }

    private LocalDate birthDateFor(Father father, int age) {
        return LocalDate.now(clock.withZone(weeklyGoals.zoneFor(father))).minusYears(age);
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.length() > 100) {
            throw WebException.badRequest("INVALID_NAME", "a name of 1-100 characters");
        }
        return name;
    }

    private static void validAge(int age) {
        if (age < 0 || age > 25) {
            throw WebException.badRequest("INVALID_AGE", "age 0-25");
        }
    }

    private static ChildView view(Child child) {
        return new ChildView(child.getId(), child.getName(), child.getAge(),
                child.getBirthDate() == null ? null : child.getBirthDate().getYear());
    }
}
