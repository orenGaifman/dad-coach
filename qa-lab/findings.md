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

# Night round (2026-10-07 22:10-23:00) — edge cases, every step checked against the father's dashboard (edge.py, dash.py)

| # | where | what we saw | fix | status |
|---|---|---|---|---|
| 9 | dashboard | a session that didn't happen ("לא יצא", typed or tapped) showed as "בוטל": nothing ever set MISSED | cancelling a session whose time has passed records MISSED (tool result says so) | fixed, e8 verified |
| 10 | dashboard home | a reschedule left a "בוטל" row next to the same session at its new time | the home's week list hides cancelled sessions (the sessions page keeps them) | fixed, verified in the browser |
| 11 | chat | "תקבע מחר ב-17" + "עם רוני, שעה" (1s apart) → booked 30 min, then offered a *new* hour | prompt: a message that only adds a detail completes what was just done (reschedule it) | fixed, e8: one session, 60 min |
| 12 | chat | a photo with a caption ("תראה מה בנינו!") got a weekly status - the coach never knew a photo came | the caption reaches the coach as "[photo] …"; prompt: react, say it can't see pictures | fixed, e8 verified |
| 13 | chat | a ❤️ reaction on a coach message got "עידן, אתה איתי?" | reactions are ignored before the AI | fixed (WhatsAppWebhookTest) |
| 14 | chat | "כמה זה עולה?" → "חלק מהליווי שאתה כבר מקבל" (invented) | THE SERVICE facts in the globalPrompt (free during launch, notice before any change) | fixed, e8 verified |
| 15 | chat | a new child mentioned mid-coaching → "add him on your page" (the hub had no add_child) | add_child in the hub + rule | fixed, e11: added and booked in chat |
| 16 | chat | a saved child's name corrected → "אשתמש בשם הנכון" while the record kept the old name; one run leaked English reasoning + "{{dashboard_url}}" | rule: no tool → honest line + the real link | fixed, 2/2 |
| 17 | chat | "בעצם זה לא קרה" after [היה מעולה] → "אין לי דרך לבטל… זה לא באמת משנה" - a completion can't be undone; belts count it | open: needs an "undo completion" tool (backend + platform migration) | open |
| 18 | timers | a session cancelled on the dashboard keeps its 3 platform timers; they fire and are suppressed (one AI turn each) | - | open, low |
| 19 | chat | slots offered "היום בשעה מאוחרת" at 22:30 and a 23:40 session for a 4-year-old accepted without a word | - | open, low |
| 20 | chat | a voice note → "can't hear recordings" (Big Boss transcribes voice) | - | open, product call |
| - | harness | parallel runs shared one phone (fresh_phone from ms) → cross-talk; now random | lab only | fixed |
| 21 | chat | "7 שעות" → the coach set 3 on its own | the number is his | fixed, 2/2 |
| 22 | chat+dashboard | "עם כולם, שעתיים" → 3 sessions × 2h, the week "covered" by one ride | one block (stopgap until joint sessions) | fixed |
| 23 | all | owner rule: Hebrew only | LANGUAGE line | fixed, e18 |
| 24 | chat+dashboard | after midnight "מחר ב-17" booked Friday, the coach said "מחר (חמישי)"; after "לחמישי" booked Thursday and wrote "יום שישי 9.10" - the day was the model's own | the tool returns when_label ("היום, יום חמישי 8.10 ב-17:00"), copied; ask when "מחר" after midnight matters | fixed, e22 2/2 match the dashboard |
| 25 | chat | "איך מקבלים חגורה?" → invented "weeks in a row" | progress.next_belt / sessions_to_next_belt / belt_at_completed_sessions in the weekly plan | fixed, e23 |
| 26 | chat | new week (lab, minutes after the last one): "the goal is set and locked" while goal.exists=false | rule; lab compresses a week into minutes | low / uncertain |
| - | regression | s1-s8 after all fixes | - | pass |
