# Dad Coach — DEPLOYMENT

Names only — never values. Secrets live on Render (and the owner-local `~/.config/dad-coach/production-secrets.env`).

## Topology (CURRENT)
- Render web service `dad-coach` — builds `backend/Dockerfile` (service root directory `backend/`) (Java 21, Spring Boot) on every push to `main`;
  health check `/actuator/health`. Profile `prod`.
- Database: Supabase Postgres 17 (`DB_URL`), Flyway on startup.
- WhatsApp: Dad Coach's own number (Meta Cloud API, WABA of its own) → webhook `https://<dad-coach>/webhook/whatsapp`.
- AI Workflow Platform: worker `dad_3`, workflow `dad-coach-3`, tenant `20082bcd-a7bf-57a8-a382-4bad32144b2f`;
  platform → Dad Coach tool/context base URL + `DAD_COACH_API_KEY` (= Dad Coach `TOOL_API_KEY`); scheduled-response
  callback route `WORKFLOW_SCHEDULEDRESPONSECALLBACK_ROUTES_1_*` (ROUTES_0 is Big Boss).
- Dashboard: WS-B (own Render service); until cut-over the Vercel app.

## Environment (backend)
| Variable | Required in prod | Purpose |
|---|---|---|
| SPRING_PROFILES_ACTIVE | yes (`prod`) | JSON logs, small pool |
| PORT | Render sets it | HTTP port |
| DB_URL, DB_USERNAME, DB_PASSWORD | yes | database |
| WEB_BASE_URL | yes, https | dashboard base: `dashboard_url` = WEB_BASE_URL/login, calendar redirects, belt images |
| ~~BELT_IMAGES_BASE_URL~~ | — | D-051: unused (the belt promotion notifier is retired) |
| WORKFLOW_PLATFORM_ENABLED | yes (`true`) | platform-only (D-001) |
| WORKFLOW_PLATFORM_BASE_URL | yes, https | platform |
| WORKFLOW_PLATFORM_API_KEY | yes, ≥24 | integration key bound to the product |
| WORKFLOW_PLATFORM_WORKER_KEY | yes (`dad_3`) | caller-selected worker |
| WORKFLOW_PLATFORM_WORKFLOW_KEY | yes (`dad-coach-3`) | caller-selected workflow |
| TOOL_API_KEY | yes, ≥24, not the old public default | `/api/tools/**`, `/api/context/**` |
| WORKFLOW_PLATFORM_CALLBACK_ENABLED | yes (`true`) | scheduled-response receiver |
| WORKFLOW_PLATFORM_CALLBACK_API_KEY | yes when enabled, ≥24 | callback key (today `${tool-api.api-key}`) |
| ~~WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME~~ | — | D-053: unused - the platform's kind→template mapping picks the template |
| PLATFORM_DELIVERY_REPORTS | no (default true since D-061, as production) | D-039: report what was delivered to the platform's conversation timeline; turn on only after the platform's Phase 3 (V123) is live |
| DC_SESSION_TIMERS_GUARD | no (default true since D-061, as production) | D-049: the hour-before reminder's platform guard may skip its AI turn when no session is there (the callback's NO_VALID_SESSION); off = the guard always proceeds. The morning / follow-up tool-only turns are switched by the workflow YAML, not by an env |
| ~~GATEWAY_HELD_REPORTS~~ | — | D-053: unused - `/held-outcome` is gone (the platform holds and reports through `/delivery-status`) |
| APP_SCHEDULING_LANES | no (default true since D-061, as production) | D-043: `true` = the WhatsApp-sending job (weekly belt promotions) keeps the single scheduler thread `scheduling-1`, the housekeeping jobs (receipt retry, template refresh, idempotency purge, platform deletions, keep-warm) run on their own pool of 3 (`scheduling-housekeeping-N`); anything but `true` (any case) = one thread runs every job, as before |
| ~~MESSAGING_MODE_LOGIN_LINK, MESSAGING_MODE_SCHEDULED, MESSAGING_MODE_REPLY, MESSAGING_MODE_FIXED_LINE, MESSAGING_MODE_BUTTON_REPLY, MESSAGING_MODE_READY_ANSWER~~ | — | D-053: unused - the platform's Send API is the only sender (no DIRECT / SHADOW) |
| MESSAGING_DELIVERY_STATUS_ENABLED | no (default true since D-053) | apply the platform's delivery reports (`POST /api/integration/channel/delivery-status`, callback key) - the only source of a message's fate since D-053; off = 404, nothing changes (the platform retries 24 h). Platform side: `WORKFLOW_CHANNELS_WHATSAPP_ROUTES_1_DELIVERYREPORTURL` |
| MESSAGING_NOT_CREATED_REPOST_WAIT | no (PT2S, at most 2 s) | D-053: a line without a ledger the platform provably did not create (503 disabled, 401/403, never written) is POSTed once more after this, then not sent - the message stays unanswered (the webhook already answered Meta 200, so Meta does not resend it) |
| MESSAGING_RECONCILE_AFTER, MESSAGING_SEND_READ_TIMEOUT_MS | no (PT5M, 12000) | D-045: when an ambiguous send is looked up by key; the Send API read timeout (platform wait 8 s + 4 s) |
| MESSAGING_LOOKUP_READ_TIMEOUT_MS, MESSAGING_RECONCILE_PASS_BUDGET | no (5000, PT15S) | D-047: the reconcile job's lookup-by-key timeout, and how long one reconcile pass may take rows (the rest next minute) - a hanging platform holds the shared scheduler thread at most ~budget + one call |
| ~~MESSAGING_MODE_BELT_PROMOTION~~ | — | D-051: unused (the belt promotion notifier is retired; the switch is gone) |
| ~~DAD_COACH_WHATSAPP_RECEIPTS_ENABLED~~ | — | D-053: unused - Meta's receipts are the platform's; a webhook payload with `statuses` is answered 200 and ignored |
| WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_ACCESS_TOKEN, WHATSAPP_WEBHOOK_SECRET, WHATSAPP_VERIFY_TOKEN | yes | Meta - receiving only since D-053: webhook signature + verify token, the access token for voice-note media; the phone number id is only checked by the startup guard |
| WHATSAPP_API_VERSION, WHATSAPP_API_BASE_URL | no | Graph version / base (voice-note media) |
| ~~WHATSAPP_WABA_ID~~ | — | D-053: unused - the templates screen shows the platform's WABA |
| JWT_SECRET | yes, ≥32 | HMAC for calendar connect links + OAuth state (historical name) |
| DADCOACH_ADMIN_API_KEY | optional (≥24 if set) | operator API `/api/v1/admin/**` |
| GOOGLE_CALENDAR_CLIENT_ID, GOOGLE_CALENDAR_CLIENT_SECRET, GOOGLE_CALENDAR_REDIRECT_URI | for calendar | OAuth (optional for fathers) |

