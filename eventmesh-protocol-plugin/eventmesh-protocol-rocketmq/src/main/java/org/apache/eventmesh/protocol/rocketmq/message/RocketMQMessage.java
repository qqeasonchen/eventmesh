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

package org.apache.eventmesh.protocol.rocketmq.message;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * RocketMQ message wrapper implementing ProtocolTransportObject.
 */
@Data
public class RocketMQMessage implements ProtocolTransportObject {
    
    private String topic;
    private String msgId;
    private byte[] body;
    private Map<String, String> properties = new HashMap<>();
    
    public void addProperty(String key, String value) {
        this.properties.put(key, value);
    }
    
    public String getProperty(String key) {
        return this.properties.get(key);
    }
} 