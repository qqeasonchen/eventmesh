"""Claude Code MCP Server: Bridge between Claude Code and EventMesh A2A Agent Mesh.

This MCP server exposes tools that allow Claude Code to:
  - Discover registered A2A agents (list_agents)
  - Send tasks to specific agents (send_task)
  - Stream task progress (stream_task)
  - Check agent health

Usage with Claude Code:
    Add to ~/.claude.json:
    {
      "mcpServers": {
        "eventmesh-agentmesh": {
          "command": "python3",
          "args": ["path/to/claude_code_mcp_server.py"],
          "env": {
            "A2A_GATEWAY_URL": "http://localhost:10105"
          }
        }
      }
    }
"""

import json
import sys
import os
import logging
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError
from typing import Any, Dict, List, Optional

logging.basicConfig(
    level=logging.WARNING,
    format="%(message)s",
    stream=sys.stderr,
)
logger = logging.getLogger("eventmesh-mcp")

A2A_GATEWAY_URL = os.environ.get("A2A_GATEWAY_URL", "http://localhost:10105")
REQUEST_TIMEOUT = int(os.environ.get("A2A_REQUEST_TIMEOUT", "30"))


# =========================================================================
# A2A Gateway HTTP Client
# =========================================================================

def a2a_get(path: str) -> Any:
    url = f"{A2A_GATEWAY_URL}{path}"
    req = Request(url)
    req.add_header("Accept", "application/json")
    try:
        with urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except HTTPError as e:
        body = e.read().decode("utf-8") if e.fp else ""
        return {"error": f"HTTP {e.code}: {body[:200]}"}
    except Exception as e:
        return {"error": str(e)}


def a2a_post(path: str, body: Dict[str, Any]) -> Any:
    url = f"{A2A_GATEWAY_URL}{path}"
    data = json.dumps(body).encode("utf-8")
    req = Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    try:
        with urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except HTTPError as e:
        resp_body = e.read().decode("utf-8") if e.fp else ""
        return {"error": f"HTTP {e.code}: {resp_body[:200]}"}
    except Exception as e:
        return {"error": str(e)}


# =========================================================================
# MCP Tool Definitions
# =========================================================================

TOOLS = [
    {
        "name": "a2a_list_agents",
        "description": (
            "List all registered A2A agents in the EventMesh Agent Mesh. "
            "Returns agent name, status (online/offline), version, and description. "
            "Use this to discover what agents are available for collaboration."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": [],
        },
    },
    {
        "name": "a2a_send_task",
        "description": (
            "Send a task to a registered A2A agent and wait for the result. "
            "The target agent processes the request and returns its response. "
            "Use this to delegate work to specialized agents (e.g. weather, code review, etc.)."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "target_agent": {
                    "type": "string",
                    "description": "Name of the target agent (use a2a_list_agents to discover names)",
                },
                "message": {
                    "type": "string",
                    "description": "The message/task to send to the agent (can be JSON or plain text)",
                },
                "mode": {
                    "type": "string",
                    "enum": ["sync", "async"],
                    "description": "sync: wait for result; async: return task ID immediately (default: sync)",
                },
            },
            "required": ["target_agent", "message"],
        },
    },
    {
        "name": "a2a_get_task_status",
        "description": (
            "Get the current status of a previously submitted async task. "
            "Returns state (SUBMITTED/WORKING/COMPLETED/FAILED/CANCELLED) and result data."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "task_id": {
                    "type": "string",
                    "description": "The task ID returned from a2a_send_task in async mode",
                },
            },
            "required": ["task_id"],
        },
    },
    {
        "name": "a2a_health_check",
        "description": (
            "Check the health of the EventMesh A2A Gateway. "
            "Returns gateway status, task count, and registered agent count."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": [],
        },
    },
    {
        "name": "a2a_get_agent_card",
        "description": (
            "Get detailed information about a specific agent, including its skills, "
            "interfaces, and capabilities."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "agent_name": {
                    "type": "string",
                    "description": "Agent name in 'org/unit/agent' format (e.g. 'default/default/hermes-assistant')",
                },
                "org_id": {
                    "type": "string",
                    "description": "Organization ID (default: 'default')",
                },
                "unit_id": {
                    "type": "string",
                    "description": "Unit ID (default: 'default')",
                },
            },
            "required": ["agent_name"],
        },
    },
]


# =========================================================================
# Tool Handlers
# =========================================================================

def handle_list_agents(args: dict) -> dict:
    result = a2a_get("/a2a/agents")
    if isinstance(result, dict) and "error" in result:
        return result
    if isinstance(result, list):
        agents_summary = []
        for agent in result:
            agents_summary.append({
                "name": agent.get("name", agent.get("id", "?")),
                "status": agent.get("status", "unknown"),
                "version": agent.get("version", ""),
                "description": agent.get("description", ""),
            })
        return {
            "agent_count": len(agents_summary),
            "agents": agents_summary,
        }
    return {"agents": [], "error": f"Unexpected response: {result}"}


