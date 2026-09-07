package com.dadcoach.api.tools;

import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.father.FatherService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
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
import java.util.Set;

/**
 * REST API controller for tool execution from ai-workflow-platform.
 * 
 * <p>This controller provides a generic endpoint for executing tools from the
 * ai-workflow-platform. Each tool call includes:</p>
 * <ul>
 *   <li>Tool key in the path: POST /api/tools/{toolKey}</li>
 *   <li>Execution metadata: executionId, idempotencyKey, userId</li>
 *   <li>Tool-specific parameters in the request body</li>
 * </ul>
 * 
 * <h2>Authentication</h2>
 * <p>All requests must include a valid API key in the X-API-Key header.
 * The API key is configured via the tool-api.api-key property.</p>
 * 
 * <h2>User Identification</h2>
 * <p>The userId can be either:</p>
 * <ul>
 *   <li>A numeric father ID (e.g., "123")</li>
 *   <li>A phone number (e.g., "+972503020551") which will be resolved to a father ID</li>
 * </ul>
 * 
 * <h2>Pre-Registration Tools</h2>
 * <p>Some tools (like save_user_profile) can create the father if they don't exist.
 * These tools skip the normal father resolution step and handle user creation internally.</p>
 * 
 * <h2>Available Tools (17 total)</h2>
 * <h3>Pre-Registration Tools</h3>
 * <ul>
 *   <li><code>save_user_profile</code> - Create or update father profile (creates father if needed)</li>
 * </ul>
 * 
 * <h3>Scheduling Tools</h3>
 * <ul>
 *   <li><code>schedule_quality_time</code> - Schedule a new Quality Time event</li>
 *   <li><code>reschedule_quality_time</code> - Reschedule an existing Quality Time</li>
 *   <li><code>cancel_quality_time</code> - Cancel a scheduled Quality Time</li>
 *   <li><code>complete_quality_time</code> - Mark a Quality Time as completed</li>
 *   <li><code>show_available_slots</code> - Get available calendar slots</li>
 * </ul>
 * 
 * <h3>Weekly Goal Tools</h3>
 * <ul>
 *   <li><code>set_weekly_goal</code> - Set a new weekly goal</li>
 *   <li><code>get_weekly_goal_status</code> - Get current goal progress</li>
 *   <li><code>show_weekly_summary</code> - Get weekly summary data</li>
 * </ul>
 * 
 * <h3>Progress & Dashboard Tools</h3>
 * <ul>
 *   <li><code>show_progress</code> - Get father's progress metrics</li>
 *   <li><code>get_dashboard_link</code> - Get URL to the dashboard</li>
 * </ul>
 * 
 * <h3>Activity & Communication Tools</h3>
 * <ul>
 *   <li><code>get_activity_ideas</code> - Get activity suggestions by age/type</li>
 *   <li><code>greet</code> - Get greeting message data</li>
 *   <li><code>show_help</code> - Get help menu data</li>
 *   <li><code>clarify</code> - Get clarification prompt</li>
 *   <li><code>connect_calendar</code> - Get calendar OAuth URL</li>
 *   <li><code>get_upcoming_quality_time</code> - Get next scheduled Quality Time</li>
 * </ul>
 * 
 * @see ToolDispatcher
 * @see ToolExecutionRequest
 * @see ToolExecutionResponse
 */
@RestController
@RequestMapping("/api/tools")
@Tag(name = "Tool API", description = "Tool execution endpoints for ai-workflow-platform integration")
@SecurityRequirement(name = "apiKey")
public class ToolApiController {

    private static final Logger log = LoggerFactory.getLogger(ToolApiController.class);

    /** Tool key for profile creation/update - handled as pre-registration tool */
    private static final String TOOL_SAVE_USER_PROFILE = "save_user_profile";

    private final ToolDispatcher toolDispatcher;
    private final FatherRepository fatherRepository;
    private final FatherService fatherService;

    public ToolApiController(
            ToolDispatcher toolDispatcher,
            FatherRepository fatherRepository,
            FatherService fatherService) {
        this.toolDispatcher = toolDispatcher;
        this.fatherRepository = fatherRepository;
        this.fatherService = fatherService;
    }

