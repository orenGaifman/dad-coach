package com.dadcoach.conversation.mission;

import com.dadcoach.conversation.entity.Conversation;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles mission state transitions triggered by AI follow-up actions within conversations.
 *
 * <p>Validates preconditions (e.g., no active mission for child) before creating missions,
 * and delegates actual generation to the MissionEngine/MissionService.
 *
 * <p>All operations execute within the caller's existing transaction boundary
 * (no new transactions created). State changes are persisted as part of the same
 * atomic unit as the conversation pipeline.
 */
public interface MissionOrchestrator {

    /**
     * Processes a GENERATE_MISSION action from the AI response.
     * Validates that no active mission exists for the relevant child, then
     * delegates to MissionService for actual mission creation.
     *
     * @param fatherId     the father's UUID (conversation-layer identifier)
     * @param conversation the current conversation
     * @return optional mission content to include in the response, empty if generation was skipped
     */
    Optional<String> generateMission(UUID fatherId, Conversation conversation);

    /**
     * Processes a CLOSE_CONVERSATION action from the AI response.
     * Transitions the conversation to COMPLETED with reason OBJECTIVE_MET.
     *
     * @param conversation the current conversation
     */
    void closeConversation(Conversation conversation);

    /**
     * Accepts a mission (ASSIGNED → ACCEPTED).
     * Called when the father acknowledges a mission within the conversation flow.
     *
     * @param missionId the domain mission ID
     */
    void acceptMission(Long missionId);

    /**
     * Completes a mission (transitions through the appropriate states).
     * Called when the father reports mission completion within the conversation flow.
     *
     * @param missionId the domain mission ID
     */
    void completeMission(Long missionId);

    /**
     * Skips a mission (ASSIGNED → SKIPPED).
     * Called when the father explicitly declines a mission within the conversation flow.
     *
     * @param missionId the domain mission ID
     */
    void skipMission(Long missionId);
}
