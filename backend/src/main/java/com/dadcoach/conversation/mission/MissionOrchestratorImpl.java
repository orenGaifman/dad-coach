package com.dadcoach.conversation.mission;

import com.dadcoach.conversation.ConversationService;
import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildService;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;
import com.dadcoach.domain.mission.MissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link MissionOrchestrator} that coordinates mission state transitions
 * triggered by AI follow-up actions within conversations.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Validates preconditions before mission generation (no active mission for child)</li>
 *   <li>Delegates to {@link MissionService} for actual mission operations</li>
 *   <li>Handles mission acceptance, completion, and skipping within conversation flow</li>
 *   <li>Transitions conversations to COMPLETED when AI suggests closing</li>
 * </ul>
 *
 * <p>All operations execute within the caller's existing {@code @Transactional} boundary.
 * No new transactions are created — state changes are persisted as part of the same
 * atomic unit as the conversation pipeline.
 */
@Service
public class MissionOrchestratorImpl implements MissionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MissionOrchestratorImpl.class);

    private static final String REASON_OBJECTIVE_MET = "OBJECTIVE_MET";

    private final ConversationService conversationService;
    private final MissionService missionService;
    private final MissionRepository missionRepository;
    private final ChildService childService;

    public MissionOrchestratorImpl(ConversationService conversationService,
                                   MissionService missionService,
                                   MissionRepository missionRepository,
                                   ChildService childService) {
        this.conversationService = conversationService;
        this.missionService = missionService;
        this.missionRepository = missionRepository;
        this.childService = childService;
    }

    /**
     * Processes a GENERATE_MISSION action from the AI response.
     *
     * <p>Steps:
     * <ol>
     *   <li>Resolve the domain father ID from the conversation-layer UUID</li>
     *   <li>Retrieve the father's active children</li>
     *   <li>For each child, check if an active mission already exists</li>
     *   <li>If a child without an active mission is found, delegate to MissionService</li>
     *   <li>If all children have active missions, skip generation and log</li>
     * </ol>
     *
     * @param fatherId     the father's UUID (conversation-layer identifier)
     * @param conversation the current conversation
     * @return optional mission content string, empty if generation was skipped
     */
    @Override
    public Optional<String> generateMission(UUID fatherId, Conversation conversation) {
        log.info("Processing GENERATE_MISSION action for father={}, conversation={}",
                fatherId, conversation.getId());

        Long domainFatherId = resolveDomainFatherId(fatherId);
        if (domainFatherId == null) {
            log.warn("Unable to resolve domain father ID from UUID={}. Skipping mission generation.", fatherId);
            return Optional.empty();
        }

        // Retrieve all active children for this father
        List<Child> children = getActiveChildren(domainFatherId);
        if (children.isEmpty()) {
            log.info("Father {} has no active children. Skipping mission generation.", fatherId);
            return Optional.empty();
        }

        // Find a child without an active mission
        Child targetChild = findChildWithoutActiveMission(children);
        if (targetChild == null) {
            log.info("All children of father {} already have active missions. Skipping mission generation.",
                    fatherId);
            return Optional.empty();
        }

        // Delegate to MissionService for actual mission creation
        try {
            Mission mission = missionService.createMission(
                    domainFatherId,
                    targetChild.getId(),
                    null, // goalId — AI can determine this in future iterations
                    generateDefaultTitle(targetChild),
                    generateDefaultDescription(targetChild),
                    "GENERAL", // default category
                    2, // default difficulty
                    15 // default estimated minutes
            );

            log.info("Mission {} created for child {} (father={})",
                    mission.getId(), targetChild.getName(), fatherId);

            return Optional.of(String.format(
                    "Misión creada para %s: %s", targetChild.getName(), mission.getTitle()));

        } catch (Exception e) {
            log.error("Failed to create mission for child {} (father={}): {}",
                    targetChild.getId(), fatherId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Processes a CLOSE_CONVERSATION action from the AI response.
     * Transitions the conversation to COMPLETED with reason OBJECTIVE_MET.
     *
     * @param conversation the current conversation to close
     */
    @Override
    public void closeConversation(Conversation conversation) {
        log.info("Processing CLOSE_CONVERSATION action for conversation={}", conversation.getId());

        try {
            conversationService.completeConversation(conversation.getId(), REASON_OBJECTIVE_MET);
            log.info("Conversation {} completed with reason OBJECTIVE_MET", conversation.getId());
        } catch (Exception e) {
            log.error("Failed to close conversation {}: {}", conversation.getId(), e.getMessage(), e);
            // Do not rethrow — the conversation pipeline should not fail due to close action failure.
            // The conversation will be closed by other mechanisms (expiration, max messages) if needed.
        }
    }

    /**
     * Accepts a mission (ASSIGNED → ACCEPTED).
     * Delegates to MissionService which handles the state transition and audit logging.
     *
     * @param missionId the domain mission ID
     */
    @Override
    public void acceptMission(Long missionId) {
        log.info("Accepting mission {}", missionId);
        missionService.acceptMission(missionId);
        log.info("Mission {} accepted successfully", missionId);
    }

    /**
     * Completes a mission within the conversation flow.
     * Starts the mission (ACCEPTED → IN_PROGRESS) then completes it (IN_PROGRESS → COMPLETED)
     * with a default outcome rating. This handles the simplified conversation-driven flow
     * where the father reports both starting and completing in one interaction.
     *
     * @param missionId the domain mission ID
     */
    @Override
    public void completeMission(Long missionId) {
        log.info("Completing mission {} from conversation flow", missionId);

        Mission mission = missionService.getMission(missionId);

        // If still in ACCEPTED state, transition through IN_PROGRESS first
        if (mission.getStatus() == com.dadcoach.mission.MissionStatus.ACCEPTED) {
            missionService.startMission(missionId);
        }

        // Complete with a default positive rating (the father can refine later via reflection)
        missionService.completeMission(missionId, 4, "Completada durante conversación");
        log.info("Mission {} completed successfully from conversation flow", missionId);
    }

    /**
     * Skips a mission (ASSIGNED → SKIPPED).
     * Delegates to MissionService which handles the state transition and audit logging.
     *
     * @param missionId the domain mission ID
     */
    @Override
    public void skipMission(Long missionId) {
        log.info("Skipping mission {}", missionId);
        missionService.skipMission(missionId);
        log.info("Mission {} skipped successfully", missionId);
    }

    // ─── Private Helpers ──────────────────────────────────────────────────

    /**
     * Resolves the UUID-based father ID (conversation layer) to the Long-based domain father ID.
     * Uses the same convention as ContextAssemblerImpl: least-significant bits of the UUID.
     */
    private Long resolveDomainFatherId(UUID fatherId) {
        if (fatherId == null) {
            return null;
        }
        return fatherId.getLeastSignificantBits();
    }

    /**
     * Retrieves all active children for a father, with graceful error handling.
     */
    private List<Child> getActiveChildren(Long domainFatherId) {
        try {
            List<Child> allChildren = childService.getChildrenByFather(domainFatherId);
            return allChildren.stream()
                    .filter(child -> "ACTIVE".equals(child.getStatus()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to retrieve children for domainFatherId={}: {}", domainFatherId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Finds the first child from the list that does not currently have an active mission.
     * Returns null if all children already have active missions (precondition violation).
     */
    private Child findChildWithoutActiveMission(List<Child> children) {
        for (Child child : children) {
            long activeCount = missionRepository.countActiveMissionsByChildId(child.getId());
            if (activeCount == 0) {
                return child;
            }
        }
        return null;
    }

    /**
     * Generates a default mission title for the target child.
     * In production, this would be replaced by AI-generated content via MissionPlanner.
     */
    private String generateDefaultTitle(Child targetChild) {
        return String.format("Misión de conexión con %s", targetChild.getName());
    }

    /**
     * Generates a default mission description for the target child.
     * In production, this would be replaced by AI-generated content via MissionPlanner.
     */
    private String generateDefaultDescription(Child targetChild) {
        return String.format(
                "Dedica 15 minutos de tiempo de calidad con %s. " +
                "Elige una actividad que disfruten juntos y enfócate en estar presente.",
                targetChild.getName());
    }
}
