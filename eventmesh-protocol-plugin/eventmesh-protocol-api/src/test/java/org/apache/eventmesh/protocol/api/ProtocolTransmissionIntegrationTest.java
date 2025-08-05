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

package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.kafka.KafkaProtocolAdapter;
import org.apache.eventmesh.protocol.kafka.message.KafkaMessage;
import org.apache.eventmesh.protocol.pulsar.PulsarProtocolAdapter;
import org.apache.eventmesh.protocol.pulsar.message.PulsarMessage;
import org.apache.eventmesh.protocol.rocketmq.RocketMQProtocolAdapter;
import org.apache.eventmesh.protocol.rocketmq.message.RocketMQMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test cases for protocol transmission optimization
 */
@ExtendWith(MockitoExtension.class)
public class ProtocolTransmissionIntegrationTest {

    private KafkaProtocolAdapter kafkaAdapter;
    private PulsarProtocolAdapter pulsarAdapter;
    private RocketMQProtocolAdapter rocketmqAdapter;

    @BeforeEach
    public void setUp() {
        kafkaAdapter = new KafkaProtocolAdapter();
        pulsarAdapter = new PulsarProtocolAdapter();
        rocketmqAdapter = new RocketMQProtocolAdapter();
    }

    @Test
    public void testKafkaDirectTransmission() throws ProtocolHandleException {
        // Create a test Kafka message
        KafkaMessage originalMessage = createTestKafkaMessage();
        
        // Test direct transmission (Kafka -> Kafka)
        KafkaMessage transmittedMessage = kafkaAdapter.transmitDirectly(originalMessage);
        
        // Verify direct transmission
        assertSame(originalMessage, transmittedMessage);
        assertEquals("test-topic", transmittedMessage.getTopic());
        assertEquals("test-key", transmittedMessage.getKey());
        assertArrayEquals("test-value".getBytes(), transmittedMessage.getValue());
        
        // Verify headers
        Map<String, String> headers = transmittedMessage.getHeaders();
        assertEquals("value1", headers.get("header1"));
        assertEquals("value2", headers.get("header2"));
    }

    @Test
    public void testPulsarDirectTransmission() throws ProtocolHandleException {
        // Create a test Pulsar message
        PulsarMessage originalMessage = createTestPulsarMessage();
        
        // Test direct transmission (Pulsar -> Pulsar)
        PulsarMessage transmittedMessage = pulsarAdapter.transmitDirectly(originalMessage);
        
        // Verify direct transmission
        assertSame(originalMessage, transmittedMessage);
        assertEquals("test-topic", transmittedMessage.getTopicName());
        assertEquals("test-id", transmittedMessage.getMessageId());
        assertArrayEquals("test-data".getBytes(), transmittedMessage.getData());
        
        // Verify properties
        Map<String, String> properties = transmittedMessage.getProperties();
        assertEquals("value1", properties.get("prop1"));
        assertEquals("value2", properties.get("prop2"));
    }

    @Test
    public void testRocketMQDirectTransmission() throws ProtocolHandleException {
        // Create a test RocketMQ message
        RocketMQMessage originalMessage = createTestRocketMQMessage();
        
        // Test direct transmission (RocketMQ -> RocketMQ)
        RocketMQMessage transmittedMessage = rocketmqAdapter.transmitDirectly(originalMessage);
        
        // Verify direct transmission
        assertSame(originalMessage, transmittedMessage);
        assertEquals("test-topic", transmittedMessage.getTopic());
        assertEquals("test-id", transmittedMessage.getMessageId());
        assertArrayEquals("test-body".getBytes(), transmittedMessage.getBody());
        
        // Verify properties
        Map<String, String> properties = transmittedMessage.getProperties();
        assertEquals("value1", properties.get("prop1"));
        assertEquals("value2", properties.get("prop2"));
    }

    @Test
    public void testCrossProtocolConversion() throws ProtocolHandleException {
        // Test Kafka -> CloudEvent -> Pulsar conversion
        KafkaMessage kafkaMessage = createTestKafkaMessage();
        
        // Convert Kafka to CloudEvent
        CloudEvent cloudEvent = kafkaAdapter.toCloudEvent(kafkaMessage);
        assertNotNull(cloudEvent);
        assertEquals("test-key", cloudEvent.getId());
        assertEquals("test-topic", cloudEvent.getSubject());
        assertArrayEquals("test-value".getBytes(), cloudEvent.getData().toBytes());
        
        // Convert CloudEvent to Pulsar
        PulsarMessage pulsarMessage = (PulsarMessage) pulsarAdapter.fromCloudEvent(cloudEvent);
        assertNotNull(pulsarMessage);
        assertEquals("test-topic", pulsarMessage.getTopicName());
        assertEquals("test-key", pulsarMessage.getMessageId());
        assertArrayEquals("test-value".getBytes(), pulsarMessage.getData());
    }

    @Test
    public void testProtocolFactoryIntegration() throws Exception {
        // Test ProtocolPluginFactory integration with direct transmission
        KafkaMessage kafkaMessage = createTestKafkaMessage();
        
        // Test direct transmission through factory
        ProtocolTransportObject transmitted = ProtocolPluginFactory.transmitDirectly("kafka", "kafka", kafkaMessage);
        assertNotNull(transmitted);
        assertTrue(transmitted instanceof KafkaMessage);
        assertEquals(kafkaMessage, transmitted);
        
        // Test cross-protocol transmission (should throw exception)
        assertThrows(IllegalArgumentException.class, () -> {
            ProtocolPluginFactory.transmitDirectly("kafka", "pulsar", kafkaMessage);
        });
    }

