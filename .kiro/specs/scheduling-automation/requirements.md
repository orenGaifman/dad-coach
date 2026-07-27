# Requirements Document

## Introduction

**SPEC-008: Scheduling & Automation**

This specification defines the scheduling and automation behavior of the Dad Coach platform. It is the authoritative product definition for WHEN automated activities occur — determining trigger timing, evaluating preconditions, resolving conflicts between scheduled actions, and handling missed or delayed executions.

This document defines ONLY scheduling behavior and timing rules. It does not define what happens during execution — that responsibility belongs to other specifications:
- SPEC-002 defines the business rules that automations enforce (inactivity thresholds, streak calculations, phase transitions)
- SPEC-003 defines AI generation behavior invoked by scheduled actions
- SPEC-005 defines conversation orchestration triggered by scheduled events
- SPEC-006 defines message delivery for scheduled outbound messages

**Scope boundaries:**
- SPEC-001 defines infrastructure and deployment
- SPEC-002 defines domain entities, state machines, and business rules
- SPEC-003 defines AI intelligence layer
- SPEC-004 defines memory lifecycle and scheduled consolidation cadence
- SPEC-005 defines conversation orchestration pipeline
- SPEC-006 defines communication channels and delivery
- SPEC-007 defines application API
- SPEC-008 (this document) defines WHEN automated activities fire, under what conditions, and with what priority

**Ownership principle:** The Scheduling_System owns timing decisions exclusively. It determines:
- WHEN a scheduled action should fire (based on time, father timezone, and configured schedule)
- WHETHER preconditions are met (father status, active conversation, Quiet_Hours, daily limits)
- WHICH action takes priority when multiple are eligible simultaneously
- WHAT happens when a scheduled action is missed or delayed

It does NOT own:
- The execution logic of triggered actions (owned by SPEC-005 Conversation_Engine)
- The delivery of messages (owned by SPEC-006 Communication_Channel)
- The business rules that define thresholds (owned by SPEC-002)
- The AI content generation (owned by SPEC-003)

The Scheduling_System produces **Automation_Triggers** that are consumed by the Conversation_Engine (SPEC-005) for execution.

## Glossary

- **Scheduling_System**: The subsystem that determines when automated activities should occur and emits triggers for execution
- **Automation**: A recurring or one-time scheduled activity bound to a father (e.g., daily coaching, weekly summary, inactivity check)
- **Automation_Trigger**: A signal emitted by the Scheduling_System indicating that a scheduled action should now be executed
- **Trigger_Window**: The time range during which a trigger is valid for execution (after which it is considered missed)
- **Precondition**: A condition that must be true at trigger time for the automation to fire (father status, no active conversation, within daily limits)
- **Schedule_Slot**: A specific date and time at which an automation is planned to fire for a father
- **Quiet_Hours**: 21:00-07:00 father's local time — no proactive outbound activity (per SPEC-002)
- **Eligible_Window**: The daily time range during which proactive automations may fire: 07:00-21:00 father's local time
- **Conflict_Resolution**: The process of determining which automation fires when multiple are eligible simultaneously
- **Missed_Trigger**: A trigger whose Trigger_Window expired without execution (due to system downtime, precondition failure, or conflict)
- **Automation_Priority**: The relative importance of a scheduled action when competing for the same time slot
- **Cooldown**: A minimum time interval between consecutive proactive triggers for the same father
- **Father_Schedule**: The complete set of planned automations for a specific father, personalized to their timezone and preferences

---

## Requirements

### Requirement 1: Scheduling Ownership and Boundaries

**User Story:** As a product owner, I want scheduling responsibilities clearly separated from execution, so that timing logic is centralized and execution subsystems remain focused on their own concerns.

#### Acceptance Criteria

1. THE Scheduling_System SHALL own exclusively:
   - Evaluating when each father's scheduled automations should fire
   - Applying timezone-aware scheduling per father
   - Evaluating preconditions at trigger time
   - Resolving conflicts between simultaneous eligible automations
   - Emitting Automation_Triggers for the Conversation_Engine to execute
   - Tracking missed triggers and applying recovery rules
   - Respecting Quiet_Hours, daily limits, and pause status

