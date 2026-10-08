package com.dadcoach.qualitytime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** D-032: the ideas button shows one short line per idea, with the child's name and a verb in the child's form. */
class ActivityIdeasTest {

    private static final ActivityIdeas.ActivityIdea TOWER = ActivityIdeas.forChild(5, "he", null).get(0);

    @Test
    void theVerbAgreesWithTheChild() {
        assertThat(ActivityIdeas.line(TOWER, "מאיה", ActivityIdeas.Form.GIRL)).isEqualTo("מגדל קוביות ענק, ומאיה בוחרת את הצבעים");
        assertThat(ActivityIdeas.line(TOWER, "יובל", ActivityIdeas.Form.BOY)).isEqualTo("מגדל קוביות ענק, ויובל בוחר את הצבעים");
        assertThat(ActivityIdeas.line(TOWER, "מטר ונעם", ActivityIdeas.Form.SEVERAL))
                .isEqualTo("מגדל קוביות ענק, ומטר ונעם בוחרים את הצבעים");
    }

    @Test
    void aChildWhoseGenderIsUnknownIsNeverGivenAGuessedForm() {
        assertThat(ActivityIdeas.line(TOWER, "שחר", ActivityIdeas.Form.UNKNOWN)).isEqualTo("מגדל קוביות ענק, ושחר ואתה בוחרים את הצבעים");
        assertThat(ActivityIdeas.line(TOWER, null, ActivityIdeas.Form.UNKNOWN)).isEqualTo("מגדל קוביות ענק, ואתם בוחרים את הצבעים");
    }

    @Test
    void everyHebrewIdeaHasAShortLineWithNoPlaceholderLeft() {
        for (int age : List.of(4, 8, 12)) {
            for (ActivityIdeas.ActivityIdea idea : ActivityIdeas.forChild(age, "he", null)) {
                String line = ActivityIdeas.line(idea, "מאיה", ActivityIdeas.Form.GIRL);
                assertThat(line).doesNotContain("{").doesNotContain("}").doesNotContain("|").doesNotContain("דק׳");
                assertThat(line.length()).isLessThanOrEqualTo(60);
            }
        }
    }
}
