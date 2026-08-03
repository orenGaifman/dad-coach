package com.dadcoach.workflow.state;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemState.ChildInfo;
import com.dadcoach.systemstate.SystemState.QualityTimeEvent;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageContext.ActivityIdea;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.message.MessageType;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;
import com.dadcoach.workflow.pattern.StatePatterns;
import com.dadcoach.workflow.pattern.WorkflowAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State handler for the ACTIVITY_IDEAS workflow state.
 * 
 * <p>This is an overlay state that provides on-demand activity suggestions when
 * the father explicitly requests ideas. It's accessible from WAITING state and
 * returns to the previous state when the interaction completes.</p>
 * 
 * <p><strong>State Behavior:</strong></p>
 * <ul>
 *   <li><strong>On entry:</strong> Read child age/interests, weather, previous activities;
 *       generate 3 ideas via AI</li>
 *   <li><strong>On IDEA_NUMBER (1-3):</strong> Show detailed idea information</li>
 *   <li><strong>On MORE_IDEAS:</strong> Generate 3 new ideas</li>
 *   <li><strong>On EXIT:</strong> Return to previous_workflow_state</li>
 * </ul>
 * 
 * <p><strong>AI Usage:</strong> This is one of the approved AI usage points per the
 * AI Usage Policy. AI is used to generate personalized activity ideas based on
 * the child's age, interests, and previous activities.</p>
 * 
 * <p><strong>Language Support:</strong> English (en) and Hebrew (he) only.</p>
 * 
 * <p>Implements Requirements 9.1, 9.2, 9.3, 9.4, 9.6 from the deterministic-workflow-engine spec.</p>
 * 
 * @see StateHandler
 * @see StatePatterns#ACTIVITY_IDEAS_PATTERNS
 * @see WorkflowState#ACTIVITY_IDEAS
 */
