"""
Multi-Agent Communication Simulation Test.

Simulates a realistic multi-agent ecosystem on a Mock A2A Gateway:
  Agent Roles:
    - hermes-assistant  (code review, security audit, infra ops, chat)
    - weather-agent     (weather lookup, forecast)
    - analyst           (market analysis, trend research)
    - researcher        (deep research, data gathering)
    - reporter          (report generation, summarization)
    - orchestrator      (task decomposition, workflow coordination)

  Scenarios:
    1.  One-to-One:         Hermes -> Weather Agent (sync task)
    2.  Fan-Out/Broadcast:  Orchestrator -> 3 Workers simultaneously
    3.  Chain Delegation:   Orchestrator -> Analyst -> Researcher -> Reporter
    4.  Concurrent Comms:   5 agents submit tasks concurrently
    5.  Discovery & Route:  Agents discover peers and route by capability
    6.  Parent-Child Chain: Parent task tracks all child tasks
    7.  SSE Streaming:      Real-time progress observation between agents
    8.  Gateway Stats:      Multi-agent observability dashboard

Run:
    cd eventmesh-agent-sdks/python
    python3 tests/test_multi_agent.py
"""

import json
import os
import sys
import threading
import time
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from tests.mock_gateway import MockGateway
from eventmesh_agent import AgentMeshClient, TaskResult, TaskState


# =========================================================================
# Agent Card Definitions (simulating real multi-agent ecosystem)
# =========================================================================

AGENT_CARDS = {
    "hermes-assistant": {
        "name": "hermes-assistant",
        "description": "Hermes AI — code review, security audit, infra ops",
        "version": "1.0.0",
        "skills": [
            {"id": "code-review", "name": "Code Review"},
            {"id": "security-audit", "name": "Security Audit"},
            {"id": "general-chat", "name": "General Chat"},
        ],
    },
    "weather-agent": {
        "name": "weather-agent",
        "description": "Weather information service",
        "version": "1.0.0",
        "skills": [
            {"id": "weather-lookup", "name": "Weather Lookup"},
            {"id": "forecast", "name": "Forecast"},
        ],
    },
    "analyst": {
        "name": "analyst",
        "description": "Market & trend analysis agent",
        "version": "1.0.0",
        "skills": [
            {"id": "market-analysis", "name": "Market Analysis"},
            {"id": "trend-research", "name": "Trend Research"},
        ],
    },
    "researcher": {
        "name": "researcher",
        "description": "Deep research & data gathering agent",
        "version": "1.0.0",
        "skills": [
            {"id": "data-gathering", "name": "Data Gathering"},
            {"id": "literature-review", "name": "Literature Review"},
        ],
    },
    "reporter": {
        "name": "reporter",
        "description": "Report generation & summarization agent",
        "version": "1.0.0",
        "skills": [
            {"id": "summarization", "name": "Summarization"},
            {"id": "report-gen", "name": "Report Generation"},
        ],
    },
    "orchestrator": {
        "name": "orchestrator",
        "description": "Workflow orchestration & task coordination",
        "version": "1.0.0",
        "skills": [
            {"id": "task-decomposition", "name": "Task Decomposition"},
            {"id": "workflow-coordination", "name": "Workflow Coordination"},
        ],
    },
}


class MultiAgentTestBase(unittest.TestCase):
    """Base class with shared mock gateway setup."""

    @classmethod
    def setUpClass(cls):
        cls.gateway = MockGateway().start()
        cls.gateway_url = cls.gateway.url
        print(f"\n  === Mock Gateway @ {cls.gateway_url} ===")

    @classmethod
    def tearDownClass(cls):
        cls.gateway.stop()
        print("  === Mock Gateway Stopped ===")

    def _create_client(self, agent_name):
        """Create and register a client for a specific agent role."""
        simple_name = agent_name.split("/")[-1]
        card = AGENT_CARDS.get(simple_name, {"name": simple_name, "version": "1.0.0", "skills": []})
        full_name = f"default/default/{simple_name}"
        return AgentMeshClient(
            gateway_url=self.gateway_url,
            agent_name=full_name,
            agent_card=card,
            heartbeat_interval=30,
        )


