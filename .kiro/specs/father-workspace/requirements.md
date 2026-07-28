# Requirements Document

## Introduction

**SPEC-008: Father Workspace Backend**

This specification defines the backend services for the Father Workspace — the central hub that powers the father's experience after sign-in. It provides the API layer for workspace data aggregation, profile management, progress tracking, and the Father Growth System (a gamification/progression engine that motivates continuous improvement).

**This specification covers the backend only.** It defines REST API contracts, domain models, business logic, caching strategy, and performance requirements. Frontend implementation (web, mobile) is out of scope.

**Scope boundaries:**
- SPEC-001 defines infrastructure and deployment
- SPEC-002 defines domain entities, state machines, and business rules (Father, Child, Goal entities)
- SPEC-003 defines AI prompt assembly, model routing, and output contracts
- SPEC-004 defines memory lifecycle, storage, and retrieval
- SPEC-005 defines conversation orchestration
- SPEC-006 defines communication channels (WhatsApp delivery)
- SPEC-007 defines invitation, registration API, provisioning, and activation (backend)
- SPEC-008 (this document) defines the Father Workspace backend: workspace aggregation APIs and the Father Growth System

**Relationship to SPEC-002:** This spec reads from the domain entities defined in SPEC-002 (Father, Child, Goal, Mission). It does NOT own those entities or their state machines. The Father Growth System introduces NEW domain entities (Belt, Achievement, Milestone, Growth_Score) that are owned by this spec.

**Relationship to the Application API spec:** The existing Application API spec (application-api) defines CRUD operations on core resources (Father, Child, Goal, Mission, Conversation, Memory) and owns request validation, authorization, response shaping, and error formatting. This spec (Father Workspace) adds:
- **Read-only aggregation endpoints**: Workspace summary, children overview, goals overview, activity feed, statistics. These compose data from existing domain services into read-optimized responses. They do NOT duplicate CRUD operations.
- **Delegation to the Application API**: Profile updates, child CRUD, and goal CRUD are delegated to the Application API. The workspace exposes convenience read endpoints but mutating operations on core resources go through the existing Application API.
- **New domain capabilities**: The Father Growth System (belts, achievements, streaks, growth signals) and the Activity Reporting commands are entirely new and owned by this spec.

**Domain ownership clarification:**
- **Notifications**: Notification persistence and delivery is owned by the Communication Channels (SPEC-006). This spec READS notification state via service calls and provides a workspace-scoped summary view. It does NOT own notification creation or persistence.
- **Activity Feed**: Activity feed records are owned by THIS spec (SPEC-008 Father Workspace). Activity feed items are projected from domain events emitted by other specs. The Father Workspace writes and reads activity feed records.
- **Quick Actions**: Quick action generation logic is owned by THIS spec. Quick actions are computed on-demand from current state — no persistence required.
- **Statistics Aggregates**: Statistics aggregation and persistence is owned by THIS spec. Raw events come from other specs; computed aggregates are stored and served by the Father Workspace.
- **Celebration Events**: Celebration events are owned by the Growth System (THIS spec). They are created when growth milestones occur and include AI-generated encouragement metadata.

**Architecture principles:**
- Clean REST APIs consumable by any frontend (web, mobile, WhatsApp bot)
- Small, cohesive services — no God services
- Separate read models from write models where appropriate
- Aggregation/facade services only where they provide clear value
- Highly testable with clear boundaries
- Consistent error model with the Application API spec (application-api)

**MVP scope:** Requirements marked with *[MVP]* are mandatory for the initial implementation. Requirements marked with *[FUTURE]* document the full vision but may be implemented in subsequent iterations without architectural changes.

## Glossary

- **Father_Workspace**: The backend subsystem that aggregates and serves workspace data for an authenticated father
- **Workspace_Summary**: A read-optimized aggregation of a father's key metrics, children overview, active goals, and recent activity
- **Dashboard_Facade**: A service that composes data from multiple domain services into a single optimized response
- **Growth_System**: The gamification/progression subsystem that tracks a father's improvement journey through belts, achievements, and scores
- **Belt**: A progression tier in the Father Growth System representing the father's overall growth level (WHITE, YELLOW, ORANGE, GREEN, BLUE, PURPLE, BROWN, BLACK)
- **Belt_Progression**: The rules and thresholds defining how a father advances from one belt to the next
- **Growth_Score**: A non-negative, unbounded composite metric measuring a father's overall engagement, quality, and consistency. The score starts at 0 and accumulates indefinitely as growth signals are recorded. It does not decay.
- **Growth_Signal**: A discrete event or measurement that contributes positively to a father's Growth_Score (conversation depth, goal completion, consistency)
- **Achievement**: A predefined badge or reward earned by meeting specific criteria (e.g., "First Mission Completed", "7-Day Streak")
- **Milestone**: A predefined significant checkpoint in the father's journey (e.g., "Reached Green Belt", "100 Missions Completed")
- **Streak**: A count of consecutive calendar days where the father had at least one qualifying interaction
- **Weekly_Progress**: An aggregation of growth signals and metrics for a 7-day period (Monday-Sunday)
- **Monthly_Progress**: An aggregation of growth signals and metrics for a calendar month
- **Celebration_Event**: A metadata record representing a significant moment worth celebrating (level-up, streak milestone, achievement earned)
- **Encouragement_Metadata**: AI-generated motivational content (celebration messages, motivational prompts) associated with growth events
- **Activity_Feed**: A chronological list of recent events and interactions for a father
- **Quick_Action**: A metadata descriptor for a suggested next action the father can take
- **Notification_Summary**: An aggregated view of a father's pending and recent notifications
- **Cache_TTL**: Time-to-live for cached workspace data before invalidation
- **Signal_Weight**: The relative contribution of a Growth_Signal type to the Growth_Score calculation

