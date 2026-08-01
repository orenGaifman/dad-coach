package com.dadcoach.conversation.ai;

import com.dadcoach.ai.AiProviderUnavailableException;
import com.dadcoach.ai.IntelligenceLayer;
import com.dadcoach.ai.output.CoachingContext;
import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.ai.safety.SafetyClassification;
import com.dadcoach.ai.safety.SafetyClassifier;
import com.dadcoach.ai.safety.SafetyResponseProvider;
import com.dadcoach.conversation.ConversationType;
import com.dadcoach.conversation.context.ConversationContext;
import com.dadcoach.conversation.dto.InboundMessageDto;
import com.dadcoach.domain.goal.FatherGoalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the AI orchestration sub-pipeline:
 * safety classification → generate → validate → retry with correction → fallback.
 *
 * <p>This class NEVER throws an exception — it always produces a deliverable response.
 * If AI generation fails or validation cannot be satisfied after one retry, a pre-written
 * fallback response is returned.
 *
 * <p>Pipeline execution order:
 * <ol>
 *   <li>Safety classification runs FIRST (before any coaching generation)</li>
 *   <li>If CRISIS or CHILD_SAFETY → immediate safety response (no generation)</li>
 *   <li>Generate coaching response via IntelligenceLayer</li>
 *   <li>Validate the response</li>
 *   <li>If validation fails → retry once with correction context</li>
 *   <li>If retry also fails → deliver fallback response</li>
 * </ol>
 *
 * <p>Maximum 1 retry (2 total AI calls) per message.
 */