def handle_send_task(args: dict) -> dict:
    target = args["target_agent"]
    message = args["message"]
    mode = args.get("mode", "sync")

    url = f"/a2a/tasks?mode={mode}"
    body = {"targetAgent": target, "message": message}

    result = a2a_post(url, body)
    if isinstance(result, dict) and "error" in result:
        return result

    return {
        "task_id": result.get("taskId", ""),
        "state": result.get("state", "SUBMITTED"),
        "data": result.get("data", result.get("result")),
        "mode": mode,
    }


def handle_get_task_status(args: dict) -> dict:
    task_id = args["task_id"]
    result = a2a_get(f"/a2a/tasks/{task_id}")
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "task_id": result.get("taskId", task_id),
        "state": result.get("state", "UNKNOWN"),
        "data": result.get("data", result.get("result")),
        "error": result.get("error"),
        "target_agent": result.get("targetAgent", ""),
    }


def handle_health_check(args: dict) -> dict:
    result = a2a_get("/a2a/health")
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "gateway_status": result.get("status", "UNKNOWN"),
        "gateway_id": result.get("gatewayId", ""),
        "namespace": result.get("namespace", ""),
        "task_count": result.get("taskCount", 0),
        "agent_count": result.get("agentCount", 0),
    }


def handle_get_agent_card(args: dict) -> dict:
    agent_name = args["agent_name"]
    org_id = args.get("org_id", "default")
    unit_id = args.get("unit_id", "default")

    # Parse agent_name if in 'org/unit/agent' format
    parts = agent_name.split("/")
    if len(parts) >= 3:
        org_id, unit_id, agent_id = parts[0], parts[1], parts[2]
    else:
        agent_id = agent_name

    result = a2a_get(f"/a2a/cards/card/{org_id}/{unit_id}/{agent_id}")
    return result


# Tool dispatch table
HANDLERS = {
    "a2a_list_agents": handle_list_agents,
    "a2a_send_task": handle_send_task,
    "a2a_get_task_status": handle_get_task_status,
    "a2a_health_check": handle_health_check,
    "a2a_get_agent_card": handle_get_agent_card,
}


# =========================================================================
# MCP Protocol Handlers (JSON-RPC over stdio)
# =========================================================================

def _send_response(response_id: Any, result: Any):
    """Send a success response."""
    _write_json({"jsonrpc": "2.0", "id": response_id, "result": result})


def _send_error(response_id: Any, code: int, message: str):
    """Send an error response."""
    _write_json({
        "jsonrpc": "2.0",
        "id": response_id,
        "error": {"code": code, "message": message},
    })


def _write_json(obj: dict):
    """Write a JSON-RPC message to stdout."""
    sys.stdout.write(json.dumps(obj, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def handle_request(req: dict):
    """Handle a single JSON-RPC request."""
    method = req.get("method", "")
    req_id = req.get("id")
    params = req.get("params", {})

    if method == "initialize":
        return _send_response(req_id, {
            "protocolVersion": "2024-11-05",
            "capabilities": {"tools": {}},
            "serverInfo": {
                "name": "eventmesh-agentmesh-mcp",
                "version": "0.1.0",
            },
        })

    if method == "notifications/initialized":
        # No response needed for notifications
        return

    if method == "tools/list":
        return _send_response(req_id, {"tools": TOOLS})

    if method == "tools/call":
        tool_name = params.get("name", "")
        tool_args = params.get("arguments", {})
        handler = HANDLERS.get(tool_name)
        if handler is None:
            return _send_error(req_id, -32601, f"Unknown tool: {tool_name}")
        try:
            result = handler(tool_args)
            # MCP expects content array
            _send_response(req_id, {
                "content": [
                    {"type": "text", "text": json.dumps(result, ensure_ascii=False, indent=2)}
                ]
            })
        except Exception as e:
            _send_error(req_id, -32000, f"Tool error: {e}")
        return

    if method == "ping":
        return _send_response(req_id, {})

    # Unknown method
    _send_error(req_id, -32601, f"Method not found: {method}")


def main():
    """MCP stdio main loop."""
    logger.info("EventMesh AgentMesh MCP Server started. Gateway: %s", A2A_GATEWAY_URL)

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
            handle_request(request)
        except json.JSONDecodeError as e:
            logger.warning("Invalid JSON: %s", line[:100])
        except Exception as e:
            logger.error("Unexpected error: %s", e)


if __name__ == "__main__":
    main()
