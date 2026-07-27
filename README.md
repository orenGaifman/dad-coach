# Dad Coach

AI-powered parenting coaching delivered through WhatsApp. Helps fathers build stronger relationships with their children through guided conversations, daily missions, and personalized coaching.

## Architecture Overview

Dad Coach is a **Spring Boot 3.4 monolith** using package-by-feature architecture with:

- **Java 21** runtime
- **PostgreSQL 17** for persistence
- **Flyway** for database migrations
- **SpringDoc OpenAPI** for API documentation
- **Logback** with JSON structured logging (production) / plain-text (local)
- **Testcontainers** for integration testing
- **Docker Compose** for local development environment

The application follows a package-by-feature layout where each domain (webhook, whatsapp, father, conversation, etc.) owns its full vertical slice. Cross-cutting concerns like error handling, logging, and configuration live in shared packages.

## System Requirements

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | Required. Use SDKMAN or brew to install |
| Docker | 24+ | Required for PostgreSQL (local) and full-stack mode |
| Maven | 3.9+ | Optional — included wrapper (`./mvnw`) recommended |

## Project Structure

```
backend/src/main/java/com/dadcoach/
├── DadCoachApplication.java
├── common/          # Global exception handler, logging filter, startup listener
├── config/          # Application configuration (OpenAPI, HTTP client, WhatsApp props)
├── webhook/         # WhatsApp webhook handling
├── whatsapp/        # WhatsApp API client
├── father/          # Father domain (future)
├── child/           # Child domain (future)
├── conversation/    # Conversation domain (future)
├── memory/          # AI memory (future)
├── ai/              # AI coaching (future)
├── scheduler/       # Scheduled tasks (future)
├── health/          # Custom health indicators (future)
├── mission/         # Coaching missions (future)
├── goal/            # Parenting goals (future)
└── notification/    # Push notifications (future)
```

## Running Locally (without Docker)

Prerequisites: Java 21 installed, a running PostgreSQL instance.

```bash
# Option 1: Start PostgreSQL via Docker (recommended)
docker run -d --name dadcoach-db \
  -e POSTGRES_DB=dadcoach \
  -e POSTGRES_USER=dadcoach \
  -e POSTGRES_PASSWORD=dadcoach \
  -p 5432:5432 \
  postgres:17-alpine

# Copy and configure environment variables
cp .env.example .env
# Edit .env with your WhatsApp credentials

# Run the application using Maven wrapper
cd backend
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` with the `local` profile (plain-text logs, Swagger UI enabled).

## Running with Docker

Full-stack mode starts both PostgreSQL and the backend in containers:

```bash
# Build and start all services
docker compose up --build

# Run in detached mode
docker compose up --build -d

# View logs
docker compose logs -f backend

# Stop all services
docker compose down

# Stop and remove data volumes
docker compose down -v
```

The backend container uses the `dev` profile (JSON structured logs, Swagger UI enabled).

## Environment Variables

Copy `.env.example` to `.env` and configure:

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_URL` | Yes | `jdbc:postgresql://localhost:5432/dadcoach` | PostgreSQL JDBC connection URL |
| `DB_USERNAME` | Yes | `dadcoach` | PostgreSQL username |
| `DB_PASSWORD` | Yes | `dadcoach` | PostgreSQL password |
| `WHATSAPP_PHONE_NUMBER_ID` | Yes | — | WhatsApp Business API phone number ID |
| `WHATSAPP_ACCESS_TOKEN` | Yes | — | WhatsApp Business API access token |
| `WHATSAPP_VERIFY_TOKEN` | Yes (dev/prod) | `dad-coach-local-dev` (local only) | Webhook verification token |
| `WHATSAPP_API_VERSION` | No | `v25.0` | WhatsApp Graph API version |
| `SPRING_PROFILES_ACTIVE` | No | `local` | Active profile: `local`, `dev`, or `prod` |
| `SERVER_PORT` | No | `8080` | HTTP server port |

## Profiles

| Profile | Logging | Swagger UI | Config Source | Health Details |
|---------|---------|------------|---------------|----------------|
| `local` | Plain-text | Enabled | Defaults + env vars | Shown |
| `dev` | JSON structured | Enabled | Env vars (required) | Hidden |
| `prod` | JSON structured | Disabled | Env vars (required) | Hidden |

## Testing

```bash
cd backend

# Unit tests only (fast, no Docker needed)
./mvnw test

# All tests including integration (requires Docker for Testcontainers)
./mvnw clean verify
```

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up a real PostgreSQL instance automatically — Docker must be running.

### Test categories

- **Unit tests** — fast, isolated, no external dependencies. Validate error handling, configuration logic.
- **Integration tests** — use Testcontainers PostgreSQL. Validate context loading, Flyway migrations, health endpoints, OpenAPI spec.

## API Documentation

Swagger UI is available at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) when running with `local` or `dev` profiles.

OpenAPI JSON spec: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Swagger UI is disabled in the `prod` profile.

## Health Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | General application health |
| `/actuator/health/liveness` | Liveness probe (is the app running?) |
| `/actuator/health/readiness` | Readiness probe (includes DB connectivity) |

Use liveness and readiness probes for container orchestration (Docker health checks, Kubernetes probes).
