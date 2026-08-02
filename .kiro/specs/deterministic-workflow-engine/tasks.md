# Implementation Plan: Deterministic Workflow Engine

## Overview

This implementation plan transforms Dad Coach from an AI-driven conversational experience to a deterministic workflow engine. The work is organized into incremental phases: database schema first, then core backend components, followed by REST APIs, scheduler jobs, frontend changes, and finally integration testing. Each phase builds on the previous, ensuring no orphaned code.

**Orchestration Layer Role**: This workflow engine serves as the central orchestration layer connecting:
- **WEB-SPEC-007 (Onboarding)**: Receives activated fathers after onboarding completes
- **WEB-SPEC-008 (Father Workspace)**: Provides dashboard data via /api/workspace/summary

**Key Principles**:
- **Mission Abstraction**: MVP implements Quality Time as the sole mission type; architecture remains extensible
- **Belt System**: SACRED — Do NOT remove or redefine the belt progression
- **AI Usage Policy**: AI is minimized, used only for activity ideas, encouragement, and celebrations
- **Language Support**: English (en) and Hebrew (he) only — NO Spanish
- **Quality Time is the Product**: Scheduling is the mechanism

## Tasks

- [x] 1. Database Schema and Entity Setup
  - [x] 1.1 Create Flyway migration for quality_time table
    - Create table with id, father_id, child_id, google_calendar_event_id, scheduled_start, scheduled_end, status, completion_notes, completed_at, reminder_sent, follow_up_sent columns
    - Add indexes for father_id, status queries, and scheduled lookups
    - _Requirements: 3.4, 15.1_

  - [x] 1.2 Create Flyway migration for workflow_state_transition_log table
    - Create table with id, father_id, from_state, to_state, trigger_reason, trigger_message_id, created_at columns
    - Add index for father_id and created_at DESC
    - _Requirements: 1.4, 16.1_

  - [x] 1.3 Create Flyway migration for message_templates table
    - Create table with id, message_type, template_text, language, active columns
    - Unique constraint on (message_type, language) for multilingual support
    - Insert default fallback templates for all message types in both English and Hebrew
    - _Requirements: 10.4_

  - [x] 1.4 Create Flyway migration for father table additions
    - Add columns: current_workflow_state, previous_workflow_state, workflow_state_entered_at, welcomed_at, quality_time_streak, quality_time_longest_streak, total_quality_times_completed, current_belt
    - Add index for current_workflow_state
    - _Requirements: 1.2, 8.2_

  - [x] 1.5 Create Flyway migration for scheduler_job_log table
    - Create table with id, job_name, started_at, completed_at, records_processed, errors_count, status columns
    - Add index for job_name and started_at
    - _Requirements: 12.3, 16.3_

  - [x] 1.6 Create data migration for existing fathers
    - Migrate completed missions to quality_time records
    - Calculate belt progression from completion counts
    - Set initial workflow state to SCHEDULE_QUALITY_TIME for existing fathers
    - _Requirements: 15.1, 15.2, 15.3_

- [x] 2. Core Domain Entities and Enums
  - [x] 2.1 Create WorkflowState enum
    - Define WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP, ACTIVITY_IDEAS, DASHBOARD
    - Implement valid transitions set for each state
    - Implement canTransitionTo and getValidTransitions methods
    - _Requirements: 1.1, 1.3_

  - [ ]* 2.2 Write property test for state transition validity
    - **Property 2: State Transition Validity**
    - For any current state and target state, transition succeeds iff target is in valid transitions set
    - **Validates: Requirements 1.3, 1.6**

  - [x] 2.3 Create Belt enum
    - Define WHITE, YELLOW, ORANGE, GREEN, BLUE, BROWN, BLACK with thresholds
    - Implement fromCompletionCount(int) method
    - Implement getNextBelt() method
    - _Requirements: 8.5_

  - [ ]* 2.4 Write property test for belt calculation
    - **Property 16: Belt Calculation Correctness**
    - For any completion count N, belt level follows thresholds exactly
    - **Validates: Requirement 8.5**

  - [x] 2.5 Create QualityTimeStatus enum
    - Define SCHEDULED, COMPLETED, MISSED, CANCELLED statuses
    - _Requirements: 3.4_

  - [x] 2.6 Create QualityTime JPA entity
    - Implement entity with all fields from schema
    - Add @ManyToOne relationships to Father and Child
    - Add JPA lifecycle callbacks for created_at/updated_at
    - _Requirements: 3.4_

  - [ ]* 2.7 Write property test for Quality Time data completeness
    - **Property 5: Quality Time Data Completeness**
    - For any created QualityTime, all required fields are non-null
    - **Validates: Requirement 3.4**

  - [x] 2.8 Create WorkflowTransition entity
    - Implement entity for state_transition_log table
    - Include father_id, from_state, to_state, trigger_reason, trigger_message_id, created_at
    - _Requirements: 1.4_

  - [x] 2.9 Update Father entity with workflow fields
    - Add currentWorkflowState, previousWorkflowState, workflowStateEnteredAt, welcomedAt
    - Add qualityTimeStreak, qualityTimeLongestStreak, totalQualityTimesCompleted, currentBelt
    - _Requirements: 1.2, 8.2_

- [x] 3. Checkpoint - Database and Entity Setup
  - Ensure all migrations run successfully, all entities compile, and basic repository operations work. Ask the user if questions arise.

