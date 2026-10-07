"""The Dad Coach lab scenarios - the real model, the real platform pipeline, the real backend.

    python3 scenarios.py <tag> [s1 s2 ...]      transcripts land in transcripts/<tag>-<scenario>.md

s1 first week      new father → onboarding → first goal → book a session ~75 min from now → timers armed →
                   1h reminder fires → session "ends" (time travel in the lab DB) → follow-up fires →
                   "it happened" → completion, progress
s2 recovery        a booked session is cancelled → no blame, a replacement offer; a missed one too
s3 quiet           the week is covered → the daily check stays silent; asking for more still works
s4 stale timers    a session is cancelled after booking → its follow-up never asks about it
s5 goal locked     a different goal number after this week's goal exists → kindly refused
s6 returning       a father whose profile + child exist writes for the first time → no re-onboarding
s8 buttons         the 1h reminder comes with [רוצה רעיונות] → ideas; the follow-up with [היה מעולה] [לא יצא] →
                   done completes with no AI turn (a second tap: already recorded); a second session → [לא יצא]
                   goes to the coach; the coach then knows what was recorded
"""
import sys
import time

import qa


def father(name):
    p = qa.fresh_phone()
    qa.PEOPLE[p] = name
    return p


def onboard(p, name="יואב", child="נועה", age="7"):
    qa.wa(p, "היי, שמעתי עליכם, אשמח להתחיל")
    qa.wa(p, name)
    qa.wa(p, f"{child}, בת {age}")
    r = qa.wa(p, "כן, נכון")
    if "?" in qa.texts(r) and "שעות" not in qa.texts(r):
        qa.wa(p, "כן")


def father_id(p):
    rows = qa.psql(f"select id from father where phone = '{p}'")
    return rows[0][0] if rows else None


def time_travel_session(p, minutes_ago_end=10):
    """Moves this father's latest UPCOMING session into the past (ended `minutes_ago_end` minutes ago)."""
    fid = father_id(p)
    qa.psql(f"""update quality_time set scheduled_start = now() - interval '{minutes_ago_end + 60} minutes',
                scheduled_end = now() - interval '{minutes_ago_end} minutes'
                where id = (select id from quality_time where father_id = {fid} and status in ('SCHEDULED','UPCOMING','PLANNED')
                            order by created_at desc limit 1)""")
    qa.note(f"lab: the session moved into the past (ended {minutes_ago_end} min ago)")


def local_in(minutes):
    t = time.localtime(time.time() + minutes * 60)
    return f"{t.tm_hour:02d}:{(t.tm_min // 5) * 5:02d}"


def s1(tag):
    qa.start_transcript(f"{tag}-s1-first-week", "s1 · השבוע הראשון")
    p = father("יואב")
    onboard(p)
    qa.wa(p, "3 שעות בשבוע נשמע טוב")
    qa.wa(p, f"בוא נקבע היום ב-{local_in(75)} עם נועה, שעה")
    qa.show_timers(p)
    qa.fire(p, "session_reminder_1h")
    time_travel_session(p)
    qa.fire(p, "session_follow_up")
    qa.wa(p, "היה מדהים! בנינו מגדל לגו ענק והיא לא הפסיקה לצחוק")
    qa.show_timers(p)
    qa.wa(p, "איך אני עומד השבוע?")


def s8(tag):
    qa.start_transcript(f"{tag}-s8-buttons", "s8 · כפתורים")
    p = father("גיל")
    onboard(p, "גיל", "מאיה", "5")
    qa.wa(p, "3 שעות בשבוע")
    qa.wa(p, f"בוא נקבע היום ב-{local_in(75)} עם מאיה, שעה")
    qa.fire(p, "session_reminder_1h")
    qa.note(f"buttons: {qa.buttons_of(p)}")
    qa.tap(p, "רוצה רעיונות")
    time_travel_session(p)
    qa.fire(p, "session_follow_up")
    qa.note(f"buttons: {qa.buttons_of(p)}")
    qa.tap(p, "היה מעולה")
    qa.note("status: " + str(qa.psql(f"select status from quality_time where father_id = {father_id(p)}")))
    qa.tap(p, "היה מעולה")
    qa.wa(p, f"תקבע עוד אחד היום ב-{local_in(75)} עם מאיה, חצי שעה")
    time_travel_session(p)
    qa.fire(p, "session_follow_up")
    qa.tap(p, "לא יצא")
    qa.wa(p, "איך אני עומד השבוע?")