---

## Requirements

### Requirement 1: Workspace Summary Aggregation API *[MVP]*

**User Story:** As a father, I want a single API call that returns my workspace overview, so that the frontend can render my home screen efficiently without multiple round-trips.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose a workspace summary endpoint that returns: father display_name, coaching_phase, current belt, growth_score, active_children_count, active_goals_count, current_streak_days, active_mission (if any), last_conversation_timestamp, and unread_notifications_count.

2. WHEN the authenticated father requests the workspace summary, THE Father_Workspace SHALL compose the response by reading from Father (SPEC-002), Growth_System, and Notification sources without duplicating domain logic.

3. THE Father_Workspace SHALL return the workspace summary within 300ms at the 95th percentile under normal load.

4. THE Father_Workspace SHALL cache the workspace summary with a TTL of 60 seconds. Mutations to underlying data (mission completion, goal update, notification received) SHALL invalidate the cache for the affected father.

5. IF any downstream service is unavailable, THEN THE Father_Workspace SHALL return a partial response with available data and indicate which sections are degraded, rather than failing the entire request.

---

### Requirement 2: Father Profile API *[MVP]*

**User Story:** As a father, I want to view my profile summary through the workspace, so that I can see my settings at a glance.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose a profile read endpoint that returns: display_name, phone (masked), timezone, coaching_style, preferred_coaching_time, language_preference, coaching_phase, days_since_activation, and account_status. This is a READ-ONLY aggregation view composed from Father (SPEC-002) and preferences (SPEC-007).

2. THE Father_Workspace SHALL NOT expose a profile update endpoint. Profile mutations are performed through the existing Application API resource endpoints. The workspace provides a read view only.

3. WHEN the Father_Workspace detects a FATHER_PROFILE_UPDATED domain event, THE Father_Workspace SHALL invalidate the workspace summary cache and the profile read cache for the affected father.

4. THE Father_Workspace SHALL log all profile read access to the audit trail for access tracking purposes.

---

### Requirement 3: Children Overview API *[MVP]*

**User Story:** As a father, I want to see a summary of all my children with their key information, so that I can quickly understand each child's coaching context.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose a children overview endpoint that returns a read-only list of all active children for the authenticated father, each including: child_id, name, computed_age, active_goals_count, completed_missions_count, recent_mission (most recent), and interests. This is a read-optimized aggregation — child CRUD is performed through the Application API.

2. THE Father_Workspace SHALL expose a child summary endpoint that returns a read-only aggregation: name, birth_date, computed_age, interests, challenges, active_goals (with progress), mission_history_summary (total, completed, skipped), and upcoming_birthday indicator (within 7 days). This does NOT duplicate the Application API child detail endpoint — it augments with cross-domain aggregated metrics.

3. THE Father_Workspace SHALL compute child age dynamically from birth_date, consistent with SPEC-002 Requirement 2 criteria 3.

4. THE Father_Workspace SHALL cache the children overview with a TTL of 120 seconds. Child creation, update, or archival (events from Application API) SHALL invalidate the cache.

5. THE Father_Workspace SHALL enforce ownership: a father can only retrieve children belonging to their own account. Requests for another father's children SHALL return Resource Not Found.

---

### Requirement 4: Goals Overview API *[MVP]*

**User Story:** As a father, I want to see all my parenting goals with their progress, so that I can track what I'm working toward.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose a goals overview endpoint that returns a read-only list of all active goals for the authenticated father, each including: goal_id, description, category, priority, progress_percentage, related_child (if linked), missions_completed_count, and missions_remaining_estimate. Goal CRUD is performed through the Application API.

2. THE Father_Workspace SHALL expose a goal progress summary endpoint that returns a read-only aggregation: goal description, category, priority, creation_date, progress_percentage, related_missions (paginated), milestones_reached, and suggested_next_steps metadata. This augments the Application API goal detail with workspace-specific aggregated metrics.

3. THE Father_Workspace SHALL calculate goal progress_percentage as a READ MODEL: completed_missions / estimated_missions * 100, capped at 100%. IF estimated_missions is zero or unavailable for a goal's category, THEN progress_percentage SHALL be calculated as: min(completed_missions / 10, 100)% using a default estimate of 10 missions. This is a workspace display value and does NOT override or conflict with any authoritative progress tracked by SPEC-002.

4. THE Father_Workspace SHALL support filtering goals by: status (active, completed, archived), category, and child_id.

5. THE Father_Workspace SHALL cache goal overview data with a TTL of 120 seconds. Goal creation, update, archival, or mission completion (events from Application API and SPEC-002) SHALL invalidate the cache.

---

### Requirement 5: Active Missions and Recent Conversations API *[MVP]*

**User Story:** As a father, I want to see my current mission and recent coaching conversations, so that I can stay engaged with my coaching journey.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose an active missions endpoint that returns all missions with status ASSIGNED, ACCEPTED, or IN_PROGRESS for the authenticated father, each including: mission_id, title, description, assigned_child, category, difficulty_level, assigned_at, and status.

