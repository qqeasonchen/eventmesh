// Package eventmesh provides an A2A Agent Mesh client for Go applications.
//
// Zero external dependencies (stdlib only).
//
// Usage:
//
//	client := eventmesh.NewClient(eventmesh.Config{
//	    GatewayURL: "http://localhost:10105",
//	    AgentName:  "default/default/my-agent",
//	    AgentCard: &eventmesh.AgentCard{
//	        Name:        "my-agent",
//	        Description: "My Agent",
//	        Version:     "1.0.0",
//	        Skills: []eventmesh.AgentSkill{
//	            {ID: "chat", Name: "Chat", Description: "General AI chat"},
//	        },
//	    },
//	})
//	client.Start()
//	defer client.Stop()
//
//	result, _ := client.SendTask("weather-agent", "Shenzhen")
//	fmt.Println(result.Data)
package eventmesh

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"
)

// =========================================================================
// Types
// =========================================================================

// TaskState represents the state of an A2A task.
type TaskState string

const (
	StateSubmitted TaskState = "SUBMITTED"
	StateWorking   TaskState = "WORKING"
	StateCompleted TaskState = "COMPLETED"
	StateFailed    TaskState = "FAILED"
	StateCancelled TaskState = "CANCELLED"
)

// IsTerminal returns true if the task has finished (completed/failed/cancelled).
func (s TaskState) IsTerminal() bool {
	return s == StateCompleted || s == StateFailed || s == StateCancelled
}

// TaskResult represents the result of an A2A task.
type TaskResult struct {
	TaskID      string `json:"taskId"`
	State       string `json:"state"`
	Data        string `json:"data,omitempty"`
	Error       string `json:"error,omitempty"`
	TargetAgent string `json:"targetAgent,omitempty"`
}

// IsTerminal returns true if this task result is in a terminal state.
func (r *TaskResult) IsTerminal() bool {
	return TaskState(r.State).IsTerminal()
}

// AgentSkill describes a skill an agent can perform.
type AgentSkill struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
}

// AgentInterface describes a protocol binding.
type AgentInterface struct {
	URL             string `json:"url"`
	ProtocolBinding string `json:"protocolBinding"`
	ProtocolVersion string `json:"protocolVersion"`
}

// AgentCard describes an agent's capabilities.
type AgentCard struct {
	Name               string           `json:"name"`
	Description        string           `json:"description"`
	Version            string           `json:"version"`
	Skills             []AgentSkill     `json:"skills,omitempty"`
	DefaultInputModes  []string         `json:"defaultInputModes,omitempty"`
	DefaultOutputModes []string         `json:"defaultOutputModes,omitempty"`
	Interfaces         []AgentInterface `json:"interfaces,omitempty"`
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

// RequestHandler handles incoming A2A task requests.
// Returns (result, error) — result is sent back as JSON.
type RequestHandler func(taskID, message string) (string, error)

// AgentRegistration holds agent identity parsed from name.
type AgentRegistration struct {
	OrgID   string
	UnitID  string
	AgentID string
}

// =========================================================================
// Client
// =========================================================================

// Client is an A2A Agent Mesh client.
type Client struct {
	cfg     Config
	http    *http.Client
	reg     AgentRegistration
	card    *AgentCard
	started bool
	closeCh chan struct{}
	handler RequestHandler

	mu sync.RWMutex
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
			Description: "Agent",
			Version:     "1.0.0",
		}
	}
	// Add default interface if none
	if len(cfg.AgentCard.Interfaces) == 0 {
		cfg.AgentCard.Interfaces = []AgentInterface{
			{
				URL:             fmt.Sprintf("%s/a2a", cfg.GatewayURL),
				ProtocolBinding: "JSONRPC",
				ProtocolVersion: "0.3",
			},
		}
	}
	return &Client{
		cfg:     cfg,
		http:    &http.Client{Timeout: cfg.RequestTimeout},
		reg:     parseAgentName(cfg.AgentName),
		card:    cfg.AgentCard,
		closeCh: make(chan struct{}),
	}
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

// =========================================================================
// Lifecycle
// =========================================================================

// Start registers the agent card and begins heartbeat.
func (c *Client) Start() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.started {
		return nil
	}
	if err := c.registerCard(); err != nil {
		return fmt.Errorf("register card: %w", err)
	}
	c.started = true
	go c.heartbeatLoop()
	return nil
}