def s2(tag):
    qa.start_transcript(f"{tag}-s2-recovery", "s2 · כשמשהו מתבטל")
    p = father("אורי")
    onboard(p, "אורי", "איתי", "4")
    qa.wa(p, "שעתיים בשבוע")
    qa.wa(p, "תקבע לי מחר ב-17:00 עם איתי, שעה")
    qa.show_timers(p)
    qa.wa(p, "אוף, מחר לא אוכל, תבטל את זה")
    qa.show_timers(p)
    qa.wa(p, "בעצם כן, תקבע במקום ביום שאחרי באותה שעה")
    time_travel_session(p, 40)
    qa.wa(p, "לא הספקנו בסוף, היה יום מטורף")


def s3(tag):
    qa.start_transcript(f"{tag}-s3-quiet-week", "s3 · שבוע מכוסה = שקט")
    p = father("דני")
    onboard(p, "דני", "מאיה", "9")
    qa.wa(p, "שעה בשבוע")
    qa.wa(p, "תקבע מחר ב-18:00 עם מאיה, שעה")
    qa.fire(p, "daily")
    qa.wa(p, "אפשר להוסיף עוד מפגש ביום חמישי ב-17:00? חצי שעה")


def s4(tag):
    qa.start_transcript(f"{tag}-s4-stale-timer", "s4 · תזכורת למפגש שבוטל")
    p = father("רון")
    onboard(p, "רון", "תמר", "6")
    qa.wa(p, "שעתיים")
    qa.wa(p, f"היום ב-{local_in(80)} עם תמר, 45 דקות")
    before = qa.timers(p)
    qa.wa(p, "תבטל את המפגש של היום, משהו קפץ")
    after = qa.timers(p)
    qa.note(f"timers before cancel: {len(before)}, after: {len(after)}")
    if after:
        qa.fire(p, "session_follow_up")


def s5(tag):
    qa.start_transcript(f"{tag}-s5-goal-locked", "s5 · היעד של השבוע נעול")
    p = father("עמית")
    onboard(p, "עמית", "יובל", "11")
    qa.wa(p, "2 שעות")
    qa.wa(p, "בעצם תשנה ל-4 שעות השבוע")


def s6(tag):
    qa.start_transcript(f"{tag}-s6-returning", "s6 · אבא שכבר רשום")
    p = father("אלון")
    qa.psql(f"""insert into father (phone, display_name, created_at, status, timezone, locale, current_belt)
                values ('{p}', 'אלון', now(), 'ACTIVE', 'Asia/Jerusalem', 'he', 'WHITE')""")
    fid = father_id(p)
    qa.psql(f"""insert into child (father_id, name, birth_date, status, created_at, updated_at)
                values ({fid}, 'שירה', current_date - interval '5 years', 'ACTIVE', now(), now())""")
    qa.note("lab: אלון and שירה already exist in Dad Coach; first message to the platform")
    qa.wa(p, "היי, מה נשמע?")
    qa.out(f"\n> state after: {qa.state_of(p)}")


def s7(tag):
    qa.start_transcript(f"{tag}-s7-web-cancel", "s7 · מפגש שבוטל בלוח - התזכורת שותקת")
    p = father("גל")
    onboard(p, "גל", "רומי", "8")
    qa.wa(p, "שעתיים")
    qa.wa(p, f"היום ב-{local_in(75)} עם רומי, שעה")
    qa.show_timers(p)
    fid = father_id(p)
    qa.psql(f"update quality_time set status = 'CANCELLED' where father_id = {fid}")
    qa.note("lab: the session was cancelled from the dashboard (the web does not clear the AI's timers)")
    qa.fire(p, "session_reminder_1h")
    time_travel_session(p)
    qa.psql(f"update quality_time set status = 'CANCELLED' where father_id = {fid}")
    qa.fire(p, "session_follow_up")


ALL = {"s7": s7, "s8": s8, "s1": s1, "s2": s2, "s3": s3, "s4": s4, "s5": s5, "s6": s6}

if __name__ == "__main__":
    tag = sys.argv[1]
    for name in (sys.argv[2:] or list(ALL)):
        try:
            ALL[name](tag)
        except Exception as e:  # keep going - the transcript shows where it stopped
            qa.note(f"scenario stopped: {type(e).__name__}: {e}")
