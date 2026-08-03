package com.dadcoach.domain.flash;

import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.goal.FatherGoalService;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Service for flash (quick spontaneous) missions.
 * Provides instant mission suggestions when father says "עכשיו" / "now".
 */
@Service
@Transactional
public class FlashMissionService {

    private static final Logger log = LoggerFactory.getLogger(FlashMissionService.class);
    private static final String FLASH_CATEGORY = "FLASH";

    private final FlashMissionTemplateRepository templateRepository;
    private final MissionRepository missionRepository;
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final FatherGoalService fatherGoalService;
    private final Random random = new Random();

    public FlashMissionService(FlashMissionTemplateRepository templateRepository,
                               MissionRepository missionRepository,
                               FatherRepository fatherRepository,
                               ChildRepository childRepository,
                               FatherGoalService fatherGoalService) {
        this.templateRepository = templateRepository;
        this.missionRepository = missionRepository;
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.fatherGoalService = fatherGoalService;
    }

    // ─── Get Flash Mission ───────────────────────────────────────────────

    /**
     * Gets a random flash mission suitable for the father's children.
     * This is the main method called when father says "עכשיו" / "now".
     *
     * @param fatherId the father ID
     * @param context optional context (HOME, CAR, OUTDOOR)
     * @return FlashMissionSuggestion with mission details
     */
    public FlashMissionSuggestion getFlashMission(Long fatherId, FlashMissionTemplate.Context context) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        // Get father's children
        List<Child> children = childRepository.findByFatherId(fatherId);
        if (children.isEmpty()) {
            log.warn("Father {} has no children registered", fatherId);
            return createNoChildrenSuggestion(father.getLocale());
        }

        // Pick a random child (or could use logic like "least recently interacted")
        Child selectedChild = children.get(random.nextInt(children.size()));
        int childAge = calculateAge(selectedChild.getBirthDate());

        // Find suitable templates
        List<FlashMissionTemplate> templates;
        if (context != null) {
            templates = templateRepository.findByContextAndAge(context, childAge);
        } else {
            templates = templateRepository.findAnywhereForAge(childAge);
        }

        if (templates.isEmpty()) {
            // Fallback to any template for this age
            templates = templateRepository.findSuitableForAge(childAge);
        }

        if (templates.isEmpty()) {
            log.warn("No suitable flash missions found for child age {}", childAge);
            return createDefaultSuggestion(father.getLocale(), selectedChild.getName());
        }

        // Pick random template
        FlashMissionTemplate template = templates.get(random.nextInt(templates.size()));
        String locale = father.getLocale() != null ? father.getLocale() : "he";