    /**
     * Execute a tool by its key.
     * 
     * <p>This is the main endpoint for tool execution. The tool key is specified
     * in the path, and the request body contains the execution parameters.</p>
     * 
     * <p>The userId can be a numeric father ID or a phone number. Phone numbers
     * are automatically resolved to father IDs.</p>
     * 
     * <p>Pre-registration tools (like save_user_profile) can create the father
     * if they don't exist, so they skip the normal father resolution step.</p>
     * 
     * <p>Example request with phone number:</p>
     * <pre>
     * POST /api/tools/show_available_slots
     * X-API-Key: your-api-key
     * Content-Type: application/json
     * 
     * {
     *   "execution_id": "exec-123",
     *   "idempotency_key": "idmp-456",
     *   "user_id": "+972503020551",
     *   "parameters": {
     *     "days_ahead": 7
     *   }
     * }
     * </pre>
     *
     * @param toolKey the tool identifier (e.g., "schedule_quality_time")
     * @param request the execution request with userId and parameters
     * @return 200 OK with execution result on success, or error response on failure
     */
    @PostMapping("/{toolKey}")
    @Operation(
            summary = "Execute a tool",
            description = "Executes the specified tool with the provided parameters"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Tool executed successfully",
                    content = @Content(schema = @Schema(implementation = ToolExecutionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ToolExecutionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ToolExecutionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tool not found or resource not found",
                    content = @Content(schema = @Schema(implementation = ToolExecutionResponse.class))
            )
    })
    public ResponseEntity<ToolExecutionResponse> executeTool(
            @Parameter(description = "Tool identifier", example = "schedule_quality_time")
            @PathVariable("toolKey") String toolKey,
            @Valid @RequestBody ToolExecutionRequest request) {

        log.info("Tool execution request: toolKey={}, executionId={}, idempotencyKey={}, userId={}",
                toolKey, request.executionId(), request.idempotencyKey(), request.userId());

        // Handle pre-registration tools that can create the father if needed
        if (TOOL_SAVE_USER_PROFILE.equals(toolKey)) {
            return handleSaveUserProfile(request);
        }

        // Standard flow: resolve userId to fatherId first
        Long fatherId = null;
        try {
            fatherId = resolveFatherId(request);
        } catch (ResourceNotFoundException e) {
            // For new user safe tools, allow proceeding without a Father record
            if (isNewUserSafeTool(toolKey)) {
                log.info("New user safe tool '{}' called for new user: {}", toolKey, request.userId());
                // fatherId remains null - handlers will use Optional.findById(null) which returns empty
            } else {
                log.warn("Father not found for userId: {}", request.userId());
                return ResponseEntity.ok(ToolExecutionResponse.notFound("Father", request.userId()));
            }
        }

        // Create resolved request with numeric father ID
        ResolvedToolRequest resolvedRequest = new ResolvedToolRequest(
                request.executionId(),
                request.idempotencyKey(),
                fatherId,
                request.parameters()
        );

        ToolExecutionResponse response = toolDispatcher.dispatch(toolKey, resolvedRequest);

        log.info("Tool execution complete: toolKey={}, executionId={}, success={}",
                toolKey, request.executionId(), response.success());

        return ResponseEntity.ok(response);
    }

    /**
     * Handles the save_user_profile tool execution.
     * 
     * <p>This tool is special because it can create the father if they don't exist.
     * It implements an upsert pattern:</p>
     * <ul>
     *   <li>If the phone exists, update the existing father profile</li>
     *   <li>If the phone doesn't exist, create a new father and set profile</li>
     * </ul>
     *
     * @param request the tool execution request
     * @return the tool execution response
     */
    @Transactional
    private ResponseEntity<ToolExecutionResponse> handleSaveUserProfile(ToolExecutionRequest request) {
        String phone = request.userId();
        Map<String, Object> params = request.parameters() != null ? request.parameters() : Map.of();

        log.info("Handling save_user_profile: phone={}", phone);

        try {
            // Extract profile parameters
            String displayName = getStringParam(params, "displayName");
            String timezone = getStringParam(params, "timezone");
            String locale = getStringParam(params, "locale");
            String preferredCoachingTime = getStringParam(params, "preferredCoachingTime");

            if (displayName == null || displayName.isBlank()) {
                return ResponseEntity.ok(ToolExecutionResponse.invalidParameters("displayName is required"));
            }

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
            father.setDisplayName(displayName);
            if (timezone != null && !timezone.isBlank()) {
                father.setTimezone(timezone);
            }
            if (locale != null && !locale.isBlank()) {
                father.setLocale(locale);
            }
            if (preferredCoachingTime != null && !preferredCoachingTime.isBlank()) {
                try {
                    LocalTime time = LocalTime.parse(preferredCoachingTime);
                    father.setPreferredCoachingTime(time);
                } catch (DateTimeParseException e) {
                    log.warn("Invalid preferredCoachingTime format: {}", preferredCoachingTime);
                    // Continue without setting - don't fail the whole request
                }
            }

            // Save the updated father
            father = fatherRepository.save(father);

            log.info("Profile {} successfully: fatherId={}", created ? "created" : "updated", father.getId());

            // Build response data
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fatherId", father.getId());
            data.put("displayName", father.getDisplayName());
            data.put("timezone", father.getTimezone());
            data.put("locale", father.getLocale());
            if (father.getPreferredCoachingTime() != null) {
                data.put("preferredCoachingTime", father.getPreferredCoachingTime().toString());
            }

            return ResponseEntity.ok(ToolExecutionResponse.success(data));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for save_user_profile: {}", e.getMessage());
            return ResponseEntity.ok(ToolExecutionResponse.invalidParameters(e.getMessage()));
        } catch (Exception e) {
            log.error("Error executing save_user_profile: {}", e.getMessage(), e);
            return ResponseEntity.ok(ToolExecutionResponse.failure(
                    "Internal error: " + e.getMessage(), "INTERNAL_ERROR"));
        }
    }

