package com.dadcoach.channel.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.channel.delivery.ProactiveSender;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The catalog is what the owner submits to Meta, so it is held to Meta's rules and to the sender's. */
class WhatsAppTemplateCatalogTest {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\d+)}}");

    @Test
    @DisplayName("every entry obeys Meta: body neither starts nor ends with a variable, variables are 1..n, no newline in an example")
    void metaRules() {
        for (WhatsAppTemplateCatalog.Entry e : WhatsAppTemplateCatalog.ALL) {
            assertThat(e.body()).doesNotStartWith("{{").doesNotEndWith("}}");
            assertThat(e.body().length()).isLessThanOrEqualTo(1024);
            Matcher m = VARIABLE.matcher(e.body());
            int seen = 0;
            while (m.find()) {
                seen++;
                assertThat(Integer.parseInt(m.group(1))).isEqualTo(seen);
            }
            assertThat(seen).isEqualTo(e.maxVariables());
            assertThat(e.example()).doesNotContain("\n").doesNotContain("\t");
            assertThat(e.sample()).doesNotContain("{{");
            assertThat(e.language()).isEqualTo("he");
            assertThat(e.name()).endsWith("_he");
        }
    }

    @Test
    @DisplayName("the general template opens with the identity line the sender drops from {{1}}, so the name never shows twice")
    void identityLineOnce() {
        WhatsAppTemplateCatalog.Entry e = WhatsAppTemplateCatalog.named(WhatsAppTemplateCatalog.UPDATE_HE).orElseThrow();
        assertThat(e.body()).startsWith("❤️ דאד קואץ׳:\n");
        String message = "❤️ דאד קואץ׳:\nעוד שעה הזמן שלך ושל מאיה 🙂\nיש כבר רעיון מה תעשו?";
        String filled = e.body().replace("{{1}}", ProactiveSender.asTemplateParameter(message));
        assertThat(filled).containsOnlyOnce("דאד קואץ׳:");
        assertThat(filled).contains("עוד שעה הזמן שלך ושל מאיה 🙂 יש כבר רעיון מה תעשו?");
    }
}
