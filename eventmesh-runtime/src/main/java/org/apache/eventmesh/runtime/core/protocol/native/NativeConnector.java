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

/**
 * Native connector interface for direct client communication.
 * Allows native clients (Kafka, Pulsar, RocketMQ) to connect to EventMesh
 * without protocol conversion when possible.
 */
public interface NativeConnector {

    /**
     * Start the native connector
     */
    void start() throws Exception;

    /**
     * Shutdown the native connector
     */
    void shutdown() throws Exception;

    /**
     * Get connector type
     */
    String getConnectorType();

    /**
     * Check if connector is running
     */
    boolean isRunning();

    /**
     * Get connector statistics
     */
    NativeConnectorStats getStats();

    /**
     * Native connector statistics
     */
    interface NativeConnectorStats {
        /**
         * Get number of pending messages
         */
        int getPendingMessageCount();

        /**
         * Check if connector is shutdown
         */
        boolean isShutdown();

        /**
         * Get connector type
         */
        String getConnectorType();
    }
} 