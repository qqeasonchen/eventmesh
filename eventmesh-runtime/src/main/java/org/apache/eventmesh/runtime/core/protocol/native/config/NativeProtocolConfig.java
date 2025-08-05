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

package org.apache.eventmesh.runtime.core.protocol.native.config;

import lombok.Data;

/**
 * Configuration for native protocol support.
 * Manages settings for Kafka, Pulsar, and RocketMQ native client connections.
 */
@Data
public class NativeProtocolConfig {

    // General native protocol settings
    private boolean enabled = true;
    private String host = "0.0.0.0";
    private int port = 9092;
    private int connectionTimeoutMs = 30000;
    private int keepaliveMs = 60000;
    private int maxConnections = 1000;
    private int maxMessageSize = 1024 * 1024; // 1MB

    // Kafka native settings
    private boolean kafkaEnabled = true;
    private int kafkaPort = 9092;
    private int kafkaMaxMessageSize = 1024 * 1024; // 1MB
    private int kafkaRequestTimeoutMs = 30000;
    private int kafkaSessionTimeoutMs = 10000;
    private int kafkaHeartbeatIntervalMs = 3000;

    // Pulsar native settings
    private boolean pulsarEnabled = true;
    private int pulsarPort = 6650;
    private int pulsarMaxMessageSize = 5 * 1024 * 1024; // 5MB
    private int pulsarConnectionTimeoutMs = 10000;
    private int pulsarOperationTimeoutMs = 30000;
    private int pulsarKeepAliveIntervalMs = 30000;

    // RocketMQ native settings
    private boolean rocketmqEnabled = true;
    private int rocketmqPort = 9876;
    private int rocketmqMaxMessageSize = 4 * 1024 * 1024; // 4MB
    private int rocketmqSendMsgTimeoutMs = 3000;
    private int rocketmqHeartbeatBrokerIntervalMs = 30000;
    private int rocketmqPersistConsumerOffsetIntervalMs = 5000;

    // Transmission optimization settings
    private boolean transmissionOptimizationEnabled = true;
    private int batchSize = 1000;
    private int transmissionTimeoutMs = 5000;
    private boolean zeroCopyEnabled = true;
    private boolean asyncProcessingEnabled = true;

    // Connection pool settings
    private int connectionPoolSize = 100;
    private int connectionPoolMaxWaitMs = 5000;
    private int connectionPoolMinIdle = 10;
    private int connectionPoolMaxIdle = 50;

    // Security settings
    private boolean sslEnabled = false;
    private String sslKeyStorePath;
    private String sslKeyStorePassword;
    private String sslTrustStorePath;
    private String sslTrustStorePassword;
    private String sslProtocol = "TLS";

    // Authentication settings
    private boolean authenticationEnabled = false;
    private String authenticationType = "none"; // none, sasl, oauth2
    private String saslMechanism = "PLAIN";
    private String saslUsername;
    private String saslPassword;

    // Monitoring settings
    private boolean metricsEnabled = true;
    private int metricsReportIntervalMs = 60000;
    private boolean detailedMetricsEnabled = false;

    /**
     * Get configuration for specific protocol
     */
    public ProtocolSpecificConfig getProtocolConfig(String protocol) {
        switch (protocol.toLowerCase()) {
            case "kafka":
                return new ProtocolSpecificConfig(
                    kafkaEnabled, kafkaPort, kafkaMaxMessageSize,
                    kafkaRequestTimeoutMs, kafkaSessionTimeoutMs, kafkaHeartbeatIntervalMs
                );
            case "pulsar":
                return new ProtocolSpecificConfig(
                    pulsarEnabled, pulsarPort, pulsarMaxMessageSize,
                    pulsarConnectionTimeoutMs, pulsarOperationTimeoutMs, pulsarKeepAliveIntervalMs
                );
            case "rocketmq":
                return new ProtocolSpecificConfig(
                    rocketmqEnabled, rocketmqPort, rocketmqMaxMessageSize,
                    rocketmqSendMsgTimeoutMs, rocketmqHeartbeatBrokerIntervalMs, rocketmqPersistConsumerOffsetIntervalMs
                );
            default:
                return new ProtocolSpecificConfig(false, port, maxMessageSize, connectionTimeoutMs, 0, 0);
        }
    }

    /**
     * Protocol-specific configuration
     */
    @Data
    public static class ProtocolSpecificConfig {
        private final boolean enabled;
        private final int port;
        private final int maxMessageSize;
        private final int requestTimeoutMs;
        private final int sessionTimeoutMs;
        private final int heartbeatIntervalMs;

        public ProtocolSpecificConfig(boolean enabled, int port, int maxMessageSize, 
                                    int requestTimeoutMs, int sessionTimeoutMs, int heartbeatIntervalMs) {
            this.enabled = enabled;
            this.port = port;
            this.maxMessageSize = maxMessageSize;
            this.requestTimeoutMs = requestTimeoutMs;
            this.sessionTimeoutMs = sessionTimeoutMs;
            this.heartbeatIntervalMs = heartbeatIntervalMs;
        }
    }
} 