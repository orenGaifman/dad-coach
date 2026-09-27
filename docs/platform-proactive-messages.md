# Workflow Platform proactive messages (Dad Coach 3)

Dad Coach 3 is a Workflow Platform workflow that writes its own proactive messages: session reminders, post-session follow-ups, the Sunday check-in and planning nudges. The platform schedules and executes them. When one produces a user-facing message, the platform calls Dad Coach, and Dad Coach delivers it over WhatsApp.

A scheduled execution whose agent decides nothing should be sent is suppressed on the platform side, and Dad Coach is not called at all.

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
