"""Static checks of Dad Coach's workflow manifest against the platform's rules (playbook §13-§14) and the
tool catalog — the mistakes that otherwise surface only after provisioning.

python3 -m unittest test_manifests   (from provisioning/scripts; needs PyYAML)
"""
import json
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "config"
CATALOG = json.loads((ROOT / "catalog" / "dad-coach-catalog.json").read_text(encoding="utf-8"))
PLATFORM_TOOLS = {"schedule_state_transition", "update_scheduled_transition", "cancel_scheduled_transition"}
DEAD_FIELDS = {"transitionGuidance", "contextGuidance"}
TRIGGERS = {"TOOL_RESPONSE", "CONTEXT_CONDITION", "USER_INTENT", "CUSTOM", "SCHEDULED", "AFTER_SCHEDULED_TURN"}


def load(name):
    return yaml.safe_load((CONFIG / name).read_text(encoding="utf-8"))


RESOURCES = json.loads((CONFIG / "platform-resources.json").read_text(encoding="utf-8"))
MANIFESTS = {name: load(name) for name in RESOURCES["workflows"]}
TOOL_KEYS = {t["key"] for t in CATALOG["tools"]}
PROVIDER_KEYS = {p["key"] for p in CATALOG["providers"]}


def one_shot_states(doc):
    return {s["stateKey"] for s in doc["states"]
            if any(t.get("triggerType") == "AFTER_SCHEDULED_TURN" for t in s.get("transitions", []))}


