# Dad Coach

A WhatsApp coach that helps a father turn his week into real quality time with his children: he sets a weekly goal
in hours, books short sessions with a child, gets reminded and followed up, and earns belts (white → black) for
weeks he meets. Hebrew first, addressed in the masculine singular.

The conversation runs on the **AI Workflow Platform** (worker `dad_3`, workflow `dad-coach-3`) - Dad Coach itself
calls no model. This repository is the product: its data, its rules, the tools and context the platform's agent
uses, the WhatsApp channel (Dad Coach's own number), proactive delivery and person deletion.

## How it fits together

```
father ──WhatsApp──▶ Meta ──webhook──▶ Dad Coach ──/api/v1/worker/execute──▶ AI Workflow Platform
                                          ▲  ▲                                     │
          reply / reminders ◀── Meta ◀────┘  └── /api/tools/*, /api/context/* ◀───┘ (agent tools + context)
                                             └── /api/integration/workflow/scheduled-response (timed turns)
```

- **Inbound** (`whatsapp/inbound`): signature-checked webhook at `/webhook/whatsapp`, durable dedup of Meta ids,
  200 at once, one turn at a time per sender, the father's timezone/person ref/name on every turn; a suppressed
  reply sends nothing, a platform failure one short Hebrew line.
- **Tools** (`api/tools`, bound in dad-coach-3): `save_user_profile`, `add_child`, `schedule_quality_time`,
  `reschedule_quality_time`, `cancel_quality_time`, `complete_quality_time`, `show_available_slots`,
  `set_weekly_goal`, `get_activity_ideas`. Idempotent, actor only from the envelope's WhatsApp number.
- **Context** (`api/context`): `family_context`, `weekly_plan_context` (this Sunday-Saturday week in his timezone).
- **Proactive**: the platform owns the timers (morning / 1-hour reminders, follow-up, daily check) and calls back;
  Dad Coach delivers inside the 24-hour window or with the approved template. Dad Coach's own job: Sunday's weekly
  completion (belts) with the promotion message.
- **Deletion**: "DELETE MY DATA" or an operator delete → the platform deletes the person via its tenancy API
  (durable outbox) and Dad Coach purges his data.
- Google Calendar is optional: when connected, sessions also appear in it and busy times are avoided.

Docs: [`docs/implementation/`](docs/implementation) - INTEGRATION_SPEC, DECISIONS, TASKS, DEPLOYMENT.
The workflow itself: `docs/dad-coach-3/` (provisioned to the platform).

## Run it locally

Requirements: Java 21, Docker.

```bash
docker compose up -d postgres            # Postgres 17 on :5432 (dadcoach/dadcoach)
cd backend && ./mvnw spring-boot:run     # profile "local", port 8081; Flyway builds the schema from empty
curl localhost:8081/actuator/health
```

`docker compose up` runs postgres + the backend image (port 8080). The platform is off by default
(`WORKFLOW_PLATFORM_ENABLED=false`); to talk to it, run the platform on :8080 and set the `WORKFLOW_PLATFORM_*`
and `TOOL_API_KEY` variables (see `.env.example`). Local WhatsApp without Meta: POST signed webhook bodies to
`/webhook/whatsapp` (HMAC-SHA256 with `WHATSAPP_WEBHOOK_SECRET`).

## Tests

```bash
cd backend && ./mvnw test      # one shared Postgres Testcontainer; platform and Meta are a local HTTP stub
```

With Docker Desktop on macOS: `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock DOCKER_API_VERSION=1.44`.
No test needs a network service or a key. A red test is never "pre-existing" - find its cause.

## Deploy

Render web service `dad-coach` builds the root `Dockerfile` on every push to `main`. A deployed instance refuses
to start without its secrets (`ProductionStartupGuard`). Variables (names only): `docs/implementation/DEPLOYMENT.md`.

## Database

Flyway, `backend/src/main/resources/db/migration`. V1-V22 are exactly what production ran - never edit an applied
migration; add a new one. Version ranges for parallel work: V23-V29 backend, V30-V39 dashboard, V40-V49 site.
A fresh empty database starts from `db/baseline/V23__baseline_schema.sql` (production's schema after V23).
