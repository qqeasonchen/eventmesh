# EventMesh Agent SDK for Go

```go
import "github.com/qqeasonchen/eventmesh/eventmesh-agent-sdk/go/pkg/eventmesh_agent"
```

## Quick Start

```go
package main

import (
    "github.com/qqeasonchen/eventmesh/eventmesh-agent-sdk/go/pkg/eventmesh_agent"
)

func main() {
    cfg := eventmesh.DefaultConfig()
    cfg.GatewayURL = "http://localhost:10105"
    cfg.AgentName = "default/default/my-agent"
    cfg.AgentCard = &eventmesh.AgentCard{
        Name:        "my-agent",
        Description: "My Go Agent",
        Version:     "1.0.0",
        Skills: []eventmesh.AgentSkill{
            {ID: "chat", Name: "Chat"},
        },
    }

    client := eventmesh.NewClient(cfg)
    client.Start()
    defer client.Stop()

    // Sync task
    result, err := client.SendTask("weather-agent", "Shenzhen?", 30)
    if err == nil {
        fmt.Printf("Result: %v\n", result.Data)
    }

    // Async task
    taskID, err := client.SendTaskAsync("analyst", "Analyze AAPL", "")
    if err == nil {
        for i := 0; i < 10; i++ {
            status, _ := client.GetTaskStatus(taskID)
            if status.IsTerminal() {
                break
            }
            time.Sleep(1 * time.Second)
        }
    }

    // Discovery
    agents, _ := client.ListAgents()
    health, _ := client.HealthCheck()
}
```

## API

| 方法 | 返回 | 说明 |
|---|---|---|
| `Start()` | `error` | 注册 AgentCard，启动心跳 |
| `Stop()` | `error` | 停止心跳 |
| `SendTask(target, msg, timeout)` | `(*TaskResult, error)` | 同步任务 |
| `SendTaskAsync(target, msg, parentID)` | `(string, error)` | 异步任务 |
| `GetTaskStatus(taskID)` | `(*TaskResult, error)` | 查询任务状态 |
| `CancelTask(taskID)` | `error` | 取消任务 |
| `ListAgents()` | `([]map[string]any, error)` | 发现 Agent |
| `HealthCheck()` | `(map[string]any, error)` | 健康检查 |

## OpenClaw Adapter

```bash
cd eventmesh-agent-sdk/go/cmd/openclaw
go build -o openclaw-adapter .
A2A_GATEWAY_URL=http://localhost:10105 ./openclaw-adapter
```
