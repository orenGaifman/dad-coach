# Requirements Document

## Introduction

**Deterministic Workflow Engine — Orchestration Layer for Dad Coach**

This specification defines the core orchestration layer of the Dad Coach application — a **deterministic workflow engine** that coordinates the entire product experience across WhatsApp, Web Dashboard, and Calendar integrations. The workflow engine is the central nervous system that connects WEB-SPEC-007 (Onboarding), WEB-SPEC-008 (Father Workspace), and all backend services.

**Product Vision:**
Dad Coach helps fathers build a habit of spending quality time with their children by making scheduling effortless and progress visible. **Quality Time is the product; scheduling is the mechanism.**

**Problem Statement:**
The previous architecture was too AI-driven and open-ended:
- Too much conversation with unpredictable AI responses
- Too many decisions made by the AI at runtime
- Inconsistent user experience across sessions
- Complex conversation orchestration that is difficult to debug and maintain
- The system felt like "chatting with ChatGPT" rather than a guided product experience

**Target State:**
The product should feel like a **guided workflow**, not an open-ended chat:
- **Deterministic**: Backend owns all business logic; AI only writes natural language messages
- **Structured**: Clear states with defined transitions and no ambiguity
- **Predictable**: Same inputs produce same outputs regardless of AI behavior
- **Easy to maintain**: Simple, clear code paths with observable state
- **Under 10 seconds**: Every interaction should complete quickly

**Core Concepts:**
- **Mission**: An abstract container for different types of parenting activities. For MVP, every Mission is a Quality Time session. The architecture remains extensible for future mission types (reading together, outdoor activities, etc.).
- **Quality Time**: The core activity type — a scheduled event where the father spends dedicated time with their child. Quality Time is backed by Google Calendar.
- **Belt System**: The gamification layer (WHITE → YELLOW → ORANGE → GREEN → BLUE → BROWN → BLACK) that represents long-term consistency. Belt progression is sacred and must not be removed or redefined.

**Core Principle — Read Before Write:**
The system SHALL always synchronize with current system state before any action:
- Read Google Calendar before suggesting times
- Read current Quality Time schedule before making proposals
- Read conversation state before responding
- Read dashboard state before displaying
- Read children information before referencing
- Never ask for information that already exists
- Never suggest something already scheduled or completed

**Architecture Philosophy:**
```
WhatsApp Message Arrives / Web Request / Scheduler Trigger
    ↓
Workflow Engine (Central Orchestrator)
    ↓
Read Current State (Database + Google Calendar)
    ↓
Deterministic Business Logic (Pattern Matching + State Machine)
    ↓
Mission Service (Create/Complete Quality Time)
    ↓
Google Calendar Sync (External)
    ↓
Belt/Streak/Dashboard Update (Database)
    ↓
AI Message Generator (ONLY writes natural language — minimal usage)
    ↓
Response Delivery (WhatsApp / REST API / WebSocket)
```

The AI is NOT the orchestrator. The Workflow_Engine is the orchestrator. AI is a text-generation utility used sparingly where it adds real value.

**AI Usage Policy:**
AI should be minimized but not removed. Use AI only where it adds real value:
- ✅ Activity recommendations (when father explicitly asks)
- ✅ Short personalized encouragement (10% of messages)
- ✅ Quality Time summaries
- ✅ Reflection questions (post-completion)
- ❌ State transitions or decision-making
- ❌ Conversation orchestration
- ❌ Data extraction or interpretation

**Scope:**
This specification covers both backend (Spring Boot) and frontend (Next.js) changes required to implement the deterministic workflow paradigm. It serves as the orchestration layer connecting:
- **WEB-SPEC-007** (Onboarding & Activation): Workflow engine receives activated fathers from onboarding
- **WEB-SPEC-008** (Father Workspace): Workflow engine provides data for dashboard and handles web-initiated actions

**Supersedes:**
This specification represents a major simplification that supersedes the complex conversation orchestration. The existing ConversationOrchestrator will be replaced by the simpler WorkflowEngine.

## Glossary

