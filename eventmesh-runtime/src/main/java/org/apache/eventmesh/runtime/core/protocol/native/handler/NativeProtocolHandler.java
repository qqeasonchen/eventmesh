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

package org.apache.eventmesh.runtime.core.protocol.native.handler;

import org.apache.eventmesh.runtime.core.protocol.native.NativeProtocolManager;
import org.apache.eventmesh.runtime.core.protocol.native.kafka.NativeKafkaConnector;
import org.apache.eventmesh.runtime.core.protocol.native.pulsar.NativePulsarConnector;
import org.apache.eventmesh.runtime.core.protocol.native.rocketmq.NativeRocketMQConnector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;

/**
 * Native protocol handler for processing native client connections and messages.
 * Automatically detects protocol type and routes messages to appropriate connectors.
 */
public class NativeProtocolHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(NativeProtocolHandler.class);

    private final NativeProtocolHandlerFactory handlerFactory;
    private String detectedProtocol = null;
    private SocketChannel channel;

    public NativeProtocolHandler(NativeProtocolHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.channel = (SocketChannel) ctx.channel();
        log.info("Native client connected: {}", channel.remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Native client disconnected: {}", channel.remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof byte[]) {
            byte[] data = (byte[]) msg;
            handleNativeMessage(data);
        } else if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            handleNativeMessage(data);
        } else {
            log.warn("Received unknown message type: {}", msg.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Error in native protocol handler", cause);
        ctx.close();
    }

    /**
     * Handle native message and detect protocol type
     */
    private void handleNativeMessage(byte[] data) {
        try {
            // Detect protocol type if not already detected
            if (detectedProtocol == null) {
                detectedProtocol = detectProtocolType(data);
                log.info("Detected protocol type: {} for client: {}", detectedProtocol, channel.remoteAddress());
            }

            // Route message to appropriate connector
            routeMessage(detectedProtocol, data);
        } catch (Exception e) {
            log.error("Failed to handle native message", e);
        }
    }

    /**
     * Detect protocol type from message data
     */
    private String detectProtocolType(byte[] data) {
        // Simple protocol detection based on message patterns
        // In a real implementation, this would be more sophisticated
        
        if (data.length < 4) {
            return "unknown";
        }

        // Check for Kafka protocol patterns
        if (isKafkaProtocol(data)) {
            return "kafka-native";
        }

        // Check for Pulsar protocol patterns
        if (isPulsarProtocol(data)) {
            return "pulsar-native";
        }

        // Check for RocketMQ protocol patterns
        if (isRocketMQProtocol(data)) {
            return "rocketmq-native";
        }

        // Default to Kafka for compatibility
        log.warn("Could not detect protocol type, defaulting to kafka-native");
        return "kafka-native";
    }

    /**
     * Check if data matches Kafka protocol
     */
    private boolean isKafkaProtocol(byte[] data) {
        // Kafka protocol detection logic
        // This is a simplified implementation
        if (data.length >= 4) {
            // Check for Kafka magic byte (0x00 for v0, 0x01 for v1, 0x02 for v2)
            byte magic = data[0];
            return magic >= 0 && magic <= 2;
        }
        return false;
    }

    /**
     * Check if data matches Pulsar protocol
     */
    private boolean isPulsarProtocol(byte[] data) {
        // Pulsar protocol detection logic
        // This is a simplified implementation
        if (data.length >= 4) {
            // Check for Pulsar protocol markers
            // Pulsar typically starts with specific byte patterns
            return data[0] == 0x0E && data[1] == 0x01;
        }
        return false;
    }

    /**
     * Check if data matches RocketMQ protocol
     */
    private boolean isRocketMQProtocol(byte[] data) {
        // RocketMQ protocol detection logic
        // This is a simplified implementation
        if (data.length >= 4) {
            // Check for RocketMQ protocol markers
            // RocketMQ typically has specific header patterns
            return data[0] == 0x00 && data[1] == 0x00 && data[2] == 0x00 && data[3] == 0x00;
        }
        return false;
    }

    /**
     * Route message to appropriate connector
     */
    private void routeMessage(String protocolType, byte[] data) {
        try {
            switch (protocolType) {
                case "kafka-native":
                    routeKafkaMessage(data);
                    break;
                case "pulsar-native":
                    routePulsarMessage(data);
                    break;
                case "rocketmq-native":
                    routeRocketMQMessage(data);
                    break;
                default:
                    log.warn("Unknown protocol type: {}", protocolType);
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to route message for protocol: {}", protocolType, e);
        }
    }

    /**
     * Route Kafka message
     */
    private void routeKafkaMessage(byte[] data) {
        try {
            NativeKafkaConnector connector = handlerFactory.getProtocolManager().getKafkaConnector();
            if (connector != null && connector.isRunning()) {
                // Convert byte array to Kafka message
                var kafkaMessage = convertToKafkaMessage(data);
                connector.handleNativeKafkaMessage(kafkaMessage);
            } else {
                log.warn("Kafka connector not available");
            }
        } catch (Exception e) {
            log.error("Failed to route Kafka message", e);
        }
    }

    /**
     * Route Pulsar message
     */
    private void routePulsarMessage(byte[] data) {
        try {
            NativePulsarConnector connector = handlerFactory.getProtocolManager().getPulsarConnector();
            if (connector != null && connector.isRunning()) {
                // Convert byte array to Pulsar message
                var pulsarMessage = convertToPulsarMessage(data);
                connector.handleNativePulsarMessage(pulsarMessage);
            } else {
                log.warn("Pulsar connector not available");
            }
        } catch (Exception e) {
            log.error("Failed to route Pulsar message", e);
        }
    }

    /**
     * Route RocketMQ message
     */
    private void routeRocketMQMessage(byte[] data) {
        try {
            NativeRocketMQConnector connector = handlerFactory.getProtocolManager().getRocketMQConnector();
            if (connector != null && connector.isRunning()) {
                // Convert byte array to RocketMQ message
                var rocketmqMessage = convertToRocketMQMessage(data);
                connector.handleNativeRocketMQMessage(rocketmqMessage);
            } else {
                log.warn("RocketMQ connector not available");
            }
        } catch (Exception e) {
            log.error("Failed to route RocketMQ message", e);
        }
    }

    /**
     * Convert byte array to Kafka message
     */
    private org.apache.eventmesh.protocol.kafka.native.message.NativeKafkaMessage convertToKafkaMessage(byte[] data) {
        // This is a simplified conversion
        // In a real implementation, this would parse the actual Kafka protocol
        var message = new org.apache.eventmesh.protocol.kafka.native.message.NativeKafkaMessage();
        message.setValue(data);
        message.setMessageId("kafka-" + System.currentTimeMillis());
        message.setTopic("default-topic");
        return message;
    }

    /**
     * Convert byte array to Pulsar message
     */
    private org.apache.eventmesh.protocol.pulsar.native.message.NativePulsarMessage convertToPulsarMessage(byte[] data) {
        // This is a simplified conversion
        // In a real implementation, this would parse the actual Pulsar protocol
        var message = new org.apache.eventmesh.protocol.pulsar.native.message.NativePulsarMessage();
        message.setData(data);
        message.setMessageId("pulsar-" + System.currentTimeMillis());
        message.setTopic("default-topic");
        return message;
    }

    /**
     * Convert byte array to RocketMQ message
     */
    private org.apache.eventmesh.protocol.rocketmq.native.message.NativeRocketMQMessage convertToRocketMQMessage(byte[] data) {
        // This is a simplified conversion
        // In a real implementation, this would parse the actual RocketMQ protocol
        var message = new org.apache.eventmesh.protocol.rocketmq.native.message.NativeRocketMQMessage();
        message.setBody(data);
        message.setMsgId("rocketmq-" + System.currentTimeMillis());
        message.setTopic("default-topic");
        return message;
    }
} 