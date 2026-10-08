package com.dadcoach.replies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-034 (D-2): a reply confirms only what this turn really did - the production examples of review 2. */
class ClaimGuardTest {

    static final List<String> KIDS = List.of("איתמר", "מטר", "נעם");

    static TurnLedger.Changes nothing(boolean upcoming, String... upcomingTimes) {
        return new TurnLedger.Changes(List.of(), List.of(), 0, 0, false, false, false, true, upcoming, null,
                List.of(upcomingTimes));
    }

    static TurnLedger.Changes booked(List<String> ready) {
        return new TurnLedger.Changes(java.util.Collections.singletonList(null), List.of(), 0, 0, false, false,
                false, true, true, ready, List.of("09:00"));
    }

    @Test
    @DisplayName("'קובע את זה' with nothing booked becomes the confirm question, built from his day, time and children")
    void bookingThatDidNotHappen() {
        ClaimGuard.Result r = ClaimGuard.check("מעולה, קובע את זה - יום שישי 09:00-10:30 עם מטר ונעם 💪\n"
                + "אעדכן אותך בבוקר ושעה לפני כדי שתהיה מוכן.", nothing(true, "17:00"), false, KIDS);
        assertThat(r.changed()).isTrue();
        assertThat(r.body()).isEqualTo("רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם מטר ונעם.\nלקבוע?");
    }

    @Test
    @DisplayName("without a day and a time the question stays plain")
    void bookingWithoutDetails() {
        assertThat(ClaimGuard.check("קבעתי! 🙂", nothing(false), false, KIDS).body())
                .isEqualTo("עוד לא קבעתי את זה.\nלקבוע עכשיו?");
    }

    @Test
    @DisplayName("'רשום אצלי' for an activity nothing stores: said honestly, the good wish stays")
    void recordThatDidNotHappen() {
        ClaimGuard.Result r = ClaimGuard.check("מעולה, רשום אצלי 😊 בהצלחה עם הבישול והאיתמר היום ב-17:00!",
                nothing(true, "17:00"), false, KIDS);
        assertThat(r.body()).isEqualTo("את זה אין לי איפה לשמור.\n\nבהצלחה עם הבישול והאיתמר היום ב-17:00!");
    }

    @Test
    @DisplayName("'רק רגע', 'אחזור אליך', 'אעדכן אותך' - nothing runs after the reply: dropped")
    void promises() {
        ClaimGuard.Result r = ClaimGuard.check("וואו, לא ידעתי על מטר ונעם 🙂 רק רגע ונמשיך משם.", nothing(true), false, KIDS);
        assertThat(r.body()).isEqualTo("וואו, לא ידעתי על מטר ונעם 🙂");
        assertThat(ClaimGuard.check("בודק את זה. אחזור אליך עוד מעט.", nothing(true), false, KIDS).body()).isEqualTo("בודק את זה.");
    }

    @Test
    @DisplayName("a booking that happened: the ready confirmation replaces the model's own, its invented reminders go")
    void readyConfirmation() {
        List<String> ready = List.of("קבעתי 🎉 *מחר, יום שישי 9.10 ב-09:00*, שעה וחצי עם מטר ונעם.",
                "אזכיר לך שעה לפני, ואשאל אחר כך איך היה.", "השבוע מכוסה.");
        ClaimGuard.Result r = ClaimGuard.check("קבעתי! יום שישי 09:00-10:30 עם מטר ונעם 💪\nהשבוע מכוסה במלואו - 120 מתוך 120 "
                + "דקות ✅ אזכיר לך שעה לפני.", booked(ready), false, KIDS);
        assertThat(r.body()).isEqualTo(String.join("\n", ready));
        // copied: unchanged, also with emoji drift; a reminder line of its own goes
        assertThat(ClaimGuard.check(String.join("\n", ready), booked(ready), false, KIDS).changed()).isFalse();
        ClaimGuard.Result extra = ClaimGuard.check(String.join("\n", ready) + "\nאזכיר לך גם בבוקר.", booked(ready), false, KIDS);
        assertThat(extra.body()).isEqualTo(String.join("\n", ready));
    }

    @Test
    @DisplayName("a sentence about a session he already has is not a new-booking claim")
    void existingSessionIsNotAClaim() {
        assertThat(ClaimGuard.check("קבענו לשישי ב-09:00 עם מטר ונעם, הכול בתוקף.", nothing(true, "09:00"), false, KIDS)
                .changed()).isFalse();
        assertThat(ClaimGuard.check("אזכיר לך שעה לפני.", nothing(true, "17:00"), false, KIDS).changed()).isFalse();
        assertThat(ClaimGuard.check("אזכיר לך שעה לפני.", nothing(false), false, KIDS).body()).isEqualTo(ClaimGuard.NOTHING_DONE);
    }

    @Test
    @DisplayName("'שלחתי לך את הכפתור' with no button sent: the product sends it")
    void buttonClaim() {
        assertThat(ClaimGuard.check("שלחתי לך את הכפתור לדשבורד 🙂", nothing(false), false, KIDS).needsButton()).isTrue();
        assertThat(ClaimGuard.check("שלחתי לך את הכפתור לדשבורד 🙂", nothing(false), true, KIDS).needsButton()).isFalse();
        assertThat(ClaimGuard.withoutButtonClaims("אפשר לתקן את הגיל בדף. שלחתי לך את הכפתור, זה פתוח אצלך עכשיו 🙂"))
                .isEqualTo("אפשר לתקן את הגיל בדף.");
    }

    @Test
    @DisplayName("a goal 'סגרנו' with no goal and a cancellation that did not happen are said honestly")
    void goalAndCancel() {
        TurnLedger.Changes noGoal = new TurnLedger.Changes(List.of(), List.of(), 0, 0, false, false, false, false, false, null,
                List.of());
        assertThat(ClaimGuard.check("סגרנו, 3 שעות השבוע 💪", noGoal, false, KIDS).body())
                .isEqualTo("היעד של השבוע עוד לא נשמר.\nכמה שעות לשבוע הזה?");
        assertThat(ClaimGuard.check("ביטלתי את המפגש של מחר.", nothing(true), false, KIDS).body())
                .isEqualTo("עוד לא ביטלתי את זה.\nלבטל עכשיו?");
    }

    @Test
    @DisplayName("'X רשום אצלי בן 10' states a stored fact - not a claim (lab r2-e false positive)")
    void aStoredFactIsNotAClaim() {
        assertThat(ClaimGuard.check("איתמר רשום אצלי בן 10 🙂", nothing(false), false, KIDS).changed()).isFalse();
        assertThat(ClaimGuard.check("מעולה, רשום אצלי 😊", nothing(false), false, KIDS).changed()).isTrue();
    }

    @Test
    @DisplayName("an ordinary reply is never touched")
    void ordinary() {
        String reply = "גם זה סוג לגיטימי של זמן איכות.\nהעיקר שאתם יחד ולא כל אחד על המסך שלו 🙂";
        ClaimGuard.Result r = ClaimGuard.check(reply, nothing(true), false, KIDS);
        assertThat(r.changed()).isFalse();
        assertThat(r.body()).isEqualTo(reply);
    }
}
