package com.dadcoach.weeklygoal;

import com.dadcoach.config.BeltImageConfig;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.whatsapp.WhatsAppApiClient;
import com.dadcoach.whatsapp.WhatsAppMessageFormatter;
import com.dadcoach.workflow.Belt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for sending belt promotion notifications via WhatsApp.
 * 
 * <p>When a father completes their weekly goal and earns a belt promotion,
 * this service sends a congratulatory message with the new belt image.</p>
 */
@Service
public class BeltPromotionNotifier {

    private static final Logger log = LoggerFactory.getLogger(BeltPromotionNotifier.class);

    private final WhatsAppApiClient whatsAppApiClient;
    private final WhatsAppMessageFormatter messageFormatter;
    private final BeltImageConfig beltImageConfig;
    private final FatherRepository fatherRepository;

    public BeltPromotionNotifier(
            WhatsAppApiClient whatsAppApiClient,
            WhatsAppMessageFormatter messageFormatter,
            BeltImageConfig beltImageConfig,
            FatherRepository fatherRepository) {
        this.whatsAppApiClient = whatsAppApiClient;
        this.messageFormatter = messageFormatter;
        this.beltImageConfig = beltImageConfig;
        this.fatherRepository = fatherRepository;
    }

    /**
     * Sends a belt promotion notification to a father.
     *
     * @param result the belt promotion result from goal completion
     */
    public void sendPromotionNotification(WeeklyGoalService.BeltPromotionResult result) {
        if (!result.promoted()) {
            log.debug("No promotion for father {}, skipping notification", result.fatherId());
            return;
        }

        Father father = fatherRepository.findById(result.fatherId())
            .orElse(null);
        
        if (father == null || father.getPhone() == null) {
            log.warn("Cannot send promotion notification - father not found or no phone: {}", result.fatherId());
            return;
        }

        String phoneNumber = father.getPhone();
        Belt newBelt = result.newBelt();
        Belt previousBelt = result.previousBelt();

        try {
            // First, send the belt image if configured
            String imageUrl = beltImageConfig.getImageUrl(newBelt);
            if (beltImageConfig.hasImage(newBelt)) {
                String caption = String.format(
                    "🎉 מזל טוב! עלית ל%s!",
                    newBelt.getDisplayName("he")
                );
                
                Map<String, Object> imagePayload = messageFormatter.formatImageMessage(
                    phoneNumber, 
                    imageUrl, 
                    caption
                );
                
                var imageResult = whatsAppApiClient.sendMessage(imagePayload);
                if (imageResult.success()) {
                    log.info("Sent belt promotion image to father {}: {} -> {}", 
                             result.fatherId(), previousBelt, newBelt);
                } else {
                    log.warn("Failed to send belt image, will send text only: {}", imageResult.errorDetail());
                }
            }

            // Then send the congratulatory text message
            String textMessage = buildPromotionMessage(result);
            
            // Use the simple text formatting method directly
            Map<String, Object> textPayload = new java.util.LinkedHashMap<>();
            textPayload.put("messaging_product", "whatsapp");
            textPayload.put("recipient_type", "individual");
            textPayload.put("to", phoneNumber.startsWith("+") ? phoneNumber.substring(1) : phoneNumber);
            textPayload.put("type", "text");
            
            Map<String, Object> textBody = new java.util.LinkedHashMap<>();
            textBody.put("preview_url", false);
            textBody.put("body", textMessage);
            textPayload.put("text", textBody);

            var textResult = whatsAppApiClient.sendMessage(textPayload);
            if (textResult.success()) {
                log.info("Sent belt promotion notification to father {}", result.fatherId());
            } else {
                log.error("Failed to send belt promotion text to father {}: {}", 
                         result.fatherId(), textResult.errorDetail());
            }

        } catch (Exception e) {
            log.error("Error sending belt promotion notification to father {}", result.fatherId(), e);
        }
    }

    /**
     * Sends promotion notifications for multiple fathers (batch operation).
     *
     * @param results the list of promotion results
     */
    public void sendBatchPromotionNotifications(List<WeeklyGoalService.BeltPromotionResult> results) {
        log.info("Sending {} belt promotion notifications", results.size());
        
        for (WeeklyGoalService.BeltPromotionResult result : results) {
            try {
                sendPromotionNotification(result);
                // Small delay to avoid rate limiting
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Batch promotion notifications interrupted");
                break;
            }
        }
    }

