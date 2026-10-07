# Dad Coach QA lab

The whole product end to end on one machine, with the real model: the father on WhatsApp (signed webhooks in,
a fake Meta capturing everything out), Dad Coach's backend, and the AI Workflow Platform running the published
`dad-coach-3` workflow. Scheduled turns are real: `qa.fire()` moves a pending platform trigger (a session timer
the AI armed, or the daily check) to now and the platform's own pipeline runs it and calls Dad Coach back.

| | port | how |
|---|---|---|
| fake Meta (`fake_meta.py`) - captures sends to `sent.jsonl` | 9399 | `python3 fake_meta.py 9399` |
| platform (jar from a worktree of ai-workflow-platform origin/main, `PLATFORM_DIR`) | 8396 | `./run-platform.sh` |
| Dad Coach backend (jar from this tree, or `BACKEND_SRC=<worktree>/backend ./build.sh`) | 8397 | `./build.sh && ./run-backend.sh` |
| Dad Coach DB / platform DB (Docker) | 55482 / 55483 | `qa-dc-db` (pgvector/pgvector:pg17), `qa-dc-platform-db` (postgres:16-alpine) |

Local-only keys live in the run scripts; none is a real secret. The lab DB starts from the production
schema snapshot (schema only, no data); the backend runs with Flyway off.

## Bring-up
```
docker start qa-dc-db qa-dc-platform-db
python3 fake_meta.py 9399 &
./run-platform.sh > .run/platform.log 2>&1 &
./build.sh && ./run-backend.sh > .run/backend.log 2>&1 &
# once: the weekly_plan_context provider (prod-only definition, in provisioning/catalog)
curl -X POST localhost:8396/api/v1/admin/context-providers -H 'X-API-Key: qa-admin-key-0123456789abcdef' \
  -H 'Content-Type: application/json' -d @../provisioning/catalog/weekly_plan_context.provider.json
PROVISION_BASELINE_DIR=$PWD/.run/baseline PLATFORM_BASE_URL=http://localhost:8396 \
  PLATFORM_ADMIN_API_KEY=qa-admin-key-0123456789abcdef python3 ../provisioning/scripts/provision_platform.py
```

## Scenarios
`python3 scenarios.py <tag> [s1 … s6]` → `transcripts/<tag>-<scenario>.md` (see the docstring): s1 first week
(onboarding → goal → booking → 1h reminder → follow-up → completion), s2 cancel/missed recovery, s3 quiet
covered week, s4 stale timer after a cancellation, s5 goal locked, s6 returning father.
`python3 prompt_preview.py <tag>` saves every state's prompt (diff two tags to prove a provisioning change).
Driver `qa.py`: `wa(phone, text)`, `fire(phone, transition_key | 'daily')`, `timers(phone)`, `state_of(phone)`,
`psql(sql)`, `fresh_phone()` (+1999…).

Voice notes (D-027): `export ELEVENLABS_API_KEY=…` before `./run-backend.sh` (real ElevenLabs, shared owner credits -
keep it short), then `qa.voice(phone, "note.ogg")`; fake_meta.py serves the file as Meta media from `.run/media/`.
A Hebrew sample: `say -v Carmit -o n.aiff "..." && ffmpeg -i n.aiff -c:a libopus -b:a 16k -ar 16000 -ac 1 note.ogg`.

Findings: `findings.md`.