// Stop stops heartbeat and shuts down.
func (c *Client) Stop() {
	c.mu.Lock()
	if !c.started {
		c.mu.Unlock()
		return
	}
	c.started = false
	c.mu.Unlock()
	close(c.closeCh)
}

// IsStarted returns whether the client is started and registered.
func (c *Client) IsStarted() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.started
}

// SetRequestHandler sets the handler for incoming A2A requests.
func (c *Client) SetRequestHandler(h RequestHandler) {
	c.handler = h
}

// =========================================================================
// Task Operations
// =========================================================================

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
	return c.SendTaskAsyncWithParent(targetAgent, message, "")
}

// SendTaskAsyncWithParent sends an async task with an optional parent task ID.
func (c *Client) SendTaskAsyncWithParent(targetAgent, message, parentTaskID string) (string, error) {
	body := map[string]string{
		"targetAgent": targetAgent,
		"message":     message,
	}
	if parentTaskID != "" {
		body["parentTaskId"] = parentTaskID
	}
	result, err := c.doTask(body, "async")
	if err != nil {
		return "", err
	}
	return result.TaskID, nil
}

// GetTaskStatus retrieves the current status of a task.
// Returns nil if the task is not found (404).
func (c *Client) GetTaskStatus(taskID string) (*TaskResult, error) {
	url := fmt.Sprintf("%s/a2a/tasks/%s", c.cfg.GatewayURL, taskID)
	result, err := c.getJSON(url)
	if err != nil {
		if ae, ok := err.(*AgentMeshError); ok && ae.Code == 404 {
			return nil, nil
		}
		return nil, err
	}
	return result, nil
}

// CancelTask cancels a pending task.
// Returns false if the task is not found (404).
func (c *Client) CancelTask(taskID string) (bool, error) {
	url := fmt.Sprintf("%s/a2a/tasks/%s", c.cfg.GatewayURL, taskID)
	req, err := http.NewRequest(http.MethodDelete, url, nil)
	if err != nil {
		return false, err
	}
	req.Header.Set("Accept", "application/json")
	resp, err := c.http.Do(req)
	if err != nil {
		return false, fmt.Errorf("cancel request: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode == 404 {
		return false, nil
	}
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return false, &AgentMeshError{Code: resp.StatusCode, Message: string(body)}
	}
	return true, nil
}

// StreamTask streams task status updates via SSE.
// The onEvent callback receives (taskID, state, data).
// Return false from onEvent to stop the stream early.
func (c *Client) StreamTask(taskID string, onEvent func(taskID, state, data string) bool, timeout time.Duration) error {
	url := fmt.Sprintf("%s/a2a/tasks/%s/stream", c.cfg.GatewayURL, taskID)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return fmt.Errorf("stream request: %w", err)
	}
	req.Header.Set("Accept", "text/event-stream")

	httpClient := &http.Client{Timeout: timeout}
	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("stream connect: %w", err)
	}
	defer resp.Body.Close()

	scanner := bufio.NewScanner(resp.Body)
	var eventBuf strings.Builder
	deadline := time.Now().Add(timeout)

	for scanner.Scan() {
		if time.Now().After(deadline) {
			break
		}
		line := scanner.Text()

		if line == "" {
			// Event boundary
			data := c.parseSSEData(eventBuf.String())
			eventBuf.Reset()
			if data != nil {
				tid := data["taskId"]
				if tid == "" {
					tid = taskID
				}
				if !onEvent(tid, data["state"], data["data"]) {
					return nil // caller asked to stop
				}
			}
		} else if strings.HasPrefix(line, ":") {
			// SSE comment (heartbeat ping)
			continue
		} else {
			eventBuf.WriteString(line)
			eventBuf.WriteByte('\n')
		}
	}
	return scanner.Err()
}

// WaitForTask polls task status until it reaches a terminal state.
func (c *Client) WaitForTask(taskID string, pollInterval, maxWait time.Duration) (*TaskResult, error) {
	deadline := time.Now().Add(maxWait)
	for time.Now().Before(deadline) {
		result, err := c.GetTaskStatus(taskID)
		if err != nil {
			return nil, err
		}
		if result == nil {
			return nil, &AgentMeshError{Code: 404, Message: "task not found: " + taskID}
		}
		if result.IsTerminal() {
			return result, nil
		}
		time.Sleep(pollInterval)
	}
	return nil, fmt.Errorf("timeout waiting for task %s after %v", taskID, maxWait)
}

// =========================================================================
// Discovery
// =========================================================================

