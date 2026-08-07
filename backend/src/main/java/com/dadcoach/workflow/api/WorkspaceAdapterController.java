package com.dadcoach.workflow.api;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.conversation.Conversation;
import com.dadcoach.domain.conversation.ConversationRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.goal.Goal;
import com.dadcoach.domain.goal.GoalRepository;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;
import com.dadcoach.workflow.Belt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter controller providing workspace endpoints for web frontend compatibility.
 * 
 * <p>These endpoints map the /api/v1/workspace/* paths expected by the web frontend
 * to the underlying data from the domain entities. This allows the frontend to work
 * without changes while we've simplified the backend architecture.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceAdapterController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceAdapterController.class);

    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final GoalRepository goalRepository;
    private final MissionRepository missionRepository;
    private final ConversationRepository conversationRepository;

    public WorkspaceAdapterController(
            FatherRepository fatherRepository,
            ChildRepository childRepository,
            GoalRepository goalRepository,
            MissionRepository missionRepository,
            ConversationRepository conversationRepository) {
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.goalRepository = goalRepository;
        this.missionRepository = missionRepository;
        this.conversationRepository = conversationRepository;
    }

    // ─── Profile Endpoints ────────────────────────────────────────────────

    /**
     * GET /api/v1/workspace/profile - Get father profile for dashboard.
     */
    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        LocalTime coachingTime = father.getPreferredCoachingTime();
        
        ProfileResponse response = new ProfileResponse(
                father.getId(),
                father.getDisplayName(),
                father.getPhone(),
                father.getLocale() != null ? father.getLocale() : "he",
                father.getTimezone() != null ? father.getTimezone() : "Asia/Jerusalem",
                coachingTime != null ? coachingTime.toString() : "08:00",
                father.hasGoogleCalendarConfigured(),
                father.getCreatedAt()
        );
        
        return ResponseEntity.ok(response);
    }

    // ─── Children Endpoints ───────────────────────────────────────────────

    /**
     * GET /api/v1/workspace/children - List all children for the father.
     */
    @GetMapping("/children")
    public ResponseEntity<ChildrenResponse> getChildren(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        List<Child> children = childRepository.findByFatherId(father.getId());
        
        List<ChildSummary> summaries = children.stream()
                .filter(child -> "ACTIVE".equals(child.getStatus()))
                .map(this::toChildSummary)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(new ChildrenResponse(summaries));
    }

    /**
     * GET /api/v1/workspace/children/{childId}/summary - Get detailed child summary.
     */
    @GetMapping("/children/{childId}/summary")
    public ResponseEntity<ChildDetailResponse> getChildSummary(
            @AuthActor ActorContext actor,
            @PathVariable Long childId) {
        Father father = findFatherByActorId(actor.getActorId());
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        
        // Verify ownership
        if (!child.getFatherId().equals(father.getId())) {
            throw new ResourceNotFoundException("Child", childId);
        }
        
        return ResponseEntity.ok(toChildDetail(child));
    }

    // ─── Goals Endpoints ──────────────────────────────────────────────────

    /**
     * GET /api/v1/workspace/goals - List all goals for the father.
     */
    @GetMapping("/goals")
    public ResponseEntity<GoalsResponse> getGoals(
            @AuthActor ActorContext actor,
            @RequestParam(required = false) String status) {
        Father father = findFatherByActorId(actor.getActorId());
        List<Goal> goals = goalRepository.findByFatherId(father.getId());
        
        // Apply filters
        if (status != null) {
            goals = goals.stream()
                    .filter(g -> status.equalsIgnoreCase(g.getStatus()))
                    .collect(Collectors.toList());
        }
        
        List<GoalSummary> summaries = goals.stream()
                .map(this::toGoalSummary)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(new GoalsResponse(summaries));
    }

    /**
     * GET /api/v1/workspace/goals/{goalId}/progress - Get goal progress details.
     */
    @GetMapping("/goals/{goalId}/progress")
    public ResponseEntity<GoalProgressResponse> getGoalProgress(
            @AuthActor ActorContext actor,
            @PathVariable Long goalId) {
        Father father = findFatherByActorId(actor.getActorId());
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
        
        // Verify ownership
        if (!goal.getFatherId().equals(father.getId())) {
            throw new ResourceNotFoundException("Goal", goalId);
        }
        
        return ResponseEntity.ok(toGoalProgress(goal));
    }

    // ─── Missions Endpoints ───────────────────────────────────────────────

    /**
     * GET /api/v1/workspace/missions/active - Get active missions for the father.
     */
    @GetMapping("/missions/active")
    public ResponseEntity<ActiveMissionResponse> getActiveMission(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        List<Mission> activeMissions = missionRepository.findActiveMissionsByFatherId(father.getId());
        
        if (activeMissions.isEmpty()) {
            return ResponseEntity.ok(new ActiveMissionResponse(null));
        }
        
        // Return the first active mission (most recently assigned)
        Mission mission = activeMissions.get(0);
        return ResponseEntity.ok(new ActiveMissionResponse(toMissionSummary(mission)));
    }

    // ─── Conversations Endpoints ──────────────────────────────────────────

    /**
     * GET /api/v1/workspace/conversations - List recent conversations.
     */
    @GetMapping("/conversations")
    public ResponseEntity<ConversationsResponse> getConversations(
            @AuthActor ActorContext actor,
            @RequestParam(defaultValue = "10") int limit) {
        Father father = findFatherByActorId(actor.getActorId());
        List<Conversation> conversations = conversationRepository.findByFatherIdOrderByCreatedAtDesc(father.getId());
        
        List<ConversationSummary> summaries = conversations.stream()
                .limit(limit)
                .map(this::toConversationSummary)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(new ConversationsResponse(summaries, conversations.size() > limit));
    }

    /**
     * GET /api/v1/workspace/conversations/{conversationId} - Get conversation details.
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationDetailResponse> getConversationDetail(
            @AuthActor ActorContext actor,
            @PathVariable Long conversationId) {
        Father father = findFatherByActorId(actor.getActorId());
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        
        // Verify ownership
        if (!conversation.getFatherId().equals(father.getId())) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }
        
        return ResponseEntity.ok(toConversationDetail(conversation));
    }

    // ─── Growth Endpoints (simplified) ────────────────────────────────────

    /**
     * GET /api/v1/workspace/growth/belt - Get belt progression data.
     */
    @GetMapping("/growth/belt")
    public ResponseEntity<BeltProgressionResponse> getBeltProgression(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        Belt currentBelt = father.getCurrentBelt() != null ? father.getCurrentBelt() : Belt.WHITE;
        int totalCompleted = father.getTotalQualityTimesCompleted();
        
        Belt nextBelt = currentBelt.getNextBelt();
        int progressPercent = 0;
        int qualityTimesToNext = 0;
        
        if (nextBelt != null) {
            int currentMin = currentBelt.getMinCompletions();
            int nextMin = nextBelt.getMinCompletions();
            int range = nextMin - currentMin;
            int progress = totalCompleted - currentMin;
            progressPercent = range > 0 ? Math.min(100, (progress * 100) / range) : 100;
            qualityTimesToNext = Math.max(0, nextMin - totalCompleted);
        } else {
            progressPercent = 100;
        }
        
        return ResponseEntity.ok(new BeltProgressionResponse(
                currentBelt.name(),
                currentBelt.getDisplayName(),
                nextBelt != null ? nextBelt.name() : null,
                nextBelt != null ? nextBelt.getDisplayName() : null,
                progressPercent,
                qualityTimesToNext,
                totalCompleted
        ));
    }

    /**
     * GET /api/v1/workspace/growth/streak - Get streak data.
     */
    @GetMapping("/growth/streak")
    public ResponseEntity<StreakResponse> getStreak(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        
        return ResponseEntity.ok(new StreakResponse(
                father.getCoachingStreak(),
                father.getLongestStreak(),
                father.getLastInteractionAt()
        ));
    }

    /**
     * GET /api/v1/workspace/growth/achievements - Get achievements (simplified).
     */
    @GetMapping("/growth/achievements")
    public ResponseEntity<AchievementsResponse> getAchievements(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        List<AchievementItem> achievements = new ArrayList<>();
        
        // Generate achievements based on father's progress
        if (father.getTotalQualityTimesCompleted() > 0) {
            achievements.add(new AchievementItem("first-quality-time", "First Step", 
                    "Completed your first Quality Time", father.getCreatedAt()));
        }
        
        Belt currentBelt = father.getCurrentBelt();
        if (currentBelt != null && currentBelt != Belt.WHITE) {
            achievements.add(new AchievementItem("belt-" + currentBelt.name().toLowerCase(),
                    currentBelt.getDisplayName() + " Belt", 
                    "Reached " + currentBelt.getDisplayName() + " belt level",
                    father.getCreatedAt()));
        }
        
        int streak = father.getLongestStreak();
        if (streak >= 5) {
            achievements.add(new AchievementItem("streak-5", "5 Day Streak",
                    "Maintained a 5-day quality time streak", father.getCreatedAt()));
        }
        
        return ResponseEntity.ok(new AchievementsResponse(achievements));
    }

    /**
     * GET /api/v1/workspace/growth/score - Get growth score (simplified).
     */
    @GetMapping("/growth/score")
    public ResponseEntity<GrowthScoreResponse> getGrowthScore(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        
        // Simple score calculation based on completions and streak
        int baseScore = father.getTotalQualityTimesCompleted() * 10;
        int streakBonus = father.getCoachingStreak() * 5;
        int totalScore = baseScore + streakBonus;
        
        return ResponseEntity.ok(new GrowthScoreResponse(totalScore, baseScore, streakBonus));
    }

    /**
     * GET /api/v1/workspace/growth/celebrations - Get pending celebrations.
     */
    @GetMapping("/growth/celebrations")
    public ResponseEntity<CelebrationsResponse> getCelebrations(@AuthActor ActorContext actor) {
        // Return empty list - celebrations are now part of the workflow engine
        return ResponseEntity.ok(new CelebrationsResponse(List.of()));
    }

    /**
     * POST /api/v1/workspace/growth/celebrations/mark-displayed - Mark celebrations as displayed.
     */
    @PostMapping("/growth/celebrations/mark-displayed")
    public ResponseEntity<Void> markCelebrationsDisplayed(
            @AuthActor ActorContext actor,
            @RequestBody MarkCelebrationsDisplayedRequest request) {
        // No-op since celebrations are now part of workflow
        return ResponseEntity.ok().build();
    }

    // ─── Notifications Endpoints (stub) ───────────────────────────────────

    /**
     * GET /api/v1/workspace/notifications - Get notifications list.
     */
    @GetMapping("/notifications")
    public ResponseEntity<NotificationsResponse> getNotifications(@AuthActor ActorContext actor) {
        // Return empty list - notifications to be implemented later
        return ResponseEntity.ok(new NotificationsResponse(List.of(), 0));
    }

    /**
     * POST /api/v1/workspace/notifications/mark-read - Mark notifications as read.
     */
    @PostMapping("/notifications/mark-read")
    public ResponseEntity<Void> markNotificationsRead(
            @AuthActor ActorContext actor,
            @RequestBody MarkNotificationsReadRequest request) {
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/workspace/notifications/mark-all-read - Mark all notifications as read.
     */
    @PostMapping("/notifications/mark-all-read")
    public ResponseEntity<Void> markAllNotificationsRead(@AuthActor ActorContext actor) {
        return ResponseEntity.ok().build();
    }

    // ─── Activity Endpoints (stub) ────────────────────────────────────────

    /**
     * POST /api/v1/workspace/activity/quality-time - Report quality time activity.
     */
    @PostMapping("/activity/quality-time")
    public ResponseEntity<ActivityResponse> reportQualityTime(
            @AuthActor ActorContext actor,
            @RequestBody QualityTimeActivityRequest request) {
        // This would be handled by the quality-time endpoints
        return ResponseEntity.ok(new ActivityResponse(true, "Quality time reported"));
    }

    /**
     * POST /api/v1/workspace/activity/positive - Report positive activity.
     */
    @PostMapping("/activity/positive")
    public ResponseEntity<ActivityResponse> reportPositiveActivity(
            @AuthActor ActorContext actor,
            @RequestBody PositiveActivityRequest request) {
        // Stub - positive activities could be logged as memories
        return ResponseEntity.ok(new ActivityResponse(true, "Activity reported"));
    }

    // ─── Helper Methods ───────────────────────────────────────────────────

    private Father findFatherByActorId(UUID actorId) {
        long internalId = actorId.getLeastSignificantBits();
        return fatherRepository.findById(internalId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", actorId));
    }

    private ChildSummary toChildSummary(Child child) {
        return new ChildSummary(
                child.getId(),
                child.getName(),
                child.getBirthDate(),
                child.getAge(),
                child.getGender(),
                child.getInterests()
        );
    }

    private ChildDetailResponse toChildDetail(Child child) {
        return new ChildDetailResponse(
                child.getId(),
                child.getName(),
                child.getBirthDate(),
                child.getAge(),
                child.getGender(),
                child.getInterests(),
                child.getCreatedAt()
        );
    }

    private GoalSummary toGoalSummary(Goal goal) {
        return new GoalSummary(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getStatus(),
                goal.getCategory() != null ? goal.getCategory().name() : null,
                goal.getProgressPercentage()
        );
    }

    private GoalProgressResponse toGoalProgress(Goal goal) {
        return new GoalProgressResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getStatus(),
                goal.getCategory() != null ? goal.getCategory().name() : null,
                goal.getProgressPercentage(),
                goal.getEstimatedTotalMissions(),
                goal.getCompletedRelatedMissions(),
                goal.getCreatedAt(),
                goal.getCompletedAt()
        );
    }

    private MissionSummary toMissionSummary(Mission mission) {
        return new MissionSummary(
                mission.getId(),
                mission.getTitle(),
                mission.getDescription(),
                mission.getStatus() != null ? mission.getStatus().name() : null,
                mission.getExpiresAt(),
                mission.getChildId()
        );
    }

    private ConversationSummary toConversationSummary(Conversation conv) {
        return new ConversationSummary(
                conv.getId(),
                conv.getType() != null ? conv.getType().name() : "UNKNOWN",
                conv.getStatus() != null ? conv.getStatus().name() : "UNKNOWN",
                conv.getCreatedAt(),
                conv.getCreatedAt() // Use createdAt as proxy for lastMessageAt
        );
    }

    private ConversationDetailResponse toConversationDetail(Conversation conv) {
        return new ConversationDetailResponse(
                conv.getId(),
                conv.getType() != null ? conv.getType().name() : "UNKNOWN",
                conv.getStatus() != null ? conv.getStatus().name() : "UNKNOWN",
                conv.getSummary(),
                conv.getObjective(),
                conv.getMessageCount(),
                conv.getCreatedAt(),
                conv.getCompletedAt()
        );
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────

    public record ProfileResponse(Long id, String displayName, String phone, String locale,
                                   String timezone, String preferredCoachingTime,
                                   boolean googleCalendarConnected, Instant createdAt) {}

    public record ChildrenResponse(List<ChildSummary> children) {}
    public record ChildSummary(Long id, String name, LocalDate birthDate, 
                                Integer age, String gender, List<String> interests) {}
    public record ChildDetailResponse(Long id, String name, LocalDate birthDate,
                                       Integer age, String gender, List<String> interests, Instant createdAt) {}

    public record GoalsResponse(List<GoalSummary> goals) {}
    public record GoalSummary(Long id, String title, String description, String status,
                               String category, int progressPercentage) {}
    public record GoalProgressResponse(Long id, String title, String description, String status,
                                        String category, int progressPercentage,
                                        int estimatedTotalMissions, int completedRelatedMissions,
                                        Instant createdAt, Instant completedAt) {}

    public record ActiveMissionResponse(MissionSummary mission) {}
    public record MissionSummary(Long id, String title, String description, String status,
                                  Instant expiresAt, Long childId) {}

    public record ConversationsResponse(List<ConversationSummary> conversations, boolean hasMore) {}
    public record ConversationSummary(Long id, String type, String status, 
                                       Instant createdAt, Instant lastActivityAt) {}
    public record ConversationDetailResponse(Long id, String type, String status,
                                              String summary, String objective, int messageCount,
                                              Instant createdAt, Instant completedAt) {}

    public record BeltProgressionResponse(String currentBelt, String currentBeltName,
                                           String nextBelt, String nextBeltName,
                                           int progressPercent, int qualityTimesToNext,
                                           int totalCompleted) {}

    public record StreakResponse(int currentStreak, int longestStreak, Instant lastInteractionAt) {}

    public record AchievementsResponse(List<AchievementItem> achievements) {}
    public record AchievementItem(String id, String title, String description, Instant earnedAt) {}

    public record GrowthScoreResponse(int totalScore, int baseScore, int streakBonus) {}

    public record CelebrationsResponse(List<Object> celebrations) {}
    public record MarkCelebrationsDisplayedRequest(List<Long> celebrationIds) {}

    public record NotificationsResponse(List<Object> notifications, int unreadCount) {}
    public record MarkNotificationsReadRequest(List<Long> notificationIds) {}

    public record ActivityResponse(boolean success, String message) {}
    public record QualityTimeActivityRequest(Long childId, Integer durationMinutes, String notes) {}
    public record PositiveActivityRequest(Long childId, String activityType, String notes) {}
}
