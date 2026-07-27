# Requirements Document

## Introduction

This specification defines the production-grade foundation for the Dad Coach backend application. The scope is strictly infrastructure, architecture, and quality — no new business logic. Every future feature (conversation memory, AI coaching, scheduling, child registration) will build upon this foundation. The goal is to establish a professional, maintainable, testable, and deployable Spring Boot monolith using package-by-feature architecture.

## Glossary

- **Backend**: The Spring Boot 4.1.0 / Java 21 application located in `dad-coach/backend/`
- **Package_By_Feature**: An architecture style where each domain module (feature) owns its own controller, service, repository, entity, DTO, mapper, and validation classes under a single top-level package
- **Feature_Package**: A top-level package under `com.dadcoach` representing a bounded domain (e.g., `whatsapp`, `father`, `mission`, `goal`, `memory`, `health`, `common`, `config`)
- **Docker_Environment**: The Docker Compose setup that starts the full application stack (backend + PostgreSQL) with a single command
- **Flyway**: A database migration tool that manages schema versioning via SQL scripts
- **Problem_Details**: The RFC 9457 standard format for HTTP API error responses
- **Structured_Logging**: JSON-formatted log output with consistent fields (timestamp, level, logger, message, context)
- **Health_Endpoint**: A Spring Boot Actuator endpoint that reports application readiness and liveness status
- **Integration_Test**: A test that boots the full Spring context with a real PostgreSQL instance (via Testcontainers)
- **Environment_Profile**: A Spring configuration profile (local, dev, prod) that activates environment-specific settings

## Requirements

### Requirement 1: Package-by-Feature Project Structure

**User Story:** As a developer, I want the backend organized by feature packages, so that each domain module is self-contained and easy to navigate.

#### Acceptance Criteria

1. THE Backend SHALL organize source code under top-level feature packages: `common`, `config`, `father`, `child`, `conversation`, `memory`, `whatsapp`, `ai`, `scheduler`, `health`, `mission`, `goal`, `notification`
2. WHEN a Feature_Package contains domain logic, THE Feature_Package SHALL own its own controller, service, repository, entity, DTO, mapper, and validation classes within that package
3. THE Backend SHALL remove empty packages and placeholder classes that have no implementation
4. THE Backend SHALL use constructor injection exclusively for all Spring-managed bean dependencies

### Requirement 2: Technology Stack and Dependencies

**User Story:** As a developer, I want the project to include all necessary production dependencies from day one, so that future features can use them without additional setup.

#### Acceptance Criteria

1. THE Backend SHALL use Java 21 with Spring Boot latest stable as the runtime and framework
2. THE Backend SHALL include MapStruct as a compile-time DTO mapping library with proper Maven annotation processor configuration
3. THE Backend SHALL include the SpringDoc OpenAPI library for automatic API documentation generation
4. THE Backend SHALL include Testcontainers (PostgreSQL module) as a test-scoped dependency for integration testing
5. THE Backend SHALL use Maven as the build system with a single `pom.xml` defining all dependencies

### Requirement 3: Environment-Driven Configuration

**User Story:** As a developer, I want configuration separated by environment, so that the same artifact runs correctly in local, dev, and prod without code changes.

#### Acceptance Criteria

1. THE Backend SHALL support three Environment_Profiles: `local`, `dev`, and `prod`
2. THE Backend SHALL resolve all sensitive configuration values (database credentials, API tokens) exclusively from environment variables
3. THE Backend SHALL contain zero secrets or credentials in committed source files
4. THE Backend SHALL provide an `.env.example` file that documents every required and optional environment variable with descriptions
5. WHEN the `local` Environment_Profile is active, THE Backend SHALL use default development values for non-sensitive configuration

### Requirement 4: Docker and Docker Compose

**User Story:** As a developer, I want to start the entire application stack with a single command, so that local development setup is fast and reproducible.

#### Acceptance Criteria

1. THE Docker_Environment SHALL start both PostgreSQL and the Backend application with the command `docker compose up`
2. THE Docker_Environment SHALL persist PostgreSQL data using a named Docker volume
3. THE Docker_Environment SHALL define health checks for both the PostgreSQL container and the Backend container
4. WHEN the PostgreSQL container health check passes, THE Docker_Environment SHALL start the Backend container
5. THE Backend SHALL provide a multi-stage Dockerfile that produces a minimal JRE-based runtime image
6. THE Docker_Environment SHALL include a Maven wrapper (`mvnw`) in the build context so the image builds without pre-installed Maven

### Requirement 5: Database and Flyway Migrations

**User Story:** As a developer, I want the database schema managed by versioned migrations, so that schema changes are traceable and reproducible across environments.

#### Acceptance Criteria

1. THE Backend SHALL use Flyway for all database schema management
2. THE Backend SHALL configure Flyway to run migrations automatically on application startup
3. THE Backend SHALL validate that JPA entity mappings match the current database schema (ddl-auto=validate)
4. WHEN a migration script contains invalid SQL, THEN THE Backend SHALL fail to start and log the migration error

