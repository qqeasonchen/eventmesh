"""
Integration tests for Hermes AgentMesh Adapter.

Tests the full Hermes agent integration against a mock A2A Gateway,
including card creation, skill routing, lifecycle management, and error handling.

Run:
    cd eventmesh-agentmesh-adapters/eventmesh-agentmesh-adapter-hermes
    PYTHONPATH=../../eventmesh-agent-sdks/python python3 tests/test_hermes_adapter.py
"""

import json
import os
import sys
import threading
import time
import unittest

# Point to shared SDK
_SDK_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
    "eventmesh-agent-sdks", "python",
)
sys.path.insert(0, _SDK_PATH)
# Also add SDK tests dir for mock_gateway
_TEST_SDK_PATH = os.path.join(_SDK_PATH, "tests")
sys.path.insert(0, _TEST_SDK_PATH)

from mock_gateway import MockGateway

# Import adapter's handler logic directly
_ADAPTER_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _ADAPTER_DIR)
from examples.hermes_agent import create_hermes_agent_card, handle_incoming_task

from eventmesh_agent import AgentMeshClient, TaskState


class TestHermesAgentCard(unittest.TestCase):
    """Tests for the Hermes AgentCard creation."""

    def test_01_card_structure(self):
        """Card has required top-level fields."""
        card = create_hermes_agent_card()
        self.assertEqual(card["name"], "hermes-assistant")
        self.assertIn("description", card)
        self.assertEqual(card["version"], "1.0.0")
        self.assertIn("skills", card)
        self.assertIn("documentation_url", card)

    def test_02_card_skills_count(self):
        """Card defines exactly 4 skills."""
        card = create_hermes_agent_card()
        self.assertEqual(len(card["skills"]), 4)

    def test_03_card_skill_ids(self):
        """All expected skill IDs are present."""
        card = create_hermes_agent_card()
        skill_ids = {s["id"] for s in card["skills"]}
        self.assertSetEqual(
            skill_ids,
            {"code-review", "security-audit", "infrastructure-ops", "general-chat"},
        )

    def test_04_card_skill_has_input_output_modes(self):
        """Each skill defines inputModes and outputModes."""
        card = create_hermes_agent_card()
        for skill in card["skills"]:
            self.assertIn("inputModes", skill)
            self.assertIn("outputModes", skill)
            self.assertIsInstance(skill["inputModes"], list)
            self.assertIsInstance(skill["outputModes"], list)

    def test_05_card_default_modes(self):
        """Card has defaultInputModes and defaultOutputModes."""
        card = create_hermes_agent_card()
        self.assertIn("defaultInputModes", card)
        self.assertIn("defaultOutputModes", card)


class TestHermesSkillRouting(unittest.TestCase):
    """Tests for the incoming task handler's skill routing."""

    def test_06_route_code_review(self):
        msg = json.dumps({"skill": "code-review", "text": "Review commit abc123"})
        result = handle_incoming_task("task-001", msg)
        self.assertIn("Code review complete", result)

    def test_07_route_security_audit(self):
        msg = json.dumps({"skill": "security-audit", "text": "Audit MCP config"})
        result = handle_incoming_task("task-002", msg)
        self.assertIn("Security audit passed", result)

    def test_08_route_infrastructure_ops(self):
        msg = json.dumps({"skill": "infrastructure-ops", "text": "Deploy to staging"})
        result = handle_incoming_task("task-003", msg)
        self.assertIn("Infrastructure operation executed", result)

    def test_09_route_unknown_skill_falls_back_to_chat(self):
        msg = json.dumps({"skill": "unknown-skill", "text": "Hello?"})
        result = handle_incoming_task("task-004", msg)
        self.assertIn("Chat response to", result)

    def test_10_route_plain_text_defaults_to_chat(self):
        """Non-JSON messages are routed to general-chat."""
        result = handle_incoming_task("task-005", "Hello world")
        self.assertIn("Chat response to", result)
        self.assertIn("Hello world", result)


