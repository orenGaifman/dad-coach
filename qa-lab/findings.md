# Dad Coach lab findings

| # | date | found in | finding | status |
|---|---|---|---|---|
| 1 | 2026-10-07 | before (prod workflow v1 + main backend) | A father without Google Calendar cannot book any session ("calendar not connected") - the whole loop (book → timers → reminders → follow-up → complete → belts) never starts. | backend fix D-007 (WS-A) |
| 2 | 2026-10-07 | before | `dashboard_url` sends the father to https://dadcoach.app/dashboard?fatherId=N - a third-party site, with his internal id. | backend fix D-006 (WS-A) |
| 3 | 2026-10-07 | before | Every message starts with "❤️ dad_3:" - the platform prefixes the worker's display name, which was its key. | fixed: worker name "Dad Coach" in platform-resources.json |
| 4 | 2026-10-07 | before | Markdown `**bold**` reaches WhatsApp raw. | fixed: conversationStyle names WhatsApp formatting |
| 5 | 2026-10-07 | v2 | Asked to change this week's goal (2→4 h), the coach called no tool and answered "נעדכן ל-4 שעות" - a false confirmation. | v3: REQUIRED rule goal-change-request (1/3 correct, 2/3 ignored the request - distracted by finding 1); v4 wording "set together at Sunday's check-in, nothing changes by itself" - re-measure after D-007 |
| 6 | 2026-10-07 | v2 | Returning father (profile + child exist) writes first: no re-onboarding, straight to coaching, offers this week's goal. | pass |
| 7 | 2026-10-07 | v1/v2 | "It happened" for a session that was never booked has nothing to record - the father's real time with his child is lost. | open: a log_quality_time tool (cross-repo) |
| 8 | 2026-10-07 | v2 | prompt-preview: one-shot states 16.0k → 10.2k characters, onboarding 27.4k → 21.5k, hub 61.5k → 53.4k; re-provisioning is a no-op. | pass |
