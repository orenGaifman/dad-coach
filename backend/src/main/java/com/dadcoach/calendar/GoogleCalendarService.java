package com.dadcoach.calendar;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.mission.Mission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for Google Calendar integration.
 * 
 * Provides functionality to:
 * - Create calendar events for missions
 * - Update calendar events when missions are rescheduled
 * - Delete calendar events when missions are cancelled/completed
 * - Fetch upcoming events from Google Calendar
 * - Manage OAuth tokens for fathers
 */
public interface GoogleCalendarService {

    /**
     * Represents a calendar event fetched from Google Calendar.
     */
    record CalendarEvent(
        String eventId,
        String title,
        String description,
        Instant startTime,
        Instant endTime,
        String location
    ) {}

    /**
     * Fetches upcoming events from a father's Google Calendar.
     * 
     * Returns events that contain "Dad Coach" or related keywords in title/description,
     * or optionally all events within the time range.
     *
     * @param father the father whose calendar to fetch from
     * @param from start of time range (inclusive)
     * @param to end of time range (exclusive)
     * @param filterDadCoachOnly if true, only return Dad Coach related events
     * @return list of calendar events, empty if calendar not configured
     */
    List<CalendarEvent> getUpcomingEvents(Father father, Instant from, Instant to, boolean filterDadCoachOnly);

    /**
     * Creates a calendar event for a mission.
     *
     * @param mission the mission to create an event for
     * @return the calendar event ID if successful, empty if calendar not configured
     */
    Optional<String> createMissionEvent(Mission mission);

    /**
     * Updates a calendar event for a rescheduled mission.
     *
     * @param mission the mission with updated schedule
     * @return true if update was successful
     */
    boolean updateMissionEvent(Mission mission);

    /**
     * Deletes a calendar event for a mission.
     *
     * @param mission the mission whose event should be deleted
     * @return true if deletion was successful
     */
    boolean deleteMissionEvent(Mission mission);

    /**
     * Generates the OAuth authorization URL for a father to connect their calendar.
     *
     * @param fatherId the father's ID (used as state parameter)
     * @return the authorization URL to redirect the user to
     */
    String getAuthorizationUrl(Long fatherId);

    /**
     * Generates the OAuth authorization URL for a father to connect their calendar.
     *
     * @param fatherId the father's ID
     * @param redirectUrl optional URL to redirect to after OAuth completes
     * @return the authorization URL to redirect the user to
     */
    String getAuthorizationUrl(Long fatherId, String redirectUrl);

    /**
     * Handles the OAuth callback and stores tokens for the father.
     *
     * @param authCode the authorization code from Google
     * @param fatherId the father's ID (from state parameter)
     * @return true if tokens were successfully stored
     */
    boolean handleOAuthCallback(String authCode, Long fatherId);

    /**
     * Disconnects a father's Google Calendar integration.
     *
     * @param father the father to disconnect
     */
    void disconnectCalendar(Father father);

    /**
     * Checks if a father has Google Calendar properly configured.
     *
     * @param father the father to check
     * @return true if calendar is configured and tokens are valid
     */
    boolean isCalendarConfigured(Father father);
}
