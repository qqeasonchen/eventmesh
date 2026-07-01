/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.connector.mcp.source;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A2A Agent Discovery Bridge for MCP Connector.
 *
 * <p>Bridges the gap between MCP clients (Claude Code, OpenClaw) and the A2A Agent Mesh.
 * When an A2A agent registers on the Agent Mesh, its skills are automatically exposed
 * as MCP tools through this registry.
 *
 * <p>Flow:
 * <pre>
 *   A2A Agent registers AgentCard ──→ McpToolRegistry registers MCP tools
 *   MCP Client lists tools          ──→ Returns union of native + A2A agent tools
 *   MCP Client calls a2a tool       ──→ Task routed to A2A agent via Gateway
 * </pre>
 *
 * <p>Configuration (server-config.yml):
 * <pre>
 *   a2aGatewayUrl: http://localhost:10105
 *   a2aAgentDiscoveryEnabled: true
 *   a2aAgentSyncIntervalMs: 30000
 * </pre>
 */
public class A2AAgentToolBridge {

    // A2A agent → MCP tool name mapping
    private final ConcurrentHashMap<String, McpTool> a2aTools = new ConcurrentHashMap<>();
    // A2A agent names
    private final List<String> registeredAgentNames = new CopyOnWriteArrayList<>();

    private final String a2aGatewayUrl;
    private final long syncIntervalMs;

    private volatile boolean started = false;

    public A2AAgentToolBridge(String a2aGatewayUrl, long syncIntervalMs) {
        this.a2aGatewayUrl = a2aGatewayUrl;
        this.syncIntervalMs = syncIntervalMs;
    }

    public void start() {
        // Future: Start periodic sync with A2A Gateway to discover agents
        started = true;
    }

    public void stop() {
        started = false;
        a2aTools.clear();
        registeredAgentNames.clear();
    }

    /**
     * Returns MCP tool representations of all discovered A2A agents.
     * These tools can be merged with the native MCP connector tools.
     */
    public List<McpTool> getAgentTools() {
        return new CopyOnWriteArrayList<>(a2aTools.values());
    }

    /**
     * Registers an A2A agent's skills as MCP tools.
     */
    public void registerAgent(String agentName, List<Map<String, Object>> skills) {
        for (Map<String, Object> skill : skills) {
            String skillId = (String) skill.getOrDefault("id", "");
            String skillName = (String) skill.getOrDefault("name", skillId);
            String skillDesc = (String) skill.getOrDefault("description", "");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) skill.getOrDefault("tags", List.of());

            String toolName = "a2a_" + agentName.replace("/", "_") + "_" + skillId;
            McpTool tool = new McpToolBuilder()
                .name(toolName)
                .description(String.format("[A2A Agent: %s] %s", agentName, skillDesc))
                .addStringParam("message", "The task message to send to the agent")
                .addStringParam("mode", "sync (wait for result) or async (return immediately)", false)
                .build();

            a2aTools.put(toolName, tool);
        }
        registeredAgentNames.add(agentName);
    }

    /**
     * Unregisters all tools for a specific agent.
     */
    public void unregisterAgent(String agentName) {
        a2aTools.keySet().removeIf(key -> key.contains("_" + agentName.replace("/", "_")));
        registeredAgentNames.remove(agentName);
    }

    /**
     * Simple builder for MCP tool definitions.
     */
    static class McpToolBuilder {
        private final Map<String, Object> properties = new ConcurrentHashMap<>();
        private final List<String> required = new CopyOnWriteArrayList<>();
        private String name;
        private String description;

        public McpToolBuilder name(String name) {
            this.name = name;
            return this;
        }

        public McpToolBuilder description(String description) {
            this.description = description;
            return this;
        }

        public McpToolBuilder addStringParam(String paramName, String paramDesc) {
            return addStringParam(paramName, paramDesc, true);
        }

        public McpToolBuilder addStringParam(String paramName, String paramDesc, boolean isRequired) {
            properties.put(paramName, Map.of("type", "string", "description", paramDesc));
            if (isRequired) {
                required.add(paramName);
            }
            return this;
        }

        public McpTool build() {
            Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", properties,
                "required", required
            );
            return new McpTool(name, description, schema);
        }
    }

    /**
     * MCP Tool definition.
     */
    public static class McpTool {
        private final String name;
        private final String description;
        private final Map<String, Object> inputSchema;

        public McpTool(String name, String description, Map<String, Object> inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, Object> getInputSchema() { return inputSchema; }
    }
}
