package com.dadcoach.qualitytime.api;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.common.AppConstants;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeSchedulingException;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.dto.CompleteQualityTimeResult;
import com.dadcoach.qualitytime.dto.QualityTimeResponse;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.dto.AvailableSlotsDto;
import com.dadcoach.workflow.dto.AvailableSlotsDto.AvailableSlotDto;
import com.dadcoach.workflow.dto.CompleteRequest;
import com.dadcoach.workflow.dto.CompleteResponse;
import com.dadcoach.workflow.dto.ScheduleRequest;
import com.dadcoach.workflow.dto.ScheduleResponse;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Quality Time management endpoints.
 * 
 * <p>Provides endpoints for Quality Time management including:</p>
 * <ul>
 *   <li>Getting available time slots from Google Calendar</li>
 *   <li>Scheduling new Quality Time events</li>
 *   <li>Completing Quality Time events</li>
 *   <li>Canceling Quality Time events (to be implemented)</li>
 * </ul>
 * 
 * <p>All endpoints require authentication via the existing magic-link session mechanism.</p>
 * 
 * <p>Implements Requirement 14.1: API Simplification</p>
 * 
 * @see QualityTimeService
 * @see SystemStateLoader
 * @see AvailableSlotsDto
 */
@RestController
@RequestMapping("/api/v1/quality-time")
public class QualityTimeController {

    private static final Logger log = LoggerFactory.getLogger(QualityTimeController.class);

    private static final int DEFAULT_DAYS_AHEAD = 7;
    private static final int MAX_DAYS_AHEAD = 14;
    private static final int DEFAULT_MIN_DURATION_MINUTES = 30;
    private static final String DEFAULT_TIMEZONE = AppConstants.DEFAULT_TIMEZONE;

    private final QualityTimeService qualityTimeService;
    private final QualityTimeRepository qualityTimeRepository;
    private final SystemStateLoader systemStateLoader;
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;

    public QualityTimeController(
            QualityTimeService qualityTimeService,
            QualityTimeRepository qualityTimeRepository,
            SystemStateLoader systemStateLoader,
            FatherRepository fatherRepository,
            ChildRepository childRepository) {
        this.qualityTimeService = qualityTimeService;
        this.qualityTimeRepository = qualityTimeRepository;
        this.systemStateLoader = systemStateLoader;
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
    }

    /**
     * Returns available time slots for Quality Time scheduling.
     * 
     * <p>Analyzes the father's Google Calendar to find available time slots
     * suitable for scheduling Quality Time. Slots are calculated by:</p>
     * <ol>
     *   <li>Reading the father's Google Calendar for the specified period</li>
     *   <li>Identifying busy periods from calendar events</li>
     *   <li>Calculating available slots of at least the specified minimum duration</li>
     *   <li>Excluding times outside preferred activity hours (6am-10pm local time)</li>
     *   <li>Returning slots ordered by proximity to current time</li>
     * </ol>
     * 
     * <p>If the father's Google Calendar is not connected, returns an empty slot list
     * with {@code calendar_connected=false}. The frontend should prompt the user to
     * connect their calendar.</p>
     * 
     * <p>Implements Requirements 2.3 and 14.1</p>
     * 
     * @param actor the authenticated actor context
     * @param daysAhead number of days to look ahead (default: 7, max: 14)
     * @param minDurationMinutes minimum slot duration in minutes (default: 30)
     * @return 200 OK with available slots, calendar connection status, and timezone
     */
    @GetMapping("/available-slots")
    public ResponseEntity<AvailableSlotsDto> getAvailableSlots(
            @AuthActor ActorContext actor,
            @RequestParam(name = "days_ahead", defaultValue = "7") int daysAhead,
            @RequestParam(name = "min_duration_minutes", defaultValue = "30") int minDurationMinutes) {

        UUID fatherUuid = actor.getActorId();
        log.debug("Fetching available slots for father {}: daysAhead={}, minDuration={}",
                fatherUuid, daysAhead, minDurationMinutes);

        // Validate parameters
        if (daysAhead < 1) {
            daysAhead = DEFAULT_DAYS_AHEAD;
        } else if (daysAhead > MAX_DAYS_AHEAD) {
            daysAhead = MAX_DAYS_AHEAD;
        }

        if (minDurationMinutes < DEFAULT_MIN_DURATION_MINUTES) {
            minDurationMinutes = DEFAULT_MIN_DURATION_MINUTES;
        }

        // Load father to get timezone and calendar connection status
        Father father = findFatherByUuid(fatherUuid);
        String timezone = father.getTimezone() != null ? father.getTimezone() : DEFAULT_TIMEZONE;
        boolean calendarConnected = father.hasGoogleCalendarConfigured();

        // If calendar is not connected, return early with empty slots
        if (!calendarConnected) {
            log.debug("Google Calendar not connected for father {}", fatherUuid);
            return ResponseEntity.ok(AvailableSlotsDto.disconnected(timezone));
        }

        // Load available slots using SystemStateLoader
        List<AvailableSlot> availableSlots = systemStateLoader.loadAvailableSlots(fatherUuid, daysAhead);

        // Filter slots by minimum duration
        final int finalMinDuration = minDurationMinutes;
        List<AvailableSlot> filteredSlots = availableSlots.stream()
                .filter(slot -> slot.canAccommodate(finalMinDuration))
                .toList();

        // Convert to DTOs
        List<AvailableSlotDto> slotDtos = filteredSlots.stream()
                .map(slot -> new AvailableSlotDto(
                        slot.startTime(),
                        slot.endTime(),
                        slot.durationMinutes()
                ))
                .toList();

        log.debug("Returning {} available slots for father {}", slotDtos.size(), fatherUuid);

        return ResponseEntity.ok(AvailableSlotsDto.connected(slotDtos, timezone));
    }

