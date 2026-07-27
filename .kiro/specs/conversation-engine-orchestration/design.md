# Technical Design — Conversation Engine & Orchestration

## Architecture

### Overview

The Conversation Engine is the central orchestrator of the Dad Coach platform. It receives normalized inbound messages from the Communication Channel (SPEC-006), coordinates all subsystems (AI, Memory, Mission, Father Domain), and produces outbound messages for delivery.

It runs within the Spring Boot monolith (Java 21, PostgreSQL) as a synchronous service layer following the package-by-feature architecture from SPEC-001.

### Architecture Decisions

**AD-1: Single-Process Orchestration** — The Conversation Engine runs within the Spring Boot monolith as a synchronous service layer. No separate process, message broker, or external orchestration engine is required at launch scale (100-1,000 fathers). The pipeline executes as a single transactional unit within one thread per father message.

**AD-2: Database-Backed Session Lock** — The per-father Session_Lock is implemented using PostgreSQL advisory locks (`pg_advisory_xact_lock(father_id_hash)`). This provides mutual exclusion without external infrastructure and automatically releases on transaction completion or timeout.

**AD-3: Transactional Outbox for Side-Effects** — Asynchronous side-effects (memory extraction, event publication, metric updates) are persisted to an outbox table within the same transaction as conversation state changes. A background poller processes the outbox, ensuring at-least-once delivery without requiring an external message broker at launch scale.

**AD-4: Idempotency via Database** — Processed message Idempotency_Keys are stored in a dedicated table with a TTL of 24 hours. Duplicate detection is a simple unique constraint check before pipeline execution.

**AD-5: Fallback-First Error Strategy** — Any failure in the AI generation path results in a Fallback_Response delivered to the father. The system never fails silently. The father always receives a response within the latency budget.

### Pipeline Execution Flow

```
InboundMessageDto arrives (from Communication Channel)
    │
    ▼
┌─ ConversationOrchestrator.processMessage() ─────────────────────┐
│                                                                   │
│  1. Check idempotency (ProcessedMessage table)                    │
│     → duplicate? return cached response                           │
│  2. Acquire Session_Lock (pg_advisory_xact_lock)                  │
│  3. Resolve father (by channel identity)                          │
│     → unknown? create Father, start ONBOARDING                    │
│  4. Check active conversation                                     │
│     → exists + not expired? route to it                           │
│     → exists + expired? transition to EXPIRED, create new         │
│     → none? create new conversation (evaluate type)               │
│  5. Detect mission expiration (if deadline passed)                │
│  6. Load context (ContextAssembler)                               │
│  7. AI Orchestration (AiOrchestrator)                             │
│     → safety → generate → validate → retry/fallback              │
│  8. Process AI follow-up action                                   │
│     → GENERATE_MISSION? → MissionOrchestrator                    │
│     → CLOSE_CONVERSATION? → transition to COMPLETED              │
│  9. Persist conversation state                                    │
│  10. Evaluate completion (8-message cap, objective met)            │
│  11. Schedule side-effects (outbox)                               │
│  12. Record idempotency key                                       │
│  COMMIT TRANSACTION (releases advisory lock)                      │
└───────────────────────────────────────────────────────────────────┘
    │
    ▼
OutboundMessageDto returned to Communication Channel for delivery
```

### Concurrency Model

