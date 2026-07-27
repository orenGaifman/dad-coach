# Requirements Document

## Introduction

**SPEC-009: Administration & Analytics**

This specification defines the administrative and analytical capabilities of the Dad Coach platform. It is the authoritative product definition for how administrators, support personnel, and operational users observe, monitor, analyze, and manage the system.

This document defines ONLY what administrators can do and what information they can observe. It does not define how these capabilities are rendered, stored, or transported.

**Scope boundaries:**
- SPEC-001 defines infrastructure and deployment
- SPEC-002 defines domain entities, state machines, and business rules
- SPEC-003 defines AI intelligence layer and telemetry
- SPEC-004 defines memory lifecycle and auditing
- SPEC-005 defines conversation orchestration
- SPEC-006 defines communication channels
- SPEC-007 defines application API (including Admin API surface)
- SPEC-008 defines scheduling and automation
- SPEC-009 (this document) defines the administrative operations, analytics, and observability requirements that CONSUME data produced by all other specifications

**Ownership principle:** The Administration layer does NOT own business logic, state machines, AI behavior, memory lifecycle, conversation orchestration, communication delivery, or scheduling. It is a consumer of data and an invoker of privileged operations exposed through the Application API (SPEC-007). Specifically:
- Administrative mutations (status overrides, deletions, setting changes) are executed through the Admin API defined in SPEC-007
- Analytical data is computed from domain events, telemetry, and audit logs produced by SPEC-002 through SPEC-008
- Operational alerts are triggered by thresholds defined in the relevant owning specification (SPEC-003 for AI health, SPEC-004 for memory capacity, SPEC-005 for pipeline failures, SPEC-006 for delivery health, SPEC-008 for schedule coverage)

This specification defines the REQUIREMENTS for what must be observable and manageable — the Admin API (SPEC-007) defines the access contract.

## Glossary

- **Administrator**: A human user with elevated access to the Dad Coach platform for support, monitoring, or management purposes
- **Admin_Role**: A named permission set assigned to administrators (SUPPORT, OPERATIONS, ANALYTICS, SUPER_ADMIN)
- **Support_Case**: A record of an administrative intervention on behalf of a father
- **Operational_Dashboard**: A real-time view of system health, performance, and business metrics
- **Business_Metric**: A computed value measuring coaching effectiveness, engagement, or growth
- **Cohort**: A group of fathers sharing a common characteristic (registration date, phase, engagement level) for comparative analysis
- **Alert**: An operational notification triggered when a metric crosses a defined threshold
- **Report**: A scheduled or on-demand aggregation of data over a time period
- **Audit_Entry**: A record of an administrative action for compliance and accountability
- **Data_Export**: A structured output of system data for external analysis or compliance
- **Search_Query**: A request to find entities matching specified criteria across the platform
- **Privacy_Boundary**: A restriction on what data an administrator may view or export based on their role and the data's sensitivity classification

---

## Requirements

### Requirement 1: Administration Ownership and Boundaries

**User Story:** As a product owner, I want administrative capabilities clearly bounded, so that the administration layer never duplicates or conflicts with business logic owned by other specifications.

#### Acceptance Criteria

1. THE Administration layer SHALL act exclusively as a consumer of data and an invoker of operations. It SHALL NOT:
   - Define or enforce domain state machines (owned by SPEC-002)
   - Generate AI content or modify prompts (owned by SPEC-003)
   - Create, score, or consolidate memories (owned by SPEC-004)
   - Orchestrate conversations or manage pipeline execution (owned by SPEC-005)
   - Deliver messages to fathers or manage provider connections (owned by SPEC-006)
   - Define API contracts or authentication (owned by SPEC-007)
   - Define scheduling cadences or trigger timing (owned by SPEC-008)

2. THE Administration layer SHALL invoke privileged operations exclusively through the Admin API surface defined in SPEC-007 Requirement 3-5. No administrative action bypasses the API validation and audit mechanisms.

3. THE Administration layer SHALL consume analytics data derived from:
   - Domain events published by SPEC-005 Requirement 11
   - AI telemetry produced by SPEC-003 Requirement 16
   - Memory audit logs produced by SPEC-004 Requirement 18
   - Communication delivery metrics produced by SPEC-006 Requirement 13
   - Scheduling metrics produced by SPEC-008 Requirement 15
   - API audit logs produced by SPEC-007 Requirement 14

