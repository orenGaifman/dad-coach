package com.dadcoach.channel.template;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for TemplateMessage entities.
 * Provides lookup by template name and language, and filtering by status.
 */
@Repository
public interface TemplateMessageRepository extends JpaRepository<TemplateMessage, UUID> {

    /**
     * Find a template by its unique name.
     */
    Optional<TemplateMessage> findByTemplateName(String templateName);

    /**
     * Find a template by name and language.
     */
    Optional<TemplateMessage> findByTemplateNameAndLanguage(String templateName, String language);

    /**
     * Find all templates with a given status.
     */
    List<TemplateMessage> findByStatus(String status);

    /**
     * Find all templates for a given language and status.
     */
    List<TemplateMessage> findByLanguageAndStatus(String language, String status);
}