# =========================================================================
# Scenario 1: One-to-One Communication
# =========================================================================

class TestScenario01_OneToOne(MultiAgentTestBase):
    """Hermes sends a weather query to Weather Agent and gets a result."""

    def test_01a_sync_one_to_one(self):
        """Sync task: Hermes -> Weather Agent, gets result immediately."""
        hermes = self._create_client("hermes-assistant")
        weather = self._create_client("weather-agent")

        hermes.start()
        weather.start()

        result = hermes.send_task("default/default/weather-agent",
                                  "What's the weather in Shenzhen?",
                                  timeout=10)

        self.assertIsInstance(result, TaskResult)
        self.assertEqual(result.state, TaskState.COMPLETED)
        self.assertIn("weather-agent", str(result.data))
        self.assertIn("Shenzhen", str(result.data))

        hermes.stop()
        weather.stop()

    def test_01b_async_one_to_one(self):
        """Async task: Hermes submits, polls for result."""
        hermes = self._create_client("hermes-assistant")
        weather = self._create_client("weather-agent")

        hermes.start()
        weather.start()

        task_id = hermes.send_task_async("default/default/weather-agent",
                                         "Beijing forecast for tomorrow?")
        self.assertTrue(task_id)

        result = hermes.wait_for_task(task_id, poll_interval=0.5, max_wait=10)
        self.assertEqual(result.state, TaskState.COMPLETED)
        self.assertIn("weather-agent", str(result.data))

        hermes.stop()
        weather.stop()

    def test_01c_verify_routing(self):
        """Verify that task is routed to the correct target agent."""
        hermes = self._create_client("hermes-assistant")
        weather = self._create_client("weather-agent")

        hermes.start()
        weather.start()

        hermes.send_task("default/default/weather-agent", "Temp check", timeout=10)

        # Check that weather-agent has tasks routed to it
        import urllib.request
        url = f"{self.gateway_url}/a2a/agents/default/default/weather-agent/tasks"
        resp = json.loads(urllib.request.urlopen(url).read())
        self.assertGreaterEqual(len(resp), 1)
        self.assertIn("weather-agent", str(resp[0].get("targetAgent", "")))

        hermes.stop()
        weather.stop()


# =========================================================================
# Scenario 2: Fan-Out / Broadcast
# =========================================================================

class TestScenario02_FanOut(MultiAgentTestBase):
    """Orchestrator fans out a task to 3 different worker agents simultaneously."""

    def setUp(self):
        self.orchestrator = self._create_client("orchestrator")
        self.analyst = self._create_client("analyst")
        self.researcher = self._create_client("researcher")
        self.reporter = self._create_client("reporter")

        for agent in [self.orchestrator, self.analyst, self.researcher, self.reporter]:
            agent.start()

    def tearDown(self):
        for agent in [self.orchestrator, self.analyst, self.researcher, self.reporter]:
            try:
                agent.stop()
            except Exception:
                pass

    def test_02a_fan_out_async(self):
        """Orchestrator sends tasks to 3 workers in parallel (async)."""
        workers = [
            "default/default/analyst",
            "default/default/researcher",
            "default/default/reporter",
        ]
        messages = [
            "Analyze tech sector performance",
            "Research AI supply chain data",
            "Generate quarterly summary report",
        ]

        # Submit all tasks asynchronously
        task_ids = {}
        for worker, msg in zip(workers, messages):
            tid = self.orchestrator.send_task_async(worker, msg)
            task_ids[worker] = tid
            self.assertTrue(tid, f"Task ID for {worker} should not be empty")

        self.assertEqual(len(task_ids), 3)

        # Wait for all to complete
        results = {}
        for worker, tid in task_ids.items():
            result = self.orchestrator.wait_for_task(tid, poll_interval=0.5, max_wait=10)
            results[worker] = result
            self.assertEqual(result.state, TaskState.COMPLETED,
                             f"Worker {worker} should complete")

        # Each result references its target
        self.assertIn("analyst", str(results["default/default/analyst"].data))
        self.assertIn("researcher", str(results["default/default/researcher"].data))
        self.assertIn("reporter", str(results["default/default/reporter"].data))

    def test_02b_fan_out_threaded(self):
        """Fan-out tasks in separate threads, verify all complete."""
        tasks = [
            ("default/default/analyst", "Sector analysis"),
            ("default/default/researcher", "Deep dive research"),
            ("default/default/reporter", "Executive summary"),
        ]

        results = {}
        lock = threading.Lock()

        def submit_and_wait(target, msg):
            tid = self.orchestrator.send_task_async(target, msg)
            result = self.orchestrator.wait_for_task(tid, poll_interval=0.5, max_wait=10)
            with lock:
                results[target] = result

        threads = []
        for target, msg in tasks:
            t = threading.Thread(target=submit_and_wait, args=(target, msg))
            threads.append(t)
            t.start()

        for t in threads:
            t.join(timeout=15)

        self.assertEqual(len(results), 3)
        for r in results.values():
            self.assertEqual(r.state, TaskState.COMPLETED)


