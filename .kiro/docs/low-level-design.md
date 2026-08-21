# Dad Coach - Low-Level Design Document

## 1. System Overview

Dad Coach is an AI-powered parenting coaching platform that helps fathers build stronger relationships with their children through WhatsApp-based coaching conversations, scheduled Quality Time activities, and gamified progression (belt system).

### 1.1 Architecture Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DAD COACH SYSTEM                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────────┐ │
│  │   Web Frontend  │    │  WhatsApp Bot   │    │   Scheduled Jobs        │ │
│  │   (Next.js)     │    │  (Webhook)      │    │   (Spring Scheduler)    │ │
│  └────────┬────────┘    └────────┬────────┘    └───────────┬─────────────┘ │
│           │                      │                         │               │
│           │     REST API         │     Webhook             │  Internal     │
│           ▼                      ▼                         ▼               │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                     SPRING BOOT BACKEND                               │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │ │
│  │  │ Onboarding  │  │  Workflow   │  │Intelligence │  │  Quality    │  │ │
│  │  │ Module      │  │  Engine     │  │  Layer (AI) │  │  Time       │  │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │ │
│  │  │  Weekly     │  │  Channel    │  │  Workspace  │  │  Mission    │  │ │
│  │  │  Goals      │  │  Adapter    │  │  API        │  │  Service    │  │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│                                    ▼                                        │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                          POSTGRESQL 17                                │ │
│  │        30 Tables: father, child, goal, mission, quality_time, etc.   │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│  ┌──────────────────┐  ┌──────────────────┐                                │
│  │  Google Calendar │  │  WhatsApp Cloud  │                                │
│  │  API             │  │  API             │                                │
│  └──────────────────┘  └──────────────────┘                                │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 1.2 Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| Frontend | Next.js 14 (App Router) | Web dashboard, onboarding wizard |
| Backend | Spring Boot 3.4 / Java 21 | REST API, business logic, workflows |
| Database | PostgreSQL 17 | Persistence, Flyway migrations |
| External APIs | WhatsApp Cloud API, Google Calendar | Communication, scheduling |
| AI | Multi-provider (OpenAI/Anthropic) | Coaching response generation |

---

## 2. Domain Model

### 2.1 Entity Relationship Diagram

```
┌─────────────────┐       ┌─────────────────┐
│     Father      │       │      Child      │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │──1:N──│ id (PK)         │
│ phone           │       │ father_id (FK)  │
│ display_name    │       │ name            │
│ status          │       │ birth_date      │
│ current_belt    │       │ gender          │
│ coaching_phase  │       │ interests[]     │
│ workflow_state  │       │ challenges[]    │
│ streak_weeks    │       └─────────────────┘
│ google_tokens   │              │
└────────┬────────┘              │
         │                       │
         │1:N                    │1:N
         ▼                       ▼
┌─────────────────┐       ┌─────────────────┐
│  Conversation   │       │  Quality Time   │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │       │ id (UUID, PK)   │
│ father_id (FK)  │       │ father_id (FK)  │
│ type            │       │ child_id (FK)   │
│ status          │       │ scheduled_start │
│ objective       │       │ scheduled_end   │
│ message_count   │       │ status          │
│ expires_at      │       │ calendar_id     │
└─────────────────┘       └─────────────────┘
         │
         │1:N
         ▼
┌─────────────────┐       ┌─────────────────┐
│     Memory      │       │   Weekly Goal   │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │       │ id (PK)         │
│ father_id (FK)  │       │ father_id (FK)  │
│ child_id (FK)   │       │ week_start_date │
│ category        │       │ target_hours    │
│ content         │       │ actual_minutes  │
│ importance      │       │ status          │
└─────────────────┘       │ starting_belt   │
                          │ ending_belt     │
┌─────────────────┐       └─────────────────┘
│      Goal       │
├─────────────────┤       ┌─────────────────┐
│ id (PK)         │       │    Mission      │
│ father_id (FK)  │──1:N──│                 │
│ title           │       │ id (PK)         │
│ category        │       │ father_id (FK)  │
│ progress_%      │       │ child_id (FK)   │
│ status          │       │ goal_id (FK)    │
└─────────────────┘       │ status          │
                          └─────────────────┘
```


### 2.2 Core Entities

#### Father
The central entity representing a parent using the system.

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGSERIAL | Primary key |
| `phone` | VARCHAR(32) | Unique WhatsApp phone number |
| `display_name` | VARCHAR(120) | User's display name |
| `status` | ENUM | NOT_STARTED, ONBOARDING, ACTIVE, PAUSED, CHURNED |
| `current_belt` | ENUM | WHITE, YELLOW, ORANGE, GREEN, BLUE, BROWN, BLACK |
| `current_workflow_state` | ENUM | WELCOME, SCHEDULE_QUALITY_TIME, WAITING, etc. |
| `current_streak_weeks` | INTEGER | Consecutive weeks meeting weekly goal |
| `google_calendar_enabled` | BOOLEAN | Whether calendar integration is active |

#### Child
Children associated with a father.

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGSERIAL | Primary key |
| `father_id` | BIGINT | FK to father |
| `name` | VARCHAR(120) | Child's name |
| `birth_date` | DATE | For age calculation and developmental bracket |
| `interests` | TEXT[] | Array of interests (LEGO, Sports, etc.) |
| `challenges` | TEXT[] | Parenting challenges with this child |

#### Quality Time
Scheduled parent-child activity sessions.

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `father_id` | BIGINT | FK to father |
| `child_id` | BIGINT | FK to child |
| `scheduled_start` | TIMESTAMP | When the session starts |
| `scheduled_end` | TIMESTAMP | When the session ends |
| `status` | ENUM | SCHEDULED, COMPLETED, MISSED, CANCELLED |
| `google_calendar_event_id` | VARCHAR | Linked calendar event |


---

## 3. User Flows

