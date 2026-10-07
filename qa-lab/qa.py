"""QA harness for the local Dad Coach lab: the father on WhatsApp (signed webhooks in, fake_meta.py capturing
everything out), the AI Workflow Platform running the published dad-coach-3 workflow with the real model, and
Dad Coach's backend. Every step is appended to a transcript so the conversation reads exactly as the father
sees it.

Scheduled turns run for real: fire(phone, key) moves the platform trigger the AI armed (session timers) or the
daily check to "now"; the platform's own pipeline claims it, runs the one-shot state, calls Dad Coach's
scheduled-response callback, and the message lands in sent.jsonl. Nothing is simulated.
"""
import datetime
import hashlib
import hmac
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid

LAB = os.path.dirname(os.path.abspath(__file__))
SENT = f"{LAB}/sent.jsonl"
BACKEND = "http://localhost:8397"
PLATFORM = "http://localhost:8396"
SECRET = b"qa-webhook-secret"
ADMIN_KEY = "qa-admin-key-0123456789abcdef"
TOOL_KEY = "qa-dc-tool-key-0123456789abcdef0123456789"
BOT = "972552961164"

PEOPLE = {}  # phone -> display name, filled by the scenarios
TRANSCRIPT = None


def out(line=""):
    print(line, flush=True)
    if TRANSCRIPT:
        with open(TRANSCRIPT, "a") as f:
            f.write(line + "\n")


def start_transcript(name, title=None):
    global TRANSCRIPT
    os.makedirs(f"{LAB}/transcripts", exist_ok=True)
    TRANSCRIPT = f"{LAB}/transcripts/{name}.md"
    open(TRANSCRIPT, "w").close()
    out(f"# {title or name}")
    out(f"_lab run {datetime.datetime.now():%Y-%m-%d %H:%M} · workflow {workflow_version()}_")


def note(text):
    out(f"\n> 🔎 {text}")


def call(base, method, path, body=None, headers=None, timeout=180):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t.strip()[:1] in ("[", "{") else t)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        try:
            return e.code, json.loads(t)
        except Exception:
            return e.code, t


def platform(method, path, body=None):
    return call(PLATFORM, method, path, body, {"X-API-Key": ADMIN_KEY})


