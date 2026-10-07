#!/usr/bin/env python3
"""Creates Dad Coach's two new Render services through the Render API (the API equivalent of render.yaml) and
wires them to the existing backend. Idempotent: a service that already exists (by name) is kept. Never prints a
secret. The backend (dad-coach) and its database (Supabase) already exist.

  dad-coach-ui    docker (frontend/Dockerfile, context ./frontend), nginx with the same-origin /api proxy;
                  BACKEND_URL -> dad-coach
  dad-coach-site  static (site/), VITE_SIGNUP_ENDPOINT -> dad-coach /api/public/site-signups
Then dad-coach gets WEB_BASE_URL (dad-coach-ui) and SITE_ORIGINS (dad-coach-site); the caller redeploys it.

  RENDER_API_KEY=... python3 provisioning/scripts/render_create_services.py
"""

import json
import os
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.render.com/v1"
REPO = "https://github.com/orenGaifman/dad-coach"
REGION = "virginia"
BACKEND = "dad-coach"


def call(method, path, body=None):
    req = urllib.request.Request(API + path, data=json.dumps(body).encode() if body is not None else None, method=method)
    req.add_header("Authorization", "Bearer " + os.environ["RENDER_API_KEY"])
    req.add_header("Accept", "application/json")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Render API {method} {path.split('?')[0]} -> HTTP {e.code}: {e.read().decode()[:400]}")


def find_service(name):
    for item in call("GET", f"/services?name={urllib.parse.quote(name)}&limit=20"):
        if item["service"]["name"] == name:
            return item["service"]
    return None


def create(owner, name, details, env):
    existing = find_service(name)
    if existing:
        print(f"{name}: exists")
        return existing
    body = {"type": details.pop("type", "web_service"), "name": name, "ownerId": owner, "repo": REPO, "branch": "main",
            "autoDeploy": "yes", "serviceDetails": details, "envVars": [{"key": k, "value": v} for k, v in env.items()]}
    created = call("POST", "/services", body)
    service = created.get("service", created)
    print(f"{name}: created")
    return service


def url_of(service):
    return call("GET", f"/services/{service['id']}")["serviceDetails"]["url"].rstrip("/")


def main():
    backend = find_service(BACKEND) or SystemExit(f"{BACKEND} not found")
    owner = backend["ownerId"]
    backend_url = url_of(backend)
    ui = create(owner, "dad-coach-ui", {
        "runtime": "docker", "plan": "starter", "region": REGION, "healthCheckPath": "/healthz",
        "envSpecificDetails": {"dockerfilePath": "./frontend/Dockerfile", "dockerContext": "./frontend"}},
        {"BACKEND_URL": backend_url})
    site = create(owner, "dad-coach-site", {
        "type": "static_site", "buildCommand": "cd site && npm ci && npm run build", "publishPath": "site/dist"},
        {"VITE_SIGNUP_ENDPOINT": backend_url + "/api/public/site-signups"})
    ui_url, site_url = url_of(ui), url_of(site)
    for key, value in {"WEB_BASE_URL": ui_url, "SITE_ORIGINS": site_url}.items():
        call("PUT", f"/services/{backend['id']}/env-vars/{key}", {"value": value})
        print(f"{BACKEND}: set {key}")
    print(f"\n{BACKEND:15} {backend_url}\ndad-coach-ui    {ui_url}\ndad-coach-site  {site_url}")


if __name__ == "__main__":
    main()