### 3.1 Onboarding Flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                           ONBOARDING FLOW                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. INVITATION VALIDATION                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  User clicks invite link → /join/{token}                            │  │
│  │  Frontend: GET /api/v1/invitations/{token}/validate                 │  │
│  │  Backend: InvitationService.validate()                              │  │
│  │    - Check token exists, not expired, not revoked, uses remaining   │  │
│  │    - Log audit entry                                                │  │
│  │  Response: {valid: true, invitation_id, inviter_display_name}       │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                       │
│                                    ▼                                       │
│  2. SESSION CREATION                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  Frontend: POST /api/v1/onboarding/sessions                         │  │
│  │    Body: {invitation_token: "abc123"}                               │  │
│  │  Backend: OnboardingSessionService.create()                         │  │
│  │    - Create onboarding_sessions record                              │  │
│  │    - Generate CSRF token                                            │  │
│  │    - Set session cookie                                             │  │
│  │  Response: {session_id, current_step: "WELCOME", csrf_token}        │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                       │
│                                    ▼                                       │
│  3. WIZARD STEPS (7 steps)                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  For each step: PUT /api/v1/onboarding/sessions/{id}/steps/{step}   │  │
│  │                                                                     │  │
│  │  LANGUAGE   → {language: "he"}                                      │  │
│  │  PROFILE    → {display_name, phone_number, timezone}                │  │
│  │  CHILDREN   → [{name, birth_date, gender, interests}]               │  │
│  │  GOALS      → {selected_goals: ["QUALITY_TIME", "COMMUNICATION"]}   │  │
│  │  PREFERENCES→ {coaching_style, notification_frequency}              │  │
│  │  CALENDAR   → {skip: true} or Google OAuth flow                     │  │
│  │  REVIEW     → {} (confirmation)                                     │  │
│  │                                                                     │  │
│  │  Each step: Validate CSRF, session, invitation → Update wizard_data │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                       │
│                                    ▼                                       │
│  4. PROVISIONING                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  Frontend: POST /api/v1/onboarding/sessions/{id}/complete           │  │
│  │  Backend: ProvisioningService.provision()                           │  │
│  │    @Transactional - ALL OR NOTHING:                                 │  │
│  │    1. Create Father entity (status=ONBOARDING)                      │  │
│  │    2. Create Child entities                                         │  │
│  │    3. Create Goal entities                                          │  │
│  │    4. Create CommunicationEndpoint (WhatsApp)                       │  │
│  │    5. Create AI Profile                                             │  │
│  │    6. Create ActivationRecord (status=PENDING)                      │  │
│  │    7. Generate WhatsApp deep link                                   │  │
│  │  Response: {father_id, deep_link, activation_status: "PENDING"}     │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                       │
│                                    ▼                                       │
│  5. ACTIVATION (WhatsApp)                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  Frontend: Display deep link, poll activation status                │  │
│  │            GET /api/v1/onboarding/sessions/{id}/activation-status   │  │
│  │                                                                     │  │
│  │  User: Clicks WhatsApp deep link → Opens WhatsApp → Sends message   │  │
│  │                                                                     │  │
│  │  Backend Webhook: WhatsAppWebhookController receives message        │  │
│  │    → ActivationListener.interceptByPhoneIfOnboarding()              │  │
│  │    → If father.status == ONBOARDING:                                │  │
│  │        1. Transition father to ACTIVE                               │  │
│  │        2. Update activation_record status to CONVERSATION_STARTED   │  │
│  │        3. Set father.activationDate                                 │  │
│  │        4. Send welcome message via WhatsApp                         │  │
│  │        5. Transition to WELCOME workflow state                      │  │
│  │                                                                     │  │
│  │  Frontend: Polling detects status change → Redirect to dashboard    │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```


### 3.2 WhatsApp Messaging Flow (Deterministic Workflow Engine)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      WHATSAPP MESSAGE PROCESSING                            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. WEBHOOK RECEPTION                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  WhatsApp Cloud API → POST /webhook/whatsapp                        │  │
│  │                                                                     │  │
│  │  WhatsAppWebhookController:                                         │  │
│  │    1. Verify X-Hub-Signature-256 header                             │  │
│  │    2. Parse WhatsAppWebhookPayload                                  │  │
│  │    3. Get ChannelAdapter via ChannelRouter                          │  │
│  │    4. Normalize to InboundMessageDto                                │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                       │
│                                    ▼                                       │
│  2. ACTIVATION CHECK                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  ActivationListener.interceptByPhoneIfOnboarding(phone, message)    │  │
│  │    - If father.status == ONBOARDING → Handle activation flow        │  │
│  │    - Return true (intercepted) or false (continue to workflow)      │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │ (not intercepted)                     │
│                                    ▼                                       │
│  3. WORKFLOW ENGINE PROCESSING                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  WorkflowEngine.processMessage(InboundMessageDto):                  │  │
│  │                                                                     │  │
│  │    Step 1: Load SystemState (Read Before Write principle)           │  │
│  │      - Father entity with current workflow state                    │  │
│  │      - Children list                                                │  │
│  │      - Active Quality Time schedule                                 │  │
│  │      - Google Calendar availability (if enabled)                    │  │
│  │      - Weekly goal progress                                         │  │
│  │                                                                     │  │
│  │    Step 2: Determine current WorkflowState                          │  │
│  │      - WELCOME, SCHEDULE_QUALITY_TIME, WAITING,                     │  │
│  │        QUALITY_TIME_FOLLOW_UP, ACTIVITY_IDEAS,                      │  │
│  │        WEEKLY_SUMMARY, SET_WEEKLY_GOAL, etc.                        │  │
│  │                                                                     │  │
│  │    Step 3: Pattern matching for current state                       │  │
│  │      - Each state has expected message patterns                     │  │
│  │      - "כן" (yes), "לא" (no), time slots, etc.                      │  │
│  │                                                                     │  │
│  │    Step 4: Execute business logic                                   │  │
│  │      - Update database (QualityTime, WeeklyGoal, etc.)              │  │
│  │      - Create Google Calendar events                                │  │
│  │      - Calculate belt progression                                   │  │
│  │                                                                     │  │
│  │    Step 5: Generate response via MessageGenerator                   │  │
│  │      - AI generates personalized Hebrew text                        │  │
│  │      - Fallback templates if AI unavailable                         │  │
│  │                                                                     │  │
│  │    Step 6: Transition to next state                                 │  │
│  │      - Update father.current_workflow_state                         │  │
│  │      - Log transition in workflow_state_transition_log              │  │
│  │                                                                     │  │
│  │  Return: OutboundMessageDto                                         │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                       │
│                                    ▼                                       │
│  4. MESSAGE DELIVERY                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  ChannelAdapter.sendMessage(OutboundMessageDto, recipientPhone)     │  │
│  │    → WhatsAppAdapter.sendMessage()                                  │  │
│  │    → WhatsAppApiClient.sendTextMessage()                            │  │
│  │    → POST https://graph.facebook.com/v25.0/{phone_id}/messages      │  │
│  │    → Log DeliveryRecord                                             │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```


