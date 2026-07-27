# Technical Design — Administration & Analytics

## Architecture

### Overview

The Administration & Analytics layer is a read-heavy consumer of platform data that provides operational monitoring, support tools, business metrics, and reporting. Per SPEC-009, it owns NO business logic — it reads from domain entities, consumes events, aggregates metrics, and invokes privileged operations through the Admin API (SPEC-007).

Built within the Spring Boot monolith as a set of admin-facing services that query operational data and compute analytics from event streams and domain state.

### Architecture Decisions

**AD-1: Materialized Views for Analytics** — Business metrics (engagement distribution, completion rates, cohort analysis) are precomputed into materialized summary tables refreshed by scheduled jobs. Live queries hit summaries, not raw transaction tables.

**AD-2: Event-Driven Metric Updates** — Real-time operational metrics (active conversations, fallback rate, delivery success) are updated incrementally by consuming business events from the side-effect outbox, not by polling domain tables.

**AD-3: Role Enforcement via API Layer** — Admin role permissions are enforced by Spring Security in the API layer (SPEC-007 design). This layer trusts that the caller is authorized and focuses on data assembly. No redundant permission checks.

**AD-4: Alerting via Threshold Evaluation** — Alert thresholds are defined by owning specs. The Administration layer evaluates metrics against thresholds periodically (every 60s for real-time metrics) and manages alert lifecycle (TRIGGERED → ACKNOWLEDGED → RESOLVED → CLOSED).

**AD-5: Privacy at Query Layer** — Sensitivity filtering (hide HIGH content for ANALYTICS role, mask phone numbers) is applied at the query/mapping layer, not at the database level. All data exists in the database; access control determines what's returned.

### Package Structure

```
com.dadcoach.admin/
├── support/
│   ├── SupportCaseService.java         # Support case creation and tracking
│   ├── FatherContextService.java       # Unified father context assembly
│   ├── SupportCase.java                # JPA entity
│   └── SupportCaseRepository.java
├── monitoring/
│   ├── OperationalHealthService.java   # Real-time health views
│   ├── AlertService.java              # Alert evaluation, lifecycle, escalation
│   ├── AlertEvaluator.java            # Periodic threshold checking
│   ├── Alert.java                     # JPA entity
│   └── AlertRepository.java
├── analytics/
│   ├── BusinessMetricsService.java     # Computes/reads business metrics
│   ├── CoachingEffectivenessService.java # Effectiveness KPIs
│   ├── CohortAnalysisService.java      # Cohort grouping + comparison
│   ├── MetricSnapshot.java             # JPA entity (daily snapshots)
│   └── MetricSnapshotRepository.java
├── reporting/
│   ├── ReportGenerator.java            # On-demand and scheduled reports
│   ├── ReportScheduler.java            # Triggers report generation per schedule
│   ├── GeneratedReport.java            # JPA entity
│   └── ReportRepository.java
├── export/
│   ├── DataExportService.java          # GDPR exports, anonymized analytics export
│   └── AnonymizationService.java       # k-anonymity, PII removal
├── search/
│   └── AdminSearchService.java         # Cross-entity search for admin tools
├── audit/
│   └── AuditExplorationService.java    # Query across audit sources
└── event/
    └── AdminEventConsumer.java         # Processes outbox events for metric updates
```

## Components and Interfaces

### FatherContextService (Unified Support View)

```java
@Service
public class FatherContextService {
    // Assembles the unified support view per SPEC-009 Req 3 criteria 3
    public FatherContextDto buildContext(UUID fatherId) {
        return new FatherContextDto(
            fatherService.getProfile(fatherId),
            conversationService.getActive(fatherId),
            missionService.getActive(fatherId),
            memoryService.retrieveRanked(fatherId, null, null, 10),
            engagementService.getTrend(fatherId, 7),
            alertService.getPendingForFather(fatherId),
            safetyEventService.getUnreviewed(fatherId)
        );
    }
}
```

### AlertEvaluator

```java
@Component
public class AlertEvaluator {
    // Evaluates thresholds defined by owning specs every 60 seconds
    @Scheduled(fixedRate = 60000)
    public void evaluate() {
        // AI health: SPEC-003 Req 16 criteria 4
        checkAiLatency();    // p95 > 10s → CRITICAL
        checkAiErrorRate();  // > 5% → CRITICAL
        checkAiQuality();    // < 0.7 → WARNING
        
        // Communication: SPEC-006 Req 13 criteria 2
        checkDeliveryRate(); // < 90% → CRITICAL
        
        // Scheduling: SPEC-008 Req 15 criteria 2
        checkDailyCoverage(); // < 80% → WARNING
        
        // Safety: SPEC-004 Req 24
        checkUnreviewedSafety(); // > 4h unreviewed → CRITICAL
    }
}
```

