// Package eventmesh provides an A2A Agent Mesh client for Go applications.
//
// Usage:
//
//	client := eventmesh.NewClient(eventmesh.Config{
//	    GatewayURL: "http://localhost:10105",
//	    AgentName:  "openclaw/agent",
//	    AgentCard: &eventmesh.AgentCard{
//	        Name:        "openclaw-agent",
//	        Description: "OpenClaw multi-agent system",
//	        Version:     "1.0.0",
//	    },
//	})
//	client.Start()
//	result, _ := client.SendTask("weather-agent", "Shenzhen")
package eventmesh

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// TaskState represents the state of an A2A task.
type TaskState string

const (
	StateSubmitted TaskState = "SUBMITTED"
	StateWorking   TaskState = "WORKING"
	StateCompleted TaskState = "COMPLETED"
	StateFailed    TaskState = "FAILED"
	StateCancelled TaskState = "CANCELLED"
)

// TaskResult represents the result of an A2A task.
type TaskResult struct {
	TaskID      string    `json:"taskId"`
	State       string    `json:"state"`
	Data        string    `json:"data,omitempty"`
	Error       string    `json:"error,omitempty"`
	TargetAgent string    `json:"targetAgent,omitempty"`
}

// AgentCard describes an agent's capabilities.
type AgentCard struct {
	Name               string   `json:"name"`
	Description        string   `json:"description"`
	Version            string   `json:"version"`
	DefaultInputModes  []string `json:"defaultInputModes,omitempty"`
	DefaultOutputModes []string `json:"defaultOutputModes,omitempty"`
}

// Config holds client configuration.
type Config struct {
	GatewayURL        string
	AgentName         string
	AgentCard         *AgentCard
	HeartbeatInterval time.Duration
	RequestTimeout    time.Duration
}

// DefaultConfig returns a Config with sensible defaults.
func DefaultConfig() Config {
	return Config{
		GatewayURL:        "http://localhost:10105",
		HeartbeatInterval: 30 * time.Second,
		RequestTimeout:    30 * time.Second,
	}
}

// Client is an A2A Agent Mesh client.
type Client struct {
	cfg      Config
	http     *http.Client
	reg      AgentRegistration
	card     *AgentCard
	started  bool
	stopCh   chan struct{}
	handler  RequestHandler
}

// RequestHandler handles incoming A2A task requests.
type RequestHandler func(taskID, message string) (string, error)

// AgentRegistration holds agent identity.
type AgentRegistration struct {
	OrgID   string
	UnitID  string
	AgentID string
}

func parseAgentName(name string) AgentRegistration {
	parts := strings.SplitN(name, "/", 3)
	r := AgentRegistration{OrgID: "default", UnitID: "default"}
	switch len(parts) {
	case 3:
		r.OrgID = parts[0]
		r.UnitID = parts[1]
		r.AgentID = parts[2]
	case 2:
		r.OrgID = parts[0]
		r.UnitID = parts[1]
		r.AgentID = name
	default:
		r.AgentID = name
	}
	return r
}

// NewClient creates a new AgentMesh client.
func NewClient(cfg Config) *Client {
	if cfg.RequestTimeout == 0 {
		cfg.RequestTimeout = 30 * time.Second
	}
	if cfg.HeartbeatInterval == 0 {
		cfg.HeartbeatInterval = 30 * time.Second
	}
	if cfg.AgentCard == nil {
		cfg.AgentCard = &AgentCard{
			Name:        cfg.AgentName,
			Description: "OpenClaw Agent",
			Version:     "1.0.0",
		}
	}
	return &Client{
		cfg: cfg,
		http: &http.Client{
			Timeout: cfg.RequestTimeout,
		},
		reg:    parseAgentName(cfg.AgentName),
		card:   cfg.AgentCard,
		stopCh: make(chan struct{}),
	}
}

// Start registers the agent card and begins heartbeat.
func (c *Client) Start() error {
	if err := c.registerCard(); err != nil {
		return fmt.Errorf("register card: %w", err)
	}
	c.started = true
	go c.heartbeatLoop()
	return nil
}

// Stop stops heartbeat and shuts down.
func (c *Client) Stop() {
	if !c.started {
		return
	}
	c.started = false
	close(c.stopCh)
}

