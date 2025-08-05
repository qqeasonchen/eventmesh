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

package org.apache.eventmesh.protocol.rocketmq.native;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.rocketmq.native.message.NativeRocketMQMessage;
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
 * Native RocketMQ protocol adapter for direct RocketMQ client communication.
 * Supports native RocketMQ message format without CloudEvent conversion.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class NativeRocketMQProtocolAdapter implements ProtocolAdaptor<NativeRocketMQMessage> {

    private static final Logger log = LoggerFactory.getLogger(NativeRocketMQProtocolAdapter.class);

    @Override
    public CloudEvent toCloudEvent(NativeRocketMQMessage nativeMessage) throws ProtocolHandleException {
        try {
            CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
                .withId(nativeMessage.getMsgId() != null ? nativeMessage.getMsgId() : 
                    nativeMessage.getTopic() + "_" + System.currentTimeMillis())
                .withSource(URI.create("rocketmq://" + nativeMessage.getTopic()))
                .withType("rocketmq.native.message")
                .withSubject(nativeMessage.getTopic());

            // Add native RocketMQ properties as extensions
            if (nativeMessage.getProperties() != null) {
                nativeMessage.getProperties().forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.withExtension("rocketmq.property." + key, value);
                    }
                });
            }

            // Add RocketMQ-specific metadata
            if (nativeMessage.getQueueId() != null) {
                builder.withExtension("rocketmq.queueId", nativeMessage.getQueueId().toString());
            }
            if (nativeMessage.getQueueOffset() != null) {
                builder.withExtension("rocketmq.queueOffset", nativeMessage.getQueueOffset().toString());
            }
            if (nativeMessage.getBornTimestamp() != null) {
                builder.withExtension("rocketmq.bornTimestamp", nativeMessage.getBornTimestamp().toString());
            }
            if (nativeMessage.getStoreTimestamp() != null) {
                builder.withExtension("rocketmq.storeTimestamp", nativeMessage.getStoreTimestamp().toString());
            }
            if (nativeMessage.getKeys() != null) {
                builder.withExtension("rocketmq.keys", nativeMessage.getKeys());
            }
            if (nativeMessage.getTags() != null) {
                builder.withExtension("rocketmq.tags", nativeMessage.getTags());
            }
            if (nativeMessage.getFlag() != null) {
                builder.withExtension("rocketmq.flag", nativeMessage.getFlag().toString());
            }
            if (nativeMessage.getDelayTimeLevel() != null) {
                builder.withExtension("rocketmq.delayTimeLevel", nativeMessage.getDelayTimeLevel().toString());
            }

            // Set data
            if (nativeMessage.getBody() != null) {
                builder.withData(nativeMessage.getBody());
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert native RocketMQ message to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(NativeRocketMQMessage nativeMessage) throws ProtocolHandleException {
        // For single message, return as list with one element
        return List.of(toCloudEvent(nativeMessage));
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            NativeRocketMQMessage nativeMessage = new NativeRocketMQMessage();
            nativeMessage.setTopic(cloudEvent.getSubject());
            nativeMessage.setMsgId(cloudEvent.getId());
            
            if (cloudEvent.getData() != null) {
                nativeMessage.setBody(cloudEvent.getData().toBytes());
            }

            // Convert CloudEvent extensions back to RocketMQ properties
            if (cloudEvent.getExtensionNames() != null) {
                cloudEvent.getExtensionNames().forEach(extensionName -> {
                    Object value = cloudEvent.getExtension(extensionName);
                    if (value != null) {
                        if (extensionName.startsWith("rocketmq.property.")) {
                            // Extract original property name
                            String propertyName = extensionName.substring("rocketmq.property.".length());
                            nativeMessage.addProperty(propertyName, value.toString());
                        } else if (extensionName.equals("rocketmq.queueId")) {
                            nativeMessage.setQueueId(Integer.parseInt(value.toString()));
                        } else if (extensionName.equals("rocketmq.queueOffset")) {
                            nativeMessage.setQueueOffset(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("rocketmq.bornTimestamp")) {
                            nativeMessage.setBornTimestamp(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("rocketmq.storeTimestamp")) {
                            nativeMessage.setStoreTimestamp(Long.parseLong(value.toString()));
                        } else if (extensionName.equals("rocketmq.keys")) {
                            nativeMessage.setKeys(value.toString());
                        } else if (extensionName.equals("rocketmq.tags")) {
                            nativeMessage.setTags(value.toString());
                        } else if (extensionName.equals("rocketmq.flag")) {
                            nativeMessage.setFlag(Integer.parseInt(value.toString()));
                        } else if (extensionName.equals("rocketmq.delayTimeLevel")) {
                            nativeMessage.setDelayTimeLevel(Integer.parseInt(value.toString()));
                        } else {
                            // Add other extensions as properties
                            nativeMessage.addProperty(extensionName, value.toString());
                        }
                    }
                });
            }

            return nativeMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to native RocketMQ message", e);
        }
    }

    @Override
    public String getProtocolType() {
        return "rocketmq-native";
    }

    @Override
    public boolean canTransmitDirectly(String targetProtocolType) {
        return "rocketmq-native".equals(targetProtocolType) || "rocketmq".equals(targetProtocolType);
    }

    @Override
    public NativeRocketMQMessage transmitDirectly(NativeRocketMQMessage protocol) throws ProtocolHandleException {
        // For native RocketMQ to native RocketMQ, directly return the original message
        // This avoids unnecessary CloudEvent conversion for better performance
        return protocol;
    }

    @Override
    public void onLoad(PluginInfo pluginInfo) throws Exception {
        log.info("Native RocketMQ protocol adapter loaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public void onUnload(PluginInfo pluginInfo) throws Exception {
        log.info("Native RocketMQ protocol adapter unloaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public void onReload(PluginInfo pluginInfo) throws Exception {
        log.info("Native RocketMQ protocol adapter reloaded: {}", pluginInfo.getProtocolType());
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Native RocketMQ protocol adapter for direct RocketMQ client communication";
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }
} 