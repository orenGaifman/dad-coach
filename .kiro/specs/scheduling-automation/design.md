# Technical Design — Scheduling & Automation

## Architecture

### Overview

The Scheduling System determines WHEN automated activities fire for each father. It evaluates triggers, applies timezone-aware timing, enforces Quiet_Hours and daily limits, resolves conflicts between competing automations, and emits Automation_Triggers consumed by the Conversation Engine (SPEC-005). Built within the Spring Boot monolith using PostgreSQL-backed scheduled triggers and Spring's `@Scheduled` for periodic evaluation.

### Architecture Decisions

**AD-1: Database-Stored Trigger Schedule** — Each father's upcoming triggers are stored as rows in a `scheduled_triggers` table with precise fire-at timestamps in UTC. A poller evaluates due triggers every 30 seconds.

**AD-2: Timezone Computation at Schedule Time** — Triggers are computed in the father's local timezone and stored as UTC. DST transitions are handled by recomputing on timezone change. The father's configured timezone is always the source of truth.

**AD-3: Single Evaluation Loop** — One Spring `@Scheduled` poller processes ALL trigger types. This avoids multiple competing threads and simplifies conflict resolution (evaluated within a single pass per father).

**AD-4: Trigger → Outbox → Conversation Engine** — Emitted triggers are written to the side-effect outbox (shared with SPEC-005). The Conversation Engine processes them alongside inbound messages using the same pipeline. This guarantees Session_Lock acquisition and precondition checking.

**AD-5: Inactivity Timer via last_interaction_at** — Inactivity-based triggers (3/7/14/21 day) are computed from the father's `last_interaction_at` timestamp rather than storing countdown timers. The evaluator simply checks `now() - last_interaction_at > threshold`.

### Package Structure

```
com.dadcoach.scheduler/
├── SchedulingService.java              # Public interface for schedule management
├── TriggerEvaluator.java              # Periodic poller: evaluates due triggers
├── TriggerEmitter.java                # Writes Automation_Triggers to outbox
├── schedule/
│   ├── FatherScheduleBuilder.java     # Creates initial schedule on onboarding
│   ├── ScheduleRecalculator.java      # Handles timezone/preference changes
│   └── BirthdayScheduler.java         # Computes birthday triggers from Child birth_dates
├── timing/
│   ├── TimezoneResolver.java          # Father timezone → UTC conversion
│   ├── QuietHoursEvaluator.java       # Is a given UTC time in father's Quiet_Hours?
│   ├── EligibleWindowEvaluator.java   # 07:00-21:00 local check
│   └── CooldownEvaluator.java         # Post-conversation cooldown check
├── conflict/
│   ├── ConflictResolver.java          # Priority-based selection when multiple triggers due
│   └── SpacingEnforcer.java           # 4-hour minimum gap enforcement
├── inactivity/
│   └── InactivityEvaluator.java       # Computes 3/7/14/21-day thresholds from last_interaction_at
├── lifecycle/
│   └── FatherLifecycleHandler.java    # Responds to ACTIVE/PAUSED/CHURNED transitions
├── entity/
│   ├── ScheduledTrigger.java          # JPA entity
│   └── TriggerHistory.java            # JPA entity (fired/missed log)
├── repository/
│   ├── ScheduledTriggerRepository.java
│   └── TriggerHistoryRepository.java
└── config/
    └── SchedulerProperties.java       # Poll interval, maintenance window, default times
```

## Components and Interfaces

### TriggerEvaluator (Main Poller)

```java
@Component
@RequiredArgsConstructor
public class TriggerEvaluator {

    private final ScheduledTriggerRepository triggerRepository;
    private final ConflictResolver conflictResolver;
    private final SpacingEnforcer spacingEnforcer;
    private final QuietHoursEvaluator quietHours;
    private final CooldownEvaluator cooldown;
    private final TriggerEmitter emitter;
    private final FatherService fatherService;

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:30000}")
    public void evaluateDueTriggers() {
        Instant now = Instant.now();
        List<ScheduledTrigger> due = triggerRepository.findDue(now, Limit.of(100));
        
        // Group by father for conflict resolution
        Map<UUID, List<ScheduledTrigger>> byFather = due.stream()
            .collect(Collectors.groupingBy(ScheduledTrigger::getFatherId));
        
        for (var entry : byFather.entrySet()) {
            UUID fatherId = entry.getKey();
            List<ScheduledTrigger> triggers = entry.getValue();
            
            // Precondition check
            Father father = fatherService.findById(fatherId).orElse(null);
            if (father == null || !isEligible(father)) {
                triggers.forEach(t -> markMissed(t, "PRECONDITION_FAILED"));
                continue;
            }
            
            // Conflict resolution: pick highest priority
            ScheduledTrigger selected = conflictResolver.resolve(triggers);
            
            // Spacing check against proactive_messages_today
            if (!spacingEnforcer.canFire(fatherId, now)) {
                defer(selected);
                continue;
            }
            
            // Emit the winner
            emitter.emit(selected);
            
            // Defer/discard losers
            triggers.stream().filter(t -> !t.equals(selected))
                .forEach(this::deferOrDiscard);
        }
    }
}
```

