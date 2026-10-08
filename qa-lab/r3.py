"""D-037 - a reminder is promised only when the platform holds it. Lab scenarios with the real model, in Hebrew, on the
round-3 lab ports (platform jar from the platform branch product-armed-timers, V120 + /api/v1/worker/scheduled-transitions):

    PLATFORM_PORT=8896 BACKEND_PORT=8897 DC_DB=dc_r3 PLATFORM_DB=platform_dc_r3 python3 r3.py <tag> [a b c]

a  booking on WhatsApp: the reply's reminder line against the platform's PENDING triggers (its own database), then
   "מתי התזכורת?" -> the real times
b  arming refused: the lab renames the three timer transitions on the platform (no such transition on the state ->
   the model's schedule_state_transition and Dad Coach's arming are both refused) -> the reply promises no reminder;
   "מתי התזכורת?" says none is set; the names restored, a new booking (Saturday) promises them again
c  a move on WhatsApp: the old session's triggers are gone, the new session's are pending at the new times
d  a timer missing after the model's turn: the lab cancels the model's 1-hour trigger on the platform (as if the turn
   had skipped that call); a second child joins the session -> after that turn Dad Coach finds it missing and arms it
   (source PRODUCT); "מתי התזכורת?" tells only what the platform holds
"""
import os
import subprocess
import sys

import qa
from scenarios import father, father_id

PLATFORM_DB = os.environ.get("PLATFORM_DB", "platform_dc_r3")
KEYS = ("session_morning_reminder", "session_reminder_1h", "session_follow_up")


def pdb(sql):
    r = subprocess.run(["docker", "exec", "qa-dc-platform-db", "psql", "-U", "postgres", "-d", PLATFORM_DB, "-At", "-F", "\t",
                        "-c", sql], capture_output=True, text=True)
    if r.returncode != 0:
        raise AssertionError(r.stderr)
    return [line.split("\t") for line in r.stdout.strip().splitlines() if line]


def pending(p):
    """The platform's own rows: PENDING triggers of this father's conversation, in Israel time."""
    return pdb("select t.transition_key, to_char(t.scheduled_at at time zone 'Asia/Jerusalem', 'Dy DD.MM HH24:MI'), t.source, "
               "left(t.trigger_payload->>'reference_id', 8) from scheduled_trigger t join workflow_instance w "
               f"on w.id = t.workflow_instance_id where w.external_user_id = 'whatsapp:{p}' and t.status = 'PENDING' "
               "and t.transition_key is not null order by t.scheduled_at")


def sessions(p):
    fid = father_id(p)
    return qa.psql(f"select left(id::text, 8), status, to_char(scheduled_start at time zone 'Asia/Jerusalem', "
                   f"'Dy DD.MM HH24:MI') from quality_time where father_id = {fid} order by scheduled_start") if fid else []


def show_platform(p, label):
    rows = pending(p)
    qa.note(f"platform DB {label}: PENDING triggers = " + ("; ".join(" ".join(r) for r in rows) if rows else "none"))
    qa.note(f"Dad Coach DB {label}: sessions = " + ("; ".join(" ".join(r) for r in sessions(p)) or "none"))
    return rows


def onboard(p):
    qa.wa(p, "היי, אשמח להתחיל")
    qa.wa(p, "רון")
    qa.wa(p, "נועה, בת 7")
    r = qa.wa(p, "כן")
    if "?" in qa.texts(r) and "שעות" not in qa.texts(r) and "יעד" not in qa.texts(r):
        qa.wa(p, "כן")
    qa.wa(p, "3 שעות השבוע")


def rename_timers(suffix_from, suffix_to):
    for k in KEYS:
        pdb(f"update workflow_state_transition set transition_key = '{k}{suffix_to}' where transition_key = '{k}{suffix_from}'")


def a(tag):
    qa.start_transcript(f"{tag}-a-booking-matches-platform", "r3-a · קביעה: שורת התזכורת מול הטריגרים בפלטפורמה")
    p = father("רון")
    onboard(p)
    qa.wa(p, "תקבע לי מחר ב-17:00 עם נועה, שעה")
    show_platform(p, "after the booking")
    qa.wa(p, "מתי התזכורת?")


def b(tag):
    qa.start_transcript(f"{tag}-b-arming-refused", "r3-b · הפלטפורמה מסרבת לתזכורות: אין הבטחה")
    p = father("רון")
    onboard(p)
    rename_timers("", "_lab_off")
    qa.note("lab: the three timer transitions are renamed on the platform - arming any of them is refused (not on the state)")
    try:
        qa.wa(p, "תקבע לי מחר ב-17:00 עם נועה, שעה")
        show_platform(p, "after the refused booking")
        qa.wa(p, "מתי התזכורת?")
    finally:
        rename_timers("_lab_off", "")
        qa.note("lab: the timer transitions are back")
    qa.wa(p, "תקבע לי גם בשבת ב-10:00 עם נועה, חצי שעה")
    show_platform(p, "after a booking with the platform accepting")


def c(tag):
    qa.start_transcript(f"{tag}-c-move", "r3-c · הזזה: הטריגרים זזים עם המפגש")
    p = father("רון")
    onboard(p)
    qa.wa(p, "תקבע לי מחר ב-17:00 עם נועה, שעה")
    show_platform(p, "before the move")
    qa.wa(p, "בעצם תזיז את זה לשבת ב-10:00")
    show_platform(p, "after the move")
    qa.wa(p, "מתי התזכורת?")


def d(tag):
    qa.start_transcript(f"{tag}-d-missing-timer", "r3-d · תזכורת שחסרה בפלטפורמה: התשובה אומרת רק מה שקיים")
    p = father("רון")
    onboard(p)
    qa.wa(p, "יש לי גם את עומר, בן 5")
    qa.wa(p, "תקבע לי מחר ב-17:00 עם נועה, שעה")
    show_platform(p, "after the booking")
    pdb("update scheduled_trigger t set status = 'CANCELLED' from workflow_instance w where w.id = t.workflow_instance_id "
        f"and w.external_user_id = 'whatsapp:{p}' and t.status = 'PENDING' and t.transition_key = 'session_reminder_1h'")
    qa.note("lab: the model's 1-hour trigger is cancelled on the platform - as if the booking turn had never armed it")
    show_platform(p, "the 1-hour timer missing")
    qa.wa(p, "תוסיף גם את עומר למפגש של מחר")
    show_platform(p, "after the join")
    qa.wa(p, "מתי התזכורת?")


if __name__ == "__main__":
    tag = sys.argv[1]
    for name in sys.argv[2:] or ["a", "b", "c"]:
        globals()[name](tag)
