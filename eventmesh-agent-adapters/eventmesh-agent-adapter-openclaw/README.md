# EventMesh Agent — OpenClaw Adapter (Skill)

OpenClaw 多智能体编排系统接入 EventMesh A2A AgentMesh 的适配器 Skill，基于 `eventmesh-agent-sdks`（Go）。

**运行即接入**，4 个技能自动注册：multi-agent-orchestration / task-decomposition / agent-routing / workflow-execution。

## 依赖

- [eventmesh-agent-sdks](../../eventmesh-agent-sdks/) → `go/pkg/eventmesh_agent/` — Go A2A 客户端库

## 快速开始

```bash
# 一键启动（自动注册 + 心跳 + 任务收发）
cd eventmesh-agent-adapters/eventmesh-agent-adapter-openclaw/
go run .

# 自定义 Gateway
A2A_GATEWAY_URL=http://localhost:10105 go run .
```

## 作为库使用

```go
package main

import (
    "github.com/qqeasonchen/eventmesh/eventmesh-agent-sdks/go/pkg/eventmesh_agent"
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