def psql(sql, db="qa-dc-db", name="dadcoach"):
    r = subprocess.run(["docker", "exec", db, "psql", "-U", "postgres", "-d", name, "-At", "-F", "\t", "-c", sql],
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise AssertionError(r.stderr)
    return [line.split("\t") for line in r.stdout.strip().splitlines() if line]


def workflow_version():
    try:
        _, wfs = platform("GET", "/api/v1/admin/workflows?size=50")
        wf = next(w for w in wfs["workflows"] if w["workflowKey"] == "dad-coach-3")
        _, d = platform("GET", f"/api/v1/admin/workflows/{wf['id']}")
        return f"dad-coach-3 v{len(d.get('versions') or [])}"
    except Exception:
        return "dad-coach-3 ?"


# ------------------------------------------------------------------ outbound (captured)
def sent_lines():
    if not os.path.exists(SENT):
        return []
    with open(SENT) as f:
        return [json.loads(l) for l in f if l.strip()]


def to_of(entry):
    return "+" + str(entry["payload"].get("to", "")).lstrip("+")


def render(entry):
    p = entry["payload"]
    kind = p.get("type")
    if kind == "text":
        return [p["text"]["body"]]
    if kind == "image":
        return [f"[תמונה] {p['image'].get('caption', '')} ({p['image'].get('link', '')})"]
    if kind == "template":
        t = p["template"]
        params = [prm.get("text") for c in t.get("components", []) for prm in c.get("parameters", []) if prm.get("type") == "text"]
        return [f"[template {t.get('name')}] " + " | ".join(str(x) for x in params)]
    if kind == "interactive":
        i = p["interactive"]
        lines = [i.get("body", {}).get("text", "")]
        a = i.get("action", {})
        if i.get("type") == "button":
            lines.append("  " + "  ".join(f"[{b['reply']['title']}]" for b in a.get("buttons", [])))
        return lines
    return [json.dumps(p, ensure_ascii=False)[:300]]


def show(entries, phone=None):
    for e in entries:
        if phone and to_of(e) != phone:
            continue
        out("  **Dad Coach:**")
        for l in "\n".join(render(e)).splitlines():
            out(f"      {l}")


# ------------------------------------------------------------------ inbound
def _post(phone, message):
    body = json.dumps({"object": "whatsapp_business_account", "entry": [{"id": "1", "changes": [{"field": "messages", "value": {
        "messaging_product": "whatsapp",
        "metadata": {"display_phone_number": BOT, "phone_number_id": "qa-phone-id"},
        "contacts": [{"profile": {"name": PEOPLE.get(phone, "")}, "wa_id": phone.lstrip("+")}],
        "messages": [message]}}]}]}).encode()
    sig = "sha256=" + hmac.new(SECRET, body, hashlib.sha256).hexdigest()
    req = urllib.request.Request(BACKEND + "/webhook/whatsapp", data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Hub-Signature-256", sig)
    with urllib.request.urlopen(req, timeout=240) as r:
        assert r.status == 200, r.status


def _settle(before, phone, timeout=180, quiet=5):
    """Waits until a message to this phone arrived (or the timeout) and the outbound went quiet."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if any(to_of(e) == phone for e in sent_lines()[before:]):
            break
        time.sleep(1)
    last, quiet_since = len(sent_lines()), time.time()
    while time.time() < deadline:
        time.sleep(1)
        n = len(sent_lines())
        if n != last:
            last, quiet_since = n, time.time()
        elif time.time() - quiet_since >= quiet:
            break
    return [e for e in sent_lines()[before:] if to_of(e) == phone]


def wa(phone, text, timeout=180):
    """The father writes on WhatsApp; returns what Dad Coach sent him for this turn."""
    out(f"\n**{PEOPLE.get(phone, phone)}:** {text}")
    before = len(sent_lines())
    msg = {"from": phone.lstrip("+"), "id": f"wamid.in-{uuid.uuid4()}", "timestamp": str(int(time.time())),
           "type": "text", "text": {"body": text}}
    started = time.time()
    _post(phone, msg)
    new = _settle(before, phone, timeout)
    show(new)
    out(f"      _({time.time() - started:.1f}s)_")
    if not new:
        note("no reply")
    return new


def buttons_of(phone):
    """The reply buttons ({title: id}) of the last interactive message sent to this phone."""
    for e in reversed(sent_lines()):
        p = e["payload"]
        if to_of(e) == phone and p.get("type") == "interactive":
            return {b["reply"]["title"]: b["reply"]["id"] for b in p["interactive"]["action"].get("buttons", [])}
    return {}


def tap(phone, title, timeout=180):
    """The father taps a reply button (by its title) on the last interactive message; returns what came back."""
    button_id = buttons_of(phone).get(title)
    assert button_id, f"no button '{title}' on the last interactive message to {phone}"
    out(f"\n**{PEOPLE.get(phone, phone)}:** [{title}]  _(tap {button_id.rsplit(':', 1)[0]}:…)_")
    before = len(sent_lines())
    msg = {"from": phone.lstrip("+"), "id": f"wamid.tap-{uuid.uuid4()}", "timestamp": str(int(time.time())),
           "type": "interactive", "interactive": {"type": "button_reply", "button_reply": {"id": button_id, "title": title}}}
    started = time.time()
    _post(phone, msg)
    new = _settle(before, phone, timeout)
    show(new)
    out(f"      _({time.time() - started:.1f}s)_")
    if not new:
        note("no reply")
    return new


def texts(entries):
    return "\n".join("\n".join(render(e)) for e in entries)


# ------------------------------------------------------------------ platform state
def instance_of(phone):
    _, data = platform("GET", "/api/v1/admin/instances?size=200&sort=createdAt,desc")
    items = data.get("content") if isinstance(data, dict) else data
    items = [i for i in (items or []) if i.get("status") == "ACTIVE" and i.get("externalUserId") == f"whatsapp:{phone}"]
    return items[0] if items else None


def state_of(phone):
    i = instance_of(phone)
    return i and (i.get("currentStateKey") or i.get("currentState"))


def timers(phone):
    i = instance_of(phone)
    if not i:
        return []
    _, data = platform("GET", f"/api/v1/admin/workflow-instances/{i['id']}/scheduled-transitions")
    return data.get("upcoming", []) if isinstance(data, dict) else []


def show_timers(phone):
    t = timers(phone)
    out("\n> ⏰ " + (", ".join(f"{x.get('transitionKey') or x.get('transitionName')} @ {x['scheduledAt']}"
                            + (f" ({x['reference'].get('type')} {x['reference'].get('id', '')[:8]})" if x.get('reference') else "")
                            for x in t) if t else "no pending timers"))
    return t


def fire(phone, key, timeout=200):
    """Moves the pending trigger `key` (a session timer's transition key, or 'daily' for the daily check) to a few
    seconds from now and waits for what the real pipeline delivers (or for it to be suppressed)."""
    i = instance_of(phone)
    match = lambda: [x for x in timers(phone) if (x.get("transitionKey") or "") == key
                     or (key == "daily" and not x.get("transitionKey"))]
    t = match()
    waited = 0
    while not t and key == "daily" and waited < 400:  # the schedule processor materializes it every 5 minutes
        time.sleep(20)
        waited += 20
        t = match()
    if not t:
        note(f"no pending {key} trigger")
        return None
    trig = sorted(t, key=lambda x: x["scheduledAt"])[0]
    out(f"\n### ⏰ {key} fires _(was due {trig['scheduledAt']})_")
    at = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(seconds=3)).strftime("%Y-%m-%dT%H:%M:%SZ")
    status, r = platform("PUT", f"/api/v1/admin/workflow-instances/{i['id']}/scheduled-transitions/{trig['triggerId']}",
                         {"scheduledAt": at})
    if status != 200:
        note(f"reschedule failed {status}: {str(r)[:200]}")
        return None
    before = len(sent_lines())
    deadline = time.time() + timeout
    outcome = None
    while time.time() < deadline:
        time.sleep(4)
        _, d = platform("GET", f"/api/v1/admin/workflow-instances/{i['id']}/scheduled-transitions/{trig['triggerId']}")
        st = d.get("status") if isinstance(d, dict) else None
        if st in ("EXECUTED", "FAILED", "CANCELLED"):
            outcome = st
            break
    time.sleep(4)
    new = [e for e in sent_lines()[before:] if to_of(e) == phone]
    show(new)
    if not new:
        note(f"nothing sent (trigger {outcome}) - suppressed or skipped")
    return new


def fresh_phone():
    return "+1999" + str(int(time.time() * 1000))[-7:]
