# Dad Coach — DECISIONS

Numbered, append-only. A later decision may supersede an earlier one; the earlier text stays. Parallel work streams
append in their branches; the main session merges and renumbers on collision (playbook §53).

## D-001 Platform-only (2026-10-07)
Dad Coach runs only on the AI Workflow Platform (worker `dad_3`, workflow `dad-coach-3`). The local AI, the
LOCAL proactive owner, the stub engine and the legacy trigger paths are deleted. The product calls no model.
A deployed instance refuses to start with `WORKFLOW_PLATFORM_ENABLED=false` (ProductionStartupGuard).

## D-002 WhatsApp is the only onboarding (2026-10-07)
The platform's ONBOARDING state onboards a new number; `save_user_profile` creates the father (ONBOARDING) with
his WhatsApp endpoint, his first `add_child` makes him ACTIVE. The web join wizard (invitations, onboarding
sessions, activation intercept) is deleted; its tables are dropped by V23. Production's one father (web wizard,
ONBOARDING) keeps his father, child and endpoint rows.

## D-003 Deleted subsystems (2026-10-07)
The memory system (memories, embeddings, pgvector), coaching, missions, the old scheduling, media, the dev API,
stub APIs and every tool handler/context provider not bound in dad-coach-3 are deleted. Activity ideas are the
static, age-banded catalog (`qualitytime/ActivityIdeas`). No ANTHROPIC/OPENAI key is needed.

## D-004 New dashboard (2026-10-07)
Tair-style React+Vite SPA in `frontend/`, Hebrew RTL, its own Render service (nginx, same-origin /api, cookie
session, CSRF double-submit). Father home + capability-gated admin with view-as one-to-one. The Vercel app is
retired after cut-over (WS-B).

## D-005 Login links (2026-10-07)
Tair's LoginLinkService pattern: short-lived single-use links, HttpOnly SameSite session cookie, logout /
logout-everywhere, ops bootstrap `/api/ops/login-links` with an ops key (WS-B). The JWT/magic-link stack is deleted.

## D-006 dashboard_url (2026-10-07)
Built from `WEB_BASE_URL`: `<WEB_BASE_URL>/login` — no father id, no token (it used to default to
`https://dadcoach.app`, a third-party site, with `?fatherId=`). Belt images default to `<WEB_BASE_URL>/belts`.
`app.dashboard.base-url` is gone; no `dadcoach.app` URL remains in the code.

## D-007 Google Calendar is optional (2026-10-07)
Verified first: production refused every booking without a connected calendar ("calendar not connected"), so the
whole core loop never started for a father without one (lab evidence). Now a session is stored in Dad Coach
either way; with a connected calendar a conflicting event refuses the slot and the event is created; a calendar
failure never undoes the booking (`calendar_event_created=false`, `calendar_error` in the tool result).
`show_available_slots` works without a calendar (D-013).

## D-008 Workflow configuration from YAML (2026-10-07)
`provisioning/config/*.yaml` provisioned by Tair's `provision_platform.py`; structured behaviorRules +
advancedBehaviorGuidance (WS-C).

## D-009 Tables of deleted features are dropped (V23, 2026-10-07)
Dropped (IF EXISTS): memories, memory, memory_versions, memory_audit_log, embedding_retry_queue,
safety_event_records, ai_telemetry, ai_profiles, message_templates, tool_wishlist, workflow_state_transition_log,
scheduler_job_log, conversation, mission, quality_time_commitment, calendar_sync_log, activation_records,
onboarding_sessions, invitations, invitation_audit_log, families, communication_preferences,
language_preferences, rate_limit_entries, magic_link, media_assets, delivery_records, api_audit_log; V19's
message_log AI-decision columns; pgvector when the database role may drop it (on a managed database it may belong
to the provider: then it stays, with a notice — never a failed deploy). Kept: father, child,
communication_endpoints, quality_time, weekly_goal, goal, message_log (no code writes it now; the platform owns
conversation history), scheduled_response_delivery, platform_person_deletion, template_messages.
The safety-event 7-year retention promise of SPEC-004 no longer applies: the feature never shipped data.

## D-010 Tool actor = the WhatsApp number in user_id (2026-10-07)
The platform's Dad Coach tool client (`DadCoachUserIdentityResolver`, platform source) strips the channel and sends
`user_id` = "+E164"; "whatsapp:+E164" is accepted too. A bare numeric father id is refused (400) — it let any
caller act as any father. The context client sends the number in `config.phone`; a numeric `user_id` is ignored.

