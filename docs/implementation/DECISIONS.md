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

## D-032 One message standard: identity line, one fact per line, the week in hours (owner, 2026-10-08)

- **Problem:** the same fact had many shapes: the week in minutes from the coach ("60 מתוך 180 דקות") and in hours from
  the code ("שעה מתוך 3 שעות"); a 50-word welcome; ideas both inside the one-hour reminder and again behind its button;
  fixed messages without the identity line (deletion, voice, media, "משהו השתבש"); a stale button that blamed a
  cancellation when the session simply had not started; and outside the 24-hour window one flattened line that repeated
  the brand. The owner approved a standard for all three products (before/after page, 2026-10-08).
- **Decision (Dad Coach voice: a short chat with a friend, not cards):**
  - Every message opens with `❤️ דאד קואץ׳:` - added to the deletion replies, the spoken-deletion reply, the media
    reply, the platform-down reply and the belt text. At most one emoji: 🙂 reminders/openings, 🎉 booked, 💪 progress.
  - The week is told in HOURS everywhere. `HebrewHours` turns minutes into the words ("שעה וחצי", "3 שעות ורבע");
    the weekly plan's `coverage.in_hours` and `previous_week.in_hours` carry the ready phrases (also in every tool's
    `week_coverage`), and the prompt tells the coach to copy them, never to convert.
  - A session's day and time are bold (`when_label`). Booking confirmation, progress ("היה / מתוכנן / חסרות" + one
    concrete offer), cancellation (what was cancelled + one slot + yes/no) and the welcome list are fixed shapes in the
    prompt (dad-coach-3 ONBOARDING, ACTIVE_COACHING, SESSION_MORNING_REMINDER, SESSION_FOLLOW_UP).
  - Ideas come only from the [רוצה רעיונות] button: the one-hour reminder is two lines and its state no longer has
    `get_activity_ideas`. The ideas reply is three short lines with the child's name (`ActivityIdeas.line`, the verb in
    worded without a gendered verb - "בצבעים לבחירת מאיה"), no minutes, ending "תספר לי אחר כך איך היה 🙂".
  - A button that no longer works says the true reason, one fact per line (not his / cancelled or moved / missed /
    not started yet / ended).
  - Deletion is in the coach's voice with a list of what is deleted; the email address, the subject and the phrase to
    type stand on their own lines so they can be copied. A captionless picture while voice notes are on is no longer
    told that recordings cannot be heard.
  - Outside the window the general template takes the message as one readable line: the message's own identity line is
    dropped (the template body starts with it) and lines become sentences (`ProactiveSender.asTemplateParameter`).
    Nothing else about delivery changed. The template `dad_coach_update_he` is redrafted in
    marketing/whatsapp-templates.md for the owner to submit; until it is approved, configured
    (`WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME`) and registered as APPROVED in `template_messages`, proactive messages to
    a father silent for 24 hours are still not sent.
- **Site:** the demo (marketing/whatsapp-demo-flow.md, site/src/demo/scenarios.js) shows the new bubbles, *bold* and the
  reply buttons.
- **Tests:** ProactiveSenderTest, ActivityIdeasTest (new); SessionButtonsTest, WhatsAppWebhookTest,
  VoiceNoteWebhookTest, WeeklyPlanContextBuilderTest, ScheduledResponseCallbackTest updated; provisioning
  test_manifests / test_provision_publish / test_provision_workers.

## D-033 — The button to his page is the whole answer (2026-10-08)

- **Why:** the owner saw two messages for one request in Tair ("📊 לוח הבקרה" card, then "✔ שלחתי לך כפתור..."); Dad Coach did the same (the coach wrote a free "it's on its way" line under the button).
- **Decision:** `dad_dashboard_link` returns a fixed `reply` line (`DashboardTools.SENT_REPLY`) and the prompt says to reply exactly with it when he only asked for his page; when the button went out in this turn (a SENT `login_link` row since the turn started) and the reply is only that line, `InboundMessageHandler` drops it (outcome `DASHBOARD_CARD`). A reply with more in it (a child to fix, connecting the calendar) still goes; so do rate-limited and failed sends.

## D-035 — No "sent you a button" line, and no second card under the first (2026-10-08)

- **Why:** in prod (12:46) "הייתי לי דשבורד" got the card and then "שלחתי לך כפתור לדף שלך 😊" - the coach dropped "בהודעה נפרדת", so D-033's exact match let it through. A minute later "מה זה הכפתור הזה?" got the same card again under the explanation. Owner: "לא צריך את ההודעה הזאת שלחתי כפתור".
- **Decision:** when the card went out in this turn, a reply whose every word is a "sent / button / your page" word (`InboundMessageHandler.SENT_LINE_WORDS`) is dropped, in any wording; a reply with anything else in it (fix a child under ילדים, the calendar) still goes. `dad_dashboard_link` sends no new card when one went out in the last 10 minutes (`LoginLinkService.ON_SCREEN`, outcome `ALREADY_SENT`) and returns a fixed line that says what the button is. The prompt (`dashboard-by-button` rule, ACTIVE_COACHING, onboarding) answers "what is this button" in one line without calling the tool.
- **After deploy (prod simulate, 10 runs):** the coach wrote "שלחתי לך את הכפתור" without calling the tool 7 times in 10 (he copies his earlier replies) - no card, a false line. So a "sent" line with no card behind it in this turn sends the card from `InboundMessageHandler` (`LoginLinkService.sendTo`) and the line is dropped; a card still on his screen gets the one line about it instead. What he says about the button is always true.

## D-036 — Facts and confirmations are written in code; a reply confirms only what really happened (owner, 2026-10-08)

- **Why (production review 2, 2026-10-08):** the coach confirmed things no tool did ("מעולה, קובע את זה - יום שישי
  09:00-10:30 עם מטר ונעם 💪" with nothing booked; "רשום אצלי" for an activity nothing stores; "רק רגע ונמשיך משם"
  and nothing came), invented reminders ("ועוד אחת ב-17:00"; "אעדכן אותך בבוקר" for a 09:00 session that has no
  morning reminder), confirmed next week's goal that nothing stored, built "מה יש לי השבוע" from English weekdays and
  minutes, repeated today's session in 12 of 19 replies, pushed the missing goal 4 times in 7 minutes, used 😊 👍 ✅ 🙏
  🎮 and " - " everywhere, never told a belt earned with "היה מעולה", and stayed silent after an English reply.
  Owner: every message rests on current data; an action is confirmed only after it really happened.
- **Ready answers from data** (`com.dadcoach.replies.CoachReplies`, the Tair D-041 pattern): every session / goal /
  child tool returns `reply` - the confirmation, built from the real result: booking (`קבעתי 🎉 *when_label*, length
  עם children.` / only the reminders its timers really have / the week in hours), move, cancel, missed, completion
  (a belt only when one was earned now), a child added, the goal. The weekly plan carries `ready_replies`
  (week_reply, progress_reply, reminder_reply, greeting_reply and the three timer messages, as lists of short lines -
  a text value is cut at 200), `upcoming_reminders`, `when_label` on every session line (no English weekday),
  Hebrew belt names, `sessions_mentioned_today` and `goal.asked_today`; `dashboard_url` is gone (the page is a
  button). The prompt: these lines are the answer, word for word (rule `ready-answers`).
- **Whole-message fact questions before the AI** (`ReadyQuestions`): "מה יש לי השבוע?", "איך אני עומד?", "מתי
  התזכורת?" are answered from this moment's data and recorded in his conversation - the lab showed the model listing
  a session he had cancelled on his page, from its own history.
- **The check before sending** (`ClaimGuard` + `TurnLedger`): his sessions, children and goal are read before and
  after the turn; a booking / cancellation / "רשמתי, שמרתי, עדכנתי" / goal / "אזכיר לך" sentence with no matching
  change is dropped and an honest line is said ("רק מוודא: *יום שישי ב-09:00*, שעה וחצי עם מטר ונעם." / "לקבוע?";
  "את זה אין לי איפה לשמור."); "רק רגע / אחזור אליך / אעדכן אותך" never goes; a booking made this turn goes out as
  the ready confirmation; a "sent you the button" sentence inside a longer reply sends the card (D-035 covers the
  whole-line case). The corrected text is recorded in his conversation.
- **Next week's goal is stored** (V44 `weekly_goal.next_week_target_hours`): `set_weekly_goal` with a different number
  while this week's goal exists keeps this week's goal and saves the number for next week (was: refused); Sunday's
  check-in offers it (`goal_history.asked_for_this_week_hours`). No tool input schema changed.
- **Once a day** (V44 `father.goal_asked_on`, `quality_time.mentioned_on`, `CoachMentions`): what went out today is
  noted; a session named today is not named again, a missing goal is raised at most once a day (rules
  `session-once-a-day`, `goal-once-a-day`); a greeting is answered as a greeting.
- **Timer messages** (`ScheduledReplies`): the morning reminder, the hour before and the follow-up are the ready
  lines (the model's own words replaced; the hour-before may end "בהצלחה עם <activity>!" when he named it today -
  D-15); a timer whose session was cancelled, moved or done sends nothing. Their templates are D-11's (another
  session, PR #23); the three default lines are unchanged.
- **The standard in code** (`ReplyStyleGuard`, on every reply and scheduled message): vocabulary 🙂 🎉 💪 📊 🎙️ only
  (😊→🙂, 👍✅🔥→💪, others dropped), one emoji per message, emoji list items → "•", " - " between words → ", "
  (time and number ranges untouched).
- **English is never silence** (B-4): the coach rewrites once in Hebrew (a Hebrew system note, correlation id
  `:he`), then a short Hebrew line.
- **Page ↔ WhatsApp:** what he does on his page (a session confirmed or cancelled, a child added or edited, his name)
  is noted in his AI conversation as a history-only line "📊 בדף שלך: ..." (the Big Boss D-180 pattern); confirming on
  the page returns and shows the belt it earned, once. WhatsApp actions show on the page (one database).
- **Not done (needs the platform):** whether a session's timers were really armed is known only to the platform (the
  model arms them; there is no product API to read or arm them) - the reminder answer states the policy that the
  booking turn arms.
- **Tests:** CoachRepliesTest, ClaimGuardTest, ReadyQuestionsTest, ReplyStyleGuardTest, TruthfulRepliesTest (new);
  ToolsTest, JointSessionsTest, WeeklyPlanContextBuilderTest, SessionButtonsTest, FatherAreaTest,
  ScheduledResponseCallbackTest, ContextProvidersTest updated. Lab transcripts: qa-lab/transcripts/r2-*.md.

## D-037 — A reminder is promised only when the platform holds it (owner, 2026-10-08)

- **Why:** the booking confirmation ("אזכיר לך שעה לפני, ואשאל אחר כך איך היה.") and the answer to "מתי התזכורת?" were
  built from the timers Dad Coach PLANNED (`SessionTimerPlanner`). Whether the model then armed them
  (schedule_state_transition) was known only to the platform; a turn that skipped a call, or a platform that refused one,
  still promised the reminder. Owner: the bot never claims reminders were scheduled unless the platform confirms it.
- **Platform API** (platform TASKS.md §32, V120): `/api/v1/worker/scheduled-transitions` under the worker key - GET the
  conversation's PENDING triggers, POST arms one (same validation and same-reference dedupe as the model's tool; source
  `PRODUCT`), DELETE cancels by reference. `WorkflowPlatformClient.pendingTimers / armTimer / cancelTimers` (outside the
  breaker, 3 s / 8 s timeouts; any failure, including 404 from an older platform, means "cannot confirm").