2. THE Father_Workspace SHALL expose a recent conversations endpoint that returns the most recent conversations (default: 10, max: 50), each including: conversation_id, type, started_at, last_message_at, message_count, summary (if available), and status.

3. THE Father_Workspace SHALL NOT expose system prompt content, AI telemetry, or internal orchestration metadata in conversation responses — only father-visible message content.

4. THE Father_Workspace SHALL return active missions within 200ms at the 95th percentile.

5. THE Father_Workspace SHALL return recent conversations within 500ms at the 95th percentile.

---

### Requirement 6: Activity Feed API *[FUTURE]*

**User Story:** As a father, I want a timeline of my recent activities and events, so that I can see my coaching journey unfold chronologically.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose an activity feed endpoint that returns a chronological list of recent events for the authenticated father, ordered by timestamp descending.

2. THE Father_Workspace SHALL include the following event types in the activity feed: MISSION_ASSIGNED, MISSION_COMPLETED, GOAL_CREATED, GOAL_PROGRESS_UPDATE, CONVERSATION_COMPLETED, ACHIEVEMENT_EARNED, MILESTONE_REACHED, BELT_LEVEL_UP, STREAK_MILESTONE, and CHILD_BIRTHDAY.

3. EACH activity feed item SHALL contain: event_id, event_type, timestamp, title (human-readable), description, related_entity_id, and related_entity_type.

4. THE Father_Workspace SHALL support cursor-based pagination for the activity feed with a default page size of 20 and a maximum of 50 items per page.

5. THE Father_Workspace SHALL retain activity feed items for 90 days. Items older than 90 days are excluded from the feed but remain in the audit trail.

6. THE Father_Workspace SHALL return the activity feed within 500ms at the 95th percentile.

---

### Requirement 7: Notifications Summary API *[MVP]*

**User Story:** As a father, I want to see my pending notifications and recent alerts, so that I don't miss important coaching moments.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose a notifications summary endpoint that returns: unread_count, total_count (last 30 days), and a paginated list of notifications ordered by creation_date descending.

2. EACH notification item SHALL contain: notification_id, type, title, body, created_at, read_at (null if unread), action_url (optional deep link), and priority (HIGH, MEDIUM, LOW).

3. THE Father_Workspace SHALL expose a mark-as-read endpoint that accepts a list of notification_ids and transitions them to read status.

4. THE Father_Workspace SHALL expose a mark-all-read endpoint that marks all unread notifications for the authenticated father as read.

5. THE Father_Workspace SHALL cache the unread_count with a TTL of 30 seconds. New notification delivery SHALL invalidate the cache.

6. THE Father_Workspace SHALL return the notifications summary within 300ms at the 95th percentile.

---

### Requirement 8: Statistics APIs *[FUTURE]*

**User Story:** As a father, I want to see my weekly and monthly statistics, so that I can understand my engagement patterns and growth over time.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose a weekly statistics endpoint that returns data for a specified week (defaults to current week, Monday-Sunday): missions_assigned, missions_completed, missions_skipped, conversations_count, total_conversation_minutes, goals_progressed, growth_score_delta, streak_days_this_week, and quality_time_minutes (if reported).

2. THE Father_Workspace SHALL expose a monthly statistics endpoint that returns data for a specified month: missions_completed, goals_completed, conversations_count, average_daily_engagement, growth_score_at_month_start, growth_score_at_month_end, belt_changes, achievements_earned, and longest_streak_in_month.

3. THE Father_Workspace SHALL support retrieving historical statistics for any past week or month within the father's account lifetime (up to 2 years of history).

4. THE Father_Workspace SHALL compute statistics asynchronously via a nightly aggregation job (02:00 UTC) for the previous day. Current-day statistics are computed on-demand from raw events.

5. THE Father_Workspace SHALL cache weekly statistics with a TTL of 300 seconds (5 minutes). Monthly statistics SHALL be cached with a TTL of 3600 seconds (1 hour) since they change infrequently.

6. THE Father_Workspace SHALL return statistics endpoints within 500ms at the 95th percentile.

---

### Requirement 9: Quick Actions Metadata API *[FUTURE]*

**User Story:** As a father, I want contextual suggestions for what to do next, so that I always know how to engage with my coaching.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose a quick actions endpoint that returns an ordered list of suggested next actions based on the father's current state.

2. THE Father_Workspace SHALL generate quick actions based on these signals: active mission available (suggest "View Mission"), no active mission (suggest "Request New Mission"), unread notifications (suggest "Check Notifications"), goal nearing completion (suggest "Review Goal Progress"), streak at risk (suggest "Log Today's Interaction"), and new achievement available (suggest "View Achievement").

3. EACH quick action item SHALL contain: action_id, action_type, title, description, priority (1-10), and action_metadata (opaque JSON for the frontend to interpret).

4. THE Father_Workspace SHALL return a maximum of 5 quick actions, ordered by priority descending.

5. THE Father_Workspace SHALL compute quick actions on-demand (not cached) to ensure they reflect the father's current state accurately.

6. THE Father_Workspace SHALL return quick actions within 300ms at the 95th percentile.

---

### Requirement 10: Belt Progression System *[MVP]*

**User Story:** As a father, I want to see my current level and progress toward the next level, so that I feel motivated to continue improving.

