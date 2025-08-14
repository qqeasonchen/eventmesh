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

package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public interface ProtocolAdaptor<T extends ProtocolTransportObject> extends PluginLifecycle {
    /**
     * 获取协议类型（如 kafka-raw、pulsar-raw、rocketmq-raw 等）
     */
    String getProtocolType();

    /**
     * 判断是否可以直通传递到目标协议
     */
    default boolean canTransmitDirectly(String targetProtocolType) {
        return getProtocolType().equals(targetProtocolType);
    }

    /**
     * 直通传递协议对象（无需 CloudEvent 转换）
     */
    default T transmitDirectly(T protocol) throws ProtocolHandleException {
        return protocol;
    }

    /**
     * 协议对象转为 CloudEvent
     */
    io.cloudevents.CloudEvent toCloudEvent(T protocol) throws ProtocolHandleException;

    /**
     * CloudEvent 转为协议对象
     */
    T fromCloudEvent(io.cloudevents.CloudEvent cloudEvent) throws ProtocolHandleException;

    /**
     * 批量协议对象转为 CloudEvent
     */
    java.util.List<io.cloudevents.CloudEvent> toBatchCloudEvent(java.util.List<T> protocolList) throws ProtocolHandleException;
}
 
 
 
 
 
 