# Dad Coach — TASKS.md

Statuses: [ ] TODO · [~] IN_PROGRESS · [x] DONE (validated) · [!] BLOCKED · [-] CANCELLED/NOT_REQUIRED
Rule: [x] means implemented + tested + acceptance criteria satisfied. Update before ending every session.
Work streams: DC-A backend (branch align-backend), DC-B dashboard, DC-C workflow/provisioning/lab, DC-D site.

## Phase 0 — Bootstrap & hygiene (WS-A)
- [x] DC-A01 Repo hygiene: COMPLETE, SCHEDULING, railway.toml, .kiro/, backend/Dockerfile (Render builds the root
  Dockerfile), scripts/test_e2e.py (web-invite flow), the workspace file removed; stale docs → docs/archive/;
  README describes the real system. Validation: `./mvnw package` (the Dockerfile's build step) green.
- [x] DC-A02 docker-compose (postgres + backend from the root Dockerfile) + .env.example with every variable name.
  Validation: names cross-checked against application.yml.

## Phase 1 — Dead code (WS-A)
- [x] DC-A03 Delete the local AI, memory system, missions, coaching, scheduling, notification, media, the local
  workflow engine + schedulers, ProactiveMessageOwnership, commitments, stub/dev APIs, the web wizard, magic links,
  JWT, rate-limit/idempotency filters, the old dashboard REST (workspace/quality-time/weekly-goal/activity-ideas/
  fathers-me/profile/goal/mission/child/conversation/memory/health/whatsapp-send), unbound tool handlers and the
  calendar/quality-time context providers (D-001..D-003). 533 → 140 main classes (~13.4k lines). Validation: compile + full suite.
- [x] DC-A04 Dependencies: Anthropic/OpenAI, mapstruct, springdoc, jjwt, jtokkit, caffeine, aop, resilience4j
  annotations, lombok, jqwik, wiremock removed from the pom. ANTHROPIC_API_KEY no longer needed.

## Phase 2 — DB / migrations (WS-A)
- [x] DC-A05 Restore V1-V9 byte-identical (production checksums verified); remove `ignore-migration-patterns`.
  Validation: MigrationTest.theMigrationFilesAreTheOnesProductionRan.
- [x] DC-A06 V23 drop the deleted features' tables/columns/pgvector (D-009); FatherDataPurger.OWNED updated.
  Validation: MigrationTest.productionUpgradesToTheSameSchemaAFreshInstallHas (production's schema + history).
- [x] DC-A07 Fresh install: V23 baseline + FlywayBaselineStrategy (D-011). Validation: the whole suite runs on a
  database Flyway built from empty; fresh and upgraded schemas identical.
- [x] DC-A08 V24 tool_idempotency (D-012).

## Phase 3 — Product ↔ platform integration (WS-A)
- [x] DC-A09 Platform client (Tair): workflowKey, tenantId, personRef, personName, metadata.timezone on every
  turn; correlationId = Meta message id; circuit breaker; no retry on timeout. Validation: WhatsAppWebhookTest
  (exact envelope).
- [x] DC-A10 Outbound recording `/api/v1/worker/messages/outbound` for belt promotions (D-016).
  Validation: BeltPromotionTest.
- [x] DC-A11 Suppressed/blank/duplicate reply → nothing; platform 5xx/4xx → "משהו השתבש אצלי, נסה שוב עוד רגע 🙏".
  Validation: WhatsAppWebhookTest.

## Phase 4 — Tools & context (WS-A)
- [x] DC-A12 ToolController (Tair): actor from user_id only (D-010), rate limit, idempotency/replay/409, one
  transaction per tool, DELETED fathers refused. Validation: ToolsTest (15).
- [x] DC-A13 F1: WhatsApp endpoint created with the father (save_user_profile) and ensured on every inbound and
  scheduled callback. Validation: ToolsTest, WhatsAppWebhookTest, ScheduledResponseCallbackTest.
- [x] DC-A14 D-007 booking without Google Calendar; calendar failures never undo a booking; slots without a
  calendar (D-013). Validation: ToolsTest. (With a connected calendar: unchanged code path, not exercised by a
  test - no Google stub; see DC-A22.)
- [x] DC-A15 Lab finding 7: time already spent this week recorded + completable; before this week refused (D-014).
  Validation: ToolsTest.timeAlreadySpent…, aSessionBeforeThisWeekIsRefused, thePastSessionShowsAsAwaitingConfirmation….
- [x] DC-A16 Context providers family_context / weekly_plan_context; dashboard_url = WEB_BASE_URL/login (D-006).
  Validation: ContextProvidersTest, WeeklyPlanContextBuilderTest.

## Phase 5 — WhatsApp (WS-A)
- [x] DC-A17 Inbound to the standard (§34): durable dedup, 200 first, per-sender serial executor, receipts ignored,
  20 turns/min/number, voice/media without words → fixed Hebrew line. Validation: WhatsAppWebhookTest (11),
  InboundTurnExecutorTest.
- [x] DC-A18 Belt promotion through ProactiveSender (window/template) + weekly completion job in weeklygoal/.
  Validation: BeltPromotionTest.

## Phase 6 — Security, config, observability (WS-A)
- [x] DC-A19 Ordered security chains (D-017); every public path listed, everything else 401 (incl. the old
  test-send, trigger-notification, calendar status/events, invitations, dev). Validation: SecurityRoutesTest (24).
- [x] DC-A20 ProductionStartupGuard (§19) folding JwtSecretGuard/ServiceKeyGuard; eager under lazy init.
  Validation: ProductionStartupGuardTest (production-like config starts; weak config refused, names only).
- [ ] DC-A21 Give the scheduled-response callback its own key (today `${tool-api.api-key}` on both sides), then
  make the guard require distinct tool/callback/admin keys. Needs a coordinated Render change on both services.
- [x] DC-A23 CorrelationIdFilter + structured events whatsapp.turn.timing, workflow.call.result,
  tool.execution.result, context.provider.result, proactive.callback.result, whatsapp.delivery.result.
- [x] DC-A31 Shared-number gateway claim (§33, Tair/BB contract): POST /api/integration/channel/claim
  {"phone"} → {"claimed"}; callback key always (own chain, closed when unset); a father who exists, not DELETED,
  not pending platform deletion. Validation: ChannelClaimTest, SecurityRoutesTest, ApiKeyAuthenticationFilterTest.
- [x] DC-A24 Calendar connect hop verifies its HMAC (it did not). Validation: CalendarConnectTest.

## Phase 7 — Lifecycle (WS-A)
- [x] DC-A25 FatherDeletionService; deletion at any stage (D-015); purge includes tool_idempotency.
  Validation: FatherDeletionTest (self-service, mid-onboarding, operator + platform outage retried).

## Phase 8 — Tests (WS-A)
- [x] DC-A26 One shared static Testcontainer (postgres:17-alpine), FakeServers JDK HttpServer for platform + Meta,
  no @MockBean, pinned TestClock. Suite: 124 tests, 0 failures, ~20 s, no hang.

## Open / follow-ups
- [ ] DC-A22 A Google Calendar API stub test for the connected-calendar path (conflict refuses, event created,
  failure keeps the booking).
- [ ] DC-A27 Cross-repo (platform): remove the Dad Coach catalog rows/registrations of deleted tools and providers
  (greet, show_help, clarify, show_progress, get_weekly_goal_status, check_calendar, get_upcoming_quality_time,
  save_activity_idea, connect_calendar, get_dashboard_link, show_weekly_summary; calendar_context,
  quality_time_context) — `HttpToolRegistrationConfig.DAD_COACH_HTTP_TOOLS` + catalog migrations. A call to one
  now answers 404.
- [x] DC-A28 WS-C: dad-coach-3 guidance still says booking needs Google Calendar — align with D-007/D-013/D-014.
- [ ] DC-A29 Delivery ledger (message_delivery with receipts, undelivered list for the admin) - the standard's
  §34 ledger; today only scheduled_response_delivery records outcomes.
- [x] DC-A30 Main session: remove the env vars listed in DEPLOYMENT.md from Render after the merge; set WEB_BASE_URL.
  Done 2026-10-07: 6 removed (the rest were never set); WEB_BASE_URL = dad-coach-ui, SITE_ORIGINS = dad-coach-site.

## WS-C — Workflow, provisioning, lab (main session)
- [x] DC-C01 provisioning/config/dad-coach-3-workflow.yaml is the source of truth (moved from docs/dad-coach-3); every
  state: keyed REQUIRED/PROHIBITED rules + advancedBehaviorGuidance, legacy behaviorGuidance empty; globalPrompt
  5,557 → ~2,200 chars; tone in conversationStyle; session timers fireFromAnyState. Validation: test_manifests (11).
- [x] DC-C02 provision_platform.py (Tair/Big Boss: drift baseline ~/.config/dad-coach/provision-baseline, change flow,
  publish-on-change, instance upgrade) + test_provision_*. Validation: 18 tests; a re-run is a no-op (lab + prod).
- [x] DC-C03 Worker display name "Dad Coach" (fathers read "❤️ dad_3:" on every message). Validation: lab + prod.
- [x] DC-C04 qa-lab: fake Meta + platform jar + backend jar + real model; fire() moves the AI-armed trigger to now
  (the platform's own pipeline runs it). Scenarios s1–s7, prompt_preview.py, findings.md.
- [x] DC-C05 Platform V114: weekly_plan_context from source (PR orenGaifman/ai-workflow-platform#8, merged 3626034;
  suite 4319/4319). V112 was reserved but V113 landed first.
- [x] DC-C06 Lab before/after: before = no booking possible (finding 1); after v5 = the whole core loop, web-cancel
  timers suppressed 2/2, goal change refused 3/3, returning father 1/1, onboarding sets the goal in one turn.
- [x] DC-C07 deployed_smoke_test.py (self-cleaning, 33 checks) + seed_demo_father.py + render_create_services.py.
- [ ] DC-C08 Cross-repo: deactivate the platform catalog rows of the tools/providers Dad Coach no longer handles
  (DC-A27; they are bound nowhere, a call answers 404).
- [ ] DC-C09 The morning reminder and the Sunday check-in were never fired in the lab (timing) - add lab scenarios
  that time-travel to 08:00 / Sunday and verify both.

## Production record — the alignment cut-over (2026-10-07)
1. Security hotfixes first (before any other work): dev API off (DADCOACH_DEV_ENABLED=false), test-send/debug-config
   removed, admin test console + invitation writes behind the admin key, calendar status/events need the father's
   token, connect + OAuth state HMAC-signed, trigger-notification removed, tool key rotated on both services
   (an 18-char default), constant-time compares. Commits 756d14f, 4edef1d; Vercel d4a1f70. Probes 401/403/404.
2. Backup: full pg_dump of production before V23 → ~/.config/dad-coach/backups/ (owner-only, 2.4 MB, 38 tables).
3. Platform: VIEWER_API_KEY set (the admin's read-only platform panel), PR #8 merged (V114).
4. dad-coach main → 0f36973: build failed (Render's service root is backend/, the cleanup had deleted
   backend/Dockerfile) - the old version kept serving; fc4ef06 restored it: migrations V23, V24, V30–V32, V40 applied
   ("now at version v40"), then the new version refused to start: WHATSAPP_VERIFY_TOKEN was a public development
   default (the startup guard working) → rotated → live, health UP.
5. New Render services dad-coach-ui (srv-db34hv6gekts739f34qg) and dad-coach-site (srv-db34hvl9fdbs739uuh70);
   WEB_BASE_URL/SITE_ORIGINS set; backend redeployed.
6. Provisioned production: dad-coach-3 v2 (24 rules), worker "Dad Coach", the one test instance upgraded; re-run no-op.
7. Production smoke 33/33 (closed routes, claim, real onboarding turns, tools + replay, callback + replay, dashboard
   through the UI proxy, delete through the platform outbox).
8. The Tair session wired the gateway claim (ROUTES_1_CLAIMURL/CLAIMAPIKEY) and verified it in production.
9. Obsolete env vars removed; dad-coach-web (Vercel) now only redirects (bc50e59).
10. Demo father יואב (+19995551000) seeded through the tool API (4 sessions done, 1 upcoming, yellow belt);
   the owner is a dashboard admin (ops bootstrap-admin).
11. Training library v1 uploaded to Bunny (dad-coach/training/v1), TRAINING_MEDIA_* set on dad-coach; then, on the
   owner's "everything in Hebrew" and "a male voice", v2 (brand "דאד קואץ׳" on every frame, the Hebrew identity line,
   narrator amit = male) at dad-coach/training/v2 - verified in production: 5/5 signed links play (206), unsigned 403.
12. Everything in Hebrew: worker display name "דאד קואץ׳" (dad-coach-3 v3), dashboard, login links, calendar text,
   the deletion reply (+ Hebrew deletion phrases); catalog titles.
13. The site reworked to impressive + humorous (site-humor, 09b24ea), live.
14. Final production smoke 33/33 at f35f95f; suite 168/168. Hub: https://claude.ai/artifact/A9kqDFimgpwXZ4DSmYxxZi

## Production runbooks & records
(append numbered sections for every production operation: plan, rehearsal, execution record, verification)
- §1 V23/V24 plan: DEPLOYMENT.md "Production migration plan". Rehearsal: MigrationTest on production's schema
  dump and history (green, 2026-10-07). Execution: pending the merge.


---

# Dashboard (WS-B, branch align-dashboard)


## WS-B - dashboard SPA + web/auth/ops APIs (branch `align-dashboard`)

| ID | Task | State | Evidence |
|----|------|-------|----------|
| DC-B01 | Sign-in (D-005): `login_link` (hash only, single use, 15 min), `dashboard_session` (HttpOnly SameSite=Lax `DADCOACH_SESSION`), CSRF double submit (`DADCOACH_XSRF` / `X-XSRF-TOKEN`), logout + logout everywhere, rate-limited "send me a link" (3/person, 20/client per 15 min), same answer for every number, `staff_user` (V30) | done | `AuthFlowTest` (11), `AuthUnitsTest` (6) |
| DC-B02 | Ops API behind `DADCOACH_OPS_API_KEY` (constant time, fail closed): `POST /api/ops/bootstrap-admin`, `POST /api/ops/login-links` (returned, never sent) | done | `AuthFlowTest.opsNeedsItsKey`, `staffSignIn` |
| DC-B03 | Own filter chains (`DashboardSecurityConfig`, @Order 1-3) before the legacy chain; `/api/admin/test/**` stays on the legacy admin-key chain; `CorrelationIdFilter`; `WebExceptionHandler` error shape `{code, message, correlationId, details}`; `DashboardStartupGuard` | done | `AdminAreaTest.areas`, `AuthFlowTest.unauthenticated` |
| DC-B04 | Father home built only from `WeeklyPlanContextBuilder` (goal, credited minutes, coverage, next session, awaiting confirmation, belt progress, streak, children, wa.me number) | done | `FatherAreaTest.home`, `phasesAgreeWithTheWeeklyPlan` |
| DC-B05 | Sessions: list (upcoming / awaiting / past), confirm "it happened" (+note) and cancel through the same `QualityTimeService` methods the AI tools call; booking stays in WhatsApp | done | `FatherAreaTest.confirm`, `cancel` |
| DC-B06 | Children (add/edit, add_child rules), progress (belt ladder, derived achievements, weekly history), settings (name, timezone, signed Google connect, disconnect, delete my data via the person-deletion outbox) | done | `FatherAreaTest.children/progress/settings/deleteMyData` |
| DC-B07 | Training: catalog `training/catalog.json` (4 entries, no files yet), `training_progress` (V31), Bunny-signed URLs, hidden until `TRAINING_MEDIA_BASE_URL` | done | `TrainingCatalogTest` (3), `FatherAreaTest.trainingHidden` |
| DC-B08 | Admin API: overview, fathers search, father detail, view-as home (same `HomeService`), platform read-through panel (`WORKFLOW_PLATFORM_ADMIN_API_KEY`), integrations, undelivered (scheduled + login links, 7 days), deletions, deactivate (PAUSED, V32 remembers the status) -> typed permanent delete | done | `AdminAreaTest` (5) incl. byte-for-byte view-as parity |
| DC-B09 | SPA `frontend/` (React 19, react-router 7, TanStack Query 5, Vite 6, CSS Modules + tokens, Hebrew RTL, shell/ + shared/ from Tair, view-as `ViewAsContext`/`useScreenOwner` from Big Boss): login, consume, home, sessions, children, progress, settings, training, admin (8 screens), legal pages (draft for legal review) | done | `npm run build` (tsc + vite) clean; vitest 7; Playwright 13 |
| DC-B10 | Brand: tokens from docs/design (warm neutrals, navy, earned gold), Assistant font, assets copied and recompressed - all 8 belt images under `public/belts/` with the original filenames (WhatsApp promotion images) | done | `frontend/public/` (≈550 KB) |
| DC-B11 | Infra: `frontend/Dockerfile`, `nginx.conf.template` (same-origin `/api` proxy, `/belts` never falls back to the SPA), Vite dev proxy, render.yaml snippet for `dad-coach-ui` | done | DEPLOYMENT.md |
| DC-B12 | Tests: one shared static Testcontainer (`AbstractWebIntegrationTest`) on production's V22 schema + Flyway history; Playwright e2e with SQL-seeded +1999 fathers, teardown through deactivate -> delete; screenshots 390x844@2x + 1440x900 | done | `frontend/e2e/screenshots/{phone,desktop}/` |
| DC-B13 | Cut-over: create Render service `dad-coach-ui`, set env (DEPLOYMENT.md), point `WEB_BASE_URL` and `BELT_IMAGES_BASE_URL` at it, retire the Vercel app | open (main session / owner) | |
| DC-B14 | Login links reach WhatsApp-onboarded fathers: they have no `communication_endpoints` row, so `DeliveryService` answers ENDPOINT_NOT_FOUND (also for scheduled responses) | open (WS-A: channel layer) | `AuthFlowTest.noEnumeration` records FAILED |
| DC-B15 | `GET /api/v1/calendar/connect/{fatherId}` never calls `CalendarLinkSigner.verifyConnect` - anyone can start an OAuth flow for any father id (the SPA does not use this endpoint; it gets the Google URL from its session) | open (WS-A: api/calendar) | |
