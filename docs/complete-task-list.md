# Dad Coach — Complete Task List

## Execution Order & Cross-Spec Dependencies

```
SPEC-001 Production Foundation (no dependencies)
    ↓
SPEC-002 Product Domain & Business Logic (depends on SPEC-001)
    ↓
SPEC-004 Memory & Knowledge System (depends on SPEC-001, SPEC-002)
SPEC-006 Communication Channels (depends on SPEC-001, SPEC-002)
    ↓
SPEC-003 AI Architecture Intelligence Layer (depends on SPEC-001, SPEC-002, SPEC-004)
    ↓
SPEC-005 Conversation Engine & Orchestration (depends on SPEC-001–004, SPEC-006)
    ↓
SPEC-008 Scheduling & Automation (depends on SPEC-001, SPEC-002, SPEC-005)
SPEC-007 Application API (depends on SPEC-001, SPEC-002, SPEC-004, SPEC-005)
    ↓
SPEC-009 Administration & Analytics (depends on SPEC-001, SPEC-002, SPEC-005, SPEC-007)
    ↓
SPEC-010 Production & Operations (cross-cutting, depends on all above)
```

---

## EPIC 1: Production Foundation (SPEC-001)

**Dependencies:** None (foundational layer)
**Parallelizable with:** Nothing — must complete first

### Feature 1.1: Project Structure & Configuration

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 1.1.1 | Refactor existing packages to package-by-feature structure (move classes into `common/`, `config/`, `webhook/`, `whatsapp/`, `father/`, `conversation/`, `health/`) | 3h | — | — |
| 1.1.2 | Create `application.yml` with Spring profile strategy (`local`, `dev`, `prod`) with env-var-driven overrides | 2h | — | Yes with 1.1.1 |
| 1.1.3 | Add `application-local.yml`, `application-dev.yml`, `application-prod.yml` profile-specific configs | 2h | 1.1.2 | — |
| 1.1.4 | Add MapStruct dependency and configure annotation processor in `pom.xml` | 2h | — | Yes with 1.1.1 |
| 1.1.5 | Add SpringDoc OpenAPI dependency and `OpenApiConfig.java` (conditional on `!prod` profile) | 2h | 1.1.1 | — |

### Feature 1.2: Error Handling (RFC 9457)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 1.2.1 | Replace `ApiExceptionHandler` with `GlobalExceptionHandler` extending `ResponseEntityExceptionHandler` | 3h | 1.1.1 | — |
| 1.2.2 | Implement validation error handling → 400 with field-level Problem Details | 2h | 1.2.1 | — |
| 1.2.3 | Implement `EntityNotFoundException` → 404 handler | 1h | 1.2.1 | Yes with 1.2.2 |
| 1.2.4 | Implement catch-all → 500 handler (no stack trace exposure, ERROR-level internal logging) | 1h | 1.2.1 | Yes with 1.2.2 |

### Feature 1.3: Observability Baseline

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 1.3.1 | Add `logstash-logback-encoder` dependency; configure JSON logging for `dev`/`prod`, plain text for `local` | 3h | 1.1.3 | — |
| 1.3.2 | Implement `RequestLoggingFilter` (method, path, status, durationMs per request) | 3h | 1.1.1 | Yes with 1.3.1 |
| 1.3.3 | Configure Actuator health endpoints (`/actuator/health`, `/health/liveness`, `/health/readiness`) with DB probe | 2h | 1.1.2 | Yes with 1.3.1 |

### Feature 1.4: Docker & Development Environment

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 1.4.1 | Update `docker-compose.yml` with PostgreSQL 17 (healthcheck, named volume) + backend service with `depends_on: postgres: condition: service_healthy` | 2h | — | Yes with 1.1.1 |
| 1.4.2 | Update multi-stage `Dockerfile` (eclipse-temurin:21, Maven build layer, runtime layer) | 2h | — | Yes with 1.4.1 |
| 1.4.3 | Update `.env.example` with all required environment variables | 1h | 1.1.2 | — |

### Feature 1.5: Testing Infrastructure

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 1.5.1 | Add Testcontainers PostgreSQL dependency to `pom.xml` | 1h | — | Yes with 1.1.1 |
| 1.5.2 | Create `IntegrationTestBase.java` with shared PostgreSQL container and `@DynamicPropertySource` | 3h | 1.5.1 | — |
| 1.5.3 | Write `ApplicationContextIntegrationTest` (app boots, Flyway runs, context loads) | 2h | 1.5.2 | — |
| 1.5.4 | Write `HealthEndpointIntegrationTest` (health, liveness, readiness return UP) | 2h | 1.5.2, 1.3.3 | Yes with 1.5.3 |
| 1.5.5 | Write `GlobalExceptionHandlerTest` (validation→400, NotFound→404, General→500) | 2h | 1.2.1 | Yes with 1.5.3 |
| 1.5.6 | Write `OpenApiIntegrationTest` (`/v3/api-docs` returns valid JSON) | 1h | 1.1.5, 1.5.2 | Yes with 1.5.3 |

---

## EPIC 2: Product Domain & Business Logic (SPEC-002)

**Dependencies:** SPEC-001 complete
**Parallelizable with:** Nothing (all downstream specs depend on this)

### Feature 2.1: Domain Entities & Database Schema

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 2.1.1 | Create Flyway migration `V2__domain_entities.sql` — ALTER father table, CREATE child, goal, habit, mission, memory, conversation, coaching_session, notification, reflection, weekly_summary, state_transition_log, engagement_event tables | 6h | EPIC 1 | — |
| 2.1.2 | Create `Father.java` JPA entity with all fields, enums, validation constraints | 3h | 2.1.1 | — |
| 2.1.3 | Create `Child.java` JPA entity with relationship to Father, age computation | 2h | 2.1.1 | Yes with 2.1.2 |
| 2.1.4 | Create `Goal.java` JPA entity with category enum, progress tracking | 2h | 2.1.1 | Yes with 2.1.2 |
| 2.1.5 | Create `Habit.java` JPA entity with frequency, streak tracking | 2h | 2.1.1 | Yes with 2.1.2 |
| 2.1.6 | Create `Mission.java` JPA entity with status, difficulty, expiration, outcome | 3h | 2.1.1 | Yes with 2.1.2 |
| 2.1.7 | Create `Memory.java` JPA entity with scoring fields, category enum | 2h | 2.1.1 | Yes with 2.1.2 |
| 2.1.8 | Create `Conversation.java` JPA entity with type/status enums, expiration | 2h | 2.1.1 | Yes with 2.1.2 |
| 2.1.9 | Create `ConversationMessage.java` JPA entity (update existing `conversation_message`) | 2h | 2.1.1 | Yes with 2.1.2 |
| 2.1.10 | Create `CoachingSession.java` JPA entity | 1h | 2.1.1 | Yes with 2.1.2 |
| 2.1.11 | Create `Notification.java` JPA entity with priority, scheduling | 2h | 2.1.1 | Yes with 2.1.2 |
| 2.1.12 | Create `Reflection.java` JPA entity | 1h | 2.1.1 | Yes with 2.1.2 |
| 2.1.13 | Create `WeeklySummary.java` JPA entity with unique(father_id, week_start) | 1h | 2.1.1 | Yes with 2.1.2 |
| 2.1.14 | Create all domain enums: `FatherStatus`, `OnboardingState`, `CoachingPhase`, `MissionStatus`, `ConversationStatus`, `ConversationType`, `CoachingSessionOutcome`, `HabitStatus`, `GoalCategory`, `NotificationType`, `MemoryCategory`, `CoachingStyle` | 3h | — | Yes with 2.1.1 |

