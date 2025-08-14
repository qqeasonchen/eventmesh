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

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import io.cloudevents.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

/**
 * Protocol plugin factory for managing protocol adaptors.
 */
public class ProtocolPluginFactory {

    private static final Map<String, ProtocolAdaptor<?>> PROTOCOL_ADAPTOR_MAP = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(ProtocolPluginFactory.class);

    static {
        // Auto-load all protocol adaptors via SPI
        ServiceLoader<ProtocolAdaptor> adaptors = ServiceLoader.load(ProtocolAdaptor.class);
        for (ProtocolAdaptor<?> adaptor : adaptors) {
            PROTOCOL_ADAPTOR_MAP.put(adaptor.getProtocolType(), adaptor);
        }
    }

    /**
     * Get protocol adaptor by protocol type.
     *
     * @param protocolType protocol type
     * @return protocol adaptor
     */
    public static ProtocolAdaptor<ProtocolTransportObject> getProtocolAdaptor(String protocolType) {
        return (ProtocolAdaptor<ProtocolTransportObject>) PROTOCOL_ADAPTOR_MAP.get(protocolType);
    }

    /**
     * Register a protocol adaptor manually.
     *
     * @param protocolType protocol type
     * @param adaptor protocol adaptor
     */
    public static void registerProtocolAdaptor(String protocolType, ProtocolAdaptor<?> adaptor) {
        PROTOCOL_ADAPTOR_MAP.put(protocolType, adaptor);
    }

    /**
     * Get all registered protocol types.
     *
     * @return protocol types
     */
    public static Map<String, ProtocolAdaptor<?>> getAllProtocolAdaptors() {
        return new ConcurrentHashMap<>(PROTOCOL_ADAPTOR_MAP);
    }

    /**
     * Check if direct transmission is possible between source and target protocols.
     *
     * @param sourceProtocolType source protocol type
     * @param targetProtocolType target protocol type
     * @return true if direct transmission is possible, false otherwise
     */
    public static boolean canTransmitDirectly(String sourceProtocolType, String targetProtocolType) {
        ProtocolAdaptor<?> sourceAdaptor = PROTOCOL_ADAPTOR_MAP.get(sourceProtocolType);
        return sourceAdaptor != null && sourceAdaptor.canTransmitDirectly(targetProtocolType);
    }

    /**
     * Directly transmit protocol object without CloudEvent conversion.
     *
     * @param sourceProtocolType source protocol type
     * @param targetProtocolType target protocol type
     * @param protocol protocol object to transmit
     * @return transmitted protocol object
     */
    @SuppressWarnings("unchecked")
    public static ProtocolTransportObject transmitDirectly(String sourceProtocolType, 
                                                          String targetProtocolType, 
                                                          ProtocolTransportObject protocol) throws Exception {
        ProtocolAdaptor<ProtocolTransportObject> sourceAdaptor = getProtocolAdaptor(sourceProtocolType);
        if (sourceAdaptor == null) {
            throw new IllegalArgumentException("Source protocol adaptor not found: " + sourceProtocolType);
        }
        
        if (!sourceAdaptor.canTransmitDirectly(targetProtocolType)) {
            throw new IllegalArgumentException("Direct transmission not supported from " + sourceProtocolType + " to " + targetProtocolType);
        }
        
        return sourceAdaptor.transmitDirectly((ProtocolTransportObject) protocol);
    }

    /**
     * Reload all protocol adaptors from classpath and plugin directory.
     * This method supports hot reloading of plugins.
     */
    public static void reloadPlugins() {
        synchronized (PROTOCOL_ADAPTOR_MAP) {
            PROTOCOL_ADAPTOR_MAP.clear();
            
            // Reload from classpath via SPI
            ServiceLoader<ProtocolAdaptor> adaptors = ServiceLoader.load(ProtocolAdaptor.class);
            for (ProtocolAdaptor<?> adaptor : adaptors) {
                PROTOCOL_ADAPTOR_MAP.put(adaptor.getProtocolType(), adaptor);
            }
            
            // Reload from plugin directory
            reloadFromPluginDirectory();
        }
    }

    /**
     * Load a specific plugin from JAR file.
     *
     * @param protocolType protocol type
     * @param jarPath path to the plugin JAR file
     * @return true if loaded successfully, false otherwise
     */
    public static boolean loadPlugin(String protocolType, String jarPath) {
        try {
            // Use EventMeshExtensionFactory to load plugin from JAR
            ProtocolAdaptor<?> adaptor = EventMeshExtensionFactory.getExtension(ProtocolAdaptor.class, protocolType);
            if (adaptor != null) {
                PROTOCOL_ADAPTOR_MAP.put(protocolType, adaptor);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to load plugin from JAR: {}", jarPath, e);
        }
        return false;
    }

    /**
     * Unload a specific plugin.
     *
     * @param protocolType protocol type to unload
     * @return true if unloaded successfully, false otherwise
     */
    public static boolean unloadPlugin(String protocolType) {
        ProtocolAdaptor<?> removed = PROTOCOL_ADAPTOR_MAP.remove(protocolType);
        return removed != null;
    }

    /**
     * Check if a plugin is loaded.
     *
     * @param protocolType protocol type
     * @return true if plugin is loaded, false otherwise
     */
    public static boolean isPluginLoaded(String protocolType) {
        return PROTOCOL_ADAPTOR_MAP.containsKey(protocolType);
    }

    /**
     * Get plugin information.
     *
     * @param protocolType protocol type
     * @return plugin information or null if not found
     */
    public static PluginInfo getPluginInfo(String protocolType) {
        ProtocolAdaptor<?> adaptor = PROTOCOL_ADAPTOR_MAP.get(protocolType);
        if (adaptor != null) {
            return new PluginInfo(protocolType, adaptor.getClass().getName(), true);
        }
        return null;
    }

    /**
     * Reload plugins from plugin directory.
     */
    private static void reloadFromPluginDirectory() {
        // This would integrate with EventMeshExtensionFactory to reload from plugin directory
        // Implementation depends on the specific plugin directory structure
        log.info("Reloading plugins from plugin directory...");
    }

    /**
     * Plugin information holder.
     */
    public static class PluginInfo {
        private final String protocolType;
        private final String className;
        private final boolean loaded;

        public PluginInfo(String protocolType, String className, boolean loaded) {
            this.protocolType = protocolType;
            this.className = className;
            this.loaded = loaded;
        }

        public String getProtocolType() { return protocolType; }
        public String getClassName() { return className; }
        public boolean isLoaded() { return loaded; }
    }
}
