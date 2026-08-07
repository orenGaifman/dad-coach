package com.dadcoach.calendar;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for Google Calendar OAuth flow.
 * 
 * <p>Provides endpoints for initiating calendar connection and handling OAuth callbacks.
 * The flow is:
 * <ol>
 *   <li>Frontend/WhatsApp sends user to GET /api/v1/calendar/connect/{fatherId}</li>
 *   <li>User authorizes the app on Google</li>
 *   <li>Google redirects to GET /api/v1/calendar/callback with code and state</li>
 *   <li>We exchange the code for tokens and store them</li>
 *   <li>User is redirected to the dashboard with success message</li>
 * </ol>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "Calendar", description = "Google Calendar OAuth endpoints")
public class CalendarOAuthController {

    private static final Logger log = LoggerFactory.getLogger(CalendarOAuthController.class);

    private final GoogleCalendarService googleCalendarService;
    private final FatherRepository fatherRepository;

    @Value("${dad-coach.web.base-url:http://localhost:3000}")
    private String webBaseUrl;

    public CalendarOAuthController(GoogleCalendarService googleCalendarService,
                                   FatherRepository fatherRepository) {
        this.googleCalendarService = googleCalendarService;
        this.fatherRepository = fatherRepository;
    }

    /**
     * Initiates Google Calendar OAuth flow by redirecting to Google's authorization page.
     * 
     * @param fatherId the ID of the father connecting their calendar
     * @return redirect to Google OAuth authorization page
     */
    @GetMapping("/connect/{fatherId}")
    @Operation(
        summary = "Start Google Calendar connection",
        description = "Redirects the user to Google's OAuth authorization page to connect their calendar"
    )
    @ApiResponse(responseCode = "302", description = "Redirect to Google OAuth")
    @ApiResponse(responseCode = "404", description = "Father not found")
    public ResponseEntity<Void> connectCalendar(
            @Parameter(description = "Father ID") @PathVariable Long fatherId) {
        
        log.info("Starting Google Calendar OAuth flow for father {}", fatherId);
        
        // Verify father exists
        Optional<Father> father = fatherRepository.findById(fatherId);
        if (father.isEmpty()) {
            log.warn("Father {} not found for calendar connection", fatherId);
            return ResponseEntity.notFound().build();
        }
        
        String authUrl = googleCalendarService.getAuthorizationUrl(fatherId);
        log.debug("Redirecting father {} to Google OAuth: {}", fatherId, authUrl);
        
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .build();
    }

    /**
     * Handles OAuth callback from Google after user authorization.
     * 
     * <p>Google redirects here with either:
     * <ul>
     *   <li>code + state (success) - we exchange code for tokens</li>
     *   <li>error + state (failure) - user denied access or error occurred</li>
     * </ul>
     * </p>
     * 
     * @param code the authorization code from Google (if successful)
     * @param state the father ID we passed during authorization
     * @param error the error code if user denied access
     * @return redirect to dashboard with success/failure message
     */
    @GetMapping("/callback")
    @Operation(
        summary = "Handle Google OAuth callback",
        description = "Receives the authorization code from Google and exchanges it for access tokens"
    )
    @ApiResponse(responseCode = "302", description = "Redirect to dashboard")
    public ResponseEntity<Void> handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        
        // Handle user denial or errors
        if (error != null) {
            log.warn("Google OAuth error for state {}: {}", state, error);
            String redirectUrl = webBaseUrl + "/dashboard?calendar_error=" + error;
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();
        }
        
        // Validate required params
        if (code == null || state == null) {
            log.error("Missing code or state in OAuth callback");
            String redirectUrl = webBaseUrl + "/dashboard?calendar_error=missing_params";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();
        }
        
        // Parse father ID from state
        Long fatherId;
        try {
            fatherId = Long.parseLong(state);
        } catch (NumberFormatException e) {
            log.error("Invalid state parameter: {}", state);
            String redirectUrl = webBaseUrl + "/dashboard?calendar_error=invalid_state";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();
        }
        
        log.info("Processing Google OAuth callback for father {}", fatherId);
        
        // Exchange code for tokens
        boolean success = googleCalendarService.handleOAuthCallback(code, fatherId);
        
        if (success) {
            log.info("Successfully connected Google Calendar for father {}", fatherId);
            String redirectUrl = webBaseUrl + "/dashboard?calendar_connected=true";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();
        } else {
            log.error("Failed to exchange OAuth code for father {}", fatherId);
            String redirectUrl = webBaseUrl + "/dashboard?calendar_error=token_exchange_failed";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();
        }
    }

    /**
     * Returns the calendar connection status for a father.
     * 
     * @param fatherId the father ID
     * @return connection status and connect URL if not connected
     */
    @GetMapping("/status/{fatherId}")
    @Operation(
        summary = "Get calendar connection status",
        description = "Returns whether the father's Google Calendar is connected"
    )
    @ApiResponse(responseCode = "200", description = "Status returned")
    @ApiResponse(responseCode = "404", description = "Father not found")
    public ResponseEntity<Map<String, Object>> getCalendarStatus(
            @Parameter(description = "Father ID") @PathVariable Long fatherId) {
        
        Optional<Father> fatherOpt = fatherRepository.findById(fatherId);
        if (fatherOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Father father = fatherOpt.get();
        boolean connected = father.hasGoogleCalendarConfigured();
        
        Map<String, Object> response = Map.of(
            "connected", connected,
            "connect_url", connected ? "" : "/api/v1/calendar/connect/" + fatherId
        );
        
        return ResponseEntity.ok(response);
    }
}