- **Workflow_Engine**: The central deterministic orchestrator that owns all business logic and state transitions. Replaces the AI-driven ConversationOrchestrator. Serves as the orchestration layer connecting WEB-SPEC-007 (Onboarding) and WEB-SPEC-008 (Father Workspace).
- **Workflow_State**: One of the six defined states in the father's journey: WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP, DASHBOARD, ACTIVITY_IDEAS
- **Mission**: An abstract container for parenting activities that a father completes with their child. The Mission abstraction allows for future extensibility (reading together, outdoor activities, etc.). For MVP, every Mission is a Quality Time session.
- **Mission_Service**: The service layer that manages Mission lifecycle. In MVP, implemented as QualityTimeMissionService. Architecture supports future MissionService implementations for different mission types.
- **Quality_Time**: The MVP implementation of a Mission — a scheduled event on Google Calendar where the father spends dedicated time with their child. Quality Time is the core activity; scheduling is the mechanism.
- **State_Transition**: A deterministic move from one Workflow_State to another, triggered by explicit events (user action, timer, calendar event)
- **Message_Generator**: AI component that generates natural language messages in the father's preferred language (English or Hebrew). It receives context including language preference and produces localized text; it makes no decisions. Used sparingly per AI Usage Policy.
- **System_State**: The complete state of the system for a given father, including: workflow state, Google Calendar data, scheduled Quality Time events (missions), dashboard metrics, children information
- **Read_Before_Write**: Core principle where the system always reads current state from all sources before taking any action
- **Time_Slot**: An available period in the father's Google Calendar that can be suggested for Quality Time scheduling
- **Dashboard_Metrics**: The father's progress data: current belt, active mission, streak count, achievements, next milestone. Provided to WEB-SPEC-008 Father Workspace via API.
- **Belt_System**: **SACRED** — Gamification progression (White → Yellow → Orange → Green → Blue → Brown → Black) based on Quality Time completions. Progression: Weekly Goal → Completed Hours → XP → Belt. Do NOT remove or redefine.
- **Weekly_Goal**: The target hours of Quality Time the father aims to complete each week. Progress toward this goal drives Belt progression.

---

## Requirements

### Requirement 1: Workflow State Machine

**User Story:** As a father, I want a clear, predictable coaching experience that guides me step-by-step through Missions (Quality Time) with my children, so that I know exactly what to do and what to expect at each stage.

#### Acceptance Criteria

1. THE Workflow_Engine SHALL implement exactly six workflow states with the following definitions:
   - WELCOME: Initial state for new fathers (arriving from WEB-SPEC-007 Onboarding), explains Dad Coach and guides to schedule first Mission (Quality Time)
   - SCHEDULE_QUALITY_TIME: Active scheduling state for Missions, reads Google Calendar, suggests slots, creates event
   - WAITING: Passive state with daily morning reminder if a Mission (Quality Time) exists today
   - QUALITY_TIME_FOLLOW_UP: Post-event state, asks if father completed the Mission, updates dashboard (feeds WEB-SPEC-008 metrics)
   - DASHBOARD: Visual display state showing belt, mission, streak, achievements, next milestone (WEB-SPEC-008 Father Workspace consumes this)
   - ACTIVITY_IDEAS: On-demand state triggered only when father explicitly asks for ideas (AI-assisted per AI Usage Policy)

2. THE Workflow_Engine SHALL enforce exactly one active workflow state per father at any time, stored in the Father entity as `current_workflow_state`

3. THE Workflow_Engine SHALL implement the following state transitions:
   - WELCOME → SCHEDULE_QUALITY_TIME: When father acknowledges welcome message
   - SCHEDULE_QUALITY_TIME → WAITING: When Mission (Quality Time) event is successfully created in Google Calendar
   - WAITING → QUALITY_TIME_FOLLOW_UP: When scheduled Mission end time has passed
   - QUALITY_TIME_FOLLOW_UP → SCHEDULE_QUALITY_TIME: When father reports completion (updates dashboard first)
   - QUALITY_TIME_FOLLOW_UP → SCHEDULE_QUALITY_TIME: When father reports non-completion (no dashboard update)
   - Any State → ACTIVITY_IDEAS: When father explicitly requests activity ideas
   - ACTIVITY_IDEAS → Previous State: When activity ideas interaction completes
   - Any State → DASHBOARD: When father requests dashboard view (frontend only, consumed by WEB-SPEC-008)

4. WHEN a state transition occurs, THE Workflow_Engine SHALL:
   - Log the transition to the state_transition_log table with timestamp, from_state, to_state, and trigger_reason
   - Update the Father entity's `current_workflow_state` field
   - Determine the appropriate response action based on the new state

5. THE Workflow_Engine SHALL persist all state in the database. No state SHALL be held in memory across requests. The system SHALL be stateless and horizontally scalable.

6. IF the system cannot determine a valid state transition for a received message, THE Workflow_Engine SHALL remain in the current state and request clarification from the father using a pre-written message template

7. THE Workflow_Engine SHALL receive activated fathers from WEB-SPEC-007 (Onboarding) after WhatsApp activation completes. New fathers start in WELCOME state.

8. THE Workflow_Engine SHALL provide dashboard data to WEB-SPEC-008 (Father Workspace) via the /api/workspace/summary endpoint. Dashboard metrics are computed from Mission (Quality Time) completion records.

---

### Requirement 2: Read Before Write Principle

**User Story:** As a father, I want the system to remember everything I've already told it and see my calendar, so that it never asks redundant questions or suggests times I'm busy.

#### Acceptance Criteria

1. BEFORE processing any inbound message, THE Workflow_Engine SHALL execute the Read_Current_State operation which loads:
   - Father profile from database (name, children, preferences, locale, timezone)
   - Current workflow state from database
   - Google Calendar events for the next 7 days (if calendar is connected)
   - Scheduled Quality Time events from database
   - Dashboard metrics from database (belt, streak, achievements)
   - Conversation context from database (last 10 messages in current workflow state)

