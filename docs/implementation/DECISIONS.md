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


---

# Dashboard (WS-B, branch align-dashboard)


## WS-B (dashboard)

- **D-B01 Separate sign-in tables, one policy.** `staff_user` is the team (created only by `POST /api/ops/bootstrap-admin`); fathers sign in as themselves. A link/session row carries `father_id` and/or `staff_user_id`, so one person who is both signs in once and gets both areas (D-150: one home - `/admin` for the team, a menu link to `/home`). `SignInPolicy` is re-checked on request, consume and every call: a father may sign in unless PAUSED (deactivated by the team), DELETED, or his platform deletion is pending.
- **D-B02 Login links over WhatsApp go through `DeliveryService`.** Free-form inside the 24-hour window, else the scheduled-response template (`WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME`) if set, else FAILED (SESSION_CLOSED / TEMPLATE_UNAVAILABLE / ENDPOINT_NOT_FOUND) recorded on the link and listed in the admin's "undelivered". The page always answers the same and tells everyone "didn't get it? write to us in WhatsApp and try again". A staff user without a father row is sent through the WhatsApp channel adapter directly.
- **D-B03 The home is the AI's week truth.** `HomeService` reads every figure and every listed session (and its phase) from `WeeklyPlanContextBuilder.build` - session ids are read from the plan's lines; rows are loaded only for child and times. The sessions page (which reaches beyond this week) uses the same phase rule (`SessionPhases`), proven equal by a test.
- **D-B04 Web confirm/cancel use the AI tools' service methods; platform timers are not touched.** `completeQualityTime` / `cancelQualityTime` exactly as `complete_quality_time` / `cancel_quality_time`. The web cannot clear timers the AI armed on the platform; when such a timer fires, the workflow's PROACTIVE TURNS rule (globalPrompt) re-checks the weekly plan and answers [[SUPPRESS_RESPONSE]] for a session that is no longer valid - the suppression is a prompt rule (WS-C: keep it, and cover it in the qa-lab), not code. A web cancel also skips the coach's same-turn recovery offer; the next daily check sees the gap in coverage. Confirm is allowed from IN_PROGRESS/AWAITING_CONFIRMATION, cancel from UPCOMING/IN_PROGRESS/AWAITING_CONFIRMATION ("לא יצא").
- **D-B05 Deactivate = PAUSED.** Blocks dashboard sign-in, revokes every session and remembers the previous status (`father_deactivation`, V32) so reactivating restores it. It does NOT stop WhatsApp coaching (the platform person stays ACTIVE) - permanent delete is the way to stop everything. Permanent delete requires deactivation first and the typed confirmation (his name, else the phone's last 4 digits), then runs the existing admin delete: platform person-deletion outbox + local purge in one transaction.
- **D-B06 Self-service "delete my data" from the web is allowed from any status** (also ONBOARDING, which the WhatsApp phrase hands to an operator): he is signed in and typed "מחיקה". DELETED at once, sessions revoked, outbox with purgeLocal=true (purge after the platform confirms).
- **D-B07 Achievements are derived, not stored**: first session, a goal met, every child had a session (2+ children), a note shared, 2 and 4 weeks streak, 10 sessions - pictures from the brand set.
- **D-B08 Test database = production's schema.** The repository cannot build an empty database (V1-V9 are gone), so the shared Testcontainer starts from a schema-only dump of production at V22 plus its Flyway history (test resource `db/prod-v22-baseline.sql`; pgvector's column stored as `real[]`), and Flyway applies the newer migrations. Switch to an empty database once WS-A's migrations can build one.
- **D-B09 Cookies are Dad Coach's own** (`DADCOACH_SESSION`, `DADCOACH_XSRF`) - localhost cookies ignore the port, and Big Boss / Tair use `XSRF-TOKEN`.
- **D-B10 Admin API paths keep the brief's names** (`/api/admin/**`) but exclude the legacy `/api/admin/test/**` console, which keeps the admin-key chain.
- **D-B11 No PII in URLs**: the login page never takes `?phone=`; the token travels in the URL fragment (`/auth/consume#token=`), which no server logs.

