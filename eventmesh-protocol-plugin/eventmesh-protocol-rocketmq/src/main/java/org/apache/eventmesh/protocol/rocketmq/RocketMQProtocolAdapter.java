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

package org.apache.eventmesh.protocol.rocketmq;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.rocketmq.message.RocketMQMessage;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.net.URI;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * RocketMQ protocol adapter, converts between RocketMQ messages and CloudEvents.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class RocketMQProtocolAdapter implements ProtocolAdaptor<RocketMQMessage> {

    @Override
    public CloudEvent toCloudEvent(RocketMQMessage rocketMQMessage) throws ProtocolHandleException {
        try {
            CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
                .withId(rocketMQMessage.getMsgId() != null ? rocketMQMessage.getMsgId() : rocketMQMessage.getTopic() + "_" + System.currentTimeMillis())
                .withSource(URI.create("rocketmq://" + rocketMQMessage.getTopic()))
                .withType("rocketmq.message")
                .withSubject(rocketMQMessage.getTopic());

            // Add properties as extensions
            if (rocketMQMessage.getProperties() != null) {
                rocketMQMessage.getProperties().forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.withExtension(key, value);
                    }
                });
            }

            // Set data
            if (rocketMQMessage.getBody() != null) {
                builder.withData(rocketMQMessage.getBody());
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert RocketMQ message to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(RocketMQMessage rocketMQMessage) throws ProtocolHandleException {
        // For single message, return as list with one element
        return List.of(toCloudEvent(rocketMQMessage));
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            RocketMQMessage rocketMQMessage = new RocketMQMessage();
            rocketMQMessage.setTopic(cloudEvent.getSubject());
            rocketMQMessage.setMsgId(cloudEvent.getId());
            
            if (cloudEvent.getData() != null) {
                rocketMQMessage.setBody(cloudEvent.getData().toBytes());
            }

            // Convert extensions to properties
            if (cloudEvent.getExtensionNames() != null) {
                cloudEvent.getExtensionNames().forEach(extensionName -> {
                    Object value = cloudEvent.getExtension(extensionName);
                    if (value != null) {
                        rocketMQMessage.addProperty(extensionName, value.toString());
                    }
                });
            }

            return rocketMQMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to RocketMQ message", e);
        }
    }

    @Override
    public String getProtocolType() {
        return "rocketmq";
    }

    @Override
    public boolean canTransmitDirectly(String targetProtocolType) {
        return "rocketmq".equals(targetProtocolType);
    }

    @Override
    public RocketMQMessage transmitDirectly(RocketMQMessage protocol) throws ProtocolHandleException {
        // For RocketMQ to RocketMQ, directly return the original message
        // This avoids unnecessary CloudEvent conversion for better performance
        return protocol;
    }
} 