# EventMesh AgentMesh — OpenClaw Adapter

OpenClaw 多智能体编排系统接入 Apache EventMesh A2A AgentMesh 的 Go 适配器。

## 工作原理

```
┌──────────────┐   HTTP/REST + Heartbeat   ┌──────────────────┐
│  OpenClaw    │ ────────────────────────→ │  EventMesh A2A   │
│  (Go)        │ ←── task response/SSE ─── │  Gateway :10105  │
└──────────────┘                           └──────────────────┘
```

## 快速开始

```bash
# 编译
cd eventmesh-agentmesh-adapters/eventmesh-agentmesh-adapter-openclaw/
go build -o openclaw-adapter .

# 运行（确保 A2A Gateway 已启动）
A2A_GATEWAY_URL=http://localhost:10105 ./openclaw-adapter
```

## 作为库使用

```go
package main

import (
    agentmesh "github.com/qqeasonchen/eventmesh/eventmesh-agentmesh-adapters/eventmesh-agentmesh-adapter-openclaw/client"
)

func main() {
    client := agentmesh.NewClient(agentmesh.Config{
        GatewayURL: "http://localhost:10105",
        AgentName:  "default/default/openclaw-agent",
        AgentCard: &agentmesh.AgentCard{
            Name:        "openclaw-agent",
            Description: "OpenClaw multi-agent system",
            Version:     "1.0.0",
        },
    })
    client.Start()
    defer client.Stop()

    // Send task
    result, _ := client.SendTask("weather-agent", "Shenzhen")
    println(result.Data)

    // List agents
    agents, _ := client.ListAgents()
    for _, a := range agents {
        println(a["name"].(string))
    }
}
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `A2A_GATEWAY_URL` | `http://localhost:10105` | A2A Gateway 地址 |
| `A2A_AGENT_NAME` | `default/default/openclaw-agent` | Agent 注册名 |
