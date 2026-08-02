# Technical Design — Deterministic Workflow Engine

## Overview

This design document defines a fundamental architectural simplification of the Dad Coach application, transforming it from an AI-driven conversational coaching experience to a **deterministic workflow engine**. The current architecture relies heavily on AI for decision-making, conversation orchestration, and user guidance. This transformation replaces AI-driven decisions with explicit backend-owned business logic.

**Orchestration Layer**: This specification serves as the central orchestration layer connecting:
- **WEB-SPEC-007 (Onboarding & Activation)**: Workflow engine receives activated fathers after onboarding completes
- **WEB-SPEC-008 (Father Workspace)**: Workflow engine provides dashboard data and handles web-initiated actions

### Design Goals

1. **Deterministic Behavior**: Backend owns all business logic; AI only generates natural language messages (per AI Usage Policy)
2. **Predictable User Experience**: Same inputs produce same outputs regardless of AI behavior
3. **Simplified Architecture**: Clear state machine with defined transitions replaces complex conversation orchestration
4. **Easy Maintainability**: Simple, debuggable code paths with observable state
5. **Read Before Write**: System always synchronizes with current state before any action
6. **Mission Abstraction**: Architecture supports multiple mission types; MVP implements Quality Time
7. **Belt System Preservation**: SACRED — Belt progression must not be removed or redefined

### Architecture Philosophy

```
WhatsApp Message Arrives / Web Request / Scheduler Trigger
    ↓
Workflow Engine (Central Orchestrator)
    ↓
Read Current State (Database + Google Calendar)
    ↓
Deterministic Business Logic (Pattern Matching + State Machine)
    ↓
Mission Service (Abstract → QualityTimeMissionService for MVP)
    ↓
Google Calendar Sync (External)
    ↓
Belt/Streak/Dashboard Update (Database)
    ↓
AI Message Generator (ONLY writes natural language — per AI Usage Policy)
    ↓
Response Delivery (WhatsApp / REST API / WebSocket)
```

**Key Principle**: The AI is NOT the orchestrator. The `WorkflowEngine` is the orchestrator. AI is a text-generation utility used sparingly per the AI Usage Policy.

### AI Usage Policy (Aligned with Requirements)

**Where AI ADDS value:**
- ✅ Activity recommendations (ACTIVITY_IDEAS state, on-demand)
- ✅ Short personalized encouragement (~10% of completion messages)
- ✅ Quality Time summaries (brief, 1-2 sentences)
- ✅ Belt level-up celebration messages

**Where AI is NOT used:**
- ❌ State transitions or decision-making
- ❌ Conversation orchestration
- ❌ Data extraction or interpretation
- ❌ Daily coaching messages (use templates)

**AI Budget**: Maximum 2 AI calls per user per day.

### Language Support

The system supports **English (en)** and **Hebrew (he)** only:
- English is the default language
- Hebrew requires RTL support in frontend
- All patterns, templates, and messages are localized for both languages
- Language preference is stored in Father entity and loaded during System State read


## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Dad Coach Backend (Spring Boot)                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐        │
│  │  WhatsApp       │    │   REST API      │    │   Scheduler     │        │
│  │  Webhook        │    │   Controllers   │    │   Jobs          │        │
│  └────────┬────────┘    └────────┬────────┘    └────────┬────────┘        │
│           │                      │                      │                  │
│           └──────────────────────┼──────────────────────┘                  │
│                                  ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                        WorkflowEngine                                  │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │ │
│  │  │  State Machine: WELCOME → SCHEDULE_QUALITY_TIME → WAITING →     │  │ │
│  │  │                 QUALITY_TIME_FOLLOW_UP → (back to schedule)     │  │ │
│  │  │                 Any State ↔ ACTIVITY_IDEAS | DASHBOARD          │  │ │
│  │  └─────────────────────────────────────────────────────────────────┘  │ │
│  └────────┬──────────────────────────┬──────────────────────────────────┘ │
│           │                          │                                     │
│           ▼                          ▼                                     │
│  ┌─────────────────┐        ┌─────────────────┐                           │
│  │ SystemState     │        │ Message         │                           │
│  │ Loader          │        │ Generator       │                           │
│  │ (Read Before    │        │ (AI Text Only)  │                           │
│  │  Write)         │        │                 │                           │
│  └────────┬────────┘        └────────┬────────┘                           │
│           │                          │                                     │
│           ▼                          ▼                                     │
│  ┌─────────────────┐        ┌─────────────────┐                           │
│  │ Google Calendar │        │ IntelligenceLayer│                          │
│  │ Service         │        │ (existing)       │                          │
│  └─────────────────┘        └─────────────────┘                           │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                              PostgreSQL Database                            │
│  ┌─────────────┐ ┌───────────────┐ ┌──────────────────┐ ┌──────────────┐  │
│  │ father      │ │ quality_time  │ │ workflow_state   │ │ message      │  │
│  │             │ │               │ │ _transition_log  │ │ _templates   │  │
│  └─────────────┘ └───────────────┘ └──────────────────┘ └──────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```


### Architecture Decisions

**AD-1: State Machine Over AI Orchestration** — The `WorkflowEngine` implements a deterministic state machine with exactly 6 states. All state transitions are driven by explicit triggers (user input patterns, timers, calendar events), not AI interpretation. This replaces the AI-driven `ConversationOrchestrator`.

**AD-2: AI as Message Generator Only** — AI (via existing `IntelligenceLayer`) is used exclusively for generating natural language messages in the father's preferred language (English or Hebrew). It receives structured context including language preference and returns localized text. It makes no decisions about state transitions, data collection, or system behavior. Per AI Usage Policy, AI is used sparingly (max 2 calls/user/day).

**AD-3: Read Before Write Principle** — Every request processing cycle begins by loading complete system state from authoritative sources (database, Google Calendar). The system never asks for information it already has, never suggests times that conflict with the calendar.

**AD-4: Mission Abstraction with Quality Time MVP** — The system uses a Mission abstraction as a container for parenting activities. For MVP, every Mission is a Quality Time session. The architecture remains extensible for future mission types (reading together, outdoor activities, etc.). This is implemented via `MissionService` interface with `QualityTimeMissionService` as the MVP implementation.

**AD-5: Belt Progression (SACRED)** — Belt progression is calculated directly from Quality Time (Mission) completion count, not from complex scoring. Thresholds: White (0-2), Yellow (3-9), Orange (10-24), Green (25-49), Blue (50-99), Brown (100-199), Black (200+). The Belt System MUST NOT be removed or redefined.

**AD-6: Pattern Matching Over NLU** — User message interpretation uses regex and keyword matching, not AI/NLU. Each state defines expected patterns for both English and Hebrew; unmatched input triggers clarification with explicit options.

**AD-7: Fallback-First Error Strategy** — Every message type has a pre-written fallback template in both English and Hebrew. If AI message generation fails or times out (5 seconds), the fallback is used immediately.

**AD-8: WEB-SPEC Integration** — This workflow engine serves as the orchestration layer:
- Receives activated fathers from WEB-SPEC-007 (Onboarding)
- Provides dashboard data to WEB-SPEC-008 (Father Workspace) via /api/workspace/summary
- Dashboard metrics computed from Mission (Quality Time) records in real-time


### State Machine Definition

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Workflow State Machine                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────┐                                                                │
│  │ WELCOME  │ ─────────── father acknowledges ──────────┐                   │
│  └──────────┘                                           │                   │
│       │ (new father only)                               ▼                   │
│       │                                        ┌────────────────────┐       │
│       │                                        │ SCHEDULE_QUALITY   │       │
│       └────────────────────────────────────────│ _TIME              │◄──────┤
│                                                └────────────────────┘       │
│                                                         │                   │
│                                            event created in calendar        │
│                                                         │                   │
│                                                         ▼                   │
│                                                ┌────────────────────┐       │
│                                                │     WAITING        │       │
│                                                └────────────────────┘       │
│                                                         │                   │
│                                           scheduled time passes             │
│                                                         │                   │
│                                                         ▼                   │
│                                                ┌────────────────────┐       │
│                                                │ QUALITY_TIME       │       │
│                                                │ _FOLLOW_UP         │───────┘
│                                                └────────────────────┘       │
│                                                    (completion/miss)        │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Overlay States (accessible from any state, return to previous):     │   │
│  │  • ACTIVITY_IDEAS — when father explicitly requests ideas            │   │
│  │  • DASHBOARD — frontend only (not a WhatsApp state)                  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```


## Components and Interfaces

### Backend Package Structure

```
com.dadcoach.workflow/
├── WorkflowEngine.java                 # Main orchestrator interface
├── WorkflowEngineImpl.java             # State machine implementation
├── WorkflowState.java                  # Enum: WELCOME, SCHEDULE_QUALITY_TIME, etc.
├── WorkflowContext.java                # Immutable context for processing
├── WorkflowTransition.java             # State transition record
├── config/
│   └── WorkflowProperties.java         # Configuration properties
├── state/
│   ├── StateHandler.java               # Interface for state-specific logic
│   ├── WelcomeStateHandler.java        # WELCOME state behavior
│   ├── ScheduleStateHandler.java       # SCHEDULE_QUALITY_TIME behavior
│   ├── WaitingStateHandler.java        # WAITING state behavior
│   ├── FollowUpStateHandler.java       # QUALITY_TIME_FOLLOW_UP behavior
│   └── ActivityIdeasStateHandler.java  # ACTIVITY_IDEAS behavior
├── pattern/
│   ├── PatternMatcher.java             # Message pattern matching interface
│   ├── PatternMatcherImpl.java         # Regex/keyword implementation
│   ├── PatternResult.java              # Match result with action
│   └── StatePatterns.java              # Pattern definitions per state
├── message/
│   ├── MessageGenerator.java           # Interface for message generation
│   ├── MessageGeneratorImpl.java       # AI-backed implementation
│   ├── MessageType.java                # Enum of message types
│   ├── MessageContext.java             # Context for message generation
│   └── FallbackMessages.java           # Pre-written fallback templates
├── dto/
│   └── WorkflowResponse.java           # Response DTO
└── repository/
    └── WorkflowTransitionLogRepository.java

com.dadcoach.qualitytime/
├── QualityTime.java                    # JPA entity
├── QualityTimeStatus.java              # Enum: SCHEDULED, COMPLETED, MISSED, CANCELLED
├── QualityTimeService.java             # Service interface
├── QualityTimeServiceImpl.java         # Implementation
├── dto/
│   ├── AvailableSlotDto.java           # Time slot suggestion
│   ├── ScheduleRequest.java            # Schedule request
│   └── QualityTimeResponse.java        # Response DTO
└── repository/
    └── QualityTimeRepository.java

com.dadcoach.mission/
├── Mission.java                        # Abstract mission interface
├── MissionType.java                    # Enum: QUALITY_TIME, (future: READING, OUTDOOR, etc.)
├── MissionService.java                 # Abstract service interface for mission operations
├── MissionStatus.java                  # Enum: SCHEDULED, COMPLETED, MISSED, CANCELLED
├── impl/
│   └── QualityTimeMissionService.java  # MVP implementation (delegates to QualityTimeService)
└── factory/
    └── MissionServiceFactory.java      # Factory to get appropriate MissionService by type

com.dadcoach.systemstate/
├── SystemStateLoader.java              # Interface for state loading
├── SystemStateLoaderImpl.java          # Implementation
├── SystemState.java                    # Complete state record
└── cache/
    └── SystemStateCache.java           # Request-scoped cache
```

