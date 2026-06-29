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

package org.apache.eventmesh.protocol.a2a.workflow;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A2A-style facade for invoking workflows through {@link WorkflowClient}.
 */
public class WorkflowA2AClient implements Closeable {

    private final WorkflowClient workflowClient;

    private WorkflowA2AClient(Builder builder) {
        this.workflowClient = builder.workflowClient != null
            ? builder.workflowClient
            : WorkflowClient.builder().baseUrl(builder.workflowServiceUrl).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public WorkflowClient.WorkflowTaskResponse sendMessage(String workflowId, String text) throws IOException {
        WorkflowClient.WorkflowTaskRequest request = new WorkflowClient.WorkflowTaskRequest();
        request.setId(UUID.randomUUID().toString());
        request.setMessage(textMessage("user", text));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("workflowId", workflowId);
        request.setMetadata(metadata);

        return workflowClient.startWorkflow(request);
    }

    public WorkflowClient.WorkflowTaskResponse getTask(String taskId) throws IOException {
        return workflowClient.getWorkflowTask(taskId);
    }

    public WorkflowClient.AgentCard getAgentCard() throws IOException {
        return workflowClient.getAgentCard();
    }

    @Override
    public void close() throws IOException {
        workflowClient.close();
    }

    private WorkflowClient.Message textMessage(String role, String text) {
        WorkflowClient.Part part = new WorkflowClient.Part();
        part.setType("text");
        part.setText(text);

        List<WorkflowClient.Part> parts = new ArrayList<>();
        parts.add(part);

        WorkflowClient.Message message = new WorkflowClient.Message();
        message.setRole(role);
        message.setParts(parts);
        return message;
    }

    public static class Builder {

        private String workflowServiceUrl = "http://localhost:9090";
        private WorkflowClient workflowClient;

        public Builder workflowServiceUrl(String workflowServiceUrl) {
            this.workflowServiceUrl = workflowServiceUrl;
            return this;
        }

        public Builder workflowClient(WorkflowClient workflowClient) {
            this.workflowClient = workflowClient;
            return this;
        }

        public WorkflowA2AClient build() {
            return new WorkflowA2AClient(this);
        }
    }
}
