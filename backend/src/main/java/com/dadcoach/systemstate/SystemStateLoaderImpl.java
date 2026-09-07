package com.dadcoach.systemstate;

import com.dadcoach.calendar.CalendarEvent;
import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.workflow.Belt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of SystemStateLoader.
 * 
 * <p>Loads complete system state for a father by querying the database
 * and Google Calendar API. Implements the Read Before Write principle.</p>
 * 
 * @see SystemStateLoader
 * @see SystemState
 */
@Service
public class SystemStateLoaderImpl implements SystemStateLoader {

    private static final Logger log = LoggerFactory.getLogger(SystemStateLoaderImpl.class);
    
    private static final int DEFAULT_ACTIVITY_START_HOUR = 6;
    private static final int DEFAULT_ACTIVITY_END_HOUR = 22;
    private static final int MINIMUM_SLOT_MINUTES = 30;
    
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final QualityTimeRepository qualityTimeRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;
    private final GoogleCalendarService googleCalendarService;
    
    public SystemStateLoaderImpl(
            FatherRepository fatherRepository,
            ChildRepository childRepository,
            QualityTimeRepository qualityTimeRepository,
            WeeklyGoalRepository weeklyGoalRepository,
            GoogleCalendarService googleCalendarService) {
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.qualityTimeRepository = qualityTimeRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.googleCalendarService = googleCalendarService;
    }
    
    @Override
    public SystemState loadState(UUID fatherId) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        
        // Convert UUID to Long for database lookup
        Long fatherIdLong = fatherId.getLeastSignificantBits();
        
        log.debug("Loading system state for father: fatherId={}", fatherIdLong);
        
