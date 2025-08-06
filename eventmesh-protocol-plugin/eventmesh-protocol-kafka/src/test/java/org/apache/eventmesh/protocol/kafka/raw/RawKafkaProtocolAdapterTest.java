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

package org.apache.eventmesh.protocol.kafka.raw;

import org.apache.eventmesh.protocol.api.PluginLifecycle;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.kafka.raw.message.RawKafkaMessage;

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
 * Test cases for RawKafkaProtocolAdapter
 */
@ExtendWith(MockitoExtension.class)
public class RawKafkaProtocolAdapterTest {

    private RawKafkaProtocolAdapter adapter;

    @Mock
    private PluginLifecycle.PluginInfo mockPluginInfo;

    @BeforeEach
    public void setUp() {
        adapter = new RawKafkaProtocolAdapter();
    }

    @Test
    public void testGetProtocolType() {
        assertEquals("kafka-raw", adapter.getProtocolType());
    }

    @Test
    public void testCanTransmitDirectly() {
        // Test same protocol transmission
        assertTrue(adapter.canTransmitDirectly("kafka-raw"));
        assertTrue(adapter.canTransmitDirectly("kafka"));
        
        // Test different protocol transmission
        assertFalse(adapter.canTransmitDirectly("pulsar"));
        assertFalse(adapter.canTransmitDirectly("rocketmq"));
        assertFalse(adapter.canTransmitDirectly("eventmeshmessage"));
    }

    @Test
    public void testTransmitDirectly() throws ProtocolHandleException {
        // Create a test raw Kafka message
        RawKafkaMessage originalMessage = new RawKafkaMessage();
        originalMessage.setTopic("test-topic");
        originalMessage.setKey("test-key");
        originalMessage.setValue("test-value".getBytes());
        originalMessage.setPartition(0);
        originalMessage.setOffset(123L);
        originalMessage.setTimestamp(System.currentTimeMillis());
        
        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        originalMessage.setHeaders(headers);

        // Test direct transmission
        RawKafkaMessage transmittedMessage = adapter.transmitDirectly(originalMessage);
        
        // Should return the same object (no conversion)
        assertSame(originalMessage, transmittedMessage);
        assertEquals("test-topic", transmittedMessage.getTopic());
        assertEquals("test-key", transmittedMessage.getKey());
        assertArrayEquals("test-value".getBytes(), transmittedMessage.getValue());
        assertEquals(0, transmittedMessage.getPartition());
        assertEquals(123L, transmittedMessage.getOffset());
        assertEquals(headers, transmittedMessage.getHeaders());
    }

    @Test
    public void testTransmitDirectlyWithNullMessage() throws ProtocolHandleException {
        // Test with null message
        RawKafkaMessage transmittedMessage = adapter.transmitDirectly(null);
        assertNull(transmittedMessage);
    }

    @Test
    public void testToCloudEvent() throws ProtocolHandleException {
        // Create a test raw Kafka message
        RawKafkaMessage rawMessage = new RawKafkaMessage();
        rawMessage.setTopic("test-topic");
        rawMessage.setKey("test-key");
        rawMessage.setValue("test-value".getBytes());
        rawMessage.setPartition(1);
        rawMessage.setOffset(456L);
        rawMessage.setTimestamp(1234567890L);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        rawMessage.setHeaders(headers);

        // Convert to CloudEvent
        CloudEvent cloudEvent = adapter.toCloudEvent(rawMessage);

        // Verify CloudEvent properties
        assertEquals("test-key", cloudEvent.getId());
        assertEquals(URI.create("kafka://test-topic"), cloudEvent.getSource());
        assertEquals("kafka.raw.message", cloudEvent.getType());
        assertEquals("test-topic", cloudEvent.getSubject());
        assertArrayEquals("test-value".getBytes(), cloudEvent.getData().toBytes());

        // Verify extensions
        assertEquals("value1", cloudEvent.getExtension("kafka.header.header1"));
        assertEquals("value2", cloudEvent.getExtension("kafka.header.header2"));
        assertEquals("1", cloudEvent.getExtension("kafka.partition"));
        assertEquals("456", cloudEvent.getExtension("kafka.offset"));
        assertEquals("1234567890", cloudEvent.getExtension("kafka.timestamp"));
    }

    @Test
    public void testToCloudEventWithNullKey() throws ProtocolHandleException {
        // Create a test raw Kafka message without key
        RawKafkaMessage rawMessage = new RawKafkaMessage();
        rawMessage.setTopic("test-topic");
        rawMessage.setValue("test-value".getBytes());

        // Convert to CloudEvent
        CloudEvent cloudEvent = adapter.toCloudEvent(rawMessage);

        // Verify CloudEvent properties
        assertTrue(cloudEvent.getId().startsWith("test-topic_"));
        assertEquals(URI.create("kafka://test-topic"), cloudEvent.getSource());
        assertEquals("kafka.raw.message", cloudEvent.getType());
        assertEquals("test-topic", cloudEvent.getSubject());
        assertArrayEquals("test-value".getBytes(), cloudEvent.getData().toBytes());
    }

