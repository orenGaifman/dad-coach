package com.dadcoach.api.profile;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.father.FatherService;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST API controller for profile creation/update from ai-workflow-platform.
 * 
 * <p>This controller provides the POST /api/profile endpoint that creates or updates
 * a father profile. It implements an upsert pattern:</p>
 * <ul>
 *   <li>If the external_user_id (phone) exists, update the existing father profile</li>
 *   <li>If the external_user_id (phone) doesn't exist, create a new father and set profile</li>
 * </ul>
 * 
 * <h2>Authentication</h2>
 * <p>All requests must include a valid API key in the X-API-Key header.
 * The API key is the same one used for the Tool API.</p>
 * 
 * @see FatherService
 */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile API", description = "Profile creation/update endpoints for ai-workflow-platform integration")
@SecurityRequirement(name = "apiKey")
public class ProfileApiController {

    private static final Logger log = LoggerFactory.getLogger(ProfileApiController.class);

    private final FatherRepository fatherRepository;
    private final FatherService fatherService;

    public ProfileApiController(FatherRepository fatherRepository, FatherService fatherService) {
        this.fatherRepository = fatherRepository;
        this.fatherService = fatherService;
    }

    /**
     * Create or update a father profile (upsert).
     * 
     * <p>If a father with the given external_user_id (phone) exists, their profile
     * fields are updated. If not, a new father is created with the provided profile.</p>
     * 
     * <p>Example request:</p>
     * <pre>
     * POST /api/profile
     * X-API-Key: your-api-key
     * Content-Type: application/json
     * 
     * {
     *   "external_user_id": "+972503020553",
     *   "display_name": "אבא גדול",
     *   "timezone": "Asia/Jerusalem",
     *   "locale": "he-IL",
     *   "preferred_coaching_time": "09:00"
     * }
     * </pre>
     *
     * @param request the profile creation/update request
     * @return 200 OK with the created/updated profile data
     */
    @PostMapping
    @Transactional
    @Operation(
            summary = "Create or update profile",
            description = "Creates a new father if not found, or updates existing profile (upsert)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile created or updated successfully",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))
            )
    })
    public ResponseEntity<ProfileResponse> createOrUpdateProfile(
            @Valid @RequestBody ProfileRequest request) {

        log.info("Profile upsert request: externalUserId={}", request.externalUserId());

        String phone = request.externalUserId();
        
        // Try to find existing father by phone
        Optional<Father> existingFather = fatherRepository.findByPhone(phone);
        
        Father father;
        boolean created;
        
        if (existingFather.isPresent()) {
            // Update existing father
            father = existingFather.get();
            created = false;
            log.info("Updating existing father profile: fatherId={}", father.getId());
        } else {
            // Create new father
            father = fatherService.createFather(phone);
            created = true;
            log.info("Created new father: fatherId={}", father.getId());
        }

        // Update profile fields
        if (request.displayName() != null && !request.displayName().isBlank()) {
            father.setDisplayName(request.displayName());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            father.setTimezone(request.timezone());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            father.setLocale(request.locale());
        }
        if (request.preferredCoachingTime() != null && !request.preferredCoachingTime().isBlank()) {
            try {
                LocalTime time = LocalTime.parse(request.preferredCoachingTime());
                father.setPreferredCoachingTime(time);
            } catch (DateTimeParseException e) {
                log.warn("Invalid preferredCoachingTime format: {}", request.preferredCoachingTime());
                // Continue without setting - don't fail the whole request
            }
        }

        // Save the updated father
        father = fatherRepository.save(father);

        log.info("Profile {} successfully: fatherId={}", created ? "created" : "updated", father.getId());

        // Build response
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fatherId", father.getId());
        data.put("displayName", father.getDisplayName());
        data.put("timezone", father.getTimezone());
        data.put("locale", father.getLocale());
        if (father.getPreferredCoachingTime() != null) {
            data.put("preferredCoachingTime", father.getPreferredCoachingTime().toString());
        }

        return ResponseEntity.ok(new ProfileResponse(true, data, null));
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────

    /**
     * Request DTO for profile creation/update.
     */
    public record ProfileRequest(
            @JsonProperty("external_user_id")
            @NotBlank(message = "external_user_id is required")
            String externalUserId,

            @JsonProperty("display_name")
            @NotBlank(message = "display_name is required")
            @Size(min = 1, max = 100, message = "display_name must be 1-100 characters")
            String displayName,

            @JsonProperty("timezone")
            String timezone,

            @JsonProperty("locale")
            String locale,

            @JsonProperty("preferred_coaching_time")
            String preferredCoachingTime
    ) {}

    /**
     * Response DTO for profile operations.
     */
    public record ProfileResponse(
            boolean success,
            Map<String, Object> data,
            @JsonProperty("error_message")
            String errorMessage
    ) {
        public static ProfileResponse failure(String message) {
            return new ProfileResponse(false, null, message);
        }
    }
}
