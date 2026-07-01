# EventMesh AgentMesh — OpenClaw Adapter

OpenClaw 多智能体编排系统接入 EventMesh A2A AgentMesh 的适配器，基于 `eventmesh-agent-sdk`（Go）。

## 依赖

- [eventmesh-agent-sdk](../../eventmesh-agent-sdk/) → `go/pkg/eventmesh_agent/` — Go A2A 客户端库

## 工作原理

```
┌──────────────┐   HTTP/REST + Heartbeat   ┌──────────────────┐
│  OpenClaw    │ ────────────────────────→ │  EventMesh A2A   │
│  Adapter     │ ←── task response/SSE ─── │  Gateway :10105  │
│  (Go)        │                           └──────────────────┘
└──────────────┘
       │
       │ import
       ▼
┌──────────────┐
│ eventmesh-   │  Go A2A Client SDK
│ agent-sdk/go │  (eventmesh_agent.NewClient)
└──────────────┘
```

## 快速开始

```bash
# 编译（go.mod 已配置 replace 指向 SDK）
cd eventmesh-agentmesh-adapters/eventmesh-agentmesh-adapter-openclaw/
go build -o openclaw-adapter .

# 运行（确保 A2A Gateway 已启动）
A2A_GATEWAY_URL=http://localhost:10105 ./openclaw-adapter
```

## 作为库使用

```go
package main

import (
    "github.com/qqeasonchen/eventmesh/eventmesh-agent-sdk/go/pkg/eventmesh_agent"
)

func main() {
    client := eventmesh_agent.NewClient(eventmesh_agent.Config{
        GatewayURL: "http://localhost:10105",
        AgentName:  "default/default/openclaw-agent",
        AgentCard: &eventmesh_agent.AgentCard{
            Name:        "openclaw-agent",
            Description: "OpenClaw multi-agent system",
            Version:     "1.0.0",
        },
    })
    client.Start()
    defer client.Stop()

    result, _ := client.SendTask("weather-agent", "Shenzhen")
    println(result.Data)
}
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `A2A_GATEWAY_URL` | `http://localhost:10105` | A2A Gateway 地址 |
| `A2A_AGENT_NAME` | `default/default/openclaw-agent` | Agent 注册名 |