    /**
     * Schedules a new Quality Time event for the authenticated father.
     * 
     * <p>This endpoint performs the following operations:</p>
     * <ol>
     *   <li>Validates the request body (child_id, start_time, duration_minutes)</li>
     *   <li>Validates the requested time slot against Google Calendar availability</li>
     *   <li>Creates a Google Calendar event with title, duration, description, reminders, and green color</li>
     *   <li>Creates a Quality Time database record</li>
     *   <li>Returns the created event details</li>
     * </ol>
     * 
     * <p>Business Rules:</p>
     * <ul>
     *   <li>Duration must be at least 30 minutes</li>
     *   <li>Start time must be in the future</li>
     *   <li>The child must belong to the authenticated father</li>
     *   <li>The time slot must be available (no calendar conflicts)</li>
     * </ul>
     * 
     * <p>Implements Requirement 14.3</p>
     *
     * @param request the validated schedule request containing childId, startTime, and durationMinutes
     * @param actor   the authenticated actor context
     * @return 201 Created with the schedule response on success
     * @throws ResourceNotFoundException if the child is not found or doesn't belong to the father
     * @throws IllegalStateException if there's a calendar conflict at the requested time
     * @throws QualityTimeSchedulingException if calendar event creation fails
     */
    @PostMapping("/schedule")
    public ResponseEntity<ScheduleResponse> scheduleQualityTime(
            @Valid @RequestBody ScheduleRequest request,
            @AuthActor ActorContext actor) {

        UUID actorUuid = actor.getActorId();
        Long fatherId = actorUuid.getLeastSignificantBits();
        
        log.info("Scheduling Quality Time for father {} with child {} at {}",
                fatherId, request.childId(), request.startTime());

        // Convert child UUID to Long
        Long childId = request.childId().getLeastSignificantBits();

        // Validate child exists and belongs to this father
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", request.childId()));

        // Verify ownership (return 404 for mismatch to prevent enumeration)
        if (!child.getFatherId().equals(fatherId)) {
            log.warn("Child {} does not belong to father {}", childId, fatherId);
            throw new ResourceNotFoundException("Child", request.childId());
        }

        // Validate start time is in the future
        Instant now = Instant.now();
        if (request.startTime().isBefore(now)) {
            throw new IllegalArgumentException("Start time must be in the future");
        }

        // Validate against calendar availability (Read Before Write principle)
        validateCalendarAvailability(actorUuid, request.startTime(), request.durationMinutes());

        // Create the Quality Time event
        Duration duration = Duration.ofMinutes(request.durationMinutes());
        ScheduleQualityTimeResult result = qualityTimeService.scheduleQualityTime(
                fatherId,
                childId,
                request.startTime(),
                duration
        );

        log.info("Quality Time {} scheduled successfully for father {} with calendar event {}",
                result.qualityTimeId(), fatherId, result.calendarEventId());

        // Convert to API response
        ScheduleResponse response = ScheduleResponse.fromResult(result);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Marks a Quality Time event as completed.
     * 
     * <p>This endpoint allows a father to mark their scheduled Quality Time as completed.
     * On completion, the following updates occur:</p>
     * <ol>
     *   <li>Quality Time status is updated to COMPLETED</li>
     *   <li>Completion notes are stored (if provided)</li>
     *   <li>Father's streak counter is incremented</li>
     *   <li>Longest streak is updated if this is a new record</li>
     *   <li>Total Quality Time completed count is incremented</li>
     *   <li>Belt level is recalculated (SACRED Belt System)</li>
     * </ol>
     * 
     * <p>Security: Returns 404 (not 403) if the Quality Time does not exist or
     * belongs to another father, to prevent resource enumeration.</p>
     * 
     * <p>Implements Requirement 14.1 (API Simplification)</p>
     * 
     * @param id the UUID of the Quality Time to complete
     * @param request the completion request with optional notes
     * @param actor the authenticated actor context
     * @return 200 OK with completion result including updated streak and belt information
     * @throws ResourceNotFoundException if Quality Time not found or not owned by the authenticated father
     * @throws IllegalStateException if Quality Time is not in SCHEDULED status
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<CompleteResponse> completeQualityTime(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CompleteRequest request,
            @AuthActor ActorContext actor) {

        UUID fatherUuid = actor.getActorId();
        Long fatherId = fatherUuid.getLeastSignificantBits();
        
        log.info("Completing Quality Time {} for father {}", id, fatherId);

        // Step 1: Load the Quality Time to verify it exists
        QualityTime qualityTime = qualityTimeRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Quality Time {} not found", id);
                    return new ResourceNotFoundException("QualityTime", id);
                });