## D-020 The shared number's claim includes site signups (2026-10-07)
The WhatsApp number is shared and invite-only: the platform gateway drops a sender no product claims. Dad Coach claims
a father it knows (not deleted, no deletion pending) and a phone that signed up on the site in the last 30 days - that
is how a new father reaches the coach. So the site's start is the signup (name + mobile) on every device, then
WhatsApp. A father's site signup is deleted with his data.

## D-021 One Dockerfile: backend/Dockerfile (2026-10-07)
Render's dad-coach service builds with root directory backend/. compose uses the same file.

## D-022 dad-coach-web (Vercel) retired by redirects (2026-10-07)
Every old address answers 308: legal pages to the site, the rest to the dashboard's /login, belt images to the
dashboard's /belts - old WhatsApp links and the Meta app settings keep working.

## D-023 Training videos on the owner's existing Bunny zone, under dad-coach/ (2026-10-07)
The catalog names dad-coach/training/v1/father-<slug>.mp4/.jpg; signed URLs (TRAINING_MEDIA_TOKEN_KEY) like Big Boss.

## D-024 Everything the father sees is Hebrew; the brand is "דאד קואץ׳" (owner, 2026-10-07)
The platform prefixes every reply with "<emoji> <worker name>:"; a Latin first line can make WhatsApp lay a Hebrew
message out left to right. The worker's display name, the coach's self-name, the dashboard, system messages, the site
and every video frame use "דאד קואץ׳" (Hebrew geresh ׳). The one narrator is a male voice (amit) - the audience is fathers.

## D-025 The site is impressive and humorous, and true (owner, 2026-10-07)
Humor from the real situation (the plan that slides to "next week", the excuse graveyard, silence as a feature,
messages it will never send, the belt dojo); the coach's bubbles stay real lab output; no testimonials or stats.

## D-026 Session buttons: "dc:<action>:<session id>", handled before the AI (2026-10-07)
Like Big Boss's follow-up buttons (fu:<action>:<task id>). Every id starts with "dc:" - the shared number's gateway
routes a tap with that prefix back to Dad Coach (ROUTES_1_BUTTONPREFIXES=dc:), whatever product the phone talked to
last. Two timer messages carry buttons, only inside the 24-hour window (the generic template carries none):
- the follow-up after a session (SESSION_FOLLOW_UP): [היה מעולה] dc:done:<id> · [לא יצא] dc:missed:<id> - the most
  recently ended session still awaiting confirmation;
- the one-hour reminder (SESSION_REMINDER_1H): [רוצה רעיונות] dc:ideas:<id> - the next session that has not started.

The callback names only the state, so the session is picked the way the weekly plan context picks it. A tap:
- done: completes the session here (the complete_quality_time path), and a fixed confirmation with the week's minutes
  goes back. A second tap says it's already recorded. No AI turn.
- missed: the coach's turn runs with the text "לא יצא" (its rules record it and offer another time).
- ideas: 3 ideas for that child that fit the session, sent here. No AI turn.
- a session that isn't his, cancelled, gone or not started: one fixed line. A "dc:" id this version does not know:
  the title goes to the coach.

Every fixed reply starts with "❤️ דאד קואץ׳:" and is recorded in the platform conversation (/messages/outbound), so the
coach knows about it on the next turn.

