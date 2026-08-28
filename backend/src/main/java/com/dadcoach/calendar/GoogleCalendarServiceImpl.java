package com.dadcoach.calendar;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;

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
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of Google Calendar integration.
 * 
 * Uses Google Calendar API to create/update/delete events for missions.
 * Manages OAuth2 tokens for fathers who connect their calendars.
 */
@Service
@Transactional
public class GoogleCalendarServiceImpl implements GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarServiceImpl.class);

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_CALENDAR_API = "https://www.googleapis.com/calendar/v3";
    private static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events";
    
    /** Keywords to identify Dad Coach related events in calendar. */
    private static final List<String> DAD_COACH_KEYWORDS = List.of(
        "dad coach", "אבא מאמן", "משימת אבא", "זמן איכות", "quality time",
        "dad mission", "🎯"
    );

    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final MissionRepository missionRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.calendar.client-id:}")
    private String clientId;

    @Value("${google.calendar.client-secret:}")
    private String clientSecret;

    @Value("${google.calendar.redirect-uri:}")
    private String redirectUri;

    public GoogleCalendarServiceImpl(FatherRepository fatherRepository,
                                     ChildRepository childRepository,
                                     MissionRepository missionRepository) {
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.missionRepository = missionRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<CalendarEvent> getUpcomingEvents(Father father, Instant from, Instant to, boolean filterDadCoachOnly) {
        if (!isCalendarConfigured(father)) {
            log.debug("Calendar not configured for father {}", father.getId());
            return Collections.emptyList();
        }

        try {
            String accessToken = getValidAccessToken(father);
            if (accessToken == null) {
                log.warn("Could not get valid access token for father {}", father.getId());
                return Collections.emptyList();
            }

            String calendarId = father.getGoogleCalendarId() != null ? 
                father.getGoogleCalendarId() : "primary";
            
            String url = GOOGLE_CALENDAR_API + "/calendars/" + 
                URLEncoder.encode(calendarId, StandardCharsets.UTF_8) + "/events" +
                "?timeMin=" + URLEncoder.encode(from.toString(), StandardCharsets.UTF_8) +
                "&timeMax=" + URLEncoder.encode(to.toString(), StandardCharsets.UTF_8) +
                "&singleEvents=true" +
                "&orderBy=startTime" +
                "&maxResults=50";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode items = root.get("items");
                
                if (items == null || !items.isArray()) {
                    return Collections.emptyList();
                }

                List<CalendarEvent> events = new ArrayList<>();
                for (JsonNode item : items) {
                    CalendarEvent event = parseCalendarEvent(item);
                    if (event != null) {
                        if (filterDadCoachOnly) {
                            if (isDadCoachEvent(event)) {
                                events.add(event);
                            }
                        } else {
                            events.add(event);
                        }
                    }
                }
                
                log.info("Fetched {} calendar events for father {} (filterDadCoach={})", 
                    events.size(), father.getId(), filterDadCoachOnly);
                return events;
            }

        } catch (Exception e) {
            log.error("Failed to fetch calendar events for father {}: {}", father.getId(), e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Parses a calendar event from Google Calendar API response.
     */
    private CalendarEvent parseCalendarEvent(JsonNode item) {
        try {
            String eventId = item.has("id") ? item.get("id").asText() : null;
            String title = item.has("summary") ? item.get("summary").asText() : "";
            String description = item.has("description") ? item.get("description").asText() : "";
            String location = item.has("location") ? item.get("location").asText() : null;
            
            // Parse start time
            Instant startTime = null;
            JsonNode startNode = item.get("start");
            if (startNode != null) {
                if (startNode.has("dateTime")) {
                    startTime = Instant.parse(normalizeDateTime(startNode.get("dateTime").asText()));
                } else if (startNode.has("date")) {
                    // All-day event - use start of day
                    String date = startNode.get("date").asText();
                    startTime = ZonedDateTime.parse(date + "T00:00:00Z", 
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC"))).toInstant();
                }
            }
            
            // Parse end time
            Instant endTime = null;
            JsonNode endNode = item.get("end");
            if (endNode != null) {
                if (endNode.has("dateTime")) {
                    endTime = Instant.parse(normalizeDateTime(endNode.get("dateTime").asText()));
                } else if (endNode.has("date")) {
                    String date = endNode.get("date").asText();
                    endTime = ZonedDateTime.parse(date + "T23:59:59Z", 
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC"))).toInstant();
                }
            }
            
            if (eventId == null || startTime == null) {
                return null;
            }
            
            return new CalendarEvent(eventId, title, description, startTime, endTime, location);
        } catch (Exception e) {
            log.debug("Failed to parse calendar event: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalizes a date-time string to ISO-8601 format that Instant can parse.
     */
    private String normalizeDateTime(String dateTime) {
        // Google returns format like "2024-01-15T10:00:00+02:00" or "2024-01-15T10:00:00Z"
        // Convert to format Instant can parse
        if (dateTime.contains("+") || dateTime.contains("-") && dateTime.lastIndexOf("-") > 7) {
            // Has timezone offset - convert to instant via ZonedDateTime
            return ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toString();
        }
        return dateTime;
    }

    /**
     * Checks if an event is a Dad Coach related event.
     */
    private boolean isDadCoachEvent(CalendarEvent event) {
        String searchText = (event.title() + " " + event.description()).toLowerCase();
        return DAD_COACH_KEYWORDS.stream()
            .anyMatch(keyword -> searchText.contains(keyword.toLowerCase()));
    }

    @Override
    public Optional<String> createMissionEvent(Mission mission) {
        Father father = mission.getFather();
        
        if (!isCalendarConfigured(father)) {
            log.debug("Calendar not configured for father {}", father.getId());
            return Optional.empty();
        }

        try {
            String accessToken = getValidAccessToken(father);
            if (accessToken == null) {
                log.warn("Could not get valid access token for father {}", father.getId());
                return Optional.empty();
            }

            Child child = childRepository.findById(mission.getChildId()).orElse(null);
            String childName = child != null ? child.getName() : "הילד";

            Map<String, Object> event = buildCalendarEvent(mission, father, childName);
            
            String calendarId = father.getGoogleCalendarId() != null ? 
                father.getGoogleCalendarId() : "primary";
            
            String url = GOOGLE_CALENDAR_API + "/calendars/" + 
                URLEncoder.encode(calendarId, StandardCharsets.UTF_8) + "/events";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String eventId = responseJson.get("id").asText();
                
                mission.setCalendarEventId(eventId);
                missionRepository.save(mission);
                
                log.info("Created calendar event {} for mission {}", eventId, mission.getId());
                return Optional.of(eventId);
            }

        } catch (Exception e) {
            log.error("Failed to create calendar event for mission {}: {}", 
                mission.getId(), e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public boolean updateMissionEvent(Mission mission) {
        if (mission.getCalendarEventId() == null) {
            // No existing event, create new one
            return createMissionEvent(mission).isPresent();
        }

        Father father = mission.getFather();
        if (!isCalendarConfigured(father)) {
            return false;
        }

        try {
            String accessToken = getValidAccessToken(father);
            if (accessToken == null) {
                return false;
            }

            Child child = childRepository.findById(mission.getChildId()).orElse(null);
            String childName = child != null ? child.getName() : "הילד";

            Map<String, Object> event = buildCalendarEvent(mission, father, childName);

            String calendarId = father.getGoogleCalendarId() != null ? 
                father.getGoogleCalendarId() : "primary";
            
            String url = GOOGLE_CALENDAR_API + "/calendars/" + 
                URLEncoder.encode(calendarId, StandardCharsets.UTF_8) + 
                "/events/" + mission.getCalendarEventId();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);
            restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

            log.info("Updated calendar event {} for mission {}", 
                mission.getCalendarEventId(), mission.getId());
            return true;

        } catch (Exception e) {
            log.error("Failed to update calendar event for mission {}: {}", 
                mission.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteMissionEvent(Mission mission) {
        if (mission.getCalendarEventId() == null) {
            return true; // Nothing to delete
        }

        Father father = mission.getFather();
        if (!isCalendarConfigured(father)) {
            return false;
        }

        try {
            String accessToken = getValidAccessToken(father);
            if (accessToken == null) {
                return false;
            }

            String calendarId = father.getGoogleCalendarId() != null ? 
                father.getGoogleCalendarId() : "primary";
            
            String url = GOOGLE_CALENDAR_API + "/calendars/" + 
                URLEncoder.encode(calendarId, StandardCharsets.UTF_8) + 
                "/events/" + mission.getCalendarEventId();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);

            mission.setCalendarEventId(null);
            missionRepository.save(mission);

            log.info("Deleted calendar event for mission {}", mission.getId());
            return true;

        } catch (Exception e) {
            log.error("Failed to delete calendar event for mission {}: {}", 
                mission.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public String getAuthorizationUrl(Long fatherId) {
        return getAuthorizationUrl(fatherId, null);
    }

    @Override
    public String getAuthorizationUrl(Long fatherId, String redirectUrl) {
        // State format: "fatherId" or "fatherId|redirectUrl"
        String state = redirectUrl != null && !redirectUrl.isEmpty() 
            ? fatherId + "|" + redirectUrl
            : String.valueOf(fatherId);
            
        return GOOGLE_AUTH_URL + "?" +
            "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
            "&redirect_uri=" + URLEncoder.encode(this.redirectUri, StandardCharsets.UTF_8) +
            "&response_type=code" +
            "&scope=" + URLEncoder.encode(CALENDAR_SCOPE, StandardCharsets.UTF_8) +
            "&access_type=offline" +
            "&prompt=consent" +
            "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    @Override
    public boolean handleOAuthCallback(String authCode, Long fatherId) {
        try {
            Father father = fatherRepository.findById(fatherId).orElse(null);
            if (father == null) {
                log.error("Father {} not found for OAuth callback", fatherId);
                return false;
            }

            // Exchange auth code for tokens
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("code", authCode);
            params.add("grant_type", "authorization_code");
            params.add("redirect_uri", redirectUri);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                GOOGLE_TOKEN_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode tokens = objectMapper.readTree(response.getBody());
                
                String accessToken = tokens.get("access_token").asText();
                father.setGoogleAccessToken(accessToken);
                
                // Google only returns refresh_token on first authorization or with prompt=consent
                JsonNode refreshTokenNode = tokens.get("refresh_token");
                if (refreshTokenNode != null && !refreshTokenNode.isNull()) {
                    father.setGoogleRefreshToken(refreshTokenNode.asText());
                    log.info("Received refresh token for father {}", fatherId);
                } else {
                    // If no refresh token returned but we have an existing one, keep it
                    // If no refresh token at all, this is a problem - log warning
                    if (father.getGoogleRefreshToken() == null || father.getGoogleRefreshToken().isEmpty()) {
                        log.warn("No refresh token received and none exists for father {}. " +
                                "Calendar will not be marked as configured. User may need to revoke app access " +
                                "in Google account settings and re-authorize.", fatherId);
                        // Still set access token and enabled flag, but hasGoogleCalendarConfigured()
                        // will return false until we get a refresh token
                    } else {
                        log.info("No refresh token in response for father {}, keeping existing token", fatherId);
                    }
                }
                
                int expiresIn = tokens.get("expires_in").asInt();
                father.setGoogleTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
                father.setGoogleCalendarEnabled(true);
                
                fatherRepository.save(father);
                
                // Log the final state for debugging
                log.info("Google Calendar OAuth complete for father {}: enabled={}, hasRefreshToken={}, hasGoogleCalendarConfigured={}", 
                        fatherId, 
                        father.getGoogleCalendarEnabled(),
                        father.getGoogleRefreshToken() != null && !father.getGoogleRefreshToken().isEmpty(),
                        father.hasGoogleCalendarConfigured());
                
                return true;
            } else {
                log.error("Google token exchange failed for father {}: status={}", 
                        fatherId, response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to handle OAuth callback for father {}: {}", fatherId, e.getMessage(), e);
        }

        return false;
    }

    @Override
    public void disconnectCalendar(Father father) {
        father.setGoogleCalendarEnabled(false);
        father.setGoogleAccessToken(null);
        father.setGoogleRefreshToken(null);
        father.setGoogleTokenExpiresAt(null);
        father.setGoogleCalendarId(null);
        fatherRepository.save(father);
        
        log.info("Disconnected Google Calendar for father {}", father.getId());
    }

    @Override
    public boolean isCalendarConfigured(Father father) {
        return father != null && father.hasGoogleCalendarConfigured();
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

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

    /**
     * Builds a Google Calendar event object for a mission.
     */
    private Map<String, Object> buildCalendarEvent(Mission mission, Father father, String childName) {
        Map<String, Object> event = new HashMap<>();
        
        String locale = father.getLocale() != null ? father.getLocale() : "he";
        String timezone = father.getTimezone() != null ? father.getTimezone() : "Asia/Jerusalem";

        // Title
        if ("he".equals(locale)) {
            event.put("summary", "🎯 משימת אבא: " + mission.getTitle() + " עם " + childName);
        } else {
            event.put("summary", "🎯 Dad Mission: " + mission.getTitle() + " with " + childName);
        }

        // Description
        StringBuilder description = new StringBuilder();
        description.append(mission.getDescription()).append("\n\n");
        if ("he".equals(locale)) {
            description.append("⏱️ זמן משוער: ").append(mission.getEstimatedMinutes()).append(" דקות\n");
            description.append("📊 רמת קושי: ").append(mission.getDifficulty()).append("/5\n");
            description.append("\n💪 בהצלחה!");
        } else {
            description.append("⏱️ Estimated time: ").append(mission.getEstimatedMinutes()).append(" minutes\n");
            description.append("📊 Difficulty: ").append(mission.getDifficulty()).append("/5\n");
            description.append("\n💪 Good luck!");
        }
        event.put("description", description.toString());

        // Time
        Instant startTime = mission.getScheduledFor() != null ? 
            mission.getScheduledFor() : Instant.now().plusSeconds(3600);
        Instant endTime = startTime.plusSeconds(mission.getEstimatedMinutes() * 60L);

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

        // Reminders
        Map<String, Object> reminders = new HashMap<>();
        reminders.put("useDefault", false);
        reminders.put("overrides", new Map[]{
            Map.of("method", "popup", "minutes", 60),
            Map.of("method", "popup", "minutes", 15)
        });
        event.put("reminders", reminders);

        // Color (green for dad missions)
        event.put("colorId", "10"); // Green

        return event;
    }
}