2. THE Workflow_Engine SHALL NOT ask the father for any information that exists in the System_State. This includes:
   - Children's names (already registered)
   - Available times (read from Google Calendar)
   - Current Quality Time schedule (in database)
   - Previous completion status (in dashboard metrics)

3. WHEN suggesting time slots for Quality Time, THE Workflow_Engine SHALL:
   - Read the father's Google Calendar for the next 7 days
   - Identify busy periods from calendar events
   - Calculate available slots of at least 30 minutes duration
   - Exclude times outside the father's preferred activity hours (default: 6am-10pm local time)
   - Present the top 3-5 available slots ordered by proximity to current time

4. THE Workflow_Engine SHALL cache the System_State for the duration of a single request processing cycle. Each new request SHALL reload state from authoritative sources.

5. IF Google Calendar is not connected for a father, THE Workflow_Engine SHALL prompt the father to connect it with a deep link to the OAuth flow, rather than asking for manual time entry

6. WHEN creating or modifying a Quality Time event, THE Workflow_Engine SHALL re-read Google Calendar immediately before the write operation to detect any conflicts created since the last read

---

### Requirement 3: Google Calendar Integration

**User Story:** As a father, I want Quality Time automatically scheduled in my Google Calendar with proper reminders, so that I don't forget my commitment to spend time with my child.

#### Acceptance Criteria

1. THE Workflow_Engine SHALL require Google Calendar connection as a prerequisite for the SCHEDULE_QUALITY_TIME state. IF calendar is not connected, THE Workflow_Engine SHALL transition to a CALENDAR_SETUP sub-state that guides the OAuth flow.

2. WHEN reading Google Calendar, THE Workflow_Engine SHALL:
   - Use the existing GoogleCalendarService to fetch events
   - Request events from `now` to `now + 7 days`
   - Include both primary calendar and any calendars the father has granted access to
   - Handle token refresh automatically when access token expires
   - Cache calendar data for 5 minutes to avoid excessive API calls within a request cycle

3. WHEN the father selects a time slot, THE Workflow_Engine SHALL create a Google Calendar event with:
   - Title: "👨‍👧 Quality Time with [Child Name]" (or appropriate variant)
   - Duration: 30 minutes minimum, configurable by father
   - Description: "Dad Coach scheduled Quality Time — enjoy your moment together!"
   - Reminders: 1 hour before (popup), 15 minutes before (popup)
   - Color: Green (colorId 10)
   - The Quality Time database record SHALL store the Google Calendar event ID for future reference

4. THE Workflow_Engine SHALL store each Quality Time event in the database with:
   - Google Calendar event ID
   - Father ID
   - Child ID (selected or default child)
   - Scheduled start time and end time
   - Status: SCHEDULED, COMPLETED, MISSED, CANCELLED
   - Completion notes (if completed)
   - Created timestamp and updated timestamp

5. WHEN a Quality Time event time passes, THE Workflow_Engine (via scheduler) SHALL detect this and transition the father to QUALITY_TIME_FOLLOW_UP state

6. IF Google Calendar event creation fails, THE Workflow_Engine SHALL:
   - Log the error with full context
   - Inform the father that scheduling failed
   - Remain in SCHEDULE_QUALITY_TIME state
   - Retry the calendar operation once with exponential backoff

7. THE Workflow_Engine SHALL sync Quality Time event status with Google Calendar: if the father deletes the event in Google Calendar, the next calendar read SHALL detect this and update the Quality Time record to CANCELLED

---

### Requirement 4: WELCOME State Behavior

**User Story:** As a new father arriving from onboarding (WEB-SPEC-007), I want a brief, clear introduction to Dad Coach that gets me to schedule my first Mission (Quality Time) quickly, so that I can start building my relationship with my child without lengthy setup.

#### Acceptance Criteria

1. WHEN a father enters the WELCOME state for the first time (after WEB-SPEC-007 activation), THE Workflow_Engine SHALL send exactly one welcome message that:
   - Greets the father by name (read from profile, populated during WEB-SPEC-007 onboarding)
   - Explains the core concept: schedule Quality Time (Mission), complete it, earn belt progression
   - Asks if the father is ready to schedule their first Mission (Quality Time)
   - Does NOT ask for any information (children, preferences, etc.) — this was collected during WEB-SPEC-007 onboarding

2. THE WELCOME state SHALL accept exactly two response patterns (supporting both English and Hebrew):
   - Affirmative response:
     - English: "yes", "ready", "let's go", "ok", "sure", "start"
     - Hebrew: "כן", "מוכן", "יאללה", "בסדר", "בוא נתחיל", "התחל"
     - Action: Transition to SCHEDULE_QUALITY_TIME
   - Request for more information:
     - English: "tell me more", "how does it work", "explain", "what is this"
     - Hebrew: "ספר לי עוד", "איך זה עובד", "הסבר", "מה זה"
     - Action: Send one explanation message, then re-prompt for readiness

