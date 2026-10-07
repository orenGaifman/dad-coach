# Dad Coach — Integration Spec
Source-verified platform: ai-workflow-platform origin/main d571a09 (2026-10-07) — tool/context clients
(`integration/dadcoach/*`), TenancyResolver, worker execute/outbound APIs. Backend: branch align-backend.

## Product
**Goal:** a father turns his week into real quality time with his children — a weekly goal in hours, short
booked sessions per child, reminders and a follow-up, belts for weeks he meets.
**Actors:** the father (WhatsApp, later the dashboard); the coach (the platform's agent, workflow dad-coach-3);
operators (admin API, later the admin dashboard).

## Tenancy & identity
- Single fixed tenant `20082bcd-a7bf-57a8-a382-4bad32144b2f` (the platform knows Dad Coach as single-tenant;
  every turn names it).
- External id `whatsapp:+E164`; person ref = `new UUID(0, father.id)`; one normalization point (E.164, Meta's
  `from` gets its "+"); `father.phone` unique in the DB; `communication_endpoints (channel, channel_identity)` unique.
- Roles: father only (operators use keys; dashboard roles: WS-B).

## Data
Product-owned: father (profile, timezone, belt/streak counters, Google tokens), child, quality_time (sessions),
weekly_goal, goal (legacy, kept), communication_endpoints (WhatsApp window), template_messages, scheduled_response_delivery,
platform_person_deletion (outbox), tool_idempotency. Platform-owned: persons, conversations, messages, executions,
timers. External: Google Calendar events (optional mirror).

## Provisioning
- Tenant: fixed, pre-created on the platform. Person: created by the platform on the first turn (lazy), carries the
  person ref from the turn after the father exists; deletion registers it under the ref first.
- Workflow config: `docs/dad-coach-3/` → `provisioning/config/*.yaml` (WS-C, D-008).

## Workflows
- Selection: always worker `dad_3` + workflow `dad-coach-3` (caller selects; WORKFLOW_PLATFORM_WORKER_KEY/WORKFLOW_KEY).
- States: ONBOARDING → ACTIVE_COACHING; one-shot reminder/follow-up states (session_morning_reminder,
  session_reminder_1h, session_follow_up), daily check (schedule "Daily coach check" 09:00 INSTANCE).
- Initial context: `metadata.timezone` = the father's zone (default Asia/Jerusalem) on every turn — it seeds a new
  instance's `workflowContext.timezone`.
- AI decides: wording, when to propose/confirm, which tool. Deterministic (product): the week (Sunday-Saturday in
  his zone), coverage numbers, reminder instants (SessionTimerPlanner), slots, belts/streaks, idempotency, deletion.

## Capabilities
Tools (`POST /api/tools/{key}`, TOOL_API_KEY, envelope {execution_id, idempotency_key, user_id, parameters};
answer {success, data, error_code, error_message}):
| key | side effect | service |
|---|---|---|
| save_user_profile | creates/updates the father (+ endpoint) | FatherService |
| add_child | child; first child activates him | ChildService |
| schedule_quality_time | session (past within this week allowed), calendar event if connected | QualityTimeService |
| reschedule_quality_time | cancel + book, one transaction | QualityTimeService |
| cancel_quality_time | session cancelled, calendar event removed | QualityTimeService |
| complete_quality_time | completed, credits the week, streak, belt | QualityTimeService + WeeklyGoalService |
| set_weekly_goal | this week's goal (create-once) | WeeklyGoalService |
| show_available_slots | read-only | AvailableSlotFinder |
| get_activity_ideas | read-only | ActivityIdeas |
Session/goal results carry `week_coverage`; session results `timezone, local_date, local_start, timers`.
Platform-side: these keys are registered by the platform's `HttpToolRegistrationConfig` (Dad Coach HTTP tools);
a schema change is a platform change + YAML binding (cross-repo).

Context providers (`POST /api/context/{key}`, same key, father by `config.phone`): `family_context` (profile,
children with child_id), `weekly_plan_context` (now, current_week, goal, coverage, sessions with phases and ids,
previous week, goal history, progress, calendar_connected, dashboard_url). Unknown father = 200 with
father_found=false / empty profile; failure = success:false.

## Scheduling
- Platform-owned: session reminders/follow-up (armed by the agent from the tool's `timers`), daily check.
- Product-owned: weekly completion (Sunday 06:00 UTC) → belts + promotion message.
- Timezone: the father's (`father.timezone`), fallback Asia/Jerusalem.

## Channels
- WhatsApp on Dad Coach's own number (not the shared gateway): webhook `/webhook/whatsapp` (verify token handshake,
  HMAC-SHA256), Graph API send. 24-hour window from `communication_endpoints.session_*` (every inbound extends it);
  outside it the approved template `WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME` (body `{{1}}`) or no send.
- Meta templates: one utility template carrying the message (catalog in template_messages); submission by the owner.

## Integration
- Dad Coach → platform: `POST /api/v1/worker/execute`, `POST /api/v1/worker/messages/outbound`,
  `PUT /api/v1/tenancy/people`, `DELETE /api/v1/tenancy/tenants/{t}/people/{ref}` (WORKFLOW_PLATFORM_API_KEY).
- Platform → Dad Coach: tools, context (TOOL_API_KEY), scheduled-response callback
  `/api/integration/workflow/scheduled-response` (WORKFLOW_PLATFORM_CALLBACK_API_KEY,
  `X-Idempotency-Key: scheduled-response:{triggerId}`); gateway claim `/api/integration/channel/claim` (same key, D-019).
- Reconfiguration: timezone change = save_user_profile (next new instance only — existing instances keep theirs).

## Lifecycle
Father: NOT_STARTED → ONBOARDING (first profile save) → ACTIVE (first child); PAUSED/CHURNED/REACTIVATED kept for
the dashboard; DELETED from any stage. Deletion: D-015/D-018 (outbox to the platform's person DELETE, purge).

## UI
WS-B (D-004/D-005): father home + admin; operator API `/api/v1/admin/fathers` meanwhile.

## Security
Keys per surface (D-017), constant-time compare, fail closed; HMAC calendar links; ProductionStartupGuard;
no secrets in logs (phones masked, ids only).

## Resilience
Platform client: breaker + 2 retries on 5xx/connection, none on timeout. Inbound dedup durable; tool idempotency
durable; callback idempotent by trigger; deletion outbox with backoff; Meta send failures logged
(`whatsapp.delivery.result`), not retried automatically.

## Operations
Structured events (whatsapp.turn.timing, workflow.call.result, tool.execution.result, context.provider.result,
proactive.callback.result), X-Correlation-Id per request. Tests: `./mvnw test` (119). Deploy: DEPLOYMENT.md.

## Open items
- DC-A21 separate callback key; DC-A22 calendar-connected path test; DC-A27 platform catalog cleanup of deleted
  tools/providers; DC-A28 workflow guidance vs D-007; DC-A29 delivery ledger + undelivered view.

## Definition of Done (§58) — backend status
Business APIs/rules tested without AI ✔ · Spec/TASKS/DECISIONS ✔ · Tenancy (single tenant) ✔ · Identity (one
normalization, DB uniqueness) ✔ · Auth: service keys + fail closed ✔ (callback key shared, DC-A21) · Tools:
service methods, auth, idempotency ✔ · Context: schema, empty = 200 ✔ · Scheduling ownership ✔ · Proactive
delivery idempotent + window/template ✔ · WhatsApp signature/dedup/serialization/window ✔, undelivered view ✗
(DC-A29) · Deletion via outbox ✔ · Observability ✔ · UI/launch kit: other streams.
