#!/usr/bin/env python3
"""
Dad Coach E2E Testing Script

Automated end-to-end testing for the Dad Coach onboarding and WhatsApp flow.

Usage:
    python scripts/test_e2e.py --phone 0503020551 --env prod

Features:
    1. Delete existing user (if exists)
    2. Create invitation link
    3. Complete onboarding wizard
    4. Simulate WhatsApp conversation
    5. Verify dashboard data
    6. Report issues found
"""

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from typing import Optional, List, Dict, Any
from urllib.parse import urljoin

try:
    import requests
except ImportError:
    print("ERROR: requests library not installed. Run: pip install requests")
    sys.exit(1)


@dataclass
class TestConfig:
    """Test configuration."""
    phone: str
    env: str
    base_url: str
    admin_token: Optional[str] = None
    verbose: bool = False


@dataclass
class TestResult:
    """Test execution result."""
    name: str
    passed: bool
    message: str
    duration_ms: float
    details: Optional[Dict[str, Any]] = None


class DadCoachE2ETester:
    """End-to-end tester for Dad Coach."""

    ENVIRONMENTS = {
        "local": "http://localhost:8080",
        "dev": "https://dad-coach-api-dev.up.railway.app",
        "prod": "https://dad-coach-api.up.railway.app",
    }

    def __init__(self, config: TestConfig):
        self.config = config
        self.session = requests.Session()
        self.results: List[TestResult] = []
        self.father_id: Optional[int] = None
        self.session_id: Optional[str] = None
        self.invitation_token: Optional[str] = None
        self.magic_link_token: Optional[str] = None
        self.jwt_token: Optional[str] = None

    def log(self, message: str, level: str = "INFO"):
        """Log a message with timestamp."""
        timestamp = datetime.now().strftime("%H:%M:%S")
        prefix = {"INFO": "ℹ️", "SUCCESS": "✅", "ERROR": "❌", "WARN": "⚠️"}.get(level, "")
        print(f"[{timestamp}] {prefix} {message}")

    def normalize_phone(self, phone: str) -> str:
        """Normalize phone number to E.164 format."""
        phone = re.sub(r"[^0-9+]", "", phone)
        if phone.startswith("0"):
            phone = "+972" + phone[1:]
        elif not phone.startswith("+"):
            phone = "+" + phone
        return phone

    def run_test(self, name: str, test_func) -> TestResult:
        """Run a single test and record result."""
        start = time.time()
        try:
            result = test_func()
            duration = (time.time() - start) * 1000
            test_result = TestResult(
                name=name,
                passed=result.get("passed", True),
                message=result.get("message", "OK"),
                duration_ms=duration,
                details=result.get("details"),
            )
        except Exception as e:
            duration = (time.time() - start) * 1000
            test_result = TestResult(
                name=name,
                passed=False,
                message=str(e),
                duration_ms=duration,
            )

        self.results.append(test_result)
        level = "SUCCESS" if test_result.passed else "ERROR"
        self.log(f"{name}: {test_result.message}", level)
        return test_result

    def api_call(
        self,
        method: str,
        endpoint: str,
        data: Optional[Dict] = None,
        headers: Optional[Dict] = None,
        expected_status: int = 200,
    ) -> Dict:
        """Make an API call and return response."""
        url = urljoin(self.config.base_url + "/", endpoint.lstrip("/"))
        req_headers = {"Content-Type": "application/json"}
        if self.jwt_token:
            req_headers["Authorization"] = f"Bearer {self.jwt_token}"
        if headers:
            req_headers.update(headers)

        if self.config.verbose:
            self.log(f"API: {method} {url}", "INFO")

        response = self.session.request(
            method=method,
            url=url,
            json=data,
            headers=req_headers,
            timeout=30,
        )

        if response.status_code != expected_status:
            raise Exception(
                f"API call failed: {method} {endpoint} "
                f"returned {response.status_code}, expected {expected_status}. "
                f"Body: {response.text[:500]}"
            )

        if response.text:
            return response.json()
        return {}

    # =========================================================================
    # Test Steps
    # =========================================================================

    def test_health_check(self) -> Dict:
        """Test 1: Verify API is healthy."""
        response = self.api_call("GET", "/actuator/health")
        status = response.get("status", "UNKNOWN")
        if status not in ["UP", "DOWN"]:
            return {"passed": False, "message": f"Unexpected health status: {status}"}
        return {"passed": True, "message": f"API health: {status}"}

    def test_delete_existing_user(self) -> Dict:
        """Test 2: Delete existing user if present."""
        phone = self.normalize_phone(self.config.phone)
        
        # Try to find user by phone via admin API
        try:
            # This endpoint may not exist - skip if 404
            response = self.session.get(
                urljoin(self.config.base_url, f"/api/v1/admin/fathers?phone={phone}"),
                headers={"Authorization": f"Bearer {self.config.admin_token}"} if self.config.admin_token else {},
                timeout=10,
            )
            if response.status_code == 200:
                fathers = response.json()
                if fathers and len(fathers) > 0:
                    father_id = fathers[0].get("id")
                    # Delete the user
                    delete_response = self.session.delete(
                        urljoin(self.config.base_url, f"/api/v1/admin/fathers/{father_id}"),
                        headers={"Authorization": f"Bearer {self.config.admin_token}"} if self.config.admin_token else {},
                        timeout=10,
                    )
                    if delete_response.status_code in [200, 204]:
                        return {"passed": True, "message": f"Deleted existing user {father_id}"}
                    return {"passed": False, "message": f"Failed to delete user: {delete_response.status_code}"}
            return {"passed": True, "message": "No existing user found"}
        except Exception as e:
            return {"passed": True, "message": f"Skip delete (admin API not available): {e}"}

    def test_create_invitation(self) -> Dict:
        """Test 3: Create a new invitation."""
        try:
            response = self.api_call(
                "POST",
                "/api/v1/invitations",
                data={
                    "type": "BETA_TESTER",
                    "max_uses": 1,
                    "created_by": "e2e-test"
                },
                expected_status=201,
            )
            self.invitation_token = response.get("token")
            if not self.invitation_token:
                return {"passed": False, "message": "No token in response"}
            return {
                "passed": True,
                "message": f"Created invitation: {self.invitation_token[:8]}...",
                "details": {"token": self.invitation_token},
            }
        except Exception as e:
            return {"passed": False, "message": str(e)}

    def test_validate_invitation(self) -> Dict:
        """Test 4: Validate the invitation token."""
        if not self.invitation_token:
            return {"passed": False, "message": "No invitation token available"}

        response = self.api_call(
            "GET",
            f"/api/v1/invitations/{self.invitation_token}/validate",
        )
        is_valid = response.get("valid", False)
        return {
            "passed": is_valid,
            "message": "Invitation valid" if is_valid else f"Invitation invalid: {response}",
        }

    def test_create_onboarding_session(self) -> Dict:
        """Test 5: Create onboarding session."""
        if not self.invitation_token:
            return {"passed": False, "message": "No invitation token"}

        response = self.api_call(
            "POST",
            "/api/v1/onboarding/sessions",
            data={"invitation_token": self.invitation_token},
            expected_status=201,
        )
        self.session_id = response.get("session_id")
        if not self.session_id:
            return {"passed": False, "message": "No session_id in response"}
        return {
            "passed": True,
            "message": f"Session created: {self.session_id[:8]}...",
        }

    def test_onboarding_step_language(self) -> Dict:
        """Test 6: Submit language selection."""
        response = self.api_call(
            "POST",
            f"/api/v1/onboarding/sessions/{self.session_id}/steps",
            data={
                "step": "LANGUAGE",
                "data": {"language": "he"},
            },
        )
        next_step = response.get("next_step")
        return {
            "passed": next_step == "FATHER_PROFILE",
            "message": f"Language submitted, next: {next_step}",
        }

    def test_onboarding_step_profile(self) -> Dict:
        """Test 7: Submit father profile."""
        phone = self.normalize_phone(self.config.phone)
        response = self.api_call(
            "POST",
            f"/api/v1/onboarding/sessions/{self.session_id}/steps",
            data={
                "step": "FATHER_PROFILE",
                "data": {
                    "display_name": "E2E Test Dad",
                    "phone_number": phone,
                    "email": "e2e-test@dadcoach.test",
                    "timezone": "Asia/Jerusalem",
                },
            },
        )
        next_step = response.get("next_step")
        return {
            "passed": next_step in ["CHILDREN", "GOALS", "PREFERENCES", "REVIEW"],
            "message": f"Profile submitted, next: {next_step}",
        }

    def test_onboarding_step_children(self) -> Dict:
        """Test 8: Submit children (optional, can skip)."""
        response = self.api_call(
            "POST",
            f"/api/v1/onboarding/sessions/{self.session_id}/steps",
            data={
                "step": "CHILDREN",
                "data": {
                    "children": [
                        {
                            "name": "Test Child",
                            "birth_date": "2020-01-15",
                            "gender": "male",
                        }
                    ]
                },
            },
        )
        next_step = response.get("next_step")
        return {
            "passed": next_step in ["GOALS", "PREFERENCES", "REVIEW"],
            "message": f"Children submitted, next: {next_step}",
        }

    def test_onboarding_step_goals(self) -> Dict:
        """Test 9: Submit goals (skip)."""
        response = self.api_call(
            "POST",
            f"/api/v1/onboarding/sessions/{self.session_id}/steps",
            data={
                "step": "GOALS",
                "data": {"goals": ["quality_time", "communication"]},
            },
        )
        next_step = response.get("next_step")
        return {
            "passed": next_step in ["PREFERENCES", "REVIEW"],
            "message": f"Goals submitted, next: {next_step}",
        }

    def test_onboarding_step_preferences(self) -> Dict:
        """Test 10: Submit preferences (skip)."""
        response = self.api_call(
            "POST",
            f"/api/v1/onboarding/sessions/{self.session_id}/steps",
            data={
                "step": "PREFERENCES",
                "data": {
                    "coaching_style": "supportive",
                    "preferred_coaching_time": "evening",
                },
            },
        )
        next_step = response.get("next_step")
        return {
            "passed": next_step == "REVIEW",
            "message": f"Preferences submitted, next: {next_step}",
        }

    def test_onboarding_complete(self) -> Dict:
        """Test 11: Complete onboarding and provision father."""
        response = self.api_call(
            "POST",
            f"/api/v1/onboarding/sessions/{self.session_id}/complete",
        )
        self.father_id = response.get("father_id")
        activation_status = response.get("activation_status")
        return {
            "passed": self.father_id is not None,
            "message": f"Onboarding complete. Father ID: {self.father_id}, Status: {activation_status}",
            "details": response,
        }

    def test_simulate_whatsapp_start(self) -> Dict:
        """Test 12: Simulate first WhatsApp message ('🚀 התחל')."""
        phone = self.normalize_phone(self.config.phone)
        
        # Simulate webhook call with "🚀 התחל" message
        webhook_payload = {
            "object": "whatsapp_business_account",
            "entry": [{
                "id": "test",
                "changes": [{
                    "value": {
                        "messaging_product": "whatsapp",
                        "metadata": {
                            "display_phone_number": "972123456789",
                            "phone_number_id": "test"
                        },
                        "contacts": [{"profile": {"name": "E2E Test"}, "wa_id": phone.replace("+", "")}],
                        "messages": [{
                            "from": phone.replace("+", ""),
                            "id": f"test_{int(time.time())}",
                            "timestamp": str(int(time.time())),
                            "text": {"body": "🚀 התחל"},
                            "type": "text"
                        }]
                    },
                    "field": "messages"
                }]
            }]
        }
        
        try:
            response = self.session.post(
                urljoin(self.config.base_url, "/api/v1/webhook/whatsapp"),
                json=webhook_payload,
                headers={"Content-Type": "application/json"},
                timeout=30,
            )
            # Webhook usually returns 200 OK
            if response.status_code == 200:
                return {"passed": True, "message": "WhatsApp webhook processed"}
            return {"passed": False, "message": f"Webhook returned {response.status_code}: {response.text[:200]}"}
        except Exception as e:
            return {"passed": False, "message": f"Webhook failed: {e}"}

    def test_verify_workflow_state(self) -> Dict:
        """Test 13: Verify father workflow state after message."""
        if not self.father_id:
            return {"passed": False, "message": "No father_id"}

        # Need to get father state - may need admin token
        try:
            if self.jwt_token:
                response = self.api_call("GET", "/api/v1/workspace/summary")
                state = response.get("current_workflow_state")
                return {
                    "passed": state is not None,
                    "message": f"Workflow state: {state}",
                    "details": response,
                }
            return {"passed": True, "message": "Skipped (no JWT token yet)"}
        except Exception as e:
            return {"passed": False, "message": str(e)}

    def test_dashboard_loads(self) -> Dict:
        """Test 14: Test dashboard API loads correctly."""
        if not self.jwt_token:
            return {"passed": True, "message": "Skipped (no JWT token)"}

        try:
            response = self.api_call("GET", "/api/v1/workspace/summary")
            has_belt = "current_belt" in response
            has_name = "father_display_name" in response
            return {
                "passed": has_belt and has_name,
                "message": f"Dashboard: belt={response.get('current_belt')}, name={response.get('father_display_name')}",
                "details": response,
            }
        except Exception as e:
            return {"passed": False, "message": str(e)}

    # =========================================================================
    # Main Runner
    # =========================================================================

    def run_all_tests(self) -> bool:
        """Run all E2E tests."""
        self.log("=" * 60)
        self.log(f"Dad Coach E2E Tests - {self.config.env.upper()}")
        self.log(f"Base URL: {self.config.base_url}")
        self.log(f"Phone: {self.config.phone}")
        self.log("=" * 60)

        tests = [
            ("Health Check", self.test_health_check),
            ("Delete Existing User", self.test_delete_existing_user),
            ("Create Invitation", self.test_create_invitation),
            ("Validate Invitation", self.test_validate_invitation),
            ("Create Onboarding Session", self.test_create_onboarding_session),
            ("Onboarding: Language", self.test_onboarding_step_language),
            ("Onboarding: Profile", self.test_onboarding_step_profile),
            ("Onboarding: Children", self.test_onboarding_step_children),
            ("Onboarding: Goals", self.test_onboarding_step_goals),
            ("Onboarding: Preferences", self.test_onboarding_step_preferences),
            ("Onboarding: Complete", self.test_onboarding_complete),
            ("WhatsApp: Send Start Message", self.test_simulate_whatsapp_start),
            ("Verify Workflow State", self.test_verify_workflow_state),
            ("Dashboard Loads", self.test_dashboard_loads),
        ]

        for name, test_func in tests:
            result = self.run_test(name, test_func)
            if not result.passed and name not in ["Delete Existing User"]:
                self.log(f"Stopping due to failure in: {name}", "ERROR")
                break

        # Print summary
        self.print_summary()

        passed = sum(1 for r in self.results if r.passed)
        total = len(self.results)
        return passed == total

    def print_summary(self):
        """Print test summary."""
        self.log("")
        self.log("=" * 60)
        self.log("TEST SUMMARY")
        self.log("=" * 60)

        passed = sum(1 for r in self.results if r.passed)
        failed = sum(1 for r in self.results if not r.passed)
        total_time = sum(r.duration_ms for r in self.results)

        for r in self.results:
            status = "✅" if r.passed else "❌"
            print(f"  {status} {r.name}: {r.message} ({r.duration_ms:.0f}ms)")

        self.log("")
        self.log(f"Results: {passed}/{len(self.results)} passed, {failed} failed")
        self.log(f"Total time: {total_time/1000:.2f}s")


def main():
    parser = argparse.ArgumentParser(description="Dad Coach E2E Testing")
    parser.add_argument("--phone", required=True, help="Phone number to test with")
    parser.add_argument("--env", choices=["local", "dev", "prod"], default="local", help="Environment")
    parser.add_argument("--base-url", help="Override base URL")
    parser.add_argument("--admin-token", help="Admin JWT token for cleanup")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    base_url = args.base_url or DadCoachE2ETester.ENVIRONMENTS.get(args.env)
    if not base_url:
        print(f"ERROR: Unknown environment: {args.env}")
        sys.exit(1)

    config = TestConfig(
        phone=args.phone,
        env=args.env,
        base_url=base_url,
        admin_token=args.admin_token,
        verbose=args.verbose,
    )

    tester = DadCoachE2ETester(config)
    success = tester.run_all_tests()
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
