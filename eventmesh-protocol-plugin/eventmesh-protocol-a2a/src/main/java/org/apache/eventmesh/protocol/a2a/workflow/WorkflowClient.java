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

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Java client for the standalone EventMesh Workflow service.
 *
 * <p>The workflow engine remains an independent service. EventMesh Java modules use this
 * client to create workflows, start workflow-backed A2A tasks and query workflow state
 * through the workflow service REST/A2A endpoints.
 */
public class WorkflowClient implements Closeable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

    private static final TypeReference<QueryWorkflowsResponse> QUERY_WORKFLOWS_TYPE =
        new TypeReference<QueryWorkflowsResponse>() {
        };

    private static final TypeReference<QueryWorkflowInstancesResponse> QUERY_WORKFLOW_INSTANCES_TYPE =
        new TypeReference<QueryWorkflowInstancesResponse>() {
        };

    private final String baseUrl;
    private final CloseableHttpClient httpClient;

    private WorkflowClient(Builder builder) {
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(builder.connectTimeoutMs)
            .setSocketTimeout(builder.socketTimeoutMs)
            .build();
        this.httpClient = builder.httpClient != null
            ? builder.httpClient
            : HttpClients.custom().setDefaultRequestConfig(config).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public void saveWorkflow(Workflow workflow) throws IOException {
        Objects.requireNonNull(workflow, "workflow");
        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);
        executePost("/workflow", request, Void.class);
    }

    public QueryWorkflowsResponse queryWorkflows(QueryWorkflowsRequest request) throws IOException {
        QueryWorkflowsRequest query = request == null ? new QueryWorkflowsRequest() : request;
        return executeGet(buildWorkflowQueryPath(query), QUERY_WORKFLOWS_TYPE);
    }

    public WorkflowResponse getWorkflow(String workflowId) throws IOException {
        requireText(workflowId, "workflowId");
        return executeGet("/workflow/" + encodePath(workflowId), WorkflowResponse.class);
    }

    public void deleteWorkflow(String workflowId) throws IOException {
        requireText(workflowId, "workflowId");
        executeDelete("/workflow/" + encodePath(workflowId));
    }

    public QueryWorkflowInstancesResponse queryWorkflowInstances(QueryWorkflowInstancesRequest request) throws IOException {
        QueryWorkflowInstancesRequest query = request == null ? new QueryWorkflowInstancesRequest() : request;
        return executeGet(buildWorkflowInstancesQueryPath(query), QUERY_WORKFLOW_INSTANCES_TYPE);
    }

    /**
     * Starts a workflow through the workflow service A2A-compatible endpoint.
     *
     * <p>The Go workflow service exposes workflow instances as A2A tasks:
     * {@code POST /a2a/tasks -> GET /a2a/tasks/{id}}.
     */
    public WorkflowTaskResponse startWorkflow(WorkflowTaskRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        return executePost("/a2a/tasks", request, WorkflowTaskResponse.class);
    }

    public WorkflowTaskResponse getWorkflowTask(String taskId) throws IOException {
        requireText(taskId, "taskId");
        return executeGet("/a2a/tasks/" + encodePath(taskId), WorkflowTaskResponse.class);
    }

    public AgentCard getAgentCard() throws IOException {
        return executeGet("/.well-known/agent-card.json", AgentCard.class);
    }

    public boolean health() throws IOException {
        HttpResponse response = executeGetRaw("/a2a/health");
        consume(response);
        return response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300;
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private <T> T executeGet(String path, Class<T> responseType) throws IOException {
        HttpResponse response = executeGetRaw(path);
        return readResponse(response, responseType);
    }

    private <T> T executeGet(String path, TypeReference<T> responseType) throws IOException {
        HttpResponse response = executeGetRaw(path);
        return readResponse(response, responseType);
    }

    private HttpResponse executeGetRaw(String path) throws IOException {
        HttpGet get = new HttpGet(baseUrl + path);
        get.setHeader("Accept", "application/json");
        return httpClient.execute(get);
    }

    private <T> T executePost(String path, Object body, Class<T> responseType) throws IOException {
        HttpPost post = new HttpPost(baseUrl + path);
        post.setHeader("Content-Type", "application/json");
        post.setHeader("Accept", "application/json");
        post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
        HttpResponse response = httpClient.execute(post);
        return readResponse(response, responseType);
    }

    private void executeDelete(String path) throws IOException {
        HttpDelete delete = new HttpDelete(baseUrl + path);
        HttpResponse response = httpClient.execute(delete);
        readResponse(response, Void.class);
    }

    private <T> T readResponse(HttpResponse response, Class<T> responseType) throws IOException {
        String body = response.getEntity() == null
            ? ""
            : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        assertSuccess(response, body);
        if (responseType == Void.class || body.isEmpty()) {
            return null;
        }
        return OBJECT_MAPPER.readValue(body, responseType);
    }

    private <T> T readResponse(HttpResponse response, TypeReference<T> responseType) throws IOException {
        String body = response.getEntity() == null
            ? ""
            : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        assertSuccess(response, body);
        if (body.isEmpty()) {
            return null;
        }
        return OBJECT_MAPPER.readValue(body, responseType);
    }

    private void assertSuccess(HttpResponse response, String body) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 400) {
            throw new IOException("Workflow service request failed: " + statusCode + " " + body);
        }
    }

    private void consume(HttpResponse response) throws IOException {
        if (response.getEntity() != null) {
            EntityUtils.consume(response.getEntity());
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        requireText(baseUrl, "baseUrl");
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String encodePath(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String buildWorkflowQueryPath(QueryWorkflowsRequest request) {
        QueryBuilder query = new QueryBuilder("/workflow");
        query.add("workflow_id", request.getWorkflowId());
        query.add("status", request.getStatus());
        query.add("page", request.getPage());
        query.add("size", request.getSize());
        return query.build();
    }

    private static String buildWorkflowInstancesQueryPath(QueryWorkflowInstancesRequest request) {
        QueryBuilder query = new QueryBuilder("/workflow/instances");
        query.add("workflow_id", request.getWorkflowId());
        query.add("page", request.getPage());
        query.add("size", request.getSize());
        return query.build();
    }

    public static class Builder {

        private String baseUrl = "http://localhost:8080";
        private int connectTimeoutMs = 10_000;
        private int socketTimeoutMs = 30_000;
        private CloseableHttpClient httpClient;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder socketTimeoutMs(int socketTimeoutMs) {
            this.socketTimeoutMs = socketTimeoutMs;
            return this;
        }

        public Builder httpClient(CloseableHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public WorkflowClient build() {
            return new WorkflowClient(this);
        }
    }

    private static class QueryBuilder {

        private final String path;
        private final StringBuilder query = new StringBuilder();

        QueryBuilder(String path) {
            this.path = path;
        }

        void add(String name, String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            addRaw(name, value);
        }

        void add(String name, int value) {
            if (value <= 0) {
                return;
            }
            addRaw(name, String.valueOf(value));
        }

        private void addRaw(String name, String value) {
            if (query.length() == 0) {
                query.append('?');
            } else {
                query.append('&');
            }
            query.append(encodePath(name)).append('=').append(encodePath(value));
        }

        String build() {
            return path + query;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Workflow {

        private int id;
        private String workflowId;
        private String workflowName;
        private String definition;
        private int status;
        private String version;
        private int totalInstances;
        private int totalRunningInstances;
        private int totalFailedInstances;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        public String getWorkflowName() {
            return workflowName;
        }

        public void setWorkflowName(String workflowName) {
            this.workflowName = workflowName;
        }

        public String getDefinition() {
            return definition;
        }

        public void setDefinition(String definition) {
            this.definition = definition;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public int getTotalInstances() {
            return totalInstances;
        }

        public void setTotalInstances(int totalInstances) {
            this.totalInstances = totalInstances;
        }

        public int getTotalRunningInstances() {
            return totalRunningInstances;
        }

        public void setTotalRunningInstances(int totalRunningInstances) {
            this.totalRunningInstances = totalRunningInstances;
        }

        public int getTotalFailedInstances() {
            return totalFailedInstances;
        }

        public void setTotalFailedInstances(int totalFailedInstances) {
            this.totalFailedInstances = totalFailedInstances;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowInstance {

        private int id;
        private String workflowId;
        private String workflowInstanceId;
        private int workflowStatus;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        public String getWorkflowInstanceId() {
            return workflowInstanceId;
        }

        public void setWorkflowInstanceId(String workflowInstanceId) {
            this.workflowInstanceId = workflowInstanceId;
        }

        public int getWorkflowStatus() {
            return workflowStatus;
        }

        public void setWorkflowStatus(int workflowStatus) {
            this.workflowStatus = workflowStatus;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowResponse {

        private Workflow workflow;

        public Workflow getWorkflow() {
            return workflow;
        }

        public void setWorkflow(Workflow workflow) {
            this.workflow = workflow;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryWorkflowsResponse {

        private List<Workflow> workflows;
        private int total;

        public List<Workflow> getWorkflows() {
            return workflows;
        }

        public void setWorkflows(List<Workflow> workflows) {
            this.workflows = workflows;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    public static class QueryWorkflowsRequest {

        private String workflowId;
        private int status;
        private int page;
        private int size;

        public String getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryWorkflowInstancesResponse {

        private List<WorkflowInstance> workflowInstances;
        private int total;

        public List<WorkflowInstance> getWorkflowInstances() {
            return workflowInstances;
        }

        public void setWorkflowInstances(List<WorkflowInstance> workflowInstances) {
            this.workflowInstances = workflowInstances;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    public static class QueryWorkflowInstancesRequest {

        private String workflowId;
        private int page;
        private int size;

        public String getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    public static class WorkflowTaskRequest {

        private String id;
        private Message message;
        private Map<String, Object> metadata;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowTaskResponse {

        private String id;
        private String status;
        private Message message;
        private List<Artifact> artifacts;
        private ErrorInfo error;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public List<Artifact> getArtifacts() {
            return artifacts;
        }

        public void setArtifacts(List<Artifact> artifacts) {
            this.artifacts = artifacts;
        }

        public ErrorInfo getError() {
            return error;
        }

        public void setError(ErrorInfo error) {
            this.error = error;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentCard {

        private String name;
        private String description;
        private String url;
        private String version;
        private Map<String, Object> metadata;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {

        private String role;
        private List<Part> parts;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Part {

        private String type;
        private String text;
        private Map<String, Object> data;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Artifact {

        private String name;
        private List<Part> parts;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorInfo {

        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