2. THE Scheduling_System SHALL NOT own:
   - Conversation creation or orchestration (owned by SPEC-005)
   - Message delivery (owned by SPEC-006)
   - AI content generation (owned by SPEC-003)
   - Domain state transitions (owned by SPEC-002)
   - Memory consolidation scheduling (owned by SPEC-004 — the Scheduling_System only provides the timer; SPEC-004 owns the cadence rules)

3. THE Scheduling_System SHALL emit Automation_Triggers as the sole interface with execution subsystems. Each trigger contains:
   - trigger_id: unique identifier
   - father_id: target father
   - automation_type: the type of automation being triggered
   - scheduled_at: when it was planned to fire
   - triggered_at: when it actually fired
   - trigger_window_expires_at: deadline for execution before the trigger is considered missed
   - priority: the automation's priority level
   - context: minimal data needed for execution routing (e.g., conversation_type to create)

4. THE Conversation_Engine (SPEC-005) SHALL consume Automation_Triggers and decide whether to execute them based on its own pipeline rules (active conversation check, cooldown, father status). The Scheduling_System does not guarantee execution — only that conditions were met at trigger time.

5. THE Scheduling_System SHALL NOT bypass the Conversation_Engine. All scheduled actions that result in father-facing communication MUST flow through the Conversation_Engine pipeline.

---

### Requirement 2: Automation Types

**User Story:** As a product owner, I want all scheduled automations explicitly defined, so that the system's proactive behavior is predictable and complete.

#### Acceptance Criteria

1. THE Scheduling_System SHALL support the following automation types:

   | Automation Type | Cadence | Trigger Time | Conversation Type Created | Reference |
   |----------------|---------|-------------|--------------------------|-----------|
   | DAILY_COACHING | Daily | Father's preferred_coaching_time | DAILY_COACHING | SPEC-002 Req 5 |
   | WEEKLY_SUMMARY | Weekly (Monday) | 08:00 father's local time | N/A (delivery only, not a conversation) | SPEC-002 Req 10 criteria 16 |
   | INACTIVITY_3DAY | One-time (3 days after last interaction) | First eligible time in Eligible_Window | INACTIVITY_CHECK | SPEC-002 Req 10 criteria 4 |
   | INACTIVITY_7DAY | One-time (7 days after last interaction) | First eligible time in Eligible_Window | INACTIVITY_CHECK | SPEC-002 Req 10 criteria 5 |
   | INACTIVITY_14DAY | One-time (14 days after last interaction) | First eligible time in Eligible_Window | INACTIVITY_CHECK | SPEC-002 Req 10 criteria 6 |
   | CHURN_DETECTION | One-time (21 days after last interaction) | Any time (internal, no outbound) | N/A (status transition only) | SPEC-002 Req 10 criteria 7 |
   | BIRTHDAY_REMINDER | Annual (7 days before child birthday) | First eligible time in Eligible_Window | CELEBRATION | SPEC-002 Req 2 criteria 7 |
   | MISSION_EXPIRATION | One-time (mission deadline) | At deadline time | N/A (state transition only) | SPEC-002 Req 6 criteria 12 |
   | PAUSE_EXPIRATION | One-time (pause end date) | At expiration time | N/A (status transition + notification) | SPEC-002 Req 1 criteria 8 |
   | REFLECTION_WEEKLY | Weekly (Sunday) | Father's preferred_coaching_time | REFLECTION | SPEC-002 Req 8 criteria 1 |
   | MEMORY_CONSOLIDATION | Weekly (configurable day) | During maintenance window | N/A (internal processing) | SPEC-004 Req 8 |
   | MEMORY_DECAY | Daily | During maintenance window | N/A (internal processing) | SPEC-004 Req 6 criteria 4 |
   | CONVERSATION_EXPIRATION | Continuous (per conversation) | At conversation expiration time | N/A (state transition) | SPEC-005 Req 10 |
   | STREAK_EVALUATION | Daily | Midnight father's local time | N/A (metric update) | SPEC-002 Req 9 criteria 1 |

2. THE Scheduling_System SHALL categorize automations by their effect:
   - **Outbound**: Result in a message to the father (require Conversation_Engine, respect Quiet_Hours and daily limits)
   - **Internal**: System-only processing with no father-facing output (state transitions, consolidation, metrics)

3. THE Scheduling_System SHALL ensure every automation type has exactly one owning schedule definition — no automation may be triggered by multiple independent schedules.

