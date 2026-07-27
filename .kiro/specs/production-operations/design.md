# Technical Design — Production & Operations

## Architecture

### Overview

The Production & Operations design defines how the non-functional requirements from SPEC-010 are achieved within the Dad Coach platform. It covers deployment strategy, observability infrastructure, backup mechanisms, resilience patterns, capacity management, and operational procedures. This design applies cross-cutting concerns across all subsystem packages.

### Architecture Decisions

**AD-1: Containerized Single-Artifact Deployment** — The Spring Boot application is packaged as a single container image (per SPEC-001 Dockerfile). Deployed as one or more replicas behind a load balancer. Horizontal scaling is achieved by adding replicas.

**AD-2: PostgreSQL as Single Data Store** — At launch scale (100-1,000 fathers), PostgreSQL serves all persistence needs: domain data, conversation state, memories (with pgvector), outbox, audit logs, metrics. No additional data stores required until Growth scale.

**AD-3: Application-Level Observability** — Metrics exposed via Micrometer (Spring Boot default), logs in structured JSON (per SPEC-001 Req 8), distributed tracing via correlation IDs propagated through all subsystems in MDC (Mapped Diagnostic Context).

**AD-4: Feature Flags via Database Configuration** — Feature flags stored in a configuration table, cached in-memory with 60-second refresh. No external feature flag service required at launch scale. Evaluation is deterministic per father_id (hash-based percentage).

**AD-5: Blue-Green Deployment Compatibility** — The application is designed for zero-downtime deployment: database migrations are always backward-compatible (additive), in-flight conversations survive across deployments (state is in PostgreSQL, not in-memory), and advisory locks are transaction-scoped (auto-release on process termination).

### Package Structure (Cross-Cutting)

```
com.dadcoach.common/
├── observability/
│   ├── CorrelationIdFilter.java       # Assigns/propagates request correlation IDs
│   ├── MetricsConfiguration.java      # Custom Micrometer metrics registration
│   └── HealthIndicators.java          # Custom health checks (AI, memory, comms)
├── resilience/
│   ├── CircuitBreakerConfig.java      # Resilience4j config per external dependency
│   ├── TimeoutConfig.java             # Timeout budgets per operation type
│   └── BackpressureMonitor.java       # Queue depth monitoring + alerting
├── config/
│   ├── FeatureFlagService.java        # Flag evaluation (global/percentage/individual)
│   ├── FeatureFlag.java               # JPA entity
│   ├── RuntimeConfigService.java      # Tunable config with validation + audit
│   └── RuntimeConfig.java             # JPA entity
├── retention/
│   ├── DataRetentionService.java      # Enforces consolidated retention schedule
│   └── RetentionJob.java             # Scheduled cleanup per data type
└── backup/
    └── BackupVerificationJob.java     # Periodic backup integrity check (query-based)
```

## Components and Interfaces

### FeatureFlagService

```java
@Service
public class FeatureFlagService {
    private final FeatureFlagRepository repository;
    private volatile Map<String, FeatureFlag> cache;  // Refreshed every 60s
    
    public boolean isEnabled(String flagName, UUID fatherId) {
        FeatureFlag flag = cache.get(flagName);
        if (flag == null || !flag.isEnabled()) return false;
        
        // Global enablement
        if (flag.getPercentage() == 100) return true;
        
        // Individual override
        if (flag.getIndividualIds().contains(fatherId)) return true;
        
        // Percentage-based (deterministic hash)
        int bucket = Math.abs(fatherId.hashCode() % 100);
        return bucket < flag.getPercentage();
    }
    
    @Scheduled(fixedRate = 60000)
    public void refreshCache() {
        cache = repository.findAll().stream()
            .collect(Collectors.toMap(FeatureFlag::getName, Function.identity()));
    }
}
```

### DataRetentionService

```java
@Service
public class DataRetentionService {
    // Implements the consolidated retention schedule from SPEC-010 Req 12
    // Runs during maintenance window (configurable, default 02:00-05:00 UTC)
    
    @Scheduled(cron = "${retention.schedule:0 0 2 * * *}")
    public void enforceRetention() {
        // Order matters: delete dependent records first
        deleteExpiredProcessedMessages();      // 24h TTL
        deleteExpiredScheduleTriggerHistory(); // 30 days
        deleteExpiredDeliveryRecords();        // 90 days
        deleteExpiredMediaAssets();            // 90 days
        deleteExpiredAiTelemetryFull();        // 30 days (metadata kept for 1 year)
        deleteExpiredAiTelemetryMeta();        // 1 year
        deleteExpiredAuditEntries();           // 2 years
        deleteExpiredSafetyRecords();          // 30-90 days per type
        deleteExpiredIdempotencyKeys();        // 24h
        deleteExpiredArchivedMemories();       // 1 year (never reactivated)
    }
}
```

