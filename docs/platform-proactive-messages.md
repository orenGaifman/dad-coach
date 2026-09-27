# Workflow Platform proactive messages (Dad Coach 3)

Dad Coach 3 is a Workflow Platform workflow that writes its own proactive messages: session reminders, post-session follow-ups, the Sunday check-in and planning nudges. The platform schedules and executes them. When one produces a user-facing message, the platform calls Dad Coach, and Dad Coach delivers it over WhatsApp.

A scheduled execution whose agent decides nothing should be sent is suppressed on the platform side, and Dad Coach is not called at all.
Suppression is only ever the agent's explicit `[[SUPPRESS_RESPONSE]]`. A failed execution (for example the model running out of its token budget, or a provider error) is not a suppression: the platform records it as failed, retries it, and never calls Dad Coach for it.

## Receiver

`POST /api/integration/workflow/scheduled-response` has the same contract as Big Boss's receiver.

**Request**

- Headers: `X-API-Key: <WORKFLOW_PLATFORM_CALLBACK_API_KEY>` and `X-Idempotency-Key: scheduled-response:{triggerId}`.
- Body: `{triggerId, workflowInstanceId, userId, channel, targetStateKey, responseContent}`, where `userId` is `whatsapp:+E164`.

**Idempotency**

- There is one `scheduled_response_delivery` row per idempotency key, enforced by a UNIQUE constraint.
- A repeated or concurrent callback replays the recorded outcome and never sends twice.

**Delivery**

Delivery goes through `DeliveryService`, and follows WhatsApp's 24-hour customer-service window:

- **Window open:** the message is sent as free-form text.
- **Window closed:** the approved template named by `WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME` is sent. The generated message goes into its single body parameter `{{1}}`, flattened to one line.
- **Window closed and no template configured or approved:** nothing is sent, and the delivery is recorded as `FAILED` with reason `SESSION_CLOSED`.

**Window tracking**

Every inbound message from the father extends his window on his primary communication endpoint (`InboundSessionTracker`). This only happens while `DADCOACH_PROACTIVE_MESSAGES_OWNER=PLATFORM`; see below.

## Production prerequisite: approved Hebrew template

Dad Coach 3 needs a Meta-approved Hebrew proactive-message template before messages can reach fathers outside the 24-hour window.

1. Create and get approval in Meta WhatsApp Manager for a template.
   - Its name must end in `_he`, for example `dad_coach_update_he`.
   - Its body must contain exactly one variable, for example `{{1}}`, with surrounding fixed text as Meta requires.
2. Register it in `template_messages` with `language = 'he'` and `status = 'APPROVED'`.
3. Set `WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME` to that name.

Without this template, proactive messages to fathers who have not written in the last 24 hours remain unsent. They are recorded as `FAILED: SESSION_CLOSED` and are never sent as free-form text that WhatsApp would drop. This is intended behavior, not a bug.

## Proactive-message ownership switch

`DADCOACH_PROACTIVE_MESSAGES_OWNER` controls which side sends proactive messages.

- **`LOCAL` (default):** Dad Coach 2 behavior.
  - Dad Coach's own scheduled jobs send proactive messages as before.
  - The session window is not tracked on inbound messages.
- **`PLATFORM`:** Dad Coach 3.
  - These jobs record a no-op run and send nothing: morning reminder, pre-session reminder, stale-state re-engagement, inactivity nudge, weekly-goal prompt, and the 30-minute commitment reminder.
  - The missed-commitment status update still runs, but its message is not sent.
  - Weekly goal completion and belt promotions still run.

The worker key is one value per deployment, so switch all of these together.

## Enabling Dad Coach 3 (all switches together)

