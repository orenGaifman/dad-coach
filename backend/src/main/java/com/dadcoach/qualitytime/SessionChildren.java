package com.dadcoach.qualitytime;

import com.dadcoach.domain.child.Child;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The names of a session's children as every surface shows them - one session can be with several children
 * ("מטר ונעם", "איתמר, מטר ונעם").
 */
public final class SessionChildren {

    private SessionChildren() {
    }

    /**
     * The session's children's names, first child first. A name comes from {@code knownNames} (child id → name, e.g.
     * his active children) when it is there, otherwise from the child row itself.
     */
    public static List<String> names(QualityTime session, Map<Long, String> knownNames) {
        List<String> names = new ArrayList<>();
        for (Child child : session.getChildren()) {
            String name = knownNames == null ? null : knownNames.get(child.getId());
            if (name == null) {
                name = child.getName();
            }
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    public static List<String> names(QualityTime session) {
        return names(session, null);
    }

    /** The names joined in Hebrew, or null when there are none. */
    public static String hebrew(QualityTime session, Map<Long, String> knownNames) {
        return joinHebrew(names(session, knownNames));
    }

    public static String hebrew(QualityTime session) {
        return hebrew(session, null);
    }

    /** "מטר", "מטר ונעם", "איתמר, מטר ונעם"; null for none. */
    public static String joinHebrew(List<String> names) {
        return join(names, ", ", " ו");
    }

    /** "Matar", "Matar and Noam", "Itamar, Matar and Noam"; null for none. */
    public static String joinEnglish(List<String> names) {
        return join(names, ", ", " and ");
    }

    private static String join(List<String> names, String separator, String last) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(separator, names.subList(0, names.size() - 1)) + last + names.get(names.size() - 1);
    }
}