4. THE Administration layer SHALL define WHAT metrics, reports, and views are required. The computation and storage of these metrics is a Tech Design concern.

---

### Requirement 2: Administrator Roles and Permissions

**User Story:** As a product owner, I want role-based access for administrators, so that each team member sees only what they need and cannot perform actions beyond their responsibility.

#### Acceptance Criteria

1. THE Administration layer SHALL define the following Admin_Roles:

   | Role | Purpose | Permissions |
   |------|---------|------------|
   | SUPPORT | Handle father support cases, view conversation history, assist with account issues | Read any father's profile, conversations, missions, memories; initiate pause/resume; trigger data export; cannot delete or override |
   | OPERATIONS | Monitor system health, investigate failures, manage communication channels | All SUPPORT permissions + view AI telemetry, delivery metrics, scheduling health, system alerts; can override father settings |
   | ANALYTICS | Analyze coaching effectiveness, produce reports, track business metrics | Read-only access to aggregated and anonymized data; cannot view individual conversation content; can access cohort analysis |
   | SUPER_ADMIN | Full system access for critical operations | All permissions from all roles + account deletion, data purge, role assignment, system configuration |

2. THE Administration layer SHALL enforce permission checks on every administrative action. Actions attempted without sufficient permission SHALL be denied and audit-logged.

3. THE Administration layer SHALL support assigning multiple roles to a single administrator (e.g., SUPPORT + ANALYTICS).

4. THE Administration layer SHALL audit all role assignments and permission changes with: who_changed, what_changed, when, and authorization (which SUPER_ADMIN approved).

5. THE Administration layer SHALL enforce the principle of least privilege: no role grants more access than required for its stated purpose.

---

### Requirement 3: Support Operations

**User Story:** As a support agent, I want to view a father's complete context and perform common support actions, so that I can help fathers quickly without needing engineering intervention.

#### Acceptance Criteria

1. THE Administration layer SHALL enable SUPPORT agents to perform the following operations:

   | Operation | Description | Conditions |
   |-----------|------------|-----------|
   | View Father Profile | Full profile including status, phase, preferences, metrics | Any father |
   | View Conversation History | Complete message history for any conversation | Excludes system prompts and AI internal context |
   | View Active Conversation | Current ACTIVE conversation state and messages | Real-time view |
   | View Memory Summary | Grouped memory listing by category with confidence indicators | Excludes embeddings and internal scoring |
   | View Mission History | All missions with status, outcomes, and difficulty progression | Includes expired and skipped |
   | View Schedule | Father's upcoming automation triggers | Next 7 days |
   | Initiate Pause | Pause a father's account (1-30 days) | Requires reason logged |
   | Resume from Pause | Resume a paused father immediately | — |
   | Trigger Data Export | Initiate GDPR data export for a father | Per SPEC-004 Req 17 criteria 7 |
   | View Delivery Status | Communication delivery history for recent messages | Last 30 days |
   | View Safety Events | Safety-event records (restricted, per SPEC-004 Req 24) | CRISIS and CHILD_SAFETY events only; no normal memories |

2. WHEN a SUPPORT agent performs any action on a father's account, THE Administration layer SHALL create a Support_Case record with: agent_id, father_id, action_performed, reason, timestamp

3. THE Administration layer SHALL provide SUPPORT agents with a unified father context view that combines: current status, active conversation summary, active mission, recent memories (top 10 by importance), engagement trend (7-day), and any pending alerts or safety flags

4. THE Administration layer SHALL restrict SUPPORT agents from: viewing raw AI prompts, modifying AI behavior, deleting accounts, overriding father status to DELETED or CHURNED, or accessing other administrators' actions

---

### Requirement 4: Father Management

**User Story:** As an operations manager, I want to manage father accounts at scale, so that I can handle operational needs like bulk status checks, engagement monitoring, and intervention prioritization.

#### Acceptance Criteria