        Father father = fatherRepository.findById(fatherIdLong)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherIdLong));
        
        // Load children
        List<Child> children = childRepository.findByFatherIdAndArchivedFalse(fatherIdLong);
        List<SystemState.ChildInfo> childInfos = children.stream()
                .map(this::mapToChildInfo)
                .collect(Collectors.toList());
        
        // Build father profile
        SystemState.FatherProfile fatherProfile = new SystemState.FatherProfile(
                father.getId(),
                father.getDisplayName(),
                father.getPhone(),
                childInfos,
                father.getLocale(),
                father.getTimezone(),
                father.getPreferredCoachingTime(),
                father.hasGoogleCalendarConfigured(),
                father.getWelcomeStep()
        );
        
        // Load calendar events (if connected)
        List<SystemState.CalendarEvent> calendarEvents = List.of();
        if (father.hasGoogleCalendarConfigured()) {
            try {
                calendarEvents = loadCalendarEvents(fatherIdLong, 7);
            } catch (Exception e) {
                log.warn("Failed to load calendar events for father: fatherId={}, error={}", 
                        fatherIdLong, e.getMessage());
            }
        }
        
        // Load quality time events
        List<QualityTime> qualityTimes = qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(
                father.getPhone());
        List<SystemState.QualityTimeEvent> qtEvents = qualityTimes.stream()
                .limit(20)
                .map(this::mapToQualityTimeEvent)
                .collect(Collectors.toList());
        
        // Build dashboard metrics
        SystemState.DashboardMetrics metrics = new SystemState.DashboardMetrics(
                father.getCurrentBelt(),
                father.getQualityTimeStreak(),
                father.getQualityTimeLongestStreak(),
                father.getTotalQualityTimesCompleted(),
                List.of(), // Recent achievements - can be enhanced later
                calculateProgressToNextBelt(father),
                calculateQualityTimesToNextBelt(father)
        );
        
        // Load weekly goal info
        SystemState.WeeklyGoalInfo weeklyGoalInfo = loadWeeklyGoalInfo(fatherIdLong);
        
        log.debug("System state loaded for father: fatherId={}, children={}, qtEvents={}", 
                fatherIdLong, childInfos.size(), qtEvents.size());
        
        return new SystemState(
                fatherProfile,
                father.getWorkflowState(),
                calendarEvents,
                qtEvents,
                metrics,
                List.of(), // Conversation context - can be enhanced later
                weeklyGoalInfo
        );
    }
    
    @Override
    public List<AvailableSlot> loadAvailableSlots(UUID fatherId, int daysAhead) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (daysAhead < 1 || daysAhead > 14) {
            throw new IllegalArgumentException("daysAhead must be between 1 and 14");
        }
        
        Long fatherIdLong = fatherId.getLeastSignificantBits();
        
        Father father = fatherRepository.findById(fatherIdLong)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherIdLong));
        
        // If no calendar connected, return empty list
        if (!father.hasGoogleCalendarConfigured()) {
            log.debug("No Google Calendar connected for father: fatherId={}", fatherIdLong);
            return List.of();
        }
        
        // Get father's timezone
        ZoneId timezone = father.getTimezone() != null 
                ? ZoneId.of(father.getTimezone()) 
                : ZoneId.of("Asia/Jerusalem");
        
        // Load calendar events
        List<CalendarEvent> calendarEvents;
        try {
            calendarEvents = googleCalendarService.getUpcomingEvents(fatherIdLong, daysAhead);
        } catch (Exception e) {
            log.warn("Failed to load calendar events: fatherId={}, error={}", fatherIdLong, e.getMessage());
            return List.of();
        }
        
        // Calculate available slots
        List<AvailableSlot> slots = calculateAvailableSlots(calendarEvents, daysAhead, timezone);
        
        log.debug("Available slots loaded: fatherId={}, daysAhead={}, slotsFound={}", 
                fatherIdLong, daysAhead, slots.size());
        
        return slots;
    }
    
    private List<AvailableSlot> calculateAvailableSlots(
            List<CalendarEvent> busyEvents, 
            int daysAhead, 
            ZoneId timezone) {
        
        List<AvailableSlot> slots = new ArrayList<>();
        Instant now = Instant.now();
        
        for (int day = 0; day < daysAhead; day++) {
            LocalDate date = LocalDate.now(timezone).plusDays(day);
            
            // Activity window for this day
            ZonedDateTime dayStart = date.atTime(DEFAULT_ACTIVITY_START_HOUR, 0).atZone(timezone);
            ZonedDateTime dayEnd = date.atTime(DEFAULT_ACTIVITY_END_HOUR, 0).atZone(timezone);
            
            // Skip if day is in the past
            if (dayEnd.toInstant().isBefore(now)) {
                continue;
            }
            
            // Adjust start time if it's today
            Instant windowStart = dayStart.toInstant().isBefore(now) ? now : dayStart.toInstant();
            Instant windowEnd = dayEnd.toInstant();
            
            // Get busy periods for this day
            List<BusyPeriod> busyPeriods = busyEvents.stream()
                    .filter(e -> !e.isAllDay())
                    .filter(e -> e.getStart().isBefore(windowEnd) && e.getEnd().isAfter(windowStart))
                    .map(e -> new BusyPeriod(
                            e.getStart().isBefore(windowStart) ? windowStart : e.getStart(),
                            e.getEnd().isAfter(windowEnd) ? windowEnd : e.getEnd()
                    ))
                    .sorted(Comparator.comparing(BusyPeriod::start))
                    .collect(Collectors.toList());
            
            // Find gaps between busy periods
            Instant currentStart = windowStart;
            for (BusyPeriod busy : busyPeriods) {
                if (currentStart.isBefore(busy.start())) {
                    long gapMinutes = Duration.between(currentStart, busy.start()).toMinutes();
                    if (gapMinutes >= MINIMUM_SLOT_MINUTES) {
                        slots.add(AvailableSlot.of(currentStart, busy.start()));
                    }
                }
                if (busy.end().isAfter(currentStart)) {
                    currentStart = busy.end();
                }
            }
            
            // Add slot after last busy period
            if (currentStart.isBefore(windowEnd)) {
                long gapMinutes = Duration.between(currentStart, windowEnd).toMinutes();
                if (gapMinutes >= MINIMUM_SLOT_MINUTES) {
                    slots.add(AvailableSlot.of(currentStart, windowEnd));
                }
            }
        }
        
        // Limit to reasonable number
        return slots.stream().limit(20).collect(Collectors.toList());
    }
    
    private record BusyPeriod(Instant start, Instant end) {}
    
    private SystemState.ChildInfo mapToChildInfo(Child child) {
        int age = child.getBirthDate() != null 
                ? Period.between(child.getBirthDate(), LocalDate.now()).getYears()
                : 0;
        
        return new SystemState.ChildInfo(
                child.getId(),
                child.getName(),
                child.getBirthDate(),
                age,
                child.getGender(),
                child.getInterests() != null ? child.getInterests() : List.of()
        );
    }
    
    private SystemState.QualityTimeEvent mapToQualityTimeEvent(QualityTime qt) {
        return new SystemState.QualityTimeEvent(
                qt.getId(),
                qt.getChildId(),
                null, // Child name could be resolved if needed
                qt.getScheduledStart(),
                qt.getScheduledEnd(),
                qt.getStatus().name(),
                qt.getGoogleCalendarEventId(),
                qt.getCompletedAt(),
                qt.getCompletionNotes()
        );
    }
    
    private List<SystemState.CalendarEvent> loadCalendarEvents(Long fatherId, int daysAhead) {
        List<CalendarEvent> events = googleCalendarService.getUpcomingEvents(fatherId, daysAhead);
        return events.stream()
                .map(e -> new SystemState.CalendarEvent(
                        e.getEventId(),
                        e.getTitle(),
                        e.getStart(),
                        e.getEnd(),
                        e.isAllDay()
                ))
                .collect(Collectors.toList());
    }
    
    private SystemState.WeeklyGoalInfo loadWeeklyGoalInfo(Long fatherId) {
        Optional<WeeklyGoal> activeGoal = weeklyGoalRepository.findActiveGoalByFatherId(fatherId);
        
        if (activeGoal.isPresent()) {
            WeeklyGoal goal = activeGoal.get();
            return new SystemState.WeeklyGoalInfo(
                    true,
                    goal.getTargetHours(),
                    goal.getActualMinutes() / 60,
                    0, // scheduled count - could be calculated
                    goal.getWeekStartDate(),
                    null // last week summary - could be enhanced
            );
        }
        
        return SystemState.WeeklyGoalInfo.noGoal();
    }
    
    private int calculateProgressToNextBelt(Father father) {
        Belt currentBelt = father.getCurrentBelt();
        Belt nextBelt = currentBelt.getNextBelt();
        if (nextBelt == null) {
            return 100; // Already at max belt
        }
        int required = nextBelt.getMinimumQualityTimes() - currentBelt.getMinimumQualityTimes();
        int completed = father.getTotalQualityTimesCompleted() - currentBelt.getMinimumQualityTimes();
        return required > 0 ? Math.min(100, (completed * 100) / required) : 100;
    }
    
    private int calculateQualityTimesToNextBelt(Father father) {
        Belt currentBelt = father.getCurrentBelt();
        Belt nextBelt = currentBelt.getNextBelt();
        if (nextBelt == null) {
            return 0;
        }
        return Math.max(0, nextBelt.getMinimumQualityTimes() - father.getTotalQualityTimesCompleted());
    }
}
