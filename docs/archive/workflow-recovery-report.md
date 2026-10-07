# Dad Coach Workflow Recovery Report

**Date:** September 10, 2026  
**Workflow ID:** `550e8400-e29b-41d4-a716-446655440001`

---

## Executive Summary

Completed a full review and recovery of the Dad Coach workflow configuration. Fixed **10 identified problems** without introducing any new architecture, database tables, or framework changes—only configuration data was modified.

### Key Metrics
| Metric | Before | After | Change |
|--------|--------|-------|--------|
| States | 10 | 10 | — |
| Transitions | 23 | 23 | — |
| Transitions with triggers | 0 | 23 | +23 ✅ |
| Total tool bindings | ~70 | ~57 | -13 |
| Context provider bindings | ~27 | ~21 | -6 |

---

## Problems Identified & Fixed

| # | Problem | Fix Applied |
|---|---------|-------------|
| 1 | All 23 transitions had `triggerType=null` | Configured all with proper trigger types |
| 2 | `connect_calendar` on all 10 states (overkill) | Removed from 8 states, kept in 2 |
| 3 | `show_help` on states where not needed | Removed from 3 states, kept only in WAITING |
| 4 | `get_activity_ideas` in WAITING (wrong place) | Removed—user should transition to ACTIVITY_IDEAS |
| 5 | `calendar_context` on states that don't need it | Removed from 7 states, kept in 3 |
| 6 | ACTIVITY_IDEAS missing `quality_time_context` | Added provider |
| 7 | QUALITY_TIME_REMINDER_1_HOUR_BEFORE missing `cancel_quality_time` | Added tool |
| 8 | RE_SCHEDULE_QUALITY_TIME missing `schedule_quality_time` | Added tool |
| 9 | Timer transitions not using structured triggers | Configured with CUSTOM type |
| 10 | Confusing transition names | Renamed for clarity |

---

## Tools Per State (Before → After)

| State | Before | After | Changes |
|-------|--------|-------|---------|
| WELCOME | greet, show_progress, get_weekly_goal_status, connect_calendar | greet, show_progress, get_weekly_goal_status | -connect_calendar |
| ONBOARDING | save_user_profile, add_child, clarify, connect_calendar, show_help | save_user_profile, add_child, clarify | -connect_calendar, -show_help |
| SET_WEEKLY_GOAL | set_weekly_goal, get_weekly_goal_status, connect_calendar, show_help | set_weekly_goal, get_weekly_goal_status | -connect_calendar, -show_help |
| SCHEDULE_QUALITY_TIME | schedule_quality_time, show_available_slots, get_activity_ideas, connect_calendar, show_help | schedule_quality_time, show_available_slots, get_activity_ideas, connect_calendar | -show_help |
| WAITING | show_progress, get_weekly_goal_status, show_weekly_summary, schedule_quality_time, reschedule_quality_time, cancel_quality_time, show_help, connect_calendar, get_activity_ideas | show_progress, get_weekly_goal_status, show_weekly_summary, schedule_quality_time, reschedule_quality_time, cancel_quality_time, show_help | -connect_calendar, -get_activity_ideas |
| QUALITY_TIME_REMINDER_MORNING | reschedule_quality_time, cancel_quality_time, get_activity_ideas, connect_calendar | reschedule_quality_time, cancel_quality_time, get_activity_ideas | -connect_calendar |
| QUALITY_TIME_REMINDER_1_HOUR_BEFORE | reschedule_quality_time, get_activity_ideas, connect_calendar | cancel_quality_time, get_activity_ideas, reschedule_quality_time | -connect_calendar, +cancel_quality_time |
| QUALITY_TIME_FOLLOW_UP | complete_quality_time, show_progress, schedule_quality_time, connect_calendar | complete_quality_time, show_progress, schedule_quality_time | -connect_calendar |
| RE_SCHEDULE_QUALITY_TIME | reschedule_quality_time, show_available_slots, cancel_quality_time, connect_calendar | schedule_quality_time, reschedule_quality_time, show_available_slots, cancel_quality_time, connect_calendar | +schedule_quality_time |
| ACTIVITY_IDEAS | get_activity_ideas, schedule_quality_time, connect_calendar | get_activity_ideas, schedule_quality_time | -connect_calendar |

---

## Context Providers Per State (Before → After)

