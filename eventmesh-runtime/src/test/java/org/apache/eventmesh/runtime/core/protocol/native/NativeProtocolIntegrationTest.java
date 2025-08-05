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
import org.apache.eventmesh.protocol.kafka.native.message.NativeKafkaMessage;
import org.apache.eventmesh.protocol.pulsar.native.message.NativePulsarMessage;
import org.apache.eventmesh.protocol.rocketmq.native.message.NativeRocketMQMessage;
import org.apache.eventmesh.runtime.core.protocol.native.kafka.NativeKafkaConnector;
import org.apache.eventmesh.runtime.core.protocol.native.pulsar.NativePulsarConnector;
import org.apache.eventmesh.runtime.core.protocol.native.rocketmq.NativeRocketMQConnector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for native protocol support.
 * Tests the integration between native clients and EventMesh.
 */
@ExtendWith(MockitoExtension.class)
public class NativeProtocolIntegrationTest {

    @Mock
    private Producer mockProducer;

    private NativeProtocolManager protocolManager;
    private NativeMessageHandler messageHandler;

    @BeforeEach
    public void setUp() throws Exception {
        messageHandler = new NativeMessageHandler() {
            @Override
            public void handleMessage(org.apache.eventmesh.common.protocol.ProtocolTransportObject message) {
                // Test message handler
            }

            @Override
            public void handleCloudEvent(io.cloudevents.CloudEvent cloudEvent) {
                // Test CloudEvent handler
            }
        };

        protocolManager = new NativeProtocolManager(mockProducer, messageHandler);
        protocolManager.start();
    }

    @Test
    public void testNativeKafkaIntegration() throws Exception {
        // Test Kafka native integration
        NativeKafkaConnector kafkaConnector = protocolManager.getKafkaConnector();
        assertNotNull(kafkaConnector, "Kafka connector should not be null");
        assertTrue(kafkaConnector.isRunning(), "Kafka connector should be running");

        // Create test Kafka message
        NativeKafkaMessage kafkaMessage = new NativeKafkaMessage();
        kafkaMessage.setTopic("test-topic");
        kafkaMessage.setKey("test-key");
        kafkaMessage.setValue("test-value".getBytes());
        kafkaMessage.setMessageId("test-message-id");

        // Test direct transmission
        CompletableFuture<Void> future = kafkaConnector.sendNativeKafkaMessage(kafkaMessage);
        assertNotNull(future, "Future should not be null");

        // Verify connector stats
        var stats = kafkaConnector.getConnectorStats();
        assertNotNull(stats, "Stats should not be null");
        assertFalse(stats.isShutdown(), "Connector should not be shutdown");
    }

    @Test
    public void testNativePulsarIntegration() throws Exception {
        // Test Pulsar native integration
        NativePulsarConnector pulsarConnector = protocolManager.getPulsarConnector();
        assertNotNull(pulsarConnector, "Pulsar connector should not be null");
        assertTrue(pulsarConnector.isRunning(), "Pulsar connector should be running");

        // Create test Pulsar message
        NativePulsarMessage pulsarMessage = new NativePulsarMessage();
        pulsarMessage.setTopic("test-topic");
        pulsarMessage.setData("test-data".getBytes());
        pulsarMessage.setMessageId("test-message-id");
        pulsarMessage.setSequenceId(1L);

        // Test direct transmission
        CompletableFuture<Void> future = pulsarConnector.sendNativePulsarMessage(pulsarMessage);
        assertNotNull(future, "Future should not be null");

        // Verify connector stats
        var stats = pulsarConnector.getConnectorStats();
        assertNotNull(stats, "Stats should not be null");
        assertFalse(stats.isShutdown(), "Connector should not be shutdown");
    }

