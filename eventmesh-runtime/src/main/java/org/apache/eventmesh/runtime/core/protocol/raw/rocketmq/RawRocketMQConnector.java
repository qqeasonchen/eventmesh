package org.apache.eventmesh.runtime.core.protocol.raw.rocketmq;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.rocketmq.raw.RawRocketMQProtocolAdapter;
import org.apache.eventmesh.protocol.rocketmq.raw.message.RawRocketMQMessage;
import org.apache.eventmesh.runtime.core.protocol.raw.RawConnector;
import org.apache.eventmesh.runtime.core.protocol.raw.RawMessageHandler;
import org.apache.eventmesh.api.producer.Producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class RawRocketMQConnector implements RawConnector {
    
    private static final Logger log = LoggerFactory.getLogger(RawRocketMQConnector.class);
    
    private ProtocolAdaptor protocolAdaptor;
    private final RawMessageHandler messageHandler;
    private final Producer producer;
    private boolean running = false;

    public RawRocketMQConnector(Producer producer, RawMessageHandler messageHandler) {
        this.producer = producer;
        this.messageHandler = messageHandler;
        this.protocolAdaptor = new org.apache.eventmesh.protocol.rocketmq.raw.RawRocketMQProtocolAdapter();
    }

    @Override
    public void start() {
        running = true;
        log.info("Raw RocketMQ connector started");
    }

    @Override
    public void shutdown() throws Exception {
        running = false;
        log.info("Raw RocketMQ connector stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getConnectorType() {
        return "rocketmq-raw";
    }

    public void handleRawRocketMQMessage(RawRocketMQMessage message) {
        if (!running) {
            log.warn("Raw RocketMQ connector is not running, message ignored");
            return;
        }

        String protocolType = detectProtocolType(message);
        
        // Check if we can handle this message directly
        if (canTransmitDirectly(protocolType)) {
            handleDirectTransmission(message);
        } else {
            handleWithConversion(message);
        }
    }

    /**
     * Handle direct transmission for same protocol optimization.
     *
     * @param message the raw RocketMQ message
     */
    private void handleDirectTransmission(RawRocketMQMessage message) {
        try {
            log.debug("Using direct transmission for RocketMQ message: {}", message.getMessageId());
            
            // Direct transmission without CloudEvent conversion
            messageHandler.handleMessage(message);
            
        } catch (Exception e) {
            log.error("Failed to handle direct transmission for RocketMQ message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle message with CloudEvent conversion.
     *
     * @param message the raw RocketMQ message
     */
    private void handleWithConversion(RawRocketMQMessage message) {
        try {
            log.debug("Converting RocketMQ message to CloudEvent: {}", message.getMessageId());
            
            // Convert to CloudEvent for cross-protocol communication
            io.cloudevents.CloudEvent cloudEvent = protocolAdaptor.toCloudEvent(message);
            messageHandler.handleCloudEvent(cloudEvent);
            
        } catch (ProtocolHandleException e) {
            log.error("Failed to convert RocketMQ message to CloudEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Send raw RocketMQ message asynchronously.
     *
     * @param message the raw RocketMQ message to send
     * @return CompletableFuture for async operation
     */
    public CompletableFuture<Void> sendRawRocketMQMessage(RawRocketMQMessage message) {
        return CompletableFuture.runAsync(() -> {
            try {
                handleRawRocketMQMessage(message);
            } catch (Exception e) {
                log.error("Failed to send raw RocketMQ message: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Detect protocol type from message for optimization.
     *
     * @param message the raw RocketMQ message
     * @return detected protocol type
     */
    private String detectProtocolType(RawRocketMQMessage message) {
        // For RocketMQ, we can detect based on message properties or topic
        if (message.getProperties() != null && message.getProperties().containsKey("protocol")) {
            return message.getProperties().get("protocol");
        }
        
        // Default to rocketmq-raw for RocketMQ messages
        return "rocketmq-raw";
    }

    /**
     * Check if direct transmission is possible.
     *
     * @param protocolType the protocol type
     * @return true if direct transmission is possible
     */
    private boolean canTransmitDirectly(String protocolType) {
        return ProtocolPluginFactory.canTransmitDirectly("rocketmq-raw", protocolType);
    }

    @Override
    public org.apache.eventmesh.runtime.core.protocol.raw.RawConnector.RawConnectorStats getStats() {
        return new RawRocketMQConnectorStats();
    }

    // 内部类实现
    private static class RawRocketMQConnectorStats implements org.apache.eventmesh.runtime.core.protocol.raw.RawConnector.RawConnectorStats {
        @Override
        public int getPendingMessageCount() { return 0; }
        @Override
        public boolean isShutdown() { return false; }
        @Override
        public String getConnectorType() { return "rocketmq"; }
    }
} 