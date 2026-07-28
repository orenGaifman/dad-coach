package com.dadcoach.api.audit;

import com.dadcoach.api.auth.ActorContext;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring AOP aspect that intercepts API operations for audit logging.
 * <p>
 * Intercepted operations:
 * <ul>
 *   <li>All POST requests (resource creation)</li>
 *   <li>All PUT requests (resource updates)</li>
 *   <li>All DELETE requests (resource removal)</li>
 *   <li>Admin GET operations on father data (/api/v1/admin/fathers/**)</li>
 * </ul>
 * <p>
 * Audit entries are written synchronously BEFORE the response is returned to
 * the client, ensuring the audit trail is consistent with the operation result.
 * <p>
 * The aspect uses {@link ActorContext#current()} to identify the authenticated
 * actor and {@link RequestContextHolder} for HTTP request details.
 */
@Aspect
@Component
public class ApiAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiAuditAspect.class);

    /**
     * Pattern to detect admin father data endpoints: /api/v1/admin/fathers/**
     */
    private static final Pattern ADMIN_FATHER_PATTERN =
            Pattern.compile("/api/v1/admin/fathers(/.*)?");

    /**
     * Pattern to extract resource ID from URL paths.
     * Matches UUID pattern in path segments.
     */
    private static final Pattern RESOURCE_ID_PATTERN =
            Pattern.compile("/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private final ApiAuditRepository auditRepository;

    public ApiAuditAspect(ApiAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Pointcut matching all methods in REST controllers annotated with
     * Spring's @PostMapping, @PutMapping, or @DeleteMapping.
     */
    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    public void postMappings() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PutMapping)")
    public void putMappings() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void deleteMappings() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    public void getMappings() {
    }

    /**
     * Pointcut matching all controller classes in the api package.
     */
    @Pointcut("within(com.dadcoach.api..*)")
    public void apiPackage() {
    }

    /**
     * Intercepts all mutating operations (POST, PUT, DELETE) in the API package.
     * Audit entry is written synchronously after the operation completes but
     * BEFORE the response is returned to the client.
     */
    @Around("apiPackage() && (postMappings() || putMappings() || deleteMappings())")
    public Object auditMutatingOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint);
    }

    /**
     * Intercepts admin GET operations on father data.
     * Only applies to requests matching /api/v1/admin/fathers/** pattern.
     */
    @Around("apiPackage() && getMappings()")
    public Object auditAdminGetOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String requestUri = request.getRequestURI();
        if (ADMIN_FATHER_PATTERN.matcher(requestUri).matches()) {
            return executeWithAudit(joinPoint);
        }

        // Non-admin-father GET requests are not audited
        return joinPoint.proceed();
    }

    /**
     * Executes the target method and records an audit entry with the result.
     * The audit entry is persisted BEFORE the response is returned (synchronous).
     */
    private Object executeWithAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.warn("Cannot audit: no HttpServletRequest in context");
            return joinPoint.proceed();
        }

        ActorContext actor = ActorContext.current();
        UUID requestId = extractRequestId(request);
        String httpMethod = request.getMethod();
        String requestUri = request.getRequestURI();
        String operation = truncate(httpMethod + " " + requestUri, 50);
        String resourceType = resolveResourceType(requestUri);
        UUID resourceId = extractResourceId(requestUri);

        Object result = null;
        Throwable failure = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                persistAuditEntry(requestId, actor, operation, resourceType,
                        resourceId, failure);
            } catch (Exception auditEx) {
                // Audit failure must not break the API response
                log.error("Failed to persist audit entry for request {}: {}",
                        requestId, auditEx.getMessage(), auditEx);
            }
        }
    }

    /**
     * Persists the audit entry to the database.
     */
    private void persistAuditEntry(UUID requestId, ActorContext actor, String operation,
                                   String resourceType, UUID resourceId, Throwable failure) {
        ApiAuditEntry.Builder builder = ApiAuditEntry.builder()
                .requestId(requestId)
                .operation(operation)
                .resourceType(resourceType)
                .resourceId(resourceId);

        if (actor != null) {
            builder.actorType(actor.getActorType().name())
                    .actorId(actor.getActorId());
        } else {
            // Anonymous or system call — use placeholder
            builder.actorType("UNKNOWN")
                    .actorId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        }

        if (failure == null) {
            builder.result("SUCCESS");
        } else {
            builder.result("FAILURE")
                    .errorCode(failure.getClass().getSimpleName());
        }

        auditRepository.save(builder.build());
    }

    /**
     * Extracts or generates a request ID from the servlet request.
     * Uses the X-Request-ID header if present, otherwise generates a new UUID.
     */
    private UUID extractRequestId(HttpServletRequest request) {
        String requestIdHeader = request.getHeader("X-Request-ID");
        if (requestIdHeader != null && !requestIdHeader.isBlank()) {
            try {
                return UUID.fromString(requestIdHeader.trim());
            } catch (IllegalArgumentException e) {
                // Header value is not a valid UUID, generate a new one
            }
        }
        return UUID.randomUUID();
    }

    /**
     * Resolves the resource type from the request URI path.
     * Maps known path segments to resource types.
     */
    private String resolveResourceType(String uri) {
        if (uri == null) {
            return "UNKNOWN";
        }
        if (uri.contains("/children")) {
            return "Child";
        }
        if (uri.contains("/goals")) {
            return "Goal";
        }
        if (uri.contains("/missions")) {
            return "Mission";
        }
        if (uri.contains("/conversations")) {
            return "Conversation";
        }
        if (uri.contains("/memories")) {
            return "Memory";
        }
        if (uri.contains("/fathers")) {
            return "Father";
        }
        if (uri.contains("/health")) {
            return "Health";
        }
        return "UNKNOWN";
    }

    /**
     * Extracts a resource UUID from the URL path.
     * Returns the last UUID found in the path, or null if none found.
     */
    private UUID extractResourceId(String uri) {
        if (uri == null) {
            return null;
        }
        Matcher matcher = RESOURCE_ID_PATTERN.matcher(uri);
        UUID lastId = null;
        while (matcher.find()) {
            try {
                lastId = UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException e) {
                // Skip malformed UUIDs
            }
        }
        return lastId;
    }

    /**
     * Gets the current HttpServletRequest from the RequestContextHolder.
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * Truncates a string to the given max length.
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