### 3.3 Workflow State Machine

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         WORKFLOW STATE MACHINE                              │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│    ┌──────────────┐                                                        │
│    │   WELCOME    │ ← Father activation complete                           │
│    └──────┬───────┘                                                        │
│           │ Auto-transition                                                │
│           ▼                                                                │
│    ┌──────────────────────┐                                                │
│    │ SCHEDULE_QUALITY_TIME│ ← Present available slots                      │
│    └──────┬───────────────┘                                                │
│           │ User selects slot                                              │
│           ▼                                                                │
│    ┌──────────────┐                                                        │
│    │   WAITING    │ ← Quality Time scheduled, waiting for it               │
│    └──────┬───────┘                                                        │
│           │ Scheduler triggers 1h before QT                                │
│           ▼                                                                │
│    ┌──────────────────────┐                                                │
│    │ QUALITY_TIME_REMINDER│ ← Send activity ideas                          │
│    └──────┬───────────────┘                                                │
│           │ After QT end time                                              │
│           ▼                                                                │
│    ┌────────────────────────┐                                              │
│    │ QUALITY_TIME_FOLLOW_UP │ ← Ask how it went                            │
│    └──────┬─────────────────┘                                              │
│           │ User reports completion/miss                                   │
│           ▼                                                                │
│    ┌──────────────────────┐                                                │
│    │   UPDATE_PROGRESS    │ ← Update WeeklyGoal, check belt                │
│    └──────┬───────────────┘                                                │
│           │                                                                │
│           ▼                                                                │
│    ┌──────────────────────┐                                                │
│    │ SCHEDULE_QUALITY_TIME│ ← Loop back (weekly cycle)                     │
│    └──────────────────────┘                                                │
│                                                                            │
│  WEEKLY BOUNDARY STATES (Sunday):                                          │
│    ┌──────────────────┐                                                    │
│    │  WEEKLY_SUMMARY  │ ← Show week stats, celebrate if goal met           │
│    └──────┬───────────┘                                                    │
│           ▼                                                                │
│    ┌──────────────────┐                                                    │
│    │  SET_WEEKLY_GOAL │ ← Set target hours for new week                    │
│    └──────────────────┘                                                    │
│                                                                            │
│  INACTIVITY HANDLING:                                                      │
│    If no response for X days → INACTIVITY_NUDGE state                      │
│    If continued inactivity → PAUSED status                                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```


### 3.4 Quality Time Scheduling Flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      QUALITY TIME SCHEDULING                                │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Option A: WhatsApp-based Scheduling                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  State: SCHEDULE_QUALITY_TIME                                       │  │
│  │                                                                     │  │
│  │  1. SystemStateLoader fetches Google Calendar free/busy             │  │
│  │  2. AvailableSlotCalculator generates 3-5 suggested slots           │  │
│  │  3. MessageGenerator formats as numbered list in Hebrew             │  │
│  │     "מתי תרצה להקדיש זמן איכות עם [child]?                          │  │
│  │      1. יום ראשון 17:00-18:00                                       │  │
│  │      2. יום שלישי 18:30-19:30                                       │  │
│  │      ..."                                                           │  │
│  │  4. User replies with number ("1" or "אפשרות 1")                    │  │
│  │  5. QualityTimeService.schedule():                                  │  │
│  │     - Insert quality_time record                                    │  │
│  │     - If Google connected: Create calendar event                    │  │
│  │     - Create scheduled reminder job                                 │  │
│  │  6. Transition to WAITING state                                     │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Option B: Web Dashboard Scheduling                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  Frontend: /dashboard → ScheduleQualityTimeCTA                      │  │
│  │                                                                     │  │
│  │  1. GET /api/v1/quality-time/available-slots?childId={id}           │  │
│  │     → QualityTimeController.getAvailableSlots()                     │  │
│  │     → Returns List<AvailableSlotDto>                                │  │
│  │                                                                     │  │
│  │  2. User selects slot in AvailableSlotPicker component              │  │
│  │                                                                     │  │
│  │  3. POST /api/v1/quality-time/schedule                              │  │
│  │     Body: {childId, startTime, endTime}                             │  │
│  │     → QualityTimeController.schedule()                              │  │
│  │     → Same QualityTimeService.schedule() as WhatsApp                │  │
│  │                                                                     │  │
│  │  4. WhatsApp confirmation sent to father                            │  │
│  │  5. Workflow state updated to WAITING                               │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```


### 3.5 Belt Progression System

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         BELT PROGRESSION                                    │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  BELT LEVELS (Martial Arts inspired):                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  WHITE  → YELLOW → ORANGE → GREEN → BLUE → BROWN → BLACK            │  │
│  │    │        │         │        │       │        │        │          │  │
│  │    0       4-5       6-7      8-9     10      11-12    13+ weeks    │  │
│  │            weeks    weeks    weeks   weeks    weeks    streak       │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  PROGRESSION LOGIC (WeeklyGoalService):                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  1. End of each week: calculateWeeklyGoalCompletion()               │  │
│  │     - Sum all completed QualityTime minutes                         │  │
│  │     - Compare against target_hours × 60                             │  │
│  │     - If actual >= target: ACHIEVED, else MISSED                    │  │
│  │                                                                     │  │
│  │  2. Update streak:                                                  │  │
│  │     - If ACHIEVED: streak_weeks++                                   │  │
│  │     - If MISSED: streak_weeks = 0 (reset)                           │  │
│  │                                                                     │  │
│  │  3. Calculate new belt:                                             │  │
│  │     Belt newBelt = Belt.fromStreak(streak_weeks);                   │  │
│  │     // Uses ordinal thresholds defined in Belt enum                 │  │
│  │                                                                     │  │
│  │  4. If belt changed:                                                │  │
│  │     - Update father.current_belt                                    │  │
│  │     - Send celebration message (WhatsApp + Web notification)        │  │
│  │     - Award achievement if applicable                               │  │
│  │     - Send belt image via WhatsApp (BeltImageConfig)                │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  WEEKLY GOAL RECORD:                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  weekly_goal table:                                                 │  │
│  │    - father_id                                                      │  │
│  │    - week_start_date (Sunday)                                       │  │
│  │    - target_hours (default: 2.0)                                    │  │
│  │    - actual_minutes (accumulated)                                   │  │
│  │    - status: PENDING, ACHIEVED, MISSED                              │  │
│  │    - starting_belt, ending_belt (for analytics)                     │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Backend Architecture

### 4.1 Package Structure