- [x] 4. Repository Layer
  - [x] 4.1 Create QualityTimeRepository
    - Add findByFatherId, findLatestScheduledForFather methods
    - Add findScheduledBetween for scheduler queries
    - Add findByStatusAndScheduledEndBefore for follow-up transitions
    - _Requirements: 3.4, 6.6, 12.4_

  - [x] 4.2 Create WorkflowTransitionLogRepository
    - Add findByFatherIdOrderByCreatedAtDesc method
    - Add save method for transition logging
    - _Requirements: 1.4, 16.1_

  - [x] 4.3 Create MessageTemplateRepository
    - Add findByMessageTypeAndLanguage method
    - Add findByMessageTypeAndActive method
    - _Requirements: 10.4_

  - [x] 4.4 Create SchedulerJobLogRepository
    - Add save method for job execution logging
    - Add findByJobNameOrderByStartedAtDesc method
    - _Requirements: 16.3_

  - [x] 4.5 Update FatherRepository for workflow queries
    - Add findByWorkflowStateAndStateEnteredAtBefore for stale state detection
    - _Requirements: 12.5_

- [ ] 5. System State Loader (Read Before Write)
  - [x] 5.1 Create SystemState record class
    - Include father profile, workflow state, calendar events, quality times, dashboard metrics, conversation context
    - Make immutable with record/value object pattern
    - _Requirements: 2.1_

  - [x] 5.2 Create SystemStateLoader interface
    - Define loadState(UUID fatherId) method
    - Define loadAvailableSlots(UUID fatherId, int daysAhead) method
    - _Requirements: 2.1, 2.3_

  - [x] 5.3 Implement SystemStateLoaderImpl
    - Load father profile, workflow state from database
    - Load Google Calendar events for next 7 days (if connected)
    - Load scheduled Quality Time events
    - Load dashboard metrics (belt, streak, achievements)
    - Load last 10 conversation messages
    - _Requirements: 2.1_

  - [x] 5.4 Create SystemStateCache for request-scoped caching
    - Use Spring @RequestScope or ThreadLocal pattern
    - Cache state for duration of single request processing
    - _Requirements: 2.4_

  - [ ] 5.5 Implement available slot calculation
    - Read Google Calendar events
    - Identify busy periods
    - Calculate available slots of at least 30 minutes
    - Exclude times outside preferred activity hours (6am-10pm)
    - Return top 5 slots ordered by proximity
    - _Requirements: 2.3_

  - [ ]* 5.6 Write property test for time slot non-overlap
    - **Property 4: Time Slot Non-Overlap**
    - For any suggested slot, it does not overlap with any existing calendar event
    - **Validates: Requirement 2.3**