    @Test
    public void testPerformanceComparison() throws ProtocolHandleException {
        // Test performance difference between direct transmission and conversion
        KafkaMessage kafkaMessage = createTestKafkaMessage();
        
        // Measure direct transmission time
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            kafkaAdapter.transmitDirectly(kafkaMessage);
        }
        long directTransmissionTime = System.nanoTime() - startTime;
        
        // Measure conversion time
        startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            CloudEvent cloudEvent = kafkaAdapter.toCloudEvent(kafkaMessage);
            kafkaAdapter.fromCloudEvent(cloudEvent);
        }
        long conversionTime = System.nanoTime() - startTime;
        
        // Direct transmission should be significantly faster
        assertTrue(directTransmissionTime < conversionTime, 
            "Direct transmission should be faster than conversion. Direct: " + directTransmissionTime + 
            "ns, Conversion: " + conversionTime + "ns");
        
        System.out.println("Direct transmission time: " + directTransmissionTime + "ns");
        System.out.println("Conversion time: " + conversionTime + "ns");
        System.out.println("Performance improvement: " + 
            String.format("%.2f", (double) conversionTime / directTransmissionTime) + "x");
    }

    @Test
    public void testPluginLifecycleIntegration() throws Exception {
        // Test plugin lifecycle integration
        PluginLifecycle.PluginInfo pluginInfo = new PluginLifecycle.PluginInfo("kafka", "KafkaProtocolAdapter", "1.0.0", true);
        
        // Test lifecycle methods
        assertDoesNotThrow(() -> kafkaAdapter.onLoad(pluginInfo));
        assertDoesNotThrow(() -> kafkaAdapter.onUnload(pluginInfo));
        assertDoesNotThrow(() -> kafkaAdapter.onReload(pluginInfo));
        
        // Test version and description
        assertEquals("1.0.0", kafkaAdapter.getVersion());
        assertEquals("Kafka protocol adapter for EventMesh", kafkaAdapter.getDescription());
        assertTrue(kafkaAdapter.supportsHotReload());
    }

    @Test
    public void testProtocolTypeConsistency() {
        // Test that all adapters return consistent protocol types
        assertEquals("kafka", kafkaAdapter.getProtocolType());
        assertEquals("pulsar", pulsarAdapter.getProtocolType());
        assertEquals("rocketmq", rocketmqAdapter.getProtocolType());
        
        // Test canTransmitDirectly method
        assertTrue(kafkaAdapter.canTransmitDirectly("kafka"));
        assertFalse(kafkaAdapter.canTransmitDirectly("pulsar"));
        assertFalse(kafkaAdapter.canTransmitDirectly("rocketmq"));
        
        assertTrue(pulsarAdapter.canTransmitDirectly("pulsar"));
        assertFalse(pulsarAdapter.canTransmitDirectly("kafka"));
        assertFalse(pulsarAdapter.canTransmitDirectly("rocketmq"));
        
        assertTrue(rocketmqAdapter.canTransmitDirectly("rocketmq"));
        assertFalse(rocketmqAdapter.canTransmitDirectly("kafka"));
        assertFalse(rocketmqAdapter.canTransmitDirectly("pulsar"));
    }

    @Test
    public void testNullHandling() throws ProtocolHandleException {
        // Test null message handling
        assertNull(kafkaAdapter.transmitDirectly(null));
        assertNull(pulsarAdapter.transmitDirectly(null));
        assertNull(rocketmqAdapter.transmitDirectly(null));
    }

    @Test
    public void testLargeMessageHandling() throws ProtocolHandleException {
        // Test with large messages
        String largeData = "x".repeat(10000); // 10KB data
        
        // Test Kafka large message
        KafkaMessage largeKafkaMessage = new KafkaMessage();
        largeKafkaMessage.setTopic("large-topic");
        largeKafkaMessage.setKey("large-key");
        largeKafkaMessage.setValue(largeData.getBytes());
        
        KafkaMessage transmittedLargeKafka = kafkaAdapter.transmitDirectly(largeKafkaMessage);
        assertSame(largeKafkaMessage, transmittedLargeKafka);
        assertEquals(largeData.length(), transmittedLargeKafka.getValue().length);
        
        // Test Pulsar large message
        PulsarMessage largePulsarMessage = new PulsarMessage();
        largePulsarMessage.setTopicName("large-topic");
        largePulsarMessage.setMessageId("large-id");
        largePulsarMessage.setData(largeData.getBytes());
        
        PulsarMessage transmittedLargePulsar = pulsarAdapter.transmitDirectly(largePulsarMessage);
        assertSame(largePulsarMessage, transmittedLargePulsar);
        assertEquals(largeData.length(), transmittedLargePulsar.getData().length);
    }

    private KafkaMessage createTestKafkaMessage() {
        KafkaMessage message = new KafkaMessage();
        message.setTopic("test-topic");
        message.setKey("test-key");
        message.setValue("test-value".getBytes());
        
        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        message.setHeaders(headers);
        
        return message;
    }

    private PulsarMessage createTestPulsarMessage() {
        PulsarMessage message = new PulsarMessage();
        message.setTopicName("test-topic");
        message.setMessageId("test-id");
        message.setData("test-data".getBytes());
        
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");
        message.setProperties(properties);
        
        return message;
    }

    private RocketMQMessage createTestRocketMQMessage() {
        RocketMQMessage message = new RocketMQMessage();
        message.setTopic("test-topic");
        message.setMessageId("test-id");
        message.setBody("test-body".getBytes());
        
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");
        message.setProperties(properties);
        
        return message;
    }
} 