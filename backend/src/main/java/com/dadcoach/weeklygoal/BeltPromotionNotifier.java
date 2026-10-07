package com.dadcoach.weeklygoal;

import com.dadcoach.channel.WhatsAppEndpoints;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.integration.platform.SentMessageRecorder;

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

/**
 * Tells a father who earned a new belt (the weekly completion job): the belt image while his 24-hour window is open
 * (images cannot go in a template), then the congratulation text through the channel layer ({@link ProactiveSender}:
 * free-form in the window, the approved template outside it). A sent congratulation is recorded into his platform
 * conversation (playbook §10.2), so "כן" to "רוצה לקבוע יעד לשבוע הבא?" is understood.
 */
@Service
public class BeltPromotionNotifier {

    private static final Logger log = LoggerFactory.getLogger(BeltPromotionNotifier.class);

    private final WhatsAppApiClient whatsAppApiClient;
    private final WhatsAppMessageFormatter messageFormatter;
    private final BeltImageConfig beltImageConfig;
    private final FatherRepository fatherRepository;
    private final ProactiveSender sender;
    private final SentMessageRecorder recorder;
    private final WhatsAppEndpoints endpoints;
    private final SessionWindowService sessionWindows;

    public BeltPromotionNotifier(WhatsAppApiClient whatsAppApiClient, WhatsAppMessageFormatter messageFormatter,
                                 BeltImageConfig beltImageConfig, FatherRepository fatherRepository, ProactiveSender sender,
                                 SentMessageRecorder recorder, WhatsAppEndpoints endpoints, SessionWindowService sessionWindows) {
        this.whatsAppApiClient = whatsAppApiClient;
        this.messageFormatter = messageFormatter;
        this.beltImageConfig = beltImageConfig;
        this.fatherRepository = fatherRepository;
        this.sender = sender;
        this.recorder = recorder;
        this.endpoints = endpoints;
        this.sessionWindows = sessionWindows;
    }

    public void sendPromotionNotification(WeeklyGoalService.BeltPromotionResult result) {
        if (!result.promoted()) {
            return;
        }
        Father father = fatherRepository.findById(result.fatherId()).orElse(null);
        if (father == null || father.getPhone() == null || father.getStatus() == com.dadcoach.father.FatherStatus.DELETED) {
            return;
        }
        Belt newBelt = result.newBelt();
        try {
            boolean windowOpen = sessionWindows.isOpen(endpoints.ensure(father));
            if (windowOpen && beltImageConfig.hasImage(newBelt)) {
                String caption = String.format("🎉 מזל טוב! עלית ל%s!", newBelt.getDisplayName("he"));
                var image = whatsAppApiClient.sendMessage(messageFormatter.formatImageMessage(
                        father.getPhone(), beltImageConfig.getImageUrl(newBelt), caption));
                if (!image.success()) {
                    log.warn("Belt image not sent (text follows): fatherId={}", father.getId());
                }
            }
            String text = buildPromotionMessage(result);
            ProactiveSender.Outcome outcome = sender.send(father, text);
            log.atInfo().setMessage("whatsapp.delivery.result")
                    .addKeyValue("kind", "BELT_PROMOTION")
                    .addKeyValue("fatherId", father.getId())
                    .addKeyValue("mode", outcome.mode())
                    .addKeyValue("status", outcome.result().status())
                    .addKeyValue("failure", outcome.result().failureReason())
                    .log();
            if (outcome.result().isSuccessful()) {
                recorder.recordSent(father, text, "belt-promotion:" + father.getId() + ":" + newBelt.name());
            }
        } catch (RuntimeException e) {
            log.error("Belt promotion notification failed: fatherId={}", father.getId(), e);
        }
    }

    public void sendBatchPromotionNotifications(List<WeeklyGoalService.BeltPromotionResult> results) {
        results.forEach(this::sendPromotionNotification);
    }

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
