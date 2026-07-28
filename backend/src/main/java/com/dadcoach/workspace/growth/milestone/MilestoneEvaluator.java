package com.dadcoach.workspace.growth.milestone;

import com.dadcoach.workspace.growth.achievement.AchievementCriteria;
import com.dadcoach.workspace.growth.achievement.AchievementCriteriaEvaluator;
import com.dadcoach.workspace.growth.belt.BeltLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for evaluating milestones for a father.
 *
 * <p>Evaluates all unreached milestones against the father's current state,
 * records newly reached milestones, and provides query methods for milestone status.</p>
 *
 * <p>Milestones share the same trigger condition format as achievements
 * (using {@link AchievementCriteria} sealed interface), so the same
 * {@link AchievementCriteriaEvaluator} is used for evaluation.</p>
 *
 * @see MilestoneRepository
 * @see FatherMilestoneRepository
 * @see AchievementCriteriaEvaluator
 */
@Service
public class MilestoneEvaluator {

    private static final Logger log = LoggerFactory.getLogger(MilestoneEvaluator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MilestoneRepository milestoneRepository;
    private final FatherMilestoneRepository fatherMilestoneRepository;
    private final AchievementCriteriaEvaluator criteriaEvaluator;

    public MilestoneEvaluator(MilestoneRepository milestoneRepository,
                              FatherMilestoneRepository fatherMilestoneRepository,
                              AchievementCriteriaEvaluator criteriaEvaluator) {
        this.milestoneRepository = milestoneRepository;
        this.fatherMilestoneRepository = fatherMilestoneRepository;
        this.criteriaEvaluator = criteriaEvaluator;
    }

    /**
     * Evaluates all milestones for a father, recording any newly reached milestones.
     *
     * <p>For each unreached milestone, parses the trigger condition JSON, evaluates via
     * {@link AchievementCriteriaEvaluator}, and saves a new {@link FatherMilestone}
     * if conditions are met.</p>
     *
     * @param fatherId the father's unique identifier
     * @return list of newly reached {@link FatherMilestone} records
     */
    @Transactional
    public List<FatherMilestone> evaluateAll(UUID fatherId) {
        List<Milestone> allMilestones = milestoneRepository.findAll();
        Set<UUID> reachedIds = fatherMilestoneRepository.findByFatherId(fatherId).stream()
                .map(FatherMilestone::getMilestoneId)
                .collect(Collectors.toSet());

        List<FatherMilestone> newlyReached = new ArrayList<>();

        for (Milestone milestone : allMilestones) {
            if (reachedIds.contains(milestone.getMilestoneId())) {
                continue;
            }

            AchievementCriteria criteria = parseTriggerCondition(milestone.getTriggerCondition());
            if (criteria == null) {
                log.warn("Failed to parse trigger condition for milestone={} — skipping",
                        milestone.getMilestoneId());
                continue;
            }

            if (criteriaEvaluator.isMet(criteria, fatherId)) {
                FatherMilestone reached = new FatherMilestone(
                        fatherId, milestone.getMilestoneId(), Instant.now());
                fatherMilestoneRepository.save(reached);
                newlyReached.add(reached);

                log.info("Milestone reached: father={}, milestone={} ({})",
                        fatherId, milestone.getMilestoneId(), milestone.getName());
            }
        }

        return newlyReached;
    }

    /**
     * Parses a trigger condition JSON string into an {@link AchievementCriteria} instance.
     *
     * <p>Milestones use the same criteria format as achievements, switching on the "type" field.</p>
     *
     * @param triggerCondition the JSON string from the milestone's trigger_condition column
     * @return the parsed criteria, or null if parsing fails
     */
    AchievementCriteria parseTriggerCondition(String triggerCondition) {
        try {
            JsonNode node = objectMapper.readTree(triggerCondition);
            String type = node.get("type").asText();

            return switch (type) {
                case "mission_count" -> new AchievementCriteria.MissionCountCriteria(
                        node.get("threshold").asInt());
                case "streak_days" -> new AchievementCriteria.StreakDaysCriteria(
                        node.get("threshold").asInt());
                case "goal_count" -> new AchievementCriteria.GoalCountCriteria(
                        node.get("threshold").asInt());
                case "conversation_count" -> new AchievementCriteria.ConversationCountCriteria(
                        node.get("threshold").asInt());
                case "belt_reached" -> new AchievementCriteria.BeltReachedCriteria(
                        BeltLevel.valueOf(node.get("required_belt").asText()));
                default -> {
                    log.warn("Unknown trigger condition type: {}", type);
                    yield null;
                }
            };
        } catch (JsonProcessingException | NullPointerException e) {
            log.error("Failed to parse trigger condition JSON: {}", triggerCondition, e);
            return null;
        }
    }
}
