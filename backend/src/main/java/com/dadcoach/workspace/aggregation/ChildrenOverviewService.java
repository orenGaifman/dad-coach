package com.dadcoach.workspace.aggregation;

import com.dadcoach.workspace.ResourceNotFoundException;
import com.dadcoach.workspace.dto.response.ChildSummaryResponse;
import com.dadcoach.workspace.dto.response.ChildrenOverviewResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/**
 * Aggregates children data with computed metrics for the workspace overview.
 *
 * <p>Owns NO state — reads from ChildDataService, MissionDataService, and GoalDataService.
 * Computes derived fields: age from birth_date, mission counts, recent mission,
 * and upcoming birthday indicator (within 7 days).</p>
 */
@Service
public class ChildrenOverviewService {

    private final ChildDataService childDataService;
    private final MissionDataService missionDataService;
    private final GoalDataService goalDataService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ChildrenOverviewService(ChildDataService childDataService,
                                    MissionDataService missionDataService,
                                    GoalDataService goalDataService) {
        this(childDataService, missionDataService, goalDataService, Clock.systemUTC());
    }

    @SuppressWarnings("unused") // used for testing with custom Clock
    public ChildrenOverviewService(ChildDataService childDataService,
                                    MissionDataService missionDataService,
                                    GoalDataService goalDataService,
                                    Clock clock) {
        this.childDataService = childDataService;
        this.missionDataService = missionDataService;
        this.goalDataService = goalDataService;
        this.clock = clock;
    }

    /**
     * Returns an overview of all children for a father, with key metrics per child.
     *
     * @param fatherId the father's unique identifier
     * @return the children overview response
     */
    public ChildrenOverviewResponse getChildrenOverview(UUID fatherId) {
        List<ChildReadModel> children = childDataService.getChildrenByFatherId(fatherId);

        List<ChildrenOverviewResponse.ChildItem> childItems = children.stream()
                .map(this::buildChildItem)
                .toList();

        return new ChildrenOverviewResponse(childItems, childItems.size());
    }

    /**
     * Returns a detailed summary for a specific child, including goals, mission history,
     * and upcoming birthday indicator.
     *
     * <p>Enforces ownership: if the child does not belong to the specified father,
     * throws ResourceNotFoundException (returns 404). This prevents information
     * leakage across fathers.</p>
     *
     * @param fatherId the father's unique identifier (for ownership verification)
     * @param childId  the child's unique identifier
     * @return the detailed child summary response
     * @throws ResourceNotFoundException if the child is not found or doesn't belong to this father
     */
    public ChildSummaryResponse getChildSummary(UUID fatherId, UUID childId) {
        // Enforce ownership — returns 404 for other father's children
        if (!childDataService.childBelongsToFather(fatherId, childId)) {
            throw new ResourceNotFoundException("child", childId);
        }

        ChildReadModel child = childDataService.getChild(childId)
                .orElseThrow(() -> new ResourceNotFoundException("child", childId));

        int age = computeAge(child.birthDate());
        int completedMissions = missionDataService.countCompletedMissionsByChildId(childId);
        List<GoalReadModel> goals = goalDataService.getGoalsByChildId(childId);
        List<MissionReadModel> missions = missionDataService.getMissionsByChildId(childId);
        MissionReadModel recentMission = missionDataService.getMostRecentMissionByChildId(childId).orElse(null);
        boolean upcomingBirthday = isUpcomingBirthday(child.birthDate());

        List<ChildSummaryResponse.GoalItem> goalItems = goals.stream()
                .map(g -> new ChildSummaryResponse.GoalItem(g.goalId(), g.title(), g.status()))
                .toList();

        List<ChildSummaryResponse.MissionHistoryItem> missionHistoryItems = missions.stream()
                .limit(10) // Show last 10 missions
                .map(m -> new ChildSummaryResponse.MissionHistoryItem(
                        m.missionId(), m.title(), m.status().name(), m.completedAt()))
                .toList();

        return ChildSummaryResponse.builder()
                .childId(child.childId())
                .name(child.name())
                .age(age)
                .birthDate(child.birthDate())
                .interests(child.interests())
                .activeGoalsCount(goals.size())
                .completedMissionsCount(completedMissions)
                .recentMission(recentMission != null ?
                        new ChildSummaryResponse.RecentMissionItem(
                                recentMission.missionId(),
                                recentMission.title(),
                                recentMission.status().name()) : null)
                .goals(goalItems)
                .missionHistory(missionHistoryItems)
                .upcomingBirthday(upcomingBirthday)
                .build();
    }

    private ChildrenOverviewResponse.ChildItem buildChildItem(ChildReadModel child) {
        int age = computeAge(child.birthDate());
        int completedMissions = missionDataService.countCompletedMissionsByChildId(child.childId());
        int activeGoals = goalDataService.getGoalsByChildId(child.childId()).size();
        MissionReadModel recentMission = missionDataService.getMostRecentMissionByChildId(child.childId()).orElse(null);

        ChildrenOverviewResponse.RecentMissionItem recentMissionItem = null;
        if (recentMission != null) {
            recentMissionItem = new ChildrenOverviewResponse.RecentMissionItem(
                    recentMission.missionId(),
                    recentMission.title(),
                    recentMission.status().name());
        }

        return new ChildrenOverviewResponse.ChildItem(
                child.childId(),
                child.name(),
                age,
                activeGoals,
                completedMissions,
                recentMissionItem,
                child.interests()
        );
    }

    /**
     * Computes age in years from birth date to today.
     *
     * @param birthDate the child's birth date (may be null)
     * @return age in years, or 0 if birth date is null
     */
    int computeAge(LocalDate birthDate) {
        if (birthDate == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(clock);
        return Period.between(birthDate, today).getYears();
    }

    /**
     * Checks whether the child's birthday is within the next 7 days.
     *
     * @param birthDate the child's birth date (may be null)
     * @return true if birthday is within 7 days
     */
    boolean isUpcomingBirthday(LocalDate birthDate) {
        if (birthDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        // Get this year's birthday
        LocalDate birthdayThisYear = birthDate.withYear(today.getYear());
        // If birthday already passed this year, check next year
        if (birthdayThisYear.isBefore(today)) {
            birthdayThisYear = birthDate.withYear(today.getYear() + 1);
        }
        long daysUntilBirthday = today.until(birthdayThisYear).getDays();
        return daysUntilBirthday >= 0 && daysUntilBirthday <= 7;
    }
}