```
com.dadcoach/
├── DadCoachApplication.java           # Spring Boot entry point
│
├── domain/                             # Core domain entities (JPA)
│   ├── father/Father.java             # Central user entity
│   ├── child/Child.java               # Child entity
│   ├── conversation/Conversation.java # Coaching conversation record
│   ├── goal/Goal.java                 # Parenting goals
│   ├── mission/Mission.java           # Actionable tasks
│   └── memory/Memory.java             # AI memory entries
│
├── ai/                                 # Intelligence Layer
│   ├── IntelligenceLayer.java         # Main AI interface
│   ├── IntelligenceLayerImpl.java     # Implementation
│   ├── AiProvider.java                # Provider interface
│   ├── provider/                       # Multi-provider support
│   │   ├── OpenAiProvider.java
│   │   └── AnthropicProvider.java
│   ├── prompt/                         # Prompt management
│   │   └── PromptTemplate.java
│   ├── safety/                         # Content safety
│   │   └── SafetyFilter.java
│   └── telemetry/                      # AI metrics
│       └── AiTelemetryService.java
│
├── workflow/                           # Deterministic state machine
│   ├── WorkflowEngine.java            # Main interface
│   ├── WorkflowEngineImpl.java        # Implementation
│   ├── WorkflowState.java             # State enum
│   ├── WorkflowTrigger.java           # Trigger enum
│   ├── Belt.java                      # Belt progression enum
│   ├── state/                          # State handlers
│   │   ├── WelcomeStateHandler.java
│   │   ├── ScheduleQualityTimeHandler.java
│   │   └── ...                        # One handler per state
│   └── scheduler/                      # Scheduled transitions
│       └── WorkflowScheduler.java
│
├── channel/                            # Multi-channel communication
│   ├── ChannelAdapter.java            # Abstract adapter
│   ├── ChannelRouter.java             # Routes to correct adapter
│   └── delivery/                       # Delivery tracking
│       └── DeliveryService.java
│
├── whatsapp/                           # WhatsApp integration
│   ├── WhatsAppWebhookController.java # Webhook endpoint
│   ├── WhatsAppAdapter.java           # ChannelAdapter impl
│   ├── WhatsAppApiClient.java         # Graph API client
│   └── WhatsAppSignatureVerifier.java # Security
│
├── onboarding/                         # User onboarding
│   ├── OnboardingController.java      # REST endpoints
│   ├── InvitationController.java      # Invitation management
│   ├── wizard/                         # Wizard flow
│   │   └── WizardService.java
│   ├── session/                        # Session management
│   │   └── OnboardingSessionService.java
│   ├── provisioning/                   # Account creation
│   │   └── ProvisioningService.java
│   └── activation/                     # WhatsApp activation
│       └── ActivationListener.java
│
├── qualitytime/                        # Quality Time feature
│   ├── QualityTimeService.java        # Business logic
│   ├── QualityTime.java               # Entity
│   └── api/                            # REST endpoints
│       └── QualityTimeController.java
│
├── weeklygoal/                         # Weekly goals & streaks
│   ├── WeeklyGoalService.java         # Goal calculation
│   ├── WeeklyGoal.java                # Entity
│   └── BeltPromotionNotifier.java     # Celebration logic
│
├── workspace/                          # Web dashboard API
│   ├── growth/GrowthController.java   # Progress endpoints
│   ├── commitment/CommitmentService.java
│   └── magiclink/MagicLinkService.java # Auth tokens
│
├── calendar/                           # Google Calendar
│   ├── GoogleCalendarService.java     # Calendar operations
│   └── CalendarOAuthController.java   # OAuth flow
│
├── scheduling/                         # Background jobs
│   ├── SchedulingService.java         # Job scheduling
│   └── InactivityService.java         # Inactivity detection
│
├── api/                                # REST API infrastructure
│   ├── auth/                           # Authentication
│   ├── error/                          # Error handling
│   ├── ratelimit/                      # Rate limiting
│   └── pagination/                     # Pagination support
│
└── config/                             # Configuration
    ├── WhatsAppProperties.java        # WhatsApp config
    ├── AsyncConfig.java               # Async settings
    └── OpenApiConfig.java             # Swagger/OpenAPI
```


### 4.2 Key Services

#### WorkflowEngine
The heart of the coaching logic - a deterministic state machine.

```java
public interface WorkflowEngine {
    OutboundMessageDto processMessage(InboundMessageDto message);
    void processScheduledTrigger(Long fatherId, WorkflowTrigger trigger);
    WorkflowState getCurrentState(Long fatherId);
}
```

**Key responsibilities:**
- Load complete system state before processing
- Pattern match user input against expected responses
- Execute business logic (schedule QT, update goals, etc.)
- Generate personalized responses via AI
- Transition to next state

#### IntelligenceLayer
AI abstraction layer with multi-provider support and fallbacks.

```java
public interface IntelligenceLayer {
    String generateCoachingResponse(CoachingContext context);
    List<ActivityIdea> generateActivityIdeas(Child child, Goal goal);
    String generateWeeklySummary(WeeklyGoalResult result);
    Optional<Memory> extractMemory(String conversationText);
}
```

**Features:**
- Provider routing (OpenAI → Anthropic fallback)
- Rate limiting (AiRateLimiter)
- Retry logic with exponential backoff
- Token estimation for cost control
- Prompt templating with context injection

#### QualityTimeService
Manages scheduling and completion of parent-child activities.

```java
public interface QualityTimeService {
    QualityTime schedule(Father father, Child child, 
                         LocalDateTime start, LocalDateTime end);
    void complete(UUID qualityTimeId, CompletionReport report);
    void markMissed(UUID qualityTimeId);
    List<AvailableSlot> getAvailableSlots(Father father, LocalDate date);
}
```

**Integration points:**
- Google Calendar (create/update events)
- Weekly Goal Service (accumulate minutes)
- Workflow Engine (trigger state transitions)

---

## 5. Frontend Architecture

### 5.1 Next.js App Structure

```
app/
├── layout.tsx                          # Root layout with providers
├── page.tsx                            # Landing redirect
│
├── join/                               # Onboarding wizard
│   ├── [token]/                        # Dynamic route with invitation token
│   │   ├── layout.tsx                  # Onboarding layout + session guard
│   │   ├── page.tsx                    # Welcome screen
│   │   ├── language/page.tsx           # Step 1: Language selection
│   │   ├── profile/page.tsx            # Step 2: Profile info
│   │   ├── children/page.tsx           # Step 3: Add children
│   │   ├── goals/page.tsx              # Step 4: Select goals
│   │   ├── preferences/page.tsx        # Step 5: Coaching preferences
│   │   ├── calendar/page.tsx           # Step 6: Google Calendar connect
│   │   ├── review/page.tsx             # Step 7: Review & confirm
│   │   └── activate/page.tsx           # Step 8: WhatsApp activation
│
├── auth/                               # Authentication
│   └── magic/page.tsx                  # Magic link landing
│
├── (workspace)/                        # Authenticated area (grouped)
│   ├── layout.tsx                      # Workspace layout + auth guard
│   │
│   ├── dashboard/page.tsx              # Main dashboard
│   │   # Components: BeltSummaryCard, WeeklyGoalProgressCard,
│   │   #             NextQualityTimeCard, RecentActivityFeed
│   │
│   ├── growth/                         # Progress & achievements
│   │   ├── page.tsx                    # Belt progression view
│   │   ├── achievements/page.tsx       # Achievement gallery
│   │   └── streak/page.tsx             # Streak history
│   │
│   ├── family/                         # Family management
│   │   ├── page.tsx                    # Family overview
│   │   ├── children/[id]/page.tsx      # Child detail
│   │   └── goals/page.tsx              # Goals management
│   │
│   ├── coaching/                       # Coaching conversations
│   │   ├── page.tsx                    # Conversation list
│   │   ├── [conversationId]/page.tsx   # Conversation detail
│   │   └── log/page.tsx                # Activity log
│   │
│   ├── profile/                        # User profile
│   │   ├── page.tsx                    # Profile overview
│   │   ├── edit/page.tsx               # Edit profile
│   │   ├── preferences/page.tsx        # Edit preferences
│   │   └── account/page.tsx            # Account settings
│   │
│   └── notifications/page.tsx          # Notification center
│
src/
├── components/                         # Shared components
│   ├── onboarding/                     # Wizard components
│   ├── dashboard/                      # Dashboard cards
│   ├── workspace/                      # Workspace components
│   ├── celebrations/                   # Belt/achievement celebrations
│   ├── qualitytime/                    # QT scheduling UI
│   └── common/                         # Shared utilities
│
├── hooks/                              # Custom React hooks
│   ├── useProfile.ts                   # Profile data
│   ├── useBeltProgression.ts           # Belt status
│   ├── useWeeklyGoal.ts                # Weekly goal state
│   ├── useChildren.ts                  # Children list
│   ├── useScheduleQualityTime.ts       # QT scheduling mutation
│   └── useActivationPolling.ts         # Onboarding polling
│
├── services/                           # API services
│   ├── onboarding.ts                   # Onboarding API calls
│   ├── workspace.ts                    # Dashboard data
│   ├── qualityTime.ts                  # QT operations
│   ├── growth.ts                       # Progress/achievements
│   └── family.ts                       # Family management
│
├── types/                              # TypeScript types
│   ├── onboarding.ts                   # Onboarding types
│   ├── workspace.ts                    # Dashboard types
│   └── qualityTime.ts                  # QT types
│
├── lib/                                # Utilities
│   ├── api-client.ts                   # Fetch wrapper
│   └── query-client.ts                 # TanStack Query config
│
└── providers/                          # React context providers
    ├── QueryProvider.tsx               # TanStack Query
    └── LanguageProvider.tsx            # i18n (he/en)
```


