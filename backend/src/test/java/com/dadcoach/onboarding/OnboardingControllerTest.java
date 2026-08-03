package com.dadcoach.onboarding;

import com.dadcoach.onboarding.activation.ActivationService;
import com.dadcoach.onboarding.activation.ActivationStatusResponse;
import com.dadcoach.onboarding.invitation.InvitationService;
import com.dadcoach.onboarding.invitation.InvitationValidationResult;
import com.dadcoach.onboarding.provisioning.ActivationStatus;
import com.dadcoach.onboarding.provisioning.ProvisioningResult;
import com.dadcoach.onboarding.provisioning.ProvisioningService;
import com.dadcoach.onboarding.security.CsrfTokenService;
import com.dadcoach.onboarding.security.OnboardingRateLimiter;
import com.dadcoach.onboarding.security.RateLimitResult;
import com.dadcoach.onboarding.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OnboardingController Unit Tests")
class OnboardingControllerTest {

    @Mock private OnboardingSessionService sessionService;
    @Mock private InvitationService invitationService;
    @Mock private ProvisioningService provisioningService;
    @Mock private ActivationService activationService;
    @Mock private OnboardingRateLimiter rateLimiter;
    @Mock private CsrfTokenService csrfTokenService;
    @Mock private SessionCookieManager cookieManager;

    private OnboardingController controller;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String CSRF_TOKEN = "valid-csrf-token";

    @BeforeEach
    void setUp() {
        controller = new OnboardingController(
                sessionService, invitationService, provisioningService,
                activationService, rateLimiter, csrfTokenService, cookieManager);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        response = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("POST /sessions")
    class CreateSessionTests {

        @Test
        @DisplayName("creates session and returns 201 with CSRF token")
        void createSession_returns201() {
            var sessionCreateReq = new com.dadcoach.onboarding.dto.SessionCreateRequest(
                    "aBcDeFgHiJkLmNoPqRsTuVwXyZ012345");
            when(rateLimiter.checkIpLimit(anyString())).thenReturn(RateLimitResult.allowed(9));

            OnboardingSession session = buildSession(SESSION_ID, WizardStep.WELCOME, SessionStatus.IN_PROGRESS);
            when(sessionService.create(any(), any(), any())).thenReturn(session);
            when(csrfTokenService.generateToken(SESSION_ID)).thenReturn(CSRF_TOKEN);

            var result = controller.createSession(sessionCreateReq, request, response);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().sessionId()).isEqualTo(SESSION_ID);
            assertThat(result.getBody().currentStep()).isEqualTo("WELCOME");
            assertThat(result.getBody().csrfToken()).isEqualTo(CSRF_TOKEN);
            verify(cookieManager).createCookie(eq(response), eq(SESSION_ID.toString()));
        }

        @Test
        @DisplayName("rate limited returns exception")
        void rateLimited_throwsException() {
            var sessionCreateReq = new com.dadcoach.onboarding.dto.SessionCreateRequest(
                    "aBcDeFgHiJkLmNoPqRsTuVwXyZ012345");
            when(rateLimiter.checkIpLimit(anyString())).thenReturn(RateLimitResult.blocked(3600));

            assertThatThrownBy(() -> controller.createSession(sessionCreateReq, request, response))
                    .isInstanceOf(OnboardingRateLimitException.class);
        }
    }

    @Nested
    @DisplayName("GET /sessions/{id}")
    class GetSessionTests {

        @Test
        @DisplayName("returns session details when cookie matches")
        void validCookie_returnsSession() {
            when(cookieManager.readSessionId(request)).thenReturn(Optional.of(SESSION_ID.toString()));
            OnboardingSession session = buildSession(SESSION_ID, WizardStep.CHILDREN, SessionStatus.IN_PROGRESS);
            when(sessionService.getSession(SESSION_ID)).thenReturn(session);

            var result = controller.getSession(SESSION_ID, request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().currentStep()).isEqualTo("CHILDREN");
        }

        @Test
        @DisplayName("allows request when cookie is missing (cross-origin support)")
        void missingCookie_allowsRequest() {
            // Per validateSessionCookie: cookie absence is allowed for cross-origin deployments
            // Session ID in path + CSRF validation is sufficient
            when(cookieManager.readSessionId(request)).thenReturn(Optional.empty());
            OnboardingSession session = buildSession(SESSION_ID, WizardStep.CHILDREN, SessionStatus.IN_PROGRESS);
            when(sessionService.getSession(SESSION_ID)).thenReturn(session);

            var result = controller.getSession(SESSION_ID, request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
        }

        @Test
        @DisplayName("throws SessionExpiredException when cookie doesn't match")
        void mismatchedCookie_throwsException() {
            when(cookieManager.readSessionId(request)).thenReturn(Optional.of(UUID.randomUUID().toString()));

            assertThatThrownBy(() -> controller.getSession(SESSION_ID, request))
                    .isInstanceOf(SessionExpiredException.class);
        }
    }

    @Nested
    @DisplayName("POST /sessions/{id}/complete")
    class CompleteTests {

