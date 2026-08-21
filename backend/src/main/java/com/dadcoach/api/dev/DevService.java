package com.dadcoach.api.dev;

import com.dadcoach.api.dev.dto.ChildDto;
import com.dadcoach.api.dev.dto.FatherListItemDto;
import com.dadcoach.api.dev.dto.FatherStateDetailsDto;
import com.dadcoach.api.dev.dto.MessageDto;
import com.dadcoach.api.dev.dto.QualityTimeDto;
import com.dadcoach.api.dev.dto.TransitionDto;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.conversation.MessageLog;
import com.dadcoach.domain.conversation.MessageLogRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.workflow.WorkflowTransition;
import com.dadcoach.workflow.WorkflowTransitionLogRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic layer for Dev Dashboard API endpoints.
 *
 * <p>This service coordinates data retrieval for debugging endpoints,
 * providing visibility into father state, messages, and workflow transitions.</p>
 *
 * @see DevController
 */
@Service
@Transactional(readOnly = true)
public class DevService {

    private static final Logger log = LoggerFactory.getLogger(DevService.class);

    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final QualityTimeRepository qualityTimeRepository;
    private final MessageLogRepository messageLogRepository;
    private final WorkflowTransitionLogRepository workflowTransitionLogRepository;

    /**
     * Maximum allowed limit for message queries.
     */
    private static final int MAX_MESSAGE_LIMIT = 200;

    /**
     * Maximum allowed limit for transition queries.
     */
    private static final int MAX_TRANSITION_LIMIT = 100;

    public DevService(FatherRepository fatherRepository,
                      ChildRepository childRepository,
                      QualityTimeRepository qualityTimeRepository,
                      MessageLogRepository messageLogRepository,
                      WorkflowTransitionLogRepository workflowTransitionLogRepository) {
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.qualityTimeRepository = qualityTimeRepository;
        this.messageLogRepository = messageLogRepository;
        this.workflowTransitionLogRepository = workflowTransitionLogRepository;
    }

