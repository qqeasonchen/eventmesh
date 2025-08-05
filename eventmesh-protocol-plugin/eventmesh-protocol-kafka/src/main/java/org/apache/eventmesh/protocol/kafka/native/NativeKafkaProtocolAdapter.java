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

package org.apache.eventmesh.protocol.kafka.native;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.kafka.native.message.NativeKafkaMessage;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.spi.PluginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Native Kafka protocol adapter for direct Kafka client communication.
 * Supports native Kafka message format without CloudEvent conversion.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class NativeKafkaProtocolAdapter implements ProtocolAdaptor<NativeKafkaMessage> {

    private static final Logger log = LoggerFactory.getLogger(NativeKafkaProtocolAdapter.class);

    @Override
    public CloudEvent toCloudEvent(NativeKafkaMessage nativeMessage) throws ProtocolHandleException {
        try {
            CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
                .withId(nativeMessage.getKey() != null ? nativeMessage.getKey() : 
                    nativeMessage.getTopic() + "_" + System.currentTimeMillis())
                .withSource(URI.create("kafka://" + nativeMessage.getTopic()))
                .withType("kafka.native.message")
                .withSubject(nativeMessage.getTopic());

            // Add native Kafka headers as extensions
            if (nativeMessage.getHeaders() != null) {
                nativeMessage.getHeaders().forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.withExtension("kafka.header." + key, value);
                    }
                });
            }

            // Add Kafka-specific metadata
            if (nativeMessage.getPartition() != null) {
                builder.withExtension("kafka.partition", nativeMessage.getPartition().toString());
            }
            if (nativeMessage.getOffset() != null) {
                builder.withExtension("kafka.offset", nativeMessage.getOffset().toString());
            }
            if (nativeMessage.getTimestamp() != null) {
                builder.withExtension("kafka.timestamp", nativeMessage.getTimestamp().toString());
            }

            // Set data
            if (nativeMessage.getValue() != null) {
                builder.withData(nativeMessage.getValue());
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert native Kafka message to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(NativeKafkaMessage nativeMessage) throws ProtocolHandleException {
        // For single message, return as list with one element
        return List.of(toCloudEvent(nativeMessage));
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            NativeKafkaMessage nativeMessage = new NativeKafkaMessage();
            nativeMessage.setTopic(cloudEvent.getSubject());
            nativeMessage.setKey(cloudEvent.getId());
            
            if (cloudEvent.getData() != null) {
                nativeMessage.setValue(cloudEvent.getData().toBytes());
            }

            // Convert CloudEvent extensions back to Kafka headers
            if (cloudEvent.getExtensionNames() != null) {
                cloudEvent.getExtensionNames().forEach(extensionName -> {
                    Object value = cloudEvent.getExtension(extensionName);
                    if (value != null) {
                        if (extensionName.startsWith("kafka.header.")) {
                            // Extract original header name
                            String headerName = extensionName.substring("kafka.header.".length());
                            nativeMessage.addHeader(headerName, value.toString());
                        } else if (extensionName.equals("kafka.partition")) {
                            nativeMessage.setPartition(Integer.parseInt(value.toString()));
                        } else if (extensionName.equals("kafka.offset")) {
                            nativeMessage.setOffset(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("kafka.timestamp")) {
                            nativeMessage.setTimestamp(Long.parseLong(value.toString()));
                        } else {
                            // Add other extensions as headers
                            nativeMessage.addHeader(extensionName, value.toString());
                        }
                    }
                });
            }

            return nativeMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to native Kafka message", e);
        }
    }

    @Override
    public String getProtocolType() {
        return "kafka-native";
    }

    @Override
    public boolean canTransmitDirectly(String targetProtocolType) {
        return "kafka-native".equals(targetProtocolType) || "kafka".equals(targetProtocolType);
    }

    @Override
    public NativeKafkaMessage transmitDirectly(NativeKafkaMessage protocol) throws ProtocolHandleException {
        // For native Kafka to native Kafka, directly return the original message
        // This avoids unnecessary CloudEvent conversion for better performance
        return protocol;
    }

    @Override
    public void onLoad(PluginInfo pluginInfo) throws Exception {
        log.info("Native Kafka protocol adapter loaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public void onUnload(PluginInfo pluginInfo) throws Exception {
        log.info("Native Kafka protocol adapter unloaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public void onReload(PluginInfo pluginInfo) throws Exception {
        log.info("Native Kafka protocol adapter reloaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Native Kafka protocol adapter for direct Kafka client communication";
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }
} 