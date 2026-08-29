package com.dadcoach.workspace.commitment;

import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.common.AppConstants;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workspace.commitment.QualityTimeCommitment.CommitmentStatus;
import com.dadcoach.workspace.magiclink.DashboardLinkAppender;
import com.dadcoach.workspace.magiclink.DashboardLinkAppender.DashboardLinkContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Scheduler that sends reminders for quality time commitments.
 * 
 * Runs every 5 minutes to check for:
 * 1. Commitments needing 30-minute reminders
 * 2. Past-due commitments that should be marked as missed
 */
@Component
public class CommitmentReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommitmentReminderScheduler.class);
    private static final int REMINDER_MINUTES_BEFORE = 30;

    private final QualityTimeCommitmentRepository repository;
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final DeliveryService deliveryService;
    private final DashboardLinkAppender dashboardLinkAppender;

    public CommitmentReminderScheduler(QualityTimeCommitmentRepository repository,
                                        FatherRepository fatherRepository,
                                        ChildRepository childRepository,
                                        DeliveryService deliveryService,
                                        DashboardLinkAppender dashboardLinkAppender) {
        this.repository = repository;
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.deliveryService = deliveryService;
        this.dashboardLinkAppender = dashboardLinkAppender;
    }

    /**
     * Runs every 5 minutes to send commitment reminders.
     * Sends reminders 30 minutes before the scheduled time.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // Every 5 minutes
    @Transactional
    public void sendCommitmentReminders() {
        log.debug("Checking for commitments needing reminders...");
        
        Instant now = Instant.now();
        Instant reminderWindowEnd = now.plusSeconds(REMINDER_MINUTES_BEFORE * 60L);
        
        List<QualityTimeCommitment> needingReminder = 
                repository.findCommitmentsNeedingReminder(now, reminderWindowEnd);
        
        int remindersSent = 0;
        for (QualityTimeCommitment commitment : needingReminder) {
            try {
                sendReminder(commitment);
                remindersSent++;
            } catch (Exception e) {
                log.error("Failed to send reminder for commitment {}: {}", 
                         commitment.getId(), e.getMessage());
            }
        }
        
        if (remindersSent > 0) {
            log.info("Sent {} commitment reminders", remindersSent);
        }
    }

    /**
     * Runs every 15 minutes to check for missed commitments.
     * Marks commitments as MISSED if they're past due by more than 1 hour.
     */
    @Scheduled(fixedRate = 15 * 60 * 1000) // Every 15 minutes
    @Transactional
    public void checkMissedCommitments() {
        log.debug("Checking for missed commitments...");
        
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        List<QualityTimeCommitment> pastDue = repository.findPastDueCommitments(oneHourAgo);
        
        int missedCount = 0;
        for (QualityTimeCommitment commitment : pastDue) {
            commitment.markMissed();
            repository.save(commitment);
            missedCount++;
            
            // Optionally send a follow-up message
            sendMissedFollowUp(commitment);
        }
        
        if (missedCount > 0) {
            log.info("Marked {} commitments as missed", missedCount);
        }
    }

    private void sendReminder(QualityTimeCommitment commitment) {
        Father father = fatherRepository.findById(commitment.getFatherId()).orElse(null);
        if (father == null) {
            log.warn("Father {} not found for commitment {}", 
                    commitment.getFatherId(), commitment.getId());
            return;
        }

        Child child = commitment.getChildId() != null 
                ? childRepository.findById(commitment.getChildId()).orElse(null)
                : null;
        
        String message = buildReminderMessage(commitment, father, child);
        
        // Add dashboard link
        String dashboardLink = dashboardLinkAppender.generateLinkMessage(
                father.getId(), DashboardLinkContext.LOG_ACTIVITY_PROMPT);
        message = message + "\n\n" + dashboardLink;
        
        String messageId = deliverMessage(father, message);
        
        commitment.markReminded(messageId);
        repository.save(commitment);
        
        log.info("Sent reminder for commitment {} to father {}", commitment.getId(), father.getId());
    }

    private void sendMissedFollowUp(QualityTimeCommitment commitment) {
        Father father = fatherRepository.findById(commitment.getFatherId()).orElse(null);
        if (father == null) return;

        Child child = commitment.getChildId() != null 
                ? childRepository.findById(commitment.getChildId()).orElse(null)
                : null;
        
        String message = buildMissedMessage(commitment, father, child);
        deliverMessage(father, message);
    }

    private String buildReminderMessage(QualityTimeCommitment commitment, Father father, Child child) {
        String fatherName = father.getDisplayName() != null ? father.getDisplayName() : "";
        String childName = child != null ? child.getName() : "הילד/ה";
        String locale = father.getLocale() != null ? father.getLocale() : AppConstants.DEFAULT_LOCALE;
        
        String timeStr = formatTime(commitment.getScheduledAt(), father.getTimezone());
        
        if ("he".equals(locale)) {
            String greeting = fatherName.isEmpty() ? "היי!" : "היי " + fatherName + "!";
            String activity = commitment.getActivityNote() != null && !commitment.getActivityNote().isBlank()
                    ? commitment.getActivityNote()
                    : "זמן איכות";
            
            return String.format(
                "%s ⏰\n\n" +
                "עוד חצי שעה מתחיל הזמן שלך עם %s!\n" +
                "📝 %s\n" +
                "🕐 %s\n\n" +
                "מוכן? 💪",
                greeting, childName, activity, timeStr
            );
        } else {
            String greeting = fatherName.isEmpty() ? "Hey!" : "Hey " + fatherName + "!";
            String activity = commitment.getActivityNote() != null && !commitment.getActivityNote().isBlank()
                    ? commitment.getActivityNote()
                    : "Quality time";
            
            return String.format(
                "%s ⏰\n\n" +
                "Your time with %s starts in 30 minutes!\n" +
                "📝 %s\n" +
                "🕐 %s\n\n" +
                "Ready? 💪",
                greeting, childName, activity, timeStr
            );
        }
    }

    private String buildMissedMessage(QualityTimeCommitment commitment, Father father, Child child) {
        String fatherName = father.getDisplayName() != null ? father.getDisplayName() : "";
        String childName = child != null ? child.getName() : "הילד/ה";
        String locale = father.getLocale() != null ? father.getLocale() : AppConstants.DEFAULT_LOCALE;
        
        if ("he".equals(locale)) {
            return String.format(
                "%s, נראה שהזמן עם %s לא יצא לפועל 😔\n\n" +
                "זה קורה! מתי נקבע זמן חדש?\n" +
                "• היום\n" +
                "• מחר\n" +
                "• סוף השבוע",
                fatherName.isEmpty() ? "היי" : fatherName, childName
            );
        } else {
            return String.format(
                "%s, looks like the time with %s didn't happen 😔\n\n" +
                "It happens! When should we reschedule?\n" +
                "• Today\n" +
                "• Tomorrow\n" +
                "• Weekend",
                fatherName.isEmpty() ? "Hey" : fatherName, childName
            );
        }
    }

    private String formatTime(Instant instant, String timezone) {
        try {
            ZoneId zone = timezone != null ? ZoneId.of(timezone) : AppConstants.DEFAULT_ZONE_ID;;
            ZonedDateTime zdt = instant.atZone(zone);
            return zdt.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return instant.toString().substring(11, 16);
        }
    }

    private String deliverMessage(Father father, String content) {
        UUID fatherUuid = new UUID(0L, father.getId());
        UUID messageId = UUID.randomUUID();
        
        OutboundMessageDto message = new OutboundMessageDto(
            messageId,
            fatherUuid,
            null, // Use primary endpoint
            MessageType.TEXT,
            content,
            null,
            false,
            null,
            null,
            MessagePriority.IMMEDIATE, // Reminders are high priority
            Instant.now()
        );
        
        try {
            deliveryService.deliver(message);
            return messageId.toString();
        } catch (Exception e) {
            log.error("Failed to deliver reminder to father {}: {}", father.getId(), e.getMessage());
            return null;
        }
    }
}
