package com.dadcoach.scheduler;

import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;
import com.dadcoach.mission.MissionStatus;

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
 * Scheduled job that sends reminders for pending missions.
 *
 * <p>Runs every 30 minutes and sends reminders for:
 * <ul>
 *   <li>Missions approaching their scheduled time (2 hours before)</li>
 *   <li>Overdue missions (past expiration with no completion)</li>
 *   <li>Missions with no activity for 12+ hours</li>
 * </ul>
 *
 * <p>Respects the father's timezone for message timing.</p>
 */
@Component
public class MissionReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(MissionReminderScheduler.class);

    private final MissionRepository missionRepository;
    private final ChildRepository childRepository;
    private final DeliveryService deliveryService;

    public MissionReminderScheduler(MissionRepository missionRepository,
                                    ChildRepository childRepository,
                                    DeliveryService deliveryService) {
        this.missionRepository = missionRepository;
        this.childRepository = childRepository;
        this.deliveryService = deliveryService;
    }

    /**
     * Runs every 30 minutes to check for missions needing reminders.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // Every 30 minutes
    @Transactional
    public void sendMissionReminders() {
        log.debug("Starting mission reminder check...");
        
        Instant now = Instant.now();
        
        // Find missions that need reminders
        List<Mission> pendingMissions = missionRepository.findByStatusIn(
            List.of(MissionStatus.ASSIGNED, MissionStatus.ACCEPTED, MissionStatus.IN_PROGRESS)
        );
        
        int remindersSent = 0;
        
        for (Mission mission : pendingMissions) {
            if (shouldSendReminder(mission, now)) {
                sendReminder(mission);
                mission.markReminderSent();
                missionRepository.save(mission);
                remindersSent++;
            }
        }
        
        log.info("Mission reminder check complete. Sent {} reminders", remindersSent);
    }

    /**
     * Runs every hour to check for overdue missions.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // Every hour
    @Transactional
    public void checkOverdueMissions() {
        log.debug("Starting overdue mission check...");
        
        Instant now = Instant.now();
        
        // Find missions past their expiration that haven't been handled
        List<Mission> overdueMissions = missionRepository.findByStatusIn(
            List.of(MissionStatus.ASSIGNED, MissionStatus.ACCEPTED)
        );
        
        int overdueCount = 0;
        
        for (Mission mission : overdueMissions) {
            if (mission.getExpiresAt() != null && now.isAfter(mission.getExpiresAt())) {
                sendOverdueReminder(mission);
                overdueCount++;
            }
        }
        
        log.info("Overdue mission check complete. Found {} overdue missions", overdueCount);
    }

    private boolean shouldSendReminder(Mission mission, Instant now) {
        // Don't send too many reminders
        if (!mission.shouldSendReminder()) {
            return false;
        }
        
        // Check if mission has a scheduled time approaching (2 hours before)
        if (mission.getScheduledFor() != null) {
            Instant twoHoursBefore = mission.getScheduledFor().minusSeconds(2 * 3600);
            if (now.isAfter(twoHoursBefore) && now.isBefore(mission.getScheduledFor())) {
                return true;
            }
        }
        
        // Check if mission is close to expiring (4 hours before)
        if (mission.getExpiresAt() != null) {
            Instant fourHoursBefore = mission.getExpiresAt().minusSeconds(4 * 3600);
            if (now.isAfter(fourHoursBefore) && now.isBefore(mission.getExpiresAt())) {
                return true;
            }
        }
        
        // Check if 12+ hours since assignment with no activity
        if (mission.getAssignedAt() != null && mission.getStatus() == MissionStatus.ASSIGNED) {
            Instant twelveHoursAfterAssignment = mission.getAssignedAt().plusSeconds(12 * 3600);
            if (now.isAfter(twelveHoursAfterAssignment)) {
                return true;
            }
        }
        
        return false;
    }

    private void sendReminder(Mission mission) {
        Father father = mission.getFather();
        Child child = childRepository.findById(mission.getChildId()).orElse(null);
        
        String childName = child != null ? child.getName() : "הילד";
        String fatherName = father.getDisplayName() != null ? father.getDisplayName() : "";
        String locale = father.getLocale() != null ? father.getLocale() : "he";
        
        String message = buildReminderMessage(mission, fatherName, childName, locale);
        
        deliverMessage(father, message);
        log.info("Sent mission reminder to father {} for mission {}", father.getId(), mission.getId());
    }

    private void sendOverdueReminder(Mission mission) {
        Father father = mission.getFather();
        Child child = childRepository.findById(mission.getChildId()).orElse(null);
        
        String childName = child != null ? child.getName() : "הילד";
        String fatherName = father.getDisplayName() != null ? father.getDisplayName() : "";
        String locale = father.getLocale() != null ? father.getLocale() : "he";
        
        String message = buildOverdueMessage(mission, fatherName, childName, locale);
        
        deliverMessage(father, message);
        log.info("Sent overdue reminder to father {} for mission {}", father.getId(), mission.getId());
    }

    private String buildReminderMessage(Mission mission, String fatherName, String childName, String locale) {
        if ("he".equals(locale)) {
            String greeting = fatherName.isEmpty() ? "היי!" : "היי " + fatherName + "!";
            
            if (mission.getScheduledFor() != null) {
                String timeStr = formatTime(mission.getScheduledFor(), mission.getFather().getTimezone());
                return String.format(
                    "%s 🔔 תזכורת: יש לך משימה עם %s - '%s' ב-%s. מוכן? 👍",
                    greeting, childName, mission.getTitle(), timeStr
                );
            } else {
                return String.format(
                    "%s 🔔 לא לשכוח! המשימה עם %s - '%s'. עשית את זה? 👍/👎",
                    greeting, childName, mission.getTitle()
                );
            }
        } else {
            String greeting = fatherName.isEmpty() ? "Hey!" : "Hey " + fatherName + "!";
            
            if (mission.getScheduledFor() != null) {
                String timeStr = formatTime(mission.getScheduledFor(), mission.getFather().getTimezone());
                return String.format(
                    "%s 🔔 Reminder: You have a mission with %s - '%s' at %s. Ready? 👍",
                    greeting, childName, mission.getTitle(), timeStr
                );
            } else {
                return String.format(
                    "%s 🔔 Don't forget! Your mission with %s - '%s'. Did you do it? 👍/👎",
                    greeting, childName, mission.getTitle()
                );
            }
        }
    }

    private String buildOverdueMessage(Mission mission, String fatherName, String childName, String locale) {
        int rescheduleCount = mission.getRescheduleCount();
        
        if ("he".equals(locale)) {
            String greeting = fatherName.isEmpty() ? "היי" : fatherName;
            
            if (rescheduleCount >= 3) {
                return String.format(
                    "%s, המשימה עם %s לא יצאה לפועל 😔 בוא נבחר משימה אחרת שמתאימה יותר. מה דעתך?",
                    greeting, childName
                );
            } else {
                return String.format(
                    "%s, הזמן עבר על המשימה עם %s. קורה! 🤗 מתי תוכל לעשות את זה? היום/מחר/סופ\"ש",
                    greeting, childName
                );
            }
        } else {
            String greeting = fatherName.isEmpty() ? "Hey" : fatherName;
            
            if (rescheduleCount >= 3) {
                return String.format(
                    "%s, the mission with %s didn't happen 😔 Let's pick a different one that fits better. What do you think?",
                    greeting, childName
                );
            } else {
                return String.format(
                    "%s, time passed on the mission with %s. No worries! 🤗 When can you do it? Today/Tomorrow/Weekend",
                    greeting, childName
                );
            }
        }
    }

    private String formatTime(Instant instant, String timezone) {
        try {
            ZoneId zone = timezone != null ? ZoneId.of(timezone) : ZoneId.of("Asia/Jerusalem");
            ZonedDateTime zdt = instant.atZone(zone);
            return zdt.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return instant.toString().substring(11, 16);
        }
    }

    private void deliverMessage(Father father, String content) {
        UUID fatherUuid = new UUID(0L, father.getId());
        OutboundMessageDto message = new OutboundMessageDto(
            UUID.randomUUID(),
            fatherUuid,
            null, // Use primary endpoint
            MessageType.TEXT,
            content,
            null,
            false,
            null,
            null,
            MessagePriority.SCHEDULED,
            Instant.now()
        );
        
        try {
            deliveryService.deliver(message);
        } catch (Exception e) {
            log.error("Failed to deliver reminder to father {}: {}", father.getId(), e.getMessage());
        }
    }
}
