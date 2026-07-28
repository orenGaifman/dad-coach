package com.dadcoach.channel.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that manages approved WhatsApp message templates.
 *
 * The TemplateRegistry stores pre-approved templates and provides lookup,
 * variable substitution, and registration capabilities. Only templates with
 * APPROVED status are available for sending.
 *
 * Templates are required when the WhatsApp 24-hour session window is closed
 * and the system needs to proactively reach out to a father.
 */
@Service
public class TemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(TemplateRegistry.class);

    /** Pattern to match template variable placeholders like {{1}}, {{2}}, etc. */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\d+)}}");

    private static final String APPROVED_STATUS = "APPROVED";
    private static final String DEFAULT_LANGUAGE = "es";

    private final TemplateMessageRepository templateRepository;

    public TemplateRegistry(TemplateMessageRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * Look up an approved template by name using the default language (es).
     *
     * @param templateName the unique template name
     * @return the template if found and APPROVED, empty otherwise
     */
    public Optional<TemplateMessage> findApprovedTemplate(String templateName) {
        return findApprovedTemplate(templateName, DEFAULT_LANGUAGE);
    }

    /**
     * Look up an approved template by name and language.
     *
     * @param templateName the unique template name
     * @param language     the language code (e.g., "es")
     * @return the template if found and APPROVED, empty otherwise
     */
    public Optional<TemplateMessage> findApprovedTemplate(String templateName, String language) {
        return templateRepository.findByTemplateNameAndLanguage(templateName, language)
                .filter(TemplateMessage::isApproved);
    }

    /**
     * Get all approved templates.
     *
     * @return list of all templates with APPROVED status
     */
    public List<TemplateMessage> getAllApprovedTemplates() {
        return templateRepository.findByStatus(APPROVED_STATUS);
    }

    /**
     * Get all approved templates for a specific language.
     *
     * @param language the language code
     * @return list of approved templates for the given language
     */
    public List<TemplateMessage> getApprovedTemplatesByLanguage(String language) {
        return templateRepository.findByLanguageAndStatus(language, APPROVED_STATUS);
    }

    /**
     * Substitute variables in a template body with provided values.
     * Replaces {{1}}, {{2}}, etc. with corresponding values from the parameters map.
     *
     * @param templateBody the template body with placeholders
     * @param variables    map of variable index (as string) to replacement value
     * @return the body with all placeholders replaced
     * @throws IllegalArgumentException if required variables are missing
     */
    public String substituteVariables(String templateBody, Map<String, String> variables) {
        if (templateBody == null) {
            throw new IllegalArgumentException("Template body must not be null");
        }
        if (variables == null || variables.isEmpty()) {
            return templateBody;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(templateBody);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String variableIndex = matcher.group(1);
            String replacement = variables.get(variableIndex);
            if (replacement == null) {
                throw new IllegalArgumentException(
                        "Missing value for template variable {{" + variableIndex + "}}");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolve a template and substitute its variables in one step.
     * Only returns a result if the template is APPROVED.
     *
     * @param templateName the template name to look up
     * @param variables    map of variable index to replacement value
     * @return the rendered message body, or empty if template not found/not approved
     * @throws IllegalArgumentException if required variables are missing
     */
    public Optional<String> renderTemplate(String templateName, Map<String, String> variables) {
        return renderTemplate(templateName, DEFAULT_LANGUAGE, variables);
    }

    /**
     * Resolve a template by name and language, then substitute its variables.
     *
     * @param templateName the template name to look up
     * @param language     the language code
     * @param variables    map of variable index to replacement value
     * @return the rendered message body, or empty if template not found/not approved
     * @throws IllegalArgumentException if required variables are missing
     */
    public Optional<String> renderTemplate(String templateName, String language,
                                           Map<String, String> variables) {
        Optional<TemplateMessage> template = findApprovedTemplate(templateName, language);
        if (template.isEmpty()) {
            log.warn("Template not found or not approved: name={}, language={}", templateName, language);
            return Optional.empty();
        }

        TemplateMessage tmpl = template.get();
        String rendered = substituteVariables(tmpl.getBody(), variables);
        return Optional.of(rendered);
    }

    /**
     * Register a new template or update an existing one.
     * Used by admin processes to manage the template registry.
     *
     * @param templateName the unique template name
     * @param language     the language code
     * @param category     the template category (UTILITY, MARKETING, AUTHENTICATION)
     * @param body         the template body with variable placeholders
     * @param status       the template status (APPROVED, PENDING, REJECTED)
     * @param maxVariables the number of variable placeholders in the body
     * @return the saved template entity
     */
    @Transactional
    public TemplateMessage registerOrUpdate(String templateName, String language, String category,
                                            String body, String status, int maxVariables) {
        Optional<TemplateMessage> existing = templateRepository.findByTemplateName(templateName);

        if (existing.isPresent()) {
            TemplateMessage template = existing.get();
            template.setBody(body);
            template.setStatus(status);
            template.setCategory(category);
            template.setMaxVariables(maxVariables);
            log.info("Updated template: name={}, status={}", templateName, status);
            return templateRepository.save(template);
        }

        TemplateMessage template = new TemplateMessage(
                templateName, language, category, body, status, maxVariables);
        log.info("Registered new template: name={}, language={}, status={}", templateName, language, status);
        return templateRepository.save(template);
    }
}
