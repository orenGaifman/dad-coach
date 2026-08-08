package com.dadcoach.workflow;

import com.dadcoach.ai.agent.AgentContext;
import com.dadcoach.ai.agent.CoachingAgent;
import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.whatsapp.WhatsAppService;
import com.dadcoach.workflow.config.FeatureFlagsConfig;
import com.dadcoach.workflow.idempotency.WorkflowIdempotencyService;
import com.dadcoach.workflow.logging.WorkflowLoggingContext;
import com.dadcoach.workflow.message.FallbackMessages;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import com.dadcoach.workflow.pattern.PatternMatcher;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.state.StateAction;
import com.dadcoach.workflow.state.StateHandler;
import com.dadcoach.workflow.state.WorkflowContext;
import com.dadcoach.workspace.magiclink.DashboardLinkAppender;
import com.dadcoach.workspace.magiclink.DashboardLinkAppender.DashboardLinkContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the deterministic WorkflowEngine.
 * 
 * <p>This is the central orchestrator for the Dad Coach workflow state machine.
 * It processes inbound WhatsApp messages through a 9-step pipeline:</p>
 * <ol>
 *   <li>Parse and validate message</li>
 *   <li>Identify father from phone number</li>
 *   <li>Load SystemState (Read Before Write)</li>
 *   <li>Determine current workflow state</li>
 *   <li>Get appropriate StateHandler and match patterns</li>
 *   <li>Execute business logic for matched pattern</li>
 *   <li>Generate response message (AI or fallback)</li>
 *   <li>Persist state changes</li>
 *   <li>Log state transition</li>
 * </ol>
 * 
 * <p>Implements Requirement 11.1 from the deterministic-workflow-engine spec.</p>
 * 
 * @see WorkflowEngine
 * @see WorkflowState
 * @see StateHandler
 */
