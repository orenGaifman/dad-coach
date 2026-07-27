# Technical Design — Memory & Knowledge System

## Architecture

### Overview

The Memory System implements long-term contextual knowledge storage for Dad Coach per SPEC-004. It manages memory creation, lifecycle (ACTIVE → CONFIRMED → SUPERSEDED/ARCHIVED/EXPIRED/DELETED), retrieval with composite scoring, duplicate detection via vector similarity, periodic consolidation, and privacy-compliant deletion.

Built within the Spring Boot monolith using PostgreSQL with pgvector for embedding similarity search.

### Architecture Decisions

**AD-1: pgvector for Embeddings** — Memory embeddings are stored as `vector(1536)` columns in PostgreSQL using the pgvector extension. This keeps the architecture simple (single database) while supporting cosine similarity search at the 500-memories-per-father scale.

**AD-2: Extraction as Async Side-Effect** — Memory extraction is triggered by the Conversation Engine's outbox (SPEC-005 design). The Memory System processes extraction requests asynchronously, never blocking conversation responses.

**AD-3: Scheduled Jobs via Spring @Scheduled** — Decay evaluation (daily) and consolidation (weekly) run as Spring scheduled tasks during the configured maintenance window. They process fathers in batches to avoid lock contention.

**AD-4: Soft-Delete with Deferred Erasure** — Memories transition to DELETED state immediately but content erasure (field nullification, embedding deletion, version history purge) is performed by a background job within 72 hours.

**AD-5: Confidence Arithmetic in Application Layer** — All confidence/importance score changes are computed and validated in Java (not database triggers), ensuring the AI-recommendation validation boundary is maintained per SPEC-004 Req 25.

### Package Structure

```
com.dadcoach.memory/
├── MemoryService.java                  # Public interface for retrieval + lifecycle
├── MemoryRepository.java               # JPA + native queries for vector search
├── Memory.java                         # JPA entity
├── MemoryVersion.java                  # JPA entity (version history)
├── extraction/
│   ├── MemoryExtractionService.java    # Processes extraction requests from outbox
│   ├── ExtractionValidator.java        # Validates AI extraction recommendations
│   └── DuplicateDetector.java          # Semantic similarity check before creation
├── lifecycle/
│   ├── MemoryDecayService.java         # Daily confidence decay job
│   ├── MemoryConsolidationService.java # Weekly merge job
│   ├── MemoryExpirationService.java    # Transitions expired memories
│   └── MemoryDeletionService.java      # 72-hour content erasure job
├── retrieval/
│   ├── MemoryRetriever.java            # Ranked retrieval with composite scoring
│   ├── CompositeScoreCalculator.java   # Formula: importance×0.5 + recency×0.3 + relevance×0.2
│   └── RetrievalMetadata.java          # Rich metadata per result (Req 19)
├── conflict/
│   └── ConflictDetector.java           # Contradiction detection + conflict group management
├── sensitive/
│   └── SensitiveMemoryService.java     # Safety-event records (separate from normal memories)
├── audit/
│   ├── MemoryAuditService.java         # Append-only audit log
│   └── MemoryAuditEntry.java           # JPA entity
├── dto/
│   ├── MemoryDto.java
│   └── RetrievalResultDto.java
└── embedding/
    ├── EmbeddingService.java           # Generates embeddings via AI provider
    └── EmbeddingQueue.java             # Retry queue for failed embeddings
```

## Components and Interfaces

### MemoryService (Public Interface)

```java
public interface MemoryService {
    List<RetrievalResultDto> retrieveRanked(UUID fatherId, String topic, UUID childId, int maxCount);
    void triggerExtraction(UUID conversationId, UUID fatherId, String transcript);
    void recordInjection(List<UUID> memoryIds, UUID conversationId);
    void recordReference(List<UUID> memoryIds, UUID conversationId);
    void confirmMemory(UUID memoryId);
    void supersedeMemory(UUID oldMemoryId, String newContent, double newConfidence);
    void deleteMemory(UUID memoryId, String reason);
    void deleteAllForFather(UUID fatherId);  // GDPR erasure
    MemoryCapacityDto getCapacity(UUID fatherId);
}
```

### Composite Score Calculator

```java
@Component
public class CompositeScoreCalculator {
    // Formula from SPEC-004 Req 16 criteria 2:
    // (importance/10 × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)
    // recency_factor = max(0, 1.0 - (days_since_last_access × 0.05))
    
    public double calculate(Memory memory, float cosineSimilarity) {
        double importance = memory.getImportanceScore() / 10.0;
        long daysSinceAccess = ChronoUnit.DAYS.between(memory.getLastAccessedAt(), Instant.now());
        double recency = Math.max(0, 1.0 - (daysSinceAccess * 0.05));
        double relevance = cosineSimilarity;  // 0-1 from pgvector
        return (importance * 0.5) + (recency * 0.3) + (relevance * 0.2);
    }
}
```

### DuplicateDetector

```java
@Service
public class DuplicateDetector {
    // SPEC-004 Req 9: similarity > 0.85 = duplicate, 0.70-0.85 = potential update
    public DuplicateResult check(UUID fatherId, String category, String subjectType, float[] embedding) {
        var similar = memoryRepository.findBySimilarity(fatherId, category, subjectType, embedding, 0.70);
        if (similar.isEmpty()) return DuplicateResult.DISTINCT;
        if (similar.get(0).similarity() > 0.85) return DuplicateResult.DUPLICATE;
        return DuplicateResult.POTENTIAL_UPDATE;
    }
}
```

## Data Models

### Core Memory Table

