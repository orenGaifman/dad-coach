# Implementation Plan

## Overview
Production foundation setup for the Dad Coach backend — project structure, dependencies, configuration, Docker, error handling, logging, OpenAPI, health endpoints, testing infrastructure, and documentation.

## Task Dependency Graph
```json
{
  "waves": [
    { "tasks": [1] },
    { "tasks": [2] },
    { "tasks": [3] },
    { "tasks": [4] },
    { "tasks": [5] },
    { "tasks": [6] },
    { "tasks": [7] },
    { "tasks": [8] },
    { "tasks": [9] },
    { "tasks": [10] }
  ]
}
```

## Tasks

- [x] 1. Maven Dependencies and Build Configuration
  - [x] 1.1. Update `pom.xml` to include: MapStruct (with annotation processor), SpringDoc OpenAPI, Testcontainers PostgreSQL, logstash-logback-encoder, spring-boot-starter-actuator (if not present), spring-boot-starter-validation
  - [x] 1.2. Configure MapStruct annotation processor in `maven-compiler-plugin`
  - [x] 1.3. Verify `mvn clean compile` succeeds with all new dependencies resolved
  - [x] 1.4. Remove any unused or placeholder dependencies

- [x] 2. Package-by-Feature Structure
  - [x] 2.1. Create top-level packages: `common`, `config`, `father`, `child`, `conversation`, `memory`, `whatsapp`, `ai`, `scheduler`, `health`, `mission`, `goal`, `notification`
  - [x] 2.2. Move existing `ApiExceptionHandler` to `common/` (will be replaced in Task 5)
  - [x] 2.3. Move existing WhatsApp classes to correct packages (`webhook/`, `whatsapp/`, `config/`)
  - [x] 2.4. Remove all empty packages and placeholder classes with no implementation
  - [x] 2.5. Ensure every package has at least one class or a `package-info.java`

- [x] 3. Environment Configuration and Profiles
  - [x] 3.1. Create `application-local.yml` with default development values (plain-text logging, Swagger enabled, default DB URL)
  - [x] 3.2. Create `application-dev.yml` with JSON logging, env-var DB resolution, Swagger enabled
  - [x] 3.3. Create `application-prod.yml` with JSON logging, env-var DB resolution, Swagger disabled
  - [x] 3.4. Update base `application.yml` to use `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` with local defaults
  - [x] 3.5. Set `spring.profiles.active=local` as the default profile
  - [x] 3.6. Create/update `.env.example` documenting ALL required and optional environment variables with descriptions
  - [x] 3.7. Verify no secrets or credentials exist in committed source files

- [x] 4. Docker and Docker Compose
  - [x] 4.1. Update `Dockerfile` to multi-stage build: Stage 1 builds with Maven wrapper (`./mvnw`), Stage 2 runs with `eclipse-temurin:21-jre-alpine`
  - [x] 4.2. Update `docker-compose.yml` with PostgreSQL 17 service: named volume for data persistence, health check (`pg_isready`)
  - [x] 4.3. Add backend service to `docker-compose.yml`: depends_on postgres (healthy), health check via `/actuator/health`
  - [x] 4.4. Verify `docker compose up` starts both services and health checks pass
  - [x] 4.5. Ensure Maven wrapper (`mvnw`, `.mvn/`) is included in the build context

- [x] 5. Global Exception Handler (RFC 9457)
  - [x] 5.1. Replace existing `ApiExceptionHandler` with `GlobalExceptionHandler` extending `ResponseEntityExceptionHandler`
  - [x] 5.2. Implement `handleMethodArgumentNotValid` → 400 with field-level details in Problem Details format
  - [x] 5.3. Implement `handleEntityNotFoundException` → 404 with `Content-Type: application/problem+json`
  - [x] 5.4. Implement catch-all `handleException` → 500 without exposing stack traces
  - [x] 5.5. Ensure all responses include: `type`, `title`, `status`, `detail`, `instance`
  - [x] 5.6. Log 4xx at WARN, 5xx at ERROR with full stack trace

