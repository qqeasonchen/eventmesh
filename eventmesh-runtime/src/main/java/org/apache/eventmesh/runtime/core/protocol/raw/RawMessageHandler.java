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

package org.apache.eventmesh.runtime.core.protocol.raw;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import io.cloudevents.CloudEvent;

/**
 * Interface for handling raw protocol messages.
 * Supports both direct raw message handling and CloudEvent conversion.
 */
public interface RawMessageHandler {
    
    /**
     * Handle a raw protocol message directly
     */
    void handleMessage(ProtocolTransportObject message);
    
    /**
     * Handle a CloudEvent
     */
    void handleCloudEvent(CloudEvent cloudEvent);
    
    /**
     * Check if this handler supports direct raw message handling
     */
    default boolean supportsDirectHandling() {
        return true;
    }
    
    /**
     * Check if this handler supports CloudEvent handling
     */
    default boolean supportsCloudEventHandling() {
        return true;
    }
} 