#!/usr/bin/env python3
"""Seeds Dad Coach's demo father in production, so the owner can show the product from the admin's view-as.

The demo father יואב (+1999 test number - the coach never reaches it; no consent) with נועה (7) and איתי (4),
a 3-hour goal this week, sessions this week (some done, one upcoming) and the belt they earn. Everything goes
through the same endpoints the platform's AI uses (POST /api/tools/{key} with the tool key) - nothing is
written to the database directly. Idempotent: an existing demo father keeps his data.

Optionally makes a staff member an admin of the dashboard (ops API bootstrap-admin) and prints a one-time
sign-in link for him (valid ~15 minutes; never stored).

  python3 provisioning/scripts/seed_demo_father.py [--secrets ~/.config/dad-coach/smoke.env]
                                                  [--admin-phone +9725... --admin-name אורן] [--login-link]
Configuration: DADCOACH_BACKEND_URL, TOOL_API_KEY, DADCOACH_OPS_API_KEY, DADCOACH_UI_URL. Standard library only.
"""
import hashlib
import json
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

DEMO_PHONE = "+19995551000"
IL = ZoneInfo("Asia/Jerusalem")


def arg(name, default=None):
    a = sys.argv[1:]
    return a[a.index(name) + 1] if name in a else default


def config():
    path = Path(arg("--secrets", str(Path.home() / ".config" / "dad-coach" / "smoke.env")))
    values = {}
    for line in path.read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            values[k.strip()] = v.strip()
    return values


def call(base, method, path, body=None, headers=None):
    req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t.strip()[:1] in "[{" else t)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        try:
            return e.code, json.loads(t)
        except Exception:
            return e.code, t


def main():
    c = config()
    be = c["DADCOACH_BACKEND_URL"].rstrip("/")
    key = {"X-API-Key": c["TOOL_API_KEY"]}

    def tool(name, params, idem=None):
        # stable per call, so a re-run replays instead of booking twice (a header must be ASCII: a hash)
        idem = idem or "demo-seed-" + hashlib.sha256(f"{name}:{json.dumps(params, sort_keys=True)}".encode()).hexdigest()[:32]
        return call(be, "POST", f"/api/tools/{name}", {"execution_id": str(uuid.uuid4()), "idempotency_key": idem,
                                                      "user_id": f"whatsapp:{DEMO_PHONE}", "parameters": params},
                    {**key, "X-Idempotency-Key": idem})

    def family():
        s, r = call(be, "POST", "/api/context/family_context", {"user_id": None, "config": {"phone": f"whatsapp:{DEMO_PHONE}"}}, key)
        return (r.get("data") or {}) if isinstance(r, dict) else {}

    fam = family()
    if not fam.get("father_profile"):
        print("profile:", tool("save_user_profile", {"displayName": "יואב", "timezone": "Asia/Jerusalem"})[0])
    names = {ch.get("name") for ch in family().get("children") or []}
    for name, age in [("נועה", 7), ("איתי", 4)]:
        if name not in names:
            print(f"child {name}:", tool("add_child", {"name": name, "age": age})[0])
    children = {ch["name"]: ch.get("child_id") or ch.get("childId") for ch in family().get("children") or []}
    print("goal:", tool("set_weekly_goal", {"target_hours": 3})[1].get("success"))

    # this week (Sunday-Saturday in Israel): done sessions earlier this week, one upcoming
    now = datetime.now(IL)
    week_start = (now - timedelta(days=(now.weekday() + 1) % 7)).replace(hour=0, minute=0, second=0, microsecond=0)
    plan = [("נועה", 0, 17, 60, "בנינו מגדל לגו ענק והיא לא הפסיקה לצחוק", True),
            ("איתי", 1, 17, 45, "פארק וגלידה", True),
            ("נועה", 2, 18, 30, "אפינו עוגיות", True),
            ("איתי", 3, 10, 60, "", True)]
    for child, day, hour, minutes, note, done in plan:
        start = week_start + timedelta(days=day, hours=hour)
        if start + timedelta(minutes=minutes) > now:
            continue  # only the moments that already passed this week are recorded as done
        s, r = tool("schedule_quality_time", {"child_id": children[child], "start_time": start.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                                              "duration_minutes": minutes})
        qt = (r.get("data") or {}).get("quality_time_id") if isinstance(r, dict) else None
        if qt and done:
            tool("complete_quality_time", {"quality_time_id": qt, "notes": note})
        print(f"session {child} {start:%a %H:%M}:", "done" if qt else r)
    upcoming = (now + timedelta(days=1)).replace(hour=17, minute=0, second=0, microsecond=0)
    s, r = tool("schedule_quality_time", {"child_id": children["איתי"], "start_time": upcoming.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                                          "duration_minutes": 60})
    print("upcoming:", (r.get("data") or {}).get("local_start") if isinstance(r, dict) else r)

    admin_phone = arg("--admin-phone")
    ops = {"X-API-Key": c["DADCOACH_OPS_API_KEY"]}
    if admin_phone:
        s, r = call(be, "POST", "/api/ops/bootstrap-admin", {"phone": admin_phone, "name": arg("--admin-name", "מנהל")}, ops)
        print("admin:", s)
        if "--login-link" in sys.argv:
            s, r = call(be, "POST", "/api/ops/login-links", {"phone": admin_phone}, ops)
            print("one-time admin sign-in link (~15 min):", r.get("loginUrl") if isinstance(r, dict) else r)
    fid = (family().get("father_profile") or {}).get("father_id")
    print(f"demo father id {fid}; view-as: {c.get('DADCOACH_UI_URL', '').rstrip('/')}/admin/fathers/{fid}/home")


if __name__ == "__main__":
    main()
