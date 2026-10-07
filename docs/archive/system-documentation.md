# Dad Coach - System Documentation

## Overview

Dad Coach is an AI-powered parenting coaching application that helps fathers build stronger relationships with their children through scheduled "Quality Time" activities. The system uses WhatsApp as the primary communication channel and integrates with Google Calendar for scheduling.

---

## Architecture

### High-Level Components

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   WhatsApp      │────▶│   Backend API   │────▶│   PostgreSQL    │
│   (Twilio)      │◀────│   (Spring Boot) │◀────│   Database      │
└─────────────────┘     └────────┬────────┘     └─────────────────┘
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼            ▼
            ┌───────────┐ ┌───────────┐ ┌───────────┐
            │  OpenAI   │ │  Google   │ │  Redis    │
            │  GPT-4    │ │ Calendar  │ │  Cache    │
            └───────────┘ └───────────┘ └───────────┘
```

### Backend Stack
- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **Database**: PostgreSQL
- **Cache**: Redis
- **AI Provider**: OpenAI GPT-4
- **Messaging**: Twilio WhatsApp API
- **Calendar**: Google Calendar API

---

## Core Concepts

### Father (User)
The primary user of the system. Each father has:
- Profile information (name, phone, locale)
- Children list
- Current workflow state
- Weekly goal
- Belt level (gamification)

### Quality Time (QT)
A scheduled activity between a father and child. States:
- `SCHEDULED` - Planned but not yet completed
- `COMPLETED` - Successfully finished
- `CANCELLED` - Cancelled by user
- `CANCELLED_BY_SYNC` - Calendar event was deleted externally

### Weekly Goal
Each father sets a target number of Quality Times per week:
- Progress tracked from Sunday to Saturday
- Belt promotions based on weekly achievements
- 8 AM Sunday prompt for new goal setting

### Workflow States
The father progresses through various states:
```
WELCOME → WAITING → QUALITY_TIME_REMINDER → QUALITY_TIME_FOLLOW_UP → WAITING
                 ↓
          SCHEDULE_QUALITY_TIME
                 ↓
          ACTIVITY_IDEAS
```

---

## Scheduler Jobs

The system runs several scheduled jobs to manage the coaching workflow:

### 1. Morning Reminders
- **Cron**: `0 50 7 * * *` (7:50 AM UTC)
- **Purpose**: Send daily 8 AM reminders to fathers
- **Method**: `WorkflowScheduler.sendMorningReminders()`

### 2. Pre-Quality Time Reminders (1-Hour Before)
- **Cron**: `0 */15 * * * *` (every 15 minutes)
- **Purpose**: Send reminder ~1 hour before scheduled QT
- **Window**: 45-75 minutes before QT start time
- **Method**: `WorkflowScheduler.processPreQtReminders()`

### 3. Follow-Up Transitions
- **Cron**: `0 */15 * * * *` (every 15 minutes)
- **Purpose**: Transition to follow-up state after QT ends
- **Method**: `WorkflowScheduler.processFollowUpTransitions()`

### 4. Weekly Goal Completion (Belt Promotions)
- **Cron**: `0 0 6 * * SUN` (6:00 AM UTC on Sundays)
- **Purpose**: Calculate weekly achievements, promote belts
- **Method**: `WorkflowScheduler.processWeeklyGoalCompletions()`

### 5. Weekly Goal Prompt
- **Cron**: `0 0 5 * * SUN` (5:00 AM UTC on Sundays = 8 AM Israel)
- **Purpose**: Prompt fathers to set their weekly goal
- **Method**: `WorkflowScheduler.promptWeeklyGoalSetting()`

---

## AI Agent (CoachingAgent)

The AI agent processes incoming WhatsApp messages and determines appropriate actions.

### Processing Flow

```
1. Receive Message
       ↓
2. Load Father Profile
       ↓
3. Load System State (QTs, Calendar, Weekly Goal)
       ↓
4. Build Context for AI
       ↓
5. Call OpenAI GPT-4
       ↓
6. Parse AI Decision → Tool Selection
       ↓
7. Execute Tool
       ↓