#### Acceptance Criteria

1. THE Growth_System SHALL define eight progression belts in ascending order: WHITE (beginner), YELLOW, ORANGE, GREEN, BLUE, PURPLE, BROWN, BLACK (mastery).

2. THE Growth_System SHALL assign each belt a Growth_Score threshold:

   | Belt | Min Score | Max Score | Description |
   |------|-----------|-----------|-------------|
   | WHITE | 0 | 99 | Getting Started |
   | YELLOW | 100 | 249 | Building Habits |
   | ORANGE | 250 | 449 | Finding Rhythm |
   | GREEN | 450 | 699 | Growing Strong |
   | BLUE | 700 | 899 | Deep Connection |
   | PURPLE | 900 | 1049 | Advanced Father |
   | BROWN | 1050 | 1199 | Near Mastery |
   | BLACK | 1200 | ∞ | Master Father |

3. WHEN a father's Growth_Score crosses a belt threshold upward, THE Growth_System SHALL transition the father to the new belt and emit a BELT_LEVEL_UP celebration event.

4. THE Growth_System SHALL NOT allow belt regression. Once a father reaches a belt, they retain that belt even if their score temporarily drops below the threshold.

5. THE Growth_System SHALL expose a belt progression endpoint that returns: current_belt, current_score, next_belt, points_to_next_belt, progress_percentage_to_next_belt, and belt_earned_at timestamp.

6. THE Growth_System SHALL return belt progression data within 200ms at the 95th percentile.

---

### Requirement 11: Growth Score Calculation *[MVP]*

**User Story:** As a father, I want my growth score to reflect meaningful engagement rather than just time spent, so that I'm rewarded for quality interactions and real parenting improvement.

#### Acceptance Criteria

1. THE Growth_System SHALL compute the Growth_Score as a composite metric based on weighted growth signals. The score accumulates over time and does not decay.

2. THE Growth_System SHALL recognize the following Growth_Signal types with their Signal_Weights:

   | Signal Type | Points | Condition |
   |-------------|--------|-----------|
   | MISSION_COMPLETED | 10 | Mission transitions to COMPLETED status |
   | MISSION_REFLECTED | 5 (bonus) | Mission transitions to REFLECTED status (on top of completion) |
   | GOAL_PROGRESS | 15 | A goal advances by at least 10% progress |
   | GOAL_COMPLETED | 50 | A goal reaches 100% completion |
   | MEANINGFUL_CONVERSATION | 8 | A coaching conversation exceeds 5 exchanges and receives a quality rating above 0.6 |
   | DAILY_ENGAGEMENT | 3 | At least one qualifying interaction on a calendar day |
   | STREAK_BONUS_7 | 20 | Father's current streak reaches exactly 7 consecutive days (awarded once per streak) |
   | STREAK_BONUS_14 | 30 | Father's current streak reaches exactly 14 consecutive days (awarded once per streak) |
   | STREAK_BONUS_21 | 40 | Father's current streak reaches exactly 21 consecutive days (awarded once per streak) |
   | STREAK_BONUS_30 | 50 | Father's current streak reaches exactly 30 consecutive days (awarded once per streak) |
   | STREAK_BONUS_60 | 75 | Father's current streak reaches exactly 60 consecutive days (awarded once per streak) |
   | STREAK_BONUS_90 | 100 | Father's current streak reaches exactly 90 consecutive days (awarded once per streak) |
   | STREAK_BONUS_180 | 150 | Father's current streak reaches exactly 180 consecutive days (awarded once per streak) |
   | STREAK_BONUS_365 | 300 | Father's current streak reaches exactly 365 consecutive days (awarded once per streak) |
   | QUALITY_TIME_REPORTED | 12 | Father reports quality time with a child via the Activity Reporting API (Requirement 25) — minimum 15 minutes |
   | POSITIVE_ACTIVITY | 5 | Father reports a positive parenting activity via the Activity Reporting API (Requirement 25) — praise, shared activity, teaching moment |

3. THE Growth_System SHALL record each Growth_Signal as an immutable event with: signal_id, father_id, signal_type, points_awarded, source_entity_id, source_entity_type, and timestamp.

4. THE Growth_System SHALL recalculate the father's total Growth_Score by summing all recorded signals. The score is append-only — signals are never removed or modified after recording.

5. THE Growth_System SHALL support extensible signal types. New signal types can be added without modifying existing scoring logic or requiring data migration.

6. THE Growth_System SHALL prevent duplicate signal recording for the same source event. A mission completion SHALL generate exactly one MISSION_COMPLETED signal regardless of how many times the event is received.

7. THE Growth_System SHALL expose a growth score breakdown endpoint that returns: total_score, score_by_signal_type (aggregated), signals_this_week, signals_this_month, and recent_signals (last 10).

---

### Requirement 12: Streak Tracking *[MVP]*

**User Story:** As a father, I want to see my consistency streak and be motivated to maintain it, so that I build lasting parenting habits.

#### Acceptance Criteria

1. THE Growth_System SHALL track the father's current consecutive engagement streak: the number of consecutive calendar days (in the father's timezone) with at least one qualifying interaction.

2. THE Growth_System SHALL define qualifying interactions for streak purposes as: completing or reflecting on a mission, having a coaching conversation with at least 3 exchanges, reporting quality time, or logging a positive parenting activity.

