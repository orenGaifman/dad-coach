package com.dadcoach.channel.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TemplateRegistry covering:
 * - Variable substitution logic
 * - Template lookup by name and language
 * - Only APPROVED templates available for sending
 * - Registration/update via admin process
 */
class TemplateRegistryTest {

    private TemplateMessageRepository repository;
    private TemplateRegistry registry;

    @BeforeEach
    void setUp() {
        repository = mock(TemplateMessageRepository.class);
        registry = new TemplateRegistry(repository);
    }

    // ===== Variable Substitution =====

    @Nested
    @DisplayName("Variable substitution")
    class VariableSubstitutionTests {

        @Test
        @DisplayName("replaces single variable placeholder")
        void substitutesSingleVariable() {
            String body = "Hello {{1}}!";
            Map<String, String> vars = Map.of("1", "Carlos");

            String result = registry.substituteVariables(body, vars);

            assertEquals("Hello Carlos!", result);
        }

        @Test
        @DisplayName("replaces multiple variable placeholders")
        void substitutesMultipleVariables() {
            String body = "{{1}}, here is your weekly summary: {{2}}";
            Map<String, String> vars = Map.of("1", "Carlos", "2", "You completed 3 missions");

            String result = registry.substituteVariables(body, vars);

            assertEquals("Carlos, here is your weekly summary: You completed 3 missions", result);
        }

        @Test
        @DisplayName("throws when required variable is missing")
        void throwsOnMissingVariable() {
            String body = "Hello {{1}}! {{2}}";
            Map<String, String> vars = Map.of("1", "Carlos");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> registry.substituteVariables(body, vars)
            );
            assertTrue(ex.getMessage().contains("{{2}}"));
        }

        @Test
        @DisplayName("returns body unchanged when no placeholders exist")
        void noPlaceholders_returnsBodyUnchanged() {
            String body = "System under maintenance";
            Map<String, String> vars = Map.of("1", "unused");

            String result = registry.substituteVariables(body, vars);

            assertEquals("System under maintenance", result);
        }

        @Test
        @DisplayName("returns body unchanged when variables map is null")
        void nullVariables_returnsBody() {
            String body = "Message without variables";

            String result = registry.substituteVariables(body, null);

            assertEquals("Message without variables", result);
        }

        @Test
        @DisplayName("returns body unchanged when variables map is empty")
        void emptyVariables_returnsBody() {
            String body = "Message without variables";

            String result = registry.substituteVariables(body, Map.of());

            assertEquals("Message without variables", result);
        }

