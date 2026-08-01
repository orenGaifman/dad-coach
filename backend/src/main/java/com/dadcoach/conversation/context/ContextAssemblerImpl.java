package com.dadcoach.conversation.context;

import com.dadcoach.conversation.dto.InboundMessageDto;
import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.entity.ConversationMessage;
import com.dadcoach.conversation.repository.ConversationMessageRepository;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.domain.goal.Goal;
import com.dadcoach.domain.goal.GoalService;
import com.dadcoach.domain.memory.Memory;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionService;
import com.dadcoach.memorysystem.MemorySystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ContextAssembler} that coordinates data retrieval
 * from all subsystem services and assembles a unified {@link ConversationContext}.
 *
 * <p>Key design principles:
 * <ul>
 *   <li>Never queries the database directly — always delegates to subsystem services</li>
 *   <li>Handles partial failures gracefully: if one subsystem fails, continues with available data</li>
 *   <li>Scopes memory retrieval by conversation topic (derived from inbound message content)</li>
 *   <li>Includes conversation history (last N messages) from ConversationMessageRepository</li>
 *   <li>Builds temporal context (day of week, time of day, weekend indicator)</li>
 * </ul>
 *
 * <p>Subsystem delegation order (per Requirement 4 priority):
 * <ol>
 *   <li>Father profile (status, coaching_phase, preferences, engagement_score)</li>
 *   <li>Children (profiles, ages, interests)</li>
 *   <li>Active goals</li>
 *   <li>Active missions</li>
 *   <li>Ranked memories (scoped by topic and conversation type)</li>
 *   <li>Conversation history (last N messages)</li>
 * </ol>
 */
