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

package org.apache.eventmesh.runtime.core.protocol.native.rocketmq;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.rocketmq.native.NativeRocketMQProtocolAdapter;
import org.apache.eventmesh.protocol.rocketmq.native.message.NativeRocketMQMessage;
import org.apache.eventmesh.runtime.core.protocol.native.NativeConnector;
import org.apache.eventmesh.runtime.core.protocol.native.NativeMessageHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native RocketMQ connector for direct RocketMQ client communication.
 * Allows native RocketMQ clients to connect to EventMesh without protocol conversion.
 */
public class NativeRocketMQConnector implements NativeConnector {

    private static final Logger log = LoggerFactory.getLogger(NativeRocketMQConnector.class);

    private final Producer producer;
    private final ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor;
    private final NativeMessageHandler messageHandler;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pendingMessages;
    private volatile boolean running = false;

    public NativeRocketMQConnector(Producer producer, NativeMessageHandler messageHandler) {
        this.producer = producer;
        this.messageHandler = messageHandler;
        this.protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor("rocketmq-native");
        this.executorService = Executors.newCachedThreadPool();
        this.pendingMessages = new ConcurrentHashMap<>();
    }

    @Override
    public void start() throws Exception {
        if (running) {
            log.warn("Native RocketMQ Connector is already running");
            return;
        }
        log.info("Starting Native RocketMQ Connector");
        running = true;
    }

    @Override
    public void shutdown() throws Exception {
        if (!running) {
            log.warn("Native RocketMQ Connector is not running");
            return;
        }
        log.info("Shutting down Native RocketMQ Connector");
        running = false;
        executorService.shutdown();
        pendingMessages.clear();
    }

    @Override
    public String getConnectorType() {
        return "rocketmq-native";
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
                return "rocketmq-native";
            }
        };
    }

    /**
     * Handle incoming native RocketMQ message
     */
    public void handleNativeRocketMQMessage(NativeRocketMQMessage message) {
        try {
            // Check if direct transmission is possible
            if (ProtocolPluginFactory.canTransmitDirectly("rocketmq-native", "rocketmq-native")) {
                // Direct transmission - no conversion needed
                handleDirectTransmission(message);
            } else {
                // Convert to CloudEvent and process
                handleWithConversion(message);
            }
        } catch (Exception e) {
            log.error("Failed to handle native RocketMQ message: {}", message.getTopic(), e);
            completePendingMessage(message.getMsgId(), e);
        }
    }

    /**
     * Handle direct transmission without conversion
     */
    private void handleDirectTransmission(NativeRocketMQMessage message) {
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
                        log.debug("Native RocketMQ message sent successfully: {}", message.getMsgId());
                        completePendingMessage(message.getMsgId(), null);
                    }

                    @Override
                    public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                        log.error("Failed to send native RocketMQ message: {}", message.getMsgId(), context.getException());
                        completePendingMessage(message.getMsgId(), context.getException());
                    }
                });
            } else {
                completePendingMessage(message.getMsgId(), null);
            }
        } catch (Exception e) {
            log.error("Failed to handle direct transmission: {}", message.getMsgId(), e);
            completePendingMessage(message.getMsgId(), e);
        }
    }

    /**
     * Handle with CloudEvent conversion
     */
    private void handleWithConversion(NativeRocketMQMessage message) {
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
                        log.debug("Native RocketMQ message converted and sent successfully: {}", message.getMsgId());
                        completePendingMessage(message.getMsgId(), null);
                    }

                    @Override
                    public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                        log.error("Failed to send converted native RocketMQ message: {}", message.getMsgId(), context.getException());
                        completePendingMessage(message.getMsgId(), context.getException());
                    }
                });
            } else {
                completePendingMessage(message.getMsgId(), null);
            }
        } catch (Exception e) {
            log.error("Failed to convert native RocketMQ message: {}", message.getMsgId(), e);
            completePendingMessage(message.getMsgId(), e);
        }
    }

    /**
     * Send native RocketMQ message to EventMesh
     */
    public CompletableFuture<Void> sendNativeRocketMQMessage(NativeRocketMQMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingMessages.put(message.getMsgId(), future);
        
        executorService.submit(() -> {
            try {
                handleNativeRocketMQMessage(message);
            } catch (Exception e) {
                log.error("Failed to send native RocketMQ message: {}", message.getMsgId(), e);
                completePendingMessage(message.getMsgId(), e);
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
    public NativeRocketMQConnectorStats getConnectorStats() {
        return new NativeRocketMQConnectorStats(
            pendingMessages.size(),
            !running,
            protocolAdaptor != null
        );
    }

    /**
     * Connector statistics
     */
    public static class NativeRocketMQConnectorStats {
        private final int pendingMessageCount;
        private final boolean isShutdown;
        private final boolean protocolAdaptorAvailable;

        public NativeRocketMQConnectorStats(int pendingMessageCount, boolean isShutdown, boolean protocolAdaptorAvailable) {
            this.pendingMessageCount = pendingMessageCount;
            this.isShutdown = isShutdown;
            this.protocolAdaptorAvailable = protocolAdaptorAvailable;
        }

        public int getPendingMessageCount() { return pendingMessageCount; }
        public boolean isShutdown() { return isShutdown; }
        public boolean isProtocolAdaptorAvailable() { return protocolAdaptorAvailable; }
    }
} 