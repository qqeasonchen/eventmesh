# EventMesh AgentMesh Adapters

将外部 Agent 框架接入 Apache EventMesh A2A AgentMesh 的适配器集合。

## 架构总览

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        EventMesh A2A Agent Mesh                              │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    A2A Gateway (:10105)                                 │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐   │ │
│  │  │ Agent    │  │ Task     │  │ SSE          │  │ Heartbeat/       │   │ │
│  │  │ Registry │  │ Router   │  │ Streaming    │  │ Health           │   │ │
│  │  └──────────┘  └──────────┘  └──────────────┘  └──────────────────┘   │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    CloudEvents + Topic Routing                          │ │
│  │  a2a/v1/{ns}/agents/{agent}/request ←── task submission                │ │
│  │  a2a/v1/{ns}/gateways/{gw}/response/{tid} ←── response routing         │ │
│  │  a2a/v1/{ns}/gateways/{gw}/status/{tid} ←── status updates             │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
            ┌───────────────────────┼───────────────────────┐
            │                       │                       │
            ▼                       ▼                       ▼
    ┌───────────────┐     ┌───────────────┐     ┌───────────────┐
    │ Hermes        │     │ Claude Code   │     │ OpenClaw      │
    │ Adapter       │     │ Adapter       │     │ Adapter       │
    │ (Python)      │     │ (MCP Bridge)  │     │ (Go)          │
    │               │     │               │     │               │
    │ · A2A Client  │     │ · MCP Server  │     │ · A2A Client  │
    │ · REST API    │     │ · stdio JSON- │     │ · net/http     │
    │ · urllib only │     │   RPC         │     │ · Heartbeat   │
    └───────┬───────┘     └───────┬───────┘     └───────┬───────┘
            │                       │                       │
            ▼                       ▼                       ▼
    ┌───────────────┐     ┌───────────────┐     ┌───────────────┐
    │ Hermes        │     │ Claude Desktop│     │ OpenClaw      │
    │ AI System     │     │ / Code CLI    │     │ Agent System  │
    │ (Eason's)     │     │ (Anthropic)   │     │ (Open Source)  │
    └───────────────┘     └───────────────┘     └───────────────┘
```

## 适配器列表

| 适配器 | 语言 | 协议 | 依赖 | 状态 |
|--------|------|------|------|------|
| **Hermes** | Python 3.6+ | HTTP REST (A2A) | 零外部依赖 | ✅ |
| **Claude Code** | Python 3.6+ | MCP (stdio JSON-RPC) → A2A REST | 零外部依赖 | ✅ |
| **OpenClaw** | Go 1.21+ | HTTP REST (A2A) | net/http only | ✅ |

## 接入流程

### 1. 启动 EventMesh A2A Gateway

```bash
cd eventmesh-runtime
# Gateway 默认端口 10105
```

### 2. 注册 Agent

每个外部 Agent 需要：
1. 定义 AgentCard（名称、描述、技能、接口）
2. `POST /a2a/cards/card/{org}/{unit}/{agent}` 注册
3. 每 30s `POST /a2a/heartbeat` 发送心跳
4. 通过 `/a2a/*` 端点收发任务

### 3. 使用适配器

```python
# Hermes
from eventmesh_agentmesh import AgentMeshClient
client = AgentMeshClient(
    agent_name="default/default/hermes-assistant",
    agent_card={"name": "hermes", "skills": [...]}
)
client.start()
result = client.send_task("weather-agent", "Shenzhen")
```

```json
// Claude Code (~/.claude.json)
{
  "mcpServers": {
    "eventmesh-agentmesh": {
      "command": "python3",
      "args": ["claude_code_mcp_server.py"],
      "env": { "A2A_GATEWAY_URL": "http://localhost:10105" }
    }
  }
}
```

```go
// OpenClaw
client := agentmesh.NewClient(agentmesh.Config{
    GatewayURL: "http://localhost:10105",
    AgentName:  "default/default/openclaw-agent",
})
client.Start()
result, _ := client.SendTask("weather-agent", "Shenzhen")
```

## 目录结构

```
eventmesh-agentmesh-adapters/
├── README.md
├── eventmesh-agentmesh-adapter-hermes/
│   ├── setup.py
│   ├── README.md
│   ├── eventmesh_agentmesh/
│   │   ├── __init__.py
│   │   ├── client.py          # AgentMeshClient
│   │   ├── agent.py           # AgentCard, AgentSkill models
│   │   └── types.py           # TaskResult, TaskState
│   └── examples/
│       └── hermes_agent.py    # Full example
├── eventmesh-agentmesh-adapter-claude-code/
│   ├── README.md
│   ├── claude_code_mcp_server.py  # MCP stdio bridge
│   └── claude_desktop_config.json # Claude Desktop config
└── eventmesh-agentmesh-adapter-openclaw/
    ├── go.mod
    ├── README.md
    ├── main.go                    # Standalone adapter binary
    └── client/
        └── client.go              # Go A2A library
```
