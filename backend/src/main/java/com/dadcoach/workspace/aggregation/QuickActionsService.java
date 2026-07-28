package com.dadcoach.workspace.aggregation;

import com.dadcoach.workspace.growth.streak.StreakService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for computing contextual quick action suggestions for the father.
 *
 * <p>Quick actions provide up to 5 priority-ordered, on-demand suggestions based on
 * the father's current state signals: active missions, unread notifications, streak
 * status, goal progress, and more.</p>
 *
 * <p>Actions are computed fresh on each request (no caching) to ensure real-time
 * relevance of suggestions.</p>
 *
 * <p>Requirements: 9.1, 9.2, 9.3, 9.4, 9.5</p>
 */
@Service
public class QuickActionsService {

    private static final Logger log = LoggerFactory.getLogger(QuickActionsService.class);
    private static final int MAX_ACTIONS = 5;

    private final MissionDataService missionDataService;
    private final NotificationDataService notificationDataService;
    private final GoalDataService goalDataService;
    private final StreakService streakService;

    public QuickActionsService(MissionDataService missionDataService,
                               NotificationDataService notificationDataService,
                               GoalDataService goalDataService,
                               StreakService streakService) {
        this.missionDataService = missionDataService;
        this.notificationDataService = notificationDataService;
        this.goalDataService = goalDataService;
        this.streakService = streakService;
    }

    /**
     * Computes up to 5 contextual quick action suggestions for the father.
     *
     * <p>Priority signals evaluated:</p>
     * <ul>
     *   <li>Active mission → "View Mission" (priority 9)</li>
     *   <li>No active mission → "Request New Mission" (priority 7)</li>
     *   <li>Unread notifications → "Check Notifications" (priority 6)</li>
     *   <li>Goal nearing completion → "Review Goal Progress" (priority 5)</li>
     *   <li>Streak at risk → "Log Today's Interaction" (priority 10)</li>
     * </ul>
     *
     * @param fatherId the father's unique identifier
     * @return priority-ordered list of quick action items (max 5)
     */
    public List<QuickActionItem> getQuickActions(UUID fatherId) {
        List<QuickActionItem> actions = new ArrayList<>();

        // Signal: Streak at risk → highest priority action
        try {
            if (streakService.isStreakAtRisk(fatherId)) {
                actions.add(new QuickActionItem(
                        UUID.randomUUID(),
                        "LOG_INTERACTION",
                        "Log Today's Interaction",
                        "Your streak is at risk! Log an interaction to keep it alive.",
                        10,
                        Map.of("reason", "streak_at_risk")
                ));
            }
        } catch (Exception e) {
            log.debug("Failed to check streak risk for father {}: {}", fatherId, e.getMessage());
        }

        // Signal: Active mission
        try {
            Optional<MissionReadModel> activeMission = missionDataService.getActiveMission(fatherId);
            if (activeMission.isPresent()) {
                MissionReadModel mission = activeMission.get();
                actions.add(new QuickActionItem(
                        UUID.randomUUID(),
                        "VIEW_MISSION",
                        "View Mission",
                        "Continue your active mission: " + mission.title(),
                        9,
                        Map.of("mission_id", mission.missionId().toString())
                ));
            } else {
                actions.add(new QuickActionItem(
                        UUID.randomUUID(),
                        "REQUEST_MISSION",
                        "Request New Mission",
                        "Ready for a new challenge? Request a mission from your coach.",
                        7,
                        Map.of()
                ));
            }
        } catch (Exception e) {
            log.debug("Failed to check missions for father {}: {}", fatherId, e.getMessage());
        }

        // Signal: Unread notifications
        try {
            int unreadCount = notificationDataService.getUnreadCount(fatherId);
            if (unreadCount > 0) {
                actions.add(new QuickActionItem(
                        UUID.randomUUID(),
                        "CHECK_NOTIFICATIONS",
                        "Check Notifications",
                        "You have " + unreadCount + " unread notification" + (unreadCount > 1 ? "s" : "") + ".",
                        6,
                        Map.of("unread_count", String.valueOf(unreadCount))
                ));
            }
        } catch (Exception e) {
            log.debug("Failed to check notifications for father {}: {}", fatherId, e.getMessage());
        }

        // Signal: Goal nearing completion (>= 80% progress)
        try {
            List<GoalReadModel> activeGoals = goalDataService.getActiveGoalsByFatherId(fatherId);
            for (GoalReadModel goal : activeGoals) {
                if (goal.estimatedMissions() > 0) {
                    double progress = (double) goal.completedMissions() / goal.estimatedMissions() * 100;
                    if (progress >= 80.0 && progress < 100.0) {
                        actions.add(new QuickActionItem(
                                UUID.randomUUID(),
                                "REVIEW_GOAL_PROGRESS",
                                "Review Goal Progress",
                                "Your goal '" + goal.title() + "' is almost complete!",
                                5,
                                Map.of("goal_id", goal.goalId().toString(),
                                        "progress_percentage", String.valueOf((int) progress))
                        ));
                        break; // Only suggest one goal
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to check goals for father {}: {}", fatherId, e.getMessage());
        }

        // Sort by priority descending, limit to MAX_ACTIONS
        actions.sort(Comparator.comparingInt(QuickActionItem::priority).reversed());

        if (actions.size() > MAX_ACTIONS) {
            return actions.subList(0, MAX_ACTIONS);
        }

        return actions;
    }

    /**
     * Represents a single quick action suggestion.
     *
     * @param actionId       unique identifier for this action instance
     * @param actionType     the type/category of action
     * @param title          display title
     * @param description    display description
     * @param priority       priority weight (1-10, higher = more important)
     * @param actionMetadata additional context metadata for the client
     */
    public record QuickActionItem(
            UUID actionId,
            String actionType,
            String title,
            String description,
            int priority,
            Map<String, String> actionMetadata
    ) {}
}