- [ ] 6. Pattern Matching Engine
  - [x] 6.1 Create PatternResult class
    - Include patternName, matchedAction, capturedGroups
    - _Requirements: 11.3_

  - [x] 6.2 Create PatternMatcher interface
    - Define match(String input, List<StatePattern> patterns) method
    - _Requirements: 11.3_

  - [x] 6.3 Create StatePattern class
    - Include patternName, regex Pattern, WorkflowAction
    - Static factory method of(name, pattern, action)
    - _Requirements: 11.3_

  - [ ] 6.4 Implement PatternMatcherImpl with regex
    - Evaluate patterns in order, first match wins
    - Extract captured groups from regex
    - Return PatternResult with action
    - _Requirements: 11.3_

  - [ ] 6.5 Define WELCOME_PATTERNS
    - AFFIRMATIVE_EN (English): yes|ready|let's go|ok|sure|start → TRANSITION_TO_SCHEDULE
    - AFFIRMATIVE_HE (Hebrew): כן|מוכן|יאללה|בסדר|התחל → TRANSITION_TO_SCHEDULE
    - MORE_INFO_EN (English): how|what is|explain|tell me more → EXPLAIN_AND_REPROMPT
    - MORE_INFO_HE (Hebrew): איך|מה זה|הסבר|ספר לי עוד → EXPLAIN_AND_REPROMPT
    - _Requirements: 4.2_

  - [ ]* 6.6 Write property test for WELCOME pattern matching
    - **Property 7: WELCOME State Pattern Matching**
    - For any input, matches exactly one of AFFIRMATIVE, MORE_INFO, or UNMATCHED
    - **Validates: Requirements 4.2, 4.3**

  - [ ] 6.7 Define SCHEDULE_PATTERNS
    - SLOT_NUMBER: ^([1-9])$ → SELECT_SLOT
    - SKIP_EN (English): skip|not now|later → POSTPONE_SCHEDULING
    - SKIP_HE (Hebrew): דלג|לא עכשיו|אחר כך → POSTPONE_SCHEDULING
    - MORE_SLOTS_EN (English): other|more|different → SHOW_MORE_SLOTS
    - MORE_SLOTS_HE (Hebrew): אחר|עוד|אחרים → SHOW_MORE_SLOTS
    - TIME_EXPRESSION_EN: tomorrow|day patterns|time patterns → PARSE_TIME
    - TIME_EXPRESSION_HE: מחר|יום patterns|time patterns → PARSE_TIME
    - _Requirements: 5.2_

  - [ ]* 6.8 Write property test for slot selection pattern matching
    - **Property 9: Slot Selection Pattern Matching**
    - For valid slot input (1-9, time expressions, skip, other), correct action identified without AI
    - **Validates: Requirement 5.2**

  - [x] 6.9 Define FOLLOW_UP_PATTERNS
    - COMPLETED_EN (English): yes|done|completed|finished → MARK_COMPLETED
    - COMPLETED_HE (Hebrew): כן|סיימתי|עשיתי|הושלם → MARK_COMPLETED
    - NOT_COMPLETED_EN (English): no|not yet|couldn't → MARK_MISSED
    - NOT_COMPLETED_HE (Hebrew): לא|עוד לא|לא הצלחתי → MARK_MISSED
    - _Requirements: 7.2, 7.3_

  - [ ] 6.10 Define WAITING_PATTERNS
    - REQUEST_IDEAS_EN (English): ideas|activity|suggestions|what can I do → TRANSITION_TO_ACTIVITY_IDEAS
    - REQUEST_IDEAS_HE (Hebrew): רעיונות|פעילות|הצעות|מה אפשר לעשות → TRANSITION_TO_ACTIVITY_IDEAS
    - RESCHEDULE_EN (English): reschedule|change|cancel → RESCHEDULE
    - RESCHEDULE_HE (Hebrew): שנה זמן|שינוי|ביטול → RESCHEDULE
    - SCHEDULE_INQUIRY_EN (English): when|schedule|next → SHOW_SCHEDULE
    - SCHEDULE_INQUIRY_HE (Hebrew): מתי|לוח זמנים|הבא → SHOW_SCHEDULE
    - DASHBOARD_EN (English): dashboard|progress|belt|streak → SHOW_DASHBOARD_SUMMARY
    - DASHBOARD_HE (Hebrew): דשבורד|התקדמות|חגורה|רצף → SHOW_DASHBOARD_SUMMARY
    - _Requirements: 6.4, 6.5_

  - [ ] 6.11 Define ACTIVITY_IDEAS_PATTERNS
    - IDEA_NUMBER: ^([1-3])$ → SHOW_IDEA_DETAILS
    - MORE_IDEAS_EN (English): more|another|different → GENERATE_MORE_IDEAS
    - MORE_IDEAS_HE (Hebrew): עוד|אחר|שונה → GENERATE_MORE_IDEAS
    - EXIT_EN (English): thanks|done|enough → RETURN_TO_PREVIOUS
    - EXIT_HE (Hebrew): תודה|סיימתי|מספיק → RETURN_TO_PREVIOUS
    - _Requirements: 9.4_

  - [ ]* 6.12 Write property test for activity ideas entry condition
    - **Property 17: Activity Ideas Entry Condition**
    - Transition to ACTIVITY_IDEAS only when message contains activity request keywords
    - **Validates: Requirement 9.1**

- [ ] 7. Checkpoint - Pattern Matching Engine
  - Ensure all pattern definitions compile, pattern matcher correctly identifies actions for sample inputs. Ask the user if questions arise.

- [ ] 8. Message Generator
  - [ ] 8.1 Create MessageType enum
    - Define all message types: WELCOME_GREETING, WELCOME_EXPLAIN, SCHEDULE_SLOTS, SCHEDULE_CONFIRM, WAITING_REMINDER, FOLLOW_UP_QUESTION, FOLLOW_UP_COMPLETED, FOLLOW_UP_MISSED, ACTIVITY_IDEAS, DASHBOARD_SUMMARY, CLARIFICATION, ERROR_GENERIC
    - _Requirements: 10.2_

  - [ ] 8.2 Create MessageContext class
    - Include messageType, fatherName, childName, timeSlots, timezone, locale, and other contextual data
    - Builder pattern for construction
    - _Requirements: 10.2_

  - [ ] 8.3 Create MessageGenerator interface
    - Define generate(MessageType type, MessageContext context) method
    - Define generateWithFallback(MessageType type, MessageContext context, long timeoutMs) method
    - _Requirements: 10.1_

  - [ ] 8.4 Create FallbackMessages class
    - Load templates from message_templates table at startup
    - Provide get(MessageType) method for instant fallback access
    - _Requirements: 10.4_

  - [ ] 8.5 Implement MessageGeneratorImpl
    - Use existing IntelligenceLayer for AI text generation
    - Pass simplified prompts that only request text, not decisions
    - Return only string output, no recommendations or metadata
    - _Requirements: 10.1, 10.3, 10.5_

  - [ ] 8.6 Implement timeout and fallback logic
    - Execute AI call with CompletableFuture and timeout
    - Use fallback template if AI exceeds 5 seconds or fails
    - Log fallback usage as warning
    - _Requirements: 10.4, 10.6_

  - [ ]* 8.7 Write property test for message generator fallback
    - **Property 20: Message Generator Fallback**
    - For any message request, if AI fails or times out, fallback template is used
    - **Validates: Requirements 10.4, 10.6**

