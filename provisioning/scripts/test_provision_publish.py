"""provision_platform.py: "already published" means a published version exists, not status ACTIVE.

python3 -m unittest test_provision_publish   (from provisioning/scripts)
"""
import unittest

import provision_platform as pp


class Detail:
    def __init__(self, versions):
        self.versions = versions

    def request(self, method, path, body=None):
        assert (method, path) == ("GET", "/api/v1/admin/workflows/wf-1")
        return {"id": "wf-1", "status": "ACTIVE", "versions": self.versions}


class IsPublishedTest(unittest.TestCase):
    def test_an_imported_workflow_whose_first_publish_failed_is_not_published(self):
        self.assertFalse(pp.is_published(Detail([]), {"id": "wf-1", "status": "ACTIVE"}))

    def test_a_workflow_with_a_version_is_published(self):
        self.assertTrue(pp.is_published(Detail([{"versionNumber": 1}]), {"id": "wf-1", "status": "ACTIVE"}))

    def test_an_archived_workflow_is_not_published(self):
        self.assertFalse(pp.is_published(Detail([{"versionNumber": 1}]), {"id": "wf-1", "status": "ARCHIVED"}))


if __name__ == "__main__":
    unittest.main()
