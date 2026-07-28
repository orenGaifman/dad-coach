package com.dadcoach.workspace.activity;

import com.dadcoach.workspace.dto.request.PositiveActivityRequest;
import com.dadcoach.workspace.dto.request.QualityTimeRequest;
import com.dadcoach.workspace.dto.response.ActivityReportResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/**
 * REST controller for activity reporting endpoints.
 *
 * <p>Provides endpoints for fathers to report quality time and positive activities.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/activities")
public class ActivityReportingController {

    private final ActivityReportingService activityReportingService;

    public ActivityReportingController(ActivityReportingService activityReportingService) {
        this.activityReportingService = activityReportingService;
    }

    /**
     * Reports quality time spent with a child.
     *
     * @param request   the quality time request
     * @param principal the authenticated user
     * @return 201 Created with the activity report response
     */
    @PostMapping("/quality-time")
    public ResponseEntity<ActivityReportResponse> reportQualityTime(
            @Valid @RequestBody QualityTimeRequest request,
            Principal principal) {

        UUID fatherId = extractFatherId(principal);
        ActivityReportResponse response = activityReportingService.reportQualityTime(fatherId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Reports a positive parenting activity.
     *
     * @param request   the positive activity request
     * @param principal the authenticated user
     * @return 201 Created with the activity report response
     */
    @PostMapping("/positive-activity")
    public ResponseEntity<ActivityReportResponse> reportPositiveActivity(
            @Valid @RequestBody PositiveActivityRequest request,
            Principal principal) {

        UUID fatherId = extractFatherId(principal);
        ActivityReportResponse response = activityReportingService.reportPositiveActivity(fatherId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Extracts the father's UUID from the security context.
     *
     * <p>TODO: Integrate with production authentication infrastructure.
     * Currently uses the principal name as a UUID string. In production,
     * this should extract from a JWT claim or custom UserDetails implementation.</p>
     */
    private UUID extractFatherId(Principal principal) {
        if (principal == null) {
            // TODO: This should never happen with proper security config.
            // Placeholder for development — use a fixed UUID.
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.fromString(principal.getName());
    }
}
