"""Edge-case rounds (2026-10-07 night QA): non-standard conversations, each step checked against the dashboard.

    python3 edge.py <tag> e1 [e2 ...]   → transcripts/<tag>-<scenario>.md
"""
import json
import sys
import time
import uuid

import dash
import qa
from scenarios import father, father_id, local_in, time_travel_session


def snap(p, label):
    return dash.snapshot(p, label)


def sessions(p):
    st, body = dash.get(p, "/father/sessions")
    return body


def raw(p, message, label):
    """Posts a non-text message (image, sticker, audio, reaction...) as the father."""
    qa.out(f"\n**{qa.PEOPLE.get(p, p)}:** [{label}]")
    before = len(qa.sent_lines())
    msg = {"from": p.lstrip("+"), "id": f"wamid.in-{uuid.uuid4()}", "timestamp": str(int(time.time()))}
    msg.update(message)
    qa._post(p, msg)
    new = qa._settle(before, p, 90)
    qa.show(new)
    if not new:
        qa.note("no reply")
    return new


def e1(tag):
    """Messy onboarding: everything in one message, a correction, a question mid-flow, two kids, emoji only."""
    qa.start_transcript(f"{tag}-e1-messy-onboarding", "e1 · אונבורדינג מבולגן")
    p = father("רועי")
    qa.wa(p, "מה זה בכלל? מי אתם?")
    qa.wa(p, "אוקיי. אני רועי, יש לי שני ילדים - איתי בן 9 ותמר בת 4. אני עובד עד מאוחר ומרגיש שאני מפספס")
    qa.wa(p, "רגע טעות, איתי בן 10 לא 9")
    qa.wa(p, "👍")
    snap(p, "after onboarding")
    qa.wa(p, "כמה זה עולה?")
    qa.wa(p, "יאללה בוא נתחיל, מה עושים")
    qa.wa(p, "2 שעות")
    snap(p, "after goal")
    rows = qa.psql(f"select name, birth_year, age from child where father_id = {father_id(p)}") if father_id(p) else []
    qa.note(f"children in DB: {rows}")


def e2(tag):
    """Booking edge cases: a time already past, an unknown child, an ambiguous time, overlap, 6 hours."""
    qa.start_transcript(f"{tag}-e2-booking-edges", "e2 · קביעות לא שגרתיות")
    p = father("עמית")
    qa.wa(p, "היי אני עמית, אבא של שירה בת 6")
    qa.wa(p, "כן")
    qa.wa(p, "3 שעות בשבוע")
    qa.wa(p, "בוא נקבע היום ב-8 בבוקר עם שירה")
    qa.wa(p, "ומחר אחרי הגן")
    qa.wa(p, "ב-5")
    snap(p, "after tomorrow booking")
    qa.wa(p, "ותוסיף גם מחר ב-17:30 עם הבן שלי עידו, חצי שעה")
    qa.wa(p, "ובשבת נלך לים כל היום, 6 שעות")
    snap(p, "after overlap + 6h")
    qa.wa(p, "מה קבוע לי השבוע?")
    qa.show_timers(p)


def e3(tag):
    """Change of mind: book, move by an hour, cancel, 'actually keep it' - chat and dashboard must agree."""
    qa.start_transcript(f"{tag}-e3-change-of-mind", "e3 · מתחרט ומשנה")
    p = father("נדב")
    qa.wa(p, "שלום, נדב, אבא של יונתן בן 8")
    qa.wa(p, "נכון")
    qa.wa(p, "שעתיים")
    qa.wa(p, "תקבע לי מחר ב-18:00 עם יונתן שעה כדורגל בפארק")
    snap(p, "booked")
    qa.wa(p, "תזיז את זה בשעה, ל-19")
    snap(p, "moved")
    qa.wa(p, "יודע מה, תבטל את זה")
    snap(p, "cancelled")
    qa.wa(p, "רגע לא, בעצם תשאיר את זה. ב-19 כמו שאמרנו")
    snap(p, "kept")
    qa.show_timers(p)


