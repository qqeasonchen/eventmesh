#!/usr/bin/env python3
"""
Claude Code MCP Bridge — 启动 stdio JSON-RPC 桥接，将 Claude Code 接入 EventMesh A2A.

此脚本启动 MCP Bridge Server，监听 stdin/stdout 的 JSON-RPC 请求，
转换为对 EventMesh A2A Gateway 的 HTTP 调用。

配置 Claude Code 使用此 Bridge:
    claude mcp add eventmesh-agent -- python run.py

或者手动配置 claude_desktop_config.json:
    {
      "mcpServers": {
        "eventmesh-agent": {
          "command": "python3",
          "args": ["<本文件路径>"],
          "env": { "A2A_GATEWAY_URL": "http://localhost:10105" }
        }
      }
    }

环境变量:
    A2A_GATEWAY_URL  — Gateway 地址 (默认: http://localhost:10105)
"""

import os
import sys

# Point to shared SDK
_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(os.path.dirname(_HERE))
_MCP_SERVER = os.path.join(_ROOT, "eventmesh-agent-sdks", "python", "integrations", "mcp", "server.py")

if not os.path.exists(_MCP_SERVER):
    print(f"Error: MCP bridge server not found at {_MCP_SERVER}", file=sys.stderr)
    sys.exit(1)

# Run MCP bridge as a subprocess so we inherit its stdio
sys.path.insert(0, os.path.join(_ROOT, "eventmesh-agent-sdks", "python"))
sys.path.insert(0, os.path.dirname(_MCP_SERVER))

# Directly import and run the server
import importlib.util
spec = importlib.util.spec_from_file_location("mcp_server", _MCP_SERVER)
mcp_server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mcp_server)
mcp_server.main()
