package org.apache.eventmesh.protocol.kafka.wire.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Kafka message codec for wire protocol
 */
public class KafkaMessageCodec {
    
    /**
     * Decode Kafka message from ByteBuffer
     */
    public static KafkaMessage decode(ByteBuffer buffer) {
        // Netty's decoder has already stripped the 4-byte size. This buffer starts at apiKey.
        buffer.order(ByteOrder.BIG_ENDIAN);

        // Need at least: apiKey(2) + apiVersion(2) + correlationId(4) + clientIdLength(2)
        if (buffer.remaining() < 10) {
            System.out.println("DEBUG: Not enough data to decode Kafka message header, remaining: " + buffer.remaining());
            return null;
        }

        // Read API key (2 bytes)
        short apiKey = buffer.getShort();
        System.out.println("DEBUG: API Key: " + apiKey + " (" + KafkaApiKeys.getApiKey(apiKey) + ")");

        // Read API version (2 bytes)
        short apiVersion = buffer.getShort();
        System.out.println("DEBUG: API Version: " + apiVersion);

        // Read correlation ID (4 bytes)
        int correlationId = buffer.getInt();
        System.out.println("DEBUG: Correlation ID: " + correlationId);

        // Read client ID length (2 bytes)
        short clientIdLength = buffer.getShort();
        System.out.println("DEBUG: Client ID length: " + clientIdLength);

        // Read client ID
        String clientId = "";
        if (clientIdLength > 0) {
            if (buffer.remaining() < clientIdLength) {
                System.out.println("DEBUG: Not enough data for clientId body, remaining: " + buffer.remaining() + ", needed: " + clientIdLength);
                return null;
            }
            byte[] clientIdBytes = new byte[clientIdLength];
            buffer.get(clientIdBytes);
            clientId = new String(clientIdBytes, StandardCharsets.UTF_8);
            System.out.println("DEBUG: Client ID: " + clientId);
        }

        // The remaining bytes are the payload
        ByteBuffer payload = null;
        if (buffer.remaining() > 0) {
            payload = buffer.slice();
            System.out.println("DEBUG: Payload remaining bytes: " + payload.remaining());
        }

        System.out.println(
            "DEBUG: Decoded Kafka message - apiKey=" + apiKey +
            ", apiVersion=" + apiVersion +
            ", correlationId=" + correlationId +
            ", clientId=" + clientId +
            ", payloadSize=" + (payload != null ? payload.remaining() : 0)
        );

        return new KafkaMessage(apiKey, apiVersion, correlationId, clientId, payload);
    }
    
    /**
     * Encode Kafka message to ByteBuffer
     */
    public static ByteBuffer encode(KafkaMessage message) {
        // Calculate total size
        int totalSize = 4; // message body length
        totalSize += 2; // api key
        totalSize += 2; // api version
        totalSize += 4; // correlation id
        totalSize += 2; // client id length
        totalSize += (message.getClientId() != null ? message.getClientId().getBytes(StandardCharsets.UTF_8).length : 0);
        totalSize += (message.getPayload() != null ? message.getPayload().remaining() : 0);

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // Write message body length (excluding the length field itself)
        buffer.putInt(totalSize - 4);

        // Write API key
        buffer.putShort(message.getApiKey());

        // Write API version
        buffer.putShort(message.getApiVersion());

        // Write correlation ID
        buffer.putInt(message.getCorrelationId());

        // Write client ID
        if (message.getClientId() != null) {
            byte[] clientIdBytes = message.getClientId().getBytes(StandardCharsets.UTF_8);
            buffer.putShort((short) clientIdBytes.length);
            buffer.put(clientIdBytes);
        } else {
            buffer.putShort((short) 0);
        }

        // Write payload
        if (message.getPayload() != null) {
            buffer.put(message.getPayload());
        }

        buffer.flip();
        return buffer;
    }
    
    /**
     * Create a simple error response
     */
    public static KafkaMessage createErrorResponse(short apiKey, short apiVersion, int correlationId, String clientId, short errorCode) {
        ByteBuffer errorPayload = ByteBuffer.allocate(2);
        errorPayload.putShort(errorCode);
        errorPayload.flip();
        
        return new KafkaMessage(apiKey, apiVersion, correlationId, clientId, errorPayload);
    }
}
