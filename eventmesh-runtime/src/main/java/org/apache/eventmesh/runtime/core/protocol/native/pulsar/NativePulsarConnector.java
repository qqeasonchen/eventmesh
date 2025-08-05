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

package org.apache.eventmesh.runtime.core.protocol.native.pulsar;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.pulsar.native.NativePulsarProtocolAdapter;
import org.apache.eventmesh.protocol.pulsar.native.message.NativePulsarMessage;
import org.apache.eventmesh.runtime.core.protocol.native.NativeConnector;
import org.apache.eventmesh.runtime.core.protocol.native.NativeMessageHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Pulsar connector for direct Pulsar client communication.
 * Allows native Pulsar clients to connect to EventMesh without protocol conversion.
 */
public class NativePulsarConnector implements NativeConnector {

    private static final Logger log = LoggerFactory.getLogger(NativePulsarConnector.class);

    private final Producer producer;
    private final ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor;
    private final NativeMessageHandler messageHandler;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pendingMessages;
    private volatile boolean running = false;

    public NativePulsarConnector(Producer producer, NativeMessageHandler messageHandler) {
        this.producer = producer;
        this.messageHandler = messageHandler;
        this.protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor("pulsar-native");
        this.executorService = Executors.newCachedThreadPool();
        this.pendingMessages = new ConcurrentHashMap<>();
    }

    @Override
    public void start() throws Exception {
        if (running) {
            log.warn("Native Pulsar Connector is already running");
            return;
        }
        log.info("Starting Native Pulsar Connector");
        running = true;
    }

    @Override
    public void shutdown() throws Exception {
        if (!running) {
            log.warn("Native Pulsar Connector is not running");
            return;
        }
        log.info("Shutting down Native Pulsar Connector");
        running = false;
        executorService.shutdown();
        pendingMessages.clear();
    }

    @Override
    public String getConnectorType() {
        return "pulsar-native";
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public NativeConnectorStats getStats() {
        return new NativeConnectorStats() {
            @Override
            public int getPendingMessageCount() {
                return pendingMessages.size();
            }

            @Override
            public boolean isShutdown() {
                return !running;
            }

            @Override
            public String getConnectorType() {
                return "pulsar-native";
            }
        };
    }

    /**
     * Handle incoming native Pulsar message
     */
    public void handleNativePulsarMessage(NativePulsarMessage message) {
        try {
            // Check if direct transmission is possible
            if (ProtocolPluginFactory.canTransmitDirectly("pulsar-native", "pulsar-native")) {
                // Direct transmission - no conversion needed
                handleDirectTransmission(message);
            } else {
                // Convert to CloudEvent and process
                handleWithConversion(message);
            }
        } catch (Exception e) {
            log.error("Failed to handle native Pulsar message: {}", message.getTopic(), e);
            completePendingMessage(message.getMessageId(), e);
        }
    }

    /**
     * Handle direct transmission without conversion
     */
    private void handleDirectTransmission(NativePulsarMessage message) {
        try {
            // Direct transmission - pass through without CloudEvent conversion
            if (messageHandler != null) {
                messageHandler.handleMessage(message);
            }
            
            // Send to producer if needed
            if (producer != null) {
                // Convert to CloudEvent for storage (if needed)
                var cloudEvent = protocolAdaptor.toCloudEvent(message);
                producer.publish(cloudEvent, new SendCallback() {
                    @Override
                    public void onSuccess(org.apache.eventmesh.api.SendResult sendResult) {
                        log.debug("Native Pulsar message sent successfully: {}", message.getMessageId());
                        completePendingMessage(message.getMessageId(), null);
                    }

                    @Override
                    public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                        log.error("Failed to send native Pulsar message: {}", message.getMessageId(), context.getException());
                        completePendingMessage(message.getMessageId(), context.getException());
                    }
                });
            } else {
                completePendingMessage(message.getMessageId(), null);
            }
        } catch (Exception e) {
            log.error("Failed to handle direct transmission: {}", message.getMessageId(), e);
            completePendingMessage(message.getMessageId(), e);
        }
    }

    /**
     * Handle with CloudEvent conversion
     */
    private void handleWithConversion(NativePulsarMessage message) {
        try {
            // Convert to CloudEvent
            var cloudEvent = protocolAdaptor.toCloudEvent(message);
            
            // Process the CloudEvent
            if (messageHandler != null) {
                messageHandler.handleCloudEvent(cloudEvent);
            }
            
            // Send to producer
            if (producer != null) {
                producer.publish(cloudEvent, new SendCallback() {
                    @Override
                    public void onSuccess(org.apache.eventmesh.api.SendResult sendResult) {
                        log.debug("Native Pulsar message converted and sent successfully: {}", message.getMessageId());
                        completePendingMessage(message.getMessageId(), null);
                    }

                    @Override
                    public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                        log.error("Failed to send converted native Pulsar message: {}", message.getMessageId(), context.getException());
                        completePendingMessage(message.getMessageId(), context.getException());
                    }
                });
            } else {
                completePendingMessage(message.getMessageId(), null);
            }
        } catch (Exception e) {
            log.error("Failed to convert native Pulsar message: {}", message.getMessageId(), e);
            completePendingMessage(message.getMessageId(), e);
        }
    }

    /**
     * Send native Pulsar message to EventMesh
     */
    public CompletableFuture<Void> sendNativePulsarMessage(NativePulsarMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingMessages.put(message.getMessageId(), future);
        
        executorService.submit(() -> {
            try {
                handleNativePulsarMessage(message);
            } catch (Exception e) {
                log.error("Failed to send native Pulsar message: {}", message.getMessageId(), e);
                completePendingMessage(message.getMessageId(), e);
            }
        });
        
        return future;
    }

    /**
     * Complete pending message
     */
    private void completePendingMessage(String messageId, Exception exception) {
        CompletableFuture<Void> future = pendingMessages.remove(messageId);
        if (future != null) {
            if (exception != null) {
                future.completeExceptionally(exception);
            } else {
                future.complete(null);
            }
        }
    }

    /**
     * Get connector statistics
     */
    public NativePulsarConnectorStats getConnectorStats() {
        return new NativePulsarConnectorStats(
            pendingMessages.size(),
            !running,
            protocolAdaptor != null
        );
    }

    /**
     * Connector statistics
     */
    public static class NativePulsarConnectorStats {
        private final int pendingMessageCount;
        private final boolean isShutdown;
        private final boolean protocolAdaptorAvailable;

        public NativePulsarConnectorStats(int pendingMessageCount, boolean isShutdown, boolean protocolAdaptorAvailable) {
            this.pendingMessageCount = pendingMessageCount;
            this.isShutdown = isShutdown;
            this.protocolAdaptorAvailable = protocolAdaptorAvailable;
        }

        public int getPendingMessageCount() { return pendingMessageCount; }
        public boolean isShutdown() { return isShutdown; }
        public boolean isProtocolAdaptorAvailable() { return protocolAdaptorAvailable; }
    }
} 