### 5.2 Key Frontend Components

#### OnboardingProvider
Manages wizard state and step navigation.

```typescript
interface OnboardingContext {
  session: OnboardingSession | null;
  wizardData: WizardData;
  updateStep: (step: WizardStep, data: Partial<WizardData>) => Promise<void>;
  completeOnboarding: () => Promise<ActivationResult>;
  currentStep: WizardStep;
  canNavigateTo: (step: WizardStep) => boolean;
}
```

#### WorkspaceDashboard
Main dashboard with real-time data.

```typescript
// Key data fetched via useWorkspaceSummary hook:
interface WorkspaceSummary {
  profile: FatherProfile;
  children: Child[];
  currentBelt: Belt;
  weeklyGoal: WeeklyGoalProgress;
  upcomingQualityTime: QualityTime | null;
  recentActivities: Activity[];
  notifications: Notification[];
}
```

#### ScheduleQualityTime
Quality Time scheduling modal.

```typescript
// Uses useAvailableSlots + useScheduleQualityTime hooks
interface ScheduleQualityTimeProps {
  childId: string;
  onScheduled: (qualityTime: QualityTime) => void;
  onCancel: () => void;
}
```

---

## 6. API Contracts

### 6.1 Onboarding APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/invitations/{token}/validate` | Validate invitation token |
| POST | `/api/v1/onboarding/sessions` | Create onboarding session |
| GET | `/api/v1/onboarding/sessions/{id}` | Get session state |
| PUT | `/api/v1/onboarding/sessions/{id}/steps/{step}` | Update wizard step |
| POST | `/api/v1/onboarding/sessions/{id}/complete` | Complete onboarding |
| GET | `/api/v1/onboarding/sessions/{id}/activation-status` | Poll activation |

### 6.2 Workspace APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/workspace/summary` | Dashboard summary |
| GET | `/api/v1/workspace/profile` | User profile |
| PUT | `/api/v1/workspace/profile` | Update profile |
| GET | `/api/v1/workspace/children` | List children |
| POST | `/api/v1/workspace/children` | Add child |
| GET | `/api/v1/workspace/children/{id}` | Get child detail |
| PUT | `/api/v1/workspace/children/{id}` | Update child |

### 6.3 Quality Time APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/quality-time/available-slots` | Get available slots |
| POST | `/api/v1/quality-time/schedule` | Schedule QT session |
| GET | `/api/v1/quality-time/upcoming` | Get upcoming sessions |
| POST | `/api/v1/quality-time/{id}/complete` | Mark as completed |
| DELETE | `/api/v1/quality-time/{id}` | Cancel session |

### 6.4 Growth APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/growth/belt-progression` | Belt status & history |
| GET | `/api/v1/growth/weekly-goals` | Weekly goal history |
| GET | `/api/v1/growth/achievements` | Achievement list |
| GET | `/api/v1/growth/streak` | Current streak info |

### 6.5 Magic Link Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/magic-links` | Generate magic link |
| GET | `/api/v1/magic-links/{token}/validate` | Validate & exchange for session |


---

## 7. Database Schema

**Total: 30 application tables** (consolidated into single Flyway migration `V1__consolidated_schema.sql`)

> **Note**: As of August 2026, all migrations were consolidated into a single V1 file for clean deployments. The redundant `state_transition_log` table was removed (workflow transitions are logged in `workflow_state_transition_log`).

### 7.1 Table Overview

| Category | Tables |
|----------|--------|
| Core Domain | father, child, conversation, memory, goal, mission |
| Quality Time | quality_time, quality_time_commitment, weekly_goal |
| Onboarding | invitations, onboarding_sessions, activation_records |
| Family & Preferences | families, communication_preferences, language_preferences |
| AI & Profiles | ai_profiles, ai_telemetry |
| Communication | communication_endpoints, delivery_records, template_messages, media_assets, message_log |
| Workflow | message_templates, workflow_state_transition_log |
| Calendar | calendar_sync_log |
| Auth & Security | magic_link, invitation_audit_log, rate_limit_entries |
| Audit & Logging | api_audit_log, scheduler_job_log |

### 7.2 Core Domain Tables

```sql
-- Father - Central user entity (JPA: Father.java)
CREATE TABLE father (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    onboarding_state VARCHAR(30) DEFAULT 'NOT_STARTED',
    coaching_phase VARCHAR(20) DEFAULT 'FOUNDATION',
    coaching_style VARCHAR(20) DEFAULT 'BALANCED',
    preferred_coaching_time TIME DEFAULT '08:00:00',
    timezone VARCHAR(64) DEFAULT 'Asia/Jerusalem',
    locale VARCHAR(10) DEFAULT 'he',
    -- Google Calendar Integration
    google_calendar_enabled BOOLEAN DEFAULT FALSE,
    google_refresh_token VARCHAR(512),
    google_access_token VARCHAR(2048),
    google_token_expires_at TIMESTAMPTZ,
    google_calendar_id VARCHAR(255),
    -- Goals and Tracking
    weekly_goal_minutes INTEGER NOT NULL DEFAULT 30,
    current_streak_weeks INTEGER NOT NULL DEFAULT 0,
    total_quality_minutes INTEGER NOT NULL DEFAULT 0,
    -- Workflow State
    current_workflow_state VARCHAR(30) DEFAULT 'WELCOME',
    previous_workflow_state VARCHAR(30),
    workflow_state_entered_at TIMESTAMPTZ,
    -- Belt System
    current_belt VARCHAR(20) NOT NULL DEFAULT 'WHITE',
    quality_time_streak INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Child - Children associated with fathers (JPA: Child.java)
CREATE TABLE child (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(10),
    interests TEXT[],
    challenges TEXT[],
    relationship_quality INTEGER DEFAULT 3,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Conversation - Coaching conversation sessions (JPA: Conversation.java)
CREATE TABLE conversation (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL DEFAULT 'COACHING',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    objective TEXT,
    summary TEXT,
    message_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- Memory - AI memory entries for personalization (JPA: Memory.java)
CREATE TABLE memory (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES child(id),
    category VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    importance_score INTEGER NOT NULL DEFAULT 5,
    confidence_score NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    access_count INTEGER NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Goal - Parenting goals (JPA: Goal.java)
CREATE TABLE goal (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(30) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    progress_percentage INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- Mission - Actionable tasks (JPA: Mission.java)
CREATE TABLE mission (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES child(id),
    goal_id BIGINT REFERENCES goal(id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    difficulty INTEGER NOT NULL DEFAULT 1,
    estimated_minutes INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
```

