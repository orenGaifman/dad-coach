# Implementation Plan

## Overview
Complete the production foundation for the Dad Coach backend — remaining tasks: actuator health configuration, security hardening, structured logging improvements, testing infrastructure, README documentation, and final verification.

## Task Dependency Graph
```json
{
  "waves": [
    { "tasks": [1, 2, 3] },
    { "tasks": [4] },
    { "tasks": [5] },
    { "tasks": [6] },
    { "tasks": [7] }
  ]
}
```

## Tasks

- [x] 1. Actuator Health Configuration and Security
  - [x] 1.1. Configure explicit health groups in application.yml: liveness includes livenessState, readiness includes readinessState and db
  - [x] 1.2. Move `show-details: always` from base application.yml to application-local.yml only; set `show-details: never` in application-prod.yml
  - [x] 1.3. Verify health endpoints respond correctly via compilation check

- [x] 2. Security Hardening
  - [x] 2.1. Remove default `dad-coach-secret` verify-token from base application.yml; require WHATSAPP_VERIFY_TOKEN env var for dev/prod (no fallback default)
  - [x] 2.2. Add `@Profile("!prod")` annotation to WhatsAppController to prevent manual send endpoint from being publicly accessible in production
  - [x] 2.3. Update .env.example to mark WHATSAPP_VERIFY_TOKEN as [REQUIRED] for dev/prod and document the change
  - [x] 2.4. Add Jakarta validation annotations to WhatsAppProperties to fail-fast if required properties are missing in dev/prod

- [x] 3. Structured Logging Improvements
  - [x] 3.1. Refactor RequestLoggingFilter to use MDC (or StructuredArguments from logstash-logback-encoder) so JSON output produces proper key-value fields, not embedded strings
  - [x] 3.2. Add filtering in RequestLoggingFilter to prevent logging sensitive webhook payload content — log only safe metadata (method, path, status, durationMs, content-length)
  - [x] 3.3. Verify compilation passes after logging refactor

- [x] 4. Testing Infrastructure
  - [x] 4.1. Create IntegrationTestBase abstract class with Testcontainers PostgreSQL 17 using @Testcontainers and @DynamicPropertySource
  - [x] 4.2. Create ApplicationContextIntegrationTest extending base: verifies context loads, Flyway runs, app starts
  - [x] 4.3. Create HealthEndpointIntegrationTest: verifies /actuator/health, /actuator/health/liveness, /actuator/health/readiness return UP
  - [x] 4.4. Create OpenApiIntegrationTest: verifies /v3/api-docs returns valid JSON spec and Swagger is enabled for local profile
  - [x] 4.5. Create SwaggerProfileTest: verifies Swagger UI is enabled with local profile and disabled with prod profile
  - [x] 4.6. Create GlobalExceptionHandlerTest (unit): verifies validation→400, notFound→404, general→500 all return Problem Details with type, title, status, detail, instance fields, and no internal exception details exposed
  - [x] 4.7. Create ReadinessDownTest: verifies readiness probe returns DOWN when DB is unavailable
  - [x] 4.8. Remove existing placeholder DadCoachApplicationTests.java
  - [x] 4.9. Run `./mvnw clean verify` and ensure all tests pass

- [x] 5. README and Documentation
  - [x] 5.1. Rewrite root README.md with: architecture overview, system requirements (Java 21, Docker), project structure diagram
  - [x] 5.2. Add separate sections for local Maven execution (`./mvnw spring-boot:run`) and Docker execution (`docker compose up --build`)
  - [x] 5.3. Add environment variables table describing every variable from .env.example with purpose, required/optional, and default value
  - [x] 5.4. Add testing section with commands for unit tests and integration tests

- [x] 6. Code Quality Verification
  - [x] 6.1. Verify all Java files use constructor injection (no @Autowired)
  - [x] 6.2. Verify all DTOs that can be records ARE records (SendTextRequest is already a record)
  - [x] 6.3. Verify Lombok is used ONLY for entity boilerplate
  - [x] 6.4. Remove any dead code, unused imports, empty directories

- [x] 7. Final Verification
  - [x] 7.1. Run `./mvnw clean verify` and report results
  - [x] 7.2. Run `docker compose up --build` and verify both services start with health checks passing
  - [x] 7.3. Report final status of all production foundation requirements

## Notes
- Tasks 1, 2, 3 can run in parallel (independent config/security/logging changes)
- Task 4 depends on 1-3 being complete (tests validate those changes)
- Task 5 depends on task 4 (README documents final state)
- Task 6 depends on task 5 (quality check on final code)
- Task 7 depends on task 6 (final verification of everything)
