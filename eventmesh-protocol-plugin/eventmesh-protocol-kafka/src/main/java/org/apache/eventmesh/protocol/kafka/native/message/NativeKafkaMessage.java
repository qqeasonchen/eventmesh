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

package org.apache.eventmesh.protocol.kafka.native.message;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * Native Kafka message wrapper implementing ProtocolTransportObject.
 * Contains complete Kafka message metadata including partition, offset, timestamp, etc.
 */
@Data
public class NativeKafkaMessage implements ProtocolTransportObject {
    
    private String topic;
    private String key;
    private byte[] value;
    private Map<String, String> headers = new HashMap<>();
    
    // Kafka-specific metadata
    private Integer partition;
    private Long offset;
    private Long timestamp;
    private String messageId;
    private Integer leaderEpoch;
    private Long checksum;
    private Integer serializedKeySize;
    private Integer serializedValueSize;
    
    // Producer metadata
    private String producerId;
    private Integer producerEpoch;
    private Integer sequence;
    private Boolean isTransactional;
    private Boolean isControl;
    
    // Consumer metadata
    private String consumerGroup;
    private Long consumerOffset;
    private Long consumerTimestamp;
    
    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }
    
    public String getHeader(String key) {
        return this.headers.get(key);
    }
    
    public void removeHeader(String key) {
        this.headers.remove(key);
    }
    
    public boolean hasHeader(String key) {
        return this.headers.containsKey(key);
    }
    
    /**
     * Get all headers as an unmodifiable map
     */
    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }
    
    /**
     * Set headers from a map
     */
    public void setHeaders(Map<String, String> headers) {
        this.headers.clear();
        if (headers != null) {
            this.headers.putAll(headers);
        }
    }
    
    /**
     * Check if this is a control message
     */
    public boolean isControlMessage() {
        return Boolean.TRUE.equals(isControl);
    }
    
    /**
     * Check if this is a transactional message
     */
    public boolean isTransactionalMessage() {
        return Boolean.TRUE.equals(isTransactional);
    }
    
    /**
     * Get the message size in bytes
     */
    public int getMessageSize() {
        int size = 0;
        if (key != null) {
            size += key.getBytes().length;
        }
        if (value != null) {
            size += value.length;
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null) {
                    size += entry.getKey().getBytes().length;
                }
                if (entry.getValue() != null) {
                    size += entry.getValue().getBytes().length;
                }
            }
        }
        return size;
    }
} 