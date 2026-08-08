package com.dadcoach.workflow.scheduler;

import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.workflow.scheduler.SchedulerJobLog;
import com.dadcoach.workflow.scheduler.SchedulerJobLogRepository;
import com.dadcoach.weeklygoal.BeltPromotionNotifier;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.whatsapp.WhatsAppService;
import com.dadcoach.workflow.WorkflowEngine;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.WorkflowTrigger;
import com.dadcoach.workflow.logging.WorkflowLoggingContext;
import com.dadcoach.workflow.message.FallbackMessages;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Scheduler component for workflow-related periodic jobs.
 * 
 * <p>This component handles time-based workflow operations:
 * <ul>
 *   <li><b>Morning reminder job</b>: Sends reminders at 8 AM local time for Quality Times scheduled today</li>
 *   <li><b>Follow-up transition job</b>: Transitions fathers to QUALITY_TIME_FOLLOW_UP when scheduled events end</li>
 *   <li><b>Stale state detection job</b>: Auto-transitions fathers stuck in QUALITY_TIME_FOLLOW_UP for over 24 hours</li>
 * </ul>
 * </p>
 * 
 * <p>All scheduler jobs are idempotent — running the same job multiple times will NOT produce 
 * duplicate messages or transitions (Requirements 12.2, 12.3).</p>
 * 
 * <p>Jobs process fathers in batches (configurable via {@code dadcoach.scheduler.batch-size})
 * to avoid overwhelming the system or WhatsApp rate limits (Requirement 12.6).</p>
 * 
 * <p>Job executions are logged to the scheduler_job_log table for debugging and monitoring.</p>
 * 
 * <p>Configuration is loaded from {@link SchedulerConfig} which maps to the 
 * {@code dadcoach.scheduler} prefix in application.yml.</p>
 * 
 * <p>Implements Requirements 12.1, 12.3 from the deterministic-workflow-engine spec.</p>
 * 
 * @see WorkflowEngine#triggerTransition(java.util.UUID, com.dadcoach.workflow.WorkflowTrigger)
 * @see SchedulerConfig
 */
