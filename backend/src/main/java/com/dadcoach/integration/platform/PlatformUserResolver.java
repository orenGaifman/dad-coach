package com.dadcoach.integration.platform;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the Workflow Platform's user id back to a father.
 *
 * <p>Dad Coach sends the platform {@code userId = "<channel>:<identifier>"} (e.g.
 * {@code whatsapp:+972501234567}, see {@code PlatformWorkflowEngine}); the platform echoes that id
 * on everything it calls back with. For WhatsApp the identifier is the father's phone.</p>
 */
@Component
public class PlatformUserResolver {

    private static final Logger log = LoggerFactory.getLogger(PlatformUserResolver.class);

    private final FatherRepository fatherRepository;

    public PlatformUserResolver(FatherRepository fatherRepository) {
        this.fatherRepository = fatherRepository;
    }

    public Optional<Father> resolve(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }

        // Parse user ID format: "channel:identifier"
        String[] parts = userId.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid user ID format: {}", userId);
            return Optional.empty();
        }

        String channel = parts[0];
        String identifier = parts[1];

        // For WhatsApp, the identifier is the phone number
        if ("whatsapp".equalsIgnoreCase(channel)) {
            return fatherRepository.findByPhone(identifier);
        }

        log.warn("Unsupported channel: {}", channel);
        return Optional.empty();
    }
}