### Feature 2.2: Repositories

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 2.2.1 | Create `FatherRepository` with custom queries (findByPhone, findActive, findInactive) | 2h | 2.1.2 | — |
| 2.2.2 | Create `ChildRepository` with findByFatherId, birthday queries | 2h | 2.1.3 | Yes with 2.2.1 |
| 2.2.3 | Create `GoalRepository` with findByFatherIdAndStatus | 1h | 2.1.4 | Yes with 2.2.1 |
| 2.2.4 | Create `HabitRepository` with findByFatherIdAndStatus | 1h | 2.1.5 | Yes with 2.2.1 |
| 2.2.5 | Create `MissionRepository` with complex queries (findActiveByChild, countByFatherAndDateRange, distribution queries) | 3h | 2.1.6 | Yes with 2.2.1 |
| 2.2.6 | Create `MemoryRepository` with findByFatherAndStatus, scoring queries | 2h | 2.1.7 | Yes with 2.2.1 |
| 2.2.7 | Create `ConversationRepository` with findActiveByFather, expiration queries | 2h | 2.1.8 | Yes with 2.2.1 |
| 2.2.8 | Create `NotificationRepository` with findDueForDispatch, dailyCount queries | 2h | 2.1.11 | Yes with 2.2.1 |
| 2.2.9 | Create remaining repositories (CoachingSession, Reflection, WeeklySummary, StateTransitionLog, EngagementEvent) | 2h | 2.1.10-13 | Yes with 2.2.1 |

### Feature 2.3: State Machine Engine

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 2.3.1 | Implement `StateMachineEngine` interface and `StateMachineEngineImpl` with transition validation + audit logging | 4h | 2.1.14, 2.2.9 | — |
| 2.3.2 | Define Father state transitions (NOT_STARTED→ONBOARDING→ACTIVE→PAUSED→CHURNED, reactivation paths) | 2h | 2.3.1 | — |
| 2.3.3 | Define Mission state transitions (ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED, skip/expire/abandon paths) | 2h | 2.3.1 | Yes with 2.3.2 |
| 2.3.4 | Define Conversation state transitions (ACTIVE→COMPLETED/EXPIRED/ABANDONED) | 1h | 2.3.1 | Yes with 2.3.2 |
| 2.3.5 | Define Habit state transitions (ACTIVE→PAUSED→COMPLETED/ARCHIVED) | 1h | 2.3.1 | Yes with 2.3.2 |
| 2.3.6 | Define Onboarding state transitions (NOT_STARTED→NAME_COLLECTED→...→COMPLETED) | 2h | 2.3.1 | Yes with 2.3.2 |

### Feature 2.4: Domain Services

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 2.4.1 | Implement `FatherService` — CRUD, status transitions, pause/resume, engagement score computation | 6h | 2.2.1, 2.3.2 | — |
| 2.4.2 | Implement `ChildService` — CRUD, age computation, birthday detection, developmental bracket classification | 4h | 2.2.2 | Yes with 2.4.1 |
| 2.4.3 | Implement `GoalService` — CRUD, progress computation, capacity enforcement (max 5 active) | 3h | 2.2.3 | Yes with 2.4.1 |
| 2.4.4 | Implement `HabitService` — CRUD, streak tracking, frequency-based reset rules, completion (66 streak) | 4h | 2.2.4, 2.3.5 | Yes with 2.4.1 |
| 2.4.5 | Implement `MissionService` — create, accept, complete, skip, expire; difficulty bounds enforcement | 4h | 2.2.5, 2.3.3 | Yes with 2.4.1 |
| 2.4.6 | Implement `EngagementScoreCalculator` — 7-day rolling window formula | 3h | 2.2.9 | Yes with 2.4.1 |
| 2.4.7 | Implement `CoachingPhaseCalculator` — day-based phase determination | 2h | 2.1.14 | Yes with 2.4.1 |

### Feature 2.5: Business Rules & Scoring

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 2.5.1 | Implement mission difficulty adaptation logic (outcome_rating → next difficulty) | 3h | 2.4.5 | — |
| 2.5.2 | Implement mission category non-repetition (max 2 per category per child per 7 days) | 3h | 2.4.5 | Yes with 2.5.1 |
| 2.5.3 | Implement mission expiration rules (weekday→24h, weekend→48h) | 2h | 2.4.5 | Yes with 2.5.1 |
| 2.5.4 | Implement mission time constraints (weekday≤30min, weekend≤120min) | 1h | 2.4.5 | Yes with 2.5.1 |
| 2.5.5 | Implement equitable mission distribution (selectNextChild algorithm) | 4h | 2.4.5, 2.4.2 | — |
| 2.5.6 | Implement single active mission per child constraint | 2h | 2.4.5 | Yes with 2.5.1 |
| 2.5.7 | Implement coaching streak calculation (consecutive days with interaction) | 3h | 2.4.1 | Yes with 2.5.1 |
| 2.5.8 | Implement goal progress computation | 2h | 2.4.3 | Yes with 2.5.1 |
| 2.5.9 | Implement memory ranking formula (importance×0.5 + recency×0.3 + relevance×0.2) | 3h | 2.2.6 | Yes with 2.5.1 |
| 2.5.10 | Implement memory capacity limit (500 per father, archive lowest scoring) | 2h | 2.2.6 | Yes with 2.5.9 |
| 2.5.11 | Implement memory tier expiration rules (score 1-3→90d, 4-6→180d, 7-10→never) | 2h | 2.2.6 | Yes with 2.5.9 |
| 2.5.12 | Implement quiet hours enforcement (21:00-07:00 local → reschedule to 07:00) | 3h | 2.4.1 | Yes with 2.5.1 |
| 2.5.13 | Implement daily notification rate limit (max 5 proactive per day) | 2h | 2.2.8 | Yes with 2.5.12 |
| 2.5.14 | Implement notification priority deconfliction (single highest priority, others spaced 2h) | 3h | 2.2.8 | Yes with 2.5.13 |
| 2.5.15 | Implement pause duration capping (min(requested, 30) days) | 1h | 2.4.1 | Yes with 2.5.1 |
| 2.5.16 | Implement inactivity-to-churn transition (21+ days → CHURNED) | 2h | 2.4.1 | Yes with 2.5.1 |
| 2.5.17 | Implement message batching logic (3+ messages in 10s → wait 5s → combine) | 4h | — | Yes with 2.5.1 |

### Feature 2.6: Property-Based Tests (SPEC-002)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 2.6.1 | Add jqwik dependency to `pom.xml` | 1h | EPIC 1 | — |
| 2.6.2 | Create custom arbitraries (Father, Child, Mission, Memory generators) | 4h | 2.1.14 | — |
| 2.6.3 | Write property tests P1-P6 (phone validation, engagement score, coaching phase, child age, birthday, developmental brackets) | 6h | 2.6.2, 2.4.1-2.4.7 | — |
| 2.6.4 | Write property tests P7-P9 (state machine transitions, pause capping, inactivity-to-churn) | 4h | 2.6.2, 2.3.1 | Yes with 2.6.3 |
| 2.6.5 | Write property tests P10-P16 (mission difficulty, category, expiration, time, distribution, single active) | 8h | 2.6.2, 2.5.1-2.5.6 | Yes with 2.6.3 |
| 2.6.6 | Write property tests P17-P20 (memory tier expiration, confidence decay, ranking, capacity) | 4h | 2.6.2, 2.5.9-2.5.11 | Yes with 2.6.3 |
| 2.6.7 | Write property tests P21-P25 (single conversation, message limit, quiet hours, daily rate, priority) | 6h | 2.6.2, 2.5.12-2.5.14 | Yes with 2.6.3 |
| 2.6.8 | Write property tests P26-P35 (streak, goal progress, habit reset, AI model selection, token budget, weekly summary exclusion, capacity limits, message batching, AI rate limit, reflection limit) | 8h | 2.6.2, 2.5.7-2.5.8 | Yes with 2.6.3 |

