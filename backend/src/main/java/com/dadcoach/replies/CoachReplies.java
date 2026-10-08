package com.dadcoach.replies;

import com.dadcoach.weeklyplan.HebrewHours;
import com.dadcoach.weeklyplan.SessionTimerPlanner;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The coach's factual answers, written in code from this turn's data (D-036): what was booked, moved, cancelled or
 * recorded, the week, the reminders, the goal. The model copies them ("your reply is exactly the ready answer");
 * the product checks the copy and falls back to these exact lines. Production showed why (review 2, 2026-10-08):
 * invented reminders ("ועוד אחת ב-17:00"), "אעדכן אותך בבוקר" for a 09:00 session that has no morning reminder, the
 * week in minutes, English day names. Voice: a short chat with a friend - the father masculine singular, the time
 * bold, hours not minutes, at most one emoji per message (🙂 opening/reminder, 🎉 booked, 💪 progress).
 */
public final class CoachReplies {

    public static final String IDENTITY = "❤️ דאד קואץ׳:\n";

    private CoachReplies() {
    }

    /** This week's numbers (the weekly plan's coverage, after the change). */
    public record Week(Integer goalMinutes, int completed, int planned) {

        public int covered() {
            return completed + planned;
        }

        public int uncovered() {
            return goalMinutes == null ? 0 : Math.max(0, goalMinutes - covered());
        }

        public boolean hasGoal() {
            return goalMinutes != null;
        }

        public boolean isCovered() {
            return goalMinutes != null && covered() >= goalMinutes;
        }

        /** From the weekly plan's {@code coverage} map; null when it is missing. */
        public static Week of(Object coverage) {
            if (!(coverage instanceof Map<?, ?> c)) {
                return null;
            }
            Integer goal = c.get("target_minutes") instanceof Number n ? n.intValue() : null;
            int completed = c.get("completed_minutes") instanceof Number n ? n.intValue() : 0;
            int planned = c.get("planned_minutes") instanceof Number n ? n.intValue() : 0;
            return new Week(goal, completed, planned);
        }
    }

    // ---- sessions -------------------------------------------------------------------------------------------------

    /** "קבעתי 🎉 *מחר, יום שישי 9.10 ב-09:00*, שעה וחצי עם מטר ונעם." + what comes next + the week. */
    public static List<String> booked(String when, int minutes, String children, Collection<String> timerKeys, Week week) {
        List<String> lines = new ArrayList<>();
        lines.add("קבעתי 🎉 *" + when + "*, " + HebrewHours.of(minutes) + with(children) + ".");
        addIfPresent(lines, timersLine(timerKeys));
        addIfPresent(lines, weekLine(week));
        return lines;
    }

    /** A moved session: the same three lines, no emoji. */
    public static List<String> moved(String when, int minutes, String children, Collection<String> timerKeys, Week week) {
        List<String> lines = new ArrayList<>();
        lines.add("הזזתי ל*" + when + "*, " + HebrewHours.of(minutes) + with(children) + ".");
        addIfPresent(lines, timersLine(timerKeys));
        addIfPresent(lines, weekLine(week));
        return lines;
    }

    /** A child added to his session at the same time: one session with all of them. */
    public static List<String> joined(String child, String when, String children) {
        return List.of("הוספתי את " + child + " למפגש של *" + when + "*.", "עכשיו זה מפגש אחד עם " + children + ".");
    }

    public static List<String> alreadyInSession(String child, String when) {
        return List.of(child + " כבר במפגש של *" + when + "*.");
    }

    /** What the session's timers will do: only the timers really planned (keys of SessionTimerPlanner.plan). */
    public static String timersLine(Collection<String> timerKeys) {
        if (timerKeys == null) {
            return null;
        }
        List<String> remind = new ArrayList<>();
        if (timerKeys.contains(SessionTimerPlanner.MORNING_REMINDER)) {
            remind.add("בבוקר");
        }
        if (timerKeys.contains(SessionTimerPlanner.REMINDER_1H)) {
            remind.add("שעה לפני");
        }
        boolean followUp = timerKeys.contains(SessionTimerPlanner.FOLLOW_UP);
        if (remind.isEmpty()) {
            return followUp ? "אשאל אחר כך איך היה." : null;
        }
        return "אזכיר לך " + String.join(" ו", remind) + (followUp ? ", ואשאל אחר כך איך היה." : ".");
    }

