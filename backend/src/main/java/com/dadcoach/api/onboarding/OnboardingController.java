package com.dadcoach.api.onboarding;

import com.dadcoach.onboarding.OnboardingExceptions;
import com.dadcoach.onboarding.activation.ActivationService;
import com.dadcoach.onboarding.activation.ActivationStatusResponse;
import com.dadcoach.onboarding.dto.*;
import com.dadcoach.onboarding.invitation.InvitationService;
import com.dadcoach.onboarding.invitation.InvitationValidationResult;
import com.dadcoach.onboarding.provisioning.ProvisioningResult;
import com.dadcoach.onboarding.provisioning.ProvisioningService;
import com.dadcoach.onboarding.security.CsrfTokenService;
import com.dadcoach.onboarding.security.OnboardingRateLimiter;
import com.dadcoach.onboarding.security.PhoneMasker;
import com.dadcoach.onboarding.security.RateLimitResult;
import com.dadcoach.onboarding.session.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for the onboarding wizard flow.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "Onboarding wizard session management")
public class OnboardingController {

    private static final Logger log = LoggerFactory.getLogger(OnboardingController.class);

    private final OnboardingSessionService sessionService;
    private final InvitationService invitationService;
    private final ProvisioningService provisioningService;
    private final ActivationService activationService;
    private final OnboardingRateLimiter rateLimiter;
    private final CsrfTokenService csrfTokenService;
    private final SessionCookieManager cookieManager;

    public OnboardingController(OnboardingSessionService sessionService,
                                 InvitationService invitationService,
                                 ProvisioningService provisioningService,
                                 ActivationService activationService,
                                 OnboardingRateLimiter rateLimiter,
                                 CsrfTokenService csrfTokenService,
                                 SessionCookieManager cookieManager) {
        this.sessionService = sessionService;
        this.invitationService = invitationService;
        this.provisioningService = provisioningService;
        this.activationService = activationService;
        this.rateLimiter = rateLimiter;
        this.csrfTokenService = csrfTokenService;
        this.cookieManager = cookieManager;
    }