3. IF the father's response does not match the expected patterns, THE Workflow_Engine SHALL send a clarification message with two explicit options:
   - English: "Ready to schedule" or "Tell me more"
   - Hebrew: "מוכן לתאם" or "ספר לי עוד"

4. THE WELCOME state SHALL NOT involve any AI decision-making. The response selection is purely pattern matching on the father's message.

5. WHEN transitioning out of WELCOME, THE Workflow_Engine SHALL mark the Father entity's `welcomed_at` timestamp and never return to WELCOME state for this father

6. THE WELCOME message SHALL reference information already collected during WEB-SPEC-007 onboarding (children names, weekly goal if set) to provide continuity

---

### Requirement 5: SCHEDULE_QUALITY_TIME State Behavior

**User Story:** As a father, I want to quickly see my available times and schedule Quality Time with minimal back-and-forth, so that scheduling feels effortless.

#### Acceptance Criteria

1. WHEN entering SCHEDULE_QUALITY_TIME state, THE Workflow_Engine SHALL:
   - Read Google Calendar for available slots
   - Read children list from database
   - Generate a message presenting 3-5 available time slots with a numbered selection option
   - If only one child exists, assume Quality Time is with that child
   - If multiple children exist, ask which child (by name) this Quality Time is for

2. THE father SHALL respond to time slot selection by:
   - Typing a number (1, 2, 3, etc.) to select a slot
   - Typing a specific time ("tomorrow at 5pm", "Saturday morning")
   - Typing "other" to see more slots
   - Typing "skip" or "not now" to postpone scheduling

3. WHEN the father selects a valid time slot, THE Workflow_Engine SHALL:
   - Re-read Google Calendar to verify slot is still available (conflict detection)
   - If available: Create the calendar event and Quality Time database record
   - If conflict detected: Inform the father and present updated available slots
   - On success: Send confirmation message with event details and transition to WAITING state

4. IF the father requests "other" slots, THE Workflow_Engine SHALL present the next 5 available slots from the pre-computed list

5. IF the father types "skip" or indicates they don't want to schedule now, THE Workflow_Engine SHALL:
   - Acknowledge the decision without judgment
   - Remain in SCHEDULE_QUALITY_TIME state
   - Set a reminder to re-prompt in 24 hours (via scheduler)

6. THE SCHEDULE_QUALITY_TIME state SHALL complete within a maximum of 5 message exchanges. If not completed after 5 exchanges, THE Workflow_Engine SHALL send a summary message and wait for the father to initiate again.

7. ALL time suggestions SHALL be presented in the father's local timezone (read from profile)

---

### Requirement 6: WAITING State Behavior

**User Story:** As a father with a Mission (Quality Time) scheduled, I want a simple reminder on the day of my event, so that I'm prepared to make the most of my time with my child.

#### Acceptance Criteria

1. WHEN the father is in WAITING state, THE Workflow_Engine SHALL be mostly passive:
   - No proactive outreach except the morning reminder
   - Respond to any father-initiated messages appropriately
   - Allow transition to ACTIVITY_IDEAS if father asks for ideas
   - Allow transition to DASHBOARD (frontend request via WEB-SPEC-008)

2. THE Workflow_Engine SHALL send exactly one morning reminder on the day of scheduled Mission (Quality Time):
   - Sent at 8:00 AM in the father's local timezone (or configurable reminder time)
   - Message format (English): "Good morning [Father Name]! Quality Time with [Child Name] today at [Time]. Have a great time! 💪"
   - Message format (Hebrew): "בוקר טוב [Father Name]! זמן איכות עם [Child Name] היום ב-[Time]. תהנו! 💪"
   - Reminder is sent via WhatsApp through the existing WhatsAppService

3. THE morning reminder SHALL be idempotent: if the scheduler runs multiple times, only one reminder is sent per Mission (Quality Time) event per day

4. IF the father sends a message while in WAITING state asking about their schedule, THE Workflow_Engine SHALL:
   - Read the next scheduled Mission (Quality Time) from database
   - Send a message confirming the date, time, and child
   - Remain in WAITING state

5. IF the father asks to reschedule while in WAITING state, THE Workflow_Engine SHALL:
   - Transition to SCHEDULE_QUALITY_TIME state
   - Cancel the existing Mission (Quality Time) event in both database and Google Calendar
   - Present new available time slots

6. WHEN the scheduled Mission (Quality Time) end time passes, THE scheduler SHALL transition the father from WAITING to QUALITY_TIME_FOLLOW_UP state

---

### Requirement 7: QUALITY_TIME_FOLLOW_UP State Behavior

**User Story:** As a father who just had scheduled Mission (Quality Time), I want to quickly confirm whether I completed it, so that my progress is tracked and I can schedule the next one.