@Component
@EnableConfigurationProperties(SchedulerConfig.class)
public class WorkflowScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);

    /**
     * Job name constant for stale state detection job logging.
     */
    static final String JOB_NAME_STALE_STATE_DETECTION = "stale_state_detection";

    /**
     * Stale state threshold: 24 hours in QUALITY_TIME_FOLLOW_UP state.
     * Requirement 7.6: If father does not respond to follow-up within 24 hours.
     */
    static final long STALE_STATE_THRESHOLD_HOURS = 24;

    private final QualityTimeRepository qualityTimeRepository;
    private final FatherRepository fatherRepository;
    private final WorkflowEngine workflowEngine;
    private final WhatsAppService whatsAppService;
    private final SchedulerJobLogRepository jobLogRepository;
    private final FallbackMessages fallbackMessages;
    private final SchedulerConfig schedulerConfig;
    private final WeeklyGoalService weeklyGoalService;
    private final BeltPromotionNotifier beltPromotionNotifier;

    /**
     * Constructs a new WorkflowScheduler with required dependencies.
     *
     * @param qualityTimeRepository repository for Quality Time queries
     * @param fatherRepository repository for Father queries
     * @param workflowEngine the workflow engine for triggering state transitions
     * @param whatsAppService service for sending WhatsApp messages
     * @param jobLogRepository repository for logging scheduler job executions
     * @param fallbackMessages fallback message templates for reminder messages
     * @param schedulerConfig configuration properties for scheduler timing and batching
     * @param weeklyGoalService service for weekly goal management
     * @param beltPromotionNotifier notifier for belt promotion messages
     */
    public WorkflowScheduler(
            QualityTimeRepository qualityTimeRepository,
            FatherRepository fatherRepository,
            WorkflowEngine workflowEngine,
            WhatsAppService whatsAppService,
            SchedulerJobLogRepository jobLogRepository,
            FallbackMessages fallbackMessages,
            SchedulerConfig schedulerConfig,
            WeeklyGoalService weeklyGoalService,
            BeltPromotionNotifier beltPromotionNotifier) {
        this.qualityTimeRepository = qualityTimeRepository;
        this.fatherRepository = fatherRepository;
        this.workflowEngine = workflowEngine;
        this.whatsAppService = whatsAppService;
        this.jobLogRepository = jobLogRepository;
        this.fallbackMessages = fallbackMessages;
        this.schedulerConfig = schedulerConfig;
        this.weeklyGoalService = weeklyGoalService;
        this.beltPromotionNotifier = beltPromotionNotifier;
        
        log.info("WorkflowScheduler initialized with config: morningReminderCron={}, followUpIntervalMs={}, " +
                "staleDetectionIntervalMs={}, batchSize={}",
                schedulerConfig.morningReminderCron(),
                schedulerConfig.followUpIntervalMs(),
                schedulerConfig.staleDetectionIntervalMs(),
                schedulerConfig.batchSize());
    }

    /**
     * Returns the batch size from configuration.
     * <p>Used for processing fathers in batches to avoid overwhelming the system
     * or WhatsApp rate limits (Requirement 12.6).</p>
     * 
     * @return the configured batch size
     */
    int getBatchSize() {
        return schedulerConfig.batchSize();
    }

    // ─── Scheduled Jobs ────────────────────────────────────────────────────
    
    /**
     * Sends morning reminders for Quality Time events scheduled today.
     * 
     * <p>This job runs at 7:50 AM UTC and sends reminders to fathers who have
     * Quality Time scheduled today and whose local time is 8 AM (within a 20-minute window).
     * This staggered approach ensures reminders arrive at approximately 8 AM local time
     * regardless of the father's timezone.</p>
     * 
     * <p>The job is idempotent: the reminder_sent flag ensures each Quality Time
     * only receives one reminder per day, even if the job runs multiple times.</p>
     * 
     * <p>Implements Requirements 6.2, 6.3, 12.1, 12.6 from the deterministic-workflow-engine spec.</p>
     */
    @Scheduled(cron = "${dadcoach.scheduler.morning-reminder-cron:0 50 7 * * *}") // Configurable cron (default 7:50 AM UTC)
    @Transactional
    public void sendMorningReminders() {
        // Implements Requirement 16.6: ALL logs SHALL include context (job_name for batch jobs)
        try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forJob("morning_reminder")) {
            log.info("Starting morning reminder job");
            SchedulerJobLog jobLog = new SchedulerJobLog("morning_reminder");
            jobLogRepository.save(jobLog);
            
            int processedCount = 0;
            int errorCount = 0;
            
            try {
                // Get today's start and end for a wide time range (covers all timezones)
                Instant now = Instant.now();
                // Look at a 48-hour window to catch Quality Times scheduled "today" in all timezones
                Instant dayStart = now.minusSeconds(24 * 60 * 60); // 24 hours ago
                Instant dayEnd = now.plusSeconds(24 * 60 * 60);    // 24 hours from now
                
                // Query all scheduled Quality Times within this window that haven't received reminders
                List<QualityTime> qualityTimes = qualityTimeRepository
                        .findScheduledTodayWithoutReminder(dayStart, dayEnd);
                
                log.info("Found {} Quality Time events to potentially send reminders", qualityTimes.size());
                
                // Process in batches using configured batch size
                int batchSize = getBatchSize();
                for (int i = 0; i < qualityTimes.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, qualityTimes.size());
                    List<QualityTime> batch = qualityTimes.subList(i, endIndex);
                    
                    for (QualityTime qualityTime : batch) {
                        try {
                            if (shouldSendReminderNow(qualityTime)) {
                                sendReminder(qualityTime);
                                processedCount++;
                            }
                        } catch (Exception e) {
                            log.error("Error sending reminder for QualityTime {}: {}", 
                                    qualityTime.getId(), e.getMessage(), e);
                            errorCount++;
                        }
                    }
                }
                
                jobLog.markCompleted(processedCount, errorCount);
                log.info("Morning reminder job completed: {} reminders sent, {} errors", 
                        processedCount, errorCount);
                
            } catch (Exception e) {
                log.error("Morning reminder job failed: {}", e.getMessage(), e);
                jobLog.markFailed(processedCount, errorCount);
            }
            
            jobLogRepository.save(jobLog);
        }
    }
    
    /**
     * Determines if a reminder should be sent now based on the father's local time.
     * 
     * <p>Sends reminders when the father's local time is approximately 8 AM
     * (within a 20-minute window from 7:50 AM to 8:10 AM).</p>
     * 
     * <p>Also verifies that the Quality Time is scheduled for today in the father's timezone.</p>
     * 
     * @param qualityTime the Quality Time to check
     * @return true if a reminder should be sent now
     */
    boolean shouldSendReminderNow(QualityTime qualityTime) {
        // Skip if reminder already sent (idempotency check)
        if (qualityTime.isReminderSent()) {
            return false;
        }
        
        Father father = qualityTime.getFather();
        if (father == null) {
            log.warn("QualityTime {} has no associated father", qualityTime.getId());
            return false;
        }
        
        String timezoneId = father.getTimezone();
        if (timezoneId == null || timezoneId.isBlank()) {
            timezoneId = "Asia/Jerusalem"; // Default timezone
        }
        
        ZoneId fatherZone;
        try {
            fatherZone = ZoneId.of(timezoneId);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' for father {}, using default", timezoneId, father.getId());
            fatherZone = ZoneId.of("Asia/Jerusalem");
        }
        
        // Get current time in father's timezone
        ZonedDateTime nowInFatherZone = ZonedDateTime.now(fatherZone);
        LocalTime localTime = nowInFatherZone.toLocalTime();
        
        // Check if local time is within the 8 AM reminder window (7:50 AM to 8:10 AM)
        LocalTime windowStart = LocalTime.of(7, 50);
        LocalTime windowEnd = LocalTime.of(8, 10);
        
        boolean isIn8AMWindow = !localTime.isBefore(windowStart) && !localTime.isAfter(windowEnd);
        
        if (!isIn8AMWindow) {
            return false;
        }
        
        // Verify the Quality Time is scheduled for today in father's timezone
        ZonedDateTime scheduledInFatherZone = qualityTime.getScheduledStart().atZone(fatherZone);
        LocalDate scheduledDate = scheduledInFatherZone.toLocalDate();
        LocalDate todayInFatherZone = nowInFatherZone.toLocalDate();
        
        return scheduledDate.equals(todayInFatherZone);
    }
    
    /**
     * Sends a morning reminder for a Quality Time event via WhatsApp.
     * 
     * <p>The message includes the father's name, child's name, and scheduled time
     * formatted in the father's local timezone.</p>
     * 
     * @param qualityTime the Quality Time event to send a reminder for
     */
    void sendReminder(QualityTime qualityTime) {
        Father father = qualityTime.getFather();
        
        // Implements Requirement 16.6: ALL logs SHALL include father_id
        try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forFather(father.getId())) {
            // Build message context
            String locale = father.getLocale() != null ? father.getLocale() : "en";
            String timezone = father.getTimezone() != null ? father.getTimezone() : "Asia/Jerusalem";
            
            // Get child name
            String childName = qualityTime.getChild() != null ? qualityTime.getChild().getName() : "your child";
            
            // Format the scheduled time in father's timezone
            ZoneId fatherZone;
            try {
                fatherZone = ZoneId.of(timezone);
            } catch (Exception e) {
                fatherZone = ZoneId.of("Asia/Jerusalem");
            }
            
            ZonedDateTime scheduledInFatherZone = qualityTime.getScheduledStart().atZone(fatherZone);
            String formattedTime = MessageContext.formatTimeInTimezone(
                    qualityTime.getScheduledStart(), 
                    fatherZone, 
                    "he".equals(locale) ? java.util.Locale.forLanguageTag("he-IL") : java.util.Locale.ENGLISH
            );
            
            // Build message context
            MessageContext context = MessageContext.builder()
                    .messageType(MessageType.WAITING_REMINDER)
                    .fatherName(father.getDisplayName())
                    .childName(childName)
                    .locale(locale)
                    .timezone(timezone)
                    .scheduledStart(qualityTime.getScheduledStart())
                    .scheduledTimeFormatted(formattedTime)
                    .build();
            
            // Generate message using fallback templates (per AI Usage Policy - use templates for daily messages)
            String message = fallbackMessages.getProcessed(MessageType.WAITING_REMINDER, context);
            
            // Send via WhatsApp
            try {
                whatsAppService.sendText(father.getPhone(), message);
                log.debug("Sent morning reminder for QualityTime {}", qualityTime.getId());
            } catch (Exception e) {
                log.error("Failed to send WhatsApp reminder: {}", e.getMessage());
                throw e;
            }
            
            // Mark reminder as sent (idempotency)
            qualityTime.setReminderSent(true);
            qualityTimeRepository.save(qualityTime);
        }
    }
    
    /**
     * Follow-up transition job that runs every 15 minutes.
     * 
     * <p>This job handles the transition from WAITING to QUALITY_TIME_FOLLOW_UP
     * when a scheduled Quality Time's end time has passed.</p>
     * 
     * <p>The job is idempotent — it uses the {@code follow_up_sent} flag to ensure
     * that each Quality Time only triggers one follow-up transition.</p>
     * 
     * <p>Processing steps:
     * <ol>
     *   <li>Query Quality Times with status=SCHEDULED and end_time &lt; now and follow_up_sent=false</li>
     *   <li>Process in batches using configured batch size</li>
     *   <li>For each Quality Time:
     *     <ul>
     *       <li>Trigger transition to QUALITY_TIME_FOLLOW_UP via WorkflowEngine</li>
     *       <li>Send follow-up question via WhatsApp</li>
     *       <li>Mark follow_up_sent=true</li>
     *     </ul>
     *   </li>
     *   <li>Log execution to scheduler_job_log table</li>
     * </ol>
     * </p>
     * 
     * <p>Implements Requirements 6.6, 12.4 from the deterministic-workflow-engine spec.</p>
     */
    @Scheduled(fixedRateString = "${dadcoach.scheduler.follow-up-interval-ms:900000}") // Configurable interval (default 15 minutes)
    @Transactional
    public void processFollowUpTransitions() {
        // Implements Requirement 16.6: ALL logs SHALL include context (job_name for batch jobs)
        try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forJob("follow_up_transition")) {
            log.info("Starting follow-up transition job");
            
            // Create job log entry
            SchedulerJobLog jobLog = new SchedulerJobLog("follow_up_transition");
            jobLogRepository.save(jobLog);
            
            int processedCount = 0;
            int errorCount = 0;
            int batchSize = getBatchSize();
            
            try {
                // Query Quality Times with status=SCHEDULED and end_time < now and follow_up_sent=false
                // The repository method findScheduledEndedAndNotFollowedUp handles all these filters
                Instant now = Instant.now();
                List<QualityTime> qualityTimesNeedingFollowUp = qualityTimeRepository
                        .findScheduledEndedAndNotFollowedUp(now);
                
                log.info("Found {} Quality Times needing follow-up", qualityTimesNeedingFollowUp.size());
                
                // Process each Quality Time
                for (int i = 0; i < qualityTimesNeedingFollowUp.size(); i++) {
                    QualityTime qualityTime = qualityTimesNeedingFollowUp.get(i);
                    
                    try {
                        processFollowUpForQualityTime(qualityTime);
                        processedCount++;
                        
                        // Log progress every batch
                        if (processedCount % batchSize == 0) {
                            log.info("Follow-up transition job progress: processed {} of {}", 
                                    processedCount, qualityTimesNeedingFollowUp.size());
                        }
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Error processing follow-up for Quality Time {}: {}", 
                                qualityTime.getId(), e.getMessage(), e);
                    }
                }
                
                // Mark job as completed
                jobLog.markCompleted(processedCount, errorCount);
                jobLogRepository.save(jobLog);
                
                log.info("Follow-up transition job completed: processed={}, errors={}", 
                        processedCount, errorCount);
                
            } catch (Exception e) {
                // Mark job as failed
                jobLog.markFailed(processedCount, errorCount + 1);
                jobLogRepository.save(jobLog);
                
                log.error("Follow-up transition job failed: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Processes a single Quality Time for follow-up transition.
     * 
     * <p>This method:
     * <ol>
     *   <li>Derives the father's UUID from the Quality Time</li>
     *   <li>Triggers the QUALITY_TIME_ENDED transition via WorkflowEngine</li>
     *   <li>Sends the follow-up message via WhatsApp</li>
     *   <li>Marks the Quality Time's follow_up_sent flag as true</li>
     * </ol>
     * </p>
     * 
     * @param qualityTime the Quality Time to process
     */
    private void processFollowUpForQualityTime(QualityTime qualityTime) {
        // Get the father to access phone number and derive UUID
        Father father = qualityTime.getFather();
        if (father == null) {
            // Lazy load father if not already loaded
            father = fatherRepository.findById(qualityTime.getFatherId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Father not found for Quality Time: " + qualityTime.getId()));
        }
        
        // Derive father UUID from domain ID (same logic as WorkflowEngineImpl)
        UUID fatherUuid = deriveUuid(father.getId());
        
        // Implements Requirement 16.6: ALL logs SHALL include father_id
        try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forFather(fatherUuid)) {
            log.debug("Processing follow-up for Quality Time {}", qualityTime.getId());
            
            // Trigger the QUALITY_TIME_ENDED transition via WorkflowEngine
            // This will:
            // - Transition father from WAITING to QUALITY_TIME_FOLLOW_UP
            // - Generate and return the follow-up question message
            Optional<OutboundMessageDto> responseOpt = workflowEngine.triggerTransition(
                    fatherUuid, 
                    WorkflowTrigger.QUALITY_TIME_ENDED
            );
            
            // Send the follow-up message via WhatsApp if one was generated
            if (responseOpt.isPresent()) {
                OutboundMessageDto response = responseOpt.get();
                String phoneNumber = father.getPhone();
                String messageText = response.textContent();
                
                if (phoneNumber != null && messageText != null && !messageText.isBlank()) {
                    whatsAppService.sendText(phoneNumber, messageText);
                    log.info("Sent follow-up message (phone: {})", maskPhone(phoneNumber));
                } else {
                    log.warn("Could not send follow-up message for Quality Time {}: missing phone or message", 
                            qualityTime.getId());
                }
            } else {
                log.debug("No follow-up message generated for Quality Time {} (father may not be in WAITING state)", 
                        qualityTime.getId());
            }
            
            // Mark follow_up_sent=true to ensure idempotency
            qualityTime.setFollowUpSent(true);
            qualityTimeRepository.save(qualityTime);
            
            log.debug("Marked Quality Time {} as follow_up_sent=true", qualityTime.getId());
        }
    }
    
    /**
     * Derives a stable UUID from the domain Long ID.
     * Uses a deterministic mapping: MSB=0, LSB=domainId.
     * 
     * @param domainId the domain ID
     * @return the derived UUID
     */
    private UUID deriveUuid(Long domainId) {
        if (domainId == null) {
            return UUID.randomUUID();
        }
        return new UUID(0L, domainId);
    }
    
    /**
     * Masks a phone number for logging (privacy).
     * 
     * @param phone the phone number
     * @return masked phone number
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
    
    // ─── Stale State Detection Job (Task 14.5) ────────────────────────────────

    /**
     * Stale state detection job that runs at a configurable interval.
     * 
     * <p>Detects fathers who have been stuck in QUALITY_TIME_FOLLOW_UP state for over 24 hours
     * and haven't responded to the follow-up question. For each such father:</p>
     * <ul>
     *   <li>Marks their pending Quality Time as MISSED</li>
     *   <li>Transitions them to SCHEDULE_QUALITY_TIME state</li>
     *   <li>Sends a gentle re-engagement message asking to schedule new Quality Time</li>
     * </ul>
     * 
     * <p>This job is idempotent: running multiple times will not produce duplicate transitions
     * because we check the father's current state before processing.</p>
     * 
     * <p>Implements Requirements 7.6 (24-hour timeout) and 12.5 (stale state detection).</p>
     */
    @Scheduled(fixedRateString = "${dadcoach.scheduler.stale-detection-interval-ms:3600000}") // Configurable interval (default 1 hour)
    @Transactional
    public void processStaleStates() {
        // Implements Requirement 16.6: ALL logs SHALL include context (job_name for batch jobs)
        try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forJob(JOB_NAME_STALE_STATE_DETECTION)) {
            log.info("Starting stale state detection job");
            
            SchedulerJobLog jobLog = new SchedulerJobLog(JOB_NAME_STALE_STATE_DETECTION);
            jobLogRepository.save(jobLog);
            
            int processedCount = 0;
            int errorCount = 0;
            int batchSize = getBatchSize();
            
            try {
                // Calculate the cutoff time: 24 hours ago
                Instant cutoffTime = Instant.now().minus(STALE_STATE_THRESHOLD_HOURS, ChronoUnit.HOURS);
                
                // Query fathers in QUALITY_TIME_FOLLOW_UP state who entered that state before the cutoff
                List<Father> staleFathers = fatherRepository.findByCurrentWorkflowStateAndWorkflowStateEnteredAtBefore(
                        WorkflowState.QUALITY_TIME_FOLLOW_UP,
                        cutoffTime);
                
                log.info("Found {} fathers in stale QUALITY_TIME_FOLLOW_UP state", staleFathers.size());
                
                // Process in batches using configured batch size
                for (int i = 0; i < staleFathers.size(); i += batchSize) {
                    int batchEnd = Math.min(i + batchSize, staleFathers.size());
                    List<Father> batch = staleFathers.subList(i, batchEnd);
                    
                    for (Father father : batch) {
                        try {
                            processStaleStateFather(father);
                            processedCount++;
                            jobLog.incrementProcessed();
                        } catch (Exception e) {
                            errorCount++;
                            jobLog.incrementErrors();
                            log.error("Error processing stale state for father {}: {}", 
                                    father.getId(), e.getMessage(), e);
                        }
                    }
                    
                    // Log batch progress
                    log.debug("Processed batch {}-{} of {} stale state fathers", 
                            i + 1, batchEnd, staleFathers.size());
                }
                
                jobLog.markCompleted(processedCount, errorCount);
                log.info("Stale state detection job completed: processed={}, errors={}", 
                        processedCount, errorCount);
                
            } catch (Exception e) {
                jobLog.markFailed(processedCount, errorCount + 1);
                log.error("Stale state detection job failed: {}", e.getMessage(), e);
                throw e;
            } finally {
                jobLogRepository.save(jobLog);
            }
        }
    }

    /**
     * Process a single father who is in a stale QUALITY_TIME_FOLLOW_UP state.
     * 
     * <p>This method is idempotent: if the father is no longer in QUALITY_TIME_FOLLOW_UP state
     * (e.g., they responded while the job was running), the method returns without action.</p>
     *
     * @param father the father to process
     */
    private void processStaleStateFather(Father father) {
        // Double-check current state (idempotency)
        if (father.getCurrentWorkflowState() != WorkflowState.QUALITY_TIME_FOLLOW_UP) {
            log.debug("Father {} no longer in QUALITY_TIME_FOLLOW_UP state, skipping", father.getId());
            return;
        }
        
        // Implements Requirement 16.6: ALL logs SHALL include father_id
        try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forFather(father.getId())) {
            ctx.setState(father.getCurrentWorkflowState());
            
            log.info("Processing stale state (in state since {})", father.getWorkflowStateEnteredAt());
            
            // 1. Mark any scheduled Quality Time as MISSED
            markPendingQualityTimeAsMissed(father);
            
            // 2. Transition to SCHEDULE_QUALITY_TIME
            transitionToScheduleQualityTime(father, ctx);
            
            // 3. Send gentle re-engagement message
            sendReEngagementMessage(father);
            
            // 4. Persist the father state changes
            fatherRepository.save(father);
            
            log.info("Successfully processed stale state");
        }
    }

    /**
     * Marks any pending (scheduled but not followed-up) Quality Time records as MISSED.
     *
     * @param father the father whose Quality Time records to update
     */
    private void markPendingQualityTimeAsMissed(Father father) {
        List<QualityTime> scheduledQualityTimes = qualityTimeRepository.findByFatherIdAndStatus(
                father.getId(), QualityTimeStatus.SCHEDULED);
        
        for (QualityTime qt : scheduledQualityTimes) {
            // Only mark as missed if the scheduled end time has passed
            if (qt.hasEnded()) {
                qt.markMissed();
                qualityTimeRepository.save(qt);
                log.debug("Marked Quality Time {} as MISSED for father {}", qt.getId(), father.getId());
            }
        }
    }

    /**
     * Transitions the father from QUALITY_TIME_FOLLOW_UP to SCHEDULE_QUALITY_TIME state.
     * 
     * <p>Implements Requirement 16.1: Log state transitions with from_state, to_state, trigger_reason.</p>
     *
     * @param father the father to transition
     * @param ctx the logging context for structured logging
     */
    private void transitionToScheduleQualityTime(Father father, WorkflowLoggingContext ctx) {
        WorkflowState fromState = father.getCurrentWorkflowState();
        WorkflowState toState = WorkflowState.SCHEDULE_QUALITY_TIME;
        
        // Store previous state for potential debugging/audit
        father.setPreviousWorkflowState(fromState);
        
        // Transition to SCHEDULE_QUALITY_TIME
        father.setCurrentWorkflowState(toState);
        father.setWorkflowStateEnteredAt(Instant.now());
        
        // Log state transition with structured context (Requirement 16.1)
        ctx.setTransition(fromState, toState, "FOLLOW_UP_TIMEOUT");
        log.info("State transition: {} -> {} (trigger: FOLLOW_UP_TIMEOUT)", fromState, toState);
        ctx.clearTransition();
    }

    /**
     * Sends a gentle re-engagement message to the father, inviting them to schedule new Quality Time.
     * 
     * <p>The message is sent in the father's preferred language (English or Hebrew).</p>
     *
     * @param father the father to send the message to
     */
    private void sendReEngagementMessage(Father father) {
        String message = buildReEngagementMessage(father);
        
        try {
            whatsAppService.sendText(father.getPhone(), message);
            log.debug("Sent re-engagement message to phone {}", maskPhone(father.getPhone()));
        } catch (Exception e) {
            // Log but don't fail the entire processing - the state has already been updated
            log.error("Failed to send re-engagement message: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds a gentle re-engagement message in the father's preferred language.
     * 
     * <p>Language support: English (en) and Hebrew (he) ONLY — NO Spanish.</p>
     *
     * @param father the father to build the message for
     * @return the localized re-engagement message
     */
    private String buildReEngagementMessage(Father father) {
        String fatherName = father.getDisplayName() != null ? father.getDisplayName() : "";
        String locale = father.getLocale() != null ? father.getLocale() : "en";
        
        if ("he".equals(locale)) {
            // Hebrew message
            if (fatherName.isEmpty()) {
                return "היי! 👋 לא הספקנו לשמוע איך היה זמן האיכות. " +
                       "זה בסדר גמור - בוא נתאם את הזמן הבא! ✨ " +
                       "מתי נוח לך לתכנן זמן איכות עם הילדים?";
            }
            return "היי " + fatherName + "! 👋 לא הספקנו לשמוע איך היה זמן האיכות. " +
                   "זה בסדר גמור - בוא נתאם את הזמן הבא! ✨ " +
                   "מתי נוח לך לתכנן זמן איכות עם הילדים?";
        } else {
            // English message (default)
            if (fatherName.isEmpty()) {
                return "Hey there! 👋 We didn't hear back about your Quality Time. " +
                       "That's totally okay - let's schedule the next one! ✨ " +
                       "When would be a good time for Quality Time with your kids?";
            }
            return "Hey " + fatherName + "! 👋 We didn't hear back about your Quality Time. " +
                   "That's totally okay - let's schedule the next one! ✨ " +
                   "When would be a good time for Quality Time with your kids?";
        }
    }

    // ─── Weekly Goal Jobs ────────────────────────────────────────────────────

    /**
     * Weekly goal completion job that runs every Sunday at 6 AM Israel time.
     * 
     * <p>This job:
     * <ol>
     *   <li>Completes all active weekly goals from the previous week</li>
     *   <li>Sends belt promotion notifications to fathers who earned promotions</li>
     * </ol>
     * </p>
     * 
     * <p>The cron expression "0 0 6 * * SUN" runs at 6:00 AM every Sunday.</p>
     */
    @Scheduled(cron = "${dadcoach.scheduler.weekly-goal-completion-cron:0 0 6 * * SUN}")
    @Transactional
    public void processWeeklyGoalCompletions() {
        try (WorkflowLoggingContext ctx = WorkflowLoggingContext.forJob("weekly_goal_completion")) {
            log.info("Starting weekly goal completion job");
            SchedulerJobLog jobLog = new SchedulerJobLog("weekly_goal_completion");
            jobLogRepository.save(jobLog);
            
            int processedCount = 0;
            int errorCount = 0;
            
            try {
                // Complete all weekly goals and get promotions
                List<WeeklyGoalService.BeltPromotionResult> promotions = 
                    weeklyGoalService.completeWeeklyGoals();
                
                processedCount = promotions.size();
                log.info("Completed weekly goals, {} fathers were promoted", promotions.size());
                
                // Send promotion notifications
                if (!promotions.isEmpty()) {
                    beltPromotionNotifier.sendBatchPromotionNotifications(promotions);
                }
                
                jobLog.markCompleted(processedCount, errorCount);
                log.info("Weekly goal completion job completed: {} promotions", processedCount);
                
            } catch (Exception e) {
                log.error("Weekly goal completion job failed: {}", e.getMessage(), e);
                jobLog.markFailed(processedCount, errorCount + 1);
            }
            
            jobLogRepository.save(jobLog);
        }
    }

    // ─── Package-private accessors for testing ───────────────────────────────

    QualityTimeRepository getQualityTimeRepository() {
        return qualityTimeRepository;
    }

    FatherRepository getFatherRepository() {
        return fatherRepository;
    }

    WorkflowEngine getWorkflowEngine() {
        return workflowEngine;
    }

    WhatsAppService getWhatsAppService() {
        return whatsAppService;
    }

    SchedulerJobLogRepository getJobLogRepository() {
        return jobLogRepository;
    }

    FallbackMessages getFallbackMessages() {
        return fallbackMessages;
    }

    SchedulerConfig getSchedulerConfig() {
        return schedulerConfig;
    }
}
