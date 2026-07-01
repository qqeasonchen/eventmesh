# EventMesh Agent SDK for Python

```bash
pip install ./eventmesh-agent-sdks/python
```

## Quick Start

```python
from eventmesh_agent import AgentMeshClient

client = AgentMeshClient(
    gateway_url="http://localhost:10105",
    agent_name="my-agent",
    agent_card={
        "name": "my-agent",
        "description": "My AI Agent",
        "version": "1.0.0",
        "skills": [
            {"id": "chat", "name": "Chat", "description": "General AI chat"},
            {"id": "code-review", "name": "Code Review", "description": "Review code"},
        ],
    },
)

client.start()

# Sync task
result = client.send_task("weather-agent", "What's the weather in Shenzhen?")
print(f"State: {result.state}, Data: {result.data}")

# Async task
task_id = client.send_task_async("analyst", "Analyze AAPL stock")
for _ in range(10):
    status = client.get_task_status(task_id)
    if status.is_terminal():
        break
    time.sleep(1)

# SSE stream
client.stream_task(task_id, on_event=lambda tid, state, data: print(f"{tid}: {state}"))

# Discovery
agents = client.list_agents()
health = client.health_check()

client.stop()
```

## API

| 方法 | 返回 | 说明 |
|---|---|---|
| `start()` | None | 注册 AgentCard，启动心跳 |
| `stop()` | None | 停止心跳 |
| `is_started()` | `bool` | 是否已启动 |
| `set_request_handler(fn)` | None | 设置 incoming task 处理器 |
| `send_task(target, msg)` | `TaskResult` | 同步任务，等待完成 |
| `send_task_async(target, msg, parent?)` | `str` (taskId) | 异步任务，支持父子关联 |
| `get_task_status(task_id)` | `TaskResult` | 查询任务状态（404 返回空） |
| `cancel_task(task_id)` | `bool` | 取消任务（404 返回 False） |
| `stream_task(task_id, cb)` | None | SSE 流式任务状态 |
| `wait_for_task(task_id)` | `TaskResult` | 轮询至终态或超时 |
| `list_agents()` | `List[dict]` | 发现所有 Agent |
| `get_agent_card(org, unit, agent)` | `dict` | 获取 AgentCard |
| `health_check()` | `dict` | 网关健康检查 |

## Data Models

- `TaskResult(task_id, state, data)` — `is_terminal()` 判断是否终态
- `TaskState.SUBMITTED / WORKING / COMPLETED / FAILED / CANCELLED`
- `AgentCard(name, skills, interfaces, capabilities)` — `to_dict()` / `from_simple_dict()`
- `AgentRegistration(org_id, unit_id, agent_id)` — `from_name("org/unit/agent")`
- `AgentSkill(id, name, description, tags)`
- `AgentMeshError` — HTTP / 连接异常

## Integrations

见 `integrations/` 目录：

| 集成 | 路径 | 用途 |
|---|---|---|
| MCP Bridge | `integrations/mcp/server.py` | Claude Code / Cursor 接入 |
| LangChain | `integrations/langchain/` | （计划中） |
| AutoGen | `integrations/autogen/` | （计划中） |