## D-011 Building a database: production history + a V23 baseline for empty ones (2026-10-07)
V1-V9 had been deleted from the repository (4bccec1) and hidden with `ignore-migration-patterns: "*:missing"`,
so an empty database could not be built. Checked: the versions in git at 4bccec1^ have exactly production's
checksums (Flyway CRC32, recomputed and compared row by row), so they are restored byte-identical and the ignore
pattern is removed — production validates cleanly. Replaying V1-V22 on an empty database would need pgvector and
rebuild long-deleted features, so an EMPTY schema is created from `db/baseline/V23__baseline_schema.sql`
(production's schema-only dump with V23 applied) and baselined at 23 (`FlywayBaselineStrategy`); production has
a history and migrates normally. `MigrationTest` proves both paths end in the same schema (columns, indexes,
constraints), with production's real history and checksums. The baseline file is never edited.

## D-012 Durable idempotency in one table (2026-10-07)
`tool_idempotency` (V24, Tair/BB): scope TOOL:<key> for side-effecting tools (reserve → execute once in one
transaction → replay to the same actor; another payload or another actor → 409; an unexpected error releases the
key so the platform's retry runs again) and WHATSAPP_INBOUND for Meta ids (7 days). The in-memory duplicate
detector that answered retries with a cached reply is deleted.

## D-013 Available slots = family-time windows (2026-10-07)
Sunday-Thursday 17:00-20:00, Friday 09:00-13:00, Saturday 09:00-12:00 and 16:00-19:00 in the father's timezone,
minus his booked sessions and, with a connected calendar, its busy times; starts rounded to the quarter hour;
at most 20. (Before: 06:00-22:00 and only with a calendar.)

## D-014 Time already spent this week can be recorded (lab finding 7, 2026-10-07)
`schedule_quality_time` accepts a start in the past inside the father's current Sunday-Saturday week (timers are
empty, the session shows AWAITING_CONFIRMATION, `complete_quality_time` credits it); before this week →
ONLY_THIS_WEEK; more than 90 days ahead → TOO_FAR_AHEAD. No calendar call for a past session. No new tool.

## D-015 Father lifecycle (2026-10-07)
NOT_STARTED/ONBOARDING may go to DELETED (a deletion request is honoured at any stage — before, a father
mid-onboarding got "an operator will delete your data"). `FatherDeletionService` is the one domain door:
`requestByFather` (DELETED now, purge after the platform confirms) and `deleteByOperator` (purge now). Both feed the
`platform_person_deletion` outbox (person lifecycle below). The purge also removes his tool-idempotency rows.

## D-016 What is recorded into the platform conversation (2026-10-07)
Messages Dad Coach sends on its own are recorded via `/api/v1/worker/messages/outbound` (belt promotion).
Not recorded: replies to his own turn (the platform has them), scheduled-response deliveries (the platform wrote
them), and the deletion confirmation — recording it could re-create a conversation the deletion is removing.

## D-017 Security surfaces (2026-10-07)
Ordered chains (Tair): 1 `/api/tools/**` + `/api/context/**` (TOOL_API_KEY), 2 `/api/integration/channel/**`
(callback key, always; D-019), 6 `/api/integration/**` (callback key, closed when the receiver is off), 3 `/api/v1/admin/**` (DADCOACH_ADMIN_API_KEY), 4 `/webhook/whatsapp` (signature
in the controller), 5 the two calendar OAuth hops (HMAC in the controller), last: only `/actuator/health*`,
everything else 401. Orders 20-99 are left for the dashboard's chains. The calendar connect hop now actually
verifies its signature (it did not: anyone could attach a Google calendar to any father id). Production's
callback key is a reference to the tool key (`${tool-api.api-key}`); accepted for now (DC-A21 tracks a separate key).

## D-018 Person lifecycle (2026-10-05, moved from docs/decisions/person-lifecycle.md)
Dad Coach deletes a father through the platform's generic person lifecycle; the platform owns deleting its data
(person, conversations, messages, executions, scheduled turns); Dad Coach never touches platform tables.
Single tenant `20082bcd-a7bf-57a8-a382-4bad32144b2f`. Every turn sends the father's person ref
`new UUID(0, id)` (never his phone). Deleting writes an outbox row in the same transaction; after commit (and every
5 minutes) it registers the person INACTIVE under the ref, deletes it, then (self-service) purges his Dad Coach
data; failures retry with backoff 1, 2, 4 … minutes up to 6 hours, never dropped; from the 6th failure every retry
logs OVERDUE. Until the platform confirms, his number's messages never reach the AI and he gets no scheduled
message. Production: V22 applied 2026-10-04; JWT_SECRET and DADCOACH_ADMIN_API_KEY set on Render then.

## D-019 Gateway channel claim (2026-10-07, owner-approved; playbook §33)
`POST /api/integration/channel/claim {"phone":"+E164"}` → `{"claimed": true|false}`, Tair/Big Boss contract.
Claimed = a father with this normalized number exists, is not DELETED and his number is not waiting for its
platform deletion. Unknown/invalid/deleted = false (never a 4xx, never data). Auth: the platform-callback key on its
own chain (order 2), required even when the scheduled-response receiver is off; no key configured = 401 (fail closed).
Dad Coach still uses its own number today; the claim lets the shared gateway route it when that changes.
