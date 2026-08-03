package com.dadcoach.workflow;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.OutboundMessageDto;

import java.util.Optional;
import java.util.UUID;

/**
 * Central orchestrator for the deterministic workflow state machine.
 * Replaces the AI-driven ConversationOrchestrator.
 * 
 * <p>All business logic decisions are made by this engine. AI is only used for 
 * text generation via MessageGenerator, not for decision-making.</p>
 * 
 * <p>Core Principle - Read Before Write:
 * The system SHALL always synchronize with current system state before any action:
 * <ul>
 *   <li>Read Google Calendar before suggesting times</li>
 *   <li>Read current Quality Time schedule before making proposals</li>
 *   <li>Read conversation state before responding</li>
 *   <li>Read dashboard state before displaying</li>
 *   <li>Read children information before referencing</li>
 * </ul>
 * Never ask for information that already exists. Never suggest something already scheduled or completed.</p>
 * 
 * <p>Implements Requirement 1.1 from the deterministic-workflow-engine spec.</p>
 * 
 * @see WorkflowState
 * @see WorkflowTrigger
 */
public interface WorkflowEngine {

    /**
     * Process an inbound WhatsApp message through the workflow state machine.
     * 
     * <p>Processing Pipeline:
     * <ol>
     *   <li>Load SystemState (Read Before Write)</li>
     *   <li>Determine current workflow state</li>
     *   <li>Match message against expected patterns for current state</li>
     *   <li>Execute business logic for matched pattern</li>
     *   <li>Generate response message (AI or fallback)</li>
     *   <li>Persist state changes</li>
     *   <li>Log state transition</li>
     * </ol>
     * </p>
     * 
     * @param message the normalized inbound message from the Communication Channel
     * @return the outbound message to send via WhatsApp
     */
    OutboundMessageDto processMessage(InboundMessageDto message);

    /**
     * Trigger a state transition by an external event (e.g., scheduler).
     * 
     * <p>Used by scheduler jobs to trigger time-based transitions such as:
     * <ul>
     *   <li>QUALITY_TIME_ENDED - when scheduled Quality Time end time has passed</li>
     *   <li>FOLLOW_UP_TIMEOUT - when father hasn't responded to follow-up within 24 hours</li>
     *   <li>SCHEDULER_REMINDER - for morning reminders on Quality Time day</li>
     * </ul>
     * </p>
     * 
     * @param fatherId the father to transition
     * @param trigger the trigger reason for the transition
     * @return the outbound message to send, or empty if no message needed
     */
    Optional<OutboundMessageDto> triggerTransition(UUID fatherId, WorkflowTrigger trigger);
}
