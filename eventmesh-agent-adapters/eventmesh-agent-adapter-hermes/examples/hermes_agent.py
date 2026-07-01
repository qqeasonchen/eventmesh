"""
Hermes AgentMesh Integration Example.

Connects Hermes (Eason's internal AI system) to EventMesh's A2A Agent Mesh.
Demonstrates: registration, heartbeat, task send/receive, agent discovery.

Prerequisites:
    1. EventMesh A2A Gateway running on localhost:10105
    2. Python 3.6+ (stdlib only, no dependencies)

Usage:
    python hermes_agent.py

Environment:
    A2A_GATEWAY_URL  — Gateway URL (default: http://localhost:10105)
    A2A_AGENT_NAME   — Agent identity (default: default/default/hermes-assistant)
"""

import json
import logging
import os
import signal
import sys
import time

# Point to the shared EventMesh Agent SDK
_SDK_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
    "eventmesh-agent-sdks", "python",
)
sys.path.insert(0, _SDK_PATH)

from eventmesh_agent import AgentMeshClient, TaskResult, TaskState

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("hermes-agent")


def create_hermes_agent_card() -> dict:
    """Define Hermes' capabilities as an A2A Agent Card."""
    return {
        "name": "hermes-assistant",
        "description": (
            "Hermes AI Assistant — Eason's internal AI system for code review, "
            "security audit, MCP/Skill management, and infrastructure operations."
        ),
        "version": "1.0.0",
        "skills": [
            {
                "id": "code-review",
                "name": "Code Review",
                "description": "Review code changes, identify bugs, suggest improvements",
                "tags": ["code", "review", "security"],
                "inputModes": ["text/plain", "application/json"],
                "outputModes": ["text/plain", "application/json"],
            },
            {
                "id": "security-audit",
                "name": "Security Audit",
                "description": "Audit MCP servers, skills, and configurations for security risks",
                "tags": ["security", "audit", "mcp"],
                "inputModes": ["text/plain", "application/json"],
                "outputModes": ["text/plain", "application/json"],
            },
            {
                "id": "infrastructure-ops",
                "name": "Infrastructure Operations",
                "description": "Manage middleware infrastructure, deployments, monitoring",
                "tags": ["infrastructure", "ops", "monitoring"],
                "inputModes": ["text/plain", "application/json"],
                "outputModes": ["text/plain", "application/json"],
            },
            {
                "id": "general-chat",
                "name": "General AI Chat",
                "description": "General-purpose AI conversation and task assistance",
                "tags": ["chat", "ai", "assistant"],
                "inputModes": ["text/plain"],
                "outputModes": ["text/plain"],
            },
        ],
        "documentation_url": "https://github.com/qqeasonchen/hermes",
        "defaultInputModes": ["text/plain", "application/json"],
        "defaultOutputModes": ["text/plain", "application/json"],
    }


def handle_incoming_task(task_id: str, message: str) -> str:
    """Handle incoming A2A task requests from other agents.

    In production, this would route to Hermes' internal AI processing pipeline.
    """
    logger.info("Received task: %s -> msg=%s", task_id, message[:120])

    try:
        request = json.loads(message) if message.startswith("{") else {"text": message}
    except json.JSONDecodeError:
        request = {"text": message}

    skill = request.get("skill", "general-chat")
    prompt = request.get("text", message)

    # Route by skill (in production, call Hermes AI model)
    handlers = {
        "code-review": lambda: f"[Hermes] Code review complete for: {prompt[:60]}",
        "security-audit": lambda: f"[Hermes] Security audit passed for: {prompt[:60]}",
        "infrastructure-ops": lambda: f"[Hermes] Infrastructure operation executed: {prompt[:60]}",
    }
    handler = handlers.get(skill, lambda: f"[Hermes] Chat response to: {prompt[:60]}")
    response = handler()

    logger.info("Task completed: %s (skill=%s)", task_id, skill)
    return response


def main():
    gateway_url = os.environ.get("A2A_GATEWAY_URL", "http://localhost:10105")
    agent_name = os.environ.get("A2A_AGENT_NAME", "default/default/hermes-assistant")

    logger.info("Hermes AgentMesh Adapter v1.0.0")
    logger.info("  Gateway: %s", gateway_url)
    logger.info("  Agent:   %s", agent_name)

    # --- Phase 1: Connect ---
    client = AgentMeshClient(
        gateway_url=gateway_url,
        agent_name=agent_name,
        agent_card=create_hermes_agent_card(),
        heartbeat_interval=30,
    )
    client.set_request_handler(handle_incoming_task)

    try:
        client.start()
    except Exception as e:
        logger.error("Failed to start: %s", e)
        sys.exit(1)

    # Graceful shutdown
    shutdown = [False]

    def on_signal(sig, frame):
        logger.info("Received signal %s, shutting down...", sig)
        shutdown[0] = True

    signal.signal(signal.SIGINT, on_signal)
    signal.signal(signal.SIGTERM, on_signal)

    # --- Phase 2: Discovery ---
    try:
        health = client.health_check()
        logger.info("Gateway health: %s", json.dumps(health, indent=2))
    except Exception as e:
        logger.warning("Health check failed (gateway may not be running): %s", e)
        client.stop()
        sys.exit(1)

    try:
        agents = client.list_agents()
        logger.info("Registered agents (%d):", len(agents))
        for a in agents:
            logger.info("  - %-30s status=%-10s skills=%d",
                        a.get("name", "?"), a.get("status", "?"),
                        len(a.get("skills", [])))
    except Exception as e:
        logger.warning("List agents failed: %s", e)

    # --- Phase 3: Demonstrate task patterns ---
    # Sync task
    logger.info("--- Sync task demo ---")
    try:
        result = client.send_task("weather-agent", json.dumps({
            "skill": "weather",
            "text": "What's the weather in Shenzhen?",
        }))
        logger.info("Sync result: state=%s data=%s", result.state, result.data[:100] if result.data else "(empty)")
    except Exception as e:
        logger.warning("Sync task failed: %s", e)

    # Async + wait
    logger.info("--- Async task demo ---")
    try:
        task_id = client.send_task_async("weather-agent", "Beijing weather?")
        logger.info("Async task submitted: %s", task_id)
        result = client.wait_for_task(task_id, poll_interval=2, max_wait=30)
        logger.info("Async result: state=%s data=%s", result.state, result.data[:100] if result.data else "(empty)")
    except TimeoutError:
        logger.warning("Async task timed out (gateway may not have a real handler)")
    except Exception as e:
        logger.warning("Async task failed: %s", e)

    # SSE stream
    logger.info("--- SSE stream demo ---")
    try:
        task_id = client.send_task_async("analyst", "Analyze market trends")
        logger.info("Streaming task: %s", task_id)

        def on_event(tid, state, data):
            logger.info("  SSE: %s -> %s", tid, state)
            # Stop when done
            return state not in ("COMPLETED", "FAILED", "CANCELLED")

        client.stream_task(task_id, on_event, timeout=15)
    except Exception as e:
        logger.warning("SSE stream failed: %s", e)

    # --- Phase 4: Running ---
    logger.info("=" * 60)
    logger.info("Hermes agent is running. Press Ctrl+C to stop.")
    logger.info("Listening for incoming A2A tasks from other agents...")
    logger.info("=" * 60)

    while not shutdown[0]:
        time.sleep(1)

    client.stop()
    logger.info("Hermes agent stopped.")


if __name__ == "__main__":
    main()