        @Test
        @DisplayName("returns 201 with provisioning result")
        void complete_returns201() {
            when(cookieManager.readSessionId(request)).thenReturn(Optional.of(SESSION_ID.toString()));
            when(csrfTokenService.validateToken(eq(SESSION_ID), anyString())).thenReturn(true);
            request.addHeader(CsrfTokenService.CSRF_HEADER, CSRF_TOKEN);

            OnboardingSession session = buildSession(SESSION_ID, WizardStep.ACTIVATION, SessionStatus.IN_PROGRESS);
            when(sessionService.getSession(SESSION_ID)).thenReturn(session);
            
            // Controller calls getTokenById to get the token before validating
            String invitationToken = "aBcDeFgHiJkLmNoPqRsTuVwXyZ012345";
            when(invitationService.getTokenById(session.getInvitationId())).thenReturn(invitationToken);
            when(invitationService.validate(eq(invitationToken), eq("192.168.1.1")))
                    .thenReturn(InvitationValidationResult.valid(buildInvitation()));

            ProvisioningResult provResult = new ProvisioningResult(
                    1L, UUID.randomUUID(), List.of(), List.of(), UUID.randomUUID(),
                    "https://wa.me/972501234567?text=%F0%9F%9A%80%20START");
            when(provisioningService.provision(SESSION_ID)).thenReturn(provResult);

            var result = controller.complete(SESSION_ID, request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().fatherId()).isEqualTo(1L);
            assertThat(result.getBody().deepLink()).contains("wa.me");
        }

        @Test
        @DisplayName("throws CsrfValidationException when CSRF invalid")
        void invalidCsrf_throwsException() {
            when(cookieManager.readSessionId(request)).thenReturn(Optional.of(SESSION_ID.toString()));
            // Don't add CSRF header - controller checks for null/blank first and throws

            assertThatThrownBy(() -> controller.complete(SESSION_ID, request))
                    .isInstanceOf(CsrfValidationException.class);
        }
    }

    @Nested
    @DisplayName("GET /sessions/{id}/activation-status")
    class ActivationStatusTests {

        @Test
        @DisplayName("returns activation status with long-poll support")
        void returnsStatus() {
            when(cookieManager.readSessionId(request)).thenReturn(Optional.of(SESSION_ID.toString()));
            ActivationStatusResponse statusResponse = new ActivationStatusResponse(
                    ActivationStatus.PENDING, Instant.now(), null, null, null, 0, null);
            when(activationService.getStatus(SESSION_ID, null)).thenReturn(Optional.of(statusResponse));

            var result = controller.getActivationStatus(SESSION_ID, null, request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().status()).isEqualTo(ActivationStatus.PENDING);
        }

        @Test
        @DisplayName("returns 404 when no activation record found")
        void returns404WhenNotFound() {
            when(cookieManager.readSessionId(request)).thenReturn(Optional.of(SESSION_ID.toString()));
            when(activationService.getStatus(SESSION_ID, null)).thenReturn(Optional.empty());

            var result = controller.getActivationStatus(SESSION_ID, null, request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(result.getBody()).isNull();
        }

        @Test
        @DisplayName("allows request when cookie is missing (cross-origin support)")
        void allowsRequestWhenCookieMissing() {
            // Per validateSessionCookie: cookie absence is allowed for cross-origin deployments
            when(cookieManager.readSessionId(request)).thenReturn(Optional.empty());
            ActivationStatusResponse statusResponse = new ActivationStatusResponse(
                    ActivationStatus.PENDING, Instant.now(), null, null, null, 0, null);
            when(activationService.getStatus(SESSION_ID, null)).thenReturn(Optional.of(statusResponse));

            var result = controller.getActivationStatus(SESSION_ID, null, request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private OnboardingSession buildSession(UUID sessionId, WizardStep step, SessionStatus status) {
        OnboardingSession session;
        try {
            var constructor = OnboardingSession.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            session = constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        session.setSessionId(sessionId);
        session.setCurrentStep(step);
        session.setStatus(status);
        session.setLanguage("he");
        session.setExpiresAt(Instant.now().plus(72, ChronoUnit.HOURS));
        session.setInvitationId(UUID.randomUUID());
        session.setWizardData(new WizardData());
        session.setIpAddress("192.168.1.1");
        return session;
    }

    private com.dadcoach.onboarding.invitation.Invitation buildInvitation() {
        // Create invitation using reflection since constructor is protected
        try {
            var constructor = com.dadcoach.onboarding.invitation.Invitation.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            com.dadcoach.onboarding.invitation.Invitation inv = constructor.newInstance();
            inv.setInvitationId(UUID.randomUUID());
            inv.setToken("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345");
            inv.setType(com.dadcoach.onboarding.invitation.InvitationType.REUSABLE);
            inv.setStatus(com.dadcoach.onboarding.invitation.InvitationStatus.OPENED);
            inv.setMaxUses(50);
            inv.setCurrentUses(1);
            inv.setExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));
            return inv;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