    @Test
    public void testNativeRocketMQIntegration() throws Exception {
        // Test RocketMQ native integration
        NativeRocketMQConnector rocketmqConnector = protocolManager.getRocketMQConnector();
        assertNotNull(rocketmqConnector, "RocketMQ connector should not be null");
        assertTrue(rocketmqConnector.isRunning(), "RocketMQ connector should be running");

        // Create test RocketMQ message
        NativeRocketMQMessage rocketmqMessage = new NativeRocketMQMessage();
        rocketmqMessage.setTopic("test-topic");
        rocketmqMessage.setBody("test-body".getBytes());
        rocketmqMessage.setMsgId("test-message-id");
        rocketmqMessage.setKeys("test-keys");

        // Test direct transmission
        CompletableFuture<Void> future = rocketmqConnector.sendNativeRocketMQMessage(rocketmqMessage);
        assertNotNull(future, "Future should not be null");

        // Verify connector stats
        var stats = rocketmqConnector.getConnectorStats();
        assertNotNull(stats, "Stats should not be null");
        assertFalse(stats.isShutdown(), "Connector should not be shutdown");
    }

    @Test
    public void testProtocolManagerStats() throws Exception {
        // Test protocol manager statistics
        var stats = protocolManager.getStats();
        assertNotNull(stats, "Manager stats should not be null");
        assertTrue(stats.isStarted(), "Manager should be started");
        assertEquals(3, stats.getTotalConnectors(), "Should have 3 connectors");
        assertEquals(3, stats.getRunningConnectors(), "Should have 3 running connectors");
    }

    @Test
    public void testConnectorAvailability() throws Exception {
        // Test connector availability
        assertTrue(protocolManager.isConnectorAvailable("kafka"), "Kafka connector should be available");
        assertTrue(protocolManager.isConnectorAvailable("pulsar"), "Pulsar connector should be available");
        assertTrue(protocolManager.isConnectorAvailable("rocketmq"), "RocketMQ connector should be available");
        assertFalse(protocolManager.isConnectorAvailable("unknown"), "Unknown connector should not be available");
    }

    @Test
    public void testConnectorTypes() throws Exception {
        // Test available connector types
        String[] connectorTypes = protocolManager.getAvailableConnectorTypes();
        assertNotNull(connectorTypes, "Connector types should not be null");
        assertEquals(3, connectorTypes.length, "Should have 3 connector types");
        
        boolean hasKafka = false, hasPulsar = false, hasRocketMQ = false;
        for (String type : connectorTypes) {
            if ("kafka".equals(type)) hasKafka = true;
            if ("pulsar".equals(type)) hasPulsar = true;
            if ("rocketmq".equals(type)) hasRocketMQ = true;
        }
        
        assertTrue(hasKafka, "Should have Kafka connector");
        assertTrue(hasPulsar, "Should have Pulsar connector");
        assertTrue(hasRocketMQ, "Should have RocketMQ connector");
    }

    @Test
    public void testConnectorShutdown() throws Exception {
        // Test connector shutdown
        protocolManager.shutdown();
        
        assertFalse(protocolManager.isStarted(), "Manager should not be started after shutdown");
        
        var stats = protocolManager.getStats();
        assertFalse(stats.isStarted(), "Manager stats should show not started");
        assertEquals(0, stats.getRunningConnectors(), "Should have 0 running connectors after shutdown");
    }

    @Test
    public void testMessageHandler() throws Exception {
        // Test message handler functionality
        assertTrue(messageHandler.supportsDirectHandling(), "Should support direct handling");
        assertTrue(messageHandler.supportsCloudEventHandling(), "Should support CloudEvent handling");
    }

    @Test
    public void testProducerIntegration() throws Exception {
        // Test producer integration
        when(mockProducer.publish(any(), any())).thenAnswer(invocation -> {
            // Simulate successful publish
            return null;
        });

        // Test with Kafka message
        NativeKafkaConnector kafkaConnector = protocolManager.getKafkaConnector();
        NativeKafkaMessage kafkaMessage = new NativeKafkaMessage();
        kafkaMessage.setTopic("test-topic");
        kafkaMessage.setValue("test-value".getBytes());
        kafkaMessage.setMessageId("test-id");

        CompletableFuture<Void> future = kafkaConnector.sendNativeKafkaMessage(kafkaMessage);
        
        // Wait for completion
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Expected in test environment
        }

        // Verify producer was called
        verify(mockProducer, timeout(5000).atLeastOnce()).publish(any(), any());
    }
} 