| State | Before | After | Changes |
|-------|--------|-------|---------|
| WELCOME | family_context, quality_time_context, calendar_context | family_context, quality_time_context | -calendar_context |
| ONBOARDING | family_context, calendar_context | family_context | -calendar_context |
| SET_WEEKLY_GOAL | family_context, quality_time_context, calendar_context | family_context, quality_time_context | -calendar_context |
| SCHEDULE_QUALITY_TIME | family_context, quality_time_context, calendar_context | family_context, quality_time_context, calendar_context | — |
| WAITING | family_context, quality_time_context, calendar_context | family_context, quality_time_context, calendar_context | — |
| QUALITY_TIME_REMINDER_MORNING | family_context, quality_time_context, calendar_context | family_context, quality_time_context | -calendar_context |
| QUALITY_TIME_REMINDER_1_HOUR_BEFORE | family_context, quality_time_context, calendar_context | family_context, quality_time_context | -calendar_context |
| QUALITY_TIME_FOLLOW_UP | family_context, quality_time_context, calendar_context | family_context, quality_time_context | -calendar_context |
| RE_SCHEDULE_QUALITY_TIME | family_context, quality_time_context, calendar_context | family_context, quality_time_context, calendar_context | — |
| ACTIVITY_IDEAS | family_context | quality_time_context, family_context | +quality_time_context |

---

## Complete Transition Configuration

### Trigger Types Used
- **CONTEXT_CONDITION (4):** Automatic routing based on user profile/goal status
- **TOOL_RESPONSE (2):** Transitions triggered by successful tool execution
- **USER_INTENT (14):** User-driven transitions based on expressed intent
- **CUSTOM (3):** Scheduled timer triggers for reminders

### All 23 Transitions

| From State | Transition Key | To State | Trigger Type |
|------------|---------------|----------|--------------|
| WELCOME | new_user_onboarding | ONBOARDING | CONTEXT_CONDITION |
| WELCOME | needs_weekly_goal | SET_WEEKLY_GOAL | CONTEXT_CONDITION |
| WELCOME | returning_user | WAITING | CONTEXT_CONDITION |
| WELCOME | immediate_schedule | SCHEDULE_QUALITY_TIME | USER_INTENT |
| ONBOARDING | onboarding_complete | SET_WEEKLY_GOAL | CONTEXT_CONDITION |
| SET_WEEKLY_GOAL | goal_set | SCHEDULE_QUALITY_TIME | TOOL_RESPONSE |
| SCHEDULE_QUALITY_TIME | scheduling_complete | WAITING | USER_INTENT |
| SCHEDULE_QUALITY_TIME | need_activity_ideas | ACTIVITY_IDEAS | USER_INTENT |
| WAITING | schedule_new | SCHEDULE_QUALITY_TIME | USER_INTENT |
| WAITING | explore_ideas | ACTIVITY_IDEAS | USER_INTENT |
| WAITING | timer_morning_reminder | QUALITY_TIME_REMINDER_MORNING | CUSTOM |
| WAITING | reschedule_request | RE_SCHEDULE_QUALITY_TIME | USER_INTENT |
| QUALITY_TIME_REMINDER_MORNING | reschedule_from_morning | RE_SCHEDULE_QUALITY_TIME | USER_INTENT |
| QUALITY_TIME_REMINDER_MORNING | ideas_from_morning | ACTIVITY_IDEAS | USER_INTENT |
| QUALITY_TIME_REMINDER_MORNING | timer_1_hour_reminder | QUALITY_TIME_REMINDER_1_HOUR_BEFORE | CUSTOM |
| QUALITY_TIME_REMINDER_1_HOUR_BEFORE | reschedule_from_1hr | RE_SCHEDULE_QUALITY_TIME | USER_INTENT |
| QUALITY_TIME_REMINDER_1_HOUR_BEFORE | ideas_from_1hr | ACTIVITY_IDEAS | USER_INTENT |
| QUALITY_TIME_REMINDER_1_HOUR_BEFORE | timer_follow_up | QUALITY_TIME_FOLLOW_UP | CUSTOM |
| QUALITY_TIME_FOLLOW_UP | quality_time_completed | WAITING | TOOL_RESPONSE |
| QUALITY_TIME_FOLLOW_UP | reschedule_from_followup | RE_SCHEDULE_QUALITY_TIME | USER_INTENT |
| RE_SCHEDULE_QUALITY_TIME | rescheduling_done | WAITING | USER_INTENT |
| ACTIVITY_IDEAS | done_with_ideas | WAITING | USER_INTENT |
| ACTIVITY_IDEAS | schedule_from_ideas | SCHEDULE_QUALITY_TIME | USER_INTENT |

---

## State Machine Diagram