    /** "השבוע מכוסה." / "השבוע: שעה וחצי מתוך 3 שעות."; null without a goal. */
    public static String weekLine(Week week) {
        if (week == null || !week.hasGoal()) {
            return null;
        }
        return week.isCovered() ? "השבוע מכוסה."
                : "השבוע: " + HebrewHours.of(week.covered()) + " מתוך " + HebrewHours.of(week.goalMinutes()) + ".";
    }

    /** "היה מעולה" / complete_quality_time: what was recorded, the week, and a belt only when one was earned now. */
    public static List<String> done(String children, Week week, String beltEarned) {
        List<String> lines = new ArrayList<>();
        lines.add("איזה כיף! רשמתי את הזמן שלך" + with(children) + ".");
        String progress = null;
        if (week != null) {
            progress = "השבוע: " + HebrewHours.of(week.completed())
                    + (week.hasGoal() ? " מתוך " + HebrewHours.of(week.goalMinutes()) : "");
        }
        if (beltEarned != null) {
            addIfPresent(lines, progress == null ? null : progress + ".");
            lines.add("עלית ל*" + beltEarned + "* 💪");
        } else {
            addIfPresent(lines, progress == null ? null : progress + " 💪");
        }
        return lines;
    }

    public static List<String> cancelled(String when, String children, Week week) {
        List<String> lines = new ArrayList<>();
        lines.add("ביטלתי את המפגש של *" + when + "*" + with(children) + ".");
        addIfPresent(lines, gapLine(week));
        return lines;
    }

    public static List<String> missed(String children, Week week) {
        List<String> lines = new ArrayList<>(List.of("קורה, העיקר שממשיכים.", "רשמתי שהמפגש" + with(children) + " לא יצא."));
        addIfPresent(lines, gapLine(week));
        return lines;
    }

    /** "השבוע חסרות עוד 3 שעות ליעד." / "השבוע חסרה עוד שעה ליעד."; null without a goal or when covered. */
    public static String gapLine(Week week) {
        if (week == null || !week.hasGoal() || week.isCovered()) {
            return null;
        }
        String hours = HebrewHours.of(week.uncovered());
        return "השבוע " + (missing(week.uncovered()).startsWith("חסרה") ? "חסרה" : "חסרות") + " עוד " + hours + " ליעד.";
    }

    public static List<String> completionUndone(String children) {
        return List.of("תיקנתי: המפגש" + with(children) + " רשום עכשיו כמפגש שלא יצא.");
    }

    // ---- goal and children ----------------------------------------------------------------------------------------

    public static String goalSet(int hours) {
        return "סגרנו: השבוע " + HebrewHours.of(hours * 60) + " 💪";
    }

    public static String goalAlreadySet(int hours) {
        return "היעד של השבוע כבר " + HebrewHours.of(hours * 60) + ".";
    }

    public static String goalStays(int hours) {
        return "היעד של השבוע נשאר " + HebrewHours.of(hours * 60) + ".";
    }

    /** D-036 (D-4): a different number while this week's goal exists is kept for next week - and said so. */
    public static String nextWeekGoal(int hours) {
        return "את השבוע הבא נקבע ביום ראשון, ונתחיל " + from(HebrewHours.of(hours * 60)) + " כמו שרצית.";
    }

    public static String childAdded(String name, Integer age, String gender) {
        String years = age == null ? "" : "girl".equals(gender) ? ", בת " + age : "boy".equals(gender) ? ", בן " + age
                : ", בגיל " + age;
        return "הוספתי את " + name + years + ".";
    }

    // ---- the week -------------------------------------------------------------------------------------------------

    /** One session still ahead this week: its when-label and its children. */
    public record Upcoming(String when, String children) {
    }