- [ ] 9. State Handlers
  - [ ] 9.1 Create StateHandler interface
    - Define getState(), getExpectedPatterns(), handle(context, match), handleUnmatched(context) methods
    - _Requirements: 1.1, 11.4_

  - [ ] 9.2 Create StateAction class
    - Include action type, next state (if transition), response message, updated entities
    - _Requirements: 1.3_

  - [ ] 9.3 Implement WelcomeStateHandler
    - On AFFIRMATIVE: transition to SCHEDULE_QUALITY_TIME, set welcomed_at timestamp
    - On MORE_INFO: send explanation, reprompt
    - On UNMATCHED: send clarification with two explicit options
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ]* 9.4 Write property test for WELCOME exit timestamp
    - **Property 8: WELCOME Exit Timestamp**
    - For any transition from WELCOME, welcomed_at is set to non-null
    - **Validates: Requirement 4.5**

  - [ ] 9.5 Implement ScheduleStateHandler
    - On entry: load available slots, present 3-5 options with numbers
    - On SLOT_NUMBER: re-read calendar, verify slot, create event, transition to WAITING
    - On SKIP: acknowledge, set reminder for 24h re-prompt, remain in state
    - On MORE_SLOTS: present next 5 available slots
    - On TIME_EXPRESSION: parse and validate, proceed as slot selection
    - Track message exchange count, send summary at 5th exchange
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [ ]* 9.6 Write property test for scheduling conversation length
    - **Property 10: Scheduling Conversation Length Limit**
    - For any scheduling conversation, completes or receives summary by 5th exchange
    - **Validates: Requirement 5.6**

  - [ ] 9.7 Implement WaitingStateHandler
    - On REQUEST_IDEAS: store previous state, transition to ACTIVITY_IDEAS
    - On RESCHEDULE: cancel existing QualityTime and calendar event, transition to SCHEDULE
    - On SCHEDULE_INQUIRY: read next QualityTime, send confirmation message
    - On DASHBOARD: send text summary with deep link
    - _Requirements: 6.1, 6.4, 6.5_

  - [ ] 9.8 Implement FollowUpStateHandler
    - On entry: send follow-up question asking about completion
    - On COMPLETED: mark QualityTime COMPLETED, increment streak, check belt milestone, transition to SCHEDULE
    - On NOT_COMPLETED: mark QualityTime MISSED, send encouraging message, transition to SCHEDULE
    - Extract completion notes if provided beyond yes/no
    - Track exchange count, complete by 3rd exchange
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 9.9 Write property test for completion updates dashboard
    - **Property 13: Completion Updates Dashboard**
    - For any completion, QualityTime status=COMPLETED, streak incremented, belt recalculated
    - **Validates: Requirement 7.2**

  - [ ]* 9.10 Write property test for non-completion preserves streak
    - **Property 14: Non-Completion Preserves Streak**
    - For any non-completion, QualityTime status=MISSED, streak unchanged
    - **Validates: Requirement 7.3**

  - [ ]* 9.11 Write property test for follow-up conversation length
    - **Property 15: Follow-Up Conversation Length Limit**
    - For any follow-up conversation, completes by 3rd exchange
    - **Validates: Requirement 7.5**

  - [ ] 9.12 Implement ActivityIdeasStateHandler
    - On entry: read child age/interests, weather, previous activities; generate 3 ideas via AI
    - On IDEA_NUMBER: show detailed idea information
    - On MORE_IDEAS: generate 3 new ideas
    - On EXIT: return to previous_workflow_state
    - Store previous state when entering
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.6_

  - [ ]* 9.13 Write property test for activity ideas format
    - **Property 18: Activity Ideas Format**
    - For any ideas response, exactly 3 ideas with title, description, duration, indoor/outdoor
    - **Validates: Requirement 9.3**

  - [ ]* 9.14 Write property test for activity ideas state restoration
    - **Property 19: Activity Ideas State Restoration**
    - For any exit from ACTIVITY_IDEAS, workflow returns to previous_workflow_state
    - **Validates: Requirement 9.6**

- [ ] 10. Checkpoint - State Handlers
  - Ensure all state handlers compile, handle patterns correctly, and generate appropriate responses. Ask the user if questions arise.

- [ ] 11. Quality Time Service
  - [ ] 11.1 Create QualityTimeService interface
    - Define scheduleQualityTime(fatherId, childId, startTime, duration) method
    - Define completeQualityTime(qualityTimeId, notes) method
    - Define cancelQualityTime(qualityTimeId) method
    - Define getUpcomingQualityTime(fatherId) method
    - _Requirements: 3.3, 3.4_

  - [ ] 11.2 Implement QualityTimeServiceImpl - scheduling
    - Re-read Google Calendar before write (conflict detection)
    - Create Google Calendar event with title, duration, description, reminders, green color
    - Create QualityTime database record with google_calendar_event_id
    - Handle calendar API failure with retry and error messaging
    - _Requirements: 3.3, 3.6, 2.6_

  - [ ]* 11.3 Write property test for calendar event completeness
    - **Property 6: Calendar Event Completeness**
    - For any created event, contains title with child name, duration>=30min, description, reminders, green color
    - **Validates: Requirement 3.3**

  - [ ] 11.4 Implement QualityTimeServiceImpl - completion
    - Update QualityTime status to COMPLETED, set completed_at
    - Increment father's quality_time_streak
    - Update quality_time_longest_streak if new record
    - Increment total_quality_times_completed
    - Recalculate and update current_belt (SACRED Belt System)
    - _Requirements: 7.2, 8.5_

  - [ ] 11.5 Implement QualityTimeServiceImpl - cancellation
    - Update QualityTime status to CANCELLED
    - Delete corresponding Google Calendar event
    - _Requirements: 3.7_

  - [ ] 11.6 Implement calendar sync for externally deleted events
    - On calendar read, detect missing events
    - Update QualityTime records to CANCELLED for missing calendar events
    - _Requirements: 3.7_

