package com.dadcoach.replies;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.integration.platform.SessionTimers;
import com.dadcoach.weeklyplan.SessionTimerPlanner;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-037: the reminder sentences of a reply come from the timers the platform confirmed - all, some, none, or none known. */
class TimerClaimsTest {

    static final String H = SessionTimerPlanner.REMINDER_1H;
    static final String M = SessionTimerPlanner.MORNING_REMINDER;
    static final String F = SessionTimerPlanner.FOLLOW_UP;
    static final String BOOKED = "קבעתי 🎉 *יום שישי 6.11 ב-17:00*, שעה עם נועה.";
    static final String WEEK = "השבוע מכוסה.";

    static Set<String> keys(String... k) {
        return new LinkedHashSet<>(List.of(k));
    }

    static List<String> ready(Set<String> planned) {
        return CoachReplies.booked("יום שישי 6.11 ב-17:00", 60, "נועה", planned,
                new CoachReplies.Week(120, 0, 120));
    }

    @Test
    @DisplayName("all confirmed: the confirmation is the planned one, word for word")
    void allConfirmed() {
        List<String> out = TimerClaims.confirmation(ready(keys(M, H, F)), keys(M, H, F), keys(M, H, F));
        assertThat(out).containsExactly(BOOKED, "אזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.", WEEK);
    }

    @Test
    @DisplayName("some confirmed: only those are promised")
    void someConfirmed() {
        assertThat(TimerClaims.confirmation(ready(keys(M, H, F)), keys(M, H, F), keys(H)))
                .containsExactly(BOOKED, "אזכיר לך שעה לפני.", WEEK);
        assertThat(TimerClaims.confirmation(ready(keys(M, H, F)), keys(M, H, F), keys(F)))
                .containsExactly(BOOKED, "אשאל אחר כך איך היה.", WEEK);
    }

    @Test
    @DisplayName("none confirmed (refused, or the platform down): one honest line, no promise")
    void noneConfirmed() {
        assertThat(TimerClaims.confirmation(ready(keys(H, F)), keys(H, F), Set.of()))
                .containsExactly(BOOKED, TimerClaims.NOT_SET, WEEK);
        assertThat(TimerClaims.line(keys(H, F), Set.of())).isEqualTo(TimerClaims.NOT_SET);
    }

    @Test
    @DisplayName("no timer was due (the session starts within the hour and ends soon): no line at all")
    void nothingDue() {
        assertThat(TimerClaims.line(Set.of(), Set.of())).isNull();
        assertThat(TimerClaims.confirmation(ready(Set.of()), Set.of(), Set.of())).containsExactly(BOOKED, WEEK);
    }

    @Test
    @DisplayName("align: the planned line in the model's copy becomes the confirmed one; its own reminder promises go")
    void alignReplacesThePlannedLine() {
        String copy = BOOKED + "\nאזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.\n" + WEEK + "\nאזכיר לך גם בערב 🙂";
        assertThat(TimerClaims.align(copy, "אזכיר לך שעה לפני.", BOOKED))
                .isEqualTo(BOOKED + "\nאזכיר לך שעה לפני.\n" + WEEK);
        assertThat(TimerClaims.align(copy, TimerClaims.NOT_SET, BOOKED))
                .isEqualTo(BOOKED + "\n" + TimerClaims.NOT_SET + "\n" + WEEK);
    }

    @Test
    @DisplayName("align: the expected line already there stays once; a reply without it gets it after the booking line")
    void alignKeepsOrInserts() {
        String ok = BOOKED + "\nאזכיר לך שעה לפני.\n" + WEEK;
        assertThat(TimerClaims.align(ok, "אזכיר לך שעה לפני.", BOOKED)).isEqualTo(ok);
        assertThat(TimerClaims.align(BOOKED + "\n" + WEEK, "אזכיר לך שעה לפני.", BOOKED)).isEqualTo(ok);
        assertThat(TimerClaims.align("בהצלחה!", TimerClaims.NONE_SET, null)).isEqualTo("בהצלחה!\n" + TimerClaims.NONE_SET);
    }

    @Test
    @DisplayName("withoutTimerClaims: 'אזכיר לך', 'תקבל ממני תזכורת', 'אשאל איך היה' go; other sentences stay")
    void withoutClaims() {
        assertThat(TimerClaims.withoutTimerClaims("מעולה. אזכיר לך שעה לפני. תקבל ממני תזכורת בבוקר.\n"
                + "חצי שעה אחרי שתסיימו, אשאל איך היה 🙂\nבהצלחה!")).isEqualTo("מעולה.\nבהצלחה!");
    }

    @Test
    @DisplayName("several sessions: all confirmed - nothing to say; some - 'חלק'; none - 'לא הצלחתי'")
    void manySessions() {
        var all = new SessionTimers.Confirmation(UUID.randomUUID(), keys(H, F), keys(H, F));
        var none = new SessionTimers.Confirmation(UUID.randomUUID(), keys(H, F), Set.of());
        var nothingDue = new SessionTimers.Confirmation(UUID.randomUUID(), Set.of(), Set.of());
        assertThat(TimerClaims.lineForMany(List.of(all, nothingDue))).isNull();
        assertThat(TimerClaims.lineForMany(List.of(all, none))).isEqualTo(TimerClaims.SOME_NOT_SET);
        assertThat(TimerClaims.lineForMany(List.of(none, none))).isEqualTo(TimerClaims.NONE_SET);
    }

    @Test
    @DisplayName("'מתי התזכורת?' from what the platform holds: the morning only, none armed, none due, follow-up only")
    void armedReminders() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        ZonedDateTime morning = LocalDate.of(2026, 11, 6).atTime(8, 0).atZone(ZoneId.of("Asia/Jerusalem"));
        assertThat(CoachReplies.armedReminders("נועה", morning, null, false, today, false))
                .containsExactly("אזכיר לך *ביום שישי 6.11 ב-08:00*, ביום של המפגש עם נועה.");
        assertThat(CoachReplies.armedReminders("נועה", null, null, true, today, false))
                .containsExactly("למפגש עם נועה לא קבועה כרגע תזכורת.", "חצי שעה אחרי שתסיימו, אשאל איך היה 🙂");
        assertThat(CoachReplies.armedReminders("נועה", null, null, false, today, true))
                .containsExactly("המפגש עם נועה מתחיל בקרוב, אז לא תהיה תזכורת נוספת.");
        assertThat(CoachReplies.armedReminders("נועה", null, morning.withHour(16), true, today, false))
                .isEqualTo(CoachReplies.reminders("נועה", null, morning.withHour(16), true, today));
    }
}
