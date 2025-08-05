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

package org.apache.eventmesh.protocol.kafka;

import org.apache.eventmesh.protocol.api.PluginLifecycle;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.kafka.message.KafkaMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for KafkaProtocolAdapter
 */
@ExtendWith(MockitoExtension.class)
public class KafkaProtocolAdapterTest {

    private KafkaProtocolAdapter adapter;

    @Mock
    private PluginLifecycle.PluginInfo mockPluginInfo;

    @BeforeEach
    public void setUp() {
        adapter = new KafkaProtocolAdapter();
    }

    @Test
    public void testGetProtocolType() {
        assertEquals("kafka", adapter.getProtocolType());
    }

    @Test
    public void testCanTransmitDirectly() {
        // Test same protocol transmission
        assertTrue(adapter.canTransmitDirectly("kafka"));
        
        // Test different protocol transmission
        assertFalse(adapter.canTransmitDirectly("pulsar"));
        assertFalse(adapter.canTransmitDirectly("rocketmq"));
        assertFalse(adapter.canTransmitDirectly("eventmeshmessage"));
    }

    @Test
    public void testTransmitDirectly() throws ProtocolHandleException {
        // Create a test Kafka message
        KafkaMessage originalMessage = new KafkaMessage();
        originalMessage.setTopic("test-topic");
        originalMessage.setKey("test-key");
        originalMessage.setValue("test-value".getBytes());
        
        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        originalMessage.setHeaders(headers);

        // Test direct transmission
        KafkaMessage transmittedMessage = adapter.transmitDirectly(originalMessage);
        
        // Should return the same object (no conversion)
        assertSame(originalMessage, transmittedMessage);
        assertEquals("test-topic", transmittedMessage.getTopic());
        assertEquals("test-key", transmittedMessage.getKey());
        assertArrayEquals("test-value".getBytes(), transmittedMessage.getValue());
        assertEquals(headers, transmittedMessage.getHeaders());
    }

    @Test
    public void testTransmitDirectlyWithNullMessage() throws ProtocolHandleException {
        // Test with null message
        KafkaMessage transmittedMessage = adapter.transmitDirectly(null);
        assertNull(transmittedMessage);
    }

    @Test
    public void testToCloudEvent() throws ProtocolHandleException {
        // Create a test Kafka message
        KafkaMessage kafkaMessage = new KafkaMessage();
        kafkaMessage.setTopic("test-topic");
        kafkaMessage.setKey("test-key");
        kafkaMessage.setValue("test-value".getBytes());
        
        Map<String, String> headers = new HashMap<>();
        headers.put("custom-header", "custom-value");
        headers.put("event-type", "test-event");
        kafkaMessage.setHeaders(headers);

        // Convert to CloudEvent
        CloudEvent cloudEvent = adapter.toCloudEvent(kafkaMessage);

        // Verify CloudEvent properties
        assertNotNull(cloudEvent);
        assertEquals("test-key", cloudEvent.getId());
        assertEquals(URI.create("kafka://test-topic"), cloudEvent.getSource());
        assertEquals("kafka.message", cloudEvent.getType());
        assertEquals("test-topic", cloudEvent.getSubject());
        assertArrayEquals("test-value".getBytes(), cloudEvent.getData().toBytes());

        // Verify extensions (headers)
        assertEquals("custom-value", cloudEvent.getExtension("custom-header"));
        assertEquals("test-event", cloudEvent.getExtension("event-type"));
    }

    @Test
    public void testToCloudEventWithNullKey() throws ProtocolHandleException {
        // Create a test Kafka message without key
        KafkaMessage kafkaMessage = new KafkaMessage();
        kafkaMessage.setTopic("test-topic");
        kafkaMessage.setValue("test-value".getBytes());

        // Convert to CloudEvent
        CloudEvent cloudEvent = adapter.toCloudEvent(kafkaMessage);

        // Verify CloudEvent properties
        assertNotNull(cloudEvent);
        assertTrue(cloudEvent.getId().startsWith("test-topic_"));
        assertEquals(URI.create("kafka://test-topic"), cloudEvent.getSource());
        assertEquals("kafka.message", cloudEvent.getType());
        assertEquals("test-topic", cloudEvent.getSubject());
        assertArrayEquals("test-value".getBytes(), cloudEvent.getData().toBytes());
    }