def e4(tag):
    """Dashboard ↔ chat: cancel on the web, then ask the coach; confirm on the web, then the follow-up."""
    qa.start_transcript(f"{tag}-e4-dashboard-chat", "e4 · דשבורד ↔ צ'אט")
    p = father("אלון")
    qa.wa(p, "היי, אלון, אבא של מיקה בת 5")
    qa.wa(p, "כן")
    qa.wa(p, "3 שעות")
    qa.wa(p, "תקבע מחר ב-17:00 עם מיקה שעה, ומחרתיים ב-17:00 שעה")
    snap(p, "two booked")
    s = sessions(p)
    items = s.get("upcoming") or s.get("sessions") or s if isinstance(s, (list, dict)) else []
    qa.note(f"sessions payload keys: {list(s.keys()) if isinstance(s, dict) else type(s)}")
    up = [x for x in (s.get("upcoming", []) if isinstance(s, dict) else []) if x.get("canCancel")]
    if up:
        st, r = dash.post(p, f"/father/sessions/{up[0]['id']}/cancel")
        qa.note(f"dashboard: cancelled {up[0]['localDate']} {up[0]['localStart']} → HTTP {st}")
    snap(p, "after web cancel")
    qa.wa(p, "מה קבוע לי השבוע?")
    qa.show_timers(p)
    # a session in the past that the father confirms on the web - the coach must not ask about it again
    time_travel_session(p, 20)
    snap(p, "session in the past")
    st, home = dash.get(p, "/father/home")
    conf = home.get("awaitingConfirmation", [])
    if conf:
        st, r = dash.post(p, f"/father/sessions/{conf[0]['id']}/confirm", {"note": "בנינו מבצר כריות"})
        qa.note(f"dashboard: confirmed {conf[0]['id'][:8]} → HTTP {st} {str(r)[:200]}")
    snap(p, "after web confirm")
    qa.fire(p, "session_follow_up")
    qa.wa(p, "איך אני עומד?")


def e5(tag):
    """Emotional and off-topic: guilt, divorce, a sick child + medical question, 'stop nagging', English."""
    qa.start_transcript(f"{tag}-e5-emotional", "e5 · רגשי ומחוץ לתסריט")
    p = father("שחר")
    qa.wa(p, "היי. שחר. אבא של ליה בת 3. אני בגירושים והיא אצלי רק פעם בשבוע")
    qa.wa(p, "כן")
    qa.wa(p, "אני מרגיש אבא גרוע האמת")
    qa.wa(p, "יש לה חום 39.5 מאתמול, לתת לה אקמול או נורופן? כמה?")
    qa.wa(p, "can you speak english?")
    qa.wa(p, "תפסיק לחפור לי, אין לי כוח לזה עכשיו")
    snap(p, "after emotional")
    qa.wa(p, "סליחה, יום קשה. בוא נגדיר שעה בשבוע, זה מה שיש")
    snap(p, "after goal")


def e6(tag):
    """Transport edges: the same message twice (Meta retry), two fast messages, image, sticker, voice, reaction."""
    qa.start_transcript(f"{tag}-e6-transport", "e6 · כפולות, תמונה, קול, סטיקר")
    p = father("עידן")
    qa.wa(p, "היי אני עידן, אבא של רוני בת 7")
    qa.wa(p, "כן")
    # Meta retries the same wamid
    wamid = f"wamid.in-{uuid.uuid4()}"
    msg = {"from": p.lstrip("+"), "id": wamid, "timestamp": str(int(time.time())), "type": "text",
           "text": {"body": "שעתיים בשבוע"}}
    qa.out("\n**עידן:** שעתיים בשבוע  _(Meta delivers the same wamid twice)_")
    before = len(qa.sent_lines())
    qa._post(p, msg)
    qa._post(p, msg)
    qa.show(qa._settle(before, p, 120))
    snap(p, "after duplicate")
    # two quick messages before the first reply
    qa.out("\n**עידן:** תקבע מחר ב-17  +  **עידן:** עם רוני, שעה  _(two messages 1s apart)_")
    before = len(qa.sent_lines())
    for t in ("תקבע מחר ב-17", "עם רוני, שעה"):
        m = {"from": p.lstrip("+"), "id": f"wamid.in-{uuid.uuid4()}", "timestamp": str(int(time.time())),
             "type": "text", "text": {"body": t}}
        import threading
        threading.Thread(target=qa._post, args=(p, m)).start()
        time.sleep(1)
    qa.show(qa._settle(before, p, 150, quiet=15))
    snap(p, "after burst")
    raw(p, {"type": "image", "image": {"id": "media-1", "mime_type": "image/jpeg", "caption": "תראה מה בנינו!"}}, "תמונה + 'תראה מה בנינו!'")
    raw(p, {"type": "sticker", "sticker": {"id": "media-2", "mime_type": "image/webp"}}, "סטיקר")
    raw(p, {"type": "audio", "audio": {"id": "media-3", "mime_type": "audio/ogg; codecs=opus", "voice": True}}, "הודעה קולית")
    last_out = [e for e in qa.sent_lines() if qa.to_of(e) == p]
    raw(p, {"type": "reaction", "reaction": {"message_id": "wamid.out-x", "emoji": "❤️"}}, "ריאקשן ❤️")
    snap(p, "end")


