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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for ProtocolPluginFactory
 */
@ExtendWith(MockitoExtension.class)
public class ProtocolPluginFactoryTest {

    @Mock
    private KafkaMessage mockKafkaMessage;
    
    @Mock
    private PulsarMessage mockPulsarMessage;
    
    @Mock
    private RocketMQMessage mockRocketMQMessage;

    @BeforeEach
    public void setUp() {
        // Reset the factory before each test
        ProtocolPluginFactory.reloadPlugins();
    }

    @Test
    public void testGetProtocolAdaptor() {
        // Test getting existing protocol adaptors
        ProtocolAdaptor<ProtocolTransportObject> kafkaAdaptor = ProtocolPluginFactory.getProtocolAdaptor("kafka");
        assertNotNull(kafkaAdaptor);
        assertEquals("kafka", kafkaAdaptor.getProtocolType());

        ProtocolAdaptor<ProtocolTransportObject> pulsarAdaptor = ProtocolPluginFactory.getProtocolAdaptor("pulsar");
        assertNotNull(pulsarAdaptor);
        assertEquals("pulsar", pulsarAdaptor.getProtocolType());

        ProtocolAdaptor<ProtocolTransportObject> rocketmqAdaptor = ProtocolPluginFactory.getProtocolAdaptor("rocketmq");
        assertNotNull(rocketmqAdaptor);
        assertEquals("rocketmq", rocketmqAdaptor.getProtocolType());

        // Test getting non-existent protocol adaptor
        ProtocolAdaptor<ProtocolTransportObject> nonExistentAdaptor = ProtocolPluginFactory.getProtocolAdaptor("non-existent");
        assertNull(nonExistentAdaptor);
    }

    @Test
    public void testRegisterProtocolAdaptor() {
        // Create a mock protocol adaptor
        ProtocolAdaptor<ProtocolTransportObject> mockAdaptor = mock(ProtocolAdaptor.class);
        when(mockAdaptor.getProtocolType()).thenReturn("test-protocol");

        // Register the mock adaptor
        ProtocolPluginFactory.registerProtocolAdaptor("test-protocol", mockAdaptor);

        // Verify it was registered
        ProtocolAdaptor<ProtocolTransportObject> retrievedAdaptor = ProtocolPluginFactory.getProtocolAdaptor("test-protocol");
        assertNotNull(retrievedAdaptor);
        assertEquals("test-protocol", retrievedAdaptor.getProtocolType());
    }

    @Test
    public void testGetAllProtocolAdaptors() {
        Map<String, ProtocolAdaptor<?>> allAdaptors = ProtocolPluginFactory.getAllProtocolAdaptors();
        
        // Should contain the default protocol adaptors
        assertTrue(allAdaptors.containsKey("kafka"));
        assertTrue(allAdaptors.containsKey("pulsar"));
        assertTrue(allAdaptors.containsKey("rocketmq"));
        
        // Verify the adaptors are correct
        assertEquals("kafka", allAdaptors.get("kafka").getProtocolType());
        assertEquals("pulsar", allAdaptors.get("pulsar").getProtocolType());
        assertEquals("rocketmq", allAdaptors.get("rocketmq").getProtocolType());
    }

