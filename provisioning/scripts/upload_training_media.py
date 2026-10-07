#!/usr/bin/env python3
"""Uploads Dad Coach's training videos and posters to the owner's Bunny storage zone and verifies them.

Every path the product's catalog names (backend/src/main/resources/training/catalog.json: "video"/"poster", e.g.
dad-coach/training/v1/father-welcome.mp4) is read from the local release folder - the part after "dad-coach/" -
and PUT to Bunny storage under the catalog path, then its size is compared with what storage reports. A file that
already has the same size is skipped. Never prints a secret.

  python3 provisioning/scripts/upload_training_media.py <release folder> [--creds ~/.config/big-boss/bunny-upload.env]
Credentials file: BUNNY_STORAGE_ZONE, BUNNY_STORAGE_PASSWORD, optional BUNNY_STORAGE_ENDPOINT (storage.bunnycdn.com).
A replaced video goes to a new path (v2/...), never over a published one (players cache by URL).
"""
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "backend" / "src" / "main" / "resources" / "training" / "catalog.json"


def creds(path):
    values = {}
    for line in Path(path).expanduser().read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            values[k.strip()] = v.strip()
    return values


def storage_size(base, key, path):
    folder, _, name = path.rpartition("/")
    req = urllib.request.Request(f"{base}/{folder}/", headers={"AccessKey": key, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            for item in json.load(r):
                if item.get("ObjectName") == name:
                    return item.get("Length")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
    return None


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    release = Path(sys.argv[1])
    a = sys.argv[1:]
    c = creds(a[a.index("--creds") + 1] if "--creds" in a else "~/.config/big-boss/bunny-upload.env")
    base = f"https://{c.get('BUNNY_STORAGE_ENDPOINT', 'storage.bunnycdn.com')}/{c['BUNNY_STORAGE_ZONE']}"
    key = c["BUNNY_STORAGE_PASSWORD"]
    entries = json.loads(CATALOG.read_text())
    entries = entries.get("videos", entries) if isinstance(entries, dict) else entries
    paths = [p for e in entries for p in (e.get("video"), e.get("poster")) if p]
    failed = 0
    for path in paths:
        local = release / path.removeprefix("dad-coach/")
        if not local.exists():
            print(f"MISSING locally: {local}")
            failed += 1
            continue
        size = local.stat().st_size
        if storage_size(base, key, path) == size:
            print(f"same      {path} ({size} bytes)")
            continue
        ctype = "video/mp4" if path.endswith(".mp4") else "image/jpeg"
        req = urllib.request.Request(f"{base}/{path}", data=local.read_bytes(), method="PUT",
                                     headers={"AccessKey": key, "Content-Type": ctype})
        with urllib.request.urlopen(req, timeout=300) as r:
            r.read()
        ok = storage_size(base, key, path) == size
        print(f"{'uploaded ' if ok else 'MISMATCH '} {path} ({size} bytes)")
        failed += 0 if ok else 1
    print(f"{len(paths) - failed}/{len(paths)} in storage")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
