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

package org.apache.eventmesh.runtime.core.protocol.native;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import io.cloudevents.CloudEvent;

/**
 * Native message handler interface for processing native protocol messages.
 * Supports both direct native message handling and CloudEvent conversion.
 */
public interface NativeMessageHandler {

    /**
     * Handle native protocol message directly
     *
     * @param message native protocol message
     */
    void handleMessage(ProtocolTransportObject message);

    /**
     * Handle CloudEvent (converted from native message)
     *
     * @param cloudEvent CloudEvent
     */
    void handleCloudEvent(CloudEvent cloudEvent);

    /**
     * Check if direct message handling is supported
     *
     * @return true if direct handling is supported
     */
    default boolean supportsDirectHandling() {
        return true;
    }

    /**
     * Check if CloudEvent handling is supported
     *
     * @return true if CloudEvent handling is supported
     */
    default boolean supportsCloudEventHandling() {
        return true;
    }
} 