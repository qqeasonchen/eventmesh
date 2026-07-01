// OpenClaw AgentMesh Adapter — connects OpenClaw multi-agent system
// to EventMesh A2A Agent Mesh via the shared eventmesh-agent-sdks Go SDK.
//
// Architecture:
//   OpenClaw Adapter → eventmesh-agent-sdks/go → A2A Gateway :10105
//
// Usage:
//   go build -o openclaw-adapter .
//   A2A_GATEWAY_URL=http://localhost:10105 ./openclaw-adapter
//
// Environment:
//   A2A_GATEWAY_URL  — Gateway URL (default: http://localhost:10105)
//   A2A_AGENT_NAME   — Agent identity (default: default/default/openclaw-agent)
package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/qqeasonchen/eventmesh/eventmesh-agent-sdks/go/pkg/eventmesh_agent"
)

func main() {
	log.SetFlags(log.Ltime | log.Lshortfile)

	// --- Config ---
	gatewayURL := os.Getenv("A2A_GATEWAY_URL")
	if gatewayURL == "" {
		gatewayURL = "http://localhost:10105"
	}
	agentName := os.Getenv("A2A_AGENT_NAME")
	if agentName == "" {
		agentName = "default/default/openclaw-agent"
	}

	log.Println("=== OpenClaw AgentMesh Adapter v1.0.0 ===")
	log.Printf("Gateway: %s", gatewayURL)
	log.Printf("Agent:   %s", agentName)

	// Define OpenClaw's capabilities
	cfg := eventmesh.DefaultConfig()
	cfg.GatewayURL = gatewayURL
	cfg.AgentName = agentName
	cfg.AgentCard = &eventmesh.AgentCard{
		Name:        "openclaw-agent",
		Description: "OpenClaw multi-agent orchestration system — coordinates multiple AI agents for complex workflows",
		Version:     "1.0.0",
		Skills: []eventmesh.AgentSkill{
			{
				ID:          "multi-agent-orchestration",
				Name:        "Multi-Agent Orchestration",
				Description: "Coordinate multiple AI agents to complete complex tasks",
			},
			{
				ID:          "task-decomposition",
				Name:        "Task Decomposition",
				Description: "Break down complex tasks into sub-tasks for parallel execution",
			},
			{
				ID:          "agent-routing",
				Name:        "Agent Routing",
				Description: "Route tasks to the most suitable agent based on capabilities",
			},
			{
				ID:          "workflow-execution",
				Name:        "Workflow Execution",
				Description: "Execute multi-step workflows across agents",
			},
		},
		DefaultInputModes:  []string{"text/plain", "application/json"},
		DefaultOutputModes: []string{"text/plain", "application/json"},
	}

	c := eventmesh.NewClient(cfg)

	// Incoming task handler — route to OpenClaw's orchestration engine
	c.SetRequestHandler(func(taskID, message string) (string, error) {
		log.Printf("[OpenClaw] Received task %s: %s", taskID, truncate(message, 120))

		// Parse incoming request
		var req map[string]interface{}
		if err := json.Unmarshal([]byte(message), &req); err != nil {
			req = map[string]interface{}{"text": message}
		}

		// In production, this routes to OpenClaw's orchestration engine
		skill, _ := req["skill"].(string)
		text, _ := req["text"].(string)

		result := map[string]interface{}{
			"status":       "processed",
			"agent":        "openclaw-agent",
			"skill":        skill,
			"input":        truncate(text, 100),
			"orchestrated": true,
			"timestamp":    time.Now().UTC().Format(time.RFC3339),
		}
		data, _ := json.Marshal(result)
		return string(data), nil
	})

	// --- Start & Register ---
	if err := c.Start(); err != nil {
		log.Fatalf("Failed to start OpenClaw adapter: %v", err)
	}
	defer c.Stop()

	log.Printf("OpenClaw adapter started: agent=%s", agentName)

	// --- Discovery ---
	health, err := c.HealthCheck()
	if err != nil {
		log.Printf("Health check failed: %v", err)
	} else {
		healthJSON, _ := json.MarshalIndent(health, "", "  ")
		log.Printf("Gateway health:\n%s", string(healthJSON))
	}

	agents, err := c.ListAgents()
	if err != nil {
		log.Printf("List agents failed: %v", err)
	} else {
		log.Printf("Registered agents (%d):", len(agents))
		for _, a := range agents {
			name := fmt.Sprintf("%v", a["name"])
			status := fmt.Sprintf("%v", a["status"])
			skillCount := 0
			if skills, ok := a["skills"]; ok {
				if skillList, ok := skills.([]interface{}); ok {
					skillCount = len(skillList)
				}
			}
			log.Printf("  - %-30s status=%-10s skills=%d", name, status, skillCount)
		}
	}

	// --- Demo: send tasks to other agents ---
	demoTask(c, "weather-agent", "Shenzhen weather?")
	demoAsyncTask(c, "code-review", "Review the latest commit for security issues")

	// --- Running ---
	log.Println("==================================================")
	log.Println("OpenClaw adapter running. Press Ctrl+C to stop.")
	log.Println("Listening for incoming A2A tasks from other agents...")
	log.Println("==================================================")

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh
	log.Println("Shutting down OpenClaw adapter...")
}

func demoTask(c *eventmesh.Client, target, message string) {
	log.Printf("--- Sync task: %s ---", target)
	result, err := c.SendTask(target, message)
	if err != nil {
		log.Printf("  Failed: %v", err)
		return
	}
	log.Printf("  Result: state=%s data=%s", result.State, truncate(result.Data, 100))
}

func demoAsyncTask(c *eventmesh.Client, target, message string) {
	log.Printf("--- Async task: %s ---", target)
	taskID, err := c.SendTaskAsync(target, message)
	if err != nil {
		log.Printf("  Failed: %v", err)
		return
	}
	log.Printf("  Task submitted: %s", taskID)
	result, err := c.WaitForTask(taskID, 2*time.Second, 30*time.Second)
	if err != nil {
		log.Printf("  Wait failed: %v", err)
		return
	}
	log.Printf("  Result: state=%s data=%s", result.State, truncate(result.Data, 100))
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}