    @Test
    public void testToCloudEventWithNullValue() throws ProtocolHandleException {
        // Create a test Kafka message without value
        KafkaMessage kafkaMessage = new KafkaMessage();
        kafkaMessage.setTopic("test-topic");
        kafkaMessage.setKey("test-key");

        // Convert to CloudEvent
        CloudEvent cloudEvent = adapter.toCloudEvent(kafkaMessage);

        // Verify CloudEvent properties
        assertNotNull(cloudEvent);
        assertEquals("test-key", cloudEvent.getId());
        assertEquals(URI.create("kafka://test-topic"), cloudEvent.getSource());
        assertEquals("kafka.message", cloudEvent.getType());
        assertEquals("test-topic", cloudEvent.getSubject());
        assertNull(cloudEvent.getData());
    }

    @Test
    public void testFromCloudEvent() throws ProtocolHandleException {
        // Create a test CloudEvent
        CloudEvent cloudEvent = CloudEventBuilder.v1()
            .withId("test-id")
            .withSource(URI.create("kafka://test-topic"))
            .withType("kafka.message")
            .withSubject("test-topic")
            .withData("test-data".getBytes())
            .withExtension("custom-header", "custom-value")
            .withExtension("event-type", "test-event")
            .build();

        // Convert to Kafka message
        KafkaMessage kafkaMessage = (KafkaMessage) adapter.fromCloudEvent(cloudEvent);

        // Verify Kafka message properties
        assertNotNull(kafkaMessage);
        assertEquals("test-topic", kafkaMessage.getTopic());
        assertEquals("test-id", kafkaMessage.getKey());
        assertArrayEquals("test-data".getBytes(), kafkaMessage.getValue());

        // Verify headers (extensions)
        Map<String, String> headers = kafkaMessage.getHeaders();
        assertNotNull(headers);
        assertEquals("custom-value", headers.get("custom-header"));
        assertEquals("test-event", headers.get("event-type"));
    }

    @Test
    public void testFromCloudEventWithNullData() throws ProtocolHandleException {
        // Create a test CloudEvent without data
        CloudEvent cloudEvent = CloudEventBuilder.v1()
            .withId("test-id")
            .withSource(URI.create("kafka://test-topic"))
            .withType("kafka.message")
            .withSubject("test-topic")
            .build();

        // Convert to Kafka message
        KafkaMessage kafkaMessage = (KafkaMessage) adapter.fromCloudEvent(cloudEvent);

        // Verify Kafka message properties
        assertNotNull(kafkaMessage);
        assertEquals("test-topic", kafkaMessage.getTopic());
        assertEquals("test-id", kafkaMessage.getKey());
        assertNull(kafkaMessage.getValue());
    }

    @Test
    public void testToBatchCloudEvent() throws ProtocolHandleException {
        // Create a test Kafka message
        KafkaMessage kafkaMessage = new KafkaMessage();
        kafkaMessage.setTopic("test-topic");
        kafkaMessage.setKey("test-key");
        kafkaMessage.setValue("test-value".getBytes());

        // Convert to batch CloudEvent
        var cloudEvents = adapter.toBatchCloudEvent(kafkaMessage);

        // Should return a list with one element
        assertNotNull(cloudEvents);
        assertEquals(1, cloudEvents.size());

        CloudEvent cloudEvent = cloudEvents.get(0);
        assertEquals("test-key", cloudEvent.getId());
        assertEquals("test-topic", cloudEvent.getSubject());
        assertArrayEquals("test-value".getBytes(), cloudEvent.getData().toBytes());
    }

    @Test
    public void testPluginLifecycle() throws Exception {
        // Test onLoad
        when(mockPluginInfo.getProtocolType()).thenReturn("kafka");
        adapter.onLoad(mockPluginInfo);
        // No exception should be thrown

        // Test onUnload
        adapter.onUnload(mockPluginInfo);
        // No exception should be thrown

        // Test onReload
        adapter.onReload(mockPluginInfo);
        // No exception should be thrown
    }

    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", adapter.getVersion());
    }

    @Test
    public void testGetDescription() {
        assertEquals("Kafka protocol adapter for EventMesh", adapter.getDescription());
    }

    @Test
    public void testSupportsHotReload() {
        assertTrue(adapter.supportsHotReload());
    }

    @Test
    public void testExceptionHandling() {
        // Test that exceptions are properly handled during conversion
        KafkaMessage invalidMessage = new KafkaMessage();
        // Don't set required fields to cause potential issues
        
        // Should not throw exception for basic operations
        assertDoesNotThrow(() -> {
            adapter.getProtocolType();
            adapter.canTransmitDirectly("kafka");
            adapter.getVersion();
            adapter.getDescription();
            adapter.supportsHotReload();
        });
    }
} 