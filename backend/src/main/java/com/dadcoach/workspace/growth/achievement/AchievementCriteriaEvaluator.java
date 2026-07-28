package com.dadcoach.workspace.growth.achievement;

import com.dadcoach.workspace.growth.belt.FatherBeltRepository;
import com.dadcoach.workspace.growth.signal.GrowthSignalRepository;
import com.dadcoach.workspace.growth.signal.GrowthSignalType;
import com.dadcoach.workspace.growth.streak.FatherStreakRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Evaluates whether a specific {@link AchievementCriteria} is met for a given father.
 *
 * <p>This component switches on the sealed criteria type and performs the appropriate
 * database queries to determine if the father has met the achievement requirements.</p>
 *
 * <p>Using a separate evaluator (rather than embedding DB queries in the criteria records)
 * keeps the sealed interface implementations as pure data objects and allows Spring
 * to manage repository injection cleanly.</p>
 *
 * @see AchievementCriteria
 */
@Component
public class AchievementCriteriaEvaluator {

    private final GrowthSignalRepository growthSignalRepository;
    private final FatherStreakRepository fatherStreakRepository;
    private final FatherBeltRepository fatherBeltRepository;

    public AchievementCriteriaEvaluator(GrowthSignalRepository growthSignalRepository,
                                        FatherStreakRepository fatherStreakRepository,
                                        FatherBeltRepository fatherBeltRepository) {
        this.growthSignalRepository = growthSignalRepository;
        this.fatherStreakRepository = fatherStreakRepository;
        this.fatherBeltRepository = fatherBeltRepository;
    }

    /**
     * Evaluates whether the given criteria are met for the specified father.
     *
     * @param criteria the achievement criteria to evaluate
     * @param fatherId the father's unique identifier
     * @return true if the criteria are met
     */
    public boolean isMet(AchievementCriteria criteria, UUID fatherId) {
        return switch (criteria) {
            case AchievementCriteria.MissionCountCriteria c ->
                    growthSignalRepository.countByFatherIdAndSignalType(fatherId, GrowthSignalType.MISSION_COMPLETED) >= c.threshold();

            case AchievementCriteria.StreakDaysCriteria c ->
                    fatherStreakRepository.findByFatherId(fatherId)
                            .map(streak -> streak.getLongestStreakDays() >= c.threshold())
                            .orElse(false);

            case AchievementCriteria.GoalCountCriteria c ->
                    growthSignalRepository.countByFatherIdAndSignalType(fatherId, GrowthSignalType.GOAL_COMPLETED) >= c.threshold();

            case AchievementCriteria.ConversationCountCriteria c ->
                    growthSignalRepository.countByFatherIdAndSignalType(fatherId, GrowthSignalType.MEANINGFUL_CONVERSATION) >= c.threshold();

            case AchievementCriteria.BeltReachedCriteria c ->
                    fatherBeltRepository.findByFatherId(fatherId)
                            .map(belt -> belt.getBeltLevel().ordinal() >= c.requiredBelt().ordinal())
                            .orElse(false);
        };
    }
}