        @Test
        @DisplayName("throws on null template body")
        void nullBody_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> registry.substituteVariables(null, Map.of("1", "val")));
        }

        @Test
        @DisplayName("handles variables with special regex characters")
        void variablesWithSpecialChars() {
            String body = "Hello {{1}}";
            Map<String, String> vars = Map.of("1", "price: $100.00");

            String result = registry.substituteVariables(body, vars);

            assertEquals("Hello price: $100.00", result);
        }
    }

    // ===== Lookup by name and language =====

    @Nested
    @DisplayName("Template lookup")
    class LookupTests {

        @Test
        @DisplayName("finds approved template by name with default language")
        void findsApprovedByName() {
            TemplateMessage template = new TemplateMessage(
                    "daily_coaching", "en", "UTILITY",
                    "Hello {{1}}! {{2}}", "APPROVED", 2);
            when(repository.findByTemplateNameAndLanguage("daily_coaching", "en"))
                    .thenReturn(Optional.of(template));

            Optional<TemplateMessage> result = registry.findApprovedTemplate("daily_coaching");

            assertTrue(result.isPresent());
            assertEquals("daily_coaching", result.get().getTemplateName());
        }

        @Test
        @DisplayName("finds approved template by name and language")
        void findsApprovedByNameAndLanguage() {
            TemplateMessage template = new TemplateMessage(
                    "weekly_summary", "en", "UTILITY",
                    "{{1}}, here is your summary: {{2}}", "APPROVED", 2);
            when(repository.findByTemplateNameAndLanguage("weekly_summary", "en"))
                    .thenReturn(Optional.of(template));

            Optional<TemplateMessage> result = registry.findApprovedTemplate("weekly_summary", "en");

            assertTrue(result.isPresent());
            assertEquals("weekly_summary", result.get().getTemplateName());
        }

        @Test
        @DisplayName("returns empty when template not found")
        void returnsEmptyWhenNotFound() {
            when(repository.findByTemplateNameAndLanguage("nonexistent", "en"))
                    .thenReturn(Optional.empty());

            Optional<TemplateMessage> result = registry.findApprovedTemplate("nonexistent");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty when template exists but is not APPROVED")
        void returnsEmptyForNonApproved() {
            TemplateMessage pendingTemplate = new TemplateMessage(
                    "pending_template", "en", "UTILITY",
                    "Some body", "PENDING", 0);
            when(repository.findByTemplateNameAndLanguage("pending_template", "en"))
                    .thenReturn(Optional.of(pendingTemplate));

            Optional<TemplateMessage> result = registry.findApprovedTemplate("pending_template");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty when template is REJECTED")
        void returnsEmptyForRejected() {
            TemplateMessage rejectedTemplate = new TemplateMessage(
                    "rejected_template", "en", "MARKETING",
                    "Promo body", "REJECTED", 0);
            when(repository.findByTemplateNameAndLanguage("rejected_template", "en"))
                    .thenReturn(Optional.of(rejectedTemplate));

            Optional<TemplateMessage> result = registry.findApprovedTemplate("rejected_template");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getAllApprovedTemplates returns only APPROVED templates")
        void getAllApproved() {
            List<TemplateMessage> approved = List.of(
                    new TemplateMessage("daily_coaching", "en", "UTILITY", "body1", "APPROVED", 2),
                    new TemplateMessage("system_notice", "en", "UTILITY", "body2", "APPROVED", 1)
            );
            when(repository.findByStatus("APPROVED")).thenReturn(approved);

            List<TemplateMessage> result = registry.getAllApprovedTemplates();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("getApprovedTemplatesByLanguage filters by language")
        void getApprovedByLanguage() {
            List<TemplateMessage> enTemplates = List.of(
                    new TemplateMessage("daily_coaching", "en", "UTILITY", "body", "APPROVED", 2)
            );
            when(repository.findByLanguageAndStatus("en", "APPROVED")).thenReturn(enTemplates);

            List<TemplateMessage> result = registry.getApprovedTemplatesByLanguage("en");

            assertEquals(1, result.size());
            assertEquals("en", result.get(0).getLanguage());
        }

        @Test
        @DisplayName("finds Hebrew template by language")
        void findsHebrewTemplate() {
            TemplateMessage template = new TemplateMessage(
                    "daily_coaching_he", "he", "UTILITY",
                    "שלום {{1}}! {{2}}", "APPROVED", 2);
            when(repository.findByTemplateNameAndLanguage("daily_coaching_he", "he"))
                    .thenReturn(Optional.of(template));

            Optional<TemplateMessage> result = registry.findApprovedTemplate("daily_coaching_he", "he");

            assertTrue(result.isPresent());
            assertEquals("he", result.get().getLanguage());
        }
    }

    // ===== Render template (lookup + substitution) =====

    @Nested
    @DisplayName("Template rendering")
    class RenderTests {

        @Test
        @DisplayName("renders approved template with variable substitution")
        void rendersApprovedTemplate() {
            TemplateMessage template = new TemplateMessage(
                    "daily_coaching", "en", "UTILITY",
                    "Hello {{1}}! {{2}}", "APPROVED", 2);
            when(repository.findByTemplateNameAndLanguage("daily_coaching", "en"))
                    .thenReturn(Optional.of(template));

            Optional<String> result = registry.renderTemplate(
                    "daily_coaching", Map.of("1", "Carlos", "2", "How is your week going?"));

            assertTrue(result.isPresent());
            assertEquals("Hello Carlos! How is your week going?", result.get());
        }

        @Test
        @DisplayName("returns empty when rendering non-existent template")
        void renderNonExistent_returnsEmpty() {
            when(repository.findByTemplateNameAndLanguage("nonexistent", "en"))
                    .thenReturn(Optional.empty());

            Optional<String> result = registry.renderTemplate("nonexistent", Map.of("1", "val"));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty when rendering non-APPROVED template")
        void renderNonApproved_returnsEmpty() {
            TemplateMessage pending = new TemplateMessage(
                    "pending", "en", "UTILITY", "{{1}}", "PENDING", 1);
            when(repository.findByTemplateNameAndLanguage("pending", "en"))
                    .thenReturn(Optional.of(pending));

            Optional<String> result = registry.renderTemplate("pending", Map.of("1", "val"));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("renders template with specific language")
        void rendersWithLanguage() {
            TemplateMessage template = new TemplateMessage(
                    "welcome_back", "en", "UTILITY",
                    "{{1}}! Great to see you back! {{2}}", "APPROVED", 2);
            when(repository.findByTemplateNameAndLanguage("welcome_back", "en"))
                    .thenReturn(Optional.of(template));

            Optional<String> result = registry.renderTemplate(
                    "welcome_back", "en",
                    Map.of("1", "Miguel", "2", "Your team was waiting for you"));

            assertTrue(result.isPresent());
            assertEquals("Miguel! Great to see you back! Your team was waiting for you", result.get());
        }

        @Test
        @DisplayName("renders Hebrew template correctly")
        void rendersHebrewTemplate() {
            TemplateMessage template = new TemplateMessage(
                    "welcome_back_he", "he", "UTILITY",
                    "{{1}}! כיף לראות אותך שוב! {{2}}", "APPROVED", 2);
            when(repository.findByTemplateNameAndLanguage("welcome_back_he", "he"))
                    .thenReturn(Optional.of(template));

            Optional<String> result = registry.renderTemplate(
                    "welcome_back_he", "he",
                    Map.of("1", "מיכאל", "2", "הצוות שלך חיכה לך"));

            assertTrue(result.isPresent());
            assertTrue(result.get().contains("מיכאל"));
        }
    }

    // ===== Registration/Update via admin process =====

    @Nested
    @DisplayName("Template registration and update")
    class RegistrationTests {

        @Test
        @DisplayName("registers new template when not existing")
        void registersNewTemplate() {
            when(repository.findByTemplateName("new_template")).thenReturn(Optional.empty());
            when(repository.save(any(TemplateMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            TemplateMessage result = registry.registerOrUpdate(
                    "new_template", "en", "UTILITY",
                    "Hello {{1}}", "APPROVED", 1);

            assertEquals("new_template", result.getTemplateName());
            assertEquals("en", result.getLanguage());
            assertEquals("UTILITY", result.getCategory());
            assertEquals("Hello {{1}}", result.getBody());
            assertEquals("APPROVED", result.getStatus());
            assertEquals(1, result.getMaxVariables());
            verify(repository).save(any(TemplateMessage.class));
        }

        @Test
        @DisplayName("updates existing template body and status")
        void updatesExistingTemplate() {
            TemplateMessage existing = new TemplateMessage(
                    "daily_coaching", "en", "UTILITY",
                    "Old body {{1}}", "PENDING", 1);
            when(repository.findByTemplateName("daily_coaching")).thenReturn(Optional.of(existing));
            when(repository.save(any(TemplateMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            TemplateMessage result = registry.registerOrUpdate(
                    "daily_coaching", "en", "UTILITY",
                    "New body {{1}} {{2}}", "APPROVED", 2);

            assertEquals("New body {{1}} {{2}}", result.getBody());
            assertEquals("APPROVED", result.getStatus());
            assertEquals(2, result.getMaxVariables());
            verify(repository).save(existing);
        }

        @Test
        @DisplayName("updates category when modifying existing template")
        void updatesCategory() {
            TemplateMessage existing = new TemplateMessage(
                    "promo_template", "en", "MARKETING",
                    "Promo {{1}}", "APPROVED", 1);
            when(repository.findByTemplateName("promo_template")).thenReturn(Optional.of(existing));
            when(repository.save(any(TemplateMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            TemplateMessage result = registry.registerOrUpdate(
                    "promo_template", "en", "UTILITY",
                    "Promo {{1}}", "APPROVED", 1);

            assertEquals("UTILITY", result.getCategory());
        }
    }
}
