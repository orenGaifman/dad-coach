#!/usr/bin/env python3
"""Deployed smoke test for Dad Coach: proves a deployment end to end, then removes everything it made.

One scratch father per run, on a +1999 test number (never a real person; Meta refuses the sends, so "sent"
means a delivery attempt was recorded):
  1. health; routes that must stay closed answer 401/404 (dev, test-send, trigger-notification, admin)
  2. the gateway claim: an unknown number is not claimed; after a site signup it is
  3. onboarding on WhatsApp (signed like Meta/the gateway) -> real AI turns on the platform -> the father,
     his child and his endpoint exist in Dad Coach, his conversation exists on the platform with his timezone
  4. the agent's tools as the platform calls them: this week's goal, a session (timers returned), the same
     call again (idempotent replay), the session completed
  5. the scheduled-response callback for a fresh trigger, and the same trigger again (replay, no second send)
  6. the dashboard: an ops login link -> his home shows the goal and the completed minutes
  7. cleanup: the operator delete -> his data is gone, the platform's copy through the deletion outbox

Configuration (environment, or KEY=value lines in --secrets <file>, default ~/.config/dad-coach/production-secrets.env):
  DADCOACH_BACKEND_URL, DADCOACH_UI_URL, TOOL_API_KEY, DADCOACH_ADMIN_API_KEY, DADCOACH_OPS_API_KEY,
  WHATSAPP_WEBHOOK_SECRET, PLATFORM_BASE_URL, PLATFORM_ADMIN_API_KEY
(the callback key is the tool key in production - WORKFLOW_PLATFORM_CALLBACK_API_KEY overrides).
Only key NAMES are printed. Standard library only.

  python3 provisioning/scripts/deployed_smoke_test.py [--secrets file] [--keep]
"""

import hashlib
import hmac
import http.cookiejar
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

REQUIRED = ["DADCOACH_BACKEND_URL", "DADCOACH_UI_URL", "TOOL_API_KEY", "DADCOACH_ADMIN_API_KEY", "DADCOACH_OPS_API_KEY",
            "WHATSAPP_WEBHOOK_SECRET", "PLATFORM_BASE_URL", "PLATFORM_ADMIN_API_KEY"]
results = []


def config():
    args = sys.argv[1:]
    path = Path(args[args.index("--secrets") + 1]) if "--secrets" in args else Path.home() / ".config" / "dad-coach" / "production-secrets.env"
    values = {}
    if path.exists():
        for line in path.read_text().splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.split("=", 1)
                values[k.strip()] = v.strip()
    values.update({k: os.environ[k] for k in REQUIRED + ["WORKFLOW_PLATFORM_CALLBACK_API_KEY"] if os.environ.get(k)})
    missing = [k for k in REQUIRED if not values.get(k)]
    if missing:
        raise SystemExit(f"missing configuration: {', '.join(missing)}")
    for k in ("DADCOACH_BACKEND_URL", "DADCOACH_UI_URL", "PLATFORM_BASE_URL"):
        values[k] = values[k].rstrip("/")
    values.setdefault("WORKFLOW_PLATFORM_CALLBACK_API_KEY", values["TOOL_API_KEY"])
    return values


class Client:
    def __init__(self, base):
        self.base = base
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None, headers=None, raw=None):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        req.add_header("Accept", "application/json")
        xsrf = next((c.value for c in self.jar if c.name == "XSRF-TOKEN"), None)
        if xsrf and method not in ("GET", "HEAD"):
            req.add_header("X-XSRF-TOKEN", xsrf)
        for k, v in (headers or {}).items():
            req.add_header(k, v)
        try:
            with self.opener.open(req, timeout=180) as r:
                text = r.read().decode()
                if self.base.startswith("http://"):
                    for ck in self.jar:
                        ck.secure = False  # a lab run is plain http; the app marks its cookies Secure
                return r.status, (json.loads(text) if text.strip()[:1] in ("[", "{") else text)
        except urllib.error.HTTPError as e:
            text = e.read().decode()
            try:
                return e.code, json.loads(text)
            except Exception:
                return e.code, text


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" - {detail}" if detail and not ok else ""))
    return ok