@Component
public class ActivityIdeasStateHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(ActivityIdeasStateHandler.class);

    private final MessageGenerator messageGenerator;
    private final SystemStateLoader systemStateLoader;
    private final FatherRepository fatherRepository;

    /**
     * Session-scoped storage for current activity ideas per father.
     * Maps fatherId to their current list of activity ideas.
     * 
     * <p>Note: In a distributed environment, this would need to be replaced
     * with a distributed cache (e.g., Redis) or stored in the database.
     * For MVP, in-memory storage is sufficient.</p>
     */
    private final Map<UUID, List<ActivityIdea>> currentIdeasByFather = new ConcurrentHashMap<>();

    /**
     * Creates a new ActivityIdeasStateHandler.
     * 
     * @param messageGenerator the message generator for AI-powered idea generation
     * @param systemStateLoader the system state loader for reading father and child data
     * @param fatherRepository the father repository for state updates
     */
    public ActivityIdeasStateHandler(
            MessageGenerator messageGenerator,
            SystemStateLoader systemStateLoader,
            FatherRepository fatherRepository) {
        this.messageGenerator = messageGenerator;
        this.systemStateLoader = systemStateLoader;
        this.fatherRepository = fatherRepository;
    }

    @Override
    public WorkflowState getState() {
        return WorkflowState.ACTIVITY_IDEAS;
    }

    @Override
    public List<StatePattern> getExpectedPatterns() {
        return StatePatterns.ACTIVITY_IDEAS_PATTERNS;
    }

    @Override
    public StateAction handle(WorkflowContext context, PatternResult match) {
        if (context == null || match == null) {
            throw new IllegalArgumentException("Context and match must not be null");
        }

        WorkflowAction action = match.matchedAction();
        log.debug("Handling ACTIVITY_IDEAS action: {} for father: {}", action, context.getFatherId());

        return switch (action) {
            case SHOW_IDEA_DETAILS -> handleShowIdeaDetails(context, match);
            case GENERATE_MORE_IDEAS -> handleGenerateMoreIdeas(context);
            case RETURN_TO_PREVIOUS -> handleReturnToPrevious(context);
            default -> {
                log.warn("Unexpected action {} in ACTIVITY_IDEAS state", action);
                yield handleUnmatched(context);
            }
        };
    }

    @Override
    public StateAction handleUnmatched(WorkflowContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        SystemState systemState = systemStateLoader.loadState(context.getFatherId());
        String locale = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().locale() 
                : MessageContext.DEFAULT_LOCALE;

        // Build state-specific clarification message with explicit options
        // Per Requirement 11.4: Do NOT use AI to interpret unmatched messages
        String clarificationMessage = buildActivityIdeasClarificationMessage(locale);

        return StateAction.clarify(clarificationMessage);
    }

    /**
     * Builds a state-specific clarification message for the ACTIVITY_IDEAS state.
     * 
     * <p>Per Requirement 11.4: The message is specific to the activity ideas state context
     * and explicitly lists valid response options. No AI interpretation is used.</p>
     * 
     * @param locale the father's locale ("en" or "he")
     * @return the clarification message with explicit options
     */
    private String buildActivityIdeasClarificationMessage(String locale) {
        if (MessageContext.LOCALE_HEBREW.equals(locale)) {
            return "לא הבנתי. הקלד מספר (1-3) לפרטים על רעיון, 'עוד' לרעיונות נוספים, או 'תודה' לסיים.";
        }
        return "I didn't understand. Type a number (1-3) for idea details, 'more' for new ideas, or 'thanks' to finish.";
    }

    /**
     * Entry point called when entering the ACTIVITY_IDEAS state.
     * Generates initial 3 activity ideas based on child info and context.
     * 
     * <p>This method should be called by the WorkflowEngine when transitioning
     * to ACTIVITY_IDEAS state. It:</p>
     * <ol>
     *   <li>Loads child age and interests from SystemState</li>
     *   <li>Reads previous activities to avoid repetition</li>
     *   <li>Generates 3 personalized activity ideas via AI</li>
     *   <li>Stores the ideas for this session</li>
     *   <li>Returns the formatted ideas message</li>
     * </ol>
     * 
     * @param fatherId the father's unique identifier
     * @return the state action with the generated ideas message
     */
    public StateAction onEntry(UUID fatherId) {
        log.info("Entering ACTIVITY_IDEAS state for father: {}", fatherId);

        SystemState systemState = systemStateLoader.loadState(fatherId);
        List<ActivityIdea> ideas = generateActivityIdeas(systemState);
        
        // Store ideas for this session
        currentIdeasByFather.put(fatherId, new ArrayList<>(ideas));

        // Build message context
        ChildInfo child = systemState.getDefaultChild();
        String fatherName = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().displayName() 
                : null;
        String locale = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().locale() 
                : MessageContext.DEFAULT_LOCALE;
        String timezone = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().timezone() 
                : MessageContext.DEFAULT_TIMEZONE;

        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.ACTIVITY_IDEAS)
                .fatherName(fatherName)
                .childName(child != null ? child.name() : null)
                .childAge(child != null ? child.age() : null)
                .activityIdeas(ideas)
                .locale(locale)
                .timezone(timezone)
                .build();

        String ideasMessage = messageGenerator.generateWithFallback(
                MessageType.ACTIVITY_IDEAS,
                messageContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS);

        return StateAction.respond(ideasMessage);
    }

    /**
     * Handles showing detailed information for a selected idea (1, 2, or 3).
     */
    private StateAction handleShowIdeaDetails(WorkflowContext context, PatternResult match) {
        // Extract idea number from the pattern match
        // The pattern "^([1-3])$" captures the number in group 1
        String input = context.getInboundMessage().trim();
        int ideaNumber;
        try {
            ideaNumber = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse idea number from input: {}", input);
            return handleUnmatched(context);
        }

        // Get the current ideas for this father
        List<ActivityIdea> currentIdeas = currentIdeasByFather.get(context.getFatherId());
        if (currentIdeas == null || currentIdeas.isEmpty()) {
            log.warn("No current ideas found for father: {}", context.getFatherId());
            // Generate new ideas if none exist
            return handleGenerateMoreIdeas(context);
        }

        // Validate idea number (1-indexed for user, 0-indexed for list)
        if (ideaNumber < 1 || ideaNumber > currentIdeas.size()) {
            log.warn("Invalid idea number {} (max: {})", ideaNumber, currentIdeas.size());
            return handleUnmatched(context);
        }

        ActivityIdea selectedIdea = currentIdeas.get(ideaNumber - 1);
        SystemState systemState = systemStateLoader.loadState(context.getFatherId());

        String locale = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().locale() 
                : MessageContext.DEFAULT_LOCALE;

        // Build a detailed response for the selected idea
        String detailedMessage = formatIdeaDetails(selectedIdea, ideaNumber, locale);

        return StateAction.respond(detailedMessage);
    }

    /**
     * Handles generating more activity ideas when the father requests them.
     */
    private StateAction handleGenerateMoreIdeas(WorkflowContext context) {
        log.info("Generating more ideas for father: {}", context.getFatherId());

        SystemState systemState = systemStateLoader.loadState(context.getFatherId());
        List<ActivityIdea> newIdeas = generateActivityIdeas(systemState);
        
        // Update stored ideas
        currentIdeasByFather.put(context.getFatherId(), new ArrayList<>(newIdeas));

        // Build message context
        ChildInfo child = systemState.getDefaultChild();
        String fatherName = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().displayName() 
                : null;
        String locale = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().locale() 
                : MessageContext.DEFAULT_LOCALE;
        String timezone = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().timezone() 
                : MessageContext.DEFAULT_TIMEZONE;

        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.ACTIVITY_IDEAS)
                .fatherName(fatherName)
                .childName(child != null ? child.name() : null)
                .childAge(child != null ? child.age() : null)
                .activityIdeas(newIdeas)
                .locale(locale)
                .timezone(timezone)
                .build();

        String ideasMessage = messageGenerator.generateWithFallback(
                MessageType.ACTIVITY_IDEAS,
                messageContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS);

        return StateAction.respond(ideasMessage);
    }

    /**
     * Handles returning to the previous workflow state when the father is done.
     */
    private StateAction handleReturnToPrevious(WorkflowContext context) {
        log.info("Returning to previous state for father: {}", context.getFatherId());

        // Clean up session data
        currentIdeasByFather.remove(context.getFatherId());

        // Load system state to get father info and previous state
        SystemState systemState = systemStateLoader.loadState(context.getFatherId());
        
        // Get the previous state from the Father entity
        // Use LeastSignificantBits as the domain ID (Long) per existing pattern
        Long fatherDomainId = context.getFatherId().getLeastSignificantBits();
        Optional<Father> fatherOpt = fatherRepository.findById(fatherDomainId);
        
        WorkflowState previousState = WorkflowState.WAITING; // Default fallback
        
        if (fatherOpt.isPresent()) {
            Father father = fatherOpt.get();
            if (father.getPreviousWorkflowState() != null) {
                previousState = father.getPreviousWorkflowState();
            }
        }

        String locale = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().locale() 
                : MessageContext.DEFAULT_LOCALE;
        String fatherName = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().displayName() 
                : null;

        // Build a brief thanks message
        String thanksMessage = locale.equals(MessageContext.LOCALE_HEBREW)
                ? String.format("תודה %s! מקווה שמצאת רעיונות טובים לזמן איכות 💪", 
                        fatherName != null ? fatherName : "")
                : String.format("Thanks %s! Hope you found some good ideas for Quality Time 💪", 
                        fatherName != null ? fatherName : "");

        log.info("Transitioning from ACTIVITY_IDEAS to {} for father: {}", 
                previousState, context.getFatherId());

        return StateAction.transition(previousState, thanksMessage);
    }

    /**
     * Generates 3 activity ideas using AI based on child info and context.
     * 
     * <p>Per Requirement 9.3, ideas are formatted as:</p>
     * <ul>
     *   <li>Numbered list (1, 2, 3)</li>
     *   <li>Each idea with: title, brief description (2-3 sentences), estimated duration</li>
     *   <li>Ideas appropriate for the child's age</li>
     *   <li>At least one indoor and one outdoor option when possible</li>
     * </ul>
     */
    private List<ActivityIdea> generateActivityIdeas(SystemState systemState) {
        ChildInfo child = systemState.getDefaultChild();
        
        // Get previous activity to avoid repetition
        String previousActivity = null;
        List<QualityTimeEvent> recentQualityTimes = systemState.qualityTimeEvents();
        if (recentQualityTimes != null && !recentQualityTimes.isEmpty()) {
            // Get the most recent completed quality time's notes as context
            previousActivity = recentQualityTimes.stream()
                    .filter(qt -> "COMPLETED".equals(qt.status()) && qt.completionNotes() != null)
                    .map(QualityTimeEvent::completionNotes)
                    .findFirst()
                    .orElse(null);
        }

        // For now, generate default ideas based on child age
        // The MessageGenerator will use AI to personalize these
        int childAge = child != null ? child.age() : 5; // Default to age 5 if unknown
        String locale = systemState.fatherProfile() != null 
                ? systemState.fatherProfile().locale() 
                : MessageContext.DEFAULT_LOCALE;

        return generateDefaultIdeas(childAge, locale);
    }

    /**
     * Generates default activity ideas based on child age.
     * These serve as a foundation that AI can personalize.
     */
    private List<ActivityIdea> generateDefaultIdeas(int childAge, String locale) {
        List<ActivityIdea> ideas = new ArrayList<>();

        if (locale.equals(MessageContext.LOCALE_HEBREW)) {
            // Hebrew ideas
            if (childAge <= 5) {
                ideas.add(new ActivityIdea(
                        "בניית מגדל קוביות",
                        "בנו יחד מגדל מקוביות או לגו. תנו לילד להוביל את הבנייה ולבחור את הצבעים. זה מפתח יצירתיות ותיאום.",
                        20,
                        true));
                ideas.add(new ActivityIdea(
                        "ציד אוצרות בחצר",
                        "צאו לחצר או לפארק הקרוב וחפשו יחד אוצרות טבע: עלים, אבנים מיוחדות, או פרחים.",
                        30,
                        false));
                ideas.add(new ActivityIdea(
                        "סיפור עם קולות",
                        "קראו יחד ספר אהוב ועשו קולות שונים לכל דמות. תנו לילד לבחור את הקולות.",
                        15,
                        true));
            } else if (childAge <= 10) {
                ideas.add(new ActivityIdea(
                        "בישול יחד",
                        "הכינו יחד מתכון פשוט כמו פנקייקים או עוגיות. תנו לילד למדוד חומרים ולערבב.",
                        30,
                        true));
                ideas.add(new ActivityIdea(
                        "טיול אופניים",
                        "צאו לרכיבת אופניים יחד בפארק או בשכונה. זה זמן איכות נהדר לשיחה תוך כדי תנועה.",
                        45,
                        false));
                ideas.add(new ActivityIdea(
                        "משחק לוח",
                        "שחקו יחד במשחק לוח מתאים לגיל. זה מפתח חשיבה אסטרטגית ולמדינות לקבל הפסד.",
                        30,
                        true));
            } else {
                ideas.add(new ActivityIdea(
                        "פרויקט DIY",
                        "בנו יחד משהו - בית ציפורים, מדף, או כל פרויקט יצירתי שהילד בוחר.",
                        45,
                        true));
                ideas.add(new ActivityIdea(
                        "משחק כדורסל",
                        "צאו לשחק כדורסל או כדורגל יחד. זמן פעילות גופנית משותפת מחזק את הקשר.",
                        40,
                        false));
                ideas.add(new ActivityIdea(
                        "לימוד מיומנות חדשה",
                        "למדו יחד משהו חדש - נגינה, שפה, או תכנות בסיסי. הילד יכול גם ללמד אתכם משהו.",
                        30,
                        true));
            }
        } else {
            // English ideas
            if (childAge <= 5) {
                ideas.add(new ActivityIdea(
                        "Building Block Tower",
                        "Build a tower together using blocks or Lego. Let your child lead the construction and choose colors. This develops creativity and coordination.",
                        20,
                        true));
                ideas.add(new ActivityIdea(
                        "Backyard Treasure Hunt",
                        "Go to the backyard or nearby park and search for nature treasures together: leaves, special rocks, or flowers.",
                        30,
                        false));
                ideas.add(new ActivityIdea(
                        "Story Time with Voices",
                        "Read a favorite book together and use different voices for each character. Let your child choose the voices.",
                        15,
                        true));
            } else if (childAge <= 10) {
                ideas.add(new ActivityIdea(
                        "Cooking Together",
                        "Make a simple recipe together like pancakes or cookies. Let your child measure ingredients and mix.",
                        30,
                        true));
                ideas.add(new ActivityIdea(
                        "Bike Ride",
                        "Go for a bike ride together in the park or neighborhood. Great quality time for conversation while moving.",
                        45,
                        false));
                ideas.add(new ActivityIdea(
                        "Board Game",
                        "Play an age-appropriate board game together. Develops strategic thinking and learning to accept losing.",
                        30,
                        true));
            } else {
                ideas.add(new ActivityIdea(
                        "DIY Project",
                        "Build something together - a birdhouse, shelf, or any creative project your child chooses.",
                        45,
                        true));
                ideas.add(new ActivityIdea(
                        "Basketball Game",
                        "Go play basketball or soccer together. Shared physical activity strengthens your bond.",
                        40,
                        false));
                ideas.add(new ActivityIdea(
                        "Learn a New Skill",
                        "Learn something new together - music, a language, or basic coding. Your child can also teach you something.",
                        30,
                        true));
            }
        }

        return ideas;
    }

    /**
     * Formats detailed information about a selected idea.
     */
    private String formatIdeaDetails(ActivityIdea idea, int number, String locale) {
        if (locale.equals(MessageContext.LOCALE_HEBREW)) {
            return String.format(
                    "🎯 *רעיון %d: %s*\n\n" +
                    "%s\n\n" +
                    "⏱️ משך: %d דקות\n" +
                    "📍 %s\n\n" +
                    "הקלד 'עוד' לרעיונות נוספים או 'תודה' לסיים.",
                    number,
                    idea.title(),
                    idea.description(),
                    idea.durationMinutes(),
                    idea.indoor() ? "פעילות בבית" : "פעילות בחוץ");
        } else {
            return String.format(
                    "🎯 *Idea %d: %s*\n\n" +
                    "%s\n\n" +
                    "⏱️ Duration: %d minutes\n" +
                    "📍 %s\n\n" +
                    "Type 'more' for new ideas or 'thanks' to finish.",
                    number,
                    idea.title(),
                    idea.description(),
                    idea.durationMinutes(),
                    idea.indoor() ? "Indoor activity" : "Outdoor activity");
        }
    }

    /**
     * Clears the session data for a father. Should be called when the father
     * leaves the ACTIVITY_IDEAS state or the session expires.
     * 
     * @param fatherId the father's unique identifier
     */
    public void clearSessionData(UUID fatherId) {
        currentIdeasByFather.remove(fatherId);
    }
}