8. Return Response to User
```

### Available Tools

| Tool | Description |
|------|-------------|
| `schedule_quality_time` | Create new QT in DB and Google Calendar |
| `complete_quality_time` | Mark QT as completed |
| `reschedule_quality_time` | Change QT date/time |
| `cancel_quality_time` | Cancel scheduled QT |
| `connect_calendar` | Generate Google OAuth link |
| `show_progress` | Display weekly progress |
| `get_activity_ideas` | Suggest activities for QT |
| `set_weekly_goal` | Set target QTs for the week |
| `clarify` | Ask for clarification |
| `acknowledge` | Acknowledge user input |

### Smart Fallback System

When the AI cannot determine user intent:
1. First attempt: Primary AI classification
2. Second attempt: Smart fallback with enhanced context
3. Third attempt: Pattern matching for common phrases
4. Final fallback: Helpful options menu (never generic error)

**Recognized patterns include:**
- Acknowledgments: "כן", "אוקי", "סבבה", "👍", "✅"
- Already done: "כבר עשיתי", "כבר עזינן", "סיימתי"
- Questions: "מה", "למה", "איך"

---

## System State Loading

The `SystemStateLoader` builds context for AI decisions:

### Data Sources
1. **Father Profile** - From database
2. **Quality Times** - SCHEDULED status, future dates
3. **Calendar Events** - From Google Calendar API
4. **Weekly Goal** - Current week's target and progress
5. **Conversation History** - Recent messages

### Calendar Sync Validation
When loading Quality Times, the system validates against Google Calendar:
- If QT has `googleCalendarEventId` but event doesn't exist in calendar
- Status automatically changes to `CANCELLED_BY_SYNC`
- Prevents AI from mentioning non-existent appointments

---

## Message Types

| Type | Purpose |
|------|---------|
| `MORNING_REMINDER` | Daily 8 AM reminder |
| `PRE_QT_REMINDER` | 1 hour before QT |
| `QT_FOLLOW_UP` | After QT completion |
| `WEEKLY_GOAL_PROMPT` | Sunday morning goal setting |
| `BELT_PROMOTION` | Achievement notification |
| `CLARIFICATION` | Request for clarification |

---

## API Endpoints

### Calendar Events
```
GET /api/v1/calendar/events/{fatherId}
```
Returns upcoming calendar events for the father.

### Webhook (WhatsApp)
```
POST /api/v1/webhook/whatsapp
```
Receives incoming WhatsApp messages from Twilio.

### Health Check
```
GET /actuator/health
```
System health status.

---

## Configuration

### Environment Variables
| Variable | Description |
|----------|-------------|
| `OPENAI_API_KEY` | OpenAI API key |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth secret |
| `TWILIO_ACCOUNT_SID` | Twilio account SID |
| `TWILIO_AUTH_TOKEN` | Twilio auth token |
| `DATABASE_URL` | PostgreSQL connection string |
| `REDIS_URL` | Redis connection string |

### Application Properties
```yaml
dadcoach:
  ai:
    enabled: true
    model: gpt-4
  scheduler:
    enabled: true
  locale:
    default: he
```

---

## Gamification (Belt System)

Fathers earn belts based on weekly achievements:

| Belt | Color | Requirement |
|------|-------|-------------|
| White | ⬜ | Starting belt |
| Yellow | 🟨 | 1 week goal met |
| Orange | 🟧 | 2 consecutive weeks |
| Green | 🟩 | 3 consecutive weeks |
| Blue | 🟦 | 4 consecutive weeks |
| Purple | 🟪 | 5 consecutive weeks |
| Brown | 🟫 | 6 consecutive weeks |
| Black | ⬛ | 7+ consecutive weeks |

---

## Recent Bug Fixes (August 2026)

### Bug 1: 1-Hour Reminder Not Sending
**Problem**: Pre-QT reminder window (30-90 min) was too wide, causing inconsistent reminders.
**Fix**: Changed window to 45-75 minutes for more reliable ~1 hour before delivery.

### Bug 2: Weekly Goal Prompt Missing
**Problem**: No scheduler existed for Sunday 8 AM weekly goal prompt.
**Fix**: Added `promptWeeklyGoalSetting()` job at 5:00 UTC (8 AM Israel).

### Bug 3: Calendar API (Not a Bug)
**Status**: API already exists at `/api/v1/calendar/events/{fatherId}`.

### Bug 4: AI Says QT Exists When Calendar Empty
**Problem**: If user deleted QT from Google Calendar, DB still showed SCHEDULED.
**Fix**: Added calendar sync validation in `SystemStateLoader` - auto-cancels orphaned QTs.

### Bug 5: Generic "לא הבנתי" Error
**Problem**: AI showed unhelpful "I don't understand" message too often.
**Fix**: Implemented smart fallback with helpful options menu, never returns generic error.

---

## Monitoring

### Logs
All significant actions are logged with structured format:
```
INFO - Processing message for father: fatherId=xxx
INFO - AI decision: tool=schedule_quality_time, params={...}
INFO - Quality time scheduled: qtId=xxx, fatherId=xxx
```

### Metrics
- Message processing time
- AI response latency
- Scheduler job execution
- Calendar sync operations

---

## Support & Troubleshooting

### Common Issues

**Father not receiving messages**
1. Check WhatsApp opt-in status
2. Verify phone number format
3. Check Twilio delivery logs

**Calendar not syncing**
1. Verify Google OAuth token validity
2. Check calendar permissions
3. Run manual sync validation

**AI not responding correctly**
1. Check OpenAI API status
2. Review conversation context
3. Check system state loading

---

## Contact

For technical issues, contact the development team.
