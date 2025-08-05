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

/**
 * Plugin lifecycle interface for managing plugin loading, unloading, and reloading.
 * Protocol adaptors can implement this interface to handle lifecycle events.
 */
public interface PluginLifecycle {

    /**
     * Called when the plugin is being loaded.
     * This method is called before the plugin becomes available for use.
     *
     * @param pluginInfo plugin information
     * @throws Exception if plugin initialization fails
     */
    default void onLoad(PluginInfo pluginInfo) throws Exception {
        // Default implementation does nothing
    }

    /**
     * Called when the plugin is being unloaded.
     * This method is called before the plugin is removed from the system.
     *
     * @param pluginInfo plugin information
     * @throws Exception if plugin cleanup fails
     */
    default void onUnload(PluginInfo pluginInfo) throws Exception {
        // Default implementation does nothing
    }

    /**
     * Called when the plugin is being reloaded.
     * This method is called when the plugin is being updated or refreshed.
     *
     * @param pluginInfo plugin information
     * @throws Exception if plugin reload fails
     */
    default void onReload(PluginInfo pluginInfo) throws Exception {
        // Default implementation does nothing
    }

    /**
     * Get plugin version information.
     *
     * @return plugin version string
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * Get plugin description.
     *
     * @return plugin description
     */
    default String getDescription() {
        return "Protocol adapter plugin";
    }

    /**
     * Check if plugin supports hot reloading.
     *
     * @return true if plugin supports hot reloading, false otherwise
     */
    default boolean supportsHotReload() {
        return false;
    }

    /**
     * Plugin information for lifecycle events.
     */
    class PluginInfo {
        private final String protocolType;
        private final String className;
        private final String version;
        private final boolean loaded;

        public PluginInfo(String protocolType, String className, String version, boolean loaded) {
            this.protocolType = protocolType;
            this.className = className;
            this.version = version;
            this.loaded = loaded;
        }

        public String getProtocolType() { return protocolType; }
        public String getClassName() { return className; }
        public String getVersion() { return version; }
        public boolean isLoaded() { return loaded; }
    }
} 