@Service
public class ContextAssemblerImpl implements ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssemblerImpl.class);

    private static final int DEFAULT_MAX_HISTORY_MESSAGES = 20;
    private static final int DEFAULT_MAX_MEMORIES = 10;

    private final FatherService fatherService;
    private final ChildService childService;
    private final GoalService goalService;
    private final MissionService missionService;
    private final MemorySystem memorySystem;
    private final ConversationMessageRepository conversationMessageRepository;

    public ContextAssemblerImpl(FatherService fatherService,
                                ChildService childService,
                                GoalService goalService,
                                MissionService missionService,
                                MemorySystem memorySystem,
                                ConversationMessageRepository conversationMessageRepository) {
        this.fatherService = fatherService;
        this.childService = childService;
        this.goalService = goalService;
        this.missionService = missionService;
        this.memorySystem = memorySystem;
        this.conversationMessageRepository = conversationMessageRepository;
    }

    @Override
    public ConversationContext assembleContext(UUID fatherId, Conversation conversation, InboundMessageDto message) {
        log.debug("Assembling context for father={}, conversation={}", fatherId, conversation.getId());

        // Resolve the Long-based father ID from the domain service
        Long domainFatherId = resolveDomainFatherId(fatherId);

        // Assemble each section independently with graceful degradation
        Map<String, Object> fatherProfile = assembleFatherProfile(domainFatherId);
        List<Map<String, Object>> children = assembleChildren(domainFatherId);
        List<Map<String, Object>> activeGoals = assembleActiveGoals(domainFatherId);
        List<Map<String, Object>> activeMissions = assembleActiveMissions(domainFatherId, children);
        List<Map<String, Object>> memories = assembleMemories(domainFatherId, message.content(), conversation.getType());
        List<Map<String, Object>> conversationHistory = assembleConversationHistory(conversation.getId());
        Map<String, Object> temporalContext = buildTemporalContext();

        ConversationContext context = new ConversationContext(
                fatherId,
                conversation.getId(),
                conversation.getType(),
                fatherProfile,
                children,
                activeGoals,
                activeMissions,
                memories,
                conversationHistory,
                temporalContext
        );

        log.debug("Context assembled: profile={}, children={}, goals={}, missions={}, memories={}, history={}",
                !fatherProfile.isEmpty(),
                children.size(),
                activeGoals.size(),
                activeMissions.size(),
                memories.size(),
                conversationHistory.size());

        return context;
    }

    // ─── Father Profile Assembly ──────────────────────────────────────────

    /**
     * Retrieves the father profile from FatherService and maps to a context-friendly format.
     * On failure, returns an empty map (graceful degradation).
     */
    private Map<String, Object> assembleFatherProfile(Long domainFatherId) {
        if (domainFatherId == null) {
            return Map.of();
        }

        try {
            Father father = fatherService.getFather(domainFatherId);
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("status", father.getStatus() != null ? father.getStatus().name() : null);
            profile.put("onboarding_state", father.getOnboardingState() != null ? father.getOnboardingState().name() : "NOT_STARTED");
            profile.put("coaching_phase", father.getCoachingPhase() != null ? father.getCoachingPhase().name() : null);
            profile.put("coaching_style", father.getCoachingStyle() != null ? father.getCoachingStyle().name() : null);
            profile.put("engagement_score", father.getEngagementScore());
            profile.put("coaching_streak", father.getCoachingStreak());
            profile.put("preferred_coaching_time", father.getPreferredCoachingTime() != null
                    ? father.getPreferredCoachingTime().toString() : null);
            profile.put("timezone", father.getTimezone());
            profile.put("locale", father.getLocale());
            profile.put("display_name", father.getDisplayName());
            profile.put("activation_date", father.getActivationDate() != null ? father.getActivationDate().toString() : null);
            profile.put("last_interaction_at", father.getLastInteractionAt() != null ? father.getLastInteractionAt().toString() : null);
            return Collections.unmodifiableMap(profile);
        } catch (Exception e) {
            log.warn("Failed to retrieve father profile for domainFatherId={}: {}", domainFatherId, e.getMessage());
            return Map.of();
        }
    }

    // ─── Children Assembly ────────────────────────────────────────────────

    /**
     * Retrieves all children from ChildService and maps to context-friendly format.
     * On failure, returns an empty list (graceful degradation).
     */
    private List<Map<String, Object>> assembleChildren(Long domainFatherId) {
        if (domainFatherId == null) {
            return List.of();
        }

        try {
            List<Child> childList = childService.getChildrenByFather(domainFatherId);
            return childList.stream()
                    .filter(child -> "ACTIVE".equals(child.getStatus()))
                    .map(this::mapChildToContext)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to retrieve children for domainFatherId={}: {}", domainFatherId, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> mapChildToContext(Child child) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", child.getId());
        map.put("name", child.getName());
        map.put("age", child.getAge());
        map.put("gender", child.getGender());
        map.put("birth_date", child.getBirthDate() != null ? child.getBirthDate().toString() : null);
        map.put("interests", child.getInterests() != null ? child.getInterests() : List.of());
        map.put("challenges", child.getChallenges() != null ? child.getChallenges() : List.of());
        map.put("developmental_bracket", child.getDevelopmentalBracket() != null
                ? child.getDevelopmentalBracket().name() : null);
        map.put("relationship_quality", child.getRelationshipQuality());
        return Collections.unmodifiableMap(map);
    }

    // ─── Active Goals Assembly ────────────────────────────────────────────

    /**
     * Retrieves active goals from GoalService and maps to context-friendly format.
     * On failure, returns an empty list (graceful degradation).
     */
    private List<Map<String, Object>> assembleActiveGoals(Long domainFatherId) {
        if (domainFatherId == null) {
            return List.of();
        }

        try {
            List<Goal> goals = goalService.getActiveGoals(domainFatherId);
            return goals.stream()
                    .map(this::mapGoalToContext)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to retrieve active goals for domainFatherId={}: {}", domainFatherId, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> mapGoalToContext(Goal goal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", goal.getId());
        map.put("title", goal.getTitle());
        map.put("description", goal.getDescription());
        map.put("category", goal.getCategory() != null ? goal.getCategory().name() : null);
        map.put("priority", goal.getPriority());
        map.put("progress_percentage", goal.getProgressPercentage());
        map.put("status", goal.getStatus());
        return Collections.unmodifiableMap(map);
    }

    // ─── Active Missions Assembly ─────────────────────────────────────────

    /**
     * Retrieves active missions for all children via MissionService.
     * On failure, returns an empty list (graceful degradation).
     */
    private List<Map<String, Object>> assembleActiveMissions(Long domainFatherId,
                                                             List<Map<String, Object>> childrenContext) {
        if (domainFatherId == null) {
            return List.of();
        }

        try {
            List<Map<String, Object>> allMissions = new ArrayList<>();
            // Retrieve active missions for each child
            for (Map<String, Object> childCtx : childrenContext) {
                Long childId = (Long) childCtx.get("id");
                if (childId != null) {
                    try {
                        List<Mission> missions = missionService.getActiveMissionsForChild(childId);
                        for (Mission mission : missions) {
                            allMissions.add(mapMissionToContext(mission));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to retrieve missions for childId={}: {}", childId, e.getMessage());
                    }
                }
            }
            return allMissions;
        } catch (Exception e) {
            log.warn("Failed to retrieve active missions for domainFatherId={}: {}", domainFatherId, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> mapMissionToContext(Mission mission) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", mission.getId());
        map.put("title", mission.getTitle());
        map.put("description", mission.getDescription());
        map.put("category", mission.getCategory());
        map.put("difficulty", mission.getDifficulty());
        map.put("estimated_minutes", mission.getEstimatedMinutes());
        map.put("status", mission.getStatus() != null ? mission.getStatus().name() : null);
        map.put("child_id", mission.getChildId());
        map.put("assigned_at", mission.getAssignedAt() != null ? mission.getAssignedAt().toString() : null);
        map.put("expires_at", mission.getExpiresAt() != null ? mission.getExpiresAt().toString() : null);
        return Collections.unmodifiableMap(map);
    }

    // ─── Memory Assembly (Scoped by Topic) ────────────────────────────────

    /**
     * Retrieves ranked memories from MemorySystem, scoped by conversation topic.
     * The topic is derived from the inbound message content.
     * On failure, returns an empty list (graceful degradation).
     */
    private List<Map<String, Object>> assembleMemories(Long domainFatherId, String messageContent,
                                                       String conversationType) {
        if (domainFatherId == null) {
            return List.of();
        }

        try {
            // Derive topic from message content for memory retrieval scoping
            String topic = deriveTopicFromMessage(messageContent, conversationType);

            List<Memory> memories = memorySystem.retrieveTopMemories(domainFatherId, topic, DEFAULT_MAX_MEMORIES);
            return memories.stream()
                    .map(this::mapMemoryToContext)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to retrieve memories for domainFatherId={}: {}", domainFatherId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Derives a topic string from the inbound message content and conversation type.
     * Used to scope memory retrieval for relevance ranking.
     */
    private String deriveTopicFromMessage(String messageContent, String conversationType) {
        if (messageContent == null || messageContent.isBlank()) {
            // Fall back to conversation type as topic hint
            return conversationType != null ? conversationType.toLowerCase().replace("_", " ") : "";
        }
        // Use the raw message content as the topic — the MemorySystem handles relevance scoring
        return messageContent;
    }

    private Map<String, Object> mapMemoryToContext(Memory memory) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", memory.getId());
        map.put("category", memory.getCategory() != null ? memory.getCategory().name() : null);
        map.put("content", memory.getContent());
        map.put("importance_score", memory.getImportanceScore());
        map.put("confidence_score", memory.getConfidenceScore());
        map.put("child_id", memory.getChildId());
        map.put("created_at", memory.getCreatedAt() != null ? memory.getCreatedAt().toString() : null);
        return Collections.unmodifiableMap(map);
    }

    // ─── Conversation History Assembly ────────────────────────────────────

    /**
     * Retrieves the last N messages from the current conversation.
     * On failure, returns an empty list (graceful degradation).
     */
    private List<Map<String, Object>> assembleConversationHistory(UUID conversationId) {
        if (conversationId == null) {
            return List.of();
        }

        try {
            List<ConversationMessage> allMessages =
                    conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId);

            // Take last N messages
            int startIndex = Math.max(0, allMessages.size() - DEFAULT_MAX_HISTORY_MESSAGES);
            List<ConversationMessage> recentMessages = allMessages.subList(startIndex, allMessages.size());

            return recentMessages.stream()
                    .map(this::mapMessageToContext)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to retrieve conversation history for conversationId={}: {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> mapMessageToContext(ConversationMessage msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("direction", msg.getDirection());
        map.put("content", msg.getContent());
        map.put("message_type", msg.getMessageType());
        map.put("sequence_number", msg.getSequenceNumber());
        map.put("created_at", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
        return Collections.unmodifiableMap(map);
    }

    // ─── Temporal Context ─────────────────────────────────────────────────

    /**
     * Builds temporal context containing day of week, time of day, and weekend indicator.
     * Uses UTC as default timezone (the AI layer can adjust based on father's timezone from profile).
     */
    private Map<String, Object> buildTemporalContext() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        Map<String, Object> temporal = new LinkedHashMap<>();
        temporal.put("day_of_week", now.getDayOfWeek().name());
        temporal.put("hour_of_day", now.getHour());
        temporal.put("time_of_day", categorizeTimeOfDay(now.getHour()));
        temporal.put("is_weekend", now.getDayOfWeek() == DayOfWeek.SATURDAY
                || now.getDayOfWeek() == DayOfWeek.SUNDAY);
        temporal.put("timestamp_utc", now.toInstant().toString());
        return Collections.unmodifiableMap(temporal);
    }

    /**
     * Categorizes the hour into a human-readable time-of-day segment.
     */
    private String categorizeTimeOfDay(int hour) {
        if (hour >= 5 && hour < 12) return "MORNING";
        if (hour >= 12 && hour < 17) return "AFTERNOON";
        if (hour >= 17 && hour < 21) return "EVENING";
        return "NIGHT";
    }

    // ─── ID Resolution ────────────────────────────────────────────────────

    /**
     * Resolves the UUID-based father ID (used by the conversation system) to the Long-based
     * domain father ID (used by Father/Child/Goal/Mission/Memory services).
     *
     * <p>In the current architecture, the Conversation entity stores fatherId as UUID,
     * but the domain services use Long IDs. This method provides the mapping.
     * For now, we extract the least-significant bits as a Long — this approach is compatible
     * with the FatherResolver's UUID-based resolution.</p>
     *
     * @param fatherId the UUID-based father ID from the conversation system
     * @return the Long-based domain father ID, or null if unable to resolve
     */
    private Long resolveDomainFatherId(UUID fatherId) {
        if (fatherId == null) {
            return null;
        }
        // The domain Father entity uses Long auto-generated IDs.
        // The conversation system stores father references as UUIDs.
        // We use the least-significant bits to derive the Long ID.
        // This is a convention established by FatherResolver which maps between the two.
        return fatherId.getLeastSignificantBits();
    }
}
