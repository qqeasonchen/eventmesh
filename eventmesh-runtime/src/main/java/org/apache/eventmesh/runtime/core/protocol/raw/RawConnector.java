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

/**
 * Interface defining common operations for raw client connectors.
 * Raw connectors handle direct protocol messages without CloudEvent conversion.
 */
public interface RawConnector {
    
    /**
     * Start the connector
     */
    void start() throws Exception;
    
    /**
     * Shutdown the connector
     */
    void shutdown() throws Exception;
    
    /**
     * Get the connector type (e.g., "kafka", "pulsar", "rocketmq")
     */
    String getConnectorType();
    
    /**
     * Check if the connector is running
     */
    boolean isRunning();
    
    /**
     * Get connector statistics
     */
    RawConnectorStats getStats();
    
    /**
     * Statistics interface for raw connectors
     */
    interface RawConnectorStats {
        /**
         * Get the number of pending messages
         */
        int getPendingMessageCount();
        
        /**
         * Check if the connector is shutdown
         */
        boolean isShutdown();
        
        /**
         * Get the connector type
         */
        String getConnectorType();
    }
} 