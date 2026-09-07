package com.dadcoach.systemstate;

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
import com.dadcoach.weeklygoal.WeeklyGoalStatus;
import com.dadcoach.workflow.Belt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of SystemStateLoader.
 * Loads complete system state for a father by querying the database
 * and Google Calendar API.
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
        
        Long fatherIdLong = fatherId.getLeastSignificantBits();
        log.debug("Loading system state for father: fatherId={}", fatherIdLong);
        
        Father father = fatherRepository.findById(fatherIdLong)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherIdLong));
        
        // Load children
        List<Child> children = childRepository.findByFatherId(fatherIdLong);
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
                calendarEvents = loadCalendarEvents(father, 7);
            } catch (Exception e) {
                log.warn("Failed to load calendar events: fatherId={}, error={}", 
                        fatherIdLong, e.getMessage());
            }
        }
        
        // Load quality time events
        List<QualityTime> qualityTimes = qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(fatherIdLong);
        List<SystemState.QualityTimeEvent> qtEvents = qualityTimes.stream()
                .limit(20)
                .map(this::mapToQualityTimeEvent)
                .collect(Collectors.toList());
        
        // Build dashboard metrics
        Belt currentBelt = father.getCurrentBelt();
        Belt nextBelt = currentBelt.getNextBelt();
        int progressToNext = 0;
        int qtToNext = 0;
        if (nextBelt != null) {
            int current = father.getTotalQualityTimesCompleted();
            int needed = nextBelt.getMinCompletions() - currentBelt.getMinCompletions();
            int done = current - currentBelt.getMinCompletions();
            progressToNext = needed > 0 ? Math.min(100, (done * 100) / needed) : 100;
            qtToNext = Math.max(0, nextBelt.getMinCompletions() - current);
        }
        
        SystemState.DashboardMetrics metrics = new SystemState.DashboardMetrics(
                currentBelt,
                father.getQualityTimeStreak(),
                father.getQualityTimeLongestStreak(),
                father.getTotalQualityTimesCompleted(),
                List.of(),
                progressToNext,
                qtToNext
        );
        
        // Load weekly goal info
        SystemState.WeeklyGoalInfo weeklyGoalInfo = loadWeeklyGoalInfo(fatherIdLong);
        
        log.debug("System state loaded: fatherId={}, children={}, qtEvents={}", 
                fatherIdLong, childInfos.size(), qtEvents.size());
        
        return new SystemState(
                fatherProfile,
                father.getCurrentWorkflowState(),
                calendarEvents,
                qtEvents,
                metrics,
                List.of(),
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
        
        if (!father.hasGoogleCalendarConfigured()) {
            log.debug("No Google Calendar connected: fatherId={}", fatherIdLong);
            return List.of();
        }
        
        ZoneId timezone = father.getTimezone() != null 
                ? ZoneId.of(father.getTimezone()) 
                : ZoneId.of("Asia/Jerusalem");
        
        Instant now = Instant.now();
        Instant to = now.plus(Duration.ofDays(daysAhead));
        
        List<GoogleCalendarService.CalendarEvent> calendarEvents;
        try {
            calendarEvents = googleCalendarService.getUpcomingEvents(father, now, to, false);
        } catch (Exception e) {
            log.warn("Failed to load calendar events: fatherId={}, error={}", fatherIdLong, e.getMessage());
            return List.of();
        }
        
        return calculateAvailableSlots(calendarEvents, daysAhead, timezone);
    }
    
    private List<AvailableSlot> calculateAvailableSlots(
            List<GoogleCalendarService.CalendarEvent> busyEvents, 
            int daysAhead, 
            ZoneId timezone) {
        
        List<AvailableSlot> slots = new ArrayList<>();
        Instant now = Instant.now();
        
        for (int day = 0; day < daysAhead; day++) {
            LocalDate date = LocalDate.now(timezone).plusDays(day);
            ZonedDateTime dayStart = date.atTime(DEFAULT_ACTIVITY_START_HOUR, 0).atZone(timezone);
            ZonedDateTime dayEnd = date.atTime(DEFAULT_ACTIVITY_END_HOUR, 0).atZone(timezone);
            
            if (dayEnd.toInstant().isBefore(now)) {
                continue;
            }
            
            Instant windowStart = dayStart.toInstant().isBefore(now) ? now : dayStart.toInstant();
            Instant windowEnd = dayEnd.toInstant();
            
            List<BusyPeriod> busyPeriods = busyEvents.stream()
                    .filter(e -> e.startTime() != null && e.endTime() != null)
                    .filter(e -> e.startTime().isBefore(windowEnd) && e.endTime().isAfter(windowStart))
                    .map(e -> new BusyPeriod(
                            e.startTime().isBefore(windowStart) ? windowStart : e.startTime(),
                            e.endTime().isAfter(windowEnd) ? windowEnd : e.endTime()
                    ))
                    .sorted(Comparator.comparing(BusyPeriod::start))
                    .collect(Collectors.toList());
            
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
            
            if (currentStart.isBefore(windowEnd)) {
                long gapMinutes = Duration.between(currentStart, windowEnd).toMinutes();
                if (gapMinutes >= MINIMUM_SLOT_MINUTES) {
                    slots.add(AvailableSlot.of(currentStart, windowEnd));
                }
            }
        }
        
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
                null,
                qt.getScheduledStart(),
                qt.getScheduledEnd(),
                qt.getStatus().name(),
                qt.getGoogleCalendarEventId(),
                qt.getCompletedAt(),
                qt.getCompletionNotes()
        );
    }
    
    private List<SystemState.CalendarEvent> loadCalendarEvents(Father father, int daysAhead) {
        Instant now = Instant.now();
        Instant to = now.plus(Duration.ofDays(daysAhead));
        
        List<GoogleCalendarService.CalendarEvent> events = 
                googleCalendarService.getUpcomingEvents(father, now, to, false);
        
        return events.stream()
                .map(e -> new SystemState.CalendarEvent(
                        e.eventId(),
                        e.title(),
                        e.startTime(),
                        e.endTime(),
                        false
                ))
                .collect(Collectors.toList());
    }
    
    private SystemState.WeeklyGoalInfo loadWeeklyGoalInfo(Long fatherId) {
        Optional<WeeklyGoal> activeGoal = weeklyGoalRepository.findByFatherIdAndStatus(
                fatherId, WeeklyGoalStatus.ACTIVE);
        
        if (activeGoal.isPresent()) {
            WeeklyGoal goal = activeGoal.get();
            return new SystemState.WeeklyGoalInfo(
                    true,
                    goal.getTargetHours(),
                    goal.getActualMinutes() / 60,
                    0,
                    goal.getWeekStartDate(),
                    null
            );
        }
        
        return SystemState.WeeklyGoalInfo.noGoal();
    }
}
