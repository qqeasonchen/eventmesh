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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class WorkflowClientTest {

    @Test
    void testSaveWorkflowPostsWrappedWorkflowBody() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        try (MockWorkflowServer server = new MockWorkflowServer()) {
            server.add("/workflow", exchange -> {
                bodyRef.set(readBody(exchange));
                writeJson(exchange, 200, "null");
            });
            server.start();

            WorkflowClient.Workflow workflow = new WorkflowClient.Workflow();
            workflow.setWorkflowId("wf-1");
            workflow.setWorkflowName("Greeting Workflow");
            workflow.setDefinition("document:\n  dsl: '1.0.3'");
            workflow.setVersion("1.0.0");

            try (WorkflowClient client = WorkflowClient.builder().baseUrl(server.baseUrl()).build()) {
                client.saveWorkflow(workflow);
            }
        }

        assertNotNull(bodyRef.get());
        assertTrue(bodyRef.get().contains("\"workflow\""));
        assertTrue(bodyRef.get().contains("\"workflow_id\":\"wf-1\""));
    }

    @Test
    void testQueryWorkflowsBuildsExpectedQuery() throws Exception {
        AtomicReference<String> pathRef = new AtomicReference<>();
        try (MockWorkflowServer server = new MockWorkflowServer()) {
            server.add("/workflow", exchange -> {
                pathRef.set(exchange.getRequestURI().toString());
                writeJson(exchange, 200, "{\"total\":1,\"workflows\":[{\"workflow_id\":\"wf-1\"}]}" );
            });
            server.start();

            WorkflowClient.QueryWorkflowsRequest request = new WorkflowClient.QueryWorkflowsRequest();
            request.setWorkflowId("wf-1");
            request.setStatus(1);
            request.setPage(2);
            request.setSize(20);

            WorkflowClient.QueryWorkflowsResponse response;
            try (WorkflowClient client = WorkflowClient.builder().baseUrl(server.baseUrl()).build()) {
                response = client.queryWorkflows(request);
            }

            assertEquals(1, response.getTotal());
            assertEquals("wf-1", response.getWorkflows().get(0).getWorkflowId());
        }

        assertEquals("/workflow?workflow_id=wf-1&status=1&page=2&size=20", pathRef.get());
    }

    @Test
    void testStartWorkflowUsesA2ATaskEndpoint() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        try (MockWorkflowServer server = new MockWorkflowServer()) {
            server.add("/a2a/tasks", exchange -> {
                bodyRef.set(readBody(exchange));
                writeJson(exchange, 200, "{\"id\":\"task-1\",\"status\":\"working\"}");
            });
            server.start();

            WorkflowClient.WorkflowTaskRequest request = new WorkflowClient.WorkflowTaskRequest();
            request.setId("task-1");
            WorkflowClient.Message message = new WorkflowClient.Message();
            message.setRole("user");
            request.setMessage(message);

            WorkflowClient.WorkflowTaskResponse response;
            try (WorkflowClient client = WorkflowClient.builder().baseUrl(server.baseUrl()).build()) {
                response = client.startWorkflow(request);
            }

            assertEquals("task-1", response.getId());
            assertEquals("working", response.getStatus());
        }

        assertNotNull(bodyRef.get());
        assertTrue(bodyRef.get().contains("\"id\":\"task-1\""));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static class MockWorkflowServer implements AutoCloseable {

        private final HttpServer server;

        MockWorkflowServer() throws IOException {
            server = HttpServer.create(new java.net.InetSocketAddress(0), 0);
        }

        void add(String path, Handler handler) {
            server.createContext(path, exchange -> handler.handle(exchange));
        }

        void start() {
            server.start();
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface Handler {

        void handle(HttpExchange exchange) throws IOException;
    }
}
