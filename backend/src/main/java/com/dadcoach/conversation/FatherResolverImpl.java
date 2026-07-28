package com.dadcoach.conversation;

import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.father.FatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a Father entity by channel identity (senderId = phone number).
 * Bridges the conversation engine (UUID-based) with the father domain (Long-based).
 *
 * <p>When a sender is unknown, creates a new Father with status NOT_STARTED
 * and generates a stable UUID for the conversation layer.
 */
@Service
public class FatherResolverImpl implements FatherResolver {

    private static final Logger log = LoggerFactory.getLogger(FatherResolverImpl.class);

    private final FatherRepository fatherRepository;
    private final FatherService fatherService;

    public FatherResolverImpl(FatherRepository fatherRepository, FatherService fatherService) {
        this.fatherRepository = fatherRepository;
        this.fatherService = fatherService;
    }

    @Override
    public Optional<ResolvedFather> findBySenderIdentity(String senderId, String channelId) {
        Optional<Father> father = fatherRepository.findByPhone(senderId);

        return father.map(f -> new ResolvedFather(
                deriveUuid(f.getId()),
                f.getStatus() != null ? f.getStatus().name() : "NOT_STARTED"
        ));
    }

    @Override
    public ResolvedFather createNewFather(String senderId, String channelId) {
        Father newFather = fatherService.createFather(senderId);
        UUID fatherUuid = deriveUuid(newFather.getId());

        log.info("Created new father with domain id={}, uuid={}, senderId={}",
                newFather.getId(), fatherUuid, senderId);

        return new ResolvedFather(fatherUuid, "NOT_STARTED");
    }

    /**
     * Derives a stable UUID from the domain Long ID.
     * Uses a deterministic mapping: MSB=0, LSB=domainId.
     * This ensures the same domain ID always produces the same UUID,
     * and the ContextAssembler can reverse it via UUID.getLeastSignificantBits().
     */
    private UUID deriveUuid(Long domainId) {
        if (domainId == null) {
            return UUID.randomUUID();
        }
        return new UUID(0L, domainId);
    }
}