### 7.3 Quality Time Tables

```sql
-- Quality Time - Scheduled sessions (JPA: QualityTime.java)
CREATE TABLE quality_time (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT NOT NULL REFERENCES child(id) ON DELETE CASCADE,
    google_calendar_event_id VARCHAR(255),
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    completion_notes TEXT,
    completed_at TIMESTAMPTZ,
    reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Quality Time Commitment (JPA: QualityTimeCommitment.java)
CREATE TABLE quality_time_commitment (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES child(id),
    scheduled_date DATE NOT NULL,
    scheduled_time TIME NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER,
    activity_type VARCHAR(50),
    activity_note VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Weekly Goal - Belt progression tracking (JPA: WeeklyGoal.java)
CREATE TABLE weekly_goal (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    week_start_date DATE NOT NULL,
    target_hours INTEGER NOT NULL CHECK (target_hours >= 1),
    actual_minutes INTEGER NOT NULL DEFAULT 0,
    scheduled_count INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    starting_belt VARCHAR(20) NOT NULL,
    ending_belt VARCHAR(20),
    belt_promoted BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_weekly_goal_father_week UNIQUE (father_id, week_start_date)
);
```

### 7.4 Onboarding Tables

```sql
-- Invitations (JPA: Invitation.java)
CREATE TABLE invitations (
    invitation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(32) NOT NULL UNIQUE,
    type VARCHAR(15) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    max_uses INTEGER NOT NULL,
    current_uses INTEGER NOT NULL DEFAULT 0,
    metadata JSONB
);

-- Onboarding Sessions (JPA: OnboardingSession.java)
CREATE TABLE onboarding_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id UUID NOT NULL,
    father_id UUID,
    current_step VARCHAR(20) NOT NULL,
    status VARCHAR(15) NOT NULL,
    wizard_data BYTEA,
    language VARCHAR(5),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500)
);

-- Activation Records (JPA: ActivationRecord.java)
CREATE TABLE activation_records (
    activation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    session_id UUID NOT NULL,
    status VARCHAR(25) NOT NULL,
    deep_link_generated_at TIMESTAMPTZ,
    message_received_at TIMESTAMPTZ,
    conversation_started_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(200)
);
```

### 7.5 Family & Preferences Tables

