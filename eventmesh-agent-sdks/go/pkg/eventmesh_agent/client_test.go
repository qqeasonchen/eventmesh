package eventmesh

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

// =========================================================================
// Mock A2A Gateway Server
// =========================================================================

type mockGateway struct {
	srv      *httptest.Server
	agents   map[string]interface{}
	tasks    map[string]*taskRecord
	mu       sync.Mutex
	taskIdx  int
}

type taskRecord struct {
	TaskID      string `json:"taskId"`
	TargetAgent string `json:"targetAgent"`
	Message     string `json:"message"`
	State       string `json:"state"`
	Data        string `json:"data,omitempty"`
	ParentID    string `json:"parentTaskId,omitempty"`
}

func newMockGateway() *mockGateway {
	gw := &mockGateway{
		agents: make(map[string]interface{}),
		tasks:  make(map[string]*taskRecord),
	}
	gw.srv = httptest.NewServer(http.HandlerFunc(gw.handle))
	return gw
}

func (m *mockGateway) close() {
	m.srv.Close()
}

func (m *mockGateway) url() string {
	return m.srv.URL
}

func (m *mockGateway) handle(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path

	switch {
	case path == "/a2a/health":
		m.handleHealth(w, r)
	case path == "/a2a/agents":
		m.handleAgents(w, r)
	case strings.HasPrefix(path, "/a2a/cards/card/") && r.Method == http.MethodPost:
		m.handleRegister(w, r, path)
	case strings.HasPrefix(path, "/a2a/cards/card/") && r.Method == http.MethodGet:
		m.handleGetCard(w, r, path)
	case path == "/a2a/heartbeat":
		m.handleHeartbeat(w, r)
	case strings.HasSuffix(path, "/stream"):
		m.handleStream(w, r, path)
	case strings.Contains(path, "/a2a/tasks/") && r.Method == http.MethodGet:
		m.handleGetTask(w, r, path)
	case strings.Contains(path, "/a2a/tasks/") && r.Method == http.MethodDelete:
		m.handleCancelTask(w, r, path)
	case strings.HasPrefix(path, "/a2a/tasks") && r.Method == http.MethodPost:
		m.handleSubmitTask(w, r)
	default:
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
	}
}

func (m *mockGateway) handleHealth(w http.ResponseWriter, r *http.Request) {
	json.NewEncoder(w).Encode(map[string]interface{}{
		"status":  "healthy",
		"version": "1.0.0",
	})
}

func (m *mockGateway) handleAgents(w http.ResponseWriter, r *http.Request) {
	m.mu.Lock()
	defer m.mu.Unlock()
	agents := make([]map[string]interface{}, 0, len(m.agents))
	for name := range m.agents {
		agents = append(agents, map[string]interface{}{
			"name":   name,
			"status": "online",
		})
	}
	json.NewEncoder(w).Encode(agents)
}

func (m *mockGateway) handleRegister(w http.ResponseWriter, r *http.Request, path string) {
	parts := strings.Split(strings.TrimPrefix(path, "/a2a/cards/card/"), "/")
	if len(parts) < 3 {
		http.Error(w, `{"error":"invalid path"}`, http.StatusBadRequest)
		return
	}
	agentName := fmt.Sprintf("%s/%s/%s", parts[0], parts[1], parts[2])
	var card map[string]interface{}
	json.NewDecoder(r.Body).Decode(&card)
	m.mu.Lock()
	m.agents[agentName] = card
	m.mu.Unlock()
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(map[string]string{"status": "registered"})
}

func (m *mockGateway) handleGetCard(w http.ResponseWriter, r *http.Request, path string) {
	parts := strings.Split(strings.TrimPrefix(path, "/a2a/cards/card/"), "/")
	if len(parts) < 3 {
		http.Error(w, `{"error":"invalid"}`, http.StatusBadRequest)
		return
	}
	agentName := fmt.Sprintf("%s/%s/%s", parts[0], parts[1], parts[2])
	m.mu.Lock()
	card, ok := m.agents[agentName]
	m.mu.Unlock()
	if !ok {
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
		return
	}
	json.NewEncoder(w).Encode(card)
}

