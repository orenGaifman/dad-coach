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
- [x] DC-A24 Calendar connect hop verifies its HMAC (it did not). Validation: CalendarConnectTest.

## Phase 7 — Lifecycle (WS-A)
- [x] DC-A25 FatherDeletionService; deletion at any stage (D-015); purge includes tool_idempotency.
  Validation: FatherDeletionTest (self-service, mid-onboarding, operator + platform outage retried).

## Phase 8 — Tests (WS-A)
- [x] DC-A26 One shared static Testcontainer (postgres:17-alpine), FakeServers JDK HttpServer for platform + Meta,
  no @MockBean, pinned TestClock. Suite: 119 tests, 0 failures, ~20 s, no hang.

## Open / follow-ups
- [ ] DC-A22 A Google Calendar API stub test for the connected-calendar path (conflict refuses, event created,
  failure keeps the booking).
- [ ] DC-A27 Cross-repo (platform): remove the Dad Coach catalog rows/registrations of deleted tools and providers
  (greet, show_help, clarify, show_progress, get_weekly_goal_status, check_calendar, get_upcoming_quality_time,
  save_activity_idea, connect_calendar, get_dashboard_link, show_weekly_summary; calendar_context,
  quality_time_context) — `HttpToolRegistrationConfig.DAD_COACH_HTTP_TOOLS` + catalog migrations. A call to one
  now answers 404.
- [ ] DC-A28 WS-C: dad-coach-3 guidance still says booking needs Google Calendar — align with D-007/D-013/D-014.
- [ ] DC-A29 Delivery ledger (message_delivery with receipts, undelivered list for the admin) - the standard's
  §34 ledger; today only scheduled_response_delivery records outcomes.
- [ ] DC-A30 Main session: remove the env vars listed in DEPLOYMENT.md from Render after the merge; set WEB_BASE_URL.

## Production runbooks & records
(append numbered sections for every production operation: plan, rehearsal, execution record, verification)
- §1 V23/V24 plan: DEPLOYMENT.md "Production migration plan". Rehearsal: MigrationTest on production's schema
  dump and history (green, 2026-10-07). Execution: pending the merge.
