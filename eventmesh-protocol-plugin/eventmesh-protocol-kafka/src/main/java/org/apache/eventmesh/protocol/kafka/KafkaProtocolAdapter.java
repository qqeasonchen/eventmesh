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

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.kafka.message.KafkaMessage;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.spi.PluginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

/**
 * Kafka protocol adapter, converts between Kafka messages and CloudEvents.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class KafkaProtocolAdapter implements ProtocolAdaptor<KafkaMessage> {

    private static final Logger log = LoggerFactory.getLogger(KafkaProtocolAdapter.class);

    @Override
    public CloudEvent toCloudEvent(KafkaMessage kafkaMessage) throws ProtocolHandleException {
        try {
            CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
                .withId(kafkaMessage.getKey() != null ? kafkaMessage.getKey() : kafkaMessage.getTopic() + "_" + System.currentTimeMillis())
                .withSource(URI.create("kafka://" + kafkaMessage.getTopic()))
                .withType("kafka.message")
                .withSubject(kafkaMessage.getTopic());

            // Add headers as extensions
            if (kafkaMessage.getHeaders() != null) {
                kafkaMessage.getHeaders().forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.withExtension(key, value);
                    }
                });
            }

            // Set data
            if (kafkaMessage.getValue() != null) {
                builder.withData(kafkaMessage.getValue());
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert Kafka message to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(KafkaMessage kafkaMessage) throws ProtocolHandleException {
        // For single message, return as list with one element
        return List.of(toCloudEvent(kafkaMessage));
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            KafkaMessage kafkaMessage = new KafkaMessage();
            kafkaMessage.setTopic(cloudEvent.getSubject());
            kafkaMessage.setKey(cloudEvent.getId());
            
            if (cloudEvent.getData() != null) {
                kafkaMessage.setValue(cloudEvent.getData().toBytes());
            }

            // Convert extensions to headers
            if (cloudEvent.getExtensionNames() != null) {
                cloudEvent.getExtensionNames().forEach(extensionName -> {
                    Object value = cloudEvent.getExtension(extensionName);
                    if (value != null) {
                        kafkaMessage.addHeader(extensionName, value.toString());
                    }
                });
            }

            return kafkaMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to Kafka message", e);
        }
    }

    @Override
    public String getProtocolType() {
        return "kafka";
    }

    @Override
    public boolean canTransmitDirectly(String targetProtocolType) {
        return "kafka".equals(targetProtocolType);
    }

    @Override
    public KafkaMessage transmitDirectly(KafkaMessage protocol) throws ProtocolHandleException {
        // For Kafka to Kafka, directly return the original message
        // This avoids unnecessary CloudEvent conversion for better performance
        return protocol;
    }

    @Override
    public void onLoad(PluginInfo pluginInfo) throws Exception {
        log.info("Kafka protocol adapter loaded: {}", pluginInfo.getProtocolType());
        // Initialize Kafka-specific resources if needed
    }

    @Override
    public void onUnload(PluginInfo pluginInfo) throws Exception {
        log.info("Kafka protocol adapter unloaded: {}", pluginInfo.getProtocolType());
        // Clean up Kafka-specific resources if needed
    }

    @Override
    public void onReload(PluginInfo pluginInfo) throws Exception {
        log.info("Kafka protocol adapter reloaded: {}", pluginInfo.getProtocolType());
        // Handle reload logic if needed
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Kafka protocol adapter for EventMesh";
    }

    @Override
    public boolean supportsHotReload() {
        return true; // Kafka adapter supports hot reloading
    }
} 