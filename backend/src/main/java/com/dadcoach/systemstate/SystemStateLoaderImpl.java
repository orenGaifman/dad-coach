package com.dadcoach.systemstate;

import com.dadcoach.common.AppConstants;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.conversation.MessageLog;
import com.dadcoach.domain.conversation.MessageLogRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.weeklygoal.WeeklyGoalStatus;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WelcomeStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of SystemStateLoader that loads complete system state from database
 * and external services.
 * 
 * <p>This class implements the Read Before Write principle, ensuring that all relevant
 * state is loaded from authoritative sources before any action is taken.</p>
 * 
 * <p>Implements Requirements 2.1:</p>
 * <ul>
 *   <li>Load father profile from database (name, children, preferences, locale, timezone)</li>
 *   <li>Load current workflow state from database</li>
 *   <li>Load Google Calendar events for the next 7 days (if connected)</li>
 *   <li>Load scheduled Quality Time events from database</li>
 *   <li>Load dashboard metrics (belt, streak, achievements) from database</li>
 *   <li>Load conversation context (last 10 messages)</li>
 * </ul>
 * 
 * @see SystemState
 * @see SystemStateLoader
 */
@Service
public class SystemStateLoaderImpl implements SystemStateLoader {

    private static final Logger log = LoggerFactory.getLogger(SystemStateLoaderImpl.class);