### Mission Abstraction

The Mission abstraction enables future extensibility while keeping MVP simple. For MVP, every Mission is a Quality Time session.

```java
package com.dadcoach.mission;

/**
 * Abstract representation of a parenting activity mission.
 * 
 * For MVP, the only mission type is QUALITY_TIME.
 * Architecture supports future mission types: READING_TOGETHER, OUTDOOR_ACTIVITY, etc.
 */
public interface Mission {
    UUID getId();
    UUID getFatherId();
    Long getChildId();
    MissionType getType();
    MissionStatus getStatus();
    Instant getScheduledStart();
    Instant getScheduledEnd();
    String getCompletionNotes();
    Instant getCompletedAt();
}

/**
 * Mission types. MVP only implements QUALITY_TIME.
 */
public enum MissionType {
    QUALITY_TIME,       // MVP: Calendar-backed quality time with child
    // Future types (not implemented in MVP):
    // READING_TOGETHER,
    // OUTDOOR_ACTIVITY,
    // LEARNING_MOMENT,
    // CREATIVE_PLAY
}

/**
 * Abstract service for mission operations.
 * Each MissionType has a corresponding implementation.
 */
public interface MissionService {
    Mission schedule(UUID fatherId, Long childId, Instant startTime, Duration duration);
    Mission complete(UUID missionId, String notes);
    Mission cancel(UUID missionId);
    Optional<Mission> getNextScheduled(UUID fatherId);
    List<Mission> getRecentCompleted(UUID fatherId, int limit);
    MissionType getSupportedType();
}

/**
 * Factory to obtain the correct MissionService for a given type.
 * MVP returns QualityTimeMissionService for all requests.
 */
@Component
public class MissionServiceFactory {
    private final Map<MissionType, MissionService> services;
    
    public MissionServiceFactory(List<MissionService> missionServices) {
        this.services = missionServices.stream()
            .collect(Collectors.toMap(MissionService::getSupportedType, s -> s));
    }
    
    public MissionService getService(MissionType type) {
        return services.get(type);
    }
    
    // MVP convenience method - always returns Quality Time service
    public MissionService getDefaultService() {
        return services.get(MissionType.QUALITY_TIME);
    }
}
```

**MVP Implementation Note**: The `QualityTimeMissionService` wraps `QualityTimeService` and implements the `MissionService` interface. The workflow engine uses `MissionService` abstraction, making it easy to add new mission types in the future without changing the core workflow logic.


### Core Interfaces

#### WorkflowEngine Interface

```java
package com.dadcoach.workflow;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.OutboundMessageDto;

/**
 * Central orchestrator for the deterministic workflow state machine.
 * Replaces the AI-driven ConversationOrchestrator.
 * 
 * All business logic decisions are made here. AI is only used for 
 * text generation via MessageGenerator.
 */
public interface WorkflowEngine {

    /**
     * Process an inbound WhatsApp message through the workflow state machine.
     * 
     * Pipeline:
     * 1. Load SystemState (Read Before Write)
     * 2. Determine current workflow state
     * 3. Match message against expected patterns for current state
     * 4. Execute business logic for matched pattern
     * 5. Generate response message (AI or fallback)
     * 6. Persist state changes
     * 7. Log state transition
     * 
     * @param message the normalized inbound message
     * @return the outbound message to send via WhatsApp
     */
    OutboundMessageDto processMessage(InboundMessageDto message);

    /**
     * Trigger a state transition by an external event (e.g., scheduler).
     * 
     * @param fatherId the father to transition
     * @param trigger the trigger reason (e.g., QUALITY_TIME_ENDED, TIMEOUT)
     * @return the outbound message to send, or empty if no message needed
     */
    Optional<OutboundMessageDto> triggerTransition(UUID fatherId, WorkflowTrigger trigger);
}
```

#### StateHandler Interface

```java
package com.dadcoach.workflow.state;

/**
 * Handler for state-specific behavior. Each workflow state has a dedicated handler.
 */
public interface StateHandler {

    /**
     * @return the workflow state this handler manages
     */
    WorkflowState getState();

    /**
     * Get the expected message patterns for this state.
     */
    List<StatePattern> getExpectedPatterns();

    /**
     * Handle a matched pattern within this state.
     * 
     * @param context the workflow context with system state
     * @param match the pattern match result
     * @return the state action to take (transition, respond, etc.)
     */
    StateAction handle(WorkflowContext context, PatternResult match);

    /**
     * Handle an unmatched message (no pattern matched).
     * 
     * @param context the workflow context
     * @return clarification response with explicit options
     */
    StateAction handleUnmatched(WorkflowContext context);
}
```


#### MessageGenerator Interface

```java
package com.dadcoach.workflow.message;

/**
 * Generates natural language messages using AI.
 * 
 * IMPORTANT: This service ONLY generates text. It does NOT:
 * - Decide which state to transition to
 * - Decide what information to ask for
 * - Decide whether the father completed Quality Time
 * - Make any determination about system state
 * 
 * Messages are generated in the father's preferred language (English or Hebrew)
 * using localization keys and message templates.
 */
public interface MessageGenerator {

    /**
     * Generate a message of the specified type.
     * 
     * @param type the message type (determines template/structure)
     * @param context the message context with required data fields and language preference
     * @return the generated message text in the father's preferred language
     * @throws MessageGenerationException if generation fails
     */
    String generate(MessageType type, MessageContext context);

    /**
     * Generate a message with fallback on failure.
     * 
     * @param type the message type
     * @param context the message context
     * @param timeoutMs maximum time to wait for AI (5000ms default)
     * @return the generated message or fallback if AI fails/times out
     */
    String generateWithFallback(MessageType type, MessageContext context, long timeoutMs);
}
```

#### SystemStateLoader Interface

```java
package com.dadcoach.systemstate;

/**
 * Loads complete system state before any action (Read Before Write principle).
 */
public interface SystemStateLoader {

    /**
     * Load the complete system state for a father.
     * 
     * Includes:
     * - Father profile (name, children, preferences, locale, timezone)
     * - Current workflow state
     * - Google Calendar events for next 7 days (if connected)
     * - Scheduled Quality Time events
     * - Dashboard metrics (belt, streak, achievements)
     * - Recent conversation context (last 10 messages)
     * 
     * @param fatherId the father ID
     * @return the complete system state
     */
    SystemState loadState(UUID fatherId);

    /**
     * Load available time slots for Quality Time scheduling.
     * 
     * @param fatherId the father ID
     * @param daysAhead number of days to look ahead (default 7)
     * @return list of available slots sorted by proximity
     */
    List<AvailableSlot> loadAvailableSlots(UUID fatherId, int daysAhead);
}
```


### REST API Design

#### New Simplified Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/workspace/summary` | GET | Dashboard data for current father |
| `/api/v1/quality-time/available-slots` | GET | Available time slots from Google Calendar |
| `/api/v1/quality-time/schedule` | POST | Schedule a new Quality Time event |
| `/api/v1/quality-time/{id}/complete` | POST | Mark Quality Time as completed |
| `/api/v1/quality-time/{id}/cancel` | POST | Cancel a scheduled Quality Time |
| `/api/v1/activity-ideas` | GET | AI-generated activity suggestions |

#### API Contracts

**GET /api/v1/workspace/summary**

Response:
```json
{
  "father_display_name": "David",
  "current_workflow_state": "WAITING",
  "current_belt": "GREEN",
  "belt_progress": {
    "current_count": 32,
    "next_belt_threshold": 50,
    "progress_percentage": 64
  },
  "current_streak": 5,
  "longest_streak": 12,
  "total_quality_times_completed": 32,
  "weekly_goal_progress": {
    "completed_hours": 2.5,
    "goal_hours": 5.0,
    "progress_percentage": 50
  },
  "next_quality_time": {
    "id": "uuid",
    "child_name": "Maya",
    "scheduled_start": "2024-01-15T17:00:00Z",
    "scheduled_end": "2024-01-15T17:30:00Z",
    "status": "SCHEDULED"
  },
  "recent_quality_times": [
    {
      "id": "uuid",
      "child_name": "Maya",
      "completed_at": "2024-01-14T18:30:00Z",
      "duration_minutes": 30
    }
  ],
  "recent_achievements": [
    {
      "achievement_id": "first-quality-time",
      "name": "First Step",
      "earned_at": "2024-01-01T10:00:00Z"
    }
  ],
  "next_milestone": {
    "name": "Blue Belt",
    "quality_times_remaining": 18
  }
}
```


**GET /api/v1/quality-time/available-slots**