def e7(tag):
    """A long week: several days of real use in one conversation - progress, belts, a missed session, a new goal."""
    qa.start_transcript(f"{tag}-e7-long-week", "e7 · שבוע ארוך")
    p = father("ליאור")
    qa.wa(p, "היי, ליאור. אבא של עומר בן 6 ונגה בת 9")
    qa.wa(p, "כן")
    qa.wa(p, "4 שעות")
    qa.wa(p, f"תקבע היום ב-{local_in(75)} עם עומר שעה")
    qa.fire(p, "session_reminder_1h")
    time_travel_session(p, 5)
    qa.fire(p, "session_follow_up")
    qa.wa(p, "היה טוב, שיחקנו שחמט")
    snap(p, "1 done")
    qa.wa(p, "עכשיו מחר ב-18 עם נגה שעה וחצי")
    time_travel_session(p, 5)
    qa.fire(p, "session_follow_up")
    qa.wa(p, "לא יצא, היא הייתה חולה")
    snap(p, "1 missed")
    qa.wa(p, "בוא נקבע לה מחדש ביום שישי ב-10")
    qa.wa(p, "ותגיד, למה אין לי חגורה צהובה עדיין?")
    qa.wa(p, "אפשר לשנות את היעד ל-2 שעות? 4 זה יותר מדי")
    snap(p, "end")
    qa.show_timers(p)


def e8(tag):
    """Re-test after fixes: split messages, a photo with a caption, a missed session → MISSED, a reschedule on home."""
    qa.start_transcript(f"{tag}-e8-retest", "e8 · בדיקה חוזרת אחרי תיקונים")
    p = father("עידן")
    qa.wa(p, "היי אני עידן, אבא של רוני בת 7")
    qa.wa(p, "כן")
    qa.wa(p, "שעתיים בשבוע")
    qa.out("\n**עידן:** תקבע מחר ב-17  +  **עידן:** עם רוני, שעה  _(two messages 1s apart)_")
    before = len(qa.sent_lines())
    import threading
    for t in ("תקבע מחר ב-17", "עם רוני, שעה"):
        m = {"from": p.lstrip("+"), "id": f"wamid.in-{uuid.uuid4()}", "timestamp": str(int(time.time())),
             "type": "text", "text": {"body": t}}
        threading.Thread(target=qa._post, args=(p, m)).start()
        time.sleep(1)
    qa.show(qa._settle(before, p, 150, quiet=15))
    snap(p, "after burst")
    raw(p, {"type": "image", "image": {"id": "media-1", "mime_type": "image/jpeg", "caption": "תראה מה בנינו!"}}, "תמונה + 'תראה מה בנינו!'")
    qa.wa(p, "תזיז את המפגש של מחר ל-18")
    snap(p, "after reschedule (home must not show the old one)")
    time_travel_session(p, 10)
    qa.fire(p, "session_follow_up")
    qa.tap(p, "לא יצא")
    qa.wa(p, "היא נרדמה מוקדם")
    snap(p, "after missed (MISSED, not CANCELLED)")
    qa.wa(p, "כמה זה עולה בכלל?")


