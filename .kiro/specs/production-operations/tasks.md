# Tasks — Production & Operations

## Task Dependency Graph

```
1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10
```

## Tasks

### Task 1: Feature Flag Infrastructure
- [ ] Create `feature_flags` database table (name, enabled, percentage, individual_ids[], description, timestamps)
- [ ] Create `FeatureFlag` JPA entity
- [ ] Create `FeatureFlagRepository`
- [ ] Create `FeatureFlagService` with in-memory cache (60-second refresh via `@Scheduled`)
- [ ] Implement `isEnabled(flagName, fatherId)`: global → individual override → percentage-based (deterministic hash of fatherId)
- [ ] Seed default flags: voice_coaching_enabled, image_analysis_enabled, calendar_integration_enabled, weather_aware_enabled, wearable_integration_enabled, rag_knowledge_base_enabled, multi_agent_enabled (all disabled)
- [ ] Write unit tests: flag evaluation logic, deterministic percentage assignment, cache refresh

### Task 2: Runtime Configuration Management
- [ ] Create `runtime_config` database table (key, value, value_type, description, updated_at, updated_by)
- [ ] Create `RuntimeConfig` JPA entity
- [ ] Create `RuntimeConfigService` with read, update (with validation), and rollback capabilities
- [ ] Implement configuration change validation: type checking, range validation, rejection of invalid values
- [ ] Implement configuration change audit logging (who, what, when, old value, new value)
- [ ] Seed default tunable configurations: AI routing rules, rate limits, scheduling params, alert thresholds
- [ ] Write unit tests: validation rules, rollback, audit logging

### Task 3: Circuit Breaker Configuration (Resilience4j)
- [ ] Add Resilience4j Spring Boot starter dependency to `pom.xml`
- [ ] Create `CircuitBreakerConfig` with named breakers: `ai-provider`, `communication-provider`
- [ ] Configure ai-provider: 5% failure threshold, 100 sliding window, 1-minute open wait
- [ ] Configure communication-provider: 10% failure threshold, 50 sliding window, 60-second open wait
- [ ] Create `TimeoutConfig` with per-operation timeout budgets (AI: 20s, communication: 10s, persistence: 5s)
- [ ] Expose circuit breaker state via Actuator health indicators
- [ ] Write integration test: verify circuit breaker opens on consecutive failures and auto-recovers

### Task 4: Observability — Correlation IDs and Tracing
- [ ] Create `CorrelationIdFilter` (servlet filter): assigns UUID correlation ID on every request, propagates via MDC
- [ ] Add correlation_id to structured log output (logback JSON encoder MDC fields)
- [ ] Ensure correlation_id propagates to all async operations (side-effect outbox entries include it)
- [ ] Register custom Micrometer metrics: `dadcoach.messages.processed`, `dadcoach.ai.calls`, `dadcoach.conversations.active`
- [ ] Configure Micrometer to expose metrics via `/actuator/prometheus` (or equivalent)
- [ ] Write integration test: verify correlation ID appears in logs for a full request lifecycle

### Task 5: Observability — SLI Metrics and Alerting Foundation
- [ ] Define and register SLI metrics: availability (health probe pass rate), latency (p95 histogram), error rate (counter), throughput (counter)
- [ ] Create `BackpressureMonitor` that tracks queue depths and logs warnings at 70% capacity
- [ ] Create `HealthIndicators` custom health checks: AI provider reachable, communication provider reachable, memory system responsive
- [ ] Configure alert threshold evaluation (consumed by SPEC-009 admin layer later)
- [ ] Add Micrometer timer around the full conversation pipeline to measure end-to-end latency
- [ ] Write unit tests: metric registration, backpressure detection logic

### Task 6: Data Retention Enforcement
- [ ] Create `DataRetentionService` with `@Scheduled` job running during maintenance window (default 02:00 UTC)
- [ ] Implement retention rules per the consolidated schedule in SPEC-010 Req 12:
  - Delete expired `processed_messages` (24h TTL)
  - Delete expired `delivery_records` (90 days)
  - Delete expired `media_assets` (90 days)
  - Delete expired `ai_telemetry` full prompts (30 days → metadata only)
  - Delete expired `ai_telemetry` metadata (1 year)
  - Delete expired `memory_audit_log` entries (2 years)
  - Delete expired `api_audit_log` entries (2 years)
  - Delete expired `trigger_history` (30 days)
  - Delete expired `idempotency_keys` (24h)
- [ ] Implement batch deletion (process in chunks to avoid long transactions)
- [ ] Log retention enforcement results: records_deleted_by_type, execution_time
- [ ] Write integration test with Testcontainers: insert expired records, run job, verify deletion

### Task 7: Maintenance Mode Support
- [ ] Create `MaintenanceMode` configuration (enabled flag, start/end time, message)
- [ ] Implement maintenance detection in the request pipeline: if maintenance active, queue inbound messages instead of processing immediately
- [ ] Implement post-maintenance queue drain: process queued messages in order when maintenance ends
- [ ] Ensure internal processing (memory decay, consolidation) continues during maintenance
- [ ] Log maintenance window usage: start, end, duration, messages queued/delayed
- [ ] Write unit test: verify message queueing during maintenance, drain on resume

### Task 8: Deployment Compatibility — Health Checks and Graceful Shutdown
- [ ] Configure Spring Boot graceful shutdown (wait for in-flight requests before stopping)
- [ ] Set `server.shutdown=graceful` with configurable timeout (default 30s)
- [ ] Ensure advisory locks auto-release on JVM shutdown (PostgreSQL handles this)
- [ ] Verify side-effect outbox entries persist across restarts (background poller resumes on startup)
- [ ] Add startup recovery: detect stale ACTIVE conversations, transition expired ones (ConversationRecoveryService already does this — verify it runs on startup)
- [ ] Write integration test: simulate restart, verify no data loss, verify recovery runs

### Task 9: Backup Verification and Capacity Planning
- [ ] Create `BackupVerificationJob` (`@Scheduled` weekly): runs a simple SELECT query against recent data to verify data integrity
- [ ] Create capacity utilization metrics: current father count, memory count distribution, storage estimates
- [ ] Implement capacity alerts: warn at 70% of estimated max capacity per the scale tiers (Launch: 100, Growth: 1000)
- [ ] Create growth projection utility: based on registration rate, estimate when next tier is reached
- [ ] Write unit test: capacity calculation logic, alert threshold detection

### Task 10: Production Readiness Verification
- [ ] Create production readiness checklist integration test that verifies:
  - Health endpoints respond correctly
  - Feature flags are loaded and evaluable
  - Circuit breakers are registered
  - Correlation IDs propagate through requests
  - Retention job can execute (dry run)
  - Metrics are being collected
  - Graceful shutdown completes without error
- [ ] Verify `mvn clean verify` passes with all production-operations tests
- [ ] Verify Docker Compose starts with all new features active
- [ ] Document operational runbook: restart procedure, maintenance window activation, flag management
