package com.dadcoach.replies;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-036: only a message that is nothing but a fact question is answered before the AI. */
class ReadyQuestionsTest {

    @Test
    @DisplayName("his week, his progress and the reminder - as the whole message, with a greeting at most")
    void recognized() {
        assertThat(ReadyQuestions.of("מה יש לי השבוע?")).contains(ReadyQuestions.Kind.WEEK);
        assertThat(ReadyQuestions.of("היי, מה מתוכנן לי השבוע")).contains(ReadyQuestions.Kind.WEEK);
        assertThat(ReadyQuestions.of("מה קבענו?")).contains(ReadyQuestions.Kind.WEEK);
        assertThat(ReadyQuestions.of("איך אני עומד השבוע?")).contains(ReadyQuestions.Kind.PROGRESS);
        assertThat(ReadyQuestions.of("כמה חסר לי ליעד?")).contains(ReadyQuestions.Kind.PROGRESS);
        assertThat(ReadyQuestions.of("מתי תהיה התזכורת?")).contains(ReadyQuestions.Kind.REMINDER);
        assertThat(ReadyQuestions.of("מתי התזכורת")).contains(ReadyQuestions.Kind.REMINDER);
    }

    @Test
    @DisplayName("anything more stays with the coach")
    void notRecognized() {
        assertThat(ReadyQuestions.of("מה יש לי השבוע ומה עם שישי?")).isEmpty();
        assertThat(ReadyQuestions.of("רוצה לתכנן את השבוע עם הילדים")).isEmpty();
        assertThat(ReadyQuestions.of("מה המצב?")).isEmpty();
        assertThat(ReadyQuestions.of("ואחרי המפגש אתה שואל אותי?")).isEmpty();
        assertThat(ReadyQuestions.of("What do I have this week?")).isEmpty();
    }
}
