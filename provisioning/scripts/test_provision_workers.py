"""provision_platform.py: a worker that lists its workflows gets exactly those; one that does not is untouched.

python3 -m unittest test_provision_workers   (from provisioning/scripts)
"""
import json
import unittest
from pathlib import Path

import provision_platform as pp


class FakeClient:
    def __init__(self, workers=(), workflows=None):
        self.workers = list(workers)
        self.workflows = workflows or {}
        self.calls = []

    def request(self, method, path, body=None):
        self.calls.append((method, path, body))
        if method == "GET" and path == "/api/v1/admin/workers":
            return self.workers
        if method == "POST" and path == "/api/v1/admin/workers":
            return {"id": "new-worker", "isPublished": False}
        if method == "PUT" and path.startswith("/api/v1/admin/workers/") and not path.endswith("/workflows"):
            return {"id": path.rsplit("/", 1)[1], "isPublished": True}
        return {}


class ProvisionWorkersTest(unittest.TestCase):

    def setUp(self):
        self._find = pp.find_workflow
        pp.find_workflow = lambda client, key: {"id": "id-" + key}

    def tearDown(self):
        pp.find_workflow = self._find

    def test_a_worker_with_workflow_keys_owns_exactly_them(self):
        client = FakeClient()
        pp.provision_workers(client, [{"workerKey": "dad_3", "name": "x", "defaultWorkflowKey": "wf_m",
                                       "workflowKeys": ["wf_e", "wf_m", "wf_t"]}], {}, dry_run=False)
        create = next(c for c in client.calls if c[0] == "POST" and c[1] == "/api/v1/admin/workers")
        self.assertNotIn("workflowKeys", create[2])
        self.assertEqual(create[2]["defaultWorkflowId"], "id-wf_m")
        assign = next(c for c in client.calls if c[1] == "/api/v1/admin/workers/new-worker/workflows")
        self.assertEqual(assign, ("PUT", "/api/v1/admin/workers/new-worker/workflows",
                                  {"workflowIds": ["id-wf_e", "id-wf_m", "id-wf_t"]}))

    def test_a_legacy_worker_without_workflow_keys_is_not_reassigned(self):
        client = FakeClient(workers=[{"workerKey": "dad_manager", "id": "w1"}])
        pp.provision_workers(client, [{"workerKey": "dad_manager", "name": "x",
                                       "defaultWorkflowKey": "dad_manager_wf"}], {}, dry_run=False)
        self.assertFalse([c for c in client.calls if c[1].endswith("/workflows")])

    def test_resources_file_option(self):
        self.assertEqual(pp.resources_file([]), "platform-resources.json")
        self.assertEqual(pp.resources_file(["--resources", "other.json"]), "other.json")
        self.assertEqual(pp.resources_file(["--dry-run", "--resources=x.json"]), "x.json")

    def test_dad_coach_is_one_worker_with_one_workflow(self):
        config = Path(pp.CONFIG_DIR)
        resources = json.loads((config / "platform-resources.json").read_text())
        (worker,) = resources["workers"]
        self.assertEqual(worker["workerKey"], "dad_3")
        self.assertEqual(worker["workflowKeys"], ["dad-coach-3"])
        self.assertEqual(resources["workflows"], ["dad-coach-3-workflow.yaml"])
        by_workflow = {}
        for s in resources["schedules"]:
            by_workflow.setdefault(s["workflowKey"], set()).add(s["targetStateKey"])
        self.assertEqual(by_workflow, {"dad-coach-3": {"ACTIVE_COACHING"}})


if __name__ == "__main__":
    unittest.main()