### InactivityEvaluator

```java
@Component
public class InactivityEvaluator {
    // Runs within TriggerEvaluator cycle for fathers without recent triggers
    // Computes: now() - father.lastInteractionAt against 3/7/14/21 day thresholds
    // Creates one-time triggers when thresholds are crossed
    
    @Scheduled(fixedRate = 3600000) // Every hour
    public void evaluateInactivity() {
        // Find active fathers where last_interaction_at > 3 days ago
        // AND no pending INACTIVITY trigger exists
        // Create appropriate INACTIVITY_*DAY trigger
    }
}
```

## Data Models

### Scheduling Tables

```sql
CREATE TABLE scheduled_triggers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL REFERENCES fathers(id),
    automation_type     VARCHAR(30) NOT NULL,
    fire_at             TIMESTAMPTZ NOT NULL,  -- When to fire (UTC)
    window_expires_at   TIMESTAMPTZ NOT NULL,  -- Trigger_Window end
    priority            INTEGER NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED, FIRED, MISSED, CANCELLED
    context             JSONB,  -- conversation_type, child_id, etc.
    retry_count         INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    fired_at            TIMESTAMPTZ,
    missed_reason       VARCHAR(50)
);

CREATE INDEX idx_triggers_due ON scheduled_triggers(fire_at, status) WHERE status = 'SCHEDULED';
CREATE INDEX idx_triggers_father ON scheduled_triggers(father_id, status);

CREATE TABLE trigger_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL,
    automation_type VARCHAR(30) NOT NULL,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    fired_at        TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL,  -- FIRED, MISSED, CANCELLED
    missed_reason   VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trigger_history_father ON trigger_history(father_id, created_at DESC);
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| Trigger fire_at in Quiet_Hours (should not happen, but safety) | Defer to 07:00 local; log scheduling error |
| Conversation Engine rejects trigger (active conversation) | Retry in 15 min, max 3 times within Trigger_Window |
| Father status changed between schedule and fire | Re-evaluate preconditions at fire time; discard if ineligible |
| System downtime misses triggers | On recovery: evaluate all overdue triggers; fire those within window; mark rest MISSED |
| Internal automation (CHURN, MISSION_EXPIRATION) fails | Retry with exponential backoff indefinitely; alert after 3 failures |
| Timezone change mid-day | Cancel all pending triggers; recompute schedule for next day |

## Correctness Properties

- Triggers fire in UTC but are COMPUTED from father's local timezone — DST-safe
- Exactly one trigger per automation type fires per father per cycle — conflict resolution guarantees single winner
- The proactive_messages_today counter is READ from the Conversation Engine (single source of truth) — no independent counter maintained
- All outbound triggers pass through the Conversation Engine pipeline — guaranteeing Session_Lock, idempotency, and state validation
- Inactivity thresholds are evaluated from `last_interaction_at` — any father message resets ALL pending inactivity triggers
- Missed triggers are NEVER retroactively fired if their Trigger_Window has expired
- ±15 min randomization on DAILY_COACHING is applied at schedule creation time and persisted — not re-randomized on retry

## Requirement Traceability

| Requirement | Design Element |
|-------------|---------------|
| Req 1: Ownership | `TriggerEmitter` → outbox → Conversation Engine (never bypasses) |
| Req 2: Automation Types | `automation_type` enum; full type table in SchedulerProperties |
| Req 3: Daily Coaching | `FatherScheduleBuilder` creates daily trigger at preferred_time ± 15min |
| Req 4: Weekly Summary | Monday 08:00 local trigger; delivery-only path through Conversation Engine |
| Req 5: Re-engagement | `InactivityEvaluator` — threshold-based from last_interaction_at |
| Req 6: Birthday | `BirthdayScheduler` — 7 days before; recalculated on child changes |
| Req 7: Mission/Pause Expiration | One-time triggers at exact deadline; cancellable |
| Req 8: Quiet Hours | `QuietHoursEvaluator` applied before every outbound trigger emission |
| Req 9: Pause/Resume | `FatherLifecycleHandler` — suspend/reactivate triggers on status change |
| Req 10: Priorities | `ConflictResolver` — priority table; single winner per evaluation cycle |
| Req 11: Spacing | `SpacingEnforcer` — 4h gap; reads proactive counter from Conversation Engine |
| Req 12: Trigger Windows | `window_expires_at` on entity; checked before fire; MISSED if expired |
| Req 13: Timezone | `TimezoneResolver` — all computation in local; stored as UTC |
| Req 14: Retries | retry_count on trigger; max 3 for outbound; indefinite for internal |
| Req 15: Observability | `trigger_history` table + metrics emitted per evaluation cycle |
| Req 16: Lifecycle | `FatherLifecycleHandler` — reacts to all status transitions |
| Req 17: Cross-Spec | Reads proactive counter from SPEC-005; routes through Conversation Engine pipeline |