---

## EPIC 3: Memory & Knowledge System (SPEC-004)

**Dependencies:** SPEC-001, SPEC-002 entities
**Parallelizable with:** EPIC 4 (Communication Channels)

### Feature 3.1: Database Schema & Entities

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 3.1.1 | Create Flyway migration for pgvector extension (`CREATE EXTENSION IF NOT EXISTS vector`) | 1h | EPIC 2 | — |
| 3.1.2 | Create Flyway migration for `memories` table (UUID PKs, vector(1536), ivfflat index), `memory_versions`, `memory_audit_log`, `safety_event_records` tables | 4h | 3.1.1 | — |
| 3.1.3 | Create `Memory.java` JPA entity (UUID-based, vector column via Hibernate types) | 3h | 3.1.2 | — |
| 3.1.4 | Create `MemoryVersion.java` JPA entity | 1h | 3.1.2 | Yes with 3.1.3 |
| 3.1.5 | Create `MemoryAuditEntry.java` JPA entity | 1h | 3.1.2 | Yes with 3.1.3 |
| 3.1.6 | Create `SafetyEventRecord.java` JPA entity | 1h | 3.1.2 | Yes with 3.1.3 |
| 3.1.7 | Create `MemoryRepository` with native query for pgvector cosine similarity search | 4h | 3.1.3 | — |

### Feature 3.2: Embedding Service

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 3.2.1 | Implement `EmbeddingService` — calls AI provider for text-embedding-3-small (1536-dim) | 4h | 3.1.3 | — |
| 3.2.2 | Implement `EmbeddingQueue` — retry queue for failed embedding generation (3 attempts / 24h) | 3h | 3.2.1 | — |

### Feature 3.3: Memory Retrieval

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 3.3.1 | Implement `CompositeScoreCalculator` (importance×0.5 + recency×0.3 + relevance×0.2) | 3h | 3.1.3 | — |
| 3.3.2 | Implement `MemoryRetriever` with ranked retrieval, diversity constraint (max 5 per category), confidence floor (≥0.3) | 5h | 3.3.1, 3.1.7 | — |
| 3.3.3 | Implement `RetrievalMetadata` — rich metadata per result (why selected, composite breakdown) | 2h | 3.3.2 | — |

### Feature 3.4: Memory Extraction & Creation

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 3.4.1 | Implement `MemoryExtractionService` — processes extraction requests from outbox | 4h | 3.1.3, 3.2.1 | — |
| 3.4.2 | Implement `ExtractionValidator` — validates AI extraction recommendations before persistence | 3h | 3.4.1 | — |
| 3.4.3 | Implement `DuplicateDetector` — cosine similarity >0.85=duplicate, 0.70-0.85=potential update | 4h | 3.1.7, 3.2.1 | — |

### Feature 3.5: Memory Lifecycle

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 3.5.1 | Implement `MemoryDecayService` — daily scheduled confidence decay job | 4h | 3.1.3 | — |
| 3.5.2 | Implement `MemoryConsolidationService` — weekly merge job for related memories | 5h | 3.1.3 | Yes with 3.5.1 |
| 3.5.3 | Implement `MemoryExpirationService` — transitions expired memories based on tier rules | 3h | 3.1.3 | Yes with 3.5.1 |
| 3.5.4 | Implement `MemoryDeletionService` — 72-hour content erasure (nullify content, drop embedding, purge versions) | 4h | 3.1.3, 3.1.4 | Yes with 3.5.1 |
| 3.5.5 | Implement `ConflictDetector` — contradiction detection + conflict group management | 4h | 3.1.7 | Yes with 3.5.1 |

### Feature 3.6: Public Interface & Audit

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 3.6.1 | Implement `MemoryService` interface and implementation (retrieveRanked, triggerExtraction, confirm, supersede, delete, deleteAll, getCapacity) | 6h | 3.3.2, 3.4.1, 3.5.1-3.5.5 | — |
| 3.6.2 | Implement `MemoryAuditService` — append-only audit log, synchronous with operations | 3h | 3.1.5 | — |
| 3.6.3 | Implement `SensitiveMemoryService` — safety event records (separate from normal memories) | 3h | 3.1.6 | Yes with 3.6.2 |

### Feature 3.7: Tests (SPEC-004)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 3.7.1 | Integration tests: memory CRUD with pgvector (Testcontainers with pgvector-enabled image) | 4h | 3.6.1 | — |
| 3.7.2 | Unit tests: CompositeScoreCalculator, DuplicateDetector, ExtractionValidator | 4h | 3.3.1, 3.4.2, 3.4.3 | Yes with 3.7.1 |
| 3.7.3 | Unit tests: lifecycle services (decay, consolidation, expiration, deletion) | 4h | 3.5.1-3.5.4 | Yes with 3.7.1 |

---

## EPIC 4: Communication Channels (SPEC-006)

**Dependencies:** SPEC-001, SPEC-002 (Father entity)
**Parallelizable with:** EPIC 3 (Memory & Knowledge System)

### Feature 4.1: Database Schema & Entities

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 4.1.1 | Create Flyway migration for `communication_endpoints`, `delivery_records`, `template_messages`, `media_assets` tables | 4h | EPIC 2 | — |
| 4.1.2 | Create `CommunicationEndpoint.java` JPA entity (father↔channel identity, session window) | 2h | 4.1.1 | — |
| 4.1.3 | Create `DeliveryRecord.java` JPA entity (status tracking, retry count) | 2h | 4.1.1 | Yes with 4.1.2 |
| 4.1.4 | Create `TemplateMessage.java` JPA entity (template registry) | 2h | 4.1.1 | Yes with 4.1.2 |
| 4.1.5 | Create `MediaAsset.java` JPA entity (BYTEA content, 90-day expiration) | 2h | 4.1.1 | Yes with 4.1.2 |
| 4.1.6 | Create repositories: `CommunicationEndpointRepository`, `DeliveryRecordRepository`, `TemplateMessageRepository`, `MediaAssetRepository` | 3h | 4.1.2-4.1.5 | — |

### Feature 4.2: Channel Adapter Pattern

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 4.2.1 | Define `ChannelAdapter` interface (getChannelName, getCapabilities, normalizeInbound, sendMessage, getSessionState, getDeliveryStatus) | 2h | — | — |
| 4.2.2 | Implement `ChannelRouter` — selects adapter based on father's primary endpoint | 3h | 4.2.1, 4.1.6 | — |
| 4.2.3 | Define `ChannelCapabilities` value object (supported message types, media, templates) | 1h | — | Yes with 4.2.1 |
| 4.2.4 | Implement `MessageDowngrader` — automatic type downgrade when unsupported by adapter | 3h | 4.2.3 | — |

### Feature 4.3: WhatsApp Adapter

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 4.3.1 | Implement `WhatsAppSignatureVerifier` (HMAC-SHA256 verification of webhook payload) | 3h | — | — |
| 4.3.2 | Refactor `WhatsAppWebhookController` — add signature verification, normalize payload, forward to Conversation Engine | 4h | 4.3.1, 4.3.3 | — |
| 4.3.3 | Implement `WhatsAppMessageParser` — raw webhook JSON → `InboundMessageDto` (text, image, audio, video, document, location, sticker) | 5h | — | Yes with 4.3.1 |
| 4.3.4 | Implement `WhatsAppMessageFormatter` — `OutboundMessageDto` → WhatsApp Cloud API format (markdown, emoji, char limits) | 4h | — | Yes with 4.3.1 |
| 4.3.5 | Implement `WhatsAppApiClient` — WebClient wrapper for Cloud API (send message, get media, delivery status) | 4h | — | Yes with 4.3.1 |
| 4.3.6 | Implement `WhatsAppAdapter` — implements `ChannelAdapter` using parser, formatter, client | 3h | 4.2.1, 4.3.3-4.3.5 | — |
| 4.3.7 | Create `WhatsAppWebhookPayload.java` and `WhatsAppSendRequest.java` DTOs | 2h | — | Yes with 4.3.1 |