class ManifestTest(unittest.TestCase):

    def test_every_tool_and_provider_exists_in_the_catalog(self):
        for name, doc in MANIFESTS.items():
            for state in doc["states"]:
                for tool in state.get("tools", []):
                    self.assertIn(tool["toolKey"], TOOL_KEYS | PLATFORM_TOOLS, f"{name}:{state['stateKey']}")
                for provider in state.get("contextProviders", []):
                    self.assertIn(provider["providerKey"], PROVIDER_KEYS, f"{name}:{state['stateKey']}")

    def test_every_catalog_tool_is_bound_somewhere(self):
        bound = {t["toolKey"] for doc in MANIFESTS.values() for s in doc["states"] for t in s.get("tools", [])}
        self.assertEqual(TOOL_KEYS - bound, set(), "a cataloged tool no state can use")

    def test_no_field_the_model_never_sees(self):
        for name, doc in MANIFESTS.items():
            for state in doc["states"]:
                self.assertFalse(DEAD_FIELDS & set(state), f"{name}:{state['stateKey']} uses a dead field")
                for t in state.get("transitions", []):
                    self.assertFalse(DEAD_FIELDS & set(t), f"{name}:{t['transitionKey']}")

    def test_states_and_transitions_are_well_formed(self):
        for name, doc in MANIFESTS.items():
            keys = [s["stateKey"] for s in doc["states"]]
            self.assertEqual(len(keys), len(set(keys)), name)
            self.assertIn(doc["workflow"]["initialStateKey"], keys, name)
            transition_keys = [t["transitionKey"] for s in doc["states"] for t in s.get("transitions", [])]
            self.assertEqual(len(transition_keys), len(set(transition_keys)), name)
            for state in doc["states"]:
                self.assertTrue(state.get("stateObjective"), f"{name}:{state['stateKey']} needs an objective")
                for t in state.get("transitions", []):
                    self.assertIn(t["targetStateKey"], keys, f"{name}:{t['transitionKey']}")
                    self.assertIn(t["triggerType"], TRIGGERS, f"{name}:{t['transitionKey']}")
                    if t["triggerType"] in ("USER_INTENT", "CONTEXT_CONDITION"):
                        self.assertTrue(t.get("triggerCondition"), f"{name}:{t['transitionKey']} needs a condition")
                    if t["triggerType"] == "TOOL_RESPONSE":
                        self.assertTrue(t.get("triggerToolKey"), f"{name}:{t['transitionKey']}")

    def test_one_shot_states_follow_the_platform_rules(self):
        """ScheduledOneShotStateRules: one exit, never the initial state, no interactive way in, no self or one-shot exit."""
        for name, doc in MANIFESTS.items():
            shots = one_shot_states(doc)
            self.assertNotIn(doc["workflow"]["initialStateKey"], shots, name)
            for state in doc["states"]:
                for t in state.get("transitions", []):
                    if t["targetStateKey"] in shots:
                        self.assertEqual(t["triggerType"], "SCHEDULED", f"{name}:{t['transitionKey']} enters a one-shot state")
                        rule = t.get("schedulingRule", {})
                        self.assertTrue(rule.get("fireFromAnyState"), f"{name}:{t['transitionKey']} must fire from any state")
                if state["stateKey"] in shots:
                    exits = state.get("transitions", [])
                    self.assertEqual(len(exits), 1, f"{name}:{state['stateKey']}")
                    self.assertNotEqual(exits[0]["targetStateKey"], state["stateKey"])
                    self.assertNotIn(exits[0]["targetStateKey"], shots)

    def test_schedules_use_the_instance_timezone_and_exist(self):
        """The daily check targets the hub (not a one-shot state): its first item may send the father back to
        onboarding, which a one-shot state (one exit) could not. Decision DC-D012."""
        by_key = {doc["workflow"]["key"]: doc for doc in MANIFESTS.values()}
        for schedule in RESOURCES["schedules"]:
            doc = by_key[schedule["workflowKey"]]
            self.assertIn(schedule["targetStateKey"], {s["stateKey"] for s in doc["states"]}, schedule["name"])
            self.assertEqual(schedule["timezoneSource"], "INSTANCE", "user-facing schedules use the instance timezone")

    def test_the_worker_owns_exactly_the_manifested_workflows(self):
        keys = {doc["workflow"]["key"] for doc in MANIFESTS.values()}
        [worker] = RESOURCES["workers"]
        self.assertEqual(worker["workerKey"], "dad_3")
        self.assertEqual(set(worker["workflowKeys"]), keys)
        self.assertIn(worker["defaultWorkflowKey"], keys)

    def test_the_session_timers_match_the_product(self):
        """SessionTimerPlanner returns exactly these transition keys - renaming one silently drops a reminder."""
        [doc] = MANIFESTS.values()
        scheduled = {t["transitionKey"]: t["targetStateKey"] for s in doc["states"] for t in s.get("transitions", [])
                     if t["triggerType"] == "SCHEDULED"}
        self.assertEqual(scheduled, {"session_morning_reminder": "SESSION_MORNING_REMINDER",
                                     "session_reminder_1h": "SESSION_REMINDER_1H",
                                     "session_follow_up": "SESSION_FOLLOW_UP"})

    def test_behavior_follows_the_standard(self):
        """Playbook §17.1: every state has keyed REQUIRED/PROHIBITED rules + advanced guidance, no legacy text."""
        for name, doc in MANIFESTS.items():
            for state in doc["states"]:
                where = f"{name}:{state['stateKey']}"
                self.assertFalse(state.get("behaviorGuidance"), f"{where} legacy behaviorGuidance must stay empty")
                self.assertTrue(state.get("advancedBehaviorGuidance"), f"{where} needs advancedBehaviorGuidance")
                rules = state.get("behaviorRules") or []
                self.assertTrue(rules, f"{where} needs behaviorRules")
                keys = [r.get("ruleKey") for r in rules]
                self.assertEqual(len(keys), len(set(keys)), f"{where} duplicate ruleKey")
                for r in rules:
                    self.assertRegex(r.get("ruleKey") or "", r"^[a-z0-9]+(-[a-z0-9]+)*$", f"{where} ruleKey")
                    self.assertIn(r["ruleStrength"], {"REQUIRED", "PROHIBITED"}, f"{where}:{r['ruleKey']}")
                    self.assertEqual(r["actionType"], "GUIDE_AI", f"{where}:{r['ruleKey']}")
                    self.assertIn(r["conditionType"], {"ALWAYS", "AI_CONDITION"}, f"{where}:{r['ruleKey']}")
                    if r["conditionType"] == "AI_CONDITION":
                        self.assertTrue(r.get("conditionDescription"), f"{where}:{r['ruleKey']} needs a condition")
                    self.assertTrue(r.get("name"), f"{where}:{r['ruleKey']} needs a name")
                    if r["ruleStrength"] == "PROHIBITED":
                        # rendered as "DO NOT: <instruction>" - a negative instruction becomes a double negative
                        self.assertNotRegex(r["actionInstruction"].lower(), r"^(never|do not|don't|not)\b",
                                            f"{where}:{r['ruleKey']} PROHIBITED text must be a positive verb phrase")

    def test_no_routing_phrases_outside_trigger_conditions(self):
        """The platform's RoutingPatterns (CASE_INSENSITIVE) refuse these in rules and advanced guidance."""
        patterns = [
            r"\btransition\s+(to|into)\s+(the\s+)?([A-Z][A-Z0-9_]+)\b",
            r"\b(move|go|proceed)\s+(to|into)\s+(the\s+)?([A-Z][A-Z0-9_]+)\b",
            r"\bnext\s+state\s+(is|should\s+be)\s+\w+",
            r"\b(exit|leave)\s+(to|and\s+go\s+to)\s+([A-Z][A-Z0-9_]+)\b",
            r"\bswitch\s+to\s+(the\s+)?[A-Z][A-Z0-9_]+\s+state\b",
            r"\bchange\s+(state\s+to|to\s+state)\s+\w+",
            r"\benter\s+(the\s+)?[A-Z][A-Z0-9_]+\s+state\b",
            r"\b(transition|move|go|proceed)\s+(to|into)\s+(the\s+)?\w+\s+state\b",
        ]
        import re
        for name, doc in MANIFESTS.items():
            for state in doc["states"]:
                texts = [state.get("advancedBehaviorGuidance") or ""]
                for r in state.get("behaviorRules") or []:
                    texts += [r.get("conditionDescription") or "", r.get("actionInstruction") or ""]
                for text in texts:
                    for p in patterns:
                        m = re.search(p, text, re.IGNORECASE)
                        self.assertIsNone(m, f"{name}:{state['stateKey']} routing phrase: {m and m.group(0)!r}")
                        for word in ("change_state",):
                            self.assertNotIn(word, text, f"{name}:{state['stateKey']} routing belongs in triggerCondition")

    def test_global_prompt_stays_global(self):
        """§17.1 layering: identity, language, sources of truth, hard-line boundaries - no state procedures."""
        [doc] = MANIFESTS.values()
        prompt = doc["workflow"]["globalPrompt"]
        self.assertLess(len(prompt), 2600, "globalPrompt grew - move procedures into a state")
        for procedure in ("schedule_state_transition", "cancel_scheduled_transition", "show_available_slots"):
            self.assertNotIn(procedure, prompt)


if __name__ == "__main__":
    unittest.main()
