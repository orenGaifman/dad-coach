package com.dadcoach.api.child;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that bridges the API-layer ChildService interface (UUID-based)
 * with the domain-layer ChildService (Long-based).
 * <p>
 * Currently uses the ChildRepository directly for lookups since the domain
 * model uses Long IDs and the API surface uses UUIDs. A full ID mapping
 * layer will be added when the Father entity introduces external UUIDs.
 * <p>
 * This adapter delegates business logic to {@link com.dadcoach.domain.child.ChildService}
 * where possible and handles DTO mapping via {@link ChildMapper}.
 */
@Service("apiChildService")
@Transactional
public class ChildServiceAdapter implements ChildApiService {

    private static final Logger log = LoggerFactory.getLogger(ChildServiceAdapter.class);

    private final com.dadcoach.domain.child.ChildService domainChildService;
    private final ChildRepository childRepository;

    public ChildServiceAdapter(com.dadcoach.domain.child.ChildService domainChildService,
                               ChildRepository childRepository) {
        this.domainChildService = domainChildService;
        this.childRepository = childRepository;
    }

    @Override
    public ChildResponseDto createChild(UUID fatherId, ChildCreateRequest request) {
        // TODO: Resolve UUID fatherId to Long when Father entity supports external UUIDs.
        // For now, use hashCode as a temporary mapping (will be replaced).
        Long domainFatherId = resolveToLongId(fatherId);

        Child child = domainChildService.createChild(
                domainFatherId,
                request.getName(),
                request.getBirthDate(),
                null,
                request.getInterests(),
                request.getChallenges()
        );

        return mapToDto(child, fatherId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildResponseDto> listChildren(UUID fatherId) {
        Long domainFatherId = resolveToLongId(fatherId);
        List<Child> children = domainChildService.getChildrenByFather(domainFatherId);

        return children.stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .map(c -> mapToDto(c, fatherId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChildResponseDto> findById(UUID childId) {
        Long domainChildId = resolveToLongId(childId);
        return childRepository.findById(domainChildId)
                .map(c -> mapToDto(c, generateFatherUuid(c.getFatherId())));
    }

    @Override
    public ChildResponseDto updateChild(UUID childId, ChildCreateRequest request) {
        Long domainChildId = resolveToLongId(childId);

        Child updated = domainChildService.updateChild(
                domainChildId,
                request.getName(),
                request.getInterests(),
                request.getChallenges()
        );

        return mapToDto(updated, generateFatherUuid(updated.getFatherId()));
    }

    @Override
    public void deleteChild(UUID childId) {
        Long domainChildId = resolveToLongId(childId);
        domainChildService.archiveChild(domainChildId);
    }

    @Override
    @Transactional(readOnly = true)
    public int countActiveChildren(UUID fatherId) {
        Long domainFatherId = resolveToLongId(fatherId);
        return (int) childRepository.countActiveByFatherId(domainFatherId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> getOwnerFatherId(UUID childId) {
        Long domainChildId = resolveToLongId(childId);
        return childRepository.findById(domainChildId)
                .map(c -> generateFatherUuid(c.getFatherId()));
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    /**
     * Temporary UUID-to-Long mapping.
     * Uses the least-significant bits of the UUID as a Long ID.
     * This will be replaced by a proper lookup table when the domain model
     * introduces external UUID identifiers.
     */
    private Long resolveToLongId(UUID uuid) {
        return Math.abs(uuid.getLeastSignificantBits() % 1_000_000);
    }

    /**
     * Generates a deterministic UUID from a Long ID for response mapping.
     * Temporary approach until Father entity has a native UUID field.
     */
    private UUID generateFatherUuid(Long fatherId) {
        if (fatherId == null) return UUID.randomUUID();
        return new UUID(0L, fatherId);
    }

    private ChildResponseDto mapToDto(Child child, UUID fatherId) {
        UUID childUuid = new UUID(0L, child.getId());
        return ChildMapper.toDto(
                childUuid,
                fatherId,
                child.getName(),
                child.getBirthDate(),
                child.getInterests(),
                child.getChallenges(),
                child.getStatus(),
                child.getCreatedAt(),
                child.getUpdatedAt()
        );
    }
}
