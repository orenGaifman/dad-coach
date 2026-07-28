package com.dadcoach.conversation.context;

import com.dadcoach.conversation.dto.InboundMessageDto;
import com.dadcoach.conversation.entity.Conversation;

import java.util.UUID;

/**
 * Assembles the full conversation context by collecting data from all subsystems.
 * Delegates to FatherService, MemoryService, MissionService, etc.
 *
 * <p>The ContextAssembler determines WHICH data to request and in what priority;
 * the Intelligence Layer owns HOW to format and assemble the final prompt.
 */
public interface ContextAssembler {

    /**
     * Collects data from all subsystems and assembles a unified ConversationContext.
     *
     * @param fatherId     the father's UUID
     * @param conversation the active conversation
     * @param message      the inbound message (provides topic context for memory retrieval)
     * @return the assembled context ready for AI orchestration
     */
    ConversationContext assembleContext(UUID fatherId, Conversation conversation, InboundMessageDto message);
}