### Feature 4.4: Delivery Service

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 4.4.1 | Implement `SessionWindowService` — track session opens/closes per endpoint, evaluate before outbound | 4h | 4.1.2, 4.1.6 | — |
| 4.4.2 | Implement `DeliveryService` — outbound orchestration (resolve endpoint → check session → check capabilities → deliver) | 5h | 4.2.2, 4.4.1 | — |
| 4.4.3 | Implement `DeliveryRetryService` — transport-level retry (2s, 4s, 8s, 16s, 32s, max 5) | 3h | 4.4.2 | — |
| 4.4.4 | Implement delivery status webhook processing (status updates from provider → update DeliveryRecord) | 3h | 4.1.3, 4.3.2 | — |

### Feature 4.5: Templates & Media

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 4.5.1 | Implement `TemplateRegistry` — store approved templates, lookup by name/category | 2h | 4.1.4, 4.1.6 | — |
| 4.5.2 | Implement `MediaService` — download from provider, store in DB, retrieve, handle expiration | 4h | 4.1.5, 4.3.5 | Yes with 4.5.1 |
| 4.5.3 | Implement media cleanup scheduled job — delete expired assets (90-day retention) daily | 2h | 4.5.2 | — |

### Feature 4.6: DTOs & Normalization

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 4.6.1 | Define `InboundMessageDto` (normalized internal format: fatherId, content, messageType, mediaRef, timestamp) | 2h | — | — |
| 4.6.2 | Define `OutboundMessageDto` (normalized internal format: fatherId, content, messageType, mediaRef, isTemplate) | 2h | — | Yes with 4.6.1 |
| 4.6.3 | Define `DeliveryResult` value object | 1h | — | Yes with 4.6.1 |

### Feature 4.7: Circuit Breaker & Error Handling

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 4.7.1 | Configure Resilience4j circuit breaker for WhatsApp API (10 consecutive failures in 5 min → pause 60s) | 3h | 4.3.5 | — |
| 4.7.2 | Implement provider rate limit handling (pause for backoff, queue pending) | 3h | 4.4.2 | Yes with 4.7.1 |

### Feature 4.8: Tests (SPEC-006)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 4.8.1 | Unit tests: WhatsAppSignatureVerifier (valid/invalid signatures) | 2h | 4.3.1 | — |
| 4.8.2 | Unit tests: WhatsAppMessageParser (all message types) | 3h | 4.3.3 | Yes with 4.8.1 |
| 4.8.3 | Unit tests: SessionWindowService (open/closed/edge cases) | 2h | 4.4.1 | Yes with 4.8.1 |
| 4.8.4 | Integration tests: DeliveryService end-to-end with WireMock | 4h | 4.4.2 | — |
| 4.8.5 | Unit tests: DeliveryRetryService (backoff sequence, max retries) | 2h | 4.4.3 | Yes with 4.8.1 |

---

## EPIC 5: AI Architecture Intelligence Layer (SPEC-003)

**Dependencies:** SPEC-001, SPEC-002, SPEC-004 (Memory)
**Parallelizable with:** Nothing at this layer (needs memory system)

### Feature 5.1: Database Schema

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.1.1 | Create Flyway migration for `ai_telemetry`, `ai_daily_summary`, `prompt_versions` tables | 3h | EPIC 2, EPIC 3 | — |

### Feature 5.2: Provider Adapter Layer

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.2.1 | Define `AiProvider` interface (sendPrompt, getCapabilities) and standardized request/response records | 3h | — | — |
| 5.2.2 | Implement `OpenAiProvider` adapter (GPT-4o, GPT-4o-mini) using WebClient, JSON mode | 5h | 5.2.1 | — |
| 5.2.3 | Implement `AnthropicProvider` adapter (Claude 3.5) as secondary fallback | 4h | 5.2.1 | Yes with 5.2.2 |
| 5.2.4 | Configure Resilience4j circuit breaker per provider (5% failure → open for 1 min) | 3h | 5.2.2, 5.2.3 | — |

### Feature 5.3: Model Routing & Fallback

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.3.1 | Implement `ModelRouter` — conversation type → model/provider routing table | 4h | 5.2.1 | — |
| 5.3.2 | Implement `FallbackChain` — ordered provider fallback (same provider lower-tier → secondary provider → pre-written) | 4h | 5.3.1, 5.2.2, 5.2.3 | — |
| 5.3.3 | Implement `CostController` — per-father daily budget tracking, tier enforcement (80%→mini, 90%→reduced, 95%→cached, 100%→fallback) | 5h | 5.1.1 | Yes with 5.3.1 |

### Feature 5.4: Prompt System

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.4.1 | Implement `TokenBudgetManager` — exact token counting with tiktoken4j (system:400, memory:500, context:300, history:600, output:200) | 5h | — | — |
| 5.4.2 | Implement `PromptAssembler` — compose prompts from sections respecting token budgets, sliding window for history | 6h | 5.4.1 | — |
| 5.4.3 | Implement `PromptRegistry` — version tracking, A/B test group assignment (deterministic hash per father_id) | 4h | 5.1.1 | Yes with 5.4.1 |
| 5.4.4 | Create initial prompt templates (YAML/Mustache) for all conversation types (system, persona, output instructions) | 8h | 5.4.3 | — |

### Feature 5.5: Safety Layer

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.5.1 | Implement `SafetyClassifier` — keyword + semantic classification (SAFE, EMOTIONAL_DISTRESS, CRISIS, CHILD_SAFETY, MEDICAL, LEGAL, MANIPULATION, OFF_TOPIC) | 6h | 5.2.2 | — |
| 5.5.2 | Create `SafetyKeywords.java` — Spanish keyword lists for each category | 3h | — | Yes with 5.5.1 |
| 5.5.3 | Create pre-written safety responses library (per classification type, in Latin American Spanish) | 3h | — | Yes with 5.5.1 |

### Feature 5.6: Decision Engine

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.6.1 | Implement `DecisionEngine` — priority-tree action selection with phase constraints | 5h | 5.4.2 | — |
| 5.6.2 | Implement `ActionHistory` — per-father action tracking (4-hour gap enforcement) | 3h | 5.6.1 | — |

### Feature 5.7: Mission Planning & Memory Extraction

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.7.1 | Implement `MissionPlanner` — generation + validation (difficulty bounds, cooldowns, child equity) | 6h | 5.2.2, 5.4.2 | — |
| 5.7.2 | Implement `MemoryExtractor` — conversation → structured memory recommendations | 4h | 5.2.2, 5.4.2 | Yes with 5.7.1 |

### Feature 5.8: Quality & Evaluation

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.8.1 | Implement `QualityScorer` — automated response quality check (mcr×0.3 + nor×0.25 + ccr×0.25 + nsd×0.2) | 4h | — | — |
| 5.8.2 | Implement `EvaluationEngine` — metrics correlation, A/B analysis | 4h | 5.8.1, 5.4.3 | — |
| 5.8.3 | Implement `OutputValidator` — schema validation per output type (all required fields present, enums valid, ranges checked) | 4h | — | Yes with 5.8.1 |

### Feature 5.9: Telemetry

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.9.1 | Implement `AiTelemetryService` — structured telemetry emission (request_id, tokens, cost, latency, model, quality_score) | 4h | 5.1.1 | — |
| 5.9.2 | Implement daily summary materialized view refresh job | 3h | 5.9.1 | — |

