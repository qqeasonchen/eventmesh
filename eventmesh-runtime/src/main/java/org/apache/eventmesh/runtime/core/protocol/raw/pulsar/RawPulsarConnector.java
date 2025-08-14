package org.apache.eventmesh.runtime.core.protocol.raw.pulsar;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.pulsar.raw.RawPulsarProtocolAdapter;
import org.apache.eventmesh.protocol.pulsar.raw.message.RawPulsarMessage;
import org.apache.eventmesh.runtime.core.protocol.raw.RawConnector;
import org.apache.eventmesh.runtime.core.protocol.raw.RawMessageHandler;
import org.apache.eventmesh.api.producer.Producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class RawPulsarConnector implements RawConnector {
    
    private static final Logger log = LoggerFactory.getLogger(RawPulsarConnector.class);
    
    private ProtocolAdaptor protocolAdaptor;
    private final RawMessageHandler messageHandler;
    private final Producer producer;
    private boolean running = false;

    public RawPulsarConnector(Producer producer, RawMessageHandler messageHandler) {
        this.producer = producer;
        this.messageHandler = messageHandler;
        this.protocolAdaptor = new org.apache.eventmesh.protocol.pulsar.raw.RawPulsarProtocolAdapter();
    }

    @Override
    public void start() {
        log.info("Raw Pulsar connector started");
        running = true;
    }

    @Override
    public void shutdown() throws Exception {
        running = false;
        log.info("Raw Pulsar connector stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getConnectorType() {
        return "pulsar-raw";
    }

    public void handleRawPulsarMessage(RawPulsarMessage message) {
        if (!running) {
            log.warn("Raw Pulsar connector is not running, message ignored");
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
     * @param message the raw Pulsar message
     */
    private void handleDirectTransmission(RawPulsarMessage message) {
        try {
            log.debug("Using direct transmission for Pulsar message: {}", message.getMessageId());
            
            // Direct transmission without CloudEvent conversion
            messageHandler.handleMessage(message);
            
        } catch (Exception e) {
            log.error("Failed to handle direct transmission for Pulsar message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle message with CloudEvent conversion.
     *
     * @param message the raw Pulsar message
     */
    private void handleWithConversion(RawPulsarMessage message) {
        try {
            log.debug("Converting Pulsar message to CloudEvent: {}", message.getMessageId());
            
            // Convert to CloudEvent for cross-protocol communication
            io.cloudevents.CloudEvent cloudEvent = protocolAdaptor.toCloudEvent(message);
            messageHandler.handleCloudEvent(cloudEvent);
            
        } catch (ProtocolHandleException e) {
            log.error("Failed to convert Pulsar message to CloudEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Send raw Pulsar message asynchronously.
     *
     * @param message the raw Pulsar message to send
     * @return CompletableFuture for async operation
     */
    public CompletableFuture<Void> sendRawPulsarMessage(RawPulsarMessage message) {
        return CompletableFuture.runAsync(() -> {
            try {
                handleRawPulsarMessage(message);
            } catch (Exception e) {
                log.error("Failed to send raw Pulsar message: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Detect protocol type from message for optimization.
     *
     * @param message the raw Pulsar message
     * @return detected protocol type
     */
    private String detectProtocolType(RawPulsarMessage message) {
        // For Pulsar, we can detect based on message properties or topic
        if (message.getProperties() != null && message.getProperties().containsKey("protocol")) {
            return message.getProperties().get("protocol");
        }
        
        // Default to pulsar-raw for Pulsar messages
        return "pulsar-raw";
    }

    /**
     * Check if direct transmission is possible.
     *
     * @param protocolType the protocol type
     * @return true if direct transmission is possible
     */
    private boolean canTransmitDirectly(String protocolType) {
        return ProtocolPluginFactory.canTransmitDirectly("pulsar-raw", protocolType);
    }

    public org.apache.eventmesh.protocol.api.ProtocolAdaptor<org.apache.eventmesh.common.protocol.ProtocolTransportObject> getProtocolAdaptor() {
        return (org.apache.eventmesh.protocol.api.ProtocolAdaptor<org.apache.eventmesh.common.protocol.ProtocolTransportObject>) protocolAdaptor;
    }

    @Override
    public org.apache.eventmesh.runtime.core.protocol.raw.RawConnector.RawConnectorStats getStats() {
        return new RawPulsarConnectorStats();
    }

    // 内部类实现
    private static class RawPulsarConnectorStats implements org.apache.eventmesh.runtime.core.protocol.raw.RawConnector.RawConnectorStats {
        @Override
        public int getPendingMessageCount() { return 0; }
        @Override
        public boolean isShutdown() { return false; }
        @Override
        public String getConnectorType() { return "pulsar"; }
    }
} 