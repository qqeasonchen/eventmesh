"""
Agent Card and registration models for the A2A protocol.
"""

import json
from typing import Optional, Dict, Any, List


class AgentSkill:
    """A skill/capability offered by an agent."""

    def __init__(self, id: str, name: str, description: str = "",
                 tags: Optional[List[str]] = None,
                 examples: Optional[List[str]] = None):
        self.id = id
        self.name = name
        self.description = description
        self.tags = tags or []
        self.examples = examples or []

    def to_dict(self) -> Dict[str, Any]:
        d = {
            "id": self.id,
            "name": self.name,
            "description": self.description,
        }
        if self.tags:
            d["tags"] = self.tags
        if self.examples:
            d["examples"] = self.examples
        return d


class AgentInterface:
    """Network interface for agent communication."""

    def __init__(self, url: str, protocol_binding: str = "JSONRPC",
                 protocol_version: str = "0.3"):
        self.url = url
        self.protocol_binding = protocol_binding
        self.protocol_version = protocol_version

    def to_dict(self) -> Dict[str, Any]:
        return {
            "url": self.url,
            "protocolBinding": self.protocol_binding,
            "protocolVersion": self.protocol_version,
        }


class AgentCapabilities:
    """Agent capabilities flags."""

    def __init__(self, streaming: bool = False, push_notifications: bool = False,
                 state_transition_history: bool = False):
        self.streaming = streaming
        self.push_notifications = push_notifications
        self.state_transition_history = state_transition_history

    def to_dict(self) -> Dict[str, Any]:
        return {
            "streaming": self.streaming,
            "pushNotifications": self.push_notifications,
            "stateTransitionHistory": self.state_transition_history,
        }


class AgentCard:
    """A2A Agent Card — declares the agent's identity, skills, and interface."""

    def __init__(self, name: str, description: str = "", version: str = "1.0.0",
                 skills: Optional[List[AgentSkill]] = None,
                 interfaces: Optional[List[AgentInterface]] = None,
                 capabilities: Optional[AgentCapabilities] = None,
                 default_input_modes: Optional[List[str]] = None,
                 default_output_modes: Optional[List[str]] = None,
                 documentation_url: str = "",
                 icon_url: str = ""):
        self.name = name
        self.description = description
        self.version = version
        self.skills = skills or []
        self.interfaces = interfaces or []
        self.capabilities = capabilities or AgentCapabilities()
        self.default_input_modes = default_input_modes or ["text/plain"]
        self.default_output_modes = default_output_modes or ["text/plain"]
        self.documentation_url = documentation_url
        self.icon_url = icon_url

    def to_dict(self) -> Dict[str, Any]:
        d = {
            "name": self.name,
            "description": self.description,
            "version": self.version,
            "skills": [s.to_dict() for s in self.skills],
            "supportedInterfaces": [i.to_dict() for i in self.interfaces],
            "capabilities": self.capabilities.to_dict(),
            "defaultInputModes": self.default_input_modes,
            "defaultOutputModes": self.default_output_modes,
        }
        if self.documentation_url:
            d["documentationUrl"] = self.documentation_url
        if self.icon_url:
            d["iconUrl"] = self.icon_url
        return d

    @classmethod
    def from_simple_dict(cls, d: Dict[str, Any]) -> "AgentCard":
        """Create from a simplified dict (user-friendly format)."""
        skills = [
            AgentSkill(
                id=s.get("id", s.get("name", "")),
                name=s.get("name", ""),
                description=s.get("description", ""),
                tags=s.get("tags", []),
            )
            for s in d.get("skills", [])
        ]
        return cls(
            name=d.get("name", ""),
            description=d.get("description", ""),
            version=d.get("version", "1.0.0"),
            skills=skills,
            documentation_url=d.get("documentation_url", ""),
            icon_url=d.get("icon_url", ""),
        )


class AgentRegistration:
    """Registration payload for an agent."""

    def __init__(self, org_id: str = "default", unit_id: str = "default",
                 agent_id: str = ""):
        self.org_id = org_id
        self.unit_id = unit_id
        self.agent_id = agent_id

    @classmethod
    def from_name(cls, name: str) -> "AgentRegistration":
        """Parse agent name in format 'org/unit/agent' or 'agent'."""
        parts = name.split("/")
        if len(parts) >= 3:
            return cls(org_id=parts[0], unit_id=parts[1], agent_id=parts[2])
        elif len(parts) == 2:
            return cls(org_id=parts[0], unit_id=parts[1], agent_id=name)
        else:
            return cls(agent_id=name)

    def to_heartbeat_dict(self) -> Dict[str, str]:
        return {
            "orgId": self.org_id,
            "unitId": self.unit_id,
            "agentId": self.agent_id,
        }
