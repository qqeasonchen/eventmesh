
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

package org.apache.eventmesh.runtime.core.protocol.raw.kafka;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.kafka.raw.message.RawKafkaMessage;
import org.apache.eventmesh.runtime.core.protocol.raw.RawConnector;
import org.apache.eventmesh.runtime.core.protocol.raw.RawMessageHandler;
import org.apache.eventmesh.api.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Raw Kafka connector for managing raw Kafka client interactions.
 * Handles raw Kafka messages and provides direct transmission or CloudEvent conversion.
 */
public class RawKafkaConnector implements RawConnector {

    private static final Logger log = LoggerFactory.getLogger(RawKafkaConnector.class);

    private final Producer producer;
    private ProtocolAdaptor protocolAdaptor;
    private final RawMessageHandler messageHandler;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pendingMessages;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong messageCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    public RawKafkaConnector(Producer producer, RawMessageHandler messageHandler) {
        this.producer = producer;
        this.messageHandler = messageHandler;
        this.protocolAdaptor = new org.apache.eventmesh.protocol.kafka.raw.RawKafkaProtocolAdapter();
        this.executorService = Executors.newFixedThreadPool(10);
        this.pendingMessages = new ConcurrentHashMap<>();
    }

    @Override
    public void start() throws Exception {
        if (running.compareAndSet(false, true)) {
            log.info("Raw Kafka connector started");
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (running.compareAndSet(true, false)) {
            executorService.shutdown();
            log.info("Raw Kafka connector shutdown");
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    public void handleRawKafkaMessage(RawKafkaMessage message) {
        if (!running.get()) {
            log.warn("Raw Kafka connector is not running, message ignored");
            return;
        }

        messageCount.incrementAndGet();
        
        // Check if we can handle this message directly
        if (messageHandler.supportsDirectHandling()) {
            handleDirectTransmission(message);
        } else {
            handleWithConversion(message);
        }
    }

    private void handleDirectTransmission(RawKafkaMessage message) {
        try {
            // Handle raw message directly without CloudEvent conversion
            messageHandler.handleMessage(message);
            log.debug("Raw Kafka message handled directly: {}", message.getTopic());
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("Error handling raw Kafka message directly", e);
        }
    }

    private void handleWithConversion(RawKafkaMessage message) {
        try {
            // Convert to CloudEvent and handle
            io.cloudevents.CloudEvent cloudEvent = protocolAdaptor.toCloudEvent(message);
            messageHandler.handleCloudEvent(cloudEvent);
            log.debug("Raw Kafka message converted and handled: {}", message.getTopic());
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("Error converting and handling raw Kafka message", e);
        }
    }

    public CompletableFuture<Void> sendRawKafkaMessage(RawKafkaMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String messageId = message.getKey() != null ? message.getKey() : 
            message.getTopic() + "_" + System.currentTimeMillis();
        
        pendingMessages.put(messageId, future);
        
        executorService.submit(() -> {
            try {
                // Convert to CloudEvent and publish
                io.cloudevents.CloudEvent cloudEvent = protocolAdaptor.toCloudEvent(message);
                producer.publish(cloudEvent, new org.apache.eventmesh.api.SendCallback() {
                    @Override
                    public void onSuccess(org.apache.eventmesh.api.SendResult sendResult) {
                        // handle success
                    }
                    @Override
                    public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                        // handle exception
                    }
                });
            } catch (Exception e) {
                completePendingMessage(messageId, e);
            }
        });
        
        return future;
    }

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

    @Override
    public org.apache.eventmesh.runtime.core.protocol.raw.RawConnector.RawConnectorStats getStats() {
        return new RawKafkaConnectorStats();
    }

    @Override
    public String getConnectorType() {
        return "kafka";
    }

    // 内部类实现
    private static class RawKafkaConnectorStats implements org.apache.eventmesh.runtime.core.protocol.raw.RawConnector.RawConnectorStats {
        @Override
        public int getPendingMessageCount() { return 0; }
        @Override
        public boolean isShutdown() { return false; }
        @Override
        public String getConnectorType() { return "kafka"; }
    }
}