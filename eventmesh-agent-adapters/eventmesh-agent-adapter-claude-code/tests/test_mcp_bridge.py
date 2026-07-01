"""
Integration tests for EventMesh Agent MCP Bridge.

Tests the JSON-RPC over stdio protocol bridge against a live mock A2A Gateway:
- MCP protocol handshake (initialize / notifications/initialized)
- tools/list returns all 5 tools
- tools/call for each tool
- ping
- error handling: unknown method, invalid JSON, gateway errors

The MCP server is spawned as a subprocess so stdin/stdout are real pipes.
Server communicates with a MockGateway over HTTP.

Run:
    cd eventmesh-agent-adapters/eventmesh-agent-adapter-claude-code
    PYTHONPATH=../../eventmesh-agent-sdks/python:../../eventmesh-agent-sdks/python/tests \\
        python3 tests/test_mcp_bridge.py
"""

import json
import os
import subprocess
import sys
import time
import unittest

# Point to shared SDK
_SDK_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
    "eventmesh-agent-sdks", "python",
)
_TEST_SDK_PATH = os.path.join(_SDK_PATH, "tests")
sys.path.insert(0, _SDK_PATH)
sys.path.insert(0, _TEST_SDK_PATH)

from mock_gateway import MockGateway

_MCP_SERVER_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "../../eventmesh-agent-sdks/python/integrations/mcp/server.py",
)


class MCPBridgeTestBase(unittest.TestCase):
    """Base class for MCP bridge tests with subprocess management."""

    mcp_proc = None
    gateway = None
    req_id_counter = 0

    @classmethod
    def setUpClass(cls):
        # Start mock A2A gateway
        cls.gateway = MockGateway().start()
        cls.gateway_url = cls.gateway.url

    @classmethod
    def tearDownClass(cls):
        if cls.mcp_proc:
            _safe_kill(cls.mcp_proc)
        if cls.gateway:
            cls.gateway.stop()

    def setUp(self):
        # Start MCP bridge as subprocess pointing at our mock gateway
        env = os.environ.copy()
        env["A2A_GATEWAY_URL"] = self.gateway_url
        env["A2A_REQUEST_TIMEOUT"] = "10"

        self.mcp_proc = subprocess.Popen(
            [sys.executable, "-u", _MCP_SERVER_PATH],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=env,
            text=True,
        )
        self.req_id_counter = 0

    def tearDown(self):
        if self.mcp_proc:
            _safe_kill(self.mcp_proc)

    def _next_id(self) -> int:
        self.req_id_counter += 1
        return self.req_id_counter

    def _request(self, method: str, params: dict = None) -> dict:
        """Send a JSON-RPC request and return the response."""
        req_id = self._next_id()
        msg = {"jsonrpc": "2.0", "id": req_id, "method": method}
        if params:
            msg["params"] = params

        line = json.dumps(msg, ensure_ascii=False) + "\n"
        self.mcp_proc.stdin.write(line)
        self.mcp_proc.stdin.flush()

        # Read response (with timeout via poll)
        return self._read_response(req_id, timeout=5)

    def _notify(self, method: str, params: dict = None):
        """Send a JSON-RPC notification (no id, no response expected)."""
        msg = {"jsonrpc": "2.0", "method": method}
        if params:
            msg["params"] = params
        line = json.dumps(msg, ensure_ascii=False) + "\n"
        self.mcp_proc.stdin.write(line)
        self.mcp_proc.stdin.flush()

    def _read_response(self, req_id: int, timeout: float = 5) -> dict:
        """Read a JSON-RPC response matching req_id."""
        import select
        deadline = time.time() + timeout
        while time.time() < deadline:
            remaining = deadline - time.time()
            ready, _, _ = select.select([self.mcp_proc.stdout], [], [], max(remaining, 0.01))
            if not ready:
                raise TimeoutError(f"No response for id={req_id} within {timeout}s")
            line = self.mcp_proc.stdout.readline()
            if not line:
                raise EOFError("MCP process closed stdout")
            resp = json.loads(line.strip())
            if resp.get("id") == req_id:
                return resp
            # Could be a stray log or earlier response; keep reading
            print(f"  [skipping response id={resp.get('id')}]")
        raise TimeoutError(f"No response for id={req_id} within {timeout}s")


# =========================================================================
# Protocol Handshake Tests
# =========================================================================