# =========================================================================
# Scenario 3: Chain Delegation
# =========================================================================

class TestScenario03_ChainDelegation(MultiAgentTestBase):
    """Orchestrator -> Analyst -> Researcher -> Reporter (sequential chain)."""

    def setUp(self):
        self.orchestrator = self._create_client("orchestrator")
        self.analyst = self._create_client("analyst")
        self.researcher = self._create_client("researcher")
        self.reporter = self._create_client("reporter")

        for agent in [self.orchestrator, self.analyst, self.researcher, self.reporter]:
            agent.start()

    def tearDown(self):
        for agent in [self.orchestrator, self.analyst, self.researcher, self.reporter]:
            try:
                agent.stop()
            except Exception:
                pass

    def test_03a_sequential_chain(self):
        """Chain: Orchestrator -> Analyst -> Researcher -> Reporter."""
        # Step 1: Orchestrator -> Analyst
        tid1 = self.orchestrator.send_task_async(
            "default/default/analyst",
            "Analyze NVIDIA stock"
        )
        r1 = self.orchestrator.wait_for_task(tid1, poll_interval=0.5, max_wait=10)
        self.assertEqual(r1.state, TaskState.COMPLETED)
        self.assertIn("analyst", str(r1.data))

        # Step 2: Orchestrator -> Researcher (with context from step 1)
        tid2 = self.orchestrator.send_task_async(
            "default/default/researcher",
            "Deep research based on analysis: " + str(r1.data)[:80]
        )
        r2 = self.orchestrator.wait_for_task(tid2, poll_interval=0.5, max_wait=10)
        self.assertEqual(r2.state, TaskState.COMPLETED)
        self.assertIn("researcher", str(r2.data))

        # Step 3: Orchestrator -> Reporter (compile final report)
        tid3 = self.orchestrator.send_task_async(
            "default/default/reporter",
            "Generate report from analysis and research"
        )
        r3 = self.orchestrator.wait_for_task(tid3, poll_interval=0.5, max_wait=10)
        self.assertEqual(r3.state, TaskState.COMPLETED)
        self.assertIn("reporter", str(r3.data))

    def test_03b_cross_agent_direct(self):
        """Analyst directly delegates to Researcher without Orchestrator."""
        # Analyst sends task directly to Researcher
        tid = self.analyst.send_task_async(
            "default/default/researcher",
            "Fetch data for TSLA analysis"
        )
        result = self.analyst.wait_for_task(tid, poll_interval=0.5, max_wait=10)
        self.assertEqual(result.state, TaskState.COMPLETED)
        self.assertIn("researcher", str(result.data))


# =========================================================================
# Scenario 4: Concurrent Communication
# =========================================================================