1. THE Administration layer SHALL provide father management operations:

   | Operation | Description | Required Role |
   |-----------|------------|--------------|
   | Search Fathers | By name, phone (partial), status, phase, engagement range, registration date | SUPPORT+ |
   | Filter Fathers | By status, coaching_phase, engagement_score ranges, last_active range | SUPPORT+ |
   | Bulk View Status | Aggregate counts by status (ACTIVE, PAUSED, CHURNED, etc.) | OPERATIONS+ |
   | View At-Risk Fathers | Fathers with declining engagement (7-day downtrend > 20%) | OPERATIONS+ |
   | View Churning Fathers | Fathers in INACTIVITY_7DAY or INACTIVITY_14DAY stage | SUPPORT+ |
   | Override Status | Force a status transition outside normal rules (with reason) | OPERATIONS+ |
   | Override Settings | Modify coaching_style, preferred_time, timezone for a father | OPERATIONS+ |
   | Initiate Deletion | Start account deletion process | SUPER_ADMIN |

2. WHEN an OPERATIONS user overrides a father's status, THE Administration layer SHALL:
   - Validate the transition is not to DELETED (requires SUPER_ADMIN)
   - Log the override with: operator_id, father_id, from_status, to_status, reason, timestamp
   - Notify the affected subsystems (scheduling, conversation engine) of the status change via standard domain events

3. THE Administration layer SHALL provide engagement distribution analysis: histogram of engagement_scores across all active fathers, identifying the distribution shape and outliers

4. THE Administration layer SHALL flag fathers requiring attention:
   - Safety escalation pending (unreviewed CRISIS or CHILD_SAFETY events)
   - Capacity full (500 memory limit reached)
   - Delivery failures (3+ consecutive failed message deliveries)
   - Unusually high message volume (50+ messages/day per SPEC-003 Req 9 criteria 10)

---

### Requirement 5: Conversation Inspection

**User Story:** As a support agent, I want to inspect any conversation in detail, so that I can understand what happened during a coaching interaction and identify issues.

#### Acceptance Criteria

1. THE Administration layer SHALL provide conversation inspection capabilities:
   - View full message history (father messages and system responses, in order)
   - View conversation metadata (type, status, created_at, completed_at, message_count, duration)
   - View which memories were injected into each AI prompt (memory_ids referenced)
   - View AI model used and response latency for each exchange
   - View whether fallback responses were delivered (and why)
   - View safety classifications applied to father messages

2. THE Administration layer SHALL NOT expose to administrators:
   - Raw system prompts or persona instructions (owned by SPEC-003, AI engineering only)
   - Raw AI model outputs before validation (internal debugging only)
   - Memory embedding vectors or similarity scores (internal)
   - Token counts or cost data per conversation (available through AI telemetry for OPERATIONS role)

3. THE Administration layer SHALL enable conversation search:
   - By father (all conversations for a father)
   - By type (all DIFFICULT_SITUATION conversations across fathers)
   - By status (all currently ACTIVE conversations)
   - By date range
   - By safety classification (conversations that contained non-SAFE messages)

4. THE Administration layer SHALL provide a real-time active conversations view showing: count of currently ACTIVE conversations, age distribution (how long each has been active), and type breakdown

---

### Requirement 6: Memory Inspection

**User Story:** As a support agent, I want to inspect a father's memory state, so that I can understand what the system knows and identify incorrect or problematic memories.

#### Acceptance Criteria

1. THE Administration layer SHALL provide memory inspection for SUPPORT+ roles:
   - List all ACTIVE and CONFIRMED memories for a father (grouped by category)
   - View memory detail: content, category, importance, confidence, state, source_type, created_at, last_accessed_at, confirmation_count
   - View memory conflict groups (conflicting memories linked together)
   - View memory version history (prior content versions)
   - View capacity utilization (active_count / 500)

2. THE Administration layer SHALL provide memory analysis for OPERATIONS+ roles:
   - Memory audit log for a father (all operations on their memories)
   - Decay history (which memories decayed and when)
   - Consolidation history (which memories were merged)
   - Extraction history (memories created per conversation)

3. THE Administration layer SHALL NOT expose:
   - Memory embeddings (vector data)
   - Retrieval scores or ranking computations
   - Other fathers' memories (always scoped to one father at a time)

