package com.dadcoach.replies;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.weeklyplan.SessionTimerPlanner;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-036: the coach's factual answers, written in code - the approved shapes, from data only. */
class CoachRepliesTest {

    static final ZoneId IL = ZoneId.of("Asia/Jerusalem");
    static final LocalDate THURSDAY = LocalDate.of(2026, 10, 8);
    static final Set<String> ALL_TIMERS = Set.of(SessionTimerPlanner.MORNING_REMINDER, SessionTimerPlanner.REMINDER_1H,
            SessionTimerPlanner.FOLLOW_UP);

    @Test
    @DisplayName("D-3: a booking names only the reminders that really exist, and the week in hours")
    void booked() {
        // Friday 09:00 has no morning reminder (before 10:00) - production said "אעדכן אותך בבוקר ושעה לפני"
        assertThat(CoachReplies.booked("מחר, יום שישי 9.10 ב-09:00", 90, "מטר ונעם",
                Set.of(SessionTimerPlanner.REMINDER_1H, SessionTimerPlanner.FOLLOW_UP), new CoachReplies.Week(120, 30, 90)))
                .containsExactly("קבעתי 🎉 *מחר, יום שישי 9.10 ב-09:00*, שעה וחצי עם מטר ונעם.",
                        "אזכיר לך שעה לפני, ואשאל אחר כך איך היה.", "השבוע מכוסה.");
        assertThat(CoachReplies.booked("היום, יום חמישי 8.10 ב-17:00", 30, "איתמר", ALL_TIMERS, new CoachReplies.Week(180, 0, 30)))
                .containsExactly("קבעתי 🎉 *היום, יום חמישי 8.10 ב-17:00*, חצי שעה עם איתמר.",
                        "אזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.", "השבוע: חצי שעה מתוך 3 שעות.");
        // no goal, nothing to remind before (starts within the hour): no week line, only the follow-up
        assertThat(CoachReplies.booked("היום, יום חמישי 8.10 ב-17:00", 60, "איתמר", Set.of(SessionTimerPlanner.FOLLOW_UP),
                new CoachReplies.Week(null, 0, 60)))
                .containsExactly("קבעתי 🎉 *היום, יום חמישי 8.10 ב-17:00*, שעה עם איתמר.", "אשאל אחר כך איך היה.");
        assertThat(CoachReplies.timersLine(Set.of())).isNull();
        assertThat(CoachReplies.moved("יום ראשון 11.10 ב-18:00", 60, "נועה", Set.of(), null))
                .containsExactly("הזזתי ל*יום ראשון 11.10 ב-18:00*, שעה עם נועה.");
    }

    @Test
    @DisplayName("B-1: a completion says the belt only when one was earned now, one emoji in the message")
    void done() {
        assertThat(CoachReplies.done("איתמר", new CoachReplies.Week(180, 60, 0), null))
                .containsExactly("איזה כיף! רשמתי את הזמן שלך עם איתמר.", "השבוע: שעה מתוך 3 שעות 💪");
        assertThat(CoachReplies.done("איתמר", new CoachReplies.Week(180, 90, 0), "חגורה צהובה"))
                .containsExactly("איזה כיף! רשמתי את הזמן שלך עם איתמר.", "השבוע: שעה וחצי מתוך 3 שעות.",
                        "עלית ל*חגורה צהובה* 💪");
        assertThat(CoachReplies.done(null, new CoachReplies.Week(null, 30, 0), null))
                .containsExactly("איזה כיף! רשמתי את הזמן שלך.", "השבוע: חצי שעה 💪");
    }

    @Test
    @DisplayName("D-8: 'מה יש לי השבוע?' - the state of the week, then each session ahead with its Hebrew day")
    void week() {
        List<CoachReplies.Upcoming> ahead = List.of(new CoachReplies.Upcoming("מחר, יום חמישי 8.10 ב-17:00", "איתמר"),
                new CoachReplies.Upcoming("יום שישי 9.10 ב-09:00", "מטר ונעם"));
        assertThat(CoachReplies.week(new CoachReplies.Week(120, 0, 120), ahead)).containsExactly("השבוע מכוסה 💪",
                "• *מחר, יום חמישי 8.10 ב-17:00* עם איתמר", "• *יום שישי 9.10 ב-09:00* עם מטר ונעם");
        assertThat(CoachReplies.week(new CoachReplies.Week(180, 60, 0), List.of()))
                .containsExactly("השבוע: שעה מתוך 3 שעות.", "אין עוד מפגשים מתוכננים השבוע.");
        assertThat(CoachReplies.week(null, List.of())).first().isEqualTo("השבוע עוד בלי יעד.");
        assertThat(CoachReplies.week(new CoachReplies.Week(180, 0, 0), List.of()))
                .containsExactly("השבוע עוד אין זמן מתוכנן, והיעד 3 שעות.", "אין עוד מפגשים מתוכננים השבוע.");
    }

