package com.dadcoach.workspace.activity;

import com.dadcoach.workspace.dto.request.PositiveActivityRequest;
import com.dadcoach.workspace.dto.request.QualityTimeRequest;
import com.dadcoach.workspace.dto.response.ActivityReportResponse;
import com.dadcoach.workspace.event.PositiveActivityReportedEvent;
import com.dadcoach.workspace.event.QualityTimeReportedEvent;
import com.dadcoach.workspace.growth.signal.GrowthSignalType;
import com.dadcoach.workspace.growth.signal.SignalWeight;
import com.dadcoach.workspace.security.ActivityReportRateLimiter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service handling activity report submissions (quality time and positive activities).
 *
 * <p>Flow for each report: validate → check duplicate → check rate limit → persist →
 * emit domain event → return response with points awarded.</p>
 */
@Service
public class ActivityReportingService {

    private final ActivityReportRepository activityReportRepository;
    private final ActivityReportValidator activityReportValidator;
    private final ActivityReportRateLimiter activityReportRateLimiter;
    private final ApplicationEventPublisher eventPublisher;

    public ActivityReportingService(ActivityReportRepository activityReportRepository,
                                    ActivityReportValidator activityReportValidator,
                                    ActivityReportRateLimiter activityReportRateLimiter,
                                    ApplicationEventPublisher eventPublisher) {
        this.activityReportRepository = activityReportRepository;
        this.activityReportValidator = activityReportValidator;
        this.activityReportRateLimiter = activityReportRateLimiter;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Reports quality time spent with a child.
     *
     * @param fatherId the father's UUID
     * @param request  the quality time request details
     * @return an activity report response with points awarded
     */
    @Transactional
    public ActivityReportResponse reportQualityTime(UUID fatherId, QualityTimeRequest request) {
        // 1. Validate
        activityReportValidator.validateQualityTimeReport(request.getDurationMinutes(), request.getActivityDate());

        // 2. Check duplicate
        if (isDuplicateQualityTime(fatherId, request.getChildId(), request.getDurationMinutes(), request.getActivityDate())) {
            // Return a response indicating the report already exists (idempotent)
            return createDuplicateResponse(fatherId, request);
        }

        // 3. Check rate limit
        activityReportRateLimiter.checkLimit(fatherId, ActivityReportRateLimiter.REPORT_TYPE_QUALITY_TIME);

        // 4. Persist
        ActivityReport report = ActivityReport.builder()
                .fatherId(fatherId)
                .childId(request.getChildId())
                .reportType(ActivityReportRateLimiter.REPORT_TYPE_QUALITY_TIME)
                .durationMinutes(request.getDurationMinutes())
                .activityDate(request.getActivityDate())
                .build();
        activityReportRepository.save(report);

        // 5. Emit domain event
        eventPublisher.publishEvent(new QualityTimeReportedEvent(
                fatherId, request.getChildId(), request.getDurationMinutes(),
                request.getActivityDate(), report.getReportId()));

        // 6. Return response
        int pointsAwarded = SignalWeight.getPoints(GrowthSignalType.QUALITY_TIME_REPORTED);
        return new ActivityReportResponse(
                report.getReportId(),
                report.getReportType(),
                report.getChildId(),
                report.getActivityDate(),
                pointsAwarded
        );
    }

    /**
     * Reports a positive parenting activity.
     *
     * @param fatherId the father's UUID
     * @param request  the positive activity request details
     * @return an activity report response with points awarded
     */
    @Transactional
    public ActivityReportResponse reportPositiveActivity(UUID fatherId, PositiveActivityRequest request) {
        // 1. Validate
        activityReportValidator.validatePositiveActivityReport(request.getActivityDate());

        // 2. Check rate limit
        activityReportRateLimiter.checkLimit(fatherId, ActivityReportRateLimiter.REPORT_TYPE_POSITIVE_ACTIVITY);

        // 3. Persist
        ActivityReport report = ActivityReport.builder()
                .fatherId(fatherId)
                .childId(request.getChildId())
                .reportType(ActivityReportRateLimiter.REPORT_TYPE_POSITIVE_ACTIVITY)
                .activityType(request.getActivityType())
                .description(request.getDescription())
                .activityDate(request.getActivityDate())
                .build();
        activityReportRepository.save(report);

        // 4. Emit domain event
        eventPublisher.publishEvent(new PositiveActivityReportedEvent(
                fatherId, request.getActivityType().name(), request.getChildId(),
                request.getActivityDate(), report.getReportId()));

        // 5. Return response
        int pointsAwarded = SignalWeight.getPoints(GrowthSignalType.POSITIVE_ACTIVITY);
        return new ActivityReportResponse(
                report.getReportId(),
                report.getReportType(),
                report.getChildId(),
                report.getActivityDate(),
                pointsAwarded
        );
    }

    /**
     * Checks if a quality time report already exists for the same father, child,
     * duration, and date combination.
     */
    public boolean isDuplicateQualityTime(UUID fatherId, UUID childId, Integer durationMinutes, LocalDate activityDate) {
        return activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                fatherId, childId, durationMinutes, activityDate);
    }

    private ActivityReportResponse createDuplicateResponse(UUID fatherId, QualityTimeRequest request) {
        // For duplicate submissions, return 0 points (idempotent behavior)
        return new ActivityReportResponse(
                null, // no new report created
                ActivityReportRateLimiter.REPORT_TYPE_QUALITY_TIME,
                request.getChildId(),
                request.getActivityDate(),
                0
        );
    }
}
