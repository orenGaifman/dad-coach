package com.dadcoach.domain.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service for logging conversation messages.
 * Stores both inbound (from father) and outbound (from coach) messages
 * to maintain conversation history for AI context.
 */
@Service
public class MessageLogService {

    private static final Logger log = LoggerFactory.getLogger(MessageLogService.class);
    private static final int MAX_MESSAGES_PER_FATHER = 50;

    private final MessageLogRepository messageLogRepository;

    public MessageLogService(MessageLogRepository messageLogRepository) {
        this.messageLogRepository = messageLogRepository;
    }

    /**
     * Logs an inbound message from a father.
     *
     * @param fatherId the father's ID
     * @param content the message content
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInbound(Long fatherId, String content) {
        if (fatherId == null || content == null || content.isBlank()) {
            log.debug("Skipping empty inbound message log for father {}", fatherId);
            return;
        }

        try {
            MessageLog message = MessageLog.inbound(fatherId, content);
            messageLogRepository.save(message);
            log.debug("Logged inbound message for father {}: {} chars", fatherId, content.length());
        } catch (Exception e) {
            // Non-critical - don't fail the main flow
            log.warn("Failed to log inbound message for father {}: {}", fatherId, e.getMessage());
        }
    }

    /**
     * Logs an outbound message to a father (simple version without AI metadata).
     *
     * @param fatherId the father's ID
     * @param content the message content
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOutbound(Long fatherId, String content) {
        if (fatherId == null || content == null || content.isBlank()) {
            log.debug("Skipping empty outbound message log for father {}", fatherId);
            return;
        }

        try {
            MessageLog message = MessageLog.outbound(fatherId, content);
            messageLogRepository.save(message);
            log.debug("Logged outbound message for father {}: {} chars", fatherId, content.length());
        } catch (Exception e) {
            // Non-critical - don't fail the main flow
            log.warn("Failed to log outbound message for father {}: {}", fatherId, e.getMessage());
        }
    }

    /**
     * Logs an outbound message to a father with full AI decision metadata.
     * Use this overload when logging AI agent responses to capture the decision flow.
     *
     * @param fatherId the father's ID
     * @param content the message content
     * @param toolUsed the AI tool that was used
     * @param toolParameters parameters passed to the tool
     * @param previousState workflow state before processing
     * @param newState workflow state after processing (null if no transition)
     * @param toolSuccess whether the tool execution succeeded
     * @param errorMessage error message if failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOutboundWithAiDecision(
            Long fatherId,
            String content,
            String toolUsed,
            Map<String, Object> toolParameters,
            String previousState,
            String newState,
            boolean toolSuccess,
            String errorMessage) {
        
        if (fatherId == null || content == null || content.isBlank()) {
            log.debug("Skipping empty outbound message log for father {}", fatherId);
            return;
        }

        try {
            MessageLog message = MessageLog.outboundWithAiDecision(
                    fatherId,
                    content,
                    toolUsed,
                    toolParameters,
                    previousState,
                    newState,
                    toolSuccess,
                    errorMessage
            );
            messageLogRepository.save(message);
            log.debug("Logged outbound message with AI decision for father {}: tool={}, stateChange={}->{}",
                    fatherId, toolUsed, previousState, newState);
        } catch (Exception e) {
            // Non-critical - don't fail the main flow
            log.warn("Failed to log outbound message for father {}: {}", fatherId, e.getMessage());
        }
    }

    /**
     * Cleanup old messages for a father, keeping only the most recent ones.
     * Should be called periodically to prevent unbounded table growth.
     *
     * @param fatherId the father's ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupOldMessages(Long fatherId) {
        try {
            messageLogRepository.cleanupOldMessages(fatherId, MAX_MESSAGES_PER_FATHER);
            log.debug("Cleaned up old messages for father {}", fatherId);
        } catch (Exception e) {
            log.warn("Failed to cleanup old messages for father {}: {}", fatherId, e.getMessage());
        }
    }
}
