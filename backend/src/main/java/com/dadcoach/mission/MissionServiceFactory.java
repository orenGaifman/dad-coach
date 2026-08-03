package com.dadcoach.mission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory for obtaining the appropriate {@link MissionService} for a given {@link MissionType}.
 * 
 * <p>Auto-discovers all {@link MissionService} implementations via Spring and maps each
 * service to its supported mission type. For MVP, only {@link MissionType#QUALITY_TIME}
 * is implemented.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * // Get service for a specific type
 * MissionService service = missionServiceFactory.getService(MissionType.QUALITY_TIME);
 * 
 * // Get the default service (Quality Time for MVP)
 * MissionService defaultService = missionServiceFactory.getDefaultService();
 * }</pre>
 * 
 * <p><strong>Extensibility:</strong></p>
 * When new mission types are added, simply create a new {@link MissionService} implementation
 * annotated with {@code @Service}. The factory will automatically discover and register it.
 * 
 * Requirements: 1.1 (Factory pattern for Mission abstraction)
 * 
 * @see MissionService
 * @see MissionType
 */
@Component
public class MissionServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(MissionServiceFactory.class);

    private final Map<MissionType, MissionService> services;

    /**
     * Creates a new MissionServiceFactory with auto-discovered MissionService implementations.
     * 
     * <p>Spring injects all beans implementing {@link MissionService} into the list parameter.
     * Each service is mapped to its supported {@link MissionType} via
     * {@link MissionService#getSupportedType()}.</p>
     * 
     * @param missionServices list of all MissionService implementations discovered by Spring
     */
    public MissionServiceFactory(List<MissionService> missionServices) {
        this.services = missionServices.stream()
                .collect(Collectors.toMap(MissionService::getSupportedType, s -> s));
        
        log.info("MissionServiceFactory initialized with {} mission service(s): {}",
                services.size(),
                services.keySet().stream().map(Enum::name).collect(Collectors.joining(", ")));
        
        // Verify that Quality Time service is registered (required for MVP)
        if (!services.containsKey(MissionType.QUALITY_TIME)) {
            log.warn("No MissionService registered for QUALITY_TIME - this is required for MVP");
        }
    }

    /**
     * Gets the MissionService for the specified mission type.
     * 
     * @param type the mission type to get the service for
     * @return the MissionService handling that type
     * @throws IllegalArgumentException if no service is registered for the type
     */
    public MissionService getService(MissionType type) {
        MissionService service = services.get(type);
        if (service == null) {
            throw new IllegalArgumentException("No MissionService registered for type: " + type);
        }
        return service;
    }

    /**
     * Gets the default MissionService.
     * 
     * <p>For MVP, this always returns the {@link MissionType#QUALITY_TIME} service.
     * This is a convenience method for the workflow engine to use when the mission
     * type doesn't need to be dynamically selected.</p>
     * 
     * @return the default MissionService (Quality Time for MVP)
     * @throws IllegalStateException if no default service is available
     */
    public MissionService getDefaultService() {
        MissionService service = services.get(MissionType.QUALITY_TIME);
        if (service == null) {
            throw new IllegalStateException("No default MissionService available - QUALITY_TIME service not registered");
        }
        return service;
    }

    /**
     * Checks if a MissionService is registered for the specified type.
     * 
     * @param type the mission type to check
     * @return true if a service is registered for this type, false otherwise
     */
    public boolean hasServiceFor(MissionType type) {
        return services.containsKey(type);
    }
}