func (m *mockGateway) handleHeartbeat(w http.ResponseWriter, r *http.Request) {
	var body map[string]string
	json.NewDecoder(r.Body).Decode(&body)
	agentName := fmt.Sprintf("%s/%s/%s", body["orgId"], body["unitId"], body["agentId"])
	m.mu.Lock()
	_, ok := m.agents[agentName]
	m.mu.Unlock()
	if ok {
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	} else {
		http.Error(w, `{"error":"not registered"}`, http.StatusNotFound)
	}
}

func (m *mockGateway) handleSubmitTask(w http.ResponseWriter, r *http.Request) {
	var body map[string]string
	json.NewDecoder(r.Body).Decode(&body)
	mode := r.URL.Query().Get("mode")
	if mode == "" {
		mode = "sync"
	}

	taskID := fmt.Sprintf("task-%d", m.taskIdx)
	m.taskIdx++

	task := &taskRecord{
		TaskID:      taskID,
		TargetAgent: body["targetAgent"],
		Message:     body["message"],
		State:       "SUBMITTED",
		ParentID:    body["parentTaskId"],
	}

	if mode == "async" {
		task.State = "SUBMITTED"
		m.mu.Lock()
		m.tasks[taskID] = task
		m.mu.Unlock()
		// Complete it in background
		go func() {
			time.Sleep(200 * time.Millisecond)
			m.mu.Lock()
			if t, ok := m.tasks[taskID]; ok {
				t.State = "COMPLETED"
				t.Data = fmt.Sprintf(`{"result":"processed: %s"}`, task.Message)
			}
			m.mu.Unlock()
		}()
		w.WriteHeader(http.StatusAccepted)
		json.NewEncoder(w).Encode(map[string]string{"taskId": taskID, "state": "SUBMITTED"})
	} else {
		task.State = "COMPLETED"
		task.Data = fmt.Sprintf(`{"result":"processed: %s"}`, task.Message)
		m.mu.Lock()
		m.tasks[taskID] = task
		m.mu.Unlock()
		json.NewEncoder(w).Encode(task)
	}
}

func (m *mockGateway) handleGetTask(w http.ResponseWriter, r *http.Request, path string) {
	taskID := strings.TrimPrefix(path, "/a2a/tasks/")
	m.mu.Lock()
	task, ok := m.tasks[taskID]
	m.mu.Unlock()
	if !ok {
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
		return
	}
	json.NewEncoder(w).Encode(task)
}

func (m *mockGateway) handleCancelTask(w http.ResponseWriter, r *http.Request, path string) {
	taskID := strings.TrimPrefix(path, "/a2a/tasks/")
	m.mu.Lock()
	defer m.mu.Unlock()
	_, ok := m.tasks[taskID]
	if !ok {
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
		return
	}
	if task, ok := m.tasks[taskID]; ok {
		task.State = "CANCELLED"
	}
	w.WriteHeader(http.StatusNoContent)
}

func (m *mockGateway) handleStream(w http.ResponseWriter, r *http.Request, path string) {
	taskID := strings.TrimSuffix(strings.TrimPrefix(path, "/a2a/tasks/"), "/stream")
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming not supported", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	for _, state := range []string{"SUBMITTED", "WORKING", "COMPLETED"} {
		data := map[string]string{
			"taskId": taskID,
			"state":  state,
		}
		if state == "COMPLETED" {
			m.mu.Lock()
			if task, ok := m.tasks[taskID]; ok {
				data["data"] = task.Data
			}
			m.mu.Unlock()
		}
		jsonData, _ := json.Marshal(data)
		fmt.Fprintf(w, "event: status\ndata: %s\n\n", jsonData)
		flusher.Flush()
		time.Sleep(100 * time.Millisecond)
	}
}

// =========================================================================
// Test: Client Creation & Config
// =========================================================================