### Feature 5.10: Public Interface

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.10.1 | Implement `IntelligenceLayer` interface and `IntelligenceLayerImpl` — coordination of all sub-components | 6h | 5.3.1, 5.4.2, 5.5.1, 5.6.1, 5.7.1, 5.8.3 | — |
| 5.10.2 | Define output records: `CoachingResponse`, `MissionOutput`, `MemoryExtractionOutput`, `SafetyClassification`, `ActionRecommendation`, `WeeklySummaryOutput`, `ReflectionInsightOutput` | 3h | — | Yes with any |

### Feature 5.11: Tests (SPEC-003)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 5.11.1 | Property tests P1-P2: TokenBudgetManager (budget invariant, sliding window minimum) | 4h | 5.4.1, 5.4.2 | — |
| 5.11.2 | Property tests P3-P5: DecisionEngine (priority ordering, phase constraints, 4-hour gap) | 5h | 5.6.1 | Yes with 5.11.1 |
| 5.11.3 | Property tests P6-P8: MemoryInjector (score ordering, diversity, confidence floor) | 4h | Feature 3.3 | Yes with 5.11.1 |
| 5.11.4 | Property tests P9-P11: MissionPlanner (difficulty bounds, cooldowns, child equity) | 5h | 5.7.1 | Yes with 5.11.1 |
| 5.11.5 | Property tests P12-P14: CostController + ModelRouter + FallbackChain | 5h | 5.3.1-5.3.3 | Yes with 5.11.1 |
| 5.11.6 | Property tests P15-P19: OutputValidator, SafetyClassifier, QualityScorer, A/B assignment, alert thresholds | 6h | 5.5.1, 5.8.1, 5.8.3 | Yes with 5.11.1 |
| 5.11.7 | Integration tests: end-to-end AI pipeline with WireMock (mock LLM providers) | 6h | 5.10.1 | — |
| 5.11.8 | Integration tests: telemetry + cost tracking persistence with Testcontainers | 3h | 5.9.1 | Yes with 5.11.7 |

---

## EPIC 6: Conversation Engine & Orchestration (SPEC-005)

**Dependencies:** SPEC-001, SPEC-002, SPEC-003, SPEC-004, SPEC-006
**Parallelizable with:** Nothing (central orchestrator, needs all subsystems)

### Feature 6.1: Database Schema

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.1.1 | Create Flyway migration `V*__conversation_engine.sql` — `conversations` (UUID PK), `conversation_messages`, `processed_messages`, `side_effect_outbox` tables | 4h | EPIC 2-5 | — |

### Feature 6.2: Session Lock & Idempotency

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.2.1 | Implement `SessionLockService` — PostgreSQL advisory locks (`pg_advisory_xact_lock`) with 45s timeout | 3h | 6.1.1 | — |
| 6.2.2 | Implement `ProcessedMessage` entity + idempotency check (24h TTL, unique constraint) | 3h | 6.1.1 | Yes with 6.2.1 |
| 6.2.3 | Implement idempotency key cleanup scheduled job | 1h | 6.2.2 | — |

### Feature 6.3: Conversation Lifecycle

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.3.1 | Implement `ConversationService` — create, findActive, transition states, evaluate completion (8-message cap, objective met) | 5h | 6.1.1 | — |
| 6.3.2 | Implement `MessageProcessor` — inbound message validation and routing (resolve father by channel identity) | 3h | 6.3.1 | — |
| 6.3.3 | Implement conversation expiration logic (per-type expiration windows from config) | 3h | 6.3.1 | Yes with 6.3.2 |
| 6.3.4 | Implement `ConversationRecoveryService` — stale conversation detection every 15 min, force-expire overdue | 3h | 6.3.1 | — |

### Feature 6.4: Context Assembly

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.4.1 | Implement `ContextAssembler` — collects data from all subsystems (father profile, children, goals, missions, memories) | 5h | EPIC 2 services, EPIC 3 | — |
| 6.4.2 | Define `ContextRequest` value object for context parameters | 1h | — | Yes with 6.4.1 |

### Feature 6.5: AI Orchestration

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.5.1 | Implement `AiOrchestrator` — safety classify → generate → validate → retry → fallback pipeline | 5h | EPIC 5 (IntelligenceLayer) | — |
| 6.5.2 | Implement `ResponseValidator` — schema + business rule validation of AI output | 3h | 5.8.3 | Yes with 6.5.1 |
| 6.5.3 | Implement `FallbackResponseProvider` — per-conversation-type pre-written fallback messages (Latin American Spanish) | 3h | — | Yes with 6.5.1 |

### Feature 6.6: Mission & Memory Orchestration

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.6.1 | Implement `MissionOrchestrator` — mission state transitions within conversations (GENERATE_MISSION action handling) | 4h | EPIC 2 MissionService | — |
| 6.6.2 | Implement `MemoryOrchestrator` — memory trigger scheduling (extraction on conversation completion, confirmation tracking) | 3h | EPIC 3 MemoryService | Yes with 6.6.1 |

### Feature 6.7: Side-Effect Outbox

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.7.1 | Implement `SideEffectScheduler` — outbox writer (within main transaction) for async work | 3h | 6.1.1 | — |
| 6.7.2 | Implement `SideEffectProcessor` — background poller (5s interval, batch 20), exponential backoff, max retries | 5h | 6.7.1 | — |
| 6.7.3 | Define `SideEffect` enum (MEMORY_EXTRACTION, EVENT_PUBLISH, METRIC_UPDATE, etc.) with mandatory vs best-effort classification | 1h | — | Yes with 6.7.1 |
| 6.7.4 | Implement `ConversationEventPublisher` — business event emission through outbox | 3h | 6.7.1, 6.7.3 | — |

### Feature 6.8: Main Pipeline

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.8.1 | Implement `ConversationOrchestrator.processMessage()` — full 12-step pipeline coordinating all services | 8h | 6.2.1, 6.3.1-6.3.2, 6.4.1, 6.5.1, 6.6.1-6.6.2, 6.7.1-6.7.4 | — |
| 6.8.2 | Implement configuration (YAML) for pipeline parameters (lock timeout, max messages, latency budget, expiration windows, cooldowns) | 2h | 6.8.1 | — |

### Feature 6.9: Tests (SPEC-005)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 6.9.1 | Unit tests: ConversationOrchestrator with mocked subsystems (happy path, each failure point) | 6h | 6.8.1 | — |
| 6.9.2 | Unit tests: AiOrchestrator (safety block, generation success, retry, fallback) | 4h | 6.5.1 | Yes with 6.9.1 |
| 6.9.3 | Integration tests: full pipeline with real PostgreSQL (advisory locks, transactions, outbox) | 6h | 6.8.1 | — |
| 6.9.4 | Integration tests: SideEffectProcessor (outbox polling, retry behavior, failure handling) | 3h | 6.7.2 | Yes with 6.9.3 |
| 6.9.5 | Recovery tests: ConversationRecoveryService (stale detection, force-expiration) | 3h | 6.3.4 | Yes with 6.9.3 |

---

## EPIC 7: Scheduling & Automation (SPEC-008)

**Dependencies:** SPEC-001, SPEC-002, SPEC-005 (Conversation Engine)
**Parallelizable with:** EPIC 8 (Application API)

### Feature 7.1: Database Schema & Entities

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.1.1 | Create Flyway migration for `scheduled_triggers`, `trigger_history` tables | 2h | EPIC 6 | — |
| 7.1.2 | Create `ScheduledTrigger.java` JPA entity (fire_at, window_expires_at, priority, status, context JSONB) | 2h | 7.1.1 | — |
| 7.1.3 | Create `TriggerHistory.java` JPA entity (audit log of fired/missed triggers) | 1h | 7.1.1 | Yes with 7.1.2 |
| 7.1.4 | Create `ScheduledTriggerRepository` and `TriggerHistoryRepository` | 2h | 7.1.2, 7.1.3 | — |

