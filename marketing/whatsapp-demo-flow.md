# Dad Coach — תסריט ההדגמה החיה (WhatsApp demo flow)

> **DRAFT bubbles from the workflow rules — to be replaced line by line with real lab transcripts
> (qa-lab) before launch.** Every coach bubble below is tagged `draft`. When a lab run produces the
> real line for the same moment, paste it here, change the tag to `lab <run-id>`, and mirror it in
> `site/src/demo/scenarios.js` in the same commit. Never "improve" a real line by hand: if the real
> line is wrong, fix the workflow, re-run the lab, and paste the new line.

**This file is the single source of truth for the site's demo.** The widget's script
(`site/src/demo/scenarios.js`) mirrors it line for line. Change this file first, then the script,
in the same commit.

Where the lines come from: `docs/dad-coach-3/dad-coach-3.workflow.yaml` (globalPrompt, ONBOARDING,
ACTIVE_COACHING sections A–E, SESSION_MORNING_REMINDER, SESSION_REMINDER_1H, SESSION_FOLLOW_UP) and
the timer policy in `backend/.../weeklyplan/SessionTimerPlanner.java` (morning reminder 08:00 local on
the session day, omitted for sessions before 10:00; reminder 1 hour before; follow-up 30 minutes after
the end). The daily check runs at 09:00 local and writes only when one of its rules applies.
Belt thresholds from `workflow/Belt.java` (white 0–2 completed sessions, yellow from 3, orange 10,
green 25, blue 50, brown 100, black 200). A cancellation never breaks the streak or the belt count.

Rules every line follows:

- The coach speaks Hebrew, first person, warm and short; the father is addressed in the masculine
  singular; children by first name. One question per message at most, none when the next step is
  clear. At most one emoji, only where natural.
- Never guilt, never shame, never "just checking in". When the week is covered, no planning nudges.
- Numbers about the week are the ones the tools return (coverage), never invented. In the demo they
  are examples; the widget says so.
- Fictional cast: the father **אורי**, his daughter **נועה** (7). Any resemblance is accidental.