- [x] 6. Structured Logging
  - [x] 6.1. Add `logstash-logback-encoder` dependency (already in Task 1)
  - [x] 6.2. Create `logback-spring.xml` with profile-conditional configuration: JSON for dev/prod, plain-text for local
  - [x] 6.3. Create `RequestLoggingFilter` (OncePerRequestFilter) logging: method, path, status, durationMs for every request
  - [x] 6.4. Ensure application startup logs: active profile, server port, database connection status
  - [x] 6.5. Remove all `System.out.println` and `System.err.println` from the codebase
  - [x] 6.6. Verify log output is valid JSON in dev/prod profiles

- [ ] 7. OpenAPI Configuration
  - [x] 7.1. Create `config/OpenApiConfig.java` with `@Profile("!prod")` annotation
  - [x] 7.2. Configure OpenAPI metadata: title "Dad Coach API", version "0.1.0", description
  - [x] 7.3. Verify Swagger UI accessible at `/swagger-ui.html` with local profile
  - [x] 7.4. Verify `/v3/api-docs` returns valid JSON specification
  - [x] 7.5. Verify Swagger UI is NOT accessible with prod profile active

- [ ] 8. Health, Readiness, and Liveness Endpoints
  - [~] 8.1. Configure Actuator in `application.yml`: expose health, liveness, readiness endpoints
  - [~] 8.2. Enable probes: `management.endpoint.health.probes.enabled=true`
  - [~] 8.3. Configure health groups: liveness (minimal), readiness (includes db indicator)
  - [~] 8.4. Verify `/actuator/health` returns UP
  - [~] 8.5. Verify `/actuator/health/liveness` returns UP
  - [~] 8.6. Verify `/actuator/health/readiness` returns UP (and DOWN when DB is unavailable)

- [ ] 9. Testing Infrastructure
  - [~] 9.1. Create `IntegrationTestBase` abstract class with Testcontainers PostgreSQL 17 (`@Testcontainers`, `@DynamicPropertySource`)
  - [~] 9.2. Create `ApplicationContextIntegrationTest` extending base: verifies context loads, Flyway runs, app starts
  - [~] 9.3. Create `HealthEndpointIntegrationTest`: verifies `/actuator/health`, liveness, readiness return correct status
  - [~] 9.4. Create `OpenApiIntegrationTest`: verifies `/v3/api-docs` returns valid specification
  - [~] 9.5. Create `GlobalExceptionHandlerTest` (unit): verifies validation→400, notFound→404, general→500 in Problem Details format
  - [~] 9.6. Remove the existing placeholder test (`DadCoachApplicationTests.java` if it only asserts `contextLoads`)
  - [~] 9.7. Verify `mvn clean verify` passes with all new tests

- [ ] 10. README and Code Quality
  - [~] 10.1. Update root `README.md` with: architecture overview, system requirements (Java 21, Docker), Docker setup commands, how to run locally (`./mvnw spring-boot:run`), how to run tests (`mvn verify`), environment variables table, project structure diagram
  - [~] 10.2. Include exact commands for each environment (local, Docker, production)
  - [~] 10.3. Describe every variable from `.env.example` with purpose and default
  - [~] 10.4. Verify all Java files use constructor injection (no `@Autowired`)
  - [~] 10.5. Verify all DTOs that can be records ARE records (not Lombok classes)
  - [~] 10.6. Verify Lombok is used ONLY for entity boilerplate
  - [~] 10.7. Remove any dead code, unused imports, empty directories
  - [~] 10.8. Final verification: `mvn clean verify` + `docker compose up` both succeed

## Notes
- Tasks are sequential (1→2→3→...→10) because each builds on the previous
- Task 1 must complete first as all subsequent tasks depend on the dependencies it adds
- Docker verification (Task 4.4) and final verification (Task 10.8) require Docker to be running
- Integration tests (Task 9) require Testcontainers-compatible Docker environment