@Service
public class AiOrchestratorImpl implements AiOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestratorImpl.class);

    private final SafetyClassifier safetyClassifier;
    private final SafetyResponseProvider safetyResponseProvider;
    private final IntelligenceLayer intelligenceLayer;
    private final ResponseValidator responseValidator;
    private final FallbackResponseProvider fallbackProvider;
    private final FatherGoalService fatherGoalService;

    public AiOrchestratorImpl(
            SafetyClassifier safetyClassifier,
            SafetyResponseProvider safetyResponseProvider,
            IntelligenceLayer intelligenceLayer,
            ResponseValidator responseValidator,
            FallbackResponseProvider fallbackProvider,
            FatherGoalService fatherGoalService
    ) {
        this.safetyClassifier = safetyClassifier;
        this.safetyResponseProvider = safetyResponseProvider;
        this.intelligenceLayer = intelligenceLayer;
        this.responseValidator = responseValidator;
        this.fallbackProvider = fallbackProvider;
        this.fatherGoalService = fatherGoalService;
    }

    /**
     * Executes the AI orchestration pipeline.
     * Guaranteed to return a valid, deliverable result (never null, never throws).
     */
    @Override
    public AiResult orchestrate(ConversationContext context, InboundMessageDto message) {
        Instant startTime = Instant.now();

        // Extract locale for fallback messages
        String locale = getLocale(context);

        try {
            // Step 1: Safety classification runs FIRST — before any coaching generation
            SafetyClassification safety = safetyClassifier.classify(message.content());

            // Step 2: If escalation (CRISIS or CHILD_SAFETY), return immediate safety response
            if (safety.requiresIntervention()) {
                log.warn("Safety escalation detected: category={}, confidence={}, reason={}",
                        safety.category(), safety.confidence(), safety.reason());
                String safetyResponse = safetyResponseProvider.getResponse(safety);
                return AiResult.safetyEscalation(safetyResponse, safety.category().name());
            }

            // Step 3: Generate coaching response
            CoachingContext coachingContext = buildCoachingContext(context, message);
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(coachingContext);

            // Step 4: Validate the response
            ValidationResult validation = responseValidator.validate(response, context);
            if (validation.passed()) {
                return buildSuccessResult(response, startTime, false);
            }

            log.info("First AI response failed validation: failures={}", validation.failures());

            // Step 5: Retry once with correction context
            CoachingContext retryContext = buildRetryContext(context, message, validation.failures());
            CoachingResponse retryResponse = intelligenceLayer.generateCoachingResponse(retryContext);

            // Validate the retry response
            ValidationResult retryValidation = responseValidator.validate(retryResponse, context);
            if (retryValidation.passed()) {
                return buildSuccessResult(retryResponse, startTime, true);
            }

            // Step 6: Both attempts failed validation — deliver fallback
            log.warn("AI response failed validation after retry: failures={}", retryValidation.failures());
            return buildFallbackResult(context.conversationType(), startTime, locale);

        } catch (AiProviderUnavailableException e) {
            // Provider exception → deliver fallback
            log.error("AI provider unavailable during orchestration: {}", e.getMessage());
            return buildFallbackResult(context.conversationType(), startTime, locale);
        } catch (Exception e) {
            // Any other unexpected exception → deliver fallback (never throw)
            log.error("Unexpected error during AI orchestration, delivering fallback", e);
            return buildFallbackResult(context.conversationType(), startTime, locale);
        }
    }

    /**
     * Extracts the locale from context, defaults to English.
     */
    private String getLocale(ConversationContext context) {
        if (context.fatherProfile() != null && context.fatherProfile().get("locale") != null) {
            return context.fatherProfile().get("locale").toString();
        }
        return "en";
    }

    // ===== Private helpers =====

    private CoachingContext buildCoachingContext(ConversationContext context, InboundMessageDto message) {
        ConversationType conversationType = parseConversationType(context.conversationType());
        String locale = getLocale(context);
        return new CoachingContext(
                context.fatherId(),
                conversationType,
                message.content(),
                List.of(), // conversation history (simplified — populated from context)
                buildSystemPrompt(context),
                formatMemories(context),
                formatContextContent(context),
                "", // output instructions
                locale
        );
    }

    private CoachingContext buildRetryContext(ConversationContext context, InboundMessageDto message,
                                             List<String> validationFailures) {
        ConversationType conversationType = parseConversationType(context.conversationType());
        String locale = getLocale(context);
        String correctionPrompt = buildSystemPrompt(context)
                + "\n\n[CORRECTION REQUIRED] Previous response failed validation: "
                + String.join("; ", validationFailures)
                + ". Please regenerate addressing these issues.";

        return new CoachingContext(
                context.fatherId(),
                conversationType,
                message.content(),
                List.of(),
                correctionPrompt,
                formatMemories(context),
                formatContextContent(context),
                "",
                locale
        );
    }

    private AiResult buildSuccessResult(CoachingResponse response, Instant startTime, boolean retried) {
        Duration latency = Duration.between(startTime, Instant.now());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model_used", response.model());
        metadata.put("provider", response.provider());
        metadata.put("latency_ms", latency.toMillis());
        metadata.put("input_tokens", response.inputTokens());
        metadata.put("output_tokens", response.outputTokens());

        String followUpAction = "NONE"; // default; CoachingResponse doesn't include action directly

        if (retried) {
            metadata.put("retried", true);
            return AiResult.retried(response.message(), followUpAction, metadata);
        }
        return AiResult.success(response.message(), followUpAction, metadata);
    }

    private AiResult buildFallbackResult(String conversationType, Instant startTime, String locale) {
        Duration latency = Duration.between(startTime, Instant.now());
        String fallbackContent;
        try {
            if (fallbackProvider instanceof FallbackResponseProviderImpl impl) {
                fallbackContent = impl.getForType(conversationType, locale);
            } else {
                fallbackContent = fallbackProvider.getForType(conversationType);
            }
        } catch (Exception e) {
            log.error("FallbackProvider.getForType failed, using generic fallback", e);
            try {
                if (fallbackProvider instanceof FallbackResponseProviderImpl impl) {
                    fallbackContent = impl.getGenericFallback(locale);
                } else {
                    fallbackContent = fallbackProvider.getGenericFallback();
                }
            } catch (Exception e2) {
                log.error("FallbackProvider.getGenericFallback also failed, using hardcoded last-resort", e2);
                fallbackContent = "he".equals(locale)
                        ? "סליחה, אני חווה קשיים טכניים. אנא נסה שוב מאוחר יותר."
                        : "Sorry, I'm experiencing technical difficulties. Please try again later.";
            }
        }
        log.info("Delivering fallback response for conversationType={} after {}ms",
                conversationType, latency.toMillis());
        return AiResult.fallback(fallbackContent);
    }

    private ConversationType parseConversationType(String type) {
        try {
            return ConversationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ConversationType.DAILY_COACHING;
        }
    }

    private String buildSystemPrompt(ConversationContext context) {
        // Get the father's language preference
        String locale = "en"; // default to English
        if (context.fatherProfile() != null && context.fatherProfile().get("locale") != null) {
            locale = context.fatherProfile().get("locale").toString();
        }

        if ("he".equals(locale)) {
            return buildGoalDrivenHebrewPrompt(context);
        } else {
            return buildGoalDrivenEnglishPrompt(context);
        }
    }

    /**
     * Builds a goal-driven Hebrew system prompt.
     * The coach LEADS the conversation toward a clear objective based on the father's current state.
     */
    private String buildGoalDrivenHebrewPrompt(ConversationContext context) {
        // Extract father info
        String fatherName = extractString(context.fatherProfile(), "display_name", "");
        String onboardingState = extractString(context.fatherProfile(), "onboarding_state", "NOT_STARTED");
        
        // Extract children info
        List<Map<String, Object>> children = context.children();
        String firstChildName = children.isEmpty() ? "" : extractString(children.get(0), "name", "");
        String firstChildAge = children.isEmpty() ? "" : String.valueOf(children.get(0).getOrDefault("age", ""));
        
        // Extract active mission info
        List<Map<String, Object>> missions = context.activeMissions();
        Map<String, Object> activeMission = missions.isEmpty() ? null : missions.get(0);
        String missionTitle = activeMission != null ? extractString(activeMission, "title", "") : "";
        String missionStatus = activeMission != null ? extractString(activeMission, "status", "") : "";
        String missionChildId = activeMission != null ? String.valueOf(activeMission.getOrDefault("child_id", "")) : "";
        String missionExpiresAt = activeMission != null ? extractString(activeMission, "expires_at", "") : "";
        
        // Find child name for mission
        String missionChildName = "";
        if (!missionChildId.isEmpty() && !children.isEmpty()) {
            for (Map<String, Object> child : children) {
                if (String.valueOf(child.get("id")).equals(missionChildId)) {
                    missionChildName = extractString(child, "name", "");
                    break;
                }
            }
        }
        
        // Get goal progress info
        String goalProgressInfo = getGoalProgressInfo(context, "he");
        
        // Determine the CURRENT OBJECTIVE based on state
        String currentObjective = determineCurrentObjective(onboardingState, activeMission, fatherName, firstChildName);
        
        return """
            # אתה מאמן אבא - מוביל, לא יועץ
            
            ## עיקרון מרכזי
            אתה מאמן שמוביל את האבא לפעולה. לא מחכה, לא מייעץ באופן כללי.
            כל הודעה שלך = צעד אחד לקראת המטרה.
            
            ## כללים קריטיים
            1. **קצר** - מקסימום 2-3 משפטים
            2. **ממוקד** - כל תשובה מתקדמת לעבר המטרה
            3. **פעיל** - תמיד סיים בשאלה או בקשה לפעולה
            4. **לא מסביר** - לא מסביר מה אתה עושה, פשוט עושה
            5. **חם אבל תכליתי** - אמוג'י אחד, משפט תמיכה קצר, וקדימה
            
            ## גבולות
            אם שואלים על נושא לא קשור להורות:
            "אני כאן בשביל הקשר שלך עם הילדים 😊 מה קורה עם [שם הילד]?"
            
            ## משימות בזק ⚡
            האבא יכול לשלוח "עכשיו" או "יש לי דקה" ולקבל משימה מיידית של 2-5 דקות.
            אם האבא מזכיר שיש לו זמן פנוי, הזכר לו: "אתה יכול לכתוב 'עכשיו' ולקבל משימת בזק!"
            
            ## מצב נוכחי
            - שם האבא: %s
            - ילד ראשון: %s (גיל %s)
            - שלב: %s
            - משימה פעילה: %s
            - סטטוס משימה: %s
            - ילד במשימה: %s
            
            ## יעדים 🎯
            %s
            
            ## המטרה הנוכחית שלך
            %s
            
            ## דוגמאות תשובות נכונות
            
            אם אין שם:
            "היי! אני המאמן שלך 🙌 איך קוראים לך?"
            
            אם אין ילד רשום:
            "אהלן %s! ספר לי על ילד אחד - מה השם והגיל?"
            
            אם יש משימה ASSIGNED:
            "היי %s! יש לך משימה עם %s - '%s'. מוכן לזה? 👍"
            
            אם יש משימה ACCEPTED/IN_PROGRESS:
            "%s, איך הולך עם המשימה עם %s? עשית את זה? 👍/👎"
            
            אם האבא אומר שלא הספיק:
            "קורה! מתי יהיה לך זמן עם %s? היום/מחר/סופ\"ש"
            
            אם המשימה הושלמה:
            "כל הכבוד %s! 🎉 איך הרגשת? ומה %s אמר/ה?"
            
            ## חשוב מאוד
            - אל תציע "אם תרצה" או "אולי" - היה ישיר
            - אל תסביר למה אתה שואל - פשוט שאל
            - אל תתן אפשרויות רבות - תן 2-3 מקסימום
            - תמיד הזכר את שם הילד אם יש
            - כשסיימת onboarding, הזכר את משימות הבזק: "דרך אגב, כשיש לך דקה פנויה - כתוב 'עכשיו' ותקבל משימה מהירה ⚡"
            """.formatted(
                fatherName.isEmpty() ? "(לא ידוע)" : fatherName,
                firstChildName.isEmpty() ? "(לא רשום)" : firstChildName,
                firstChildAge.isEmpty() ? "?" : firstChildAge,
                onboardingState,
                missionTitle.isEmpty() ? "(אין)" : missionTitle,
                missionStatus.isEmpty() ? "-" : missionStatus,
                missionChildName.isEmpty() ? "-" : missionChildName,
                goalProgressInfo,
                currentObjective,
                fatherName,
                fatherName,
                missionChildName,
                missionTitle,
                fatherName,
                missionChildName,
                missionChildName,
                fatherName,
                missionChildName
            );
    }

    /**
     * Builds a goal-driven English system prompt.
     */
    private String buildGoalDrivenEnglishPrompt(ConversationContext context) {
        // Extract father info
        String fatherName = extractString(context.fatherProfile(), "display_name", "");
        String onboardingState = extractString(context.fatherProfile(), "onboarding_state", "NOT_STARTED");
        
        // Extract children info
        List<Map<String, Object>> children = context.children();
        String firstChildName = children.isEmpty() ? "" : extractString(children.get(0), "name", "");
        String firstChildAge = children.isEmpty() ? "" : String.valueOf(children.get(0).getOrDefault("age", ""));
        
        // Extract active mission info
        List<Map<String, Object>> missions = context.activeMissions();
        Map<String, Object> activeMission = missions.isEmpty() ? null : missions.get(0);
        String missionTitle = activeMission != null ? extractString(activeMission, "title", "") : "";
        String missionStatus = activeMission != null ? extractString(activeMission, "status", "") : "";
        String missionChildId = activeMission != null ? String.valueOf(activeMission.getOrDefault("child_id", "")) : "";
        
        // Find child name for mission
        String missionChildName = "";
        if (!missionChildId.isEmpty() && !children.isEmpty()) {
            for (Map<String, Object> child : children) {
                if (String.valueOf(child.get("id")).equals(missionChildId)) {
                    missionChildName = extractString(child, "name", "");
                    break;
                }
            }
        }
        
        // Get goal progress info
        String goalProgressInfo = getGoalProgressInfo(context, "en");
        
        // Determine the CURRENT OBJECTIVE based on state
        String currentObjective = determineCurrentObjectiveEnglish(onboardingState, activeMission, fatherName, firstChildName);
        
        return """
            # You are Dad Coach - A Leader, Not an Advisor
            
            ## Core Principle
            You LEAD the father toward action. Don't wait, don't give general advice.
            Every message = one step toward the goal.
            
            ## Critical Rules
            1. **Short** - Maximum 2-3 sentences
            2. **Focused** - Every response advances toward the goal
            3. **Active** - Always end with a question or call to action
            4. **No explaining** - Don't explain what you're doing, just do it
            5. **Warm but purposeful** - One emoji, short supportive phrase, and forward
            
            ## Boundaries
            If asked about unrelated topics:
            "I'm here for your connection with your kids 😊 What's going on with [child name]?"
            
            ## Flash Missions ⚡
            Dad can send "now" or "got a minute" and get an instant 2-5 minute mission.
            If dad mentions having free time, remind him: "You can type 'now' and get a flash mission!"
            
            ## Current State
            - Father's name: %s
            - First child: %s (age %s)
            - Stage: %s
            - Active mission: %s
            - Mission status: %s
            - Child in mission: %s
            
            ## Goals 🎯
            %s
            
            ## Your Current Objective
            %s
            
            ## Example Correct Responses
            
            If no name:
            "Hey! I'm your coach 🙌 What's your name?"
            
            If no child registered:
            "Hey %s! Tell me about one child - name and age?"
            
            If mission is ASSIGNED:
            "Hey %s! You have a mission with %s - '%s'. Ready? 👍"
            
            If mission is ACCEPTED/IN_PROGRESS:
            "%s, how's the mission with %s going? Did you do it? 👍/👎"
            
            If dad says he couldn't:
            "No worries! When will you have time with %s? Today/Tomorrow/Weekend"
            
            If mission completed:
            "Great job %s! 🎉 How did it feel? And what did %s say?"
            
            ## Very Important
            - Don't offer "if you want" or "maybe" - be direct
            - Don't explain why you're asking - just ask
            - Don't give many options - give 2-3 max
            - Always mention the child's name if available
            - After completing onboarding, mention flash missions: "BTW, when you have a free minute - type 'now' and get a quick mission ⚡"
            """.formatted(
                fatherName.isEmpty() ? "(unknown)" : fatherName,
                firstChildName.isEmpty() ? "(not registered)" : firstChildName,
                firstChildAge.isEmpty() ? "?" : firstChildAge,
                onboardingState,
                missionTitle.isEmpty() ? "(none)" : missionTitle,
                missionStatus.isEmpty() ? "-" : missionStatus,
                missionChildName.isEmpty() ? "-" : missionChildName,
                goalProgressInfo,
                currentObjective,
                fatherName,
                fatherName,
                missionChildName,
                missionTitle,
                fatherName,
                missionChildName,
                missionChildName,
                fatherName,
                missionChildName
            );
    }

    /**
     * Gets goal progress information to include in the prompt.
     */
    private String getGoalProgressInfo(ConversationContext context, String locale) {
        // Extract father ID from context to get goal progress
        Object fatherIdObj = context.fatherProfile().get("id");
        if (fatherIdObj == null) {
            return locale.equals("he") ? "(אין מידע על יעדים)" : "(no goal info)";
        }

        try {
            Long fatherId = fatherIdObj instanceof Long ? (Long) fatherIdObj : Long.parseLong(fatherIdObj.toString());
            FatherGoalService.GoalProgressResult progress = fatherGoalService.getProgress(fatherId);
            
            StringBuilder sb = new StringBuilder();
            sb.append(progress.getWeeklyProgressText(locale));
            
            String streakText = progress.getStreakText(locale);
            if (!streakText.isEmpty()) {
                sb.append("\n").append(streakText);
            }
            
            return sb.toString();
        } catch (Exception e) {
            log.debug("Could not get goal progress: {}", e.getMessage());
            return locale.equals("he") ? "(אין מידע על יעדים)" : "(no goal info)";
        }
    }

    /**
     * Determines the current objective in Hebrew based on the father's state.
     */
    private String determineCurrentObjective(String onboardingState, Map<String, Object> activeMission, 
                                             String fatherName, String childName) {
        // Onboarding not complete - focus on getting basic info
        if ("NOT_STARTED".equals(onboardingState) || onboardingState == null) {
            return "🎯 לקבל את השם של האבא";
        }
        if ("NAME_COLLECTED".equals(onboardingState)) {
            return "🎯 לרשום ילד אחד (שם וגיל)";
        }
        if ("CHILDREN_REGISTERED".equals(onboardingState)) {
            return "🎯 לברר מה האבא רוצה לשפר עם הילד";
        }
        if ("GOALS_SET".equals(onboardingState)) {
            return "🎯 לקבוע זמן מועדף לתזכורות";
        }
        if ("SCHEDULE_SET".equals(onboardingState)) {
            return "🎯 לתת את המשימה הראשונה";
        }
        
        // Onboarding complete - focus on missions
        if (activeMission == null) {
            return "🎯 ליצור משימה חדשה עם " + (childName.isEmpty() ? "הילד" : childName);
        }
        
        String status = extractString(activeMission, "status", "");
        switch (status) {
            case "ASSIGNED":
                return "🎯 לוודא שהאבא מקבל את המשימה ומוכן לבצע";
            case "ACCEPTED":
                return "🎯 לעקוב אם האבא התחיל את המשימה";
            case "IN_PROGRESS":
                return "🎯 לבדוק אם המשימה הושלמה ולקבל פידבק";
            case "COMPLETED":
                return "🎯 לחגוג את ההצלחה וליצור משימה חדשה";
            case "EXPIRED":
            case "SKIPPED":
                return "🎯 להבין מה קרה ולקבוע מועד חדש למשימה";
            default:
                return "🎯 להתקדם עם " + (fatherName.isEmpty() ? "האבא" : fatherName);
        }
    }

    /**
     * Determines the current objective in English based on the father's state.
     */
    private String determineCurrentObjectiveEnglish(String onboardingState, Map<String, Object> activeMission,
                                                    String fatherName, String childName) {
        if ("NOT_STARTED".equals(onboardingState) || onboardingState == null) {
            return "🎯 Get the father's name";
        }
        if ("NAME_COLLECTED".equals(onboardingState)) {
            return "🎯 Register one child (name and age)";
        }
        if ("CHILDREN_REGISTERED".equals(onboardingState)) {
            return "🎯 Find out what the father wants to improve with the child";
        }
        if ("GOALS_SET".equals(onboardingState)) {
            return "🎯 Set preferred reminder time";
        }
        if ("SCHEDULE_SET".equals(onboardingState)) {
            return "🎯 Give the first mission";
        }
        
        if (activeMission == null) {
            return "🎯 Create a new mission with " + (childName.isEmpty() ? "the child" : childName);
        }
        
        String status = extractString(activeMission, "status", "");
        switch (status) {
            case "ASSIGNED":
                return "🎯 Confirm dad accepts the mission and is ready";
            case "ACCEPTED":
                return "🎯 Check if dad started the mission";
            case "IN_PROGRESS":
                return "🎯 Check if mission is done and get feedback";
            case "COMPLETED":
                return "🎯 Celebrate success and create new mission";
            case "EXPIRED":
            case "SKIPPED":
                return "🎯 Understand what happened and reschedule";
            default:
                return "🎯 Move forward with " + (fatherName.isEmpty() ? "dad" : fatherName);
        }
    }

    /**
     * Helper to safely extract a string from a map.
     */
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private String formatMemories(ConversationContext context) {
        if (context.rankedMemories().isEmpty()) {
            return null;
        }
        return "Memories: " + context.rankedMemories().toString();
    }

    private String formatContextContent(ConversationContext context) {
        StringBuilder sb = new StringBuilder();
        if (!context.children().isEmpty()) {
            sb.append("Children: ").append(context.children());
        }
        if (!context.activeGoals().isEmpty()) {
            sb.append(" Goals: ").append(context.activeGoals());
        }
        if (!context.activeMissions().isEmpty()) {
            sb.append(" Missions: ").append(context.activeMissions());
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
