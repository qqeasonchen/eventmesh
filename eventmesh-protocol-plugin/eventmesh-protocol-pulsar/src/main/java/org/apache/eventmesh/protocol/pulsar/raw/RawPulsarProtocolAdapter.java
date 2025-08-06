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

package org.apache.eventmesh.protocol.pulsar.raw;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.pulsar.raw.message.RawPulsarMessage;
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
 * Raw Pulsar protocol adapter for raw Pulsar client communication.
 * Supports raw Pulsar message format without CloudEvent conversion.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class RawPulsarProtocolAdapter implements ProtocolAdaptor<RawPulsarMessage> {

    private static final Logger log = LoggerFactory.getLogger(RawPulsarProtocolAdapter.class);

    @Override
    public CloudEvent toCloudEvent(RawPulsarMessage rawMessage) throws ProtocolHandleException {
        try {
            CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
                .withId(rawMessage.getMessageId() != null ? rawMessage.getMessageId() : 
                    rawMessage.getTopic() + "_" + System.currentTimeMillis())
                .withSource(URI.create("pulsar://" + rawMessage.getTopic()))
                .withType("pulsar.raw.message")
                .withSubject(rawMessage.getTopic());

            // Add raw Pulsar properties as extensions
            if (rawMessage.getProperties() != null) {
                rawMessage.getProperties().forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.withExtension("pulsar.property." + key, value);
                    }
                });
            }

            // Add Pulsar-specific metadata
            if (rawMessage.getSequenceId() != null) {
                builder.withExtension("pulsar.sequenceId", rawMessage.getSequenceId().toString());
            }
            if (rawMessage.getPublishTime() != null) {
                builder.withExtension("pulsar.publishTime", rawMessage.getPublishTime().toString());
            }
            if (rawMessage.getEventTime() != null) {
                builder.withExtension("pulsar.eventTime", rawMessage.getEventTime().toString());
            }
            if (rawMessage.getKey() != null) {
                builder.withExtension("pulsar.key", rawMessage.getKey());
            }
            if (rawMessage.getOrderingKey() != null) {
                builder.withExtension("pulsar.orderingKey", rawMessage.getOrderingKey());
            }

            // Set data
            if (rawMessage.getData() != null) {
                builder.withData(rawMessage.getData());
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert raw Pulsar message to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(RawPulsarMessage rawMessage) throws ProtocolHandleException {
        // For single message, return as list with one element
        return List.of(toCloudEvent(rawMessage));
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            RawPulsarMessage rawMessage = new RawPulsarMessage();
            rawMessage.setTopic(cloudEvent.getSubject());
            rawMessage.setMessageId(cloudEvent.getId());
            
            if (cloudEvent.getData() != null) {
                rawMessage.setData(cloudEvent.getData().toBytes());
            }

            // Convert CloudEvent extensions back to Pulsar properties
            if (cloudEvent.getExtensionNames() != null) {
                cloudEvent.getExtensionNames().forEach(extensionName -> {
                    Object value = cloudEvent.getExtension(extensionName);
                    if (value != null) {
                        if (extensionName.startsWith("pulsar.property.")) {
                            // Extract original property name
                            String propertyName = extensionName.substring("pulsar.property.".length());
                            rawMessage.addProperty(propertyName, value.toString());
                        } else if (extensionName.equals("pulsar.sequenceId")) {
                            rawMessage.setSequenceId(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("pulsar.publishTime")) {
                            rawMessage.setPublishTime(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("pulsar.eventTime")) {
                            rawMessage.setEventTime(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("pulsar.key")) {
                            rawMessage.setKey(value.toString());
                        } else if (extensionName.equals("pulsar.orderingKey")) {
                            rawMessage.setOrderingKey(value.toString().getBytes());
                        } else {
                            // Add other extensions as properties
                            rawMessage.addProperty(extensionName, value.toString());
                        }
                    }
                });
            }

            return rawMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to raw Pulsar message", e);
        }
    }

    @Override
    public String getProtocolType() {
        return "pulsar-raw";
    }

    @Override
    public boolean canTransmitDirectly(String targetProtocolType) {
        return "pulsar-raw".equals(targetProtocolType) || "pulsar".equals(targetProtocolType);
    }

    @Override
    public RawPulsarMessage transmitDirectly(RawPulsarMessage protocol) throws ProtocolHandleException {
        // For raw Pulsar to raw Pulsar, directly return the original message
        // This avoids unnecessary CloudEvent conversion for better performance
        return protocol;
    }

    @Override
    public void onLoad(PluginInfo pluginInfo) throws Exception {
        log.info("Raw Pulsar protocol adapter loaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public void onUnload(PluginInfo pluginInfo) throws Exception {
        log.info("Raw Pulsar protocol adapter unloaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public void onReload(PluginInfo pluginInfo) throws Exception {
        log.info("Raw Pulsar protocol adapter reloaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Raw Pulsar protocol adapter for raw Pulsar client communication";
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }
} 