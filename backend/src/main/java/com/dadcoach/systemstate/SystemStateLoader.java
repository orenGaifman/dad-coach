package com.dadcoach.systemstate;

import java.util.List;
import java.util.UUID;

/**
 * Loads complete system state before any action (Read Before Write principle).
 * 
 * <p>This interface defines the contract for loading all necessary state about a father
 * before processing any request. The Read Before Write principle ensures that:</p>
 * <ul>
 *   <li>The system never asks for information it already has</li>
 *   <li>The system never suggests times that conflict with the calendar</li>
 *   <li>All decisions are based on current, complete information</li>
 * </ul>
 * 
 * <p>Implements Requirements 2.1 and 2.3 from the deterministic-workflow-engine spec.</p>
 * 
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // At the start of every request processing cycle:
 * SystemState state = systemStateLoader.loadState(fatherId);
 * 
 * // For scheduling flows:
 * List<AvailableSlot> slots = systemStateLoader.loadAvailableSlots(fatherId, 7);
 * }</pre>
 * 
 * @see SystemState
 * @see AvailableSlot
 * @see <a href="Requirements 2.1">System State Loading</a>
 * @see <a href="Requirements 2.3">Available Slot Calculation</a>
 */
public interface SystemStateLoader {

    /**
     * Load the complete system state for a father.
     * 
     * <p>This method loads all relevant state from authoritative sources:</p>
     * <ul>
     *   <li>Father profile from database (name, children, preferences, locale, timezone)</li>
     *   <li>Current workflow state from database</li>
     *   <li>Google Calendar events for the next 7 days (if connected)</li>
     *   <li>Scheduled Quality Time events from database</li>
     *   <li>Dashboard metrics (belt, streak, achievements) from database</li>
     *   <li>Recent conversation context (last 10 messages in current workflow state)</li>
     * </ul>
     * 
     * <p>The returned {@link SystemState} is immutable and represents a snapshot
     * of all state at the time of loading. It should be used for the duration of
     * a single request processing cycle.</p>
     * 
     * <p><strong>Requirements:</strong></p>
     * <ul>
     *   <li>Requirement 2.1: Load complete state before any action</li>
     *   <li>Requirement 2.2: Never ask for information that exists in the SystemState</li>
     *   <li>Requirement 2.4: Cache state for the duration of single request processing</li>
     * </ul>
     * 
     * @param fatherId the father's unique identifier
     * @return the complete system state, never null
     * @throws IllegalArgumentException if fatherId is null
     * @throws com.dadcoach.common.ResourceNotFoundException if the father does not exist
     */
    SystemState loadState(UUID fatherId);

    /**
     * Load available time slots for Quality Time scheduling.
     * 
     * <p>This method analyzes the father's Google Calendar to find available time slots
     * suitable for scheduling Quality Time. The algorithm:</p>
     * <ol>
     *   <li>Reads the father's Google Calendar for the specified number of days ahead</li>
     *   <li>Identifies busy periods from calendar events</li>
     *   <li>Calculates available slots of at least 30 minutes duration</li>
     *   <li>Excludes times outside preferred activity hours (default 6am-10pm local time)</li>
     *   <li>Returns slots ordered by proximity to current time</li>
     * </ol>
     * 
     * <p>If Google Calendar is not connected, this method returns an empty list.
     * The caller should handle this case by prompting the father to connect their calendar.</p>
     * 
     * <p><strong>Requirements:</strong></p>
     * <ul>
     *   <li>Requirement 2.3: Calculate available slots from Google Calendar</li>
     *   <li>Requirement 2.5: Prompt for calendar connection if not connected</li>
     *   <li>Requirement 5.1: Present 3-5 available time slots with numbered selection</li>
     * </ul>
     * 
     * @param fatherId the father's unique identifier
     * @param daysAhead number of days to look ahead (default 7, max 14)
     * @return list of available slots sorted by proximity to current time, may be empty
     * @throws IllegalArgumentException if fatherId is null
     * @throws IllegalArgumentException if daysAhead is less than 1 or greater than 14
     * @throws com.dadcoach.common.ResourceNotFoundException if the father does not exist
     */
    List<AvailableSlot> loadAvailableSlots(UUID fatherId, int daysAhead);
    
    /**
     * Load available time slots with default look-ahead period of 7 days.
     * 
     * <p>Convenience method equivalent to {@code loadAvailableSlots(fatherId, 7)}.</p>
     * 
     * @param fatherId the father's unique identifier
     * @return list of available slots sorted by proximity to current time
     * @see #loadAvailableSlots(UUID, int)
     */
    default List<AvailableSlot> loadAvailableSlots(UUID fatherId) {
        return loadAvailableSlots(fatherId, 7);
    }
}