`ProductionStartupGuard` (Render or `prod`) refuses to start when any required value above is missing, too short,
http instead of https, or a development default once committed here — the previous deploy keeps serving.

### Remove from Render (no longer read)
`DADCOACH_FEATURES_AI_AGENT_ENABLED`, `DADCOACH_DEV_ENABLED`, `DADCOACH_PROACTIVE_MESSAGES_OWNER`,
`ANTHROPIC_API_KEY`, `ANTHROPIC_BASE_URL`, `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `AI_DAILY_TOKEN_BUDGET`,
`WIZARD_DATA_ENCRYPTION_KEY`, `WORKFLOW_PLATFORM_WORKFLOW_ID`, `CORS_ALLOWED_ORIGINS`, `JWT_ISSUER`,
`WHATSAPP_PHONE_NUMBER`, `BELT_IMAGE_WHITE` … `BELT_IMAGE_BLACK`, `APP_DASHBOARD_BASE_URL` (if set), any `DADCOACH_FEATURES_*`.
After the D-053 deploy is verified (one key at a time, then a deploy): `MESSAGING_MODE_LOGIN_LINK`, `MESSAGING_MODE_SCHEDULED`,
`MESSAGING_MODE_REPLY`, `MESSAGING_MODE_FIXED_LINE`, `MESSAGING_MODE_BUTTON_REPLY`, `MESSAGING_MODE_READY_ANSWER`,
`MESSAGING_MODE_BELT_PROMOTION`, `GATEWAY_HELD_REPORTS`, `DAD_COACH_WHATSAPP_RECEIPTS_ENABLED`,
`WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME`, `WHATSAPP_WABA_ID`, `BELT_IMAGES_BASE_URL` - keep their values written down
for the rollback below.
Keep: everything in the table above (GOOGLE_* stay).

### Before merging to main (checklist for the main session)
1. Render has `WEB_BASE_URL` = an **https** dashboard URL (the guard refuses http/localhost). Until the new
   dashboard exists, the Vercel URL works (its `/login` page is WS-B's).
2. `WORKFLOW_PLATFORM_API_KEY` is ≥24 characters (the platform refuses shorter keys, so it should be).
3. A Supabase backup/PITR point exists (V23 drops tables).
4. After deploy: logs show `now at version v24`, `/actuator/health` 200, one WhatsApp turn (`workflow.call.result`
   SUCCESS, `whatsapp.turn.timing`), one tool call (`tool.execution.result`).

## Production migration plan (V23, V24) — production has 1 father (ONBOARDING), 1 child, 0 sessions/goals
- Flyway validates V1-V22 against production's history: the restored files have production's exact checksums
  (`MigrationTest` checks every row), so validation passes without the old `*:missing` ignore pattern.
- **V23** (one transaction): drops 28 tables of deleted features (D-009) — the father's rows in the wizard tables
  (onboarding_sessions, invitations, activation_records, families, preferences, ai_profiles) go with them; drops
  message_log's six V19 columns (0 rows); drops pgvector if the role may (else a notice). Kept: his father, child,
  communication_endpoints rows. Rehearsed on production's schema dump (`MigrationTest`, plus a manual run).
- **V24** creates `tool_idempotency` (empty).
- Failure of either rolls back completely (PostgreSQL DDL is transactional); the new version does not start and
  the previous one keeps serving. Rollback after success = restore the backup (the dropped tables held no data
  the product uses).

## Runbooks
- **D-053 (Unified Workflow 6.4: the platform owns delivery) - deploy, in this order (spec PHASE6_4_CLEANUP_SPEC §9.1):**
  1. The platform's P64 release is live (V141/V142, `templateData`, `/messaging/kinds`); `/messaging/kinds?workerKey=dad_3`
     lists `LOGIN_LINK ""`, `SCHEDULED ""` and the three session variants, each with a usable candidate.
  2. Platform `MESSAGING_ALLOWED_PRODUCTS` carries the whole key `dad_coach` (not a per-kind list), `MESSAGING_ENABLED=true`.
  3. Pre-deploy check: `WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME` on Render is unset or exactly `dad_coach_update_he` -
     the platform's seed maps every default message type to `dad_coach_update_he`; another value means the general
     template in use today differs from the mapping (fix the mapping first, via the platform's admin API).
  4. Read-only check (R11): no open raw-payload held row for route 1 (`whatsapp_held_outbound` with
     `outbound_message_id IS NULL AND outcome IS NULL`); if there is one, wait (the old endpoint is gone after this deploy).
  5. Deploy Dad Coach. Watch: `messaging.pending_swept` at startup and then hourly (counts); no `messaging.platform_disabled`,
     `messaging.platform_auth_failed`, `messaging.*.request_failed` (ERROR); every reply `whatsapp.delivery.result` with a
     `platformStatus`; the templates screen shows the platform's state.
  6. Then platform `WORKFLOW_CHANNELS_WHATSAPP_FORWARDSTATUSES=false` and unset `..._ROUTES_1_HELDREPORTURL` (after all
     three products), then remove the unused env above.
- **Roll back D-053:** set back BEFORE the revert deploy `MESSAGING_MODE_LOGIN_LINK|SCHEDULED|REPLY|FIXED_LINE|
  BUTTON_REPLY|READY_ANSWER=PLATFORM`, `GATEWAY_HELD_REPORTS=true`, `MESSAGING_DELIVERY_STATUS_ENABLED=true`,
  `DAD_COACH_WHATSAPP_RECEIPTS_ENABLED`, `WHATSAPP_WABA_ID` and `WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME` (their values
  of today, written down before they were removed); platform
  `WORKFLOW_CHANNELS_WHATSAPP_FORWARDSTATUSES=true` + `ROUTES_1_HELDREPORTURL`; then `git revert -m 1 <merge>` and deploy.
  No migration to undo. Check first that no row is `platform_state = 'NOT_CREATED'` (the old code would leave it pending
  without re-POSTing). Products first, platform last.
- **Kill switch since D-053:** platform `MESSAGING_ENABLED=false` / `dad_coach` out of the allow-list = links and scheduled
  messages wait and are re-POSTed while still true (then FAILED `PLATFORM_NOT_CREATED`); replies are not sent and the
  message stays unanswered (the webhook answered Meta 200 before processing: nothing resends it). Dad Coach never sends to Meta itself.
- *The D-045 / D-046 / D-048 rollout and rollback runbooks below are history (their switches are gone with D-053).*
- **Delete a father (operator):** `DELETE /api/v1/admin/fathers/{uuid}` with `X-API-Key: $DADCOACH_ADMIN_API_KEY`
  → purged now; the platform deletion follows from the outbox (logs `Platform person deletion …`).
- **A proactive message was not delivered:** `scheduled_response_delivery` row by trigger id (status, failure
  reason: SESSION_CLOSED without a template, ENDPOINT_NOT_FOUND, Meta error).
- **Roll back D-042 (the gateway's held-outcome reports):** platform env `WORKFLOW_CHANNELS_WHATSAPP_ROUTES_1_HELDREPORTURL`
  unset (reports stop) and/or Dad Coach `GATEWAY_HELD_REPORTS=false` (the endpoint answers 404, the receipt buffer keeps
  only "failed" as before; the platform retries a report for 24 h, then gives up - log noise only). Nothing to undo in
  the data. Before a **code revert** below D-042 (the old enum cannot load the new status), run once and again a few
  minutes after the revert is live:
  `UPDATE scheduled_response_delivery SET status = 'HELD' WHERE status = 'UNKNOWN';`
  V46 stays (two partial indexes, harmless).
- **Roll back D-043 (scheduler lanes):** `APP_SCHEDULING_LANES=false` (unset = on since D-061) and restart - one scheduler thread
  runs every job again. Nothing in the data. Check after turning it on: the log lines of `whatsapp.receipt` /
  `meta.templates.refreshed` show thread `scheduling-housekeeping-N`, `weekly_goal.completion` thread `scheduling-1`.
- **Platform Send API for the login link (D-045, Unified Workflow 6.3-DC step DC1) - rollout, in this order:**
  1. **Deploy Dad Coach** with nothing set (`MESSAGING_MODE_LOGIN_LINK` unset = DIRECT, `MESSAGING_DELIVERY_STATUS_ENABLED`
     unset = false). V47 is additive (seven nullable/defaulted `login_link` columns, two partial indexes). Check: logs
     `now at version v47`, `/actuator/health` 200; one dashboard link (owner asks the coach for his page) logs
     `auth.login_link.sent … delivered=true` and **no** `messaging.*` line. Behaviour is byte-identical to before.
  2. **Dad Coach** `MESSAGING_DELIVERY_STATUS_ENABLED=true` (restart). Nothing reports yet; a probe without a key gets 401.
  3. **Platform** (one env change, one restart; nothing else of the platform changes):
     - first, read-only: `GET /api/v1/worker/whatsapp/templates?workerKey=dad_3&name=dad_coach_update_he` (Dad Coach's
       integration key) → `APPROVED` (or `sync` off / no snapshot = Meta judges, as today). Spec §17 rule 2.
     - `WORKFLOW_CHANNELS_WHATSAPP_ROUTES_1_DELIVERYREPORTURL=https://dad-coach.onrender.com/api/integration/channel/delivery-status`
       (route 1 = `dad-coach`; its claim key is Dad Coach's callback key, header `X-API-Key` - no `REPORTAPIKEY` needed)
     - `MESSAGING_ALLOWED_PRODUCTS=dad_coach:LOGIN_LINK` (only this kind; any other Dad Coach kind still gets 503)
     - `MESSAGING_ENABLED=true`
     - leave `MESSAGING_AMBIGUOUS_RETRY_MAX` unset (NONE = Dad Coach's parity: a 5xx from Meta is UNKNOWN, never retried)
     - check: admin Settings → Messaging card shows enabled, the allow-list, no rows. Dad Coach is still DIRECT, so no
       `/send` arrives yet.
  4. **Dad Coach** `MESSAGING_MODE_LOGIN_LINK=SHADOW` (restart) **for at least 24 h**, with a few real links (the owner
     asking the coach for his page counts). Watch Dad Coach: `messaging.shadow.match` / `messaging.shadow.mismatch`
     (`field` hold / outcome / window / template, `product` vs `platform`) / `messaging.shadow.unavailable`; platform:
     `messaging.dry_run`. Gate to go on (spec §17 rule 4): mismatches < 1 % and **every** mismatch explained
     (expected: `window` around the 5-minute margin, N2; `hold` when the gate's state moved between the send and the
     dry run). Still exactly one message per link (`auth.login_link.sent`, one Meta send).
  5. **Dad Coach** `MESSAGING_MODE_LOGIN_LINK=PLATFORM` (restart). Watch 24 h (spec §17): Dad Coach
     `messaging.login_link.platform` (status ACCEPTED / HELD), `messaging.delivery_status … applied=true`, **zero**
     `messaging.login_link.ambiguous` / `refused` / `platform_after_direct` / `messaging.platform_auth_failed` /
     `direct_fallback` (a `direct_fallback` with `why=HTTP_503 messaging.disabled` means the platform switch is off);
     platform `messaging.send.requested`, `messaging.report.sent`; admin timeline: exactly one row
     `dashboard-link:<id>` per link, with the card's text and no link; the link row
     `SELECT delivery_status, receipt_status, platform_state, platform_status, platform_request IS NULL FROM login_link
     ORDER BY created_at DESC LIMIT 5` → SENT / NULL·DELIVERED·READ / OWNED / … / true.
- **Roll back D-045:** Dad Coach `MESSAGING_MODE_LOGIN_LINK=DIRECT` (restart) - new links go directly at once; keep
  `MESSAGING_DELIVERY_STATUS_ENABLED=true` for 48 h after the last PLATFORM send (messages the platform still owns are
  reported). Without a Dad Coach change: platform `MESSAGING_ENABLED=false` or `MESSAGING_ALLOWED_PRODUCTS` without
  `dad_coach:LOGIN_LINK` → every new link gets `503 messaging.disabled` and Dad Coach sends it itself (an ambiguous
  link still being reconciled is never sent twice: after an ambiguous attempt only the lookup by key, minutes later,
  lets Dad Coach send it itself). A **code revert** below D-045: first set `MESSAGING_MODE_LOGIN_LINK=DIRECT` and wait
  until `SELECT count(*) FROM login_link WHERE platform_state IN ('POSTING','AMBIGUOUS')` is 0 (at most 1 h after the
  last PLATFORM send: the reconcile job adopts, sends directly on "not found", or gives up as UNKNOWN) - reverted code
  would leave those links unresolved. Then revert; V47 stays (harmless); run once
  `UPDATE login_link SET receipt_status = NULL WHERE receipt_status = 'QUEUED';` (the old receipt code does not know
  QUEUED) and `UPDATE login_link SET platform_request = NULL;` (no link left in the clear). While D-045 is deployed the
  reconcile job costs one indexed `SELECT` a minute even in DIRECT (`platform_next_at`, partial index - empty in DIRECT).
- **Platform Send API for scheduled messages and the belt promotion (D-046, 6.3-DC steps DC2 / DC3)** - only after
  D-045's PLATFORM has had its 24 h, one kind at a time (each ≥ 24 h before the next), same pattern:
  1. **Deploy Dad Coach** with nothing set (both modes unset = DIRECT). V48 is additive (seven nullable/defaulted
     `scheduled_response_delivery` columns, two partial indexes). Check: `now at version v48`, health 200; the next
     morning / timer callback logs `proactive.callback.result … status=DELIVERED` and **no** `messaging.scheduled.*`
     line. Behaviour is byte-identical (the callback answer is still `{status, detail[, D-039 fields]}`).
  2. **Platform**, read-only first: `GET /api/v1/worker/whatsapp/templates?workerKey=dad_3` → `dad_coach_session_morning_he`,
     `dad_coach_session_hour_before_he`, `dad_coach_session_follow_up_he` and the general template APPROVED (a session
     template not approved = the platform takes the general fallback, as Dad Coach does today). Then
     `MESSAGING_ALLOWED_PRODUCTS=dad_coach:LOGIN_LINK,dad_coach:SCHEDULED` (restart).
  3. **Dad Coach** `MESSAGING_MODE_SCHEDULED=SHADOW` ≥ 24 h (a day of mornings, timers and daily checks). Watch
     `messaging.shadow.match` / `mismatch` with `kind=SCHEDULED`; gate as D-045 step 4.
  4. **Platform first (review M1):** `SCHEDULED_RESPONSE_CALLBACK_READ_TIMEOUT_MS=30000` (restart) **before** the switch
     below. With PLATFORM the callback waits on `/send` (up to 12 s, plus one immediate re-POST); at the default 8 s the
     platform would time out and retry the callback (it replays the row - no second send - but the turn is marked
     `CALLBACK_TIMEOUT` until the retry). The setting is shared by every callback route (Big Boss, Tair): a longer wait
     only for answers that are slow anyway. Then **Dad Coach** `MESSAGING_MODE_SCHEDULED=PLATFORM`. Watch 24 h: `messaging.scheduled.platform` (ACCEPTED / HELD /
     QUEUED), `messaging.delivery_status … applied=true`, **zero** `messaging.scheduled.ambiguous` / `refused` /
     `platform_after_direct` / `direct_fallback`; the callback answers `{"platformMessageId":…}`; admin timeline: the
     scheduled turn's draft replaced by exactly one row `callback:scheduled-transition:<triggerId>` (the platform's);
     `SELECT status, platform_state, platform_status, platform_request IS NULL FROM scheduled_response_delivery ORDER BY
     created_at DESC LIMIT 5` → ACCEPTED·DELIVERED·READ / OWNED / … / true. Any `proactive.callback` timeout on the
     platform means the read timeout above was not raised.
  5. **Belt promotion** (D-051: retired - the note below is history; no job promotes anyone since D-030 - the switch only matters if that changes): platform
     `MESSAGING_ALLOWED_PRODUCTS=…,dad_coach:BELT_PROMOTION`, and if the platform's image-host list
     (6.2b `workflow.messaging.send.image-hosts`, not in its application.yml: env `WORKFLOW_MESSAGING_SEND_IMAGEHOSTS_0=…`)
     is set it must list the host of the belt images (Dad Coach's `BELT_IMAGES_BASE_URL`, default `WEB_BASE_URL/belts`;
     empty list = any https host); else the image is refused (400, logged `messaging.belt_promotion.refused`)
     and only the text goes. Then `MESSAGING_MODE_BELT_PROMOTION=SHADOW`, then `PLATFORM`.
- **Roll back D-046:** the kind's switch to `DIRECT` (restart) - new messages go directly at once; keep
  `MESSAGING_DELIVERY_STATUS_ENABLED=true` 48 h after the last PLATFORM send. Without a Dad Coach change: the platform's
  allow-list without `dad_coach:SCHEDULED` / `:BELT_PROMOTION` (503 disabled → Dad Coach sends it itself). A **code
  revert** below D-046: first the switches to DIRECT and wait until `SELECT count(*) FROM scheduled_response_delivery
  WHERE platform_state IN ('POSTING','AMBIGUOUS')` is 0 (at most 1 h); V48 stays (harmless); `UPDATE
  scheduled_response_delivery SET platform_request = NULL;`. The new reconcile job (`ScheduledPlatformSend`, messaging
  lane) costs one indexed `SELECT` a minute, empty in DIRECT.
- **Platform Send API for the conversational lines (D-048, 6.3-DC step DC4)** - last, after SCHEDULED (and BELT_PROMOTION if
  used) had their 24 h on PLATFORM. Four switches, one kind at a time, each ≥ 24 h before the next, lowest volume first:
  `BUTTON_REPLY` → `READY_ANSWER` → `FIXED_LINE` → `REPLY` (the bulk - every coach answer). No migration.
  1. **Deploy Dad Coach** with nothing set (all four unset = DIRECT). Check: health 200; a message to the coach logs
     `whatsapp.delivery.result kind=CONVERSATIONAL_REPLY status=SENT` with **no** `via` field and no `messaging.reply.*`
     line. Behaviour is byte-identical (Meta bytes, D-039 reports).
  2. **Platform** `MESSAGING_ALLOWED_PRODUCTS=…,dad_coach:<KIND>` (restart) for the kind being moved. Keep
     `MESSAGING_AMBIGUOUS_RETRY_MAX` unset (NONE). Dad Coach's route already has its `DELIVERYREPORTURL` (D-045); reports
     of these kinds are answered `known:false` (no ledger) - expected, they close the platform's report.
  3. **Dad Coach** `MESSAGING_MODE_<KIND>=SHADOW` ≥ 24 h. Watch `messaging.shadow.match` / `mismatch kind=<KIND>`;
     expected mismatches: `hold` when the gate's state moved between the send and the dry run. A `window` mismatch is
     never expected (the hint is his message's own time) - explain each one before going on. `messaging.shadow.dropped`
     (lane full, 100 queued per kind) means only that comparisons were skipped.
  4. **Dad Coach** `MESSAGING_MODE_<KIND>=PLATFORM` (restart). Watch 24 h: `whatsapp.delivery.result via=PLATFORM
     platformStatus=ACCEPTED|HELD`, `messaging.reply.platform`; **zero** `messaging.reply.refused`,
     `messaging.platform_auth_failed`, `messaging.reply.request_failed`; `messaging.reply.ambiguous`,
     `messaging.reply.rate_limited`, `messaging.reply.resend` and `platformStatus=FAILED` rare and each explained (an
     ambiguous line is answered as "on its way" and never sent again; a FAILED or only-429 one leaves the message
     unanswered, as a refused send today; a resendable FAILED is resent once as `~r1` on Meta's redelivery);
     `messaging.delivery_status.unmatched … status=FAILED` = a reply that failed after the 5 s wait; `whatsapp.turn.timing deliveryMs`
     p95 under 3 s (the turn's thread waits at most `MESSAGING_REPLY_READ_TIMEOUT_MS`, 5 s); admin timeline: exactly one
     row per answer - the turn's own row with its delivery (REPLY as written), one `<wamid>:delivered` row in place of
     the draft (a corrected reply, the not-Hebrew line, the line instead of the card), or his message then one
     `<wamid>` / `<wamid>:reply` row (button answer, ready answer, fixed line), his message first.
  - **Never through the platform** (whatever the switches): the "something went wrong" line (sent when the platform
    cannot be reached) and the answer to "DELETE MY DATA" (his platform person is deleted at that moment).
- **Roll back D-048:** the kind's switch to `DIRECT` (restart) - lines go directly at once. Nothing to reconcile (no
  ledger): a line the platform still holds (HELD while he is on another product) is sent and recorded by the platform.
  Without a Dad Coach change: the platform's allow-list without `dad_coach:<KIND>` (503 disabled → Dad Coach sends it
  itself on the first POST). A code revert needs no data step.
- **Tests:** `cd backend && ./mvnw test` (Docker needed).


---

# Dashboard (WS-B, branch align-dashboard)


## Dashboard (WS-B)

### New Render service `dad-coach-ui` (main session creates it)

```yaml
  - type: web
    name: dad-coach-ui
    runtime: docker
    region: <same as dad-coach> # keep the proxy hop inside one region
    plan: starter
    dockerfilePath: ./frontend/Dockerfile
    dockerContext: ./frontend
    healthCheckPath: /healthz
    autoDeploy: true
    envVars:
      - { key: BACKEND_URL, sync: false }   # https://<dad-coach backend host> - nginx proxies same-origin /api to it
```

The SPA and its `/api` are one origin (nginx), so cookies are SameSite and there is no CORS. nginx serves
`/belts/<color>-belt.webp` (the WhatsApp belt-promotion images) and never falls back to the SPA for them.

### Backend env vars (service `dad-coach`)

| Var | Required | What |
|-----|----------|------|
| `DADCOACH_OPS_API_KEY` | recommended (≥ 24 chars) | Ops API (`/api/ops/bootstrap-admin`, `/api/ops/login-links`). Unset = ops surface closed. A shorter key refuses startup on Render. |
| `DADCOACH_WHATSAPP_PUBLIC_NUMBER` | yes for the "talk to the coach" buttons | Dad Coach's public WhatsApp number (E.164). Unset = no wa.me buttons. |
| `WORKFLOW_PLATFORM_ADMIN_API_KEY` | optional | Platform admin key for the admin's read-only "platform" panel. Unset = "not configured". |
| `TRAINING_MEDIA_BASE_URL` | optional | Media host for training videos. Unset = no training anywhere ("soon"). |
| `TRAINING_MEDIA_TOKEN_KEY` | with the base URL | Bunny pull zone "URL Token Authentication Key" (signed, 2 h URLs). |
| `TRAINING_MEDIA_UNSIGNED` | local only | `true` serves unsigned URLs (lab). |
| `ELEVENLABS_API_KEY` | optional (secret) | ElevenLabs speech to text for WhatsApp voice notes (D-027; the owner's key, as Big Boss's). Unset = a voice note is answered "אפשר לכתוב לי במילים?", whatever the admin's switch says. On/off is the admin's ("אינטגרציות" -> "הודעות קוליות"), not an env var. Optional: `VOICE_NOTES_MODEL` (`scribe_v2`), `VOICE_NOTES_LANGUAGE` (`heb`; blank = detect), `VOICE_NOTES_MAX_BYTES` (3 MB ≈ 25 min), `VOICE_NOTES_TIMEOUT_MS` (60000), `ELEVENLABS_BASE_URL`. |
| `DADCOACH_COOKIE_SECURE` | never in prod | Defaults to `true`; `false` only for http localhost (refused on Render). |
| `WEB_BASE_URL` | yes | Change to the `dad-coach-ui` URL at cut-over: login links, calendar OAuth return and (WS-A) `dashboard_url` use it. |
| `BELT_IMAGES_BASE_URL` | at cut-over | `<WEB_BASE_URL>/belts` (today it defaults to the Vercel app). |

Migrations: V30 (`staff_user`, `login_link`, `dashboard_session`), V31 (`training_progress`), V32
(`father_deactivation`) - additive only; expect "now at version v32" (or higher with WS-A's). V43 (`system_setting`, D-027) starts empty
(voice notes on).

### First admin (after deploy)

```sh
curl -s -X POST "$BACKEND/api/ops/bootstrap-admin" -H "X-API-Key: $DADCOACH_OPS_API_KEY" \
  -H 'Content-Type: application/json' -d '{"phone":"+972…","name":"…"}'
curl -s -X POST "$BACKEND/api/ops/login-links" -H "X-API-Key: $DADCOACH_OPS_API_KEY" \
  -H 'Content-Type: application/json' -d '{"phone":"+972…"}'      # -> {"loginUrl": ".../auth/consume#token=…"}
```

### Dashboard - local

```sh
# 1. Postgres with production's V22 schema (Flyway then applies V23+):
docker run -d --name dc-dash-postgres -e POSTGRES_DB=dadcoach -e POSTGRES_USER=dadcoach -e POSTGRES_PASSWORD=dadcoach \
  -p 55490:5432 -v "$PWD/backend/src/test/resources/db/prod-v22-baseline.sql:/docker-entrypoint-initdb.d/01.sql:ro" postgres:17-alpine
# 2. Backend (profile local, platform off) on :8491:
SPRING_PROFILES_ACTIVE=local SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55490/dadcoach \
SPRING_DATASOURCE_USERNAME=dadcoach SPRING_DATASOURCE_PASSWORD=dadcoach SPRING_FLYWAY_ENABLED=true \
SPRING_JPA_HIBERNATE_DDL_AUTO=validate SERVER_PORT=8491 WEB_BASE_URL=http://localhost:5390 \
DADCOACH_OPS_API_KEY=local-ops-key-0123456789abcdef DADCOACH_COOKIE_SECURE=false \
DADCOACH_WHATSAPP_PUBLIC_NUMBER=+19995550100 java -jar backend/target/dad-coach-backend-0.1.0.jar
# 3. SPA on :5390 (proxies /api):
cd frontend && npm ci && VITE_API_TARGET=http://localhost:8491 npm run dev
# 4. Tests: npm run build && npm test && E2E_DB_CONTAINER=dc-dash-postgres npx playwright test
```
