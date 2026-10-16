package com.dadcoach.channel.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The catalog is what the owner submits to Meta, so it is held to Meta's rules, and the session templates are held
 * to the lines the coach is told to write (the workflow YAML) — a change there fails here until the template follows.
 */
class WhatsAppTemplateCatalogTest {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\d+)}}");
    private static final Path WORKFLOW = Path.of("..", "provisioning", "config", "dad-coach-3-workflow.yaml");

    @Test
    @DisplayName("every entry obeys Meta: no variable at either end, none side by side, 3 own words per variable, plain buttons")
    void metaRules() {
        assertThat(WhatsAppTemplateCatalog.ALL.stream().map(WhatsAppTemplateCatalog.Entry::name).distinct())
                .hasSameSizeAs(WhatsAppTemplateCatalog.ALL);
        for (WhatsAppTemplateCatalog.Entry e : WhatsAppTemplateCatalog.ALL) {
            assertThat(e.name()).matches("[a-z0-9_]+_he");
            assertThat(e.category()).isEqualTo("UTILITY");
            assertThat(e.body()).startsWith(WhatsAppTemplateCatalog.IDENTITY);
            assertThat(e.body().strip()).doesNotStartWith("{{").doesNotEndWith("}}");
            assertThat(e.body().length()).isLessThanOrEqualTo(1024);
            Matcher m = VARIABLE.matcher(e.body());
            int seen = 0;
            while (m.find()) {
                seen++;
                assertThat(Integer.parseInt(m.group(1))).as(e.name() + " variables in order").isEqualTo(seen);
            }
            assertThat(seen).as(e.name() + " one example per variable").isEqualTo(e.maxVariables());
            String[] between = e.body().split("\\{\\{\\d+}}");
            for (int i = 1; i < between.length - 1; i++) {
                assertThat(between[i]).as(e.name() + ": a word between {{" + i + "}} and {{" + (i + 1) + "}}")
                        .containsPattern("\\p{L}{2,}");
            }
            long ownWords = Arrays.stream(VARIABLE.matcher(e.body()).replaceAll(" ").split("\\s+"))
                    .filter(w -> w.codePoints().anyMatch(Character::isLetter)).count();
            assertThat(ownWords).as(e.name() + " own words").isGreaterThanOrEqualTo(3L * e.maxVariables());
            for (String example : e.examples()) {
                assertThat(example).isNotBlank().doesNotContain("\n").doesNotContain("\t");
            }
            assertThat(e.sample()).doesNotContain("{{");
            for (String button : e.quickReplies()) {
                assertThat(button.length()).isLessThanOrEqualTo(25);
                assertThat(button).as(e.name() + " button is plain text").matches("[\\p{L} ]+");
            }
            if (e.urlButton() != null) {
                assertThat(e.urlButton().text().length()).isLessThanOrEqualTo(25);
                assertThat(e.urlButton().text()).as(e.name() + " URL button is plain text").matches("[\\p{L} ]+");
                assertThat(e.urlButton().path()).as("one dynamic suffix, at the end").startsWith("/").endsWith("{{1}}")
                        .containsOnlyOnce("{{");
            }
        }
    }

    @Test
    @DisplayName("D-067: the morning's dashboard twin - the morning's very body, no quick replies, the dashboard card's label "
            + "on a URL button to the sign-in link (token in the fragment)")
    void morningDashboardTwin() {
        WhatsAppTemplateCatalog.Entry morning = WhatsAppTemplateCatalog.require(WhatsAppTemplateCatalog.SESSION_MORNING_HE);
        WhatsAppTemplateCatalog.Entry dash = WhatsAppTemplateCatalog.require(WhatsAppTemplateCatalog.SESSION_MORNING_DASH_HE);
        assertThat(dash.name()).isEqualTo("dad_coach_session_morning_dash_he");
        assertThat(dash.body()).isEqualTo(morning.body()).isEqualTo(WhatsAppTemplateCatalog.IDENTITY
                + "*היום ב-{{1}}* זה הזמן שלך ושל {{2}} 🙂");
        assertThat(dash.category()).isEqualTo(morning.category()).isEqualTo("UTILITY");
        assertThat(dash.language()).isEqualTo("he");
        assertThat(dash.examples()).isEqualTo(morning.examples());
        assertThat(dash.quickReplies()).isEmpty();
        assertThat(morning.urlButton()).isNull();
        assertThat(dash.urlButton().text()).isEqualTo(com.dadcoach.auth.LoginLinkDelivery.fatherLabel()).isEqualTo("כניסה לדף שלי");
        assertThat(dash.urlButton().path()).isEqualTo("/auth/consume#token={{1}}");
        assertThat(dash.urlButton().url("https://dad-coach.example/")).isEqualTo("https://dad-coach.example/auth/consume#token={{1}}");
        assertThat(TemplateCall.of(WhatsAppTemplateCatalog.SESSION_MORNING_DASH_HE, "17:00", "מאיה").text())
                .isEqualTo(TemplateCall.of(WhatsAppTemplateCatalog.SESSION_MORNING_HE, "17:00", "מאיה").text());
    }

    @Test
    @DisplayName("D-067: Meta's definition of the new template - body with its examples, one URL button at the web base URL")
    void metaDefinitionOfTheDashboardTwin() throws Exception {
        java.util.Map<String, Object> def = com.dadcoach.web.admin.AdminTemplatesController.definition(
                WhatsAppTemplateCatalog.require(WhatsAppTemplateCatalog.SESSION_MORNING_DASH_HE), "https://dad-coach.example");
        String expected = """
                {"name":"dad_coach_session_morning_dash_he","language":"he","category":"UTILITY","components":[\
                {"type":"BODY","text":"❤️ דאד קואץ׳:\\n*היום ב-{{1}}* זה הזמן שלך ושל {{2}} 🙂",\
                "example":{"body_text":[["17:00","מאיה"]]}},\
                {"type":"BUTTONS","buttons":[{"type":"URL","text":"כניסה לדף שלי",\
                "url":"https://dad-coach.example/auth/consume#token={{1}}",\
                "example":["https://dad-coach.example/auth/consume#token=Xk3vQ9pLm2RtY7wB4nZc8dHs6fJa1gEu5iKo0qWxTyU"]}]}]}""";
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(json.readTree(json.writeValueAsString(def))).isEqualTo(json.readTree(expected));
        // a template without a URL button keeps its quick replies only
        java.util.Map<String, Object> hour = com.dadcoach.web.admin.AdminTemplatesController.definition(
                WhatsAppTemplateCatalog.require(WhatsAppTemplateCatalog.SESSION_HOUR_BEFORE_HE), "https://dad-coach.example");
        assertThat(json.writeValueAsString(hour)).contains("{\"type\":\"QUICK_REPLY\",\"text\":\"רוצה רעיונות\"}")
                .doesNotContain("URL");
    }

    @Test
    @DisplayName("the session templates are the lines the workflow tells the coach to write")
    void sessionTemplatesMirrorTheWorkflow() throws IOException {
        String workflow = Files.readString(WORKFLOW);
        assertThat(workflow).contains(line(WhatsAppTemplateCatalog.SESSION_MORNING_HE, 0, "17:00", "[child]"));
        assertThat(workflow).contains(line(WhatsAppTemplateCatalog.SESSION_HOUR_BEFORE_HE, 0, "[child]"));
        assertThat(workflow).contains(line(WhatsAppTemplateCatalog.SESSION_HOUR_BEFORE_HE, 1, "[child]"));
        assertThat(workflow).contains(line(WhatsAppTemplateCatalog.SESSION_FOLLOW_UP_HE, 0, "[child]"));
    }

    /** The template's line {@code index} under the identity line, with {{1}}.. replaced by {@code values}. */
    private static String line(String template, int index, String... values) {
        WhatsAppTemplateCatalog.Entry e = WhatsAppTemplateCatalog.require(template);
        return e.render(java.util.List.of(values)).substring(WhatsAppTemplateCatalog.IDENTITY.length()).split("\n")[index];
    }

    @Test
    @DisplayName("the general template opens with the identity line the sender drops from {{1}}, so the name never shows twice")
    void identityLineOnce() {
        WhatsAppTemplateCatalog.Entry e = WhatsAppTemplateCatalog.require(WhatsAppTemplateCatalog.UPDATE_HE);
        String message = "❤️ דאד קואץ׳:\nעוד שעה הזמן שלך ושל מאיה 🙂\nיש כבר רעיון מה תעשו?";
        String filled = e.body().replace("{{1}}", TemplateText.asTemplateParameter(message));
        assertThat(filled).containsOnlyOnce("דאד קואץ׳:");
        assertThat(filled).contains("עוד שעה הזמן שלך ושל מאיה 🙂 יש כבר רעיון מה תעשו?");
    }

    @Test
    @DisplayName("a call is refused when its values or button payloads do not fit the template")
    void callsFitTheirTemplate() {
        assertThat(TemplateCall.of(WhatsAppTemplateCatalog.SESSION_MORNING_HE, "17:00", "מאיה").text())
                .isEqualTo("❤️ דאד קואץ׳:\n*היום ב-17:00* זה הזמן שלך ושל מאיה 🙂");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TemplateCall.of(WhatsAppTemplateCatalog.SESSION_MORNING_HE, "17:00"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TemplateCall.of(WhatsAppTemplateCatalog.SESSION_FOLLOW_UP_HE, "מאיה"))
                .as("the follow-up needs its two button payloads").isInstanceOf(IllegalArgumentException.class);
    }
}