@Service
public class WorkflowEngineImpl implements WorkflowEngine {
    
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineImpl.class);
    
    /**
     * Timeout in milliseconds before sending a "processing" message.
     * Implements Requirement 11.2: Response within 30 seconds, otherwise send processing message.
     */
    private static final long PROCESSING_TIMEOUT_MS = 30_000;
    
    private final SystemStateLoader systemStateLoader;
    private final Map<WorkflowState, StateHandler> stateHandlers;
    private final PatternMatcher patternMatcher;
    private final MessageGenerator messageGenerator;
    private final FatherRepository fatherRepository;
    private final WorkflowTransitionLogRepository transitionLogRepository;
    private final WhatsAppService whatsAppService;
    private final FallbackMessages fallbackMessages;
    private final WorkflowMetrics workflowMetrics;
    private final WorkflowIdempotencyService idempotencyService;
    private final DashboardLinkAppender dashboardLinkAppender;
    private final ScheduledExecutorService timeoutScheduler;
    
    // Optional: AI Agent for natural language understanding (injected via setter)
    private CoachingAgent coachingAgent;
    private FeatureFlagsConfig featureFlagsConfig;
    
    /**
     * Creates a new WorkflowEngineImpl with all required dependencies.
     * 
     * @param systemStateLoader the loader for system state (Read Before Write)
     * @param stateHandlers the list of state handlers (auto-wired by Spring)
     * @param patternMatcher the pattern matcher for message analysis
     * @param messageGenerator the message generator for response creation
     * @param fatherRepository the repository for father entity operations
     * @param transitionLogRepository the repository for logging state transitions
     * @param whatsAppService the WhatsApp service for sending messages (used for processing timeout)
     * @param fallbackMessages the fallback messages provider
     * @param workflowMetrics the metrics collector for workflow monitoring (Requirement 16.2)
     * @param idempotencyService the idempotency service for duplicate message detection
     * @param dashboardLinkAppender the dashboard link appender for adding magic links to messages
     */
    public WorkflowEngineImpl(
            SystemStateLoader systemStateLoader,
            List<StateHandler> stateHandlers,
            PatternMatcher patternMatcher,
            MessageGenerator messageGenerator,
            FatherRepository fatherRepository,
            WorkflowTransitionLogRepository transitionLogRepository,
            WhatsAppService whatsAppService,
            FallbackMessages fallbackMessages,
            WorkflowMetrics workflowMetrics,
            WorkflowIdempotencyService idempotencyService,
            DashboardLinkAppender dashboardLinkAppender) {
        
        this.systemStateLoader = Objects.requireNonNull(systemStateLoader, "systemStateLoader must not be null");
        this.patternMatcher = Objects.requireNonNull(patternMatcher, "patternMatcher must not be null");
        this.messageGenerator = Objects.requireNonNull(messageGenerator, "messageGenerator must not be null");
        this.fatherRepository = Objects.requireNonNull(fatherRepository, "fatherRepository must not be null");
        this.transitionLogRepository = Objects.requireNonNull(transitionLogRepository, "transitionLogRepository must not be null");
        this.whatsAppService = Objects.requireNonNull(whatsAppService, "whatsAppService must not be null");
        this.fallbackMessages = Objects.requireNonNull(fallbackMessages, "fallbackMessages must not be null");
        this.workflowMetrics = Objects.requireNonNull(workflowMetrics, "workflowMetrics must not be null");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService must not be null");
        this.dashboardLinkAppender = Objects.requireNonNull(dashboardLinkAppender, "dashboardLinkAppender must not be null");
        
        // Executor for scheduling timeout tasks
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "workflow-timeout-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // Build state handler map from the injected list
        this.stateHandlers = new EnumMap<>(WorkflowState.class);
        for (StateHandler handler : stateHandlers) {
            this.stateHandlers.put(handler.getState(), handler);
            log.debug("Registered state handler for state {}: {}", 
                    handler.getState(), handler.getClass().getSimpleName());
        }
        
        log.info("WorkflowEngineImpl initialized with {} state handlers", this.stateHandlers.size());
    }
    
    /**
     * Sets the CoachingAgent for AI-powered message processing.
     * Injected via setter to avoid circular dependency and keep constructor clean.
     * 
     * @param coachingAgent the AI coaching agent (optional)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCoachingAgent(CoachingAgent coachingAgent) {
        this.coachingAgent = coachingAgent;
        if (coachingAgent != null) {
            log.info("CoachingAgent injected into WorkflowEngine");
        }
    }
    
    /**
     * Sets the feature flags configuration.
     * 
     * @param featureFlagsConfig the feature flags config
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFeatureFlagsConfig(FeatureFlagsConfig featureFlagsConfig) {
        this.featureFlagsConfig = featureFlagsConfig;
        if (featureFlagsConfig != null) {
            log.info("FeatureFlagsConfig injected: aiAgentEnabled={}", featureFlagsConfig.isAiAgentEnabled());
        }
    }
    
    /**
     * Check if AI Agent mode is enabled.
     */
    private boolean isAiAgentEnabled() {
        return featureFlagsConfig != null && 
               featureFlagsConfig.isAiAgentEnabled() && 
               coachingAgent != null && 
               coachingAgent.isEnabled();
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Processes an inbound WhatsApp message through the 9-step pipeline.</p>
     * 
     * <p>Implements Requirement 11.2: If processing takes longer than 30 seconds,
     * a "processing" message is sent immediately, and the actual response follows
     * when processing completes.</p>
     */
    @Override
    @Transactional
    public OutboundMessageDto processMessage(InboundMessageDto message) {
        Objects.requireNonNull(message, "message must not be null");
        
        log.info("Processing inbound message: id={}, from={}, type={}", 
                message.messageId(), message.fatherChannelIdentity(), message.messageType());
        
        // Track processing start time for timeout handling
        final long startTime = System.currentTimeMillis();
        final String phoneNumber = message.fatherChannelIdentity();
        
        // AtomicBoolean to track if processing message was sent
        final AtomicBoolean processingMessageSent = new AtomicBoolean(false);
        
        // Pre-fetch father info for timeout message (before the main processing)
        String fatherLocale = "en";
        String fatherName = "";
        try {
            Optional<Father> fatherOpt = fatherRepository.findByPhone(phoneNumber);
            if (fatherOpt.isPresent()) {
                fatherLocale = fatherOpt.get().getLocale();
                fatherName = fatherOpt.get().getDisplayName();
            }
        } catch (Exception e) {
            log.debug("Could not pre-fetch father info for timeout handling: {}", e.getMessage());
        }
        
        // Schedule a timeout task that sends "processing" message after 30 seconds
        final String finalLocale = fatherLocale;
        final String finalName = fatherName;
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            if (!processingMessageSent.getAndSet(true)) {
                sendProcessingMessage(phoneNumber, finalName, finalLocale);
            }
        }, PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        try {
            // Main processing logic
            OutboundMessageDto response = doProcessMessage(message);
            
            // Cancel the timeout task if processing completed in time
            timeoutTask.cancel(false);
            
            // Log elapsed time
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.debug("Message processing completed in {}ms (timeout: {}ms)", elapsedMs, PROCESSING_TIMEOUT_MS);
            
            // If processing message was already sent, this response will be sent as follow-up
            if (processingMessageSent.get()) {
                log.info("Processing message was sent, actual response follows as follow-up for father {}", 
                        phoneNumber);
            }
            
            return response;
            
        } catch (Exception e) {
            // Cancel timeout task on error
            timeoutTask.cancel(false);
            throw e;
        }
    }
    
    /**
     * Sends a "processing" message to the father when processing takes longer than 30 seconds.
     * 
     * @param phoneNumber the father's phone number
     * @param fatherName the father's display name
     * @param locale the father's preferred locale
     */
    private void sendProcessingMessage(String phoneNumber, String fatherName, String locale) {
        try {
            log.info("Processing timeout reached, sending processing message to {}", phoneNumber);
            
            // Build message context
            MessageContext msgContext = MessageContext.builder()
                    .messageType(com.dadcoach.workflow.message.MessageType.PROCESSING)
                    .fatherName(fatherName != null ? fatherName : "")
                    .locale(locale != null ? locale : "en")
                    .build();
            
            // Get the processing message (uses fallback since this should be fast)
            String processingMessage = fallbackMessages.getProcessed(
                    com.dadcoach.workflow.message.MessageType.PROCESSING, 
                    msgContext
            );
            
            // Send via WhatsApp
            whatsAppService.sendText(phoneNumber, processingMessage);
            
            log.info("Processing message sent successfully to {}", phoneNumber);
        } catch (Exception e) {
            log.error("Failed to send processing message to {}: {}", phoneNumber, e.getMessage(), e);
        }
    }
    
    /**
     * The actual message processing logic, extracted from processMessage for timeout handling.
     */
    private OutboundMessageDto doProcessMessage(InboundMessageDto message) {
        
        try {
            // Step 0: Check idempotency — if duplicate, return cached response immediately
            String idempotencyKey = message.idempotencyKey();
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<OutboundMessageDto> cached = idempotencyService.checkDuplicate(idempotencyKey);
                if (cached.isPresent()) {
                    log.info("Duplicate message detected for idempotency key '{}'. Returning cached response.",
                            idempotencyKey);
                    return cached.get();
                }
            }
            
            // Step 1: Parse and validate message (already done by channel layer)
            String messageText = message.textContent();
            if (messageText == null) {
                messageText = "";
            }
            messageText = messageText.trim();
            
            // Step 2: Identify father from phone number
            String phoneNumber = message.fatherChannelIdentity();
            Father father = fatherRepository.findByPhone(phoneNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Father", phoneNumber));
            
            UUID fatherUuid = deriveUuid(father.getId());
            
            // Set up structured logging context with father_id
            // Implements Requirement 16.6: ALL logs SHALL include father_id
            try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forFather(fatherUuid)) {
                ctx.setMessageId(message.messageId());
                
                log.debug("Identified father: domainId={}, currentState={}", 
                        father.getId(), father.getCurrentWorkflowState());
                
                // Step 3: Load SystemState (Read Before Write)
                SystemState systemState = systemStateLoader.loadState(fatherUuid);
                
                // Step 4: Determine current workflow state
                WorkflowState currentState = father.getCurrentWorkflowState();
                if (currentState == null) {
                    currentState = WorkflowState.WELCOME;
                    log.warn("Father has null workflow state, defaulting to WELCOME");
                }
                
                ctx.setState(currentState);
                
                // ─── AI Agent Mode ───────────────────────────────────────────────────
                // If AI Agent is enabled, use CoachingAgent for natural language processing
                // instead of the pattern-matching approach below.
                if (isAiAgentEnabled()) {
                    log.info("Processing message with AI Agent for father: {}", fatherUuid);
                    return processMessageWithAiAgent(message, father, fatherUuid, currentState, systemState, ctx);
                }
                // ─── End AI Agent Mode ────────────────────────────────────────────────
                
                // Step 5: Get appropriate StateHandler and match patterns
                StateHandler handler = stateHandlers.get(currentState);
                if (handler == null) {
                    log.error("No state handler found for state {}", currentState);
                    return createErrorResponse(fatherUuid, father.getLocale(), 
                            "Internal error - no handler for state");
                }
                
                // Build workflow context
                WorkflowContext context = WorkflowContext.builder()
                        .systemState(systemState)
                        .fatherId(fatherUuid)
                        .currentState(currentState)
                        .inboundMessage(messageText)
                        .build();
                
                // Match message against state patterns
                Optional<PatternResult> matchResult = patternMatcher.match(
                        messageText, 
                        handler.getExpectedPatterns()
                );
                
                // Step 6: Execute business logic for matched pattern
                StateAction action;
                if (matchResult.isPresent() && matchResult.get().isMatched()) {
                    PatternResult match = matchResult.get();
                    log.debug("Pattern matched: {} -> {}", 
                            match.patternName(), match.matchedAction());
                    action = handler.handle(context, match);
                } else {
                    log.debug("No pattern matched for message '{}' in state {}", 
                            truncateForLog(messageText), currentState);
                    action = handler.handleUnmatched(context);
                }
                
                // Step 7: Generate response message (AI or fallback) - handled by StateHandler
                String responseMessage = action.getResponseMessage().orElse("");
                
                // Step 8: Persist state changes
                if (action.isTransition()) {
                    WorkflowState newState = action.getNextState()
                            .orElseThrow(() -> new IllegalStateException("Transition action without next state"));
                    
                    // Update father's workflow state
                    father.setPreviousWorkflowState(currentState);
                    father.setCurrentWorkflowState(newState);
                    father.setWorkflowStateEnteredAt(Instant.now());
                    father.setLastInteractionAt(Instant.now());
                    fatherRepository.save(father);
                    
                    // Log state transition with structured context
                    // Implements Requirement 16.1: Log transitions with from_state, to_state, trigger_reason
                    ctx.setTransition(currentState, newState, WorkflowTrigger.USER_MESSAGE.name());
                    log.info("State transition: {} -> {} (trigger: {})", 
                            currentState, newState, WorkflowTrigger.USER_MESSAGE.name());
                    ctx.clearTransition();
                    
                    // Record state transition metric (Requirement 16.2)
                    workflowMetrics.recordStateTransition(currentState, newState);
                    
                    // Step 9: Log state transition to audit table
                    logTransition(fatherUuid, currentState, newState, 
                            WorkflowTrigger.USER_MESSAGE.name(), message.messageId());
                } else {
                    // Update last interaction even without state change
                    father.setLastInteractionAt(Instant.now());
                    fatherRepository.save(father);
                }
                
                // Build the response
                // Append dashboard link for:
                // 1. State transitions (especially from WELCOME to SCHEDULE_QUALITY_TIME)
                // 2. First message in WELCOME state (so father can see their dashboard)
                String finalResponseMessage = responseMessage;
                boolean shouldAppendDashboardLink = action.isTransition() || currentState == WorkflowState.WELCOME;
                
                if (shouldAppendDashboardLink) {
                    try {
                        // Determine context based on current state
                        DashboardLinkContext linkContext = currentState == WorkflowState.WELCOME 
                                ? DashboardLinkContext.WELCOME 
                                : DashboardLinkContext.WEEKLY_CHECKIN;
                        
                        // Add dashboard link with locale support
                        String locale = father.getLocale() != null ? father.getLocale() : "en";
                        String dashboardLink = dashboardLinkAppender.generateLinkMessage(
                                father.getId(), 
                                linkContext,
                                locale
                        );
                        finalResponseMessage = responseMessage + "\n\n" + dashboardLink;
                        log.debug("Appended dashboard link ({}) to response for father {}", 
                                linkContext.name(), fatherUuid);
                    } catch (Exception e) {
                        // Non-critical: log and continue without dashboard link
                        log.warn("Failed to append dashboard link for father {}: {}", fatherUuid, e.getMessage());
                    }
                }
                OutboundMessageDto response = buildResponse(fatherUuid, finalResponseMessage);
                
                // Step 10: Record idempotency key to prevent duplicate processing
                // (idempotencyKey was already extracted at the start of this method)
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    idempotencyService.recordProcessed(idempotencyKey, response);
                }
                
                return response;
            }
            
        } catch (ResourceNotFoundException e) {
            log.warn("Father not found for phone {}: {}", 
                    message.fatherChannelIdentity(), e.getMessage());
            // For unknown fathers, return a generic message
            return createErrorResponse(
                    UUID.randomUUID(), // No father UUID available
                    "en",
                    "I don't recognize this number. Please complete onboarding first."
            );
        } catch (Exception e) {
            log.error("Error processing message {} for father {}: {}", 
                    message.messageId(), message.fatherChannelIdentity(), e.getMessage(), e);
            return createErrorResponse(
                    UUID.randomUUID(),
                    "en",
                    "Something went wrong. Please try again."
            );
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Triggers a state transition by an external event (e.g., scheduler).</p>
     */
    @Override
    @Transactional
    public Optional<OutboundMessageDto> triggerTransition(UUID fatherId, WorkflowTrigger trigger) {
        Objects.requireNonNull(fatherId, "fatherId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        
        // Set up structured logging context with father_id
        // Implements Requirement 16.6: ALL logs SHALL include father_id
        try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forFather(fatherId)) {
            ctx.setTriggerType(trigger.name());
            
            log.info("Triggering transition with trigger {}", trigger);
            
            try {
                // Load father by UUID (convert to Long ID)
                Long domainId = fatherId.getLeastSignificantBits();
                Father father = fatherRepository.findById(domainId)
                        .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId.toString()));
                
                WorkflowState currentState = father.getCurrentWorkflowState();
                if (currentState == null) {
                    currentState = WorkflowState.WELCOME;
                }
                
                ctx.setState(currentState);
                
                // Load system state
                SystemState systemState = systemStateLoader.loadState(fatherId);
                
                // Determine target state and response based on trigger type
                return switch (trigger) {
                    case QUALITY_TIME_ENDED -> handleQualityTimeEnded(father, fatherId, currentState, systemState, ctx);
                    case FOLLOW_UP_TIMEOUT -> handleFollowUpTimeout(father, fatherId, currentState, systemState, ctx);
                    case SCHEDULER_REMINDER -> handleSchedulerReminder(father, fatherId, currentState, systemState, ctx);
                    case USER_MESSAGE -> {
                        log.warn("USER_MESSAGE trigger should not be used with triggerTransition(), use processMessage() instead");
                        yield Optional.empty();
                    }
                };
                
            } catch (ResourceNotFoundException e) {
                log.warn("Father not found for trigger");
                return Optional.empty();
            } catch (Exception e) {
                log.error("Error processing trigger {}: {}", trigger, e.getMessage(), e);
                return Optional.empty();
            }
        }
    }
    
    // ─── Private Helper Methods ─────────────────────────────────────────────────
    
    /**
     * Handles the QUALITY_TIME_ENDED trigger.
     * Transitions from WAITING to QUALITY_TIME_FOLLOW_UP and sends follow-up question.
     */
    private Optional<OutboundMessageDto> handleQualityTimeEnded(
            Father father, UUID fatherId, WorkflowState currentState, SystemState systemState,
            WorkflowLoggingContext ctx) {
        
        if (currentState != WorkflowState.WAITING) {
            log.debug("Not in WAITING state (current: {}), skipping QUALITY_TIME_ENDED trigger", currentState);
            return Optional.empty();
        }
        
        WorkflowState newState = WorkflowState.QUALITY_TIME_FOLLOW_UP;
        
        // Update father's workflow state
        father.setPreviousWorkflowState(currentState);
        father.setCurrentWorkflowState(newState);
        father.setWorkflowStateEnteredAt(Instant.now());
        fatherRepository.save(father);
        
        // Log state transition with structured context
        // Implements Requirement 16.1: Log transitions with from_state, to_state, trigger_reason
        ctx.setTransition(currentState, newState, WorkflowTrigger.QUALITY_TIME_ENDED.name());
        log.info("State transition: {} -> {} (trigger: {})", 
                currentState, newState, WorkflowTrigger.QUALITY_TIME_ENDED.name());
        ctx.clearTransition();
        
        // Log transition to audit table
        logTransition(fatherId, currentState, newState, 
                WorkflowTrigger.QUALITY_TIME_ENDED.name(), null);
        
        // Record state transition metric (Requirement 16.2)
        workflowMetrics.recordStateTransition(currentState, newState);
        
        // Generate follow-up message
        String childName = systemState.getDefaultChild() != null 
                ? systemState.getDefaultChild().name() 
                : "";
        
        MessageContext msgContext = MessageContext.builder()
                .messageType(com.dadcoach.workflow.message.MessageType.FOLLOW_UP_QUESTION)
                .fatherName(father.getDisplayName())
                .childName(childName)
                .locale(father.getLocale())
                .timezone(father.getTimezone())
                .build();
        
        String responseMessage = messageGenerator.generateWithFallback(
                com.dadcoach.workflow.message.MessageType.FOLLOW_UP_QUESTION,
                msgContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );
        
        return Optional.of(buildResponse(fatherId, responseMessage));
    }
    
    /**
     * Handles the FOLLOW_UP_TIMEOUT trigger.
     * Transitions from QUALITY_TIME_FOLLOW_UP to SCHEDULE_QUALITY_TIME,
     * marks Quality Time as MISSED.
     */
    private Optional<OutboundMessageDto> handleFollowUpTimeout(
            Father father, UUID fatherId, WorkflowState currentState, SystemState systemState,
            WorkflowLoggingContext ctx) {
        
        if (currentState != WorkflowState.QUALITY_TIME_FOLLOW_UP) {
            log.debug("Not in QUALITY_TIME_FOLLOW_UP state (current: {}), skipping FOLLOW_UP_TIMEOUT", currentState);
            return Optional.empty();
        }
        
        WorkflowState newState = WorkflowState.SCHEDULE_QUALITY_TIME;
        
        // Update father's workflow state
        father.setPreviousWorkflowState(currentState);
        father.setCurrentWorkflowState(newState);
        father.setWorkflowStateEnteredAt(Instant.now());
        fatherRepository.save(father);
        
        // Log state transition with structured context
        // Implements Requirement 16.1: Log transitions with from_state, to_state, trigger_reason
        ctx.setTransition(currentState, newState, WorkflowTrigger.FOLLOW_UP_TIMEOUT.name());
        log.info("State transition: {} -> {} (trigger: {})", 
                currentState, newState, WorkflowTrigger.FOLLOW_UP_TIMEOUT.name());
        ctx.clearTransition();
        
        // Log transition to audit table
        logTransition(fatherId, currentState, newState, 
                WorkflowTrigger.FOLLOW_UP_TIMEOUT.name(), null);
        
        // Record state transition metric (Requirement 16.2)
        workflowMetrics.recordStateTransition(currentState, newState);
        
        // Generate re-engagement message
        MessageContext msgContext = MessageContext.builder()
                .messageType(com.dadcoach.workflow.message.MessageType.SCHEDULE_SLOTS)
                .fatherName(father.getDisplayName())
                .locale(father.getLocale())
                .timezone(father.getTimezone())
                .build();
        
        String responseMessage = messageGenerator.generateWithFallback(
                com.dadcoach.workflow.message.MessageType.SCHEDULE_SLOTS,
                msgContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );
        
        return Optional.of(buildResponse(fatherId, responseMessage));
    }
    
    /**
     * Handles the SCHEDULER_REMINDER trigger.
     * Sends a morning reminder without changing state.
     */
    private Optional<OutboundMessageDto> handleSchedulerReminder(
            Father father, UUID fatherId, WorkflowState currentState, SystemState systemState,
            WorkflowLoggingContext ctx) {
        
        if (currentState != WorkflowState.WAITING) {
            log.debug("Not in WAITING state (current: {}), skipping SCHEDULER_REMINDER", currentState);
            return Optional.empty();
        }
        
        // Get next scheduled Quality Time
        SystemState.QualityTimeEvent nextQT = systemState.getNextScheduledQualityTime();
        if (nextQT == null) {
            log.debug("No scheduled Quality Time found, skipping reminder");
            return Optional.empty();
        }
        
        // Generate reminder message
        MessageContext msgContext = MessageContext.builder()
                .messageType(com.dadcoach.workflow.message.MessageType.WAITING_REMINDER)
                .fatherName(father.getDisplayName())
                .childName(nextQT.childName())
                .scheduledStart(nextQT.scheduledStart())
                .locale(father.getLocale())
                .timezone(father.getTimezone())
                .build();
        
        String responseMessage = messageGenerator.generateWithFallback(
                com.dadcoach.workflow.message.MessageType.WAITING_REMINDER,
                msgContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );
        
        log.info("Sent morning reminder for Quality Time with {}", nextQT.childName());
        
        return Optional.of(buildResponse(fatherId, responseMessage));
    }
    
    /**
     * Logs a workflow state transition to the audit log.
     */
    private void logTransition(UUID fatherId, WorkflowState fromState, WorkflowState toState, 
                               String triggerReason, UUID triggerMessageId) {
        // Convert UUID to Long: father UUID uses least significant bits for the Long ID
        Long fatherDomainId = fatherId.getLeastSignificantBits();
        
        WorkflowTransition transition = WorkflowTransition.builder()
                .fatherId(fatherDomainId)
                .fromState(fromState)
                .toState(toState)
                .triggerReason(triggerReason)
                .triggerMessageId(triggerMessageId)
                .createdAt(Instant.now())
                .build();
        
        transitionLogRepository.save(transition);
        
        log.debug("Logged transition: {} -> {} for father {} (trigger: {})", 
                fromState, toState, fatherId, triggerReason);
    }
    
    /**
     * Builds an outbound message DTO.
     */
    private OutboundMessageDto buildResponse(UUID fatherId, String textContent) {
        return new OutboundMessageDto(
                UUID.randomUUID(),
                fatherId,
                "WHATSAPP",
                MessageType.TEXT,
                textContent,
                null,
                false,
                null,
                null,
                MessagePriority.IMMEDIATE,
                Instant.now()
        );
    }
    
    /**
     * Creates an error response message.
     */
    private OutboundMessageDto createErrorResponse(UUID fatherId, String locale, String message) {
        String errorMessage;
        if ("he".equals(locale)) {
            errorMessage = "משהו השתבש. אנא נסה שוב.";
        } else {
            errorMessage = message != null ? message : "Something went wrong. Please try again.";
        }
        return buildResponse(fatherId, errorMessage);
    }
    
    /**
     * Derives a stable UUID from the domain Long ID.
     * Uses a deterministic mapping: MSB=0, LSB=domainId.
     */
    private UUID deriveUuid(Long domainId) {
        if (domainId == null) {
            return UUID.randomUUID();
        }
        return new UUID(0L, domainId);
    }
    
    // ─── AI Agent Processing ────────────────────────────────────────────────────
    
    /**
     * Process a message using the AI CoachingAgent instead of pattern matching.
     * 
     * <p>This method is called when the AI Agent feature flag is enabled.
     * It uses Claude to understand user intent and select appropriate tools.</p>
     * 
     * @param message the inbound message
     * @param father the father entity
     * @param fatherUuid the father's UUID
     * @param currentState the current workflow state
     * @param systemState the loaded system state
     * @param ctx the logging context
     * @return the outbound message response
     */
    private OutboundMessageDto processMessageWithAiAgent(
            InboundMessageDto message,
            Father father,
            UUID fatherUuid,
            WorkflowState currentState,
            SystemState systemState,
            WorkflowLoggingContext ctx) {
        
        String messageText = message.textContent();
        if (messageText == null) {
            messageText = "";
        }
        messageText = messageText.trim();
        
        try {
            // Call the CoachingAgent
            CoachingAgent.AgentResponse agentResponse = coachingAgent.processMessage(
                fatherUuid,
                messageText,
                currentState,
                List.of() // TODO: Load conversation history from recent messages
            );
            
            log.info("AI Agent response: tool={}, success={}, hasTransition={}",
                    agentResponse.toolUsed(), agentResponse.success(), agentResponse.hasStateTransition());
            
            // Handle state transition if needed
            if (agentResponse.hasStateTransition()) {
                WorkflowState newState = agentResponse.newState();
                
                // Update father's workflow state
                father.setPreviousWorkflowState(currentState);
                father.setCurrentWorkflowState(newState);
                father.setWorkflowStateEnteredAt(Instant.now());
                father.setLastInteractionAt(Instant.now());
                fatherRepository.save(father);
                
                // Log transition
                ctx.setTransition(currentState, newState, "AI_AGENT_" + agentResponse.toolUsed());
                log.info("AI Agent state transition: {} -> {} (tool: {})",
                        currentState, newState, agentResponse.toolUsed());
                
                // Log transition to database
                Long fatherDomainId = fatherUuid.getLeastSignificantBits();
                WorkflowTransition transition = WorkflowTransition.builder()
                        .fatherId(fatherDomainId)
                        .fromState(currentState)
                        .toState(newState)
                        .triggerReason("AI_AGENT_" + agentResponse.toolUsed())
                        .triggerMessageId(message.messageId())
                        .createdAt(Instant.now())
                        .build();
                transitionLogRepository.save(transition);
            } else {
                // Just update last interaction time
                father.setLastInteractionAt(Instant.now());
                fatherRepository.save(father);
            }
            
            // Create response DTO
            String responseMessage = agentResponse.message();
            if (responseMessage == null || responseMessage.isEmpty()) {
                responseMessage = fallbackMessages.getProcessed(
                    com.dadcoach.workflow.message.MessageType.ERROR_GENERIC,
                    MessageContext.builder()
                        .fatherName(father.getDisplayName())
                        .locale(father.getLocale())
                        .build()
                );
            }
            
            // Add dashboard link if appropriate (when showing progress)
            if (responseMessage.contains("התקדמות") || responseMessage.contains("📊")) {
                // Generate a dashboard link for the father
                String linkMessage = dashboardLinkAppender.generateLinkMessage(
                    father.getId(),
                    com.dadcoach.workspace.magiclink.DashboardLinkAppender.DashboardLinkContext.QUALITY_TIME_LOGGED,
                    father.getLocale()
                );
                responseMessage = responseMessage + "\n\n" + linkMessage;
            }
            
            return new OutboundMessageDto(
                UUID.randomUUID(),  // Generate new message ID
                fatherUuid,
                "WHATSAPP",
                MessageType.TEXT,
                responseMessage,
                null,
                false,
                null,
                null,
                MessagePriority.IMMEDIATE,
                Instant.now()
            );
            
        } catch (Exception e) {
            log.error("AI Agent processing failed for father {}: {}", fatherUuid, e.getMessage(), e);
            
            // Fall back to error message
            String errorMessage = fallbackMessages.getProcessed(
                com.dadcoach.workflow.message.MessageType.ERROR_GENERIC,
                MessageContext.builder()
                    .fatherName(father.getDisplayName())
                    .locale(father.getLocale())
                    .build()
            );
            
            return new OutboundMessageDto(
                UUID.randomUUID(),
                fatherUuid,
                "WHATSAPP",
                MessageType.TEXT,
                errorMessage,
                null,
                false,
                null,
                null,
                MessagePriority.IMMEDIATE,
                Instant.now()
            );
        }
    }
    
    // ─── End AI Agent Processing ────────────────────────────────────────────────
    
    /**
     * Truncates a message for logging purposes.
     */
    private String truncateForLog(String message) {
        if (message == null) {
            return "<null>";
        }
        if (message.length() > 50) {
            return message.substring(0, 50) + "...";
        }
        return message;
    }
}
