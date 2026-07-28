package com.dadcoach.onboarding;

import com.dadcoach.onboarding.invitation.*;
import com.dadcoach.onboarding.security.InvitationAuditService;
import com.dadcoach.onboarding.security.OnboardingRateLimiter;
import com.dadcoach.onboarding.security.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationController Unit Tests")
class InvitationControllerTest {

    @Mock private InvitationService invitationService;
    @Mock private OnboardingRateLimiter rateLimiter;
    @Mock private InvitationAuditService auditService;

    private InvitationController controller;
    private MockHttpServletRequest request;

    private static final String VALID_TOKEN = "aBcDeFgHiJkLmNoPqRsTuVwXyZ012345";
    private static final String CLIENT_IP = "192.168.1.1";

    @BeforeEach
    void setUp() {
        controller = new InvitationController(invitationService, rateLimiter, auditService);
        request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
    }

    @Nested
    @DisplayName("GET /invitations/{token}/validate")
    class ValidateTokenTests {

        @Test
        @DisplayName("returns 200 with valid invitation data")
        void validToken_returns200() {
            when(rateLimiter.checkIpLimit(CLIENT_IP)).thenReturn(RateLimitResult.allowed(9));
            when(invitationService.validate(VALID_TOKEN, CLIENT_IP)).thenReturn(
                    new InvitationValidationResult(
                            InvitationValidationResult.Status.VALID,
                            UUID.randomUUID(),
                            InvitationType.REUSABLE,
                            Instant.now().plus(30, ChronoUnit.DAYS),
                            42
                    )
            );

            var response = controller.validateToken(VALID_TOKEN, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().invitationType()).isEqualTo("REUSABLE");
            assertThat(response.getBody().remainingUses()).isEqualTo(42);
        }

        @Test
        @DisplayName("throws OnboardingRateLimitException when rate limited")
        void rateLimited_throws429() {
            when(rateLimiter.checkIpLimit(CLIENT_IP)).thenReturn(RateLimitResult.blocked(3600));

            assertThatThrownBy(() -> controller.validateToken(VALID_TOKEN, request))
                    .isInstanceOf(OnboardingRateLimitException.class);

            verify(auditService).logValidationAttempt(eq(VALID_TOKEN), eq("VALIDATION"),
                    eq("RATE_LIMITED"), eq(CLIENT_IP), isNull());
        }

        @Test
        @DisplayName("throws InvitationNotFoundException when token not found")
        void tokenNotFound_throws404() {
            when(rateLimiter.checkIpLimit(CLIENT_IP)).thenReturn(RateLimitResult.allowed(9));
            when(invitationService.validate(VALID_TOKEN, CLIENT_IP))
                    .thenReturn(InvitationValidationResult.notFound());

            assertThatThrownBy(() -> controller.validateToken(VALID_TOKEN, request))
                    .isInstanceOf(InvitationNotFoundException.class);
        }

        @Test
        @DisplayName("throws InvitationExpiredException when expired")
        void expired_throws410() {
            when(rateLimiter.checkIpLimit(CLIENT_IP)).thenReturn(RateLimitResult.allowed(9));
            when(invitationService.validate(VALID_TOKEN, CLIENT_IP))
                    .thenReturn(InvitationValidationResult.expired());

            assertThatThrownBy(() -> controller.validateToken(VALID_TOKEN, request))
                    .isInstanceOf(InvitationExpiredException.class);
        }

        @Test
        @DisplayName("throws InvitationRevokedException when revoked")
        void revoked_throws410() {
            when(rateLimiter.checkIpLimit(CLIENT_IP)).thenReturn(RateLimitResult.allowed(9));
            when(invitationService.validate(VALID_TOKEN, CLIENT_IP))
                    .thenReturn(InvitationValidationResult.revoked());

            assertThatThrownBy(() -> controller.validateToken(VALID_TOKEN, request))
                    .isInstanceOf(InvitationRevokedException.class);
        }

        @Test
        @DisplayName("throws InvitationExhaustedException when max uses reached")
        void exhausted_throws410() {
            when(rateLimiter.checkIpLimit(CLIENT_IP)).thenReturn(RateLimitResult.allowed(9));
            when(invitationService.validate(VALID_TOKEN, CLIENT_IP))
                    .thenReturn(InvitationValidationResult.exhausted());

            assertThatThrownBy(() -> controller.validateToken(VALID_TOKEN, request))
                    .isInstanceOf(InvitationExhaustedException.class);
        }

        @Test
        @DisplayName("uses X-Forwarded-For header for client IP")
        void usesXForwardedFor() {
            String realIp = "10.0.0.1";
            request.addHeader("X-Forwarded-For", realIp + ", 192.168.0.1");
            when(rateLimiter.checkIpLimit(realIp)).thenReturn(RateLimitResult.allowed(9));
            when(invitationService.validate(VALID_TOKEN, realIp)).thenReturn(
                    InvitationValidationResult.notFound());

            assertThatThrownBy(() -> controller.validateToken(VALID_TOKEN, request))
                    .isInstanceOf(InvitationNotFoundException.class);

            verify(rateLimiter).checkIpLimit(realIp);
        }

        @Test
        @DisplayName("logs audit entry on every validation attempt")
        void logsAuditEntry() {
            when(rateLimiter.checkIpLimit(CLIENT_IP)).thenReturn(RateLimitResult.allowed(9));
            when(invitationService.validate(VALID_TOKEN, CLIENT_IP))
                    .thenReturn(InvitationValidationResult.notFound());

            assertThatThrownBy(() -> controller.validateToken(VALID_TOKEN, request))
                    .isInstanceOf(InvitationNotFoundException.class);

            verify(auditService).logValidationAttempt(
                    eq(VALID_TOKEN), eq("VALIDATION"), eq("NOT_FOUND"), eq(CLIENT_IP), isNull());
        }
    }

    @Nested
    @DisplayName("DELETE /invitations/{invitationId}")
    class RevokeTests {

        @Test
        @DisplayName("returns 204 on successful revocation")
        void revokeSuccess_returns204() {
            UUID invitationId = UUID.randomUUID();
            doNothing().when(invitationService).revoke(eq(invitationId), any(UUID.class));

            var response = controller.revokeInvitation(invitationId, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(invitationService).revoke(eq(invitationId), any(UUID.class));
        }
    }
}
