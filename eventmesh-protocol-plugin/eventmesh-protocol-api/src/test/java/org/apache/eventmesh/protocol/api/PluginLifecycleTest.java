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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for PluginLifecycle interface
 */
@ExtendWith(MockitoExtension.class)
public class PluginLifecycleTest {

    @Mock
    private PluginLifecycle.PluginInfo mockPluginInfo;

    @Test
    public void testPluginInfoConstructor() {
        PluginLifecycle.PluginInfo info = new PluginLifecycle.PluginInfo("test-protocol", "TestClass", "1.0.0", true);
        
        assertEquals("test-protocol", info.getProtocolType());
        assertEquals("TestClass", info.getClassName());
        assertEquals("1.0.0", info.getVersion());
        assertTrue(info.isLoaded());
    }

    @Test
    public void testPluginInfoGetters() {
        PluginLifecycle.PluginInfo info = new PluginLifecycle.PluginInfo("kafka", "KafkaProtocolAdapter", "2.1.0", false);
        
        assertEquals("kafka", info.getProtocolType());
        assertEquals("KafkaProtocolAdapter", info.getClassName());
        assertEquals("2.1.0", info.getVersion());
        assertFalse(info.isLoaded());
    }

    @Test
    public void testDefaultPluginLifecycleMethods() throws Exception {
        // Create a test implementation that uses default methods
        TestPluginLifecycle testPlugin = new TestPluginLifecycle();
        
        // Test default lifecycle methods (should not throw exceptions)
        assertDoesNotThrow(() -> testPlugin.onLoad(mockPluginInfo));
        assertDoesNotThrow(() -> testPlugin.onUnload(mockPluginInfo));
        assertDoesNotThrow(() -> testPlugin.onReload(mockPluginInfo));
    }

    @Test
    public void testDefaultVersion() {
        TestPluginLifecycle testPlugin = new TestPluginLifecycle();
        assertEquals("1.0.0", testPlugin.getVersion());
    }

    @Test
    public void testDefaultDescription() {
        TestPluginLifecycle testPlugin = new TestPluginLifecycle();
        assertEquals("Protocol adapter plugin", testPlugin.getDescription());
    }

    @Test
    public void testDefaultSupportsHotReload() {
        TestPluginLifecycle testPlugin = new TestPluginLifecycle();
        assertFalse(testPlugin.supportsHotReload());
    }

    @Test
    public void testCustomPluginLifecycleImplementation() throws Exception {
        // Create a custom implementation
        CustomPluginLifecycle customPlugin = new CustomPluginLifecycle();
        
        // Test custom version
        assertEquals("2.0.0", customPlugin.getVersion());
        
        // Test custom description
        assertEquals("Custom protocol adapter", customPlugin.getDescription());
        
        // Test custom hot reload support
        assertTrue(customPlugin.supportsHotReload());
        
        // Test lifecycle methods
        when(mockPluginInfo.getProtocolType()).thenReturn("custom-protocol");
        
        assertDoesNotThrow(() -> customPlugin.onLoad(mockPluginInfo));
        assertDoesNotThrow(() -> customPlugin.onUnload(mockPluginInfo));
        assertDoesNotThrow(() -> customPlugin.onReload(mockPluginInfo));
    }

    @Test
    public void testPluginInfoEquality() {
        PluginLifecycle.PluginInfo info1 = new PluginLifecycle.PluginInfo("kafka", "KafkaAdapter", "1.0.0", true);
        PluginLifecycle.PluginInfo info2 = new PluginLifecycle.PluginInfo("kafka", "KafkaAdapter", "1.0.0", true);
        PluginLifecycle.PluginInfo info3 = new PluginLifecycle.PluginInfo("pulsar", "PulsarAdapter", "1.0.0", true);
        
        // Test equality
        assertEquals(info1, info2);
        assertNotEquals(info1, info3);
        
        // Test hash code
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1.hashCode(), info3.hashCode());
    }

    @Test
    public void testPluginInfoToString() {
        PluginLifecycle.PluginInfo info = new PluginLifecycle.PluginInfo("test", "TestClass", "1.0.0", true);
        String toString = info.toString();
        
        // Should contain the key information
        assertTrue(toString.contains("test"));
        assertTrue(toString.contains("TestClass"));
        assertTrue(toString.contains("1.0.0"));
    }

    @Test
    public void testExceptionHandlingInLifecycleMethods() throws Exception {
        ExceptionThrowingPluginLifecycle exceptionPlugin = new ExceptionThrowingPluginLifecycle();
        
        when(mockPluginInfo.getProtocolType()).thenReturn("exception-protocol");
        
        // Test that exceptions are properly thrown
        assertThrows(RuntimeException.class, () -> exceptionPlugin.onLoad(mockPluginInfo));
        assertThrows(RuntimeException.class, () -> exceptionPlugin.onUnload(mockPluginInfo));
        assertThrows(RuntimeException.class, () -> exceptionPlugin.onReload(mockPluginInfo));
    }

    @Test
    public void testNullPluginInfoHandling() throws Exception {
        TestPluginLifecycle testPlugin = new TestPluginLifecycle();
        
        // Test with null PluginInfo (should not throw exception for default implementation)
        assertDoesNotThrow(() -> testPlugin.onLoad(null));
        assertDoesNotThrow(() -> testPlugin.onUnload(null));
        assertDoesNotThrow(() -> testPlugin.onReload(null));
    }

    // Test implementations for testing

    private static class TestPluginLifecycle implements PluginLifecycle {
        // Uses all default implementations
    }

    private static class CustomPluginLifecycle implements PluginLifecycle {
        @Override
        public String getVersion() {
            return "2.0.0";
        }

        @Override
        public String getDescription() {
            return "Custom protocol adapter";
        }

        @Override
        public boolean supportsHotReload() {
            return true;
        }

        @Override
        public void onLoad(PluginInfo pluginInfo) throws Exception {
            // Custom implementation
            if (pluginInfo != null) {
                // Do something with pluginInfo
            }
        }

        @Override
        public void onUnload(PluginInfo pluginInfo) throws Exception {
            // Custom implementation
            if (pluginInfo != null) {
                // Do something with pluginInfo
            }
        }

        @Override
        public void onReload(PluginInfo pluginInfo) throws Exception {
            // Custom implementation
            if (pluginInfo != null) {
                // Do something with pluginInfo
            }
        }
    }

    private static class ExceptionThrowingPluginLifecycle implements PluginLifecycle {
        @Override
        public void onLoad(PluginInfo pluginInfo) throws Exception {
            throw new RuntimeException("Load exception");
        }

        @Override
        public void onUnload(PluginInfo pluginInfo) throws Exception {
            throw new RuntimeException("Unload exception");
        }

        @Override
        public void onReload(PluginInfo pluginInfo) throws Exception {
            throw new RuntimeException("Reload exception");
        }
    }
} 