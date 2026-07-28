package com.dadcoach.workspace.aggregation;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stub implementation of {@link GoalDataService}.
 * Returns empty results until wired to the real Goal domain layer.
 */
@Service
public class GoalDataServiceImpl implements GoalDataService {

    @Override
    public List<GoalReadModel> getActiveGoalsByFatherId(UUID fatherId) {
        return List.of();
    }

    @Override
    public List<GoalReadModel> getAllGoalsByFatherId(UUID fatherId) {
        return List.of();
    }

    @Override
    public Optional<GoalReadModel> getGoalById(UUID goalId) {
        return Optional.empty();
    }

    @Override
    public List<GoalReadModel> getGoalsByChildId(UUID childId) {
        return List.of();
    }

    @Override
    public int countActiveGoalsByFatherId(UUID fatherId) {
        return 0;
    }
}