4. THE Administration layer SHALL flag memories that may need attention:
   - Memories with confidence < 0.3 that are still ACTIVE (candidates for expiration)
   - Conflict groups unresolved for > 14 days
   - IDENTITY memories with confidence < 1.0 (potentially incorrect hard facts)
   - Sensitive memories (HIGH sensitivity per SPEC-004 Req 17 criteria 9)

---

### Requirement 7: Mission and Goal Administration

**User Story:** As an operations user, I want to inspect mission and goal performance, so that I can evaluate coaching effectiveness and identify systematic issues.

#### Acceptance Criteria

1. THE Administration layer SHALL provide mission inspection:
   - View any father's mission history (with filtering by status, category, child, difficulty, date)
   - View mission outcome distribution (completion rate by category, by difficulty, by phase)
   - View mission generation patterns (categories assigned, difficulty trends)
   - View consecutive skip/expire patterns per father (identifying disengagement)

2. THE Administration layer SHALL provide goal inspection:
   - View all goals for a father (active, completed, archived)
   - View goal progress over time
   - View goal-mission linkage (which missions contribute to which goals)

3. THE Administration layer SHALL provide aggregate mission analytics (ANALYTICS role):
   - System-wide mission completion rate (overall and by category)
   - Average outcome_rating by category, difficulty, and coaching_phase
   - Most/least completed mission categories
   - Difficulty distribution across active fathers
   - Mission completion rate by time of week (weekday vs weekend effectiveness)

4. THE Administration layer SHALL NOT enable manual mission creation, modification, or completion through administrative interfaces. Missions are exclusively managed by the coaching flow (SPEC-005 + SPEC-003). Administrators can only observe.

---

### Requirement 8: Operational Monitoring

**User Story:** As an operations user, I want real-time system health visibility, so that I can detect and respond to issues before they impact fathers.

#### Acceptance Criteria

1. THE Administration layer SHALL provide real-time operational views (OPERATIONS+ role):

   | View | Content | Update Frequency |
   |------|---------|-----------------|
   | System Health | Application liveness, readiness, subsystem status | Every 30 seconds |
   | AI Health | Model availability, error rates, latency, fallback usage | Every 60 seconds |
   | Communication Health | Delivery success rate, provider status, failure rate by type | Every 60 seconds |
   | Scheduling Health | Trigger fire rate, miss rate, coverage percentage, delay average | Every 60 seconds |
   | Memory Health | Consolidation job status, extraction queue depth, capacity alerts | Every 5 minutes |
   | Conversation Pipeline | Active conversations count, average response latency, fallback rate | Every 30 seconds |

2. THE Administration layer SHALL provide trend views for each operational metric: current value, 1-hour trend, 24-hour trend, 7-day trend. Trends indicate IMPROVING, STABLE, or DEGRADING.

3. THE Administration layer SHALL display active alerts with: severity (CRITICAL, WARNING, INFO), triggering metric, threshold crossed, time triggered, and acknowledged status

4. THE Administration layer SHALL consume alerts defined by owning specifications:
   - AI alerts from SPEC-003 Requirement 16 criteria 4
   - Communication alerts from SPEC-006 Requirement 13 criteria 2
   - Scheduling alerts from SPEC-008 Requirement 15 criteria 2
   - Memory alerts from SPEC-004 Requirement 20 criteria 14
   The Administration layer displays and tracks these alerts; it does not define the triggering thresholds (those are owned by the respective specs).

5. THE Administration layer SHALL support alert acknowledgment: an OPERATIONS user can acknowledge an alert, recording who acknowledged and when. Acknowledged alerts stop escalating but remain visible until resolved.


---

### Requirement 9: Business Analytics

**User Story:** As a product owner, I want business metrics computed and available for analysis, so that I can make data-driven decisions about product development and coaching effectiveness.

#### Acceptance Criteria