```sql
-- Families (JPA: Family.java)
CREATE TABLE families (
    family_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    family_name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Communication Preferences (JPA: CommunicationPreference.java)
CREATE TABLE communication_preferences (
    preference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    preferred_coaching_time TIME NOT NULL DEFAULT '08:00:00',
    notification_frequency VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    quiet_hours_start TIME NOT NULL DEFAULT '21:00:00',
    quiet_hours_end TIME NOT NULL DEFAULT '07:00:00',
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Language Preferences (JPA: LanguagePreference.java)
CREATE TABLE language_preferences (
    preference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    language_code VARCHAR(5) NOT NULL DEFAULT 'he',
    date_format VARCHAR(20) NOT NULL DEFAULT 'dd/MM/yyyy',
    time_format VARCHAR(20) NOT NULL DEFAULT 'HH:mm',
    text_direction VARCHAR(3) NOT NULL DEFAULT 'RTL',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 7.6 AI & Profile Tables

```sql
-- AI Profiles (JPA: AiProfile.java)
CREATE TABLE ai_profiles (
    profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    coaching_style VARCHAR(30) NOT NULL,
    language VARCHAR(5) NOT NULL,
    children_context TEXT,
    goals_context TEXT,
    personality_brief TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- AI Telemetry (JPA: AiTelemetryRecord.java)
CREATE TABLE ai_telemetry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    father_id UUID NOT NULL,
    conversation_id UUID,
    interaction_type VARCHAR(30) NOT NULL,
    model_provider VARCHAR(20) NOT NULL,
    model_name VARCHAR(50) NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    estimated_cost_usd REAL,
    total_latency_ms INTEGER NOT NULL,
    validation_passed BOOLEAN NOT NULL DEFAULT TRUE,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 7.7 Communication Tables

```sql
-- Communication Endpoints (JPA: CommunicationEndpoint.java)
CREATE TABLE communication_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    channel_identity VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    session_opens_at TIMESTAMPTZ,
    session_closes_at TIMESTAMPTZ,
    last_active_at TIMESTAMPTZ,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Delivery Records (JPA: DeliveryRecord.java)
CREATE TABLE delivery_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL,
    father_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    failure_reason VARCHAR(100),
    retry_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Template Messages (JPA: TemplateMessage.java)
CREATE TABLE template_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_name VARCHAR(100) NOT NULL UNIQUE,
    language VARCHAR(10) NOT NULL,
    category VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    max_variables INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Media Assets (JPA: MediaAsset.java)
CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL,
    message_id UUID NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content BYTEA NOT NULL,
    downloaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Message Log - Conversation history for AI (JPA: MessageLog.java)
CREATE TABLE message_log (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 7.8 Workflow & State Tables

```sql
-- Message Templates for workflow (JPA: MessageTemplate.java)
CREATE TABLE message_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type VARCHAR(50) NOT NULL UNIQUE,
    template_text TEXT NOT NULL,
    language VARCHAR(10) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Workflow State Transition Log (JPA: WorkflowTransition.java)
-- This is the single audit trail for all workflow state transitions
CREATE TABLE workflow_state_transition_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL,
    from_state VARCHAR(30) NOT NULL,
    to_state VARCHAR(30) NOT NULL,
    trigger_reason VARCHAR(50) NOT NULL,
    trigger_message_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 7.9 Calendar & Auth Tables

```sql
-- Calendar Sync Log (JPA: CalendarSyncLog.java)
CREATE TABLE calendar_sync_log (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    mission_id BIGINT,
    action VARCHAR(30) NOT NULL,
    calendar_event_id VARCHAR(255),
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT
);

-- Magic Link - Passwordless auth (JPA: MagicLink.java)
CREATE TABLE magic_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(32) NOT NULL UNIQUE,
    father_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    redirect_path VARCHAR(255),
    context VARCHAR(50)
);
```

### 7.10 Audit & Security Tables

```sql
-- Invitation Audit Log (JPA: InvitationAuditLog.java)
CREATE TABLE invitation_audit_log (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL,
    action VARCHAR(30) NOT NULL,
    result VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Rate Limit Entries (JPA: RateLimitEntry.java)
CREATE TABLE rate_limit_entries (
    entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_type VARCHAR(10) NOT NULL,
    key_value VARCHAR(255) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(key_type, key_value, window_start)
);

-- API Audit Log (JPA: ApiAuditEntry.java)
CREATE TABLE api_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id UUID NOT NULL,
    operation VARCHAR(50) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID,
    result VARCHAR(20) NOT NULL,
    error_code VARCHAR(50),
    changes JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Scheduler Job Log (JPA: SchedulerJobLog.java)
CREATE TABLE scheduler_job_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    records_processed INTEGER NOT NULL DEFAULT 0,
    errors_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 8. External Integrations

### 8.1 WhatsApp Cloud API

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      WHATSAPP CLOUD API INTEGRATION                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Configuration (WhatsAppProperties):                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  whatsapp.phone-number-id: "123456789"                              │  │
│  │  whatsapp.access-token: "${WHATSAPP_ACCESS_TOKEN}"                  │  │
│  │  whatsapp.verify-token: "${WHATSAPP_VERIFY_TOKEN}"                  │  │
│  │  whatsapp.webhook-secret: "${WHATSAPP_WEBHOOK_SECRET}"              │  │
│  │  whatsapp.api-version: "v25.0"                                      │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Webhook Verification (GET /webhook/whatsapp):                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  1. Check hub.mode == "subscribe"                                   │  │
│  │  2. Verify hub.verify_token matches configured token                │  │
│  │  3. Return hub.challenge as plain text                              │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Message Reception (POST /webhook/whatsapp):                               │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  1. Verify X-Hub-Signature-256 (HMAC-SHA256)                        │  │
│  │  2. Parse webhook payload                                           │  │
│  │  3. Extract message from entry[0].changes[0].value.messages[0]     │  │
│  │  4. Process via WorkflowEngine                                      │  │
│  │  5. Return 200 OK (acknowledge receipt)                             │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Sending Messages (WhatsAppApiClient):                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  POST https://graph.facebook.com/v25.0/{phone_id}/messages          │  │
│  │  Headers:                                                           │  │
│  │    Authorization: Bearer {access_token}                             │  │
│  │    Content-Type: application/json                                   │  │
│  │  Body:                                                              │  │
│  │    {                                                                │  │
│  │      "messaging_product": "whatsapp",                               │  │
│  │      "recipient_type": "individual",                                │  │
│  │      "to": "{recipient_phone}",                                     │  │
│  │      "type": "text",                                                │  │
│  │      "text": { "body": "{message}" }                                │  │
│  │    }                                                                │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```


### 8.2 Google Calendar API

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      GOOGLE CALENDAR INTEGRATION                            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  OAuth Flow:                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  1. User clicks "Connect Calendar" in onboarding/settings           │  │
│  │  2. Redirect to Google OAuth consent screen                         │  │
│  │     - Scopes: calendar.readonly, calendar.events                    │  │
│  │  3. User grants permission                                          │  │
│  │  4. Google redirects to /api/v1/calendar/oauth/callback             │  │
│  │  5. Exchange code for access_token + refresh_token                  │  │
│  │  6. Store tokens encrypted in father table                          │  │
│  │  7. Set father.google_calendar_enabled = true                       │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Available Slots Calculation:                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  GoogleCalendarService.getFreeBusySlots(father, dateRange):         │  │
│  │    1. Call Freebusy.query API                                       │  │
│  │    2. Get busy times from all calendars                             │  │
│  │    3. Calculate inverse (free times)                                │  │
│  │    4. Filter by preferred hours (evenings, weekends)                │  │
│  │    5. Return List<TimeSlot>                                         │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Event Creation:                                                           │  
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  GoogleCalendarService.createEvent(father, qualityTime):            │  │
│  │    Event:                                                           │  │
│  │      summary: "👨‍👧 זמן איכות עם {childName}"                        │  │
│  │      description: "Quality Time session via Dad Coach"              │  │
│  │      start: qualityTime.scheduledStart                              │  │
│  │      end: qualityTime.scheduledEnd                                  │  │
│  │      reminders: [30 minutes before]                                 │  │
│  │    Store event ID in quality_time.google_calendar_event_id          │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Token Refresh:                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  Before any API call:                                               │  │
│  │    1. Check if token expires within 5 minutes                       │  │
│  │    2. If yes, use refresh_token to get new access_token             │  │
│  │    3. Update stored tokens                                          │  │
│  │    4. Handle refresh failure (mark calendar disconnected)           │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```


### 8.3 AI Provider Integration

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         AI PROVIDER INTEGRATION                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Provider Hierarchy:                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  Primary: OpenAI (GPT-4o-mini)                                      │  │
│  │    - Fast, cost-effective                                           │  │
│  │    - Good Hebrew support                                            │  │
│  │                                                                     │  │
│  │  Fallback: Anthropic (Claude 3.5 Sonnet)                            │  │
│  │    - Used when OpenAI rate-limited or unavailable                   │  │
│  │    - Excellent for nuanced parenting advice                         │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Request Flow:                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  IntelligenceLayerImpl.generateResponse(context):                   │  │
│  │    1. Build prompt from template + context                          │  │
│  │    2. Estimate tokens (for cost tracking)                           │  │
│  │    3. Check rate limits                                             │  │
│  │    4. Try primary provider                                          │  │
│  │    5. On failure: Try fallback provider                             │  │
│  │    6. On all failures: Return template fallback                     │  │
│  │    7. Log telemetry (latency, tokens, provider, success)            │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Prompt Templates:                                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  coaching_response.txt:                                             │  │
│  │    - System prompt: Parenting coach persona                         │  │
│  │    - Context: Father profile, child info, current state             │  │
│  │    - Memories: Relevant AI memories for personalization             │  │
│  │    - Task: Generate appropriate Hebrew response                     │  │
│  │                                                                     │  │
│  │  activity_ideas.txt:                                                │  │
│  │    - Child interests and age                                        │  │
│  │    - Current goal focus                                             │  │
│  │    - Generate 3-5 activity suggestions                              │  │
│  │                                                                     │  │
│  │  weekly_summary.txt:                                                │  │
│  │    - Week statistics                                                │  │
│  │    - Generate encouraging summary                                   │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  Safety Layer:                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  SafetyFilter.validateInput(message):                               │  │
│  │    - Check for harmful content                                      │  │
│  │    - Detect crisis indicators                                       │  │
│  │    - Flag for human review if needed                                │  │
│  │                                                                     │  │
│  │  SafetyFilter.validateOutput(response):                             │  │
│  │    - Ensure parenting-appropriate content                           │  │
│  │    - Remove any harmful suggestions                                 │  │
│  │    - Verify response relevance                                      │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Scheduled Jobs

### 9.1 Job Types

| Job Type | Trigger | Description |
|----------|---------|-------------|
| `QUALITY_TIME_REMINDER` | 1 hour before QT | Send activity ideas via WhatsApp |
| `QUALITY_TIME_FOLLOW_UP` | 30 min after QT end | Ask how it went |
| `WEEKLY_SUMMARY` | Sunday 20:00 | Generate and send weekly summary |
| `WEEKLY_GOAL_CLOSE` | Saturday 23:59 | Close current week, calculate belt |
| `INACTIVITY_NUDGE` | After 3 days silence | Send gentle reminder |
| `INACTIVITY_PAUSE` | After 7 days silence | Mark father as PAUSED |

### 9.2 Scheduler Implementation

```java
@Component
public class WorkflowScheduler {
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void pollScheduledJobs() {
        List<ScheduledJob> dueJobs = scheduledJobRepository
            .findByScheduledForBeforeAndStatus(Instant.now(), PENDING);
        
        for (ScheduledJob job : dueJobs) {
            try {
                executeJob(job);
                job.setStatus(COMPLETED);
            } catch (Exception e) {
                job.setStatus(FAILED);
                job.setErrorMessage(e.getMessage());
            }
            scheduledJobRepository.save(job);
        }
    }
    
    private void executeJob(ScheduledJob job) {
        WorkflowTrigger trigger = WorkflowTrigger.valueOf(job.getJobType());
        workflowEngine.processScheduledTrigger(job.getFatherId(), trigger);
    }
}
```

---

## 10. Error Handling & Resilience

### 10.1 Error Categories

| Category | Handling Strategy |
|----------|-------------------|
| WhatsApp API errors | Retry with backoff, log for alerting |
| AI provider errors | Fallback to secondary provider, then template |
| Database errors | Transaction rollback, retry transient failures |
| Calendar API errors | Mark calendar disconnected, notify user |
| Validation errors | Return 400 with detailed error messages |

### 10.2 Circuit Breaker Pattern

```java
@CircuitBreaker(name = "whatsapp", fallbackMethod = "whatsappFallback")
@Retry(name = "whatsapp")
public void sendMessage(String phone, String message) {
    whatsAppApiClient.sendTextMessage(phone, message);
}

public void whatsappFallback(String phone, String message, Exception e) {
    // Queue for retry
    messageRetryQueue.add(new PendingMessage(phone, message));
    log.error("WhatsApp send failed, queued for retry: {}", e.getMessage());
}
```

---

## 11. Security Considerations

### 11.1 Authentication & Authorization

| Component | Mechanism |
|-----------|-----------|
| WhatsApp webhook | HMAC-SHA256 signature verification |
| Web dashboard | Magic link + session cookie |
| API requests | Session token in cookie or Authorization header |
| Onboarding | CSRF token + session validation |

### 11.2 Data Protection

- **Encryption at rest**: PostgreSQL with encrypted storage
- **Encryption in transit**: HTTPS only (enforced)
- **Token storage**: Google OAuth tokens encrypted before storage
- **PII handling**: Phone numbers, names - minimal logging
- **Session management**: Short-lived sessions, secure cookie flags

### 11.3 Rate Limiting

```java
@RateLimiter(name = "api", fallbackMethod = "rateLimitFallback")
public ResponseEntity<?> handleRequest() {
    // Process request
}

// Limits configured in application.yml:
// - API: 100 requests/minute per IP
// - WhatsApp sending: 80 messages/minute (Meta limit)
// - AI calls: 60 requests/minute per father
```

---

## 12. Deployment Architecture

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        MULTI-PLATFORM DEPLOYMENT                            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                     VERCEL (Frontend)                               │  │
│  │                                                                     │  │
│  │  ┌───────────────────────────────────────────────────────────────┐ │  │
│  │  │                    Next.js 14 App                              │ │  │
│  │  │  • Server-side rendering (SSR)                                 │ │  │
│  │  │  • API routes proxy to backend                                 │ │  │
│  │  │  • Edge functions for optimal performance                      │ │  │
│  │  │  • Automatic SSL/HTTPS                                         │ │  │
│  │  └───────────────────────────────────────────────────────────────┘ │  │
│  │                                                                     │  │
│  │            https://dad-coach-web.vercel.app                         │  │
│  └──────────────────────────────┬──────────────────────────────────────┘  │
│                                 │                                          │
│                                 │ API Proxy (rewrites)                     │
│                                 ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                     RENDER (Backend) + SUPABASE (DB)                │  │
│  │                                                                     │  │
│  │  ┌─────────────────┐          ┌─────────────────────────────────┐  │  │
│  │  │   Spring Boot   │          │         Supabase                │  │  │
│  │  │   (Java 21)     │ ◄──────► │         PostgreSQL 17           │  │  │
│  │  │                 │          │  30 Tables                      │  │  │
│  │  │   Port: 8080    │          │  Flyway Migrations              │  │  │
│  │  └────────┬────────┘          └─────────────────────────────────┘  │  │
│  │           │                                                         │  │
│  │           │  https://dad-coach.onrender.com                         │  │
│  └───────────┼─────────────────────────────────────────────────────────┘  │
│              │                                                             │
│              ▼                                                             │
│         INTERNET                                                           │
│              │                                                             │
│   ┌──────────┼──────────────────┬──────────────────┐                      │
│   │          │                  │                  │                      │
│   ▼          ▼                  ▼                  ▼                      │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐      ┌──────────┐                  │
│ │ WhatsApp │ │  Google  │ │ OpenAI/  │      │   Web    │                  │
│ │ Cloud API│ │ Calendar │ │ Anthropic│      │ Browsers │                  │
│ └──────────┘ └──────────┘ └──────────┘      └──────────┘                  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### 12.1 Platform Responsibilities

| Platform | Components | Responsibilities |
|----------|------------|------------------|
| **Vercel** | Next.js Frontend | SSR, static assets, API proxying, edge caching |
| **Render** | Spring Boot Backend | REST API, business logic, webhooks, scheduled jobs |
| **Supabase** | PostgreSQL 17 | Data persistence, Flyway migrations |

### 12.2 API Proxy Configuration

The frontend proxies API requests to the backend via Next.js rewrites:

```typescript
// next.config.ts
const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.BACKEND_URL}/api/:path*`,
      },
    ];
  },
};
```

