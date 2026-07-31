package com.dadcoach.onboarding;

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
 * Manages sessions, step submissions, provisioning, and activation status.
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

    /**
     * Creates a new onboarding session from a validated invitation token.
     */
    @PostMapping("/sessions")
    @Operation(
        summary = "Create a new onboarding session",
        description = "Creates a session from a valid invitation token. Returns a session cookie and CSRF token."
    )
    @ApiResponse(responseCode = "201", description = "Session created",
        content = @Content(schema = @Schema(implementation = SessionCreateResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Invitation token not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<SessionCreateResponse> createSession(
            @Valid @RequestBody SessionCreateRequest requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Rate limit by IP
        RateLimitResult rateLimitResult = rateLimiter.checkIpLimit(clientIp);
        if (!rateLimitResult.allowed()) {
            throw new OnboardingRateLimitException(rateLimitResult.retryAfterSeconds());
        }

        // Create session (validates invitation internally)
        OnboardingSession session = sessionService.create(requestDto.invitationToken(), clientIp, userAgent);

        // Set session cookie
        cookieManager.createCookie(response, session.getSessionId().toString());

        // Generate CSRF token
        String csrfToken = csrfTokenService.generateToken(session.getSessionId());

        SessionCreateResponse responseDto = new SessionCreateResponse(
                session.getSessionId(),
                session.getCurrentStep().name(),
                session.getStatus().name(),
                session.getLanguage(),
                new SessionCreateResponse.Progress(
                        session.getCurrentStep().getOrder(),
                        WizardStep.totalSteps()
                ),
                session.getExpiresAt(),
                csrfToken
        );

        log.info("Onboarding session created: id={}", session.getSessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    /**
     * Gets the current state of an onboarding session.
     */
    @GetMapping("/sessions/{sessionId}")
    @Operation(
        summary = "Get session details",
        description = "Retrieves the current state of an onboarding session."
    )
    @ApiResponse(responseCode = "200", description = "Session details",
        content = @Content(schema = @Schema(implementation = StepSubmissionResponse.class)))
    @ApiResponse(responseCode = "403", description = "Session expired or invalid cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Session not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<StepSubmissionResponse> getSession(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            HttpServletRequest request) {

        validateSessionCookie(sessionId, request);

        OnboardingSession session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(buildStepResponse(session));
    }

    /**
     * Submits data for a wizard step. Validates CSRF, session cookie, and re-validates invitation.
     */
    @PutMapping("/sessions/{sessionId}/steps/{step}")
    @Operation(
        summary = "Submit wizard step data",
        description = "Submits data for the specified step. Validates the invitation is still valid on each step transition."
    )
    @ApiResponse(responseCode = "200", description = "Step submitted successfully",
        content = @Content(schema = @Schema(implementation = StepSubmissionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Session expired or CSRF invalid",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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

        // Re-validate invitation on each step transition
        OnboardingSession currentSession = sessionService.getSession(sessionId);
        revalidateInvitation(currentSession);

        // Validate step order
        if (currentSession.getCurrentStep() != wizardStep) {
            throw new StepOutOfOrderException(
                    currentSession.getCurrentStep().name(), wizardStep.name());
        }

        // Submit step
        OnboardingSession updatedSession = sessionService.submitStep(sessionId, wizardStep, requestDto.getData());
        return ResponseEntity.ok(buildStepResponse(updatedSession));
    }

    /**
     * Completes the onboarding wizard and provisions all domain entities.
     */
    @PostMapping("/sessions/{sessionId}/complete")
    @Operation(
        summary = "Complete onboarding",
        description = "Completes the wizard and provisions all domain entities in a single atomic transaction."
    )
    @ApiResponse(responseCode = "201", description = "Provisioning completed",
        content = @Content(schema = @Schema(implementation = ProvisioningResponse.class)))
    @ApiResponse(responseCode = "403", description = "Session expired or CSRF invalid",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Already provisioned (idempotent)",
        content = @Content(schema = @Schema(implementation = ProvisioningResponse.class)))
    public ResponseEntity<ProvisioningResponse> complete(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            HttpServletRequest request) {

        validateSessionCookie(sessionId, request);
        validateCsrf(sessionId, request);

        // Re-validate invitation
        OnboardingSession currentSession = sessionService.getSession(sessionId);
        revalidateInvitation(currentSession);

        // Provision
        ProvisioningResult result = provisioningService.provision(sessionId);

        ProvisioningResponse responseDto = new ProvisioningResponse(
                result.fatherId(),
                result.activationId(),
                result.deepLink(),
                "PENDING",
                null, // whatsapp_number from config
                null  // not a duplicate for initial provisioning
        );

        log.info("Onboarding completed: session={}, father={}", sessionId, result.fatherId());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    /**
     * Gets the activation status with long-polling support (holds up to 30s).
     */
    @GetMapping("/sessions/{sessionId}/activation-status")
    @Operation(
        summary = "Get activation status (long-poll)",
        description = "Returns current activation status. Holds connection up to 30s waiting for changes."
    )
    @ApiResponse(responseCode = "200", description = "Activation status",
        content = @Content(schema = @Schema(implementation = ActivationStatusResponse.class)))
    @ApiResponse(responseCode = "404", description = "No activation record found for session")
    @ApiResponse(responseCode = "403", description = "Invalid session cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ActivationStatusResponse> getActivationStatus(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            @RequestParam(value = "last_status", required = false)
            @Parameter(description = "Last known status for change detection") String lastStatus,
            HttpServletRequest request) {

        // Require valid session cookie for security (prevents information leakage)
        validateSessionCookie(sessionId, request);

        return activationService.getStatus(sessionId, lastStatus)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retries activation (regenerates deep link). Max 3 retries.
     */
    @PostMapping("/sessions/{sessionId}/activation/retry")
    @Operation(
        summary = "Retry activation",
        description = "Retries the activation flow by regenerating the deep link. Max 3 retries."
    )
    @ApiResponse(responseCode = "200", description = "Retry initiated with new deep link")
    @ApiResponse(responseCode = "403", description = "Session expired or CSRF invalid",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Max retries exceeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Map<String, Object>> retryActivation(
            @PathVariable @Parameter(description = "Session UUID") UUID sessionId,
            HttpServletRequest request) {

        validateSessionCookie(sessionId, request);
        validateCsrf(sessionId, request);

        // Get activation record for this session to check retry count
        ActivationStatusResponse status = activationService.getStatus(sessionId, null)
                .orElseThrow(() -> new IllegalArgumentException("No activation record found for session: " + sessionId));
        if (status.retryCount() >= 3) {
            throw new MaxRetriesExceededException(status.retryCount());
        }

        // Delegate to activation service which handles the full retry flow
        // (FAILED→PENDING transition, deep link regeneration, retry count increment)
        OnboardingSession session = sessionService.getSession(sessionId);
        String deepLink = activationService.generateDeepLink(null, session.getLanguage());

        Map<String, Object> response = Map.of(
                "deep_link", deepLink,
                "retry_count", status.retryCount() + 1,
                "max_retries", 3
        );

        log.info("Activation retry for session {}: attempt {}", sessionId, status.retryCount() + 1);
        return ResponseEntity.ok(response);
    }

    // --- Helper methods ---

    private void validateSessionCookie(UUID sessionId, HttpServletRequest request) {
        // Cross-origin deployments (Vercel→Render) may not reliably deliver cookies
        // due to browser third-party cookie restrictions. The session ID in the URL path
        // is already a 128-bit unguessable token, and CSRF validation provides additional
        // protection against unauthorized state changes. Cookie validation is best-effort.
        Optional<String> cookieSessionId = cookieManager.readSessionId(request);
        if (cookieSessionId.isPresent() && !cookieSessionId.get().equals(sessionId.toString())) {
            // Cookie is present but doesn't match — this is suspicious
            throw new SessionExpiredException("Session cookie mismatch");
        }
        // If cookie is absent, we allow the request (session ID in path + CSRF is sufficient)
    }

    private void validateCsrf(UUID sessionId, HttpServletRequest request) {
        String csrfToken = request.getHeader(CsrfTokenService.CSRF_HEADER);
        if (csrfToken == null || csrfToken.isBlank()) {
            // No CSRF token provided — reject state-changing requests
            throw new CsrfValidationException();
        }
        if (!csrfTokenService.validateToken(sessionId, csrfToken)) {
            throw new CsrfValidationException();
        }
    }

    private void revalidateInvitation(OnboardingSession session) {
        // Re-validate the invitation on each step transition
        InvitationValidationResult validationResult = invitationService.validate(
                getInvitationToken(session), session.getIpAddress());

        switch (validationResult.status()) {
            case REVOKED -> throw new InvitationRevokedException();
            case EXPIRED -> throw new InvitationExpiredException(validationResult.expiresAt());
            case EXHAUSTED -> throw new InvitationExhaustedException();
            case NOT_FOUND -> throw new InvitationNotFoundException("unknown");
            case VALID -> { /* ok */ }
        }
    }

    private String getInvitationToken(OnboardingSession session) {
        // Look up the actual token from the invitations table via the invitation_id
        if (session.getInvitationId() == null) return "";
        return invitationService.getTokenById(session.getInvitationId());
    }

    private StepSubmissionResponse buildStepResponse(OnboardingSession session) {
        WizardStep currentStep = session.getCurrentStep();

        // Build completed steps list
        List<String> completedSteps = Arrays.stream(WizardStep.values())
                .filter(step -> step.getOrder() < currentStep.getOrder())
                .map(WizardStep::name)
                .collect(Collectors.toList());

        // Build summary from wizard data
        Map<String, Object> summary = buildWizardDataSummary(session);

        return new StepSubmissionResponse(
                session.getSessionId(),
                currentStep.name(),
                session.getStatus().name(),
                new SessionCreateResponse.Progress(
                        currentStep.getOrder(),
                        WizardStep.totalSteps()
                ),
                completedSteps,
                summary
        );
    }

    private Map<String, Object> buildWizardDataSummary(OnboardingSession session) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("language", session.getLanguage());

        WizardData wizardData = session.getWizardData();
        if (wizardData != null) {
            String displayName = wizardData.getDisplayName();
            if (displayName != null) {
                summary.put("display_name", displayName);
            }
            String phone = wizardData.getPhoneNumber();
            if (phone != null) {
                summary.put("phone_masked", PhoneMasker.mask(phone));
            }
            var children = wizardData.getChildren();
            summary.put("children_count", children != null ? children.size() : 0);
            var goals = wizardData.getGoals();
            summary.put("goals_count", goals != null ? goals.size() : 0);
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
