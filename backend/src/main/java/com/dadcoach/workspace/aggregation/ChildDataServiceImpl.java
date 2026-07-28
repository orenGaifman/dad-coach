package com.dadcoach.workspace.aggregation;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stub implementation of {@link ChildDataService}.
 * Returns empty results until wired to the real Child domain layer.
 */
@Service
public class ChildDataServiceImpl implements ChildDataService {

    @Override
    public List<ChildReadModel> getChildrenByFatherId(UUID fatherId) {
        return List.of();
    }

    @Override
    public Optional<ChildReadModel> getChild(UUID childId) {
        return Optional.empty();
    }

    @Override
    public boolean childBelongsToFather(UUID fatherId, UUID childId) {
        return false;
    }
}