    private static final String GOOGLE_CALENDAR_API = "https://www.googleapis.com/calendar/v3";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    private static final int DEFAULT_DAYS_AHEAD = 7;
    private static final int MAX_DAYS_AHEAD = 14;
    private static final int MIN_SLOT_DURATION_MINUTES = 30;
    private static final int DEFAULT_ACTIVITY_START_HOUR = 6;  // 6 AM
    private static final int DEFAULT_ACTIVITY_END_HOUR = 22;   // 10 PM
    private static final int RECENT_MESSAGES_LIMIT = 10;        // Load last 10 messages for context

    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final QualityTimeRepository qualityTimeRepository;
    private final MessageLogRepository messageLogRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.calendar.client-id:}")
    private String clientId;

    @Value("${google.calendar.client-secret:}")
    private String clientSecret;

    public SystemStateLoaderImpl(
            FatherRepository fatherRepository,
            ChildRepository childRepository,
            QualityTimeRepository qualityTimeRepository,
            MessageLogRepository messageLogRepository,
            WeeklyGoalRepository weeklyGoalRepository) {
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.qualityTimeRepository = qualityTimeRepository;
        this.messageLogRepository = messageLogRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SystemState loadState(UUID fatherId) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }

        log.debug("Loading system state for father: {}", fatherId);

        // Load father by UUID - we need to convert UUID to Long since Father.id is Long
        // The conversation entity uses UUID for fatherId, but Father entity uses Long id
        // We need to find the father differently - by checking if there's a mapping
        Father father = findFatherByUuid(fatherId);
        if (father == null) {
            throw new ResourceNotFoundException("Father", fatherId);
        }

        // Load all components of the system state
        SystemState.FatherProfile fatherProfile = loadFatherProfile(father);
        List<SystemState.CalendarEvent> calendarEvents = loadCalendarEvents(father, DEFAULT_DAYS_AHEAD);
        List<SystemState.QualityTimeEvent> qualityTimeEvents = loadQualityTimeEvents(father);
        SystemState.DashboardMetrics dashboardMetrics = loadDashboardMetrics(father);
        List<SystemState.ConversationMessage> conversationContext = loadConversationContext(father);
        SystemState.WeeklyGoalInfo weeklyGoalInfo = loadWeeklyGoalInfo(father);

        log.debug("System state loaded successfully for father: {}", fatherId);

        return new SystemState(
                fatherProfile,
                father.getCurrentWorkflowState(),
                calendarEvents,
                qualityTimeEvents,
                dashboardMetrics,
                conversationContext,
                weeklyGoalInfo
        );
    }

    @Override
    public List<AvailableSlot> loadAvailableSlots(UUID fatherId, int daysAhead) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (daysAhead < 1 || daysAhead > MAX_DAYS_AHEAD) {
            throw new IllegalArgumentException("daysAhead must be between 1 and " + MAX_DAYS_AHEAD);
        }

        Father father = findFatherByUuid(fatherId);
        if (father == null) {
            throw new ResourceNotFoundException("Father", fatherId);
        }

        // If Google Calendar is not connected, return empty list
        if (!father.hasGoogleCalendarConfigured()) {
            log.debug("Google Calendar not configured for father {}, returning empty slots", fatherId);
            return List.of();
        }

        // Load calendar events and quality time events
        List<SystemState.CalendarEvent> calendarEvents = loadCalendarEvents(father, daysAhead);
        List<QualityTime> qualityTimeEvents = qualityTimeRepository.findByFatherIdAndStatus(
                father.getId(), 
                com.dadcoach.qualitytime.QualityTimeStatus.SCHEDULED
        );

        // Calculate available slots
        return calculateAvailableSlots(father, calendarEvents, qualityTimeEvents, daysAhead);
    }

    // ─── Private Helper Methods ───────────────────────────────────────────

    /**
     * Finds a Father by UUID. Since Father.id is Long, we need to look up
     * through the conversation's fatherId (UUID) mapping or directly by Long id.
     * This method attempts to parse the UUID as a numeric ID first.
     */
    private Father findFatherByUuid(UUID fatherId) {
        // The Conversation entity uses UUID for fatherId, but Father entity uses Long id
        // Try to find by Long id if the UUID represents a numeric value
        try {
            // Convert UUID to string and try to extract the least significant bits
            // which might represent the father's Long id
            long numericId = fatherId.getLeastSignificantBits();
            // Note: numericId can be negative for random UUIDs, but Father IDs from BIGSERIAL
            // are always positive. We still try the lookup for both cases to support test scenarios.
            Optional<Father> father = fatherRepository.findById(numericId);
            if (father.isPresent()) {
                return father.get();
            }
        } catch (Exception e) {
            log.debug("Could not convert UUID to numeric father ID: {}", fatherId);
        }

        // If direct lookup fails, search all fathers (this is inefficient but a fallback)
        // In a real system, you'd want a proper UUID-to-Father mapping
        log.warn("Falling back to inefficient father lookup for UUID: {}", fatherId);
        
        // Try to find by iterating (not ideal, but works for now)
        List<Father> allFathers = fatherRepository.findAll();
        for (Father father : allFathers) {
            // Check if the father's ID matches the UUID's numeric representation
            if (father.getId() != null && father.getId().equals(fatherId.getLeastSignificantBits())) {
                return father;
            }
        }
        
        return null;
    }

    /**
     * Loads the current weekly goal information for the father.
     * 
     * @param father the father whose weekly goal to load
     * @return WeeklyGoalInfo, or noGoal() if no goal is set for this week
     */
    private SystemState.WeeklyGoalInfo loadWeeklyGoalInfo(Father father) {
        try {
            // Get current week's start date (Sunday)
            LocalDate today = LocalDate.now();
            LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() % 7);
            
            // Find active weekly goal for current week
            Optional<WeeklyGoal> activeGoal = weeklyGoalRepository.findByFatherIdAndStatus(
                    father.getId(), WeeklyGoalStatus.ACTIVE);
            
            if (activeGoal.isEmpty()) {
                // Also check for the current week's goal even if not ACTIVE
                activeGoal = weeklyGoalRepository.findByFatherIdAndWeekStartDate(
                        father.getId(), weekStart);
            }
            
            if (activeGoal.isPresent()) {
                WeeklyGoal goal = activeGoal.get();
                return new SystemState.WeeklyGoalInfo(
                        true,
                        goal.getTargetHours(),  // Using targetHours as number of quality times
                        goal.getCompletedCount(),
                        goal.getScheduledCount(),
                        goal.getWeekStartDate()
                );
            }
            
            return SystemState.WeeklyGoalInfo.noGoal();
        } catch (Exception e) {
            log.warn("Failed to load weekly goal info for father {}: {}", father.getId(), e.getMessage());
            return SystemState.WeeklyGoalInfo.noGoal();
        }
    }

    /**
     * Loads recent conversation messages for the father from the message log.
     * Messages are returned in chronological order (oldest first) for AI context.
     */
    private List<SystemState.ConversationMessage> loadConversationContext(Father father) {
        try {
            // Load recent messages from DB (returns newest first)
            List<MessageLog> recentMessages = messageLogRepository.findRecentByFatherId(
                    father.getId(), RECENT_MESSAGES_LIMIT);

            // Reverse to get chronological order (oldest first) for AI context
            Collections.reverse(recentMessages);

            return recentMessages.stream()
                    .map(msg -> new SystemState.ConversationMessage(
                            UUID.randomUUID(),  // Generate a UUID for the message
                            msg.getDirection() == MessageLog.Direction.INBOUND ? "FATHER" : "COACH",
                            msg.getContent(),
                            msg.getCreatedAt()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to load conversation context for father {}: {}", father.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Loads the father profile including children information.
     */
    private SystemState.FatherProfile loadFatherProfile(Father father) {
        List<Child> children = childRepository.findByFatherId(father.getId());

        List<SystemState.ChildInfo> childInfos = children.stream()
                .filter(child -> "ACTIVE".equals(child.getStatus()))
                .map(this::mapToChildInfo)
                .collect(Collectors.toList());

        return new SystemState.FatherProfile(
                father.getId(),
                father.getDisplayName(),
                father.getPhone(),
                childInfos,
                father.getLocale() != null ? father.getLocale() : "en",
                father.getTimezone() != null ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE,
                father.getPreferredCoachingTime(),
                father.hasGoogleCalendarConfigured(),
                father.getWelcomeStep()
        );
    }

    /**
     * Maps a Child entity to a ChildInfo record.
     */
    private SystemState.ChildInfo mapToChildInfo(Child child) {
        return new SystemState.ChildInfo(
                child.getId(),
                child.getName(),
                child.getBirthDate(),
                child.getAge(),
                child.getGender(),
                child.getInterests() != null ? child.getInterests() : List.of()
        );
    }

    /**
     * Loads Google Calendar events for the next N days.
     */
    private List<SystemState.CalendarEvent> loadCalendarEvents(Father father, int daysAhead) {
        if (!father.hasGoogleCalendarConfigured()) {
            log.debug("Google Calendar not configured for father {}", father.getId());
            return List.of();
        }

        try {
            String accessToken = getValidAccessToken(father);
            if (accessToken == null) {
                log.warn("Could not get valid access token for father {}", father.getId());
                return List.of();
            }

            return fetchCalendarEvents(father, accessToken, daysAhead);
        } catch (Exception e) {
            log.error("Failed to load calendar events for father {}: {}", father.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches calendar events from Google Calendar API.
     */
    private List<SystemState.CalendarEvent> fetchCalendarEvents(Father father, String accessToken, int daysAhead) {
        String timezone = father.getTimezone() != null ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE;
        ZoneId zoneId = ZoneId.of(timezone);

        Instant now = Instant.now();
        Instant endTime = now.plus(Duration.ofDays(daysAhead));

        String timeMin = now.toString();
        String timeMax = endTime.toString();

        String calendarId = father.getGoogleCalendarId() != null ? father.getGoogleCalendarId() : "primary";
        String url = GOOGLE_CALENDAR_API + "/calendars/" +
                URLEncoder.encode(calendarId, StandardCharsets.UTF_8) +
                "/events?timeMin=" + URLEncoder.encode(timeMin, StandardCharsets.UTF_8) +
                "&timeMax=" + URLEncoder.encode(timeMax, StandardCharsets.UTF_8) +
                "&singleEvents=true&orderBy=startTime";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseCalendarEvents(response.getBody());
            } else {
                log.warn("Calendar API returned non-success status: {}, fatherId={}", 
                        response.getStatusCode(), father.getId());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Log detailed error information for debugging
            log.error("Error fetching calendar events for father {}: status={}, body={}", 
                    father.getId(), e.getStatusCode(), e.getResponseBodyAsString());
            
            // If it's a 401 Unauthorized, the token might be invalid - clear it
            if (e.getStatusCode().value() == 401) {
                log.warn("Calendar access token invalid for father {}, clearing token", father.getId());
                father.setGoogleAccessToken(null);
                father.setGoogleTokenExpiresAt(null);
                fatherRepository.save(father);
            }
            // If it's a 400 Bad Request, disable calendar integration to prevent repeated errors
            else if (e.getStatusCode().value() == 400) {
                log.warn("Calendar API 400 error for father {}, disabling calendar integration - calendarId='{}', timeMin='{}', timeMax='{}'", 
                        father.getId(), calendarId, timeMin, timeMax);
                // Clear all calendar credentials to disable integration
                father.setGoogleAccessToken(null);
                father.setGoogleRefreshToken(null);
                father.setGoogleTokenExpiresAt(null);
                father.setGoogleCalendarId(null);
                fatherRepository.save(father);
            }
        } catch (Exception e) {
            log.error("Error fetching calendar events for father {}: {}", father.getId(), e.getMessage());
        }

        return List.of();
    }

    /**
     * Parses the Google Calendar API response into CalendarEvent records.
     */
    private List<SystemState.CalendarEvent> parseCalendarEvents(String responseBody) {
        List<SystemState.CalendarEvent> events = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.get("items");

            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    String eventId = item.has("id") ? item.get("id").asText() : null;
                    String title = item.has("summary") ? item.get("summary").asText() : "No title";

                    // Parse start time
                    JsonNode startNode = item.get("start");
                    Instant startTime = null;
                    boolean allDay = false;

                    if (startNode != null) {
                        if (startNode.has("dateTime")) {
                            startTime = Instant.parse(startNode.get("dateTime").asText());
                        } else if (startNode.has("date")) {
                            // All-day event
                            allDay = true;
                            LocalDate date = LocalDate.parse(startNode.get("date").asText());
                            startTime = date.atStartOfDay(ZoneOffset.UTC).toInstant();
                        }
                    }

                    // Parse end time
                    JsonNode endNode = item.get("end");
                    Instant endTime = null;

                    if (endNode != null) {
                        if (endNode.has("dateTime")) {
                            endTime = Instant.parse(endNode.get("dateTime").asText());
                        } else if (endNode.has("date")) {
                            LocalDate date = LocalDate.parse(endNode.get("date").asText());
                            endTime = date.atStartOfDay(ZoneOffset.UTC).toInstant();
                        }
                    }

                    if (eventId != null && startTime != null && endTime != null) {
                        events.add(new SystemState.CalendarEvent(eventId, title, startTime, endTime, allDay));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing calendar events: {}", e.getMessage());
        }

        return events;
    }

    /**
     * Loads Quality Time events for the father.
     */
    private List<SystemState.QualityTimeEvent> loadQualityTimeEvents(Father father) {
        List<QualityTime> qualityTimes = qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(father.getId());

        return qualityTimes.stream()
                .map(qt -> mapToQualityTimeEvent(qt, father))
                .collect(Collectors.toList());
    }

    /**
     * Maps a QualityTime entity to a QualityTimeEvent record.
     */
    private SystemState.QualityTimeEvent mapToQualityTimeEvent(QualityTime qt, Father father) {
        String childName = "Unknown";

        // Get child name
        try {
            if (qt.getChild() != null) {
                childName = qt.getChild().getName();
            } else if (qt.getChildId() != null) {
                Optional<Child> child = childRepository.findById(qt.getChildId());
                childName = child.map(Child::getName).orElse("Unknown");
            }
        } catch (Exception e) {
            log.debug("Could not load child name for quality time {}", qt.getId());
        }

        return new SystemState.QualityTimeEvent(
                qt.getId(),
                qt.getChildId(),
                childName,
                qt.getScheduledStart(),
                qt.getScheduledEnd(),
                qt.getStatus().name(),
                qt.getGoogleCalendarEventId(),
                qt.getCompletedAt(),
                qt.getCompletionNotes()
        );
    }

    /**
     * Loads dashboard metrics for the father.
     */
    private SystemState.DashboardMetrics loadDashboardMetrics(Father father) {
        Belt currentBelt = father.getCurrentBelt() != null ? father.getCurrentBelt() : Belt.WHITE;
        int totalCompleted = father.getTotalQualityTimesCompleted();
        int currentStreak = father.getQualityTimeStreak();
        int longestStreak = father.getQualityTimeLongestStreak();

        // Calculate progress to next belt
        int progressToNextBelt = 0;
        int qualityTimesToNextBelt = 0;

        Belt nextBelt = currentBelt.getNextBelt();
        if (nextBelt != null) {
            int currentMin = currentBelt.getMinCompletions();
            int nextMin = nextBelt.getMinCompletions();
            int range = nextMin - currentMin;
            int progress = totalCompleted - currentMin;

            progressToNextBelt = range > 0 ? Math.min(100, (progress * 100) / range) : 100;
            qualityTimesToNextBelt = Math.max(0, nextMin - totalCompleted);
        } else {
            // Already at BLACK belt
            progressToNextBelt = 100;
            qualityTimesToNextBelt = 0;
        }

        // Load recent achievements (placeholder - achievements not fully implemented yet)
        List<SystemState.Achievement> recentAchievements = loadRecentAchievements(father);

        return new SystemState.DashboardMetrics(
                currentBelt,
                currentStreak,
                longestStreak,
                totalCompleted,
                recentAchievements,
                progressToNextBelt,
                qualityTimesToNextBelt
        );
    }

    /**
     * Loads recent achievements for the father.
     * Currently returns a placeholder list - full achievement system to be implemented.
     */
    private List<SystemState.Achievement> loadRecentAchievements(Father father) {
        List<SystemState.Achievement> achievements = new ArrayList<>();

        // Add belt achievement if applicable
        if (father.getCurrentBelt() != Belt.WHITE) {
            achievements.add(new SystemState.Achievement(
                    "belt-" + father.getCurrentBelt().name().toLowerCase(),
                    father.getCurrentBelt().name() + " Belt Earned",
                    "Reached " + father.getCurrentBelt().name() + " belt level",
                    father.getCreatedAt() // Placeholder - would need actual earned date
            ));
        }

        // Add streak achievement if applicable
        if (father.getQualityTimeLongestStreak() >= 5) {
            achievements.add(new SystemState.Achievement(
                    "streak-5",
                    "5 Quality Time Streak",
                    "Completed 5 Quality Times in a row",
                    father.getCreatedAt() // Placeholder
            ));
        }

        // Add first quality time achievement
        if (father.getTotalQualityTimesCompleted() > 0) {
            achievements.add(new SystemState.Achievement(
                    "first-quality-time",
                    "First Step",
                    "Completed your first Quality Time",
                    father.getCreatedAt() // Placeholder
            ));
        }

        return achievements;
    }

    /**
     * Calculates available time slots based on calendar events and quality time events.
     * 
     * <p>Implements Requirement 2.3:</p>
     * <ul>
     *   <li>Read Google Calendar events for the specified period</li>
     *   <li>Identify busy periods from all calendar events (including all-day events)</li>
     *   <li>Calculate available slots of at least 30 minutes</li>
     *   <li>Exclude times outside preferred activity hours (6am-10pm local time)</li>
     *   <li>Return top 5 slots ordered by proximity to current time</li>
     * </ul>
     * 
     * @param father the father for whom to calculate slots
     * @param calendarEvents Google Calendar events for the period
     * @param qualityTimeEvents scheduled Quality Time events
     * @param daysAhead number of days to look ahead
     * @return list of up to 5 available slots, ordered by proximity
     */
    private List<AvailableSlot> calculateAvailableSlots(
            Father father,
            List<SystemState.CalendarEvent> calendarEvents,
            List<QualityTime> qualityTimeEvents,
            int daysAhead) {

        String timezone = father.getTimezone() != null ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE;
        ZoneId zoneId = ZoneId.of(timezone);
        Instant now = Instant.now();

        // Merge all busy periods
        List<BusyPeriod> busyPeriods = new ArrayList<>();

        // Add calendar events as busy periods
        for (SystemState.CalendarEvent event : calendarEvents) {
            if (event.allDay()) {
                // For all-day events, block the preferred activity hours for that day
                // Convert the event's start date to local time and block 6am-10pm
                ZonedDateTime eventDayStart = event.startTime().atZone(zoneId)
                        .withHour(DEFAULT_ACTIVITY_START_HOUR)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);
                ZonedDateTime eventDayEnd = eventDayStart.withHour(DEFAULT_ACTIVITY_END_HOUR);
                busyPeriods.add(new BusyPeriod(eventDayStart.toInstant(), eventDayEnd.toInstant()));
            } else {
                busyPeriods.add(new BusyPeriod(event.startTime(), event.endTime()));
            }
        }

        // Add scheduled Quality Time as busy periods
        for (QualityTime qt : qualityTimeEvents) {
            busyPeriods.add(new BusyPeriod(qt.getScheduledStart(), qt.getScheduledEnd()));
        }

        // Sort busy periods by start time and merge overlapping ones
        List<BusyPeriod> mergedBusyPeriods = mergeOverlappingPeriods(busyPeriods);

        // Calculate available slots
        List<AvailableSlot> availableSlots = new ArrayList<>();

        // Iterate through each day
        for (int day = 0; day < daysAhead; day++) {
            ZonedDateTime dayStart = ZonedDateTime.now(zoneId).plusDays(day)
                    .withHour(DEFAULT_ACTIVITY_START_HOUR)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);

            ZonedDateTime dayEnd = dayStart.withHour(DEFAULT_ACTIVITY_END_HOUR);

            // Skip if day end is in the past
            if (dayEnd.toInstant().isBefore(now)) {
                continue;
            }

            // Adjust day start if it's in the past
            if (dayStart.toInstant().isBefore(now)) {
                dayStart = ZonedDateTime.ofInstant(now, zoneId);
                // Round up to next 30 minute mark for cleaner slots
                int minute = dayStart.getMinute();
                if (minute > 0 && minute <= 30) {
                    dayStart = dayStart.withMinute(30).withSecond(0).withNano(0);
                } else if (minute > 30) {
                    dayStart = dayStart.plusHours(1).withMinute(0).withSecond(0).withNano(0);
                }
                
                // If after rounding we're past the activity hours, skip this day
                if (dayStart.getHour() >= DEFAULT_ACTIVITY_END_HOUR) {
                    continue;
                }
            }

            // Find gaps in this day
            List<AvailableSlot> daySlots = findAvailableSlots(
                    dayStart.toInstant(),
                    dayEnd.toInstant(),
                    mergedBusyPeriods
            );

            availableSlots.addAll(daySlots);
        }

        // Sort by proximity and return top 5
        return availableSlots.stream()
                .sorted(Comparator.comparing(AvailableSlot::startTime))
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Merges overlapping busy periods into non-overlapping intervals.
     * This ensures correct gap detection when events overlap.
     * 
     * @param periods the list of potentially overlapping busy periods
     * @return a sorted list of merged, non-overlapping busy periods
     */
    private List<BusyPeriod> mergeOverlappingPeriods(List<BusyPeriod> periods) {
        if (periods.isEmpty()) {
            return List.of();
        }

        // Sort by start time
        List<BusyPeriod> sorted = new ArrayList<>(periods);
        sorted.sort(Comparator.comparing(bp -> bp.start));

        List<BusyPeriod> merged = new ArrayList<>();
        BusyPeriod current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            BusyPeriod next = sorted.get(i);

            // Check if current and next overlap or are adjacent
            if (!next.start.isAfter(current.end)) {
                // Merge: extend current's end if needed
                Instant newEnd = current.end.isAfter(next.end) ? current.end : next.end;
                current = new BusyPeriod(current.start, newEnd);
            } else {
                // No overlap: add current and move to next
                merged.add(current);
                current = next;
            }
        }

        // Don't forget the last period
        merged.add(current);

        return merged;
    }

    /**
     * Finds available slots within a time range, avoiding busy periods.
     * 
     * <p>This method scans the time range from start to end and identifies
     * gaps between busy periods that are at least {@link #MIN_SLOT_DURATION_MINUTES}
     * minutes long.</p>
     * 
     * @param rangeStart the start of the time range to search
     * @param rangeEnd the end of the time range to search
     * @param busyPeriods sorted, merged list of busy periods (must not overlap)
     * @return list of available slots within the range
     */
    private List<AvailableSlot> findAvailableSlots(
            Instant rangeStart,
            Instant rangeEnd,
            List<BusyPeriod> busyPeriods) {

        List<AvailableSlot> slots = new ArrayList<>();
        Instant currentStart = rangeStart;

        for (BusyPeriod busy : busyPeriods) {
            // Skip busy periods that end before or at our range start
            if (!busy.end.isAfter(currentStart)) {
                continue;
            }

            // Stop if busy period starts at or after our range end
            if (!busy.start.isBefore(rangeEnd)) {
                break;
            }

            // Check for a gap before this busy period
            if (busy.start.isAfter(currentStart)) {
                // The gap ends at either the busy period start or our range end
                Instant gapEnd = busy.start.isBefore(rangeEnd) ? busy.start : rangeEnd;
                Duration gapDuration = Duration.between(currentStart, gapEnd);

                if (gapDuration.toMinutes() >= MIN_SLOT_DURATION_MINUTES) {
                    slots.add(AvailableSlot.of(currentStart, gapEnd));
                }
            }

            // Move current start past this busy period
            if (busy.end.isAfter(currentStart)) {
                currentStart = busy.end;
            }
        }

        // Check for remaining gap after all busy periods
        if (currentStart.isBefore(rangeEnd)) {
            Duration remainingDuration = Duration.between(currentStart, rangeEnd);
            if (remainingDuration.toMinutes() >= MIN_SLOT_DURATION_MINUTES) {
                slots.add(AvailableSlot.of(currentStart, rangeEnd));
            }
        }

        return slots;
    }

    /**
     * Gets a valid access token for the father, refreshing if necessary.
     */
    private String getValidAccessToken(Father father) {
        if (!father.needsTokenRefresh()) {
            return father.getGoogleAccessToken();
        }

        // Check if we have credentials configured
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            log.warn("Google Calendar client credentials not configured, cannot refresh token");
            return null;
        }

        // Check if we have a refresh token
        if (father.getGoogleRefreshToken() == null || father.getGoogleRefreshToken().isEmpty()) {
            log.warn("No refresh token available for father {}", father.getId());
            return null;
        }

        // Refresh the token
        try {
            org.springframework.util.LinkedMultiValueMap<String, String> params = 
                    new org.springframework.util.LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("refresh_token", father.getGoogleRefreshToken());
            params.add("grant_type", "refresh_token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = 
                    new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    GOOGLE_TOKEN_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode tokens = objectMapper.readTree(response.getBody());

                String newAccessToken = tokens.get("access_token").asText();
                int expiresIn = tokens.get("expires_in").asInt();

                father.setGoogleAccessToken(newAccessToken);
                father.setGoogleTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
                fatherRepository.save(father);

                log.debug("Successfully refreshed access token for father {}", father.getId());
                return newAccessToken;
            } else {
                log.error("Failed to refresh token for father {}: status={}", 
                        father.getId(), response.getStatusCode());
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to refresh access token for father {}: status={}, body={}",
                    father.getId(), e.getStatusCode(), e.getResponseBodyAsString());
            
            // If refresh token is invalid (400 or 401), mark calendar as disconnected
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 401) {
                log.warn("Refresh token invalid for father {}, disabling calendar integration", father.getId());
                father.setGoogleCalendarEnabled(false);
                father.setGoogleAccessToken(null);
                father.setGoogleTokenExpiresAt(null);
                // Keep the refresh token in case user wants to reconnect
                fatherRepository.save(father);
            }
        } catch (Exception e) {
            log.error("Failed to refresh access token for father {}: {}",
                    father.getId(), e.getMessage());
        }

        return null;
    }

    /**
     * Helper record for busy periods during slot calculation.
     */
    private record BusyPeriod(Instant start, Instant end) {}
}
