package com.dadcoach.replies;

import com.dadcoach.qualitytime.SessionChildren;
import com.dadcoach.weeklyplan.HebrewHours;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The last check before a coach reply goes out (D-034, D-2): the reply may say something was done only when this turn
 * really did it ({@link TurnLedger.Changes}). Production (review 2, 2026-10-08): "מעולה, קובע את זה - יום שישי
 * 09:00-10:30 עם מטר ונעם 💪" with nothing booked (the father: "אני לא רואה שקבעת"); "מעולה, רשום אצלי 😊" for an
 * activity nothing can store; "... רק רגע ונמשיך משם." and nothing came; "אעדכן אותך בבוקר" for a session that has no
 * morning reminder. Rules, in code:
 * <ul>
 *   <li>"רק רגע", "אחזור אליך", "אעדכן אותך" - nothing waits in the background, ever: the sentence is dropped;</li>
 *   <li>a booking / cancellation / "רשמתי"/"שמרתי" / a goal "סגרנו" / "אזכיר לך" with no matching change: the sentence is
 *       dropped and an honest line is said instead ("רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם מטר ונעם." / "לקבוע?",
 *       built from the dropped sentence's day, time and children when they are there);</li>
 *   <li>a session booked or moved this turn: the confirmation is the ready one ({@link CoachReplies#booked}) - if the
 *       reply does not carry it, it is put in, and the reply's own lines about times, reminders and the week go;</li>
 *   <li>"שלחתי לך את הכפתור" with no button sent: {@link Result#needsButton()} - the product sends it, so it is true.</li>
 * </ul>
 * Sentences are compared by words only (emoji and punctuation drift).
 */
public final class ClaimGuard {

    public record Result(String body, boolean changed, boolean needsButton) {
    }

    /** The whole reply was a promise or a claim: said honestly, and he is asked again. */
    static final String NOTHING_DONE = "עוד לא עשיתי את זה.\nאפשר לכתוב לי שוב מה צריך?";

    enum Claim { PROMISE, BOOK, CANCEL, RECORD, GOAL, REMIND, BUTTON }

    private static final String LEAD = "^(?:(?:מעולה|סגור|סגרנו|יופי|אוקיי|אוקי|בסדר|יאללה|סבבה|אין בעיה|נהדר|כן|מצוין|בשמחה|בטח|טוב|"
            + "וואו|איזה כיף|כיף|אז|הנה|ו?עכשיו|זהו|דאן|בוצע)[\\s,!.:\\-–—]*)*";
    private static final Pattern PROMISE = Pattern.compile("רק רגע|רגע אחד ו|אחזור אליך|אחזור אלייך|אעדכן אותך|אבדוק ואחזור|"
            + "תכף אחזור|מיד אחזור|אני בודק ואחזור|אני על זה|תן לי רגע|תן לי דקה");
    private static final Pattern BOOK = Pattern.compile(LEAD + "(?:קבעתי|קובע|קבענו|נקבע|הזזתי|מזיז|העברתי|רשמתי לך מפגש|שריינתי)"
            + "|קובע את זה|קובע לך|קבעתי לך|קבעתי את|הזזתי את");
    private static final Pattern CANCEL = Pattern.compile(LEAD + "(?:ביטלתי|מבטל|ביטלנו)");
    private static final Pattern RECORD = Pattern.compile(LEAD + "(?:רשמתי|רשום|נרשם|שמרתי|שומר|עדכנתי|מעדכן|סימנתי|הוספתי|מוסיף)"
            + "|רשום אצלי|שמור אצלי|שמרתי את|עדכנתי את|רשמתי את|רשמתי ש|רשמתי לי|אני רושם|אני שומר|אני מעדכן");
    private static final Pattern GOAL = Pattern.compile("^(?:" + LEAD.substring(1) + ")?סגרנו|קבענו יעד|היעד נקבע|היעד שלך נקבע|"
            + "קבעתי יעד|קבעתי את היעד|היעד שלך עכשיו|היעד מעכשיו");
    private static final Pattern REMIND = Pattern.compile("אזכיר לך|אזכיר אותך|אשלח לך תזכורת|תקבל תזכורת|תקבל ממני תזכורת");
    private static final Pattern BUTTON = Pattern.compile("שלחתי לך (?:את )?(?:ה)?(?:כפתור|קישור)|הכפתור בדרך|שולח לך (?:את )?(?:ה)?כפתור|"
            + "הנה הכפתור|זה פתוח אצלך");
    /** A line about times, reminders or the week - dropped when the ready booking confirmation replaces the model's. */
    private static final Pattern BOOKING_DETAIL = Pattern.compile("קבע|אזכיר|תזכורת|השבוע|מכוסה|דקות|\\d{1,2}:\\d{2}|🎉|אעדכן");

    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?:\\s*[-–]\\s*(\\d{1,2}):(\\d{2}))?");
    private static final Pattern DAY = Pattern.compile("היום|מחר|(?:ב?יום\\s+)?(ראשון|שני|שלישי|רביעי|חמישי|שישי|שבת)");
    private static final Pattern LENGTH = Pattern.compile("חצי שעה|רבע שעה|שעה וחצי|שעתיים וחצי|שעתיים|\\d+ שעות(?: וחצי)?|\\d+ דקות|שעה");
    /** Sentences end at . ! ? or at an emoji (one sentence can carry the claim and the next a good wish). */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+|(?<=\\p{IsExtended_Pictographic}\\x{FE0F}?)\\s+");

    private ClaimGuard() {
    }

    /**
     * @param body          the reply without the identity line
     * @param changes       what this turn really did
     * @param buttonSent    a button to his page went out in this turn
     * @param childNames    his children's names (to rebuild an honest question from a dropped booking sentence)
     */
    public static Result check(String body, TurnLedger.Changes changes, boolean buttonSent, Collection<String> childNames) {
        List<List<String>> lines = new ArrayList<>();
        for (String line : body.split("\n", -1)) {
            List<String> sentences = new ArrayList<>();
            if (!line.isBlank()) {
                for (String s : SENTENCE_END.split(line.strip())) {
                    if (!s.isBlank()) {
                        sentences.add(s.strip());
                    }
                }
            }
            lines.add(sentences);
        }
        boolean changed = false;
        boolean needsButton = false;
        String droppedBooking = null;
        boolean droppedCancel = false;
        boolean droppedRecord = false;
        boolean droppedGoal = false;
        for (List<String> sentences : lines) {
            for (int i = 0; i < sentences.size(); i++) {
                String s = sentences.get(i);
                Claim invalid = invalidClaim(s, changes);
                if (invalid == Claim.BUTTON) {
                    if (!buttonSent) {
                        needsButton = true;
                    }
                    continue;
                }
                if (invalid == null) {
                    continue;
                }
                sentences.remove(i--);
                changed = true;
                switch (invalid) {
                    case BOOK -> droppedBooking = droppedBooking == null ? s : droppedBooking + " " + s;
                    case CANCEL -> droppedCancel = true;
                    case RECORD -> droppedRecord = true;
                    case GOAL -> droppedGoal = true;
                    default -> { }
                }
            }
        }
        List<String> honest = new ArrayList<>();
        if (droppedBooking != null) {
            honest.addAll(confirmQuestion(droppedBooking, childNames));
        } else if (droppedCancel) {
            honest.add("עוד לא ביטלתי את זה.");
            honest.add("לבטל עכשיו?");
        } else if (droppedGoal) {
            honest.add("היעד של השבוע עוד לא נשמר.");
            honest.add("כמה שעות לשבוע הזה?");
        }
        if (droppedRecord) {
            honest.add(0, "את זה אין לי איפה לשמור.");
        }

        List<String> kept = join(lines);
        // a session booked or moved now: the ready confirmation, not the model's own times and reminders (D-3)
        List<String> ready = changes.bookingReply();
        if (ready != null && !ready.isEmpty() && !words(String.join(" ", kept)).contains(words(ready.get(0)))) {
            List<String> rest = new ArrayList<>();
            for (String line : kept) {
                if (!line.isEmpty() && !BOOKING_DETAIL.matcher(line).find()) {
                    rest.add(line);
                }
            }
            kept = new ArrayList<>(ready);
            if (!rest.isEmpty()) {
                kept.add("");
                kept.addAll(rest);
            }
            changed = true;
        } else if (ready != null && !ready.isEmpty()) {
            // the confirmation is there; a reminder sentence of its own ("אעדכן אותך בבוקר") is not
            String timers = ready.size() > 1 ? words(ready.get(1)) : "";
            List<String> clean = new ArrayList<>();
            for (String line : kept) {
                boolean ownReminder = (REMIND.matcher(line).find() || line.contains("מפגש אחד"))
                        && !words(line).equals(timers) && !words(String.join(" ", ready)).contains(words(line));
                if (ownReminder) {
                    changed = true;
                } else {
                    clean.add(line);
                }
            }
            kept = clean;
        }
        if (!honest.isEmpty()) {
            boolean honestAsks = honest.get(honest.size() - 1).endsWith("?");
            List<String> rest = new ArrayList<>();
            for (String line : kept) {
                boolean aboutTheBooking = droppedBooking != null && BOOKING_DETAIL.matcher(line).find();
                if (!(honestAsks && line.endsWith("?")) && !aboutTheBooking) {
                    rest.add(line);
                }
            }
            kept = new ArrayList<>(honest);
            if (rest.stream().anyMatch(l -> !l.isEmpty())) {
                kept.add("");
                kept.addAll(rest);
            }
        }
        String out = changed ? tidy(kept) : body;
        if (changed && words(out).isEmpty()) {
            out = NOTHING_DONE;
        }
        return new Result(out, changed, needsButton);
    }

    static Claim invalidClaim(String sentence, TurnLedger.Changes c) {
        if (PROMISE.matcher(sentence).find()) {
            return Claim.PROMISE;
        }
        if (BUTTON.matcher(sentence).find()) {
            return Claim.BUTTON;
        }
        if (sentence.strip().endsWith("?")) {
            return null; // "נקבע לשישי?" asks - it claims nothing
        }
        if (BOOK.matcher(sentence).find() && !c.sessionBookedOrMoved() && !namesAnUpcomingSession(sentence, c)) {
            return Claim.BOOK;
        }
        if (CANCEL.matcher(sentence).find() && c.cancelled() == 0) {
            return Claim.CANCEL;
        }
        if (GOAL.matcher(sentence).find() && !c.goalExists() && !c.sessionBookedOrMoved()) {
            return Claim.GOAL;
        }
        if (RECORD.matcher(sentence).find() && !c.any()) {
            return Claim.RECORD;
        }
        if (REMIND.matcher(sentence).find() && !c.upcomingSession()) {
            return Claim.REMIND;
        }
        return null;
    }

    /** "קבענו לשישי ב-09:00" about a session he already has is not a claim of a new booking. */
    private static boolean namesAnUpcomingSession(String sentence, TurnLedger.Changes c) {
        Matcher time = TIME.matcher(sentence);
        while (time.find()) {
            if (c.upcomingStarts().contains(String.format("%02d:%s", Integer.parseInt(time.group(1)), time.group(2)))) {
                return true;
            }
        }
        return false;
    }

    /** "רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם מטר ונעם." / "לקבוע?" - or the plain honest question. */
    static List<String> confirmQuestion(String dropped, Collection<String> childNames) {
        Matcher time = TIME.matcher(dropped);
        Matcher day = DAY.matcher(dropped);
        if (!time.find() || !day.find()) {
            return List.of("עוד לא קבעתי את זה.", "לקבוע עכשיו?");
        }
        String dayText = day.group(1) != null ? "יום " + day.group(1) : day.group();
        String start = String.format("%02d:%s", Integer.parseInt(time.group(1)), time.group(2));
        String length = null;
        if (time.group(3) != null) {
            try {
                long minutes = Duration.between(LocalTime.parse(start),
                        LocalTime.parse(String.format("%02d:%s", Integer.parseInt(time.group(3)), time.group(4)))).toMinutes();
                if (minutes > 0) {
                    length = HebrewHours.of((int) minutes);
                }
            } catch (RuntimeException ignored) {
                // not a time range after all
            }
        }
        if (length == null) {
            Matcher l = LENGTH.matcher(dropped);
            if (l.find()) {
                length = l.group();
            }
        }
        List<String> named = new ArrayList<>();
        for (String name : childNames) {
            if (name != null && !name.isBlank() && dropped.contains(name)) {
                named.add(name);
            }
        }
        named.sort((a, b) -> Integer.compare(dropped.indexOf(a), dropped.indexOf(b)));
        String children = SessionChildren.joinHebrew(named);
        return List.of("רק מוודא: *" + dayText + " ב-" + start + "*" + (length == null ? "" : ", " + length)
                + (children == null ? "" : " עם " + children) + ".", "לקבוע?");
    }

    private static List<String> join(List<List<String>> lines) {
        List<String> out = new ArrayList<>();
        for (List<String> sentences : lines) {
            out.add(String.join(" ", sentences));
        }
        return out;
    }

    /** Blank lines at most one in a row, none at the ends; a line left as only an emoji goes. */
    private static String tidy(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String l = line.strip();
            if (!l.isEmpty() && words(l).isEmpty()) {
                continue;
            }
            if (l.isEmpty() && (out.isEmpty() || out.get(out.size() - 1).isEmpty())) {
                continue;
            }
            out.add(l);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return String.join("\n", out);
    }

    /** The reply without its sentences that say a button was sent (it could not be). */
    public static String withoutButtonClaims(String body) {
        List<String> out = new ArrayList<>();
        for (String line : body.split("\n", -1)) {
            List<String> keep = new ArrayList<>();
            for (String s : SENTENCE_END.split(line.strip())) {
                if (!s.isBlank() && !BUTTON.matcher(s).find()) {
                    keep.add(s.strip());
                }
            }
            out.add(String.join(" ", keep));
        }
        return tidy(out);
    }

    public static String words(String text) {
        return text == null ? "" : text.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
