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


if __name__ == "__main__":
    tag = sys.argv[1]
    for name in sys.argv[2:]:
        try:
            globals()[name](tag)
        except Exception as ex:
            qa.note(f"SCENARIO CRASHED: {ex!r}")
            import traceback
            traceback.print_exc()
