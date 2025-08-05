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

package org.apache.eventmesh.runtime.core.protocol.native;

import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.runtime.core.protocol.native.kafka.NativeKafkaConnector;
import org.apache.eventmesh.runtime.core.protocol.native.pulsar.NativePulsarConnector;
import org.apache.eventmesh.runtime.core.protocol.native.rocketmq.NativeRocketMQConnector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native protocol manager for managing all native client connectors.
 * Provides unified interface for native Kafka, Pulsar, and RocketMQ client communication.
 */
public class NativeProtocolManager {

    private static final Logger log = LoggerFactory.getLogger(NativeProtocolManager.class);

    private final Map<String, NativeConnector> connectors = new ConcurrentHashMap<>();
    private final Producer producer;
    private final NativeMessageHandler messageHandler;
    private boolean started = false;

    public NativeProtocolManager(Producer producer, NativeMessageHandler messageHandler) {
        this.producer = producer;
        this.messageHandler = messageHandler;
    }

    /**
     * Start all native connectors
     */
    public void start() throws Exception {
        if (started) {
            log.warn("Native protocol manager is already started");
            return;
        }

        log.info("Starting Native Protocol Manager");

        // Initialize native connectors
        initializeConnectors();

        // Start all connectors
        for (Map.Entry<String, NativeConnector> entry : connectors.entrySet()) {
            try {
                entry.getValue().start();
                log.info("Started native connector: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Failed to start native connector: {}", entry.getKey(), e);
                throw e;
            }
        }

        started = true;
        log.info("Native Protocol Manager started successfully");
    }

    /**
     * Shutdown all native connectors
     */
    public void shutdown() throws Exception {
        if (!started) {
            log.warn("Native protocol manager is not started");
            return;
        }

        log.info("Shutting down Native Protocol Manager");

        // Shutdown all connectors
        for (Map.Entry<String, NativeConnector> entry : connectors.entrySet()) {
            try {
                entry.getValue().shutdown();
                log.info("Shutdown native connector: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Failed to shutdown native connector: {}", entry.getKey(), e);
            }
        }

        connectors.clear();
        started = false;
        log.info("Native Protocol Manager shutdown successfully");
    }

    /**
     * Initialize native connectors
     */
    private void initializeConnectors() {
        // Initialize Kafka connector
        try {
            NativeKafkaConnector kafkaConnector = new NativeKafkaConnector(producer, messageHandler);
            connectors.put("kafka", kafkaConnector);
            log.info("Initialized Kafka native connector");
        } catch (Exception e) {
            log.error("Failed to initialize Kafka native connector", e);
        }

        // Initialize Pulsar connector
        try {
            NativePulsarConnector pulsarConnector = new NativePulsarConnector(producer, messageHandler);
            connectors.put("pulsar", pulsarConnector);
            log.info("Initialized Pulsar native connector");
        } catch (Exception e) {
            log.error("Failed to initialize Pulsar native connector", e);
        }

        // Initialize RocketMQ connector
        try {
            NativeRocketMQConnector rocketmqConnector = new NativeRocketMQConnector(producer, messageHandler);
            connectors.put("rocketmq", rocketmqConnector);
            log.info("Initialized RocketMQ native connector");
        } catch (Exception e) {
            log.error("Failed to initialize RocketMQ native connector", e);
        }
    }

    /**
     * Get native connector by type
     *
     * @param connectorType connector type (kafka, pulsar, rocketmq)
     * @return native connector
     */
    public NativeConnector getConnector(String connectorType) {
        return connectors.get(connectorType);
    }

    /**
     * Get Kafka connector
     */
    public NativeKafkaConnector getKafkaConnector() {
        return (NativeKafkaConnector) connectors.get("kafka");
    }

    /**
     * Get Pulsar connector
     */
    public NativePulsarConnector getPulsarConnector() {
        return (NativePulsarConnector) connectors.get("pulsar");
    }

    /**
     * Get RocketMQ connector
     */
    public NativeRocketMQConnector getRocketMQConnector() {
        return (NativeRocketMQConnector) connectors.get("rocketmq");
    }

    /**
     * Check if connector is available
     *
     * @param connectorType connector type
     * @return true if connector is available
     */
    public boolean isConnectorAvailable(String connectorType) {
        NativeConnector connector = connectors.get(connectorType);
        return connector != null && connector.isRunning();
    }

    /**
     * Get all available connector types
     *
     * @return available connector types
     */
    public String[] getAvailableConnectorTypes() {
        return connectors.keySet().toArray(new String[0]);
    }

    /**
     * Get connector statistics
     *
     * @param connectorType connector type
     * @return connector statistics
     */
    public NativeConnector.NativeConnectorStats getConnectorStats(String connectorType) {
        NativeConnector connector = connectors.get(connectorType);
        return connector != null ? connector.getStats() : null;
    }

    /**
     * Check if manager is started
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Get manager statistics
     */
    public NativeProtocolManagerStats getStats() {
        int totalConnectors = connectors.size();
        int runningConnectors = 0;
        int totalPendingMessages = 0;

        for (NativeConnector connector : connectors.values()) {
            if (connector.isRunning()) {
                runningConnectors++;
            }
            NativeConnector.NativeConnectorStats stats = connector.getStats();
            if (stats != null) {
                totalPendingMessages += stats.getPendingMessageCount();
            }
        }

        return new NativeProtocolManagerStats(
            totalConnectors,
            runningConnectors,
            totalPendingMessages,
            started
        );
    }

    /**
     * Manager statistics
     */
    public static class NativeProtocolManagerStats {
        private final int totalConnectors;
        private final int runningConnectors;
        private final int totalPendingMessages;
        private final boolean isStarted;

        public NativeProtocolManagerStats(int totalConnectors, int runningConnectors, 
                                        int totalPendingMessages, boolean isStarted) {
            this.totalConnectors = totalConnectors;
            this.runningConnectors = runningConnectors;
            this.totalPendingMessages = totalPendingMessages;
            this.isStarted = isStarted;
        }

        public int getTotalConnectors() { return totalConnectors; }
        public int getRunningConnectors() { return runningConnectors; }
        public int getTotalPendingMessages() { return totalPendingMessages; }
        public boolean isStarted() { return isStarted; }
    }
} 