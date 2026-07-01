# EventMesh AgentMesh Adapters

将外部 Agent 框架接入 Apache EventMesh A2A AgentMesh 的适配器集合。

所有适配器依赖共享的 **[eventmesh-agent-sdk](../eventmesh-agent-sdk/)** — 多语言 A2A 客户端抽象层。

## 架构总览

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        EventMesh A2A Agent Mesh                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    A2A Gateway (:10105)                                 │ │
│  │  Agent Registry / Task Router / SSE Streaming / Heartbeat              │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                    CloudEvents + Topic Routing                               │
└──────────────────────────────────────────────────────────────────────────────┘
                  │                          │                          │
                  ▼                          ▼                          ▼
    ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
    │ eventmesh-agent- │    │ eventmesh-agent- │    │ eventmesh-agent- │
    │ sdk/python       │    │ sdk/python/      │    │ sdk/go           │
    │ (eventmesh_agent)│    │ integrations/mcp │    │ (eventmesh_agent)│
    └────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘
             │ import                │ import                 │ import
    ┌────────▼─────────┐    ┌────────▼─────────┐    ┌────────▼─────────┐
    │  Hermes Adapter  │    │ Claude Code      │    │ OpenClaw Adapter │
    │  · hermes_agent  │    │ Adapter          │    │ · main.go        │
    │  · 4技能注册      │    │ · MCP config     │    │ · 多Agent编排    │
    └────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘
             │                       │                       │
    ┌────────▼─────────┐    ┌────────▼─────────┐    ┌────────▼─────────┐
    │ Hermes AI        │    │ Claude Desktop   │    │ OpenClaw         │
    │ (Eason's)        │    │ / Code CLI       │    │ Agent System     │
    └──────────────────┘    └──────────────────┘    └──────────────────┘
```

## 适配器列表

| 适配器 | 语言 | SDK 层 | 职责 |
|--------|------|--------|------|
| **Hermes** | Python | `eventmesh_agent` | 注册 Hermes 技能卡，连接 A2A Gateway |
| **Claude Code** | Python (MCP) | `integrations/mcp` | MCP stdio bridge → A2A REST |
| **OpenClaw** | Go | `go/pkg/eventmesh_agent` | OpenClaw 多 Agent 编排接入 |

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

### 3. 使用

```python
# Hermes — 直接 import SDK
from eventmesh_agent import AgentMeshClient
client = AgentMeshClient(
    agent_name="default/default/hermes-assistant",
    agent_card={"name": "hermes", "skills": [...]}
)
client.start()
result = client.send_task("weather-agent", "Shenzhen")
```

```json
// Claude Code — MCP 配置指向 SDK 中的 bridge
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

```go
// OpenClaw — import SDK Go 包
import "github.com/qqeasonchen/eventmesh/eventmesh-agent-sdk/go/pkg/eventmesh_agent"

client := eventmesh_agent.NewClient(eventmesh_agent.Config{
    GatewayURL: "http://localhost:10105",
    AgentName:  "default/default/openclaw-agent",
})
client.Start()
```

## 目录结构

```
eventmesh-agentmesh-adapters/
├── README.md                                     ← 本文档
├── eventmesh-agentmesh-adapter-hermes/            ← Hermes 适配器
│   └── examples/hermes_agent.py                  ← 示例 Agent（import SDK）
├── eventmesh-agentmesh-adapter-claude-code/       ← Claude Code 适配器
│   └── claude_desktop_config.json                ← MCP 配置（指向 SDK bridge）
└── eventmesh-agentmesh-adapter-openclaw/          ← OpenClaw 适配器
    ├── go.mod                                     ← replace → SDK
    └── main.go                                    ← 独立二进制（import SDK）
```

SDK 代码在 **[../eventmesh-agent-sdk/](../eventmesh-agent-sdk/)**：

```
eventmesh-agent-sdk/
├── python/eventmesh_agent/    ← A2A Client SDK（AgentMeshClient）
├── python/integrations/mcp/   ← MCP Bridge Server
├── go/pkg/eventmesh_agent/    ← Go A2A Client SDK
└── java/                      ← A2AAgentToolBridge 参考
```
