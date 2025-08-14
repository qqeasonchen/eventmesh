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

package org.apache.eventmesh.protocol.rocketmq.raw;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.rocketmq.raw.message.RawRocketMQMessage;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.protocol.api.PluginLifecycle.PluginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.SpecVersion;

/**
 * Raw RocketMQ protocol adapter for raw RocketMQ client communication.
 * Supports raw RocketMQ message format without CloudEvent conversion.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class RawRocketMQProtocolAdapter implements ProtocolAdaptor<RawRocketMQMessage> {

    private static final Logger log = LoggerFactory.getLogger(RawRocketMQProtocolAdapter.class);

    @Override
    public CloudEvent toCloudEvent(RawRocketMQMessage rawMessage) throws ProtocolHandleException {
        try {
            CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(SpecVersion.V1)
                .withId(rawMessage.getMsgId() != null ? rawMessage.getMsgId() : 
                    rawMessage.getTopic() + "_" + System.currentTimeMillis())
                .withSource(URI.create("rocketmq://" + rawMessage.getTopic()))
                .withType("rocketmq.raw.message")
                .withSubject(rawMessage.getTopic());

            // Add raw RocketMQ properties as extensions
            if (rawMessage.getProperties() != null) {
                rawMessage.getProperties().forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.withExtension("rocketmq.property." + key, value);
                    }
                });
            }

            // Add RocketMQ-specific metadata
            if (rawMessage.getQueueId() != null) {
                builder.withExtension("rocketmq.queueId", rawMessage.getQueueId().toString());
            }
            if (rawMessage.getQueueOffset() != null) {
                builder.withExtension("rocketmq.queueOffset", rawMessage.getQueueOffset().toString());
            }
            if (rawMessage.getBornTimestamp() != null) {
                builder.withExtension("rocketmq.bornTimestamp", rawMessage.getBornTimestamp().toString());
            }
            if (rawMessage.getStoreTimestamp() != null) {
                builder.withExtension("rocketmq.storeTimestamp", rawMessage.getStoreTimestamp().toString());
            }
            if (rawMessage.getKeys() != null) {
                builder.withExtension("rocketmq.keys", rawMessage.getKeys());
            }
            if (rawMessage.getTags() != null) {
                builder.withExtension("rocketmq.tags", rawMessage.getTags());
            }
            if (rawMessage.getFlag() != null) {
                builder.withExtension("rocketmq.flag", rawMessage.getFlag().toString());
            }
            if (rawMessage.getDelayTimeLevel() != null) {
                builder.withExtension("rocketmq.delayTimeLevel", rawMessage.getDelayTimeLevel().toString());
            }

            // Set data
            if (rawMessage.getBody() != null) {
                builder.withData(rawMessage.getBody());
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert raw RocketMQ message to CloudEvent", e);
        }
    }

    @Override
    public java.util.List<io.cloudevents.CloudEvent> toBatchCloudEvent(java.util.List<RawRocketMQMessage> protocolList) throws ProtocolHandleException {
        java.util.List<io.cloudevents.CloudEvent> result = new java.util.ArrayList<>();
        if (protocolList != null) {
            for (RawRocketMQMessage protocol : protocolList) {
                result.add(toCloudEvent(protocol));
            }
        }
        return result;
    }

    @Override
    public RawRocketMQMessage fromCloudEvent(io.cloudevents.CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            RawRocketMQMessage rawMessage = new RawRocketMQMessage();
            rawMessage.setTopic(cloudEvent.getSubject());
            rawMessage.setMsgId(cloudEvent.getId());
            
            if (cloudEvent.getData() != null) {
                rawMessage.setBody(cloudEvent.getData().toBytes());
            }

            // Convert CloudEvent extensions back to RocketMQ properties
            if (cloudEvent.getExtensionNames() != null) {
                cloudEvent.getExtensionNames().forEach(extensionName -> {
                    Object value = cloudEvent.getExtension(extensionName);
                    if (value != null) {
                        if (extensionName.startsWith("rocketmq.property.")) {
                            // Extract original property name
                            String propertyName = extensionName.substring("rocketmq.property.".length());
                            rawMessage.addProperty(propertyName, value.toString());
                        } else if (extensionName.equals("rocketmq.queueId")) {
                            rawMessage.setQueueId(Integer.parseInt(value.toString()));
                        } else if (extensionName.equals("rocketmq.queueOffset")) {
                            rawMessage.setQueueOffset(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("rocketmq.bornTimestamp")) {
                            rawMessage.setBornTimestamp(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("rocketmq.storeTimestamp")) {
                            rawMessage.setStoreTimestamp(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("rocketmq.keys")) {
                            rawMessage.setKeys(value.toString());
                        } else if (extensionName.equals("rocketmq.tags")) {
                            rawMessage.setTags(value.toString());
                        } else if (extensionName.equals("rocketmq.flag")) {
                            rawMessage.setFlag(Integer.parseInt(value.toString()));
                        } else if (extensionName.equals("rocketmq.delayTimeLevel")) {
                            rawMessage.setDelayTimeLevel(Integer.parseInt(value.toString()));
                        } else {
                            // Add other extensions as properties
                            rawMessage.addProperty(extensionName, value.toString());
                        }
                    }
                });
            }

            return rawMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to raw RocketMQ message", e);
        }
    }

    @Override
    public String getProtocolType() {
        return "rocketmq-raw";
    }

    @Override
    public boolean canTransmitDirectly(String targetProtocolType) {
        return "rocketmq-raw".equals(targetProtocolType) || "rocketmq".equals(targetProtocolType);
    }

    @Override
    public RawRocketMQMessage transmitDirectly(RawRocketMQMessage protocol) throws ProtocolHandleException {
        // For raw RocketMQ to raw RocketMQ, directly return the original message
        // This avoids unnecessary CloudEvent conversion for better performance
        return protocol;
    }

    @Override
    public void onLoad(PluginInfo pluginInfo) throws Exception {
        log.info("Raw RocketMQ protocol adapter loaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public void onUnload(PluginInfo pluginInfo) throws Exception {
        log.info("Raw RocketMQ protocol adapter unloaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public void onReload(PluginInfo pluginInfo) throws Exception {
        log.info("Raw RocketMQ protocol adapter reloaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Raw RocketMQ protocol adapter for raw RocketMQ client communication";
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }
} 