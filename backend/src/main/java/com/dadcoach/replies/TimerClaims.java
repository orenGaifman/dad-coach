package com.dadcoach.replies;

import com.dadcoach.integration.platform.SessionTimers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * D-037: a reply promises a reminder or a follow-up only when the platform holds that timer
 * ({@link SessionTimers#ensure}). The booking confirmation's timers line is built from the confirmed timers; every
 * other sentence about reminders in a turn that booked or moved a session goes. When nothing could be confirmed the
 * reply says so in one line - never a promise the platform does not keep. Sentences are compared by words only.
 */
public final class TimerClaims {

    /** One session, none of its timers confirmed (the platform down, or it refused them). */
    public static final String NOT_SET = "את התזכורת למפגש הזה לא הצלחתי לקבוע הפעם.";
    /** Several sessions, none confirmed. */
    public static final String NONE_SET = "את התזכורות למפגשים האלה לא הצלחתי לקבוע הפעם.";
    /** Several sessions, some confirmed. */
    public static final String SOME_NOT_SET = "חלק מהתזכורות לא הצלחתי לקבוע הפעם.";

    /** "אשאל אחר כך איך היה", "אשאל איך היה", "אשאל אותך אחרי איך היה". */
    private static final Pattern FOLLOW_UP = Pattern.compile("אשאל[^.!?\\n]{0,25}איך היה");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+|(?<=\\p{IsExtended_Pictographic}\\x{FE0F}?)\\s+");

    private TimerClaims() {
    }

    /** The timers line of ONE session's confirmation: the confirmed timers, the honest line when none, null when none were due. */
    public static String line(Collection<String> planned, Collection<String> confirmed) {
        if (planned == null || planned.isEmpty()) {
            return null;
        }
        if (confirmed == null || confirmed.isEmpty()) {
            return NOT_SET;
        }
        return CoachReplies.timersLine(confirmed);
    }

    /** The honest line for a turn with several sessions, or null when every planned timer is confirmed. */
    public static String lineForMany(List<SessionTimers.Confirmation> confirmations) {
        boolean allDone = confirmations.stream().allMatch(SessionTimers.Confirmation::complete);
        if (allDone) {
            return null;
        }
        boolean anyConfirmed = confirmations.stream().anyMatch(c -> !c.confirmed().isEmpty());
        return anyConfirmed ? SOME_NOT_SET : NONE_SET;
    }

    /**
     * The ready booking/move confirmation with its timers line from the CONFIRMED timers: the planned line is replaced
     * (or dropped when no timer was due), the rest stays.
     */
    public static List<String> confirmation(List<String> ready, Set<String> planned, Set<String> confirmed) {
        if (ready == null) {
            return null;
        }
        String plannedLine = CoachReplies.timersLine(planned);
        String trueLine = line(planned, confirmed);
        List<String> out = new ArrayList<>();
        boolean replaced = false;
        for (String l : ready) {
            if (!replaced && plannedLine != null && l.equals(plannedLine)) {
                replaced = true;
                if (trueLine != null) {
                    out.add(trueLine);
                }
            } else {
                out.add(l);
            }
        }
        if (!replaced && trueLine != null && !out.isEmpty()) {
            out.add(1, trueLine);
        }
        return out;
    }

    /**
     * The reply with no sentence about reminders or the follow-up except {@code expected}; {@code expected} (when
     * given) is put right after the line holding {@code anchor} (the booking line), or last.
     */
    public static String align(String body, String expected, String anchor) {
        String expectedWords = ClaimGuard.words(expected);
        List<String> lines = new ArrayList<>();
        boolean present = false;
        for (String line : body.split("\n", -1)) {
            List<String> keep = new ArrayList<>();
            for (String s : SENTENCE_END.split(line.strip())) {
                if (s.isBlank()) {
                    continue;
                }
                String w = ClaimGuard.words(s);
                if (expected != null && w.equals(expectedWords)) {
                    if (!present) {
                        keep.add(s.strip());
                        present = true;
                    }
                    continue;
                }
                if (!isTimerClaim(s)) {
                    keep.add(s.strip());
                }
            }
            if (line.isBlank() || !keep.isEmpty()) {
                lines.add(String.join(" ", keep)); // a line left empty by a removed promise goes, a blank line stays
            }
        }
        if (expected != null && !present) {
            int at = lines.size();
            String anchorWords = ClaimGuard.words(anchor);
            for (int i = 0; i < lines.size(); i++) {
                if (!anchorWords.isEmpty() && ClaimGuard.words(lines.get(i)).contains(anchorWords)) {
                    at = i + 1;
                    break;
                }
            }
            lines.add(at, expected);
        }
        return tidy(lines);
    }

    /** The reply without any sentence that promises a reminder or a follow-up (when nothing can be confirmed). */
    public static String withoutTimerClaims(String body) {
        return align(body, null, null);
    }

    static boolean isTimerClaim(String sentence) {
        String w = ClaimGuard.words(sentence);
        return ClaimGuard.REMIND.matcher(sentence).find() || FOLLOW_UP.matcher(sentence).find()
                || w.equals(ClaimGuard.words(NOT_SET)) || w.equals(ClaimGuard.words(NONE_SET))
                || w.equals(ClaimGuard.words(SOME_NOT_SET));
    }

    /** Blank lines at most one in a row, none at the ends; a line of only an emoji goes. */
    private static String tidy(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String l = line.strip();
            if (!l.isEmpty() && ClaimGuard.words(l).isEmpty()) {
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
}