// ListAgents returns all registered agents.
func (c *Client) ListAgents() ([]map[string]interface{}, error) {
	url := fmt.Sprintf("%s/a2a/agents", c.cfg.GatewayURL)
	return c.getJSONList(url)
}

// GetAgentCard retrieves a specific agent's card.
func (c *Client) GetAgentCard(orgID, unitID, agentID string) (map[string]interface{}, error) {
	url := fmt.Sprintf("%s/a2a/cards/card/%s/%s/%s", c.cfg.GatewayURL, orgID, unitID, agentID)
	return c.getJSONMap(url)
}

// HealthCheck checks gateway health.
func (c *Client) HealthCheck() (map[string]interface{}, error) {
	url := fmt.Sprintf("%s/a2a/health", c.cfg.GatewayURL)
	return c.getJSONMap(url)
}

// =========================================================================
// Internal: Registration & Heartbeat
// =========================================================================

func (c *Client) registerCard() error {
	url := fmt.Sprintf("%s/a2a/cards/card/%s/%s/%s",
		c.cfg.GatewayURL, c.reg.OrgID, c.reg.UnitID, c.reg.AgentID)
	data, err := json.Marshal(c.card)
	if err != nil {
		return err
	}
	resp, err := c.http.Post(url, "application/json", bytes.NewReader(data))
	if err != nil {
		return &AgentMeshError{Code: 0, Message: fmt.Sprintf("register connect: %v", err)}
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return &AgentMeshError{Code: resp.StatusCode, Message: string(body)}
	}
	return nil
}

func (c *Client) heartbeatLoop() {
	for {
		select {
		case <-c.closeCh:
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

// =========================================================================
// Internal: HTTP Helpers
// =========================================================================

func (c *Client) doTask(body map[string]string, mode string) (*TaskResult, error) {
	url := fmt.Sprintf("%s/a2a/tasks?mode=%s", c.cfg.GatewayURL, mode)
	data, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	resp, err := c.http.Post(url, "application/json", bytes.NewReader(data))
	if err != nil {
		return nil, &AgentMeshError{Code: 0, Message: fmt.Sprintf("task request: %v", err)}
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}
	if resp.StatusCode >= 400 {
		return nil, &AgentMeshError{Code: resp.StatusCode, Message: string(raw)}
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
		return nil, &AgentMeshError{Code: 0, Message: fmt.Sprintf("request: %v", err)}
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode >= 400 {
		return nil, &AgentMeshError{Code: resp.StatusCode, Message: string(raw)}
	}
	var result TaskResult
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, err
	}
	return &result, nil
}

func (c *Client) getJSONList(url string) ([]map[string]interface{}, error) {
	resp, err := c.http.Get(url)
	if err != nil {
		return nil, &AgentMeshError{Code: 0, Message: fmt.Sprintf("request: %v", err)}
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode >= 400 {
		return nil, &AgentMeshError{Code: resp.StatusCode, Message: string(raw)}
	}
	var list []map[string]interface{}
	if err := json.Unmarshal(raw, &list); err != nil {
		return nil, err
	}
	return list, nil
}

func (c *Client) getJSONMap(url string) (map[string]interface{}, error) {
	resp, err := c.http.Get(url)
	if err != nil {
		return nil, &AgentMeshError{Code: 0, Message: fmt.Sprintf("request: %v", err)}
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode >= 400 {
		return nil, &AgentMeshError{Code: resp.StatusCode, Message: string(raw)}
	}
	var m map[string]interface{}
	if err := json.Unmarshal(raw, &m); err != nil {
		return nil, err
	}
	return m, nil
}

func (c *Client) parseSSEData(raw string) map[string]string {
	for _, line := range strings.Split(raw, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "data:") {
			data := strings.TrimSpace(strings.TrimPrefix(line, "data:"))
			var result map[string]string
			if err := json.Unmarshal([]byte(data), &result); err != nil {
				return nil
			}
			return result
		}
	}
	return nil
}

// =========================================================================
// AgentMeshError
// =========================================================================

// AgentMeshError represents an error from the A2A Gateway.
type AgentMeshError struct {
	Code    int    // HTTP status code (0 = connection error)
	Message string
}

func (e *AgentMeshError) Error() string {
	if e.Code == 404 {
		return fmt.Sprintf("A2A: not found (404): %s", e.Message)
	}
	if e.Code > 0 {
		return fmt.Sprintf("A2A: HTTP %d: %s", e.Code, e.Message)
	}
	return fmt.Sprintf("A2A: %s", e.Message)
}
