
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

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.kafka.raw.message.RawKafkaMessage;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Raw Kafka protocol adapter for raw Kafka client communication.
 * Supports raw Kafka message format without CloudEvent conversion.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class RawKafkaProtocolAdapter implements ProtocolAdaptor<RawKafkaMessage> {

    private static final Logger log = LoggerFactory.getLogger(RawKafkaProtocolAdapter.class);

    @Override
    public io.cloudevents.CloudEvent toCloudEvent(RawKafkaMessage rawMessage) throws ProtocolHandleException {
        try {
            io.cloudevents.core.builder.CloudEventBuilder builder = io.cloudevents.core.builder.CloudEventBuilder.v1()
                .withId(rawMessage.getKey() != null ? rawMessage.getKey() : java.util.UUID.randomUUID().toString())
                .withSource(java.net.URI.create("/eventmesh/kafka"))
                .withType("org.apache.eventmesh.kafka.raw")
                .withSubject(rawMessage.getTopic());

            // Add raw Kafka headers as extensions
            if (rawMessage.getHeaders() != null) {
                rawMessage.getHeaders().forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.withExtension("kafka.header." + key, value);
                    }
                });
            }

            // Add Kafka-specific metadata
            if (rawMessage.getPartition() != null) {
                builder.withExtension("kafka.partition", rawMessage.getPartition().toString());
            }
            if (rawMessage.getOffset() != null) {
                builder.withExtension("kafka.offset", rawMessage.getOffset().toString());
            }
            if (rawMessage.getTimestamp() != null) {
                builder.withExtension("kafka.timestamp", rawMessage.getTimestamp().toString());
            }

            // Set data
            if (rawMessage.getValue() != null) {
                builder.withData(rawMessage.getValue());
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert raw Kafka message to CloudEvent", e);
        }
    }

    @Override
    public java.util.List<io.cloudevents.CloudEvent> toBatchCloudEvent(java.util.List<RawKafkaMessage> protocolList) throws ProtocolHandleException {
        java.util.List<io.cloudevents.CloudEvent> result = new java.util.ArrayList<>();
        for (RawKafkaMessage msg : protocolList) {
            result.add(toCloudEvent(msg));
        }
        return result;
    }

    @Override
    public RawKafkaMessage fromCloudEvent(io.cloudevents.CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            RawKafkaMessage rawMessage = new RawKafkaMessage();
            rawMessage.setTopic(cloudEvent.getSubject());
            rawMessage.setKey(cloudEvent.getId());
            
            if (cloudEvent.getData() != null) {
                rawMessage.setValue(cloudEvent.getData().toBytes());
            }

            // Convert CloudEvent extensions back to Kafka headers
            if (cloudEvent.getExtensionNames() != null) {
                cloudEvent.getExtensionNames().forEach(extensionName -> {
                    Object value = cloudEvent.getExtension(extensionName);
                    if (value != null) {
                        if (extensionName.startsWith("kafka.header.")) {
                            // Extract original header name
                            String headerName = extensionName.substring("kafka.header.".length());
                            rawMessage.addHeader(headerName, value.toString());
                        } else if (extensionName.equals("kafka.partition")) {
                            rawMessage.setPartition(Integer.parseInt(value.toString()));
                        } else if (extensionName.equals("kafka.offset")) {
                            rawMessage.setOffset(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("kafka.timestamp")) {
                            rawMessage.setTimestamp(Long.parseLong(value.toString()));
                        } else {
                            // Add other extensions as headers
                            rawMessage.addHeader(extensionName, value.toString());
                        }
                    }
                });
            }

            return rawMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to raw Kafka message", e);
        }
    }

    @Override
    public String getProtocolType() {
        return "kafka-raw";
    }

    @Override
    public boolean canTransmitDirectly(String targetProtocolType) {
        return "kafka-raw".equals(targetProtocolType) || "kafka".equals(targetProtocolType);
    }

    @Override
    public RawKafkaMessage transmitDirectly(RawKafkaMessage protocol) throws ProtocolHandleException {
        // For raw Kafka to raw Kafka, directly return the original message
        // This avoids unnecessary CloudEvent conversion for better performance
        return protocol;
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Raw Kafka protocol adapter for raw Kafka client communication";
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }}