### Feature 7.2: Timing Services

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.2.1 | Implement `TimezoneResolver` — father local time ↔ UTC conversion, DST-safe | 3h | — | — |
| 7.2.2 | Implement `QuietHoursEvaluator` — check if UTC time falls in father's quiet hours (21:00-07:00 local) | 3h | 7.2.1 | — |
| 7.2.3 | Implement `EligibleWindowEvaluator` — 07:00-21:00 local time validation | 2h | 7.2.1 | Yes with 7.2.2 |
| 7.2.4 | Implement `CooldownEvaluator` — post-conversation cooldown check | 2h | — | Yes with 7.2.2 |

### Feature 7.3: Conflict Resolution & Spacing

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.3.1 | Implement `ConflictResolver` — priority-based selection when multiple triggers due for same father | 3h | 7.1.2 | — |
| 7.3.2 | Implement `SpacingEnforcer` — 4-hour minimum gap enforcement between proactive messages | 3h | — | Yes with 7.3.1 |

### Feature 7.4: Schedule Building & Recalculation

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.4.1 | Implement `FatherScheduleBuilder` — creates initial schedule on onboarding (daily coaching at preferred_time ± 15min) | 4h | 7.1.4, 7.2.1 | — |
| 7.4.2 | Implement `ScheduleRecalculator` — handles timezone/preference changes (cancel pending, recompute for next day) | 3h | 7.4.1 | — |
| 7.4.3 | Implement `BirthdayScheduler` — compute birthday triggers from child birth_dates (7 days before) | 3h | 7.1.4, 7.2.1 | Yes with 7.4.1 |

### Feature 7.5: Trigger Evaluation Loop

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.5.1 | Implement `TriggerEvaluator` — main poller (30s interval): find due triggers, group by father, precondition check, conflict resolve, spacing check, emit | 6h | 7.1.4, 7.2.2, 7.3.1, 7.3.2 | — |
| 7.5.2 | Implement `TriggerEmitter` — writes Automation_Triggers to side-effect outbox for Conversation Engine consumption | 3h | 7.5.1, EPIC 6 outbox | — |

### Feature 7.6: Inactivity Detection

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.6.1 | Implement `InactivityEvaluator` — hourly check for fathers crossing 3/7/14/21-day inactivity thresholds | 4h | 7.1.4 | — |

### Feature 7.7: Father Lifecycle Handling

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.7.1 | Implement `FatherLifecycleHandler` — respond to ACTIVE/PAUSED/CHURNED transitions (suspend/reactivate triggers) | 3h | 7.1.4 | — |

### Feature 7.8: Configuration & Error Recovery

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.8.1 | Implement `SchedulerProperties` configuration (poll interval, maintenance window, default times, randomization) | 2h | — | — |
| 7.8.2 | Implement system recovery logic — on startup, evaluate all overdue triggers; fire those within window; mark rest MISSED | 3h | 7.5.1 | — |

### Feature 7.9: Tests (SPEC-008)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 7.9.1 | Unit tests: TimezoneResolver (DST transitions, edge cases) | 3h | 7.2.1 | — |
| 7.9.2 | Unit tests: ConflictResolver + SpacingEnforcer | 3h | 7.3.1, 7.3.2 | Yes with 7.9.1 |
| 7.9.3 | Unit tests: InactivityEvaluator (threshold crossing scenarios) | 2h | 7.6.1 | Yes with 7.9.1 |
| 7.9.4 | Integration tests: TriggerEvaluator full cycle with Testcontainers | 4h | 7.5.1 | — |
| 7.9.5 | Integration tests: FatherScheduleBuilder + recalculation | 3h | 7.4.1, 7.4.2 | Yes with 7.9.4 |

---

## EPIC 8: Application API (SPEC-007)

**Dependencies:** SPEC-001, SPEC-002, SPEC-004, SPEC-005
**Parallelizable with:** EPIC 7 (Scheduling & Automation)

### Feature 8.1: Security Infrastructure

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 8.1.1 | Implement `SecurityConfig` — Spring Security filter chain with role-based authorization (FATHER, ADMIN, SERVICE) | 4h | EPIC 1 | — |
| 8.1.2 | Implement `JwtAuthFilter` — token validation, claim extraction (father_id, roles) | 4h | 8.1.1 | — |
| 8.1.3 | Implement `ActorContext` — ThreadLocal with current actor (type, id, permissions) | 2h | 8.1.2 | — |
| 8.1.4 | Implement `RolePermission` — role → permission mapping (FATHER: own data, ADMIN: all, SERVICE: internal) | 2h | — | Yes with 8.1.1 |
| 8.1.5 | Implement `CorsConfig` — CORS allowed origins configuration | 1h | — | Yes with 8.1.1 |

### Feature 8.2: Database Schema & Cross-Cutting

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 8.2.1 | Create Flyway migration for `api_audit_log`, `idempotency_keys` tables | 2h | EPIC 2 | — |
| 8.2.2 | Implement `ApiAuditAspect` (AOP) — intercept all mutating API calls, write audit entries synchronously | 4h | 8.2.1 | — |
| 8.2.3 | Implement `IdempotencyFilter` — check Idempotency-Key header, return cached response if duplicate | 4h | 8.2.1 | Yes with 8.2.2 |
| 8.2.4 | Implement `RateLimitFilter` — per-actor rate limit enforcement | 3h | 8.1.3 | Yes with 8.2.2 |

### Feature 8.3: Pagination & Error Handling

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 8.3.1 | Implement `CursorPageRequest` — opaque cursor parsing (base64-encoded composite keys) | 3h | — | — |
| 8.3.2 | Implement `CursorPageResponse` — response with next_cursor + has_more | 2h | 8.3.1 | — |
| 8.3.3 | Implement API-specific `GlobalExceptionHandler` with full error code enum (VALIDATION_FAILED, TOKEN_EXPIRED, RESOURCE_NOT_FOUND, LIMIT_EXCEEDED, etc.) | 4h | EPIC 1 1.2.1 | — |
| 8.3.4 | Define `ErrorCode` enum with all error codes, HTTP status mappings, and retryable flags | 2h | — | Yes with 8.3.3 |

### Feature 8.4: Father API Controllers

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 8.4.1 | Implement `FatherController` — GET /api/v1/fathers/me, PUT (update profile), DELETE (account deletion) | 4h | 8.1.3, EPIC 2 | — |
| 8.4.2 | Implement `ChildController` — full CRUD under /api/v1/fathers/me/children (with ownership enforcement) | 4h | 8.1.3, EPIC 2 | Yes with 8.4.1 |
| 8.4.3 | Implement `GoalController` — CRUD under /api/v1/fathers/me/goals (with capacity limit 5) | 3h | 8.1.3, EPIC 2 | Yes with 8.4.1 |
| 8.4.4 | Implement `MissionController` — read-only: list, get, active mission | 3h | 8.1.3, EPIC 2 | Yes with 8.4.1 |
| 8.4.5 | Implement `ConversationController` — read-only: list, get with messages (pagination) | 3h | 8.1.3, EPIC 6 | Yes with 8.4.1 |
| 8.4.6 | Implement `MemoryController` — list, get, delete (for Father API) | 3h | 8.1.3, EPIC 3 | Yes with 8.4.1 |

### Feature 8.5: Admin API Controllers

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 8.5.1 | Implement `AdminFatherController` — search, list, get any father, override status | 4h | 8.1.3 | — |
| 8.5.2 | Implement `AdminMemoryController` — includes archived, audit history, embeddings excluded | 3h | 8.1.3 | Yes with 8.5.1 |
| 8.5.3 | Implement `HealthController` — detailed health for Service API (authenticated) | 2h | 8.1.3 | Yes with 8.5.1 |

