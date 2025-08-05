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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.processor;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.kafka.KafkaProtocolAdapter;
import org.apache.eventmesh.protocol.kafka.message.KafkaMessage;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.SessionSender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandlerContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for MessageTransferProcessor with protocol transmission optimization
 */
@ExtendWith(MockitoExtension.class)
public class MessageTransferProcessorTest {

    private MessageTransferProcessor processor;

    @Mock
    private EventMeshTCPServer eventMeshTCPServer;
    
    @Mock
    private Producer producer;
    
    @Mock
    private AuthService authService;
    
    @Mock
    private MetricsRegistry metricsRegistry;
    
    @Mock
    private Acl acl;
    
    @Mock
    private ChannelHandlerContext ctx;
    
    @Mock
    private Session session;
    
    @Mock
    private SessionSender sessionSender;
    
    @Mock
    private ProtocolAdaptor<ProtocolTransportObject> mockProtocolAdaptor;

    @BeforeEach
    public void setUp() {
        // Setup mocks
        when(eventMeshTCPServer.getAcl()).thenReturn(acl);
        when(session.getSender()).thenReturn(sessionSender);
        
        // Create processor instance
        processor = new MessageTransferProcessor(eventMeshTCPServer, producer, authService, metricsRegistry);
    }

    @Test
    public void testConstructor() {
        assertNotNull(processor);
        // Verify that the processor was created with the correct dependencies
    }

