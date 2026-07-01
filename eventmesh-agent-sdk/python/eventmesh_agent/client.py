"""
A2A Agent Mesh Client for Hermes.

Connects the Hermes AI system to EventMesh's A2A Agent Mesh.
Supports: agent registration, heartbeat, task submission/list/stream,
and agent discovery.
"""

import json
import logging
import threading
import time
from typing import Optional, Dict, Any, Callable, List
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError
from urllib.parse import urlencode, urljoin

from .types import TaskResult, TaskState
from .agent import AgentCard, AgentRegistration, AgentInterface, AgentCapabilities

logger = logging.getLogger(__name__)


class AgentMeshClient:
    """Client for connecting an agent to EventMesh's A2A Agent Mesh.

    Parameters:
        gateway_url: URL of the A2A Gateway (default: http://localhost:10105)
        agent_name: Agent name in 'org/unit/agent' or 'agent' format
        agent_card: AgentCard or simplified dict describing the agent
        heartbeat_interval: Seconds between heartbeats (default: 30)

    Example:
        client = AgentMeshClient(
            gateway_url="http://localhost:10105",
            agent_name="hermes-assistant",
            agent_card={
                "name": "hermes-assistant",
                "description": "Hermes AI Assistant",
                "version": "1.0.0",
                "skills": [
                    {"id": "chat", "name": "Chat", "description": "AI chat"}
                ]
            }
        )
        client.start()
        result = client.send_task("weather-agent", "Weather in Shenzhen?")
        print(result.data)
    """

    _DEFAULT_TIMEOUT = 30
    _DEFAULT_HEARTBEAT = 30

    def __init__(self, gateway_url: str = "http://localhost:10105",
                 agent_name: str = "",
                 agent_card: AgentCard = None,
                 heartbeat_interval: int = _DEFAULT_HEARTBEAT):
        self.gateway_url = gateway_url.rstrip("/")
        self.agent_name = agent_name
        self._registration = AgentRegistration.from_name(agent_name) if agent_name else None

        # Build AgentCard
        if agent_card is None:
            agent_card = AgentCard(name=agent_name, version="1.0.0")
        elif isinstance(agent_card, dict):
            agent_card = AgentCard.from_simple_dict(agent_card)

        # Add default interface (REST JSON-RPC on the gateway)
        if not agent_card.interfaces:
            agent_card.interfaces.append(
                AgentInterface(
                    url=f"{self.gateway_url}/a2a",
                    protocol_binding="JSONRPC",
                    protocol_version="0.3",
                )
            )
        self.agent_card = agent_card

        self.heartbeat_interval = heartbeat_interval
        self._started = False
        self._heartbeat_thread = None
        self._request_handler = None

    # =========================================================================
    # Lifecycle
    # =========================================================================

    def start(self):
        """Register agent card and start heartbeat."""
        if self._started:
            return
        self._register_card()
        self._start_heartbeat()
        self._started = True
        logger.info("AgentMeshClient started: agent=%s", self.agent_name)

    def stop(self):
        """Stop heartbeat and unregister."""
        self._started = False
        if self._heartbeat_thread:
            self._heartbeat_thread = None
        logger.info("AgentMeshClient stopped: agent=%s", self.agent_name)

    def set_request_handler(self, handler: Callable[[str, str], str]):
        """Set handler for incoming A2A task requests.

        Args:
            handler: Function(task_id, message) -> result_string
        """
        self._request_handler = handler

    # =========================================================================
    # Agent Card Registration
    # =========================================================================

    def _register_card(self):
        """Register the agent card on the A2A Gateway."""
        reg = self._registration
        if not reg:
            raise ValueError("agent_name not set, cannot register")
        url = f"{self.gateway_url}/a2a/cards/card/{reg.org_id}/{reg.unit_id}/{reg.agent_id}"
        body = json.dumps(self.agent_card.to_dict()).encode("utf-8")
        self._post(url, body)
        logger.info("AgentCard registered: %s", self.agent_name)

    def _start_heartbeat(self):
        """Start periodic heartbeat in a daemon thread."""
        def _loop():
            while self._started:
                try:
                    self._send_heartbeat()
                except Exception as e:
                    logger.warning("Heartbeat failed: %s", e)
                time.sleep(self.heartbeat_interval)

        self._heartbeat_thread = threading.Thread(target=_loop, daemon=True)
        self._heartbeat_thread.start()

    def _send_heartbeat(self):
        reg = self._registration
        url = f"{self.gateway_url}/a2a/heartbeat"
        body = json.dumps(reg.to_heartbeat_dict()).encode("utf-8")
        self._post(url, body)

    # =========================================================================
    # Task Submission
    # =========================================================================

    def send_task(self, target_agent: str, message: str,
                  timeout: int = _DEFAULT_TIMEOUT) -> TaskResult:
        """Submit a task to a target agent and wait for result (sync mode).

        Args:
            target_agent: Name of the target agent
            message: Task message (JSON string or plain text)
            timeout: Max wait time in seconds

        Returns:
            TaskResult with task ID, state, and data
        """
        url = f"{self.gateway_url}/a2a/tasks?mode=sync"
        body = json.dumps({
            "targetAgent": target_agent,
            "message": message,
        }).encode("utf-8")
        resp = self._post(url, body, timeout=timeout)
        return TaskResult.from_dict(resp)

    def send_task_async(self, target_agent: str, message: str,
                        parent_task_id: Optional[str] = None) -> str:
        """Submit a task and return immediately with the task ID (async mode).

        Returns:
            Task ID string for polling later via get_task_status()
        """
        url = f"{self.gateway_url}/a2a/tasks?mode=async"
        payload = {
            "targetAgent": target_agent,
            "message": message,
        }
        if parent_task_id:
            payload["parentTaskId"] = parent_task_id
        body = json.dumps(payload).encode("utf-8")
        resp = self._post(url, body)
        return resp.get("taskId", "")

    def get_task_status(self, task_id: str) -> TaskResult:
        """Get current status of a task.

        Returns empty TaskResult if the task is not found (404).
        """
        url = f"{self.gateway_url}/a2a/tasks/{task_id}"
        try:
            resp = self._get(url)
            return TaskResult.from_dict(resp)
        except AgentMeshError as e:
            if "404" in str(e):
                return TaskResult(task_id="", state="UNKNOWN")
            raise

    def cancel_task(self, task_id: str) -> bool:
        """Cancel a pending task.

        Returns False if the task is not found (404).
        """
        url = f"{self.gateway_url}/a2a/tasks/{task_id}"
        try:
            self._delete(url)
            return True
        except AgentMeshError as e:
            if "404" in str(e):
                return False
            raise

    def stream_task(self, task_id: str,
                    on_event: Callable[[str, str, Optional[str]], bool],
                    timeout: int = 120) -> None:
        """Stream task status updates via SSE.

        Args:
            task_id: Task ID to stream
            on_event: Callback(task_id, state, data) -> bool (return False to stop)
            timeout: Max stream duration in seconds
        """
        url = f"{self.gateway_url}/a2a/tasks/{task_id}/stream"
        req = Request(url)
        req.add_header("Accept", "text/event-stream")

        start = time.time()
        try:
            with urlopen(req, timeout=timeout) as resp:
                event_buf = ""
                for line_bytes in resp:
                    if time.time() - start > timeout:
                        break
                    line = line_bytes.decode("utf-8").rstrip("\n").rstrip("\r")

                    if line == "":
                        # Event boundary
                        if event_buf:
                            keep_going = self._process_sse_event(event_buf, task_id, on_event)
                            event_buf = ""
                            if not keep_going:
                                break
                    elif line.startswith(":"):
                        # SSE comment / heartbeat
                        pass
                    else:
                        event_buf += line + "\n"
        except Exception as e:
            logger.error("SSE stream error for task %s: %s", task_id, e)

    def _process_sse_event(self, raw: str, task_id: str,
                           on_event: Callable) -> bool:
        """Parse an SSE event and call the handler.

        Parses standard SSE format:
            event: status
            data: {"taskId": "x", "state": "WORKING"}

        Extracts the 'data:' field, ignoring 'event:' and 'id:'.
        """
        data_payload = None
        for line in raw.split("\n"):
            line = line.strip()
            if line.startswith("data:"):
                data_payload = line[5:].strip()
                break

        if data_payload is None:
            logger.warning("SSE event without data field: %s", raw[:100])
            return True

        try:
            event_obj = json.loads(data_payload)
            event_task_id = event_obj.get("taskId", task_id)
            state = event_obj.get("state", "")
            event_data = event_obj.get("data")
            return on_event(event_task_id, state, event_data)
        except json.JSONDecodeError:
            logger.warning("Failed to parse SSE event data: %s", data_payload[:100])
            return True

    # =========================================================================
    # Discovery
    # =========================================================================

    def list_agents(self) -> List[Dict[str, Any]]:
        """List all registered agents."""
        url = f"{self.gateway_url}/a2a/agents"
        return self._get(url)

    def get_agent_card(self, org_id: str = "default", unit_id: str = "default",
                       agent_id: str = "") -> Dict[str, Any]:
        """Get a specific agent's card."""
        url = f"{self.gateway_url}/a2a/cards/card/{org_id}/{unit_id}/{agent_id}"
        return self._get(url)

    def health_check(self) -> Dict[str, Any]:
        """Check gateway health."""
        url = f"{self.gateway_url}/a2a/health"
        return self._get(url)

    # =========================================================================
    # HTTP Helpers
    # =========================================================================

    def _get(self, url: str, timeout: int = _DEFAULT_TIMEOUT) -> Any:
        req = Request(url)
        req.add_header("Accept", "application/json")
        return self._do_request(req, timeout)

    def _post(self, url: str, body: bytes = None,
              timeout: int = _DEFAULT_TIMEOUT) -> Any:
        req = Request(url, data=body or b"", method="POST")
        req.add_header("Content-Type", "application/json")
        req.add_header("Accept", "application/json")
        return self._do_request(req, timeout)

    def _delete(self, url: str, timeout: int = _DEFAULT_TIMEOUT) -> Any:
        req = Request(url, method="DELETE")
        req.add_header("Accept", "application/json")
        return self._do_request(req, timeout)

    def _do_request(self, req: Request, timeout: int) -> Any:
        try:
            with urlopen(req, timeout=timeout) as resp:
                raw = resp.read().decode("utf-8")
                if not raw.strip():
                    return {}
                return json.loads(raw)
        except HTTPError as e:
            body = e.read().decode("utf-8") if e.fp else ""
            raise AgentMeshError(
                f"HTTP {e.code}: {body[:200]}"
            ) from e
        except URLError as e:
            raise AgentMeshError(
                f"Connection failed: {e.reason}"
            ) from e

    def __repr__(self) -> str:
        return f"AgentMeshClient(agent={self.agent_name}, gateway={self.gateway_url})"


class AgentMeshError(Exception):
    """Raised when an A2A Gateway request fails."""
    pass
