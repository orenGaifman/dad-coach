package com.dadcoach.workflow.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MessageTemplate} entities.
 * 
 * Provides methods for querying message templates used as fallbacks
 * when AI message generation fails or times out.
 * 
 * Requirements: 10.4 - Every message type SHALL have a corresponding fallback template
 */
@Repository
public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    /**
     * Find a message template by message type and language.
     * Used to retrieve the specific localized template for a message type.
     * 
     * Requirements: 10.4
     * 
     * @param messageType the message type identifier (e.g., "WELCOME_GREETING")
     * @param language the ISO language code (e.g., "en" or "he")
     * @return the matching template, if found
     */
    Optional<MessageTemplate> findByMessageTypeAndLanguage(String messageType, String language);

    /**
     * Find all active templates for a message type.
     * Used to retrieve fallback templates when AI fails.
     * 
     * Requirements: 10.4
     * 
     * @param messageType the message type identifier
     * @param active whether the template is active
     * @return list of matching templates
     */
    List<MessageTemplate> findByMessageTypeAndActive(String messageType, boolean active);

    /**
     * Find a template by message type only.
     * Falls back to any language if specific language not found.
     * 
     * @param messageType the message type identifier
     * @return the matching template, if found
     */
    Optional<MessageTemplate> findByMessageType(String messageType);

    /**
     * Find all active templates.
     * Used for caching all templates at startup.
     * 
     * @return list of all active message templates
     */
    List<MessageTemplate> findByActive(boolean active);

    /**
     * Find all templates for a specific language.
     * 
     * @param language the ISO language code
     * @return list of templates for the language
     */
    List<MessageTemplate> findByLanguage(String language);
}