class TestHermesLifecycleAgainstGateway(unittest.TestCase):
    """Full 4-phase lifecycle test against a live mock gateway."""

    @classmethod
    def setUpClass(cls):
        cls.gateway = MockGateway().start()
        cls.gateway_url = cls.gateway.url

    @classmethod
    def tearDownClass(cls):
        cls.gateway.stop()

    def setUp(self):
        self.client = AgentMeshClient(
            gateway_url=self.gateway_url,
            agent_name="default/default/hermes-assistant",
            agent_card=create_hermes_agent_card(),
            heartbeat_interval=5,
        )
        self.client.set_request_handler(handle_incoming_task)

    def tearDown(self):
        try:
            self.client.stop()
        except Exception:
            pass

    # --- Phase 1: Connect ---

    def test_11_start_and_register(self):
        """Agent starts and registers successfully."""
        self.client.start()
        self.assertTrue(self.client.is_started())

    def test_12_heartbeat_runs(self):
        """Heartbeat thread starts and server receives it."""
        self.client.start()
        time.sleep(0.5)
        # Verify agent is registered in gateway
        agents = self.client.list_agents()
        names = [a.get("name", "") for a in agents]
        self.assertIn("default/default/hermes-assistant", names)

    def test_13_start_twice_is_idempotent(self):
        """Calling start() twice does not crash."""
        self.client.start()
        self.client.start()  # should be safe
        self.assertTrue(self.client.is_started())

    # --- Phase 2: Discovery ---

    def test_14_health_check(self):
        """Health check returns gateway status."""
        self.client.start()
        health = self.client.health_check()
        self.assertIsNotNone(health)
        self.assertIn("status", health)

    def test_15_list_agents(self):
        """List agents returns all registered agents."""
        self.client.start()
        agents = self.client.list_agents()
        self.assertIsInstance(agents, list)
        self.assertGreaterEqual(len(agents), 1)

    def test_16_get_agent_card(self):
        """Get own agent card from gateway."""
        self.client.start()
        card = self.client.get_agent_card(
            org_id="default", unit_id="default", agent_id="hermes-assistant"
        )
        self.assertIsNotNone(card)
        self.assertEqual(card.get("name"), "hermes-assistant")

    # --- Phase 3: Task Patterns ---

    def test_17_sync_task_returns_result(self):
        """Synchronous task completes and returns data."""
        self.client.start()
        result = self.client.send_task(
            "weather-agent",
            json.dumps({"skill": "weather", "text": "Shenzhen weather?"}),
            timeout=10,
        )
        self.assertIn(result.state, ("COMPLETED", "WORKING"))
        if result.state == "COMPLETED":
            self.assertIsNotNone(result.data)

    def test_18_async_task_with_wait(self):
        """Async task can be polled to completion."""
        self.client.start()
        task_id = self.client.send_task_async("analyst", "Analyze XYZ stock")
        self.assertTrue(task_id)
        result = self.client.wait_for_task(task_id, poll_interval=1, max_wait=15)
        self.assertEqual(result.state, "COMPLETED")

    def test_19_async_task_with_parent(self):
        """Child task tracks parent task ID."""
        self.client.start()
        parent_id = self.client.send_task_async("orchestrator", "Parent task")
        child_id = self.client.send_task_async(
            "worker", "Child task", parent_task_id=parent_id
        )
        self.assertTrue(child_id)
        parent_result = self.client.wait_for_task(parent_id, poll_interval=1, max_wait=15)
        self.assertEqual(parent_result.state, "COMPLETED")

    def test_20_cancel_task(self):
        """Cancel a pending async task."""
        self.client.start()
        task_id = self.client.send_task_async("sleeper", "Sleep for 100s")
        time.sleep(0.2)
        cancelled = self.client.cancel_task(task_id)
        self.assertTrue(cancelled)
        status = self.client.get_task_status(task_id)
        self.assertEqual(status.state, "CANCELLED")

    def test_21_sse_stream_tracks_states(self):
        """SSE streaming reports SUBMITTED -> WORKING -> COMPLETED."""
        self.client.start()
        task_id = self.client.send_task_async("analyst", "Market trend analysis")

        events = []
        def on_event(tid, state, data):
            events.append((state, data is not None))
            return state not in ("COMPLETED", "FAILED", "CANCELLED")

        self.client.stream_task(task_id, on_event, timeout=15)
        states = [s for s, _ in events]
        self.assertIn("SUBMITTED", states)
        self.assertIn("WORKING", states)
        self.assertIn("COMPLETED", states)
        # Only COMPLETED has data
        for state, has_data in events:
            if state == "COMPLETED":
                self.assertTrue(has_data, f"COMPLETED should have data, got events={events}")

    # --- Phase 4: Error Handling ---

    def test_22_connection_refused_raises(self):
        """Connecting to a non-existent port raises exception."""
        bad_client = AgentMeshClient(
            gateway_url="http://127.0.0.1:19999",
            agent_name="default/default/test",
            agent_card={"name": "test", "skills": []},
            heartbeat_interval=5,
        )
        with self.assertRaises(Exception):
            bad_client.start()
        self.assertFalse(bad_client.is_started())

    def test_23_nonexistent_agent_card(self):
        """Getting a card for non-existent agent returns None (handles 404)."""
        self.client.start()
        result = self.client.get_agent_card(
            org_id="nonexistent", unit_id="nonexistent", agent_id="nonexistent"
        )
        self.assertIsNone(result)

    def test_24_nonexistent_task_status_returns_empty(self):
        """Getting status for a non-existent task returns empty TaskResult."""
        self.client.start()
        result = self.client.get_task_status("nonexistent-task-id")
        self.assertIsNotNone(result)
        # Should be an empty/error state
        self.assertTrue(result.state in ("", TaskState.COMPLETED) or not result.data)

    def test_25_stop_cleans_up(self):
        """Stopping the client marks it as not started."""
        self.client.start()
        self.assertTrue(self.client.is_started())
        self.client.stop()
        self.assertFalse(self.client.is_started())

    def test_26_multi_skill_sequential_tasks(self):
        """Multiple tasks with different skills complete in sequence."""
        self.client.start()
        for i, skill in enumerate(["code-review", "security-audit", "general-chat"]):
            task_id = self.client.send_task_async(
                "test-agent", json.dumps({"skill": skill, "text": f"Test {i}"})
            )
            result = self.client.wait_for_task(task_id, poll_interval=1, max_wait=15)
            self.assertEqual(result.state, "COMPLETED", f"Skill {skill} did not complete")


if __name__ == "__main__":
    unittest.main(verbosity=2)
