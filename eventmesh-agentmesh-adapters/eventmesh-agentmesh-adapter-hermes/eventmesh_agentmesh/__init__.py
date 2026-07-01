"""
EventMesh AgentMesh Python SDK for Hermes.

Lightweight A2A (Agent-to-Agent) client for connecting Hermes AI system
to the Apache EventMesh Agent Mesh.

Usage:
    from eventmesh_agentmesh import AgentMeshClient

    client = AgentMeshClient(
        gateway_url="http://localhost:10105",
        agent_name="hermes-assistant",
        agent_card={
            "name": "hermes-assistant",
            "description": "Hermes AI Assistant",
            "version": "1.0.0",
            "skills": [
                {"id": "chat", "name": "Chat", "description": "General AI chat"}
            ]
        }
    )
    client.start()
    result = client.send_task("weather-agent", "What's the weather in Shenzhen?")
    print(result)
"""

from .client import AgentMeshClient
from .agent import AgentRegistration, AgentSkill, AgentCard
from .types import TaskResult, TaskState

__all__ = [
    "AgentMeshClient",
    "AgentRegistration",
    "AgentSkill",
    "AgentCard",
    "TaskResult",
    "TaskState",
]
__version__ = "0.1.0"
