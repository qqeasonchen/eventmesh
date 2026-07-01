# EventMesh Agent — Hermes Adapter (Skill)

Hermes AI 系统接入 EventMesh A2A AgentMesh 的适配器 Skill，基于 `eventmesh-agent-sdks`（Python）。

**运行即接入**，4 个技能自动注册：code-review / security-audit / infrastructure-ops / general-chat。

## 依赖

- [eventmesh-agent-sdks](../../eventmesh-agent-sdks/) — 共享 A2A 客户端抽象层

## 快速开始

```bash
# 一键启动（自动注册 + 心跳 + 任务收发）
python run.py

# 自定义 Gateway
A2A_GATEWAY_URL=http://localhost:10105 python run.py

# 自定义 Agent 名称
A2A_AGENT_NAME=my-org/my-unit/my-agent python run.py
```

## 架构

```
┌──────────────┐                     ┌──────────────────┐
│ eventmesh-   │  import             │  Hermes Adapter  │
│ agent-sdk    │ ◄─────────────────  │  (hermes_agent)  │
│ (Python)     │                     └────────┬─────────┘
└──────┬───────┘                              │
       │ HTTP/REST                    定义 Hermes 技能卡
       ▼                              (code-review / security-audit
┌──────────────────┐                    infrastructure-ops / general-chat)
│  EventMesh A2A   │
│  Gateway :10105  │
└──────────────────┘
```