Query Parameters:
- `days_ahead`: number (default: 7)
- `min_duration_minutes`: number (default: 30)

Response:
```json
{
  "slots": [
    {
      "start_time": "2024-01-15T17:00:00Z",
      "end_time": "2024-01-15T19:00:00Z",
      "duration_minutes": 120
    },
    {
      "start_time": "2024-01-16T09:00:00Z",
      "end_time": "2024-01-16T12:00:00Z",
      "duration_minutes": 180
    }
  ],
  "calendar_connected": true,
  "timezone": "America/Mexico_City"
}
```

**POST /api/v1/quality-time/schedule**

Request:
```json
{
  "child_id": "uuid",
  "start_time": "2024-01-15T17:00:00Z",
  "duration_minutes": 30
}
```

Response:
```json
{
  "quality_time_id": "uuid",
  "calendar_event_id": "google-event-id",
  "child_name": "Sofia",
  "start_time": "2024-01-15T17:00:00Z",
  "end_time": "2024-01-15T17:30:00Z",
  "status": "SCHEDULED"
}
```

**POST /api/v1/quality-time/{id}/complete**

Request:
```json
{
  "notes": "We played soccer together, she scored 3 goals!"
}
```

Response:
```json
{
  "quality_time_id": "uuid",
  "status": "COMPLETED",
  "streak_updated": true,
  "new_streak": 6,
  "belt_earned": null,
  "points_awarded": 10
}
```

**GET /api/v1/activity-ideas**

Query Parameters:
- `child_id`: UUID (required)

Response:
```json
{
  "ideas": [
    {
      "title": "Cooking Together",
      "description": "Prepare a simple recipe together. Let Sofia measure ingredients and mix them.",
      "duration_minutes": 30,
      "indoor": true
    },
    {
      "title": "Nature Walk",
      "description": "Take a walk in the park and identify 5 different plants or animals.",
      "duration_minutes": 45,
      "indoor": false
    },
    {
      "title": "Story Time with Voices",
      "description": "Read a book together using different voices for each character.",
      "duration_minutes": 20,
      "indoor": true
    }
  ]
}
```


## Data Models

### Database Schema

#### New Tables

**quality_time table**
```sql
CREATE TABLE quality_time (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL REFERENCES father(id),
    child_id            BIGINT NOT NULL REFERENCES child(id),
    google_calendar_event_id VARCHAR(255),
    scheduled_start     TIMESTAMPTZ NOT NULL,
    scheduled_end       TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    completion_notes    TEXT,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    reminder_sent       BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_sent      BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_qt_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'MISSED', 'CANCELLED'))
);

CREATE INDEX idx_quality_time_father ON quality_time(father_id);
CREATE INDEX idx_quality_time_father_scheduled ON quality_time(father_id, scheduled_start) 
    WHERE status = 'SCHEDULED';
CREATE INDEX idx_quality_time_status ON quality_time(status, scheduled_end) 
    WHERE status = 'SCHEDULED';
```

**workflow_state_transition_log table**
```sql
CREATE TABLE workflow_state_transition_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL REFERENCES father(id),
    from_state      VARCHAR(30) NOT NULL,
    to_state        VARCHAR(30) NOT NULL,
    trigger_reason  VARCHAR(50) NOT NULL,
    trigger_message_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wstl_father ON workflow_state_transition_log(father_id, created_at DESC);
```

**message_templates table**
```sql
CREATE TABLE message_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type    VARCHAR(50) NOT NULL,
    template_text   TEXT NOT NULL,
    language        VARCHAR(10) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_templates_type_lang UNIQUE (message_type, language),
    CONSTRAINT chk_message_templates_lang CHECK (language IN ('en', 'he'))
);
-- Note: Templates are stored per language (English and Hebrew). The message_type + language 
-- combination is unique. The system loads the appropriate template based on father's language preference.
```


#### Modified Tables

**father table additions**
```sql
ALTER TABLE father ADD COLUMN current_workflow_state VARCHAR(30) DEFAULT 'WELCOME';
ALTER TABLE father ADD COLUMN previous_workflow_state VARCHAR(30);
ALTER TABLE father ADD COLUMN workflow_state_entered_at TIMESTAMPTZ;
ALTER TABLE father ADD COLUMN welcomed_at TIMESTAMPTZ;
ALTER TABLE father ADD COLUMN quality_time_streak INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN quality_time_longest_streak INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN total_quality_times_completed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN current_belt VARCHAR(20) NOT NULL DEFAULT 'WHITE';

CREATE INDEX idx_father_workflow_state ON father(current_workflow_state);
```

### JPA Entities

#### QualityTime Entity

```java
package com.dadcoach.qualitytime;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quality_time")
public class QualityTime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private UUID fatherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Column(name = "child_id", insertable = false, updatable = false)
    private Long childId;

    @Column(name = "google_calendar_event_id", length = 255)
    private String googleCalendarEventId;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QualityTimeStatus status = QualityTimeStatus.SCHEDULED;

    @Column(name = "completion_notes", columnDefinition = "TEXT")
    private String completionNotes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent = false;

    @Column(name = "follow_up_sent", nullable = false)
    private boolean followUpSent = false;

    // ... getters, setters, and business methods
}
```


#### WorkflowState Enum

```java
package com.dadcoach.workflow;

import java.util.Set;

/**
 * The six workflow states in the deterministic state machine.
 * 
 * This is the central orchestration layer connecting:
 * - WEB-SPEC-007 (Onboarding): New fathers arrive in WELCOME state
 * - WEB-SPEC-008 (Father Workspace): DASHBOARD provides metrics
 */
public enum WorkflowState {
    /**
     * Initial state for new fathers arriving from WEB-SPEC-007 Onboarding.
     * Explains Dad Coach, guides to first Mission (Quality Time).
     */
    WELCOME(Set.of(SCHEDULE_QUALITY_TIME)),

    /**
     * Active scheduling state for Missions. Reads Google Calendar, suggests slots, creates events.
     * MVP Mission type: Quality Time
     */
    SCHEDULE_QUALITY_TIME(Set.of(WAITING, ACTIVITY_IDEAS)),

    /**
     * Passive waiting state. Daily morning reminder if a Mission (Quality Time) exists today.
     */
    WAITING(Set.of(QUALITY_TIME_FOLLOW_UP, SCHEDULE_QUALITY_TIME, ACTIVITY_IDEAS)),

    /**
     * Post-event state. Asks if father completed the Mission, updates dashboard metrics
     * which are consumed by WEB-SPEC-008 Father Workspace.
     */
    QUALITY_TIME_FOLLOW_UP(Set.of(SCHEDULE_QUALITY_TIME)),

    /**
     * On-demand state. Triggered only when father explicitly asks for ideas.
     * This is one of the few places AI is used per AI Usage Policy.
     * Returns to previous state when done.
     */
    ACTIVITY_IDEAS(Set.of(WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP)),

    /**
     * Frontend-only state for dashboard display (WEB-SPEC-008).
     * Not persisted in WhatsApp flow.
     */
    DASHBOARD(Set.of());

    private final Set<WorkflowState> validTransitions;

    WorkflowState(Set<WorkflowState> validTransitions) {
        this.validTransitions = validTransitions;
    }

    public boolean canTransitionTo(WorkflowState target) {
        return validTransitions.contains(target);
    }

    public Set<WorkflowState> getValidTransitions() {
        return validTransitions;
    }
}
```

#### Belt Enum

```java
package com.dadcoach.workflow;

/**
 * Belt progression levels based on Mission (Quality Time) completion count.
 * 
 * SACRED: The Belt System MUST NOT be removed or redefined.
 * Progression: Weekly Goal → Completed Hours → XP → Belt
 * 
 * Thresholds represent cumulative completed Missions.
 */
public enum Belt {
    WHITE(0, 2),
    YELLOW(3, 9),
    ORANGE(10, 24),
    GREEN(25, 49),
    BLUE(50, 99),
    BROWN(100, 199),
    BLACK(200, Integer.MAX_VALUE);

    private final int minCompletions;
    private final int maxCompletions;

    Belt(int minCompletions, int maxCompletions) {
        this.minCompletions = minCompletions;
        this.maxCompletions = maxCompletions;
    }

    /**
     * Calculate belt from total Mission (Quality Time) completions.
     * This is the core of the SACRED Belt System.
     */
    public static Belt fromCompletionCount(int count) {
        for (Belt belt : values()) {
            if (count >= belt.minCompletions && count <= belt.maxCompletions) {
                return belt;
            }
        }
        return BLACK;
    }

    public int getMinCompletions() {
        return minCompletions;
    }

    public int getMaxCompletions() {
        return maxCompletions;
    }

    public Belt getNextBelt() {
        int ordinal = this.ordinal();
        if (ordinal >= values().length - 1) {
            return null; // BLACK is the highest
        }
        return values()[ordinal + 1];
    }
    
    /**
     * Calculate completions remaining to reach next belt.
     */
    public int completionsToNextBelt(int currentCount) {
        Belt next = getNextBelt();
        if (next == null) return 0;
        return next.getMinCompletions() - currentCount;
    }
}
```


### Pattern Matching

#### Pattern Definitions by State