        // Step 2: Verify ownership - return 404 (not 403) to prevent resource enumeration
        if (!qualityTime.getFatherId().equals(fatherId)) {
            log.warn("Quality Time {} does not belong to father {} (actual owner: {})",
                    id, fatherId, qualityTime.getFatherId());
            throw new ResourceNotFoundException("QualityTime", id);
        }

        // Step 3: Extract notes from request (may be null)
        String notes = (request != null) ? request.notes() : null;

        // Step 4: Complete the Quality Time using the service
        // This handles: status update, streak increment, belt recalculation
        CompleteQualityTimeResult result = qualityTimeService.completeQualityTime(id, notes);

        log.info("Quality Time {} completed successfully. New streak: {}, Belt earned: {}",
                id, result.newStreak(), result.beltEarned());

        // Step 5: Return the response
        return ResponseEntity.ok(CompleteResponse.fromResult(result));
    }

    /**
     * Cancels a scheduled Quality Time event.
     * 
     * <p>This endpoint performs the following operations:</p>
     * <ol>
     *   <li>Loads the Quality Time by ID (returns 404 if not found)</li>
     *   <li>Verifies the authenticated father owns the Quality Time (returns 404 if not owner)</li>
     *   <li>Delegates to QualityTimeService to cancel the event and delete from Google Calendar</li>
     *   <li>Returns the updated Quality Time with CANCELLED status</li>
     * </ol>
     * 
     * <p>Note: Returns 404 (not 403) for unauthorized access to prevent resource enumeration.</p>
     * 
     * <p>Implements Requirement 14.1: API Simplification</p>
     * 
     * @param id the UUID of the Quality Time to cancel
     * @param actor the authenticated actor context
     * @return 200 OK with the cancelled QualityTimeResponse
     * @throws ResourceNotFoundException if Quality Time not found or not owned by the authenticated father
     * @throws IllegalStateException if Quality Time is not in SCHEDULED status
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<QualityTimeResponse> cancelQualityTime(
            @PathVariable("id") UUID id,
            @AuthActor ActorContext actor) {

        UUID fatherUuid = actor.getActorId();
        Long fatherId = fatherUuid.getLeastSignificantBits();
        
        log.info("Cancel Quality Time request: qualityTimeId={}, fatherId={}", id, fatherId);

        // Step 1: Load the Quality Time (404 if not found)
        QualityTime qualityTime = qualityTimeRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Quality Time {} not found", id);
                    return new ResourceNotFoundException("QualityTime", id);
                });

        // Step 2: Verify ownership - return 404 (not 403) to prevent resource enumeration
        if (!qualityTime.getFatherId().equals(fatherId)) {
            log.warn("Quality Time {} does not belong to father {} (actual owner: {})",
                    id, fatherId, qualityTime.getFatherId());
            throw new ResourceNotFoundException("QualityTime", id);
        }

        // Step 3: Cancel the Quality Time (also deletes Google Calendar event)
        qualityTimeService.cancelQualityTime(id);

        // Step 4: Reload the Quality Time to get updated status
        QualityTime cancelledQualityTime = qualityTimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QualityTime", id));

        log.info("Quality Time {} cancelled successfully", id);

        return ResponseEntity.ok(QualityTimeResponse.from(cancelledQualityTime));
    }

    /**
     * Finds a Father by UUID.
     * 
     * <p>Since Father.id is Long and ActorContext uses UUID, we need to convert.
     * The UUID's least significant bits represent the father's Long id.</p>
     * 
     * @param fatherUuid the father's UUID
     * @return the Father entity
     * @throws ResourceNotFoundException if father not found
     */
    private Father findFatherByUuid(UUID fatherUuid) {
        // Convert UUID to Long id
        long numericId = fatherUuid.getLeastSignificantBits();
        
        return fatherRepository.findById(numericId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherUuid));
    }

    /**
     * Validates that the requested time slot is available in the father's calendar.
     * 
     * <p>Implements the Read Before Write principle (Requirement 2.6) by checking
     * calendar availability before creating the event. This detects any conflicts
     * that may have been created since the available slots were last read.</p>
     *
     * @param fatherUuid the father's UUID
     * @param startTime the requested start time
     * @param durationMinutes the requested duration in minutes
     * @throws IllegalStateException if the time slot conflicts with existing events
     */
    private void validateCalendarAvailability(UUID fatherUuid, Instant startTime, int durationMinutes) {
        try {
            // Load available slots for the father
            List<AvailableSlot> availableSlots = systemStateLoader.loadAvailableSlots(fatherUuid);
            
            if (availableSlots.isEmpty()) {
                // If no slots are returned, it likely means calendar is not connected
                // Allow the scheduling to proceed - QualityTimeService will handle calendar status
                log.debug("No available slots found for father {}, proceeding with scheduling", fatherUuid);
                return;
            }

            // Check if the requested slot falls within any available slot
            Instant endTime = startTime.plusSeconds(durationMinutes * 60L);
            
            boolean isAvailable = availableSlots.stream()
                    .anyMatch(slot -> 
                            !startTime.isBefore(slot.startTime()) && 
                            !endTime.isAfter(slot.endTime()));

            if (!isAvailable) {
                log.warn("Requested time slot {}-{} is not available for father {}",
                        startTime, endTime, fatherUuid);
                throw new IllegalStateException(
                        "The requested time slot is not available. Please choose a different time.");
            }
            
            log.debug("Time slot {}-{} validated as available for father {}", startTime, endTime, fatherUuid);
            
        } catch (IllegalStateException e) {
            // Re-throw validation errors
            throw e;
        } catch (Exception e) {
            // Log other errors but allow scheduling to proceed
            // QualityTimeService will perform its own conflict check
            log.warn("Error validating calendar availability for father {}: {}. Proceeding with scheduling.",
                    fatherUuid, e.getMessage());
        }
    }
}