func TestDefaultConfig(t *testing.T) {
	cfg := DefaultConfig()
	if cfg.GatewayURL != "http://localhost:10105" {
		t.Errorf("default GatewayURL = %s, want http://localhost:10105", cfg.GatewayURL)
	}
	if cfg.HeartbeatInterval != 30*time.Second {
		t.Errorf("default HeartbeatInterval = %v", cfg.HeartbeatInterval)
	}
	if cfg.RequestTimeout != 30*time.Second {
		t.Errorf("default RequestTimeout = %v", cfg.RequestTimeout)
	}
}

func TestNewClient(t *testing.T) {
	cfg := Config{
		GatewayURL: "http://localhost:9999",
		AgentName:  "test/test/agent-1",
		AgentCard: &AgentCard{
			Name:        "agent-1",
			Description: "Test agent",
			Version:     "1.0.0",
			Skills: []AgentSkill{
				{ID: "skill-1", Name: "Skill 1"},
			},
		},
		HeartbeatInterval: 5 * time.Second,
	}
	c := NewClient(cfg)
	if c.reg.OrgID != "test" || c.reg.UnitID != "test" || c.reg.AgentID != "agent-1" {
		t.Errorf("parsed agent name: got %s/%s/%s", c.reg.OrgID, c.reg.UnitID, c.reg.AgentID)
	}
	if c.cfg.AgentCard.Name != "agent-1" {
		t.Errorf("agent card name = %s", c.cfg.AgentCard.Name)
	}
	// Default interfaces should be added
	if len(c.cfg.AgentCard.Interfaces) != 1 {
		t.Errorf("expected 1 default interface, got %d", len(c.cfg.AgentCard.Interfaces))
	}
}

func TestNewClientWithMinimalConfig(t *testing.T) {
	cfg := Config{
		GatewayURL: "http://localhost:9999",
		AgentName:  "minimal",
	}
	c := NewClient(cfg)
	if c.cfg.HeartbeatInterval != 30*time.Second {
		t.Errorf("expected default heartbeat, got %v", c.cfg.HeartbeatInterval)
	}
	if c.cfg.RequestTimeout != 30*time.Second {
		t.Errorf("expected default timeout, got %v", c.cfg.RequestTimeout)
	}
	if c.cfg.AgentCard.Name != "minimal" {
		t.Errorf("expected card name minimal, got %s", c.cfg.AgentCard.Name)
	}
}

// =========================================================================
// Test: Lifecycle
// =========================================================================

