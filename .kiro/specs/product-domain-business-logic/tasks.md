# Tasks — Product Domain & Business Logic

## Task Dependency Graph

```
1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13 → 14 → 15
```

## Tasks

### Task 1: Domain Enums and Base Infrastructure

- [x] Create all domain enums: `FatherStatus`, `OnboardingState`, `CoachingPhase`, `MissionStatus`, `ConversationStatus`, `ConversationType`, `CoachingSessionOutcome`, `HabitStatus`, `GoalCategory`, `NotificationType`, `MemoryCategory`, `CoachingStyle`
- [x] Add state transition validation to each enum (e.g., `FatherStatus.canTransitionTo(FatherStatus target)`)
- [x] Create shared exceptions: `InvalidStateTransitionException`, `BusinessRuleViolationException`, `ResourceNotFoundException`
- [x] Create the `StateMachineEngine` interface and implementation with audit logging
- [x] Add jqwik test dependency to pom.xml

### Task 2: Flyway V2 Migration — Domain Tables

- [x] Create `V2__domain_entities.sql` migration extending the existing father table with all new columns (status, onboarding_state, coaching_phase, coaching_style, preferred_coaching_time, timezone, engagement_score, coaching_streak, etc.)
- [x] Create child, goal, habit, mission, memory, conversation, coaching_session, notification, reflection, weekly_summary tables
- [x] Create state_transition_log and engagement_event append-only tables
- [x] Add all indexes defined in the design
- [x] Update conversation_message table with conversation_id FK and role column
- [x] Verify migration runs cleanly on existing V1 schema

### Task 3: Father Entity and Service

- [x] Create `Father` JPA entity with all fields, status enum, and `transitionTo()` method
- [x] Create `FatherRepository` with custom queries (findByPhone, findByStatus, findInactiveSince)
- [x] Create `FatherService` with CRUD operations, status transitions, and pause/resume logic
- [x] Implement E.164 phone validation (regex `^\+[1-9]\d{1,14}$`)
- [x] Implement pause duration capping at 30 days
- [x] Write Property Tests: #1 (phone validation), #7 (state transitions), #8 (pause capping)

### Task 4: Child Entity and Service

- [x] Create `Child` JPA entity with all fields (name, birth_date, interests[], challenges[], status)
- [x] Create `ChildRepository` with findByFatherId, countActiveByFatherId
- [x] Create `ChildService` with create (max 8 validation), update, archive operations
- [x] Implement dynamic age computation from birth_date (never stored)
- [x] Implement birthday detection (within 7 days, including year wrap-around)
- [x] Implement developmental bracket classification (6 brackets: INFANT through TEENAGER)
- [x] Write Property Tests: #4 (age computation), #5 (birthday detection), #6 (age brackets)

### Task 5: Goal and Habit Entities

- [x] Create `Goal` JPA entity with category, priority, progress tracking, estimated_missions
- [x] Create `GoalRepository` with findActiveByFatherId (max 5), progress update queries
- [x] Create `GoalService` with create, update progress, complete, archive operations
- [x] Create `Habit` JPA entity with frequency, streak tracking, status state machine
- [x] Create `HabitRepository` with findActiveByFatherId (max 5)
- [x] Create `HabitService` with streak calculation, reset rules per frequency, completion at 66
- [x] Write Property Tests: #27 (goal progress), #28 (habit streaks), #32 (capacity limits)

### Task 6: Mission Entity and State Machine

- [x] Create `Mission` JPA entity with all fields, status state machine, expiration logic
- [x] Create `MissionRepository` with complex queries (active by child, recent by category, completion stats)
- [x] Create `MissionService` with state transitions (ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED etc.)
- [x] Implement expiration calculation (weekday: +24h, weekend: +48h)
- [x] Implement single-active-mission-per-child constraint (SELECT FOR UPDATE)
- [x] Write Property Tests: #13 (expiration by day), #14 (time constraints), #16 (single active per child)

### Task 7: Mission Engine — Generation and Difficulty

- [x] Create `MissionEngine` interface and implementation
- [x] Implement difficulty bounds per coaching phase (FOUNDATION=[1,2], BUILDING=[1,3], etc.)
- [x] Implement difficulty adaptation logic (+1 for rating 4-5, -1 for rating 1-2, reset after 3 skips)
- [x] Implement category non-repetition (max 2 per category per 7-day window per child)
- [x] Implement equitable distribution across children (round-robin with equity check)
- [x] Implement child selection algorithm (least missions in 7 days, tiebreaker = longest since last)
- [x] Write Property Tests: #10 (difficulty bounds), #11 (adaptation), #12 (non-repetition), #15 (distribution)

### Task 8: Memory Entity and Core Operations