```java
package com.dadcoach.workflow.pattern;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Pattern definitions for each workflow state.
 * Patterns are evaluated in order; first match wins.
 * 
 * LANGUAGE SUPPORT: English (en) and Hebrew (he) only.
 * NO Spanish patterns.
 */
public class StatePatterns {

    // WELCOME state patterns (English and Hebrew)
    public static final List<StatePattern> WELCOME_PATTERNS = List.of(
        // Affirmative - ready to schedule (English)
        StatePattern.of(
            "AFFIRMATIVE_EN",
            Pattern.compile("(?i)^(yes|ready|let's go|lets go|ok|okay|sure|start|begin).*"),
            WorkflowAction.TRANSITION_TO_SCHEDULE
        ),
        // Affirmative - ready to schedule (Hebrew)
        StatePattern.of(
            "AFFIRMATIVE_HE",
            Pattern.compile("^(כן|מוכן|יאללה|בסדר|בוא נתחיל|התחל).*"),
            WorkflowAction.TRANSITION_TO_SCHEDULE
        ),
        // Request more information (English)
        StatePattern.of(
            "MORE_INFO_EN",
            Pattern.compile("(?i).*(how|what is|explain|tell me more|more info).*"),
            WorkflowAction.EXPLAIN_AND_REPROMPT
        ),
        // Request more information (Hebrew)
        StatePattern.of(
            "MORE_INFO_HE",
            Pattern.compile(".*(איך|מה זה|הסבר|ספר לי עוד|מידע נוסף).*"),
            WorkflowAction.EXPLAIN_AND_REPROMPT
        )
    );

    // SCHEDULE_QUALITY_TIME state patterns (English and Hebrew)
    public static final List<StatePattern> SCHEDULE_PATTERNS = List.of(
        // Slot selection by number (universal)
        StatePattern.of(
            "SLOT_NUMBER",
            Pattern.compile("^([1-9])$"),
            WorkflowAction.SELECT_SLOT
        ),
        // Skip/postpone (English)
        StatePattern.of(
            "SKIP_EN",
            Pattern.compile("(?i)^(skip|not now|later|another day|cancel).*"),
            WorkflowAction.POSTPONE_SCHEDULING
        ),
        // Skip/postpone (Hebrew)
        StatePattern.of(
            "SKIP_HE",
            Pattern.compile("^(דלג|לא עכשיו|אחר כך|יום אחר|ביטול).*"),
            WorkflowAction.POSTPONE_SCHEDULING
        ),
        // Request more options (English)
        StatePattern.of(
            "MORE_SLOTS_EN",
            Pattern.compile("(?i).*(other|another|more|different).*"),
            WorkflowAction.SHOW_MORE_SLOTS
        ),
        // Request more options (Hebrew)
        StatePattern.of(
            "MORE_SLOTS_HE",
            Pattern.compile(".*(אחר|אחרים|עוד|שונה).*"),
            WorkflowAction.SHOW_MORE_SLOTS
        ),
        // Natural time expression (English)
        StatePattern.of(
            "TIME_EXPRESSION_EN",
            Pattern.compile("(?i).*(tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
                           "\\d{1,2}:\\d{2}|\\d{1,2}\\s*(am|pm)|" +
                           "in the (morning|afternoon|evening)).*"),
            WorkflowAction.PARSE_TIME
        ),
        // Natural time expression (Hebrew)
        StatePattern.of(
            "TIME_EXPRESSION_HE",
            Pattern.compile(".*(מחר|יום ראשון|יום שני|יום שלישי|יום רביעי|יום חמישי|יום שישי|שבת|" +
                           "\\d{1,2}:\\d{2}|" +
                           "ב(בוקר|צהריים|ערב)).*"),
            WorkflowAction.PARSE_TIME
        )
    );

    // QUALITY_TIME_FOLLOW_UP state patterns (English and Hebrew)
    public static final List<StatePattern> FOLLOW_UP_PATTERNS = List.of(
        // Affirmative - completed (English)
        StatePattern.of(
            "COMPLETED_EN",
            Pattern.compile("(?i)^(yes|done|finished|completed|did it|yep|sure).*"),
            WorkflowAction.MARK_COMPLETED
        ),
        // Affirmative - completed (Hebrew)
        StatePattern.of(
            "COMPLETED_HE",
            Pattern.compile("^(כן|סיימתי|עשיתי|הושלם|בוצע).*"),
            WorkflowAction.MARK_COMPLETED
        ),
        // Negative - not completed (English)
        StatePattern.of(
            "NOT_COMPLETED_EN",
            Pattern.compile("(?i)^(no|not|nope|not yet|couldn't|didn't).*"),
            WorkflowAction.MARK_MISSED
        ),
        // Negative - not completed (Hebrew)
        StatePattern.of(
            "NOT_COMPLETED_HE",
            Pattern.compile("^(לא|עוד לא|לא הצלחתי|לא עשיתי).*"),
            WorkflowAction.MARK_MISSED
        )
    );

    // ACTIVITY_IDEAS state patterns (English and Hebrew)
    public static final List<StatePattern> ACTIVITY_IDEAS_PATTERNS = List.of(
        // Select idea by number for details (universal)
        StatePattern.of(
            "IDEA_NUMBER",
            Pattern.compile("^([1-3])$"),
            WorkflowAction.SHOW_IDEA_DETAILS
        ),
        // Request more ideas (English)
        StatePattern.of(
            "MORE_IDEAS_EN",
            Pattern.compile("(?i).*(more|another|other|different).*"),
            WorkflowAction.GENERATE_MORE_IDEAS
        ),
        // Request more ideas (Hebrew)
        StatePattern.of(
            "MORE_IDEAS_HE",
            Pattern.compile(".*(עוד|אחר|שונה|נוסף).*"),
            WorkflowAction.GENERATE_MORE_IDEAS
        ),
        // Exit (English)
        StatePattern.of(
            "EXIT_EN",
            Pattern.compile("(?i)^(thanks|done|enough|ok|bye|exit).*"),
            WorkflowAction.RETURN_TO_PREVIOUS
        ),
        // Exit (Hebrew)
        StatePattern.of(
            "EXIT_HE",
            Pattern.compile("^(תודה|סיימתי|מספיק|בסדר|יציאה).*"),
            WorkflowAction.RETURN_TO_PREVIOUS
        )
    );

    // WAITING state patterns (English and Hebrew)
    public static final List<StatePattern> WAITING_PATTERNS = List.of(
        // Request activity ideas (English)
        StatePattern.of(
            "REQUEST_IDEAS_EN",
            Pattern.compile("(?i).*(ideas|activity|what can I do|suggestions|help me plan).*"),
            WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS
        ),
        // Request activity ideas (Hebrew)
        StatePattern.of(
            "REQUEST_IDEAS_HE",
            Pattern.compile(".*(רעיונות|פעילות|מה אפשר לעשות|הצעות|עזור לי לתכנן).*"),
            WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS
        ),
        // Request reschedule (English)
        StatePattern.of(
            "RESCHEDULE_EN",
            Pattern.compile("(?i).*(reschedule|change|move|cancel).*"),
            WorkflowAction.RESCHEDULE
        ),
        // Request reschedule (Hebrew)
        StatePattern.of(
            "RESCHEDULE_HE",
            Pattern.compile(".*(שנה זמן|שינוי|להזיז|ביטול).*"),
            WorkflowAction.RESCHEDULE
        ),
        // Ask about schedule (English)
        StatePattern.of(
            "SCHEDULE_INQUIRY_EN",
            Pattern.compile("(?i).*(when|schedule|next|my appointment).*"),
            WorkflowAction.SHOW_SCHEDULE
        ),
        // Ask about schedule (Hebrew)
        StatePattern.of(
            "SCHEDULE_INQUIRY_HE",
            Pattern.compile(".*(מתי|לוח זמנים|הבא|הפגישה שלי).*"),
            WorkflowAction.SHOW_SCHEDULE
        ),
        // Request dashboard (English)
        StatePattern.of(
            "DASHBOARD_EN",
            Pattern.compile("(?i).*(dashboard|progress|my status|belt|streak).*"),
            WorkflowAction.SHOW_DASHBOARD_SUMMARY
        ),
        // Request dashboard (Hebrew)
        StatePattern.of(
            "DASHBOARD_HE",
            Pattern.compile(".*(דשבורד|התקדמות|הסטטוס שלי|חגורה|רצף).*"),
            WorkflowAction.SHOW_DASHBOARD_SUMMARY
        )
    );
}
```


### Scheduler Jobs

```java
package com.dadcoach.scheduler;

/**
 * Scheduler jobs for time-based workflow transitions and reminders.
 */
@Component
@RequiredArgsConstructor
public class WorkflowScheduler {

    private final QualityTimeRepository qualityTimeRepository;
    private final FatherRepository fatherRepository;
    private final WorkflowEngine workflowEngine;
    private final WhatsAppService whatsAppService;
    private final SchedulerJobLogRepository jobLogRepository;

    /**
     * Morning reminder job.
     * Runs at 7:50 AM UTC, sends reminders at 8 AM local time for each father.
     * 
     * Frequency: Every day at 7:50 AM UTC
     */
    @Scheduled(cron = "0 50 7 * * *")
    public void morningReminderJob() {
        Instant now = Instant.now();
        Instant endOfDay = now.plus(Duration.ofHours(16)); // Look ahead 16 hours
        
        List<QualityTime> todayQualityTimes = qualityTimeRepository
            .findScheduledBetween(now, endOfDay)
            .stream()
            .filter(qt -> !qt.isReminderSent())
            .filter(qt -> isLocalTime8AM(qt.getFather().getTimezone()))
            .toList();

        for (QualityTime qt : todayQualityTimes) {
            try {
                sendMorningReminder(qt);
                qt.setReminderSent(true);
                qualityTimeRepository.save(qt);
            } catch (Exception e) {
                log.error("Failed to send reminder for QualityTime {}", qt.getId(), e);
            }
        }
        
        logJobExecution("MORNING_REMINDER", todayQualityTimes.size());
    }

    /**
     * Follow-up transition job.
     * Checks for Quality Time events that have ended and transitions fathers.
     * 
     * Frequency: Every 15 minutes
     */
    @Scheduled(fixedRate = 900_000) // 15 minutes
    public void followUpTransitionJob() {
        Instant now = Instant.now();
        
        List<QualityTime> endedQualityTimes = qualityTimeRepository
            .findByStatusAndScheduledEndBefore(QualityTimeStatus.SCHEDULED, now)
            .stream()
            .filter(qt -> !qt.isFollowUpSent())
            .toList();

        int processed = 0;
        for (QualityTime qt : endedQualityTimes) {
            try {
                workflowEngine.triggerTransition(
                    qt.getFatherId(), 
                    WorkflowTrigger.QUALITY_TIME_ENDED
                );
                qt.setFollowUpSent(true);
                qualityTimeRepository.save(qt);
                processed++;
            } catch (Exception e) {
                log.error("Failed to transition father {} for QualityTime {}", 
                    qt.getFatherId(), qt.getId(), e);
            }
        }
        
        logJobExecution("FOLLOW_UP_TRANSITION", processed);
    }

    /**
     * Stale state detection job.
     * Checks for fathers stuck in QUALITY_TIME_FOLLOW_UP for over 24 hours.
     * 
     * Frequency: Every hour
     */
    @Scheduled(fixedRate = 3_600_000) // 1 hour
    public void staleStateDetectionJob() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        
        List<Father> staleFathers = fatherRepository
            .findByWorkflowStateAndStateEnteredAtBefore(
                WorkflowState.QUALITY_TIME_FOLLOW_UP, cutoff
            );

        int processed = 0;
        for (Father father : staleFathers) {
            try {
                // Mark any pending Quality Time as MISSED
                qualityTimeRepository.findLatestScheduledForFather(father.getId())
                    .ifPresent(qt -> {
                        qt.setStatus(QualityTimeStatus.MISSED);
                        qualityTimeRepository.save(qt);
                    });

                // Transition to SCHEDULE_QUALITY_TIME
                workflowEngine.triggerTransition(
                    father.getId(), 
                    WorkflowTrigger.FOLLOW_UP_TIMEOUT
                );
                processed++;
            } catch (Exception e) {
                log.error("Failed to process stale father {}", father.getId(), e);
            }
        }
        
        logJobExecution("STALE_STATE_DETECTION", processed);
    }

    private void logJobExecution(String jobName, int recordsProcessed) {
        SchedulerJobLog log = new SchedulerJobLog(jobName, recordsProcessed);
        jobLogRepository.save(log);
    }
}
```


