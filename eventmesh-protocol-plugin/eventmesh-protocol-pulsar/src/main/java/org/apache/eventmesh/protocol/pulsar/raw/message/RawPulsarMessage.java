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

package org.apache.eventmesh.protocol.pulsar.raw.message;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * Raw Pulsar message wrapper implementing ProtocolTransportObject.
 * Contains complete Pulsar message metadata including sequenceId, publishTime, properties, etc.
 */
@Data
public class RawPulsarMessage implements ProtocolTransportObject {
    
    private String topic;
    private String messageId;
    private byte[] data;
    private Map<String, String> properties = new HashMap<>();
    
    // Pulsar-specific metadata
    private Long sequenceId;
    private Long publishTime;
    private Long eventTime;
    private String key;
    private byte[] orderingKey;
    private String producerName;
    private String schemaVersion;
    private Boolean isReplicated;
    private String replicatedFrom;
    private Integer redeliveryCount;
    private Long ledgerId;
    private Long entryId;
    private Integer partitionIndex;
    private Boolean isChunked;
    private String chunkMessageId;
    private Integer totalChunkMsgSize;
    private Integer chunkId;
    
    public void addProperty(String key, String value) {
        this.properties.put(key, value);
    }
    
    public String getProperty(String key) {
        return this.properties.get(key);
    }
    
    public void removeProperty(String key) {
        this.properties.remove(key);
    }
    
    public boolean hasProperty(String key) {
        return this.properties.containsKey(key);
    }
    
    /**
     * Get all properties as an unmodifiable map
     */
    public Map<String, String> getProperties() {
        return new HashMap<>(properties);
    }
    
    /**
     * Set properties from a map
     */
    public void setProperties(Map<String, String> properties) {
        this.properties.clear();
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }
    
    /**
     * Check if this is a replicated message
     */
    public boolean isReplicatedMessage() {
        return Boolean.TRUE.equals(isReplicated);
    }
    
    /**
     * Check if this is a chunked message
     */
    public boolean isChunkedMessage() {
        return Boolean.TRUE.equals(isChunked);
    }
    
    /**
     * Get the message size in bytes
     */
    public int getMessageSize() {
        int size = 0;
        if (data != null) {
            size += data.length;
        }
        if (key != null) {
            size += key.getBytes().length;
        }
        if (orderingKey != null) {
            size += orderingKey.length;
        }
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
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