// SetRequestHandler sets the handler for incoming A2A requests.
func (c *Client) SetRequestHandler(h RequestHandler) {
	c.handler = h
}

// SendTask sends a sync task to a target agent and waits for the result.
func (c *Client) SendTask(targetAgent, message string) (*TaskResult, error) {
	body := map[string]string{
		"targetAgent": targetAgent,
		"message":     message,
	}
	return c.doTask(body, "sync")
}

// SendTaskAsync sends an async task and returns the task ID.
func (c *Client) SendTaskAsync(targetAgent, message string) (string, error) {
	body := map[string]string{
		"targetAgent": targetAgent,
		"message":     message,
	}
	result, err := c.doTask(body, "async")
	if err != nil {
		return "", err
	}
	return result.TaskID, nil
}

// GetTaskStatus retrieves the current status of a task.
func (c *Client) GetTaskStatus(taskID string) (*TaskResult, error) {
	url := fmt.Sprintf("%s/a2a/tasks/%s", c.cfg.GatewayURL, taskID)
	return c.getJSON(url)
}

// CancelTask cancels a pending task.
func (c *Client) CancelTask(taskID string) error {
	url := fmt.Sprintf("%s/a2a/tasks/%s", c.cfg.GatewayURL, taskID)
	req, err := http.NewRequest(http.MethodDelete, url, nil)
	if err != nil {
		return err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("cancel failed: %d %s", resp.StatusCode, string(body))
	}
	return nil
}

// ListAgents returns all registered agents.
func (c *Client) ListAgents() ([]map[string]interface{}, error) {
	url := fmt.Sprintf("%s/a2a/agents", c.cfg.GatewayURL)
	return c.getJSONList(url)
}

// HealthCheck checks gateway health.
func (c *Client) HealthCheck() (map[string]interface{}, error) {
	url := fmt.Sprintf("%s/a2a/health", c.cfg.GatewayURL)
	return c.getJSONMap(url)
}

// =========================================================================
// Internal
// =========================================================================

func (c *Client) registerCard() error {
	url := fmt.Sprintf("%s/a2a/cards/card/%s/%s/%s",
		c.cfg.GatewayURL, c.reg.OrgID, c.reg.UnitID, c.reg.AgentID)
	data, _ := json.Marshal(c.card)
	resp, err := c.http.Post(url, "application/json", bytes.NewReader(data))
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("register failed: %d %s", resp.StatusCode, string(body))
	}
	return nil
}

func (c *Client) heartbeatLoop() {
	for {
		select {
		case <-c.stopCh:
			return
		case <-time.After(c.cfg.HeartbeatInterval):
			c.sendHeartbeat()
		}
	}
}

func (c *Client) sendHeartbeat() {
	url := fmt.Sprintf("%s/a2a/heartbeat", c.cfg.GatewayURL)
	body := map[string]string{
		"orgId":   c.reg.OrgID,
		"unitId":  c.reg.UnitID,
		"agentId": c.reg.AgentID,
	}
	data, _ := json.Marshal(body)
	resp, err := c.http.Post(url, "application/json", bytes.NewReader(data))
	if err != nil {
		return
	}
	resp.Body.Close()
}

func (c *Client) doTask(body map[string]string, mode string) (*TaskResult, error) {
	url := fmt.Sprintf("%s/a2a/tasks?mode=%s", c.cfg.GatewayURL, mode)
	data, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	resp, err := c.http.Post(url, "application/json", bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("task request: %w", err)
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("task failed: %d %s", resp.StatusCode, string(raw))
	}

	var result TaskResult
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, fmt.Errorf("parse result: %w", err)
	}
	return &result, nil
}

func (c *Client) getJSON(url string) (*TaskResult, error) {
	resp, err := c.http.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	var result TaskResult
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, err
	}
	return &result, nil
}

func (c *Client) getJSONList(url string) ([]map[string]interface{}, error) {
	resp, err := c.http.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	var list []map[string]interface{}
	if err := json.Unmarshal(raw, &list); err != nil {
		return nil, err
	}
	return list, nil
}

func (c *Client) getJSONMap(url string) (map[string]interface{}, error) {
	resp, err := c.http.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	var m map[string]interface{}
	if err := json.Unmarshal(raw, &m); err != nil {
		return nil, err
	}
	return m, nil
}