def e9(tag):
    """A long relationship: a child added on the dashboard, ideas, a new child by chat, deletion in free words, then
    the exact phrase - and the dashboard after it."""
    qa.start_transcript(f"{tag}-e9-long", "e9 · שיחה ארוכה + מחיקה")
    p = father("יוסי")
    qa.wa(p, "שלום, יוסי, אבא של דניאל בן 11")
    qa.wa(p, "נכון")
    qa.wa(p, "3 שעות")
    st, r = dash.post(p, "/father/children", {"name": "אביגיל", "age": 2})
    qa.note(f"dashboard: added child אביגיל (2) → HTTP {st}")
    snap(p, "child added on web")
    qa.wa(p, "תקבע לי מחר ב-10 בבוקר עם אביגיל, שעה")
    qa.wa(p, "יש לך רעיונות מה לעשות עם ילדה בת שנתיים?")
    qa.wa(p, "ויש לי עוד בן, אגב. נועם, בן 14. הוא לא מדבר איתי הרבה")
    snap(p, "after new child by chat")
    qa.wa(p, "טעות שלי, קוראים לו נעם בלי ו׳")
    qa.wa(p, "אני רוצה למחוק את החשבון שלי, איך עושים את זה?")
    snap(p, "after deletion question")
    qa.wa(p, "מחק את המידע שלי")
    st, body = dash.get(p, "/father/home")
    qa.note(f"dashboard after deletion: HTTP {st} {str(body)[:120]}")
    rows = qa.psql(f"select count(*) from father where phone = '{p}'")
    qa.note(f"father rows left: {rows}")
    qa.wa(p, "היי?")


def e10(tag):
    """Old buttons: done on a follow-up whose session was cancelled meanwhile; a double tap; ideas after the session."""
    qa.start_transcript(f"{tag}-e10-old-buttons", "e10 · כפתורים ישנים")
    p = father("משה")
    qa.wa(p, "היי, משה, אבא של תום בן 4")
    qa.wa(p, "כן")
    qa.wa(p, "2 שעות")
    qa.wa(p, f"תקבע היום ב-{local_in(75)} עם תום חצי שעה")
    qa.fire(p, "session_reminder_1h")
    time_travel_session(p, 10)
    qa.fire(p, "session_follow_up")
    qa.tap(p, "היה מעולה")
    snap(p, "after done tap")
    # the same button again (he taps twice)
    qa.tap(p, "היה מעולה")
    qa.wa(p, "רגע, בעצם זה לא קרה, התבלבלתי עם אתמול")
    snap(p, "after 'actually it didn't happen'")


def e11(tag):
    """A child mentioned mid-coaching is added in the chat and booked right away; a correction of his name."""
    qa.start_transcript(f"{tag}-e11-new-child-in-chat", "e11 · ילד חדש בשיחה")
    p = father("יוסי")
    qa.wa(p, "שלום, יוסי, אבא של דניאל בן 11")
    qa.wa(p, "נכון")
    qa.wa(p, "3 שעות")
    qa.wa(p, "ויש לי עוד בן, אגב. נועם, בן 14. הוא לא מדבר איתי הרבה")
    snap(p, "after new child by chat")
    qa.wa(p, "טעות שלי, קוראים לו נעם בלי ו׳")
    snap(p, "after name correction")
    qa.wa(p, "תקבע לי איתו מחר ב-20:00, שעה, נצא לאכול המבורגר")
    snap(p, "after booking with the new child")


def e12(tag):
    """A saved child's name corrected in the chat - an honest answer, never a false 'updated'."""
    qa.start_transcript(f"{tag}-e12-child-correction", "e12 · תיקון שם של ילד שמור")
    p = father("יוסי")
    qa.wa(p, "שלום, יוסי, אבא של דניאל בן 11 ונועם בן 14")
    qa.wa(p, "נכון")
    qa.wa(p, "3 שעות")
    qa.wa(p, "טעות שלי, קוראים לו נעם בלי ו׳")
    snap(p, "after name correction")


def e13(tag):
    """The daily check on an uncovered week; "not this week, I'm swamped"; the next daily check must not nag."""
    qa.start_transcript(f"{tag}-e13-daily-check", "e13 · בדיקה יומית ושבוע עמוס")
    p = father("גיא")
    qa.wa(p, "היי, גיא, אבא של אלה בת 8")
    qa.wa(p, "כן")
    qa.wa(p, "3 שעות")
    qa.wa(p, "אחר כך נקבע")
    snap(p, "goal, nothing booked")
    qa.fire(p, "daily")
    qa.wa(p, "השבוע אני ממש עמוס, לא אצליח. שבוע הבא")
    snap(p, "after 'not this week'")
    qa.fire(p, "daily")
    qa.show_timers(p)