    @Test
    @DisplayName("the progress block in hours; one hour is 'חסרה' (D-14), more is 'חסרות'")
    void progress() {
        assertThat(CoachReplies.progress(new CoachReplies.Week(180, 0, 90)))
                .containsExactly("השבוע עד עכשיו:", "היה: עוד לא", "מתוכנן: שעה וחצי", "חסרה: שעה וחצי ליעד של 3 שעות");
        assertThat(CoachReplies.progress(new CoachReplies.Week(240, 60, 60)).get(3)).isEqualTo("חסרות: שעתיים ליעד של 4 שעות");
        assertThat(CoachReplies.progress(new CoachReplies.Week(120, 60, 60)).get(3)).isEqualTo("השבוע מכוסה 💪");
    }

    @Test
    @DisplayName("D-1: 'מתי התזכורת?' - the real timers: 17:00-17:30 is reminded at 16:00 and asked about at 18:00")
    void reminders() {
        ZonedDateTime hour = THURSDAY.atTime(16, 0).atZone(IL);
        assertThat(CoachReplies.reminders("איתמר", null, hour, true, THURSDAY))
                .containsExactly("אזכיר לך *היום ב-16:00*, שעה לפני המפגש עם איתמר.", "חצי שעה אחרי שתסיימו, אשאל איך היה 🙂");
        ZonedDateTime morning = THURSDAY.plusDays(1).atTime(8, 0).atZone(IL);
        assertThat(CoachReplies.reminders("נועה", morning, THURSDAY.plusDays(1).atTime(16, 0).atZone(IL), true, THURSDAY).get(0))
                .isEqualTo("אזכיר לך *מחר ב-08:00*, ושוב ב-16:00, שעה לפני המפגש עם נועה.");
        assertThat(CoachReplies.reminders("נועה", null, null, true, THURSDAY).get(0))
                .isEqualTo("המפגש עם נועה מתחיל בקרוב, אז לא תהיה תזכורת נוספת.");
        assertThat(CoachReplies.remindersInShort(null, hour, THURSDAY.atTime(18, 0).atZone(IL)))
                .isEqualTo("תזכורת שעה לפני ב-16:00 · שאלה איך היה ב-18:00");
    }

    @Test
    @DisplayName("a cancellation says the real gap left in the week")
    void cancelled() {
        assertThat(CoachReplies.cancelled("מחר, יום שישי 9.10 ב-19:00", "איתמר", new CoachReplies.Week(180, 0, 0)))
                .containsExactly("ביטלתי את המפגש של *מחר, יום שישי 9.10 ב-19:00* עם איתמר.", "השבוע חסרות עוד 3 שעות ליעד.");
        assertThat(CoachReplies.missed("נועה", new CoachReplies.Week(120, 60, 0)))
                .containsExactly("קורה, העיקר שממשיכים.", "רשמתי שהמפגש עם נועה לא יצא.", "השבוע חסרה עוד שעה ליעד.");
        assertThat(CoachReplies.cancelled("היום ב-17:00", "נועה", new CoachReplies.Week(60, 60, 0))).hasSize(1);
    }

    @Test
    @DisplayName("the session timers' messages and the goal lines")
    void timersAndGoal() {
        assertThat(CoachReplies.morning(List.of("17:00"), "איתמר")).isEqualTo("*היום ב-17:00* זה הזמן שלך ושל איתמר 🙂");
        assertThat(CoachReplies.morning(List.of("17:00", "19:00"), "איתמר ונעם"))
                .isEqualTo("*היום ב-17:00 וב-19:00* זה הזמן שלך ושל איתמר ונעם 🙂");
        assertThat(CoachReplies.hourBefore("איתמר")).containsExactly("עוד שעה הזמן שלך ושל איתמר 🙂", "יש כבר רעיון מה תעשו?");
        assertThat(CoachReplies.followUp("מטר ונעם")).isEqualTo("נו, איך היה לכם עם מטר ונעם? 🙂");
        assertThat(CoachReplies.greeting("אורן")).isEqualTo("היי אורן 🙂 מה נשמע?");
        assertThat(CoachReplies.goalSet(2)).isEqualTo("סגרנו: השבוע שעתיים 💪");
        assertThat(CoachReplies.nextWeekGoal(3)).isEqualTo("את השבוע הבא נקבע ביום ראשון, ונתחיל מ-3 שעות כמו שרצית.");
        assertThat(CoachReplies.nextWeekGoal(2)).isEqualTo("את השבוע הבא נקבע ביום ראשון, ונתחיל משעתיים כמו שרצית.");
        assertThat(CoachReplies.childAdded("נעם", 5, "girl")).isEqualTo("הוספתי את נעם, בת 5.");
        assertThat(CoachReplies.childAdded("רון", 9, null)).isEqualTo("הוספתי את רון, בגיל 9.");
    }

    @Test
    @DisplayName("Hebrew days: today, tomorrow, a date - never an English weekday")
    void when() {
        ZonedDateTime friday9 = THURSDAY.plusDays(1).atTime(9, 0).atZone(IL);
        assertThat(HebrewWhen.label(friday9, THURSDAY)).isEqualTo("מחר, יום שישי 9.10 ב-09:00");
        assertThat(HebrewWhen.label(friday9, THURSDAY.minusDays(3))).isEqualTo("יום שישי 9.10 ב-09:00");
        assertThat(HebrewWhen.at(friday9, THURSDAY.minusDays(3))).isEqualTo("ביום שישי 9.10 ב-09:00");
    }
}