## Data Models

### Administration Tables

```sql
CREATE TABLE support_cases (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id        UUID NOT NULL,
    father_id       UUID NOT NULL REFERENCES fathers(id),
    action          VARCHAR(50) NOT NULL,
    reason          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_cases_father ON support_cases(father_id, created_at DESC);
CREATE INDEX idx_support_cases_agent ON support_cases(agent_id, created_at DESC);

CREATE TABLE alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source          VARCHAR(30) NOT NULL,  -- AI, COMMUNICATION, SCHEDULING, MEMORY, SAFETY, PIPELINE
    severity        VARCHAR(10) NOT NULL,  -- CRITICAL, WARNING, INFO
    metric_name     VARCHAR(50) NOT NULL,
    threshold       VARCHAR(50) NOT NULL,
    current_value   VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'TRIGGERED',  -- TRIGGERED, ACKNOWLEDGED, RESOLVED, CLOSED
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alerts_active ON alerts(status, created_at DESC) WHERE status IN ('TRIGGERED','ACKNOWLEDGED');

CREATE TABLE metric_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_name     VARCHAR(50) NOT NULL,
    metric_value    NUMERIC NOT NULL,
    dimensions      JSONB,  -- e.g., {"phase": "BUILDING", "category": "PLAY"}
    snapshot_date   DATE NOT NULL,
    granularity     VARCHAR(10) NOT NULL,  -- DAILY, WEEKLY, MONTHLY
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(metric_name, dimensions, snapshot_date, granularity)
);

CREATE INDEX idx_metrics_name_date ON metric_snapshots(metric_name, snapshot_date DESC);

CREATE TABLE generated_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type     VARCHAR(30) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    content         JSONB NOT NULL,
    generated_by    UUID,  -- null for scheduled
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reports_type ON generated_reports(report_type, period_start DESC);
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| Metric computation fails (source data unavailable) | Use last known value; mark metric as STALE; alert operations |
| Alert evaluation timeout | Skip cycle; retry next interval; log missed evaluation |
| Report generation fails | Mark report as FAILED; retain partial data; alert SUPER_ADMIN |
| GDPR export request | Async processing; if fails, retry 3 times; escalate to SUPER_ADMIN if all fail |
| Search timeout (complex query) | Return partial results with indicator; suggest narrower filters |
| Event consumer falls behind | Backpressure: mark metrics as delayed; catch up without dropping events |

## Correctness Properties

- The Administration layer NEVER writes to domain tables directly — all mutations go through the Admin API (SPEC-007)
- Metrics are eventually consistent (refreshed on schedule) — never presented as real-time without a freshness indicator
- Alert thresholds are READ from configuration (defined by owning specs) — never hardcoded in the evaluator
- ANALYTICS role sees ONLY aggregated data — no individual father identification possible from any query
- Audit entries are append-only — no admin can modify or delete audit records regardless of role
- Phone numbers are masked in all admin responses unless actor is SUPER_ADMIN or the owning FATHER

## Requirement Traceability

| Requirement | Design Element |
|-------------|---------------|
| Req 1: Boundaries | Consumer-only pattern: reads domain state, invokes Admin API for mutations |
| Req 2: Roles | Enforced by API Security layer (SPEC-007); trusted here |
| Req 3: Support | `SupportCaseService` + `FatherContextService` (unified view) |
| Req 4: Father Mgmt | Search/filter via `AdminSearchService`; overrides via Admin API |
| Req 5: Conversation Inspection | Reads from `conversation_messages` table; filters system prompts |
| Req 6: Memory Inspection | Reads from `memories` + `memory_audit_log`; filters embeddings |
| Req 7: Mission/Goal Admin | Reads from `missions` + `goals`; aggregates in analytics services |
| Req 8: Operational Monitoring | `OperationalHealthService` + `AlertEvaluator` (60s cycle) |
| Req 9: Business Analytics | `BusinessMetricsService` + `metric_snapshots` materialized views |
| Req 10: Effectiveness | `CoachingEffectivenessService` — KPIs against SPEC-003 Req 12 targets |
| Req 11: Search | `AdminSearchService` — cross-entity, role-scoped, sensitivity-masked |
| Req 12: Reporting | `ReportGenerator` + `ReportScheduler` + `generated_reports` table |
| Req 13: Data Export | `DataExportService` + `AnonymizationService` (k >= 5) |
| Req 14: Audit Exploration | `AuditExplorationService` — queries across audit tables by actor/subject/time |
| Req 15: Alerts | `AlertService` + `AlertEvaluator` + alert lifecycle state machine |
| Req 16: Privacy | Sensitivity filtering in mapping layer; ANALYTICS gets aggregated only |
| Req 17: Cross-Spec | Reads thresholds from config; consumes events from outbox; uses Admin API |