    @Test
    public void testToCloudEventWithNullValue() throws ProtocolHandleException {
        // Create a test raw Kafka message without value
        RawKafkaMessage rawMessage = new RawKafkaMessage();
        rawMessage.setTopic("test-topic");
        rawMessage.setKey("test-key");

        // Convert to CloudEvent
        CloudEvent cloudEvent = adapter.toCloudEvent(rawMessage);

        // Verify CloudEvent properties
        assertEquals("test-key", cloudEvent.getId());
        assertEquals(URI.create("kafka://test-topic"), cloudEvent.getSource());
        assertEquals("kafka.raw.message", cloudEvent.getType());
        assertEquals("test-topic", cloudEvent.getSubject());
        assertNull(cloudEvent.getData());
    }

    @Test
    public void testFromCloudEvent() throws ProtocolHandleException {
        // Create a test CloudEvent
        CloudEvent cloudEvent = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
            .withId("test-key")
            .withSource(URI.create("kafka://test-topic"))
            .withType("kafka.raw.message")
            .withSubject("test-topic")
            .withData("test-value".getBytes())
            .withExtension("kafka.header.header1", "value1")
            .withExtension("kafka.header.header2", "value2")
            .withExtension("kafka.partition", "1")
            .withExtension("kafka.offset", "456")
            .withExtension("kafka.timestamp", "1234567890")
            .build();

        // Convert to raw Kafka message
        RawKafkaMessage rawMessage = (RawKafkaMessage) adapter.fromCloudEvent(cloudEvent);

        // Verify raw Kafka message properties
        assertEquals("test-topic", rawMessage.getTopic());
        assertEquals("test-key", rawMessage.getKey());
        assertArrayEquals("test-value".getBytes(), rawMessage.getValue());
        assertEquals("value1", rawMessage.getHeader("header1"));
        assertEquals("value2", rawMessage.getHeader("header2"));
        assertEquals(1, rawMessage.getPartition());
        assertEquals(456L, rawMessage.getOffset());
        assertEquals(1234567890L, rawMessage.getTimestamp());
    }

    @Test
    public void testFromCloudEventWithNullData() throws ProtocolHandleException {
        // Create a test CloudEvent without data
        CloudEvent cloudEvent = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
            .withId("test-key")
            .withSource(URI.create("kafka://test-topic"))
            .withType("kafka.raw.message")
            .withSubject("test-topic")
            .build();

        // Convert to raw Kafka message
        RawKafkaMessage rawMessage = (RawKafkaMessage) adapter.fromCloudEvent(cloudEvent);

        // Verify raw Kafka message properties
        assertEquals("test-topic", rawMessage.getTopic());
        assertEquals("test-key", rawMessage.getKey());
        assertNull(rawMessage.getValue());
    }

    @Test
    public void testToBatchCloudEvent() throws ProtocolHandleException {
        // Create a test raw Kafka message
        RawKafkaMessage rawMessage = new RawKafkaMessage();
        rawMessage.setTopic("test-topic");
        rawMessage.setKey("test-key");
        rawMessage.setValue("test-value".getBytes());

        // Convert to batch CloudEvent
        var cloudEvents = adapter.toBatchCloudEvent(rawMessage);

        // Should return a list with one element
        assertEquals(1, cloudEvents.size());
        assertEquals("test-key", cloudEvents.get(0).getId());
        assertEquals("test-topic", cloudEvents.get(0).getSubject());
    }

    @Test
    public void testPluginLifecycle() throws Exception {
        when(mockPluginInfo.getProtocolType()).thenReturn("kafka-raw");

        // Test onLoad
        adapter.onLoad(mockPluginInfo);

        // Test onUnload
        adapter.onUnload(mockPluginInfo);

        // Test onReload
        adapter.onReload(mockPluginInfo);

        // Verify interactions
        verify(mockPluginInfo, times(3)).getProtocolType();
    }

    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", adapter.getVersion());
    }

    @Test
    public void testGetDescription() {
        assertEquals("Raw Kafka protocol adapter for raw Kafka client communication", adapter.getDescription());
    }

    @Test
    public void testSupportsHotReload() {
        assertTrue(adapter.supportsHotReload());
    }

    @Test
    public void testExceptionHandling() {
        // Test with invalid CloudEvent
        CloudEvent invalidCloudEvent = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
            .withId("test-id")
            .withSource(URI.create("invalid://source"))
            .withType("test.type")
            .build();

        // Should throw ProtocolHandleException
        assertThrows(ProtocolHandleException.class, () -> {
            adapter.fromCloudEvent(invalidCloudEvent);
        });
    }
} 