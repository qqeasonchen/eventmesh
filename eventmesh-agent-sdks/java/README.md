# EventMesh Agent SDK for Java

## A2AAgentToolBridge

将 EventMesh A2A Gateway 上注册的 Agent 自动映射为 MCP Tools。

```java
// 创建桥接
A2AAgentToolBridge bridge = new A2AAgentToolBridge("http://localhost:10105");

// 注册 Hermes Agent 的所有 skills 位 MCP tools
bridge.registerAgent("default/default/hermes-assistant");
// 自动生成：a2a_hermes-assistant_code-review, a2a_hermes-assistant_security-audit, etc.

// 获取所有 MCP Tool 定义
List<McpTool> tools = bridge.getMcpTools();

// 调用 tool
McpToolResult result = bridge.executeTool("a2a_hermes-assistant_code-review", args);
```

## 集成到 EventMesh MCP Connector

此文件本位于 `eventmesh-connectors/eventmesh-connector-mcp/src/main/java/org/apache/eventmesh/connector/mcp/source/A2AAgentToolBridge.java`，SDK 目录下保留副本用于参考和独立开发。

## 架构

```
MCP Client  ──tools/call──► MCP Connector
                                │
                    ┌───────────┼───────────┐
                    │           │           │
              A2AAgentToolBridge            │
                    │                      │
               HTTP REST                   │
                    │                      │
            EventMesh A2A Gateway ◄────────┘
               :10105
```
