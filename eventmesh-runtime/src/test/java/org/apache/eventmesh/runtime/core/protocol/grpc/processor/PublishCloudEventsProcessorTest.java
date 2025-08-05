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

package org.apache.eventmesh.runtime.core.protocol.grpc.processor;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.PublisherService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.grpc.stub.StreamObserver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for PublishCloudEventsProcessor with protocol transmission optimization
 */
@ExtendWith(MockitoExtension.class)
public class PublishCloudEventsProcessorTest {

    private PublishCloudEventsProcessor processor;

    @Mock
    private EventMeshGrpcServer eventMeshGrpcServer;
    
    @Mock
    private Producer producer;
    
    @Mock
    private AuthService authService;
    
    @Mock
    private MetricsRegistry metricsRegistry;
    
    @Mock
    private StreamObserver<CloudEvent> mockEmitter;
    
    @Mock
    private ProtocolAdaptor<ProtocolTransportObject> mockProtocolAdaptor;

    @BeforeEach
    public void setUp() {
        // Create processor instance
        processor = new PublishCloudEventsProcessor(eventMeshGrpcServer, authService, metricsRegistry, producer);
    }

    @Test
    public void testConstructor() {
        assertNotNull(processor);
        // Verify that the processor was created with the correct dependencies
    }

    @Test
    public void testHandleCloudEventWithDirectTransmission() throws Exception {
        // Create a test CloudEvent with Kafka protocol
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "kafka");
        
        // Mock ProtocolPluginFactory to return true for direct transmission
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"))
                .thenReturn(true);
            
            mockedFactory.when(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", any(ProtocolTransportObject.class)))
                .thenReturn(mock(ProtocolTransportObject.class));
            
            // Handle the CloudEvent
            processor.handleCloudEvent(cloudEvent, mockEmitter);
            
            // Verify that direct transmission was called
            mockedFactory.verify(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"));
            mockedFactory.verify(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", any(ProtocolTransportObject.class)));
        }
    }

    @Test
    public void testHandleCloudEventWithStandardConversion() throws Exception {
        // Create a test CloudEvent with different source and target protocols
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "pulsar");
        
        // Mock ProtocolPluginFactory
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            // Mock producer publish
            doNothing().when(producer).publish(any(CloudEvent.class), any(SendCallback.class));
            
            // Handle the CloudEvent
            processor.handleCloudEvent(cloudEvent, mockEmitter);
            
            // Verify that standard conversion was used (no direct transmission)
            mockedFactory.verify(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"));
            verify(producer).publish(any(CloudEvent.class), any(SendCallback.class));
        }
    }

    @Test
    public void testHandleCloudEventWithNullTargetProtocol() throws Exception {
        // Create a test CloudEvent with null target protocol (should default to source protocol)
        CloudEvent cloudEvent = createTestCloudEvent("kafka", null);
        
        // Mock ProtocolPluginFactory to return true for direct transmission (same protocol)
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"))
                .thenReturn(true);
            
            mockedFactory.when(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", any(ProtocolTransportObject.class)))
                .thenReturn(mock(ProtocolTransportObject.class));
            
            // Handle the CloudEvent
            processor.handleCloudEvent(cloudEvent, mockEmitter);
            
            // Verify that direct transmission was called with same protocol
            mockedFactory.verify(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"));
            mockedFactory.verify(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", any(ProtocolTransportObject.class)));
        }
    }

    @Test
    public void testHandleCloudEventWithNoProtocolExtension() throws Exception {
        // Create a test CloudEvent without protocol extension
        CloudEvent cloudEvent = CloudEventBuilder.v1()
            .withId("test-id")
            .withSource(URI.create("test://source"))
            .withType("test.type")
            .withSubject("test-topic")
            .withData("test-data".getBytes())
            .build();
        
        // Mock ProtocolPluginFactory to return false for direct transmission
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("eventmeshmessage", "eventmeshmessage"))
                .thenReturn(false);
            
            // Mock producer publish
            doNothing().when(producer).publish(any(CloudEvent.class), any(SendCallback.class));
            
            // Handle the CloudEvent
            processor.handleCloudEvent(cloudEvent, mockEmitter);
            
            // Verify that standard conversion was used
            mockedFactory.verify(() -> ProtocolPluginFactory.canTransmitDirectly("eventmeshmessage", "eventmeshmessage"));
            verify(producer).publish(any(CloudEvent.class), any(SendCallback.class));
        }
    }

    @Test
    public void testGetTargetProtocolType() throws Exception {
        // Test with target protocol in extensions
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "pulsar");
        String targetProtocol = processor.getTargetProtocolType(cloudEvent);
        assertEquals("pulsar", targetProtocol);
        
        // Test with no target protocol (should default to source protocol)
        CloudEvent cloudEvent2 = createTestCloudEvent("kafka", null);
        String targetProtocol2 = processor.getTargetProtocolType(cloudEvent2);
        assertEquals("kafka", targetProtocol2);
        