---

### Requirement 3: Daily Coaching Schedule

**User Story:** As a father, I want my daily coaching message at my preferred time, so that coaching fits naturally into my routine.

#### Acceptance Criteria

1. THE Scheduling_System SHALL schedule one DAILY_COACHING trigger per active father per day at the father's preferred_coaching_time in the father's local timezone

2. THE Scheduling_System SHALL evaluate the following preconditions before emitting a DAILY_COACHING trigger:
   - Father status is ACTIVE
   - Father is not in PAUSED state
   - No ACTIVE conversation exists for the father (per SPEC-005 one-conversation rule)
   - Daily proactive message limit (5) not yet reached
   - Current time is within the Eligible_Window (07:00-21:00 father's local time)

3. WHEN a DAILY_COACHING trigger's preconditions fail, THE Scheduling_System SHALL:
   - If father has an active conversation: defer the trigger until the conversation completes (within same day)
   - If daily limit reached: discard the trigger for today
   - If preferred_coaching_time falls in Quiet_Hours: reschedule to 07:00

4. WHEN a Father updates their preferred_coaching_time, THE Scheduling_System SHALL apply the change starting the next calendar day (per SPEC-002 Requirement 10 criteria 10)

5. THE Scheduling_System SHALL vary DAILY_COACHING timing by ±15 minutes randomly per day to avoid predictable robot-like patterns. The randomized time must remain within the Eligible_Window.

---

### Requirement 4: Weekly Summary Schedule

**User Story:** As a father, I want my weekly progress summary delivered reliably every Monday, so that I can reflect on my coaching journey.

#### Acceptance Criteria

1. THE Scheduling_System SHALL schedule one WEEKLY_SUMMARY trigger per active father every Monday at 08:00 in the father's local timezone (per SPEC-002 Requirement 10 criteria 16)

2. THE Scheduling_System SHALL evaluate the following preconditions before emitting a WEEKLY_SUMMARY trigger:
   - Father status is ACTIVE or REACTIVATED
   - Father is not in PAUSED state
   - Father has been active for at least 7 days (no summary for first partial week)

3. THE WEEKLY_SUMMARY automation is a delivery-only action — it does not create a coaching conversation. The Conversation_Engine formats and delivers the summary through the Communication_Channel.

4. WHEN a WEEKLY_SUMMARY trigger falls during Quiet_Hours (Monday before 07:00), THE Scheduling_System SHALL defer to 08:00 Monday. If 08:00 is already past (system was down), the trigger remains valid until end of Monday (Trigger_Window: same calendar day).

---

### Requirement 5: Re-engagement Automations

**User Story:** As a product owner, I want inactive fathers re-engaged at progressive intervals, so that valuable coaching relationships are preserved without being pushy.

#### Acceptance Criteria

1. THE Scheduling_System SHALL track each father's last_interaction_at (timestamp of last inbound message from the father) and schedule inactivity triggers at the following thresholds:
   - INACTIVITY_3DAY: 72 hours after last_interaction_at
   - INACTIVITY_7DAY: 168 hours (7 days) after last_interaction_at
   - INACTIVITY_14DAY: 336 hours (14 days) after last_interaction_at
   - CHURN_DETECTION: 504 hours (21 days) after last_interaction_at

2. WHEN the father sends any message, THE Scheduling_System SHALL cancel all pending inactivity triggers for that father and reset the inactivity timer

3. THE Scheduling_System SHALL evaluate the following preconditions for inactivity triggers (INACTIVITY_3DAY, 7DAY, 14DAY):
   - Father status is ACTIVE (not PAUSED, not already CHURNED)
   - No ACTIVE conversation exists
   - Daily proactive limit not reached
   - Current time is within Eligible_Window

4. THE CHURN_DETECTION trigger is internal-only: it transitions the father to CHURNED status (per SPEC-002 Requirement 10 criteria 7) without sending a message. No preconditions related to Quiet_Hours or daily limits apply.

5. THE Scheduling_System SHALL ensure inactivity automations are mutually exclusive per tier: once INACTIVITY_3DAY fires, INACTIVITY_7DAY and INACTIVITY_14DAY are scheduled relative to the original last_interaction_at (not relative to the 3-day trigger). Each fires at most once per inactivity period.

6. WHEN an inactivity trigger's preconditions fail due to an active conversation, THE Scheduling_System SHALL defer the trigger until the conversation completes (within the same Trigger_Window — maximum 24 hours).

---

### Requirement 6: Birthday and Event Automations

**User Story:** As a product owner, I want birthday celebrations triggered automatically, so that meaningful family moments are never missed by the coaching system.

#### Acceptance Criteria

1. THE Scheduling_System SHALL schedule a BIRTHDAY_REMINDER trigger 7 days before each active child's birthday (computed from the Child entity's birth_date per SPEC-002 Requirement 2 criteria 7)