class TestScenario04_Concurrent(MultiAgentTestBase):
    """Multiple agents submit tasks concurrently, simulating busy mesh."""

    def test_04a_concurrent_submissions(self):
        """5 agents submit tasks to each other concurrently."""
        agents = [
            self._create_client(name)
            for name in ["hermes-assistant", "weather-agent",
                         "analyst", "researcher", "reporter"]
        ]
        for a in agents:
            a.start()

        targets = [f"default/default/{a._registration.agent_id}" for a in agents]
        all_tids = []
        lock = threading.Lock()

        def submit_burst(caller_idx):
            caller = agents[caller_idx]
            for target_idx in range(len(agents)):
                if target_idx == caller_idx:
                    continue  # skip self
                tid = caller.send_task_async(
                    targets[target_idx],
                    f"Message from agent-{caller_idx} to agent-{target_idx}"
                )
                with lock:
                    all_tids.append((caller_idx, target_idx, tid))

        threads = []
        for i in range(len(agents)):
            t = threading.Thread(target=submit_burst, args=(i,))
            threads.append(t)
            t.start()

        for t in threads:
            t.join(timeout=30)

        # Should have 5 * 4 = 20 tasks (each agent sends to 4 others)
        self.assertEqual(len(all_tids), 20,
                         f"Expected 20 tasks (5 agents * 4), got {len(all_tids)}")

        # Wait for all to complete
        for _, _, tid in all_tids:
            result = agents[0].wait_for_task(tid, poll_interval=0.3, max_wait=15)
            self.assertEqual(result.state, TaskState.COMPLETED,
                             f"Task {tid} should complete")

        for a in agents:
            a.stop()


# =========================================================================
# Scenario 5: Discovery & Capability-Based Routing
# =========================================================================

class TestScenario05_DiscoveryAndRoute(MultiAgentTestBase):
    """Agents discover peers and route tasks based on capabilities."""

    def test_05a_discovery(self):
        """Register 4 agents, then discover them via list_agents."""
        hermes = self._create_client("hermes-assistant")
        weather = self._create_client("weather-agent")
        analyst = self._create_client("analyst")
        researcher = self._create_client("researcher")

        all_agents = [hermes, weather, analyst, researcher]
        for a in all_agents:
            a.start()

        # Any agent can list all
        agent_list = hermes.list_agents()
        self.assertEqual(len(agent_list), 4)

        names = {a["name"] for a in agent_list}
        expected = {
            "default/default/hermes-assistant",
            "default/default/weather-agent",
            "default/default/analyst",
            "default/default/researcher",
        }
        self.assertSetEqual(names, expected)

        for a in all_agents:
            a.stop()

    def test_05b_capability_routing(self):
        """Orchestrator discovers agents, then routes by skill."""
        orchestrator = self._create_client("orchestrator")
        weather = self._create_client("weather-agent")
        analyst = self._create_client("analyst")
        reporter = self._create_client("reporter")

        for a in [orchestrator, weather, analyst, reporter]:
            a.start()

        # Step 1: Discover available agents
        peers = orchestrator.list_agents()
        self.assertGreaterEqual(len(peers), 3)

        # Step 2: Route based on agent name (simulating capability match)
        # In production, this would inspect agent cards for skill matching
        weather_name = "default/default/weather-agent"
        analyst_name = "default/default/analyst"
        reporter_name = "default/default/reporter"

        # Weather query -> weather agent
        r1 = orchestrator.send_task(weather_name, "Weather in Tokyo?", timeout=10)
        self.assertEqual(r1.state, TaskState.COMPLETED)
        self.assertIn("weather-agent", str(r1.data))

        # Analysis -> analyst
        r2 = orchestrator.send_task(analyst_name, "Market trends for AI sector", timeout=10)
        self.assertEqual(r2.state, TaskState.COMPLETED)
        self.assertIn("analyst", str(r2.data))

        # Report -> reporter
        r3 = orchestrator.send_task(reporter_name, "Weekly summary", timeout=10)
        self.assertEqual(r3.state, TaskState.COMPLETED)
        self.assertIn("reporter", str(r3.data))

        for a in [orchestrator, weather, analyst, reporter]:
            a.stop()

    def test_05c_get_agent_card_for_routing(self):
        """Discover an agent and read its card to determine capabilities."""
        orchestrator = self._create_client("orchestrator")
        analyst = self._create_client("analyst")

        orchestrator.start()
        analyst.start()

        # Read analyst's card
        card = orchestrator.get_agent_card(
            org_id="default", unit_id="default", agent_id="analyst"
        )
        self.assertIsNotNone(card)
        self.assertEqual(card["name"], "analyst")
        self.assertEqual(len(card["skills"]), 2)
        skill_ids = {s["id"] for s in card["skills"]}
        self.assertIn("market-analysis", skill_ids)

        orchestrator.stop()
        analyst.stop()


