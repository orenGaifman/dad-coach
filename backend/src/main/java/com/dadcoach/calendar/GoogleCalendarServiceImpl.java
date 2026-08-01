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
import java.util.HashMap;
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
        return GOOGLE_AUTH_URL + "?" +
            "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
            "&response_type=code" +
            "&scope=" + URLEncoder.encode(CALENDAR_SCOPE, StandardCharsets.UTF_8) +
            "&access_type=offline" +
            "&prompt=consent" +
            "&state=" + fatherId;
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
                
                father.setGoogleAccessToken(tokens.get("access_token").asText());
                father.setGoogleRefreshToken(tokens.get("refresh_token").asText());
                
                int expiresIn = tokens.get("expires_in").asInt();
                father.setGoogleTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
                father.setGoogleCalendarEnabled(true);
                
                fatherRepository.save(father);
                
                log.info("Successfully connected Google Calendar for father {}", fatherId);
                return true;
            }

        } catch (Exception e) {
            log.error("Failed to handle OAuth callback for father {}: {}", fatherId, e.getMessage());
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