- **After every turn that booked, moved or joined a session** (`InboundMessageHandler`, before the reply is sent - the
  turn has returned, so the platform's conversation lock is released; never from inside a tool call): `SessionTimers`
  reads the father's pending timers once, arms every planned timer that is missing or at another time, and returns what
  the platform confirmed. The ready confirmation's timers line is built from the confirmed timers only (`TimerClaims`):
  all - as before; some - only those ("אזכיר לך שעה לפני."); none (refused, down) - "את התזכורת למפגש הזה לא הצלחתי
  לקבוע הפעם."; several sessions not all confirmed - "חלק מהתזכורות לא הצלחתי לקבוע הפעם." / "את התזכורות למפגשים
  האלה לא הצלחתי לקבוע הפעם.". Every other reminder or follow-up promise in that reply goes. If the claim check itself
  fails, the reply goes without any reminder promise. Logs: `session.timers.confirmed` (planned, confirmed, armedNow,
  result), `session.timers.arm_failed`, `whatsapp.reply.timer_claims_corrected`.
- **Sessions closed in the turn** (cancelled, missed, done, or the old session of a move): their pending timers are
  cancelled on the platform after the turn (best effort; the timer turns already stand down for them).
- **"מתי התזכורת?" and the weekly plan:** `ready_replies.reminder_reply` and `upcoming_reminders` come from the timers
  the platform holds (`WeeklyPlanContextBuilder.build(father, ArmedTimers)`, one GET per context load / per question; a
  plain read, which does not wait on the turn's lock): the real times; "למפגש עם X לא קבועה כרגע תזכורת." when none is
  armed; "כרגע אני לא מצליח לבדוק את התזכורות שלך." / "לא ניתן לבדוק כרגע" when the platform cannot be asked. The
  question reads; it never arms.
- **Unchanged:** the model still arms the timers in the turn (the prompt and dad-coach-3 are not touched - the platform's
  dedupe makes the second arming a no-op, and an older platform keeps working); the tools' `reply` and `timers`; every
  WhatsApp template and the three timer messages; the page (it books nothing - booking stays in WhatsApp - and page
  cancels keep D-B04).
- **Tests:** TimerClaimsTest (all / some / none / none due / several sessions / align / the armed reminder answers),
  ArmedTimersTest (through the webhook with the fake platform's timers: armed after the turn, already armed, armed at a
  wrong time and moved, refused 409, partial 422, down 503 and older 404, the model's own promise replaced, a move moves
  the timers, "מתי התזכורת?" real time and platform down, weekly_plan_context), WeeklyPlanContextBuilderTest
  (remindersFromThePlatform), TruthfulRepliesTest (the reminder answer reads the platform). FakeServers holds the
  platform's timers in memory. Full suite 333/333 (after merging main 750f120).
- **Lab (real model, Hebrew, qa-lab/r3.py, transcripts r3-*.md):** a - the booking line "אזכיר לך בבוקר ושעה לפני,
  ואשאל אחר כך איך היה." matched the platform's three PENDING rows, and "מתי התזכורת?" gave their times; b - with the
  timer transitions refused by the platform: "את התזכורת למפגש הזה לא הצלחתי לקבוע הפעם." and "למפגש עם נועה לא
  קבועה כרגע תזכורת.", none pending; restored, the next booking promised them again; c - a move left only the new
  session's triggers, at the new times; d - with the 1-hour trigger missing, the answer named only the morning reminder
  and the follow-up. Run 1 (r3run1-*) found the platform answering a refusal with 500 instead of 409 (fixed there);
  the model armed every timer itself in all lab bookings. Seen, not D-037: the coach refused to add a second child to
  a session ("כל מפגש הוא עם ילד אחד") - joint sessions exist (r3-d).
- **Deploy order:** the platform first (V120 + the API), then Dad Coach. No re-provisioning.

## D-038 — Delivery reliability: wamids kept, Meta's receipts applied, a message is done only when answered (Phase 2, 2026-10-11)

- **Why:** the Unified Workflow Phase 1 baseline (Task 1.2, `backend/src/test/java/com/dadcoach/baseline/`) pinned four
  delivery bugs: the wamid Meta answers a send with was dropped (DC-B1), Meta's status receipts were parsed and thrown
  away (DC-B2), an inbound message was marked handled before it was processed so a redelivery of one the platform or
  Meta failed was dropped (DC-B3), and a retried turn the platform answered as a duplicate WITH the cached reply sent
  nothing (DC-B4). Spec: `docs/architecture/PHASE2_DADCOACH_SPEC.md`.
- **DC-B1:** V45 (additive) adds `provider_message_id`, `gateway_hold_id` (+ `status_at`) to
  `scheduled_response_delivery` and `provider_message_id`, `gateway_hold_id`, `receipt_status`, `receipt_at` to
  `login_link`, with partial indexes on the wamid. A message the shared-number gateway held (`held:<n>`) keeps that id
  apart and is never matched as a wamid. No outbound ledger for the other sends (conversational replies, fixed lines,
  belt promotions): nothing reads their receipts yet; `getDeliveryStatus` answers PENDING for them as before.
- **DC-B2:** `scheduled_response_delivery.status` distinguishes ACCEPTED (Meta's API took it) and HELD from SENT /
  DELIVERED / READ (receipts) and FAILED; DELIVERED now means a delivery receipt (rows before V45 keep DELIVERED = accepted,
  no wamid). `DeliveryReceipts` moves a row forward only (ACCEPTED < SENT < DELIVERED < READ; FAILED before delivery,
  final; one conditional UPDATE, race-safe), stores Meta's error as `META_<code>: <title>`. A link's
  `delivery_status` stays SENT through delivered/read (the coach's "already on his screen" check reads it) and turns
  FAILED on a failed receipt, so the next request sends a new button and the admin's undelivered list shows it.
  `WhatsAppAdapter.getDeliveryStatus` answers from the rows. A "failed" receipt that comes before its row is committed
  (Meta can answer within milliseconds) is kept in memory 2 minutes and re-applied every 15 s; it can still be lost on
  a restart or another instance (only "failed" is kept - a later receipt corrects a lost sent/delivered). The platform still hears `DELIVERED` for every message handed
  over (it reads only the HTTP status). Switch: `dad-coach.whatsapp.receipts.enabled` (default true).
- **DC-B3:** the webhook claims Meta's message id IN_PROGRESS (`IdempotencyService.claim`, atomic insert) and marks it
  done only when `InboundMessageHandler.handle` says HANDLED; UNANSWERED (platform unavailable after retries, a reply Meta
  refused, an exception) releases it, so the redelivery is processed. A message answered or being processed is dropped;
  a claim older than `dad-coach.whatsapp.inbound.claim-lease` (PT10M) is taken over. A platform refusal (4xx) is an
  answer, not a retry. An error after something already reached him (a fixed line, a ready answer, a button reply,
  the AI reply) counts as HANDLED, so a redelivery never sends it again. Per-sender order unchanged. A released platform
  timeout re-runs the turn with the same correlation id: that it does not book twice relies on the platform's
  duplicate-request check (`WORKFLOW_DUPLICATE_CHECK_ENABLED`, on by default, also in the platform admin's Settings),
  which answers it as a duplicate with the cached reply. Switch: `dad-coach.whatsapp.inbound.retry-unanswered` (default true).
  Limit: Meta redelivers only when it got no 2xx; this makes a redelivery processable, it does not create one.
- **DC-B4:** a duplicate answer with content is sent once: the durable guard `tool_idempotency(WHATSAPP_REPLY, wamid)` is
  taken before any reply to the message goes out and released only when that send failed (or the turn threw before
  anything was sent). A duplicate without content
  still sends nothing.
- **In passing:** `ScheduledResponseDelivery` takes its times from the injected clock.
- **Tests:** the 9 known-bug baseline tests run (no longer `@Disabled`); three PASS assertions in
  ScheduledDeliveryBaselineTest now expect the row ACCEPTED (not DELIVERED) after Meta's API accepted the send;
  WhatsAppWebhookTest `statusReceiptsAreAcknowledgedAndIgnored` renamed `...AndNeverRunATurn` (assertions unchanged).
  New: DeliveryReceiptsTest, InboundClaimTest, DeliveryFlagsOffTest.
- **Rollback:** the two switches (env `DAD_COACH_WHATSAPP_RECEIPTS_ENABLED`, `DAD_COACH_WHATSAPP_INBOUND_RETRY_UNANSWERED`)
  restore the old behaviour without a code change. A code revert keeps V45; once the revert is live, run
  `UPDATE scheduled_response_delivery SET status = 'DELIVERED' WHERE status IN ('ACCEPTED','HELD','SENT','READ')` (the old
  enum cannot load the new values), and once more a few minutes later for rows written during the switch-over.
- **Admin:** HELD reads "ממתין — האב בשיחה עם מוצר אחר"; a login link shows its receipt status next to its status.

## D-039 — The platform's conversation shows what the father got (Phase 3.4, 2026-10-11)

- **Why:** the platform's conversation (the model's history, the admin view, reviews) held the model's drafts: a reply
  that ClaimGuard / TimerClaims / ReplyStyleGuard changed, an English reply rewritten in Hebrew (and the rewrite note as
  if the father wrote it), a "שלחתי לך את הכפתור" line that never went, a timer message replaced by the ready one, a
  skipped timer, and none of the messages answered without a turn (taps, ready answers, files, unheard notes).
  Spec: `docs/architecture/PHASE3_DADCOACH_SPEC.md` (path -> platform call table); platform contract: implementation spec
  §3.2/§3.4/§3.8.
- **Switch:** `workflow.platform.delivery-reports` (env `PLATFORM_DELIVERY_REPORTS`, default false). Off = exactly the
  pre-Phase-3 calls and bodies (no `internal`, the `:corrected` record, every `SentMessageRecorder` record, callback answer
  `{status, detail}`). On after the platform's Phase 3 is live.
- **Turn replies:** sent as written (identity / heard lines aside) -> turn-outcome AS_IS with Meta's wamid; changed ->
  `/messages/outbound` `{cid}:delivered` with `replacesDraft` and exactly what was sent (no identity line), which replaces
  the `:corrected` record; the Hebrew rewrite runs `/execute` `internal: true` and the delivered text supersedes `{cid}:he`;
  the sent-line drop -> the card (recorded where it is sent, linked to the turn when the handler sent it) + DROPPED
  `SENT_LINE_CARD`; a DC-B4 duplicate reply is reported like a first one.
- **Not reported (decided):** a reply Meta refused (DC-B3 redelivers it; FAILED is final on the platform and would turn
  the redelivery's report into a 409); a platform failure on the first `/execute`; the typed deletion request (he is
  deleted now - a report could re-open a conversation); anything from a number with no father; reactions.
- **Code-answered:** `/messages/inbound` (his words, the button title + `{buttonId, title}`, or a marker `[photo]` /
  `[voice note]`...) before the product's `/messages/outbound` (kind BUTTON_REPLY / READY_ANSWER / FIXED_LINE).
- **Product sends:** dashboard card - its text and button title, never the link (DASHBOARD_LINK; the template form
  without the link); dashboard notes - kind DASHBOARD_NOTE with no provider id and no status (the platform keeps
  `delivery_status` null); belt promotion - BELT_PROMOTION with its wamid / template.
- **Scheduled callbacks:** the 2xx body adds `outcome` AS_IS (daily check as written) / DROPPED (`NO_VALID_SESSION`,
  `BLOCKED_NOT_HEBREW`) / FAILED (the send's reason), or `deliveredContent` (the ready message, the cleaned text, the
  template as rendered - the `{{1}}` line when the catalog has no body - with `template {name, params}` and the `dc:`
  buttons) + the wamid. A replayed trigger answers as before (what was sent is not stored per trigger).
- **Sender:** `TimelineReports` - 4 lanes, a father's reports always on one lane (in order); 3 attempts (1 s, 2 s; 5xx /
  408 / 429 / no answer) within 115 s per report; a turn-tied report (it waits on the platform behind an in-flight turn)
  gets 100 s and a timeout is `timeline.report_outcome_unknown`, never retried; others 10 s. Bounded queues
  (QUEUE_FULL / SHUTDOWN logged apart); `timeline.report_failed` when a report does not land (the draft then stays, as
  before). Never blocks a delivery, never throws to its caller (review follow-up).
- **Tests:** `PlatformDeliveryReportsTest` (25, contract against FakeServers, which answers `/messages/inbound` and
  `/messages/turn-outcome` additively); the switch is reset off before every test; full suite 398/398.
- **Rollback:** switch off (no code change).


## D-040 — A refused belt image never stops the belt promotion text (2026-10-11)

- **Why (code read):** `BeltPromotionNotifier` sends the optional belt image (window open, image configured) straight
  through `WhatsAppApiClient`, then the text through `ProactiveSender`. The client throws on a refused send (non-2xx,
  429, timeout) and returns null on an empty 2xx body, so the `!image.success()` branch never ran: the exception reached
  the method's catch-all and the promotion text was never sent (or reported).
- **Fix:** the image send is isolated (`sendBeltImage`): a throw, an unsuccessful or a missing answer is logged
  (`Belt image not sent (text follows)`) and the text follows exactly as before - same order (image, then text), same
  wording, same reporting (BELT_PROMOTION timeline report with `PLATFORM_DELIVERY_REPORTS` on, the legacy
  `SentMessageRecorder` record off). Schedule, wording and who is promoted unchanged (the job still promotes no one, D-030).
- **Not changed (decided):** the image still bypasses `WhatsAppAdapter` (its circuit breaker): the adapter's IMAGE path
  takes an uploaded media id (`mediaReference`), not a link, so routing it there needs a new link-image shape in
  `OutboundMessageDto` / the formatter for a path that is unused today. The shared-number gate already applies (it sits
  in `WhatsAppApiClient.sendMessage`). Consequence: an image failure does not count toward the breaker, and an image is
  tried while the breaker is open (the text then fails through the adapter as any other send).
- **Tests:** `BeltPromotionNotifierTest` (unit: throws / unsuccessful / null -> text sent and recorded; OK -> image then
  text; timeline on; a failed text still not recorded), `BeltPromotionTest` and `PlatformDeliveryReportsTest` (Meta
  refuses the image with HTTP 500 -> the text reaches Meta and is recorded / reported BELT_PROMOTION).

## D-042 — The shared number's held messages report back: sent, failed, unknown or expired (Unified Workflow Phase 6.1, 2026-10-11)

- **Why:** a scheduled coach message or a dashboard link the shared-number gateway held kept `held:<n>` forever: Dad
  Coach never learned whether it went out, and every Meta receipt for the replay was dropped (unknown wamid). Platform
  6.0 (`e05d846`, V130; spec `ai-workflow-platform/docs/architecture/PHASE6_SHARED_MESSAGING_SPEC.md`, "Spec v2" §v2.7)
  now POSTs the outcome of every held message it closes to a per-route URL with the route's claim key.
- **Endpoint:** `POST /api/integration/channel/held-outcome` (the `/api/integration/channel/**` chain opens with the
  callback key always - the key the platform already uses for the claim). Body `{heldId, workerKey, route, outcome,
  providerMessageId, errorCode, closedReason, latestStatus, latestStatusAt, latestErrorCode, heldAt, closedAt}`, header
  `X-Idempotency-Key: held-outcome:<heldId>:<outcome>:<latestStatus|->` (stored in `tool_idempotency`, scope
  `GATEWAY_HELD_OUTCOME`, 7 days; the same key again = 200 `duplicate`, being applied = 409). An id no row holds = 200
  `{"applied":false,"known":false}` (never retried). A wrong request = 400 (the platform gives up). Switch
  `GATEWAY_HELD_REPORTS` (`dad-coach.whatsapp.gateway-held-reports`, default **false**): off = **404**, nothing read or
  written. 404 rather than the spec's `{"applied":false}`: the platform retries a 404 (backoff up to 24 h), so a report
  sent while the switch was off is applied once it is on instead of being marked done and lost.
- **Apply** (`HeldOutcomes`, one transaction, rows by `gateway_hold_id = 'held:'+heldId`; V46 adds the two partial
  indexes; only a row still HELD moves, so an outcome is applied once and never replaced by another):
  - **SENT** → scheduled `ACCEPTED` + `provider_message_id` = the wamid; link `receipt_status` NULL (accepted) + wamid,
    `delivery_status` stays SENT. Then the latest status the gateway saw (`latestStatus`: ACCEPTED = Meta's "sent",
    DELIVERED, READ, FAILED) and any receipt Dad Coach kept for that wamid are applied through `DeliveryReceipts`, forward
    only - also on a later report for the same held id, but only to the wamid this held id was linked to. The platform
    sends such a second report only when a newer status reached its held row while the first report was in flight (the
    2xx is then not taken as done and the row is reported again at once, with the new status in its key); otherwise one
    report per held message. From then on Meta's receipts match as for any send. A SENT without a wamid = ACCEPTED without one.
  - **FAILED** (Meta refused the replay, or the request was never written) → scheduled FAILED, reason
    `GATEWAY_<errorCode>`; link `delivery_status` FAILED, `receipt_status` FAILED, `delivery_error` the same.
  - **EXPIRED**, defined: the gateway closed it **without ever sending it** (`closedReason` NOT_IN_LATEST_10 - not among
    the latest 10 when he came back - or TTL - held longer than 24 h). He never got it. → FAILED as above, reason
    `GATEWAY_HELD_EXPIRED_<closedReason>`. **Never re-sent** (whether to resend later, e.g. by template, is the owner's
    open decision O2; Dad Coach has no path that would).
  - **UNKNOWN** (5xx / timeout / connection lost after the request was written: Meta may have it) → **not failed**:
    scheduled status `UNKNOWN` (new enum value; VARCHAR, no CHECK), reason `GATEWAY_UNKNOWN_<errorCode>`; link keeps
    `delivery_status` SENT with `receipt_status` UNKNOWN. The spec's amendment mapped DC UNKNOWN to FAILED; not done: a
    FAILED link makes his next request send a second card that may duplicate one on his screen, and the admin would
    count a message that may have arrived as failed. Never re-sent.
- **Readers checked - applying an outcome never sends anything, and nothing re-sends on it:**
  - `scheduled_response_delivery`: `ScheduledResponseDeliveryService.deliver` replays ANY existing row of the trigger's
    key and never sends again; `ScheduledResponseResult.of` answers a replayed callback FAILED / DELIVERED (UNKNOWN is
    handed over) - the platform treats every 2xx as delivered and reads only `outcome` / `deliveredContent`, absent on a
    replay (`ScheduledResponseNotifier`, `ConversationTimelineService.callbackAccepted`), so no retry follows;
    `DeliveryReceipts` (receipts, `statusOf`: UNKNOWN = PENDING); `AdminQueries` (counts / lists, display only);
    `FatherDataPurger` (deletion). No job reads the table to send (WeeklyGoalCompletionJob, BeltPromotion, KeepWarm do not).
  - `login_link`: `sentToFatherSince` (delivery SENT) - `LoginLinkService.sendTo`'s "already on his screen" (10 min) and
    `InboundMessageHandler` (links created since the turn started: a held link is older than the turn that may run when
    the report arrives, so it never counts there). A FAILED/EXPIRED link therefore only means that the **next time he
    asks** for his page a new button goes out instead of "it is on your screen" - the D-038 rule for a link Meta failed;
    he never got the first one. UNKNOWN keeps SENT (no second card). `useIfValid` (the token) is untouched;
    `AdminQueries` display; `DeliveryReceipts`.
  - Not changed: `CoachMentions` recorded the held message's names when the callback answered DELIVERED; a later
    FAILED/EXPIRED leaves them (the coach repeats a name less, nothing is sent). The platform's own timeline links
    `held:<n>` itself (spec v2.6).
- **Early receipts (only with `GATEWAY_HELD_REPORTS` on):** `DeliveryReceipts` keeps a receipt of **any** status for a
  wamid no row holds - the latest per status, at most 500 wamids, 2 minutes, re-applied every 15 s in order sent →
  delivered → read → failed once the row exists (checked first: one query per kept wamid, no repeated UPDATEs/logs),
  and at once when a SENT report links the wamid (`applyPending`). Receipts of conversational replies (no row) are
  kept 2 minutes and dropped. **Off: exactly as before D-042** - only "failed" is kept (one per wamid, a full buffer
  takes nothing, the existence check only for an unmatched "failed"), re-applied and dropped once it moved or its row
  exists (`DeliveryReceiptsTest.withHeldReportsOffOnlyAFailedReceiptIsKeptForAnUnknownWamid`). Lost on a restart; what
  covers it: the report's `latestStatus` carries the latest receipt the platform's held row had when the report was
  sent (a receipt that reached the platform before the report), and a receipt after the report matches the now-linked
  wamid directly.
- **Admin:** status UNKNOWN reads "לא ידוע אם הגיע"; reasons GATEWAY_HELD_EXPIRED / GATEWAY_UNKNOWN / GATEWAY_* in words.
- **Tests:** `HeldOutcomeTest` (15, integration: switch off = 404 and no change, then the platform's retry applies;
  SENT links the wamid and later receipts match, replayed callback sends nothing and does not re-gate; latestStatus
  forward only across reports; receipt before the report; kept receipt applied by the retry; held link SENT stays "on
  screen"; FAILED / EXPIRED (scheduled + link: next request only) / UNKNOWN (scheduled + link: no second card); a late
  SENT cannot revive a FAILED row; duplicate key; unknown id 200; tool / admin / wrong / no key 401; bad requests 400),
  `HeldOutcomeControllerTest` (3, unit: switch and request checks before any read). `DeliveryReceiptsTest` follows the
  renames (`PENDING_FOR`, `retryPending`) and adds the switch-off buffer test (9). Full backend suite 425/425; frontend tsc + vitest green.
- **Rollout:** deploy (switch off; V46 = two partial indexes) → `GATEWAY_HELD_REPORTS=true` → platform env
  `WORKFLOW_CHANNELS_WHATSAPP_ROUTES_1_HELDREPORTURL=https://dad-coach.onrender.com/api/integration/channel/held-outcome`
  (gateway route 1 = `dad-coach`, worker `dad_3`; its claim key is already Dad Coach's callback key, header X-API-Key) →
  watch `channel.whatsapp.held.reported` (platform) and `whatsapp.held.outcome` (Dad Coach) at the next switch back.
- **Rollback** (also in DEPLOYMENT.md, Runbooks): unset the platform URL (reports stop) and/or
  `GATEWAY_HELD_REPORTS=false` (404 and the receipt buffer as before D-042; the platform retries for 24 h, then gives
  up - log noise only). A code revert keeps V46 (indexes only); before it, run
  `UPDATE scheduled_response_delivery SET status = 'HELD' WHERE status = 'UNKNOWN'` (the old enum cannot load UNKNOWN).

## D-043 — Scheduler lanes: the sending job keeps its thread, housekeeping gets a pool (Unified Workflow 6.0b, R6/R18, 2026-10-11)

- **Why:** every `@Scheduled` job ran on Spring Boot's single default scheduler thread `scheduling-1`. A belt
  promotion run (shared-number gate up to 3 s + Meta up to 10 s per send, image + text: ≈26 s per father) held up the
  receipt retry (15 s), the template refresh, the idempotency purge, the platform deletions and the keep-warm ping;
  a slow template read from Meta (15 s per page) held up the belt run the same way. Spec: platform
  `docs/architecture/PHASE6_SHARED_MESSAGING_SPEC.md` §6.
- **Switch** `APP_SCHEDULING_LANES` (`app.scheduling.lanes`, default **false**), `config/SchedulingLanes`. On only for
  `true` in any case; anything else (`yes`, `on`, `1`, empty, a typo) is off - Big Boss's D-195 rule, so a mistyped
  value falls back to today instead of leaving no scheduler bean (the application would not start):
  - **off** - exactly as before: one `ThreadPoolTaskScheduler` built by Boot's own builder (pool 1, `scheduling-N`,
    `spring.task.scheduling.*`), under the names `taskScheduler`, `messagingScheduler` and `housekeepingScheduler`.
    Declaring it makes Boot drop its own `taskScheduler` and (Boot 3.4: it backs off on any `Executor` bean) its
    `applicationTaskExecutor`, so that executor is declared too, as Boot declares it (lazy, same builder, names
    `applicationTaskExecutor` / `taskExecutor`; nothing in Dad Coach uses it - no `@Async`, no async MVC - but the
    context keeps the same executors). Tested against a Boot-only context.
  - **on** - `taskScheduler` = `messagingScheduler`: one thread, still named `scheduling-1`; `housekeepingScheduler`:
    pool of 3, `scheduling-housekeeping-N`.
- **The default is the serial lane** (the spec named the pool as default; inverted on purpose): a job that names no
  lane - every job added later until someone checks it - runs where every job ran before. Only the jobs below opt into
  the pool with `@Scheduled(scheduler = SchedulingLanes.HOUSEKEEPING)`. A single `@Scheduled` method never overlaps
  itself in either mode (one future per method), so the pool only lets *different* housekeeping jobs run side by side.

  | Job | Lane | Sends WhatsApp | Writes |
  |---|---|---|---|
  | `WeeklyGoalCompletionJob.run` (belt promotions, Sunday 06:00 UTC; promotes no one since D-030) | messaging (named) | yes: image via `WhatsAppApiClient` + text via `ProactiveSender` | `weekly_goal`, `father` (belt/streak), `communication_endpoints` (`ensure`), timeline / `SentMessageRecorder` (HTTP) |
  | `DeliveryReceipts.retryPending` (15 s) | housekeeping | no | `scheduled_response_delivery`, `login_link` (forward-only conditional UPDATEs) + the in-memory early-receipt buffer |
  | `MetaTemplateDirectory.scheduled` (10 min) | housekeeping | no (GET templates) | `template_messages` |
  | `IdempotencyService.purgeExpired` (1 h) | housekeeping | no | `tool_idempotency` (expired rows) |
  | `PlatformPersonDeletions.sendPeriodically` (5 min) | housekeeping | no (platform HTTP) | `platform_person_deletion`; `FatherDataPurger` deletes the father's rows (incl. `scheduled_response_delivery`, `login_link`, `tool_idempotency`) |
  | `KeepWarmJob.keepWarm` (5 min) | housekeeping | no (GET own health) | nothing |

- **Cross-lane checks (switch on), shared rows first:**
  - **`DeliveryReceipts` vs every writer of `scheduled_response_delivery` / `login_link`:** the writers are
    `ScheduledResponseDeliveryService` (platform callback, Tomcat), `HeldOutcomes` (D-042 report, Tomcat),
    `LoginLinkService` / `LoginLinkDelivery` / `InboundMessageHandler` (Tomcat, `wa-turn`), `FatherDataPurger`
    (deletions). **None runs on the messaging lane** - the belt job writes neither table. Each receipt move is one
    conditional, forward-only UPDATE per table (row locks order it against any other writer; FAILED is final), and
    `retryPending` already ran concurrently with all of them (webhook / Tomcat / `wa-turn` threads) - only its own
    thread changes. **D-042 buffer (`GATEWAY_HELD_REPORTS`):** a `ConcurrentHashMap`; `retryPending` takes a wamid's
    kept receipts with `remove(key, value)` (on) or replays and then `remove(key, value)` (off), `applyPending` with
    `remove(key)`, `remember` with `compute` - whoever removes first replays, a value replaced in between is kept for
    the next round. These callers were already on different threads (scheduler vs Tomcat); unchanged.
  - **`DeliveryReceipts` vs `PlatformPersonDeletions`** (both housekeeping, may now overlap): an UPDATE on a row being
    deleted either lands first or matches nothing - the same pair already ran concurrently (webhook receipts vs the
    deletion's own `platform-person-deletion` thread).
  - **`PlatformPersonDeletions` vs the belt job** (purge of a father being promoted): `sendDue` is `synchronized` and
    already runs on its own `platform-person-deletion` thread right after every deletion, concurrently with the old
    scheduler thread; the scheduled scan moving lanes adds no new interleaving. The belt notifier skips a DELETED father.
  - **`IdempotencyService.purgeExpired` vs `FatherDataPurger`** (both delete `tool_idempotency` rows): overlapping
    DELETEs, the second skips rows already gone; already concurrent via the deletion thread.
  - **`MetaTemplateDirectory` vs the belt job:** the belt text reads `template_messages` (approved or not) while a
    refresh may update it - each row update is committed on its own, so a send sees the state just before or just
    after one row's change; the same interleaving already exists for `wa-turn` replies, platform callbacks and the
    admin's on-demand refresh. `refresh` is `synchronized`; the snapshot is a `volatile` reference swap.
  - **`KeepWarmJob`:** no data.
  - **Database connections:** production's Hikari pool is **5** (`application-prod.yml:9`, `maximum-pool-size: 5`,
    Supabase), shared with Tomcat, the `wa-turn` pool (8), `platform-person-deletion` and `TimelineReports`. Before,
    the scheduler used at most 1 connection at a time; switch on, at most 4 (1 messaging + 3 housekeeping), and only
    if all jobs coincide. None holds a connection across a network wait: the belt job is not transactional (each
    send's reads/writes are short; `completeWeeklyGoals` is one short transaction before the sends),
    `retryPending`, `MetaTemplateDirectory.refresh` and `PlatformPersonDeletions.sendDue` are not transactional (HTTP
    outside any transaction; `FatherDataPurger.purge` and the registry updates are short transactions),
    `purgeExpired` is one DELETE, `KeepWarmJob` uses none. A busy pool makes a caller wait (Hikari's 30 s
    connection timeout), it does not fail at once. If the pool shows waits, turn the switch off.
  - **Doubtful, kept on the messaging lane:** none of today's housekeeping jobs - none sends and none shares a row
    with the belt job. Everything not listed above (any future job) stays on the messaging lane by default.
- **Not changed:** what is sent, when, and in which order among sending jobs; the belt job's thread name
  (`scheduling-1` in both modes); `PlatformPersonDeletions`' own thread, `TimelineReports` lanes, `wa-turn`.
  Further (6.2): move Meta waits off the messaging lane entirely.
- **Tests:** `SchedulingLanesTest` (21, no DB: any value but `true` (`yes`, `on`, `1`, empty, blank, `enabled`,
  `false`, `FALSE`, `no`) starts with today's single scheduler and runs every job on `scheduling-1`; `true` / `TRUE` /
  `True` turn it on; production's lazy initialization (Boot's `LazyInitializationBeanFactoryPostProcessor`, as
  `-Dspring.main.lazy-initialization=true` in the Dockerfile) off and on - the schedulers are lazy, the jobs still run
  on their lanes and never overlap; every `@Scheduled` method of the product is in the lane table above, so
  a new job fails until it gets a lane; the thread each real job is routed to - Spring's `TaskSchedulerRouter` with
  the job's own qualifier - off: all `scheduling-1`, on: belt `scheduling-1`, housekeeping `scheduling-housekeeping-N`;
  off = Boot's scheduler and executor (names, aliases, pool sizes, prefixes, lazy executor); on: a housekeeping job
  keeps running while a belt send waits on a slow fake Meta (latch) and the other messaging-lane job waits; off: it
  waits too (today); on: two messaging-lane jobs never run at once (max concurrent = 1)), `SchedulingLanesAppTest`
  (real context, switch off: every registered job on `scheduling-1`), `SchedulingLanesOnAppTest` (real context, switch
  on, closed after the class: every registered job on the pool, the default on `scheduling-1`),
  `SchedulingLanesLazyAppTest` (real context with `spring.main.lazy-initialization=true` as in production, switch
  unset: the scheduler bean is lazy and every registered job runs on `scheduling-1`). Full backend suite 449/449.
- **Rollout:** deploy (switch off = no change) → `APP_SCHEDULING_LANES=true` → restart; the JSON logs' `thread_name`
  of `whatsapp.receipt` / `meta.templates.refreshed` read `scheduling-housekeeping-N`, `weekly_goal.completion`
  `scheduling-1`. **Rollback:** `APP_SCHEDULING_LANES=false` and restart; nothing in the data (DEPLOYMENT.md, Runbooks).

## D-045 — A father's dashboard link can be sent by the platform's Send API, behind a switch (Unified Workflow Phase 6.3-DC, step DC1, 2026-10-11)

- **Why:** Phase 6.2b put a Send API on the platform (`POST /api/v1/worker/messages/send`: one send engine for the
  shared number - window, templates, the gate, receipts, the timeline row - spec
  `ai-workflow-platform/docs/architecture/PHASE6_2_SEND_API_SPEC.md`, "Spec v2" v2.1-v2.16; live but inert:
  `MESSAGING_ENABLED=false`, empty allow-list). Spec §17.1: Dad Coach moves first, kind by kind - login link, then
  scheduled callbacks, then belt promotion, then replies. This is the first kind. The staff login link and the
  platform-down line stay direct (a platform send would add a window check to the staff link, spec N3).
- **Switch** `MESSAGING_MODE_LOGIN_LINK` (`dad-coach.messaging.modes.login-link`, `MessagingSettings`):
  - `DIRECT` (default; also anything but the three names): today's path, unchanged - no call to the Send API, no new
    column written (`LoginLinkPlatform.shadow` returns at once). Tested: same Meta bytes as SHADOW (phone and token
    aside), platform columns NULL, the D-039 report as before.
  - `SHADOW`: today's path, then on a thread of its own (`messaging-shadow`, queue 100, dropped when full) the
    platform's dry run of the same request (`dryRun: true`; no row, no Meta call on the platform). Logs
    `messaging.shadow.match` or `messaging.shadow.mismatch {kind, linkId, field: hold|outcome|window|template, product,
    platform}`, `messaging.shadow.unavailable` when the platform cannot answer. Never a second message, never a row change.
  - `PLATFORM`: a **father's** link through `/send` (a staff user's stays direct).
- **The request** (`LoginLinkPlatform.request`): `workerKey dad_3`, `workflowKey`, `userId whatsapp:+…`, `to`, the tenant,
  `personRef`/`personName`; `idempotencyKey login-link:<loginLinkId>` (the row is new per send), `kind LOGIN_LINK`,
  `messageClass UTILITY`; `content` = the same `link_button` card as the direct path (text, footer "הקישור אישי…",
  button "כניסה לדף שלי"); `fallbackText` = the text with the link on its own line (the direct path's
  `withLinkAsText` when Meta refuses the button); `template` = the general template (`WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME`,
  default `dad_coach_update_he`) with the one-line `{{1}}` carrying the link, as today outside the window;
  `windowPolicy AUTO`, `outsideWindowWithoutTemplate FAIL`; `windowHint.lastInboundAt = session_closes_at − 24 h`
  (v2.13); `holdPolicy GATEWAY` (Dad Coach does **not** call the gate itself - the platform gates); timeline
  `NEW_ROW`, `correlationId dashboard-link:<id>` (as D-039), the turn's id, and `content` = the card's text **without
  the link** (otherwise the platform would record the template's rendered body, link and token included); `wait ACCEPTED`.
- **Durable bytes (v2.6):** the link row and the exact request bytes (`login_link.platform_request`, V47) are committed
  in their own transaction **before** the first POST; every re-POST and the reconcile send exactly those bytes. They
  carry the sign-in link, so they are set to NULL as soon as they are no longer needed (the platform answered, Dad Coach
  sent it itself, refused, gave up). Only the token's hash stays, as before D-045.
- **The answer** (`LoginLinkPlatform.verdict`/`settle`, spec §16 + v2.5/v2.6):

  | Answer | Row | Sent directly? |
  |---|---|---|
  | 200/202 with a status | §7.4 below; `platform_message_id`; state OWNED; bytes cleared | no |
  | 503 with body `messaging.disabled` on the **first** attempt (the platform answers an existing key before its kill switch, v2.1) | state DIRECT, today's send, today's D-039 report | **yes** |
  | 503 `messaging.disabled` **after an ambiguous attempt** (review M1: that attempt may still be in flight on another platform instance while a restart flips the switch) | stays AMBIGUOUS; only the lookup by key decides | not until the lookup says 404 |
  | 401/403 (refused before any handler), first attempt | same, + ERROR `messaging.platform_auth_failed` | **yes** |
  | connection refused / DNS / TLS (never written), or no platform configured, first attempt | same | **yes** |
  | 400/422, on **any** attempt | FAILED `PLATFORM_<error>[_<reason>]` (e.g. `PLATFORM_TEMPLATE_UNUSABLE_NOT_FOUND`), ERROR log, state REFUSED | no - the request is wrong |
  | 409 `idempotency_conflict` on the first attempt | FAILED `PLATFORM_KEY_CONFLICT`, ERROR | no |
  | 409 `idempotency_conflict` after an ambiguous attempt | **adopted** (the body carries the message's state), WARN `messaging.platform_conflict_adopted` | no |
  | timeout, 5xx, an edge's 502/503/504 without the body code, connection lost after write, 404, 429, an unreadable 2xx | state AMBIGUOUS, row SENT / `receipt_status` QUEUED ("on its way") | **never** |

  - Ambiguous: one immediate re-POST of the same bytes on the turn's thread - not after a timeout (the thread already
    waited 12 s) and not after a 429. Then the job (`LoginLinkPlatform.reconcileDue`, every minute, messaging lane:
    it may send) re-POSTs once more (3 POSTs in all = "re-POST up to 2 times"), and `MESSAGING_RECONCILE_AFTER`
    (5 min) after the first POST asks `GET /api/v1/worker/messages?workerKey&idempotencyKey`: **200** = adopt;
    **404 with `messaging.message_not_found`** = nothing was created (the platform commits before any answer) → sent
    directly, if the link is still unrevoked and unexpired (else FAILED `PLATFORM_NOT_CREATED_LINK_ENDED`); anything
    else = ask again every 5 min; after 1 h = GAVE_UP, `receipt_status` UNKNOWN, `PLATFORM_RECONCILE_GAVE_UP`, never sent.
    A re-POST that is never written, 401/403 or 503 `messaging.disabled` after an ambiguous attempt stays ambiguous
    (the first one may have been created). A 400/422 is FAILED on any attempt: the platform answers an existing key
    (200 replay / 409) **before** its kill switch, rate limit and template checks (v2.1), so a 422 on a re-POST means
    no message exists for the key, and the bytes are the same, so the request is wrong whichever attempt it is (a 400
    is the shape check: identical bytes cannot pass it once and fail it later). Claims: `platform_next_at` moved by
    one conditional UPDATE (lease 2 min, from the time each row is taken, not the pass's start) before a row is
    worked. `MESSAGING_RECONCILE_AFTER` is never below 5 min (clamped, WARN `messaging.reconcile_after_clamped`): "not
    found" proves nothing only when no request can still be in flight. The Send API client has Reactor Netty's
    automatic retry off (`disableRetry`): no hidden second POST. The job's idle cost is one indexed `SELECT` a minute.
  - A POST in flight belongs to the sending thread for 2 minutes (`platform_next_at` = lease); a thread that died
    leaves the row POSTING, and the job then treats it as ambiguous.
- **Mapping (spec §7.4), forward only, under the row's lock (`SELECT … FOR UPDATE`, own transaction), read off the row
  as it stands (Meta's receipts may be ahead):** ISSUED 0 < QUEUED 1 < HELD / UNKNOWN 2 < accepted 3 < SENT 4 <
  DELIVERED 5 < READ 6; FAILED only before DELIVERED and final; UNKNOWN only from ISSUED / QUEUED / HELD.
  - QUEUED → `delivery_status` SENT, `receipt_status` **QUEUED** (new value). **Deviation from §7.4** (`ISSUED`):
    ISSUED is not "sent", so `InboundMessageHandler`'s check (a card sent during this turn) and the 10-minute "on his
    screen" check would both send a second card while the platform is still sending the first.
  - HELD → SENT / HELD, `gateway_hold_id` `held:<heldId>` (the platform excludes linked held rows from held-outcome
    reports, v2.13, so D-042 never sees them). ACCEPTED → SENT, receipt NULL (= accepted), wamid. DELIVERED / READ →
    SENT + that receipt. FAILED → FAILED, `delivery_error` / `receipt_status` FAILED, `PLATFORM_<reason>`.
    UNKNOWN → SENT / UNKNOWN (as D-042: never counted failed, never re-sent).
  - The coach's tool and `InboundMessageHandler` read the outcome as today: SENT for QUEUED / HELD / ACCEPTED /
    UNKNOWN / ambiguous (it is on its way or may have arrived), FAILED only for a FAILED answer or a refusal.
  - A wamid newly linked applies Meta receipts kept for it (`DeliveryReceipts.applyPending`).
  - A link the platform keeps QUEUED / HELD with no report is looked up by key (QUEUED after 5 min, HELD hourly, for
    26 h) - also when the platform's QUEUED / HELD did not move the row (an ambiguous link already reading SENT /
    QUEUED): its status is still recorded and the lookup still scheduled (review L1).
- **Delivery reports:** `POST /api/integration/channel/delivery-status` (`DeliveryStatusController`; the
  `/api/integration/channel/**` chain opens with the callback key - the platform route's claim key, used as its report
  key by default). Body per spec §3.5, header `X-Idempotency-Key: delivery:<messageId>:<status>` (scope
  `PLATFORM_DELIVERY_STATUS` in `tool_idempotency`; same key again = 200 `duplicate`, in progress = 409). Matched by
  `idempotencyKey` (`login-link:<id>`), else by `platform_message_id`; another kind or no row = 200 `known:false`.
  Switch `MESSAGING_DELIVERY_STATUS_ENABLED` (default false): off = **404** (the platform retries for 24 h). A report
  for a row Dad Coach sent itself (state DIRECT) is never applied and logs ERROR
  `messaging.login_link.platform_after_direct` (a second message is possible: should never happen). A report for any
  other non-platform row: WARN, not applied.
- **V47** (additive): `login_link.platform_message_id`, `platform_request`, `platform_state`, `platform_status`,
  `platform_posts` (default 0), `platform_first_post_at`, `platform_next_at` + two partial indexes.
- **Threads and connections:** the PLATFORM path runs where the link is asked for (Tomcat, `wa-turn`); the POST waits
  up to the platform's 8 s + 4 s (`MESSAGING_SEND_READ_TIMEOUT_MS`, 12000; connect 2 s). Its row writes are short
  `REQUIRES_NEW` transactions, so a link send briefly holds 2 pooled connections (the caller's transaction + one),
  never across the HTTP call beyond what the caller's transaction already did before D-045. `LoginLinkPlatform` is
  `@Lazy(false)` (production starts lazily: the reconcile job must run after a restart). D-043 lane table: the job is
  on the messaging lane; it writes only `login_link` rows in POSTING / AMBIGUOUS / OWNED (conditional, row-locked,
  forward-only UPDATEs, the same rule `DeliveryReceipts` follows on the housekeeping lane).
- **Review (independent, `a0523ff`: APPROVE WITH NITS) - fixed in the follow-up commit:** M1 (503 disabled after an
  ambiguous attempt is ambiguous), L1 (QUEUED / HELD without a move keeps the lookup), L2 (this wording), L3
  (`disableRetry`, 5-min floor, claim per row), L4 (DEPLOYMENT.md rollback waits for open rows). Every test checks
  that no log line of the application carries the link (`token=`, `auth/consume`).
- **Tests:** `LoginLinkPlatformSendTest` (24 after the review, integration, fake platform + fake Meta, no real WhatsApp): DIRECT
  unchanged (no Send API call, columns NULL, timeline report); DIRECT and SHADOW send Meta identical bytes; SHADOW's
  dry run off the send thread (the send returns while the dry run is blocked), one message, row unchanged; mismatch
  logs (hold, window); PLATFORM ACCEPTED (bytes committed before the POST - read by another connection during it -,
  request fields, wamid, no Meta call, no gate call, no D-039 report, the link signs in); HELD / QUEUED (no second card)
  / FAILED / UNKNOWN mapping; 503 disabled → direct once, with the gate and the D-039 report; 403 → direct + ERROR;
  422 / first-attempt 409 → FAILED, no re-POST, no Meta; edge 502 → identical re-POSTs, never direct, reconcile 500 then
  200 → adopted; 504 then lookup 404 → direct exactly once; lookup never answering → UNKNOWN after 1 h, never sent;
  timeout → no re-POST on the thread, the job's re-POST gets the replay (DELIVERED) and adopts it; 409 after an
  ambiguous attempt → adopted; never-written only first; staff stays direct; HELD with no report looked up after 1 h;
  reports forward only with a Meta receipt in between (READ, then a stale DELIVERED and a FAILED change nothing); a
  report before the sync answer; FAILED report → next request sends a new card; endpoint contract (off 404 then
  applied, duplicate, by messageId, unknown, 400s, 401s); report for a DIRECT row ignored. `PlatformMessagingUnitTest`
  (2: switch values; refused / DNS / TLS never written, timeout / reset ambiguous). `SchedulingLanesTest` /
  `SchedulingLanesOnAppTest` follow the new job (messaging lane).
- **Rollout** (DEPLOYMENT.md, "Platform Send API for the login link (D-045)"): deploy (DIRECT) → platform enabled for
  `dad_coach:LOGIN_LINK` + route 1 `DELIVERYREPORTURL` + Dad Coach `MESSAGING_DELIVERY_STATUS_ENABLED=true` → SHADOW
  for a day → PLATFORM. **Rollback:** `MESSAGING_MODE_LOGIN_LINK=DIRECT` (keep the delivery-status switch on 48 h after
  the last PLATFORM send), or platform-side `MESSAGING_ENABLED=false` / allow-list without it (every new link then gets
  503 disabled and goes directly, no Dad Coach change).

## D-046 — Scheduled messages and the belt promotion can be sent by the platform's Send API, behind switches (Unified Workflow Phase 6.3-DC, steps DC2 / DC3, 2026-10-11)

- **Why:** spec §17.1 (`ai-workflow-platform/docs/architecture/PHASE6_2_SEND_API_SPEC.md`, "Spec v2" v2.1-v2.16): after the
  login link (D-045), the scheduled callbacks (DC2), then the belt promotion (DC3). Prepared only: both switches default
  `DIRECT`, nothing is enabled.
- **Switches** (`MessagingSettings`, same values and parsing as D-045; anything but the three names = DIRECT):
  `MESSAGING_MODE_SCHEDULED` (`dad-coach.messaging.modes.scheduled`) and `MESSAGING_MODE_BELT_PROMOTION`
  (`dad-coach.messaging.modes.belt-promotion`). DIRECT = today's path, nothing else called, no new column written.
  Tested byte for byte: DIRECT and SHADOW send Meta the same bytes and answer the callback the same body
  (`{"status":"DELIVERED","detail":"Delivered"}` with D-039 off); `PlatformDeliveryReportsTest`, `ScheduledDeliveryBaselineTest`,
  `BeltPromotionNotifierTest`, `BeltPromotionTest`, `HeldOutcomeTest` pass unchanged.
- **Refactor (no behaviour change):** D-045's mechanics moved out of `LoginLinkPlatform` into
  `integration/platform/messaging/PlatformSendFlow`: bytes committed before the first POST, the answer table (`verdict`),
  the one immediate re-POST (not after a timeout or a 429), settle (OWNED / ADOPT / DIRECT on the first attempt only /
  REFUSED / AMBIGUOUS), the forward-only `apply` under the row's lock, delivery reports, the reconcile job's state
  machine (re-POST up to 3 POSTs, lookup by key ≥ `MESSAGING_RECONCILE_AFTER` (5-min floor), 404 = never created,
  UNKNOWN after 1 h), the SHADOW lane and comparison, and the request's "who" fields + window hint. A kind plugs in a
  `Ledger` (its table with the same platform columns, how a platform status reads on its row, its refused / gave-up /
  too-late columns, whether a late direct send is still right, how it is sent directly) and its own logger, so log
  lines keep their names (`messaging.<event>.*`). `LoginLinkPlatform` is now the `login_link` ledger; its public API, its
  log lines and SQL effects are unchanged and `LoginLinkPlatformSendTest` (24) passes unchanged. Kinds without a ledger
  use `sendOnce` (bytes in memory, spec v2.6).
- **SCHEDULED (DC2)** - everything through the platform's scheduled-response callback (`ScheduledResponseDeliveryService`:
  session morning / hour-before / follow-up, daily check, weekly plan, any other timer). `ScheduledPlatformSend` is the
  `scheduled_response_delivery` ledger. What stays in Dad Coach first, in every mode: the row claim under the trigger's
  UNIQUE key (a replayed trigger never reaches the platform), the no-valid-session skip and the ready texts
  (`ScheduledReplies`, D-036), `ReplyStyleGuard`, the not-Hebrew block.
  - **Request:** key `scheduled-response:<triggerId>`, kind `SCHEDULED`, UTILITY; `content` = what DIRECT sends
    free-form: `buttons` (the session's `dc:` buttons) when they fit an interactive body, else `text`; `template` = the
    message's own template (`ScheduledMessageTemplates`: `dad_coach_session_morning_he` / `_hour_before_he` /
    `_follow_up_he` with its quick-reply payloads, values one-lined as DIRECT does) and `fallbackTemplates` = the
    general one (`WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME`, `{{1}}` = `ProactiveSender.asTemplateParameter`); no own
    template → the general one is `template`. `expectedBody` = the catalog body when the catalog has the template (DC's
    BODY_DIFFERS check). `windowPolicy AUTO`, `FAIL` outside without a template, `windowHint.lastInboundAt =
    session_closes_at − 24 h`, `holdPolicy GATEWAY` (Dad Coach does not gate), `wait ACCEPTED`.
  - **notAfter** (the platform never sends it later; it matters when the gateway holds it while he is on another
    product, up to 24 h): morning - the start of the day's last session it names; hour-before - its session's start;
    follow-up - 12 h after its session ended (the timers' own window); anything else - the end of his local day. Never
    closer than 15 min (a message sent now is never refused for it).
  - **Timeline:** `REPLACES_DRAFT`, `turnCorrelationId scheduled-transition:<triggerId>`, `correlationId
    callback:scheduled-transition:<triggerId>` (the row D-039's `deliveredContent` creates today), `content` = the text
    without the identity line. Deviation, cosmetic: for a template send the platform's row shows this text, not the
    rendered template (the template's name is on the row).
  - **Callback answer:** platform owns it (OWNED / ADOPT, any status incl. FAILED - the platform records it) or may own
    it (AMBIGUOUS) → `{}` plus `platformMessageId` when known: no `status`, no `outcome` / `deliveredContent`, so the
    platform records once (`callbackAccepted` ignores a body without them). Provably nothing created → today's send and
    today's answer (D-039 report included). Refused (400/422/first 409) → FAILED `PLATFORM_<error>[_<reason>]`, answered
    as a failed send today (`{status: FAILED, detail}` + D-039 `outcome FAILED`): nothing was created, so nothing else
    records it. `CoachMentions` are noted when it went out or is on its way (DIRECT: as today, on DELIVERED).
  - **Mapping** (§7.4, forward only; SENDING 0 < QUEUED 1 < HELD / UNKNOWN 2 < ACCEPTED 3 < SENT 4 < DELIVERED 5 < READ
    6; FAILED final): QUEUED and an ambiguous answer leave the row SENDING (a replay answers "Delivery already in
    progress"); HELD → HELD + `held:<n>` (the platform excludes linked held rows from held-outcome reports, so D-042 never
    moves them); ACCEPTED → ACCEPTED + wamid + `delivery_mode` from `channelWindow`; DELIVERED / READ; FAILED →
    `PLATFORM_<reason>`; UNKNOWN → UNKNOWN (never failed, never re-sent, D-042). Meta's receipts keep matching the wamid
    (`DeliveryReceipts`), kept early receipts are applied when the wamid is linked.
  - **Reconcile** (`ScheduledPlatformSend.scheduledReconcile`, every minute, messaging lane - it may send): as D-045.
    "Never created" (lookup 404 after ≥ 5 min) → sent directly **only before `notAfter`** (else FAILED
    `PLATFORM_NOT_CREATED_TOO_LATE`), exactly the request's content / buttons / own template through `ProactiveSender`,
    and - the callback was answered long ago - reported to the timeline as the turn's replacement
    (`/messages/outbound` `callback:scheduled-transition:<triggerId>`, `replacesDraft`) when D-039 is on.
  - **Thread / timeouts:** the callback runs on Tomcat; the POST waits up to 12 s (+ one re-POST). The platform's callback
    read timeout (8 s default) is raised to 30 s before SCHEDULED=PLATFORM (review M1, DEPLOYMENT.md step 4); if not, a
    slow platform makes it retry the callback, which replays the row (`SENDING` while the first `/send` is in flight) -
    never a second send. `wait ACCEPTED` kept (the row gets its wamid at once; reports are not required for the common
    case). A request that cannot be built (nothing posted) is sent today's way (review N1).
- **BELT_PROMOTION (DC3)** - `BeltPromotionPlatform`, no ledger (the job promotes no one since D-030): the image (when
  configured) `belt-promotion:<fatherId>:<BELT>:image`, `content.image {link, caption}`, `windowPolicy SESSION_ONLY` (as
  today only inside his window; outside = the platform's FAILED `WINDOW_CLOSED`, nothing sent), timeline not recorded
  (D-039 never reported the image); then the text `…:text`, `content.text`, the general template, timeline `NEW_ROW`
  `belt-promotion:<fatherId>:<BELT>` with the text without identity - the platform records it, Dad Coach neither reports
  nor writes the legacy `SentMessageRecorder` record. Each part: 503 disabled / 401 / 403 / never written on the first
  attempt → that part today's way (the image only if Dad Coach's window is open); refused or ambiguous → logged, never
  sent directly (no ledger to reconcile; spec v2.6). A refused image (e.g. its host not on the platform's 6.2b
  image-host list) never stops the text (D-040). Fixed keys per father and belt: a re-run replays on the platform.
- **SHADOW (all kinds, incl. the live LOGIN_LINK; review fixes):** a dry run the platform refuses (400 / 409 / 422) while
  Dad Coach's own send went out is now `messaging.shadow.mismatch field=refused code=PLATFORM_<error>[_<reason>]` (it was
  logged as a match unless a template was involved: in PLATFORM mode that message would FAIL, never go directly); both
  refusing is a match (M2). One shadow lane per kind (`messaging-shadow-<kind>`, queue 100 each): a burst of one kind
  never drops another's comparisons; a drop logs `messaging.shadow.dropped {kind, dropped}` (running count). The dry-run
  request is built on that lane, not on the sending / callback thread (N3).
- **Delivery reports:** `DeliveryStatusController` routes by `kind`: `LOGIN_LINK` → link, `SCHEDULED` → scheduled row (by
  key `scheduled-response:…`, else by `platform_message_id`), no kind → link then scheduled; `BELT_PROMOTION` and others
  → `known:false`. A report for a row Dad Coach sent itself is never applied (as D-045).
- **V48** (additive): the D-045 platform columns on `scheduled_response_delivery` + two partial indexes.
- **Not changed:** the staff login link and the platform-down line (direct forever); replies (DC4, next); quiet hours (none
  in Dad Coach).
- **Review (independent, `6e41078`: APPROVE WITH NITS) - fixed in the follow-up commit:** M1 (callback read timeout,
  documented), M2 (refusal = mismatch), N1 (request failure → today's send), N2 (test: retry while in flight), N3 (lanes
  per kind, request built off-thread). Added tests: `ScheduledPlatformSendTest` +4 (refusal mismatch / both refuse =
  match, lanes per kind with drops counted, request failure → direct, retry while the first `/send` is in flight →
  SENDING, one POST), `LoginLinkPlatformSendTest` +1 (refusal mismatch; the 24 earlier tests unchanged).
- **Tests:** `ScheduledPlatformSendTest` (15, integration, fake platform + fake Meta, no real WhatsApp): DIRECT today's
  path and columns NULL; DIRECT = SHADOW byte for byte (Meta body, callback body, row); SHADOW off the callback thread +
  window mismatch logged; PLATFORM ACCEPTED (bytes committed before the POST, every request field incl. own template +
  quick-reply payloads + `expectedBody`, general fallback, window hint, notAfter, REPLACES_DRAFT, no Meta, no gate, no
  timeline report, mentions noted, answer exactly `{"platformMessageId":…}`); templates and notAfter per message (daily /
  hour-before / morning) and the 15-min floor; HELD / QUEUED→lookup / FAILED / UNKNOWN; 503 → direct once with today's
  answer; 422 → FAILED, no direct; edge 502 → identical re-POSTs, answer `{}`, replayed trigger POSTs nothing, job
  re-POST, lookup adopts; lookup 404 → sent late once + timeline replacement; 404 past notAfter → FAILED, never sent;
  lookup never answering → UNKNOWN after 1 h; 409 after ambiguous adopted / first-attempt 409 FAILED; reports by key /
  by messageId / stale / DIRECT row untouched. `BeltPromotionPlatformTest` (8): DIRECT; DIRECT = SHADOW bytes + two dry
  runs; PLATFORM image then text (fields), re-run same keys; refused image → text still; 503 → both direct + D-039
  report; 502 → one identical re-POST per part, never direct; report `known:false`; no image configured → text only.
  `SchedulingLanesTest` lists the new job (messaging lane).
- **Rollout / rollback:** DEPLOYMENT.md "Platform Send API for scheduled messages and the belt promotion (D-046)".

## D-047 — A reconcile pass never holds the scheduler thread for long (Unified Workflow 6.3-DC follow-up, 2026-10-11)

- **Why (Big Boss review, applies here):** Dad Coach runs with `APP_SCHEDULING_LANES` off - one scheduler thread for
  every job (D-043). A `PlatformSendFlow` reconcile pass takes up to 20 rows, each a lookup or a re-POST with the 12 s
  Send API read timeout: a hanging platform could hold that thread ~4 min per job (two jobs: login links, scheduled
  messages), delaying the receipt retry, the template refresh, deletions and the belt job.
- **Change:** the lookup by key (`GET /messages?idempotencyKey`) has its own read timeout,
  `MESSAGING_LOOKUP_READ_TIMEOUT_MS` (default 5000 - a lookup has no platform wait); `POST /send` keeps 12 s. Each pass
  stops taking rows once it has run `MESSAGING_RECONCILE_PASS_BUDGET` (default PT15S, real time; zero/negative = the
  default, never unlimited), logging `messaging.<event>.reconcile_budget_spent {seen, left, budgetMs}`. Rows not taken
  keep their `platform_next_at` (still due) and are taken by the next pass a minute later. Worst case per pass: the
  budget plus one call (lookup 5 s / re-POST 12 s) ≈ 27 s, per job.
- **Safety unchanged:** a lookup that times out is ambiguous ("not found" needs a 404 with
  `messaging.message_not_found`), so the row is asked again in 5 min and never sent directly on a timeout; the
  1-hour give-up still counts from the first POST.
- **Tests:** `ScheduledPlatformSendTest.aHangingPlatformNeverHoldsTheJob` (8 ambiguous rows, lookups hanging, timeout
  300 ms, budget 1 s: the first pass stops at its budget in < 2.5 s, the rows left stay due, following passes look every
  row up exactly once, nothing sent, rows stay SENDING / AMBIGUOUS); `PlatformMessagingUnitTest` (defaults 5000 ms /
  15 s, zero budget = default).
- **Rollback:** raise the two settings (env), no code change.

## D-048 — Every line answering a father can be sent by the platform's Send API, behind four switches (Unified Workflow Phase 6.3-DC, step DC4, 2026-10-11)

- **Why:** owner 2026-10-11 - migrate all message types (no live customers yet). Spec §17.1 DC4
  (`ai-workflow-platform/docs/architecture/PHASE6_2_SEND_API_SPEC.md`, "Spec v2" v2.1-v2.16), after LOGIN_LINK (D-045),
  SCHEDULED and BELT_PROMOTION (D-046). Prepared only: every switch defaults `DIRECT`, nothing is enabled.
- **Inventory - every outbound WhatsApp send in Dad Coach and where it now stands:**

  | Send | Site | Switch |
  |---|---|---|
  | A father's dashboard link (tool, card from a reply, login page) | `LoginLinkDelivery` via `DeliveryService` | `LOGIN_LINK` (D-045) |
  | Scheduled callbacks (timers, daily check, weekly plan) + their late direct send | `ScheduledResponseDeliveryService` / `ScheduledPlatformSend.sendLate` → `ProactiveSender` | `SCHEDULED` (D-046) |
  | Belt promotion image + text | `BeltPromotionNotifier` (`WhatsAppApiClient` / `ProactiveSender`) | `BELT_PROMOTION` (D-046) |
  | The coach's turn reply (as written or corrected) | `InboundMessageHandler` → `WhatsAppAdapter` | **`REPLY`** |
  | Fixed lines: picture / file, voice note off / not heard / too long, spoken deletion request, not-Hebrew line, line instead of the card | same | **`FIXED_LINE`** |
  | A session button's fixed answer | same (`SessionButtonTaps`) | **`BUTTON_REPLY`** |
  | Answers from data ("מה יש לי השבוע?" ...) | same (D-036) | **`READY_ANSWER`** |
  | "משהו השתבש אצלי" (platform down / refused, a tap that failed in Dad Coach) | same | direct forever (spec) |
  | The answer to "DELETE MY DATA" (confirmation / no account) | same | direct forever (below) |
  | A staff member's login link | `LoginLinkDelivery` → adapter | direct (spec: owner call, N3) |
  | Dashboard notes (`DashboardNotes`) | - | not a WhatsApp send (history only) |

  No other caller of `WhatsAppAdapter.sendMessage`, `WhatsAppApiClient.sendMessage`, `DeliveryService.deliver` or
  `ProactiveSender.send` exists.
- **Switches** (`MessagingSettings`, parsed as D-045's): `MESSAGING_MODE_REPLY`, `MESSAGING_MODE_FIXED_LINE`,
  `MESSAGING_MODE_BUTTON_REPLY`, `MESSAGING_MODE_READY_ANSWER` (`dad-coach.messaging.modes.*`); the kind names are
  D-039's timeline kinds, so the platform's rows keep today's `kind`. Read once per line (a switch flipped meanwhile
  never sends a line twice). DIRECT = exactly today's send, log line and reports.
- **The request** (`whatsapp/inbound/ReplyPlatformSend`, no ledger - spec v2.6, the bytes stay in memory): key
  `reply:<inbound wamid>:<n>` (spec DC4; n = the line's place among the lines sent for that message, so Meta's
  redelivery of the message posts the same keys and the platform replays), kind as above, `SERVICE`, `content.text` =
  exactly DIRECT's text (cut to 4096 as the formatter does), `windowPolicy SESSION_ONLY` (a reply is free-form, as
  today; the platform now also checks the window, S5), `windowHint.lastInboundAt` = his message's Meta time,
  `holdPolicy GATEWAY` (Dad Coach does not gate), `wait ACCEPTED`. No `notAfter` (parity: a held reply is released
  when he is back, as today).
- **Timeline (no double record, Phase 3):** the reply as the platform wrote it - `TURN_AS_IS` on the turn (D-039's
  turn-outcome AS_IS); a corrected reply, the Hebrew rewrite (superseding `<wamid>:he`), the not-Hebrew line, the line
  instead of the card - `REPLACES_DRAFT` `<wamid>:delivered` on the turn; a line with no turn - `NEW_ROW` with D-039's
  correlation (`<wamid>` for a button / ready answer, `<wamid>:reply` for a fixed line), the text without the identity
  line; a stranger's number - `record:false` (a report never opens a conversation for a stranger). When the platform
  has the line (or may have it) Dad Coach reports nothing of it: no turn-outcome, no `/messages/outbound`, no legacy
  `SentMessageRecorder` row (tap, ready answer, `:corrected`). As D-046, the platform records also while D-039 is off.
  His own message (no-turn lines, D-039 on) is reported by Dad Coach **before** the POST - one synchronous attempt
  ≤ 2 s (`TimelineReports.inboundBeforeAnswer`), else the background report (the platform records an inbound id once) -
  so it comes before the platform's row of the answer; when the platform then sends nothing (DIRECT fallback) the
  D-039 report skips the inbound only if the synchronous attempt landed (else it is reported again - recorded once).
- **The answer** (`PlatformSendFlow.sendOnce` + new `Options`; default options = the D-046 behaviour, unchanged):
  - **Latency:** the POST's read timeout is `MESSAGING_REPLY_READ_TIMEOUT_MS` (default 5000, clamped 1000 .. the send
    timeout) instead of 12 s - the father waits; today's Meta call blocks up to 10 s. No re-POST after a timeout; one
    immediate re-POST of another ambiguous answer (502 edge, reset); a 429 re-POSTed once after its Retry-After when
    ≤ 2 s (spec §16 "turn replies: once, ≤ 2 s"; a 429 created nothing, v2.1).
  - ACCEPTED / DELIVERED / READ → sent with the wamid; HELD → sent (`held:<n>`, as the gate today); QUEUED, UNKNOWN or an
    ambiguous answer (timeout, 5xx, edge page) → answered as "on its way" (spec §16 UNKNOWN-equivalent, D-042): the
    message is HANDLED, a redelivery is dropped, the line is **never sent directly and never posted again**.
  - Only 429s (a second 429, or a Retry-After above 2 s) → **nothing was created** (the platform answers an existing key
    before its rate limit, v2.1): unanswered `PLATFORM_RATE_LIMITED` (new `Verdict.RATE_LIMITED`, only from `sendOnce`
    with a rate-limit wait), so Meta's redelivery posts the same key again; never sent directly (spec §16). A 429 after
    an ambiguous attempt stays ambiguous (that attempt may be in flight).
  - FAILED → unanswered (as a refused send today: the webhook releases the message). On Meta's redelivery the same key
    and bytes are posted and the platform replays the FAILED. If its reason is **resendable** (the platform's
    `MetaErrorPolicy.RESENDABLE`, copied as `ReplyPlatformSend.RESENDABLE`: NEVER_SENT_*, SENDER_UNAVAILABLE,
    MESSAGING_DISABLED, HELD_*, NOT_AFTER, WINDOW_CLOSED, NO_TEMPLATE, 131047/131056/130429/131048/131057/80007/4,
    HTTP_429 - nothing reached him and Meta decided nothing), the line is sent again **once** as a new message
    `<key>~r1` with `retryOf` = the failed message (spec §10; the platform enforces G1-G5: not delivered, once, same
    person, window, durable key) - same content, its own timeline row (`NEW_ROW <row>~r1`, the failed message keeps its
    FAILED row); a later redelivery replays both keys. A resend that fails, or any other FAILED (131026, 131049,
    131050 ...), is final. A refused resend (409 `retry_not_permitted`) → unanswered `RESEND_NOT_PERMITTED`.
    Refused (400/422) → unanswered, nothing sent.
  - A 409 `idempotency_conflict` carrying the existing message is **adopted even on the first POST** (`conflictAdopts`):
    the key belongs to one inbound message, so a 409 means an earlier processing of it (a redelivery whose text came out
    different) already owns it - one answer per message.
  - Provably nothing created on the first POST (503 `messaging.disabled`, 401/403, never written) → today's send and
    today's D-039 reports.
- **Direct forever (deviation, owner may decide):** besides the spec's "something went wrong" line (also when a tap
  failed inside Dad Coach - the same fail-safe line), the answer to "DELETE MY DATA": the father's platform person
  deletion starts at that moment (after commit, in the background); a Send API row created then for his phone could
  outlive that deletion (person lifecycle: the platform owns deletion), and a timeline record could reopen a
  conversation (D-039 never reports it). The spoken deletion request ("type it") is an ordinary FIXED_LINE.
- **Delivery reports:** the conversational kinds keep no ledger - `DeliveryStatusController` answers `known:false`
  (closes the platform's report), as for BELT_PROMOTION, and logs every report no row holds at INFO
  (`messaging.delivery_status.unmatched {messageId, kind, idempotencyKey, status, reason, resendable}`), so a line that
  FAILED after Dad Coach's 5 s wait is visible.
- **Not changed:** the reply guard (`WHATSAPP_REPLY`, D-038), the webhook claim and UNANSWERED rule (DC-B3), the turn,
  the guards, mentions; no migration (no ledger; V48 stays the latest).
- **Tests:** `ReplyPlatformSendTest` (23 at `1bc55c3`, 26 after the review; integration, fake platform + fake Meta, no real WhatsApp): DIRECT default (4
  Meta sends, D-039 reports as today, Send API never asked); DIRECT = SHADOW byte for byte for all four kinds (Meta
  bodies, timeline reports) + one dry run per line with its kind; SHADOW never on the turn's thread (a 3 s dry run, the
  answer < 2.5 s); PLATFORM REPLY as written (every request field, TURN_AS_IS, no Meta / gate / report, redelivery
  dropped); corrected reply (REPLACES_DRAFT, no `:corrected`); Hebrew rewrite (superseded `:he`) and the not-Hebrew
  line as FIXED_LINE; FIXED_LINE picture (his message reported first, then the POST, NEW_ROW `<wamid>:reply`);
  stranger (record false, no person); BUTTON_REPLY (his tap first; D-039 off: no legacy record); READY_ANSWER; the
  deletion answer and the platform-down line stay direct with every kind on PLATFORM; a 5000-char reply cut as today;
  HELD / QUEUED / UNKNOWN answered; FAILED unanswered + the redelivery posts the same bytes; 503 → today's send, his
  message reported once; 422 → nothing sent; edge 502 → one identical re-POST, never Meta, redelivery dropped; latency -
  a hanging platform holds the turn ≤ the reply timeout (1 s: answered in 0.9-3 s), one POST, never Meta; timeout
  bounds; 409 with state adopted; 429 once after Retry-After / twice → on its way; report `known:false`; switches
  independent per kind. `PlatformMessagingUnitTest` +1 (the four switches parse and default DIRECT).
- **Review (independent, `1bc55c3`: APPROVE WITH NITS) - fixed in the follow-up commit:** (1) only-429 answers =
  nothing created → unanswered `PLATFORM_RATE_LIMITED` (was "on its way"); (2) a resendable FAILED is resent once via
  `retryOf` `<key>~r1` on the redelivery (was replayed forever); (3) unmatched delivery reports logged at INFO;
  (4) one PLATFORM path in `ReplyPlatformSend.send` (DIRECT / SHADOW only in the handler); (5) `hisReported` only when
  the synchronous report landed. Tests: `ReplyPlatformSendTest` 23 → 26 (resend on the redelivery with retryOf and its
  own row, a failed resend is final - never `~r2`, a final FAILED never resent, only-429 / Retry-After > 2 s
  unanswered + redelivery re-POST, unmatched report logged at INFO, his message reported again when the synchronous
  attempt failed). `FakeServers.Reply` can carry a `Retry-After` header.
- **Rollout / rollback:** DEPLOYMENT.md "Platform Send API for the conversational lines (D-048)".

## D-049 — The session timers run from data: morning and follow-up with no model, the hour before behind a guard (Unified Workflow Phase 5.3 slice D, 2026-10-11)

- **Why:** the three session timer turns ran the model, and the callback then replaced its text by the ready line from
  the data anyway (D-036) or dropped it (`NO_VALID_SESSION`, e.g. a session closed from the dashboard or the "done"
  button). Platform spec `PHASE5_3_SPEC.md` §4.
- **Change:** one read-only tool, `dc_session_timer_card` (`api/tools/SessionTimerTools.Card`, logic
  `integration/platform/scheduled/SessionTimerCard`; platform catalog row V136, never shown to the model), param
  `timer` = the state (static in the YAML), `firedAt` / `referenceId` only logged; Dad Coach's own clock decides.
  - `SESSION_MORNING_REMINDER`, `SESSION_FOLLOW_UP`: `execution.mode: TOOL_ONLY` (no model call). `message.text` is
    `ScheduledReplies.plan`'s text - what the callback sent whatever the model wrote - so the same words, buttons and
    template go out. Nothing is sent (`message: null`, no callback) exactly where nothing went before:
    `NO_VALID_SESSION` (the callback's skip); `ALREADY_REMINDED_TODAY` (every session the morning names was already
    in today's morning: two sessions on one day wake the 08:00 morning twice - the model saw its first reminder in
    the conversation); `NOT_JUST_ENDED` (the session ended over 3 h ago: the rule's "N up to about 180"; the callback's
    plan looks back 12 h); `ALREADY_ASKED` (asked about this session in the last 3 h: "one session can wake you more
    than once"). The two "already" facts are new columns written by the callback when such a message is handed over
    (`quality_time.morning_reminded_on`, `follow_up_asked_at`, V49). Unknown number: `UNKNOWN_RECIPIENT` (the callback
    would refuse it).
  - `SESSION_REMINDER_1H`: stays an AI turn (keeps the D-15 wish) with `execution.guard` = the same tool; it declines
    only on `NO_VALID_SESSION`, and only with `DC_SESSION_TIMERS_GUARD=true` (default false: always proceeds). A guard
    failure runs the turn (`onToolFailure: PROCEED`).
  - The callback is unchanged apart from writing the two notes; it still checks and builds every message.
- **No product switch for the tool-only pair:** no Dad Coach setting can make the platform run the model for a
  TOOL_ONLY state, and the tool's text is already what the callback sends, so the switch is the workflow YAML (as for
  the 5.2 mornings of Tair and Big Boss). Rollback: remove the `execution` blocks, republish, upgrade instances.
- **Residual (accepted):** what only the conversation shows stays out - a follow-up he answered in words without the
  session being recorded (the model would stay quiet; the card asks); a session named in a reply before 08:00 is not
  "reminded" (only a morning reminder counts); when the first morning's callback fails and is retried after the
  second morning trigger ran, both go (the model would have seen the first turn's text).
- **Tests:** `SessionTimerCardParityTest` - the grid (15 data situations × morning / follow-up × window open / closed ×
  three model texts: the AI turn with a rule-keeping scripted model + callback equals the tool-only turn + callback:
  same decision, text, buttons, template); second morning of the day, a session added after the morning, follow-up
  asked once within 3 h and again after, each reason; the hour before: decline ⇔ the callback's skip at the same
  instant, and a decline never where a reminder went out before (callback at +0, +30 s, +2 h), flag off always
  proceeds, the wish kept when it proceeds; the envelope (read-only, no idempotency, unknown number, bad timer).
  Provisioning: `test_manifests.test_session_timers_run_from_data`.

## D-050 — Dad Coach owns its session timers; Send API requests carry the father's timezone (Unified Workflow Phase 5.4, 2026-10-11)

- **Why:** the 5.4 spec (`ai-workflow-platform/docs/architecture/PHASE5_4_SPEC.md` §4.4, reviewed) keeps the session timers
  as platform timers but makes Dad Coach their one owner. Before: the model armed them in the booking turn and Dad Coach
  confirmed them after it, and only a WhatsApp turn cancelled them - a session confirmed or cancelled on the dashboard, or
  "done" from its button, kept its timers (their tool-only / guarded turns then declined, D-049).
- **`DC_SESSION_TIMERS_PRODUCT_OWNED` (default false = as before):** a session closed on the dashboard or by its button has
  its pending timers cancelled after the change commits; every 10 minutes (housekeeping lane) the timers of SCHEDULED
  sessions starting within 36 h, until their follow-up time (end + 30 min), are read back and the missing ones armed.
- **Enabling needs `DC_SESSION_TIMERS_GUARD=true`** (it is, in production): a timer the reconcile re-arms a moment before a
  cancel is then declined by its tool-only / guarded state, never sent. The reconcile skips fathers who are DELETED, PAUSED
  or CHURNED, re-reads each father's sessions right before arming, never arms when the platform's timers could not be
  read, and logs only what it armed; the after-turn ensure is unchanged.
- **Next (provisioning, not yet):** once this is on and the platform's `PRODUCT_TIMERS_ANY_STATE` is live, the three timer
  transitions get `onEvent` (hidden from the model, refused for its tool; product arming still allowed) and the rules
  `arm-timers-after-booking` / `clear-timers-when-closed` go - the model stops arming.
- **Timezone (5.4 R-4):** every platform Send API request carries the father's timezone, so a conversation the platform
  creates runs his 09:00 daily check in it (it ran at 09:00 UTC for a conversation created without one).

## D-051 — The belt promotion notifier is retired (Unified Workflow Phase 5.4, 2026-10-11)

- Since D-030 belts come from completed sessions and `WeeklyGoal.complete()` never promotes, so the Sunday job's notifier
  was unreachable (`BELT_PROMOTION` never sent; it stayed in SHADOW with no trigger path). Removed: `BeltPromotionNotifier`,
  `BeltPromotionPlatform`, `BeltImageConfig` (+ `belt.images`), the `BELT_PROMOTION` switch (`MESSAGING_MODE_BELT_PROMOTION`
  is now unused) and their tests. `WeeklyGoalCompletionJob` still completes goals and streaks every Sunday; the test that a
  met week keeps the belt and sends nothing stays. The belt earned by a session is still told by the session's own reply.

## D-052 — Delivery status: no lost writes - a receipt racing its row's commit is applied, kept receipts survive a rollback (lost-write audit, 2026-10-11)

- **Why:** the audit after Big Boss 007b26c / Tair D-070 (after-commit work lost in a finished transaction). Dad Coach
  registers no after-commit work on these paths, but three other lost writes were found and forced in tests:
  1. **A receipt racing its row's commit** (`DeliveryReceipts.apply`): Meta's receipt UPDATE found no row (the wamid
     was not committed yet - the send's answer, a platform report, a held outcome or a direct send in flight); that
     commit and its `applyPending` (nothing kept yet) then ran; the receipt looked for the row, found it, and was dropped
     as "its row exists". Meta's 131047 "failed" comes within milliseconds, so the window is real.
  2. **The 15-second re-apply** (`retryPending`, `GATEWAY_HELD_REPORTS` off) the same way: re-applied (no row), the row
     committed (a direct send calls no `applyPending`), then "its row exists" - dropped.
  3. **Kept receipts taken out of memory inside a transaction that does not commit**: `PlatformSendFlow.apply` and
     `LoginLinkPlatform.sendDirectly` ran `applyPending` in the caller's transaction (`LoginLinkService.sendTo`, a
     tool's `ToolController` transaction); `HeldOutcomes.apply` in its own. A rollback lost the moves and the receipts.
- **Change:** (1) no row yet - the receipt is kept first, then the row looked for once more (a commit before that look:
  replayed here; after it: the committer's `applyPending` finds it); a row there now - the move once more (decisive,
  forward only). (2) the row is looked for before the re-apply, so a row committed during it stays for the next pass.
  (3) `PlatformSendFlow.apply` and `LoginLinkPlatform.sendDirectly` run `applyPending` in a transaction of their own
  (their own write is already committed); `HeldOutcomes` keeps joining (its write links the wamid in that transaction),
  and any `applyPending` whose transaction does not commit puts the receipts back in memory (`afterCompletion`, memory
  only - no write joins the finished transaction). No migration, no switch; behaviour otherwise unchanged.
- **Review follow-ups (same decision):** the post-commit `applyPending` in `PlatformSendFlow.apply` /
  `LoginLinkPlatform.sendDirectly` never fails a send that succeeded (caught, logged
  `messaging.<event>.pending_receipts_failed`; its receipts are kept again). A receipt whose `apply` threw (Meta got its
  200 and never resends) is kept (`keepAfterFailure`) for the next pass; receipts the on-mode pass took out and failed to
  replay go back. The 15-second pass is configurable (`dad-coach.whatsapp.receipts.retry-interval` /
  `retry-initial-delay`, default PT15S); tests set the first run to PT24H so a forced race is never rescued by it.
- **Not fixed - removed by Phase 6.4 cleanup** (products drop direct sends, Meta receipts, held outcomes): held outcomes
  rely on the 15-second pass (their `applyPending` runs before their commit), a held outcome that beats its row's commit
  is acknowledged `known:false`, a direct link's row is written in the caller's transaction after Meta accepted it.
- **Checked safe (tests pin them):** platform reports and reconcile lookups use `SELECT ... FOR UPDATE` and forward-only
  rules against Meta's conditional UPDATEs (concurrent "read" + DELIVERED report always READ, both ledgers); kinds with
  no ledger (REPLY, FIXED_LINE, BUTTON_REPLY, READY_ANSWER, BELT_PROMOTION) write nothing but the report's key; the
  O2 resend keeps no state beyond the inbound claim; the session-timer notes are autocommit writes after the callback.
- **Tests:** `DeliveryLostWritesTest` - each race forced by a test-only statement trigger that parks Meta's FAILED
  receipt between its UPDATE and its look for the row (SCHEDULED report, LOGIN_LINK report, held outcome, direct send +
  re-apply pass); the caller's / held outcome's rollback; concurrency x10 (barrier) failed + ACCEPTED report and read +
  DELIVERED report on both ledgers; reconcile lookup with a kept receipt; no-ledger reports; session-timer notes; a
  failing kept-receipt apply never fails the send; a receipt whose apply threw is kept (both forced by a test-only
  raising trigger).

## D-053 — The platform owns delivery: no Dad Coach send to Meta, no switches, no receipts (Unified Workflow Phase 6.4, 2026-10-11)

- **Why:** the owner's 2026-10-11 instruction (finish the Unified Workflow migration) and the reviewed spec
  `ai-workflow-platform/docs/architecture/PHASE6_4_CLEANUP_SPEC.md` ("Spec v2" authoritative). Production already ran
  every Dad Coach kind on PLATFORM (D-045/D-046/D-048), but every path still fell back to Dad Coach's own Meta send when
  the platform provably created nothing, the staff link, the deletion answer and the "something went wrong" line always
  went directly, and Dad Coach named the Meta template, checked approval against its own Meta read, and parsed Meta's
  receipts. Now the platform is the only sender and the only owner of template choice and receipts; Dad Coach keeps the
  business: what is said, to whom, whether it is still true, its ledgers and their reactions.
- **Removed:** the per-kind switches (`MessagingSettings` modes, `MESSAGING_MODE_*`), SHADOW (lanes, compare/mismatch,
  `dryRun` requests), the DIRECT transport (`WhatsAppApiClient`, `WhatsAppAdapter`, `ChannelRouter`/`ChannelAdapter`,
  `ChannelCapabilities`, `MessageDowngrader`, `DeliveryService`, `ProactiveSender` - its two text helpers moved to
  `TemplateText`), `SharedNumberGate`, the DIRECT branches of `InboundMessageHandler`, `LoginLinkDelivery`'s sends (now the
  card's copy only), `sendLate` of both ledgers, `MetaTemplateDirectory` (+ its 10-minute refresh job) and
  `TemplateRegistry`/`TemplateMessage` (the send-path approval check), `DeliveryReceipts` (+ its 15 s retry job),
  `HeldOutcomeController`/`HeldOutcomes`/`GatewayHeldReports` (`/api/integration/channel/held-outcome`), the Meta payload
  builders of `WhatsAppMessageFormatter`, `SessionWindowService.checkBeforeDelivery`. Tests of that code went with it.
  No migration: `template_messages`, `gateway_hold_id`, the receipt columns stay (unused), so a revert runs on the schema.
  This also retires D-052's receipt-race fixes (the kept-receipt buffer, its 15 s pass, `applyPending` after a report):
  with no receipt handler there is no receipt to race; `DeliveryLostWritesTest` keeps the paths that remain (reports
  racing each other forward only on both ledgers, a forwarded receipt changing nothing, the reconcile lookup committed,
  no-ledger kinds, the session-timer notes).
- **Requests name no Meta template (spec §2):** `templateData` instead of `template`/`fallbackTemplates`/`expectedBody`.
  SCHEDULED with its own session template: `{variant: session_morning_he | session_hour_before_he | session_follow_up_he,
  values, payloads, named: {text}}` (the session's values and `dc:` payloads, `named.text` = the message as one line for
  the general template); any other scheduled message `{variant: "", named: {text}}`; LOGIN_LINK (father and staff)
  `{variant: "", named: {text: <the link line>}}`. The platform's seed (V142, `dad_coach`) maps the session variants to
  their template then `dad_coach_update_he`, and `""` to `dad_coach_update_he`. Replies stay SESSION_ONLY, no
  `templateData`. `windowPolicy`, `notAfter`, `timeline`, `windowHint`, `holdPolicy`, `timezone` (D-050) unchanged.
- **Every line through `/send`:** the "something went wrong" line is a FIXED_LINE (SESSION_ONLY) with keys of its own
  (`reply:<wamid>:down:<n>`, so a later real reply to the same message never replays it); the deletion answer is a FIXED_LINE sent
  BEFORE the deletion is requested, naming nobody (`to` only - no `userId`/`personRef`/`personName`), `sensitive: true`,
  `timeline.record: false`, `wait: ACCEPTED` (spec v2 R7; the person deletion that follows removes the platform's row);
  the staff link is a LOGIN_LINK like a father's but not recorded on any timeline. **Behaviour change (an improvement,
  spec §4.1 / C5):** a staff link outside his 24-hour window now goes as the general template (the platform decides);
  before, a free-form message Meta refused (131047) went out.
- **The answer table (spec §4.1 + v2 R5/R6/R12/R13/R14), no DIRECT row:** 2xx OWNED; 503 `messaging.disabled`
  (ERROR `messaging.platform_disabled`), 401/403 (ERROR `messaging.platform_auth_failed`) or never written, on an attempt
  after which nothing may exist = **NOT_CREATED**: a ledger row (bytes persisted before the first POST) stays pending
  (scheduled: SENDING; link: SENT/QUEUED, so no second card) in `platform_state = NOT_CREATED`, and the reconcile job
  re-POSTs the same bytes (1 min, doubling, at most every 10 min) while its relevance rule holds - scheduled `notAfter`,
  a link unrevoked, unexpired and at most 1 h old - then FAILED `PLATFORM_NOT_CREATED`; a line without a ledger: one
  re-POST after ≤ 2 s (`MESSAGING_NOT_CREATED_REPOST_WAIT`), then WARN `messaging.platform_not_created` and nothing sent -
  the message stays unanswered. Meta does not send it again (the webhook answered 200 before processing); only if the
  same message does arrive again is it processed again, under the same keys. 400/422 and a first-attempt 409 REFUSED; a 409
  after an ambiguous attempt ADOPT; ambiguous re-POSTed then reconciled by key; reconcile lookup 404 = re-POST the stored
  bytes (the platform holds nothing under that key), still only while relevant. A request that cannot be built is a bug:
  FAILED `REQUEST_BUILD_FAILED` + ERROR. A key the platform's shape refuses travels as `h:` + 32 hex of its SHA-256
  (`PlatformSendFlow.platformKey`, deterministic; a scheduled report by that key still finds its row).
- **Receipts and reports:** the webhook still accepts a payload with `statuses` (200) and ignores them; the platform's
  delivery reports (`/api/integration/channel/delivery-status`, forward-only `applyStatus`) and the reconcile lookup are
  the only source of a message's fate. `MESSAGING_DELIVERY_STATUS_ENABLED` now defaults to true.
- **Sweep (R12):** at startup and then at most hourly (from the scheduled messages' reconcile job), ledger rows still pending (scheduled SENDING/HELD, links QUEUED/HELD or
  ISSUED with a platform state) with no platform message id, not driven by the reconcile job and older than 1 h, become
  UNKNOWN (`SWEPT_NO_PLATFORM_MESSAGE`; never failed, never re-sent), counts logged `messaging.pending_swept`.
- **Templates screen:** reads the platform (`GET /api/v1/worker/whatsapp/templates` and `/messaging/kinds`,
  `PlatformTemplateStatus`, cached 5 min, a failed read keeps the last good answer and is not retried for 60 s); "sync"
  reads it again. Same JSON shape (+ `usedFor`; `metaBodyMatches` is null - the mapping, not a body comparison,
  decides); `ready` = a mapped candidate is usable now. The integrations card's "template outside 24 h" =
  the platform could send SCHEDULED's default type as a template now.
- **What still touches Meta (receiving, spec §4.4):** the voice-note media download (`WhatsAppMediaDownloader`, D-029),
  the webhook's verify token and signature secret. So `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_API_BASE_URL`/`_VERSION`,
  `WHATSAPP_WEBHOOK_SECRET`, `WHATSAPP_VERIFY_TOKEN` stay; `WHATSAPP_PHONE_NUMBER_ID` is now only checked by the startup
  guard and the integrations card, `WHATSAPP_WABA_ID` is read by nothing (the screen shows the platform's WABA).
- **Env now unused (verified by grep of the code):** `MESSAGING_MODE_LOGIN_LINK`, `MESSAGING_MODE_SCHEDULED`,
  `MESSAGING_MODE_REPLY`, `MESSAGING_MODE_FIXED_LINE`, `MESSAGING_MODE_BUTTON_REPLY`, `MESSAGING_MODE_READY_ANSWER`
  (+ `MESSAGING_MODE_BELT_PROMOTION`, already D-051), `GATEWAY_HELD_REPORTS`, `DAD_COACH_WHATSAPP_RECEIPTS_ENABLED`,
  `WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME`, `WHATSAPP_WABA_ID`, `BELT_IMAGES_BASE_URL` (D-051). New:
  `MESSAGING_NOT_CREATED_REPOST_WAIT` (optional, default PT2S).
- **Rollback:** `git revert` of the merge + a new deploy, with the env of today set back BEFORE the deploy (spec §6 / R16):
  `MESSAGING_MODE_LOGIN_LINK|SCHEDULED|REPLY|FIXED_LINE|BUTTON_REPLY|READY_ANSWER=PLATFORM`, `GATEWAY_HELD_REPORTS=true`,
  `MESSAGING_DELIVERY_STATUS_ENABLED=true`, and `DAD_COACH_WHATSAPP_RECEIPTS_ENABLED`, `WHATSAPP_WABA_ID`,
  `WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME` with today's values (the reverted code reads them again); on the platform `WORKFLOW_CHANNELS_WHATSAPP_FORWARDSTATUSES=true` and
  `WORKFLOW_CHANNELS_WHATSAPP_ROUTES_1_HELDREPORTURL` restored. Without the mode env a reverted Dad Coach sends DIRECT
  (it keeps its token), a working state. Products first, platform last; never the platform alone (an old platform
  refuses `templateData` requests, R9). Rows a revert finds in `platform_state = NOT_CREATED` are read by the old code as
  neither ambiguous nor owned: their `platform_next_at` is cleared by its reconcile (nothing sent, stays pending) - check
  `SELECT count(*) ... WHERE platform_state = 'NOT_CREATED'` before reverting and expect 0 in normal operation.
- **Kill switch semantics:** platform `MESSAGING_ENABLED=false` or `dad_coach` out of `MESSAGING_ALLOWED_PRODUCTS` now
  means "Dad Coach queues and re-POSTs" (ledger kinds, while relevant) or "the line is not sent, the message stays
  unanswered" (replies) - never a Dad Coach send. Before each deploy the platform allow-list must carry the whole product
  key `dad_coach` (R2).
- **Tests:** the test base asserts after every test that fake Meta received no send; every former fake-Meta assertion is
  an assertion on the `/send` request (`FakeServers.sendRequests`/`sentTexts`, the default fake platform accepts and
  replays by key); the answer table row by row (`PlatformMessagingUnitTest`, `LoginLinkPlatformSendTest`,
  `ScheduledPlatformSendTest`, `ReplyPlatformSendTest`); statuses on the webhook ignored; reports drive both ledgers;
  `KindTemplateSeedContractTest` - every (kind, variant) Dad Coach emits exists in the platform seed (copied as
  `kind-template-seed.json`) with matching values / payloads / `named.text`, and `LoginLinkSeedContractTest` for the
  real father and staff link requests; the deletion answer failing (503, 401: exactly one identical re-POST) never stops
  the deletion; `PendingSweepTest` (incl. at most hourly).

## D-054 — Dad Coach's scheduled jobs on the platform's product-job scheduler (Unified Workflow Phase 5.4 v3, 2026-10-11)

- **Why:** owner, 2026-10-11: the platform owns all scheduling (clock, run-once, retries); products keep business logic.
  Spec `ai-workflow-platform/docs/architecture/PHASE5_4_SPEC.md` §v3/v3.8.
- `DC_PLATFORM_JOBS` lists the jobs the platform owns (empty = all tick here, as before): `weekly_goals` (its Sunday 06:00
  UTC slot, as the tick - review: hourly would complete a week right after Israel's Sunday 00:00 and mid-week in other zones), `session_timers` (D-050's reconcile), `person_deletions`, `qa_probe`
  (no business effect, for controlled checks). A listed job's `@Scheduled` tick does nothing; the platform calls
  `POST /api/integration/jobs/{key}` (the callback key) once per slot; an unlisted job answers PRODUCT_OWNED.
- Every run holds the job's Postgres advisory lock (and the messaging lane's for a job that sends): never overlapping, never
  on two instances. The slot ledger `platform_job_run` (V50) answers a repeated slot with its first result. Startup
  registration (`PUT /api/v1/worker/jobs`, enabled only when listed); a watchdog runs a listed job here once (ERROR the first
  time, then WARN) when neither the ledger (any instance) nor this instance shows a run for 3× its interval and the job is
  not running.
- Review (BLOCK, fixed): the tick runs the tick's own body unchanged (guard, summary log, catch); the platform's body is the
  same work without the catch; an unregistered job ticks as before. Lock connections come from a pool of their own (8,
  idle-closed), never the main pool; a platform call waits at most 5 s for the messaging lane, then answers BUSY (asked
  again in 30 s, not counted); a connection whose unlock failed is evicted.
- Not moved: the Send API reconciles (rewritten by the 6.4 cleanup first), template refresh and receipt retry (removed by
  it), keep-warm and the idempotency purge (per instance / in-process).

## D-055 — The session-timer reconcile and the person deletions have no tick any more: the platform's product-job scheduler is their one scheduler (Unified Workflow Phase 5.4 v3, 2026-10-12)

- **Why:** owner, 2026-10-11/12: remove the product-side `@Scheduled` code once each job's migration is proven. Proven in
  production (D-054): both ran on the platform every slot from 2026-10-11 20:35-20:40Z (DONE; the session-timer reconcile
  answers DISABLED while `DC_SESSION_TIMERS_PRODUCT_OWNED` is off, as its tick did); the scheduler itself was proven on
  Tair and Big Boss with the same code (one run per slot, duplicates, retries, restarts of the product and the platform).
- **What:** the ticks of `session_timers` and `person_deletions` (and their `*Here()` bodies) are removed; both are
  `asPlatformOnly()` - owned by the platform whatever `DC_PLATFORM_JOBS` says. The watchdog here runs a job the platform
  stopped calling (a degraded mode, not a scheduler; a lab platform needs `PRODUCT_JOBS_ENABLED=true`).
- **Kept:** `weekly_goals` keeps its Sunday 06:00 UTC tick (a no-op while `DC_PLATFORM_JOBS` lists it) until its first
  platform slot, 2026-10-18 06:00 UTC, has run - its retirement follows. Also kept: the Send API reconciles (D-053),
  keep-warm and the idempotency purge (per instance), the platform-jobs maintenance. qa_probe stays opt-in.
- **Rollback:** revert this commit (the ticks come back, owned by the list as in D-054).

## D-056 — Reading his Google Calendar works again; a meeting is told, never a refusal (2026-10-13)

- **Why:** found in the Google verification E2E (2026-10-12). Every read of a father's calendar answered `400 Bad
  Request`, because the URL was built with `URLEncoder` and passed as a String to `RestTemplate.exchange`, which encodes it
  again (`%3A` → `%253A`). So since the calendar was added, free-slot suggestions never saw his meetings, and the
  conflict check before booking never ran ("Failed to check calendar conflicts … Proceeding with scheduling").
  Writing (create, move, cancel) was never affected.
- **What:**
  - Both reads pass the already-encoded URL as a `URI`, so it is encoded once. A read now takes up to 250 events
    instead of 50, so 14 busy days fit.
  - One rule decides what takes up his time (`GoogleBusyTime`): only a timed event he is in. These never count:
    - all-day events (a birthday, a holiday);
    - events marked "free";
    - cancelled events;
    - invitations he declined;
    - Dad Coach's own events. New events carry the private property `dadCoach=1`; older ones are recognised by
      Dad Coach's own descriptions (Hebrew before and after 2026-10-07, and English). So a leftover event, a second child joining, or a session moving onto part of its
      own time is never "a meeting".
- **A meeting doesn't refuse a booking.**
  - Production never refused one: the check never ran. D-007 keeps the calendar optional.
  - The session is booked with its event, the result says `calendar_conflict: true`, and the confirmation written
    in code (D-036) adds a line after its first one: "שים לב: ביומן שלך יש כבר משהו בשעה הזאת. רוצה שנמצא שעה
    אחרת?" This also applies to the second child of a booking made a moment ago, whose call rebuilds the whole
    confirmation.
  - No prompt change.
  - Review: a hard refusal would have been new to fathers. The prompt doesn't know the code, and there was no way to
    book anyway.
- **Moves:** `reschedule_quality_time` checks the date rules (`checkBookable`: ONLY_THIS_WEEK, TOO_FAR_AHEAD) before it
  cancels the old session. Cancelling deletes the old Google event, and a rollback can't bring it back, so a refused move
  leaves the session and its event exactly as they were.
- **Tests:**
  - `CalendarBusyTimeTest` runs against a fake Google as strict as Google: 400 when `timeMin`, decoded once, isn't
    RFC 3339. The default fake checks this too.
  - Each test fails on its own mutation: double encoding, the date check after the cancel, and own events counted as
    meetings.
- **Not changed:** the other calls (create, patch, delete, exists) still pass a String URL. Their only encoded part is
  the calendar id, which is always `primary` (nothing ever stores another one).
- **Rollback:** revert this commit. Reads fail silently again, and nothing else changes.

## D-057 — A refused access token is forgotten at once, so settings never say "connected" for a dead grant (2026-10-13)

- **Why:** found in the Google verification E2E (2026-10-12). After the grant was revoked outside Dad Coach (then: a Tair
  disconnect, fixed by Tair D-115; also possible: the father removes access in his Google Account), Google refused the
  cached access token with 401 before it expired. Bookings answered CALENDAR_RECONNECT_REQUIRED, but the refresh token was
  kept, so settings said "מחובר" for up to an hour, until the token expired and the refresh met invalid_grant.
- **What:**
  - A 401 on a cached, unexpired token, on creating an event or on reading the calendar, forgets the access token.
  - Creating an event retries once, which refreshes the token: if the grant is dead, Google answers invalid_grant, the
    father is marked "reconnect" (D-022) and settings say so at once; if only the token was refused, the event is
    written.
  - A 401 on a token that was just refreshed, and any 403, still mean reconnect, as before.
  - Forgetting is a targeted update (`forgetAccessToken`: only that token, only if it's still stored), never a save of
    a possibly stale copy. Review: a slots read holds a detached father and could have put back tokens a reconnect had
    just replaced.
- **Tests:** `CalendarReconnectTest`, three new tests (dead grant, live grant, read). Each fails without the change.
- **Rollback:** revert. Settings go back to lagging behind for up to an hour.

## D-058 — A morning reminder claims its day before it is sent, so two sessions on one day never send two (2026-10-13)

- **Why:** architecture audit D-1. Two sessions on one day wake the morning twice, both at 08:00. The tool-only turn
  (D-049) skips when every session it names was already reminded today, but that mark was written only after the
  callback's send (`SessionTimerCard.sent`), and the platform releases the conversation before it calls back. Both turns
  could pass the card before either callback, and both callbacks sent. Prod had no organic case yet (one real morning
  ever); the race is real in tests.
- **What:**
  - The scheduled callback claims today for the morning's sessions in one atomic update, right before the send:
    `morning_reminded_on = today WHERE id IN (...) AND morning_reminded_on IS DISTINCT FROM today`. Nothing claimed:
    nothing is sent, the row is FAILED `ALREADY_REMINDED_TODAY` (a replay answers the same) and the platform is told
    DROPPED.
  - When the message was provably not handed over (the platform refused it, the request could not be built, the
    platform said FAILED), the claim is given back: the sessions hold their earlier value, as before, when nothing was
    marked.
  - The follow-up keeps its mark after the send (unchanged).
  - The claim locks its rows in id order (no deadlock between two mornings); giving it back is best effort. The admin's
    failure counts and lists leave the skipped morning out (it is no failed delivery).
  - Accepted: if the winning morning is then refused, the one it skipped does not go either (the same request would
    almost surely be refused the same way).
- **Tests:** `SessionTimerCardParityTest.racingMorningsSendOne` (two concurrent callbacks after two cards: one send;
  fails without the change), `claimIsAtomic` (four concurrent claims, 20 rounds: one wins) and
  `refusedMorningGivesTheClaimBack`. Independent review: APPROVE; its non-blocking points are fixed.
- **Rollback:** revert. Two mornings on one day can both go again.

## D-059 — The platform reads family_context through its generic HTTP provider; Dad Coach answers the agent's view (2026-10-13)

- **Why:** architecture effort P1. The platform still held Dad Coach-only Java (a tool client, a context client and
  `FamilyContextProvider`, which rebuilt our family_context into the camelCase view the agent reads). Tair and Big Boss
  use the platform's generic HTTP tools and providers, configured as data. Moving Dad Coach onto the same path lets the
  platform delete its Dad Coach Java.
- **What:**
  - `POST /api/context/agent_family_context` answers the agent's view: exactly what `FamilyContextProvider` built from
    `family_context` (isNewUser, hasProfile, father{displayName, locale, timezone, preferredCoachingTime,
    googleCalendarConnected, welcomeStep}, children[{childId, name, age, gender, interests}], hasMultipleChildren,
    childCount, hasGoogleCalendarConnected; `config.includeChildren=false` leaves the children out). That mapping never
    found a calendar flag, a coaching time or a welcome step in our answer, so they stay false/null: the agent sees what
    it saw before. `family_context` itself is unchanged (the platform's old path reads it until its Java is deleted).
  - The tools accept every identity the platform's old client accepted: any `{channel}:` prefix, with or without
    the plus (the generic tool sends the platform identity as is). A bare number (a father id) is still refused.
  - One difference, on purpose: when this endpoint fails, the platform now reports the failure; its old path showed
    the agent a brand-new father (isNewUser) and could start onboarding someone we know.
  - The AI tools need nothing new: the generic tool's envelope (`whatsapp:+E164`, every schema parameter with missing
    ones as null, an `execution_context`) is the same call as the old client's. A contract test proves it.
- **Tests:** `ContextProvidersTest` (agent view, unknown/deleted father = new user), `ToolsTest`
  `theGenericEnvelopeIsTheSameCallAsTheDadCoachClientEnvelope` (all nine live tools, both envelopes, equal answers) and
  `theActorIsOnlyTheWhatsAppNumberNeverANumericId` (channel identities).
- **Rollback:** the endpoint is additive; the platform's rollback migration points family_context back to its Java path.
## D-059 — The model stops arming the session timers: Dad Coach is their one owner (D-050 "Next", 2026-10-13)

- **Why:** architecture audit D-2. Since D-050 Dad Coach arms every booked, moved or joined session's timers after the
  turn and cancels a closed session's (`InboundMessageHandler.confirmTimers`), cancels them from the dashboard and the
  "done" button, and heals them every 10 minutes (`DC_SESSION_TIMERS_PRODUCT_OWNED`, now on in production). The model
  still armed and cleared them too, by two REQUIRED rules: the same timers, twice, with tool calls in every booking turn.
- **What (provisioning, `dad-coach-3`):**
  - The three timer transitions get `onEvent: dc.<transition key>`: hidden from the prompt and refused for the model's
    `schedule_state_transition`; product arming is unchanged (platform Phase 5.3 §3.2, `PRODUCT_TIMERS_ANY_STATE=true`).
  - ACTIVE_COACHING loses the rules `arm-timers-after-booking` and `clear-timers-when-closed`, the tools
    `schedule_state_transition` and `cancel_scheduled_transition` (no other SCHEDULED transition is visible), and the
    phrases that told it to arm or clear timers. Nothing else in the prompt changes.
- **Same timers, same times:** the model copied the scheduling result's `timers`, which are `SessionTimerPlanner`'s;
  Dad Coach arms exactly those after the turn.
- **Production check before (2026-10-13, `DC_SESSION_TIMERS_PRODUCT_OWNED=true` live 11:25 UTC, scratch +1999 father,
  no model call):** two sessions booked by tool (nothing armed) - the 11:30 reconcile armed all five timers at exactly the
  scheduling result's times; cancelled on his page - its two timers CANCELLED at once and not re-armed by the next pass;
  a third session confirmed done on his page - its follow-up CANCELLED. The one real conversation had no upcoming
  session (no pending timer), so nothing of his changed.
- **Code:** a turn that ends without a checked reply (suppressed, blank, already answered, the not-Hebrew fixed line, the
  dashboard card) now arms or clears its sessions' timers too (`InboundMessageHandler.timersAfterTurn`); before, only the
  model did on those paths. A turn the platform failed is left to the reconcile (it may still hold the conversation).
- **Tests:** `test_manifests.test_only_dad_coach_arms_the_session_timers`; `ArmedTimersTest`
  `aTurnWithoutAReplyStillArmsItsTimers` and `aTurnEndingInTheFixedLineStillClearsItsTimers` (both fail without the
  code). Independent review: APPROVE; its points taken (these paths, the transitions' text, the rollback note).
- **Rollback:** revert this commit and provision again (the model arms again; Dad Coach's arming stays harmless: the
  platform keeps one timer per key and session). `DC_SESSION_TIMERS_PRODUCT_OWNED` and the platform's
  `PRODUCT_TIMERS_ANY_STATE` must stay on while this is in place - turn either off only after reverting it.