# =========================================================================
# Scenario 6: Parent-Child Task Chain
# =========================================================================

class TestScenario06_ParentChildChain(MultiAgentTestBase):
    """Orchestrator creates parent task, spawns child tasks, tracks chain."""

    def test_06a_parent_child_tracking(self):
        """Parent task spawns 3 child tasks, chain is trackable."""
        orchestrator = self._create_client("orchestrator")
        analyst = self._create_client("analyst")
        researcher = self._create_client("researcher")
        reporter = self._create_client("reporter")

        for a in [orchestrator, analyst, researcher, reporter]:
            a.start()

        # Create parent task
        parent_tid = orchestrator.send_task_async(
            "default/default/analyst",
            "Parent: Full market analysis"
        )

        # Create child tasks linked to parent
        child1_tid = orchestrator.send_task_async(
            "default/default/researcher",
            "Child 1: Data gathering",
            parent_task_id=parent_tid
        )
        child2_tid = orchestrator.send_task_async(
            "default/default/reporter",
            "Child 2: Report formatting",
            parent_task_id=parent_tid
        )
        child3_tid = orchestrator.send_task_async(
            "default/default/analyst",
            "Child 3: Validation check",
            parent_task_id=parent_tid
        )

        # Wait for all tasks
        for tid in [parent_tid, child1_tid, child2_tid, child3_tid]:
            result = orchestrator.wait_for_task(tid, poll_interval=0.5, max_wait=10)
            self.assertEqual(result.state, TaskState.COMPLETED)

        # Verify chain via gateway
        import urllib.request
        url = f"{self.gateway_url}/a2a/tasks/{parent_tid}/chain"
        resp = json.loads(urllib.request.urlopen(url).read())

        self.assertEqual(resp["parentTaskId"], parent_tid)
        child_ids = [c["taskId"] for c in resp["children"]]
        self.assertEqual(len(child_ids), 3)

        for ctid in [child1_tid, child2_tid, child3_tid]:
            self.assertIn(ctid, child_ids)

        for a in [orchestrator, analyst, researcher, reporter]:
            a.stop()

    def test_06b_agent_task_history(self):
        """Verify each agent's task history via the agent-tasks endpoint."""
        orchestrator = self._create_client("orchestrator")
        analyst = self._create_client("analyst")
        researcher = self._create_client("researcher")

        orchestrator.start()
        analyst.start()
        researcher.start()

        # Send multiple tasks to analyst and researcher
        orchestrator.send_task_async("default/default/analyst", "Task A1")
        orchestrator.send_task_async("default/default/analyst", "Task A2")
        orchestrator.send_task_async("default/default/researcher", "Task R1")

        time.sleep(0.2)  # let tasks start processing

        import urllib.request

        # Check analyst's tasks
        url_a = f"{self.gateway_url}/a2a/agents/default/default/analyst/tasks"
        resp_a = json.loads(urllib.request.urlopen(url_a).read())
        self.assertGreaterEqual(len(resp_a), 2)

        # Check researcher's tasks
        url_r = f"{self.gateway_url}/a2a/agents/default/default/researcher/tasks"
        resp_r = json.loads(urllib.request.urlopen(url_r).read())
        self.assertGreaterEqual(len(resp_r), 1)

        for a in [orchestrator, analyst, researcher]:
            a.stop()


# =========================================================================
# Scenario 7: SSE Streaming Between Agents
# =========================================================================