class TestMCPHandshake(MCPBridgeTestBase):

    def test_01_initialize(self):
        """Initialize returns protocol version and capabilities."""
        resp = self._request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "test-client", "version": "1.0"},
        })
        self.assertIn("result", resp)
        result = resp["result"]
        self.assertEqual(result["protocolVersion"], "2024-11-05")
        self.assertIn("capabilities", result)
        self.assertIn("tools", result["capabilities"])
        self.assertEqual(result["serverInfo"]["name"], "eventmesh-agent-sdks-mcp")

    def test_02_notifications_initialized(self):
        """notifications/initialized is accepted without response."""
        self._notify("notifications/initialized", {})
        # Notifications don't get a response; verify process is still alive
        # poll() returns None when process is still running
        self.assertIsNone(self.mcp_proc.poll(), "Process died after notification")

    def test_03_initialize_then_notify(self):
        """Full handshake: initialize -> notifications/initialized."""
        resp = self._request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "test", "version": "0.1"},
        })
        self.assertIn("result", resp)
        self._notify("notifications/initialized", {})
        self.assertIsNone(self.mcp_proc.poll(), "Process died after handshake")

    def test_04_ping(self):
        """Ping returns empty result."""
        resp = self._request("ping")
        self.assertIn("result", resp)
        self.assertEqual(resp["result"], {})


# =========================================================================
# tools/list Tests
# =========================================================================

class TestMCPToolList(MCPBridgeTestBase):

    def test_05_list_tools_count(self):
        """tools/list returns exactly 5 tools."""
        resp = self._request("tools/list")
        tools = resp["result"]["tools"]
        self.assertEqual(len(tools), 5)

    def test_06_list_tools_names(self):
        """All expected tool names are present."""
        resp = self._request("tools/list")
        tool_names = {t["name"] for t in resp["result"]["tools"]}
        expected = {
            "a2a_list_agents",
            "a2a_send_task",
            "a2a_get_task_status",
            "a2a_health_check",
            "a2a_get_agent_card",
        }
        self.assertEqual(tool_names, expected)

    def test_07_list_tools_each_has_schema(self):
        """Each tool defines inputSchema."""
        resp = self._request("tools/list")
        for tool in resp["result"]["tools"]:
            self.assertIn("inputSchema", tool, f"Tool {tool.get('name')} missing inputSchema")
            self.assertIn("type", tool["inputSchema"])

    def test_08_list_tools_each_has_description(self):
        """Each tool has a non-empty description."""
        resp = self._request("tools/list")
        for tool in resp["result"]["tools"]:
            self.assertTrue(tool.get("description"), f"Tool {tool['name']} has empty description")


# =========================================================================
# tools/call Tests (Requires Gateway)
# =========================================================================

class TestMCPToolCall(MCPBridgeTestBase):

    def test_09_call_health_check(self):
        """a2a_health_check returns gateway status."""
        resp = self._request("tools/call", {
            "name": "a2a_health_check",
            "arguments": {},
        })
        result = resp["result"]
        self.assertIn("content", result)
        self.assertEqual(len(result["content"]), 1)
        self.assertEqual(result["content"][0]["type"], "text")
        data = json.loads(result["content"][0]["text"])
        self.assertIn("gateway_status", data)

    def test_10_call_list_agents(self):
        """a2a_list_agents returns agent list."""
        # First register an agent via HTTP so there is something to list
        from urllib.request import Request, urlopen
        register_body = json.dumps({"name": "test-agent"}).encode()
        req = Request(
            f"{self.gateway_url}/a2a/cards/card/test/unit/agent-a",
            data=register_body, method="POST",
        )
        urlopen(req, timeout=5)

        resp = self._request("tools/call", {
            "name": "a2a_list_agents",
            "arguments": {},
        })
        result = resp["result"]
        data = json.loads(result["content"][0]["text"])
        self.assertIn("agent_count", data)
        self.assertGreaterEqual(data["agent_count"], 1)

    def test_11_call_send_task_sync(self):
        """a2a_send_task in sync mode returns completed task."""
        resp = self._request("tools/call", {
            "name": "a2a_send_task",
            "arguments": {
                "target_agent": "weather-agent",
                "message": "Shenzhen weather?",
                "mode": "sync",
            },
        })
        result = resp["result"]
        data = json.loads(result["content"][0]["text"])
        self.assertIn("task_id", data)
        self.assertEqual(data["state"], "COMPLETED")
        self.assertIsNotNone(data.get("data"))

    def test_12_call_send_task_async(self):
        """a2a_send_task in async mode returns task ID immediately."""
        resp = self._request("tools/call", {
            "name": "a2a_send_task",
            "arguments": {
                "target_agent": "analyst",
                "message": "Market analysis",
                "mode": "async",
            },
        })
        result = resp["result"]
        data = json.loads(result["content"][0]["text"])
        self.assertIn("task_id", data)
        self.assertEqual(data["state"], "SUBMITTED")
        self.assertEqual(data["mode"], "async")

    def test_13_call_get_task_status(self):
        """a2a_get_task_status checks an async task."""
        # Submit a task first
        submit = self._request("tools/call", {
            "name": "a2a_send_task",
            "arguments": {
                "target_agent": "worker",
                "message": "Test task",
                "mode": "async",
            },
        })
        submit_data = json.loads(submit["result"]["content"][0]["text"])
        task_id = submit_data["task_id"]

        # Check status
        resp = self._request("tools/call", {
            "name": "a2a_get_task_status",
            "arguments": {"task_id": task_id},
        })
        result = resp["result"]
        data = json.loads(result["content"][0]["text"])
        self.assertIn("task_id", data)
        self.assertIn("state", data)

    def test_14_call_get_agent_card(self):
        """a2a_get_agent_card returns agent details."""
        # Register an agent
        from urllib.request import Request, urlopen
        card_body = json.dumps({
            "name": "target-agent",
            "version": "1.0",
            "skills": [{"id": "skill-1", "name": "Skill 1"}],
        }).encode()
        req = Request(
            f"{self.gateway_url}/a2a/cards/card/default/default/target-agent",
            data=card_body, method="POST",
        )
        urlopen(req, timeout=5)

        resp = self._request("tools/call", {
            "name": "a2a_get_agent_card",
            "arguments": {
                "agent_name": "default/default/target-agent",
            },
        })
        result = resp["result"]
        data = json.loads(result["content"][0]["text"])
        self.assertEqual(data.get("name"), "target-agent")
        self.assertEqual(data.get("version"), "1.0")

    def test_15_call_get_agent_card_not_found(self):
        """a2a_get_agent_card returns error for non-existent agent."""
        resp = self._request("tools/call", {
            "name": "a2a_get_agent_card",
            "arguments": {
                "agent_name": "ghost/ghost/ghost",
            },
        })
        result = resp["result"]
        data = json.loads(result["content"][0]["text"])
        # Should contain error field from gateway
        self.assertIn("error", data)

    def test_16_call_send_task_default_mode_sync(self):
        """a2a_send_task defaults to sync mode when mode not specified."""
        resp = self._request("tools/call", {
            "name": "a2a_send_task",
            "arguments": {
                "target_agent": "chatbot",
                "message": "Hello!",
            },
        })
        result = resp["result"]
        data = json.loads(result["content"][0]["text"])
        self.assertEqual(data["state"], "COMPLETED")
        self.assertEqual(data.get("mode"), "sync")


