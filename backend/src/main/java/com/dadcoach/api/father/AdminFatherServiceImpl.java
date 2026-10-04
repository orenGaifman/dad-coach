package com.dadcoach.api.father;

import com.dadcoach.common.MaskingUtils;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.api.pagination.CursorPageResponse;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherDataPurger;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions;
import com.dadcoach.workspace.magiclink.MagicLinkService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AdminFatherService that provides admin operations for father management.
 */
@Service
@Transactional
public class AdminFatherServiceImpl implements AdminFatherService {

    private static final Logger log = LoggerFactory.getLogger(AdminFatherServiceImpl.class);

    private final FatherRepository fatherRepository;
    private final MagicLinkService magicLinkService;
    private final FatherDataPurger purger;
    private final PlatformPersonDeletions platformDeletions;

    public AdminFatherServiceImpl(
            FatherRepository fatherRepository,
            MagicLinkService magicLinkService,
            FatherDataPurger purger,
            PlatformPersonDeletions platformDeletions) {
        this.fatherRepository = fatherRepository;
        this.magicLinkService = magicLinkService;
        this.purger = purger;
        this.platformDeletions = platformDeletions;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminFatherSummaryDto> listFathers(
            String query, String status, String phase, String cursor, int pageSize) {
        
        // Simple implementation: fetch all and convert to DTOs
        // In production, this should use proper pagination and filtering
        List<Father> fathers = fatherRepository.findAll();
        
        List<AdminFatherSummaryDto> summaries = fathers.stream()
            .map(this::toSummaryDto)
            .toList();
        
        return CursorPageResponse.of(summaries, null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminFatherDetailDto> getFatherDetail(UUID fatherId) {
        // UUID is derived from Long ID using new UUID(0L, id)
        long id = fatherId.getLeastSignificantBits();
        return fatherRepository.findById(id)
            .map(this::toDetailDto);
    }

    /**
     * Deletes the father for good: his Dad Coach data now ({@link FatherDataPurger}), and - in the same transaction -
     * a request to the AI Workflow Platform to delete his person and conversations, sent after commit and retried
     * until it is done ({@link PlatformPersonDeletions}). Admin only.
     */
    @Override
    @Transactional
    public void deleteFather(Long fatherId) {
        Father father = fatherRepository.findById(fatherId)
            .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));
        String phone = father.getPhone();
        log.info("Deleting father: id={}, phone={}", fatherId, MaskingUtils.maskPhone(phone));
        fatherRepository.flush();
        platformDeletions.request(fatherId, phone, false);
        purger.purge(fatherId);
        log.info("Deleted father and all related data: id={}", fatherId);
    }

    private AdminFatherSummaryDto toSummaryDto(Father father) {
        AdminFatherSummaryDto dto = new AdminFatherSummaryDto();
        dto.setId(new UUID(0L, father.getId()));
        // Use phone as fallback display name if not set
        String displayName = father.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = father.getPhone() != null ? MaskingUtils.maskPhoneForDisplay(father.getPhone()) : "User";
        }
        dto.setDisplayName(displayName);
        dto.setPhoneNumber(father.getPhone());
        dto.setStatus(father.getStatus() != null ? father.getStatus().name() : null);
        dto.setLocale(father.getLocale());
        dto.setCreatedAt(father.getCreatedAt());
        dto.setCurrentWorkflowState(father.getCurrentWorkflowState() != null 
            ? father.getCurrentWorkflowState().name() : null);
        dto.setWorkflowStateEnteredAt(father.getWorkflowStateEnteredAt());
        
        // Get existing valid magic link (if any) - read-only operation
        try {
            magicLinkService.getExistingValidLink(father.getId())
                .ifPresent(dto::setDashboardUrl);
        } catch (Exception e) {
            log.warn("Failed to get dashboard URL for father {}: {}", father.getId(), e.getMessage());
        }
        
        return dto;
    }

    private AdminFatherDetailDto toDetailDto(Father father) {
        AdminFatherDetailDto dto = new AdminFatherDetailDto();
        dto.setId(new UUID(0L, father.getId()));
        // Use phone as fallback display name if not set
        String displayName = father.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = father.getPhone() != null ? MaskingUtils.maskPhoneForDisplay(father.getPhone()) : "User";
        }
        dto.setDisplayName(displayName);
        dto.setPhoneNumber(father.getPhone());
        dto.setStatus(father.getStatus() != null ? father.getStatus().name() : null);
        dto.setLocale(father.getLocale());
        dto.setTimezone(father.getTimezone());
        dto.setCreatedAt(father.getCreatedAt());
        dto.setLastActiveAt(father.getLastInteractionAt());
        dto.setCoachingPhase(father.getCoachingPhase() != null ? father.getCoachingPhase().name() : null);
        dto.setCoachingStyle(father.getCoachingStyle() != null ? father.getCoachingStyle().name() : null);
        dto.setEngagementScore(father.getEngagementScore());
        dto.setCoachingStreak(father.getCoachingStreak());
        return dto;
    }
}
