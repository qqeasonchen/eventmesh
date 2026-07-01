# EventMesh AgentMesh — Hermes Adapter

Hermes AI 系统接入 EventMesh A2A AgentMesh 的适配器，基于 `eventmesh-agent-sdks`（Python）。

## 依赖

- [eventmesh-agent-sdks](../../eventmesh-agent-sdks/) — 共享 A2A 客户端抽象层

## 快速开始

```python
import sys, os
sys.path.insert(0, "../../eventmesh-agent-sdks/python")

from eventmesh_agent import AgentMeshClient

client = AgentMeshClient(
    gateway_url="http://localhost:10105",
    agent_name="default/default/hermes-assistant",
    agent_card={
        "name": "hermes-assistant",
        "description": "Hermes AI Assistant",
        "version": "1.0.0",
        "skills": [
            {"id": "code-review", "name": "Code Review", "description": "Review code changes"},
            {"id": "security-audit", "name": "Security Audit", "description": "Security checks"},
        ]
    }
)
client.start()

# 向其他 Agent 发送任务
result = client.send_task("weather-agent", "Shenzhen")
print(result.data)

client.stop()
```

## 运行示例

```bash
# 确保 EventMesh A2A Gateway 已启动（端口 10105）
python examples/hermes_agent.py
```

## 架构

```
┌──────────────┐                     ┌──────────────────┐
│ eventmesh-   │  import             │  Hermes Adapter  │
│ agent-sdk    │ ◄─────────────────  │  (hermes_agent)  │
│ (Python)     │                     └────────┬─────────┘
└──────┬───────┘                              │
       │ HTTP/REST                    定义 Hermes 技能卡
       ▼                              (code-review / security-audit
┌──────────────────┐                    infrastructure-ops / general-chat)
│  EventMesh A2A   │
│  Gateway :10105  │
└──────────────────┘
```
