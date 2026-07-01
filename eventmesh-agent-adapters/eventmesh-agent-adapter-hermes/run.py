#!/usr/bin/env python3
"""
Hermes Agent Adapter — 一键启动，自动注册到 EventMesh A2A AgentMesh.

使用:
    python run.py                          # 默认 Gateway
    A2A_GATEWAY_URL=http://localhost:10105 python run.py
    A2A_AGENT_NAME=my-org/my-unit/my-agent python run.py

Skills: code-review / security-audit / infrastructure-ops / general-chat
"""

import os
import sys

# Point to shared SDK
_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(os.path.dirname(_HERE))
_SDK_PATH = os.path.join(_ROOT, "eventmesh-agent-sdks", "python")
sys.path.insert(0, _SDK_PATH)

# Add examples dir for import
sys.path.insert(0, os.path.join(_HERE, "examples"))

from hermes_agent import main

if __name__ == "__main__":
    main()
