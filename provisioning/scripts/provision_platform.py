#!/usr/bin/env python3
"""Provision Dad Coach into a running ai-workflow-platform instance - idempotently.

Copied from the Big Boss reference (provision_platform.py at 3f81403) per playbook §21/L6 -
drift detection, publish-only-on-change and instance upgrade are kept as they are.
Applies, in dependency order, everything Dad Coach needs on the platform side:

  1. import each workflow YAML in provisioning/config (upsert by workflow key)
  2. validate it, then publish a new version only if the imported configuration
     differs from what was already there (no version churn on a no-op re-run)
  3. upsert the schedules (matched by name within the workflow) - the owner's daily digest
  4. upsert the WorkerDefinitions (matched by workerKey), set the workflows each
     owns ("workflowKeys" - the workflows Dad Coach may name as workflowKey; the
     platform never chooses among them) and publish them
  5. move every running instance of a workflow that got a new version onto it
     (the platform pins each instance to the version it started on, so without
     this a published fix never reaches existing conversations)

These files are the source of truth, and the platform is where they run. A change
made on the platform itself - edited in its screens, or a suggestion its AI
applied - would be overwritten by the next run, so every run first checks for
one (D-162): it keeps a copy of what it last published to each platform
(~/.config/dad-coach/provision-baseline/<host>/, or PROVISION_BASELINE_DIR) and
stops, showing the difference, when the platform's workflow is no longer that.
  --pull   write the platform's current workflows next to the files
           (provisioning/config/<name>.platform.yaml) with the difference, and
           accept them as the new starting point; take what you want into the
           files, commit, then provision as usual
  --force  overwrite a platform change without taking it (it is lost)
The first run against a platform records its current workflows as the baseline.

The tool/context-provider catalog itself is not provisioned here - it ships as
platform Flyway migrations (V18, V38, V62, V69, V114 weekly_plan_context, V115 dad_dashboard_link); provisioning/catalog/
dad-coach-catalog.json mirrors it so manifest tests catch a missing key.

Usage:
  PLATFORM_BASE_URL=https://<platform-backend> \\
  PLATFORM_ADMIN_API_KEY=<admin key, if that platform has security enabled> \\
  python3 provisioning/scripts/provision_platform.py [--dry-run | --pull | --force] [--resources <file>]

--resources picks another resources file in provisioning/config (default
platform-resources.json: the dad_3 worker and its dad-coach-3 workflow).

Standard library only. Exits non-zero on the first failure.
"""

import difflib
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

CONFIG_DIR = Path(__file__).resolve().parent.parent / "config"


def _splits_a_surrogate_pair(text, chunk=1024):
    """Whether an emoji outside the BMP (two UTF-16 units) straddles a multiple of `chunk` units."""
    units = 0
    for ch in text:
        width = 2 if ord(ch) > 0xFFFF else 1
        if width == 2 and (units + 1) % chunk == 0:
            return True
        units += width
    return False


def without_split_surrogates(text):
    """
    The platform's YAML reader fails ("Range [1024, 1024 + 1) out of bounds") when an emoji outside
    the BMP straddles one of its 1024-character buffer boundaries - any edit can move one there (QA
    2026-09-30: a new line put the 🔴 of the manager prompt exactly on unit 11264). A leading comment
    of the right length moves every emoji off the boundaries; the comment itself changes nothing.
    """
    for pad in range(0, 64):
        candidate = ("#" + " " * pad + "\n" + text) if pad else text
        if not _splits_a_surrogate_pair(candidate):
            return candidate
    raise SystemExit("could not pad the YAML so that no emoji straddles a 1024-unit boundary")


class PlatformClient:
    def __init__(self, base_url, api_key):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key

    def request(self, method, path, body=None, accept="application/json", missing_ok=False):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base_url + path, data=data, method=method)
        req.add_header("Accept", accept)
        if data is not None:
            req.add_header("Content-Type", "application/json")
        if self.api_key:
            req.add_header("X-API-Key", self.api_key)
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                raw = resp.read().decode()
        except urllib.error.HTTPError as e:
            if missing_ok and e.code == 404:
                return None
            raise SystemExit(f"{method} {path} -> HTTP {e.code}: {e.read().decode()[:2000]}")
        if accept != "application/json":
            return raw
        return json.loads(raw) if raw else None