    @PostMapping("/sessions")
    @Operation(summary = "Create a new onboarding session",
        description = "Creates a session from a valid invitation token.")
    @ApiResponse(responseCode = "201", description = "Session created",
        content = @Content(schema = @Schema(implementation = SessionCreateResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<SessionCreateResponse> createSession(
            @Valid @RequestBody SessionCreateRequest requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        RateLimitResult rateLimitResult = rateLimiter.checkIpLimit(clientIp);
        if (!rateLimitResult.allowed()) {
            throw new OnboardingExceptions.RateLimitExceeded(rateLimitResult.retryAfterSeconds());
        }

        OnboardingSession session = sessionService.create(requestDto.invitationToken(), clientIp, userAgent);
        cookieManager.createCookie(response, session.getSessionId().toString());
        String csrfToken = csrfTokenService.generateToken(session.getSessionId());

        SessionCreateResponse responseDto = new SessionCreateResponse(
                session.getSessionId(),
                session.getCurrentStep().name(),
                session.getStatus().name(),
                session.getLanguage(),
                new SessionCreateResponse.Progress(session.getCurrentStep().getOrder(), WizardStep.totalSteps()),
                session.getExpiresAt(),
                csrfToken
        );

        log.info("Onboarding session created: id={}", session.getSessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Get session details")
    @ApiResponse(responseCode = "200", description = "Session details",
        content = @Content(schema = @Schema(implementation = StepSubmissionResponse.class)))
    public ResponseEntity<StepSubmissionResponse> getSession(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            HttpServletRequest request) {
        validateSessionCookie(sessionId, request);
        return ResponseEntity.ok(buildStepResponse(sessionService.getSession(sessionId)));
    }

    @PutMapping("/sessions/{sessionId}/steps/{step}")
    @Operation(summary = "Submit wizard step data")
    @ApiResponse(responseCode = "200", description = "Step submitted successfully",
        content = @Content(schema = @Schema(implementation = StepSubmissionResponse.class)))
    @ApiResponse(responseCode = "422", description = "Step out of order",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<StepSubmissionResponse> submitStep(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            @PathVariable @Parameter(description = "Wizard step to submit") String step,
            @RequestBody StepSubmissionRequest requestDto,
            HttpServletRequest request) {

        validateSessionCookie(sessionId, request);
        validateCsrf(sessionId, request);

        WizardStep wizardStep;
        try {
            wizardStep = WizardStep.valueOf(step.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown wizard step: " + step);
        }

        OnboardingSession currentSession = sessionService.getSession(sessionId);
        revalidateInvitation(currentSession);

        if (currentSession.getCurrentStep() != wizardStep) {
            throw new OnboardingExceptions.StepOutOfOrder(currentSession.getCurrentStep().name(), wizardStep.name());
        }

        return ResponseEntity.ok(buildStepResponse(sessionService.submitStep(sessionId, wizardStep, requestDto.getData())));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    @Operation(summary = "Complete onboarding")
    @ApiResponse(responseCode = "201", description = "Provisioning completed",
        content = @Content(schema = @Schema(implementation = ProvisioningResponse.class)))
    public ResponseEntity<ProvisioningResponse> complete(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            HttpServletRequest request) {

        validateSessionCookie(sessionId, request);
        validateCsrf(sessionId, request);

        OnboardingSession currentSession = sessionService.getSession(sessionId);
        revalidateInvitation(currentSession);

        ProvisioningResult result = provisioningService.provision(sessionId);

        ProvisioningResponse responseDto = new ProvisioningResponse(
                result.fatherId(), result.activationId(), result.deepLink(), result.deepLink(),
                "🚀 START", "PENDING", null, null);

        log.info("Onboarding completed: session={}, father={}", sessionId, result.fatherId());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @GetMapping("/sessions/{sessionId}/activation-status")
    @Operation(summary = "Get activation status (long-poll)")
    @ApiResponse(responseCode = "200", description = "Activation status",
        content = @Content(schema = @Schema(implementation = ActivationStatusResponse.class)))
    public ResponseEntity<ActivationStatusResponse> getActivationStatus(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            @RequestParam(value = "last_status", required = false) String lastStatus,
            HttpServletRequest request) {
        validateSessionCookie(sessionId, request);
        return activationService.getStatus(sessionId, lastStatus)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sessions/{sessionId}/activation/retry")
    @Operation(summary = "Retry activation")
    @ApiResponse(responseCode = "200", description = "Retry initiated with new deep link")
    @ApiResponse(responseCode = "429", description = "Max retries exceeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Map<String, Object>> retryActivation(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            HttpServletRequest request) {

        validateSessionCookie(sessionId, request);
        validateCsrf(sessionId, request);

        ActivationStatusResponse status = activationService.getStatus(sessionId, null)
                .orElseThrow(() -> new IllegalArgumentException("No activation record found for session: " + sessionId));
        if (status.retryCount() >= 3) {
            throw new OnboardingExceptions.MaxRetriesExceeded(status.retryCount());
        }

        OnboardingSession session = sessionService.getSession(sessionId);
        String deepLink = activationService.generateDeepLink(null, session.getLanguage());

        log.info("Activation retry for session {}: attempt {}", sessionId, status.retryCount() + 1);
        return ResponseEntity.ok(Map.of("deep_link", deepLink, "retry_count", status.retryCount() + 1, "max_retries", 3));
    }

    private void validateSessionCookie(UUID sessionId, HttpServletRequest request) {
        Optional<String> cookieSessionId = cookieManager.readSessionId(request);
        if (cookieSessionId.isPresent() && !cookieSessionId.get().equals(sessionId.toString())) {
            throw new OnboardingExceptions.SessionExpired("Session cookie mismatch");
        }
    }

    private void validateCsrf(UUID sessionId, HttpServletRequest request) {
        String csrfToken = request.getHeader(CsrfTokenService.CSRF_HEADER);
        if (csrfToken == null || csrfToken.isBlank() || !csrfTokenService.validateToken(sessionId, csrfToken)) {
            throw new OnboardingExceptions.CsrfValidation();
        }
    }

    private void revalidateInvitation(OnboardingSession session) {
        InvitationValidationResult result = invitationService.validate(getInvitationToken(session), session.getIpAddress());
        switch (result.status()) {
            case REVOKED -> throw new OnboardingExceptions.InvitationRevoked();
            case EXPIRED -> throw new OnboardingExceptions.InvitationExpired(result.expiresAt());
            case EXHAUSTED -> throw new OnboardingExceptions.InvitationExhausted();
            case NOT_FOUND -> throw new OnboardingExceptions.InvitationNotFound("unknown");
            case VALID -> { }
        }
    }

    private String getInvitationToken(OnboardingSession session) {
        if (session.getInvitationId() == null) return "";
        return invitationService.getTokenById(session.getInvitationId());
    }

    private StepSubmissionResponse buildStepResponse(OnboardingSession session) {
        WizardStep currentStep = session.getCurrentStep();
        List<String> completedSteps = Arrays.stream(WizardStep.values())
                .filter(step -> step.getOrder() < currentStep.getOrder())
                .map(WizardStep::name)
                .collect(Collectors.toList());

        return new StepSubmissionResponse(
                session.getSessionId(),
                currentStep.name(),
                session.getStatus().name(),
                new SessionCreateResponse.Progress(currentStep.getOrder(), WizardStep.totalSteps()),
                completedSteps,
                buildWizardDataSummary(session)
        );
    }

    private Map<String, Object> buildWizardDataSummary(OnboardingSession session) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("language", session.getLanguage());
        WizardData wizardData = session.getWizardData();
        if (wizardData != null) {
            if (wizardData.getDisplayName() != null) summary.put("display_name", wizardData.getDisplayName());
            if (wizardData.getPhoneNumber() != null) summary.put("phone_masked", PhoneMasker.mask(wizardData.getPhoneNumber()));
            summary.put("children_count", wizardData.getChildren() != null ? wizardData.getChildren().size() : 0);
            summary.put("goals_count", wizardData.getGoals() != null ? wizardData.getGoals().size() : 0);
        }
        return summary;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