- [ ] 11.5. Mission Service Abstraction (Extensibility Layer)
  - [ ] 11.5.1 Create Mission interface
    - Define getId(), getFatherId(), getChildId(), getType(), getStatus() methods
    - Define getScheduledStart(), getScheduledEnd(), getCompletionNotes() methods
    - Define getCompletedAt() method
    - Document as abstract container for parenting activities
    - _Requirements: 1.1 (Mission concept)_

  - [ ] 11.5.2 Create MissionType enum
    - Define QUALITY_TIME as MVP mission type
    - Add placeholder comments for future types (READING_TOGETHER, OUTDOOR_ACTIVITY)
    - _Requirements: 1.1 (Mission extensibility)_

  - [ ] 11.5.3 Create MissionStatus enum
    - Define SCHEDULED, COMPLETED, MISSED, CANCELLED statuses
    - Reuse QualityTimeStatus or create shared enum
    - _Requirements: 3.4_

  - [ ] 11.5.4 Create MissionService interface
    - Define schedule(), complete(), cancel() methods
    - Define getNextScheduled(), getRecentCompleted() methods
    - Define getSupportedType() method
    - Document as abstract service for mission operations
    - _Requirements: 1.1 (Mission abstraction)_

  - [ ] 11.5.5 Implement QualityTimeMissionService
    - Implement MissionService interface
    - Delegate to QualityTimeService for actual operations
    - Return QualityTime as Mission
    - Set getSupportedType() to return QUALITY_TIME
    - _Requirements: 1.1 (MVP implementation)_

  - [ ] 11.5.6 Create MissionServiceFactory
    - Auto-discover MissionService implementations via Spring
    - Map each service to its supported MissionType
    - Provide getService(MissionType) method
    - Provide getDefaultService() convenience method for MVP
    - _Requirements: 1.1 (Factory pattern)_

  - [ ] 11.5.7 Update WorkflowEngine to use MissionService
    - Replace direct QualityTimeService calls with MissionService calls
    - Use MissionServiceFactory.getDefaultService() for MVP
    - Ensure all mission operations go through abstraction layer
    - _Requirements: 1.1, 1.7 (WEB-SPEC-007 integration)_

- [ ] 12. Workflow Engine Core
  - [x] 12.1 Create WorkflowEngine interface
    - Define processMessage(InboundMessageDto) method
    - Define triggerTransition(fatherId, trigger) method
    - _Requirements: 1.1_

  - [ ] 12.2 Create WorkflowContext class
    - Include systemState, fatherId, currentState, inboundMessage
    - Immutable with builder pattern
    - _Requirements: 11.1_

  - [ ] 12.3 Create WorkflowTrigger enum
    - Define USER_MESSAGE, QUALITY_TIME_ENDED, FOLLOW_UP_TIMEOUT, SCHEDULER_REMINDER
    - _Requirements: 12.1_

  - [ ] 12.4 Implement WorkflowEngineImpl - message processing pipeline
    - Step 1: Parse and validate message
    - Step 2: Identify father from phone number
    - Step 3: Load SystemState (Read Before Write)
    - Step 4: Determine current workflow state
    - Step 5: Get appropriate StateHandler and match patterns
    - Step 6: Execute business logic for matched pattern
    - Step 7: Generate response message (AI or fallback)
    - Step 8: Persist state changes
    - Step 9: Log state transition
    - _Requirements: 11.1_

  - [ ]* 12.5 Write property test for single workflow state invariant
    - **Property 1: Single Workflow State Invariant**
    - For any father and any sequence of operations, exactly one active workflow state at any time
    - **Validates: Requirement 1.2**

  - [ ]* 12.6 Write property test for state transition audit logging
    - **Property 3: State Transition Audit Logging**
    - For any successful transition, exactly one log entry created with correct from/to/trigger
    - **Validates: Requirement 1.4**

  - [ ] 12.7 Implement unmatched message handling
    - Send clarification message specific to current state
    - Include explicit options for valid responses
    - Do NOT use AI to interpret unmatched messages
    - _Requirements: 11.4_

  - [ ]* 12.8 Write property test for unmatched message handling
    - **Property 21: Unmatched Message Handling**
    - For any unmatched message, clarification sent with explicit valid options
    - **Validates: Requirement 11.4**

  - [ ] 12.9 Implement 30-second response timeout
    - If processing takes >30 seconds, send "processing" message immediately
    - Follow up with actual response when ready
    - _Requirements: 11.2_

  - [ ] 12.10 Implement timezone-aware message formatting
    - All time suggestions formatted in father's configured timezone
    - _Requirements: 5.7_

  - [ ]* 12.11 Write property test for timezone consistency
    - **Property 11: Timezone Consistency**
    - For any time slot suggestion, time formatted in father's configured timezone
    - **Validates: Requirement 5.7**

- [ ] 13. Checkpoint - Workflow Engine Core
  - Ensure WorkflowEngine processes messages through full pipeline, transitions states correctly, and generates responses. Ask the user if questions arise.