- [x] Create `Memory` JPA entity with all fields (category, importance, confidence, status, access tracking)
- [x] Create `MemoryRepository` with ranking queries, capacity count, expiration queries
- [x] Create `MemoryService` with create, supersede, expire, archive operations
- [x] Implement tier classification (1-3=Short, 4-6=Medium, 7-10=Long) and expiration assignment
- [x] Implement confidence decay on contradiction (reduce by 0.3, min 0.0)
- [x] Implement 500-memory capacity enforcement (archive lowest importance×confidence)
- [x] Write Property Tests: #17 (tier expiration), #18 (confidence decay), #20 (capacity limit)

### Task 9: Memory System — Retrieval and Ranking

- [x] Create `MemorySystem` interface and implementation
- [x] Implement composite ranking formula: `(importance×0.5) + (recency_factor×0.3) + (relevance×0.2)`
- [x] Implement recency_factor calculation: `max(0, 1.0 - (days_since_creation × 0.05))`
- [x] Implement top-15 retrieval with ranking
- [x] Implement memory consolidation job (merge short-term memories older than 7 days)
- [x] Write Property Test: #19 (ranking order)

### Task 10: Conversation and Coaching Session Entities

- [x] Create `Conversation` JPA entity with type, status, message_count, expiration
- [x] Create `ConversationRepository` with findActiveByFatherId, findExpired queries
- [x] Create `ConversationService` with lifecycle management and message limit enforcement (max 8 outbound)
- [x] Create `CoachingSession` JPA entity (outcome metadata linked to conversation)
- [x] Implement single-active-conversation-per-father constraint (DIFFICULT_SITUATION preempts)
- [x] Write Property Tests: #21 (single active), #22 (message limit)

### Task 11: Engagement Score and Metrics

- [x] Create `EngagementService` implementing the engagement formula: `min(100, msgs×2 + missions×15 + reflections×10 + min(streak,10))`
- [x] Create `MetricsService` for Mission_Completion_Rate, Relationship_Progress, Consistency_Score
- [x] Implement coaching streak calculation (consecutive days with interaction in father's timezone)
- [x] Implement coaching phase computation from days since activation (forward-only)
- [x] Write Property Tests: #2 (engagement formula), #3 (phase computation), #26 (streak calculation)

### Task 12: Notification Entity and Quiet Hours

- [x] Create `Notification` JPA entity with type, channel, status, priority, scheduling fields
- [x] Create `NotificationRepository` with findDue, countDailyByFather queries
- [x] Create `NotificationService` with scheduling, quiet hours enforcement, rate limiting
- [x] Implement quiet hours logic (21:00-07:00 in father's local timezone → reschedule to 07:00)
- [x] Implement daily limit enforcement (max 5 proactive per day, excludes conversation replies)
- [x] Implement priority-based deconfliction (highest priority wins, others rescheduled at 2h intervals)
- [x] Write Property Tests: #23 (quiet hours), #24 (daily rate limit), #25 (priority deconfliction)

### Task 13: AI Provider Layer

- [x] Create `AiProvider` interface with `complete(AiRequest)` method
- [x] Create `AiRequest` and `AiResponse` records
- [x] Create `AiModel` enum with model selection per conversation type (GPT-4o for complex, mini for routine)
- [x] Implement token estimation utility
- [x] Implement context token budget enforcement (max 2000 tokens)
- [x] Implement retry with exponential backoff (1s, 2s, 4s, 8s, 16s) and fallback message
- [x] Implement daily AI call rate limit (max 20 per father per day)
- [x] Write Property Tests: #29 (model selection), #30 (token budget), #34 (rate limiting)

### Task 14: Scheduling, Reflection, and Weekly Summary

- [x] Create scheduling infrastructure (timezone-aware job dispatch)
- [x] Implement daily coaching time detection (find fathers due for coaching per timezone)
- [x] Implement inactivity detection (3-day, 7-day, 14-day, 21-day thresholds → CHURNED)
- [x] Create `Reflection` entity and service (mission/weekly/phase types, max 1 per day)
- [x] Create `WeeklySummary` entity and service (Monday 08:00 local, exclude PAUSED/CHURNED/DELETED)
- [x] Write Property Tests: #9 (inactivity→churn), #31 (weekly summary exclusion), #35 (reflection limit)

### Task 15: Integration Tests and Edge Cases

- [x] Create integration test base class with Testcontainers PostgreSQL
- [x] Write integration test: full onboarding flow (Father creation → status transitions → memory creation)
- [x] Write integration test: mission lifecycle (generate → accept → complete → difficulty adaptation)
- [x] Write integration test: conversation lifecycle (create → message exchanges → complete → summary)
- [x] Write integration test: scheduling (daily coaching dispatch, quiet hours, inactivity detection)
- [x] Implement message batching (3+ messages in 10s → wait 5s → process combined)
- [x] Implement emoji-only message interpretation (👍=ack, ❌=decline, etc.)
- [x] Write Property Test: #33 (message batching)
