# Dad Coach — father onboarding video library (plan v1) · 2026-10-07

Six short videos (≤ 60 s each, most 30–45 s) that show a father how to use Dad Coach. WhatsApp-first:
the screen *is* the father's WhatsApp, recorded from the qa-lab on the real workflow (same cast as the
site demo: אורי and נועה, 7), plus the real dashboard captured from the lab's demo tenant. Production
by WS-E with the ad's tooling (HTML film, Playwright frames, ElevenLabs VO — the site intro's voice
"amit", calm pace; captions burned in; 9:16 for WhatsApp/Instagram, 16:9 copy for the dashboard).

Where they live: the dashboard's help area and the site's FAQ (one link per answer), and the coach may
send the right one when it helps — e.g. "ברוך הבא" after onboarding, "חיבור יומן" when he asks about the
calendar. (Sending a video from the workflow is a WS-C decision; hosting with signed URLs as in Big Boss.)

Rules: masculine singular; no guilt; every screen real; every number either a product rule or an
example; no music under the VO louder than −24 LUFS; captions always on. Re-record a video whenever
the screen it shows changes.

---

## 1. "ברוך הבא" (≈ 40 s)

Shows: the first message "היי, אני רוצה להתחיל", the coach's welcome (value in two sentences + belts),
the name, one child's name and age, the confirmation, the first weekly goal (2–3 hours suggested).

VO (draft):
> ברוך הבא ל-Dad Coach. כאן, בוואטסאפ, אתה קובע כמה זמן אתה רוצה עם הילדים בכל שבוע, ואני עוזר
> שזה יקרה. בהתחלה אני שואל רק שני דברים: איך לקרוא לך, ושם וגיל של אחד הילדים. אחר כך בוחרים יעד
> לשבוע. אם אתה לא בטוח, שעתיים-שלוש זו התחלה מצוינת. זהו, אתה בפנים.

## 2. "קובעים זמן" (≈ 40 s)

Shows: booking in one message ("נועה, שלישי 17:00, שעה"), the confirmation with the week's coverage,
asking for suggestions ("מתי יש לי זמן השבוע?") and picking one of the proposed slots.

VO:
> לקבוע זמן זה הודעה אחת: שם הילד, יום, שעה וכמה זמן. למשל: נועה, שלישי חמש, שעה. אני מאשר, ואומר
> לך כמה מהיעד של השבוע כבר בלוח. לא בטוח מתי? תשאל אותי, ואציע חלונות שמתאימים לשבוע שלך.

## 3. "התזכורות" (≈ 35 s)

Shows: the three proactive moments of a session day on the lock screen — 08:00 on the day, one hour
before (with 2–3 ideas for the child's age), 30 minutes after the end "נו, איך היה לכם עם נועה?" — and
the one-word answer that confirms it and moves the belt.

VO:
> ביום של המפגש אני כותב לך שלוש פעמים, וקצר. בבוקר, כדי שתדע. שעה לפני, עם רעיון או שניים אם אין
> לך עדיין תוכנית. ואחרי, אני שואל איך היה. מספיקה מילה אחת. מה שתספר, אזכור לפעם הבאה.

## 4. "מה קורה כשמשהו מתבטל" (≈ 35 s)

Shows: "היום לא אצליח" → the coach cancels, proposes a replacement in the same week → "כן" → covered.
Also the follow-up answered "לא יצא" → the same recovery. The belt and the streak do not drop.

VO:
> משהו התבטל? קורה לכולם. תכתוב לי, או תענה "לא יצא" כשאני שואל. אני מבטל, ומציע זמן אחר באותו
> שבוע, כדי שהשבוע לא יתכווץ בשקט. ביטול לא מוריד חגורה ולא שובר את הרצף.

## 5. "הלוח שלי" (≈ 45 s)

Shows (real dashboard, WS-B): opening the login link the coach sends, the week (goal, what happened,
what is booked), the sessions with their notes, the children, the belt progress; adding a second child
(if the dashboard owns that). 16:9 and 9:16 versions.

VO:
> כל מה שקרה בוואטסאפ מחכה לך גם בלוח האישי. תבקש ממני את הקישור, ואין צורך בסיסמה. כאן תראה את
> היעד של השבוע, מה כבר קרה ומה עוד בלוח, את המפגשים עם מה שסיפרת עליהם, ואת הדרך שלך לחגורה הבאה.

## 6. "חיבור יומן" (≈ 40 s) — record only after D-007

Shows: that the calendar is optional; connecting Google Calendar from the dashboard; a booked session
appearing in his calendar; suggestions that now respect his busy times; disconnecting.

VO:
> לא חייבים לחבר יומן. הכול עובד גם בלי. אבל אם תחבר את יומן Google, המפגשים יופיעו בו, ואציע זמנים
> רק כשאתה באמת פנוי. מתחברים מהלוח האישי, בלחיצה, ואפשר לנתק מתי שרוצים.

---

## Production checklist (per video)

- [ ] Lab run of the exact flow; transcript pasted into the script; screen captured at 1080 px wide.
- [ ] VO generated (voice "amit", stability 0.5, seed fixed), checked back with speech-to-text.
- [ ] Captions = the VO, ≤ 2 lines, Assistant 700.
- [ ] Owner review; then publish and link from the FAQ / dashboard help.