### Frontend Design (Next.js)

#### Simplified Component Structure

```
src/
├── components/
│   ├── workspace/
│   │   ├── WorkspaceDashboard.tsx       # Main dashboard container
│   │   ├── BeltProgressionCard.tsx      # Belt display with progress bar
│   │   ├── NextQualityTimeCard.tsx      # Upcoming Quality Time display
│   │   ├── StreakDisplay.tsx            # Current and longest streak
│   │   ├── RecentActivityFeed.tsx       # Last 5 Quality Time completions
│   │   └── AchievementBadges.tsx        # Earned achievements grid
│   ├── quality-time/
│   │   ├── ScheduleQualityTime.tsx      # Calendar picker modal
│   │   ├── AvailableSlotPicker.tsx      # Time slot selection
│   │   ├── ChildSelector.tsx            # Child selection (multi-child)
│   │   └── ConfirmationModal.tsx        # Schedule confirmation
│   └── celebrations/
│       ├── BeltEarnedModal.tsx          # Belt progression celebration
│       └── CelebrationOverlay.tsx       # Generic celebration animation
├── hooks/
│   ├── useWorkspaceSummary.ts           # Fetch workspace summary
│   ├── useAvailableSlots.ts             # Fetch available time slots
│   ├── useScheduleQualityTime.ts        # Mutation for scheduling
│   ├── useCompleteQualityTime.ts        # Mutation for completion
│   └── useBeltCelebration.ts            # Belt celebration trigger
├── services/
│   ├── qualityTime.ts                   # Quality Time API calls
│   └── activityIdeas.ts                 # Activity ideas API calls
└── types/
    └── qualityTime.ts                   # TypeScript types
```

#### TypeScript Types

```typescript
// src/types/qualityTime.ts

export type QualityTimeStatus = 'SCHEDULED' | 'COMPLETED' | 'MISSED' | 'CANCELLED';

export type BeltLevel = 'WHITE' | 'YELLOW' | 'ORANGE' | 'GREEN' | 'BLUE' | 'BROWN' | 'BLACK';

export type WorkflowState = 
  | 'WELCOME' 
  | 'SCHEDULE_QUALITY_TIME' 
  | 'WAITING' 
  | 'QUALITY_TIME_FOLLOW_UP' 
  | 'ACTIVITY_IDEAS';

export interface QualityTime {
  id: string;
  childName: string;
  scheduledStart: string; // ISO datetime
  scheduledEnd: string;
  status: QualityTimeStatus;
  completionNotes?: string;
  completedAt?: string;
}

export interface AvailableSlot {
  startTime: string; // ISO datetime
  endTime: string;
  durationMinutes: number;
}

export interface BeltProgress {
  currentCount: number;
  nextBeltThreshold: number;
  progressPercentage: number;
}

export interface WeeklyGoalProgress {
  completedHours: number;
  goalHours: number;
  progressPercentage: number;
}

export interface WorkspaceSummary {
  fatherDisplayName: string;
  currentWorkflowState: WorkflowState;
  currentBelt: BeltLevel;
  beltProgress: BeltProgress;
  currentStreak: number;
  longestStreak: number;
  totalQualityTimesCompleted: number;
  weeklyGoalProgress: WeeklyGoalProgress;
  nextQualityTime: QualityTime | null;
  recentQualityTimes: QualityTime[];
  recentAchievements: Achievement[];
  nextMilestone: {
    name: string;
    qualityTimesRemaining: number;
  } | null;
}

export interface ScheduleRequest {
  childId: string;
  startTime: string;
  durationMinutes: number;
}

export interface ScheduleResponse {
  qualityTimeId: string;
  calendarEventId: string;
  childName: string;
  startTime: string;
  endTime: string;
  status: QualityTimeStatus;
}

export interface ActivityIdea {
  title: string;
  description: string;
  durationMinutes: number;
  indoor: boolean;
}
```


#### React Hooks

```typescript
// src/hooks/useWorkspaceSummary.ts

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/src/lib/api-client';
import type { WorkspaceSummary } from '@/src/types/qualityTime';

export function useWorkspaceSummary() {
  return useQuery<WorkspaceSummary>({
    queryKey: ['workspace', 'summary'],
    queryFn: async () => {
      return apiClient.get<WorkspaceSummary>('/workspace/summary');
    },
    refetchInterval: 60_000, // Refresh every 60 seconds
    staleTime: 30_000,
  });
}
```

```typescript
// src/hooks/useScheduleQualityTime.ts

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/src/lib/api-client';
import type { ScheduleRequest, ScheduleResponse } from '@/src/types/qualityTime';

export function useScheduleQualityTime() {
  const queryClient = useQueryClient();

  return useMutation<ScheduleResponse, Error, ScheduleRequest>({
    mutationFn: async (request) => {
      return apiClient.post<ScheduleResponse>('/quality-time/schedule', request);
    },
    onSuccess: () => {
      // Invalidate workspace summary to reflect new Quality Time
      queryClient.invalidateQueries({ queryKey: ['workspace', 'summary'] });
    },
  });
}
```

```typescript
// src/hooks/useBeltCelebration.ts

import { useState, useEffect } from 'react';
import type { BeltLevel } from '@/src/types/qualityTime';

interface BeltCelebration {
  isActive: boolean;
  newBelt: BeltLevel | null;
  dismiss: () => void;
}

export function useBeltCelebration(currentBelt: BeltLevel, previousBelt?: BeltLevel): BeltCelebration {
  const [celebration, setCelebration] = useState<{ newBelt: BeltLevel } | null>(null);

  useEffect(() => {
    if (previousBelt && currentBelt !== previousBelt) {
      const beltOrder: BeltLevel[] = ['WHITE', 'YELLOW', 'ORANGE', 'GREEN', 'BLUE', 'BROWN', 'BLACK'];
      const currentIndex = beltOrder.indexOf(currentBelt);
      const previousIndex = beltOrder.indexOf(previousBelt);
      
      if (currentIndex > previousIndex) {
        setCelebration({ newBelt: currentBelt });
      }
    }
  }, [currentBelt, previousBelt]);

  return {
    isActive: celebration !== null,
    newBelt: celebration?.newBelt ?? null,
    dismiss: () => setCelebration(null),
  };
}
```


#### Workspace Dashboard Component

```tsx
// src/components/workspace/WorkspaceDashboard.tsx

import { useWorkspaceSummary } from '@/src/hooks/useWorkspaceSummary';
import { useBeltCelebration } from '@/src/hooks/useBeltCelebration';
import { BeltProgressionCard } from './BeltProgressionCard';
import { NextQualityTimeCard } from './NextQualityTimeCard';
import { StreakDisplay } from './StreakDisplay';
import { RecentActivityFeed } from './RecentActivityFeed';
import { AchievementBadges } from './AchievementBadges';
import { BeltEarnedModal } from '../celebrations/BeltEarnedModal';
import { ScheduleQualityTime } from '../quality-time/ScheduleQualityTime';
import { useState } from 'react';

export function WorkspaceDashboard() {
  const { data: summary, isLoading, error } = useWorkspaceSummary();
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  
  const beltCelebration = useBeltCelebration(
    summary?.currentBelt ?? 'WHITE',
    // Track previous belt in local storage or state
  );

  if (isLoading) return <DashboardSkeleton />;
  if (error) return <DashboardError error={error} />;
  if (!summary) return null;

  return (
    <div className="space-y-6 p-4">
      {/* Welcome message - localized based on father's language preference */}
      <h1 className="text-2xl font-bold">
        {language === 'he' ? `שלום, ${summary.fatherDisplayName}! 👋` : `Hi, ${summary.fatherDisplayName}! 👋`}
      </h1>

      {/* Belt Progression */}
      <BeltProgressionCard
        currentBelt={summary.currentBelt}
        progress={summary.beltProgress}
        totalCompleted={summary.totalQualityTimesCompleted}
      />

      {/* Next Quality Time or Schedule CTA */}
      {summary.nextQualityTime ? (
        <NextQualityTimeCard qualityTime={summary.nextQualityTime} />
      ) : (
        <ScheduleQualityTimeCTA onClick={() => setShowScheduleModal(true)} />
      )}

      {/* Streak Display */}
      <StreakDisplay
        currentStreak={summary.currentStreak}
        longestStreak={summary.longestStreak}
      />

      {/* Recent Activity */}
      <RecentActivityFeed activities={summary.recentQualityTimes} />

      {/* Achievements */}
      <AchievementBadges achievements={summary.recentAchievements} />

      {/* Schedule Modal */}
      {showScheduleModal && (
        <ScheduleQualityTime onClose={() => setShowScheduleModal(false)} />
      )}

      {/* Belt Celebration Modal */}
      {beltCelebration.isActive && (
        <BeltEarnedModal
          newBelt={beltCelebration.newBelt!}
          onDismiss={beltCelebration.dismiss}
        />
      )}
    </div>
  );
}
```