1. THE Administration layer SHALL compute and provide the following business metrics (ANALYTICS+ role):

   | Metric | Definition | Granularity |
   |--------|-----------|-------------|
   | Active Fathers | Count of fathers in ACTIVE status | Daily snapshot |
   | New Registrations | Fathers completing onboarding per period | Daily, weekly, monthly |
   | Churn Rate | Fathers transitioning to CHURNED / active fathers at period start | Weekly, monthly |
   | Retention Rate | Fathers remaining ACTIVE after N days (7, 14, 30, 60, 90) | Cohort-based |
   | Engagement Distribution | Histogram of engagement_scores across active fathers | Daily snapshot |
   | Mission Completion Rate | Missions COMPLETED / missions ASSIGNED per period | Daily, weekly, monthly |
   | Average Outcome Rating | Mean outcome_rating of completed missions | Weekly, monthly |
   | Coaching Phase Distribution | Count of fathers in each phase (FOUNDATION, BUILDING, DEEPENING, MASTERY) | Daily snapshot |
   | Phase Progression Rate | Average days to reach each phase | Monthly |
   | Conversation Continuation Rate | Percentage of AI messages receiving a father response within 24h | Weekly |
   | Weekly Summary Engagement | Percentage of weekly summaries that receive a father response | Weekly |

2. THE Administration layer SHALL support cohort analysis:
   - Group fathers by registration week/month
   - Compare retention curves across cohorts
   - Compare engagement_score trends across cohorts
   - Compare mission completion rates across cohorts
   - Identify which cohort behaviors correlate with long-term retention

3. THE Administration layer SHALL provide time-series analysis for all business metrics: daily, weekly, and monthly aggregations with year-over-year comparison when sufficient data exists.

4. THE Administration layer SHALL compute metrics from domain events and state — never by querying raw conversation content or AI outputs. Metrics are derived from structured data (statuses, scores, timestamps, counts).

5. THE ANALYTICS role SHALL access ONLY aggregated and anonymized data. Individual father conversations, memory content, or personally identifiable information SHALL NOT be accessible to the ANALYTICS role.

---

### Requirement 10: Coaching Effectiveness Metrics

**User Story:** As a product owner, I want to measure coaching quality, so that I can continuously improve the coaching methodology and AI behavior.

#### Acceptance Criteria

1. THE Administration layer SHALL compute coaching effectiveness metrics:

   | Metric | Definition | Target (from SPEC-003 Req 12) |
   |--------|-----------|------|
   | Mission Completion Rate | Completed / Assigned (30-day rolling) | > 60% |
   | Average Outcome Rating | Mean rating of completed missions (30-day) | > 3.5/5 |
   | Conversation Continuation Rate | AI messages receiving response within 24h | > 40% |
   | Streak Retention | Percentage of active fathers with 7+ day streaks | > 30% |
   | Phase Progression Speed | Average days to reach BUILDING phase | < 20 days |
   | Monthly Churn Rate | Fathers churning per month / active at month start | < 15% |

2. THE Administration layer SHALL provide effectiveness breakdown by:
   - Coaching_style (GENTLE vs BALANCED vs DIRECT vs MOTIVATIONAL)
   - Coaching_phase (FOUNDATION vs BUILDING vs DEEPENING vs MASTERY)
   - Number of children (1, 2, 3+)
   - Child age bracket (0-3, 4-6, 7-10, 11-14, 15-18)
   - Mission category (CONNECTION, COMMUNICATION, PLAY, etc.)

3. THE Administration layer SHALL provide AI quality metrics (OPERATIONS+ role):
   - Response quality score average (per SPEC-003 Requirement 12 criteria 3)
   - Validation failure rate by interaction type
   - Fallback usage rate
   - Safety escalation rate
   - Cost per father per month (AI token costs)

4. THE Administration layer SHALL identify concerning patterns:
   - Mission categories with < 40% completion rate (coaching methodology issue)
   - Fathers whose engagement_score has declined for 3+ consecutive weeks
   - Conversation types with < 30% continuation rate (content quality issue)
   - Phase transitions taking > 2× the average duration

5. THE Administration layer SHALL support A/B test result viewing (per SPEC-003 Requirement 8 criteria 3): which prompt version is winning, sample sizes, confidence intervals, and recommendation (promote/continue/rollback)

---

### Requirement 11: Search Capabilities

**User Story:** As a support agent, I want to search across all system entities quickly, so that I can find relevant information during support interactions without navigating multiple views.

#### Acceptance Criteria

1. THE Administration layer SHALL provide cross-entity search (SUPPORT+ role):
   - Search fathers by: display_name (partial match), phone number (partial, masked results), status, engagement range
   - Search conversations by: father, type, status, date range, safety classification
   - Search missions by: father, child, category, status, date range
   - Search memories by: father, category, content keywords, confidence range, state

