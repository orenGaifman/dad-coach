package com.dadcoach.domain.child;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Production showed "MALE" on a legacy child next to "girl" on new ones; the coach reads one vocabulary. */
class ChildGenderTest {

    @Test
    void legacyAndSynonymValuesReadAsBoyOrGirl() {
        Child child = new Child();
        child.setGender("MALE");
        assertThat(child.getGender()).isEqualTo("boy");
        child.setGender("female");
        assertThat(child.getGender()).isEqualTo("girl");
        child.setGender("בת");
        assertThat(child.getGender()).isEqualTo("girl");
        child.setGender("girl");
        assertThat(child.getGender()).isEqualTo("girl");
        child.setGender(null);
        assertThat(child.getGender()).isNull();
    }
}