- [ ] 14. Scheduler Jobs
  - [ ] 14.1 Create WorkflowScheduler component
    - Use Spring @Scheduled annotations
    - Include QualityTimeRepository, FatherRepository, WorkflowEngine, WhatsAppService
    - _Requirements: 12.1, 12.3_

  - [ ] 14.2 Implement morning reminder job
    - Run at 7:50 AM UTC, send at 8 AM local time
    - Query Quality Times scheduled today
    - Filter by reminder_sent=false and isLocalTime8AM
    - Send reminder via WhatsApp
    - Mark reminder_sent=true
    - Process in batches of 100
    - Log job execution
    - _Requirements: 6.2, 6.3, 12.1, 12.6_

  - [ ]* 14.3 Write property test for morning reminder idempotency
    - **Property 12: Morning Reminder Idempotency**
    - For any Quality Time and any number of scheduler runs, exactly one reminder sent per day
    - **Validates: Requirements 6.2, 6.3**

  - [ ] 14.4 Implement follow-up transition job
    - Run every 15 minutes
    - Query Quality Times with status=SCHEDULED and end_time < now
    - Filter by follow_up_sent=false
    - Trigger transition to QUALITY_TIME_FOLLOW_UP
    - Send follow-up question via WhatsApp
    - Mark follow_up_sent=true
    - _Requirements: 6.6, 12.4_

  - [ ] 14.5 Implement stale state detection job
    - Run every hour
    - Query fathers in QUALITY_TIME_FOLLOW_UP for >24 hours
    - Mark pending Quality Time as MISSED
    - Transition to SCHEDULE_QUALITY_TIME
    - Send gentle re-engagement message
    - _Requirements: 7.6, 12.5_

  - [ ]* 14.6 Write property test for scheduler job idempotency
    - **Property 22: Scheduler Job Idempotency**
    - For any job and any number of executions, no duplicate messages, transitions, or state updates
    - **Validates: Requirement 12.2**

- [ ] 15. REST API Controllers
  - [ ] 15.1 Create WorkspaceSummaryDto response class
    - Include fatherDisplayName, currentWorkflowState, currentBelt, beltProgress, currentStreak, longestStreak, totalQualityTimesCompleted, weeklyGoalProgress, nextQualityTime, recentQualityTimes, recentAchievements, nextMilestone
    - _Requirements: 8.2, 14.1_

  - [ ] 15.2 Create AvailableSlotsDto response class
    - Include list of slots with startTime, endTime, durationMinutes
    - Include calendarConnected flag and timezone
    - _Requirements: 14.1_

  - [ ] 15.3 Create ScheduleRequest and ScheduleResponse DTOs
    - Request: childId, startTime, durationMinutes
    - Response: qualityTimeId, calendarEventId, childName, startTime, endTime, status
    - _Requirements: 14.3_

  - [ ] 15.4 Create CompleteRequest and CompleteResponse DTOs
    - Request: notes (optional)
    - Response: qualityTimeId, status, streakUpdated, newStreak, beltEarned, pointsAwarded
    - _Requirements: 14.1_

  - [ ] 15.5 Create ActivityIdeaDto and ActivityIdeasResponse DTOs
    - Include title, description, durationMinutes, indoor flag
    - _Requirements: 14.1_

  - [ ] 15.6 Implement GET /api/v1/workspace/summary endpoint
    - Load system state for authenticated father
    - Compute dashboard metrics in real-time
    - Return WorkspaceSummaryDto
    - _Requirements: 8.2, 14.1_

  - [ ] 15.7 Implement GET /api/v1/quality-time/available-slots endpoint
    - Accept days_ahead and min_duration_minutes query params
    - Load available slots from SystemStateLoader
    - Return AvailableSlotsDto
    - _Requirements: 14.1_

  - [ ] 15.8 Implement POST /api/v1/quality-time/schedule endpoint
    - Validate request against calendar availability
    - Use QualityTimeService to create event
    - Return ScheduleResponse
    - _Requirements: 14.3_

  - [ ] 15.9 Implement POST /api/v1/quality-time/{id}/complete endpoint
    - Use QualityTimeService to mark complete
    - Return CompleteResponse with updated metrics
    - _Requirements: 14.1_

  - [ ] 15.10 Implement POST /api/v1/quality-time/{id}/cancel endpoint
    - Use QualityTimeService to cancel event
    - Return updated QualityTimeResponse
    - _Requirements: 14.1_

  - [ ] 15.11 Implement GET /api/v1/activity-ideas endpoint
    - Accept child_id query param
    - Use MessageGenerator to generate 3 activity ideas
    - Return ActivityIdeasResponse
    - _Requirements: 14.1_

  - [ ]* 15.12 Write API integration tests for all endpoints
    - Test happy paths and error conditions
    - Verify authentication requirements
    - _Requirements: 14.5_

- [ ] 16. Checkpoint - Backend API Complete
  - Ensure all REST endpoints work correctly, return proper DTOs, and handle errors gracefully. Ask the user if questions arise.

- [ ] 17. Frontend TypeScript Types
  - [ ] 17.1 Create qualityTime.ts types file
    - Define QualityTimeStatus, BeltLevel, WorkflowState types
    - Define QualityTime, AvailableSlot, BeltProgress, Achievement interfaces
    - Define WorkspaceSummary interface
    - Define ScheduleRequest, ScheduleResponse interfaces
    - Define ActivityIdea interface
    - _Requirements: 13.1_

