"""
Mock A2A Gateway — lightweight HTTP server that emulates EventMesh's A2A Gateway.

Supports: agent card registration, heartbeat, task lifecycle (sync/async/cancel/stream),
agent discovery, health check.

Zero external dependencies (Python stdlib only).
"""

import json
import re
import threading
import time
import uuid
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs


class A2AGatewayState:
    """In-memory state for the mock gateway with multi-agent support."""

    def __init__(self):
        self.agents = {}          # agent_id -> agent_card
        self.heartbeats = {}      # agent_id -> last heartbeat epoch
        self.tasks = {}           # task_id -> task_info
        self.agent_tasks = {}     # agent_key -> [task_id, ...]  (tasks routed to this agent)
        self.task_children = {}   # parent_task_id -> [child_task_id, ...]
        self.task_history = []    # ordered list of all completed task_ids
        self._lock = threading.Lock()

    def register_agent(self, org_id: str, unit_id: str, agent_id: str, card: dict):
        with self._lock:
            key = f"{org_id}/{unit_id}/{agent_id}"
            self.agents[key] = card
            self.heartbeats[key] = time.time()
            if key not in self.agent_tasks:
                self.agent_tasks[key] = []

    def heartbeat(self, org_id: str, unit_id: str, agent_id: str):
        with self._lock:
            key = f"{org_id}/{unit_id}/{agent_id}"
            self.heartbeats[key] = time.time()
            return key in self.agents

    def list_agents(self) -> list:
        with self._lock:
            return [
                {
                    "name": k,
                    "status": "online",
                    "lastHeartbeat": self.heartbeats.get(k, 0),
                    "taskCount": len(self.agent_tasks.get(k, [])),
                }
                for k in self.agents
            ]

    def get_agent_card(self, org_id: str, unit_id: str, agent_id: str) -> dict:
        with self._lock:
            key = f"{org_id}/{unit_id}/{agent_id}"
            return self.agents.get(key)

    def get_agent_tasks(self, org_id: str, unit_id: str, agent_id: str) -> list:
        """Get all task IDs routed to a specific agent."""
        with self._lock:
            key = f"{org_id}/{unit_id}/{agent_id}"
            return list(self.agent_tasks.get(key, []))

    def get_task_chain(self, parent_task_id: str) -> list:
        """Get all child task IDs for a parent task (recursive)."""
        with self._lock:
            return list(self.task_children.get(parent_task_id, []))

    def create_task(self, target_agent: str, message: str,
                    parent_task_id: str = "", caller_agent: str = "") -> dict:
        task_id = str(uuid.uuid4())[:8]
        task = {
            "taskId": task_id,
            "targetAgent": target_agent,
            "message": message,
            "state": "SUBMITTED",
            "parentTaskId": parent_task_id,
            "callerAgent": caller_agent,
            "createdAt": time.time(),
            "children": [],
        }
        with self._lock:
            self.tasks[task_id] = task
            # Track task routing
            self.agent_tasks.setdefault(target_agent, []).append(task_id)
            # Track parent-child chain
            if parent_task_id:
                self.task_children.setdefault(parent_task_id, []).append(task_id)
                if parent_task_id in self.tasks:
                    self.tasks[parent_task_id].setdefault("children", []).append(task_id)

        # Simulate async processing: move SUBMITTED -> WORKING -> COMPLETED
        def _process():
            time.sleep(0.25)
            with self._lock:
                if task_id in self.tasks and self.tasks[task_id]["state"] not in ("CANCELLED",):
                    self.tasks[task_id]["state"] = "WORKING"
            time.sleep(0.25)
            with self._lock:
                if task_id in self.tasks and self.tasks[task_id]["state"] not in ("CANCELLED",):
                    self.tasks[task_id]["state"] = "COMPLETED"
                    # Route result with agent identity
                    self.tasks[task_id]["data"] = {
                        "result": f"[{target_agent}] Completed: {message[:60]}",
                        "taskId": task_id,
                        "targetAgent": target_agent,
                    }
                    self.task_history.append(task_id)

        t = threading.Thread(target=_process, daemon=True)
        t.start()
        return task

    def get_task(self, task_id: str) -> dict:
        with self._lock:
            return self.tasks.get(task_id)

    def cancel_task(self, task_id: str) -> bool:
        with self._lock:
            if task_id in self.tasks:
                self.tasks[task_id]["state"] = "CANCELLED"
                return True
            return False

    def get_stats(self) -> dict:
        """Return gateway statistics for multi-agent observability."""
        with self._lock:
            agent_task_counts = {
                k: len(v) for k, v in self.agent_tasks.items()
            }
            return {
                "totalAgents": len(self.agents),
                "totalTasks": len(self.tasks),
                "completedTasks": len(self.task_history),
                "agentTaskCounts": agent_task_counts,
                "parentChains": len(self.task_children),
            }

    def stream_task(self, task_id: str):
        """Generator yielding SSE events for a task."""
        task = self.get_task(task_id)
        if not task:
            yield f"event: error\ndata: {json.dumps({'error': 'task not found'})}\n\n"
            return

        states = ["SUBMITTED", "WORKING", "COMPLETED"]
        for i, state in enumerate(states):
            if i > 0:
                time.sleep(0.5)
                task = self.get_task(task_id)
                if task:
                    task["state"] = state
            event = {
                "taskId": task_id,
                "state": state,
                "data": task.get("data") if state == "COMPLETED" else None,
                "callerAgent": task.get("callerAgent", ""),
            }
            yield f"event: status\ndata: {json.dumps(event)}\n\n"


