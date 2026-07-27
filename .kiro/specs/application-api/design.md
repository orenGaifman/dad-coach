# Technical Design — Application API

## Architecture

### Overview

The Application API exposes the Dad Coach platform to external consumers through versioned REST endpoints per SPEC-007. It provides three surfaces: Father API (self-service), Admin API (operational management), and Service API (internal). Built using Spring Boot controllers with Jakarta Validation, Spring Security for authentication/authorization, and Problem Details (RFC 9457) for error responses.

### Architecture Decisions

**AD-1: Spring MVC Controllers per Resource** — Each domain resource (Father, Child, Goal, Mission, Conversation, Memory) has its own controller. Admin-specific operations have separate `Admin*Controller` classes.

**AD-2: JWT-Based Authentication** — Authentication uses JWT tokens. Father tokens contain the father_id; Admin tokens contain role claims. Token validation is handled by Spring Security filters. The identity provider is external (configurable).

**AD-3: Cursor-Based Pagination** — All list endpoints use opaque cursor tokens (base64-encoded composite keys) for stable, performant pagination per SPEC-007 Req 10.

**AD-4: MapStruct for DTO Mapping** — Domain entities are never exposed directly. MapStruct generates compile-time mappers between entities and API response DTOs, filtering sensitive fields per actor type.

**AD-5: API Versioning via Path Prefix** — All endpoints are prefixed with `/api/v1/`. Breaking changes require `/api/v2/` with 6-month parallel support.

**AD-6: Request Audit via AOP** — A Spring AOP aspect intercepts all mutating API calls and writes audit entries. Admin read operations on father data are also audited.

### Package Structure

```
com.dadcoach.api/
├── config/
│   ├── SecurityConfig.java            # Spring Security: JWT + role enforcement
│   ├── ApiVersionConfig.java          # Version prefix registration
│   └── CorsConfig.java               # CORS allowed origins
├── auth/
│   ├── JwtAuthFilter.java            # Token validation filter
│   ├── ActorContext.java             # ThreadLocal with current actor (FATHER/ADMIN/SERVICE)
│   └── RolePermission.java           # Role → permission mapping
├── father/
│   ├── FatherController.java         # GET /api/v1/fathers/me, PUT, DELETE
│   ├── AdminFatherController.java    # GET /api/v1/admin/fathers, search, override
│   ├── FatherResponseDto.java        # Public fields only
│   └── FatherUpdateRequest.java      # Validated input
├── child/
│   ├── ChildController.java          # CRUD under /api/v1/fathers/me/children
│   ├── ChildCreateRequest.java
│   └── ChildResponseDto.java
├── goal/
│   ├── GoalController.java           # CRUD under /api/v1/fathers/me/goals
│   └── GoalCreateRequest.java
├── mission/
│   └── MissionController.java        # Read-only: list, get, active
├── conversation/
│   └── ConversationController.java   # Read-only: list, get with messages
├── memory/
│   ├── MemoryController.java         # List, get, delete
│   └── AdminMemoryController.java    # Includes archived, audit history
├── health/
│   └── HealthController.java         # Liveness, readiness, detailed (Service API)
├── audit/
│   ├── ApiAuditAspect.java           # AOP aspect for audit logging
│   ├── ApiAuditEntry.java            # JPA entity
│   └── ApiAuditRepository.java
├── error/
│   ├── GlobalExceptionHandler.java   # Problem Details formatting
│   ├── ProblemDetail.java            # RFC 9457 response structure
│   └── ErrorCode.java                # All error codes enum
├── pagination/
│   ├── CursorPageRequest.java        # Cursor parsing
│   └── CursorPageResponse.java       # Response with next_cursor + has_more
├── idempotency/
│   ├── IdempotencyFilter.java        # Checks Idempotency-Key header
│   └── IdempotencyStore.java         # 24h TTL key → response cache
└── ratelimit/
    └── RateLimitFilter.java          # Per-actor rate limit enforcement
```

## Components and Interfaces

