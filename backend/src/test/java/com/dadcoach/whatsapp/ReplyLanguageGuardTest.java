package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Real replies from production and from replays of the production turn (2026-10-07). */
class ReplyLanguageGuardTest {

    private static final String ID = "❤️ דאד קואץ׳:\n";

    @Test
    void anEnglishReplyIsNeverSent() {
        assertThat(ReplyLanguageGuard.clean(ID + "Noted — is there something specific I can help you adjust about the week, "
                + "or would you like to leave it as is?")).isEmpty();
        assertThat(ReplyLanguageGuard.clean("Fine as is, no change needed.")).isEmpty();
        assertThat(ReplyLanguageGuard.clean("Clear, no action needed.")).isEmpty();
        assertThat(ReplyLanguageGuard.clean("Based on the weekly plan, the week is already covered (is_covered=true)")).isEmpty();
    }

    @Test
    void anEnglishNoteInFrontOfTheMessageIsCutOff() {
        assertThat(ReplyLanguageGuard.clean("Got it — זה בסדר, השבוע נשאר מכוסה כמו שקבענו."))
                .contains("זה בסדר, השבוע נשאר מכוסה כמו שקבענו.");
        assertThat(ReplyLanguageGuard.clean(ID + "No tool call needed - just clarifying.  מה בדיוק לא מסתדר לך?"))
                .contains(ID + "מה בדיוק לא מסתדר לך?");
        assertThat(ReplyLanguageGuard.clean("Nothing to actually do here—no new info or request. Just acknowledge warmly.  "
                + "לא נורא, השבוע בכל מקרה מכוסה 😊")).contains("לא נורא, השבוע בכל מקרה מכוסה 😊");
    }

    @Test
    void hebrewRepliesGoOutAsWritten() {
        String reply = ID + "קבעתי! יום שישי 09:00-10:30 עם מטר ונעם 💪\nהדף שלך: https://dad-coach-ui.onrender.com/home";
        assertThat(ReplyLanguageGuard.clean(reply)).contains(reply);
        assertThat(ReplyLanguageGuard.clean("Google Calendar מחובר, אפשר לקבוע")).contains("Google Calendar מחובר, אפשר לקבוע");
        assertThat(ReplyLanguageGuard.clean("*מעולה* 😊")).contains("*מעולה* 😊");
        assertThat(ReplyLanguageGuard.clean("👍")).contains("👍");
        assertThat(ReplyLanguageGuard.changes(reply)).isFalse();
    }
}
