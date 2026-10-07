package com.dadcoach.api.tools;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.api.error.NotFoundException;
import com.dadcoach.api.error.ValidationException;
import com.dadcoach.calendar.CalendarIntegrationException;
import com.dadcoach.common.AppConstants;
import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.idempotency.IdempotencyService;
import com.dadcoach.integration.platform.FatherTimezones;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/tools/{toolKey}} (playbook §11, Tair's ToolController). The tool key is enforced by the security
 * chain before this runs. Here: actor from user_id only → rate limit → idempotency for side-effecting tools (the
 * X-Idempotency-Key header must equal the body key; reserve, execute once in one transaction, replay; same key with
 * other parameters → 409) → handler. A business rejection rolls the tool's changes back and becomes success:false
 * (HTTP 200), replayed like a success; an unexpected error releases the key (the platform's retry runs again) and
 * surfaces as 500.
 */
@RestController
public class ToolController {

    private static final Logger log = LoggerFactory.getLogger(ToolController.class);
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final Map<String, ToolHandler> handlers;
    private final ToolActorResolver actors;
    private final IdempotencyService idempotency;
    private final ToolEndpointRateLimiter rateLimiter;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final ObjectMapper canonicalJson;

    public ToolController(List<ToolHandler> handlerList, ToolActorResolver actors, IdempotencyService idempotency,
                          ToolEndpointRateLimiter rateLimiter, TransactionTemplate tx, ObjectMapper json) {
        Map<String, ToolHandler> byKey = new HashMap<>();
        handlerList.forEach(h -> {
            if (byKey.put(h.toolKey(), h) != null) {
                throw new IllegalStateException("two handlers for tool " + h.toolKey());
            }
        });
        this.handlers = Map.copyOf(byKey);
        this.actors = actors;
        this.idempotency = idempotency;
        this.rateLimiter = rateLimiter;
        this.tx = tx;
        this.json = json;
        this.canonicalJson = json.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public Set<String> toolKeys() {
        return handlers.keySet();
    }

    @PostMapping("/api/tools/{toolKey}")
    public ResponseEntity<String> execute(@PathVariable String toolKey,
                                          @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String headerKey,
                                          @RequestBody ToolRequest request) {
        Instant started = Instant.now();
        String result = "ERROR";
        ToolActor actor = null;
        try {
            ToolHandler handler = handlers.get(toolKey);
            if (handler == null) {
                result = "TOOL_NOT_FOUND";
                throw new NotFoundException("tool " + toolKey);
            }
            actor = actors.resolve(request.userId());
            if (!rateLimiter.tryAcquire(actor.ref())) {
                result = "RATE_LIMITED";
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "too many tool calls, try again shortly");
            }
            ToolParams params = new ToolParams(request.parameters(),
                    actor.father().map(FatherTimezones::of).orElse(AppConstants.DEFAULT_ZONE_ID));
            if (!handler.sideEffecting()) {
                ToolResponse response = invoke(handler, actor, params);
                result = response.success() ? "SUCCESS" : "BUSINESS_FAILURE";
                return ok(write(response));
            }
            String key = request.idempotencyKey();
            if (key == null || key.isBlank() || !key.equals(headerKey)) {
                result = "VALIDATION_ERROR";
                throw new ValidationException(IDEMPOTENCY_HEADER + " header must equal the body idempotency_key");
            }
            String scope = "TOOL:" + toolKey;
            String hash = IdempotencyService.hashOf(canonical(request));
            if (idempotency.reserve(scope, key, actor.ref(), hash) instanceof IdempotencyService.Outcome.Replay replay) {
                result = "REPLAYED";
                return ResponseEntity.status(replay.status()).contentType(MediaType.APPLICATION_JSON).body(replay.body());
            }
            try {
                ToolResponse response = invoke(handler, actor, params);
                String body = write(response);
                idempotency.complete(scope, key, true, 200, body);
                result = response.success() ? "SUCCESS" : "BUSINESS_FAILURE";
                return ok(body);
            } catch (RuntimeException unexpected) {
                idempotency.release(scope, key);
                result = "INTERNAL_ERROR";
                throw unexpected;
            }
        } finally {
            log.atInfo().setMessage("tool.execution.result")
                    .addKeyValue("toolKey", toolKey)
                    .addKeyValue("executionId", request.executionId())
                    .addKeyValue("idempotencyKeyHash", request.idempotencyKey() == null ? null
                            : IdempotencyService.hashOf(request.idempotencyKey()).substring(0, 12))
                    .addKeyValue("fatherId", actor == null ? null : actor.father().map(f -> f.getId()).orElse(null))
                    .addKeyValue("state", request.currentStateKey())
                    .addKeyValue("result", result)
                    .addKeyValue("durationMs", Duration.between(started, Instant.now()).toMillis())
                    .log();
        }
    }

    /** Runs the tool in one transaction; any business rejection rolls it back and becomes success:false. */
    private ToolResponse invoke(ToolHandler handler, ToolActor actor, ToolParams params) {
        try {
            return ToolResponse.ok(tx.execute(status -> handler.handle(actor, params)));
        } catch (ApiException business) {
            return ToolResponse.failure(business.code(), business.getMessage());
        } catch (CalendarIntegrationException calendar) {
            return ToolResponse.failure(calendar.getErrorType().code(), calendar.getMessage());
        } catch (BusinessRuleViolationException rule) {
            return ToolResponse.failure(rule.getRuleName(), rule.getDetail());
        } catch (ResourceNotFoundException notFound) {
            return ToolResponse.failure("NOT_FOUND", notFound.getEntityType() + " not found");
        } catch (IllegalStateException | InvalidStateTransitionException state) {
            return ToolResponse.failure("INVALID_STATE", state.getMessage());
        } catch (IllegalArgumentException invalid) {
            return ToolResponse.failure("INVALID_PARAMETERS", invalid.getMessage());
        }
    }

    private String canonical(ToolRequest request) {
        try {
            return canonicalJson.writeValueAsString(request.parameters() == null ? Map.of() : new TreeMap<>(request.parameters()));
        } catch (JsonProcessingException e) {
            throw new ValidationException("malformed parameters");
        }
    }

    private String write(ToolResponse response) {
        try {
            return json.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseEntity<String> ok(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
