package com.dadcoach.onboarding;

import com.dadcoach.onboarding.dto.ErrorResponse;
import com.dadcoach.onboarding.dto.InvitationCreateRequestDto;
import com.dadcoach.onboarding.dto.InvitationCreateResponseDto;
import com.dadcoach.onboarding.dto.InvitationValidationResponse;
import com.dadcoach.onboarding.invitation.Invitation;
import com.dadcoach.onboarding.invitation.InvitationCreateRequest;
import com.dadcoach.onboarding.invitation.InvitationService;
import com.dadcoach.onboarding.invitation.InvitationType;
import com.dadcoach.onboarding.invitation.InvitationValidationResult;
import com.dadcoach.onboarding.security.InvitationAuditService;
import com.dadcoach.onboarding.security.OnboardingRateLimiter;
import com.dadcoach.onboarding.security.RateLimitResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for invitation management.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "Invitations", description = "Invitation token validation and management")
public class InvitationController {

    private static final Logger log = LoggerFactory.getLogger(InvitationController.class);

    private final InvitationService invitationService;
    private final OnboardingRateLimiter rateLimiter;
    private final InvitationAuditService auditService;

    public InvitationController(InvitationService invitationService,
                                 OnboardingRateLimiter rateLimiter,
                                 InvitationAuditService auditService) {
        this.invitationService = invitationService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @GetMapping("/{token}/validate")
    @Operation(summary = "Validate an invitation token",
        description = "Checks if the token is valid, not expired, and has remaining uses.")
    @ApiResponse(responseCode = "200", description = "Invitation is valid",
        content = @Content(schema = @Schema(implementation = InvitationValidationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Token not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "410", description = "Invitation expired, revoked, or exhausted",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<InvitationValidationResponse> validateToken(
            @PathVariable @Parameter(description = "32-character invitation token") String token,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        RateLimitResult rateLimitResult = rateLimiter.checkIpLimit(clientIp);
        if (!rateLimitResult.allowed()) {
            auditService.logValidationAttempt(token, "VALIDATION", "RATE_LIMITED", clientIp, userAgent);
            throw new OnboardingExceptions.RateLimitExceeded(rateLimitResult.retryAfterSeconds());
        }

        InvitationValidationResult result = invitationService.validate(token, clientIp);
        auditService.logValidationAttempt(token, "VALIDATION", result.status().name(), clientIp, userAgent);

        return switch (result.status()) {
            case VALID -> ResponseEntity.ok(new InvitationValidationResponse(
                    result.type().name(), null, result.expiresAt(), result.remainingUses()));
            case NOT_FOUND -> throw new OnboardingExceptions.InvitationNotFound(token);
            case EXPIRED -> throw new OnboardingExceptions.InvitationExpired(result.expiresAt());
            case REVOKED -> throw new OnboardingExceptions.InvitationRevoked();
            case EXHAUSTED -> throw new OnboardingExceptions.InvitationExhausted();
        };
    }

    @PostMapping
    @Operation(summary = "Create a new invitation",
        description = "Creates a new invitation token. Requires authenticated admin access.")
    @ApiResponse(responseCode = "201", description = "Invitation created",
        content = @Content(schema = @Schema(implementation = InvitationCreateResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<InvitationCreateResponseDto> createInvitation(
            @Valid @RequestBody InvitationCreateRequestDto requestDto,
            HttpServletRequest request) {

        UUID createdBy = UUID.randomUUID(); // TODO: Extract from authentication context

        InvitationType type = InvitationType.valueOf(requestDto.type().toUpperCase());
        int maxUses = requestDto.maxUses() != null ? requestDto.maxUses() : (type == InvitationType.REUSABLE ? 50 : 1);
        InvitationCreateRequest createRequest = new InvitationCreateRequest(type, null, maxUses);

        Invitation invitation = invitationService.create(createRequest, createdBy);

        InvitationCreateResponseDto responseDto = new InvitationCreateResponseDto(
                invitation.getInvitationId(),
                invitation.getToken(),
                "https://dadcoach.app/join/" + invitation.getToken(),
                invitation.getType().name(),
                invitation.getMaxUses(),
                invitation.getExpiresAt(),
                invitation.getStatus().name()
        );

        log.info("Invitation created: id={}, type={}", invitation.getInvitationId(), invitation.getType());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @DeleteMapping("/{invitationId}")
    @Operation(summary = "Revoke an invitation",
        description = "Revokes an invitation, preventing further use.")
    @ApiResponse(responseCode = "204", description = "Invitation revoked")
    @ApiResponse(responseCode = "404", description = "Invitation not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> revokeInvitation(
            @PathVariable @Parameter(description = "Invitation UUID") UUID invitationId,
            HttpServletRequest request) {

        UUID revokedBy = UUID.randomUUID(); // TODO: Extract from authentication context
        invitationService.revoke(invitationId, revokedBy);
        log.info("Invitation revoked: id={}", invitationId);
        return ResponseEntity.noContent().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