#### Acceptance Criteria

1. WHEN the father enters QUALITY_TIME_FOLLOW_UP state, THE Workflow_Engine SHALL send a message asking:
   - English: "Did you complete your Quality Time with [Child Name]?" with options [Yes ✅] [No]
   - Hebrew: "השלמת את זמן האיכות עם [Child Name]?" with options [כן ✅] [לא]

2. IF the father responds affirmatively ("yes", "done", "completed" in English; "כן", "סיימתי", "עשיתי", "הושלם" in Hebrew), THE Workflow_Engine SHALL:
   - Update the Mission (Quality Time) record status to COMPLETED
   - Increment the father's streak counter
   - Add XP toward weekly goal and belt progression (SACRED Belt System)
   - Check if belt progression milestone is reached
   - If milestone reached: send celebration message before proceeding
   - Transition to SCHEDULE_QUALITY_TIME to schedule the next Mission
   - Update the Dashboard metrics in database (for WEB-SPEC-008)

3. IF the father responds negatively ("no", "not yet", "couldn't" in English; "לא", "עוד לא", "לא הצלחתי" in Hebrew), THE Workflow_Engine SHALL:
   - Update the Mission (Quality Time) record status to MISSED
   - Send an encouraging message without judgment
   - Transition to SCHEDULE_QUALITY_TIME to try again
   - Do NOT decrement streak (only consecutive misses affect streak)