3. THE Growth_System SHALL expose a streak endpoint that returns: current_streak_days, longest_streak_days, streak_start_date, last_qualifying_interaction_date, and streak_at_risk (true if no qualifying interaction today and it's past 18:00 in the father's timezone).

4. WHEN a father's streak reaches 7, 14, 21, 30, 60, 90, 180, or 365 consecutive days, THE Growth_System SHALL emit a STREAK_MILESTONE celebration event and award the corresponding streak bonus points.

5. WHEN a father misses a calendar day (no qualifying interaction by 23:59 in their timezone), THE Growth_System SHALL reset current_streak_days to 0 and begin a new streak on the next qualifying day.

6. THE Growth_System SHALL retain the longest_streak_days value permanently — it is never reset.

---

### Requirement 13: Achievements and Milestones *[MVP]*

**User Story:** As a father, I want to earn achievements and reach milestones, so that I feel recognized for my parenting efforts and can see tangible markers of progress.

#### Acceptance Criteria

1. THE Growth_System SHALL define achievements as predefined badges earned by meeting specific criteria. Each achievement has: achievement_id, name, description, category, criteria (machine-readable rule), and icon_key. Achievements do NOT directly award Growth_Score points. The underlying activity that triggers the achievement (mission completion, streak milestone, etc.) already awards points via Growth_Signals. Achievement earning is a recognition layer on top of the scoring system — it does not contribute additional points to avoid double-counting.

2. THE Growth_System SHALL define the following achievement categories: MISSIONS (mission-related achievements), CONSISTENCY (streak-related), GROWTH (belt/score-related), CONVERSATIONS (conversation-related), GOALS (goal-related), and SPECIAL (event-driven, seasonal).

3. THE Growth_System SHALL include at minimum these predefined achievements:

   | Achievement | Category | Criteria |
   |-------------|----------|----------|
   | First Steps | MISSIONS | Complete first mission |
   | Mission Master 10 | MISSIONS | Complete 10 missions |
   | Mission Master 50 | MISSIONS | Complete 50 missions |
   | Mission Master 100 | MISSIONS | Complete 100 missions |
   | Week Warrior | CONSISTENCY | 7-day streak |
   | Month Champion | CONSISTENCY | 30-day streak |
   | Quarter Legend | CONSISTENCY | 90-day streak |
   | Goal Getter | GOALS | Complete first goal |
   | Goal Crusher | GOALS | Complete 5 goals |
   | Deep Talker | CONVERSATIONS | 10 meaningful conversations |
   | Connection King | CONVERSATIONS | 50 meaningful conversations |
   | Rising Star | GROWTH | Reach Yellow Belt |
   | Green Machine | GROWTH | Reach Green Belt |
   | Elite Father | GROWTH | Reach Purple Belt |
   | Grandmaster | GROWTH | Reach Black Belt |

4. WHEN a father meets an achievement's criteria, THE Growth_System SHALL record the achievement as earned with an earned_at timestamp and emit an ACHIEVEMENT_EARNED celebration event.

5. THE Growth_System SHALL NOT revoke earned achievements. Once earned, an achievement is permanent regardless of subsequent activity changes.

6. THE Growth_System SHALL expose an achievements endpoint that returns: total_achievements_available, total_achievements_earned, achievements list (each with earned_at or null if not yet earned), and next_achievable (the closest unearned achievement the father is progressing toward).

7. THE Growth_System SHALL define milestones as significant journey checkpoints. Each milestone has: milestone_id, name, description, and trigger_condition. Milestones include belt transitions, total mission count thresholds (25, 50, 100, 250, 500), and account age markers (30 days, 90 days, 180 days, 1 year).

8. WHEN a father reaches a milestone, THE Growth_System SHALL record the milestone with a reached_at timestamp and emit a MILESTONE_REACHED celebration event.

---

### Requirement 14: Progress History and Celebration Events *[FUTURE]*

**User Story:** As a father, I want to see my growth journey over time and celebrate my wins, so that I stay motivated and appreciate how far I've come.

#### Acceptance Criteria

1. THE Growth_System SHALL expose a progress history endpoint that returns a chronological timeline of significant growth events: belt transitions, achievements earned, milestones reached, and notable streak records. The list is paginated (cursor-based, default 20 items).

2. THE Growth_System SHALL expose a celebration events endpoint that returns recent celebration events (last 30 days) that the frontend has not yet displayed, each including: event_id, event_type (BELT_LEVEL_UP, ACHIEVEMENT_EARNED, MILESTONE_REACHED, STREAK_MILESTONE), title, description, related_growth_signal_points (points from the underlying signal that triggered this event, if applicable), and displayed (boolean).

3. WHEN the frontend marks a celebration event as displayed, THE Growth_System SHALL update the displayed flag. Displayed events are excluded from subsequent queries unless explicitly requested.

4. THE Growth_System SHALL generate Encouragement_Metadata for each celebration event: a celebration_message (short congratulatory text) and a motivational_prompt (suggestion for next steps). These are generated via the Intelligence Layer (SPEC-003) asynchronously at event creation time.

5. THE Growth_System SHALL retain progress history indefinitely for the father's account lifetime. Celebration event display status is retained for 90 days.

---

### Requirement 15: Engagement and Quality Metrics *[FUTURE]*

**User Story:** As a father, I want to understand my engagement patterns and quality time metrics, so that I can identify areas for improvement in my parenting.

