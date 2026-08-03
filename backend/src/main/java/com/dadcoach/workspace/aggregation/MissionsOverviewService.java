package com.dadcoach.workspace.aggregation;

import com.dadcoach.mission.LegacyMissionStatus;
import com.dadcoach.workspace.dto.response.ActiveMissionsResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Aggregates mission data for the workspace missions overview.
 *
 * <p>Provides active missions for the father — missions with status IN (ASSIGNED, ACCEPTED, IN_PROGRESS).</p>
 */
@Service
public class MissionsOverviewService {

    private static final List<LegacyMissionStatus> ACTIVE_STATUSES = List.of(
            LegacyMissionStatus.ASSIGNED,
            LegacyMissionStatus.ACCEPTED,
            LegacyMissionStatus.IN_PROGRESS
    );

    private final MissionDataService missionDataService;
    private final ChildDataService childDataService;

    public MissionsOverviewService(MissionDataService missionDataService,
                                   ChildDataService childDataService) {
        this.missionDataService = missionDataService;
        this.childDataService = childDataService;
    }

    /**
     * Retrieves all active missions for a father.
     *
     * <p>Active missions are those with status IN (ASSIGNED, ACCEPTED, IN_PROGRESS).</p>
     *
     * @param fatherId the father's unique identifier
     * @return the active missions response
     */
    public ActiveMissionsResponse getActiveMissions(UUID fatherId) {
        List<MissionReadModel> missions = missionDataService.getMissionsByFatherIdAndStatus(
                fatherId, ACTIVE_STATUSES);

        List<ActiveMissionsResponse.MissionItem> missionItems = missions.stream()
                .map(this::buildMissionItem)
                .toList();

        return new ActiveMissionsResponse(missionItems, missionItems.size());
    }

    private ActiveMissionsResponse.MissionItem buildMissionItem(MissionReadModel mission) {
        String assignedChildName = null;
        if (mission.childId() != null) {
            assignedChildName = childDataService.getChild(mission.childId())
                    .map(ChildReadModel::name)
                    .orElse(null);
        }

        return new ActiveMissionsResponse.MissionItem(
                mission.missionId(),
                mission.title(),
                mission.description(),
                assignedChildName,
                null, // category - not in current MissionReadModel
                null, // difficultyLevel - not in current MissionReadModel
                mission.createdAt(),
                mission.status().name()
        );
    }
}