class TestScenario07_SSEStreaming(MultiAgentTestBase):
    """Agent A observes Agent B's task progress in real-time via SSE."""

    def test_07a_sse_observe_peer(self):
        """Hermes watches weather-agent's task progress via SSE."""
        hermes = self._create_client("hermes-assistant")
        weather = self._create_client("weather-agent")

        hermes.start()
        weather.start()

        # Submit a task via hermes to weather
        task_id = hermes.send_task_async(
            "default/default/weather-agent",
            "Long weather analysis for Guangdong"
        )

        # Observe progress via SSE
        events = []
        def on_event(tid, state, data):
            events.append(state)
            return state not in (TaskState.COMPLETED, TaskState.FAILED, TaskState.CANCELLED)

        hermes.stream_task(task_id, on_event, timeout=15)

        # Should see all state transitions
        self.assertIn(TaskState.SUBMITTED, events)
        self.assertIn(TaskState.WORKING, events)
        self.assertIn(TaskState.COMPLETED, events)

        # States should appear in order
        idx_submitted = events.index(TaskState.SUBMITTED)
        idx_working = events.index(TaskState.WORKING)
        idx_completed = events.index(TaskState.COMPLETED)
        self.assertLess(idx_submitted, idx_working)
        self.assertLess(idx_working, idx_completed)

        hermes.stop()
        weather.stop()

    def test_07b_multi_stream_concurrent_tasks(self):
        """Submit 3 tasks, stream all simultaneously in threads."""
        hermes = self._create_client("hermes-assistant")
        weather = self._create_client("weather-agent")
        analyst = self._create_client("analyst")

        for a in [hermes, weather, analyst]:
            a.start()

        # Submit 3 async tasks
        tid1 = hermes.send_task_async("default/default/weather-agent", "Stream 1: Weather")
        tid2 = hermes.send_task_async("default/default/analyst", "Stream 2: Analysis")
        tid3 = hermes.send_task_async("default/default/analyst", "Stream 3: Forecast")

        all_events = {}
        lock = threading.Lock()

        def stream_one(task_id):
            events = []
            def on_event(tid, state, data):
                events.append((tid, state))
                return state not in (TaskState.COMPLETED, TaskState.FAILED, TaskState.CANCELLED)

            # Need a separate client for streaming (thread safety)
            streamer = self._create_client("hermes-assistant")
            streamer.start()
            streamer.stream_task(task_id, on_event, timeout=15)
            streamer.stop()

            with lock:
                all_events[task_id] = events

        threads = []
        for tid in [tid1, tid2, tid3]:
            t = threading.Thread(target=stream_one, args=(tid,))
            threads.append(t)
            t.start()

        for t in threads:
            t.join(timeout=20)

        # All 3 streams should have completed
        self.assertEqual(len(all_events), 3)
        for tid, evts in all_events.items():
            states = [s for _, s in evts]
            self.assertIn(TaskState.COMPLETED, states,
                          f"Task {tid} should reach COMPLETED via SSE")

        for a in [hermes, weather, analyst]:
            a.stop()


# =========================================================================
# Scenario 8: Gateway Statistics Dashboard
# =========================================================================

class TestScenario08_GatewayStats(MultiAgentTestBase):
    """Verify gateway statistics reflect multi-agent activity."""

    def test_08a_stats_after_multi_agent_activity(self):
        """Run full workflow, then check gateway stats."""
        agents = [
            self._create_client(name)
            for name in ["orchestrator", "analyst", "researcher", "reporter", "weather-agent"]
        ]
        for a in agents:
            a.start()

        # Simulate workflow
        orch = agents[0]
        targets = [f"default/default/{a._registration.agent_id}" for a in agents[1:]]

        task_ids = []
        for target in targets:
            tid = orch.send_task_async(target, "Workflow task")
            task_ids.append(tid)

        for tid in task_ids:
            orch.wait_for_task(tid, poll_interval=0.5, max_wait=10)

        # Check stats
        import urllib.request
        url = f"{self.gateway_url}/a2a/stats"
        stats = json.loads(urllib.request.urlopen(url).read())

        self.assertEqual(stats["totalAgents"], 5)
        self.assertGreaterEqual(stats["totalTasks"], 4)
        self.assertGreaterEqual(stats["completedTasks"], 4)
        self.assertIn("agentTaskCounts", stats)

        # Each worker should have at least 1 task
        for target_name in targets:
            found = any(target_name in k and v > 0
                       for k, v in stats["agentTaskCounts"].items())
            self.assertTrue(found, f"Agent {target_name} should have tasks")

        for a in agents:
            a.stop()