## Error Handling

### Error Classification

| Error Type | Recovery Strategy | User Message (localized via message_templates) |
|------------|-------------------|--------------|
| Google Calendar API failure | Retry once with backoff, log error, inform user | en: "We couldn't access your calendar. Please try again." / he: "לא הצלחנו לגשת ללוח השנה שלך. אנא נסה שוב." |
| AI Message generation timeout | Use fallback template | (User sees fallback, no error shown) |
| Database transaction failure | Retry once, then fail with error | en: "An error occurred. Please try again." / he: "אירעה שגיאה. אנא נסה שוב." |
| Invalid workflow state | Log as warning, remain in current state | en: "I didn't understand your message. [explicit options]" / he: "לא הבנתי את ההודעה שלך. [explicit options]" |
| Calendar conflict detected | Re-read calendar, present updated slots | en: "That time is no longer available. Here are other options." / he: "הזמן הזה כבר לא פנוי. הנה אפשרויות אחרות." |
| Rate limit (WhatsApp) | Queue message for delayed delivery | (Message delivered after delay) |

### Fallback Message Templates

```java
public enum MessageType {
    WELCOME_GREETING("welcome_greeting"),
    WELCOME_EXPLAIN("welcome_explain"),
    SCHEDULE_SLOTS("schedule_slots"),
    SCHEDULE_CONFIRM("schedule_confirm"),
    SCHEDULE_NO_SLOTS("schedule_no_slots"),
    WAITING_REMINDER("waiting_reminder"),
    WAITING_SCHEDULE_INFO("waiting_schedule_info"),
    FOLLOW_UP_QUESTION("follow_up_question"),
    FOLLOW_UP_COMPLETED("follow_up_completed"),
    FOLLOW_UP_MISSED("follow_up_missed"),
    ACTIVITY_IDEAS("activity_ideas"),
    DASHBOARD_SUMMARY("dashboard_summary"),
    CLARIFICATION("clarification"),
    ERROR_GENERIC("error_generic");

    private final String templateKey;
    
    // Fallback templates are stored in message_templates table
    // and loaded at startup for fast access
}
```

### Error Logging

All errors include:
- `father_id` for tracing
- `workflow_state` at time of error
- `message_id` if triggered by a message
- Full stack trace for internal errors
- Structured JSON format for log aggregation


## Data Migration

### Migration Strategy

The migration runs as a Flyway script and is designed to be:
1. **Non-destructive**: No existing data is deleted
2. **Idempotent**: Can be run multiple times safely
3. **Reversible**: Feature flags allow fallback to previous architecture

### Migration Script

```sql
-- V15__deterministic_workflow_engine.sql

-- 1. Create quality_time table
CREATE TABLE quality_time (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL REFERENCES father(id),
    child_id            BIGINT NOT NULL REFERENCES child(id),
    google_calendar_event_id VARCHAR(255),
    scheduled_start     TIMESTAMPTZ NOT NULL,
    scheduled_end       TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    completion_notes    TEXT,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    reminder_sent       BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_sent      BOOLEAN NOT NULL DEFAULT FALSE,
    migrated_from_mission_id BIGINT,
    CONSTRAINT chk_qt_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'MISSED', 'CANCELLED'))
);

CREATE INDEX idx_quality_time_father ON quality_time(father_id);
CREATE INDEX idx_quality_time_father_scheduled ON quality_time(father_id, scheduled_start) 
    WHERE status = 'SCHEDULED';
CREATE INDEX idx_quality_time_status ON quality_time(status, scheduled_end) 
    WHERE status = 'SCHEDULED';

-- 2. Create workflow_state_transition_log table
CREATE TABLE workflow_state_transition_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL REFERENCES father(id),
    from_state      VARCHAR(30) NOT NULL,
    to_state        VARCHAR(30) NOT NULL,
    trigger_reason  VARCHAR(50) NOT NULL,
    trigger_message_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wstl_father ON workflow_state_transition_log(father_id, created_at DESC);

-- 3. Create message_templates table
CREATE TABLE message_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type    VARCHAR(50) NOT NULL,
    template_text   TEXT NOT NULL,
    language        VARCHAR(10) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_templates_type_lang UNIQUE (message_type, language),
    CONSTRAINT chk_message_templates_lang CHECK (language IN ('en', 'he'))
);

-- 4. Add workflow columns to father table
ALTER TABLE father ADD COLUMN IF NOT EXISTS current_workflow_state VARCHAR(30) DEFAULT 'SCHEDULE_QUALITY_TIME';
ALTER TABLE father ADD COLUMN IF NOT EXISTS previous_workflow_state VARCHAR(30);
ALTER TABLE father ADD COLUMN IF NOT EXISTS workflow_state_entered_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE father ADD COLUMN IF NOT EXISTS welcomed_at TIMESTAMPTZ;
ALTER TABLE father ADD COLUMN IF NOT EXISTS quality_time_streak INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN IF NOT EXISTS quality_time_longest_streak INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN IF NOT EXISTS total_quality_times_completed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN IF NOT EXISTS current_belt VARCHAR(20) NOT NULL DEFAULT 'WHITE';

CREATE INDEX IF NOT EXISTS idx_father_workflow_state ON father(current_workflow_state);

-- 5. Migrate completed missions to quality_time records
INSERT INTO quality_time (
    father_id, child_id, scheduled_start, scheduled_end, 
    status, completion_notes, completed_at, created_at, updated_at,
    migrated_from_mission_id
)
SELECT 
    f.id,
    m.child_id,
    COALESCE(m.scheduled_for, m.assigned_at),
    COALESCE(m.scheduled_for, m.assigned_at) + INTERVAL '30 minutes',
    CASE 
        WHEN m.status = 'COMPLETED' THEN 'COMPLETED'
        WHEN m.status IN ('EXPIRED', 'ABANDONED', 'SKIPPED') THEN 'MISSED'
        ELSE 'CANCELLED'
    END,
    m.outcome_notes,
    m.completed_at,
    m.assigned_at,
    COALESCE(m.completed_at, m.assigned_at),
    m.id
FROM mission m
JOIN father f ON f.id = m.father_id
WHERE m.status IN ('COMPLETED', 'EXPIRED', 'ABANDONED', 'SKIPPED');

-- 6. Calculate and update belt progression for each father
UPDATE father f
SET 
    total_quality_times_completed = (
        SELECT COUNT(*) FROM quality_time qt 
        WHERE qt.father_id = f.id AND qt.status = 'COMPLETED'
    ),
    current_belt = CASE 
        WHEN (SELECT COUNT(*) FROM quality_time qt WHERE qt.father_id = f.id AND qt.status = 'COMPLETED') >= 200 THEN 'BLACK'
        WHEN (SELECT COUNT(*) FROM quality_time qt WHERE qt.father_id = f.id AND qt.status = 'COMPLETED') >= 100 THEN 'BROWN'
        WHEN (SELECT COUNT(*) FROM quality_time qt WHERE qt.father_id = f.id AND qt.status = 'COMPLETED') >= 50 THEN 'BLUE'
        WHEN (SELECT COUNT(*) FROM quality_time qt WHERE qt.father_id = f.id AND qt.status = 'COMPLETED') >= 25 THEN 'GREEN'
        WHEN (SELECT COUNT(*) FROM quality_time qt WHERE qt.father_id = f.id AND qt.status = 'COMPLETED') >= 10 THEN 'ORANGE'
        WHEN (SELECT COUNT(*) FROM quality_time qt WHERE qt.father_id = f.id AND qt.status = 'COMPLETED') >= 3 THEN 'YELLOW'
        ELSE 'WHITE'
    END,
    current_workflow_state = CASE
        WHEN f.onboarding_state = 'COMPLETED' THEN 'SCHEDULE_QUALITY_TIME'
        ELSE 'WELCOME'
    END,
    quality_time_streak = 0,  -- Fresh start for streak tracking
    quality_time_longest_streak = COALESCE(f.longest_streak, 0);

-- 7. Insert default fallback message templates (English and Hebrew)
INSERT INTO message_templates (message_type, template_text, language) VALUES
-- English templates
('welcome_greeting', 'Hi {father_name}! 👋 Welcome to Dad Coach. I''ll help you spend quality time with your kids. Ready to schedule your first Quality Time?', 'en'),
('schedule_slots', 'Here are the available times:\n{slots}\n\nReply with your preferred number, or type "other" for more options.', 'en'),
('schedule_confirm', '✅ Perfect! Your Quality Time with {child_name} is scheduled for {date_time}. I''ll send you a reminder on the same day. Enjoy!', 'en'),
('waiting_reminder', 'Good morning {father_name}! 🌅 Today you have Quality Time with {child_name} at {time}. Enjoy! 💪', 'en'),
('follow_up_question', 'Did you complete your Quality Time with {child_name}? 🤔\n\nReply "yes" or "no".', 'en'),
('follow_up_completed', '🎉 Excellent! Your streak is now {streak} days. {belt_message} Shall we schedule the next one?', 'en'),
('clarification', 'I didn''t understand your message. Please choose an option:\n{options}', 'en'),
('error_generic', 'An error occurred. Please try again in a moment.', 'en'),
-- Hebrew templates
('welcome_greeting', 'שלום {father_name}! 👋 ברוך הבא ל-Dad Coach. אעזור לך להעביר זמן איכות עם הילדים שלך. מוכן לתזמן את זמן האיכות הראשון שלך?', 'he'),
('schedule_slots', 'הנה הזמנים הפנויים:\n{slots}\n\nענה עם המספר המועדף עליך, או כתוב "אחר" לאפשרויות נוספות.', 'he'),
('schedule_confirm', '✅ מצוין! זמן האיכות שלך עם {child_name} מתוזמן ל-{date_time}. אשלח לך תזכורת ביום עצמו. תהנה!', 'he'),
('waiting_reminder', 'בוקר טוב {father_name}! 🌅 היום יש לך זמן איכות עם {child_name} ב-{time}. תהנו! 💪', 'he'),
('follow_up_question', 'האם השלמת את זמן האיכות עם {child_name}? 🤔\n\nענה "כן" או "לא".', 'he'),
('follow_up_completed', '🎉 מעולה! הרצף שלך עכשיו הוא {streak} ימים. {belt_message} נתזמן את הפעם הבאה?', 'he'),
('clarification', 'לא הבנתי את ההודעה שלך. אנא בחר אפשרות:\n{options}', 'he'),
('error_generic', 'אירעה שגיאה. אנא נסה שוב בעוד רגע.', 'he')
ON CONFLICT (message_type) DO NOTHING;

-- 8. Create scheduler job log table
CREATE TABLE IF NOT EXISTS scheduler_job_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name        VARCHAR(50) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    records_processed INTEGER NOT NULL DEFAULT 0,
    errors_count    INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
);

CREATE INDEX idx_scheduler_job_log ON scheduler_job_log(job_name, started_at DESC);
```