def find_workflow(client, workflow_key):
    listing = client.request("GET", "/api/v1/admin/workflows?size=500")
    for wf in listing["workflows"]:
        if wf["workflowKey"] == workflow_key:
            return wf
    return None


def is_published(client, workflow):
    """ACTIVE is not published: an imported workflow is ACTIVE before any version exists (a first
    publish that failed validation leaves it so), and only a published version can serve turns."""
    detail = client.request("GET", f"/api/v1/admin/workflows/{workflow['id']}")
    return workflow.get("status") == "ACTIVE" and bool(detail.get("versions"))


def export_yaml(client, workflow_id):
    return client.request("GET", f"/api/v1/admin/workflows/{workflow_id}/export", accept="application/x-yaml")


class Baseline:
    """What this script last published to one platform (D-162) - the reference for a change made there."""

    def __init__(self, base_url):
        host = urllib.parse.urlparse(base_url).netloc.replace(":", "_") or "platform"
        root = os.environ.get("PROVISION_BASELINE_DIR") or str(Path.home() / ".config" / "dad-coach" / "provision-baseline")
        self.dir = Path(root) / host

    def path(self, workflow_key):
        return self.dir / f"{workflow_key}.yaml"

    def read(self, workflow_key):
        path = self.path(workflow_key)
        return path.read_text() if path.exists() else None

    def write(self, workflow_key, exported):
        self.dir.mkdir(parents=True, exist_ok=True)
        self.path(workflow_key).write_text(exported)

    def source_unchanged(self, workflow_key, yaml_content):
        """Whether the file is what was last provisioned from - then nothing is planned, so "Changes" stays clean."""
        path = self.dir / f"{workflow_key}.source.sha256"
        return path.exists() and path.read_text() == hashlib.sha256(yaml_content.encode()).hexdigest()

    def record_source(self, workflow_key, yaml_content):
        self.dir.mkdir(parents=True, exist_ok=True)
        (self.dir / f"{workflow_key}.source.sha256").write_text(hashlib.sha256(yaml_content.encode()).hexdigest())


def platform_changes(baseline_text, current_text, limit=120):
    """The unified difference between what was last published and what the platform has now."""
    lines = list(difflib.unified_diff(
        baseline_text.splitlines(), current_text.splitlines(), "last provisioned", "on the platform now", lineterm="", n=1))
    return lines[:limit] + ([f"... ({len(lines) - limit} more lines)"] if len(lines) > limit else [])


def check_platform_changes(client, baseline, filename, workflow_key, mode):
    """Stops before an import would overwrite a change made on the platform itself; --pull takes it out first."""
    existing = find_workflow(client, workflow_key)
    if not existing:
        return
    current = export_yaml(client, existing["id"])
    known = baseline.read(workflow_key)
    if known is None:
        baseline.write(workflow_key, current)
        print(f"{workflow_key}: first run against this platform - its current workflow recorded as the baseline")
        return
    if known == current:
        return
    if mode == "pull":
        side = CONFIG_DIR / (Path(filename).stem + ".platform.yaml")
        side.write_text(current)
        print(f"{workflow_key}: changed on the platform - written to {side.relative_to(CONFIG_DIR.parent.parent)}:")
        print("\n".join(platform_changes(known, current)))
        baseline.write(workflow_key, current)
        print(f"{workflow_key}: accepted as the new baseline - take what you want into {filename}, then provision")
        return
    if mode == "force":
        print(f"{workflow_key}: changed on the platform - overwriting it (--force):")
        print("\n".join(platform_changes(known, current)))
        return
    print("\n".join(platform_changes(known, current)))
    raise SystemExit(
        f"{workflow_key} was changed on the platform since it was last provisioned (above). Nothing was changed.\n"
        f"  --pull   write it next to {filename} and accept it as the baseline, to take the change into the file\n"
        f"  --force  overwrite it with {filename} (the platform change is lost)")


