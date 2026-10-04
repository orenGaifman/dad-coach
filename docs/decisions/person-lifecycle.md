# Father deletion and the platform person lifecycle (2026-10-05)

**Decision.** Dad Coach deletes a father through the AI Workflow Platform's generic person lifecycle, like every
product: the platform owns the deletion of its own data (person, conversations, messages, executions, scheduled
turns); Dad Coach never touches platform tables. Platform contract: `docs/multi-tenancy.md` (ai-workflow-platform).

**Who the father is on the platform.** Dad Coach is a single-tenant product (tenant
`20082bcd-a7bf-57a8-a382-4bad32144b2f`, `workflow.platform.tenant-id`). Every turn now sends the father's
**person ref** - his external UUID `new UUID(0, id)` (`PersonRefs`), never his phone - so the platform's person
carries it. Persons from before (created without a ref) are registered under the ref by the deletion itself.

**Deleting.**
- *Self-service* (`DELETE /api/v1/fathers/me`) and *"DELETE MY DATA" on WhatsApp* (the public data-deletion page;
  exact phrase, from a known father; a confirmation reply): the father is DELETED at once, and a request goes into
  the outbox `platform_person_deletion` (V22) in the same transaction. The pipeline then registers his person
  INACTIVE under its ref (his scheduled turns stop), deletes it on the platform, and purges his Dad Coach data.
- *Admin* (`DELETE /api/v1/admin/fathers/{id}`): his Dad Coach data is purged at once, and the platform request goes
  into the same outbox.
- The outbox is sent after commit and retried with backoff (1, 2, 4 ... minutes up to every 6 hours) until the
  platform confirms - never dropped; from the 6th failure every retry is an ERROR log ("OVERDUE"). The phone is kept
  only until then.
- `FatherDataPurger` deletes every table of his own data, children first, skipping tables a database lacks. Kept on
  purpose: `safety_event_records` (7-year legal retention, SPEC-004), `tool_wishlist` (anonymous once he is gone),
  `api_audit_log` (security audit).

**After deletion.** A DELETED father, or a number whose platform deletion is not confirmed yet, never reaches the AI
(`DeletedSenders`), and gets no scheduled message (`PlatformUserResolver` treats him as unknown). Once deleted, the
same number writing again is a new contact with a new conversation - the old one is gone.

**Admin API.** `/api/v1/admin/**` was public (`permitAll`). It now needs `X-API-Key` = `DADCOACH_ADMIN_API_KEY`
(`AdminApiKeyAuthFilter`, role `ADMIN_API`); no key configured = closed to everyone. A JWT never opens it.
Production also refuses to start with the public development `JWT_SECRET` (`JwtSecretGuard`).
The dev page `dad-coach-web/app/dev/invite` (list/delete fathers) no longer works without the key - it is a
development tool; `scripts/test_e2e.py --admin-token` now sends the key.

**Production (2026-10-04 ~22:10 UTC):** `1dc2708` live (Flyway V22 applied). `JWT_SECRET` (63 chars) and
`DADCOACH_ADMIN_API_KEY` set on Render before the deploy (values only there and in the owner-only
`~/.config/dad-coach/production-secrets.env`). Admin API: 401 without the key for GET and DELETE, 200 with it. The
platform accepts Dad Coach's key for its own tenant only (Big Boss tenant: 403). The one Dad Coach person on the
platform is a +1999…9001 test number from 2026-10-04 - left as it is.