# =========================================================================
# Error Handling Tests
# =========================================================================

class TestMCPErrors(MCPBridgeTestBase):

    def test_17_unknown_method(self):
        """Unknown method returns JSON-RPC error code -32601."""
        resp = self._request("nonexistent_method")
        self.assertIn("error", resp)
        self.assertEqual(resp["error"]["code"], -32601)

    def test_18_unknown_tool(self):
        """tools/call with unknown tool name returns JSON-RPC error."""
        resp = self._request("tools/call", {
            "name": "a2a_no_such_tool",
            "arguments": {},
        })
        self.assertIn("error", resp)
        self.assertEqual(resp["error"]["code"], -32601)
        self.assertIn("Unknown tool", resp["error"]["message"])

    def test_19_missing_required_args(self):
        """a2a_send_task without required args returns error (key missing)."""
        resp = self._request("tools/call", {
            "name": "a2a_send_task",
            "arguments": {},
        })
        # Tool handler raises KeyError because "target_agent"/"message" missing
        # Server wraps in JSON-RPC error with code -32000
        self.assertIn("error", resp, f"Expected error, got: {resp}")
        self.assertEqual(resp["error"]["code"], -32000)
        self.assertIn("Tool error", resp["error"]["message"])

    def test_20_multiple_requests_in_sequence(self):
        """Multiple consecutive requests receive correct responses."""
        # Initialize
        r1 = self._request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "test", "version": "1.0"},
        })
        self.assertIn("result", r1)

        # tools/list
        r2 = self._request("tools/list")
        self.assertEqual(len(r2["result"]["tools"]), 5)

        # health check
        r3 = self._request("tools/call", {
            "name": "a2a_health_check",
            "arguments": {},
        })
        self.assertIn("result", r3)

        # ping
        r4 = self._request("ping")
        self.assertEqual(r4["result"], {})

    def test_21_invalid_json_crash_recovery(self):
        """Sending invalid JSON does not kill the process."""
        self.mcp_proc.stdin.write("not valid json!!!\n")
        self.mcp_proc.stdin.flush()

        # Process should still be alive
        time.sleep(0.5)
        self.assertIsNone(self.mcp_proc.poll(), "Process died on invalid JSON")

        # Can still process valid requests
        resp = self._request("ping")
        self.assertEqual(resp["result"], {})


def _safe_kill(proc):
    """Gracefully terminate a subprocess."""
    try:
        proc.stdin.close()
    except Exception:
        pass
    try:
        proc.terminate()
        proc.wait(timeout=3)
    except Exception:
        try:
            proc.kill()
        except Exception:
            pass


if __name__ == "__main__":
    unittest.main(verbosity=2)