    /** "מה יש לי השבוע?" (D-8): the state of the week, then his sessions still ahead, one per line. */
    public static List<String> week(Week week, List<Upcoming> upcoming) {
        List<String> lines = new ArrayList<>();
        if (week == null || !week.hasGoal()) {
            lines.add("השבוע עוד בלי יעד.");
        } else if (week.isCovered()) {
            lines.add("השבוע מכוסה 💪");
        } else if (week.covered() == 0) {
            lines.add("השבוע עוד אין זמן מתוכנן, והיעד " + HebrewHours.of(week.goalMinutes()) + ".");
        } else {
            lines.add("השבוע: " + HebrewHours.of(week.covered()) + " מתוך " + HebrewHours.of(week.goalMinutes()) + ".");
        }
        if (upcoming.isEmpty()) {
            lines.add("אין עוד מפגשים מתוכננים השבוע.");
        }
        for (Upcoming u : upcoming) {
            lines.add("• *" + u.when() + "*" + with(u.children()));
        }
        return lines;
    }

    /** "איך אני עומד?": the approved progress block (D-032), in hours. */
    public static List<String> progress(Week week) {
        List<String> lines = new ArrayList<>();
        lines.add("השבוע עד עכשיו:");
        lines.add("היה: " + hoursOrNotYet(week == null ? 0 : week.completed()));
        lines.add("מתוכנן: " + hoursOrNotYet(week == null ? 0 : week.planned()));
        if (week == null || !week.hasGoal()) {
            lines.add("עוד אין יעד לשבוע.");
        } else if (week.isCovered()) {
            lines.add("השבוע מכוסה 💪");
        } else {
            lines.add(missing(week.uncovered()) + " ליעד של " + HebrewHours.of(week.goalMinutes()));
        }
        return lines;
    }

    /** "חסרה: שעה וחצי" (one hour is singular - D-14) / "חסרות: שעתיים". */
    public static String missing(int minutes) {
        String hours = HebrewHours.of(minutes);
        boolean singular = hours.startsWith("שעה") || hours.startsWith("חצי שעה") || hours.startsWith("רבע שעה")
                || hours.startsWith("שלושת רבעי");
        return (singular ? "חסרה: " : "חסרות: ") + hours;
    }

    private static String hoursOrNotYet(int minutes) {
        return minutes <= 0 ? "עוד לא" : HebrewHours.of(minutes);
    }

    // ---- reminders --------------------------------------------------------------------------------------------------

    /**
     * "מתי התזכורת?" (D-1): when he will really be reminded, from the session's planned timers - the morning
     * reminder only when there is one (not for a session before 10:00), the hour before, the follow-up after.
     *
     * @param morning    the morning reminder in his time, or null when there is none ahead
     * @param hourBefore the one-hour reminder in his time, or null when it already passed
     */
    public static List<String> reminders(String children, ZonedDateTime morning, ZonedDateTime hourBefore,
                                         boolean followUp, LocalDate today) {
        List<String> lines = new ArrayList<>();
        String session = "המפגש" + with(children);
        if (hourBefore != null) {
            String hour = HebrewWhen.at(hourBefore, today);
            if (morning != null) {
                String sameDay = morning.toLocalDate().equals(hourBefore.toLocalDate())
                        ? "ב-" + HebrewWhen.time(hourBefore.toLocalTime()) : hour;
                lines.add("אזכיר לך *" + HebrewWhen.at(morning, today) + "*, ושוב " + sameDay + ", שעה לפני " + session + ".");
            } else {
                lines.add("אזכיר לך *" + hour + "*, שעה לפני " + session + ".");
            }
        } else {
            lines.add(session + " מתחיל בקרוב, אז לא תהיה תזכורת נוספת.");
        }
        if (followUp) {
            lines.add("חצי שעה אחרי שתסיימו, אשאל איך היה 🙂");
        }
        return lines;
    }