## Testing Strategy

### Test Pyramid

| Level | Scope | Tools | Coverage Target |
|-------|-------|-------|-----------------|
| Unit | WorkflowEngine, StateHandlers, PatternMatcher, MessageGenerator | JUnit 5, Mockito | 80% line coverage |
| Integration | Full workflow pipeline, Database, Google Calendar mock | Testcontainers, WireMock | Critical paths |
| API | REST endpoints, request/response contracts | Spring MockMvc, REST Assured | All endpoints |
| E2E | WhatsApp → Backend → Database → Response | Testcontainers, WhatsApp test account | Happy paths |

### Unit Test Examples

```java
@Test
void shouldTransitionFromWelcomeToScheduleOnAffirmativeResponse() {
    // Given
    Father father = createFather(WorkflowState.WELCOME);
    InboundMessageDto message = createMessage("yes, ready");
    when(stateLoader.loadState(father.getId())).thenReturn(createSystemState(father));
    
    // When
    OutboundMessageDto response = workflowEngine.processMessage(message);
    
    // Then
    assertThat(father.getCurrentWorkflowState()).isEqualTo(WorkflowState.SCHEDULE_QUALITY_TIME);
    verify(transitionLogRepository).save(any(WorkflowTransition.class));
}

@Test
void shouldTransitionFromWelcomeToScheduleOnHebrewAffirmativeResponse() {
    // Given
    Father father = createFather(WorkflowState.WELCOME);
    InboundMessageDto message = createMessage("כן, מוכן");
    when(stateLoader.loadState(father.getId())).thenReturn(createSystemState(father));
    
    // When
    OutboundMessageDto response = workflowEngine.processMessage(message);
    
    // Then
    assertThat(father.getCurrentWorkflowState()).isEqualTo(WorkflowState.SCHEDULE_QUALITY_TIME);
    verify(transitionLogRepository).save(any(WorkflowTransition.class));
}

@Test
void shouldUsePatternMatchingNotAI() {
    // Given
    InboundMessageDto message = createMessage("2");
    Father father = createFather(WorkflowState.SCHEDULE_QUALITY_TIME);
    
    // When
    PatternResult result = patternMatcher.match(message.getContent(), 
        StatePatterns.SCHEDULE_PATTERNS);
    
    // Then
    assertThat(result.getPatternName()).isEqualTo("SLOT_NUMBER");
    assertThat(result.getCapturedGroups().get(0)).isEqualTo("2");
    verifyNoInteractions(intelligenceLayer); // AI not called for pattern matching
}

@Test
void shouldFallbackWhenAITimesOut() {
    // Given
    MessageContext context = createMessageContext(MessageType.SCHEDULE_SLOTS);
    when(intelligenceLayer.generateCoachingResponse(any()))
        .thenAnswer(invocation -> {
            Thread.sleep(6000); // Exceed 5s timeout
            return "AI response";
        });
    
    // When
    String message = messageGenerator.generateWithFallback(
        MessageType.SCHEDULE_SLOTS, context, 5000);
    
    // Then
    assertThat(message).isEqualTo(fallbackMessages.get(MessageType.SCHEDULE_SLOTS));
}
```

### Integration Test Examples

```java
@SpringBootTest
@Testcontainers
class WorkflowEngineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private WorkflowEngine workflowEngine;
    
    @MockBean
    private GoogleCalendarService calendarService;

    @Test
    void shouldCompleteFullSchedulingFlow() {
        // Given
        Father father = createAndSaveFather(WorkflowState.SCHEDULE_QUALITY_TIME);
        when(calendarService.getAvailableSlots(any())).thenReturn(createSlots());
        when(calendarService.createEvent(any())).thenReturn("calendar-event-id");
        
        // When - User selects slot 1
        OutboundMessageDto response1 = workflowEngine.processMessage(
            createMessage(father.getPhone(), "1"));
        
        // Then
        assertThat(father.getCurrentWorkflowState()).isEqualTo(WorkflowState.WAITING);
        assertThat(qualityTimeRepository.findByFatherId(father.getId())).hasSize(1);
        verify(calendarService).createEvent(any());
    }

    @Test
    void shouldHandleCalendarConflict() {
        // Given
        Father father = createAndSaveFather(WorkflowState.SCHEDULE_QUALITY_TIME);
        when(calendarService.getAvailableSlots(any()))
            .thenReturn(createSlots())
            .thenReturn(createDifferentSlots()); // Second call returns different slots
        when(calendarService.createEvent(any()))
            .thenThrow(new CalendarConflictException("Slot no longer available"));
        
        // When
        OutboundMessageDto response = workflowEngine.processMessage(
            createMessage(father.getPhone(), "1"));
        
        // Then
        assertThat(father.getCurrentWorkflowState()).isEqualTo(WorkflowState.SCHEDULE_QUALITY_TIME);
        assertThat(response.getContent()).contains("is not available");
    }
}
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Single Workflow State Invariant

*For any* father entity and *for any* sequence of workflow operations, the father SHALL have exactly one active workflow state at any point in time.

**Validates: Requirement 1.2**

### Property 2: State Transition Validity

*For any* current workflow state S1 and *for any* target state S2, a transition from S1 to S2 succeeds if and only if S2 is in the valid transitions set for S1, otherwise the transition is rejected and S1 remains unchanged.

**Validates: Requirements 1.3, 1.6**

### Property 3: State Transition Audit Logging

*For any* successful state transition from state S1 to state S2, exactly one workflow_state_transition_log entry SHALL be created with the correct from_state, to_state, and trigger_reason fields.

**Validates: Requirement 1.4**

### Property 4: Time Slot Non-Overlap

*For any* set of Google Calendar events and *for any* suggested Quality Time slot, the slot SHALL NOT overlap with any existing calendar event.

**Validates: Requirement 2.3**

### Property 5: Quality Time Data Completeness

*For any* created Quality Time record, all required fields (father_id, child_id, scheduled_start, scheduled_end, status) SHALL be non-null.

**Validates: Requirement 3.4**

### Property 6: Calendar Event Completeness

*For any* created Google Calendar event for Quality Time, the event SHALL contain: title with child name, duration >= 30 minutes, description, 1-hour and 15-minute reminders, and green color.

**Validates: Requirement 3.3**

### Property 7: WELCOME State Pattern Coverage

*For any* input message in WELCOME state, the message matches exactly one of: AFFIRMATIVE (transitions to SCHEDULE), MORE_INFO (explains and reprompts), or UNMATCHED (sends clarification with options).

**Validates: Requirements 4.2, 4.3**

### Property 8: WELCOME Exit Timestamp

*For any* transition from WELCOME state to any other state, the father's welcomed_at timestamp SHALL be set to a non-null value.

**Validates: Requirement 4.5**

### Property 9: Slot Selection Pattern Matching

*For any* valid slot selection input (numbers 1-9, time expressions, "skip", "other") in SCHEDULE_QUALITY_TIME state, the pattern matcher SHALL identify the correct action without invoking AI.

**Validates: Requirement 5.2**

### Property 10: Scheduling Conversation Length Limit

*For any* scheduling conversation in SCHEDULE_QUALITY_TIME state, the conversation SHALL complete or receive a summary message by the 5th message exchange.

**Validates: Requirement 5.6**

### Property 11: Timezone Consistency

*For any* time slot suggestion presented to a father, the time SHALL be formatted in the father's configured timezone.

**Validates: Requirement 5.7**

### Property 12: Morning Reminder Idempotency

*For any* scheduled Quality Time event and *for any* number of scheduler executions on the same day, exactly one morning reminder SHALL be sent.

**Validates: Requirements 6.2, 6.3**

### Property 13: Completion Updates Dashboard

*For any* Quality Time completion (father responds affirmatively), the Quality Time status SHALL be COMPLETED, the streak counter SHALL be incremented by 1, and belt progression SHALL be recalculated.

**Validates: Requirement 7.2**

### Property 14: Non-Completion Preserves Streak

*For any* Quality Time non-completion (father responds negatively), the Quality Time status SHALL be MISSED, and the streak counter SHALL remain unchanged.

**Validates: Requirement 7.3**

### Property 15: Follow-Up Conversation Length Limit

*For any* follow-up conversation in QUALITY_TIME_FOLLOW_UP state, the conversation SHALL complete by the 3rd message exchange.

**Validates: Requirement 7.5**

### Property 16: Belt Calculation Correctness

*For any* Quality Time completion count N, the belt level SHALL be: WHITE (0-2), YELLOW (3-9), ORANGE (10-24), GREEN (25-49), BLUE (50-99), BROWN (100-199), BLACK (200+).

**Validates: Requirement 8.5**

### Property 17: Activity Ideas Entry Condition

*For any* transition to ACTIVITY_IDEAS state, the triggering message SHALL contain one of the activity request keywords:
- English: ideas, activity, suggestions, what can I do, help me plan
- Hebrew: רעיונות, פעילות, הצעות, מה אפשר לעשות, עזור לי לתכנן

**Validates: Requirement 9.1**

### Property 18: Activity Ideas Format

*For any* activity ideas response, it SHALL contain exactly 3 ideas, each with title, description, duration, and indoor/outdoor indicator.

**Validates: Requirement 9.3**

### Property 19: Activity Ideas State Restoration

*For any* exit from ACTIVITY_IDEAS state, the workflow SHALL return to the previous_workflow_state stored before entering ACTIVITY_IDEAS.

**Validates: Requirement 9.6**

### Property 20: Message Generator Fallback

*For any* message generation request, if AI fails or exceeds 5 seconds timeout, the fallback template for that message type SHALL be used.

**Validates: Requirements 10.4, 10.6**

### Property 21: Unmatched Message Handling

*For any* inbound message that does not match any expected pattern for the current state, a clarification message SHALL be sent containing explicit options for valid responses.

**Validates: Requirement 11.4**

### Property 22: Scheduler Job Idempotency

*For any* scheduler job (morning reminder, follow-up transition, stale detection) and *for any* number of executions, the job SHALL not produce duplicate messages, duplicate transitions, or duplicate state updates.

**Validates: Requirement 12.2**


## Configuration

### Application Properties

```yaml
# application.yml additions for deterministic workflow engine

