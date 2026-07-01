// OpenClaw AgentMesh Adapter — connects OpenClaw to EventMesh A2A Agent Mesh.
//
// Usage:
//   go build -o openclaw-adapter .
//   A2A_GATEWAY_URL=http://localhost:10105 ./openclaw-adapter
package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/qqeasonchen/eventmesh/eventmesh-agent-sdk/go/pkg/eventmesh_agent"
)

func main() {
	gatewayURL := os.Getenv("A2A_GATEWAY_URL")
	if gatewayURL == "" {
		gatewayURL = "http://localhost:10105"
	}
	agentName := os.Getenv("A2A_AGENT_NAME")
	if agentName == "" {
		agentName = "default/default/openclaw-agent"
	}

	cfg := eventmesh.DefaultConfig()
	cfg.GatewayURL = gatewayURL
	cfg.AgentName = agentName
	cfg.AgentCard = &eventmesh.AgentCard{
		Name:        "openclaw-agent",
		Description: "OpenClaw multi-agent orchestration system",
		Version:     "1.0.0",
		DefaultInputModes:  []string{"text/plain", "application/json"},
		DefaultOutputModes: []string{"text/plain", "application/json"},
	}

	c := eventmesh.NewClient(cfg)

	// Handle incoming A2A tasks from other agents
	c.SetRequestHandler(func(taskID, message string) (string, error) {
		log.Printf("[OpenClaw] Received task %s: %s", taskID, truncate(message, 100))
		result := map[string]interface{}{
			"status":  "processed",
			"agent":   "openclaw-agent",
			"message": message,
		}
		data, _ := json.Marshal(result)
		return string(data), nil
	})

	if err := c.Start(); err != nil {
		log.Fatalf("Failed to start OpenClaw adapter: %v", err)
	}
	defer c.Stop()

	log.Printf("OpenClaw AgentMesh Adapter started: agent=%s, gateway=%s", agentName, gatewayURL)

	// Health check
	health, err := c.HealthCheck()
	if err != nil {
		log.Printf("Health check failed: %v", err)
	} else {
		healthJSON, _ := json.MarshalIndent(health, "", "  ")
		log.Printf("Gateway health:\n%s", string(healthJSON))
	}

	// List agents
	agents, err := c.ListAgents()
	if err != nil {
		log.Printf("List agents failed: %v", err)
	} else {
		log.Printf("Registered agents: %d", len(agents))
		for _, a := range agents {
			name := "?"
			if n, ok := a["name"]; ok {
				name = fmt.Sprintf("%v", n)
			}
			status := "?"
			if s, ok := a["status"]; ok {
				status = fmt.Sprintf("%v", s)
			}
			log.Printf("  - %s (%s)", name, status)
		}
	}

	// Wait for shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	log.Printf("OpenClaw adapter running. Press Ctrl+C to stop.")
	<-sigCh
	log.Println("Shutting down...")
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}
