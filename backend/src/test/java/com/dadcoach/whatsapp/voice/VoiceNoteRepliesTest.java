package com.dadcoach.whatsapp.voice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** D-027: the echo of what was heard, and the lines for a note that was not. */
class VoiceNoteRepliesTest {

    @Test
    void theEchoGoesUnderTheIdentityLine() {
        assertThat(VoiceNoteReplies.withHeard("❤️ דאד קואץ׳:\nיופי, נקבע לשישי?", "רוצה  לקבוע\nזמן עם נועה"))
                .isEqualTo("❤️ דאד קואץ׳:\n🎙️ שמעתי: \"רוצה לקבוע זמן עם נועה\"\n\nיופי, נקבע לשישי?");
    }

    @Test
    void withoutAnIdentityLineTheEchoOpensTheReply() {
        assertThat(VoiceNoteReplies.withHeard("משהו השתבש אצלי, נסה שוב עוד רגע 🙏", "שלום"))
                .isEqualTo("🎙️ שמעתי: \"שלום\"\n\nמשהו השתבש אצלי, נסה שוב עוד רגע 🙏");
    }

    @Test
    void typedTextIsLeftAsItIsAndALongNoteIsCut() {
        assertThat(VoiceNoteReplies.withHeard("❤️ דאד קואץ׳:\nהיי", null)).isEqualTo("❤️ דאד קואץ׳:\nהיי");
        String echoed = VoiceNoteReplies.withHeard("היי", "א".repeat(500));
        assertThat(echoed).contains("…\"").hasSizeLessThan(260);
    }

    @Test
    void notHeardLinesAreSignedHebrewToHim() {
        assertThat(VoiceNoteReplies.notHeard(new VoiceNotes.Outcome.TooLong())).startsWith("❤️ דאד קואץ׳:\n🎙️").contains("ארוכה מדי");
        assertThat(VoiceNoteReplies.notHeard(new VoiceNotes.Outcome.Silent())).contains("לא שמעתי מילים").contains("נסה שוב");
        assertThat(VoiceNoteReplies.notHeard(new VoiceNotes.Outcome.Failed("X"))).contains("לא הצלחתי לשמוע").contains("לכתוב לי במילים");
        assertThat(VoiceNoteReplies.TURN_NOTE).doesNotContainPattern("[A-Za-z]");
    }
}