def changed_parts(before, after):
    """Which parts of a workflow an import changed - its global settings and each state, by key - for the version notes."""
    def parts(text):
        found, key, lines = {}, "global settings", []
        for line in (text or "").splitlines():
            match = re.match(r"\s*- stateKey: ['\"]?([A-Z0-9_]+)", line)
            if match:
                found[key] = "\n".join(lines)
                key, lines = match.group(1), []
            lines.append(line)
        found[key] = "\n".join(lines)
        return found
    old, new = parts(before), parts(after)
    return [key for key in new if old.get(key) != new[key]] + [f"{key} (removed)" for key in old if key not in new]


def source_of(filename):
    """The commit the file comes from ("<hash> <subject>") when provisioning runs from a git checkout, else None."""
    try:
        out = subprocess.run(["git", "log", "-1", "--format=%h %s", "--", str(CONFIG_DIR / filename)],
                             cwd=CONFIG_DIR, capture_output=True, text=True, timeout=10)
    except (OSError, subprocess.SubprocessError):
        return None
    return out.stdout.strip() or os.environ.get("PROVISION_SOURCE") or None


def change_notes(filename, before, after):
    """What the platform's version history shows for this publish: the source commit, its decision, what changed (D-162)."""
    notes = f"Provisioned from dad-coach provisioning/config/{filename}"
    source = source_of(filename)
    if source:
        notes += f" @ {source}"
    changed = changed_parts(before, after) if before else []
    if changed:
        notes += " | changed: " + ", ".join(changed)
    return notes[:1000]


def import_as_change(client, workflow_id, yaml_content, summary):
    """Imports into an existing workflow through the platform's change flow (D-163), so it is listed with its
    diff under the workflow's "Changes" - like an edit in its screens or its assistant - and can be undone there.

    The platform's workflow document also holds the schedules, which these files do not (they are upserted
    from platform-resources.json): the platform's current schedules go with the file, and a plan that would
    still remove one is refused. Returns False when the platform has no such flow (the caller imports the old
    way), else True (applied, or nothing to change)."""
    current = client.request("GET", f"/api/v1/admin/authoring/workflows/{workflow_id}/document", missing_ok=True)
    if current is None:
        return False
    # JSON is YAML: the schedules ride as one flow-style line at the end of the file.
    content = yaml_content.rstrip() + "\nschedules: " + json.dumps(current.get("schedules") or [], ensure_ascii=False) + "\n"
    plan = client.request("POST", "/api/v1/admin/authoring/import/plan", {"content": content, "summary": summary}, missing_ok=True)
    if plan is None:
        return False
    change = plan["changeset"]
    diff = change.get("diff") or []
    if any(entry.get("kind") == "REMOVED" and str(entry.get("path", "")).startswith("schedules/") for entry in diff):
        client.request("POST", f"/api/v1/admin/authoring/changesets/{change['id']}/reject")
        raise SystemExit(f"refusing to import: the plan would remove a schedule: {json.dumps(diff, ensure_ascii=False)[:1500]}")
    if not diff:
        client.request("POST", f"/api/v1/admin/authoring/changesets/{change['id']}/reject")
        return True
    for entry in diff:
        print(f"  {entry.get('kind', '').lower():8s} {entry.get('summary') or entry.get('path')}")
    client.request("POST", f"/api/v1/admin/authoring/changesets/{change['id']}/apply")
    return True