2. THE Administration layer SHALL return search results within 3 seconds for standard queries (single-entity, single-filter searches)

3. THE Administration layer SHALL support result pagination for all search operations (per SPEC-007 Requirement 10 pagination rules)

4. THE Administration layer SHALL restrict search scope by role:
   - SUPPORT: can search individual fathers and their related entities
   - OPERATIONS: can search across all fathers with aggregate filters
   - ANALYTICS: can search aggregated data only (no individual father results)

5. THE Administration layer SHALL mask sensitive data in search results:
   - Phone numbers are always partially masked unless the operator has SUPER_ADMIN role
   - Memory content marked as HIGH sensitivity shows category and metadata only (not content) in search results
   - Conversation messages are not searchable by content (only metadata-based search)

---

### Requirement 12: Reporting

**User Story:** As a product owner, I want scheduled and on-demand reports, so that I can track progress and share insights with stakeholders without manual data gathering.

#### Acceptance Criteria

1. THE Administration layer SHALL support the following report types:

   | Report | Content | Schedule | Audience |
   |--------|---------|----------|----------|
   | Daily Operations Summary | Active fathers, conversations processed, messages sent/received, failures, alerts | Daily (configurable time) | OPERATIONS |
   | Weekly Business Report | New registrations, churn, engagement trends, mission stats, phase progression | Weekly (Monday) | ANALYTICS, SUPER_ADMIN |
   | Monthly Coaching Effectiveness | All effectiveness metrics, cohort analysis, AI quality summary | Monthly (1st) | ANALYTICS, SUPER_ADMIN |
   | Safety Incident Report | All CRISIS and CHILD_SAFETY events, resolution status, response times | Weekly | SUPER_ADMIN |
   | AI Cost Report | Token consumption, cost per father, model usage distribution | Monthly | OPERATIONS, SUPER_ADMIN |

2. THE Administration layer SHALL support on-demand report generation: any report type can be generated for an arbitrary date range (not just the scheduled period)

3. THE Administration layer SHALL retain generated reports for 1 year for historical comparison

4. THE Administration layer SHALL ensure reports contain ONLY aggregated data. No individual father identification appears in reports accessible to the ANALYTICS role. OPERATIONS and SUPER_ADMIN reports may reference individual fathers by identifier when investigating specific issues.

5. THE Administration layer SHALL support report export in structured format (for external analysis) as defined by the data export requirements (Requirement 13).

---

### Requirement 13: Data Export

**User Story:** As a product owner, I want data exportable for external analysis and compliance, so that I can perform advanced analytics and fulfill regulatory obligations.

#### Acceptance Criteria

1. THE Administration layer SHALL support the following export types:

   | Export Type | Content | Audience | Privacy Rules |
   |-------------|---------|----------|--------------|
   | Father Data Export (GDPR) | Single father's complete data (per SPEC-004 Req 17 criteria 7) | SUPPORT+ (initiated by father request) | Full personal data for the requesting father |
   | Anonymized Analytics Export | Aggregated metrics, cohort data, effectiveness scores | ANALYTICS+ | No PII, no individual identification possible |
   | Operational Export | System metrics, alert history, scheduling performance | OPERATIONS+ | No father PII in metrics |
   | Audit Export | Administrative action audit trail for a date range | SUPER_ADMIN | Contains admin identifiers but masks father PII |

2. WHEN a Father Data Export is requested (GDPR right to portability), THE Administration layer SHALL:
   - Include: father profile, all children profiles, all active and archived memories (content), all conversations (messages), all missions, all goals
   - Exclude: AI prompts, memory embeddings, internal scoring, system metadata
   - Deliver within 30 days (per SPEC-004 Requirement 17 criteria 7)
   - Format: structured, machine-readable (specific format is a Tech Design decision)

3. THE Anonymized Analytics Export SHALL ensure that no individual father can be re-identified from the exported data:
   - Remove all direct identifiers (name, phone, father_id)
   - Suppress groups smaller than 5 individuals (k-anonymity >= 5)
   - Aggregate timestamps to week granularity (no exact dates)

4. THE Administration layer SHALL log every data export with: requester, export_type, scope (date range, father_id if applicable), timestamp, and justification