    /**
     * Builds the promotion message text.
     */
    private String buildPromotionMessage(WeeklyGoalService.BeltPromotionResult result) {
        Belt newBelt = result.newBelt();
        Belt previousBelt = result.previousBelt();
        int actualMinutes = result.actualMinutes();
        int targetMinutes = result.targetMinutes();
        int streak = result.currentStreak();
        boolean programCompleted = result.programCompleted();

        StringBuilder sb = new StringBuilder();
        
        // Special header for program completion (BLACK belt)
        if (programCompleted) {
            sb.append("🎊🏆🎊 מזל טוב ענק! סיימת את התוכנית! 🎊🏆🎊\n\n");
        } else {
            sb.append("🏆 כל הכבוד! עמדת ביעד השבועי!\n\n");
        }
        
        sb.append("📊 סיכום השבוע:\n");
        sb.append("🎯 יעד: ").append(targetMinutes / 60).append(" שעות\n");
        sb.append("✅ ביצוע: ").append(formatMinutesAsTime(actualMinutes)).append("\n");
        
        // Show streak
        if (streak > 1) {
            sb.append("🔥 רצף: ").append(streak).append(" שבועות רצופים!\n");
        }
        sb.append("\n");
        
        sb.append("🥋 עלית חגורה!\n");
        sb.append("מ").append(previousBelt.getDisplayName("he"));
        sb.append(" ל").append(newBelt.getDisplayName("he")).append("!\n\n");
        
        // Add encouragement based on the new belt and context
        if (programCompleted) {
            sb.append(getProgramCompletionMessage());
        } else {
            sb.append(getBeltEncouragement(newBelt, streak));
        }
        
        if (!programCompleted) {
            sb.append("\n\n📅 רוצה לקבוע יעד לשבוע הבא?");
        }
        
        return sb.toString();
    }

    /**
     * Formats minutes as hours and minutes string.
     */
    private String formatMinutesAsTime(int totalMinutes) {
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        
        if (minutes > 0) {
            return hours + " שעות ו-" + minutes + " דקות";
        } else {
            return hours + " שעות";
        }
    }

    /**
     * Returns an encouraging message based on the new belt level and streak.
     */
    private String getBeltEncouragement(Belt belt, int streak) {
        // Add streak bonus message
        String streakBonus = "";
        if (streak >= 3) {
            streakBonus = "\n🔥 " + streak + " שבועות ברצף! אתה על גלגל!";
        }
        
        String beltMessage = switch (belt) {
            case YELLOW -> "💛 התחלת את המסע! כל חגורה מקרבת אותך לאבא מעולה יותר.";
            case ORANGE -> "🧡 יופי! אתה בדרך הנכונה. עוד 5 שבועות לחגורה שחורה!";
            case GREEN -> "💚 מרשים! חצי דרך לפסגה! הילדים מרגישים את זה.";
            case BLUE -> "💙 מדהים! אתה אבא מסור. עוד 3 שבועות לסיום!";
            case BROWN -> "🤎 וואו! אתה כמעט שם! עוד שבוע אחד לחגורה שחורה! 💪";
            case BLACK -> "🖤 השגת את הפסגה! חגורה שחורה - אבא אלוף! 🏆";
            default -> "👏 כל הכבוד על ההתקדמות!";
        };
        
        return beltMessage + streakBonus;
    }

    /**
     * Returns the special message for completing the 7-week program.
     */
    private String getProgramCompletionMessage() {
        return """
            🏆 הגעת לחגורה שחורה! 🏆
            
            אתה הוכחת מחויבות אמיתית לילדים שלך.
            7 שבועות של זמן איכות, קשר, ובניית יחסים.
            
            הילדים שלך יזכרו את הרגעים האלה לתמיד.
            אתה אבא מדהים! 💪❤️
            
            המסע לא נגמר כאן - תמשיך להיות נוכח!
            אני כאן תמיד לעזור.""";
    }
}