def provision_workflow(client, filename, dry_run, baseline=None, mode=None):
    yaml_content = without_split_surrogates((CONFIG_DIR / filename).read_text())
    check = client.request("POST", "/api/v1/admin/workflows/import/validate", {"yamlContent": yaml_content})
    if not check.get("isValid", check.get("valid", False)):
        raise SystemExit(f"{filename}: import validation failed: {json.dumps(check)}")
    if baseline is not None:
        check_platform_changes(client, baseline, filename, check["workflowKey"], mode)
        if mode == "pull":
            return None
    if dry_run:
        print(f"[dry-run] {filename}: import validation OK ({check.get('operation')} {check.get('workflowKey')})")
        return None

    # The platform's own export before and after the import tells whether this
    # run changed anything; an unchanged, already-published workflow is not
    # republished, so re-running provisioning does not churn versions.
    existing = find_workflow(client, check["workflowKey"])
    published = bool(existing) and is_published(client, existing)
    if (published and mode != "force" and baseline is not None
            and baseline.source_unchanged(existing["workflowKey"], yaml_content)):
        print(f"{existing['workflowKey']}: {filename} unchanged since it was last provisioned - nothing to do")
        upgrade_instances(client, existing["workflowKey"], existing["id"])
        return existing["workflowKey"], existing["id"]
    before = export_yaml(client, existing["id"]) if existing else None

    source = source_of(filename)
    summary = f"Provisioned from dad-coach {filename}" + (f" @ {source}" if source else "")
    if existing and import_as_change(client, existing["id"], yaml_content, summary[:400]):
        workflow_key, workflow_id = existing["workflowKey"], existing["id"]
        print(f"{filename}: imported into {workflow_key} ({workflow_id}) as a change on the platform")
    else:
        result = client.request("POST", "/api/v1/admin/workflows/import", {"yamlContent": yaml_content})
        if not result.get("success"):
            raise SystemExit(f"{filename}: import failed: {result.get('errorMessage') or json.dumps(result)}")
        workflow_key, workflow_id = result["workflowKey"], result["workflowId"]
        print(f"{filename}: {result['operation']} {workflow_key} ({workflow_id}) - "
              f"{result['statesCreated']} states, {result['toolsAssigned']} tools, "
              f"{result['contextProvidersAssigned']} context providers, {result['transitionsCreated']} transitions")

    validation = client.request("POST", f"/api/v1/admin/workflows/{workflow_id}/validate")
    if not validation.get("valid", False):
        raise SystemExit(f"{workflow_key}: publish validation failed: {json.dumps(validation)}")

    after = export_yaml(client, workflow_id)
    if published and before == after:
        print(f"{workflow_key}: configuration unchanged and already published - not republishing")
    else:
        notes = change_notes(filename, before, after)
        version = client.request("POST", f"/api/v1/admin/workflows/{workflow_id}/publish", {"changeNotes": notes})
        print(f"{workflow_key}: published version {version['versionNumber']} - {notes}")
    if baseline is not None:
        baseline.write(workflow_key, export_yaml(client, workflow_id))
        baseline.record_source(workflow_key, yaml_content)
    upgrade_instances(client, workflow_key, workflow_id)
    return workflow_key, workflow_id


def upgrade_instances(client, workflow_key, workflow_id):
    """Upgrade running instances to the latest published version, preserving their state.

    Uses the platform's own state-preserving upgrade; an instance whose current state no longer
    exists comes back MAPPING_REQUIRED and is left untouched for an operator to map.
    """
    page, outcomes = 0, {}
    while True:
        listing = client.request("GET", f"/api/v1/admin/instances?workflowId={workflow_id}&status=ACTIVE&size=100&page={page}")
        for instance in listing["content"]:
            result = client.request("POST", f"/api/v1/admin/instances/{instance['id']}/upgrade", {})
            outcomes[result["status"]] = outcomes.get(result["status"], 0) + 1
            if result["status"] not in ("UPGRADED", "NO_UPGRADE_AVAILABLE"):
                print(f"  instance {instance['id']}: {result['status']} - {result.get('message')}")
        if listing.get("last", True) or page + 1 >= listing.get("totalPages", 1):
            break
        page += 1
    if outcomes:
        print(f"{workflow_key}: running instances -> {outcomes}")


def provision_schedules(client, schedules, workflow_ids, dry_run):
    for spec in schedules:
        workflow_id = workflow_ids.get(spec["workflowKey"])
        if workflow_id is None:
            wf = find_workflow(client, spec["workflowKey"])
            if wf is None:
                if dry_run:
                    print(f"[dry-run] schedule '{spec['name']}': workflow {spec['workflowKey']} not provisioned yet")
                    continue
                raise SystemExit(f"schedule '{spec['name']}': workflow {spec['workflowKey']} not found")
            workflow_id = wf["id"]
        body = {k: v for k, v in spec.items() if k != "workflowKey"}
        base = f"/api/v1/admin/workflow-definitions/{workflow_id}/schedules"
        existing = next((s for s in client.request("GET", base) if s["name"] == spec["name"]), None)
        if dry_run:
            print(f"[dry-run] schedule '{spec['name']}': would {'update' if existing else 'create'}")
        elif existing:
            client.request("PUT", f"{base}/{existing['id']}", body)
            print(f"schedule '{spec['name']}': updated ({existing['id']})")
        else:
            created = client.request("POST", base, body)
            print(f"schedule '{spec['name']}': created ({created['id']})")