5. THE Administration layer SHALL enforce that exports respect the data retention rules of their source specifications:
   - Memory content deleted per SPEC-004 is not available for export
   - Audit entries older than 2 years are not available (per SPEC-004 Requirement 18 criteria 3)
   - Delivery logs older than 90 days are not available (per SPEC-006 Requirement 13 criteria 3)

---

### Requirement 14: Audit Exploration

**User Story:** As an operations manager, I want to explore the audit trail, so that I can investigate issues, verify compliance, and understand what happened and when.

#### Acceptance Criteria

1. THE Administration layer SHALL provide audit exploration for OPERATIONS+ roles:
   - API audit trail (per SPEC-007 Requirement 14): all administrative API calls
   - Memory audit trail (per SPEC-004 Requirement 18): all memory operations for a father
   - State transition audit (per SPEC-002 Requirement 11 criteria 7): all entity state changes
   - Scheduling audit (per SPEC-008 Requirement 15 criteria 3): trigger history per father

2. THE Administration layer SHALL support audit search by:
   - Actor (who performed the action)
   - Subject (which father/entity was affected)
   - Operation type
   - Time range
   - Result (success/failure)

3. THE Administration layer SHALL provide audit timeline view: for a given father, show all events (state transitions, memory operations, conversations, administrative actions) in chronological order — enabling reconstruction of what happened and why

4. THE Administration layer SHALL enforce audit access restrictions:
   - SUPPORT: may view audit entries related to fathers they have active Support_Cases for
   - OPERATIONS: may view all audit entries
   - ANALYTICS: no access to individual audit entries (aggregated audit statistics only)
   - SUPER_ADMIN: full access including other administrators' audit trails

5. THE Administration layer SHALL retain audit entries per the retention policy of the source specification (2 years for memory and API audits). Audit entries SHALL NOT be modifiable or deletable by any role (append-only).

---

### Requirement 15: Operational Alerts

**User Story:** As an operations user, I want to be notified when system behavior deviates from expected thresholds, so that I can intervene before issues impact fathers.

#### Acceptance Criteria

1. THE Administration layer SHALL aggregate and present alerts from all subsystems:

   | Source | Alert Examples | Severity |
   |--------|---------------|----------|
   | SPEC-003 AI | Latency p95 > 10s, error rate > 5%, quality score < 0.7 | CRITICAL / WARNING |
   | SPEC-004 Memory | Extraction queue depth > 100, consolidation job failure, capacity_full fathers > 10 | WARNING |
   | SPEC-005 Pipeline | Fallback rate > 5%, average response > 20s, pipeline failure rate > 1% | CRITICAL / WARNING |
   | SPEC-006 Communication | Delivery success < 90%, circuit breaker tripped, template rejection > 5% | CRITICAL / WARNING |
   | SPEC-008 Scheduling | Daily coaching coverage < 80%, miss rate > 10%, trigger delay > 30min | WARNING |
   | Safety | Unreviewed CRISIS event > 4 hours, CHILD_SAFETY event > 2 hours | CRITICAL |

2. THE Administration layer SHALL categorize alerts by severity:
   - CRITICAL: Requires immediate attention; father experience actively degraded
   - WARNING: Trending toward degradation; action needed within hours
   - INFO: Notable but not urgent; review during next operational check

3. THE Administration layer SHALL support alert lifecycle:
   - TRIGGERED → ACKNOWLEDGED (operator confirms awareness) → RESOLVED (metric returns to normal) → CLOSED
   - Unacknowledged CRITICAL alerts SHALL escalate after 30 minutes (notification to next-level operator)
   - Alerts auto-resolve when the triggering metric returns within normal bounds

4. THE Administration layer SHALL provide alert history: past alerts with resolution time, acknowledging operator, and whether they resulted in Support_Cases or operational interventions

5. THE Administration layer SHALL support alert notification to operators through configurable channels (email, messaging). Specific notification mechanisms are a Tech Design decision.

---

### Requirement 16: Privacy and Access Restrictions

**User Story:** As a product owner, I want administrative access governed by strict privacy rules, so that father data is protected even from internal personnel.

#### Acceptance Criteria