### CircuitBreakerConfig

```java
@Configuration
public class CircuitBreakerConfig {
    // Resilience4j circuit breakers per SPEC-010 Req 11
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        var aiConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(5)          // 5% failure → open
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .slidingWindowSize(100)
            .build();
            
        var commsConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(10)         // per SPEC-006 Req 7 criteria 8
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .slidingWindowSize(50)
            .build();
            
        return CircuitBreakerRegistry.of(Map.of(
            "ai-provider", aiConfig,
            "communication-provider", commsConfig
        ));
    }
}
```

## Data Models

### Operational Tables

```sql
CREATE TABLE feature_flags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    percentage      INTEGER NOT NULL DEFAULT 0 CHECK (percentage BETWEEN 0 AND 100),
    individual_ids  UUID[] DEFAULT '{}',
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE runtime_config (
    key             VARCHAR(100) PRIMARY KEY,
    value           TEXT NOT NULL,
    value_type      VARCHAR(20) NOT NULL,  -- STRING, INTEGER, BOOLEAN, DURATION
    description     TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID
);

-- Pre-seed feature flags per SPEC-003 Req 13 criteria 10
INSERT INTO feature_flags (name, enabled, percentage, description) VALUES
    ('voice_coaching_enabled', FALSE, 0, 'Voice coaching via STT/TTS'),
    ('image_analysis_enabled', FALSE, 0, 'Image understanding via multimodal models'),
    ('calendar_integration_enabled', FALSE, 0, 'Calendar-aware mission planning'),
    ('weather_aware_enabled', FALSE, 0, 'Weather-aware mission suggestions'),
    ('wearable_integration_enabled', FALSE, 0, 'Wearable health data integration'),
    ('rag_knowledge_base_enabled', FALSE, 0, 'RAG coaching knowledge base'),
    ('multi_agent_enabled', FALSE, 0, 'Multi-agent complex reasoning');
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| Backup verification fails | CRITICAL alert; schedule immediate re-backup; notify SUPER_ADMIN |
| Retention job exceeds maintenance window | Pause; resume next window; log partial completion |
| Feature flag cache refresh fails | Use stale cache (last known values); alert after 5 consecutive failures |
| Circuit breaker opens | Fallback behavior activates (per subsystem design); alert operations |
| Deployment health check fails | Rollback initiated automatically; alert engineering |
| Capacity utilization > 70% | WARNING alert; growth projection report generated |

## Correctness Properties

- Feature flag evaluation is DETERMINISTIC per father_id — same father always gets same result for same flag state
- Zero-downtime deployments: all migrations are additive; no column renames or type changes without a migration bridge
- Data retention is ENFORCED (not advisory) — expired data is deleted within 7 days of retention period end
- Circuit breakers prevent cascading failures — each external dependency has independent failure isolation
- Correlation IDs propagate across ALL subsystems — end-to-end tracing from webhook to delivery
- Backup integrity is VERIFIED (not assumed) — quarterly restore tests confirm recoverability
- Configuration changes are VALIDATED before application — invalid values never reach production behavior

## Requirement Traceability

| Requirement | Design Element |
|-------------|---------------|
| Req 2: Availability | Multiple replicas + health checks + auto-restart on failure |
| Req 3: Reliability | Transactional outbox (at-least-once), idempotency, advisory locks |
| Req 4: Scalability | Horizontal scaling via replicas; per-father independence (no shared state) |
| Req 5: Performance | Timeout budgets in `TimeoutConfig`; Micrometer latency histograms |
| Req 6: Configuration | `RuntimeConfigService` + `FeatureFlagService` (runtime tunable, audited) |
| Req 7: Feature Flags | `FeatureFlagService` — global/percentage/individual; 60s cache; deterministic |
| Req 8: Maintenance | Scheduled jobs in maintenance window; message queueing during maintenance |
| Req 9: Backup | PostgreSQL WAL archiving + periodic pg_dump; `BackupVerificationJob` |
| Req 10: Disaster Recovery | Documented runbooks; recovery from WAL archive; tested quarterly |
| Req 11: Resilience | Resilience4j circuit breakers + timeout budgets + graceful degradation |
| Req 12: Retention | `DataRetentionService` — consolidated schedule, enforced daily |
| Req 13: Compliance | Privacy by design across all subsystems; GDPR flows in domain services |
| Req 14: Observability | Micrometer metrics + JSON logs + correlation IDs in MDC |
| Req 15: Capacity | `BackpressureMonitor` + capacity utilization metrics + alerts at 70% |
| Req 16: Deployment | Blue-green ready; additive migrations; rollback within 5 min |
| Req 17: Business Continuity | Streak tolerance via daily recalc; outbox guarantees no message loss |
| Req 18: Cross-Spec | All subsystem latency budgets monitored; retention schedule is authoritative |
