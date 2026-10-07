#!/usr/bin/env python3
"""A stand-in for Meta's Graph API for the Dad Coach lab.

- POST /<phone-id>/messages: accepted; appended to sent.jsonl (one JSON per line) — the lab reads it.
- POST /<waba>/message_templates: a template submission (from the admin's review dialog); stored and
  reported APPROVED on the next list, as if Meta approved it at once.
- GET  /<waba>/message_templates: the stored templates, in Meta's list shape.
- GET  /<version>/<media-id>: a voice note's address (D-029), when .run/media/<media-id> exists (qa.voice() puts it
  there); GET /media-files/<media-id> is the file itself. Both need the WhatsApp token, as Meta does.
"""
import json, os, sys, time, uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, "sent.jsonl")
TEMPLATES = os.path.join(HERE, ".run", "templates.json")
MEDIA = os.path.join(HERE, ".run", "media")


def load_templates():
    try:
        with open(TEMPLATES) as f:
            return json.load(f)
    except Exception:
        return {}


def save_templates(t):
    os.makedirs(os.path.dirname(TEMPLATES), exist_ok=True)
    with open(TEMPLATES, "w") as f:
        json.dump(t, f, ensure_ascii=False)


class H(BaseHTTPRequestHandler):
    def _json(self, obj, status=200):
        out = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length") or 0))
        try:
            payload = json.loads(body or b"{}")
        except Exception:
            payload = {"raw": body.decode(errors="replace")}
        if self.path.split("?")[0].endswith("/message_templates"):
            t = load_templates()
            payload["status"] = "APPROVED"
            payload["id"] = str(uuid.uuid4().int)[:15]
            t[payload.get("name", "?")] = payload
            save_templates(t)
            return self._json({"id": payload["id"], "status": "PENDING", "category": payload.get("category")})
        wamid = f"wamid.qa-{uuid.uuid4()}"
        if payload.get("status") != "read":
            with open(LOG, "a") as f:
                f.write(json.dumps({"at": time.strftime("%Y-%m-%dT%H:%M:%S"), "wamid": wamid, "path": self.path,
                                    "payload": payload}, ensure_ascii=False) + "\n")
        self._json({"messaging_product": "whatsapp", "contacts": [{"wa_id": payload.get("to", "")}],
                    "messages": [{"id": wamid}], "success": True})

    def do_GET(self):
        path = self.path.split("?")[0]
        if path.endswith("/message_templates"):
            return self._json({"data": list(load_templates().values()), "paging": {}})
        parts = path.strip("/").split("/")
        if len(parts) == 2 and (parts[0] == "media-files" or parts[0].startswith("v")):
            media_id = os.path.basename(parts[1])
            file = os.path.join(MEDIA, media_id)
            if not (self.headers.get("Authorization") or "").startswith("Bearer "):
                return self._json({"error": {"message": "no token", "code": 190}}, 401)
            if not os.path.isfile(file):
                return self._json({"error": {"message": "unknown media", "code": 100}}, 404)
            if parts[0] == "media-files":
                data = open(file, "rb").read()
                self.send_response(200)
                self.send_header("Content-Type", "audio/ogg")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
                return
            host = self.headers.get("Host") or f"127.0.0.1:{self.server.server_address[1]}"
            return self._json({"messaging_product": "whatsapp", "id": media_id, "mime_type": "audio/ogg; codecs=opus",
                               "sha256": "lab", "file_size": os.path.getsize(file),
                               "url": f"http://{host}/media-files/{media_id}"})
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, *a):
        pass


HTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 9299), H).serve_forever()