1. THE Administration layer SHALL enforce the sensitivity classification defined in SPEC-004 Requirement 17 criteria 9:
   - HIGH sensitivity content (medical, custody, conflicts, mental health): visible only to SUPPORT and SUPER_ADMIN roles; never in aggregate analytics
   - MEDIUM sensitivity content (relationship dynamics, challenges): visible to SUPPORT+
   - LOW sensitivity content (interests, preferences, scheduling): visible to SUPPORT+

2. THE Administration layer SHALL enforce that safety-event records (per SPEC-004 Requirement 24) are accessible ONLY to the human escalation queue:
   - CRISIS and CHILD_SAFETY records: visible to SUPPORT (for review) and SUPER_ADMIN
   - Never visible to ANALYTICS or OPERATIONS (no aggregate statistics on safety events are produced)
   - Exception: SUPER_ADMIN receives the Safety Incident Report (Requirement 12) which contains aggregate counts only

3. THE Administration layer SHALL enforce data minimization for the ANALYTICS role:
   - No individual father identification possible from any view or export
   - No conversation message content accessible
   - No memory content accessible
   - Only aggregated counts, percentages, averages, and distributions

4. THE Administration layer SHALL enforce that SUPPORT agents can only view father data in the context of an active support interaction. Browsing random father accounts without a Support_Case SHALL be logged as a policy violation.

5. THE Administration layer SHALL enforce that no administrator can view their own audit trail modifications. An administrator's audit entries are visible to other operators of equal or higher role.

6. THE Administration layer SHALL enforce father consent boundaries: if a father has not consented to analytics participation (future opt-in feature), their data SHALL be excluded from aggregated analytics while still available for individual support.

---

### Requirement 17: Cross-Spec Compatibility

**User Story:** As an architect, I want explicit verification that the administration layer is compatible with all other specifications, so that no ownership conflicts exist.

#### Acceptance Criteria

1. THE Administration layer SHALL NOT define new alert thresholds. All alerting thresholds are defined by the owning specification:
   - AI thresholds: SPEC-003 Requirement 16 criteria 4
   - Communication thresholds: SPEC-006 Requirement 13 criteria 2
   - Scheduling thresholds: SPEC-008 Requirement 15 criteria 2
   - Memory thresholds: SPEC-004 Requirement 20 criteria 14 (operational metrics)
   The Administration layer aggregates, displays, and manages the lifecycle of these alerts.

2. THE Administration layer SHALL use the Admin API surface (SPEC-007 Requirements 3-5) for all mutating operations. No administrative action uses a private interface that bypasses the API layer's validation and audit mechanisms.

3. THE Administration layer SHALL consume business events published by the Conversation_Engine (SPEC-005 Requirement 11) and the Application API (SPEC-007 Requirement 16 criteria 10) to maintain real-time operational views without polling domain services.

4. THE Administration layer SHALL respect all data retention policies:
   - Memory audit: 2 years (SPEC-004 Requirement 18 criteria 3)
   - API audit: 2 years (SPEC-007 Requirement 14 criteria 3)
   - Delivery logs: 90 days (SPEC-006 Requirement 13 criteria 3)
   - Scheduling audit: 30 days (SPEC-008 Requirement 15 criteria 3)
   - Safety-event records: 30-90 days depending on type (SPEC-004 Requirement 24)
   Data no longer retained by the source is not available for administrative exploration.

5. THE Administration layer SHALL NOT duplicate metrics computation already performed by subsystems. Where SPEC-003 computes AI quality scores, or SPEC-002 defines engagement_score formulas, the Administration layer reads the computed values — it does not recompute them.

6. THE Administration layer SHALL respect the coaching effectiveness targets defined in SPEC-003 Requirement 12 criteria 1 as benchmark values for dashboards and reports. These targets are the product-defined goals; the Administration layer reports actuals against them.

7. THE Administration layer SHALL coordinate with SPEC-007's authentication and authorization model. Admin_Roles defined here correspond to the ADMIN actor type in SPEC-007 Requirement 6. The specific role-to-permission mapping is defined in this specification (Requirement 2); the enforcement mechanism is defined in SPEC-007.

8. THE Administration layer SHALL NOT create conversations, send messages, or interact with fathers. All father-facing communication flows through SPEC-005 → SPEC-006. Administrators observe and manage; they never coach.
