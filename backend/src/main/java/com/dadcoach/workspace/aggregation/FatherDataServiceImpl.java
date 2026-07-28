package com.dadcoach.workspace.aggregation;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Stub implementation of {@link FatherDataService}.
 * Returns empty results until wired to the real Father domain layer.
 */
@Service
public class FatherDataServiceImpl implements FatherDataService {

    @Override
    public Optional<FatherReadModel> getFather(UUID fatherId) {
        return Optional.empty();
    }
}