### 12.3 Environment Variables

**Vercel (Frontend)**:
| Variable | Description |
|----------|-------------|
| `BACKEND_URL` | Render backend URL (https://dad-coach.onrender.com) |
| `NEXT_PUBLIC_APP_URL` | Public app URL |
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL |

**Render (Backend)**:
| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | Supabase PostgreSQL connection string |
| `WHATSAPP_ACCESS_TOKEN` | Meta Graph API token |
| `WHATSAPP_PHONE_NUMBER_ID` | WhatsApp business phone ID |
| `WHATSAPP_VERIFY_TOKEN` | Webhook verification token |
| `OPENAI_API_KEY` | OpenAI API key |
| `ANTHROPIC_API_KEY` | Anthropic API key |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `SESSION_SECRET` | Secret for session encryption |

---

## 13. Monitoring & Observability

### 13.1 Metrics (Micrometer)

- **Business metrics**: Active fathers, QT sessions/week, belt distributions
- **Technical metrics**: API latency, error rates, AI response times
- **Infrastructure metrics**: Memory, CPU, DB connections

### 13.2 Logging Strategy

```java
// Structured logging with MDC
MDC.put("fatherId", father.getId().toString());
MDC.put("workflowState", state.name());
log.info("Processing message", 
    kv("messageType", message.getType()),
    kv("action", "workflow_transition"));
```

### 13.3 Health Checks

```java
@Component
public class WhatsAppHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        if (whatsAppApiClient.isHealthy()) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "API unreachable").build();
    }
}
```

---

*Document Version: 1.0*
*Last Updated: August 2026*