def e15(tag):
    """Three kids: a session with all of them, then one kid only; limits - 5 minutes, a 10-hour goal, 0 hours."""
    qa.start_transcript(f"{tag}-e15-family-limits", "e15 · כל הילדים + גבולות")
    p = father("אבי")
    qa.wa(p, "שלום, אני אבי. יש לי שלושה: יואב 12, מיה 9, ותום 4")
    qa.wa(p, "נכון")
    qa.wa(p, "10 שעות בשבוע")
    qa.wa(p, "טוב אז 7")
    snap(p, "after goal")
    qa.wa(p, "תקבע מחר ב-18 עם כולם, טיול אופניים שעתיים")
    snap(p, "after 'with all of them'")
    qa.wa(p, "ותוסיף ביום שישי 5 דקות עם תום לפני השינה")
    qa.wa(p, "בעצם לא חשוב, תמחק את היעד, 0 שעות")
    snap(p, "end")


def e16(tag):
    """Abroad: a father in New York this week books in his local time; the dashboard must show the right hour."""
    qa.start_transcript(f"{tag}-e16-abroad", "e16 · אבא בחו\"ל")
    p = father("רן")
    qa.wa(p, "היי, רן, אבא של שקד בת 6")
    qa.wa(p, "כן")
    qa.wa(p, "2 שעות")
    qa.wa(p, "אני בניו יורק עד שבוע הבא, אז נעשה שיחת וידאו. תקבע מחר ב-18:00 שעון ניו יורק, חצי שעה")
    snap(p, "after booking in NY time")
    qa.show_timers(p)


def e17(tag):
    """A new week (lab: last week's goal and sessions moved back 7 days): the check-in reflects on last week, asks
    "same or change?", he answers in a roundabout way; then he asks to change it again right after."""
    qa.start_transcript(f"{tag}-e17-new-week", "e17 · שבוע חדש")
    p = father("עומר")
    qa.wa(p, "היי, עומר, אבא של נויה בת 10")
    qa.wa(p, "כן")
    qa.wa(p, "3 שעות")
    qa.wa(p, f"תקבע היום ב-{local_in(75)} עם נויה שעה")
    time_travel_session(p, 5)
    qa.fire(p, "session_follow_up")
    qa.wa(p, "היה מעולה, הלכנו לגלידה ודיברנו על בית הספר")
    fid = father_id(p)
    qa.psql(f"update weekly_goal set week_start_date = week_start_date - 7, status = 'MISSED' where father_id = {fid}")
    qa.psql(f"update quality_time set scheduled_start = scheduled_start - interval '7 days', scheduled_end = scheduled_end - interval '7 days' where father_id = {fid}")
    qa.note("lab: last week = goal 3h, one hour done (moved back 7 days); no goal this week")
    snap(p, "new week, before the check-in")
    qa.wa(p, "בוקר טוב")
    qa.wa(p, "האמת השבוע הקודם היה קשה, בוא נוריד קצת. נגיד שעתיים? או אולי בעצם שלוש. לא יודע, מה אתה אומר?")
    qa.wa(p, "טוב שעתיים")
    snap(p, "after this week's goal")
    qa.wa(p, "רגע בעצם תעשה 3")


def e18(tag):
    """Hebrew only: the father writes in English and asks for English."""
    qa.start_transcript(f"{tag}-e18-hebrew-only", "e18 · רק עברית")
    p = father("David")
    qa.wa(p, "Hi, I'm David, dad of Maya, she's 6")
    qa.wa(p, "yes")
    qa.wa(p, "can you speak english please?")
    qa.wa(p, "2 hours a week")


def e19(tag):
    """The morning reminder of today's session; he answers "I'm sick today" -> cancelled with no guilt, a replacement
    offered; the 1h reminder and the follow-up of that session must not come."""
    qa.start_transcript(f"{tag}-e19-sick-today", "e19 · חולה היום")
    p = father("יניב")
    qa.wa(p, "היי, יניב, אבא של אורי בן 5")
    qa.wa(p, "כן")
    qa.wa(p, "2 שעות")
    qa.wa(p, "תקבע מחר ב-17:00 עם אורי שעה")
    qa.show_timers(p)
    qa.fire(p, "session_morning_reminder")
    qa.wa(p, "אני חולה היום, עם חום. לא אוכל")
    snap(p, "after 'sick'")
    qa.show_timers(p)
    qa.fire(p, "session_reminder_1h")
    qa.wa(p, "תודה, נקבע ביום שבת במקום")
    snap(p, "end")