#### Acceptance Criteria

1. THE Growth_System SHALL track engagement metrics per father: conversations_this_week, conversations_this_month, total_conversations, average_conversation_depth (exchanges per conversation), logins_this_week, and total_active_days.

2. THE Growth_System SHALL track quality time metrics per father: quality_time_this_week_minutes, quality_time_this_month_minutes, activities_reported_this_week, and quality_time_by_child (breakdown per child).

3. THE Growth_System SHALL track goal and mission completion metrics: goals_completed_total, goals_in_progress, missions_completed_total, missions_completed_this_week, mission_completion_rate (completed / (completed + skipped + expired) over last 30 days).

4. THE Growth_System SHALL expose a metrics dashboard endpoint that returns all engagement, quality time, and completion metrics in a single response for the authenticated father.

5. THE Growth_System SHALL compute engagement metrics incrementally: events update running counters rather than recomputing from raw data on each request.

6. THE Growth_System SHALL return the metrics dashboard within 400ms at the 95th percentile.

---

### Requirement 16: Permission Model and Data Isolation *[MVP]*

**User Story:** As a product owner, I want strict data isolation between fathers, so that no father can ever access another father's workspace data.

#### Acceptance Criteria

1. THE Father_Workspace SHALL enforce resource ownership on every endpoint: each request SHALL verify that the target data belongs to the authenticated father. Access to another father's workspace data SHALL return Resource Not Found (not Forbidden — to prevent enumeration).

2. THE Father_Workspace SHALL require authentication for every endpoint. Unauthenticated requests SHALL be rejected immediately.

