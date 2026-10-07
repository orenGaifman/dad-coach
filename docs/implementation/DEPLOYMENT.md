# Dad Coach — DEPLOYMENT

Names only — never values. Secrets live on Render (and the owner-local `~/.config/dad-coach/production-secrets.env`).

## Topology (CURRENT)
- Render web service `dad-coach` — builds the ROOT `Dockerfile` (Java 21, Spring Boot) on every push to `main`;
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
| BELT_IMAGES_BASE_URL | no | default WEB_BASE_URL/belts |
| WORKFLOW_PLATFORM_ENABLED | yes (`true`) | platform-only (D-001) |
| WORKFLOW_PLATFORM_BASE_URL | yes, https | platform |
| WORKFLOW_PLATFORM_API_KEY | yes, ≥24 | integration key bound to the product |
| WORKFLOW_PLATFORM_WORKER_KEY | yes (`dad_3`) | caller-selected worker |
| WORKFLOW_PLATFORM_WORKFLOW_KEY | yes (`dad-coach-3`) | caller-selected workflow |
| TOOL_API_KEY | yes, ≥24, not the old public default | `/api/tools/**`, `/api/context/**` |
| WORKFLOW_PLATFORM_CALLBACK_ENABLED | yes (`true`) | scheduled-response receiver |
| WORKFLOW_PLATFORM_CALLBACK_API_KEY | yes when enabled, ≥24 | callback key (today `${tool-api.api-key}`) |
| WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME | recommended | approved template `{{1}}` for messages outside 24 h |
| WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_ACCESS_TOKEN, WHATSAPP_WEBHOOK_SECRET, WHATSAPP_VERIFY_TOKEN | yes | Meta |
| WHATSAPP_WABA_ID, WHATSAPP_API_VERSION, WHATSAPP_API_BASE_URL | no | templates / Graph version / base |
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
- **Delete a father (operator):** `DELETE /api/v1/admin/fathers/{uuid}` with `X-API-Key: $DADCOACH_ADMIN_API_KEY`
  → purged now; the platform deletion follows from the outbox (logs `Platform person deletion …`).
- **A proactive message was not delivered:** `scheduled_response_delivery` row by trigger id (status, failure
  reason: SESSION_CLOSED without a template, ENDPOINT_NOT_FOUND, Meta error).
- **Tests:** `cd backend && ./mvnw test` (Docker needed).