- **Per-father serialization**: PostgreSQL advisory locks ensure one pipeline execution per father at a time
- **Cross-father parallelism**: Different fathers processed concurrently by separate threads (Spring's thread pool)
- **Lock timeout**: 45 seconds (configurable). If lock cannot be acquired, message queued for retry via outbox
- **Side-effect isolation**: Outbox processing runs in its own thread pool, independent of request threads

```
com.dadcoach.conversation/
├── ConversationOrchestrator.java        # Main pipeline coordinator
├── ConversationService.java             # Conversation CRUD and state management
├── MessageProcessor.java                # Inbound message validation and routing
├── SessionLockService.java              # Per-father advisory lock management
├── context/
│   ├── ContextAssembler.java            # Collects data from all subsystems
│   └── ContextRequest.java              # Value object for context parameters
├── ai/
│   ├── AiOrchestrator.java             # AI call pipeline (safety → generate → validate)
│   ├── ResponseValidator.java           # Schema + business rule validation
│   └── FallbackResponseProvider.java    # Pre-written fallback message retrieval
├── mission/
│   └── MissionOrchestrator.java         # Mission state transitions within conversations
├── memory/
│   └── MemoryOrchestrator.java          # Memory trigger scheduling (extraction, confirmation)
├── sideeffect/
│   ├── SideEffectScheduler.java         # Outbox writer for async work
│   ├── SideEffectProcessor.java         # Background outbox poller and executor
│   └── SideEffect.java                  # Enum of side-effect types
├── event/
│   └── ConversationEventPublisher.java  # Business event emission
├── recovery/
│   └── ConversationRecoveryService.java # Stale conversation detection, reconciliation
├── entity/
│   ├── Conversation.java                # JPA entity
│   ├── ConversationMessage.java         # JPA entity (individual messages)
│   └── ProcessedMessage.java            # Idempotency tracking entity
├── dto/
│   ├── InboundMessageDto.java           # Internal message format (from SPEC-006)
│   └── OutboundMessageDto.java          # Internal message format (to SPEC-006)
├── mapper/
│   └── ConversationMapper.java          # MapStruct mapper
└── repository/
    ├── ConversationRepository.java
    ├── ConversationMessageRepository.java
    ├── ProcessedMessageRepository.java
    └── SideEffectOutboxRepository.java
```

## Components and Interfaces

### Database Schema

#### conversations table
```sql
CREATE TABLE conversations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL REFERENCES fathers(id),
    type                VARCHAR(30) NOT NULL,  -- ONBOARDING, DAILY_COACHING, etc.
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, COMPLETED, EXPIRED, ABANDONED
    message_count       INTEGER NOT NULL DEFAULT 0,
    father_message_count INTEGER NOT NULL DEFAULT 0,
    system_message_count INTEGER NOT NULL DEFAULT 0,
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at     TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    completion_reason   VARCHAR(50),  -- OBJECTIVE_MET, MAX_MESSAGES, PREEMPTED, EXPIRATION, ABANDONED
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED','ABANDONED'))
);

CREATE INDEX idx_conversations_father_active ON conversations(father_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_conversations_expires ON conversations(expires_at) WHERE status = 'ACTIVE';
```

### conversation_messages table
```sql
CREATE TABLE conversation_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    direction       VARCHAR(10) NOT NULL,  -- INBOUND, OUTBOUND
    content         TEXT NOT NULL,
    message_type    VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    metadata        JSONB,  -- model_used, latency_ms, fallback_used, memories_injected, etc.
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sequence_number INTEGER NOT NULL
);

CREATE INDEX idx_conv_messages_conversation ON conversation_messages(conversation_id, sequence_number);
```

### processed_messages table (idempotency)
```sql
CREATE TABLE processed_messages (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    father_id       UUID NOT NULL,
    response_id     UUID,  -- reference to the outbound message produced
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL  -- processed_at + 24 hours
);

CREATE INDEX idx_processed_messages_expires ON processed_messages(expires_at);
```

### side_effect_outbox table
```sql
CREATE TABLE side_effect_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL,
    conversation_id UUID,
    effect_type     VARCHAR(50) NOT NULL,  -- MEMORY_EXTRACTION, EVENT_PUBLISH, METRIC_UPDATE, etc.
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    retry_count     INTEGER NOT NULL DEFAULT 0,
    max_retries     INTEGER NOT NULL DEFAULT 3,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_retry_at   TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_detail    TEXT
);

CREATE INDEX idx_outbox_pending ON side_effect_outbox(status, next_retry_at) WHERE status IN ('PENDING','FAILED');
```

## Data Models

### Component Design

### ConversationOrchestrator (Main Pipeline)

The central class implementing the 16-step pipeline from SPEC-005 Requirement 3.

```java
@Service
@RequiredArgsConstructor
public class ConversationOrchestrator {

    private final SessionLockService sessionLock;
    private final MessageProcessor messageProcessor;
    private final ConversationService conversationService;
    private final ContextAssembler contextAssembler;
    private final AiOrchestrator aiOrchestrator;
    private final MissionOrchestrator missionOrchestrator;
    private final MemoryOrchestrator memoryOrchestrator;
    private final SideEffectScheduler sideEffectScheduler;
    private final ConversationEventPublisher eventPublisher;
    private final FallbackResponseProvider fallbackProvider;

    /**
     * Main entry point. Called by Communication Channel adapter
     * after message normalization.
     */
    @Transactional
    public OutboundMessageDto processMessage(InboundMessageDto message) {
        // Step 1: Acquire lock (or queue if locked)
        // Step 2-7: Load context
        // Step 8: Assemble context
        // Step 9-12: AI orchestration + validation
        // Step 13: Persist state
        // Step 14: Produce outbound message
        // Step 15: Release lock (implicit via transaction commit)
        // Step 16: Schedule side-effects (written to outbox within same tx)
    }
}
```

### SessionLockService

```java
@Service
@RequiredArgsConstructor
public class SessionLockService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Acquires a PostgreSQL advisory lock scoped to the transaction.
     * Blocks if another transaction holds the lock for this father.
     * Automatically released on transaction commit/rollback.
     * Timeout: 45 seconds (configurable).
     */
    public void acquireLock(UUID fatherId) {
        long lockKey = fatherId.getMostSignificantBits();
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");
    }
}
```

### AiOrchestrator

Implements the AI sub-pipeline from SPEC-005 Requirement 5.

```java
@Service
@RequiredArgsConstructor
public class AiOrchestrator {

    private final SafetyClassifier safetyClassifier;       // SPEC-003 Req 9
    private final IntelligenceLayer intelligenceLayer;      // SPEC-003 interface
    private final ResponseValidator responseValidator;
    private final FallbackResponseProvider fallbackProvider;

    /**
     * Executes: safety classify → generate → validate → retry/fallback
     * Returns a validated coaching response or fallback.
     * Never throws — always produces a deliverable response.
     */
    public AiResult orchestrate(ConversationContext context, InboundMessageDto message) {
        // 1. Safety classification
        SafetyClassification safety = safetyClassifier.classify(message);
        if (safety.isEscalation()) {
            return AiResult.safetyResponse(safety);
        }

        // 2. Generate response
        try {
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);
            
            // 3. Validate
            ValidationResult validation = responseValidator.validate(response, context);
            if (validation.passed()) {
                return AiResult.success(response);
            }

            // 4. Retry once with correction
            CoachingResponse retry = intelligenceLayer.retryWithCorrection(context, validation.failures());
            ValidationResult retryValidation = responseValidator.validate(retry, context);
            if (retryValidation.passed()) {
                return AiResult.success(retry, /* retried */ true);
            }

            // 5. Fallback
            return AiResult.fallback(fallbackProvider.getForType(context.conversationType()));
        } catch (AiProviderException e) {
            return AiResult.fallback(fallbackProvider.getForType(context.conversationType()));
        }
    }
}
```

### SideEffectScheduler + Processor

```java
@Service
@RequiredArgsConstructor
public class SideEffectScheduler {

    private final SideEffectOutboxRepository outboxRepository;

    /**
     * Called within the main transaction to schedule async work.
     * Persists to outbox table — guaranteed to be committed with conversation state.
     */
    public void schedule(SideEffect type, UUID fatherId, UUID conversationId, Map<String, Object> payload) {
        var entry = new SideEffectOutboxEntity();
        entry.setEffectType(type.name());
        entry.setFatherId(fatherId);
        entry.setConversationId(conversationId);
        entry.setPayload(payload);
        entry.setStatus("PENDING");
        entry.setMaxRetries(type.isMandatory() ? Integer.MAX_VALUE : 3);
        outboxRepository.save(entry);
    }
}

@Component
@RequiredArgsConstructor
public class SideEffectProcessor {

    private final SideEffectOutboxRepository outboxRepository;
    // ... injected processors per type

    /**
     * Polls outbox every 5 seconds for PENDING entries.
     * Processes each, marks COMPLETED or increments retry.
     */
    @Scheduled(fixedDelay = 5000)
    public void poll() {
        List<SideEffectOutboxEntity> pending = outboxRepository.findPending(Limit.of(20));
        for (var entry : pending) {
            try {
                dispatch(entry);
                entry.setStatus("COMPLETED");
                entry.setCompletedAt(Instant.now());
            } catch (Exception e) {
                entry.setRetryCount(entry.getRetryCount() + 1);
                entry.setNextRetryAt(computeBackoff(entry.getRetryCount()));
                if (entry.getRetryCount() >= entry.getMaxRetries()) {
                    entry.setStatus("FAILED");
                    entry.setErrorDetail(e.getMessage());
                }
            }
            outboxRepository.save(entry);
        }
    }
}
```

### ConversationRecoveryService

```java
@Component
@RequiredArgsConstructor
public class ConversationRecoveryService {

    private final ConversationRepository conversationRepository;

    /**
     * Runs on application startup and periodically (every 15 minutes).
     * Detects stale ACTIVE conversations past their expiration.
     */
    @Scheduled(fixedRate = 900_000) // 15 minutes
    public void detectStaleConversations() {
        List<Conversation> stale = conversationRepository
            .findByStatusAndExpiresAtBefore("ACTIVE", Instant.now());
        
        for (var conversation : stale) {
            conversation.setStatus("EXPIRED");
            conversation.setCompletedAt(Instant.now());
            conversation.setCompletionReason("EXPIRATION");
            conversationRepository.save(conversation);
            // Schedule memory extraction if 2+ father messages
            // Publish CONVERSATION_EXPIRED event
        }
    }
}
```

## Pipeline Execution Flow

```
InboundMessageDto arrives (from Communication Channel)
    │
    ▼
┌─ ConversationOrchestrator.processMessage() ─────────────────────┐
│                                                                   │
│  1. Check idempotency (ProcessedMessage table)                    │
│     → duplicate? return cached response                           │
│                                                                   │
│  2. Acquire Session_Lock (pg_advisory_xact_lock)                  │
│                                                                   │
│  3. Resolve father (by channel identity)                          │
│     → unknown? create Father, start ONBOARDING                    │
│                                                                   │
│  4. Check active conversation                                     │
│     → exists + not expired? route to it                           │
│     → exists + expired? transition to EXPIRED, create new         │
│     → none? create new conversation (evaluate type)               │
│                                                                   │
│  5. Detect mission expiration (if deadline passed)                │
│                                                                   │
│  6. Load context (ContextAssembler)                               │
│     → father profile, children, goals, missions, memories         │
│                                                                   │
│  7. AI Orchestration (AiOrchestrator)                             │
│     → safety → generate → validate → retry/fallback              │
│                                                                   │
│  8. Process AI follow-up action                                   │
│     → GENERATE_MISSION? → MissionOrchestrator                    │
│     → CLOSE_CONVERSATION? → transition to COMPLETED              │
│                                                                   │
│  9. Persist conversation state                                    │
│     → save inbound message, outbound message, update counters     │
│                                                                   │
│  10. Evaluate completion                                          │
│      → 8 outbound messages reached? → COMPLETED                  │
│      → objective met? → COMPLETED                                │
│                                                                   │
│  11. Schedule side-effects (outbox)                               │
│      → memory injection tracking                                  │
│      → event publication (CONVERSATION_COMPLETED, etc.)           │
│      → memory extraction (if completed)                           │
│                                                                   │
│  12. Record idempotency key                                       │
│                                                                   │
│  COMMIT TRANSACTION (releases advisory lock)                      │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
    │
    ▼
OutboundMessageDto returned to Communication Channel for delivery
```

## Concurrency Model

- **Per-father serialization**: PostgreSQL advisory locks ensure one pipeline execution per father at a time
- **Cross-father parallelism**: Different fathers are processed concurrently by separate threads (Spring's thread pool)
- **Lock timeout**: 45 seconds. If lock cannot be acquired within this time, the message is queued for retry (exponential backoff via the outbox)
- **Side-effect isolation**: Outbox processing runs in its own thread pool, independent of the main request-handling threads

## Error Handling

| Failure Point | Recovery |
|--------------|----------|
| Before AI generation (context load fails) | Deliver fallback, persist father message, schedule deferred regeneration |
| AI generation fails | Fallback_Response delivered immediately |
| State persistence fails | Retry persistence once; if fails, deliver response anyway + log alert |
| After persistence, delivery fails | Message queued for redelivery via outbox; state is already consistent |
| System restart with pending outbox | Outbox poller resumes processing on startup |
| Stale conversations (missed expiration) | Recovery service detects and transitions every 15 minutes |

## Configuration

```yaml
conversation:
  session-lock:
    timeout-seconds: 45
  pipeline:
    max-outbound-messages: 8
    latency-budget-ms: 30000
  idempotency:
    ttl-hours: 24
  side-effects:
    poll-interval-ms: 5000
    batch-size: 20
    max-retries-best-effort: 3
  recovery:
    stale-check-interval-ms: 900000  # 15 minutes
  expiration-windows:
    ONBOARDING: PT48H
    DAILY_COACHING: PT24H
    FOLLOW_UP: PT24H
    REFLECTION: PT24H
    INACTIVITY_CHECK: PT48H
    CELEBRATION: PT24H
    DIFFICULT_SITUATION: null  # no expiration
  cooldowns:
    after-expired: PT24H
    after-abandoned: PT48H
    after-completed: PT0S
```

## Interfaces with Other Subsystems

| Subsystem | Interface | Direction |
|-----------|-----------|-----------|
| Communication Channel (SPEC-006) | `InboundMessageDto` / `OutboundMessageDto` | Inbound → Engine, Engine → Outbound |
| Intelligence Layer (SPEC-003) | `IntelligenceLayer` interface (`generateCoachingResponse`, `classifyMessage`) | Engine → AI |
| Memory System (SPEC-004) | `MemoryService` interface (`retrieveRanked`, `triggerExtraction`, `recordAccess`) | Engine → Memory |
| Father Domain (SPEC-002) | `FatherService`, `ChildService`, `GoalService`, `MissionService` | Engine → Domain (read + write) |
| Scheduling System (SPEC-008) | `AutomationTriggerDto` consumed via the same `processMessage` entry point | Scheduler → Engine |
| Event Consumers (SPEC-009) | Business events published to outbox → polled by consumers | Engine → Consumers |

## Testing Strategy

| Level | Scope | Tools |
|-------|-------|-------|
| Unit | ConversationOrchestrator, AiOrchestrator, ResponseValidator, each service in isolation | JUnit 5, Mockito |
| Integration | Full pipeline with real PostgreSQL (advisory locks, transactions, outbox) | Testcontainers, Spring Boot Test |
| Contract | IntelligenceLayer interface mocked with known responses; validate pipeline behavior | Mockito, assertions on state |
| Recovery | Simulate failures at each recovery point; verify correct fallback/reconciliation | Testcontainers, fault injection |

## Migration Script

```sql
-- V2__conversation_engine.sql

CREATE TABLE conversations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL REFERENCES fathers(id),
    type                VARCHAR(30) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    message_count       INTEGER NOT NULL DEFAULT 0,
    father_message_count INTEGER NOT NULL DEFAULT 0,
    system_message_count INTEGER NOT NULL DEFAULT 0,
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at     TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    completion_reason   VARCHAR(50),
    CONSTRAINT chk_conv_status CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED','ABANDONED')),
    CONSTRAINT chk_conv_type CHECK (type IN ('ONBOARDING','DAILY_COACHING','FOLLOW_UP','REFLECTION','INACTIVITY_CHECK','CELEBRATION','DIFFICULT_SITUATION'))
);

CREATE INDEX idx_conversations_father_active ON conversations(father_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_conversations_expires ON conversations(expires_at) WHERE status = 'ACTIVE';

CREATE TABLE conversation_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    direction       VARCHAR(10) NOT NULL,
    content         TEXT NOT NULL,
    message_type    VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sequence_number INTEGER NOT NULL,
    CONSTRAINT chk_msg_direction CHECK (direction IN ('INBOUND','OUTBOUND'))
);

CREATE INDEX idx_conv_messages_conversation ON conversation_messages(conversation_id, sequence_number);

CREATE TABLE processed_messages (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    father_id       UUID NOT NULL,
    response_id     UUID,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_processed_messages_expires ON processed_messages(expires_at);

CREATE TABLE side_effect_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL,
    conversation_id UUID,
    effect_type     VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER NOT NULL DEFAULT 0,
    max_retries     INTEGER NOT NULL DEFAULT 3,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_retry_at   TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_detail    TEXT,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
);

CREATE INDEX idx_outbox_pending ON side_effect_outbox(status, next_retry_at) WHERE status IN ('PENDING','FAILED');
```

## Requirement Traceability

| Requirement | Design Element |
|-------------|---------------|
| Req 1: Conversation Lifecycle | `ConversationService` + `Conversation` entity + status constraints |
| Req 2: Message Lifecycle | `MessageProcessor` + `ProcessedMessage` table + validation logic |
| Req 3: Orchestration Pipeline | `ConversationOrchestrator.processMessage()` — 16 steps |
| Req 4: Context Construction | `ContextAssembler` — delegates to subsystem services |
| Req 5: AI Orchestration | `AiOrchestrator` — safety → generate → validate → retry → fallback |
| Req 6: Mission Orchestration | `MissionOrchestrator` — validates and persists mission state changes |
| Req 7: Memory Orchestration | `MemoryOrchestrator` + outbox entries for async extraction |
| Req 8: Conversation Recovery | `ConversationRecoveryService` + outbox retry mechanism |
| Req 9: Concurrency | `SessionLockService` (pg_advisory_xact_lock) + single-tx pipeline |
| Req 10: Business Timing | Expiration timestamps on `conversations` + periodic recovery check |
| Req 11: Event Publication | `ConversationEventPublisher` + outbox (mandatory side-effect) |
| Req 12: Operational Rules | Configuration-driven limits + outbox for async/sync boundary |
| Req 13: Edge Cases | Fallback paths in `AiOrchestrator` + recovery service + idempotency |
| Req 14: Cross-Spec Compat | Interface contracts + delegation to subsystem services (no internal logic) |
