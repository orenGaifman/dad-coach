package com.dadcoach.api.context;

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
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST API controller for context providers that ai-workflow-platform can call.
 * 
 * <p>This controller provides endpoints for loading context data that the AI workflow
 * platform uses to make decisions. Each context provider aggregates data from multiple
 * sources into a single response.</p>
 * 
 * <h2>Authentication</h2>
 * <p>All requests must include a valid API key in the X-API-Key header.
 * The API key is the same as configured for the Tool API (tool-api.api-key).</p>
 * 
 * <h2>Available Context Providers</h2>
 * <ul>
 *   <li><code>family_context</code> - Father profile and children information</li>
 *   <li><code>calendar_context</code> - Calendar events and available time slots</li>
 *   <li><code>quality_time_context</code> - Quality time history and dashboard metrics</li>
 * </ul>
 * 
 * @see ContextProviderRouter
 * @see ContextProviderRequest
 * @see ContextProviderResponse
 */
@RestController
@RequestMapping("/api/context")
@Tag(name = "Context Provider API", description = "Context loading endpoints for ai-workflow-platform integration")
@SecurityRequirement(name = "apiKey")
public class ContextProviderController {

    private static final Logger log = LoggerFactory.getLogger(ContextProviderController.class);

    private final ContextProviderRouter contextProviderRouter;

    public ContextProviderController(ContextProviderRouter contextProviderRouter) {
        this.contextProviderRouter = contextProviderRouter;
    }

    /**
     * Load context data from a specific provider.
     * 
     * <p>This is the main endpoint for context loading. The provider key is specified
     * in the path, and the request body contains the userId and optional config.</p>
     * 
     * <h3>Example: Family Context</h3>
     * <pre>
     * POST /api/context/family_context
     * X-API-Key: your-api-key
     * Content-Type: application/json
     * 
     * {
     *   "user_id": 1
     * }
     * </pre>
     * 
     * <h3>Example: Calendar Context with Config</h3>
     * <pre>
     * POST /api/context/calendar_context
     * X-API-Key: your-api-key
     * Content-Type: application/json
     * 
     * {
     *   "user_id": 1,
     *   "config": {
     *     "days_ahead": 14
     *   }
     * }
     * </pre>
     *
     * @param providerKey the context provider identifier (e.g., "family_context")
     * @param request the context request with userId and optional config
     * @return 200 OK with context data on success, or error response on failure
     */
    @PostMapping("/{providerKey}")
    @Operation(
            summary = "Load context from a provider",
            description = "Loads context data from the specified provider for the given user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Context loaded successfully",
                    content = @Content(schema = @Schema(implementation = ContextProviderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ContextProviderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ContextProviderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Provider not found",
                    content = @Content(schema = @Schema(implementation = ContextProviderResponse.class))
            )
    })
    public ResponseEntity<ContextProviderResponse> loadContext(
            @Parameter(description = "Context provider identifier", example = "family_context")
            @PathVariable("providerKey") String providerKey,
            @Valid @RequestBody ContextProviderRequest request) {

        log.info("Context provider request: providerKey={}, userId={}",
                providerKey, request.userId());

        ContextProviderResponse response = contextProviderRouter.dispatch(providerKey, request);

        log.info("Context provider response: providerKey={}, userId={}, success={}",
                providerKey, request.userId(), response.success());

        return ResponseEntity.ok(response);
    }

    /**
     * List all available context providers.
     * 
     * <p>Returns a list of all registered context provider keys that can be called
     * via the POST /{providerKey} endpoint.</p>
     *
     * @return 200 OK with list of available providers
     */
    @GetMapping
    @Operation(
            summary = "List available context providers",
            description = "Returns a list of all registered context provider keys"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of available providers"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid API key"
            )
    })
    public ResponseEntity<Map<String, Object>> listProviders() {
        Set<String> providers = contextProviderRouter.getAvailableProviders();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("providers", providers);
        response.put("count", providers.size());

        log.debug("Listed {} available context providers", providers.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for the Context Provider API.
     * 
     * <p>Returns a simple health status. This endpoint can be used for
     * monitoring and load balancer health checks.</p>
     *
     * @return 200 OK with health status
     */
    @GetMapping("/health")
    @Operation(
            summary = "Health check",
            description = "Returns health status of the Context Provider API"
    )
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "healthy");
        response.put("service", "context-provider-api");
        response.put("providers_count", contextProviderRouter.getAvailableProviders().size());

        return ResponseEntity.ok(response);
    }
}