### Requirement 6: OpenAPI Documentation

**User Story:** As a developer, I want auto-generated API documentation, so that endpoints are discoverable and testable without reading source code.

#### Acceptance Criteria

1. THE Backend SHALL expose a Swagger UI at the path `/swagger-ui.html`
2. THE Backend SHALL expose the OpenAPI JSON specification at the path `/v3/api-docs`
3. THE Backend SHALL include request/response schemas derived from DTO validation annotations in the OpenAPI output
4. WHEN the `prod` Environment_Profile is active, THE Backend SHALL disable Swagger UI access

### Requirement 7: Problem Details Error Responses (RFC 9457)

**User Story:** As a developer, I want consistent, standards-compliant error responses, so that API consumers can reliably parse and handle errors.

#### Acceptance Criteria

1. WHEN a validation error occurs, THE Backend SHALL return an HTTP response with `Content-Type: application/problem+json` containing fields: `type`, `title`, `status`, `detail`, and `instance`
2. WHEN an unhandled exception occurs, THE Backend SHALL return an HTTP response with `Content-Type: application/problem+json` and status 500 without exposing internal stack traces
3. WHEN a resource is not found, THE Backend SHALL return an HTTP response with `Content-Type: application/problem+json` and status 404
4. THE Backend SHALL use a global exception handler to apply Problem_Details formatting to all error responses

### Requirement 8: Structured Logging

**User Story:** As a developer, I want structured, queryable logs, so that production issues can be diagnosed efficiently.

#### Acceptance Criteria

1. THE Backend SHALL produce all log output in JSON format using SLF4J with Logback
2. THE Backend SHALL log application startup information including active profile, server port, and database connection status
3. THE Backend SHALL log every incoming HTTP request with method, path, response status, and duration
4. WHEN an unexpected exception occurs, THE Backend SHALL log the exception with full stack trace at ERROR level
5. THE Backend SHALL contain zero uses of `System.out.println` or `System.err.println`

### Requirement 9: Health, Readiness, and Liveness Endpoints

**User Story:** As an operator, I want separate health probes, so that orchestrators (Docker, Kubernetes) can correctly manage the application lifecycle.

#### Acceptance Criteria

1. THE Backend SHALL expose a liveness probe at `/actuator/health/liveness`
2. THE Backend SHALL expose a readiness probe at `/actuator/health/readiness`
3. THE Backend SHALL expose a general health endpoint at `/actuator/health`
4. WHEN the database connection is unavailable, THE readiness probe SHALL report status DOWN

### Requirement 10: Testing Foundation

**User Story:** As a developer, I want a working test infrastructure, so that future features can be tested with real database interactions from the start.

#### Acceptance Criteria

1. THE Backend SHALL use JUnit 5 as the test framework
2. THE Backend SHALL use Mockito for unit test mocking
3. THE Backend SHALL use Testcontainers with a PostgreSQL container for integration tests
4. THE Backend SHALL include one passing Integration_Test that boots the full Spring context, runs Flyway migrations, and verifies the application starts successfully
5. THE Backend SHALL remove placeholder tests that assert nothing

### Requirement 11: README Documentation

**User Story:** As a new developer, I want comprehensive project documentation, so that I can understand, build, and run the project without external help.

#### Acceptance Criteria

1. THE Backend SHALL provide a root README.md documenting: architecture overview, system requirements, Docker setup, how to run locally, how to run tests, environment variables, and project structure
2. THE README SHALL include the exact commands needed to start the application in each environment
3. THE README SHALL describe every environment variable from `.env.example` with its purpose and default value

### Requirement 12: Code Quality and Conventions

**User Story:** As a developer, I want clean, idiomatic code conventions enforced from day one, so that the codebase stays maintainable as features are added.

#### Acceptance Criteria

1. THE Backend SHALL use Java record types for immutable DTOs where appropriate
2. THE Backend SHALL use Lombok exclusively for entity boilerplate (not for DTOs that can be records)
3. THE Backend SHALL use constructor injection for all Spring beans without the `@Autowired` annotation
4. THE Backend SHALL contain no dead code, unused imports, or empty package directories
5. THE Backend SHALL keep each Feature_Package cohesive with only related classes

### Requirement 13: Build and Deployment Validation

**User Story:** As a developer, I want confidence that the production foundation is fully functional, so that future features build on a verified base.

#### Acceptance Criteria

1. WHEN `mvn clean verify` is executed, THE Backend SHALL compile without errors and all tests SHALL pass
2. WHEN `docker compose up` is executed, THE Docker_Environment SHALL start successfully with all health checks passing
3. WHEN the Backend starts, Flyway SHALL apply all pending migrations without errors
4. WHEN the Backend is running, THE OpenAPI endpoint SHALL return a valid specification
5. WHEN the Backend is running, THE Health_Endpoint SHALL return status UP