- [ ] 18. Frontend API Services
  - [ ] 18.1 Create qualityTime.ts API service
    - Implement getWorkspaceSummary() function
    - Implement getAvailableSlots(daysAhead, minDuration) function
    - Implement scheduleQualityTime(request) function
    - Implement completeQualityTime(id, notes) function
    - Implement cancelQualityTime(id) function
    - Use existing apiClient pattern
    - _Requirements: 13.2_

  - [ ] 18.2 Create activityIdeas.ts API service
    - Implement getActivityIdeas(childId) function
    - _Requirements: 13.1_

- [ ] 19. Frontend React Hooks
  - [ ] 19.1 Create useWorkspaceSummary hook
    - Use @tanstack/react-query for data fetching
    - Configure refetchInterval at 60 seconds
    - Configure staleTime at 30 seconds
    - _Requirements: 13.2, 13.6_

  - [ ] 19.2 Create useAvailableSlots hook
    - Fetch available time slots from API
    - Accept daysAhead and minDuration params
    - _Requirements: 13.4_

  - [ ] 19.3 Create useScheduleQualityTime hook
    - Use @tanstack/react-query useMutation
    - Invalidate workspace summary on success
    - _Requirements: 13.4_

  - [ ] 19.4 Create useCompleteQualityTime hook
    - Use useMutation for completion
    - Invalidate workspace summary on success
    - _Requirements: 13.1_

  - [ ] 19.5 Create useBeltCelebration hook
    - Track previous belt and current belt
    - Detect belt progression
    - Return isActive, newBelt, dismiss function
    - _Requirements: 13.3, 13.6_

- [ ] 20. Frontend Dashboard Components
  - [ ] 20.1 Create BeltProgressionCard component
    - Display current belt icon/image
    - Show progress bar to next belt
    - Display Quality Times completed count
    - _Requirements: 13.1_

  - [ ] 20.2 Create NextQualityTimeCard component
    - Display scheduled date, time, child name
    - Show countdown timer
    - Include quick reschedule link
    - _Requirements: 13.1_

  - [ ] 20.3 Create StreakDisplay component
    - Show current streak with flame icon
    - Display longest streak badge
    - _Requirements: 13.1_

  - [ ] 20.4 Create RecentActivityFeed component
    - List last 5 Quality Time completions
    - Show date, child name, duration
    - _Requirements: 13.1_

  - [ ] 20.5 Create AchievementBadges component
    - Display earned achievements as earned
    - Show locked achievements as silhouettes
    - _Requirements: 13.1_

  - [ ] 20.6 Create ScheduleQualityTimeCTA component
    - Primary action button "Schedule Quality Time"
    - Opens scheduling modal on click
    - _Requirements: 13.4_

- [ ] 21. Checkpoint - Dashboard Components
  - Ensure all dashboard components render correctly with mock data. Ask the user if questions arise.

- [ ] 22. Frontend Quality Time Scheduling Components
  - [ ] 22.1 Create AvailableSlotPicker component
    - Display available time slots from API
    - Allow selection of slot
    - Show date/time in user-friendly format
    - _Requirements: 13.4_

  - [ ] 22.2 Create ChildSelector component
    - Display list of children if multiple exist
    - Allow selection of child for Quality Time
    - Skip if only one child
    - _Requirements: 13.4_

  - [ ] 22.3 Create ConfirmationModal component
    - Show selected slot and child
    - Confirm button to schedule
    - Cancel button to close
    - _Requirements: 13.4_

  - [ ] 22.4 Create ScheduleQualityTime modal component
    - Integrate AvailableSlotPicker
    - Integrate ChildSelector (if multiple children)
    - Handle scheduling mutation
    - Show success/error states
    - _Requirements: 13.4_

- [ ] 23. Frontend Celebration Components
  - [ ] 23.1 Create BeltEarnedModal component
    - Display new belt name with congratulations
    - Show celebratory animation
    - Include dismiss button
    - _Requirements: 13.3, 13.6_

  - [ ] 23.2 Create CelebrationOverlay component
    - Generic celebration animation (confetti, etc.)
    - Configurable duration
    - _Requirements: 13.3_

- [ ] 24. Frontend Workspace Dashboard Page
  - [ ] 24.1 Create WorkspaceDashboard container component
    - Fetch workspace summary with useWorkspaceSummary
    - Manage scheduling modal state
    - Integrate belt celebration hook
    - Compose all dashboard components
    - Handle loading and error states
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6_

  - [ ] 24.2 Remove old AI chat interface components
    - Remove complex conversation components
    - Remove memory/conversation history displays
    - Remove AI-generated coaching tips
    - _Requirements: 13.5_

- [ ] 25. Checkpoint - Frontend Complete
  - Ensure frontend workspace displays correctly, scheduling flow works end-to-end with backend. Ask the user if questions arise.

- [ ] 26. Configuration and Feature Flags
  - [ ] 26.1 Add workflow configuration properties
    - Add max-exchanges configuration per state
    - Add calendar-lookahead-days setting
    - Add min-slot-duration-minutes setting
    - Add message generator timeout setting
    - _Requirements: 5.6, 7.5, 10.6_

  - [ ] 26.2 Add scheduler configuration properties
    - Add morning-reminder-cron setting
    - Add follow-up-interval-ms setting
    - Add stale-detection-interval-ms setting
    - Add batch-size setting
    - _Requirements: 12.1_

  - [ ] 26.3 Add feature flags
    - Add deterministic-workflow-engine master switch
    - Add ai-message-generation toggle
    - Add morning-reminders toggle
    - _Requirements: 15.6_

