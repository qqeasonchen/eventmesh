# EventMesh AgentMesh — Claude Code Adapter

Claude Code 通过 MCP 协议接入 EventMesh A2A AgentMesh 的适配器，基于 `eventmesh-agent-sdk` 的 MCP Bridge。

## 依赖

- [eventmesh-agent-sdk](../../eventmesh-agent-sdk/) → `python/integrations/mcp/server.py` — MCP stdio bridge

## 工作原理

```
┌──────────────┐   MCP (stdio)    ┌──────────────────┐   HTTP/REST    ┌──────────────────┐
│ Claude Code  │ ←──────────────→ │  MCP Bridge      │ ←───────────→ │  EventMesh A2A   │
│              │  tools/list/call │  (SDK 提供)       │               │  Gateway :10105  │
└──────────────┘                  └──────────────────┘               └──────────────────┘
```

Claude Code 原生支持 MCP 协议。通过 SDK 中的 MCP Bridge Server，Claude Code 可以：

- **发现 Agent** — `a2a_list_agents` 列出 AgentMesh 中所有已注册 Agent
- **发送任务** — `a2a_send_task` 向特定 Agent 委派工作
- **查询状态** — `a2a_get_task_status` 检查异步任务进度
- **健康检查** — `a2a_health_check` 查看 Gateway 状态

## 配置

**Claude Desktop** (`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "eventmesh-agentmesh": {
      "command": "python3",
      "args": [
        "eventmesh-agent-sdk/python/integrations/mcp/server.py"
      ],
      "env": {
        "A2A_GATEWAY_URL": "http://localhost:10105"
      }
    }
  }
}
```

**Claude Code CLI** (`~/.claude.json`):

```json
{
  "mcpServers": {
    "eventmesh-agentmesh": {
      "command": "python3",
      "args": ["eventmesh-agent-sdk/python/integrations/mcp/server.py"],
      "env": { "A2A_GATEWAY_URL": "http://localhost:10105" }
    }
  }
}
```

## 可用工具

| 工具 | 功能 | 参数 |
|------|------|------|
| `a2a_list_agents` | 列出所有已注册 Agent | 无 |
| `a2a_send_task` | 向 Agent 发送任务 | target_agent, message, mode |
| `a2a_get_task_status` | 查询异步任务状态 | task_id |
| `a2a_health_check` | Gateway 健康检查 | 无 |
| `a2a_get_agent_card` | 获取 Agent 详情 | agent_name, org_id, unit_id |

## 使用示例

在 Claude Code 对话中自然使用：

```
> 帮我看看 EventMesh 里有哪些 Agent 可以用
> 让 weather-agent 查一下深圳的天气
> 把这段代码发给 code-review-agent 审查一下
```

Claude Code 会自动调用对应的 MCP Tool。

## 前置条件

1. EventMesh A2A Gateway 运行中（默认端口 10105）
2. Python 3.6+（纯 stdlib，无外部依赖）
3. 目标 Agent 已在 Gateway 注册
