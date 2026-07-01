"""
Type definitions for the EventMesh A2A protocol.
"""

try:
    from dataclasses import dataclass
except ImportError:
    # Python 3.6 fallback
    pass

import json
from typing import Optional, Dict, Any, List


class TaskState:
    SUBMITTED = "SUBMITTED"
    WORKING = "WORKING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class TaskResult:
    """Result of an A2A task submission."""

    def __init__(self, task_id: str, state: str, data: Any = None,
                 error: Optional[str] = None):
        self.task_id = task_id
        self.state = state
        self.data = data
        self.error = error

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "TaskResult":
        return cls(
            task_id=d.get("taskId", ""),
            state=d.get("state", d.get("status", "UNKNOWN")),
            data=d.get("data", d.get("result")),
            error=d.get("error"),
        )

    def is_terminal(self) -> bool:
        return self.state in (
            TaskState.COMPLETED,
            TaskState.FAILED,
            TaskState.CANCELLED,
        )

    def __repr__(self) -> str:
        return f"TaskResult(task_id={self.task_id}, state={self.state}, data={self.data!r})"