4. IF the father provides details about what they did (beyond yes/no), THE Workflow_Engine SHALL:
   - Extract only completion status (did they or didn't they complete)
   - Store any notes in the Mission (Quality Time) record's `completion_notes` field
   - Process as either completion or non-completion per above rules

5. THE QUALITY_TIME_FOLLOW_UP state SHALL complete within a maximum of 3 message exchanges. The first exchange is the question, the second is the answer processing, the third (if needed) is clarification.

6. IF the father does not respond to follow-up within 24 hours, THE Workflow_Engine SHALL mark the Mission (Quality Time) as MISSED and transition to SCHEDULE_QUALITY_TIME state

---

### Requirement 8: DASHBOARD State Behavior

**User Story:** As a father, I want to see my progress visually with belt, streak, achievements, and next milestone, so that I stay motivated to continue.

#### Acceptance Criteria

1. THE Dashboard SHALL be primarily a frontend (web) experience provided by WEB-SPEC-008 (Father Workspace), not a WhatsApp experience. THE Workflow_Engine SHALL provide the API endpoint that serves dashboard data.

2. THE Dashboard API SHALL return the following data structure (aligned with WEB-SPEC-008 Requirements):
   - Current belt (WHITE, YELLOW, ORANGE, GREEN, BLUE, BROWN, BLACK) — feeds WEB-SPEC-008 Requirement 2
   - Progress to next belt (percentage and Quality Time count remaining)
   - Current streak (consecutive completed Quality Times/Missions) — feeds WEB-SPEC-008 Requirement 4
   - Longest streak (historical maximum)
   - Total Quality Times (Missions) completed
   - Recent achievements (last 5) — feeds WEB-SPEC-008 Requirement 3
   - Active mission (if any — simple text description)
   - Next milestone description
   - Weekly goal progress (hours completed / goal hours)

3. WHEN the father sends "dashboard" or "progress" (or Hebrew equivalents: "דשבורד", "התקדמות") via WhatsApp, THE Workflow_Engine SHALL:
   - Generate and send a text summary of their current stats
   - Include a deep link to the web dashboard (WEB-SPEC-008) for the full visual experience
   - Remain in the current workflow state (DASHBOARD is not a persistent state for WhatsApp)

4. THE Dashboard data SHALL be computed from Quality Time (Mission) completion records in real-time. No separate aggregation tables are required at launch scale.

5. Belt progression milestones SHALL be (SACRED — do NOT modify):
   - White Belt: 0-2 Quality Times
   - Yellow Belt: 3-9 Quality Times
   - Orange Belt: 10-24 Quality Times
   - Green Belt: 25-49 Quality Times
   - Blue Belt: 50-99 Quality Times
   - Brown Belt: 100-199 Quality Times
   - Black Belt: 200+ Quality Times

6. THE frontend Dashboard page (WEB-SPEC-008) SHALL automatically refresh belt progression with celebration animation when a new belt is earned (WEB-SPEC-008 Requirement 16)

7. THE Dashboard API SHALL serve as the single source of truth for WEB-SPEC-008 Father Workspace metrics, ensuring consistency between WhatsApp summaries and web dashboard display

---

### Requirement 9: ACTIVITY_IDEAS State Behavior

**User Story:** As a father, I want to get activity ideas for Quality Time with my child only when I ask for them, so that I'm not overwhelmed with suggestions I didn't request.

#### Acceptance Criteria

1. THE ACTIVITY_IDEAS state SHALL only be entered when the father explicitly requests ideas. Keywords:
   - English: "ideas", "activity", "suggestions", "what can I do", "help me plan"
   - Hebrew: "רעיונות", "פעילות", "הצעות", "מה אפשר לעשות", "עזור לי לתכנן"

2. WHEN entering ACTIVITY_IDEAS state, THE Workflow_Engine SHALL:
   - Read the child's age and interests from database
   - Read the time of day and weather (if available via external API)
   - Read previous Quality Time activities (to avoid repetition)
   - Use the AI Message_Generator to create personalized activity suggestions

3. THE Message_Generator SHALL produce exactly 3 activity ideas formatted as:
   - Numbered list (1, 2, 3)
   - Each idea with: title, brief description (2-3 sentences), estimated duration
   - Ideas appropriate for the child's age
   - At least one indoor and one outdoor option when possible

4. WHEN activity ideas are presented, THE Workflow_Engine SHALL:
   - Allow the father to select an idea by number for more details
   - Allow the father to request more ideas
   - Allow the father to exit with "thanks" or "enough" — return to previous state

5. THE AI Message_Generator is the ONLY place where AI generates content in the ACTIVITY_IDEAS flow. The decision to present ideas, the number of ideas, the format, and the exit conditions are all deterministic backend logic.

6. AFTER exiting ACTIVITY_IDEAS, THE Workflow_Engine SHALL return to the state the father was in before entering ACTIVITY_IDEAS (stored in `previous_workflow_state` field)

---

### Requirement 10: Message Generation (AI Role Restriction)

**User Story:** As a product owner, I want AI to only generate natural language messages without making any decisions, so that the product behavior is consistent and predictable.

#### Acceptance Criteria

1. THE Message_Generator service SHALL only generate text content. It SHALL NOT:
   - Decide which state to transition to
   - Decide what information to ask for
   - Decide whether the father completed Quality Time (Mission)
   - Decide which time slots to suggest
   - Make any determination about system state
   - Orchestrate conversations or workflows

2. THE Message_Generator SHALL receive a structured context object containing:
   - The message type (WELCOME, TIME_SLOTS, CONFIRMATION, FOLLOW_UP_QUESTION, etc.)
   - Required data fields for that message type (father name, child name, time slots, etc.)
   - Target language (father's preferred language: "en" for English or "he" for Hebrew, read from LanguagePreference entity)

3. THE Message_Generator SHALL return only a string (the message text) and SHALL NOT return:
   - State transition recommendations
   - Follow-up action suggestions
   - Metadata about confidence or alternatives

4. IF the Message_Generator fails or times out, THE Workflow_Engine SHALL use a pre-written fallback message template for that message type. Every message type SHALL have a corresponding fallback template in both English and Hebrew.

5. THE Message_Generator implementation SHALL use the existing IntelligenceLayer infrastructure but with simplified prompts that only request text generation, not decision-making

6. Message generation latency budget: 5 seconds maximum. If exceeded, use fallback template immediately.

7. **AI Usage Policy Alignment — WHERE AI ADDS VALUE:**
   - ✅ Activity recommendations (ACTIVITY_IDEAS state, when father explicitly asks)
   - ✅ Short personalized encouragement (~10% of completion messages)
   - ✅ Quality Time (Mission) summaries (brief, 1-2 sentences)
   - ✅ Reflection questions (post-completion, optional)
   - ✅ Belt level-up celebration messages (personalized)

8. **AI Usage Policy Alignment — WHERE AI IS NOT USED:**
   - ❌ State transitions or decision-making (handled by WorkflowEngine)
   - ❌ Conversation orchestration (deterministic state machine)
   - ❌ Data extraction or interpretation (use pattern matching)
   - ❌ Daily coaching messages (use templates)
   - ❌ Follow-up questions (fixed templates)
   - ❌ Re-engagement messages (simple templates)

9. AI call budget: Maximum 2 AI calls per user per day. If exceeded, use templates for remaining interactions.

---

### Requirement 11: WhatsApp Message Flow

**User Story:** As a father, I want my WhatsApp interaction with Dad Coach to feel like a simple, guided flow rather than an unpredictable chat, so that I can engage quickly without confusion.

#### Acceptance Criteria

1. WHEN a WhatsApp message arrives, THE Workflow_Engine SHALL process it through this pipeline:
   - Step 1: Parse and validate message (existing WhatsAppMessageParser)
   - Step 2: Identify father from phone number (existing FatherResolver)
   - Step 3: Load System_State (per Requirement 2)
   - Step 4: Determine current workflow state
   - Step 5: Match message against expected patterns for current state
   - Step 6: Execute business logic for matched pattern
   - Step 7: Generate response message (Message_Generator or fallback)
   - Step 8: Persist state changes
   - Step 9: Send response via WhatsApp

2. THE Workflow_Engine SHALL respond to every WhatsApp message within 30 seconds. If processing takes longer, send a "processing" message immediately and follow up with the real response.

3. EACH workflow state SHALL define its expected message patterns. Patterns are regex-based or keyword-based (supporting English and Hebrew), not AI-interpreted:
   - WELCOME:
     - Affirmative: EN: "yes", "ready", "ok", "sure", "start"; HE: "כן", "מוכן", "יאללה", "בסדר", "התחל"
     - Question: EN: "how", "what", "explain"; HE: "איך", "מה", "הסבר"
   - SCHEDULE_QUALITY_TIME:
     - Numbers (1-9)
     - Time expressions (EN: "tomorrow", "saturday", "5pm"; HE: "מחר", "שבת", "17:00")
     - Skip: EN: "skip", "not now", "later"; HE: "דלג", "לא עכשיו", "אחר כך"
     - More: EN: "other", "more"; HE: "אחר", "עוד"
   - WAITING:
     - Reschedule: EN: "reschedule", "change", "cancel"; HE: "שנה זמן", "שינוי", "ביטול"
     - Schedule inquiry: EN: "when", "schedule", "next"; HE: "מתי", "לוח זמנים", "הבא"
     - Dashboard: EN: "dashboard", "progress"; HE: "דשבורד", "התקדמות"
   - QUALITY_TIME_FOLLOW_UP:
     - Affirmative: EN: "yes", "done", "completed"; HE: "כן", "סיימתי", "עשיתי", "הושלם"
     - Negative: EN: "no", "not yet", "couldn't"; HE: "לא", "עוד לא", "לא הצלחתי"
   - ACTIVITY_IDEAS:
     - Numbers (1-3)
     - More: EN: "more"; HE: "עוד"
     - Exit: EN: "thanks", "done", "enough"; HE: "תודה", "סיימתי", "מספיק"

4. IF an incoming message does not match any expected pattern for the current state, THE Workflow_Engine SHALL:
   - Send a clarifying message specific to the current state
   - Include explicit options for valid responses (in father's language)
   - NOT attempt to interpret the message using AI

5. THE Workflow_Engine SHALL support quick reply buttons where WhatsApp allows:
   - Present numbered options (1, 2, 3) as interactive buttons when possible
   - Fall back to text-based numbered lists for broader compatibility

---

### Requirement 12: Scheduler and Time-Based Transitions

**User Story:** As a father, I want the system to automatically follow up after my Quality Time and send timely reminders, so that I don't have to remember to check in manually.

#### Acceptance Criteria

1. THE Workflow_Engine scheduler SHALL run the following periodic jobs:
   - Morning reminder job: Every day at 7:50 AM UTC, check for Quality Times scheduled today and send reminders at 8 AM local time
   - Follow-up transition job: Every 15 minutes, check for Quality Time events that have ended and transition fathers to QUALITY_TIME_FOLLOW_UP
   - Stale state detection job: Every hour, check for fathers stuck in QUALITY_TIME_FOLLOW_UP for over 24 hours and auto-transition them

2. ALL scheduler jobs SHALL be idempotent. Running the same job multiple times SHALL NOT produce duplicate messages or transitions.

3. THE scheduler SHALL use the existing Spring @Scheduled infrastructure and persist job execution logs to the database for debugging.

4. WHEN the follow-up transition job runs, it SHALL:
   - Query Quality Time events with status SCHEDULED and end_time < now
   - For each event, update the father's workflow state to QUALITY_TIME_FOLLOW_UP
   - Send the follow-up question via WhatsApp
   - Update Quality Time status to FOLLOW_UP_SENT

5. WHEN the stale state detection job runs, it SHALL:
   - Query fathers in QUALITY_TIME_FOLLOW_UP state for over 24 hours
   - Mark their Quality Time as MISSED
   - Transition them to SCHEDULE_QUALITY_TIME
   - Send a gentle message inviting them to schedule again

6. THE scheduler SHALL process fathers in batches of 100 to avoid overwhelming the system or WhatsApp rate limits

---

### Requirement 13: Frontend Workspace Changes

**User Story:** As a father, I want the web dashboard to show my clear progress path and upcoming Quality Time, so that I stay engaged with the program.

#### Acceptance Criteria

1. THE frontend workspace SHALL display these primary components:
   - Belt progression card: Current belt, visual progress bar to next belt, count of Quality Times completed
   - Next Quality Time card: Date, time, child name, countdown timer, quick reschedule link
   - Streak display: Current streak with flame icon, longest streak badge
   - Recent activity feed: Last 5 Quality Time completions with dates and children
   - Achievement badges: Unlocked achievements displayed as earned, locked as silhouettes

2. THE frontend SHALL fetch workspace data from a single API endpoint: GET /api/workspace/summary which returns all dashboard data in one call

3. WHEN a new belt is earned, THE frontend SHALL:
   - Display a celebration modal with belt name and congratulations message
   - Play a subtle animation
   - Update the belt progression card automatically

4. THE frontend SHALL include a "Schedule Quality Time" primary action button that:
   - Opens a calendar picker pre-populated with suggested available slots (from backend)
   - Allows selection of child if multiple children exist
   - Submits scheduling request to backend
   - Shows confirmation on success

5. THE frontend workspace SHALL NOT include:
   - Free-form coaching chat interface
   - AI-generated coaching tips or messages
   - Complex mission selection flows
   - Memory or conversation history displays

6. THE frontend SHALL poll for workspace updates every 60 seconds when the tab is active, or use WebSocket for real-time updates if available

---

### Requirement 14: API Simplification

**User Story:** As a developer, I want simple, RESTful APIs that correspond to clear business operations, so that the frontend and backend contract is easy to understand and maintain.

#### Acceptance Criteria

1. THE Workflow_Engine SHALL expose these primary REST endpoints:
   - GET /api/workspace/summary — Dashboard data for current father
   - GET /api/quality-time/available-slots — Next available time slots from Google Calendar
   - POST /api/quality-time/schedule — Schedule a new Quality Time event
   - POST /api/quality-time/{id}/complete — Mark Quality Time as completed
   - POST /api/quality-time/{id}/cancel — Cancel a scheduled Quality Time
   - GET /api/activity-ideas — Get AI-generated activity suggestions (only endpoint using AI)

2. ALL API responses SHALL return deterministic data based on database state. No AI processing SHALL occur in API responses except for /api/activity-ideas.

3. THE /api/quality-time/schedule endpoint SHALL:
   - Accept: { childId, startTime, duration }
   - Validate against Google Calendar availability
   - Create Google Calendar event
   - Create Quality Time database record
   - Return: { qualityTimeId, calendarEventId, startTime, endTime, childName }

4. THE existing workspace, conversation, and mission APIs SHALL be deprecated and eventually removed. New frontend features SHALL only use the new simplified API structure.

5. ALL API endpoints SHALL require authentication via the existing magic-link session mechanism

6. API error responses SHALL use consistent error codes defined in WorkspaceErrorCode enum, not AI-generated error messages

---

### Requirement 15: Data Migration and Backwards Compatibility

**User Story:** As a product owner, I want existing fathers to transition smoothly to the new deterministic workflow without losing their history or progress, so that no user is negatively impacted by this architectural change.

#### Acceptance Criteria

1. THE migration SHALL preserve all existing data:
   - Father profiles (unchanged)
   - Children data (unchanged)
   - Mission history (converted to Quality Time completion records)
   - Conversation history (archived but not used in new flow)
   - Belt/streak data (recalculated from Quality Time completions)

2. FOR each existing father, THE migration SHALL:
   - Calculate their current belt based on total mission completions
   - Set initial streak to 0 (fresh start for streak tracking)
   - Preserve their longest streak if tracked
   - Set workflow state to SCHEDULE_QUALITY_TIME (not WELCOME, since they're not new)
   - Preserve Google Calendar connection if already established

3. THE migration SHALL be performed via a Flyway migration script that:
   - Creates the new quality_time table
   - Creates the workflow_state_log table
   - Adds current_workflow_state column to fathers table
   - Migrates completed missions to quality_time completions
   - Is idempotent (can be re-run safely)

4. THE migration SHALL NOT delete any existing tables or data. Old tables (missions, conversations, memories) remain for reference but are not used by the new workflow.

5. AFTER migration, existing fathers SHALL receive a one-time message explaining the simplified new experience the next time they interact with the system

6. THE migration SHALL be reversible: if critical issues are discovered, the system SHALL be able to fall back to the previous architecture using feature flags

---

### Requirement 16: Operational Observability

**User Story:** As an operator, I want clear visibility into workflow state distribution and transition patterns, so that I can identify issues and understand user behavior.

#### Acceptance Criteria

1. THE Workflow_Engine SHALL log every state transition to the state_transition_log table with:
   - Father ID
   - From state
   - To state
   - Trigger reason (user_message, scheduler, timeout)
   - Timestamp
   - Message ID that triggered the transition (if applicable)

2. THE system SHALL expose metrics for:
   - Count of fathers in each workflow state
   - State transition rates (transitions per hour by type)
   - Quality Time completion rate (completed vs missed)
   - Average time spent in each state
   - Message generation latency (AI vs fallback usage)

3. THE scheduler jobs SHALL log their execution with:
   - Job name
   - Start and end timestamps
   - Number of records processed
   - Number of errors encountered
   - Duration

4. WHEN the Message_Generator uses a fallback template instead of AI, THIS SHALL be logged as a warning-level event for monitoring AI reliability

5. THE system SHALL expose a health endpoint that reports:
   - Workflow Engine status
   - Google Calendar API status (last successful call timestamp)
   - WhatsApp API status
   - Database connection status
   - Scheduler last-run timestamps

6. ALL logs SHALL include father_id as a field to enable filtering logs for a specific user's journey
