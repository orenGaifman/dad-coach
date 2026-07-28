package com.dadcoach.workspace.growth.signal;

import com.dadcoach.workspace.event.AchievementEarnedEvent;
import com.dadcoach.workspace.event.ConversationCompletedEvent;
import com.dadcoach.workspace.event.GoalCompletedEvent;
import com.dadcoach.workspace.event.GoalProgressEvent;
import com.dadcoach.workspace.event.GrowthSignalRecordedEvent;
import com.dadcoach.workspace.event.MilestoneReachedEvent;
import com.dadcoach.workspace.event.MissionCompletedEvent;
import com.dadcoach.workspace.event.MissionReflectedEvent;
import com.dadcoach.workspace.event.PositiveActivityReportedEvent;
import com.dadcoach.workspace.event.QualityTimeReportedEvent;
import com.dadcoach.workspace.event.StreakMilestoneEvent;
import com.dadcoach.workspace.growth.achievement.Achievement;
import com.dadcoach.workspace.growth.achievement.AchievementEvaluator;
import com.dadcoach.workspace.growth.achievement.AchievementRepository;
import com.dadcoach.workspace.growth.achievement.FatherAchievement;
import com.dadcoach.workspace.growth.belt.BeltLevel;
import com.dadcoach.workspace.growth.belt.BeltProgressionService;
import com.dadcoach.workspace.growth.milestone.FatherMilestone;
import com.dadcoach.workspace.growth.milestone.Milestone;
import com.dadcoach.workspace.growth.milestone.MilestoneEvaluator;
import com.dadcoach.workspace.growth.milestone.MilestoneRepository;
import com.dadcoach.workspace.growth.score.GrowthScoreService;
import com.dadcoach.workspace.growth.streak.StreakService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the {@link GrowthSignalProcessor} that converts domain events into growth signals.
 *
 * <p>Each handler follows the pattern:</p>
 * <ol>
 *   <li>Check for duplicate signal (idempotency guard)</li>
 *   <li>Record the signal via {@link GrowthSignalService}</li>
 *   <li>Update cached score via {@link GrowthScoreService}</li>
 *   <li>Publish {@link GrowthSignalRecordedEvent} for downstream processing
 *       (belt evaluation, achievement checks, cache invalidation)</li>
 * </ol>
 *
 * <p>Special conditions per Requirement 11.2:</p>
 * <ul>
 *   <li>{@code onGoalProgress}: only records if progress increase is ≥10%</li>
 *   <li>{@code onConversationCompleted}: only records if quality rating &gt; 0.6 AND exchange count &gt; 5</li>
 * </ul>
 *
 * @see GrowthSignalProcessor
 * @see GrowthSignalService
 * @see GrowthScoreService
 */
@Service
public class GrowthSignalProcessorImpl implements GrowthSignalProcessor {

    private static final Logger log = LoggerFactory.getLogger(GrowthSignalProcessorImpl.class);

    /**
     * Streak milestone thresholds. When a father's current streak reaches one of these
     * values, a STREAK_BONUS signal is recorded and a StreakMilestoneEvent is published.
     */
    private static final Set<Integer> STREAK_MILESTONES = Set.of(7, 14, 21, 30, 60, 90, 180, 365);

