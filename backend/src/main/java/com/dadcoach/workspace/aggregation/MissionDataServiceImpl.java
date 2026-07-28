package com.dadcoach.workspace.aggregation;

import com.dadcoach.mission.MissionStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stub implementation of {@link MissionDataService}.
 * Returns empty results until wired to the real Mission domain layer.
 */
@Service
public class MissionDataServiceImpl implements MissionDataService {

    @Override
    public List<MissionReadModel> getMissionsByFatherIdAndStatus(UUID fatherId, List<MissionStatus> statuses) {
        return List.of();
    }

    @Override
    public List<MissionReadModel> getMissionsByChildId(UUID childId) {
        return List.of();
    }

    @Override
    public int countCompletedMissionsByChildId(UUID childId) {
        return 0;
    }

    @Override
    public Optional<MissionReadModel> getMostRecentMissionByChildId(UUID childId) {
        return Optional.empty();
    }

    @Override
    public Optional<MissionReadModel> getActiveMission(UUID fatherId) {
        return Optional.empty();
    }
}
