# EventMesh Agent SDK

多语言 Agent SDK，将主流 AI Agent 框架接入 [Apache EventMesh](https://eventmesh.apache.org/) A2A Agent Mesh。

```
                    ┌─ Hermes (Python HTTP SDK)
                    │
┌─────────┐    ┌────┴────┐    ┌──────────────────┐
│ LangChain│────│ Python  │────│                  │
│ AutoGen  │────│  SDK    │────│   EventMesh      │
│ CrewAI   │────│         │────│   A2A Gateway    │
│ Dify     │────│         │────│   :10105         │
└─────────┘    └────┬────┘    │                  │
                    │         │   ┌─────────┐    │
┌─────────┐    ┌────┴────┐    │   │ Agents  │    │
│ OpenClaw │────│  Go SDK │────│   │ Registry│    │
│ Go Agent │────│         │────│   └─────────┘    │
└─────────┘    └─────────┘    └──────────────────┘
                                    │
┌─────────┐               ┌─────────┴────────┐
│Claude   │──── stdio ────│  MCP Bridge       │
│Code     │               │  (tools/tasks)    │
└─────────┘               └──────────────────┘
```

## 目录结构

```
eventmesh-agent-sdk/
├── python/                 # Python SDK
│   ├── eventmesh_agent/    # 核心 A2A 客户端
│   ├── integrations/       # 框架集成
│   │   └── mcp/            #   MCP Bridge（Claude Code / Cursor）
│   │   └── langchain/      #   （计划中）LangChain Tool
│   │   └── autogen/        #   （计划中）AutoGen Agent
│   └── tests/              # 集成测试
├── go/                     # Go SDK
│   ├── pkg/eventmesh_agent/# 核心 A2A 客户端
│   └── cmd/openclaw/       # OpenClaw 适配器示例
├── java/                   # Java 集成
│   └── A2AAgentToolBridge.java  # MCP Connector A2A 桥接
└── README.md
```

## Python SDK

```python
from eventmesh_agent import AgentMeshClient

client = AgentMeshClient(
    gateway_url="http://localhost:10105",
    agent_name="my-agent",
    agent_card={
        "name": "my-agent",
        "skills": [{"id": "chat", "name": "Chat"}],
    },
)
client.start()

# 同步任务
result = client.send_task("weather-agent", "Shenzhen?")
print(result.data)

# 异步 + SSE 流
task_id = client.send_task_async("analyst", "analyze AAPL")
client.stream_task(task_id, on_event=lambda tid, state, data: print(state))
```

安装：
```bash
pip install ./eventmesh-agent-sdk/python
```

## Go SDK

```go
import "github.com/qqeasonchen/eventmesh/eventmesh-agent-sdk/go/pkg/eventmesh_agent"

cfg := eventmesh.DefaultConfig()
cfg.GatewayURL = "http://localhost:10105"
client := eventmesh.NewClient(cfg)
client.Start()
```

## MCP Bridge

将 EventMesh A2A Agent 暴露为 MCP Tools：

```json
{
  "mcpServers": {
    "eventmesh-agent": {
      "command": "python3",
      "args": ["eventmesh-agent-sdk/python/integrations/mcp/server.py"],
      "env": { "A2A_GATEWAY_URL": "http://localhost:10105" }
    }
  }
}
```

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 1 | 核心 SDK (Python/Go) + MCP Bridge | ✅ 已完成 |
| Phase 2 | LangChain / AutoGen / LangGraph 集成 | 🔜 计划中 |
| Phase 3 | TypeScript SDK (Mastra/OpenAI) | 📋 待定 |
| Phase 4 | Spring AI Java 集成 | 📋 待定 |

## 许可证

Apache License 2.0