class MockA2AHandler(BaseHTTPRequestHandler):
    """HTTP handler for mock A2A Gateway."""

    gateway_state = A2AGatewayState()  # shared across all requests

    # Route patterns
    _ROUTE_CARD_REGISTER = re.compile(r"^/a2a/cards/card/([^/]+)/([^/]+)/([^/]+)$")
    _ROUTE_CARD_GET = re.compile(r"^/a2a/cards/card/([^/]+)/([^/]+)/([^/]+)$")
    _ROUTE_HEARTBEAT = re.compile(r"^/a2a/heartbeat$")
    _ROUTE_HEALTH = re.compile(r"^/a2a/health$")
    _ROUTE_AGENTS = re.compile(r"^/a2a/agents$")
    _ROUTE_STATS = re.compile(r"^/a2a/stats$")
    _ROUTE_TASKS = re.compile(r"^/a2a/tasks$")
    _ROUTE_TASK_BY_ID = re.compile(r"^/a2a/tasks/([^/]+)$")
    _ROUTE_TASK_STREAM = re.compile(r"^/a2a/tasks/([^/]+)/stream$")
    _ROUTE_TASK_CHAIN = re.compile(r"^/a2a/tasks/([^/]+)/chain$")
    _ROUTE_AGENT_TASKS = re.compile(r"^/a2a/agents/([^/]+)/([^/]+)/([^/]+)/tasks$")

    def log_message(self, format, *args):
        """Suppress default logging to stderr; use our own."""
        pass

    def _send_json(self, code: int, data: dict):
        body = json.dumps(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_text(self, code: int, text: str):
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/event-stream" if "event-stream" in text else "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self) -> dict:
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            return {}
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            return {}

    def _parse_query(self) -> dict:
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        # flatten single-value params
        return {k: v[0] if len(v) == 1 else v for k, v in qs.items()}

    # =========================================================================
    # Routing
    # =========================================================================

    def do_GET(self):
        path = urlparse(self.path).path

        # Health check
        if self._ROUTE_HEALTH.match(path):
            self._send_json(200, {
                "status": "healthy",
                "version": "1.0.0",
                "agents": len(self.gateway_state.agents),
            })
            return

        # List agents
        if self._ROUTE_AGENTS.match(path):
            self._send_json(200, self.gateway_state.list_agents())
            return

        # Gateway stats (multi-agent observability)
        if self._ROUTE_STATS.match(path):
            self._send_json(200, self.gateway_state.get_stats())
            return

        # Get agent card
        m = self._ROUTE_CARD_GET.match(path)
        if m:
            org_id, unit_id, agent_id = m.groups()
            card = self.gateway_state.get_agent_card(org_id, unit_id, agent_id)
            if card:
                self._send_json(200, card)
            else:
                self._send_json(404, {"error": "agent not found"})
            return

        # Get agent's tasks
        m = self._ROUTE_AGENT_TASKS.match(path)
        if m:
            org_id, unit_id, agent_id = m.groups()
            task_ids = self.gateway_state.get_agent_tasks(org_id, unit_id, agent_id)
            tasks = [self.gateway_state.get_task(tid) for tid in task_ids]
            self._send_json(200, [t for t in tasks if t])
            return

        # Get task chain (children of a parent)
        m = self._ROUTE_TASK_CHAIN.match(path)
        if m:
            parent_id = m.group(1)
            child_ids = self.gateway_state.get_task_chain(parent_id)
            children = [self.gateway_state.get_task(tid) for tid in child_ids]
            self._send_json(200, {
                "parentTaskId": parent_id,
                "children": [t for t in children if t],
            })
            return

        # Get task status
        m = self._ROUTE_CARD_GET.match(path)
        if m:
            org_id, unit_id, agent_id = m.groups()
            card = self.gateway_state.get_agent_card(org_id, unit_id, agent_id)
            if card:
                self._send_json(200, card)
            else:
                self._send_json(404, {"error": "agent not found"})
            return

        # Get task status
        m = self._ROUTE_TASK_BY_ID.match(path)
        if m:
            task_id = m.group(1)
            task = self.gateway_state.get_task(task_id)
            if task:
                self._send_json(200, task)
            else:
                self._send_json(404, {"error": "task not found"})
            return

        # SSE task stream
        m = self._ROUTE_TASK_STREAM.match(path)
        if m:
            task_id = m.group(1)
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.end_headers()

            for event in self.gateway_state.stream_task(task_id):
                self.wfile.write(event.encode("utf-8"))
                self.wfile.flush()
            return

        self._send_json(404, {"error": "not found"})

    def do_POST(self):
        path = urlparse(self.path).path
        body = self._read_body()
        params = self._parse_query()

        # Register agent card
        m = self._ROUTE_CARD_REGISTER.match(path)
        if m:
            org_id, unit_id, agent_id = m.groups()
            self.gateway_state.register_agent(org_id, unit_id, agent_id, body)
            self._send_json(201, {"status": "registered", "agentId": agent_id})
            return

        # Heartbeat
        if self._ROUTE_HEARTBEAT.match(path):
            found = self.gateway_state.heartbeat(
                body.get("orgId", "default"),
                body.get("unitId", "default"),
                body.get("agentId", ""),
            )
            if found:
                self._send_json(200, {"status": "ok"})
            else:
                self._send_json(404, {"error": "agent not registered"})
            return

        # Submit task
        if self._ROUTE_TASKS.match(path):
            mode = params.get("mode", "sync")
            target_agent = body.get("targetAgent", "")
            message = body.get("message", "")
            parent_task_id = body.get("parentTaskId", "")
            caller_agent = body.get("callerAgent", "")

            task = self.gateway_state.create_task(
                target_agent, message, parent_task_id, caller_agent
            )

            if mode == "sync":
                # Wait for task completion
                for _ in range(20):
                    t = self.gateway_state.get_task(task["taskId"])
                    if t and t["state"] in ("COMPLETED", "FAILED", "CANCELLED"):
                        self._send_json(200, t)
                        return
                    time.sleep(0.1)
                # Timeout fallback
                self._send_json(200, self.gateway_state.get_task(task["taskId"]))
            else:
                self._send_json(202, {"taskId": task["taskId"], "state": "SUBMITTED"})
            return

        self._send_json(404, {"error": "not found"})

    def do_DELETE(self):
        path = urlparse(self.path).path

        m = self._ROUTE_TASK_BY_ID.match(path)
        if m:
            task_id = m.group(1)
            cancelled = self.gateway_state.cancel_task(task_id)
            if cancelled:
                self.send_response(204)
                self.end_headers()
            else:
                self._send_json(404, {"error": "task not found"})
            return

        self._send_json(404, {"error": "not found"})


class MockGateway:
    """Mock A2A Gateway server that can be started/stopped programmatically."""

    def __init__(self, host: str = "127.0.0.1", port: int = 0):
        self.host = host
        self.port = port
        self._server = None
        self._thread = None

    @property
    def url(self) -> str:
        return f"http://{self.host}:{self.port}"

    @property
    def handler_class(self):
        return MockA2AHandler

    def reset(self):
        """Reset gateway state (clear agents, tasks, etc.)."""
        MockA2AHandler.gateway_state = A2AGatewayState()

    def start(self):
        """Start the mock gateway in a background thread."""
        self.reset()  # always fresh state on start
        self._server = HTTPServer((self.host, self.port), self.handler_class)
        self.port = self._server.server_port  # read back actual port (0 = OS-assigned)
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
        self._thread.start()
        return self

    def stop(self):
        """Shutdown the mock gateway."""
        if self._server:
            self._server.shutdown()
            self._server = None
        if self._thread:
            self._thread.join(timeout=1)
            self._thread = None

    def __enter__(self):
        return self.start()

    def __exit__(self, *args):
        self.stop()