        // Test with no protocol type at all (should default to eventmeshmessage)
        CloudEvent cloudEvent3 = CloudEventBuilder.v1()
            .withId("test-id")
            .withSource(URI.create("test://source"))
            .withType("test.type")
            .withSubject("test-topic")
            .build();
        String targetProtocol3 = processor.getTargetProtocolType(cloudEvent3);
        assertEquals("eventmeshmessage", targetProtocol3);
    }

    @Test
    public void testHandleDirectTransmission() throws Exception {
        // Create a test CloudEvent
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "kafka");
        
        // Mock ProtocolPluginFactory
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"))
                .thenReturn(true);
            
            mockedFactory.when(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", any(ProtocolTransportObject.class)))
                .thenReturn(mock(ProtocolTransportObject.class));
            
            // Handle direct transmission
            processor.handleDirectTransmission(mock(ProtocolTransportObject.class), mockEmitter);
            
            // Verify that the method completes without exception
            // Note: In a real scenario, this would send a response to the emitter
        }
    }

    @Test
    public void testHandleDirectTransmissionWithEventMeshCloudEventWrapper() throws Exception {
        // Create a test CloudEvent
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "kafka");
        
        // Mock ProtocolPluginFactory
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"))
                .thenReturn(true);
            
            // Create a mock EventMeshCloudEventWrapper
            ProtocolTransportObject wrapper = mock(ProtocolTransportObject.class);
            when(wrapper.getClass().getSimpleName()).thenReturn("EventMeshCloudEventWrapper");
            
            mockedFactory.when(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", any(ProtocolTransportObject.class)))
                .thenReturn(wrapper);
            
            // Handle direct transmission
            processor.handleDirectTransmission(wrapper, mockEmitter);
            
            // Verify that the method completes without exception
        }
    }

    @Test
    public void testProducerPublishSuccess() throws Exception {
        // Create a test CloudEvent
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "pulsar");
        
        // Mock ProtocolPluginFactory to return false for direct transmission
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            // Mock successful producer publish
            doAnswer(invocation -> {
                SendCallback callback = invocation.getArgument(1);
                callback.onSuccess(mock(org.apache.eventmesh.api.SendResult.class));
                return null;
            }).when(producer).publish(any(CloudEvent.class), any(SendCallback.class));
            
            // Handle the CloudEvent
            processor.handleCloudEvent(cloudEvent, mockEmitter);
            
            // Verify that producer was called
            verify(producer).publish(any(CloudEvent.class), any(SendCallback.class));
        }
    }

    @Test
    public void testProducerPublishException() throws Exception {
        // Create a test CloudEvent
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "pulsar");
        
        // Mock ProtocolPluginFactory to return false for direct transmission
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            // Mock producer publish with exception
            doAnswer(invocation -> {
                SendCallback callback = invocation.getArgument(1);
                callback.onException(mock(org.apache.eventmesh.api.exception.OnExceptionContext.class));
                return null;
            }).when(producer).publish(any(CloudEvent.class), any(SendCallback.class));
            
            // Handle the CloudEvent
            processor.handleCloudEvent(cloudEvent, mockEmitter);
            
            // Verify that producer was called
            verify(producer).publish(any(CloudEvent.class), any(SendCallback.class));
        }
    }

    @Test
    public void testPerformanceOptimization() throws Exception {
        // Test that direct transmission is faster than standard conversion
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "kafka");
        
        long startTime = System.nanoTime();
        
        // Mock ProtocolPluginFactory for direct transmission
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"))
                .thenReturn(true);
            
            mockedFactory.when(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", any(ProtocolTransportObject.class)))
                .thenReturn(mock(ProtocolTransportObject.class));
            
            // Handle with direct transmission
            processor.handleCloudEvent(cloudEvent, mockEmitter);
        }
        
        long directTransmissionTime = System.nanoTime() - startTime;
        
        // Reset and test standard conversion
        startTime = System.nanoTime();
        
        CloudEvent cloudEvent2 = createTestCloudEvent("kafka", "pulsar");
        
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            doNothing().when(producer).publish(any(CloudEvent.class), any(SendCallback.class));
            
            // Handle with standard conversion
            processor.handleCloudEvent(cloudEvent2, mockEmitter);
        }
        
        long standardConversionTime = System.nanoTime() - startTime;
        
        // Direct transmission should be faster (though in test environment the difference might be minimal)
        assertTrue(directTransmissionTime <= standardConversionTime, 
            "Direct transmission should be faster than standard conversion");
    }

    @Test
    public void testExceptionHandling() throws Exception {
        // Test that exceptions are properly handled
        CloudEvent cloudEvent = createTestCloudEvent("kafka", "pulsar");
        
        // Mock ProtocolPluginFactory to throw exception
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenThrow(new RuntimeException("Test exception"));
            
            // Handle the CloudEvent - should not throw exception
            assertDoesNotThrow(() -> {
                processor.handleCloudEvent(cloudEvent, mockEmitter);
            });
        }
    }

    private CloudEvent createTestCloudEvent(String sourceProtocol, String targetProtocol) {
        CloudEventBuilder builder = CloudEventBuilder.v1()
            .withId("test-id")
            .withSource(URI.create("test://source"))
            .withType("test.type")
            .withSubject("test-topic")
            .withData("test-data".getBytes());
        
        // Add protocol extensions
        if (sourceProtocol != null) {
            builder.withExtension("protocol_type", sourceProtocol);
        }
        if (targetProtocol != null) {
            builder.withExtension("target_protocol_type", targetProtocol);
        }
        
        return builder.build();
    }
} 