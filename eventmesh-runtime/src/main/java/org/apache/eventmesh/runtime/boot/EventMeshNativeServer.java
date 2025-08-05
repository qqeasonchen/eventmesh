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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.runtime.core.protocol.native.NativeMessageHandler;
import org.apache.eventmesh.runtime.core.protocol.native.NativeProtocolManager;
import org.apache.eventmesh.runtime.core.protocol.native.config.NativeProtocolConfig;
import org.apache.eventmesh.runtime.core.protocol.native.server.NativeProtocolServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EventMesh Native Protocol Server for supporting native Kafka, Pulsar, and RocketMQ clients.
 * Integrates native protocol support into EventMesh startup process.
 */
public class EventMeshNativeServer {

    private static final Logger log = LoggerFactory.getLogger(EventMeshNativeServer.class);

    private final NativeProtocolConfig config;
    private final NativeProtocolManager protocolManager;
    private final NativeProtocolServer protocolServer;
    private final ExecutorService executorService;
    private volatile boolean started = false;

    public EventMeshNativeServer(Producer producer) {
        this.config = new NativeProtocolConfig();
        this.protocolManager = new NativeProtocolManager(producer, createMessageHandler());
        this.protocolServer = new NativeProtocolServer(protocolManager);
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public EventMeshNativeServer(Producer producer, NativeProtocolConfig config) {
        this.config = config;
        this.protocolManager = new NativeProtocolManager(producer, createMessageHandler());
        this.protocolServer = new NativeProtocolServer(protocolManager);
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Start the native protocol server
     */
    public void start() throws Exception {
        if (started) {
            log.warn("EventMesh Native Server is already started");
            return;
        }

        if (!config.isEnabled()) {
            log.info("Native protocol support is disabled");
            return;
        }

        log.info("Starting EventMesh Native Server");

        try {
            // Start protocol manager
            protocolManager.start();
            log.info("Native Protocol Manager started successfully");

            // Start protocol server
            CompletableFuture.runAsync(() -> {
                try {
                    if (config.getHost() != null && config.getPort() > 0) {
                        protocolServer.start(config.getHost(), config.getPort());
                    } else {
                        protocolServer.start(config.getPort());
                    }
                } catch (Exception e) {
                    log.error("Failed to start native protocol server", e);
                }
            }, executorService);

            started = true;
            log.info("EventMesh Native Server started successfully");
        } catch (Exception e) {
            log.error("Failed to start EventMesh Native Server", e);
            throw e;
        }
    }

    /**
     * Shutdown the native protocol server
     */
    public void shutdown() {
        if (!started) {
            log.warn("EventMesh Native Server is not started");
            return;
        }

        log.info("Shutting down EventMesh Native Server");

        try {
            // Shutdown protocol server
            protocolServer.shutdown();
            log.info("Native Protocol Server shutdown completed");

            // Shutdown protocol manager
            protocolManager.shutdown();
            log.info("Native Protocol Manager shutdown completed");

            // Shutdown executor service
            executorService.shutdown();
            log.info("Executor Service shutdown completed");

            started = false;
            log.info("EventMesh Native Server shutdown completed");
        } catch (Exception e) {
            log.error("Error during EventMesh Native Server shutdown", e);
        }
    }

    /**
     * Create message handler for native protocols
     */
    private NativeMessageHandler createMessageHandler() {
        return new NativeMessageHandler() {
            @Override
            public void handleMessage(org.apache.eventmesh.common.protocol.ProtocolTransportObject message) {
                log.debug("Handling native message: {}", message.getClass().getSimpleName());
                // Implement native message handling logic
            }

            @Override
            public void handleCloudEvent(io.cloudevents.CloudEvent cloudEvent) {
                log.debug("Handling CloudEvent: {}", cloudEvent.getId());
                // Implement CloudEvent handling logic
            }
        };
    }

    /**
     * Check if server is started
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Get server statistics
     */
    public ServerStats getStats() {
        return new ServerStats(
            started,
            protocolServer.getStats(),
            protocolManager.getStats()
        );
    }

    /**
     * Get protocol manager
     */
    public NativeProtocolManager getProtocolManager() {
        return protocolManager;
    }

    /**
     * Get configuration
     */
    public NativeProtocolConfig getConfig() {
        return config;
    }

    /**
     * Server statistics
     */
    public static class ServerStats {
        private final boolean isStarted;
        private final NativeProtocolServer.NativeProtocolServerStats serverStats;
        private final NativeProtocolManager.NativeProtocolManagerStats managerStats;

        public ServerStats(boolean isStarted, 
                         NativeProtocolServer.NativeProtocolServerStats serverStats,
                         NativeProtocolManager.NativeProtocolManagerStats managerStats) {
            this.isStarted = isStarted;
            this.serverStats = serverStats;
            this.managerStats = managerStats;
        }

        public boolean isStarted() { return isStarted; }
        public NativeProtocolServer.NativeProtocolServerStats getServerStats() { return serverStats; }
        public NativeProtocolManager.NativeProtocolManagerStats getManagerStats() { return managerStats; }
    }
} 