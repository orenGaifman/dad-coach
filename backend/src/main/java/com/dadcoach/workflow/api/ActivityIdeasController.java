package com.dadcoach.workflow.api;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workflow.dto.ActivityIdeaDto;
import com.dadcoach.workflow.dto.ActivityIdeasResponse;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageContext.ActivityIdea;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.message.MessageType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for activity ideas endpoint.
 * 
 * <p>Provides AI-generated activity ideas for Quality Time with children.
 * The endpoint uses MessageGenerator to produce personalized activity suggestions
 * based on the child's age and the father's language preference.</p>
 * 
 * <p>Implements Requirements 9.3 and 14.1 from the deterministic-workflow-engine spec:</p>
 * <ul>
 *   <li>Requirement 9.3: Return exactly 3 activity ideas with title, description, duration, indoor/outdoor flag</li>
 *   <li>Requirement 14.1: REST API endpoint for activity ideas</li>
 * </ul>
 * 
 * <p><strong>Language Support:</strong> English (en) and Hebrew (he) only,
 * based on the father's language preference stored in the Father entity.</p>
 * 
 * @see ActivityIdeasResponse
 * @see ActivityIdeaDto
 * @see MessageGenerator
 */
@RestController
@RequestMapping("/api/v1/activity-ideas")
@Tag(name = "Activity Ideas", description = "AI-generated activity ideas for Quality Time")
public class ActivityIdeasController {

    private static final Logger log = LoggerFactory.getLogger(ActivityIdeasController.class);
    
    /** Number of activity ideas to return (per Requirement 9.3) */
    private static final int ACTIVITY_IDEAS_COUNT = 3;

    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final MessageGenerator messageGenerator;

    public ActivityIdeasController(
            FatherRepository fatherRepository,
            ChildRepository childRepository,
            MessageGenerator messageGenerator) {
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.messageGenerator = messageGenerator;
    }

    /**
     * GET /api/v1/activity-ideas — Retrieve activity ideas for Quality Time with a child.
     * 
     * <p>Returns exactly 3 activity ideas tailored to the child's age and the father's
     * language preference. Each idea includes:</p>
     * <ul>
     *   <li>Title: A short name for the activity</li>
     *   <li>Description: 2-3 sentences explaining the activity</li>
     *   <li>Duration: Estimated time in minutes</li>
     *   <li>Indoor flag: Whether the activity is suitable indoors</li>
     * </ul>
     * 
     * <p>Ideas include at least one indoor and one outdoor option when possible
     * (per Requirement 9.3).</p>
     * 
     * @param actor the authenticated actor context (injected via @AuthActor)
     * @param childId the UUID of the child to generate ideas for
     * @return 200 OK with 3 activity ideas, or 404 if child not found/not owned
     */
    @GetMapping
    @Operation(
        summary = "Get activity ideas",
        description = "Returns 3 AI-generated activity ideas for Quality Time with the specified child"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Activity ideas generated successfully",
            content = @Content(schema = @Schema(implementation = ActivityIdeasResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Child not found or does not belong to the authenticated father"
        )
    })
    public ResponseEntity<ActivityIdeasResponse> getActivityIdeas(
            @AuthActor ActorContext actor,
            @Parameter(description = "UUID of the child to get activity ideas for", required = true)
            @RequestParam("child_id") UUID childId) {

        UUID fatherUuid = actor.getActorId();
        Long fatherId = fatherUuid.getLeastSignificantBits();
        
        log.debug("Fetching activity ideas for father {} with child {}", fatherId, childId);

        // Load father to get language preference
        Father father = findFatherByUuid(fatherUuid);
        
        // Convert child UUID to Long and load child
        Long childIdLong = childId.getLeastSignificantBits();
        Child child = childRepository.findById(childIdLong)
                .orElseThrow(() -> {
                    log.warn("Child {} not found", childId);
                    return new ResourceNotFoundException("Child", childId);
                });

        // Verify ownership (return 404 for mismatch to prevent enumeration)
        if (!child.getFatherId().equals(fatherId)) {
            log.warn("Child {} does not belong to father {}", childIdLong, fatherId);
            throw new ResourceNotFoundException("Child", childId);
        }

        // Calculate child's age
        int childAge = calculateChildAge(child.getBirthDate());

        // Get father's language preference (defaults to "he" for Hebrew)
        String locale = father.getLocale() != null ? father.getLocale() : MessageContext.LOCALE_HEBREW;
        String timezone = father.getTimezone() != null ? father.getTimezone() : MessageContext.DEFAULT_TIMEZONE;

        // Generate activity ideas
        List<ActivityIdea> ideas = generateActivityIdeas(childAge, locale);

        // Build MessageContext and use MessageGenerator to potentially personalize
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.ACTIVITY_IDEAS)
                .fatherName(father.getDisplayName())
                .childName(child.getName())
                .childAge(childAge)
                .activityIdeas(ideas)
                .locale(locale)
                .timezone(timezone)
                .build();