## D-027 The dashboard is a button that always works (owner, 2026-10-07)
Owner: no link valid for minutes, no phone number to type - whoever asks for the dashboard gets a nice button that
always works, like Big Boss. Production showed why: "תן לי דשבורד" got a bare `/login` URL and "sign in with your phone".
- The coach has a tool, `dad_dashboard_link` (platform V115, a generic HTTP tool like Tair's `tair_dashboard_link`),
  bound in ACTIVE_COACHING and ONBOARDING. It sends the button as its own WhatsApp message (cta_url: "📊 הדף שלך
  בדאד קואץ׳" + [כניסה לדף שלי], footer "הכפתור אישי, לא להעביר הלאה") and returns only a note - the token never
  reaches the platform or the model. The YAML says: call it, one short line, never write a link (not even dashboard_url).
- Every sign-in link Dad Coach sends (the tool, the login page's request, the ops API) is reusable for 365 days
  (`LoginLinkService.LINK_TTL`): not spent on use; each use re-checks the sign-in policy and opens a normal session;
  use counted (V33: last_used_at, use_count, revoked_at). "Log out everywhere", deactivation and deletion revoke the
  person's links with his sessions (SessionService); deletion also cascades. Only the hash is stored, as before.
- Budget: 5 links per person per 15 minutes (was 3) - a reusable link rarely needs a second one. A sixth request from
  the coach tells him to tap the recent button.
- Delivery: the button inside the 24-hour window; a button Meta refuses for good (4xx) or one over Meta's limits goes
  out as text with the link on its own line; outside the window the general template carries the link (a dedicated
  template with a URL button is drafted in marketing/whatsapp-templates.md).
- The dashboard: `/login` leads with "write 'דשבורד' to the coach" (wa.me), the phone form only behind a link; a button
  that no longer works sends someone signed in on that device to his page, anyone else to that same short way.

## D-028 A father lands on his own page; the admin is in the account menu (owner, 2026-10-07)
Like Big Boss: a person with a father profile - also when he is on the team - lands on his page (`/`, after sign-in);
the account menu (his name / avatar in the top bar) has "ניהול דאד קואץ׳", and in the admin "הדף שלי" back. A team
member who is not a father lands on the admin (`homeFor` in frontend/src/lib/session.ts).

## D-029 The coach hears WhatsApp voice notes - ElevenLabs writes down the words; on/off in the admin (owner, 2026-10-07)
The owner: "the ability to hear a recording exists in Big Boss; put it in Dad Coach too, with the ability to turn it
off in the admin, exactly like in Big Boss" (Big Boss D-176, same code shape, same env var).
- **Until now** a voice note got "אני עדיין לא יכול לשמוע הקלטות או לראות קבצים 🙏 אפשר לכתוב לי במילים?". Now, when
  voice notes are on: the parser keeps the note's Meta media id (`InboundMessageDto.mediaId`); `VoiceNotes` downloads
  it (`GET <WHATSAPP_API_BASE_URL>/<version>/<media-id>`, then the file, with `WHATSAPP_ACCESS_TOKEN` - the shared
  number's gateway forwards audio unchanged) and sends it to ElevenLabs speech to text (`POST /v1/speech-to-text`,
  `scribe_v2`, Hebrew, no sound tags, no timings). The words then go through everything typed text goes through: the
  deletion phrase (a spoken "מחק את המידע שלי" never deletes - deleting cannot be undone, so he is asked to type it), the rate limit (before the transcription, so a flood
  spends no credit), the father's own turn (message type `text`). The coach reads them after a Hebrew note -
  `[הודעה קולית, תומללה אוטומטית - שמות ומספרים עלולים להישמע לא נכון]` - Hebrew so it does not invite English (D-024).
- **What the father sees:** the reply opens, under "❤️ דאד קואץ׳:", with `🎙️ שמעתי: "..."` (up to 200 characters), so a
  misheard word shows at once - also on the "משהו השתבש" line. Signed fixed lines, no AI turn: no words - "לא שמעתי מילים
  בהקלטה - נסה שוב, או כתוב לי במילים 🙏"; over 3 MB (about 25 minutes, refused before downloading) - "ההקלטה ארוכה מדי
  בשבילי..."; any failure (Meta, ElevenLabs, out of credit) - "לא הצלחתי לשמוע את ההקלטה - אפשר לכתוב לי במילים? 🙏".
  Session-button taps, photos and files are unchanged.
- **On/off:** a new `system_setting` table (V43, key/value, empty = defaults) holds `voice_notes.enabled`; no row = on.
  The admin's "אינטגרציות" screen has a "הודעות קוליות" card: the switch (`PUT /api/admin/integrations/voice-notes`,
  the team only - a father gets 403 `NOT_STAFF`, a write needs the CSRF echo), whether an ElevenLabs key is set (never
  the key), the last note heard and the last failure in Hebrew (out of credit `quota_exceeded`, a key id set instead
  of the key `api_key_id_used_as_api_key`, a refused key, ElevenLabs slow, the Meta download) - since the last restart
  (in memory, like Big Boss). Off, or no `ELEVENLABS_API_KEY`: the old line, unchanged.
- **Privacy and cost:** the audio is held in memory for the request and sent only to ElevenLabs; Dad Coach stores
  neither the audio nor a copy - the words reach the conversation like typed text. Logs carry sizes and timings, never
  the words. The ElevenLabs plan is the owner's (shared with Big Boss and Tair): when its credit runs out ElevenLabs
  refuses and fathers are asked to write until it renews - the admin card says so.
- **Tests:** `VoiceNoteHttpTest` (Meta lookup under the Graph version then download, both with the token; over the
  limit not downloaded; Meta refusal / unreachable; the ElevenLabs multipart request and key; `quota_exceeded`,
  `api_key_id_used_as_api_key`, a 503), `VoiceNotesTest` (heard, off by switch or missing key, silent, too long is not a
  failure, failures by safe code, unexpected errors), `VoiceNoteRepliesTest`, `WhatsAppMessageParserVoiceTest`,
  `VoiceNoteWebhookTest` (signed webhook end to end on FakeServers: heard -> the turn and the echo; switch off; out of
  credit; too long; silent; Meta 404; platform down; spoken deletion; typed text untouched), `VoiceNotesAdminTest` (on by
  default, off and on again, 400, a father / a visitor / no CSRF refused, the key never in the answer). Local check with
  the real ElevenLabs: a Hebrew note (macOS Carmit, ogg/opus 16 kHz) was written down word for word in 2.1 s.

## D-030 Belts come only from sessions; one name per thing (2026-10-08)
- **Belts:** `Belt.fromCompletionCount` on every completed session is the only rule (site, dashboard and coach all say
  so). The Sunday weekly completion still promoted one belt per met week - the old 7-week program - so a father with 4
  sessions could jump to ORANGE (10) and drop back on his next session, and the message spoke of "סיימת את התוכנית",
  "עוד 5 שבועות", "🔥 רצף" and "אתה אבא מדהים!". `WeeklyGoal.complete()` no longer moves the belt (status and streak
  only); `BeltPromotionNotifier`'s text is calm and factual if it is ever used. `BeltPromotionTest` pins it.
- **Names:** the dashboard is "הדף האישי" (as "כניסה לדף שלי" on WhatsApp and the menu), a booked session "מתוכנן",
  a confirmed one "היה", "יומן גוגל"; the deletion phrase "מחק את המידע שלי" everywhere, one email subject.
- **Site claims fixed to the product:** a cancellation never lowers a belt but can break the weekly streak; no
  "replaced by" view; children by message or on the page; no stop word exists (silence does not stop the Sunday and
  goal invites - see the open item); the footer starts with the signup because an unclaimed number is dropped.

## D-031 On the shared number, a message for a father who is on another product waits (owner, 2026-10-08)

- **Problem:** the owner was on Dad Coach in WhatsApp and still got Tair's and Big Boss's 08:00 messages; the platform's
  gateway only routed what people wrote, and each product sends on the shared number itself.
- **Decision:** before every send `WhatsAppApiClient` asks the platform (`SharedNumberGate`, `POST
  /api/v1/worker/whatsapp/outbound-gate` with the worker key and the Meta body). When the father is on another product
  the platform keeps the message and sends it when he moves to Dad Coach (picker, `@dadcoach`, a `dc:` button, writing
  to it); Dad Coach treats it as sent (message id `held:<n>`). Owner's choices: kept until he switches (not dropped),
  for everyone who belongs to several products. A first message (no Dad Coach conversation yet) is never held. Any
  failure to ask sends as before; `dadcoach.whatsapp.shared-number-gate=false` switches it off.
- **Tests:** WhatsAppApiClientSharedNumberTest, SharedNumberGateIntegrationTest (FakeServers answers the gate); suite
  248/248. Platform side: ai-workflow-platform V119, docs/whatsapp-gateway.md.
