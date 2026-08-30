package com.dadcoach.qualitytime;

import com.dadcoach.common.AppConstants;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.dto.CompleteQualityTimeResult;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.qualitytime.dto.UpcomingQualityTimeDto;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import com.dadcoach.workspace.commitment.CommitmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Implementation of the QualityTimeService interface.
 * 
 * <p>Manages Quality Time lifecycle including scheduling, completion, and cancellation.
 * Quality Time is the core engagement unit of the deterministic workflow engine.</p>
 * 
 * <p>Implements Requirements: 3.3, 3.4, 3.6, 3.7, 7.2, 8.5</p>
 * 
 * @see QualityTimeService
 * @see QualityTime
 */
@Service
@Transactional
public class QualityTimeServiceImpl implements QualityTimeService {

    private static final Logger log = LoggerFactory.getLogger(QualityTimeServiceImpl.class);

    private static final String GOOGLE_CALENDAR_API = "https://www.googleapis.com/calendar/v3";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final QualityTimeRepository qualityTimeRepository;
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final WorkflowMetrics workflowMetrics;
    private final CommitmentService commitmentService;
    private final WeeklyGoalService weeklyGoalService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.calendar.client-id:}")
    private String clientId;

    @Value("${google.calendar.client-secret:}")
    private String clientSecret;

    public QualityTimeServiceImpl(
            QualityTimeRepository qualityTimeRepository,
            FatherRepository fatherRepository,
            ChildRepository childRepository,
            WorkflowMetrics workflowMetrics,
            CommitmentService commitmentService,
            WeeklyGoalService weeklyGoalService,
            RestTemplate restTemplate) {
        this.qualityTimeRepository = qualityTimeRepository;
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.workflowMetrics = workflowMetrics;
        this.commitmentService = commitmentService;
        this.weeklyGoalService = weeklyGoalService;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Implementation details for scheduling (Requirements 3.3, 3.6, 2.6):</p>
     * <ol>
     *   <li>Load father and child entities</li>
     *   <li>Re-read Google Calendar to detect conflicts (Read Before Write principle per Req 2.6)</li>
     *   <li>Create Google Calendar event with title, duration, description, reminders, green color</li>
     *   <li>Create QualityTime database record with google_calendar_event_id</li>
     *   <li>Handle calendar API failure with retry and error messaging (Req 3.6)</li>
     * </ol>
     * 
     * <p>Google Calendar Event Details (Requirement 3.3):</p>
     * <ul>
     *   <li>Title: "👨‍👧 Quality Time with [Child Name]" (English) / "👨‍👧 זמן איכות עם [Child Name]" (Hebrew)</li>
     *   <li>Description: "Dad Coach scheduled Quality Time — enjoy your moment together!"</li>
     *   <li>Reminders: 1 hour before (popup), 15 minutes before (popup)</li>
     *   <li>Color: Green (colorId 10)</li>
     * </ul>
     */
    @Override
    public ScheduleQualityTimeResult scheduleQualityTime(
            Long fatherId,
            Long childId,
            Instant startTime,
            Duration duration) {
        
        log.info("Scheduling Quality Time for father {} with child {} at {}", 
                fatherId, childId, startTime);

        // Step 1: Load father and child entities
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new IllegalArgumentException("Child not found: " + childId));

        // Calculate end time
        Instant endTime = startTime.plus(duration);

        // Step 2: Re-read Google Calendar before write (conflict detection per Requirement 2.6)
        // This implements the "Read Before Write" principle to detect any conflicts
        // that may have been created since the available slots were last read
        if (father.hasGoogleCalendarConfigured()) {
            boolean hasConflict = checkCalendarConflict(father, startTime, endTime);
            if (hasConflict) {
                log.warn("Calendar conflict detected for father {} at {}-{}", 
                        fatherId, startTime, endTime);
                throw new IllegalStateException(
                        "Calendar conflict detected at the requested time. Please choose a different slot.");
            }
        }

        // Create QualityTime entity
        QualityTime qualityTime = new QualityTime(father, child, startTime, endTime);

        // Step 3 & 4: Create Google Calendar event and handle retry (Requirement 3.6)
        String calendarEventId = null;
        if (father.hasGoogleCalendarConfigured()) {
            calendarEventId = createCalendarEventWithRetry(father, child, startTime, endTime);
            if (calendarEventId != null) {
                qualityTime.setGoogleCalendarEventId(calendarEventId);
            }
        }

        // Save the Quality Time record with google_calendar_event_id
        QualityTime saved = qualityTimeRepository.save(qualityTime);
        log.info("Quality Time {} scheduled successfully with calendar event {}", 
                saved.getId(), calendarEventId);

        // Also create a commitment record for dashboard display
        try {
            commitmentService.createCommitment(
                fatherId,
                childId,
                startTime,
                "QUALITY_TIME",
                "זמן איכות עם " + child.getName(),
                "WHATSAPP",
                null  // No conversation ID in this context
            );
            log.info("Created commitment for quality time {} display on dashboard", saved.getId());
        } catch (Exception e) {
            log.warn("Failed to create commitment for quality time {}: {}", saved.getId(), e.getMessage());
            // Don't fail the whole scheduling if commitment creation fails
        }

        return ScheduleQualityTimeResult.success(
                saved.getId(),
                calendarEventId,
                child.getName(),
                startTime,
                endTime
        );
    }

    /**
     * Checks if there's a calendar conflict at the requested time slot.
     * Implements the "Read Before Write" principle from Requirement 2.6.
     *
     * @param father the father whose calendar to check
     * @param startTime the requested start time
     * @param endTime the requested end time
     * @return true if there's a conflict, false otherwise
     */
    private boolean checkCalendarConflict(Father father, Instant startTime, Instant endTime) {
        try {
            String accessToken = getValidAccessToken(father);
            if (accessToken == null) {
                log.warn("Could not get access token for conflict check, proceeding without check");
                return false;
            }

            String calendarId = father.getGoogleCalendarId() != null 
                    ? father.getGoogleCalendarId() : "primary";
            String timezone = father.getTimezone() != null 
                    ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE;

            // Query events in the time range
            String timeMin = ZonedDateTime.ofInstant(startTime, ZoneId.of(timezone))
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String timeMax = ZonedDateTime.ofInstant(endTime, ZoneId.of(timezone))
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            String url = GOOGLE_CALENDAR_API + "/calendars/" +
                    URLEncoder.encode(calendarId, StandardCharsets.UTF_8) +
                    "/events?timeMin=" + URLEncoder.encode(timeMin, StandardCharsets.UTF_8) +
                    "&timeMax=" + URLEncoder.encode(timeMax, StandardCharsets.UTF_8) +
                    "&singleEvents=true";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                JsonNode items = responseJson.get("items");
                if (items != null && items.isArray() && !items.isEmpty()) {
                    log.debug("Found {} conflicting events in time range", items.size());
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.warn("Failed to check calendar conflicts: {}. Proceeding with scheduling.", e.getMessage());
            return false;
        }
    }

    /**
     * Creates a Google Calendar event with retry logic per Requirement 3.6.
     * Handles calendar API failure with retry and error messaging.
     *
     * @param father the father whose calendar to use
     * @param child the child the Quality Time is with
     * @param startTime the start time
     * @param endTime the end time
     * @return the calendar event ID
     * @throws QualityTimeSchedulingException if creation fails after retry
     */
    private String createCalendarEventWithRetry(Father father, Child child, Instant startTime, Instant endTime) {
        Exception lastException = null;

        // First attempt
        try {
            String eventId = createCalendarEvent(father, child, startTime, endTime);
            if (eventId != null) {
                log.info("Created Google Calendar event {} for Quality Time", eventId);
                return eventId;
            }
        } catch (Exception e) {
            lastException = e;
            log.warn("First calendar event creation attempt failed: {}", e.getMessage());
        }

        // Retry with exponential backoff (Requirement 3.6)
        try {
            Thread.sleep(1000); // 1 second delay before retry
            String eventId = createCalendarEvent(father, child, startTime, endTime);
            if (eventId != null) {
                log.info("Created Google Calendar event {} on retry", eventId);
                return eventId;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new QualityTimeSchedulingException("Interrupted during calendar retry", ie);
        } catch (Exception e) {
            lastException = e;
            log.error("Calendar event creation failed after retry: {}", e.getMessage());
        }

        // Both attempts failed - throw exception with informative message (Requirement 3.6)
        String errorMessage = "Failed to create calendar event after retry";
        if (lastException != null) {
            throw new QualityTimeSchedulingException(errorMessage, lastException);
        }
        throw new QualityTimeSchedulingException(errorMessage);
    }

    @Override
    public CompleteQualityTimeResult completeQualityTime(UUID qualityTimeId, String notes) {
        log.info("Completing Quality Time {}", qualityTimeId);

        // Load Quality Time
        QualityTime qualityTime = qualityTimeRepository.findById(qualityTimeId)
                .orElseThrow(() -> new IllegalArgumentException("Quality Time not found: " + qualityTimeId));

        // Validate status
        if (qualityTime.getStatus() != QualityTimeStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "Cannot complete Quality Time with status: " + qualityTime.getStatus());
        }

        // Mark as completed
        qualityTime.markCompleted(notes);

        // Update father's stats
        Father father = qualityTime.getFather();
        Belt previousBelt = father.getCurrentBelt();

        // Increment streak
        int newStreak = father.getQualityTimeStreak() + 1;
        father.setQualityTimeStreak(newStreak);

        // Update longest streak if new record
        if (newStreak > father.getQualityTimeLongestStreak()) {
            father.setQualityTimeLongestStreak(newStreak);
        }

        // Increment total completed
        int totalCompleted = father.getTotalQualityTimesCompleted() + 1;
        father.setTotalQualityTimesCompleted(totalCompleted);

        // Recalculate belt
        Belt newBelt = Belt.fromCompletionCount(totalCompleted);
        father.setCurrentBelt(newBelt);

        // Save entities
        qualityTimeRepository.save(qualityTime);
        fatherRepository.save(father);
        
        // Record Quality Time completion metric (Requirement 16.2)
        workflowMetrics.recordQualityTimeCompletion();

        // Record completed minutes to weekly goal
        int durationMinutes = calculateDurationMinutes(qualityTime);
        weeklyGoalService.recordCompletedQualityTime(father.getId(), durationMinutes);
        log.info("Recorded {} minutes to weekly goal for father {}", durationMinutes, father.getId());

        log.info("Quality Time {} completed. Streak: {}, Total: {}, Belt: {}", 
                qualityTimeId, newStreak, totalCompleted, newBelt);

        // Return result with or without belt earned
        if (newBelt != previousBelt) {
            log.info("Father {} earned new belt: {}", father.getId(), newBelt);
            return CompleteQualityTimeResult.withNewBelt(qualityTimeId, newStreak, newBelt);
        } else {
            return CompleteQualityTimeResult.withoutNewBelt(qualityTimeId, newStreak, newBelt);
        }
    }

    /**
     * Calculates the duration of a Quality Time event in minutes.
     * 
     * @param qualityTime the Quality Time entity
     * @return the duration in minutes
     */
    private int calculateDurationMinutes(QualityTime qualityTime) {
        if (qualityTime.getScheduledStart() == null || qualityTime.getScheduledEnd() == null) {
            return 30; // Default to 30 minutes if times are not set
        }
        long minutes = Duration.between(qualityTime.getScheduledStart(), qualityTime.getScheduledEnd()).toMinutes();
        return (int) Math.max(minutes, 1); // Minimum 1 minute
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Implementation details for cancellation (Requirement 3.7):</p>
     * <ol>
     *   <li>Load QualityTime by ID</li>
     *   <li>If status is already COMPLETED or CANCELLED, throw exception</li>
     *   <li>Delete Google Calendar event using googleCalendarEventId</li>
     *   <li>Update QualityTime status to CANCELLED</li>
     *   <li>Save the entity</li>
     * </ol>
     * 
     * <p>Error Handling:</p>
     * <ul>
     *   <li>If calendar event is already deleted externally, continue with database update</li>
     *   <li>Log warning but don't fail if calendar deletion fails</li>
     *   <li>Ensure database is always updated even if calendar fails</li>
     * </ul>
     */
    @Override
    public void cancelQualityTime(UUID qualityTimeId) {
        log.info("Cancelling Quality Time {}", qualityTimeId);

        // Step 1: Load QualityTime by ID
        QualityTime qualityTime = qualityTimeRepository.findById(qualityTimeId)
                .orElseThrow(() -> new IllegalArgumentException("Quality Time not found: " + qualityTimeId));

        // Step 2: Validate status - cannot cancel if already COMPLETED or CANCELLED
        QualityTimeStatus currentStatus = qualityTime.getStatus();
        if (currentStatus == QualityTimeStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot cancel Quality Time that is already COMPLETED: " + qualityTimeId);
        }
        if (currentStatus == QualityTimeStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot cancel Quality Time that is already CANCELLED: " + qualityTimeId);
        }

        // Step 3: Delete Google Calendar event if exists
        // Error handling: Continue with database update even if calendar deletion fails
        String calendarEventId = qualityTime.getGoogleCalendarEventId();
        if (calendarEventId != null && !calendarEventId.isEmpty()) {
            Father father = qualityTime.getFather();
            try {
                deleteCalendarEvent(father, calendarEventId);
                log.info("Deleted Google Calendar event {} for Quality Time {}", 
                        calendarEventId, qualityTimeId);
            } catch (HttpClientErrorException.NotFound e) {
                // Calendar event already deleted externally - this is OK
                log.warn("Google Calendar event {} was already deleted (404). " +
                        "Continuing with database update for Quality Time {}", 
                        calendarEventId, qualityTimeId);
            } catch (HttpClientErrorException.Gone e) {
                // Event was already deleted - this is OK
                log.warn("Google Calendar event {} was already deleted (410 Gone). " +
                        "Continuing with database update for Quality Time {}", 
                        calendarEventId, qualityTimeId);
            } catch (Exception e) {
                // Any other calendar error - log warning but don't fail
                // Database update must still proceed
                log.warn("Failed to delete Google Calendar event {} for Quality Time {}: {}. " +
                        "Continuing with database update.", 
                        calendarEventId, qualityTimeId, e.getMessage());
            }
            // Clear the calendar event ID since it's deleted (or we tried to delete it)
            qualityTime.setGoogleCalendarEventId(null);
        }

        // Step 4: Update QualityTime status to CANCELLED
        qualityTime.markCancelled();

        // Step 5: Save the entity
        qualityTimeRepository.save(qualityTime);
        
        log.info("Quality Time {} cancelled successfully", qualityTimeId);
    }

    @Override
    public Optional<UpcomingQualityTimeDto> getUpcomingQualityTime(Long fatherId) {
        log.debug("Getting upcoming Quality Time for father {}", fatherId);

        return qualityTimeRepository.findFirstByFatherIdAndStatusOrderByScheduledStartAsc(fatherId, QualityTimeStatus.SCHEDULED)
                .map(UpcomingQualityTimeDto::from);
    }

    @Override
    public List<UpcomingQualityTimeDto> getAllUpcomingQualityTime(Long fatherId) {
        log.debug("Getting all upcoming Quality Time for father {}", fatherId);

        return qualityTimeRepository.findByFatherIdAndStatus(fatherId, QualityTimeStatus.SCHEDULED)
                .stream()
                .sorted(Comparator.comparing(QualityTime::getScheduledStart))
                .map(UpcomingQualityTimeDto::from)
                .toList();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Implementation details for calendar sync (Requirement 3.7):</p>
     * <ol>
     *   <li>Load all SCHEDULED Quality Time records that have a google_calendar_event_id</li>
     *   <li>For each record, check if the calendar event still exists in Google Calendar</li>
     *   <li>If the event no longer exists (404 or 410 response), mark Quality Time as CANCELLED</li>
     *   <li>Clear the google_calendar_event_id as the event no longer exists</li>
     * </ol>
     * 
     * <p>Error Handling:</p>
     * <ul>
     *   <li>If Google Calendar is not configured, return 0 (nothing to sync)</li>
     *   <li>If access token cannot be obtained, skip sync (return 0)</li>
     *   <li>For each event, handle API errors gracefully - only mark as deleted on 404/410</li>
     * </ul>
     */
    @Override
    public int syncExternallyDeletedEvents(Long fatherId) {
        log.info("Syncing externally deleted events for father {}", fatherId);

        // Step 1: Load father and check calendar configuration
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));

        if (!father.hasGoogleCalendarConfigured()) {
            log.debug("Calendar not configured for father {}, skipping sync", fatherId);
            return 0;
        }

        // Step 2: Get valid access token
        String accessToken = getValidAccessToken(father);
        if (accessToken == null) {
            log.warn("Could not get access token for father {}, skipping sync", fatherId);
            return 0;
        }

        // Step 3: Load all SCHEDULED Quality Time records with calendar event IDs
        List<QualityTime> scheduledWithCalendarEvents = 
                qualityTimeRepository.findScheduledWithCalendarEventByFatherId(fatherId);

        if (scheduledWithCalendarEvents.isEmpty()) {
            log.debug("No scheduled Quality Time with calendar events for father {}", fatherId);
            return 0;
        }

        log.debug("Found {} scheduled Quality Time events with calendar IDs for father {}", 
                scheduledWithCalendarEvents.size(), fatherId);

        // Step 4: Check each event and mark as cancelled if deleted
        int cancelledCount = 0;
        String calendarId = father.getGoogleCalendarId() != null 
                ? father.getGoogleCalendarId() : "primary";

        for (QualityTime qualityTime : scheduledWithCalendarEvents) {
            String eventId = qualityTime.getGoogleCalendarEventId();
            boolean eventExists = checkCalendarEventExists(accessToken, calendarId, eventId);
            
            if (!eventExists) {
                log.info("Calendar event {} not found in Google Calendar, marking Quality Time {} as CANCELLED",
                        eventId, qualityTime.getId());
                
                // Mark as cancelled and clear the calendar event ID
                qualityTime.markCancelled();
                qualityTime.setGoogleCalendarEventId(null);
                qualityTimeRepository.save(qualityTime);
                cancelledCount++;
            }
        }

        log.info("Sync complete for father {}: {} events marked as CANCELLED due to external deletion",
                fatherId, cancelledCount);
        return cancelledCount;
    }

    /**
     * Checks if a Google Calendar event exists.
     * 
     * @param accessToken the OAuth access token
     * @param calendarId the calendar ID (or "primary")
     * @param eventId the event ID to check
     * @return true if the event exists, false if it was deleted (404/410) or cannot be verified
     */
    private boolean checkCalendarEventExists(String accessToken, String calendarId, String eventId) {
        try {
            String url = GOOGLE_CALENDAR_API + "/calendars/" +
                    URLEncoder.encode(calendarId, StandardCharsets.UTF_8) +
                    "/events/" + eventId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            // If we get a 2xx response, the event exists
            return response.getStatusCode().is2xxSuccessful();

        } catch (HttpClientErrorException.NotFound e) {
            // 404 - Event was deleted
            log.debug("Calendar event {} not found (404)", eventId);
            return false;
        } catch (HttpClientErrorException.Gone e) {
            // 410 - Event was permanently deleted
            log.debug("Calendar event {} is gone (410)", eventId);
            return false;
        } catch (Exception e) {
            // Other errors - log warning but assume event still exists to be safe
            // We don't want to cancel events due to transient API errors
            log.warn("Error checking calendar event {}: {}. Assuming event exists.", 
                    eventId, e.getMessage());
            return true;
        }
    }

    // ─── Private Helper Methods ───────────────────────────────────────────

    /**
     * Creates a Google Calendar event for a Quality Time session.
     *
     * @param father the father whose calendar to use
     * @param child the child the Quality Time is with
     * @param startTime the start time
     * @param endTime the end time
     * @return the calendar event ID, or null if creation fails
     */
    private String createCalendarEvent(Father father, Child child, Instant startTime, Instant endTime) {
        String accessToken = getValidAccessToken(father);
        if (accessToken == null) {
            log.warn("Could not get valid access token for father {}", father.getId());
            return null;
        }

        try {
            String timezone = father.getTimezone() != null ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE;
            String locale = father.getLocale() != null ? father.getLocale() : AppConstants.DEFAULT_LOCALE;

            Map<String, Object> event = buildCalendarEvent(child.getName(), startTime, endTime, timezone, locale);

            String calendarId = father.getGoogleCalendarId() != null ? father.getGoogleCalendarId() : "primary";
            String url = GOOGLE_CALENDAR_API + "/calendars/" +
                    URLEncoder.encode(calendarId, StandardCharsets.UTF_8) + "/events";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                return responseJson.get("id").asText();
            }

        } catch (Exception e) {
            log.error("Error creating calendar event: {}", e.getMessage());
            throw new RuntimeException("Calendar event creation failed", e);
        }

        return null;
    }

    /**
     * Deletes a Google Calendar event.
     *
     * @param father the father whose calendar contains the event
     * @param eventId the ID of the event to delete
     */
    private void deleteCalendarEvent(Father father, String eventId) {
        if (!father.hasGoogleCalendarConfigured()) {
            log.debug("Calendar not configured for father {}, skipping delete", father.getId());
            return;
        }

        String accessToken = getValidAccessToken(father);
        if (accessToken == null) {
            log.warn("Could not get valid access token for father {}", father.getId());
            throw new RuntimeException("Could not obtain valid access token for calendar deletion");
        }

        String calendarId = father.getGoogleCalendarId() != null ? father.getGoogleCalendarId() : "primary";
        String url = GOOGLE_CALENDAR_API + "/calendars/" +
                URLEncoder.encode(calendarId, StandardCharsets.UTF_8) +
                "/events/" + eventId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
    }

    /**
     * Builds a Google Calendar event object.
     */
    private Map<String, Object> buildCalendarEvent(
            String childName,
            Instant startTime,
            Instant endTime,
            String timezone,
            String locale) {

        Map<String, Object> event = new HashMap<>();

        // Title (Requirement 3.3: "👨‍👧 Quality Time with [Child Name]")
        if ("he".equals(locale)) {
            event.put("summary", "👨‍👧 זמן איכות עם " + childName);
        } else {
            event.put("summary", "👨‍👧 Quality Time with " + childName);
        }

        // Description (Requirement 3.3)
        if ("he".equals(locale)) {
            event.put("description", "Dad Coach - זמן איכות מתוכנן. תהנו מהרגע יחד!");
        } else {
            event.put("description", "Dad Coach scheduled Quality Time — enjoy your moment together!");
        }

        // Time
        ZonedDateTime startZdt = startTime.atZone(ZoneId.of(timezone));
        ZonedDateTime endZdt = endTime.atZone(ZoneId.of(timezone));

        Map<String, String> start = new HashMap<>();
        start.put("dateTime", startZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        start.put("timeZone", timezone);
        event.put("start", start);

        Map<String, String> end = new HashMap<>();
        end.put("dateTime", endZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        end.put("timeZone", timezone);
        event.put("end", end);

        // Reminders (Requirement 3.3: 1 hour before, 15 minutes before)
        Map<String, Object> reminders = new HashMap<>();
        reminders.put("useDefault", false);
        reminders.put("overrides", new Map[]{
                Map.of("method", "popup", "minutes", 60),
                Map.of("method", "popup", "minutes", 15)
        });
        event.put("reminders", reminders);

        // Color: Green (colorId 10) (Requirement 3.3)
        event.put("colorId", "10");

        return event;
    }

    /**
     * Gets a valid access token for the father, refreshing if necessary.
     */
    private String getValidAccessToken(Father father) {
        if (!father.needsTokenRefresh()) {
            return father.getGoogleAccessToken();
        }

        // Refresh the token
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("refresh_token", father.getGoogleRefreshToken());
            params.add("grant_type", "refresh_token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    GOOGLE_TOKEN_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode tokens = objectMapper.readTree(response.getBody());

                String newAccessToken = tokens.get("access_token").asText();
                int expiresIn = tokens.get("expires_in").asInt();

                father.setGoogleAccessToken(newAccessToken);
                father.setGoogleTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
                fatherRepository.save(father);

                return newAccessToken;
            }

        } catch (Exception e) {
            log.error("Failed to refresh access token for father {}: {}",
                    father.getId(), e.getMessage());
        }

        return null;
    }
}