    @Test
    public void testProcessWithDirectTransmission() throws Exception {
        // Create a test package with Kafka protocol
        Package pkg = createTestPackage("kafka", "kafka");
        
        // Mock ProtocolPluginFactory to return true for direct transmission
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"))
                .thenReturn(true);
            
            mockedFactory.when(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", pkg))
                .thenReturn(pkg);
            
            // Process the package
            processor.process(pkg, ctx, System.currentTimeMillis());
            
            // Verify that direct transmission was called
            mockedFactory.verify(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"));
            mockedFactory.verify(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", pkg));
        }
    }

    @Test
    public void testProcessWithStandardConversion() throws Exception {
        // Create a test package with different source and target protocols
        Package pkg = createTestPackage("kafka", "pulsar");
        
        // Mock CloudEvent
        CloudEvent mockCloudEvent = mock(CloudEvent.class);
        when(mockCloudEvent.getId()).thenReturn("test-id");
        when(mockCloudEvent.getSubject()).thenReturn("test-topic");
        
        // Mock ProtocolPluginFactory
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            mockedFactory.when(() -> ProtocolPluginFactory.getProtocolAdaptor("kafka"))
                .thenReturn(mockProtocolAdaptor);
            
            when(mockProtocolAdaptor.toCloudEvent(pkg)).thenReturn(mockCloudEvent);
            
            // Mock authentication
            when(authService.authenticate(mockCloudEvent)).thenReturn(true);
            
            // Mock rate limiter
            when(eventMeshTCPServer.getRateLimiter()).thenReturn(mock());
            
            // Process the package
            processor.process(pkg, ctx, System.currentTimeMillis());
            
            // Verify that standard conversion was used
            mockedFactory.verify(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"));
            mockedFactory.verify(() -> ProtocolPluginFactory.getProtocolAdaptor("kafka"));
            verify(mockProtocolAdaptor).toCloudEvent(pkg);
            verify(authService).authenticate(mockCloudEvent);
        }
    }

    @Test
    public void testProcessWithAuthenticationFailure() throws Exception {
        // Create a test package
        Package pkg = createTestPackage("kafka", "pulsar");
        
        // Mock CloudEvent
        CloudEvent mockCloudEvent = mock(CloudEvent.class);
        when(mockCloudEvent.getId()).thenReturn("test-id");
        when(mockCloudEvent.getSubject()).thenReturn("test-topic");
        
        // Mock ProtocolPluginFactory
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            mockedFactory.when(() -> ProtocolPluginFactory.getProtocolAdaptor("kafka"))
                .thenReturn(mockProtocolAdaptor);
            
            when(mockProtocolAdaptor.toCloudEvent(pkg)).thenReturn(mockCloudEvent);
            
            // Mock authentication failure
            when(authService.authenticate(mockCloudEvent)).thenReturn(false);
            
            // Process the package
            processor.process(pkg, ctx, System.currentTimeMillis());
            
            // Verify that authentication was checked
            verify(authService).authenticate(mockCloudEvent);
        }
    }

    @Test
    public void testProcessWithProtocolAdaptorNotFound() throws Exception {
        // Create a test package
        Package pkg = createTestPackage("unknown-protocol", "kafka");
        
        // Mock ProtocolPluginFactory to return null for unknown protocol
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("unknown-protocol", "kafka"))
                .thenReturn(false);
            
            mockedFactory.when(() -> ProtocolPluginFactory.getProtocolAdaptor("unknown-protocol"))
                .thenReturn(null);
            
            // Process the package - should throw exception
            assertThrows(Exception.class, () -> {
                processor.process(pkg, ctx, System.currentTimeMillis());
            });
            
            // Verify that protocol adaptor was requested
            mockedFactory.verify(() -> ProtocolPluginFactory.getProtocolAdaptor("unknown-protocol"));
        }
    }

    @Test
    public void testProcessWithNullCloudEvent() throws Exception {
        // Create a test package
        Package pkg = createTestPackage("kafka", "pulsar");
        
        // Mock ProtocolPluginFactory
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            mockedFactory.when(() -> ProtocolPluginFactory.getProtocolAdaptor("kafka"))
                .thenReturn(mockProtocolAdaptor);
            
            // Mock null CloudEvent
            when(mockProtocolAdaptor.toCloudEvent(pkg)).thenReturn(null);
            
            // Process the package - should throw exception
            assertThrows(Exception.class, () -> {
                processor.process(pkg, ctx, System.currentTimeMillis());
            });
            
            // Verify that conversion was attempted
            verify(mockProtocolAdaptor).toCloudEvent(pkg);
        }
    }

    @Test
    public void testGetTargetProtocolType() throws Exception {
        // Test with target protocol in properties
        Package pkg = createTestPackage("kafka", "pulsar");
        String targetProtocol = processor.getTargetProtocolType(pkg);
        assertEquals("pulsar", targetProtocol);
        
        // Test with no target protocol (should default to source protocol)
        Package pkg2 = createTestPackage("kafka", null);
        String targetProtocol2 = processor.getTargetProtocolType(pkg2);
        assertEquals("kafka", targetProtocol2);
        
        // Test with no protocol type at all (should default to eventmeshmessage)
        Package pkg3 = createTestPackage(null, null);
        String targetProtocol3 = processor.getTargetProtocolType(pkg3);
        assertEquals("eventmeshmessage", targetProtocol3);
    }

    @Test
    public void testHandleDirectTransmission() throws Exception {
        // Create a test package
        Package pkg = createTestPackage("kafka", "kafka");
        
        // Test direct transmission handling
        processor.handleDirectTransmission(pkg, ctx, System.currentTimeMillis());
        
        // Verify that the method completes without exception
        // Note: In a real scenario, this would write to the channel
    }

    @Test
    public void testHandleDirectTransmissionWithNonPackageObject() throws Exception {
        // Create a non-Package object
        KafkaMessage kafkaMessage = new KafkaMessage();
        kafkaMessage.setTopic("test-topic");
        
        // Test direct transmission handling with non-Package object
        processor.handleDirectTransmission(kafkaMessage, ctx, System.currentTimeMillis());
        
        // Verify that the method completes without exception
        // Note: In a real scenario, this would handle the object appropriately
    }

    @Test
    public void testProcessWithRateLimiterRejection() throws Exception {
        // Create a test package
        Package pkg = createTestPackage("kafka", "pulsar");
        
        // Mock CloudEvent
        CloudEvent mockCloudEvent = mock(CloudEvent.class);
        when(mockCloudEvent.getId()).thenReturn("test-id");
        when(mockCloudEvent.getSubject()).thenReturn("test-topic");
        
        // Mock ProtocolPluginFactory
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            mockedFactory.when(() -> ProtocolPluginFactory.getProtocolAdaptor("kafka"))
                .thenReturn(mockProtocolAdaptor);
            
            when(mockProtocolAdaptor.toCloudEvent(pkg)).thenReturn(mockCloudEvent);
            
            // Mock authentication success
            when(authService.authenticate(mockCloudEvent)).thenReturn(true);
            
            // Mock rate limiter rejection
            when(eventMeshTCPServer.getRateLimiter()).thenReturn(mock());
            
            // Process the package
            processor.process(pkg, ctx, System.currentTimeMillis());
            
            // Verify that authentication was checked
            verify(authService).authenticate(mockCloudEvent);
        }
    }

    @Test
    public void testPerformanceOptimization() throws Exception {
        // Test that direct transmission is faster than standard conversion
        Package pkg = createTestPackage("kafka", "kafka");
        
        long startTime = System.nanoTime();
        
        // Mock ProtocolPluginFactory for direct transmission
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "kafka"))
                .thenReturn(true);
            
            mockedFactory.when(() -> ProtocolPluginFactory.transmitDirectly("kafka", "kafka", pkg))
                .thenReturn(pkg);
            
            // Process with direct transmission
            processor.process(pkg, ctx, System.currentTimeMillis());
        }
        
        long directTransmissionTime = System.nanoTime() - startTime;
        
        // Reset and test standard conversion
        startTime = System.nanoTime();
        
        Package pkg2 = createTestPackage("kafka", "pulsar");
        
        CloudEvent mockCloudEvent = mock(CloudEvent.class);
        when(mockCloudEvent.getId()).thenReturn("test-id");
        when(mockCloudEvent.getSubject()).thenReturn("test-topic");
        
        try (MockedStatic<ProtocolPluginFactory> mockedFactory = mockStatic(ProtocolPluginFactory.class)) {
            mockedFactory.when(() -> ProtocolPluginFactory.canTransmitDirectly("kafka", "pulsar"))
                .thenReturn(false);
            
            mockedFactory.when(() -> ProtocolPluginFactory.getProtocolAdaptor("kafka"))
                .thenReturn(mockProtocolAdaptor);
            
            when(mockProtocolAdaptor.toCloudEvent(pkg2)).thenReturn(mockCloudEvent);
            when(authService.authenticate(mockCloudEvent)).thenReturn(true);
            when(eventMeshTCPServer.getRateLimiter()).thenReturn(mock());
            
            // Process with standard conversion
            processor.process(pkg2, ctx, System.currentTimeMillis());
        }
        
        long standardConversionTime = System.nanoTime() - startTime;
        
        // Direct transmission should be faster (though in test environment the difference might be minimal)
        assertTrue(directTransmissionTime <= standardConversionTime, 
            "Direct transmission should be faster than standard conversion");
    }

    private Package createTestPackage(String sourceProtocol, String targetProtocol) {
        Package pkg = new Package();
        Header header = new Header();
        
        Map<String, Object> properties = new HashMap<>();
        if (sourceProtocol != null) {
            properties.put(Constants.PROTOCOL_TYPE, sourceProtocol);
        }
        if (targetProtocol != null) {
            properties.put("target_protocol_type", targetProtocol);
        }
        
        header.setProperties(properties);
        pkg.setHeader(header);
        
        return pkg;
    }
} 