# =========================================================================
# Scenario 9: Error Recovery & Resiliency
# =========================================================================

class TestScenario09_Resiliency(MultiAgentTestBase):
    """Test error handling in multi-agent scenarios."""

    def test_09a_task_to_nonexistent_agent(self):
        """Sending task to a non-existent agent still works (mock processes it)."""
        hermes = self._create_client("hermes-assistant")
        hermes.start()

        # Target doesn't exist, but mock gateway processes anyway
        result = hermes.send_task("default/default/ghost-agent", "Are you there?", timeout=10)
        self.assertEqual(result.state, TaskState.COMPLETED)
        # Result mentions the target agent name
        self.assertIn("ghost-agent", str(result.data))

        hermes.stop()

    def test_09b_cancel_mid_chain(self):
        """Cancel one task in a chain, others still complete."""
        orchestrator = self._create_client("orchestrator")
        analyst = self._create_client("analyst")
        researcher = self._create_client("researcher")

        orchestrator.start()
        analyst.start()
        researcher.start()

        tid1 = orchestrator.send_task_async("default/default/analyst", "Keep this")
        tid2 = orchestrator.send_task_async("default/default/researcher", "Cancel this")

        # Cancel tid2
        cancelled = orchestrator.cancel_task(tid2)
        self.assertTrue(cancelled)

        # tid1 should still complete
        r1 = orchestrator.wait_for_task(tid1, poll_interval=0.5, max_wait=10)
        self.assertEqual(r1.state, TaskState.COMPLETED)

        # tid2 should be cancelled
        status = orchestrator.get_task_status(tid2)
        self.assertEqual(status.state, TaskState.CANCELLED)

        orchestrator.stop()
        analyst.stop()
        researcher.stop()

    def test_09c_rapid_start_stop(self):
        """Rapidly start/stop multiple agents."""
        for name in ["hermes-assistant", "weather-agent", "analyst"]:
            agent = self._create_client(name)
            agent.start()
            self.assertTrue(agent.is_started())
            agent.stop()
            self.assertFalse(agent.is_started())


# =========================================================================
# Main
# =========================================================================

if __name__ == "__main__":
    print("=" * 70)
    print("  EventMesh Multi-Agent Communication Simulation")
    print("  7 Agent Roles | 8+ Scenarios | Mock A2A Gateway")
    print("=" * 70)

    # Run all scenarios
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()

    suite.addTests(loader.loadTestsFromTestCase(TestScenario01_OneToOne))
    suite.addTests(loader.loadTestsFromTestCase(TestScenario02_FanOut))
    suite.addTests(loader.loadTestsFromTestCase(TestScenario03_ChainDelegation))
    suite.addTests(loader.loadTestsFromTestCase(TestScenario04_Concurrent))
    suite.addTests(loader.loadTestsFromTestCase(TestScenario05_DiscoveryAndRoute))
    suite.addTests(loader.loadTestsFromTestCase(TestScenario06_ParentChildChain))
    suite.addTests(loader.loadTestsFromTestCase(TestScenario07_SSEStreaming))
    suite.addTests(loader.loadTestsFromTestCase(TestScenario08_GatewayStats))
    suite.addTests(loader.loadTestsFromTestCase(TestScenario09_Resiliency))

    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    # Summary
    print(f"\n{'='*70}")
    print(f"  Multi-Agent Test Results:")
    print(f"    Total:  {result.testsRun}")
    print(f"    Passed: {result.testsRun - len(result.failures) - len(result.errors)}")
    print(f"    Failed: {len(result.failures)}")
    print(f"    Errors: {len(result.errors)}")
    print(f"{'='*70}")

    sys.exit(0 if result.wasSuccessful() else 1)