### Security Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/service/**").hasRole("SERVICE")
                .requestMatchers("/api/v1/fathers/me/**").hasRole("FATHER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### Resource Ownership Enforcement

```java
// Every Father API endpoint verifies ownership
@GetMapping("/api/v1/fathers/me/children/{childId}")
public ChildResponseDto getChild(@PathVariable UUID childId, @AuthActor ActorContext actor) {
    Child child = childService.findById(childId)
        .orElseThrow(() -> new ResourceNotFoundException("Child", childId));
    if (!child.getFatherId().equals(actor.getFatherId())) {
        throw new ResourceNotFoundException("Child", childId); // 404, not 403
    }
    return childMapper.toDto(child);
}
```

## Data Models

### Audit and Idempotency Tables

```sql
CREATE TABLE api_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id      UUID NOT NULL,
    actor_type      VARCHAR(20) NOT NULL,
    actor_id        UUID NOT NULL,
    operation       VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(30) NOT NULL,
    resource_id     UUID,
    result          VARCHAR(20) NOT NULL,  -- SUCCESS, FAILURE
    error_code      VARCHAR(50),
    changes         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_audit_actor ON api_audit_log(actor_id, created_at DESC);
CREATE INDEX idx_api_audit_resource ON api_audit_log(resource_type, resource_id, created_at DESC);

CREATE TABLE idempotency_keys (
    key             VARCHAR(255) PRIMARY KEY,
    actor_id        UUID NOT NULL,
    response_status INTEGER NOT NULL,
    response_body   JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);
```

## Error Handling

All errors return Problem Details (RFC 9457) per SPEC-001 Req 7:

```json
{
  "type": "https://dadcoach.app/errors/LIMIT_EXCEEDED",
  "title": "Business Rule Violation",
  "status": 422,
  "detail": "Maximum of 8 children per father. Current count: 8.",
  "instance": "/api/v1/fathers/me/children",
  "error_code": "LIMIT_EXCEEDED",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "retryable": false
}
```

| HTTP Status | Error Codes | Scenario |
|-------------|------------|----------|
| 400 | VALIDATION_FAILED, FIELD_REQUIRED, FIELD_INVALID | Structural validation failure |
| 401 | UNAUTHORIZED, TOKEN_EXPIRED | Authentication failure |
| 403 | FORBIDDEN | Insufficient permissions |
| 404 | RESOURCE_NOT_FOUND | Resource missing or ownership mismatch |
| 409 | STATE_TRANSITION_INVALID, DUPLICATE_RESOURCE | Conflict |
| 422 | LIMIT_EXCEEDED, OPERATION_NOT_ALLOWED | Business rule violation |
| 429 | RATE_LIMIT_EXCEEDED | Rate limit hit |
| 500 | INTERNAL_ERROR | Unexpected server error |

## Correctness Properties

- Father actors NEVER see 403 for other fathers' resources — always 404 (prevents enumeration)
- All mutating operations are audit-logged BEFORE response (synchronous with the operation)
- Idempotency keys are checked BEFORE any business logic executes
- Rate limits are enforced per actor_id, not per IP — actor identity required
- API responses NEVER contain: embeddings, AI prompts, raw confidence scores (for Father API), phone numbers (masked except for owning father)
- API version v1 contract is immutable once published — additive changes only until v2

## Requirement Traceability

| Requirement | Design Element |
|-------------|---------------|
| Req 1: Scope/Boundaries | Three controller packages (father/, admin/, service/) with route guards |
| Req 2: Resource Model | Dedicated controller + DTO per resource; MapStruct for mapping |
| Req 3-5: Operations | Controller methods per resource operation |
| Req 6: Auth/AuthZ | `SecurityConfig` + `JwtAuthFilter` + `ActorContext` + ownership checks |
| Req 7: Validation | Jakarta Bean Validation annotations + custom validators for business rules |
| Req 8: Idempotency | `IdempotencyFilter` + `idempotency_keys` table (24h TTL) |
| Req 9: Error Model | `GlobalExceptionHandler` + `ProblemDetail` (RFC 9457) |
| Req 10: Pagination | `CursorPageRequest` / `CursorPageResponse` on all list endpoints |
| Req 11: Versioning | `/api/v1/` prefix; major version = new prefix |
| Req 12: Performance | Pagination enforced; payload limits in filter; rate limits per actor |
| Req 13: Security | JWT + CORS + input sanitization + field sensitivity mapping |
| Req 14: Audit | `ApiAuditAspect` (AOP) + `api_audit_log` table |
| Req 15: Health | Actuator endpoints (unauth) + custom `/api/v1/service/health` (auth) |
| Req 16: Cross-Spec | Controllers delegate to domain services; no direct DB writes in controllers |