    /**
     * "מתי התזכורת?" (D-037): when he will really be reminded - from the timers the platform HOLDS for the session, not
     * the policy. A session whose reminders were not armed is told so; one starting within the hour has none due.
     *
     * @param morning    the pending morning reminder in his time, or null
     * @param hourBefore the pending one-hour reminder in his time, or null
     * @param followUp   a follow-up is pending
     * @param startsSoon the session starts within the hour (no reminder is due any more)
     */
    public static List<String> armedReminders(String children, ZonedDateTime morning, ZonedDateTime hourBefore,
                                              boolean followUp, LocalDate today, boolean startsSoon) {
        if (hourBefore != null || startsSoon) {
            return reminders(children, morning, hourBefore, followUp, today);
        }
        List<String> lines = new ArrayList<>();
        String session = "המפגש" + with(children);
        if (morning != null) {
            lines.add("אזכיר לך *" + HebrewWhen.at(morning, today) + "*, ביום של " + session + ".");
        } else {
            lines.add("למפגש" + with(children) + " לא קבועה כרגע תזכורת.");
        }
        if (followUp) {
            lines.add("חצי שעה אחרי שתסיימו, אשאל איך היה 🙂");
        }
        return lines;
    }

    /** The platform could not be asked: no time is stated (D-037). */
    public static List<String> remindersUnknown() {
        return List.of("כרגע אני לא מצליח לבדוק את התזכורות שלך.", "אפשר לשאול שוב עוד כמה דקות.");
    }

    /** The weekly plan's line for a session whose timers could not be read. */
    public static final String REMINDERS_UNKNOWN_SHORT = "לא ניתן לבדוק כרגע";

    public static List<String> noUpcomingSession() {
        return List.of("אין כרגע מפגש מתוכנן, אז גם אין תזכורת.");
    }

    /** One line about a session's reminders, for the weekly plan (the coach reads it, never works one out). */
    public static String remindersInShort(ZonedDateTime morning, ZonedDateTime hourBefore, ZonedDateTime followUp) {
        List<String> parts = new ArrayList<>();
        if (morning != null) {
            parts.add("תזכורת בוקר ב-" + HebrewWhen.time(morning.toLocalTime()));
        }
        if (hourBefore != null) {
            parts.add("תזכורת שעה לפני ב-" + HebrewWhen.time(hourBefore.toLocalTime()));
        }
        if (followUp != null) {
            parts.add("שאלה איך היה ב-" + HebrewWhen.time(followUp.toLocalTime()));
        }
        return parts.isEmpty() ? "אין תזכורות נוספות" : String.join(" · ", parts);
    }

    // ---- the session timers' messages -------------------------------------------------------------------------------

    /** The morning reminder: "*היום ב-17:00* זה הזמן שלך ושל איתמר 🙂" (several sessions: "ב-17:00 וב-19:00"). */
    public static String morning(List<String> times, String children) {
        return "*היום " + joinTimes(times) + "* זה הזמן שלך ושל " + children + " 🙂";
    }

    public static String joinTimes(List<String> times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            sb.append(i == 0 ? "ב-" : i == times.size() - 1 ? " וב-" : ", ב-").append(times.get(i));
        }
        return sb.toString();
    }

    public static final String HOUR_BEFORE_QUESTION = "יש כבר רעיון מה תעשו?";

    public static List<String> hourBefore(String children) {
        return List.of("עוד שעה הזמן שלך ושל " + children + " 🙂", HOUR_BEFORE_QUESTION);
    }

    public static String followUp(String children) {
        return "נו, איך היה לכם עם " + children + "?";
    }

    /** D-5: a greeting is answered as a greeting - the session of today is not its tail. */
    public static String greeting(String name) {
        return name == null || name.isBlank() ? "היי 🙂 מה נשמע?" : "היי " + name + " 🙂 מה נשמע?";
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    public static String text(List<String> lines) {
        return String.join("\n", lines);
    }

    private static String with(String children) {
        return children == null || children.isBlank() ? "" : " עם " + children;
    }

    /** "מ-3 שעות", "משעתיים", "משעה". */
    static String from(String hours) {
        return Character.isDigit(hours.charAt(0)) ? "מ-" + hours : "מ" + hours;
    }

    private static void addIfPresent(List<String> lines, String line) {
        if (line != null) {
            lines.add(line);
        }
    }
}
