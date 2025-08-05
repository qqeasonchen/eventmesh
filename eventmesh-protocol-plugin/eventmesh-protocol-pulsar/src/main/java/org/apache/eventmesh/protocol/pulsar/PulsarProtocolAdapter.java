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

package org.apache.eventmesh.protocol.pulsar;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.pulsar.message.PulsarMessage;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.net.URI;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Pulsar protocol adapter, converts between Pulsar messages and CloudEvents.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class PulsarProtocolAdapter implements ProtocolAdaptor<PulsarMessage> {

    @Override
    public CloudEvent toCloudEvent(PulsarMessage pulsarMessage) throws ProtocolHandleException {
        try {
            CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(io.cloudevents.SpecVersion.V1_0)
                .withId(pulsarMessage.getMessageId() != null ? pulsarMessage.getMessageId() : pulsarMessage.getTopicName() + "_" + System.currentTimeMillis())
                .withSource(URI.create("pulsar://" + pulsarMessage.getTopicName()))
                .withType("pulsar.message")
                .withSubject(pulsarMessage.getTopicName());

            // Add properties as extensions
            if (pulsarMessage.getProperties() != null) {
                pulsarMessage.getProperties().forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.withExtension(key, value);
                    }
                });
            }

            // Set data
            if (pulsarMessage.getData() != null) {
                builder.withData(pulsarMessage.getData());
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert Pulsar message to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(PulsarMessage pulsarMessage) throws ProtocolHandleException {
        // For single message, return as list with one element
        return List.of(toCloudEvent(pulsarMessage));
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            PulsarMessage pulsarMessage = new PulsarMessage();
            pulsarMessage.setTopicName(cloudEvent.getSubject());
            pulsarMessage.setMessageId(cloudEvent.getId());
            
            if (cloudEvent.getData() != null) {
                pulsarMessage.setData(cloudEvent.getData().toBytes());
            }

            // Convert extensions to properties
            if (cloudEvent.getExtensionNames() != null) {
                cloudEvent.getExtensionNames().forEach(extensionName -> {
                    Object value = cloudEvent.getExtension(extensionName);
                    if (value != null) {
                        pulsarMessage.addProperty(extensionName, value.toString());
                    }
                });
            }

            return pulsarMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to Pulsar message", e);
        }
    }

    @Override
    public String getProtocolType() {
        return "pulsar";
    }

    @Override
    public boolean canTransmitDirectly(String targetProtocolType) {
        return "pulsar".equals(targetProtocolType);
    }

    @Override
    public PulsarMessage transmitDirectly(PulsarMessage protocol) throws ProtocolHandleException {
        // For Pulsar to Pulsar, directly return the original message
        // This avoids unnecessary CloudEvent conversion for better performance
        return protocol;
    }
} 