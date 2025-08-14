package org.apache.eventmesh.protocol.kafka.wire.message;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wire protocol message
 */
public class KafkaMessage {
    private short apiKey;
    private short apiVersion;
    private int correlationId;
    private String clientId;
    private ByteBuffer payload;
    private Map<String, String> headers;

    public KafkaMessage() {
        this.headers = new HashMap<>();
    }

    public KafkaMessage(short apiKey, short apiVersion, int correlationId, String clientId, ByteBuffer payload) {
        this.apiKey = apiKey;
        this.apiVersion = apiVersion;
        this.correlationId = correlationId;
        this.clientId = clientId;
        this.payload = payload;
        this.headers = new HashMap<>();
    }

    // Getters and setters
    public short getApiKey() { return apiKey; }
    public void setApiKey(short apiKey) { this.apiKey = apiKey; }
    
    public short getApiVersion() { return apiVersion; }
    public void setApiVersion(short apiVersion) { this.apiVersion = apiVersion; }
    
    public int getCorrelationId() { return correlationId; }
    public void setCorrelationId(int correlationId) { this.correlationId = correlationId; }
    
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    
    public ByteBuffer getPayload() { return payload; }
    public void setPayload(ByteBuffer payload) { this.payload = payload; }
    
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    @Override
    public String toString() {
        return "KafkaMessage{" +
                "apiKey=" + apiKey +
                ", apiVersion=" + apiVersion +
                ", correlationId=" + correlationId +
                ", clientId='" + clientId + '\'' +
                ", payloadSize=" + (payload != null ? payload.remaining() : 0) +
                '}';
    }
}
