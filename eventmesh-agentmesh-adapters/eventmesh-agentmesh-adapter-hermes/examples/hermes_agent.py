"""
Hermes AgentMesh Integration Example.

This example demonstrates how to connect Hermes (Eason's internal AI system)
to EventMesh's A2A Agent Mesh.

Prerequisites:
    1. EventMesh A2A Gateway running on localhost:10105
    2. Python 3.6+ (stdlib only, no dependencies)

Usage:
    python hermes_agent.py
"""

import json
import logging
import sys
import os

# Point to the shared EventMesh Agent SDK
_SDK_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
    "eventmesh-agent-sdk", "python",
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
                "description": "Review code changes, identify bugs, and suggest improvements",
                "tags": ["code", "review", "security"],
            },
            {
                "id": "security-audit",
                "name": "Security Audit",
                "description": "Audit MCP servers, skills, and configurations for security risks",
                "tags": ["security", "audit", "mcp"],
            },
            {
                "id": "infrastructure-ops",
                "name": "Infrastructure Operations",
                "description": "Manage middleware infrastructure, deployments, and monitoring",
                "tags": ["infrastructure", "ops", "monitoring"],
            },
            {
                "id": "general-chat",
                "name": "General AI Chat",
                "description": "General-purpose AI conversation and task assistance",
                "tags": ["chat", "ai", "assistant"],
            },
        ],
        "documentation_url": "https://github.com/qqeasonchen/hermes",
    }


def handle_incoming_task(task_id: str, message: str) -> str:
    """Handle incoming A2A task requests from other agents.

    In production, this would route to Hermes' internal AI processing.
    """
    logger.info("Received task: %s -> %s", task_id, message[:100])

    # Parse the request
    try:
        request = json.loads(message) if message.startswith("{") else {"text": message}
    except json.JSONDecodeError:
        request = {"text": message}

    # Route based on skill
    skill = request.get("skill", "general-chat")
    prompt = request.get("text", "")

    # In production, this calls Hermes' AI model
    response = f"[Hermes] Processed '{skill}' task: {prompt[:50]}..."
    logger.info("Task completed: %s", task_id)
    return response


def main():
    # Connect to A2A Gateway
    client = AgentMeshClient(
        gateway_url=os.environ.get("A2A_GATEWAY_URL", "http://localhost:10105"),
        agent_name=os.environ.get("A2A_AGENT_NAME", "default/default/hermes-assistant"),
        agent_card=create_hermes_agent_card(),
        heartbeat_interval=30,
    )

    # Set handler for incoming tasks from other agents
    client.set_request_handler(handle_incoming_task)

    # Start: register card + heartbeat
    logger.info("Starting Hermes AgentMesh client...")
    client.start()

    try:
        # Check gateway health
        health = client.health_check()
        logger.info("Gateway health: %s", json.dumps(health, indent=2))

        # List all registered agents
        agents = client.list_agents()
        logger.info("Registered agents: %d", len(agents))
        for agent in agents:
            logger.info("  - %s (status: %s)", agent.get("name", "?"), agent.get("status", "?"))

        # Example: send a task to the weather agent
        logger.info("Sending task to weather-agent...")
        result = client.send_task("weather-agent", "Shenzhen")
        logger.info("Result: %s", result)

        # Example: async task submission
        task_id = client.send_task_async("weather-agent", "Beijing")
        logger.info("Async task submitted: %s", task_id)

        # Poll for result
        import time
        for _ in range(10):
            status = client.get_task_status(task_id)
            if status.is_terminal():
                logger.info("Async result: %s", status)
                break
            time.sleep(1)

        # Keep alive to receive incoming requests
        logger.info("Hermes agent is running. Press Ctrl+C to stop.")
        import threading
        threading.Event().wait()

    except KeyboardInterrupt:
        logger.info("Shutting down...")
    finally:
        client.stop()


if __name__ == "__main__":
    main()