2. THE Scheduling_System SHALL recalculate birthday schedules:
   - When a new child is registered
   - When a child's birth_date is corrected
   - When a child is archived (cancel pending birthday triggers)
   - Annually after each birthday passes (schedule next year's)

3. THE BIRTHDAY_REMINDER trigger SHALL create a CELEBRATION conversation (per SPEC-002 Requirement 8 criteria 1) that results in a birthday-themed mission and reminder.

4. THE Scheduling_System SHALL evaluate preconditions for BIRTHDAY_REMINDER:
   - Father status is ACTIVE
   - Child is not ARCHIVED
   - No ACTIVE conversation exists (defer if busy)
   - Within Eligible_Window

5. WHEN a BIRTHDAY_REMINDER's Trigger_Window expires without execution (7 days before → birthday day), THE Scheduling_System SHALL attempt delivery once more on the birthday itself. If that also fails, the trigger is discarded (birthday has passed).

---

### Requirement 7: Mission Expiration and Pause Handling

**User Story:** As a product owner, I want time-sensitive automations (mission deadlines, pause expirations) handled precisely, so that state transitions occur at the correct moment.

#### Acceptance Criteria

1. THE Scheduling_System SHALL schedule a MISSION_EXPIRATION trigger at the exact deadline for each ASSIGNED or ACCEPTED mission:
   - Weekday missions: 24 hours after assignment (per SPEC-002 Requirement 6 criteria 12)
   - Weekend missions: 48 hours after assignment (per SPEC-002 Requirement 6 criteria 12)

2. WHEN a MISSION_EXPIRATION trigger fires, THE Scheduling_System SHALL emit a trigger for the Conversation_Engine to transition the mission to EXPIRED (or ABANDONED if IN_PROGRESS). This is an internal state transition — no outbound message is required at expiration time.

3. WHEN a mission is completed, accepted, or skipped before its deadline, THE Scheduling_System SHALL cancel the pending MISSION_EXPIRATION trigger for that mission.

4. THE Scheduling_System SHALL schedule a PAUSE_EXPIRATION trigger at the exact end of a father's pause duration (per SPEC-002 Requirement 1 criteria 8):
   - At expiration: transition father from PAUSED to ACTIVE
   - Send a welcome-back notification (through Communication_Channel, respecting Quiet_Hours)

5. WHEN a PAUSED father manually resumes before the pause expires, THE Scheduling_System SHALL cancel the pending PAUSE_EXPIRATION trigger.

---

### Requirement 8: Quiet Hours Interaction

**User Story:** As a product owner, I want Quiet_Hours respected by all proactive automations, so that fathers are never disturbed during sleep hours.

#### Acceptance Criteria

1. THE Scheduling_System SHALL enforce Quiet_Hours (21:00-07:00 father's local time per SPEC-002 Requirement 10 criteria 1) for ALL outbound automation types. No proactive trigger that results in a father-facing message SHALL fire during Quiet_Hours.

2. WHEN an outbound automation's scheduled time falls within Quiet_Hours, THE Scheduling_System SHALL defer it to the start of the next Eligible_Window (07:00 father's local time).

3. WHEN multiple automations are deferred to 07:00 due to Quiet_Hours, THE Scheduling_System SHALL apply priority-based conflict resolution (Requirement 10) and spacing rules (Requirement 11) to determine execution order.

4. THE Scheduling_System SHALL NOT defer internal automations for Quiet_Hours. Internal automations (MEMORY_CONSOLIDATION, MEMORY_DECAY, STREAK_EVALUATION, CHURN_DETECTION, MISSION_EXPIRATION) execute regardless of time of day since they produce no outbound messages.

5. WHEN a father's timezone changes, THE Scheduling_System SHALL recalculate all pending triggers for that father using the new timezone, effective the next calendar day (per SPEC-002 Requirement 10 criteria 10).


---

### Requirement 9: Pause and Resume Behavior

**User Story:** As a product owner, I want all proactive automations suspended during a pause and cleanly resumed afterward, so that paused fathers receive zero unwanted contact.

#### Acceptance Criteria

1. WHEN a Father transitions to PAUSED status, THE Scheduling_System SHALL:
   - Suspend ALL outbound automations for that father (DAILY_COACHING, WEEKLY_SUMMARY, INACTIVITY checks, BIRTHDAY_REMINDER, REFLECTION_WEEKLY)
   - Preserve pending internal automations (MISSION_EXPIRATION continues — missions expire even during pause; MEMORY_DECAY continues at 50% rate per SPEC-004 Requirement 6 criteria 8)
   - Schedule a PAUSE_EXPIRATION trigger for the end of the requested pause duration

2. WHEN a Father transitions from PAUSED back to ACTIVE (either by manual resume or pause expiration), THE Scheduling_System SHALL:
   - Re-activate all outbound automations
   - Schedule the next DAILY_COACHING for the father's preferred_coaching_time on the following day
   - Resume WEEKLY_SUMMARY on the next Monday
   - Reset inactivity timers (pause duration does not count as inactivity)
   - Cancel the PAUSE_EXPIRATION trigger if resume was manual

3. THE Scheduling_System SHALL NOT send any catch-up triggers for automations missed during the pause (no backfill of daily coaching or summaries skipped during pause).

4. WHEN a PAUSED father sends an inbound message (initiating resume per SPEC-002 Requirement 1 criteria 8), THE Scheduling_System SHALL treat the message receipt as the resume event and reactivate schedules immediately.

---

### Requirement 10: Scheduling Priorities and Conflict Resolution

**User Story:** As a product owner, I want a clear priority system for competing automations, so that the most important action always wins and fathers are never overwhelmed.

#### Acceptance Criteria

1. THE Scheduling_System SHALL assign priorities to automation types (1 = highest):

   | Priority | Automation Type | Rationale |
   |----------|----------------|-----------|
   | 1 | BIRTHDAY_REMINDER | Time-sensitive, once per year, high emotional value |
   | 2 | REFLECTION_WEEKLY | Weekly cadence, important for coaching progression |
   | 3 | DAILY_COACHING | Core daily engagement, primary coaching mechanism |
   | 4 | INACTIVITY_3DAY | Re-engagement, progressively urgent |
   | 5 | INACTIVITY_7DAY | Re-engagement |
   | 6 | INACTIVITY_14DAY | Re-engagement |
   | 7 | WEEKLY_SUMMARY | Informational, not interactive |
   | 8 | PAUSE_EXPIRATION notification | Administrative |

2. WHEN multiple outbound automations are eligible for the same father at the same time (e.g., both DAILY_COACHING and BIRTHDAY_REMINDER are due), THE Scheduling_System SHALL emit only the highest-priority trigger and defer the lower-priority trigger to the next available slot (respecting the minimum cooldown per Requirement 11).

3. WHEN a deferred trigger's Trigger_Window expires before it can be executed, THE Scheduling_System SHALL discard it and log the miss. Daily automations (DAILY_COACHING) have a Trigger_Window of the same calendar day. One-time automations have their own defined windows (Requirement 12).

4. THE Scheduling_System SHALL resolve same-priority conflicts (theoretically impossible given the table, but as a fallback) by selecting the automation with the earlier original scheduled_at.

5. WHEN CHURN_DETECTION (21 days) fires on the same day as INACTIVITY_14DAY, THE Scheduling_System SHALL fire CHURN_DETECTION (internal, no message) and cancel the INACTIVITY_14DAY trigger (no point messaging a father being marked CHURNED).

---

### Requirement 11: Spacing and Cooldown Rules

**User Story:** As a product owner, I want proactive messages spaced appropriately, so that fathers never feel bombarded by the system.

#### Acceptance Criteria

1. THE Scheduling_System SHALL enforce a minimum 4-hour gap between consecutive outbound Automation_Triggers for the same father (per SPEC-003 Requirement 4 criteria 5 — Decision_Engine minimum gap between proactive outbound messages)

2. WHEN two eligible triggers violate the 4-hour spacing rule, THE Scheduling_System SHALL fire the higher-priority trigger at its scheduled time and defer the lower-priority trigger to scheduled_time + 4 hours (if still within its Trigger_Window and Eligible_Window).

3. THE Scheduling_System SHALL enforce the daily proactive message limit of 5 per father (per SPEC-002 Requirement 10 criteria 2). Triggers beyond this limit are discarded for the day.

4. THE Scheduling_System SHALL read the authoritative proactive_messages_today counter owned by the Conversation_Engine (SPEC-005 Requirement 12 criteria 7) before emitting outbound triggers. The Conversation_Engine is the single source of truth for this counter because it processes both scheduled triggers and father-initiated interactions. The Scheduling_System does NOT maintain its own independent counter.

5. THE Scheduling_System SHALL NOT apply spacing rules to internal automations. Internal triggers may fire at any frequency since they produce no outbound messages.

---

### Requirement 12: Trigger Windows and Missed Triggers

**User Story:** As a product owner, I want clear rules for when a missed trigger is still valid and when it should be discarded, so that stale automations never produce confusing out-of-context messages.

#### Acceptance Criteria

1. THE Scheduling_System SHALL define Trigger_Windows per automation type:

   | Automation Type | Trigger_Window | Missed Behavior |
   |----------------|---------------|-----------------|
   | DAILY_COACHING | Same calendar day (father's timezone) | If not fired by 21:00, discard — next day's trigger takes over |
   | WEEKLY_SUMMARY | Same calendar day (Monday) | If not delivered by end of Monday, discard |
   | INACTIVITY_3DAY | 24 hours from scheduled time | If expired, skip directly to next tier if applicable |
   | INACTIVITY_7DAY | 24 hours from scheduled time | If expired, skip to next tier |
   | INACTIVITY_14DAY | 24 hours from scheduled time | If expired, CHURN_DETECTION will fire at 21 days |
   | BIRTHDAY_REMINDER | 7 days (from 7 days before birthday through birthday day) | Attempt on birthday itself; if missed entirely, discard |
   | REFLECTION_WEEKLY | Same calendar day (Sunday) | If not fired by end of Sunday, discard |
   | MISSION_EXPIRATION | Immediate (exact deadline) | Must fire within 1 hour of deadline; late fire still transitions state |
   | PAUSE_EXPIRATION | Immediate (exact time) | Must fire within 1 hour; late fire still transitions state |
   | CHURN_DETECTION | 24 hours | Must fire within 24 hours of 21-day mark |

2. WHEN the system recovers from downtime, THE Scheduling_System SHALL evaluate all triggers that were due during the downtime period:
   - If the trigger's Trigger_Window has not expired: fire immediately (subject to preconditions and conflict resolution)
   - If the trigger's Trigger_Window has expired: mark as MISSED, log the miss, and do not fire

3. THE Scheduling_System SHALL log every missed trigger with: trigger_id, automation_type, father_id, scheduled_at, window_expired_at, reason (SYSTEM_DOWNTIME, PRECONDITION_FAILED, CONFLICT_DEFERRED, LIMIT_REACHED)

4. THE Scheduling_System SHALL NOT attempt to "catch up" by firing multiple triggers in rapid succession after downtime. Normal spacing and limit rules apply even during recovery.

---

### Requirement 13: Timezone Behavior

**User Story:** As a product owner, I want all scheduling computed in the father's local timezone, so that coaching times feel natural regardless of where the father lives.

#### Acceptance Criteria

1. THE Scheduling_System SHALL compute all father-facing schedule times in the father's configured timezone (stored on the Father entity per SPEC-002)

2. THE Scheduling_System SHALL handle timezone transitions (daylight saving time changes) by recalculating schedules when the father's timezone offset changes. A trigger scheduled for "08:00 local" always fires at 08:00 local regardless of UTC offset changes.

3. WHEN a father's timezone is updated (explicit request per SPEC-002 Requirement 12 criteria 5), THE Scheduling_System SHALL recalculate all pending triggers for the new timezone effective the next calendar day.

4. THE Scheduling_System SHALL compute "calendar day" boundaries (midnight, start of week, start of month) in the father's local timezone — never in UTC. Daily limits reset at midnight local, weekly schedules fire on the local Monday/Sunday.

5. THE Scheduling_System SHALL handle fathers in all valid IANA timezones (UTC-12 through UTC+14). Edge cases with extreme offsets (e.g., International Date Line crossings) are handled by computing everything relative to the father's configured timezone.

6. FOR internal automations (MEMORY_CONSOLIDATION, MEMORY_DECAY), THE Scheduling_System SHALL use the configured maintenance window (per SPEC-004 Requirement 20 criteria 10) which operates in UTC — these are system-level schedules not tied to any father's timezone.

---

### Requirement 14: Retry Scheduling

**User Story:** As a product owner, I want failed trigger execution handled with appropriate retries, so that transient failures don't result in permanently missed coaching moments.

#### Acceptance Criteria

1. WHEN the Conversation_Engine rejects an Automation_Trigger (e.g., active conversation blocking execution), THE Scheduling_System SHALL evaluate whether to retry:
   - If the Trigger_Window has not expired: schedule a retry after a configurable delay (default: 15 minutes)
   - If the Trigger_Window has expired: mark as MISSED and do not retry

2. THE Scheduling_System SHALL retry a rejected trigger a maximum of 3 times within its Trigger_Window. After 3 rejections, the trigger is marked MISSED regardless of remaining window time.

3. WHEN a trigger is retried, THE Scheduling_System SHALL re-evaluate all preconditions (father status may have changed, daily limit may now be reached, conversation may have completed). Retries are not blind — they recheck eligibility.

4. THE Scheduling_System SHALL NOT retry internal automations (MISSION_EXPIRATION, CHURN_DETECTION, PAUSE_EXPIRATION). These either succeed or are escalated as operational alerts — they represent state transitions that must eventually occur.

5. WHEN an internal automation fails to execute (e.g., persistence failure on a state transition), THE Scheduling_System SHALL retry indefinitely with exponential backoff (1m, 5m, 15m, 60m, max 60m) until success or manual intervention. These are critical state transitions that cannot be skipped.

---

### Requirement 15: Automation Observability

**User Story:** As a product owner, I want visibility into scheduling behavior, so that I can monitor automation health and detect patterns of missed or delayed triggers.

#### Acceptance Criteria

1. THE Scheduling_System SHALL track and report the following metrics:
   - Triggers scheduled per hour (by automation_type)
   - Triggers fired per hour (by automation_type)
   - Triggers missed per hour (by automation_type, by reason)
   - Triggers deferred per hour (by reason: Quiet_Hours, conflict, spacing, active_conversation)
   - Average trigger delay: difference between scheduled_at and triggered_at
   - Retry rate: retried triggers / total triggers
   - Father coverage: percentage of active fathers who received their DAILY_COACHING today

2. THE Scheduling_System SHALL alert operations when:
   - DAILY_COACHING coverage drops below 80% (more than 20% of active fathers missed their daily)
   - More than 10% of triggers are MISSED in any 1-hour window
   - Average trigger delay exceeds 30 minutes
   - Any internal automation (CHURN_DETECTION, MISSION_EXPIRATION) fails to execute after 3 retries

3. THE Scheduling_System SHALL maintain a per-father schedule audit log:
   - All scheduled triggers (planned)
   - All emitted triggers (fired)
   - All missed triggers with reason
   - All deferred triggers with deferral reason and new time
   - Retained for 30 days for operational analysis

4. THE Admin API (SPEC-007) SHALL expose:
   - A father's current schedule (next planned triggers)
   - A father's trigger history (last 30 days)
   - System-wide scheduling health dashboard data

---

### Requirement 16: Father Lifecycle Integration

**User Story:** As a product owner, I want scheduling to respond correctly to all father lifecycle events, so that automations are always appropriate for the father's current status.

#### Acceptance Criteria

1. THE Scheduling_System SHALL respond to the following Father status transitions:

   | Transition | Scheduling Action |
   |-----------|------------------|
   | → ACTIVE (onboarding complete) | Activate all recurring automations; schedule first DAILY_COACHING for next day |
   | ACTIVE → PAUSED | Suspend all outbound automations; schedule PAUSE_EXPIRATION |
   | PAUSED → ACTIVE | Reactivate automations; cancel PAUSE_EXPIRATION; schedule next day's DAILY_COACHING |
   | ACTIVE → CHURNED | Cancel all automations for this father |
   | CHURNED → REACTIVATED | Reactivate automations; schedule DAILY_COACHING for next day |
   | REACTIVATED → ACTIVE | No schedule change (already reactivated) |
   | Any → DELETED | Cancel and remove all automations permanently |

2. WHEN a Father completes onboarding, THE Scheduling_System SHALL generate the father's initial schedule:
   - First DAILY_COACHING: next day at preferred_coaching_time
   - First REFLECTION_WEEKLY: next Sunday at preferred_coaching_time
   - First WEEKLY_SUMMARY: next Monday at 08:00 local
   - Birthday reminders for all registered children
   - Inactivity timers initialized to onboarding completion time

3. WHEN a new Child is registered, THE Scheduling_System SHALL schedule a BIRTHDAY_REMINDER for that child (7 days before next birthday)

4. WHEN a Child is archived, THE Scheduling_System SHALL cancel any pending BIRTHDAY_REMINDER for that child

---

### Requirement 17: Cross-Spec Compatibility

**User Story:** As an architect, I want explicit verification that the scheduling system is compatible with all other specifications, so that no ownership conflicts or timing contradictions exist.

#### Acceptance Criteria

1. THE Scheduling_System SHALL respect all business timing rules defined in SPEC-002 Requirement 10. Specifically:
   - Quiet_Hours enforcement (criteria 1)
   - Daily proactive message limit of 5 (criteria 2)
   - Maximum 8 messages per conversation (criteria 3 — enforced by Conversation_Engine, not scheduler)
   - Inactivity thresholds at 3, 7, 14, 21 days (criteria 4-7)
   - Preferred_coaching_time changes effective next day (criteria 10)
   - 30-second response latency (criteria 11 — not a scheduling concern, owned by SPEC-005)

2. THE Scheduling_System SHALL respect the minimum 4-hour gap between proactive messages defined in SPEC-003 Requirement 4 criteria 5 (Decision_Engine spacing rule). The scheduler enforces spacing before emitting triggers.

3. THE Scheduling_System SHALL emit triggers consumed by the Conversation_Engine (SPEC-005). The interface is the Automation_Trigger defined in Requirement 1 criteria 3. SPEC-005 Requirement 13 criteria 10 defines how the Conversation_Engine handles these triggers when an active conversation exists.

4. THE Scheduling_System SHALL NOT directly invoke the Communication_Channel (SPEC-006). All outbound communication — including WEEKLY_SUMMARY delivery — flows through the Conversation_Engine, which then uses the Communication_Channel for delivery. The Conversation_Engine handles WEEKLY_SUMMARY triggers as a delivery-only action (no coaching conversation created, but session/template evaluation and Quiet_Hours enforcement still apply through the standard pipeline).

5. THE Scheduling_System SHALL respect the memory consolidation and decay cadences defined in SPEC-004:
   - MEMORY_CONSOLIDATION: weekly, during maintenance window (SPEC-004 Requirement 8 criteria 1)
   - MEMORY_DECAY: daily, during maintenance window (SPEC-004 Requirement 6 criteria 4)
   The Scheduling_System provides the timer; SPEC-004 owns the processing logic.

6. THE Scheduling_System SHALL provide schedule data to the Admin API (SPEC-007) for operational visibility, but SHALL NOT be queryable or modifiable through the Father API. Fathers influence their schedule indirectly through preferences (coaching_time, pause requests), not by directly manipulating automation triggers.

7. THE Scheduling_System SHALL NOT duplicate the conversation expiration checking defined in SPEC-005 Requirement 10 criteria 3. Conversation expiration is evaluated both by the Conversation_Engine (on every inbound message) and by the Scheduling_System (periodic check). These are complementary — the scheduler catches conversations that expire without inbound activity.

8. THE Scheduling_System SHALL coordinate with SPEC-005's cooldown rules:
   - After EXPIRED conversation: 24-hour cooldown before proactive triggers (SPEC-005 Req 10 criteria 4)
   - After ABANDONED conversation: 48-hour cooldown
   - After COMPLETED conversation: no cooldown for proactive triggers
   The Scheduling_System checks conversation history before emitting outbound triggers.
