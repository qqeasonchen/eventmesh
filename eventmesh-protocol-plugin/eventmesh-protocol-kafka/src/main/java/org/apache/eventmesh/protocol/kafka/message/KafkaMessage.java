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

package org.apache.eventmesh.protocol.kafka.message;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * Kafka message wrapper implementing ProtocolTransportObject.
 */
@Data
public class KafkaMessage implements ProtocolTransportObject {
    
    private String topic;
    private String key;
    private byte[] value;
    private Map<String, String> headers = new HashMap<>();
    
    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }
    
    public String getHeader(String key) {
        return this.headers.get(key);
    }
} 