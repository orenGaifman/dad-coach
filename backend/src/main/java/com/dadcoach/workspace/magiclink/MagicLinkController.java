package com.dadcoach.workspace.magiclink;

import com.dadcoach.workspace.magiclink.MagicLinkService.MagicLinkValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for magic link authentication.
 * 
 * This endpoint is public (no auth required) because the magic link token
 * itself serves as the authentication credential.
 */
@RestController
@RequestMapping("/api/v1/auth/magic-link")
public class MagicLinkController {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkController.class);

    private final MagicLinkService magicLinkService;

    public MagicLinkController(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;
    }

    /**
     * Validates a magic link token and returns authentication credentials.
     * 
     * Request body:
     * <pre>
     * {
     *   "token": "abc123..."
     * }
     * </pre>
     * 
     * Success response:
     * <pre>
     * {
     *   "response_status": "OK",
     *   "access_token": "eyJ...",
     *   "redirect_path": "/growth"
     * }
     * </pre>
     * 
     * Error response:
     * <pre>
     * {
     *   "response_status": "ERROR",
     *   "error_code": "TOKEN_EXPIRED",
     *   "error_message": "This link has expired..."
     * }
     * </pre>
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestBody ValidateTokenRequest request) {
        
        if (request.token() == null || request.token().isBlank()) {
            log.warn("Magic link validation called with empty token");
            return ResponseEntity.badRequest().body(Map.of(
                    "response_status", "ERROR",
                    "error_code", "TOKEN_MISSING",
                    "error_message", "Token is required"
            ));
        }

        MagicLinkValidationResult result = magicLinkService.validateToken(request.token());

        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "response_status", "OK",
                    "access_token", result.accessToken(),
                    "redirect_path", result.redirectPath() != null ? result.redirectPath() : "/dashboard"
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "response_status", "ERROR",
                    "error_code", result.errorCode(),
                    "error_message", result.errorMessage()
            ));
        }
    }

    /**
     * Request DTO for token validation.
     */
    public record ValidateTokenRequest(String token) {}
}