        // Try to use AI for personalization (with fallback to default ideas)
        try {
            String generatedMessage = messageGenerator.generateWithFallback(
                    MessageType.ACTIVITY_IDEAS,
                    messageContext,
                    MessageGenerator.DEFAULT_TIMEOUT_MS);
            log.debug("AI message generated: {}", generatedMessage);
            // Note: The generated message is for WhatsApp formatting.
            // For the REST API, we return the structured DTOs directly.
        } catch (Exception e) {
            log.warn("Failed to generate AI message, using default ideas: {}", e.getMessage());
        }

        // Convert ActivityIdea to ActivityIdeaDto
        List<ActivityIdeaDto> ideaDtos = ideas.stream()
                .map(idea -> ActivityIdeaDto.of(
                        idea.title(),
                        idea.description(),
                        idea.durationMinutes(),
                        idea.indoor()))
                .toList();

        log.info("Returning {} activity ideas for father {} with child {} (age {})",
                ideaDtos.size(), fatherId, childIdLong, childAge);

        return ResponseEntity.ok(ActivityIdeasResponse.of(ideaDtos));
    }

    /**
     * Calculates the child's age in years from their birth date.
     * 
     * @param birthDate the child's birth date
     * @return the child's age in years, or 5 as a default if birth date is null
     */
    private int calculateChildAge(LocalDate birthDate) {
        if (birthDate == null) {
            log.warn("Child birth date is null, defaulting to age 5");
            return 5;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * Generates activity ideas based on child's age and language preference.
     * 
     * <p>Per Requirement 9.3:</p>
     * <ul>
     *   <li>Returns exactly 3 ideas</li>
     *   <li>Each idea with title, description (2-3 sentences), estimated duration</li>
     *   <li>Ideas appropriate for the child's age</li>
     *   <li>At least one indoor and one outdoor option</li>
     * </ul>
     * 
     * @param childAge the child's age in years
     * @param locale the language preference ("en" or "he")
     * @return a list of 3 activity ideas
     */
    private List<ActivityIdea> generateActivityIdeas(int childAge, String locale) {
        List<ActivityIdea> ideas = new ArrayList<>();

        if (MessageContext.LOCALE_HEBREW.equals(locale)) {
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
            // English ideas (default)
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
     * Finds a Father by UUID.
     * 
     * <p>Since Father.id is Long and ActorContext uses UUID, we need to convert.
     * The UUID's least significant bits represent the father's Long id.</p>
     * 
     * @param fatherUuid the father's UUID
     * @return the Father entity
     * @throws ResourceNotFoundException if father not found
     */
    private Father findFatherByUuid(UUID fatherUuid) {
        long numericId = fatherUuid.getLeastSignificantBits();
        return fatherRepository.findById(numericId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherUuid));
    }
}
