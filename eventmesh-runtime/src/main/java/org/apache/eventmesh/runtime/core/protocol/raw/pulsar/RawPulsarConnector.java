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

package org.apache.eventmesh.runtime.core.protocol.raw.pulsar;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.pulsar.raw.RawPulsarProtocolAdapter;
import org.apache.eventmesh.protocol.pulsar.raw.message.RawPulsarMessage;
import org.apache.eventmesh.runtime.core.protocol.raw.RawConnector;
import org.apache.eventmesh.runtime.core.protocol.raw.RawMessageHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Raw Pulsar connector for handling raw Pulsar protocol messages.
 * This connector provides direct communication with Pulsar clients
 * without CloudEvent conversion for better performance.
 */
public class RawPulsarConnector extends RawConnector {

    private static final Logger log = LoggerFactory.getLogger(RawPulsarConnector.class);

    private final RawPulsarProtocolAdapter protocolAdaptor;
    private final RawMessageHandler messageHandler;

    public RawPulsarConnector() {
        this.protocolAdaptor = new org.apache.eventmesh.protocol.pulsar.raw.RawPulsarProtocolAdapter();
        this.messageHandler = new RawMessageHandler();
    }

    /**
     * Handle raw Pulsar message with protocol detection and optimization.
     *
     * @param message the raw Pulsar message
     */
    public void handleRawPulsarMessage(RawPulsarMessage message) {
        try {
            // Detect protocol type for optimization
            String protocolType = detectProtocolType(message);
            
            if (canTransmitDirectly(protocolType)) {
                handleDirectTransmission(message);
            } else {
                handleWithConversion(message);
            }
        } catch (Exception e) {
            log.error("Failed to handle raw Pulsar message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle direct transmission for same protocol optimization.
     *
     * @param message the raw Pulsar message
     */
    private void handleDirectTransmission(RawPulsarMessage message) {
        try {
            log.debug("Using direct transmission for Pulsar message: {}", message.getMessageId());
            
            // Direct transmission without CloudEvent conversion
            messageHandler.handleDirectTransmission(message, "pulsar-raw");
            
        } catch (Exception e) {
            log.error("Failed to handle direct transmission for Pulsar message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle message with CloudEvent conversion.
     *
     * @param message the raw Pulsar message
     */
    private void handleWithConversion(RawPulsarMessage message) {
        try {
            log.debug("Converting Pulsar message to CloudEvent: {}", message.getMessageId());
            
            // Convert to CloudEvent for cross-protocol communication
            var cloudEvent = protocolAdaptor.toCloudEvent(message);
            messageHandler.handleWithConversion(cloudEvent, "pulsar-raw");
            
        } catch (ProtocolHandleException e) {
            log.error("Failed to convert Pulsar message to CloudEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Send raw Pulsar message asynchronously.
     *
     * @param message the raw Pulsar message to send
     * @return CompletableFuture for async operation
     */
    public CompletableFuture<Void> sendRawPulsarMessage(RawPulsarMessage message) {
        return CompletableFuture.runAsync(() -> {
            try {
                handleRawPulsarMessage(message);
            } catch (Exception e) {
                log.error("Failed to send raw Pulsar message: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Detect protocol type from message for optimization.
     *
     * @param message the raw Pulsar message
     * @return detected protocol type
     */
    private String detectProtocolType(RawPulsarMessage message) {
        // For Pulsar, we can detect based on message properties or topic
        if (message.getProperties() != null && message.getProperties().containsKey("protocol")) {
            return message.getProperties().get("protocol");
        }
        
        // Default to pulsar-raw for Pulsar messages
        return "pulsar-raw";
    }

    /**
     * Check if direct transmission is possible.
     *
     * @param protocolType the protocol type
     * @return true if direct transmission is possible
     */
    private boolean canTransmitDirectly(String protocolType) {
        return ProtocolPluginFactory.canTransmitDirectly("pulsar-raw", protocolType);
    }

    @Override
    public ProtocolAdaptor<ProtocolTransportObject> getProtocolAdaptor() {
        return protocolAdaptor;
    }

    @Override
    public String getProtocolType() {
        return "pulsar-raw";
    }

    @Override
    public void start() {
        log.info("Raw Pulsar connector started");
    }

    @Override
    public void stop() {
        log.info("Raw Pulsar connector stopped");
    }
} 