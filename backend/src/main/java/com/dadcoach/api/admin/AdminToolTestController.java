package com.dadcoach.api.admin;

import com.dadcoach.api.context.ContextProviderRequest;
import com.dadcoach.api.context.ContextProviderResponse;
import com.dadcoach.api.context.ContextProviderRouter;
import com.dadcoach.api.tools.ToolDispatcher;
import com.dadcoach.api.tools.ToolApiController;
import com.dadcoach.api.tools.ToolExecutionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Admin controller for testing tools and context providers.
 * 
 * <p>This controller provides endpoints to test individual tools and context providers
 * without going through the full workflow platform integration. Useful for:</p>
 * <ul>
 *   <li>Verifying tool implementations work correctly</li>
 *   <li>Testing context provider data retrieval</li>
 *   <li>Debugging tool failures in production</li>
 *   <li>Manual testing during development</li>
 * </ul>
 * 
 * <p>All endpoints require admin API key authentication.</p>
 */
@RestController
@RequestMapping("/api/admin/test")
@Tag(name = "Admin Tool Testing", description = "Endpoints for testing tools and context providers")
public class AdminToolTestController {

    private static final Logger log = LoggerFactory.getLogger(AdminToolTestController.class);

    private final ToolDispatcher toolDispatcher;
    private final ContextProviderRouter contextProviderRouter;

