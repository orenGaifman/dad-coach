"""Round-2 message fixes (D-036) - lab scenarios with the real model, in Hebrew, on the round-2 lab ports.

    PLATFORM_PORT=8796 BACKEND_PORT=8797 DC_DB=dc_r2 python3 r2.py <tag> [a b c d]

a  onboarding, the first goal with two numbers ("3 שעות, אבל השבוע 2"), a booking for children not yet known
   ("עם מטר ונעם"), "כן", "מה יש לי השבוע?", "מתי התזכורת?", the dashboard
b  a session today: "נבשל" / "תכתוב שהמפגש הוא בישול", the 1-hour reminder, a greeting right after it, "ואחרי המפגש
   אתה שואל?", the follow-up and the "היה מעולה" button that earns the yellow belt
c  move and cancel a session; a session cancelled on his page gets no reminder; the week after
e  page <-> WhatsApp: a session booked on WhatsApp shows on his page; he cancels it and fixes a child's age on the
   page; the coach knows both; "היה מעולה" on WhatsApp shows on the page
d  English input, the voice-note question, and a long chat whose history holds the OLD answer shapes (production's
   own lines) - then "מה יש לי השבוע?", a booking, "מה המצב?"
"""
import json
import sys
import time

import qa
from scenarios import father, father_id, local_in, time_travel_session

OLD_SHAPES = [
    "היי אורן! טוב לראות אותך 😊 אין עדיין יעד שבועי השבוע - רוצה לקבוע כמה שעות איכות עם איתמר תרצה להשקיע השבוע?",
    "קבעתי! מחר ב-17:00, חצי שעה עם איתמר 😊 אזכיר לך בבוקר ושעה לפני.\n\nאגב, עדיין לא קבענו יעד שבועי - רוצה שנסגור על 2-3 שעות השבוע?",
    "סגרנו - 2 שעות השבוע, ו-3 שעות מתחילת שבוע הבא 👍\n\nעדיין חסרות 90 דקות השבוע. יש חלון יפה ביום שישי 09:00-13:00 - לקבוע?",
    "השבוע כבר מכוסה במלואו 💪 יש לך:\n- מחר (חמישי) 17:00 עם איתמר\n- שישי 09:00-10:30 עם מטר ונעם\n\nרוצה בכל זאת להוסיף עוד משהו?",
    "מעולה, רשום אצלי 😊 בהצלחה עם הבישול והאיתמר היום ב-17:00!",
    "התזכורת שעה לפני המפגש, כלומר ב-16:00, ועוד אחת בסביבות שעת המפגש עצמה ב-17:00 😊",
]


def db(sql):
    return qa.psql(sql)


def facts(p, label=""):
    fid = father_id(p)
    if not fid:
        qa.note(f"db {label}: no father yet")
        return
    rows = db(f"select status, to_char(scheduled_start at time zone 'Asia/Jerusalem','Dy DD.MM HH24:MI'), "
              f"extract(epoch from scheduled_end - scheduled_start)/60, mentioned_on from quality_time "
              f"where father_id = {fid} order by scheduled_start")
    goal = db(f"select target_hours, next_week_target_hours from weekly_goal where father_id = {fid}")
    kids = db(f"select name, extract(year from age(birth_date)) from child where father_id = {fid} and status='ACTIVE'")
    fa = db(f"select current_belt, total_quality_times_completed, goal_asked_on from father where id = {fid}")
    qa.note(f"db {label}: sessions={rows} goal={goal} children={kids} father={fa}")


def onboard_oren(p):
    qa.wa(p, "היי, אשמח להתחיל")
    qa.wa(p, "אורן")
    qa.wa(p, "איתמר, בן 6")
    r = qa.wa(p, "כן")
    if "?" in qa.texts(r) and "שעות" not in qa.texts(r) and "יעד" not in qa.texts(r):
        qa.wa(p, "כן")


def a(tag):
    qa.start_transcript(f"{tag}-a-onboarding-goal-booking", "r2-a · הצטרפות, יעד בשני מספרים, קביעה, השבוע, תזכורת, הדף")
    p = father("אורן")
    onboard_oren(p)
    qa.wa(p, "יעד שבועי יהיה 3 שעות שבועיות אבל השבוע 2 מספיק")
    facts(p, "after the goal")
    qa.wa(p, "יום שישי בתשע בבוקר שעה וחצי עם מטר ונעם")
    qa.wa(p, "מטר בת 8 נעם בת 5")
    r = qa.wa(p, "כן")
    if "קבעתי" not in qa.texts(r):
        qa.wa(p, "כן, תקבע")
    facts(p, "after the booking")
    qa.show_timers(p)
    qa.wa(p, "מה יש לי השבוע?")
    qa.wa(p, "מתי התזכורת?")
    qa.wa(p, "תן לי דשבורד")
    facts(p, "end")