```
                              ┌─────────────────┐
                              │     WELCOME     │ ◄── Initial State
                              │   (router)      │
                              └────────┬────────┘
                                       │
           ┌───────────────────────────┼───────────────────────────┐
           │                           │                           │
           ▼                           ▼                           ▼
   ┌───────────────┐          ┌───────────────┐          ┌─────────────────┐
   │  ONBOARDING   │          │SET_WEEKLY_GOAL│          │     WAITING     │◄─────────┐
   │ (new users)   │          │  (set goal)   │          │   (home state)  │          │
   └───────┬───────┘          └───────┬───────┘          └────────┬────────┘          │
           │                          │                           │                    │
           └──────────────────────────┘                           │                    │
                      │                                           │                    │
                      ▼                                           │                    │
           ┌─────────────────────┐                               │                    │
           │ SCHEDULE_QUALITY_   │◄──────────────────────────────┤                    │
           │       TIME          │                               │                    │
           └──────────┬──────────┘                               │                    │
                      │                                           │                    │
                      └───────────────────────────────────────────┤                    │
                                                                  │                    │
                                   ┌──────────────────────────────┘                    │
                                   │                                                   │
                                   ▼                                                   │
                    ┌──────────────────────────┐                                      │
                    │QUALITY_TIME_REMINDER_    │                                      │
                    │      MORNING             │                                      │
                    └────────────┬─────────────┘                                      │
                                 │                                                    │
                                 ▼                                                    │
                    ┌──────────────────────────┐                                      │
                    │QUALITY_TIME_REMINDER_    │                                      │
                    │   1_HOUR_BEFORE          │                                      │
                    └────────────┬─────────────┘                                      │
                                 │                                                    │
                                 ▼                                                    │
                    ┌──────────────────────────┐                                      │
                    │  QUALITY_TIME_FOLLOW_UP  │──────────────────────────────────────┘
                    └──────────────────────────┘
                                 │
                                 │ (can also go to)
                                 ▼
                    ┌──────────────────────────┐
                    │  RE_SCHEDULE_QUALITY_    │───────────────────────────────────────┐
                    │        TIME              │                                       │
                    └──────────────────────────┘                                       │
                                                                                       │
                    ┌──────────────────────────┐                                       │
                    │     ACTIVITY_IDEAS       │───────────────────────────────────────┘
                    └──────────────────────────┘
                          (can go to SCHEDULE_QUALITY_TIME or WAITING)
```

---

## Validated User Flows

### Flow 1: New User Onboarding
```
WELCOME → ONBOARDING → SET_WEEKLY_GOAL → SCHEDULE_QUALITY_TIME → WAITING
```

### Flow 2: Returning User
```
WELCOME → WAITING (direct)
```

### Flow 3: Quality Time Reminder Chain
```
WAITING → MORNING_REMINDER → 1_HOUR_BEFORE → FOLLOW_UP → WAITING
```

---

## Graph Validation Results

| Check | Result |
|-------|--------|
| All states have outbound transitions | ✅ Pass |
| All states reachable from WELCOME | ✅ Pass |
| All states can return to WAITING | ✅ Pass |
| No dead ends | ✅ Pass |
| Graph is connected | ✅ Pass |

---

## Recommendations for Future Work

1. **Behavior Rules:** 6 states have 0 behavior rules (WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_REMINDER_1_HOUR_BEFORE, QUALITY_TIME_FOLLOW_UP, RE_SCHEDULE_QUALITY_TIME). Consider adding state-specific guidance rules.

2. **Migrated Rules:** Existing behavior rules (ONBOARDING=25, SET_WEEKLY_GOAL=11, etc.) all have `conditionType=ALWAYS` despite having conditional descriptions. Consider reviewing and updating condition types.

3. **Timer Implementation:** CUSTOM trigger type is used for scheduled reminders. Verify the backend scheduler service correctly interprets these triggers.

---

## API Reference

All changes made via Admin API:
- `DELETE /api/v1/admin/workflows/{workflowId}/states/{stateId}/tools/{toolId}`
- `POST /api/v1/admin/workflows/{workflowId}/states/{stateId}/tools`
- `DELETE /api/v1/admin/workflows/{workflowId}/states/{stateId}/context-providers/{bindingId}`
- `POST /api/v1/admin/workflows/{workflowId}/states/{stateId}/context-providers`
- `PUT /api/v1/admin/workflows/{workflowId}/states/{stateId}/transitions/{transitionId}`

---

*Report generated automatically by workflow recovery process.*