### Feature 8.6: DTOs & Mappers

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 8.6.1 | Create request DTOs with Jakarta Validation annotations (FatherUpdateRequest, ChildCreateRequest, GoalCreateRequest) | 3h | — | — |
| 8.6.2 | Create response DTOs (FatherResponseDto, ChildResponseDto, MissionResponseDto, etc.) — never expose sensitive fields | 3h | — | Yes with 8.6.1 |
| 8.6.3 | Create MapStruct mappers (entity → DTO) with sensitivity filtering per actor type | 4h | 8.6.1, 8.6.2 | — |

### Feature 8.7: API Versioning

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 8.7.1 | Implement `ApiVersionConfig` — version prefix registration (/api/v1/) | 1h | — | — |

### Feature 8.8: Tests (SPEC-007)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 8.8.1 | Unit tests: JwtAuthFilter (valid/expired/invalid tokens) | 3h | 8.1.2 | — |
| 8.8.2 | Unit tests: CursorPageRequest/Response (encoding/decoding, edge cases) | 2h | 8.3.1-8.3.2 | Yes with 8.8.1 |
| 8.8.3 | Integration tests: Father API endpoints (CRUD, ownership enforcement, 404 vs 403) | 5h | 8.4.1-8.4.6 | — |
| 8.8.4 | Integration tests: Admin API (role enforcement, search, override) | 4h | 8.5.1-8.5.3 | Yes with 8.8.3 |
| 8.8.5 | Integration tests: IdempotencyFilter (duplicate detection, cached response) | 3h | 8.2.3 | Yes with 8.8.3 |
| 8.8.6 | Integration tests: RateLimitFilter (per-actor enforcement, 429 response) | 2h | 8.2.4 | Yes with 8.8.3 |

---

## EPIC 9: Administration & Analytics (SPEC-009)

**Dependencies:** SPEC-001, SPEC-002, SPEC-005, SPEC-007
**Parallelizable with:** Nothing (needs almost all other systems)

### Feature 9.1: Database Schema

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.1.1 | Create Flyway migration for `support_cases`, `alerts`, `metric_snapshots`, `generated_reports` tables | 3h | EPIC 8 | — |
| 9.1.2 | Create JPA entities: `SupportCase`, `Alert`, `MetricSnapshot`, `GeneratedReport` | 3h | 9.1.1 | — |
| 9.1.3 | Create repositories for all admin entities | 2h | 9.1.2 | — |

### Feature 9.2: Support & Context

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.2.1 | Implement `SupportCaseService` — support case creation and tracking | 3h | 9.1.3 | — |
| 9.2.2 | Implement `FatherContextService` — unified father context assembly (profile, conversations, missions, memories, engagement, alerts, safety events) | 5h | EPIC 2-6 services | — |

### Feature 9.3: Monitoring & Alerts

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.3.1 | Implement `OperationalHealthService` — real-time health views (system status, active conversations, delivery rate) | 4h | EPIC 4-6 | — |
| 9.3.2 | Implement `AlertService` — alert lifecycle state machine (TRIGGERED → ACKNOWLEDGED → RESOLVED → CLOSED), escalation | 4h | 9.1.3 | Yes with 9.3.1 |
| 9.3.3 | Implement `AlertEvaluator` — periodic threshold checking (60s): AI latency, error rate, quality, delivery rate, daily coverage, unreviewed safety | 5h | 9.3.2 | — |

### Feature 9.4: Business Analytics

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.4.1 | Implement `BusinessMetricsService` — compute/read business metrics (engagement distribution, completion rates) | 5h | 9.1.3 | — |
| 9.4.2 | Implement `CoachingEffectivenessService` — effectiveness KPIs (quality score against targets) | 4h | 9.4.1 | — |
| 9.4.3 | Implement `CohortAnalysisService` — cohort grouping (by join date, phase, engagement level) + comparison | 4h | 9.4.1 | Yes with 9.4.2 |
| 9.4.4 | Implement metric snapshot scheduled job — daily/weekly/monthly materialized summaries | 4h | 9.4.1 | — |

### Feature 9.5: Reporting

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.5.1 | Implement `ReportGenerator` — on-demand and scheduled report creation (engagement, effectiveness, cost, safety) | 5h | 9.4.1-9.4.3 | — |
| 9.5.2 | Implement `ReportScheduler` — triggers report generation per schedule (daily ops, weekly business, monthly executive) | 3h | 9.5.1 | — |

