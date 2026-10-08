package com.dadcoach.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-034 (D-9, D-10): the message standard in code - the vocabulary, one emoji, no " - " joining sentences. */
class ReplyStyleGuardTest {

    @Test
    @DisplayName("😊 becomes 🙂, 👍/✅ become 💪, other emoji go, and only the first one stays")
    void emoji() {
        assertThat(ReplyStyleGuard.clean("❤️ דאד קואץ׳:\nשמח שזה דיבר אליך 😊"))
                .isEqualTo("❤️ דאד קואץ׳:\nשמח שזה דיבר אליך 🙂");
        assertThat(ReplyStyleGuard.clean("סגרנו 👍 השבוע מכוסה ✅")).isEqualTo("סגרנו 💪 השבוע מכוסה");
        assertThat(ReplyStyleGuard.clean("העיקר שזה יחד 🎮")).isEqualTo("העיקר שזה יחד");
        assertThat(ReplyStyleGuard.clean("סליחה על זה 🙏 השבוע מכוסה.")).isEqualTo("סליחה על זה השבוע מכוסה.");
        assertThat(ReplyStyleGuard.clean("🎙️ שמעתי: \"היי\"\n\nהיי אורן 🙂 מה נשמע?"))
                .isEqualTo("🎙️ שמעתי: \"היי\"\n\nהיי אורן מה נשמע?");
        assertThat(ReplyStyleGuard.clean("קבעתי 🎉 *מחר ב-17:00*.")).isEqualTo("קבעתי 🎉 *מחר ב-17:00*.");
    }

    @Test
    @DisplayName("ideas behind emoji become a '•' list (production: 🥞 🎲 🚲)")
    void emojiList() {
        assertThat(ReplyStyleGuard.clean("כמה רעיונות:\n🥞 בישול יחד\n🎲 משחק לוח\n🚲 רכיבה בפארק\n\nתהנו 😊"))
                .isEqualTo("כמה רעיונות:\n• בישול יחד\n• משחק לוח\n• רכיבה בפארק\n\nתהנו 🙂");
    }

    @Test
    @DisplayName("' - ' between words becomes a comma; time ranges, number ranges and 'ב-17:00' stay")
    void dashes() {
        assertThat(ReplyStyleGuard.clean("נכון - אם זה לא יקרה, זה לא ייספר כאילו קרה."))
                .isEqualTo("נכון, אם זה לא יקרה, זה לא ייספר כאילו קרה.");
        assertThat(ReplyStyleGuard.clean("לגבי אורן — אתה!")).isEqualTo("לגבי אורן, אתה!");
        assertThat(ReplyStyleGuard.clean("סיימתי! - עכשיו שלושתם במעקב")).isEqualTo("סיימתי! עכשיו שלושתם במעקב");
        assertThat(ReplyStyleGuard.clean("יום שישי 09:00-10:30 עם מטר")).isEqualTo("יום שישי 09:00-10:30 עם מטר");
        assertThat(ReplyStyleGuard.clean("בין 09:00 - 10:00 או 2 - 3 שעות, ב-17:00 ומ-3")).isEqualTo("בין 09:00 - 10:00 או 2 - 3 שעות, ב-17:00 ומ-3");
        assertThat(ReplyStyleGuard.clean("2-3 שעות בשבוע")).isEqualTo("2-3 שעות בשבוע");
        assertThat(ReplyStyleGuard.clean("יש לך:\n- מחר 17:00 עם איתמר\n- שישי 09:00 עם מטר"))
                .isEqualTo("יש לך:\n• מחר 17:00 עם איתמר\n• שישי 09:00 עם מטר");
        assertThat(ReplyStyleGuard.clean("**שבוע טוב**")).isEqualTo("*שבוע טוב*");
    }

    @Test
    @DisplayName("the identity line is left as it is")
    void identity() {
        assertThat(ReplyStyleGuard.clean("❤️ דאד קואץ׳:\nהיי 🙂")).startsWith("❤️ דאד קואץ׳:\n");
    }
}
