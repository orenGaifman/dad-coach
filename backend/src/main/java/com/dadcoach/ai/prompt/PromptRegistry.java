package com.dadcoach.ai.prompt;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for versioned prompt templates with A/B test support.
 *
 * <p>Loads prompt templates from YAML resource files at startup and provides
 * version-aware retrieval with deterministic A/B test group assignment.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Templates loaded from classpath:prompts/*.yml at startup</li>
 *   <li>At most 1 active version per prompt_type per ab_test_group</li>
 *   <li>A/B test group assignment is deterministic via hash(father_id) % 2</li>
 *   <li>Version immutability: content cannot be changed once registered</li>
 * </ul>
 *
 * @see PromptTemplate
 * @see AbTestAssigner
 */
@Service
public class PromptRegistry {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistry.class);
    private static final String PROMPTS_LOCATION = "classpath:prompts/*.yml";

    /**
     * All registered templates indexed by type, then by version string.
     */
    private final Map<PromptType, List<PromptTemplate>> templates = new ConcurrentHashMap<>();

    @PostConstruct
    void loadTemplates() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(PROMPTS_LOCATION);
            log.info("Loading prompt templates from {} resource files", resources.length);

            for (Resource resource : resources) {
                loadFromResource(resource);
            }

            log.info("Loaded {} prompt types with {} total templates",
                templates.size(),
                templates.values().stream().mapToInt(List::size).sum());
        } catch (IOException e) {
            log.warn("No prompt templates found at '{}': {}", PROMPTS_LOCATION, e.getMessage());
        }
    }

    /**
     * Load templates from a single YAML resource file.
     * Expected YAML structure:
     * <pre>
     * prompt_type: SYSTEM
     * templates:
     *   - version: "1.0.0"
     *     content: "You are a coaching assistant for {{father_name}}..."
     *     is_active: true
     *     ab_test_group: null
     *   - version: "1.1.0"
     *     content: "You are an empathetic coach..."
     *     is_active: false
     *     ab_test_group: "B"
     * </pre>
     */
    @SuppressWarnings("unchecked")
    void loadFromResource(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                log.warn("Empty YAML file: {}", resource.getFilename());
                return;
            }

            String typeStr = (String) data.get("prompt_type");
            if (typeStr == null) {
                log.warn("YAML file missing 'prompt_type': {}", resource.getFilename());
                return;
            }

            PromptType promptType = PromptType.valueOf(typeStr.toUpperCase());
            List<Map<String, Object>> templateList = (List<Map<String, Object>>) data.get("templates");

            if (templateList == null || templateList.isEmpty()) {
                log.warn("YAML file has no templates: {}", resource.getFilename());
                return;
            }

            for (Map<String, Object> entry : templateList) {
                PromptTemplate template = parseTemplateEntry(promptType, entry);
                register(template);
            }

            log.debug("Loaded {} templates for type {} from {}",
                templateList.size(), promptType, resource.getFilename());
        } catch (IOException e) {
            log.error("Failed to load prompt template from {}: {}", resource.getFilename(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Invalid prompt template in {}: {}", resource.getFilename(), e.getMessage());
        }
    }

    private PromptTemplate parseTemplateEntry(PromptType promptType, Map<String, Object> entry) {
        String versionStr = (String) entry.get("version");
        String content = (String) entry.get("content");
        Boolean isActive = (Boolean) entry.getOrDefault("is_active", false);
        String abTestGroup = (String) entry.get("ab_test_group");

        String createdAtStr = (String) entry.get("created_at");
        Instant createdAt = createdAtStr != null ? Instant.parse(createdAtStr) : Instant.now();

        return new PromptTemplate(
            promptType,
            PromptVersion.parse(versionStr),
            content,
            isActive,
            abTestGroup,
            createdAt
        );
    }

    /**
     * Register a template into the registry.
     * Enforces version immutability — duplicate type+version+group is rejected.
     */
    public void register(PromptTemplate template) {
        Objects.requireNonNull(template, "template must not be null");

        templates.computeIfAbsent(template.promptType(), k -> new ArrayList<>());
        List<PromptTemplate> typeTemplates = templates.get(template.promptType());

        // Check for duplicate version+group (immutability enforcement)
        boolean exists = typeTemplates.stream().anyMatch(t ->
            t.version().equals(template.version()) &&
            Objects.equals(t.abTestGroup(), template.abTestGroup())
        );
        if (exists) {
            throw new IllegalArgumentException(
                "Template already exists: %s version %s group %s. Versions are immutable."
                    .formatted(template.promptType(), template.version(), template.abTestGroup()));
        }

        typeTemplates.add(template);
    }

    /**
     * Get the active template for a given prompt type.
     * If there's no A/B test active, returns the single active version.
     * If there's an A/B test, returns the template matching the global active (no group).
     *
     * @param promptType the type of prompt template to retrieve
     * @return the active template, or empty if none is active
     */
    public Optional<PromptTemplate> getActiveTemplate(PromptType promptType) {
        List<PromptTemplate> typeTemplates = templates.getOrDefault(promptType, List.of());
        return typeTemplates.stream()
            .filter(PromptTemplate::isActive)
            .filter(t -> t.abTestGroup() == null)
            .findFirst()
            .or(() -> typeTemplates.stream()
                .filter(PromptTemplate::isActive)
                .findFirst());
    }

    /**
     * Get the active template for a prompt type for a specific father,
     * taking A/B test group assignment into account.
     *
     * <p>If an A/B test is active for this prompt type:
     * - Determine the father's group via AbTestAssigner
     * - Return the active template matching their group
     * - If no group-specific template exists, fall back to the default active
     *
     * @param promptType the type of prompt template
     * @param fatherId the father's unique identifier
     * @return the appropriate template for this father
     */
    public Optional<PromptTemplate> getActiveTemplateForFather(PromptType promptType, UUID fatherId) {
        Objects.requireNonNull(fatherId, "fatherId must not be null");
        String group = AbTestAssigner.assignGroup(fatherId);

        List<PromptTemplate> typeTemplates = templates.getOrDefault(promptType, List.of());

        // First try: active template matching this father's A/B group
        Optional<PromptTemplate> groupTemplate = typeTemplates.stream()
            .filter(PromptTemplate::isActive)
            .filter(t -> group.equals(t.abTestGroup()))
            .findFirst();

        if (groupTemplate.isPresent()) {
            return groupTemplate;
        }

        // Fallback: active template with no A/B group (default)
        return typeTemplates.stream()
            .filter(PromptTemplate::isActive)
            .filter(t -> t.abTestGroup() == null)
            .findFirst();
    }

    /**
     * Get all versions registered for a prompt type, sorted by version descending.
     */
    public List<PromptTemplate> getAllVersions(PromptType promptType) {
        return templates.getOrDefault(promptType, List.of()).stream()
            .sorted(Comparator.comparing(PromptTemplate::version).reversed())
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get a specific version of a prompt template.
     */
    public Optional<PromptTemplate> getVersion(PromptType promptType, PromptVersion version) {
        return templates.getOrDefault(promptType, List.of()).stream()
            .filter(t -> t.version().equals(version))
            .filter(t -> t.abTestGroup() == null)
            .findFirst();
    }

    /**
     * Get all registered prompt types that have at least one template.
     */
    public Set<PromptType> getRegisteredTypes() {
        return templates.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return total number of templates in the registry
     */
    public int size() {
        return templates.values().stream().mapToInt(List::size).sum();
    }
}