- [ ] 27. Observability and Monitoring
  - [ ] 27.1 Implement metrics collection
    - Count of fathers in each workflow state
    - State transition rates
    - Quality Time completion rate
    - Message generation latency
    - AI vs fallback usage
    - _Requirements: 16.2_

  - [ ] 27.2 Implement health endpoint enhancements
    - Report Workflow Engine status
    - Report Google Calendar API status
    - Report WhatsApp API status
    - Report scheduler last-run timestamps
    - _Requirements: 16.5_

  - [ ] 27.3 Add structured logging with father_id
    - Include father_id in all workflow logs
    - Log fallback usage as warnings
    - Log all state transitions
    - _Requirements: 16.4, 16.6_

- [ ] 28. Integration and Wire-Up
  - [ ] 28.1 Wire WorkflowEngine to WhatsApp webhook
    - Replace ConversationOrchestrator calls with WorkflowEngine
    - Maintain existing message parsing and response delivery
    - _Requirements: 11.1_

  - [ ] 28.2 Wire schedulers to WorkflowEngine
    - Connect morning reminder job
    - Connect follow-up transition job
    - Connect stale state detection job
    - _Requirements: 12.1_

  - [ ] 28.3 Register all StateHandlers with WorkflowEngine
    - Auto-discover or explicitly register all StateHandler implementations
    - Map each handler to its WorkflowState
    - _Requirements: 1.1_

  - [ ]* 28.4 Write end-to-end integration tests
    - Test full WhatsApp → Backend → Database → Response flow
    - Test scheduler-triggered transitions
    - Test frontend → API → Backend flows
    - _Requirements: 11.1, 12.1, 13.1_

- [ ] 29. Final Checkpoint - Full System Integration
  - Ensure all tests pass, system runs end-to-end, migrations complete successfully. Ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Backend tasks (1-16) should be completed before frontend tasks (17-25)
- Database migrations (task 1) must be run first as all other tasks depend on the schema
- The WorkflowEngine replaces the existing ConversationOrchestrator
- AI is used ONLY for text generation (MessageGenerator), not for decisions
- Pattern matching uses regex, not AI/NLU
- All times are handled in father's local timezone

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4", "1.5"] },
    { "id": 1, "tasks": ["1.6", "2.1", "2.3", "2.5"] },
    { "id": 2, "tasks": ["2.2", "2.4", "2.6", "2.8", "2.9"] },
    { "id": 3, "tasks": ["2.7", "4.1", "4.2", "4.3", "4.4", "4.5"] },
    { "id": 4, "tasks": ["5.1", "5.2", "6.1", "6.2", "6.3", "8.1", "8.2"] },
    { "id": 5, "tasks": ["5.3", "5.4", "5.5", "6.4", "6.5", "8.3", "8.4"] },
    { "id": 6, "tasks": ["5.6", "6.6", "6.7", "6.9", "6.10", "6.11", "8.5", "8.6"] },
    { "id": 7, "tasks": ["6.8", "6.12", "8.7", "9.1", "9.2"] },
    { "id": 8, "tasks": ["9.3", "9.5", "9.7", "9.8", "9.12", "11.1"] },
    { "id": 9, "tasks": ["9.4", "9.6", "9.9", "9.10", "9.11", "9.13", "9.14", "11.2"] },
    { "id": 10, "tasks": ["11.3", "11.4", "11.5", "11.6", "11.5.1", "11.5.2", "11.5.3"] },
    { "id": 11, "tasks": ["11.5.4", "11.5.5", "12.1", "12.2", "12.3"] },
    { "id": 12, "tasks": ["11.5.6", "11.5.7", "12.4", "12.5", "12.6", "12.7"] },
    { "id": 13, "tasks": ["12.8", "12.9", "12.10", "12.11", "14.1"] },
    { "id": 14, "tasks": ["14.2", "14.4", "14.5"] },
    { "id": 15, "tasks": ["14.3", "14.6", "15.1", "15.2", "15.3", "15.4", "15.5"] },
    { "id": 16, "tasks": ["15.6", "15.7", "15.8", "15.9", "15.10", "15.11"] },
    { "id": 17, "tasks": ["15.12", "17.1"] },
    { "id": 18, "tasks": ["18.1", "18.2"] },
    { "id": 19, "tasks": ["19.1", "19.2", "19.3", "19.4", "19.5"] },
    { "id": 20, "tasks": ["20.1", "20.2", "20.3", "20.4", "20.5", "20.6"] },
    { "id": 21, "tasks": ["22.1", "22.2", "22.3"] },
    { "id": 22, "tasks": ["22.4", "23.1", "23.2"] },
    { "id": 23, "tasks": ["24.1", "24.2"] },
    { "id": 24, "tasks": ["26.1", "26.2", "26.3"] },
    { "id": 25, "tasks": ["27.1", "27.2", "27.3"] },
    { "id": 26, "tasks": ["28.1", "28.2", "28.3"] },
    { "id": 27, "tasks": ["28.4"] }
  ]
}
```
