"""
Integration tests for EventMesh AgentMesh Hermes SDK.

Tests the full A2A client lifecycle against a mock gateway.
Covers: registration, heartbeat, task sync/async/cancel/stream,
agent discovery, health check, and error handling.

Run:
    cd eventmesh-agentmesh-adapters/eventmesh-agentmesh-adapter-hermes
    python3 tests/test_hermes_client.py
"""

import json
import os
import sys
import time
import unittest

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from tests.mock_gateway import MockGateway
from eventmesh_agentmesh import (
    AgentMeshClient,
    AgentRegistration,
    AgentCard,
    AgentSkill,
    TaskResult,
    TaskState,
)


class TestAgentMeshClientLifecycle(unittest.TestCase):
    """Full lifecycle test: connect → register → heartbeat → tasks → stop."""

    @classmethod
    def setUpClass(cls):
        cls.gateway = MockGateway().start()
        print(f"\n  Mock Gateway running at {cls.gateway.url}")

    @classmethod
    def tearDownClass(cls):
        cls.gateway.stop()
        print("  Mock Gateway stopped")

    def setUp(self):
        """Create a fresh client for each test."""
        self.client = AgentMeshClient(
            gateway_url=self.gateway.url,
            agent_name="default/default/hermes-assistant",
            agent_card={
                "name": "hermes-assistant",
                "description": "Hermes AI Assistant for testing",
                "version": "1.0.0",
                "skills": [
                    {"id": "code-review", "name": "Code Review", "description": "Review code"},
                    {"id": "security-audit", "name": "Security Audit", "description": "Audit security"},
                    {"id": "general-chat", "name": "General Chat", "description": "AI chat"},
                ],
                "documentation_url": "https://example.com/hermes",
            },
            heartbeat_interval=5,
        )

    # =========================================================================
    # Health Check
    # =========================================================================

    def test_01_health_check(self):
        """Gateway health check returns healthy status."""
        health = self.client.health_check()
        self.assertEqual(health["status"], "healthy")
        self.assertIn("version", health)

    def test_01b_health_check_before_start(self):
        """Health check works even without agent registration."""
        health = self.client.health_check()
        self.assertEqual(health["status"], "healthy")

    # =========================================================================
    # Registration & Heartbeat
    # =========================================================================

    def test_02_registration(self):
        """Agent start registers card on gateway."""
        self.client.start()
        self.assertTrue(self.client._started)

        # Verify by listing agents
        agents = self.client.list_agents()
        agent_names = [a["name"] for a in agents]
        self.assertIn("default/default/hermes-assistant", agent_names)

    def test_03_heartbeat(self):
        """Heartbeat is sent periodically after start."""
        self.client.start()

        # Wait for at least one heartbeat (interval=5s, but gateway records first)
        time.sleep(1)

        agents = self.client.list_agents()
        self.assertGreaterEqual(len(agents), 1)

        agent = next(
            (a for a in agents if a["name"] == "default/default/hermes-assistant"),
            None,
        )
        self.assertIsNotNone(agent)
        self.assertEqual(agent["status"], "online")
        # lastHeartbeat should be recent (within last 10 seconds)
        self.assertGreater(agent["lastHeartbeat"], time.time() - 10)

    def test_04_get_agent_card(self):
        """Retrieve own agent card from gateway."""
        self.client.start()

        card = self.client.get_agent_card(
            org_id="default", unit_id="default", agent_id="hermes-assistant"
        )
        self.assertEqual(card["name"], "hermes-assistant")
        self.assertEqual(card["version"], "1.0.0")
        self.assertEqual(len(card["skills"]), 3)
        skill_ids = [s["id"] for s in card["skills"]]
        self.assertIn("code-review", skill_ids)
        self.assertIn("security-audit", skill_ids)
        self.assertIn("general-chat", skill_ids)

    # =========================================================================
    # Task: Synchronous
    # =========================================================================

    def test_05_sync_task(self):
        """Submit a synchronous task and get result."""
        self.client.start()

        result = self.client.send_task("weather-agent", "What's the weather in Shenzhen?")
        self.assertIsInstance(result, TaskResult)
        self.assertTrue(result.task_id)
        self.assertEqual(result.state, TaskState.COMPLETED)
        self.assertIn("weather-agent", str(result.data))
        self.assertIn("Shenzhen", str(result.data))

    def test_06_multiple_sync_tasks(self):
        """Submit multiple tasks sequentially."""
        self.client.start()

        queries = ["Shenzhen", "Beijing", "Shanghai"]
        for q in queries:
            result = self.client.send_task("weather-agent", q)
            self.assertEqual(result.state, TaskState.COMPLETED)
            self.assertIn(q, str(result.data))

    # =========================================================================
    # Task: Asynchronous
    # =========================================================================

    def test_07_async_task_polling(self):
        """Submit async task and poll for result."""
        self.client.start()

        task_id = self.client.send_task_async("weather-agent", "Beijing")
        self.assertTrue(task_id)
        self.assertIsInstance(task_id, str)

        # Poll until complete
        for _ in range(20):
            status = self.client.get_task_status(task_id)
            if status.is_terminal():
                break
            time.sleep(0.2)

        self.assertTrue(status.is_terminal())
        self.assertEqual(status.state, TaskState.COMPLETED)
        self.assertIn("Beijing", str(status.data))

    def test_08_async_task_with_parent(self):
        """Submit async task with parent task ID."""
        self.client.start()

        parent_id = "parent-task-001"
        task_id = self.client.send_task_async(
            "weather-agent", "Guangzhou", parent_task_id=parent_id
        )
        self.assertTrue(task_id)

        # Poll
        for _ in range(20):
            status = self.client.get_task_status(task_id)
            if status.is_terminal():
                break
            time.sleep(0.2)

        self.assertEqual(status.state, TaskState.COMPLETED)

    # =========================================================================
    # Task: Cancel
    # =========================================================================

    def test_09_cancel_task(self):
        """Cancel a pending task."""
        self.client.start()

        task_id = self.client.send_task_async("weather-agent", "Should be cancelled")
        self.assertTrue(task_id)

        cancelled = self.client.cancel_task(task_id)
        self.assertTrue(cancelled)

        # Verify state is CANCELLED
        status = self.client.get_task_status(task_id)
        self.assertEqual(status.state, TaskState.CANCELLED)

    # =========================================================================
    # Task: SSE Stream
    # =========================================================================

    def test_10_sse_stream(self):
        """Stream task status via SSE."""
        self.client.start()

        task_id = self.client.send_task_async("weather-agent", "Stream this please")

        events = []

        def on_event(tid: str, state: str, data) -> bool:
            events.append((tid, state, data))
            # Stop when terminal
            return state not in (TaskState.COMPLETED, TaskState.FAILED, TaskState.CANCELLED)

        self.client.stream_task(task_id, on_event, timeout=10)

        self.assertGreaterEqual(len(events), 1)
        # Last event should be COMPLETED
        final_state = events[-1][1]
        self.assertEqual(final_state, TaskState.COMPLETED)

    # =========================================================================
    # Agent Discovery
    # =========================================================================

    def test_11_list_agents_multiple(self):
        """Register multiple agents and list them."""
        # Register hermes-assistant
        self.client.start()

        # Register a second agent via direct POST
        client2 = AgentMeshClient(
            gateway_url=self.gateway.url,
            agent_name="default/default/weather-agent",
            agent_card={
                "name": "weather-agent",
                "description": "Weather service",
                "version": "1.0.0",
                "skills": [
                    {"id": "weather", "name": "Weather Lookup", "description": "Get weather"}
                ],
            },
        )
        client2.start()

        agents = self.client.list_agents()
        self.assertGreaterEqual(len(agents), 2)

        names = [a["name"] for a in agents]
        self.assertIn("default/default/hermes-assistant", names)
        self.assertIn("default/default/weather-agent", names)

        client2.stop()

    # =========================================================================
    # Stop & Lifecycle
    # =========================================================================

    def test_12_stop(self):
        """Client stop cleanly shuts down."""
        self.client.start()
        self.assertTrue(self.client._started)

        self.client.stop()
        self.assertFalse(self.client._started)

    def test_13_double_start(self):
        """Starting twice is idempotent."""
        self.client.start()
        self.assertTrue(self.client._started)

        # Second start should be a no-op
        self.client.start()
        self.assertTrue(self.client._started)

    # =========================================================================
    # Error Handling
    # =========================================================================

    def test_14_connection_refused(self):
        """Connection refused raises AgentMeshError."""
        bad_client = AgentMeshClient(
            gateway_url="http://127.0.0.1:19999",  # unlikely to be open
            agent_name="test-agent",
        )
        from eventmesh_agentmesh.client import AgentMeshError

        with self.assertRaises(AgentMeshError):
            bad_client.health_check()

    def test_15_task_not_found(self):
        """Querying a non-existent task returns empty/default result."""
        self.client.start()

        status = self.client.get_task_status("nonexistent-task-id")
        self.assertEqual(status.task_id, "")
        self.assertEqual(status.state, "UNKNOWN")

    def test_16_cancel_nonexistent_task(self):
        """Cancelling a non-existent task returns False on 404."""
        self.client.start()

        cancelled = self.client.cancel_task("nonexistent-task-id")
        # Gateway returns 404; client returns False
        self.assertFalse(cancelled)

    # =========================================================================
    # Data Model Tests
    # =========================================================================

    def test_17_task_result_terminal_states(self):
        """TaskResult.is_terminal() works for all states."""
        self.assertTrue(TaskResult("t1", TaskState.COMPLETED).is_terminal())
        self.assertTrue(TaskResult("t2", TaskState.FAILED).is_terminal())
        self.assertTrue(TaskResult("t3", TaskState.CANCELLED).is_terminal())
        self.assertFalse(TaskResult("t4", TaskState.SUBMITTED).is_terminal())
        self.assertFalse(TaskResult("t5", TaskState.WORKING).is_terminal())

    def test_18_agent_registration_parsing(self):
        """AgentRegistration.from_name() parses various formats."""
        r = AgentRegistration.from_name("org/unit/agent")
        self.assertEqual(r.org_id, "org")
        self.assertEqual(r.unit_id, "unit")
        self.assertEqual(r.agent_id, "agent")

        r = AgentRegistration.from_name("simple-agent")
        self.assertEqual(r.org_id, "default")
        self.assertEqual(r.unit_id, "default")
        self.assertEqual(r.agent_id, "simple-agent")

        r = AgentRegistration.from_name("org/agent")
        self.assertEqual(r.org_id, "org")
        self.assertEqual(r.unit_id, "agent")
        self.assertEqual(r.agent_id, "org/agent")

    def test_19_agent_card_serialization(self):
        """AgentCard to_dict() produces valid JSON structure."""
        import json

        skill = AgentSkill(id="test-skill", name="Test", tags=["testing"])
        card = AgentCard(
            name="test-agent",
            description="A test agent",
            version="2.0.0",
            skills=[skill],
            documentation_url="https://example.com",
            icon_url="https://example.com/icon.png",
        )
        d = card.to_dict()
        self.assertEqual(d["name"], "test-agent")
        self.assertEqual(d["version"], "2.0.0")
        self.assertEqual(len(d["skills"]), 1)
        self.assertEqual(d["skills"][0]["id"], "test-skill")
        self.assertEqual(d["skills"][0]["tags"], ["testing"])
        self.assertEqual(d["documentationUrl"], "https://example.com")
        self.assertEqual(d["iconUrl"], "https://example.com/icon.png")
        # Must be valid JSON
        json.dumps(d)

    def test_20_agent_card_from_dict(self):
        """AgentCard.from_simple_dict() creates from user-friendly dict."""
        d = {
            "name": "test-agent",
            "description": "A test",
            "version": "3.0.0",
            "skills": [
                {"id": "skill-a", "name": "Skill A", "tags": ["tag1"]},
            ],
        }
        card = AgentCard.from_simple_dict(d)
        self.assertEqual(card.name, "test-agent")
        self.assertEqual(card.version, "3.0.0")
        self.assertEqual(len(card.skills), 1)
        self.assertEqual(card.skills[0].tags, ["tag1"])

    def test_21_client_repr(self):
        """Client repr shows agent name and gateway."""
        r = repr(self.client)
        self.assertIn("hermes-assistant", r)
        self.assertIn(self.gateway.url, r)


if __name__ == "__main__":
    # Run tests with verbose output
    suite = unittest.TestLoader().loadTestsFromTestCase(TestAgentMeshClientLifecycle)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    # Summary
    print(f"\n{'='*60}")
    print(f"Results: {result.testsRun} tests run")
    print(f"  Passed: {result.testsRun - len(result.failures) - len(result.errors)}")
    print(f"  Failed: {len(result.failures)}")
    print(f"  Errors: {len(result.errors)}")
    print(f"{'='*60}")

    sys.exit(0 if result.wasSuccessful() else 1)