def e20(tag):
    """כפתורים ישנים: מפגש שבוטל בלוח הבקרה אחרי תזכורת השעה - הוא לוחץ [רוצה רעיונות]; ואז מוחק את המידע שלו מלוח
    הבקרה וכותב שוב בוואטסאפ."""
    qa.start_transcript(f"{tag}-e20-stale-button-web-delete", "e20 · כפתור ישן ומחיקה מלוח הבקרה")
    p = father("שלומי")
    qa.wa(p, "היי, שלומי, אבא של גאיה בת 7")
    qa.wa(p, "כן")
    qa.wa(p, "2 שעות")
    qa.wa(p, f"תקבע היום ב-{local_in(75)} עם גאיה שעה")
    qa.fire(p, "session_reminder_1h")
    s = sessions(p)
    up = [x for x in s.get("upcoming", []) if x.get("canCancel")]
    if up:
        st, r = dash.post(p, f"/father/sessions/{up[0]['id']}/cancel")
        qa.note(f"ביטל את המפגש בלוח הבקרה → {st}")
    qa.tap(p, "רוצה רעיונות")
    snap(p, "אחרי לחיצה על כפתור של מפגש שבוטל")
    st, r = dash.post(p, "/father/delete-my-data", {"confirmation": "מחיקה"})
    qa.note(f"מחיקה מלוח הבקרה → {st} {str(r)[:200]}")
    st, body = dash.get(p, "/father/home")
    qa.note(f"לוח הבקרה אחרי המחיקה: {st}")
    qa.note("שורות אב: " + str(qa.psql(f"select count(*) from father where phone = '{p}'")))
    qa.wa(p, "היי, מה קורה עם המפגש של היום?")


def e21(tag):
    """שיחה ארוכה עם קפיצות נושא: שני מפגשים, רעיונות, בדיחה, חגורות, הזזה, "מה קבוע", הודעה עם שגיאות כתיב, לילה טוב."""
    qa.start_transcript(f"{tag}-e21-long-chat", "e21 · שיחה ארוכה")
    p = father("אורי")
    for t in ["אהלן, אורי פה, אבא של ליבי בת 4 ושל בן בן 8", "נכון", "4 שעות",
              "תקבע מחר ב-17:00 עם בן כדורגל שעה, ובשישי ב-10 עם ליבי שעה וחצי",
              "מה אפשר לעשות עם ילדה בת 4 שעה וחצי בלי מסכים?",
              "חחח היא בטח תרצה רק לצבוע את הקירות",
              "איך מקבלים חגורה כתומה?",
              "תזיז את הכדורגל של בן לשבת בבוקר באותה שעה",
              "מה קבוע לי השבוע בסוף?",
              "תוסיף עוד שעה עם שניהם ביחד בשבת אחהצ, נלך לגינה",
              "אחי אתה תותח, תודה",
              "רגע, כמה דקות נשארו לי ליעד?",
              "לילה טוב"]:
        qa.wa(p, t)
    snap(p, "סוף השיחה")


def e22(tag):
    """אחרי חצות: "תקבע מחר ב-17" ב-00:0x - שאלה אחת או אישור עם יום ותאריך שמתאים ללוח הבקרה."""
    qa.start_transcript(f"{tag}-e22-after-midnight", "e22 · אחרי חצות")
    p = father("טל")
    qa.wa(p, "היי, טל, אבא של רומי בן 9")
    qa.wa(p, "כן")
    qa.wa(p, "2 שעות")
    qa.wa(p, "תקבע מחר ב-17:00 עם רומי שעה")
    qa.wa(p, "כן, לחמישי")
    snap(p, "אחרי הקביעה")


def e23(tag):
    """חגורות: "איך מקבלים חגורה צהובה/כתומה?" - תשובה מהעובדות."""
    qa.start_transcript(f"{tag}-e23-belts", "e23 · חגורות")
    p = father("ניר")
    qa.wa(p, "היי, ניר, אבא של עדן בן 6")
    qa.wa(p, "כן")
    qa.wa(p, "2 שעות")
    qa.wa(p, "איך מקבלים חגורה צהובה? ואחרי זה כתומה?")


if __name__ == "__main__":
    tag = sys.argv[1]
    for name in sys.argv[2:]:
        try:
            globals()[name](tag)
        except Exception as ex:
            qa.note(f"SCENARIO CRASHED: {ex!r}")
            import traceback
            traceback.print_exc()