    /**
     * Lists fathers with optional search filtering and pagination.
     *
     * <p>Search is case-insensitive and matches against phone number or display_name.
     * Results are ordered by lastInteractionAt descending (most recent first).</p>
     *
     * @param search optional search string to filter by phone or display_name
     * @param pageable pagination parameters (page, size, sort ignored - uses default ordering)
     * @return paginated list of FatherListItemDto
     */
    public Page<FatherListItemDto> listFathers(String search, Pageable pageable) {
        log.debug("Listing fathers with search='{}', page={}, size={}",
                search, pageable.getPageNumber(), pageable.getPageSize());

        // Get all fathers - we'll filter in memory for now
        // In a production system with many records, this would use a custom query
        List<Father> allFathers = fatherRepository.findAll();

        // Apply case-insensitive search filtering
        List<Father> filtered = allFathers.stream()
                .filter(father -> matchesSearch(father, search))
                .toList();

        // Sort by lastInteractionAt descending (most recent first)
        List<Father> sorted = filtered.stream()
                .sorted((f1, f2) -> {
                    if (f1.getLastInteractionAt() == null && f2.getLastInteractionAt() == null) {
                        return 0;
                    }
                    if (f1.getLastInteractionAt() == null) {
                        return 1; // nulls last
                    }
                    if (f2.getLastInteractionAt() == null) {
                        return -1;
                    }
                    return f2.getLastInteractionAt().compareTo(f1.getLastInteractionAt());
                })
                .toList();

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), sorted.size());

        List<FatherListItemDto> pageContent;
        if (start >= sorted.size()) {
            pageContent = List.of();
        } else {
            pageContent = sorted.subList(start, end).stream()
                    .map(this::toFatherListItemDto)
                    .toList();
        }

        return new PageImpl<>(pageContent, pageable, sorted.size());
    }

    /**
     * Checks if a father matches the search criteria.
     *
     * @param father the father entity to check
     * @param search the search string (case-insensitive)
     * @return true if search is null/empty or if phone/display_name contains the search string
     */
    private boolean matchesSearch(Father father, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }

        String lowerSearch = search.toLowerCase();

        // Check phone number
        if (father.getPhone() != null &&
                father.getPhone().toLowerCase().contains(lowerSearch)) {
            return true;
        }

        // Check display name
        if (father.getDisplayName() != null &&
                father.getDisplayName().toLowerCase().contains(lowerSearch)) {
            return true;
        }

        return false;
    }

    /**
     * Maps a Father entity to FatherListItemDto.
     *
     * @param father the entity to map
     * @return the DTO representation
     */
    private FatherListItemDto toFatherListItemDto(Father father) {
        return new FatherListItemDto(
                father.getId(),
                father.getDisplayName(),
                father.getPhone(),
                father.getStatus() != null ? father.getStatus().name() : null,
                father.getCurrentWorkflowState() != null ? father.getCurrentWorkflowState().name() : null,
                father.getPreviousWorkflowState() != null ? father.getPreviousWorkflowState().name() : null,
                father.getCurrentBelt() != null ? father.getCurrentBelt().name() : null,
                father.getLastInteractionAt()
        );
    }

    /**
     * Retrieves detailed state information for a specific father.
     *
     * <p>Implements partial data handling: if children or quality time queries fail,
     * the method returns HTTP 200 with partial data and error indicators rather than
     * failing the entire request.</p>
     *
     * @param fatherId the ID of the father to retrieve
     * @return FatherStateDetailsDto with complete or partial data
     * @throws FatherNotFoundException if the father ID does not exist
     */
    public FatherStateDetailsDto getFatherState(Long fatherId) {
        log.debug("Getting state for father id={}", fatherId);

        // Father must exist - this is a hard failure
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new FatherNotFoundException(fatherId));

        List<String> errors = new ArrayList<>();
        boolean partial = false;

        // Query children with error accumulation
        List<ChildDto> children;
        try {
            children = childRepository.findByFatherId(fatherId).stream()
                    .map(this::toChildDto)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load children for father {}", fatherId, e);
            children = List.of();
            errors.add("Failed to load children");
            partial = true;
        }

        // Query scheduled quality times with error accumulation
        List<QualityTimeDto> scheduledQualityTimes;
        try {
            scheduledQualityTimes = qualityTimeRepository
                    .findByFatherIdAndStatus(fatherId, QualityTimeStatus.SCHEDULED).stream()
                    .map(this::toQualityTimeDto)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load scheduled quality times for father {}", fatherId, e);
            scheduledQualityTimes = List.of();
            errors.add("Failed to load scheduled quality times");
            partial = true;
        }

        return new FatherStateDetailsDto(
                father.getId(),
                father.getDisplayName(),
                father.getPhone(),
                father.getStatus() != null ? father.getStatus().name() : null,
                new FatherStateDetailsDto.WorkflowInfo(
                        father.getCurrentWorkflowState() != null ? father.getCurrentWorkflowState().name() : null,
                        father.getPreviousWorkflowState() != null ? father.getPreviousWorkflowState().name() : null,
                        father.getWorkflowStateEnteredAt(),
                        father.getWelcomedAt()
                ),
                new FatherStateDetailsDto.BeltInfo(
                        father.getCurrentBelt() != null ? father.getCurrentBelt().name() : null,
                        father.getTotalQualityTimesCompleted(),
                        father.getCurrentStreakWeeks() != null ? father.getCurrentStreakWeeks() : 0
                ),
                children,
                scheduledQualityTimes,
                partial,
                errors
        );
    }

    /**
     * Maps a Child entity to ChildDto.
     *
     * @param child the entity to map
     * @return the DTO representation
     */
    private ChildDto toChildDto(Child child) {
        return new ChildDto(
                child.getId(),
                child.getName(),
                child.getBirthDate()
        );
    }

    /**
     * Maps a QualityTime entity to QualityTimeDto.
     *
     * @param qualityTime the entity to map
     * @return the DTO representation
     */
    private QualityTimeDto toQualityTimeDto(QualityTime qualityTime) {
        // Get child name - the child relationship should be loaded
        String childName = null;
        try {
            Child child = qualityTime.getChild();
            if (child != null) {
                childName = child.getName();
            }
        } catch (Exception e) {
            log.warn("Failed to load child name for quality time {}", qualityTime.getId(), e);
        }

        return new QualityTimeDto(
                qualityTime.getId(),
                childName,
                qualityTime.getScheduledStart(),
                qualityTime.getScheduledEnd(),
                qualityTime.getStatus() != null ? qualityTime.getStatus().name() : null
        );
    }

    /**
     * Retrieves workflow state transitions for a specific father.
     *
     * <p>Returns transitions ordered by created_at descending (newest first),
     * with the count limited by the provided limit parameter (max 100).</p>
     *
     * <p>Implements Requirement 4.1: Return workflow_state_transition_log entries with
     * id, from_state, to_state, trigger_reason, trigger_message_id, and created_at.</p>
     *
     * <p>Implements Requirement 4.2: Limit enforcement with max 100.</p>
     *
     * <p>Implements Requirement 4.3: Order by created_at descending.</p>
     *
     * @param fatherId the ID of the father whose transitions to retrieve
     * @param limit maximum number of transitions to return (capped at 100)
     * @return list of TransitionDto ordered by created_at descending
     * @throws FatherNotFoundException if the father ID does not exist
     */
    public List<TransitionDto> getTransitions(Long fatherId, int limit) {
        log.debug("Getting transitions for father id={}, limit={}", fatherId, limit);

        // Verify father exists - throw FatherNotFoundException if not
        if (!fatherRepository.existsById(fatherId)) {
            throw new FatherNotFoundException(fatherId);
        }

        // Apply max limit constraint
        int effectiveLimit = Math.min(limit, MAX_TRANSITION_LIMIT);

        // Query transitions ordered by created_at descending
        List<WorkflowTransition> transitions = workflowTransitionLogRepository
                .findByFatherIdOrderByCreatedAtDesc(fatherId);

        // Apply limit and map to DTOs
        return transitions.stream()
                .limit(effectiveLimit)
                .map(this::toTransitionDto)
                .toList();
    }

    /**
     * Maps a WorkflowTransition entity to TransitionDto.
     *
     * @param transition the entity to map
     * @return the DTO representation
     */
    private TransitionDto toTransitionDto(WorkflowTransition transition) {
        return new TransitionDto(
                transition.getId(),
                transition.getFromState() != null ? transition.getFromState().name() : null,
                transition.getToState() != null ? transition.getToState().name() : null,
                transition.getTriggerReason(),
                transition.getTriggerMessageId(),
                transition.getCreatedAt()
        );
    }

    /**
     * Retrieves message log entries for a specific father.
     *
     * <p>Returns messages ordered by created_at descending (newest first),
     * with the count limited by the provided limit parameter (max 200).</p>
     *
     * <p>Implements Requirement 3.1: Return message_log entries with
     * id, direction, content, and created_at.</p>
     *
     * <p>Implements Requirement 3.3: Order by created_at descending.</p>
     *
     * <p>Implements Requirement 3.4: Filter by since timestamp if provided.</p>
     *
     * @param fatherId the ID of the father whose messages to retrieve
     * @param limit maximum number of messages to return (capped at 200)
     * @param since optional timestamp to filter messages created after this time
     * @return list of MessageDto ordered by created_at descending
     * @throws FatherNotFoundException if the father ID does not exist
     */
    public List<MessageDto> getMessages(Long fatherId, int limit, Instant since) {
        log.debug("Getting messages for father id={}, limit={}, since={}", fatherId, limit, since);

        // Verify father exists - throw FatherNotFoundException if not
        if (!fatherRepository.existsById(fatherId)) {
            throw new FatherNotFoundException(fatherId);
        }

        // Apply max limit constraint
        int effectiveLimit = Math.min(limit, MAX_MESSAGE_LIMIT);

        // Query messages with or without since filter
        List<MessageLog> messages;
        if (since != null) {
            messages = messageLogRepository.findRecentByFatherIdAndSince(fatherId, since, effectiveLimit);
        } else {
            messages = messageLogRepository.findRecentByFatherId(fatherId, effectiveLimit);
        }

        // Map to DTOs
        return messages.stream()
                .map(this::toMessageDto)
                .toList();
    }

    /**
     * Maps a MessageLog entity to MessageDto.
     *
     * @param message the entity to map
     * @return the DTO representation
     */
    private MessageDto toMessageDto(MessageLog message) {
        return new MessageDto(
                message.getId(),
                message.getDirection() != null ? message.getDirection().name() : null,
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