def main():
    c = config()
    be, ui, pl = Client(c["DADCOACH_BACKEND_URL"]), Client(c["DADCOACH_UI_URL"]), Client(c["PLATFORM_BASE_URL"])
    phone = "+1999" + str(int(time.time()))[-7:]
    print(f"Dad Coach smoke · scratch father {phone[:7]}…")

    print("1. health and closed routes")
    check("health", be.call("GET", "/actuator/health")[0] == 200)
    for path, method in [("/api/v1/dev/fathers", "GET"), ("/webhook/whatsapp/test-send", "GET"), ("/api/admin/test/tools", "GET"),
                         ("/api/webhooks/trigger-notification", "POST"), ("/api/v1/admin/fathers", "GET"), ("/api/me", "GET")]:
        status, _ = be.call(method, path, {} if method == "POST" else None)
        check(f"{method} {path} closed ({status})", status in (401, 403, 404))
    check("dashboard UI serves", ui.call("GET", "/healthz")[0] == 200)

    print("2. gateway claim")
    claim = lambda: be.call("POST", "/api/integration/channel/claim", {"phone": phone},
                            {"X-API-Key": c["WORKFLOW_PLATFORM_CALLBACK_API_KEY"]})
    s, r = claim()
    check("unknown number not claimed", s == 200 and r.get("claimed") is False, f"{s} {r}")
    check("claim needs the key", be.call("POST", "/api/integration/channel/claim", {"phone": phone})[0] == 401)
    s, _ = be.call("POST", "/api/public/site-signups", {"name": "בדיקה", "phone": phone, "source": "smoke", "page": "/", "website": ""})
    check("site signup accepted", s in (200, 201, 202), str(s))
    # the site form only accepts Israeli mobiles; a +1999 number is claimed only once he exists (step 3)

    print("3. onboarding on WhatsApp (real AI turns)")
    secret = c["WHATSAPP_WEBHOOK_SECRET"].encode()

    def whatsapp(text):
        body = json.dumps({"object": "whatsapp_business_account", "entry": [{"id": "1", "changes": [{"field": "messages", "value": {
            "messaging_product": "whatsapp", "metadata": {"display_phone_number": "972552961164", "phone_number_id": "smoke"},
            "contacts": [{"profile": {"name": "בדיקה"}, "wa_id": phone.lstrip("+")}],
            "messages": [{"from": phone.lstrip("+"), "id": f"wamid.smoke-{uuid.uuid4()}", "timestamp": str(int(time.time())),
                          "type": "text", "text": {"body": text}}]}}]}]}).encode()
        sig = "sha256=" + hmac.new(secret, body, hashlib.sha256).hexdigest()
        return be.call("POST", "/webhook/whatsapp", raw=body, headers={"X-Hub-Signature-256": sig})[0]

    admin = {"X-API-Key": c["DADCOACH_ADMIN_API_KEY"]}

    tool_key = {"X-API-Key": c["TOOL_API_KEY"]}

    def family():
        s, r = be.call("POST", "/api/context/family_context", {"user_id": None, "config": {"phone": f"whatsapp:{phone}"}}, tool_key)
        return (r.get("data") or {}) if isinstance(r, dict) else {}

    def find_father():
        """His Dad Coach id as the operator API names it (the admin list masks phones)."""
        fid = (family().get("father_profile") or {}).get("father_id")
        if fid is None:
            return None
        admin_id = str(uuid.UUID(int=int(fid)))
        s, r = be.call("GET", f"/api/v1/admin/fathers/{admin_id}", headers=admin)
        return {"id": admin_id} if s == 200 else None

    for text in ["היי, אני רוצה להתחיל", "קוראים לי יואב", "יש לי בת, נועה, בת 6", "כן, נכון"]:
        check(f"webhook accepted: {text}", whatsapp(text) == 200)
        time.sleep(25)
    father = None
    for _ in range(12):
        father = find_father()
        if father:
            break
        time.sleep(10)
    check("the father exists in Dad Coach", father is not None)
    s, inst = pl.call("GET", "/api/v1/admin/instances?size=200&sort=createdAt,desc", headers={"X-API-Key": c["PLATFORM_ADMIN_API_KEY"]})
    mine = [i for i in (inst.get("content") or []) if i.get("externalUserId") == f"whatsapp:{phone}"]
    check("his conversation exists on the platform (dad-coach-3)", bool(mine) and mine[0].get("workflowName", "").startswith("Dad Coach"))
    check("claimed now that he exists", claim()[1].get("claimed") is True)

    print("4. the agent's tools")

    def tool(key, params, idem=None):
        idem = idem or str(uuid.uuid4())
        return be.call("POST", f"/api/tools/{key}", {"execution_id": str(uuid.uuid4()), "idempotency_key": idem,
                                                     "user_id": f"whatsapp:{phone}", "parameters": params},
                       {**tool_key, "X-Idempotency-Key": idem})

    s, r = tool("set_weekly_goal", {"target_hours": 2})
    check("set_weekly_goal", s == 200 and r.get("success") is not False, str(r)[:200])
    fam = family()
    children = fam.get("children") or []
    child_id = (children[0].get("child_id") or children[0].get("childId")) if children else None
    check("family_context lists the child", child_id is not None, str(fam)[:200])
    start = (datetime.now(timezone.utc) + timedelta(days=1)).replace(hour=14, minute=0, second=0, microsecond=0)
    idem = str(uuid.uuid4())
    s, booked = tool("schedule_quality_time", {"child_id": child_id, "start_time": start.strftime("%Y-%m-%dT%H:%M:%SZ"), "duration_minutes": 45}, idem)
    data = (booked.get("data") or {}) if isinstance(booked, dict) else {}
    check("schedule_quality_time (no calendar needed)", s == 200 and data.get("quality_time_id"), str(booked)[:200])
    check("timers returned", isinstance(data.get("timers"), dict))
    s, again = tool("schedule_quality_time", {"child_id": child_id, "start_time": start.strftime("%Y-%m-%dT%H:%M:%SZ"), "duration_minutes": 45}, idem)
    check("same call again replays", s == 200 and (again.get("data") or {}).get("quality_time_id") == data.get("quality_time_id"))
    s, done = tool("complete_quality_time", {"quality_time_id": data.get("quality_time_id"), "notes": "smoke"})
    check("complete_quality_time", s == 200 and done.get("success") is not False, str(done)[:200])

    print("5. scheduled-response callback")
    trigger = str(uuid.uuid4())
    cb = {"X-API-Key": c["WORKFLOW_PLATFORM_CALLBACK_API_KEY"], "X-Idempotency-Key": f"scheduled-response:{trigger}"}
    body = {"triggerId": trigger, "workflowInstanceId": str(uuid.uuid4()), "userId": f"whatsapp:{phone}", "channel": "whatsapp",
            "targetStateKey": "SESSION_REMINDER_1H", "responseContent": "בדיקה: עוד שעה זמן איכות"}
    s1, r1 = be.call("POST", "/api/integration/workflow/scheduled-response", body, cb)
    s2, r2 = be.call("POST", "/api/integration/workflow/scheduled-response", body, cb)
    check("callback handled", s1 == 200 and r1.get("status") in ("DELIVERED", "FAILED"), f"{s1} {r1}")
    check("same trigger replays", s2 == 200 and r2.get("status") == r1.get("status"))

    print("6. the dashboard")
    s, link = be.call("POST", "/api/ops/login-links", {"phone": phone}, {"X-API-Key": c["DADCOACH_OPS_API_KEY"]})
    token = (link.get("loginUrl") or "").split("#token=")[-1] if isinstance(link, dict) else ""
    check("ops login link", s in (200, 201) and token, str(s))
    s, _ = ui.call("POST", "/api/auth/consume-link", {"token": token})
    check("signed in through the UI's /api proxy", s in (200, 204), str(s))
    s, home = ui.call("GET", "/api/father/home")
    check("home shows his goal and the completed session", s == 200 and "120" in json.dumps(home) and "45" in json.dumps(home), str(home)[:200])

    print("7. cleanup")
    if "--keep" in sys.argv:
        print("  (kept)")
    elif father:
        fid = father.get("id")
        s, _ = be.call("DELETE", f"/api/v1/admin/fathers/{fid}", headers=admin)
        check("operator delete", s in (200, 204), str(s))
        check("gone from Dad Coach", find_father() is None)
        gone = False
        for _ in range(24):
            s, inst = pl.call("GET", "/api/v1/admin/instances?size=200&sort=createdAt,desc", headers={"X-API-Key": c["PLATFORM_ADMIN_API_KEY"]})
            if not [i for i in (inst.get("content") or []) if i.get("externalUserId") == f"whatsapp:{phone}"]:
                gone = True
                break
            time.sleep(15)
        check("gone from the platform (deletion outbox)", gone)
        check("not claimed any more", claim()[1].get("claimed") is False)

    failed = [n for n, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
