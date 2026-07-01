# EventMesh Agent SDK — Go

EventMesh A2A Agent Mesh 的 Go 客户端 SDK。零外部依赖（stdlib only）。

## 安装

```bash
# 通过 go.mod replace 引用（项目内）
go mod edit -replace github.com/qqeasonchen/eventmesh/eventmesh-agent-sdks/go=../../eventmesh-agent-sdks/go
```

## 快速开始

```go
package main

import (
    "fmt"
    "github.com/qqeasonchen/eventmesh/eventmesh-agent-sdks/go/pkg/eventmesh_agent"
)

func main() {
    cfg := eventmesh.DefaultConfig()
    cfg.GatewayURL = "http://localhost:10105"
    cfg.AgentName = "default/default/my-agent"
    cfg.AgentCard = &eventmesh.AgentCard{
        Name:        "my-agent",
        Description: "My AI Agent",
        Version:     "1.0.0",
        Skills: []eventmesh.AgentSkill{
            {ID: "chat", Name: "Chat", Description: "General AI chat"},
        },
    }

    client := eventmesh.NewClient(cfg)
    client.SetRequestHandler(func(taskID, message string) (string, error) {
        return fmt.Sprintf("Processed: %s", message), nil
    })

    client.Start()
    defer client.Stop()

    // Sync task
    result, _ := client.SendTask("weather-agent", "Shenzhen weather?")
    fmt.Println(result.Data)

    // Async + poll
    taskID, _ := client.SendTaskAsync("analyst", "Analyze market")
    result, _ = client.WaitForTask(taskID, 2*time.Second, 60*time.Second)
    fmt.Println(result.State)

    // SSE stream
    client.StreamTask(taskID, func(tid, state, data string) bool {
        fmt.Printf("%s -> %s\n", tid, state)
        return state != "COMPLETED"
    }, 30*time.Second)
}
```

## API 参考

### 生命周期

| 方法 | 说明 |
|------|------|
| `NewClient(cfg)` | 创建客户端 |
| `Start() error` | 注册 AgentCard + 启动心跳 |
| `Stop()` | 停止心跳，关闭客户端 |
| `IsStarted() bool` | 是否已启动 |
| `SetRequestHandler(h)` | 设置 incoming task 处理函数 |

### 任务操作

| 方法 | 说明 |
|------|------|
| `SendTask(target, msg) (*TaskResult, error)` | 同步任务，等待结果 |
| `SendTaskAsync(target, msg) (string, error)` | 异步任务，返回 taskID |
| `SendTaskAsyncWithParent(target, msg, parentID) (string, error)` | 异步子任务 |
| `GetTaskStatus(taskID) (*TaskResult, error)` | 查询任务状态（404 返回 nil） |
| `CancelTask(taskID) (bool, error)` | 取消任务（404 返回 false） |
| `StreamTask(taskID, cb, timeout) error` | SSE 流式跟踪 |
| `WaitForTask(taskID, interval, max) (*TaskResult, error)` | 轮询至终态 |

### 发现

| 方法 | 说明 |
|------|------|
| `HealthCheck() (map, error)` | 网关健康检查 |
| `ListAgents() ([]map, error)` | 列出所有 Agent |
| `GetAgentCard(org, unit, id) (map, error)` | 获取 Agent 详情 |

### 数据模型

| 类型 | 字段 |
|------|------|
| `TaskResult` | TaskID, State, Data, Error, TargetAgent |
| `AgentCard` | Name, Description, Version, Skills, Interfaces |
| `AgentSkill` | ID, Name, Description |
| `AgentMeshError` | Code (HTTP status), Message |

## 示例

```bash
cd eventmesh-agent-sdks/go/cmd/openclaw
go build -o openclaw-adapter .
A2A_GATEWAY_URL=http://localhost:10105 ./openclaw-adapter
```