workflow:
  engine:
    enabled: true
    # Maximum message exchanges per state before forcing completion
    max-exchanges:
      schedule-quality-time: 5
      quality-time-follow-up: 3
      activity-ideas: 10
    # Default Quality Time duration in minutes
    default-duration-minutes: 30
    # Days ahead to fetch calendar slots
    calendar-lookahead-days: 7
    # Minimum slot duration to consider available
    min-slot-duration-minutes: 30

message-generator:
  # Timeout for AI message generation before using fallback
  timeout-ms: 5000
  # Enable/disable AI (uses fallback only when disabled)
  ai-enabled: true
  # Default language for messages (falls back to this if father's preference is not set)
  default-language: en

scheduler:
  # Morning reminder job runs at 7:50 AM UTC
  morning-reminder-cron: "0 50 7 * * *"
  # Follow-up transition job interval
  follow-up-interval-ms: 900000  # 15 minutes
  # Stale state detection job interval  
  stale-detection-interval-ms: 3600000  # 1 hour
  # Stale threshold in hours
  stale-threshold-hours: 24
  # Batch size for processing fathers
  batch-size: 100

quality-time:
  # Reminder times (hours before event)
  reminder-hours-before:
    - 1
    - 0.25  # 15 minutes
  # Calendar event color (Google Calendar colorId)
  calendar-color-id: "10"  # Green

google-calendar:
  # Cache duration for calendar data
  cache-duration-minutes: 5
  # Retry configuration
  retry:
    max-attempts: 2
    backoff-ms: 1000

belt-progression:
  thresholds:
    WHITE: 0
    YELLOW: 3
    ORANGE: 10
    GREEN: 25
    BLUE: 50
    BROWN: 100
    BLACK: 200
```

### Feature Flags

```yaml
feature-flags:
  # Master switch for new workflow engine (for rollback capability)
  deterministic-workflow-engine: true
  # Enable AI message generation (false = fallback only)
  ai-message-generation: true
  # Enable morning reminders
  morning-reminders: true
  # Enable WebSocket for real-time dashboard updates
  websocket-dashboard: false
```


## Requirements Traceability Matrix

| Requirement | Design Section | Correctness Properties | Test Strategy |
|-------------|----------------|------------------------|---------------|
| 1.1 Six workflow states | WorkflowState Enum | - | Unit test |
| 1.2 Single active state | WorkflowEngine | Property 1 | Property test |
| 1.3 State transitions | State Machine Definition | Property 2 | Property test |
| 1.4 Transition logging | WorkflowTransitionLogRepository | Property 3 | Property test |
| 1.5 Stateless persistence | WorkflowEngine | - | Integration test |
| 1.6 Invalid transition handling | StateHandler | Property 2 | Property test |
| 2.1 Read before write | SystemStateLoader | - | Unit test |
| 2.2 No redundant questions | SystemState | - | Example tests |
| 2.3 Calendar slot calculation | SystemStateLoader.loadAvailableSlots | Property 4 | Property test |
| 2.4 Request-scoped caching | SystemStateCache | - | Integration test |
| 2.5 Calendar connection prompt | ScheduleStateHandler | - | Unit test |
| 2.6 Conflict detection | QualityTimeService | - | Unit test |
| 3.1 Calendar prerequisite | ScheduleStateHandler | - | Unit test |
| 3.2 Calendar reading | GoogleCalendarService | - | Integration test |
| 3.3 Calendar event creation | QualityTimeService | Property 6 | Property test |
| 3.4 Quality Time persistence | QualityTime entity | Property 5 | Property test |
| 3.5 Time-based transition | WorkflowScheduler | - | Integration test |
| 3.6 Calendar error handling | QualityTimeService | - | Unit test |
| 3.7 Calendar sync | QualityTimeService | - | Integration test |
| 4.1 Welcome message | WelcomeStateHandler | - | Unit test |
| 4.2 Welcome patterns | StatePatterns.WELCOME_PATTERNS | Property 7 | Property test |
| 4.3 Welcome clarification | WelcomeStateHandler.handleUnmatched | Property 7 | Property test |
| 4.4 No AI in welcome | WelcomeStateHandler | - | Unit test |
| 4.5 Welcome exit timestamp | WelcomeStateHandler | Property 8 | Property test |
| 5.1 Schedule entry behavior | ScheduleStateHandler | - | Unit test |
| 5.2 Slot selection patterns | StatePatterns.SCHEDULE_PATTERNS | Property 9 | Property test |
| 5.3 Slot selection flow | ScheduleStateHandler | - | Unit test |
| 5.4 More slots handling | ScheduleStateHandler | - | Unit test |
| 5.5 Skip handling | ScheduleStateHandler | - | Unit test |
| 5.6 Exchange limit | ScheduleStateHandler | Property 10 | Property test |
| 5.7 Timezone formatting | MessageGenerator | Property 11 | Property test |
| 6.1 Waiting passivity | WaitingStateHandler | - | Unit test |
| 6.2 Morning reminder | WorkflowScheduler.morningReminderJob | Property 12 | Property test |
| 6.3 Reminder idempotency | WorkflowScheduler | Property 12 | Property test |
| 6.4 Schedule inquiry | WaitingStateHandler | - | Unit test |
| 6.5 Reschedule handling | WaitingStateHandler | - | Unit test |
| 6.6 Time-based follow-up | WorkflowScheduler.followUpTransitionJob | - | Integration test |
| 7.1 Follow-up question | FollowUpStateHandler | - | Unit test |
| 7.2 Completion handling | FollowUpStateHandler | Property 13 | Property test |
| 7.3 Non-completion handling | FollowUpStateHandler | Property 14 | Property test |
| 7.4 Detail extraction | PatternMatcher | - | Example tests |
| 7.5 Follow-up exchange limit | FollowUpStateHandler | Property 15 | Property test |
| 7.6 Follow-up timeout | WorkflowScheduler.staleStateDetectionJob | - | Integration test |
| 8.1 Dashboard as frontend | REST API Design | - | - |
| 8.2 Dashboard API structure | WorkspaceSummary DTO | - | API test |
| 8.3 WhatsApp dashboard | WaitingStateHandler | - | Unit test |
| 8.4 Real-time computation | Dashboard API | - | Integration test |
| 8.5 Belt thresholds | Belt Enum | Property 16 | Property test |
| 8.6 Frontend celebration | Frontend Design | - | Frontend test |
| 9.1 Activity ideas entry | StatePatterns | Property 17 | Property test |
| 9.2 Context loading | ActivityIdeasStateHandler | - | Unit test |
| 9.3 Ideas format | MessageGenerator | Property 18 | Property test |
| 9.4 Ideas interaction | ActivityIdeasStateHandler | - | Example tests |
| 9.5 AI isolation | ActivityIdeasStateHandler | - | Unit test |
| 9.6 State restoration | ActivityIdeasStateHandler | Property 19 | Property test |
| 10.1 AI text only | MessageGenerator interface | - | Unit test |
| 10.2 Structured context | MessageContext | - | Unit test |
| 10.3 String return | MessageGenerator interface | - | Unit test |
| 10.4 Fallback on failure | MessageGeneratorImpl | Property 20 | Property test |
| 10.5 IntelligenceLayer usage | MessageGeneratorImpl | - | - |
| 10.6 5-second timeout | MessageGeneratorImpl | Property 20 | Property test |
| 11.1 Message pipeline | WorkflowEngine.processMessage | - | Integration test |
| 11.2 30-second response | WorkflowEngine | - | Performance test |
| 11.3 State patterns | StatePatterns | - | Unit test |
| 11.4 Unmatched handling | StateHandler.handleUnmatched | Property 21 | Property test |
| 11.5 Quick reply buttons | WhatsAppService | - | - |
| 12.1 Scheduler jobs | WorkflowScheduler | - | Integration test |
| 12.2 Job idempotency | WorkflowScheduler | Property 22 | Property test |
| 12.3 Spring @Scheduled | WorkflowScheduler | - | - |
| 12.4 Follow-up job | WorkflowScheduler.followUpTransitionJob | - | Integration test |
| 12.5 Stale detection job | WorkflowScheduler.staleStateDetectionJob | - | Integration test |
| 12.6 Batch processing | WorkflowScheduler | - | - |
| 13.1-13.6 Frontend workspace | Frontend Design | - | Frontend tests |
| 14.1-14.6 API simplification | REST API Design | - | API tests |
| 15.1-15.6 Data migration | Data Migration | - | Migration tests |
| 16.1-16.6 Observability | Error Handling, Scheduler | - | Integration tests |
