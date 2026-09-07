package com.dadcoach.api.webhooks;

import com.dadcoach.common.AppConstants;
import com.dadcoach.common.MaskingUtils;
import com.dadcoach.domain.conversation.MessageLogService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.whatsapp.WhatsAppService;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.logging.WorkflowLoggingContext;
import com.dadcoach.workflow.message.FallbackMessages;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageType;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Webhook endpoint for receiving trigger notifications from ai-workflow-platform.
 *
 * <p>When scheduled triggers fire (reminders, follow-ups), the platform calls this
 * endpoint to notify dad-coach. This controller then:</p>
 * <ul>
 *   <li>Looks up the father by user ID</li>
 *   <li>Composes the appropriate message based on trigger type</li>
 *   <li>Sends the message via WhatsApp</li>
 *   <li>Updates workflow state if needed</li>
 * </ul>
 *
 * <p>Supported trigger types:</p>
 * <ul>
 *   <li>REMINDER_MORNING - Morning reminder on quality time day at 08:00</li>
 *   <li>REMINDER_1_HOUR - Reminder 1 hour before scheduled quality time</li>
 *   <li>FOLLOW_UP - Follow-up 1 hour after quality time ends</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/webhooks")
public class TriggerNotificationController {

    private static final Logger log = LoggerFactory.getLogger(TriggerNotificationController.class);

    private final FatherRepository fatherRepository;
    private final WhatsAppService whatsAppService;
    private final FallbackMessages fallbackMessages;
    private final MessageLogService messageLogService;

    public TriggerNotificationController(
            FatherRepository fatherRepository,
            WhatsAppService whatsAppService,
            FallbackMessages fallbackMessages,
            MessageLogService messageLogService) {
        this.fatherRepository = fatherRepository;
        this.whatsAppService = whatsAppService;
        this.fallbackMessages = fallbackMessages;
        this.messageLogService = messageLogService;
    }

