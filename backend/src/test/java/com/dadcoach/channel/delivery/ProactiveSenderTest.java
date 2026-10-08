package com.dadcoach.channel.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** D-032: outside the 24-hour window the message is the one line of the general template's {{1}}. */
class ProactiveSenderTest {

    @Test
    void theIdentityLineIsDroppedBecauseTheTemplateBodyCarriesIt() {
        assertThat(ProactiveSender.asTemplateParameter("❤️ דאד קואץ׳:\nעוד שעה הזמן שלך ושל מאיה 🙂\nיש כבר רעיון מה תעשו?"))
                .isEqualTo("עוד שעה הזמן שלך ושל מאיה 🙂 יש כבר רעיון מה תעשו?");
    }

    @Test
    void linesStillReadAsSentencesAndListItemsKeepTheirBullet() {
        assertThat(ProactiveSender.asTemplateParameter("❤️ דאד קואץ׳:\nהיי גיל, השבוע חסרות עוד שעה וחצי ליעד\n\n"
                + "יש חלון *ביום רביעי ב-17:00*\nלקבוע עם מאיה?"))
                .isEqualTo("היי גיל, השבוע חסרות עוד שעה וחצי ליעד. יש חלון *ביום רביעי ב-17:00*. לקבוע עם מאיה?");
        assertThat(ProactiveSender.asTemplateParameter("רעיונות:\n• מגדל קוביות\n• ספר אהוב"))
                .isEqualTo("רעיונות: • מגדל קוביות • ספר אהוב");
    }

    @Test
    void neitherLineBreaksNorTabsNorLongSpacesReachMeta() {
        String flat = ProactiveSender.asTemplateParameter("שורה\tאחת     רווחים\r\n\r\nשורה שתיים");
        assertThat(flat).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t").doesNotContain("    ");
        assertThat(ProactiveSender.asTemplateParameter("טקסט בלי שורת זהות")).isEqualTo("טקסט בלי שורת זהות");
    }
}