Phone legend: there is one phone, **F** = the father's phone (chat header "דאד קואץ׳"). Every coach bubble opens with the identity line `❤️ דאד קואץ׳:` (the product adds it to every coach message; the widget renders it as the first line of each coach bubble and lock-screen notification, so the rows below leave it out). The father's
own messages are outgoing (green, left side in Hebrew WhatsApp); the coach's are incoming (white,
right side). A coach message marked **🔔 lock screen** is proactive (a timer or the daily check woke
the coach up): the widget first shows it as a WhatsApp notification on the locked phone, then it
lands in the chat. `—` in the buttons column means plain text; the 1-hour reminder and the follow-up
carry reply buttons (D-026). `<br>` is a line break inside one bubble; `*...*` is WhatsApp bold (the
session's day and time, D-032).

Captions: rows of kind **scene** replace the caption next to the phone while the scenario plays (the
static, reduced-motion view keeps the opening scene).

Progress card: next to the phone the widget shows a small "השבוע שלך" card (goal, done, planned,
belt). Rows of kind **progress** below update it; they are not messages.

---

## Scenario 1 — "ההודעה הראשונה" (first contact, profile, first weekly goal)

Scene: יום ראשון, 21:10. אורי ראה מודעה, השאיר שם ונייד, ושלח בוואטסאפ את ההודעה שכבר הייתה כתובה.

| # | Phone | From | Time | Message | buttons |
|---|---|---|---|---|---|
| 1 | F | אורי | 21:10 | היי, אני רוצה להתחיל | — |
| 2 | F | דאד קואץ׳ `draft` | 21:10 | היי, כיף שהצטרפת 🙂 אני דאד קואץ׳.<br>אתה בוחר כמה זמן בשבוע עם הילדים, ואני דואג שזה יקרה:<br>• קובע איתך מתי<br>• מזכיר לפני<br>• שואל אחרי איך היה<br><br>איך קוראים לך? | — |
| 3 | F | אורי | 21:11 | אורי | — |
| 4 | F | דאד קואץ׳ `draft` | 21:11 | נעים מאוד, אורי. ספר לי על אחד הילדים: מה השם ובאיזה גיל? | — |
| 5 | F | אורי | 21:11 | נועה, בת 7 | — |
| 6 | F | דאד קואץ׳ `draft` | 21:11 | רק מוודא: אתה אורי, ויש לך את נועה בת 7. לשמור? | — |
| 7 | F | אורי | 21:12 | כן | — |
| 8 | F | דאד קואץ׳ `draft` | 21:12 | מעולה! עכשיו בוא נקבע יעד שבועי שיתאים לך ולנועה, ומיד נכניס אותו לשבוע. | — |
| 9 | F | אורי | 21:12 | יאללה | — |
| 10 | F | דאד קואץ׳ `draft` | 21:12 | אז נועה בת 7, גיל מעולה למבצרים.<br>להתחלה אני ממליץ על 2-3 שעות בשבוע, בלי לחץ.<br>מתי בשבוע יש לך בדרך כלל זמן? | — |
| 11 | F | אורי | 21:13 | אחר הצהריים. נלך על 3 שעות | — |
| 12 | F | דאד קואץ׳ `draft` | 21:13 | קבעתי יעד: 3 שעות השבוע 💪<br>יש חלון *ביום שלישי ב-17:00*.<br>לקבוע שם שעה עם נועה? | — |
| p | — | progress | — | goal 3h · done 0 · planned 0 · belt לבן | — |

Takeaway: שלוש דקות בוואטסאפ: שם, ילדה אחת, יעד. בלי להוריד אפליקציה ובלי טופס.

Faithful to: ONBOARDING (the approved welcome list, D-032 - belts come after the first session; one question at a time; name,
then one child's name and age; summarize and confirm before saving — the REQUIRED behavior rule; the
closing line is the workflow's own example), then ACTIVE_COACHING B "first goal" (reference the child's
age, suggest 2–3 hours, ask when he has time; valid range 1–7) and C (propose a concrete slot).

## Scenario 2 — "מפגש שקרה" (booking, the three reminders, "it happened", belt progress)

Scene: יום ראשון, 21:20. אורי קובע את המפגש הראשון השבוע בהודעה אחת.

| # | Phone | From | Time | Message | buttons |
|---|---|---|---|---|---|
| 1 | F | אורי | 21:20 | נועה, שלישי 17:00, שעה | — |
| 2 | F | דאד קואץ׳ `draft` | 21:20 | קבעתי 🎉 *יום שלישי 13.10 ב-17:00*, שעה עם נועה.<br>אזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.<br>השבוע: שעה מתוכננת מתוך 3. | — |
| p | — | progress | — | goal 3h · done 0 · planned 1h · belt לבן | — |
| day | — | — | — | יום שלישי | — |
| scene | — | caption | — | יום שלישי, יום המפגש. דאד קואץ׳ כותב שלוש פעמים, קצר: בבוקר, שעה לפני, ואחרי. | — |
| 3 | F | דאד קואץ׳ `draft` 🔔 lock screen | 08:00 | *היום ב-17:00* זה הזמן שלך ושל נועה 🙂 | — |
| 4 | F | דאד קואץ׳ `draft` 🔔 lock screen | 16:00 | עוד שעה הזמן שלך ושל נועה 🙂<br>יש כבר רעיון מה תעשו? | רוצה רעיונות |
| 4a | F | אורי (לחיצה) | 16:02 | רוצה רעיונות | — |
| 4b | F | דאד קואץ׳ (הודעה קבועה, `SessionButtonTaps`) | 16:02 | 3 רעיונות לשעה עם נועה:<br>• פנקייקים יחד, עם נועה במדידות ובערבוב<br>• משחק לוח לבחירת נועה<br>• סיבוב אופניים בפארק או בשכונה<br><br>תספר לי אחר כך איך היה 🙂 | — |
| 5 | F | דאד קואץ׳ `draft` 🔔 lock screen | 18:30 | נו, איך היה לכם עם נועה? | היה מעולה · לא יצא |
| 6 | F | אורי | 18:41 | היה מעולה. בנינו מבצר מכריות והיא לא הסכימה לצאת ממנו 😄 | — |
| 7 | F | דאד קואץ׳ `draft` | 18:41 | איזה כיף! מבצר שאי אפשר לצאת ממנו.<br>רשמתי את השעה עם נועה.<br>השבוע: שעה מתוך 3 💪 | — |
| p | — | progress | — | goal 3h · done 1h · planned 0 · belt לבן · עוד 2 לצהובה | — |

Takeaway: הודעה אחת קבעה את המפגש. שלוש תזכורות קטנות בזמן הנכון, ותשובה במילה אחת סגרה אותו.

Faithful to: D SCHEDULE (the confirmation line is the workflow's own example + one coverage line from
week_coverage), SESSION_MORNING_REMINDER (08:00, the workflow's own example line, no question),
SESSION_REMINDER_1H (two lines, no ideas in the text; the ideas come from the [רוצה רעיונות] button, row 4b is Dad Coach's fixed reply to the tap, catalog `ActivityIdeas` for age 6-10), SESSION_FOLLOW_UP
(30 minutes after the 18:00 end, the workflow's own example line, nothing else), D IT HAPPENED (celebrate,
no extra question because he already told what they did, complete with his notes, progress in one line in hours;
the belt is named only when one is earned - yellow at 3 completed sessions, the card shows the count).

## Scenario 3 — "משהו התבטל" (a cancelled session → no guilt, a replacement slot)

Scene: יום חמישי, 15:20. נקבעה לאורי שעה עם נועה ב-17:00, ועכשיו ישיבה בעבודה נמשכת.

| # | Phone | From | Time | Message | buttons |
|---|---|---|---|---|---|
| p | — | progress | — | goal 3h · done 2h · planned 1h · belt לבן | — |
| 1 | F | אורי | 15:20 | היום לא אצליח עם נועה, נתקעתי בעבודה | — |
| 2 | F | דאד קואץ׳ `draft` | 15:20 | אין בעיה, ביטלתי את היום עם נועה.<br>כדי לא לאבד את הזמן השבוע, יש חלון *ביום שישי ב-10:00*.<br>לקבוע במקום? | — |
| p | — | progress | — | goal 3h · done 2h · planned 0 · belt לבן | — |
| 3 | F | אורי | 15:24 | כן, שישי בבוקר מעולה | — |
| 4 | F | דאד קואץ׳ `draft` | 15:24 | קבעתי 🎉 *מחר, יום שישי 16.10 ב-10:00*, שעה עם נועה.<br>אזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.<br>השבוע מכוסה. | — |
| p | — | progress | — | goal 3h · done 2h · planned 1h · belt לבן | — |

Takeaway: בלי "חבל" ובלי "שוב?". הביטול לא הקטין את השבוע: דאד קואץ׳ הציע זמן אחר מיד, ואורי אמר כן. ביטול גם לא שובר את הרצף.

Faithful to: D CANCEL (cancel, clear the timers, and when week_coverage shows a gap propose a concrete
replacement in the same week — the workflow's own example wording), the PROHIBITED "No shaming" rule,
SCHEDULE confirmation line, "covered" said only because the result's is_covered is true. The same
recovery happens when he answers the follow-up with "לא יצא" (IT DID NOT HAPPEN).

## Scenario 4 — "סיכום של יום ראשון" (Sunday check-in: last week and this week's goal)

Scene: יום ראשון, 09:00. שבוע חדש מתחיל, ועוד אין לו יעד.

| # | Phone | From | Time | Message | buttons |
|---|---|---|---|---|---|
| p | — | progress | — | goal — · done 0 · planned 0 · belt צהוב | — |
| 1 | F | דאד קואץ׳ `draft` 🔔 lock screen | 09:00 | בוקר טוב אורי 🙂<br>בשבוע שעבר: 3 שעות מתוך 3 עם נועה, כולל מבצר המכריות.<br>נשאיר 3 שעות גם השבוע, או לשנות? | — |
| 2 | F | אורי | 09:14 | בוא נעלה ל-4 | — |
| 3 | F | דאד קואץ׳ `draft` | 09:14 | סגור, 4 שעות השבוע 💪<br>• *יום שלישי ב-17:00*, שעתיים<br>• *יום חמישי ב-17:00*, שעתיים<br>לקבוע את שניהם עם נועה? | — |
| p | — | progress | — | goal 4h · done 0 · planned 0 · belt צהוב | — |
| 4 | F | אורי | 09:15 | כן | — |
| 5 | F | דאד קואץ׳ `draft` | 09:15 | קבעתי 🎉 שעתיים עם נועה בכל אחד:<br>• *יום שלישי 20.10 ב-17:00*<br>• *יום חמישי 22.10 ב-17:00*<br>אזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.<br>השבוע מכוסה. | — |
| p | — | progress | — | goal 4h · done 0 · planned 4h · belt צהוב | — |

Takeaway: פעם בשבוע, הודעה אחת: איך היה השבוע שעבר, וכמה זמן השבוע. שתי תשובות, והשבוע בלוח.

Faithful to: A2 daily check rule 2 (no goal this week, had goals before → start the weekly check-in),
B Sunday check-in steps 1–4 (reflect on previous_week with a highlight from the completion notes, the
workflow's own "keep or change" question, set_weekly_goal for this week, start planning in the same
reply), D SCHEDULE confirmation.

## Scenario 5 — "שבוע מכוסה" (a covered week → silence)

Scene: יום שני עד רביעי. השבוע של אורי כבר מתוכנן כולו, 4 מתוך 4 שעות.

The point of this scenario is **what does not arrive**. The widget shows the daily 09:00 check as
event bars outside the phone, and the locked phone with no notification.

| # | Phone | From | Time | Message | buttons |
|---|---|---|---|---|---|
| p | — | progress | — | goal 4h · done 0 · planned 4h · belt צהוב | — |
| e1 | — | event | שני 09:00 | בדיקת הבוקר: השבוע מכוסה. אין סיבה לכתוב, אז דאד קואץ׳ לא כותב. | — |
| e2 | — | event | רביעי 09:00 | עדיין מכוסה. גם היום, שום הודעה. | — |
| scene | — | caption | — | יום חמישי, 08:00. היום יש מפגש, אז יש סיבה לכתוב. | — |
| day | — | — | — | יום חמישי | — |
| 1 | F | דאד קואץ׳ `draft` 🔔 lock screen | 08:00 | *היום ב-17:00* זה הזמן שלך ושל נועה 🙂 | — |

Takeaway: כשהשבוע מכוסה, דאד קואץ׳ שקט. לא "רק בודק מה נשמע", לא "אולי עוד מפגש?". הוא כותב כשיש בשביל מה: לפני מפגש, אחריו, או כשמשהו צריך תיקון.

Faithful to: A2 rule 5 (a covered week is not a reason to write → [[SUPPRESS_RESPONSE]]), the PROHIBITED
"No pressure when covered" rule, globalPrompt "Never send just checking in messages"; the session's own
morning reminder still arrives (scenario 2, row 3). Tuesday's session (17:00–19:00) also gets its
reminders; the scenario skips them to keep the point.

---

## Lab replacement checklist (before launch)

Run each scenario in `qa-lab/` against the real workflow (same cast, same week shape) and replace each
`draft` bubble with the real line:

- [ ] 1 — first contact & first goal
- [ ] 2 — booking, morning / 1h / follow-up, "it happened"
- [ ] 3 — cancellation and replacement
- [ ] 4 — Sunday check-in (needs a completed previous week in the lab DB)
- [ ] 5 — covered week (verify the daily check suppresses on Mon–Wed: no outbound message)
