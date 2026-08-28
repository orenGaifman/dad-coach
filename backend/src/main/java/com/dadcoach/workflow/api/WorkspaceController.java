package com.dadcoach.workflow.api;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.dto.WorkspaceSummaryDto;
import com.dadcoach.workflow.dto.WorkspaceSummaryDto.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for workspace summary endpoint.
 * 
 * <p>Provides the dashboard data for WEB-SPEC-008 (Father Workspace) via the
 * /api/v1/workspace/summary endpoint. This endpoint computes dashboard metrics
 * in real-time from Quality Time completion records.</p>
 * 
 * <p>Belt thresholds (SACRED - do NOT modify):</p>
 * <ul>
 *   <li>WHITE: 0-2 Quality Times</li>
 *   <li>YELLOW: 3-9 Quality Times</li>
 *   <li>ORANGE: 10-24 Quality Times</li>
 *   <li>GREEN: 25-49 Quality Times</li>
 *   <li>BLUE: 50-99 Quality Times</li>
 *   <li>BROWN: 100-199 Quality Times</li>
 *   <li>BLACK: 200+ Quality Times</li>
 * </ul>
 * 
 * @see WorkspaceSummaryDto
 */
@RestController
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);
    
    private static final int RECENT_QUALITY_TIMES_LIMIT = 5;
    private static final int RECENT_ACHIEVEMENTS_LIMIT = 5;
    private static final int DEFAULT_WEEKLY_GOAL_MINUTES = 30;

    private final FatherRepository fatherRepository;
    private final QualityTimeRepository qualityTimeRepository;
    private final ChildRepository childRepository;

    public WorkspaceController(
            FatherRepository fatherRepository,
            QualityTimeRepository qualityTimeRepository,
            ChildRepository childRepository) {
        this.fatherRepository = fatherRepository;
        this.qualityTimeRepository = qualityTimeRepository;
        this.childRepository = childRepository;
    }

    /**
     * GET /api/v1/workspace/summary — Retrieve dashboard data for the authenticated father.
     * 
     * <p>Returns a complete dashboard overview including:</p>
     * <ul>
     *   <li>Father display name and workflow state</li>
     *   <li>Belt level and progress to next belt</li>
     *   <li>Streak information (current and longest)</li>
     *   <li>Weekly goal progress</li>
     *   <li>Next scheduled Quality Time</li>
     *   <li>Recent completed Quality Times</li>
     *   <li>Recent achievements</li>
     *   <li>Next milestone</li>
     * </ul>
     * 
     * @param actor the authenticated actor context (injected via @AuthActor)
     * @return the workspace summary DTO with all dashboard metrics
     */
    @GetMapping("/api/v1/workspace/summary")
    public ResponseEntity<WorkspaceSummaryDto> getWorkspaceSummary(@AuthActor ActorContext actor) {
        log.debug("Loading workspace summary for actor: {}", actor.getActorId());
        
        Father father = findFatherByActorId(actor.getActorId());
        
        // Build the workspace summary with all dashboard metrics
        WorkspaceSummaryDto summary = buildWorkspaceSummary(father);
        
        log.debug("Workspace summary loaded successfully for father: {}", father.getId());
        return ResponseEntity.ok(summary);
    }

    /**
     * Builds the complete workspace summary DTO from the father's data.
     */
    private WorkspaceSummaryDto buildWorkspaceSummary(Father father) {
        Long fatherId = father.getId();
        
        // Get belt and completion count
        Belt currentBelt = father.getCurrentBelt() != null ? father.getCurrentBelt() : Belt.WHITE;
        int totalCompleted = father.getTotalQualityTimesCompleted();
        
        // Compute belt progress
        BeltProgressDto beltProgress = BeltProgressDto.from(totalCompleted, currentBelt);
        
        // Compute weekly goal progress
        WeeklyGoalProgressDto weeklyGoalProgress = computeWeeklyGoalProgress(father);
        
        // Get next scheduled quality time
        QualityTimeSummaryDto nextQualityTime = getNextScheduledQualityTime(fatherId);
        
        // Get recent completed quality times
        List<RecentQualityTimeDto> recentQualityTimes = getRecentCompletedQualityTimes(fatherId);
        
        // Get recent achievements
        List<AchievementDto> recentAchievements = getRecentAchievements(father);
        
        // Compute next milestone
        MilestoneDto nextMilestone = MilestoneDto.forNextBelt(currentBelt, totalCompleted);
        
        // Weekly streak info
        int currentStreakWeeks = father.getCurrentStreakWeeks() != null ? father.getCurrentStreakWeeks() : 0;
        int longestStreakWeeks = father.getLongestStreakWeeks() != null ? father.getLongestStreakWeeks() : 0;
        int weeksToBlackBelt = calculateWeeksToBlackBelt(currentBelt);
        boolean programCompleted = currentBelt == Belt.BLACK;
        
        return WorkspaceSummaryDto.builder()
                .fatherDisplayName(father.getDisplayName())
                .currentWorkflowState(father.getCurrentWorkflowState())
                .currentBelt(currentBelt)
                .beltProgress(beltProgress)
                .currentStreak(father.getQualityTimeStreak())
                .longestStreak(father.getQualityTimeLongestStreak())
                .totalQualityTimesCompleted(totalCompleted)
                .weeklyGoalProgress(weeklyGoalProgress)
                .nextQualityTime(nextQualityTime)
                .recentQualityTimes(recentQualityTimes)
                .recentAchievements(recentAchievements)
                .nextMilestone(nextMilestone)
                .currentStreakWeeks(currentStreakWeeks)
                .longestStreakWeeks(longestStreakWeeks)
                .weeksToBlackBelt(weeksToBlackBelt)
                .programCompleted(programCompleted)
                .build();
    }

    /**
     * Calculates weeks remaining to BLACK belt.
     */
    private int calculateWeeksToBlackBelt(Belt currentBelt) {
        int weeks = 0;
        Belt belt = currentBelt;
        while (belt != null && belt != Belt.BLACK) {
            belt = belt.getNextBelt();
            weeks++;
        }
        return weeks;
    }

    /**
     * Computes weekly goal progress by calculating hours of Quality Time completed this week.
     */
    private WeeklyGoalProgressDto computeWeeklyGoalProgress(Father father) {
        Long fatherId = father.getId();
        String timezone = father.getTimezone() != null ? father.getTimezone() : "Asia/Jerusalem";
        
        // Get the start of the current week (Monday) in father's timezone
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        
        Instant weekStartInstant = weekStart.toInstant();
        Instant nowInstant = Instant.now();
        
        // Find all completed quality times this week
        List<QualityTime> allQualityTimes = qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(fatherId);
        List<QualityTime> completedThisWeek = allQualityTimes.stream()
                .filter(qt -> qt.getStatus() == QualityTimeStatus.COMPLETED)
                .filter(qt -> qt.getCompletedAt() != null)
                .filter(qt -> qt.getCompletedAt().isAfter(weekStartInstant))
                .filter(qt -> qt.getCompletedAt().isBefore(nowInstant) || qt.getCompletedAt().equals(nowInstant))
                .collect(Collectors.toList());
        
        // Calculate total minutes completed this week
        long totalMinutesCompleted = completedThisWeek.stream()
                .mapToLong(qt -> {
                    Duration duration = Duration.between(qt.getScheduledStart(), qt.getScheduledEnd());
                    return duration.toMinutes();
                })
                .sum();
        
        // Convert to hours
        double completedHours = totalMinutesCompleted / 60.0;
        
        // Get goal hours (convert minutes to hours)
        int goalMinutes = father.getWeeklyGoalMinutes() != null 
                ? father.getWeeklyGoalMinutes() 
                : DEFAULT_WEEKLY_GOAL_MINUTES;
        double goalHours = goalMinutes / 60.0;
        
        return WeeklyGoalProgressDto.from(completedHours, goalHours);
    }

    /**
     * Gets the next scheduled Quality Time event for the father.
     */
    private QualityTimeSummaryDto getNextScheduledQualityTime(Long fatherId) {
        Optional<QualityTime> nextQualityTime = qualityTimeRepository.findFirstByFatherIdAndStatusOrderByScheduledStartAsc(fatherId, QualityTimeStatus.SCHEDULED);
        
        if (nextQualityTime.isEmpty()) {
            return null;
        }
        
        QualityTime qt = nextQualityTime.get();
        String childName = getChildName(qt.getChildId());
        
        return new QualityTimeSummaryDto(
                qt.getId(),
                childName,
                qt.getScheduledStart(),
                qt.getScheduledEnd(),
                qt.getStatus()
        );
    }

    /**
     * Gets recent completed Quality Times for the father.
     */
    private List<RecentQualityTimeDto> getRecentCompletedQualityTimes(Long fatherId) {
        List<QualityTime> allQualityTimes = qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(fatherId);
        
        return allQualityTimes.stream()
                .filter(qt -> qt.getStatus() == QualityTimeStatus.COMPLETED)
                .filter(qt -> qt.getCompletedAt() != null)
                .sorted(Comparator.comparing(QualityTime::getCompletedAt).reversed())
                .limit(RECENT_QUALITY_TIMES_LIMIT)
                .map(qt -> {
                    String childName = getChildName(qt.getChildId());
                    int durationMinutes = (int) Duration.between(
                            qt.getScheduledStart(), 
                            qt.getScheduledEnd()
                    ).toMinutes();
                    
                    return new RecentQualityTimeDto(
                            qt.getId(),
                            childName,
                            qt.getCompletedAt(),
                            durationMinutes
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets recent achievements for the father.
     * 
     * <p>Currently generates achievements based on:</p>
     * <ul>
     *   <li>First Quality Time completion</li>
     *   <li>Belt achievements (earned when reaching each belt level)</li>
     *   <li>Streak achievements (5+, 10+, etc.)</li>
     * </ul>
     */
    private List<AchievementDto> getRecentAchievements(Father father) {
        List<AchievementDto> achievements = new ArrayList<>();
        
        // First Quality Time achievement
        if (father.getTotalQualityTimesCompleted() > 0) {
            achievements.add(new AchievementDto(
                    "first-quality-time",
                    "First Step",
                    father.getCreatedAt() // Placeholder - would need actual earned date
            ));
        }
        
        // Belt achievement
        Belt currentBelt = father.getCurrentBelt() != null ? father.getCurrentBelt() : Belt.WHITE;
        if (currentBelt != Belt.WHITE) {
            achievements.add(new AchievementDto(
                    "belt-" + currentBelt.name().toLowerCase(),
                    currentBelt.getDisplayName() + " Earned",
                    father.getCreatedAt() // Placeholder - would need actual earned date
            ));
        }
        
        // Streak achievements
        int longestStreak = father.getQualityTimeLongestStreak();
        if (longestStreak >= 5) {
            achievements.add(new AchievementDto(
                    "streak-5",
                    "5 Day Streak",
                    father.getCreatedAt() // Placeholder
            ));
        }
        if (longestStreak >= 10) {
            achievements.add(new AchievementDto(
                    "streak-10",
                    "10 Day Streak",
                    father.getCreatedAt() // Placeholder
            ));
        }
        if (longestStreak >= 25) {
            achievements.add(new AchievementDto(
                    "streak-25",
                    "25 Day Streak",
                    father.getCreatedAt() // Placeholder
            ));
        }
        
        // Completion milestones
        int totalCompleted = father.getTotalQualityTimesCompleted();
        if (totalCompleted >= 10) {
            achievements.add(new AchievementDto(
                    "completion-10",
                    "10 Quality Times",
                    father.getCreatedAt() // Placeholder
            ));
        }
        if (totalCompleted >= 50) {
            achievements.add(new AchievementDto(
                    "completion-50",
                    "50 Quality Times",
                    father.getCreatedAt() // Placeholder
            ));
        }
        if (totalCompleted >= 100) {
            achievements.add(new AchievementDto(
                    "completion-100",
                    "Century Club",
                    father.getCreatedAt() // Placeholder
            ));
        }
        
        // Sort by earned_at descending (newest first) and limit to recent achievements
        return achievements.stream()
                .sorted(Comparator.comparing(AchievementDto::earnedAt).reversed())
                .limit(RECENT_ACHIEVEMENTS_LIMIT)
                .collect(Collectors.toList());
    }

    /**
     * Gets a child's name by ID.
     */
    private String getChildName(Long childId) {
        if (childId == null) {
            return "Unknown";
        }
        return childRepository.findById(childId)
                .map(Child::getName)
                .orElse("Unknown");
    }

    /**
     * Resolves a Father entity from the actor's UUID.
     * <p>
     * The UUID's least significant bits correspond to the internal Long ID.
     */
    private Father findFatherByActorId(UUID actorId) {
        long internalId = actorId.getLeastSignificantBits();
        return fatherRepository.findById(internalId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", actorId));
    }
}