def workflow_id_of(client, workflow_ids, key):
    if key in workflow_ids:
        return workflow_ids[key]
    wf = find_workflow(client, key)
    return wf["id"] if wf else None


def provision_workers(client, workers, workflow_ids, dry_run):
    existing_by_key = {w["workerKey"]: w for w in client.request("GET", "/api/v1/admin/workers")}
    for spec in workers:
        workflow_id = workflow_ids.get(spec["defaultWorkflowKey"])
        if workflow_id is None:
            wf = find_workflow(client, spec["defaultWorkflowKey"])
            workflow_id = wf["id"] if wf else None
        if workflow_id is None:
            if dry_run:
                print(f"[dry-run] worker {spec['workerKey']}: workflow {spec['defaultWorkflowKey']} not provisioned yet")
                continue
            raise SystemExit(f"worker {spec['workerKey']}: workflow {spec['defaultWorkflowKey']} not found")
        body = {k: v for k, v in spec.items() if k not in ("defaultWorkflowKey", "workflowKeys")}
        body["defaultWorkflowId"] = workflow_id
        owned_keys = spec.get("workflowKeys") or [spec["defaultWorkflowKey"]]
        owned_ids = [workflow_id_of(client, workflow_ids, key) for key in owned_keys]
        missing = [key for key, wid in zip(owned_keys, owned_ids) if wid is None]
        if missing:
            if dry_run:
                print(f"[dry-run] worker {spec['workerKey']}: workflows {missing} not provisioned yet")
                continue
            raise SystemExit(f"worker {spec['workerKey']}: workflows {missing} not found")
        existing = existing_by_key.get(spec["workerKey"])
        if dry_run:
            print(f"[dry-run] worker {spec['workerKey']}: would {'update' if existing else 'create'}, owning {owned_keys}")
            continue
        if existing:
            worker = client.request("PUT", f"/api/v1/admin/workers/{existing['id']}", body)
            print(f"worker {spec['workerKey']}: updated ({existing['id']})")
        else:
            worker = client.request("POST", "/api/v1/admin/workers", body)
            print(f"worker {spec['workerKey']}: created ({worker['id']})")
        if spec.get("workflowKeys"):
            # Only for a worker that lists its workflows: an older platform has no such endpoint, and a worker
            # with only a default workflow owns exactly that one already.
            client.request("PUT", f"/api/v1/admin/workers/{worker['id']}/workflows", {"workflowIds": owned_ids})
            print(f"worker {spec['workerKey']}: owns {owned_keys}")
        if not worker.get("isPublished"):
            client.request("POST", f"/api/v1/admin/workers/{worker['id']}/publish")
            print(f"worker {spec['workerKey']}: published")


def resources_file(args):
    """--resources <file> (or --resources=<file>), a file name in provisioning/config."""
    for i, arg in enumerate(args):
        if arg.startswith("--resources="):
            return arg.split("=", 1)[1]
        if arg == "--resources" and i + 1 < len(args):
            return args[i + 1]
    return "platform-resources.json"


def main():
    dry_run = "--dry-run" in sys.argv[1:]
    mode = "pull" if "--pull" in sys.argv[1:] else "force" if "--force" in sys.argv[1:] else None
    base_url = os.environ.get("PLATFORM_BASE_URL")
    if not base_url:
        raise SystemExit("PLATFORM_BASE_URL is required (the ai-workflow-platform backend's base URL)")
    client = PlatformClient(base_url, os.environ.get("PLATFORM_ADMIN_API_KEY"))
    baseline = Baseline(base_url)
    resources = json.loads((CONFIG_DIR / resources_file(sys.argv[1:])).read_text())

    workflow_ids = {}
    for filename in resources["workflows"]:
        provisioned = provision_workflow(client, filename, dry_run, baseline, mode)
        if provisioned:
            workflow_ids[provisioned[0]] = provisioned[1]
    if mode == "pull":
        print("Pull complete - nothing was provisioned.")
        return
    provision_schedules(client, resources["schedules"], workflow_ids, dry_run)
    provision_workers(client, resources["workers"], workflow_ids, dry_run)
    print("Provisioning complete." if not dry_run else "Dry run complete - nothing was changed.")


if __name__ == "__main__":
    main()
