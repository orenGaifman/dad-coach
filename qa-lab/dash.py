"""Dashboard checks for the lab: signs in as a father (ops login link → consume-link) and reads the same API the
dashboard renders (home, sessions, progress, children), so every conversation step can be checked against what the
father sees on the web. Also drives dashboard actions (cancel a session) with the CSRF cookie like the SPA does."""
import json
import urllib.error
import urllib.parse
import urllib.request

import qa

OPS_KEY = "qa-dc-ops-key-0123456789abcdef"
_sessions = {}


def _opener(phone):
    if phone in _sessions:
        return _sessions[phone]
    jar = {}
    op = urllib.request.build_opener()
    st, link = qa.call(qa.BACKEND, "POST", "/api/ops/login-links", {"phone": phone}, {"X-API-Key": OPS_KEY})
    assert st == 201, (st, link)
    frag = urllib.parse.urlparse(link["loginUrl"]).fragment
    token = urllib.parse.parse_qs(frag)["token"][0]
    _req(op, jar, "POST", "/api/auth/consume-link", {"token": token})
    _sessions[phone] = (op, jar)
    return op, jar


def _req(op, jar, method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(qa.BACKEND + path, data=data, method=method)
    r.add_header("Accept", "application/json")
    r.add_header("User-Agent", "qa-lab")
    if data is not None:
        r.add_header("Content-Type", "application/json")
    if jar:
        r.add_header("Cookie", "; ".join(f"{k}={v}" for k, v in jar.items()))
    if jar.get("DADCOACH_XSRF"):
        r.add_header("X-XSRF-TOKEN", jar["DADCOACH_XSRF"])
    try:
        with op.open(r, timeout=60) as resp:
            for h in resp.headers.get_all("Set-Cookie") or []:  # the lab runs on http: keep cookies by hand
                k, v = h.split(";", 1)[0].split("=", 1)
                jar[k.strip()] = v
            t = resp.read().decode()
            return resp.status, (json.loads(t) if t.strip()[:1] in ("[", "{") else t)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        try:
            return e.code, json.loads(t)
        except Exception:
            return e.code, t


def get(phone, path):
    op, jar = _opener(phone)
    return _req(op, jar, "GET", "/api" + path)


def post(phone, path, body=None):
    op, jar = _opener(phone)
    _req(op, jar, "GET", "/api/me")  # the SPA's first call sets the CSRF cookie
    return _req(op, jar, "POST", "/api" + path, body if body is not None else {})


def snapshot(phone, label=""):
    """Writes what the dashboard shows into the transcript (home, sessions, progress)."""
    out = {}
    for p in ("/father/home", "/father/sessions", "/father/progress", "/father/children"):
        st, body = get(phone, p)
        out[p] = body if st == 200 else f"HTTP {st}: {str(body)[:200]}"
    qa.out(f"\n<details><summary>📊 dashboard {label}</summary>\n\n```json\n"
           + json.dumps(out, ensure_ascii=False, indent=1)[:6000] + "\n```\n</details>")
    return out