| Where | Setting |
|---|---|
| Workflow Platform | A worker (e.g. `dad_3`) whose default workflow is Dad Coach 3 |
| Workflow Platform | `WORKFLOW_SCHEDULEDRESPONSECALLBACK_ROUTES_0_WORKERKEYS=dad_3`, `..._BASEURL=<dad-coach base URL>`, `..._APIKEY=<shared callback key>` |
| Dad Coach | `WORKFLOW_PLATFORM_WORKER_KEY=dad_3` |
| Dad Coach | `WORKFLOW_PLATFORM_CALLBACK_ENABLED=true`, `WORKFLOW_PLATFORM_CALLBACK_API_KEY=<same shared key>` |
| Dad Coach | `WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME=<approved _he template>` (see prerequisite) |
| Dad Coach | `DADCOACH_PROACTIVE_MESSAGES_OWNER=PLATFORM` |

Leaving everything at its defaults keeps Dad Coach 2 behavior unchanged.

## Dad Coach 3 workflow configuration

The Dad Coach 3 workflow itself lives in [`dad-coach-3/`](dad-coach-3/):

| File | What it is |
|---|---|
| `dad-coach-3.workflow.yaml` | The workflow (states, prompts, tools, providers, transitions) - import with `POST /api/v1/admin/workflows/import`, then publish |
| `weekly_plan_context.provider.json` | Catalog definition of the `weekly_plan_context` HTTP context provider - create once with `POST /api/v1/admin/context-providers` (before importing the workflow) |
| `daily-check.schedule.json` | The workflow-level schedule (daily awareness check, 09:00 in the father's timezone) |

### `weekly_plan_context`

`POST /api/context/weekly_plan_context` is Dad Coach's authoritative view of the father's current Sunday-Saturday week, computed in his timezone: local now, week boundaries, this week's goal, coverage in minutes (credited completed minutes + still-valid scheduled minutes vs the target), every session with its phase (UPCOMING / IN_PROGRESS / AWAITING_CONFIRMATION / COMPLETED / CANCELLED / MISSED), today's and the next session, sessions awaiting confirmation, and the previous week's result read directly from its goal and sessions (independent of the Sunday finalization job).

The Workflow Platform calls it as a generic, DB-configured HTTP provider (no platform code). It sends the instance identity (`whatsapp:+E164`) as `config.phone`; the context API accepts channel-qualified identities. The output is shaped for the platform's prompt renderer, which shows nested objects one level deep, collapses lists of more than three items into a count and cuts values at 200 characters: every collection is a map of one-line entries, each session line ends with its `id=`.

The platform process needs `DAD_COACH_BASE_URL` and `DAD_COACH_API_KEY` in its **environment** (the generic provider resolves `${...}` from environment variables only).

### Session reminders

`schedule_quality_time` and `reschedule_quality_time` return `timers`: transition key -> UTC instant, computed by Dad Coach (`SessionTimerPlanner`):

- `session_morning_reminder` - 08:00 local on the session day (only when the session starts at 10:00 or later),
- `session_reminder_1h` - one hour before the start,
- `session_follow_up` - 30 minutes after the end;

timers already in the past are omitted. The workflow copies each entry into `schedule_state_transition` with `reference_type=quality_time`, `reference_id=<session id>`, and clears a session's timers with one `cancel_scheduled_transition` by reference when it is cancelled, rescheduled or completed.

Each timer enters its own small pass-through state - `SESSION_MORNING_REMINDER`, `SESSION_REMINDER_1H`, `SESSION_FOLLOW_UP` - which re-checks the session against `weekly_plan_context` when it fires (a session cancelled, moved or completed since, including outside the chat, is suppressed), sends at most one message and returns to `ACTIVE_COACHING`, where the father's reply is handled. The daily check targets `ACTIVE_COACHING` itself.

### Known limitation: returning from the pass-through states

The return to `ACTIVE_COACHING` is the agent's own `change_state` call in the same turn (a REQUIRED rule in each state), not a deterministic platform step. It has held in every test, but if a turn fails for good (all retries) or the model skips the call, the instance stays in the pass-through state: the father's next message is handled there (each state then returns him), and session timers - which fire only from `ACTIVE_COACHING` - wait or fail until it returns. The daily check moves him back to `ACTIVE_COACHING` from any state. Big Boss's scheduled summary/check-in states have the same shape. The generic fix belongs in the Workflow Platform: a transient scheduled state that the platform returns from deterministically after its execution (follow-up task).
