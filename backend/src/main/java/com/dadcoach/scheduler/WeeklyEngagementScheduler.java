package com.dadcoach.scheduler;

import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.goal.FatherGoalService;
import com.dadcoach.father.FatherStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job for weekly engagement activities:
 * <ul>
 *   <li>Sunday morning: Flash mission reminder</li>
 *   <li>Saturday night: Weekly goal summary + streak check</li>
 *   <li>End of week: Reset missed goal streaks</li>
 * </ul>
 */
@Component
public class WeeklyEngagementScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyEngagementScheduler.class);

    private final FatherRepository fatherRepository;
    private final FatherGoalService fatherGoalService;
    private final DeliveryService deliveryService;

    public WeeklyEngagementScheduler(FatherRepository fatherRepository,
                                     FatherGoalService fatherGoalService,
                                     DeliveryService deliveryService) {
        this.fatherRepository = fatherRepository;
        this.fatherGoalService = fatherGoalService;
        this.deliveryService = deliveryService;
    }

    /**
     * Runs every Sunday at 10:00 AM (Israel time) to remind fathers about flash missions.
     * Cron: 0 0 10 ? * SUN (second minute hour day month weekday)
     */
    @Scheduled(cron = "0 0 10 ? * SUN", zone = "Asia/Jerusalem")
    @Transactional(readOnly = true)
    public void sendWeeklyFlashMissionReminder() {
        log.info("Starting weekly flash mission reminder...");
        
        List<Father> activeFathers = fatherRepository.findByStatus(FatherStatus.ACTIVE);
        int sentCount = 0;
        
        for (Father father : activeFathers) {
            try {
                String message = buildFlashMissionReminder(father);
                deliverMessage(father, message);
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to send flash mission reminder to father {}: {}", 
                    father.getId(), e.getMessage());
            }
        }
        
        log.info("Weekly flash mission reminder complete. Sent to {} fathers", sentCount);
    }

    /**
     * Runs every Saturday at 8:00 PM (Israel time) to send weekly summary.
     * Cron: 0 0 20 ? * SAT
     */
    @Scheduled(cron = "0 0 20 ? * SAT", zone = "Asia/Jerusalem")
    @Transactional(readOnly = true)
    public void sendWeeklySummary() {
        log.info("Starting weekly summary...");
        
        List<Father> activeFathers = fatherRepository.findByStatus(FatherStatus.ACTIVE);
        int sentCount = 0;
        
        for (Father father : activeFathers) {
            try {
                FatherGoalService.GoalProgressResult progress = 
                    fatherGoalService.getProgress(father.getId());
                
                String message = buildWeeklySummary(father, progress);
                deliverMessage(father, message);
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to send weekly summary to father {}: {}", 
                    father.getId(), e.getMessage());
            }
        }
        
        log.info("Weekly summary complete. Sent to {} fathers", sentCount);
    }

    /**
     * Runs every Sunday at 1:00 AM (Israel time) to reset missed streaks.
     * Cron: 0 0 1 ? * SUN
     */
    @Scheduled(cron = "0 0 1 ? * SUN", zone = "Asia/Jerusalem")
    @Transactional
    public void resetMissedStreaks() {
        log.info("Starting weekly streak reset check...");
        fatherGoalService.resetMissedStreaks();
        log.info("Weekly streak reset check complete");
    }

    private String buildFlashMissionReminder(Father father) {
        String name = father.getDisplayName() != null ? father.getDisplayName() : "";
        String locale = father.getLocale() != null ? father.getLocale() : "he";
        
        if ("he".equals(locale)) {
            String greeting = name.isEmpty() ? "היי!" : "היי " + name + "!";
            return String.format(
                "%s שבוע חדש מתחיל 🌟\n\n" +
                "יש לך רגע פנוי עם הילדים? שלח 'עכשיו' ותקבל משימת בזק של 2-5 דקות ⚡\n\n" +
                "כל דקה משותפת = חיבור חזק יותר 💙",
                greeting
            );
        } else {
            String greeting = name.isEmpty() ? "Hey!" : "Hey " + name + "!";
            return String.format(
                "%s New week starts 🌟\n\n" +
                "Got a free moment with the kids? Send 'now' and get a 2-5 minute flash mission ⚡\n\n" +
                "Every shared minute = stronger connection 💙",
                greeting
            );
        }
    }

    private String buildWeeklySummary(Father father, FatherGoalService.GoalProgressResult progress) {
        String name = father.getDisplayName() != null ? father.getDisplayName() : "";
        String locale = father.getLocale() != null ? father.getLocale() : "he";
        
        int completed = progress.weeklyGoal().getCompletedMinutes();
        int target = progress.weeklyGoal().getTargetMinutes();
        int streak = progress.currentStreak();
        boolean goalReached = completed >= target;
        
        if ("he".equals(locale)) {
            String greeting = name.isEmpty() ? "" : name + ", ";
            
            if (goalReached) {
                StringBuilder sb = new StringBuilder();
                sb.append("🏆 ").append(greeting).append("כל הכבוד!\n\n");
                sb.append(String.format("השגת את היעד השבועי: %d/%d דקות עם הילדים!\n\n", completed, target));
                
                if (streak > 1) {
                    sb.append(String.format("🔥 אתה ברצף של %d שבועות! המשך כך!\n\n", streak));
                }
                
                sb.append("שבוע הבא נעלה את הרף? 💪");
                return sb.toString();
            } else {
                int remaining = target - completed;
                StringBuilder sb = new StringBuilder();
                sb.append("📊 ").append(greeting).append("סיכום שבועי\n\n");
                sb.append(String.format("השבוע: %d/%d דקות (נשארו %d)\n\n", completed, target, remaining));
                
                if (completed == 0) {
                    sb.append("עדיין לא מאוחר! שלח 'עכשיו' למשימה מהירה ⚡");
                } else {
                    sb.append("התחלה טובה! בשבוע הבא נגיע ליעד 🎯");
                }
                return sb.toString();
            }
        } else {
            String greeting = name.isEmpty() ? "" : name + ", ";
            
            if (goalReached) {
                StringBuilder sb = new StringBuilder();
                sb.append("🏆 ").append(greeting).append("Well done!\n\n");
                sb.append(String.format("You hit your weekly goal: %d/%d minutes with the kids!\n\n", completed, target));
                
                if (streak > 1) {
                    sb.append(String.format("🔥 You're on a %d week streak! Keep it up!\n\n", streak));
                }
                
                sb.append("Raise the bar next week? 💪");
                return sb.toString();
            } else {
                int remaining = target - completed;
                StringBuilder sb = new StringBuilder();
                sb.append("📊 ").append(greeting).append("Weekly Summary\n\n");
                sb.append(String.format("This week: %d/%d minutes (%d to go)\n\n", completed, target, remaining));
                
                if (completed == 0) {
                    sb.append("It's not too late! Send 'now' for a quick mission ⚡");
                } else {
                    sb.append("Good start! We'll hit the goal next week 🎯");
                }
                return sb.toString();
            }
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
            log.error("Failed to deliver weekly message to father {}: {}", father.getId(), e.getMessage());
        }
    }
}