    @Test
    public void testCanTransmitDirectly() {
        // Test same protocol transmission (should be true)
        assertTrue(ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"));
        assertTrue(ProtocolPluginFactory.canTransmitDirectly("pulsar", "pulsar"));
        assertTrue(ProtocolPluginFactory.canTransmitDirectly("rocketmq", "rocketmq"));

        // Test different protocol transmission (should be false)
        assertFalse(ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"));
        assertFalse(ProtocolPluginFactory.canTransmitDirectly("pulsar", "rocketmq"));
        assertFalse(ProtocolPluginFactory.canTransmitDirectly("rocketmq", "kafka"));

        // Test with non-existent protocol
        assertFalse(ProtocolPluginFactory.canTransmitDirectly("non-existent", "kafka"));
        assertFalse(ProtocolPluginFactory.canTransmitDirectly("kafka", "non-existent"));
    }

    @Test
    public void testTransmitDirectly() throws Exception {
        // Test Kafka direct transmission
        KafkaMessage kafkaMessage = new KafkaMessage();
        kafkaMessage.setTopic("test-topic");
        kafkaMessage.setKey("test-key");
        kafkaMessage.setValue("test-value".getBytes());

        ProtocolTransportObject transmittedKafka = ProtocolPluginFactory.transmitDirectly("kafka", "kafka", kafkaMessage);
        assertNotNull(transmittedKafka);
        assertTrue(transmittedKafka instanceof KafkaMessage);
        assertEquals(kafkaMessage, transmittedKafka);

        // Test Pulsar direct transmission
        PulsarMessage pulsarMessage = new PulsarMessage();
        pulsarMessage.setTopicName("test-topic");
        pulsarMessage.setMessageId("test-id");
        pulsarMessage.setData("test-data".getBytes());

        ProtocolTransportObject transmittedPulsar = ProtocolPluginFactory.transmitDirectly("pulsar", "pulsar", pulsarMessage);
        assertNotNull(transmittedPulsar);
        assertTrue(transmittedPulsar instanceof PulsarMessage);
        assertEquals(pulsarMessage, transmittedPulsar);

        // Test RocketMQ direct transmission
        RocketMQMessage rocketmqMessage = new RocketMQMessage();
        rocketmqMessage.setTopic("test-topic");
        rocketmqMessage.setMessageId("test-id");
        rocketmqMessage.setBody("test-body".getBytes());

        ProtocolTransportObject transmittedRocketMQ = ProtocolPluginFactory.transmitDirectly("rocketmq", "rocketmq", rocketmqMessage);
        assertNotNull(transmittedRocketMQ);
        assertTrue(transmittedRocketMQ instanceof RocketMQMessage);
        assertEquals(rocketmqMessage, transmittedRocketMQ);
    }

    @Test
    public void testTransmitDirectlyWithDifferentProtocols() {
        // Test transmission between different protocols (should throw exception)
        KafkaMessage kafkaMessage = new KafkaMessage();
        kafkaMessage.setTopic("test-topic");

        assertThrows(IllegalArgumentException.class, () -> {
            ProtocolPluginFactory.transmitDirectly("kafka", "pulsar", kafkaMessage);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            ProtocolPluginFactory.transmitDirectly("pulsar", "rocketmq", kafkaMessage);
        });
    }

    @Test
    public void testTransmitDirectlyWithNonExistentProtocol() {
        KafkaMessage kafkaMessage = new KafkaMessage();
        kafkaMessage.setTopic("test-topic");

        assertThrows(IllegalArgumentException.class, () -> {
            ProtocolPluginFactory.transmitDirectly("non-existent", "kafka", kafkaMessage);
        });
    }

    @Test
    public void testReloadPlugins() {
        // Register a custom adaptor
        ProtocolAdaptor<ProtocolTransportObject> customAdaptor = mock(ProtocolAdaptor.class);
        when(customAdaptor.getProtocolType()).thenReturn("custom-protocol");
        ProtocolPluginFactory.registerProtocolAdaptor("custom-protocol", customAdaptor);

        // Verify it was registered
        assertNotNull(ProtocolPluginFactory.getProtocolAdaptor("custom-protocol"));

        // Reload plugins
        ProtocolPluginFactory.reloadPlugins();

        // Custom adaptor should be removed after reload
        assertNull(ProtocolPluginFactory.getProtocolAdaptor("custom-protocol"));

        // Default adaptors should still be available
        assertNotNull(ProtocolPluginFactory.getProtocolAdaptor("kafka"));
        assertNotNull(ProtocolPluginFactory.getProtocolAdaptor("pulsar"));
        assertNotNull(ProtocolPluginFactory.getProtocolAdaptor("rocketmq"));
    }

    @Test
    public void testLoadPlugin() {
        // Test loading a plugin (this would require actual JAR file)
        // For now, we test the method exists and handles errors gracefully
        boolean result = ProtocolPluginFactory.loadPlugin("test-protocol", "/non-existent/path.jar");
        assertFalse(result);
    }

    @Test
    public void testUnloadPlugin() {
        // Register a custom adaptor
        ProtocolAdaptor<ProtocolTransportObject> customAdaptor = mock(ProtocolAdaptor.class);
        when(customAdaptor.getProtocolType()).thenReturn("custom-protocol");
        ProtocolPluginFactory.registerProtocolAdaptor("custom-protocol", customAdaptor);

        // Verify it was registered
        assertTrue(ProtocolPluginFactory.isPluginLoaded("custom-protocol"));

        // Unload the plugin
        boolean result = ProtocolPluginFactory.unloadPlugin("custom-protocol");
        assertTrue(result);

        // Verify it was unloaded
        assertFalse(ProtocolPluginFactory.isPluginLoaded("custom-protocol"));

        // Try to unload non-existent plugin
        boolean nonExistentResult = ProtocolPluginFactory.unloadPlugin("non-existent");
        assertFalse(nonExistentResult);
    }

    @Test
    public void testIsPluginLoaded() {
        // Test default plugins
        assertTrue(ProtocolPluginFactory.isPluginLoaded("kafka"));
        assertTrue(ProtocolPluginFactory.isPluginLoaded("pulsar"));
        assertTrue(ProtocolPluginFactory.isPluginLoaded("rocketmq"));

        // Test non-existent plugin
        assertFalse(ProtocolPluginFactory.isPluginLoaded("non-existent"));
    }

    @Test
    public void testGetPluginInfo() {
        // Test getting info for existing plugin
        ProtocolPluginFactory.PluginInfo kafkaInfo = ProtocolPluginFactory.getPluginInfo("kafka");
        assertNotNull(kafkaInfo);
        assertEquals("kafka", kafkaInfo.getProtocolType());
        assertTrue(kafkaInfo.isLoaded());
        assertNotNull(kafkaInfo.getClassName());

        // Test getting info for non-existent plugin
        ProtocolPluginFactory.PluginInfo nonExistentInfo = ProtocolPluginFactory.getPluginInfo("non-existent");
        assertNull(nonExistentInfo);
    }

    @Test
    public void testPluginInfoProperties() {
        ProtocolPluginFactory.PluginInfo info = new ProtocolPluginFactory.PluginInfo("test", "TestClass", true);
        
        assertEquals("test", info.getProtocolType());
        assertEquals("TestClass", info.getClassName());
        assertTrue(info.isLoaded());
    }
}
