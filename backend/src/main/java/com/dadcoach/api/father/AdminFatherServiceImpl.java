package com.dadcoach.api.father;

import com.dadcoach.common.MaskingUtils;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.api.pagination.CursorPageResponse;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.goal.GoalRepository;
import com.dadcoach.domain.memory.MemoryRepository;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.onboarding.provisioning.ActivationRecordRepository;
import com.dadcoach.onboarding.provisioning.AiProfileRepository;
import com.dadcoach.onboarding.provisioning.CommunicationPreferenceRepository;
import com.dadcoach.onboarding.provisioning.FamilyRepository;
import com.dadcoach.onboarding.provisioning.LanguagePreferenceRepository;
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
    private final ChildRepository childRepository;
    private final GoalRepository goalRepository;
    private final MemoryRepository memoryRepository;
    private final CommunicationEndpointRepository communicationEndpointRepository;
    private final FamilyRepository familyRepository;
    private final LanguagePreferenceRepository languagePreferenceRepository;
    private final CommunicationPreferenceRepository communicationPreferenceRepository;
    private final AiProfileRepository aiProfileRepository;
    private final ActivationRecordRepository activationRecordRepository;
    private final MagicLinkService magicLinkService;

    public AdminFatherServiceImpl(
            FatherRepository fatherRepository,
            ChildRepository childRepository,
            GoalRepository goalRepository,
            MemoryRepository memoryRepository,
            CommunicationEndpointRepository communicationEndpointRepository,
            FamilyRepository familyRepository,
            LanguagePreferenceRepository languagePreferenceRepository,
            CommunicationPreferenceRepository communicationPreferenceRepository,
            AiProfileRepository aiProfileRepository,
            ActivationRecordRepository activationRecordRepository,
            MagicLinkService magicLinkService) {
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.goalRepository = goalRepository;
        this.memoryRepository = memoryRepository;
        this.communicationEndpointRepository = communicationEndpointRepository;
        this.familyRepository = familyRepository;
        this.languagePreferenceRepository = languagePreferenceRepository;
        this.communicationPreferenceRepository = communicationPreferenceRepository;
        this.aiProfileRepository = aiProfileRepository;
        this.activationRecordRepository = activationRecordRepository;
        this.magicLinkService = magicLinkService;
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

    @Override
    @Transactional
    public void deleteFather(Long fatherId) {
        Father father = fatherRepository.findById(fatherId)
            .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        log.info("Deleting father: id={}, phone={}", fatherId, MaskingUtils.maskPhone(father.getPhone()));

        UUID fatherUuid = new UUID(0L, fatherId);

        // Delete related entities in order (due to foreign key constraints)
        // 1. Delete memories (must be deleted before father due to FK constraint)
        memoryRepository.deleteByFatherId(fatherId);
        
        // 2. Delete children
        childRepository.deleteByFatherId(fatherId);
        
        // 3. Delete goals
        goalRepository.deleteByFatherId(fatherId);
        
        // 4. Delete communication endpoints
        communicationEndpointRepository.deleteByFatherId(fatherUuid);
        
        // 5. Delete family
        familyRepository.deleteByFatherId(fatherUuid);
        
        // 6. Delete language preference
        languagePreferenceRepository.deleteByFatherId(fatherUuid);
        
        // 7. Delete communication preference
        communicationPreferenceRepository.deleteByFatherId(fatherUuid);
        
        // 8. Delete AI profile
        aiProfileRepository.deleteByFatherId(fatherUuid);
        
        // 9. Delete activation record
        activationRecordRepository.deleteByFatherId(fatherUuid);
        
        // 10. Finally delete the father
        fatherRepository.delete(father);
        
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
