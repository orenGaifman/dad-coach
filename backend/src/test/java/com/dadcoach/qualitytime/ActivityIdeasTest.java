package com.dadcoach.qualitytime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** D-032: the ideas button shows one short line per idea, with the child's name and a verb in the child's form. */
class ActivityIdeasTest {

    private static final ActivityIdeas.ActivityIdea TOWER = ActivityIdeas.forChild(5, "he", null).get(0);

    @Test
    void theLineNamesTheChildrenWithoutAGenderedVerb() {
        // the child's gender is often unknown, so the lines never need it (D-032)
        assertThat(ActivityIdeas.line(TOWER, "מאיה", ActivityIdeas.Form.GIRL)).isEqualTo("מגדל קוביות ענק, בצבעים לבחירת מאיה");
        assertThat(ActivityIdeas.line(TOWER, "שחר", ActivityIdeas.Form.UNKNOWN)).isEqualTo("מגדל קוביות ענק, בצבעים לבחירת שחר");
        assertThat(ActivityIdeas.line(TOWER, "מטר ונעם", ActivityIdeas.Form.SEVERAL)).isEqualTo("מגדל קוביות ענק, בצבעים לבחירת מטר ונעם");
        assertThat(ActivityIdeas.line(TOWER, null, ActivityIdeas.Form.UNKNOWN)).isEqualTo("מגדל קוביות ענק, בצבעים לבחירת הילדים");
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
