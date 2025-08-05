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

package org.apache.eventmesh.protocol.rocketmq.native.message;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * Native RocketMQ message wrapper implementing ProtocolTransportObject.
 * Contains complete RocketMQ message metadata including queueId, queueOffset, bornTimestamp, etc.
 */
@Data
public class NativeRocketMQMessage implements ProtocolTransportObject {
    
    private String topic;
    private String msgId;
    private byte[] body;
    private Map<String, String> properties = new HashMap<>();
    
    // RocketMQ-specific metadata
    private Integer queueId;
    private Long queueOffset;
    private Long bornTimestamp;
    private Long storeTimestamp;
    private String keys;
    private String tags;
    private Integer flag;
    private Integer delayTimeLevel;
    private String bornHost;
    private String storeHost;
    private String producerGroup;
    private Integer reconsumeTimes;
    private Boolean preparedTransactionOffset;
    private String transactionId;
    private String msgBodyCrc;
    private Integer bodyLength;
    private Integer bodyCompressed;
    private String cluster;
    private String brokerName;
    private Integer sysFlag;
    private Long commitLogOffset;
    private Integer bodyCrc;
    private Integer bornHostV6Flag;
    private Integer storeHostV6Flag;
    private Integer reconsumeTimesV3;
    private Long preparedTransactionOffsetV3;
    
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
     * Check if this is a delay message
     */
    public boolean isDelayMessage() {
        return delayTimeLevel != null && delayTimeLevel > 0;
    }
    
    /**
     * Check if this is a transaction message
     */
    public boolean isTransactionMessage() {
        return transactionId != null && !transactionId.isEmpty();
    }
    
    /**
     * Check if this is a prepared transaction message
     */
    public boolean isPreparedTransactionMessage() {
        return Boolean.TRUE.equals(preparedTransactionOffset);
    }
    
    /**
     * Get the message size in bytes
     */
    public int getMessageSize() {
        int size = 0;
        if (body != null) {
            size += body.length;
        }
        if (keys != null) {
            size += keys.getBytes().length;
        }
        if (tags != null) {
            size += tags.getBytes().length;
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