```sql
-- Requires: CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE memories (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id               UUID NOT NULL REFERENCES fathers(id),
    child_id                UUID REFERENCES children(id),
    category                VARCHAR(30) NOT NULL,
    subject_type            VARCHAR(10) NOT NULL,  -- FATHER, CHILD, FAMILY
    content                 TEXT NOT NULL CHECK (length(content) <= 500),
    importance_score        INTEGER NOT NULL CHECK (importance_score BETWEEN 1 AND 10),
    confidence_score        NUMERIC(3,2) NOT NULL CHECK (confidence_score BETWEEN 0.0 AND 1.0),
    state                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_type             VARCHAR(30) NOT NULL,
    source_conversation_id  UUID,
    superseded_by           UUID REFERENCES memories(id),
    conflict_group_id       UUID,
    goal_id                 UUID,
    event_date              DATE,
    event_end_date          DATE,
    is_recurring            BOOLEAN DEFAULT FALSE,
    embedding               vector(1536),
    confirmation_count      INTEGER NOT NULL DEFAULT 0,
    access_count            INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_confirmed_at       TIMESTAMPTZ,
    last_accessed_at        TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ
);

CREATE INDEX idx_memories_father_state ON memories(father_id, state);
CREATE INDEX idx_memories_father_category ON memories(father_id, category, state);
CREATE INDEX idx_memories_father_child ON memories(father_id, subject_type, child_id, state);
CREATE INDEX idx_memories_expires ON memories(expires_at) WHERE state = 'ACTIVE';
CREATE INDEX idx_memories_embedding ON memories USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);

CREATE TABLE memory_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_id       UUID NOT NULL REFERENCES memories(id) ON DELETE CASCADE,
    version_number  INTEGER NOT NULL,
    content         TEXT NOT NULL,
    confidence      NUMERIC(3,2) NOT NULL,
    importance      INTEGER NOT NULL,
    changed_at      TIMESTAMPTZ NOT NULL,
    change_reason   VARCHAR(30) NOT NULL,
    UNIQUE(memory_id, version_number)
);

CREATE TABLE memory_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_id       UUID NOT NULL,
    father_id       UUID NOT NULL,
    operation_type  VARCHAR(30) NOT NULL,
    from_state      VARCHAR(20),
    to_state        VARCHAR(20),
    trigger_type    VARCHAR(30) NOT NULL,
    triggered_by    VARCHAR(50) NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_memory_audit_father ON memory_audit_log(father_id, created_at DESC);

CREATE TABLE safety_event_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL REFERENCES fathers(id),
    event_type      VARCHAR(30) NOT NULL,
    summary         VARCHAR(100) NOT NULL,
    requires_review BOOLEAN NOT NULL DEFAULT TRUE,
    reviewed_by     UUID,
    reviewed_at     TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Vector Similarity Query

```sql
-- Retrieval query: top N by composite score with diversity constraint
SELECT m.*, 1 - (m.embedding <=> :query_embedding) as cosine_similarity
FROM memories m
WHERE m.father_id = :father_id
  AND m.state IN ('ACTIVE', 'CONFIRMED')
  AND m.confidence_score >= 0.3
ORDER BY cosine_similarity DESC
LIMIT :max_candidates;
-- Application layer applies composite scoring + diversity after fetch
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| Embedding generation fails | Store memory without embedding; queue retry (3 attempts / 24h); exclude from similarity search until embedded |
| Extraction AI produces invalid output | Discard invalid memory; continue processing remaining; log for quality review |
| Duplicate detection unavailable (pgvector down) | Create memory without check; flag for deferred detection in next consolidation |
| 500-memory capacity reached | Archive lowest-scoring memory; if all protected → reject + alert operations |
| GDPR deletion request | Immediate state transition; 72-hour background erasure of content + embeddings + versions |
| Consolidation encounters race condition | Skip memory that changed state since job start; log and continue |

## Correctness Properties

- Memories are NEVER created directly by AI — the `ExtractionValidator` validates every recommendation before persistence
- Confidence only increases from explicit evidence sources (never from system usage alone per SPEC-004 Req 5 criteria 2)
- Content erasure (deletion) removes: content field, embedding, all version history snapshots — within 72 hours
- Audit log is append-only and written synchronously with memory operations (rollback on audit failure)
- Domain entity data (names, birthdays, phone) is NEVER stored as a memory (validated at creation time)
- The 500-memory limit is enforced at the database level as a pre-insert check within a transaction

## Requirement Traceability

| Requirement | Design Element |
|-------------|---------------|
| Req 1: Categories | `category` enum on Memory entity; validated at creation |
| Req 2: Lifecycle States | `state` field + transition validation in MemoryService |
| Req 3: Creation Rules | `MemoryExtractionService` + `ExtractionValidator` |
| Req 4-5: Scoring | Application-layer arithmetic in MemoryService methods |
| Req 6: Decay/Aging | `MemoryDecayService` (daily scheduled job) |
| Req 7: Conflicts | `ConflictDetector` + conflict_group_id field |
| Req 8: Consolidation | `MemoryConsolidationService` (weekly scheduled job) |
| Req 9: Duplicate Detection | `DuplicateDetector` + pgvector cosine similarity |
| Req 10: Confirmation | `confirmMemory()` + confirmation_count tracking |
| Req 14: Summaries | Consolidation job creates weekly/monthly summaries |
| Req 15: Capacity | Pre-insert count check + archival scoring |
| Req 16: Retrieval | `MemoryRetriever` + `CompositeScoreCalculator` + diversity filter |
| Req 17: Privacy | `MemoryDeletionService` (72h erasure) + data isolation by father_id |
| Req 18: Audit | `MemoryAuditService` + `memory_audit_log` table |
| Req 24: Sensitive | `SensitiveMemoryService` + `safety_event_records` table (separate store) |
| Req 25: AI Boundaries | `ExtractionValidator` — all AI output validated before persistence |