    /**
     * Extracts a string parameter from the parameters map.
     */
    private String getStringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Resolves a userId (phone number or numeric ID) to a father ID.
     *
     * @param request the tool execution request
     * @return the resolved father ID
     * @throws ResourceNotFoundException if the father is not found
     */
    private Long resolveFatherId(ToolExecutionRequest request) {
        String userId = request.userId();
        
        // Try to parse as numeric ID first
        Long numericId = request.resolveNumericUserId();
        if (numericId != null) {
            // Verify father exists
            if (fatherRepository.existsById(numericId)) {
                log.debug("Resolved userId as numeric ID: {}", numericId);
                return numericId;
            }
            throw new ResourceNotFoundException("Father", numericId);
        }

        // Must be a phone number - look up by phone
        log.debug("Resolving userId as phone number: {}", userId);
        Optional<Father> father = fatherRepository.findByPhone(userId);
        if (father.isPresent()) {
            Long fatherId = father.get().getId();
            log.debug("Resolved phone {} to fatherId {}", userId, fatherId);
            return fatherId;
        }

        throw new ResourceNotFoundException("Father", userId);
    }

    /**
     * List all available tools.
     * 
     * <p>Returns a list of all registered tool keys that can be executed
     * via the POST /{toolKey} endpoint.</p>
     *
     * @return 200 OK with list of available tools
     */
    @GetMapping
    @Operation(
            summary = "List available tools",
            description = "Returns a list of all registered tool keys"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of available tools"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid API key"
            )
    })
    public ResponseEntity<Map<String, Object>> listTools() {
        Set<String> tools = toolDispatcher.getAvailableTools();
        // Add pre-registration tools to the list
        Set<String> allTools = new java.util.TreeSet<>(tools);
        allTools.add(TOOL_SAVE_USER_PROFILE);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tools", allTools);
        response.put("count", allTools.size());

        log.debug("Listed {} available tools", allTools.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for the Tool API.
     * 
     * <p>Returns a simple health status. This endpoint can be used for
     * monitoring and load balancer health checks.</p>
     *
     * @return 200 OK with health status
     */
    @GetMapping("/health")
    @Operation(
            summary = "Health check",
            description = "Returns health status of the Tool API"
    )
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "healthy");
        response.put("service", "tool-api");
        response.put("tools_count", toolDispatcher.getAvailableTools().size() + 1); // +1 for save_user_profile

        return ResponseEntity.ok(response);
    }

    /**
     * Internal resolved request with numeric father ID.
     */
    public record ResolvedToolRequest(
            String executionId,
            String idempotencyKey,
            Long userId,
            Map<String, Object> parameters
    ) {
        public String getStringParam(String key) {
            if (parameters == null) return null;
            Object value = parameters.get(key);
            return value != null ? value.toString() : null;
        }

        public Long getLongParam(String key) {
            if (parameters == null) return null;
            Object value = parameters.get(key);
            if (value == null) return null;
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public Integer getIntParam(String key) {
            if (parameters == null) return null;
            Object value = parameters.get(key);
            if (value == null) return null;
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public Boolean getBooleanParam(String key) {
            if (parameters == null) return null;
            Object value = parameters.get(key);
            if (value == null) return null;
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return Boolean.parseBoolean(value.toString());
        }
    }

    /**
     * Checks if a tool can be executed without a Father record.
     * These tools handle new user scenarios gracefully.
     * 
     * @param toolKey the tool key to check
     * @return true if the tool can work without a Father record
     */
    private boolean isNewUserSafeTool(String toolKey) {
        return Set.of("greet", "show_help", "clarify").contains(toolKey);
    }
}