func TestStartStop(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()

	c := NewClient(Config{
		GatewayURL:        gw.url(),
		AgentName:         "default/default/test-agent",
		HeartbeatInterval: 30 * time.Second,
	})

	if c.IsStarted() {
		t.Error("client should not be started initially")
	}

	err := c.Start()
	if err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	if !c.IsStarted() {
		t.Error("client should be started after Start()")
	}

	// Verify agent is registered in gateway
	agents, err := c.ListAgents()
	if err != nil {
		t.Fatalf("ListAgents() error: %v", err)
	}
	found := false
	for _, a := range agents {
		if a["name"] == "default/default/test-agent" {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("agent not found in gateway after registration")
	}

	c.Stop()
	if c.IsStarted() {
		t.Error("client should not be started after Stop()")
	}
}

func TestStartIdempotent(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()

	c := NewClient(Config{
		GatewayURL: gw.url(),
		AgentName:  "default/default/idempotent",
	})
	err := c.Start()
	if err != nil {
		t.Fatalf("first Start() error: %v", err)
	}
	err = c.Start()
	if err != nil {
		t.Fatalf("second Start() should not error: %v", err)
	}
	c.Stop()
}

func TestStopUnstarted(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()

	c := NewClient(Config{GatewayURL: gw.url(), AgentName: "test"})
	c.Stop() // should not panic
}

func TestStartConnectionRefused(t *testing.T) {
	c := NewClient(Config{
		GatewayURL:        "http://127.0.0.1:19999",
		AgentName:         "default/default/bad",
		HeartbeatInterval: 30 * time.Second,
		RequestTimeout:    1 * time.Second,
	})
	err := c.Start()
	if err == nil {
		t.Error("expected error for connection refused")
	}
	if c.IsStarted() {
		t.Error("should not be started after failed registration")
	}
}

// =========================================================================
// Test: Task Operations
// =========================================================================

func TestSendTaskSync(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/sync-agent")
	defer c.Stop()

	result, err := c.SendTask("weather-agent", "Shenzhen weather?")
	if err != nil {
		t.Fatalf("SendTask error: %v", err)
	}
	if result.State != "COMPLETED" {
		t.Errorf("expected COMPLETED, got %s", result.State)
	}
	if !strings.Contains(result.Data, "Shenzhen") {
		t.Errorf("result data missing task message: %s", result.Data)
	}
}

func TestSendTaskAsync(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/async-agent")
	defer c.Stop()

	taskID, err := c.SendTaskAsync("analyst", "Market analysis")
	if err != nil {
		t.Fatalf("SendTaskAsync error: %v", err)
	}
	if taskID == "" {
		t.Error("expected non-empty task ID")
	}
	if !strings.HasPrefix(taskID, "task-") {
		t.Errorf("unexpected task ID format: %s", taskID)
	}
}

func TestSendTaskAsyncWithParent(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/parent-agent")
	defer c.Stop()

	parentID, _ := c.SendTaskAsyncWithParent("orchestrator", "Parent task", "")
	if parentID == "" {
		t.Fatal("parent task ID is empty")
	}

	childID, err := c.SendTaskAsyncWithParent("worker", "Child task", parentID)
	if err != nil {
		t.Fatalf("child task error: %v", err)
	}
	if childID == "" {
		t.Error("child task ID is empty")
	}
	if childID == parentID {
		t.Error("child ID should differ from parent ID")
	}
}

func TestGetTaskStatus(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/status-agent")
	defer c.Stop()

	taskID, _ := c.SendTaskAsync("worker", "Test")
	result, err := c.GetTaskStatus(taskID)
	if err != nil {
		t.Fatalf("GetTaskStatus error: %v", err)
	}
	if result == nil || result.State == "" {
		t.Error("expected valid task result")
	}
}

func TestGetTaskStatusNonexistent(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/nonexistent")
	defer c.Stop()

	result, err := c.GetTaskStatus("ghost-999")
	if err != nil {
		t.Fatalf("GetTaskStatus unexpected error: %v", err)
	}
	if result != nil {
		t.Error("expected nil for non-existent task")
	}
}

func TestCancelTask(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/cancel-agent")
	defer c.Stop()

	// Submit async task (it completes in background)
	taskID, _ := c.SendTaskAsync("worker", "Gonna cancel")
	cancelled, err := c.CancelTask(taskID)
	if err != nil {
		t.Fatalf("CancelTask error: %v", err)
	}
	// Task may be already completed or cancelled
	if !cancelled {
		// Just verify no error and task exists
		_, err := c.GetTaskStatus(taskID)
		if err != nil {
			t.Logf("Warning: task %s not found after cancel: %v", taskID, err)
		}
	}
}

func TestCancelTaskNonexistent(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/cancel-nonexistent")
	defer c.Stop()

	cancelled, err := c.CancelTask("nonexistent-task")
	if err != nil {
		t.Fatalf("CancelTask for non-existent should not error: %v", err)
	}
	if cancelled {
		t.Error("expected false for non-existent task cancel")
	}
}

// =========================================================================
// Test: WaitForTask
// =========================================================================

func TestWaitForTask(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/wait-agent")
	defer c.Stop()

	taskID, _ := c.SendTaskAsync("worker", "Background job")
	result, err := c.WaitForTask(taskID, 100*time.Millisecond, 5*time.Second)
	if err != nil {
		t.Fatalf("WaitForTask error: %v", err)
	}
	if result.State != "COMPLETED" {
		t.Errorf("expected COMPLETED, got %s", result.State)
	}
}

func TestWaitForTaskTimeout(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/timeout-agent")
	defer c.Stop()

	_, err := c.WaitForTask("nonexistent-task", 50*time.Millisecond, 200*time.Millisecond)
	if err == nil {
		t.Error("expected timeout error")
	}
}

// =========================================================================
// Test: SSE Streaming
// =========================================================================

func TestStreamTask(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/stream-agent")
	defer c.Stop()

	taskID, _ := c.SendTaskAsync("analyst", "Streamed analysis")
	states := make([]string, 0)
	done := make(chan struct{})
	errCh := make(chan error, 1)

	go func() {
		err := c.StreamTask(taskID, func(tid, state, data string) bool {
			states = append(states, state)
			if state == "COMPLETED" {
				done <- struct{}{}
				return false
			}
			return true
		}, 10*time.Second)
		if err != nil {
			errCh <- err
		}
	}()

	select {
	case <-done:
		if len(states) < 3 {
			t.Errorf("expected at least 3 SSE events, got %d: %v", len(states), states)
		}
		hasSubmitted := false
		hasCompleted := false
		for _, s := range states {
			if s == "SUBMITTED" {
				hasSubmitted = true
			}
			if s == "COMPLETED" {
				hasCompleted = true
			}
		}
		if !hasSubmitted {
			t.Error("expected SUBMITTED event")
		}
		if !hasCompleted {
			t.Error("expected COMPLETED event")
		}
	case err := <-errCh:
		t.Fatalf("StreamTask error: %v", err)
	case <-time.After(5 * time.Second):
		t.Fatal("timed out waiting for stream")
	}
}

// =========================================================================
// Test: Discovery
// =========================================================================

func TestHealthCheck(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/health-agent")
	defer c.Stop()

	health, err := c.HealthCheck()
	if err != nil {
		t.Fatalf("HealthCheck error: %v", err)
	}
	if health["status"] != "healthy" {
		t.Errorf("expected healthy, got %v", health["status"])
	}
}

func TestListAgents(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/list-agent")
	defer c.Stop()

	agents, err := c.ListAgents()
	if err != nil {
		t.Fatalf("ListAgents error: %v", err)
	}
	if len(agents) < 1 {
		t.Errorf("expected at least 1 agent, got %d", len(agents))
	}
}

func TestGetAgentCard(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/card-agent")
	defer c.Stop()

	card, err := c.GetAgentCard("default", "default", "card-agent")
	if err != nil {
		t.Fatalf("GetAgentCard error: %v", err)
	}
	if card["name"] != "card-agent" {
		t.Errorf("expected card name 'card-agent', got %v", card["name"])
	}
}

func TestGetAgentCardNotFound(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/finder")
	defer c.Stop()

	_, err := c.GetAgentCard("ghost", "ghost", "ghost")
	if err == nil {
		t.Error("expected error for non-existent agent card")
	}
}

// =========================================================================
// Test: AgentCard & Config Models
// =========================================================================

func TestAgentCardOpenClawStyle(t *testing.T) {
	card := &AgentCard{
		Name:        "openclaw-agent",
		Description: "Multi-agent orchestration system",
		Version:     "1.0.0",
		Skills: []AgentSkill{
			{ID: "multi-agent-orchestration", Name: "Multi-Agent Orchestration"},
			{ID: "task-decomposition", Name: "Task Decomposition"},
			{ID: "agent-routing", Name: "Agent Routing"},
			{ID: "workflow-execution", Name: "Workflow Execution"},
		},
		DefaultInputModes:  []string{"text/plain", "application/json"},
		DefaultOutputModes: []string{"text/plain", "application/json"},
	}
	if len(card.Skills) != 4 {
		t.Errorf("expected 4 skills, got %d", len(card.Skills))
	}
	if card.DefaultInputModes[0] != "text/plain" {
		t.Errorf("expected text/plain input, got %s", card.DefaultInputModes[0])
	}
}

func TestTaskStateIsTerminal(t *testing.T) {
	tests := []struct {
		state    TaskState
		terminal bool
	}{
		{StateSubmitted, false},
		{StateWorking, false},
		{StateCompleted, true},
		{StateFailed, true},
		{StateCancelled, true},
	}
	for _, tt := range tests {
		if tt.state.IsTerminal() != tt.terminal {
			t.Errorf("%s.IsTerminal() = %v, want %v", tt.state, tt.state.IsTerminal(), tt.terminal)
		}
	}
}

func TestTaskResultIsTerminal(t *testing.T) {
	r := &TaskResult{State: "COMPLETED"}
	if !r.IsTerminal() {
		t.Error("COMPLETED should be terminal")
	}
	r.State = "WORKING"
	if r.IsTerminal() {
		t.Error("WORKING should not be terminal")
	}
}

// =========================================================================
// Test: Request Handler
// =========================================================================

func TestSetRequestHandler(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := NewClient(Config{
		GatewayURL: gw.url(),
		AgentName:  "default/default/handler-agent",
	})
	called := false
	c.SetRequestHandler(func(taskID, message string) (string, error) {
		called = true
		return `{"status":"ok"}`, nil
	})
	if !called {
		// Handler is stored, not called here; just verify no panic
	}
	// Ensure handler is accessible
	c.mu.RLock()
	hasHandler := c.handler != nil
	c.mu.RUnlock()
	if !hasHandler {
		t.Error("handler should be set after SetRequestHandler")
	}
}

// =========================================================================
// Test: Error Handling
// =========================================================================

func TestAgentMeshError(t *testing.T) {
	e := &AgentMeshError{Code: 404, Message: "not found"}
	if !strings.Contains(e.Error(), "404") {
		t.Errorf("error message should contain 404: %s", e.Error())
	}

	e.Code = 0
	e.Message = "connection refused"
	if !strings.Contains(e.Error(), "connection refused") {
		t.Errorf("error message: %s", e.Error())
	}
}

// =========================================================================
// Test: Concurrent Safety
// =========================================================================

func TestConcurrentStartStop(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := NewClient(Config{
		GatewayURL: gw.url(),
		AgentName:  "default/default/concurrent",
	})

	var wg sync.WaitGroup
	for i := 0; i < 5; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_ = c.Start()
		}()
	}
	wg.Wait()

	if !c.IsStarted() {
		t.Error("client should be started after concurrent starts")
	}

	for i := 0; i < 5; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			c.Stop()
		}()
	}
	wg.Wait()
}

