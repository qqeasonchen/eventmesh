# EventMesh AgentMesh — Hermes Adapter

Hermes AI 系统接入 Apache EventMesh AgentMesh (A2A 协议) 的 Python SDK。

## 特性

- **零外部依赖** — 纯 Python 标准库 (urllib + json + threading)
- **完整 A2A 协议支持** — AgentCard 注册、心跳、任务提交/查询/取消/流式
- **开箱即用** — 一行代码注册 Hermes 为 AgentMesh 中的 Agent

## 安装

```bash
# 从源码安装
cd eventmesh-agentmesh-adapters/eventmesh-agentmesh-adapter-hermes/
pip install -e .

# 或直接导入（零安装）
export PYTHONPATH=$PYTHONPATH:$(pwd)
```

## 快速开始

```python
from eventmesh_agentmesh import AgentMeshClient

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

# 异步任务
task_id = client.send_task_async("other-agent", '{"skill":"chat","text":"hello"}')
status = client.get_task_status(task_id)

# 发现其他 Agent
agents = client.list_agents()
for agent in agents:
    print(f"  {agent['name']} — {agent['status']}")

client.stop()
```

## 架构

```
┌──────────────┐     HTTP/REST      ┌──────────────────┐     CloudEvents      ┌──────────────┐
│   Hermes     │ ──── AgentCard ───→│  EventMesh A2A   │ ──── pub/sub ──────→│  Other       │
│   (Python)   │ ←── task/response──│  Gateway :10105   │ ←── task/response──│  Agents      │
└──────────────┘                    └──────────────────┘                    └──────────────┘
```

## 运行示例

```bash
# 确保 EventMesh A2A Gateway 已启动（端口 10105）
python examples/hermes_agent.py
```