def b(tag):
    qa.start_transcript(f"{tag}-b-today-cooking-belt", "r2-b · מפגש היום: בישול, תזכורת, ברכה אחריה, איך היה, חגורה")
    p = father("אורן")
    onboard_oren(p)
    qa.wa(p, "2 שעות השבוע")
    qa.wa(p, f"תקבע היום ב-{local_in(75)} עם איתמר, שעה")
    qa.show_timers(p)
    qa.wa(p, "אנחנו נבשל! תכתוב שהמפגש היום הוא בישול")
    qa.wa(p, "מתי תהיה התזכורת?")
    qa.fire(p, "session_reminder_1h")
    qa.wa(p, "היי")
    qa.wa(p, "ואחרי המפגש אתה שואל אותי?")
    fid = father_id(p)
    db(f"update father set total_quality_times_completed = 2, current_belt = 'WHITE' where id = {fid}")
    qa.note("lab: he has 2 completed sessions - the next one earns the yellow belt")
    time_travel_session(p)
    qa.fire(p, "session_follow_up")
    qa.note(f"buttons: {qa.buttons_of(p)}")
    qa.tap(p, "היה מעולה")
    facts(p, "after the tap")
    qa.wa(p, "איך אני עומד השבוע?")


def c(tag):
    qa.start_transcript(f"{tag}-c-move-cancel-page", "r2-c · הזזה, ביטול, ביטול בדף בלי תזכורת")
    p = father("אורן")
    onboard_oren(p)
    qa.wa(p, "3 שעות")
    qa.wa(p, "תקבע מחר ב-18:00 עם איתמר, שעה")
    qa.wa(p, "בעצם תזיז את זה ל-19:00")
    facts(p, "after the move")
    qa.wa(p, "תבטל את המפגש של מחר")
    facts(p, "after the cancel")
    qa.wa(p, f"תקבע היום ב-{local_in(75)} עם איתמר, חצי שעה")
    fid = father_id(p)
    db(f"update quality_time set status = 'CANCELLED' where father_id = {fid} and status = 'SCHEDULED'")
    qa.note("lab: he cancelled today's session on his page (status CANCELLED, the platform timers stay armed)")
    qa.fire(p, "session_reminder_1h")
    qa.wa(p, "מה יש לי השבוע?")


def seed_old_history(p):
    """Production's old answer shapes, put into his conversation the way Dad Coach records a sent message."""
    for i, text in enumerate(OLD_SHAPES):
        qa.call(qa.PLATFORM, "POST", "/api/v1/worker/messages/outbound", {
            "workerKey": "dad_3", "userId": f"whatsapp:{p}", "channelId": "whatsapp", "correlationId": f"old-{p}-{i}",
            "content": "❤️ דאד קואץ׳:\n" + text, "metadata": {"timezone": "Asia/Jerusalem"},
            "tenantId": "20082bcd-a7bf-57a8-a382-4bad32144b2f", "workflowKey": "dad-coach-3"},
            {"X-API-Key": "qa-dc-worker-key-0123456789abcdef"})
    qa.note(f"lab: {len(OLD_SHAPES)} old-shape coach messages from production put into his conversation")


def d(tag):
    qa.start_transcript(f"{tag}-d-english-voice-long-history", "r2-d · אנגלית, הקלטה, היסטוריה בצורות הישנות")
    p = father("אורן")
    onboard_oren(p)
    qa.wa(p, "2 שעות")
    seed_old_history(p)
    qa.wa(p, "What do I have this week?")
    qa.wa(p, "[הודעה קולית, תומללה אוטומטית - שמות ומספרים עלולים להישמע לא נכון]\nאתה לא יכול לשמוע קולות?")
    qa.wa(p, "תקבע מחר ב-17:00 עם איתמר, חצי שעה")
    qa.wa(p, "מה יש לי השבוע?")
    qa.wa(p, "מתי התזכורת?")
    qa.wa(p, "מה המצב?")
    facts(p, "end")


def e(tag):
    import dash
    qa.start_transcript(f"{tag}-e-page-and-whatsapp", "r2-e · הדף והוואטסאפ מסונכרנים")
    p = father("אורן")
    onboard_oren(p)
    qa.wa(p, "2 שעות")
    qa.wa(p, "תקבע מחר ב-18:00 עם איתמר, שעה")
    st, sessions = dash.get(p, "/father/sessions")
    up = sessions["upcoming"]
    qa.note(f"page /father/sessions upcoming: {[(x['childName'], x.get('start') or x.get('startsAt') or x.get('localStart')) for x in up]}")
    st, _ = dash.post(p, f"/father/sessions/{up[0]['id']}/cancel")
    qa.note(f"page: he cancelled it on the page -> HTTP {st}")
    st, kids = dash.get(p, "/father/children")
    kid = kids[0]
    op, jar = dash._opener(p)
    dash._req(op, jar, "GET", "/api/me")
    st, _ = dash._req(op, jar, "PUT", f"/api/father/children/{kid['id']}", {"name": kid["name"], "age": 10})
    qa.note(f"page: he set איתמר's age to 10 -> HTTP {st}")
    qa.wa(p, "יש לי משהו מחר עם איתמר?")
    qa.wa(p, "בן כמה איתמר אצלך?")
    qa.wa(p, f"תקבע היום ב-{local_in(75)} עם איתמר, שעה")
    time_travel_session(p)
    qa.fire(p, "session_follow_up")
    qa.tap(p, "היה מעולה")
    st, home = dash.get(p, "/father/home")
    qa.note(f"page /father/home after the tap: coverage={home.get('coverage')} progress={home.get('progress')}")


if __name__ == "__main__":
    tag = sys.argv[1]
    for name in sys.argv[2:] or ["a", "b", "c", "d"]:
        globals()[name](tag)