        return new FlashMissionSuggestion(
                template.getId(),
                selectedChild.getId(),
                selectedChild.getName(),
                template.getTitle(locale),
                personalizeDescription(template.getDescription(locale), selectedChild.getName()),
                template.getEstimatedMinutes(),
                template.getCategory().name(),
                locale
        );
    }

    /**
     * Gets a flash mission for a specific child.
     */
    public FlashMissionSuggestion getFlashMissionForChild(Long fatherId, Long childId,
                                                          FlashMissionTemplate.Context context) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        int childAge = calculateAge(child.getBirthDate());
        String locale = father.getLocale() != null ? father.getLocale() : "he";

        List<FlashMissionTemplate> templates;
        if (context != null) {
            templates = templateRepository.findByContextAndAge(context, childAge);
        } else {
            templates = templateRepository.findAnywhereForAge(childAge);
        }

        if (templates.isEmpty()) {
            return createDefaultSuggestion(locale, child.getName());
        }

        FlashMissionTemplate template = templates.get(random.nextInt(templates.size()));

        return new FlashMissionSuggestion(
                template.getId(),
                child.getId(),
                child.getName(),
                template.getTitle(locale),
                personalizeDescription(template.getDescription(locale), child.getName()),
                template.getEstimatedMinutes(),
                template.getCategory().name(),
                locale
        );
    }

    // ─── Complete Flash Mission ──────────────────────────────────────────

    /**
     * Records a completed flash mission and updates goals.
     *
     * @param fatherId the father ID
     * @param templateId the template ID (can be null for ad-hoc)
     * @param childId the child ID
     * @param actualMinutes actual time spent
     * @return GoalProgressResult with updated progress
     */
    public FatherGoalService.GoalProgressResult completeFlashMission(Long fatherId, Long templateId,
                                                                Long childId, int actualMinutes) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        // Create a mission record for tracking
        String title = "משימת בזק";
        String description = "משימה ספונטנית";

        if (templateId != null) {
            Optional<FlashMissionTemplate> template = templateRepository.findById(templateId);
            if (template.isPresent()) {
                String locale = father.getLocale() != null ? father.getLocale() : "he";
                title = template.get().getTitle(locale);
                description = template.get().getDescription(locale);
            }
        }

        Mission mission = new Mission(father, child, title, description, FLASH_CATEGORY, 1, actualMinutes);
        mission.transitionTo(com.dadcoach.mission.LegacyMissionStatus.COMPLETED);
        missionRepository.save(mission);

        // Update goals
        FatherGoalService.GoalProgressResult progress = fatherGoalService.addQualityMinutes(fatherId, actualMinutes);

        log.info("Flash mission completed for father {} with child {}. {} minutes added.",
                fatherId, childId, actualMinutes);

        return progress;
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    private int calculateAge(LocalDate birthDate) {
        if (birthDate == null) return 6; // Default assumption
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private String personalizeDescription(String description, String childName) {
        return description
                .replace("הילד", childName)
                .replace("your child", childName)
                .replace("the child", childName);
    }

    private FlashMissionSuggestion createNoChildrenSuggestion(String locale) {
        if ("he".equals(locale)) {
            return new FlashMissionSuggestion(
                    null, null, null,
                    "הוסף ילד קודם",
                    "כדי לקבל משימות בזק, קודם ספר לי על הילדים שלך 😊",
                    0, null, locale
            );
        } else {
            return new FlashMissionSuggestion(
                    null, null, null,
                    "Add a child first",
                    "To get flash missions, first tell me about your children 😊",
                    0, null, locale
            );
        }
    }

    private FlashMissionSuggestion createDefaultSuggestion(String locale, String childName) {
        if ("he".equals(locale)) {
            return new FlashMissionSuggestion(
                    null, null, childName,
                    "רגע איכות עם " + childName,
                    "שב עם " + childName + " לדקה ושאל מה קורה. הקשבה פשוטה = חיבור אמיתי 💙",
                    3, "CONNECTION", locale
            );
        } else {
            return new FlashMissionSuggestion(
                    null, null, childName,
                    "Quality moment with " + childName,
                    "Sit with " + childName + " for a minute and ask what's up. Simple listening = real connection 💙",
                    3, "CONNECTION", locale
            );
        }
    }

    // ─── Result DTO ──────────────────────────────────────────────────────

    public record FlashMissionSuggestion(
            Long templateId,
            Long childId,
            String childName,
            String title,
            String description,
            int estimatedMinutes,
            String category,
            String locale
    ) {
        /**
         * Formats the suggestion as a chat message.
         */
        public String toMessage() {
            if (templateId == null && childId == null) {
                // Error state
                return description;
            }

            StringBuilder sb = new StringBuilder();
            if ("he".equals(locale)) {
                sb.append("⚡ *משימת בזק עם ").append(childName).append("*\n\n");
                sb.append(description).append("\n\n");
                sb.append("⏱️ ").append(estimatedMinutes).append(" דקות\n\n");
                sb.append("עשית? שלח 👍");
            } else {
                sb.append("⚡ *Flash mission with ").append(childName).append("*\n\n");
                sb.append(description).append("\n\n");
                sb.append("⏱️ ").append(estimatedMinutes).append(" minutes\n\n");
                sb.append("Done? Send 👍");
            }
            return sb.toString();
        }
    }
}