    private final GrowthSignalService growthSignalService;
    private final GrowthScoreService growthScoreService;
    private final BeltProgressionService beltProgressionService;
    private final StreakService streakService;
    private final AchievementEvaluator achievementEvaluator;
    private final MilestoneEvaluator milestoneEvaluator;
    private final AchievementRepository achievementRepository;
    private final MilestoneRepository milestoneRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GrowthSignalProcessorImpl(GrowthSignalService growthSignalService,
                                     GrowthScoreService growthScoreService,
                                     BeltProgressionService beltProgressionService,
                                     StreakService streakService,
                                     AchievementEvaluator achievementEvaluator,
                                     MilestoneEvaluator milestoneEvaluator,
                                     AchievementRepository achievementRepository,
                                     MilestoneRepository milestoneRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.growthSignalService = growthSignalService;
        this.growthScoreService = growthScoreService;
        this.beltProgressionService = beltProgressionService;
        this.streakService = streakService;
        this.achievementEvaluator = achievementEvaluator;
        this.milestoneEvaluator = milestoneEvaluator;
        this.achievementRepository = achievementRepository;
        this.milestoneRepository = milestoneRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @EventListener
    @Transactional
    public void onMissionCompleted(MissionCompletedEvent event) {
        UUID fatherId = event.getFatherId();
        UUID sourceEntityId = event.getMissionId();

        log.debug("Processing MissionCompletedEvent for father={}, mission={}", fatherId, sourceEntityId);

        if (growthSignalService.isDuplicate(GrowthSignalType.MISSION_COMPLETED, fatherId, sourceEntityId)) {
            log.debug("Duplicate MISSION_COMPLETED signal for father={}, mission={} — skipping", fatherId, sourceEntityId);
            return;
        }

        GrowthSignal signal = growthSignalService.recordSignal(
                GrowthSignalType.MISSION_COMPLETED, fatherId, sourceEntityId, "mission");

        growthScoreService.incrementScore(fatherId, signal.getPointsAwarded());

        int newTotalScore = growthScoreService.getTotalScore(fatherId);
        eventPublisher.publishEvent(new GrowthSignalRecordedEvent(
                fatherId, signal.getSignalType(), signal.getPointsAwarded(), sourceEntityId, newTotalScore));

        evaluateAndPromoteBelt(fatherId, newTotalScore);

        // Qualifying streak interaction per Requirement 12.2
        int newStreakDays = streakService.recordQualifyingInteraction(fatherId, Instant.now());
        checkStreakMilestone(fatherId, newStreakDays);

        log.info("Recorded MISSION_COMPLETED signal for father={}, points={}, newScore={}",
                fatherId, signal.getPointsAwarded(), newTotalScore);

        evaluateAchievementsAndMilestones(fatherId);
    }

    @Override
    @EventListener
    @Transactional
    public void onMissionReflected(MissionReflectedEvent event) {
        UUID fatherId = event.getFatherId();
        UUID sourceEntityId = event.getMissionId();

        log.debug("Processing MissionReflectedEvent for father={}, mission={}", fatherId, sourceEntityId);

        if (growthSignalService.isDuplicate(GrowthSignalType.MISSION_REFLECTED, fatherId, sourceEntityId)) {
            log.debug("Duplicate MISSION_REFLECTED signal for father={}, mission={} — skipping", fatherId, sourceEntityId);
            return;
        }

        GrowthSignal signal = growthSignalService.recordSignal(
                GrowthSignalType.MISSION_REFLECTED, fatherId, sourceEntityId, "mission");

        growthScoreService.incrementScore(fatherId, signal.getPointsAwarded());

        int newTotalScore = growthScoreService.getTotalScore(fatherId);
        eventPublisher.publishEvent(new GrowthSignalRecordedEvent(
                fatherId, signal.getSignalType(), signal.getPointsAwarded(), sourceEntityId, newTotalScore));

        evaluateAndPromoteBelt(fatherId, newTotalScore);

        // Qualifying streak interaction per Requirement 12.2
        int newStreakDays = streakService.recordQualifyingInteraction(fatherId, Instant.now());
        checkStreakMilestone(fatherId, newStreakDays);

        log.info("Recorded MISSION_REFLECTED signal for father={}, points={}, newScore={}",
                fatherId, signal.getPointsAwarded(), newTotalScore);

        evaluateAchievementsAndMilestones(fatherId);
    }

    @Override
    @EventListener
    @Transactional
    public void onGoalProgress(GoalProgressEvent event) {
        UUID fatherId = event.getFatherId();
        UUID sourceEntityId = event.getGoalId();

        int progressIncrease = event.getCurrentProgressPercent() - event.getPreviousProgressPercent();

        log.debug("Processing GoalProgressEvent for father={}, goal={}, increase={}%",
                fatherId, sourceEntityId, progressIncrease);

        if (progressIncrease < 10) {
            log.debug("Goal progress increase {}% < 10% threshold for father={}, goal={} — skipping",
                    progressIncrease, fatherId, sourceEntityId);
            return;
        }

        if (growthSignalService.isDuplicate(GrowthSignalType.GOAL_PROGRESS, fatherId, sourceEntityId)) {
            log.debug("Duplicate GOAL_PROGRESS signal for father={}, goal={} — skipping", fatherId, sourceEntityId);
            return;
        }

        GrowthSignal signal = growthSignalService.recordSignal(
                GrowthSignalType.GOAL_PROGRESS, fatherId, sourceEntityId, "goal");

        growthScoreService.incrementScore(fatherId, signal.getPointsAwarded());

        int newTotalScore = growthScoreService.getTotalScore(fatherId);
        eventPublisher.publishEvent(new GrowthSignalRecordedEvent(
                fatherId, signal.getSignalType(), signal.getPointsAwarded(), sourceEntityId, newTotalScore));

        evaluateAndPromoteBelt(fatherId, newTotalScore);

        log.info("Recorded GOAL_PROGRESS signal for father={}, increase={}%, points={}, newScore={}",
                fatherId, progressIncrease, signal.getPointsAwarded(), newTotalScore);

        evaluateAchievementsAndMilestones(fatherId);
    }

    @Override
    @EventListener
    @Transactional
    public void onGoalCompleted(GoalCompletedEvent event) {
        UUID fatherId = event.getFatherId();
        UUID sourceEntityId = event.getGoalId();

        log.debug("Processing GoalCompletedEvent for father={}, goal={}", fatherId, sourceEntityId);

        if (growthSignalService.isDuplicate(GrowthSignalType.GOAL_COMPLETED, fatherId, sourceEntityId)) {
            log.debug("Duplicate GOAL_COMPLETED signal for father={}, goal={} — skipping", fatherId, sourceEntityId);
            return;
        }

        GrowthSignal signal = growthSignalService.recordSignal(
                GrowthSignalType.GOAL_COMPLETED, fatherId, sourceEntityId, "goal");

        growthScoreService.incrementScore(fatherId, signal.getPointsAwarded());

        int newTotalScore = growthScoreService.getTotalScore(fatherId);
        eventPublisher.publishEvent(new GrowthSignalRecordedEvent(
                fatherId, signal.getSignalType(), signal.getPointsAwarded(), sourceEntityId, newTotalScore));

        evaluateAndPromoteBelt(fatherId, newTotalScore);

        log.info("Recorded GOAL_COMPLETED signal for father={}, points={}, newScore={}",
                fatherId, signal.getPointsAwarded(), newTotalScore);

        evaluateAchievementsAndMilestones(fatherId);
    }

    @Override
    @EventListener
    @Transactional
    public void onConversationCompleted(ConversationCompletedEvent event) {
        UUID fatherId = event.getFatherId();
        UUID sourceEntityId = event.getConversationId();

        log.debug("Processing ConversationCompletedEvent for father={}, conversation={}, quality={}, exchanges={}",
                fatherId, sourceEntityId, event.getQualityRating(), event.getExchangeCount());

        if (event.getQualityRating() <= 0.6 || event.getExchangeCount() <= 5) {
            log.debug("Conversation for father={} does not meet quality threshold (rating={}, exchanges={}) — skipping",
                    fatherId, event.getQualityRating(), event.getExchangeCount());
            return;
        }

        if (growthSignalService.isDuplicate(GrowthSignalType.MEANINGFUL_CONVERSATION, fatherId, sourceEntityId)) {
            log.debug("Duplicate MEANINGFUL_CONVERSATION signal for father={}, conversation={} — skipping",
                    fatherId, sourceEntityId);
            return;
        }

        GrowthSignal signal = growthSignalService.recordSignal(
                GrowthSignalType.MEANINGFUL_CONVERSATION, fatherId, sourceEntityId, "conversation");

        growthScoreService.incrementScore(fatherId, signal.getPointsAwarded());

        int newTotalScore = growthScoreService.getTotalScore(fatherId);
        eventPublisher.publishEvent(new GrowthSignalRecordedEvent(
                fatherId, signal.getSignalType(), signal.getPointsAwarded(), sourceEntityId, newTotalScore));

        evaluateAndPromoteBelt(fatherId, newTotalScore);

        // Qualifying streak interaction per Requirement 12.2
        int newStreakDays = streakService.recordQualifyingInteraction(fatherId, Instant.now());
        checkStreakMilestone(fatherId, newStreakDays);

        log.info("Recorded MEANINGFUL_CONVERSATION signal for father={}, points={}, newScore={}",
                fatherId, signal.getPointsAwarded(), newTotalScore);

        evaluateAchievementsAndMilestones(fatherId);
    }

    @Override
    @EventListener
    @Transactional
    public void onQualityTimeReported(QualityTimeReportedEvent event) {
        UUID fatherId = event.getFatherId();
        UUID sourceEntityId = event.getReportId();

        log.debug("Processing QualityTimeReportedEvent for father={}, report={}", fatherId, sourceEntityId);

        if (growthSignalService.isDuplicate(GrowthSignalType.QUALITY_TIME_REPORTED, fatherId, sourceEntityId)) {
            log.debug("Duplicate QUALITY_TIME_REPORTED signal for father={}, report={} — skipping",
                    fatherId, sourceEntityId);
            return;
        }

        GrowthSignal signal = growthSignalService.recordSignal(
                GrowthSignalType.QUALITY_TIME_REPORTED, fatherId, sourceEntityId, "activity_report");

        growthScoreService.incrementScore(fatherId, signal.getPointsAwarded());

        int newTotalScore = growthScoreService.getTotalScore(fatherId);
        eventPublisher.publishEvent(new GrowthSignalRecordedEvent(
                fatherId, signal.getSignalType(), signal.getPointsAwarded(), sourceEntityId, newTotalScore));

        evaluateAndPromoteBelt(fatherId, newTotalScore);

        // Qualifying streak interaction per Requirement 12.2
        int newStreakDays = streakService.recordQualifyingInteraction(fatherId, Instant.now());
        checkStreakMilestone(fatherId, newStreakDays);

        log.info("Recorded QUALITY_TIME_REPORTED signal for father={}, points={}, newScore={}",
                fatherId, signal.getPointsAwarded(), newTotalScore);

        evaluateAchievementsAndMilestones(fatherId);
    }

    @Override
    @EventListener
    @Transactional
    public void onPositiveActivityReported(PositiveActivityReportedEvent event) {
        UUID fatherId = event.getFatherId();
        UUID sourceEntityId = event.getReportId();

        log.debug("Processing PositiveActivityReportedEvent for father={}, report={}", fatherId, sourceEntityId);

        if (growthSignalService.isDuplicate(GrowthSignalType.POSITIVE_ACTIVITY, fatherId, sourceEntityId)) {
            log.debug("Duplicate POSITIVE_ACTIVITY signal for father={}, report={} — skipping",
                    fatherId, sourceEntityId);
            return;
        }

        GrowthSignal signal = growthSignalService.recordSignal(
                GrowthSignalType.POSITIVE_ACTIVITY, fatherId, sourceEntityId, "activity_report");

        growthScoreService.incrementScore(fatherId, signal.getPointsAwarded());

        int newTotalScore = growthScoreService.getTotalScore(fatherId);
        eventPublisher.publishEvent(new GrowthSignalRecordedEvent(
                fatherId, signal.getSignalType(), signal.getPointsAwarded(), sourceEntityId, newTotalScore));

        evaluateAndPromoteBelt(fatherId, newTotalScore);

        // Qualifying streak interaction per Requirement 12.2
        int newStreakDays = streakService.recordQualifyingInteraction(fatherId, Instant.now());
        checkStreakMilestone(fatherId, newStreakDays);

        log.info("Recorded POSITIVE_ACTIVITY signal for father={}, points={}, newScore={}",
                fatherId, signal.getPointsAwarded(), newTotalScore);

        evaluateAchievementsAndMilestones(fatherId);
    }

    @Override
    public void replaySignalsForFather(UUID fatherId) {
        log.info("Replaying signals for father={} — this is an administrative operation", fatherId);
        // Administrative recalculation: rebuild the score from the authoritative signal store.
        // Full implementation depends on having access to historical domain events.
        growthScoreService.rebuildScore(fatherId);
        log.info("Score rebuilt for father={} from authoritative signal store", fatherId);
    }

    /**
     * Evaluates whether the father qualifies for a belt promotion after a score change,
     * and performs the promotion if warranted.
     *
     * <p>Called after each signal recording + score increment. If evaluatePromotion returns
     * a new belt level, delegates to {@link BeltProgressionService#promoteBelt} which handles
     * updating the belt record and publishing the {@link com.dadcoach.workspace.event.BeltLevelUpEvent}.</p>
     *
     * @param fatherId      the father's unique identifier
     * @param newTotalScore the father's updated total score after signal recording
     */
    private void evaluateAndPromoteBelt(UUID fatherId, int newTotalScore) {
        Optional<BeltLevel> promotionResult = beltProgressionService.evaluatePromotion(fatherId, newTotalScore);
        promotionResult.ifPresent(newBelt -> {
            log.info("Belt promotion triggered for father={}: new belt={}, score={}",
                    fatherId, newBelt, newTotalScore);
            beltProgressionService.promoteBelt(fatherId, newBelt);
        });
    }

    /**
     * Checks whether the current streak days matches a milestone threshold
     * and, if so, records a STREAK_BONUS signal and publishes a StreakMilestoneEvent.
     *
     * <p>Milestone thresholds: 7, 14, 21, 30, 60, 90, 180, 365 days.</p>
     *
     * <p>Uses a synthetic sourceEntityId derived from the father ID and milestone to ensure
     * idempotency — only one bonus signal per milestone per streak is recorded. The duplicate
     * check ensures re-recording a qualifying interaction on the same milestone day does not
     * award duplicate bonus points.</p>
     *
     * @param fatherId          the father's unique identifier
     * @param currentStreakDays the father's current streak day count after the interaction
     */
    private void checkStreakMilestone(UUID fatherId, int currentStreakDays) {
        if (!STREAK_MILESTONES.contains(currentStreakDays)) {
            return;
        }

        GrowthSignalType milestoneSignalType = mapMilestoneToSignalType(currentStreakDays);
        if (milestoneSignalType == null) {
            log.warn("No signal type mapped for streak milestone {} — skipping", currentStreakDays);
            return;
        }

        // Synthetic sourceEntityId ensures one bonus per milestone per streak
        UUID sourceEntityId = UUID.nameUUIDFromBytes(
                ("streak_" + fatherId + "_" + currentStreakDays).getBytes(StandardCharsets.UTF_8));

        if (growthSignalService.isDuplicate(milestoneSignalType, fatherId, sourceEntityId)) {
            log.debug("Streak milestone {} already recorded for father={} — skipping bonus",
                    currentStreakDays, fatherId);
            return;
        }

        // Record the streak bonus signal
        GrowthSignal signal = growthSignalService.recordSignal(
                milestoneSignalType, fatherId, sourceEntityId, "streak");

        growthScoreService.incrementScore(fatherId, signal.getPointsAwarded());

        int newTotalScore = growthScoreService.getTotalScore(fatherId);
        eventPublisher.publishEvent(new GrowthSignalRecordedEvent(
                fatherId, signal.getSignalType(), signal.getPointsAwarded(), sourceEntityId, newTotalScore));

        // Evaluate belt promotion after streak bonus
        evaluateAndPromoteBelt(fatherId, newTotalScore);

        // Publish StreakMilestoneEvent
        int previousStreakDays = currentStreakDays - 1;
        eventPublisher.publishEvent(new StreakMilestoneEvent(fatherId, currentStreakDays, previousStreakDays));

        log.info("Streak milestone {} reached for father={}, bonus points={}, newScore={}",
                currentStreakDays, fatherId, signal.getPointsAwarded(), newTotalScore);
    }

    /**
     * Evaluates achievements and milestones for a father after each signal processing.
     *
     * <p>Called after belt evaluation. For each newly earned achievement, publishes an
     * {@link AchievementEarnedEvent}. For each newly reached milestone, publishes a
     * {@link MilestoneReachedEvent}.</p>
     *
     * @param fatherId the father's unique identifier
     */
    private void evaluateAchievementsAndMilestones(UUID fatherId) {
        // Evaluate achievements
        List<FatherAchievement> newAchievements = achievementEvaluator.evaluateAll(fatherId);
        for (FatherAchievement earned : newAchievements) {
            String name = achievementRepository.findById(earned.getAchievementId())
                    .map(Achievement::getName)
                    .orElse("Unknown");
            eventPublisher.publishEvent(new AchievementEarnedEvent(fatherId, earned.getAchievementId(), name));
            log.info("Published AchievementEarnedEvent: father={}, achievement={} ({})",
                    fatherId, earned.getAchievementId(), name);
        }

        // Evaluate milestones
        List<FatherMilestone> newMilestones = milestoneEvaluator.evaluateAll(fatherId);
        for (FatherMilestone reached : newMilestones) {
            String name = milestoneRepository.findById(reached.getMilestoneId())
                    .map(Milestone::getName)
                    .orElse("Unknown");
            eventPublisher.publishEvent(new MilestoneReachedEvent(fatherId, reached.getMilestoneId(), name));
            log.info("Published MilestoneReachedEvent: father={}, milestone={} ({})",
                    fatherId, reached.getMilestoneId(), name);
        }
    }

    /**
     * Maps a milestone day count to the corresponding STREAK_BONUS signal type.
     *
     * @param milestoneDays the milestone threshold (7, 14, 21, 30, 60, 90, 180, 365)
     * @return the corresponding GrowthSignalType, or null if not a valid milestone
     */
    private GrowthSignalType mapMilestoneToSignalType(int milestoneDays) {
        return switch (milestoneDays) {
            case 7 -> GrowthSignalType.STREAK_BONUS_7;
            case 14 -> GrowthSignalType.STREAK_BONUS_14;
            case 21 -> GrowthSignalType.STREAK_BONUS_21;
            case 30 -> GrowthSignalType.STREAK_BONUS_30;
            case 60 -> GrowthSignalType.STREAK_BONUS_60;
            case 90 -> GrowthSignalType.STREAK_BONUS_90;
            case 180 -> GrowthSignalType.STREAK_BONUS_180;
            case 365 -> GrowthSignalType.STREAK_BONUS_365;
            default -> null;
        };
    }
}
