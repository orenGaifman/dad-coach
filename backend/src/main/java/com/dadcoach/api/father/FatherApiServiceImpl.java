package com.dadcoach.api.father;

import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.CoachingStyle;
import com.dadcoach.father.FatherStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Implementation of the Father API service layer.
 * <p>
 * Bridges the UUID-based actor context from the API layer to the Long-based
 * internal Father entity. In production, this would use an external_id or
 * mapping table; for now it derives the internal ID from the UUID.
 */
@Service
@Transactional
public class FatherApiServiceImpl implements FatherApiService {

    private final FatherRepository fatherRepository;

    public FatherApiServiceImpl(FatherRepository fatherRepository) {
        this.fatherRepository = fatherRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Father getProfile(UUID actorId) {
        return findFatherByActorId(actorId);
    }

    @Override
    public Father updatePreferences(UUID actorId, FatherUpdateRequest request) {
        Father father = findFatherByActorId(actorId);

        if (request.timezone() != null) {
            // Validate timezone is a recognized IANA zone
            ZoneId.of(request.timezone()); // throws DateTimeException if invalid
            father.setTimezone(request.timezone());
        }

        if (request.coachingStyle() != null) {
            father.setCoachingStyle(request.coachingStyle());
        }

        if (request.preferredCoachingTime() != null) {
            LocalTime time = LocalTime.parse(request.preferredCoachingTime());
            father.setPreferredCoachingTime(time);
        }

        return fatherRepository.save(father);
    }

    @Override
    public void requestDeletion(UUID actorId) {
        Father father = findFatherByActorId(actorId);
        // Trigger GDPR deletion flow by transitioning to DELETED status.
        // The actual data purge is handled asynchronously by the deletion pipeline.
        father.transitionTo(FatherStatus.DELETED);
        fatherRepository.save(father);
    }

    /**
     * Resolves a Father entity from the actor's UUID.
     * <p>
     * The UUID's least significant bits correspond to the internal Long ID.
     * In a production system, this would use a dedicated mapping table or
     * the entity would have a UUID external_id column.
     */
    private Father findFatherByActorId(UUID actorId) {
        long internalId = actorId.getLeastSignificantBits();
        return fatherRepository.findById(internalId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", actorId));
    }
}
