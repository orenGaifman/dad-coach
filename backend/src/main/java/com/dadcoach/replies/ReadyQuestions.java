package com.dadcoach.replies;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Whole-message questions answered from data before any AI turn (D-036, the Tair D-041 pattern). The lab showed why:
 * a session cancelled on his page, then "מה יש לי השבוע?" - the model listed the cancelled session from its own
 * earlier booking message, with the week's hours from that message too. These answers are facts; they come from the
 * weekly plan of this moment ({@code ready_replies}), never from the chat history. Only a message that is nothing
 * but the question (an optional greeting in front, punctuation around) - "מה יש לי השבוע ומה עם שישי?" stays with
 * the coach.
 */
public final class ReadyQuestions {

    public enum Kind {
        WEEK("week_reply"), PROGRESS("progress_reply"), REMINDER("reminder_reply");

        public final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    private static final String GREETING = "(?:(?:היי|הי|אהלן|שלום|בוקר טוב|ערב טוב|יו|אחי|תגיד|רגע|נו|אז|וגם|ו)\\s+)*";
    private static final Pattern WEEK = Pattern.compile("^" + GREETING
            + "(?:מה\\s+(?:יש\\s+לי|יש|מתוכנן(?:\\s+לי)?|קבענו|קבעת(?:\\s+לי)?|התוכנית(?:\\s+שלי)?|התכנית(?:\\s+שלי)?|הלו\"ז(?:\\s+שלי)?)"
            + "(?:\\s+(?:ה|ל)?שבוע(?:\\s+הזה)?)?|(?:תראה|תזכיר)\\s+לי\\s+(?:מה\\s+יש\\s+)?(?:ה|ל)?שבוע|איזה\\s+מפגשים\\s+יש\\s+לי(?:\\s+(?:ה|ל)?שבוע)?)$");
    private static final Pattern PROGRESS = Pattern.compile("^" + GREETING
            + "(?:איך\\s+אני\\s+עומד(?:\\s+(?:ה|ב)?שבוע)?|מה\\s+המצב\\s+(?:ה|ב)?שבוע|כמה\\s+(?:שעות\\s+)?(?:עשיתי|יש\\s+לי)\\s+(?:ה|ב)?שבוע|"
            + "כמה\\s+חסר(?:\\s+לי)?(?:\\s+(?:ל)?יעד)?(?:\\s+(?:ה|ב)?שבוע)?)$");
    private static final Pattern REMINDER = Pattern.compile("^" + GREETING
            + "(?:מתי\\s+(?:תהיה\\s+|יש\\s+|תגיע\\s+)?(?:ה)?תזכורת(?:\\s+(?:הבאה|שלי))?|מתי\\s+(?:אתה\\s+)?(?:תזכיר|מזכיר)\\s+לי|"
            + "מתי\\s+תשלח(?:\\s+לי)?\\s+תזכורת)$");

    private ReadyQuestions() {
    }

    public static Optional<Kind> of(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.strip().replaceAll("[?!.,׳'״\\s]+$", "").replaceAll("^[\\s,]+", "").replaceAll("\\s+", " ")
                .replaceAll(",", "");
        if (t.isEmpty() || t.length() > 60) {
            return Optional.empty();
        }
        if (WEEK.matcher(t).matches()) {
            return Optional.of(Kind.WEEK);
        }
        if (PROGRESS.matcher(t).matches()) {
            return Optional.of(Kind.PROGRESS);
        }
        if (REMINDER.matcher(t).matches()) {
            return Optional.of(Kind.REMINDER);
        }
        return Optional.empty();
    }
}