    public AdminToolTestController(
            ToolDispatcher toolDispatcher,
            ContextProviderRouter contextProviderRouter) {
        this.toolDispatcher = toolDispatcher;
        this.contextProviderRouter = contextProviderRouter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool Testing Endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * List all available tools with their descriptions.
     */
    @GetMapping("/tools")
    @Operation(summary = "List all available tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        Set<String> tools = toolDispatcher.getAvailableTools();
        
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (String toolKey : tools) {
            Map<String, Object> toolInfo = new LinkedHashMap<>();
            toolInfo.put("key", toolKey);
            toolInfo.put("description", getToolDescription(toolKey));
            toolInfo.put("parameters", getToolParameters(toolKey));
            toolList.add(toolInfo);
        }
        
        // Sort by key
        toolList.sort((a, b) -> ((String) a.get("key")).compareTo((String) b.get("key")));
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tools", toolList);
        response.put("count", tools.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Execute a tool for testing purposes.
     */
    @PostMapping("/tools/{toolKey}")
    @Operation(summary = "Execute a tool for testing")
    public ResponseEntity<Map<String, Object>> testTool(
            @PathVariable("toolKey") String toolKey,
            @RequestBody TestToolRequest request) {
        
        log.info("Admin tool test: toolKey={}, userId={}", toolKey, request.userId());
        
        Instant startTime = Instant.now();
        
        // Build execution request
        String executionId = "admin-test-" + UUID.randomUUID().toString().substring(0, 8);
        String idempotencyKey = "admin-idmp-" + System.currentTimeMillis();
        
        ToolApiController.ResolvedToolRequest execRequest = new ToolApiController.ResolvedToolRequest(
                executionId,
                idempotencyKey,
                request.userId(),
                request.parameters() != null ? request.parameters() : Map.of()
        );
        
        // Execute
        ToolExecutionResponse execResponse = toolDispatcher.dispatch(toolKey, execRequest);
        
        Instant endTime = Instant.now();
        long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        
        // Build response with timing info
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("toolKey", toolKey);
        response.put("executionId", executionId);
        response.put("success", execResponse.success());
        response.put("data", execResponse.data());
        if (!execResponse.success()) {
            response.put("errorMessage", execResponse.errorMessage());
            response.put("errorCode", execResponse.errorCode());
        }
        response.put("durationMs", durationMs);
        response.put("timestamp", endTime.toString());
        
        log.info("Admin tool test complete: toolKey={}, success={}, durationMs={}",
                toolKey, execResponse.success(), durationMs);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Run all tools for a user and return a summary.
     */
    @PostMapping("/tools/run-all")
    @Operation(summary = "Run all tools for a user and return results summary")
    public ResponseEntity<Map<String, Object>> runAllTools(@RequestBody TestToolRequest request) {
        log.info("Admin running all tools for userId={}", request.userId());
        
        Set<String> toolKeys = toolDispatcher.getAvailableTools();
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        
        for (String toolKey : toolKeys) {
            Instant startTime = Instant.now();
            
            // Build tool-specific parameters
            Map<String, Object> params = buildTestParameters(toolKey, request);
            
            String executionId = "admin-all-" + UUID.randomUUID().toString().substring(0, 8);
            ToolApiController.ResolvedToolRequest execRequest = new ToolApiController.ResolvedToolRequest(
                    executionId, "idmp-" + System.currentTimeMillis(),
                    request.userId(), params
            );
            
            ToolExecutionResponse execResponse = toolDispatcher.dispatch(toolKey, execRequest);
            
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("toolKey", toolKey);
            result.put("success", execResponse.success());
            result.put("durationMs", durationMs);
            if (!execResponse.success()) {
                result.put("errorCode", execResponse.errorCode());
                result.put("errorMessage", execResponse.errorMessage());
                failureCount++;
            } else {
                successCount++;
                // Include data summary (not full data to keep response manageable)
                if (execResponse.data() != null) {
                    result.put("dataKeys", execResponse.data().keySet());
                }
            }
            
            results.add(result);
        }
        
        // Sort by tool key
        results.sort((a, b) -> ((String) a.get("toolKey")).compareTo((String) b.get("toolKey")));
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", request.userId());
        response.put("totalTools", toolKeys.size());
        response.put("successCount", successCount);
        response.put("failureCount", failureCount);
        response.put("results", results);
        response.put("timestamp", Instant.now().toString());
        
        log.info("Admin all tools complete: userId={}, success={}, failures={}",
                request.userId(), successCount, failureCount);
        
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Context Provider Testing Endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * List all available context providers.
     */
    @GetMapping("/context-providers")
    @Operation(summary = "List all available context providers")
    public ResponseEntity<Map<String, Object>> listContextProviders() {
        Set<String> providers = contextProviderRouter.getAvailableProviders();
        
        List<Map<String, Object>> providerList = new ArrayList<>();
        for (String providerKey : providers) {
            Map<String, Object> providerInfo = new LinkedHashMap<>();
            providerInfo.put("key", providerKey);
            providerInfo.put("description", getProviderDescription(providerKey));
            providerInfo.put("configOptions", getProviderConfigOptions(providerKey));
            providerList.add(providerInfo);
        }
        
        providerList.sort((a, b) -> ((String) a.get("key")).compareTo((String) b.get("key")));
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("providers", providerList);
        response.put("count", providers.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Load context from a provider for testing.
     */
    @PostMapping("/context-providers/{providerKey}")
    @Operation(summary = "Load context from a provider for testing")
    public ResponseEntity<Map<String, Object>> testContextProvider(
            @PathVariable("providerKey") String providerKey,
            @RequestBody TestContextRequest request) {
        
        log.info("Admin context test: providerKey={}, userId={}", providerKey, request.userId());
        
        Instant startTime = Instant.now();
        
        ContextProviderRequest ctxRequest = new ContextProviderRequest(
                request.userId(),
                request.config() != null ? request.config() : Map.of()
        );
        
        ContextProviderResponse ctxResponse = contextProviderRouter.dispatch(providerKey, ctxRequest);
        
        Instant endTime = Instant.now();
        long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("providerKey", providerKey);
        response.put("success", ctxResponse.success());
        response.put("data", ctxResponse.data());
        if (!ctxResponse.success()) {
            response.put("errorMessage", ctxResponse.errorMessage());
            response.put("errorCode", ctxResponse.errorCode());
        }
        response.put("durationMs", durationMs);
        response.put("timestamp", endTime.toString());
        
        log.info("Admin context test complete: providerKey={}, success={}, durationMs={}",
                providerKey, ctxResponse.success(), durationMs);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Run all context providers for a user.
     */
    @PostMapping("/context-providers/run-all")
    @Operation(summary = "Run all context providers for a user")
    public ResponseEntity<Map<String, Object>> runAllContextProviders(@RequestBody TestContextRequest request) {
        log.info("Admin running all context providers for userId={}", request.userId());
        
        Set<String> providerKeys = contextProviderRouter.getAvailableProviders();
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        
        for (String providerKey : providerKeys) {
            Instant startTime = Instant.now();
            
            ContextProviderRequest ctxRequest = new ContextProviderRequest(
                    request.userId(),
                    request.config() != null ? request.config() : Map.of()
            );
            
            ContextProviderResponse ctxResponse = contextProviderRouter.dispatch(providerKey, ctxRequest);
            
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("providerKey", providerKey);
            result.put("success", ctxResponse.success());
            result.put("durationMs", durationMs);
            if (!ctxResponse.success()) {
                result.put("errorCode", ctxResponse.errorCode());
                result.put("errorMessage", ctxResponse.errorMessage());
                failureCount++;
            } else {
                successCount++;
                if (ctxResponse.data() != null) {
                    result.put("dataKeys", ctxResponse.data().keySet());
                }
            }
            
            results.add(result);
        }
        
        results.sort((a, b) -> ((String) a.get("providerKey")).compareTo((String) b.get("providerKey")));
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", request.userId());
        response.put("totalProviders", providerKeys.size());
        response.put("successCount", successCount);
        response.put("failureCount", failureCount);
        response.put("results", results);
        response.put("timestamp", Instant.now().toString());
        
        log.info("Admin all context providers complete: userId={}, success={}, failures={}",
                request.userId(), successCount, failureCount);
        
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────

    private String getToolDescription(String toolKey) {
        return switch (toolKey) {
            case "schedule_quality_time" -> "Schedule a new Quality Time event with a child";
            case "reschedule_quality_time" -> "Reschedule an existing Quality Time to a new time";
            case "cancel_quality_time" -> "Cancel a scheduled Quality Time event";
            case "complete_quality_time" -> "Mark a Quality Time as completed";
            case "show_available_slots" -> "Get available time slots from Google Calendar";
            case "set_weekly_goal" -> "Set a new weekly Quality Time goal";
            case "get_weekly_goal_status" -> "Get current weekly goal progress";
            case "show_weekly_summary" -> "Get weekly summary statistics";
            case "show_progress" -> "Get father's overall progress metrics";
            case "get_dashboard_link" -> "Get URL to the father's dashboard";
            case "get_activity_ideas" -> "Get activity ideas based on child age";
            case "greet" -> "Get greeting message data";
            case "show_help" -> "Get help menu with available commands";
            case "clarify" -> "Get clarification prompt for unclear input";
            case "connect_calendar" -> "Get Google Calendar OAuth URL";
            case "get_upcoming_quality_time" -> "Get next scheduled Quality Time";
            default -> "No description available";
        };
    }

    private List<Map<String, Object>> getToolParameters(String toolKey) {
        List<Map<String, Object>> params = new ArrayList<>();
        
        switch (toolKey) {
            case "schedule_quality_time" -> {
                params.add(param("child_id", "Long", true, "ID of the child"));
                params.add(param("start_time", "ISO8601", true, "Start time (e.g., 2024-01-15T10:00:00Z)"));
                params.add(param("duration_minutes", "Integer", false, "Duration in minutes (default: 30)"));
            }
            case "reschedule_quality_time" -> {
                params.add(param("quality_time_id", "UUID", true, "ID of the Quality Time to reschedule"));
                params.add(param("new_start_time", "ISO8601", true, "New start time"));
                params.add(param("new_duration_minutes", "Integer", false, "New duration in minutes"));
            }
            case "cancel_quality_time", "complete_quality_time" -> {
                params.add(param("quality_time_id", "UUID", true, "ID of the Quality Time"));
                if (toolKey.equals("complete_quality_time")) {
                    params.add(param("notes", "String", false, "Completion notes"));
                }
            }
            case "show_available_slots" -> {
                params.add(param("days_ahead", "Integer", false, "Days to look ahead (default: 7, max: 14)"));
            }
            case "set_weekly_goal" -> {
                params.add(param("target_hours", "Integer", true, "Target hours for the week"));
            }
            case "get_activity_ideas" -> {
                params.add(param("child_id", "Long", false, "Child ID to get age-appropriate ideas"));
                params.add(param("activity_type", "String", false, "Filter: indoor, outdoor, or both"));
            }
            case "clarify" -> {
                params.add(param("topic", "String", false, "Topic needing clarification"));
            }
            case "connect_calendar" -> {
                params.add(param("redirect_url", "String", false, "Custom redirect URL after OAuth"));
            }
        }
        
        return params;
    }

    private Map<String, Object> param(String name, String type, boolean required, String description) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", name);
        param.put("type", type);
        param.put("required", required);
        param.put("description", description);
        return param;
    }

    private String getProviderDescription(String providerKey) {
        return switch (providerKey) {
            case "family_context" -> "Father profile and children information";
            case "calendar_context" -> "Calendar events and available time slots";
            case "quality_time_context" -> "Quality time history, goals, and metrics";
            default -> "No description available";
        };
    }

    private List<Map<String, Object>> getProviderConfigOptions(String providerKey) {
        List<Map<String, Object>> options = new ArrayList<>();
        
        switch (providerKey) {
            case "calendar_context" -> {
                options.add(param("days_ahead", "Integer", false, "Days to look ahead (default: 7, max: 14)"));
            }
            case "quality_time_context" -> {
                options.add(param("history_days", "Integer", false, "Days of history (default: 30, max: 90)"));
            }
        }
        
        return options;
    }

    private Map<String, Object> buildTestParameters(String toolKey, TestToolRequest request) {
        Map<String, Object> params = new HashMap<>();
        
        // Add any parameters from the request
        if (request.parameters() != null) {
            params.putAll(request.parameters());
        }
        
        // For tools that need specific parameters to work, add defaults if not provided
        switch (toolKey) {
            case "set_weekly_goal" -> {
                if (!params.containsKey("target_hours")) {
                    params.put("target_hours", 3);
                }
            }
            // Most tools can run without parameters using defaults
        }
        
        return params;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request DTOs
    // ─────────────────────────────────────────────────────────────────────────

    public record TestToolRequest(
            Long userId,
            Map<String, Object> parameters
    ) {}

    public record TestContextRequest(
            Long userId,
            Map<String, Object> config
    ) {}
}