3. THE Father_Workspace SHALL support the same actor model as the existing Application API: FATHER (own data only), ADMIN (any father's data with appropriate permissions), and SERVICE (internal metrics and health).

4. THE Father_Workspace SHALL implement the ownership check at the service layer (not only at the controller layer), ensuring that even internal service calls respect data boundaries.

5. THE Father_Workspace SHALL log every access attempt (successful and failed) with: actor_type, actor_id, endpoint, target_father_id, timestamp, and result.

---

### Requirement 17: Caching Strategy *[MVP]*

**User Story:** As an architect, I want a well-defined caching strategy, so that the workspace API performs efficiently without serving stale data.

#### Acceptance Criteria

1. THE Father_Workspace SHALL implement per-father caching with the following TTLs:

   | Data Type | Cache TTL | Invalidation Trigger |
   |-----------|-----------|---------------------|
   | Workspace Summary | 60 seconds | Any mutation to father's data |
   | Children Overview | 120 seconds | Child create/update/archive |
   | Goals Overview | 120 seconds | Goal create/update/archive, mission completion |
   | Active Missions | 60 seconds | Mission status change |
   | Recent Conversations | 120 seconds | Conversation completed |
   | Notifications Summary | 30 seconds | New notification, mark-as-read |
   | Weekly Statistics | 300 seconds | Nightly aggregation job |
   | Monthly Statistics | 3600 seconds | Nightly aggregation job |
   | Belt Progression | 300 seconds | Growth signal recorded |
   | Streak Data | 120 seconds | Qualifying interaction recorded |
   | Achievements | 600 seconds | Achievement earned |
   | Metrics Dashboard | 120 seconds | Any growth signal recorded |

2. THE Father_Workspace SHALL use a cache key structure of `workspace:{father_id}:{data_type}` to enable granular invalidation per father per data type.

3. WHEN a domain event occurs that affects cached data, THE Father_Workspace SHALL invalidate only the affected cache entries for the affected father — not the entire cache.

4. THE Father_Workspace SHALL implement cache stampede protection: when multiple concurrent requests miss the cache for the same key, only one request populates the cache while others wait for the result.

5. IF the cache is entirely unavailable, THEN THE Father_Workspace SHALL serve requests directly from the source services with degraded performance, returning a cache-miss indicator in response headers for observability.

---

### Requirement 18: Performance Requirements *[MVP]*

**User Story:** As a product owner, I want performance expectations clearly defined per endpoint, so that the workspace meets user experience standards consistently.

#### Acceptance Criteria

1. THE Father_Workspace SHALL meet the following response time SLAs at the 95th percentile:

   | Endpoint Category | Target p95 Latency | Notes |
   |-------------------|-------------------|-------|
   | Workspace Summary | < 300ms | Aggregation from cache |
   | Profile Read/Update | < 300ms | Single entity |
   | Children/Goals Overview | < 400ms | List with computed fields |
   | Active Missions | < 200ms | Small result set |
   | Recent Conversations | < 500ms | May include message previews |
   | Activity Feed | < 500ms | Paginated timeline |
   | Notifications Summary | < 300ms | Count + recent list |
   | Statistics (Weekly/Monthly) | < 500ms | Pre-aggregated data |
   | Quick Actions | < 300ms | Computed on-demand |
   | Belt Progression | < 200ms | Single record |
   | Growth Score Breakdown | < 300ms | Aggregated signals |
   | Streak Data | < 200ms | Single record |
   | Achievements List | < 400ms | List with status |
   | Metrics Dashboard | < 400ms | Aggregated counters |
   | Health Check | < 100ms | Lightweight probe |

2. THE Father_Workspace SHALL enforce a maximum response payload size of 5 MB per endpoint. Responses exceeding this limit SHALL be paginated.

3. THE Father_Workspace SHALL enforce rate limits: 60 requests per minute per authenticated father, 300 per minute for ADMIN actors, 1000 per minute for SERVICE actors.

4. THE Father_Workspace SHALL reject requests with bodies exceeding 256 KB.

5. THE Father_Workspace SHALL support conditional requests (ETag-based) for frequently-polled endpoints (workspace summary, notifications count) to reduce payload transfer when data has not changed.

---

### Requirement 19: Error Handling *[MVP]*

**User Story:** As a product owner, I want consistent error responses across all workspace endpoints, so that frontend clients can build reliable error handling.

#### Acceptance Criteria

1. THE Father_Workspace SHALL return all errors in the same structure as the existing Application API (Requirement 9): error_code, message, details (optional array), request_id, and retryable boolean.

2. THE Father_Workspace SHALL define additional error codes specific to the Growth System:

   | Error Code | Description | Retryable |
   |------------|-------------|-----------|
   | GROWTH_SIGNAL_DUPLICATE | Duplicate signal for the same source event | No |
   | CELEBRATION_NOT_FOUND | Referenced celebration event does not exist | No |
   | STATISTICS_NOT_AVAILABLE | Requested time period has no computed statistics | No |
   | CACHE_DEGRADED | Response served without cache; may be slower | Yes |

3. THE Father_Workspace SHALL return partial responses when some data sources are unavailable. Partial response semantics:
   - The HTTP status SHALL remain 200 OK (the request itself succeeded; degradation is at the data level).
   - The response SHALL include a `degraded_sections` array listing section names that could not be populated (e.g., `["growth_system", "notifications"]`).
   - Unavailable sections SHALL be represented as `null` values in the response — they are NOT omitted and NOT replaced with defaults.
   - The response SHALL include a `response_status` field with value `"complete"` (all sections available) or `"partial"` (one or more sections degraded).

4. WHEN a downstream service times out (individual section timeout: 2 seconds), THE Father_Workspace SHALL mark that section as degraded and continue assembling the response from remaining available sources. The overall endpoint SHALL return within 5 seconds regardless of how many sections time out.

5. THE Father_Workspace SHALL NOT expose internal stack traces, service names, or infrastructure details in error responses.

---

### Requirement 20: API Versioning and Documentation *[MVP]*

**User Story:** As a product owner, I want workspace APIs versioned and documented, so that frontend teams can integrate reliably and new capabilities don't break existing clients.

#### Acceptance Criteria

1. THE Father_Workspace SHALL use the same versioning strategy as the existing Application API: explicit version identification, major versions for breaking changes, backward compatibility within a major version.

2. THE Father_Workspace SHALL launch at version 1. All endpoints SHALL include version identification.

3. THE Father_Workspace SHALL expose OpenAPI 3.0 documentation for all endpoints, including: request/response schemas, error codes, authentication requirements, and example payloads.

4. THE Father_Workspace SHALL maintain the OpenAPI specification as the source of truth for API contracts. Implementation SHALL be validated against the specification in CI.

5. THE Father_Workspace SHALL include deprecation warnings in responses when endpoints are scheduled for removal, with a minimum 6-month transition period.

---

### Requirement 21: Observability *[MVP]*

**User Story:** As an operations engineer, I want full observability into the workspace backend, so that I can monitor health, diagnose issues, and understand system behavior.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose health check endpoints (liveness and readiness) consistent with SPEC-001 Requirement 9, accessible without authentication.

2. THE Father_Workspace SHALL emit structured metrics for: request_count (by endpoint), request_latency_ms (by endpoint, p50/p95/p99), cache_hit_rate (by data type), cache_miss_rate (by data type), growth_signal_count (by type), error_count (by error_code), and active_fathers_today.

3. THE Father_Workspace SHALL propagate distributed trace context (trace_id, span_id) across all internal service calls, enabling end-to-end request tracing from the API layer through domain services to the database.

4. THE Father_Workspace SHALL log all requests with: request_id, endpoint, actor_id, response_status, latency_ms, cache_hit (boolean), and timestamp. Logs SHALL NOT contain request/response bodies or sensitive data.

5. THE Father_Workspace SHALL emit alerts when: p95 latency exceeds 2x the target SLA for 5 consecutive minutes, error rate exceeds 5% over a 5-minute window, or cache hit rate drops below 50% for 10 consecutive minutes.

---

### Requirement 22: Audit Logging *[MVP]*

**User Story:** As a product owner, I want all profile changes and significant workspace actions auditable, so that I can investigate issues and demonstrate data handling compliance.

#### Acceptance Criteria

1. THE Father_Workspace SHALL audit every mutating operation with: request_id, actor_type, actor_id, operation, resource_type, resource_id, timestamp, result (success/failure), and changed_fields (for updates).

2. THE Father_Workspace SHALL audit Growth_System mutations: growth_signal_recorded, achievement_earned, milestone_reached, belt_level_up, and celebration_displayed.

3. THE Father_Workspace SHALL retain audit logs for 2 years, consistent with the existing Application API audit retention policy.

4. THE Father_Workspace SHALL NOT log sensitive personal data in audit records. Only metadata (identifiers, types, timestamps) appears in audit logs.

5. THE Father_Workspace SHALL produce audit records in a format consumable by the future Administration & Analytics EPIC. Administration interfaces for searching and querying audit logs are OUT OF SCOPE for this spec.

---

### Requirement 23: Growth Signal Processing Architecture *[MVP]*

**User Story:** As an architect, I want growth signal processing decoupled from the API request path, so that score calculation never blocks user-facing operations and the system remains responsive.

#### Acceptance Criteria

1. THE Growth_System SHALL process growth signals asynchronously: domain events (mission completed, conversation ended, goal progressed) trigger signal recording via event listeners, not synchronous API calls.

2. THE Growth_System SHALL guarantee at-least-once delivery of growth signals from source events. Duplicate detection (Requirement 11 criteria 6) ensures exactly-once scoring.

3. THE Growth_System SHALL process growth signals within 5 seconds of the source event occurring under normal load. The father's Growth_Score, belt, and achievements SHALL reflect the signal within 10 seconds.

4. THE Growth_System SHALL record signal processing failures in a dead-letter queue for manual investigation. Failed signals SHALL NOT block subsequent signal processing.

5. THE Growth_System SHALL support bulk replay of historical events for score recalculation (e.g., when adding a new signal type retroactively). Bulk replay SHALL NOT affect the production event stream.

6. THE Growth_System SHALL expose a Service API endpoint for triggering signal replay for a specific father (administrative recalculation). This operation is idempotent due to duplicate detection.

7. **Scoring Policy Versioning:** Each Growth_Signal record SHALL include a `scoring_policy_version` field (integer, starting at 1) indicating which scoring rules were in effect when the signal was recorded. Existing awarded points are IMMUTABLE — they are never recalculated when scoring rules change. New scoring rules apply only to signals recorded after the rule change. When a new signal type is added retroactively via replay, the replay generates new signal records (with the new policy version) and duplicate detection prevents double-counting of signals already recorded under the previous policy. Full score rebuilds from raw events are NOT supported — the Growth_Score is always the sum of immutable signal records.

---

### Requirement 24: Testing Strategy *[MVP]*

**User Story:** As a developer, I want a comprehensive testing approach defined, so that the workspace backend is reliable, regression-free, and confidently deployable.

#### Acceptance Criteria

1. THE Father_Workspace SHALL define three testing tiers:
   - **Unit tests**: Individual service methods, score calculations, signal processing logic, cache invalidation logic. Target: 90%+ line coverage for business logic.
   - **Integration tests**: API endpoints with real database, cache integration, event processing pipelines. Target: all happy paths and critical error paths.
   - **Property-based tests**: Growth_Score calculation invariants, belt progression monotonicity, streak calculation correctness, duplicate signal idempotency.

2. THE Growth_System SHALL include property-based tests for:
   - Growth_Score is monotonically non-decreasing (score never decreases when a valid signal is added)
   - Belt progression is monotonically non-decreasing (belt never regresses)
   - Duplicate signals produce the same total score as a single signal (idempotency)
   - Streak calculation is consistent regardless of event processing order within the same day

3. THE Father_Workspace SHALL include integration tests verifying:
   - Workspace summary returns correct aggregated data
   - Cache invalidation triggers on domain events
   - Permission model rejects cross-father access
   - Partial degradation when downstream services fail
   - Rate limiting enforces configured limits

4. THE Father_Workspace SHALL include contract tests verifying that API responses match the published OpenAPI specification.

5. THE Father_Workspace SHALL include performance tests verifying that endpoints meet their defined SLA targets under simulated load (100 concurrent fathers).


---

### Requirement 25: Activity Reporting API *[MVP]*

**User Story:** As a father, I want to log quality time and positive parenting activities, so that my growth score accurately reflects my real-world parenting efforts beyond digital interactions.

#### Acceptance Criteria

1. THE Father_Workspace SHALL expose a POST endpoint for reporting quality time: `/api/v1/workspace/activities/quality-time`. The request body SHALL include: child_id (required, must belong to the father), duration_minutes (required, integer, minimum 15, maximum 480), activity_description (optional, max 200 chars), and activity_date (optional, defaults to today, cannot be in the future or more than 7 days in the past).

2. THE Father_Workspace SHALL expose a POST endpoint for reporting positive parenting activities: `/api/v1/workspace/activities/positive-activity`. The request body SHALL include: child_id (optional — activity may not be child-specific), activity_type (required, enum: PRAISE, SHARED_ACTIVITY, TEACHING_MOMENT, QUALITY_CONVERSATION, OTHER), description (optional, max 200 chars), and activity_date (optional, defaults to today, same constraints as quality time).

3. WHEN a quality time report is accepted, THE Father_Workspace SHALL emit a QUALITY_TIME_REPORTED domain event that the Growth System processes as a growth signal (12 points per Requirement 11).

4. WHEN a positive activity report is accepted, THE Father_Workspace SHALL emit a POSITIVE_ACTIVITY domain event that the Growth System processes as a growth signal (5 points per Requirement 11).

5. THE Father_Workspace SHALL rate-limit activity reporting: maximum 10 quality time reports per father per day, maximum 20 positive activity reports per father per day. Exceeding these limits SHALL return HTTP 429.

6. THE Father_Workspace SHALL validate that the child_id (when provided) belongs to the authenticated father before accepting the report.

7. THE Father_Workspace SHALL prevent duplicate reports: the same father cannot report quality time for the same child with the same duration on the same activity_date more than once. Duplicate detection is based on (father_id, child_id, duration_minutes, activity_date) tuple.

