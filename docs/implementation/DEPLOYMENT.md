# Dad Coach - deployment

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
| `DADCOACH_COOKIE_SECURE` | never in prod | Defaults to `true`; `false` only for http localhost (refused on Render). |
| `WEB_BASE_URL` | yes | Change to the `dad-coach-ui` URL at cut-over: login links, calendar OAuth return and (WS-A) `dashboard_url` use it. |
| `BELT_IMAGES_BASE_URL` | at cut-over | `<WEB_BASE_URL>/belts` (today it defaults to the Vercel app). |

Migrations: V30 (`staff_user`, `login_link`, `dashboard_session`), V31 (`training_progress`), V32
(`father_deactivation`) - additive only; expect "now at version v32" (or higher with WS-A's).

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
