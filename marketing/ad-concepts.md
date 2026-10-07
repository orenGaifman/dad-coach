# Dad Coach — שלושה קונספטים לסרטון של 60 שניות (9:16) · v1, 2026-10-07

Audience: Israeli fathers 28–45 with children 0–12, employed, on WhatsApp all day, who already *want*
more time with the kids and keep losing it to the week (`dad-coach-web/docs/brand/TARGET_AUDIENCE.md`,
PERSONAS: Amit, David, Yoni). Tone: warm, direct, never guilt (`TONE_OF_VOICE.md`, `ANTI_GOALS.md`).
Format: 1080×1920, ~60 s, Hebrew voice-over (male, masculine singular to the father), burned-in Hebrew
captions (most people watch muted), keep text inside the central 1080×1420 safe zone.

Production (WS-E, later): an HTML film with a deterministic `render(t)`, rendered frame by frame with
Playwright, VO and music from ElevenLabs, mixed with a committed script — exactly like
`tair/marketing/ad/{film,audio}`. **The chat is the real one** (qa-lab transcripts on the real
workflow, `whatsapp-demo-flow.md` after the lab replacement), **the dashboard is the real one**
(captured from the lab with the demo tenant, never a mock-up). Re-render after every change the ad shows.

Hard rules for all three:

- **No guilt and no fear.** No "your kids are growing up without you", no crying child, no countdown
  of years. The pain is the *week*, not the father (ANTI_GOALS: "No message will use a child's
  wellbeing as leverage").
- No testimonials, user counts, stars, or invented statistics. Every number on screen is either
  the product's real rule (3 reminders per session, belts at 3/10/25…) or visibly an example.
- Fictional names only (אורי, נועה). No real children's faces; the brand's artwork (the father in a
  gi, the torii, the night sky) and the real WhatsApp/dashboard screens carry the film.
- Claims only what is built. "Google Calendar optional" only once D-007 is live.
- CTA = "מתחילים בוואטסאפ" (Meta's Click-to-WhatsApp, straight to +972 55-296-1164 with the prefilled
  text). Unlike Tair, Dad Coach has its own number and onboards anyone who writes, so a WhatsApp CTA is
  the right destination.

Every concept follows the same spine (playbook §58.1):
**pain → brand → the real chat in steps → the real dashboard → the one-time setup → punchline → CTA + motto.**

---

## Concept 1 (recommended) — "השבוע שלך, לא הכוונות שלך"

The pain as a calendar that fills itself with everything except the kids.

| t (s) | Picture | VO (draft) | On-screen text |
|---|---|---|---|
| 0–6 | A week view filling with work blocks in fast motion: ישיבה, נסיעה, עוד ישיבה. A small "זמן עם נועה" block keeps getting pushed to the next week. | "ראשון: השבוע אני אוסף את נועה מוקדם. רביעי: נו, שבוע הבא." | שבוע הבא. שוב. |
| 6–10 | The block fades. Night sky, the torii, the Dad Coach logo rises. | "תכיר את Dad Coach." | Dad Coach |
| 10–20 | Real chat (scenario 1/2): "נועה, שלישי 17:00, שעה" → "יופי, קבעתי! אזכיר לך ביום עצמו ושעה לפני." | "אתה כותב לו בוואטסאפ כמה זמן אתה רוצה עם הילדים השבוע, ומתי." | הודעה אחת |
| 20–32 | Lock screen 08:00 "היום ב-17:00 זה הזמן שלך ושל נועה 🙂" → 16:00 the 1-hour reminder with three ideas for age 7 → 18:30 "נו, איך היה לכם עם נועה?" → the father: "בנינו מבצר מכריות". | "בבוקר הוא מזכיר. שעה לפני, נותן רעיון. ואחרי? שואל איך היה." | בבוקר · שעה לפני · אחרי |
| 32–40 | Real chat (scenario 3): "היום לא אצליח" → "אין בעיה, קורה… יש חלון בשישי ב-10:00, לקבוע במקום?" | "משהו התבטל? בלי נאומים. הוא מוצא זמן אחר באותו שבוע." | בלי רגשות אשם |
| 40–48 | The real dashboard: the week bar filling, the session list, the yellow belt card. | "ובלוח האישי אתה רואה את השבוע מתמלא, וחגורה אחרי חגורה." | 3 מתוך 3 שעות (דוגמה) |
| 48–53 | The setup: the first message "היי, אני רוצה להתחיל" → name → "נועה, בת 7". | "ההרשמה? שם, ילד וגיל. זהו." | שלוש דקות. בלי אפליקציה. |
| 53–57 | Punchline: the same week view from 0–6, but now the "זמן עם נועה" block stays, glows gold, ✔. | "השבוע, זה פשוט קרה." | — |
| 57–60 | End card: logo, motto, CTA button. | "Dad Coach. הזמן שתכננת. הפעם הוא קורה." | הזמן שתכננת. הפעם הוא קורה. · [מתחילים בוואטסאפ] |

Why it works: the viewer recognizes himself in the first three seconds without being accused; the
whole product is shown as it really works (four real moments of the week); the punchline mirrors the
opening so it "clicks".

## Concept 2 — "שלוש הודעות קטנות"

Built entirely around the lock screen. The hook is a notification, because that is the product.

- 0–5 · Black lock screen, one notification lands: "היום ב-17:00 זה הזמן שלך ושל נועה 🙂". VO: "זו
  ההודעה הכי חשובה שתקבל היום."
- 5–10 · Brand: logo over the night sky. "Dad Coach. מאמן בוואטסאפ לאבות."
- 10–35 · The three messages of a session day, each as a lock-screen moment (08:00, 16:00, 18:30),
  then the one-word answer that confirms it, then a cancellation day handled kindly.
- 35–45 · The real dashboard, the belts row (white → black), "ביטול לא מוריד חגורה".
- 45–52 · Setup in three bubbles.
- 52–56 · Punchline: a week where nothing arrives — the lock screen stays empty Monday to Wednesday.
  VO: "וכשהשבוע מכוסה? שקט. הוא לא מנדנד."
- 56–60 · Motto + CTA.

Strength: the "silence" punchline is unique in the category (no app promises to message *less*).
Risk: needs the lock screen to read at feed size; test on a phone.

## Concept 3 — "החגורה" (the belt)

The belt system as the frame: the father in the gi (the brand's artwork) goes white → yellow over the
film, one completed session at a time.

- 0–6 · The white-belt father, arms crossed, a calendar behind him full of "בשבוע הבא". VO: "כל אבא
  מתחיל בחגורה לבנה."
- 6–10 · Brand.
- 10–40 · Each real chat step adds a stripe: the booking, the reminders, "נו, איך היה?", the
  replacement after a cancellation — at the third completed session the belt turns yellow (the real
  threshold).
- 40–48 · The real dashboard's progress card.
- 48–54 · Setup.
- 54–60 · Punchline: the belt is not points; it is three afternoons with נועה. Motto + CTA.

Strength: memorable visual identity, uses the artwork the brand already owns. Risk: ANTI_GOALS warn
against gamification; the VO must say the belt counts *time that happened*, not app usage, and never
show a leaderboard or comparison.

---

## Mottos (pick one; it closes the ad, the site footer and the training videos)

1. **"הזמן שתכננת. הפעם הוא קורה."** — the product's promise in five words, the same line as the
   site's hero, no guilt, about the father's own intention. **Recommended.**
2. "פחות כוונות. יותר זמן ביחד." — warm and short, but "פחות כוונות" can read as a jab at the viewer.
3. "שעה עם הילדים. בלוח. ובאמת." — concrete and rhythmic, but narrower (it sounds like a calendar app).

Recommendation: **Concept 1 with motto 1** for the cold audience (it explains the most in 60 s and the
opening/ending mirror makes it land); **Concept 2** as the retargeting cut (people who visited the site
or watched 50%+ of concept 1), because its "silence" punchline answers the objection "עוד אפליקציה
שתנדנד לי"; Concept 3 as a 15 s cutdown for Reels once the belts are shown in the real dashboard.

Cutdowns for every concept: 15 s (pain → one real chat moment → motto + CTA) and 6 s (the 08:00
notification → logo → CTA).