    /**
     * Receives trigger notification from ai-workflow-platform.
     *
     * @param notification the trigger notification payload
     * @return success response with processing details
     */
    @PostMapping("/trigger-notification")
    @Transactional
    public ResponseEntity<TriggerNotificationResponse> handleTriggerNotification(
            @RequestBody TriggerNotificationRequest notification) {

        log.info("Received trigger notification: triggerId={}, type={}, instanceId={}",
                notification.triggerId(), notification.triggerType(), notification.workflowInstanceId());

        try {
            // Parse user ID to extract father ID
            // Expected format: "whatsapp:+972501234567" or similar
            Father father = findFatherByUserId(notification.userId());

            if (father == null) {
                log.warn("Father not found for user ID: userId={}", notification.userId());
                return ResponseEntity.ok(new TriggerNotificationResponse(
                        false, "Father not found for user ID: " + notification.userId(), null));
            }

            // Process the trigger based on type
            try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forFather(father.getId())) {
                String result = processTrigger(notification, father);

                log.info("Trigger processed successfully: triggerId={}, fatherId={}, result={}",
                        notification.triggerId(), father.getId(), result);

                return ResponseEntity.ok(new TriggerNotificationResponse(true, result, null));
            }

        } catch (Exception e) {
            log.error("Error processing trigger notification: triggerId={}, error={}",
                    notification.triggerId(), e.getMessage(), e);
            return ResponseEntity.ok(new TriggerNotificationResponse(
                    false, "Error processing trigger", e.getMessage()));
        }
    }

    /**
     * Processes a trigger notification and sends the appropriate message.
     */
    private String processTrigger(TriggerNotificationRequest notification, Father father) {
        TriggerType triggerType;
        try {
            triggerType = TriggerType.valueOf(notification.triggerType());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown trigger type: {}", notification.triggerType());
            return "Unknown trigger type: " + notification.triggerType();
        }

        // Pass Worker identity for message formatting
        WorkerIdentity workerIdentity = notification.workerIdentity();
        if (workerIdentity != null) {
            log.debug("Processing trigger with Worker identity: workerKey={}", workerIdentity.workerKey());
        }

        return switch (triggerType) {
            case REMINDER_MORNING -> processMorningReminder(notification, father, workerIdentity);
            case REMINDER_1_HOUR -> processOneHourReminder(notification, father, workerIdentity);
            case FOLLOW_UP -> processFollowUp(notification, father, workerIdentity);
        };
    }

    /**
     * Processes morning reminder (08:00 on quality time day).
     */
    private String processMorningReminder(TriggerNotificationRequest notification, Father father,
                                           WorkerIdentity workerIdentity) {
        log.debug("Processing REMINDER_MORNING for father {}", father.getId());

        // Extract context from trigger payload
        Map<String, Object> payload = notification.triggerPayload();
        String childName = payload != null ? (String) payload.getOrDefault("child_name", "your child") : "your child";

        // Build message context
        MessageContext context = MessageContext.builder()
                .messageType(MessageType.WAITING_REMINDER)
                .fatherName(father.getDisplayName())
                .childName(childName)
                .locale(father.getLocale() != null ? father.getLocale() : "en")
                .timezone(father.getTimezone() != null ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE)
                .build();

        // Generate and send message with Worker identity
        String message = fallbackMessages.getProcessed(MessageType.WAITING_REMINDER, context);
        sendWhatsAppMessage(father, message, workerIdentity);

        return "Morning reminder sent";
    }

    /**
     * Processes 1-hour before reminder.
     * Transitions from WAITING to QUALITY_TIME_REMINDER state.
     */
    private String processOneHourReminder(TriggerNotificationRequest notification, Father father,
                                           WorkerIdentity workerIdentity) {
        log.debug("Processing REMINDER_1_HOUR for father {}", father.getId());

        // Extract context from trigger payload
        Map<String, Object> payload = notification.triggerPayload();
        String childName = payload != null ? (String) payload.getOrDefault("child_name", "your child") : "your child";

        // Only transition if in WAITING state
        if (father.getCurrentWorkflowState() == WorkflowState.WAITING) {
            WorkflowState fromState = father.getCurrentWorkflowState();
            father.setPreviousWorkflowState(fromState);
            father.setCurrentWorkflowState(WorkflowState.QUALITY_TIME_REMINDER);
            father.setWorkflowStateEnteredAt(Instant.now());
            fatherRepository.save(father);

            log.info("State transition: {} -> QUALITY_TIME_REMINDER (trigger: REMINDER_1_HOUR)",
                    fromState);
        } else {
            log.debug("Father {} not in WAITING state (currently {}), skipping state transition",
                    father.getId(), father.getCurrentWorkflowState());
        }

        // Build message context
        MessageContext context = MessageContext.builder()
                .messageType(MessageType.QUALITY_TIME_REMINDER)
                .fatherName(father.getDisplayName())
                .childName(childName)
                .locale(father.getLocale() != null ? father.getLocale() : "en")
                .timezone(father.getTimezone() != null ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE)
                .build();

        // Generate and send message with Worker identity
        String message = fallbackMessages.getProcessed(MessageType.QUALITY_TIME_REMINDER, context);
        sendWhatsAppMessage(father, message, workerIdentity);

        return "1-hour reminder sent, state transition to QUALITY_TIME_REMINDER";
    }

    /**
     * Processes follow-up trigger (1 hour after quality time ends).
     * Transitions from WAITING to QUALITY_TIME_FOLLOW_UP state.
     */
    private String processFollowUp(TriggerNotificationRequest notification, Father father,
                                    WorkerIdentity workerIdentity) {
        log.debug("Processing FOLLOW_UP for father {}", father.getId());

        // Extract context from trigger payload
        Map<String, Object> payload = notification.triggerPayload();
        String childName = payload != null ? (String) payload.getOrDefault("child_name", "your child") : "your child";

        // Only transition if in WAITING or QUALITY_TIME_REMINDER state
        WorkflowState currentState = father.getCurrentWorkflowState();
        if (currentState == WorkflowState.WAITING || currentState == WorkflowState.QUALITY_TIME_REMINDER) {
            father.setPreviousWorkflowState(currentState);
            father.setCurrentWorkflowState(WorkflowState.QUALITY_TIME_FOLLOW_UP);
            father.setWorkflowStateEnteredAt(Instant.now());
            fatherRepository.save(father);

            log.info("State transition: {} -> QUALITY_TIME_FOLLOW_UP (trigger: FOLLOW_UP)",
                    currentState);
        } else {
            log.debug("Father {} not in WAITING/QUALITY_TIME_REMINDER state (currently {}), skipping state transition",
                    father.getId(), currentState);
        }

        // Build message context for follow-up question
        MessageContext context = MessageContext.builder()
                .messageType(MessageType.FOLLOW_UP_QUESTION)
                .fatherName(father.getDisplayName())
                .childName(childName)
                .locale(father.getLocale() != null ? father.getLocale() : "en")
                .timezone(father.getTimezone() != null ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE)
                .build();

        // Generate and send follow-up question with Worker identity
        String message = fallbackMessages.getProcessed(MessageType.FOLLOW_UP_QUESTION, context);
        sendWhatsAppMessage(father, message, workerIdentity);

        return "Follow-up sent, state transition to QUALITY_TIME_FOLLOW_UP";
    }

    /**
     * Sends a WhatsApp message to the father and logs it.
     *
     * <p>If Worker identity is provided, the message is prefixed with the Worker's
     * identity (emoji + name) in the format: "{emoji} {name}: {message}".</p>
     *
     * @param father the father to send the message to
     * @param message the message content
     * @param workerIdentity the Worker identity for prefixing (may be null for legacy triggers)
     */
    private void sendWhatsAppMessage(Father father, String message, WorkerIdentity workerIdentity) {
        try {
            // Format message with Worker identity prefix if available
            String formattedMessage = formatWithWorkerIdentity(message, workerIdentity);
            
            whatsAppService.sendText(father.getPhone(), formattedMessage);
            messageLogService.logOutbound(father.getId(), formattedMessage);
            log.debug("WhatsApp message sent to phone {}", MaskingUtils.maskPhone(father.getPhone()));
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message: fatherId={}, error={}",
                    father.getId(), e.getMessage(), e);
            // Don't rethrow - the trigger is still processed even if message fails
        }
    }

    /**
     * Formats a message with Worker identity prefix if available.
     *
     * <p>Format: "{emoji} {name}: {message}" where:</p>
     * <ul>
     *   <li>If both emoji and name are present: "🥗 Nutri: message"</li>
     *   <li>If only emoji is present: "🥗: message"</li>
     *   <li>If only name is present: "Nutri: message"</li>
     *   <li>If neither is present: "message" (unchanged)</li>
     * </ul>
     *
     * @param message the original message
     * @param workerIdentity the Worker identity (may be null)
     * @return the formatted message
     */
    private String formatWithWorkerIdentity(String message, WorkerIdentity workerIdentity) {
        if (workerIdentity == null) {
            return message;
        }

        String emoji = workerIdentity.iconEmoji();
        String name = workerIdentity.workerName();

        boolean hasEmoji = emoji != null && !emoji.isBlank();
        boolean hasName = name != null && !name.isBlank();

        if (!hasEmoji && !hasName) {
            return message;
        }

        StringBuilder prefix = new StringBuilder();
        if (hasEmoji) {
            prefix.append(emoji);
            if (hasName) {
                prefix.append(" ");
            }
        }
        if (hasName) {
            prefix.append(name);
        }
        prefix.append(": ");

        log.debug("Formatted message with Worker identity: workerKey={}", workerIdentity.workerKey());
        return prefix + message;
    }

    /**
     * Sends a WhatsApp message to the father and logs it (legacy method for backward compatibility).
     */
    private void sendWhatsAppMessage(Father father, String message) {
        sendWhatsAppMessage(father, message, null);
    }

    /**
     * Finds a father by the platform user ID.
     *
     * <p>User ID format: "channel:identifier" (e.g., "whatsapp:+972501234567")</p>
     */
    private Father findFatherByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        // Parse user ID format: "channel:identifier"
        String[] parts = userId.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid user ID format: {}", userId);
            return null;
        }

        String channel = parts[0];
        String identifier = parts[1];

        // For WhatsApp, the identifier is the phone number
        if ("whatsapp".equalsIgnoreCase(channel)) {
            return fatherRepository.findByPhone(identifier).orElse(null);
        }

        log.warn("Unsupported channel: {}", channel);
        return null;
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────

    /**
     * Trigger types supported by the webhook.
     */
    enum TriggerType {
        REMINDER_MORNING,
        REMINDER_1_HOUR,
        FOLLOW_UP
    }

    /**
     * Request payload from ai-workflow-platform.
     */
    public record TriggerNotificationRequest(
            @JsonProperty("triggerId") String triggerId,
            @JsonProperty("triggerType") String triggerType,
            @JsonProperty("targetStateKey") String targetStateKey,
            @JsonProperty("scheduledAt") String scheduledAt,
            @JsonProperty("firedAt") String firedAt,
            @JsonProperty("triggerPayload") Map<String, Object> triggerPayload,
            @JsonProperty("workflowInstanceId") String workflowInstanceId,
            @JsonProperty("userId") String userId,
            @JsonProperty("channel") String channel,
            @JsonProperty("workflowContext") Map<String, Object> workflowContext,
            @JsonProperty("workerIdentity") WorkerIdentity workerIdentity
    ) {}

    /**
     * Worker identity information from ai-workflow-platform.
     *
     * <p>When a scheduled trigger is associated with a Worker, this information
     * is included so messages can be formatted with the Worker's identity prefix
     * (e.g., "🥗 Nutri: Here's your meal plan...").</p>
     */
    public record WorkerIdentity(
            @JsonProperty("workerInstanceId") String workerInstanceId,
            @JsonProperty("workerKey") String workerKey,
            @JsonProperty("workerName") String workerName,
            @JsonProperty("iconEmoji") String iconEmoji
    ) {}

    /**
     * Response to ai-workflow-platform.
     */
    public record TriggerNotificationResponse(
            boolean success,
            String message,
            String error
    ) {}
}