### Feature 9.6: Data Export & Privacy

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.6.1 | Implement `DataExportService` — GDPR export (father's complete data package) and anonymized analytics export | 4h | EPIC 2-6 | — |
| 9.6.2 | Implement `AnonymizationService` — k-anonymity (k≥5), PII removal for analytics datasets | 4h | 9.6.1 | — |

### Feature 9.7: Search & Audit

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.7.1 | Implement `AdminSearchService` — cross-entity search (father by phone/name, conversations, missions) with role-scoped results | 4h | EPIC 2 | — |
| 9.7.2 | Implement `AuditExplorationService` — query across audit tables by actor/subject/time range | 3h | EPIC 8 audit tables | — |

### Feature 9.8: Event Consumer

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.8.1 | Implement `AdminEventConsumer` — processes outbox events for real-time metric updates (incremental, not polling) | 4h | EPIC 6 outbox | — |

### Feature 9.9: Tests (SPEC-009)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 9.9.1 | Unit tests: AlertEvaluator (threshold crossing, alert lifecycle) | 3h | 9.3.3 | — |
| 9.9.2 | Unit tests: CohortAnalysisService, BusinessMetricsService | 3h | 9.4.1-9.4.3 | Yes with 9.9.1 |
| 9.9.3 | Integration tests: FatherContextService (unified view assembly) | 3h | 9.2.2 | — |
| 9.9.4 | Integration tests: DataExportService (GDPR export completeness) | 3h | 9.6.1 | Yes with 9.9.3 |
| 9.9.5 | Unit tests: AnonymizationService (k-anonymity validation) | 2h | 9.6.2 | Yes with 9.9.1 |

---

## EPIC 10: Production & Operations (SPEC-010)

**Dependencies:** All previous EPICs (cross-cutting)
**Parallelizable with:** Partially — observability can start early; operations needs all systems

### Feature 10.1: Observability Infrastructure

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 10.1.1 | Implement `CorrelationIdFilter` — assigns/propagates request correlation IDs via MDC (UUID per request, preserved across subsystem calls) | 3h | EPIC 1 | — |
| 10.1.2 | Implement `MetricsConfiguration` — custom Micrometer metrics registration (AI latency histograms, delivery rate counters, conversation throughput gauges) | 4h | EPIC 1 | Yes with 10.1.1 |
| 10.1.3 | Implement custom `HealthIndicators` — AI provider health, memory system health, communication channel health | 3h | EPIC 3-5 | — |
| 10.1.4 | Add correlation ID propagation to all subsystem service calls (ensure end-to-end traceability) | 3h | 10.1.1 | — |

### Feature 10.2: Resilience Patterns

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 10.2.1 | Implement `CircuitBreakerConfig` — Resilience4j configuration per external dependency (AI: 5%/100 window, comms: 10%/50 window) | 3h | EPIC 4-5 | — |
| 10.2.2 | Implement `TimeoutConfig` — timeout budgets per operation type (AI call: 10s, delivery: 5s, pipeline total: 30s) | 2h | EPIC 5-6 | Yes with 10.2.1 |
| 10.2.3 | Implement `BackpressureMonitor` — queue depth monitoring (outbox pending count, embedding queue depth) + alert at thresholds | 3h | EPIC 6 outbox | — |

### Feature 10.3: Feature Flags

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 10.3.1 | Create Flyway migration for `feature_flags`, `runtime_config` tables (with pre-seeded flags) | 2h | — | — |
| 10.3.2 | Implement `FeatureFlagService` — flag evaluation (global/percentage/individual), 60s cache refresh, deterministic hash per father_id | 4h | 10.3.1 | — |
| 10.3.3 | Implement `RuntimeConfigService` — tunable config with validation + audit trail | 3h | 10.3.1 | Yes with 10.3.2 |
| 10.3.4 | Integrate feature flags into AI layer (voice, image_analysis, calendar, weather, wearable, RAG, multi-agent) | 3h | 10.3.2, EPIC 5 | — |

### Feature 10.4: Data Retention

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 10.4.1 | Implement `DataRetentionService` — consolidated retention schedule enforced daily during maintenance window | 5h | ALL EPICs | — |
| 10.4.2 | Implement `RetentionJob` — scheduled cleanup per data type (processed_messages: 24h, trigger_history: 30d, delivery_records: 90d, media_assets: 90d, ai_telemetry: 30d metadata: 1y, audit: 2y, etc.) | 4h | 10.4.1 | — |

### Feature 10.5: Backup & Disaster Recovery

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 10.5.1 | Implement `BackupVerificationJob` — periodic backup integrity check (query-based validation that backups are complete) | 3h | — | — |
| 10.5.2 | Document disaster recovery runbook (PostgreSQL WAL archive recovery, procedure steps, RTO/RPO targets) | 4h | — | Yes with 10.5.1 |
| 10.5.3 | Configure PostgreSQL WAL archiving and periodic pg_dump in Docker/deployment config | 3h | — | Yes with 10.5.1 |

### Feature 10.6: Deployment Configuration

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 10.6.1 | Verify all Flyway migrations are backward-compatible (additive only, no renames/drops) | 2h | ALL Flyway migrations | — |
| 10.6.2 | Configure deployment health checks (startup probe, liveness probe, readiness probe with DB check) | 2h | EPIC 1 | — |
| 10.6.3 | Document blue-green deployment procedure and rollback steps | 3h | — | Yes with 10.6.2 |
| 10.6.4 | Configure horizontal scaling parameters (thread pool sizing, connection pool limits, advisory lock behavior under multiple replicas) | 3h | EPIC 6 | — |

### Feature 10.7: Tests (SPEC-010)

| ID | Task | Estimate | Dependencies | Parallel |
|----|------|----------|--------------|----------|
| 10.7.1 | Unit tests: FeatureFlagService (global, percentage, individual, deterministic hash) | 3h | 10.3.2 | — |
| 10.7.2 | Unit tests: DataRetentionService (correct deletion order, partial completion handling) | 3h | 10.4.1 | Yes with 10.7.1 |
| 10.7.3 | Integration tests: CorrelationIdFilter (propagation through full request lifecycle) | 2h | 10.1.1, 10.1.4 | Yes with 10.7.1 |
| 10.7.4 | Integration tests: CircuitBreaker behavior (open/close/half-open transitions) | 3h | 10.2.1 | Yes with 10.7.1 |
| 10.7.5 | Integration tests: BackupVerificationJob (successful verification, failure alerting) | 2h | 10.5.1 | Yes with 10.7.1 |

---

## Summary Statistics

| Epic | Spec | Features | Tasks | Est. Total Hours |
|------|------|----------|-------|-----------------|
| 1 | Production Foundation | 5 | 21 | 42h |
| 2 | Product Domain & Business Logic | 6 | 56 | 133h |
| 3 | Memory & Knowledge System | 7 | 24 | 78h |
| 4 | Communication Channels | 8 | 33 | 94h |
| 5 | AI Architecture Intelligence Layer | 11 | 34 | 134h |
| 6 | Conversation Engine & Orchestration | 9 | 24 | 93h |
| 7 | Scheduling & Automation | 9 | 25 | 73h |
| 8 | Application API | 8 | 32 | 98h |
| 9 | Administration & Analytics | 9 | 22 | 79h |
| 10 | Production & Operations | 7 | 21 | 67h |
| **TOTAL** | | **79 Features** | **292 Tasks** | **~891 hours** |

---

## Parallelization Opportunities

### Phase 1 (Sequential): Foundation
- EPIC 1 → EPIC 2 (strict sequential — everything depends on these)

### Phase 2 (Parallel): Core Subsystems
After EPIC 2 completes, these can execute in parallel:
- **Stream A:** EPIC 3 (Memory) → feeds into EPIC 5 (AI)
- **Stream B:** EPIC 4 (Communication Channels) → independent until EPIC 6

### Phase 3 (Sequential): Orchestration
- EPIC 5 (AI) + EPIC 4 (Comms) + EPIC 3 (Memory) complete → EPIC 6 (Conversation Engine)

### Phase 4 (Parallel): Consumers
After EPIC 6 completes:
- **Stream A:** EPIC 7 (Scheduling)
- **Stream B:** EPIC 8 (Application API)

### Phase 5 (Sequential): Administration
- EPIC 7 + EPIC 8 complete → EPIC 9 (Administration & Analytics)

### Phase 6 (Overlay): Operations
- EPIC 10 (Production & Operations) — starts partially in Phase 1 (observability, feature flags) but completes last (retention, backup verification)

### Critical Path
```
EPIC 1 → EPIC 2 → EPIC 3 → EPIC 5 → EPIC 6 → EPIC 9 → EPIC 10
                 ↘ EPIC 4 ↗
```

**Estimated Critical Path Duration:** ~550 hours (sequential bottleneck)
**With full parallelization and 2 developers:** ~450 hours
**With 3 developers:** ~350 hours

---

## Cross-Cutting Concerns Checklist

| Concern | Covered In |
|---------|------------|
| Database migrations (Flyway) | 2.1.1, 3.1.1-3.1.2, 4.1.1, 5.1.1, 6.1.1, 7.1.1, 8.2.1, 9.1.1, 10.3.1 |
| Error handling (RFC 9457) | 1.2.1-1.2.4, 8.3.3-8.3.4 |
| Authentication / Authorization | 8.1.1-8.1.5 |
| Structured logging (JSON) | 1.3.1-1.3.2, 10.1.1-10.1.4 |
| Health probes | 1.3.3, 10.1.3, 10.6.2 |
| Rate limiting | 8.2.4 (API), 2.5.13-2.5.14 (notifications), 5.3.3 (AI cost) |
| Circuit breakers | 4.7.1, 5.2.4, 10.2.1 |
| Retry / Backoff | 4.4.3, 6.7.2, 7.8.2 |
| Data retention / Cleanup | 10.4.1-10.4.2 |
| Feature flags | 10.3.1-10.3.4 |
| Audit logging | 2.3.1, 3.6.2, 8.2.2, 9.7.2 |
| Idempotency | 6.2.2-6.2.3, 8.2.3 |
| GDPR / Privacy | 3.5.4, 3.6.1, 9.6.1-9.6.2 |
| Backup & DR | 10.5.1-10.5.3 |
| Deployment (Docker) | 1.4.1-1.4.2, 10.6.1-10.6.4 |
| Property-based testing | 2.6.1-2.6.8, 5.11.1-5.11.6 |
| Integration testing | 1.5.2-1.5.6, 3.7.1, 4.8.4, 5.11.7-5.11.8, 6.9.3-6.9.5, 7.9.4-7.9.5, 8.8.3-8.8.6, 9.9.3-9.9.4, 10.7.3-10.7.5 |
| Security (input validation) | 8.6.1, 4.3.1 (signature verification) |
| Observability / Metrics | 5.9.1-5.9.2, 10.1.1-10.1.4 |
| Scheduled jobs | 3.5.1-3.5.4, 4.5.3, 5.9.2, 6.2.3, 6.3.4, 7.5.1, 7.6.1, 9.3.3, 9.4.4, 9.5.2, 10.4.2 |