func TestConcurrentSendTasks(t *testing.T) {
	gw := newMockGateway()
	defer gw.close()
	c := newStartedClient(t, gw, "default/default/concurrent-tasks")
	defer c.Stop()

	var wg sync.WaitGroup
	errs := make(chan error, 3)
	for i := 0; i < 3; i++ {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			_, err := c.SendTask("agent", fmt.Sprintf("task %d", idx))
			if err != nil {
				errs <- err
			}
		}(i)
	}
	wg.Wait()
	close(errs)

	for err := range errs {
		t.Errorf("concurrent task error: %v", err)
	}
}

// =========================================================================
// Helpers
// =========================================================================

func newStartedClient(t *testing.T, gw *mockGateway, agentName string) *Client {
	t.Helper()
	parts := strings.SplitN(agentName, "/", 3)
	agentID := agentName
	if len(parts) == 3 {
		agentID = parts[2]
	}
	c := NewClient(Config{
		GatewayURL:        gw.url(),
		AgentName:         agentName,
		HeartbeatInterval: 30 * time.Second,
		AgentCard: &AgentCard{
			Name:        agentID,
			Description: "Test agent for " + agentName,
			Version:     "1.0.0",
			Skills: []AgentSkill{
				{ID: "test", Name: "Test Skill"},
			},
		},
	})
	if err := c.Start(); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	return c
}
