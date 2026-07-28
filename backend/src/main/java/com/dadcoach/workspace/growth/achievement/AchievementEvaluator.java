package com.dadcoach.workspace.growth.achievement;

import com.dadcoach.workspace.dto.response.AchievementsResponse;
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
 * Service responsible for evaluating achievements for a father.
 *
 * <p>Evaluates all unearned achievements against the father's current state,
 * awards newly met achievements, and provides query methods for achievement status.</p>
 *
 * @see AchievementCriteriaEvaluator
 * @see AchievementRepository
 * @see FatherAchievementRepository
 */
@Service
public class AchievementEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AchievementEvaluator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AchievementRepository achievementRepository;
    private final FatherAchievementRepository fatherAchievementRepository;
    private final AchievementCriteriaEvaluator criteriaEvaluator;

    public AchievementEvaluator(AchievementRepository achievementRepository,
                                FatherAchievementRepository fatherAchievementRepository,
                                AchievementCriteriaEvaluator criteriaEvaluator) {
        this.achievementRepository = achievementRepository;
        this.fatherAchievementRepository = fatherAchievementRepository;
        this.criteriaEvaluator = criteriaEvaluator;
    }

    /**
     * Evaluates all achievements for a father, awarding any newly met achievements.
     *
     * <p>For each unearned achievement, parses the criteria JSON, evaluates via
     * {@link AchievementCriteriaEvaluator}, and saves a new {@link FatherAchievement}
     * if criteria are met.</p>
     *
     * <p>Handles concurrent evaluation gracefully: if a unique constraint violation occurs
     * (another thread already awarded the same achievement), the duplicate is silently skipped.</p>
     *
     * @param fatherId the father's unique identifier
     * @return list of newly awarded {@link FatherAchievement} records
     */
    @Transactional
    public List<FatherAchievement> evaluateAll(UUID fatherId) {
        List<Achievement> allAchievements = achievementRepository.findAll();
        Set<UUID> earnedIds = fatherAchievementRepository.findByFatherId(fatherId).stream()
                .map(FatherAchievement::getAchievementId)
                .collect(Collectors.toSet());

        List<FatherAchievement> newlyAwarded = new ArrayList<>();

        for (Achievement achievement : allAchievements) {
            if (earnedIds.contains(achievement.getAchievementId())) {
                continue;
            }

            AchievementCriteria criteria = parseCriteriaJson(achievement.getCriteriaJson());
            if (criteria == null) {
                log.warn("Failed to parse criteria for achievement={} — skipping", achievement.getAchievementId());
                continue;
            }

            if (criteriaEvaluator.isMet(criteria, fatherId)) {
                try {
                    FatherAchievement earned = new FatherAchievement(
                            fatherId, achievement.getAchievementId(), Instant.now());
                    fatherAchievementRepository.saveAndFlush(earned);
                    newlyAwarded.add(earned);

                    log.info("Achievement earned: father={}, achievement={} ({})",
                            fatherId, achievement.getAchievementId(), achievement.getName());
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Concurrent thread already awarded this achievement — skip silently
                    log.debug("Achievement already awarded concurrently: father={}, achievement={} — skipping",
                            fatherId, achievement.getAchievementId());
                }
            }
        }

        return newlyAwarded;
    }

    /**
     * Builds the full achievements response DTO for the given father.
     *
     * @param fatherId the father's unique identifier
     * @return the achievements response including earned, total, and next achievable
     */
    public AchievementsResponse getAchievements(UUID fatherId) {
        List<Achievement> allAchievements = achievementRepository.findAll();
        List<FatherAchievement> earnedRecords = fatherAchievementRepository.findByFatherId(fatherId);

        Map<UUID, Instant> earnedMap = earnedRecords.stream()
                .collect(Collectors.toMap(FatherAchievement::getAchievementId, FatherAchievement::getEarnedAt));

        List<AchievementsResponse.AchievementItem> items = allAchievements.stream()
                .map(a -> new AchievementsResponse.AchievementItem(
                        a.getAchievementId(),
                        a.getName(),
                        a.getDescription(),
                        a.getCategory().name(),
                        a.getIconKey(),
                        earnedMap.get(a.getAchievementId())
                ))
                .toList();

        Optional<Achievement> nextAchievable = getNextAchievable(fatherId);
        AchievementsResponse.AchievementItem nextItem = nextAchievable
                .map(a -> new AchievementsResponse.AchievementItem(
                        a.getAchievementId(),
                        a.getName(),
                        a.getDescription(),
                        a.getCategory().name(),
                        a.getIconKey(),
                        null
                ))
                .orElse(null);

        return new AchievementsResponse(
                allAchievements.size(),
                earnedRecords.size(),
                items,
                nextItem
        );
    }

    /**
     * Finds the next achievable (closest unearned) achievement for a father,
     * ordered by sort_order.
     *
     * @param fatherId the father's unique identifier
     * @return the next unearned achievement closest by sort order, or empty if all earned
     */
    public Optional<Achievement> getNextAchievable(UUID fatherId) {
        List<Achievement> allAchievements = achievementRepository.findAll();
        Set<UUID> earnedIds = fatherAchievementRepository.findByFatherId(fatherId).stream()
                .map(FatherAchievement::getAchievementId)
                .collect(Collectors.toSet());

        return allAchievements.stream()
                .filter(a -> !earnedIds.contains(a.getAchievementId()))
                .min(Comparator.comparingInt(Achievement::getSortOrder));
    }

    /**
     * Parses a criteria JSON string into an {@link AchievementCriteria} instance.
     *
     * <p>Switches on the "type" field to create the appropriate sealed interface implementation.</p>
     *
     * @param criteriaJson the JSON string from the achievement's criteria_json column
     * @return the parsed criteria, or null if parsing fails
     */
    AchievementCriteria parseCriteriaJson(String criteriaJson) {
        try {
            JsonNode node = objectMapper.readTree(criteriaJson);
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
                    log.warn("Unknown criteria type: {}", type);
                    yield null;
                }
            };
        } catch (JsonProcessingException | NullPointerException e) {
            log.error("Failed to parse criteria JSON: {}", criteriaJson, e);
            return null;
        }
    }
}
