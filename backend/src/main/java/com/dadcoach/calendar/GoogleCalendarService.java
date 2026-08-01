package com.dadcoach.calendar;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.mission.Mission;

import java.util.Optional;

/**
 * Service interface for Google Calendar integration.
 * 
 * Provides functionality to:
 * - Create calendar events for missions
 * - Update calendar events when missions are rescheduled
 * - Delete calendar events when missions are cancelled/completed
 * - Manage OAuth tokens for fathers
 */
public interface GoogleCalendarService {

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
