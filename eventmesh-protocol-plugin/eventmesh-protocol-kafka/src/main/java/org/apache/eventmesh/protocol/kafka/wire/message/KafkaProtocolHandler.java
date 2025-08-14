package org.apache.eventmesh.protocol.kafka.wire.message;

import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka wire protocol handler
 */
public class KafkaProtocolHandler {
    private static final Logger log = LoggerFactory.getLogger(KafkaProtocolHandler.class);
    
    private final Producer producer;
    private final Map<String, List<KafkaTopicMetadata>> topicMetadata = new HashMap<>();
    
    public KafkaProtocolHandler(Producer producer) {
        this.producer = producer;
        // Initialize with some default topics
        initializeDefaultTopics();
    }
    
    private void initializeDefaultTopics() {
        List<KafkaTopicMetadata> topics = new ArrayList<>();
        topics.add(new KafkaTopicMetadata("eventmesh-raw-test", 1, 0));
        topics.add(new KafkaTopicMetadata("test-topic", 1, 0));
        topicMetadata.put("localhost:19092", topics);
    }
    
    public KafkaMessage handleRequest(KafkaMessage request) {
        if (request == null) {
            log.error("Received null request");
            return null;
        }
        
        log.info("Handling Kafka request: apiKey={}, apiVersion={}, correlationId={}, clientId={}", 
                request.getApiKey(), request.getApiVersion(), request.getCorrelationId(), request.getClientId());
        
        try {
            switch (request.getApiKey()) {
                case KafkaApiKeys.API_VERSIONS:
                    log.info("Processing API_VERSIONS request");
                    return handleApiVersions(request);
                case KafkaApiKeys.METADATA:
                    log.info("Processing METADATA request");
                    return handleMetadata(request);
                case KafkaApiKeys.PRODUCE:
                    log.info("Processing PRODUCE request");
                    return handleProduce(request);
                case KafkaApiKeys.FETCH:
                    log.info("Processing FETCH request");
                    return handleFetch(request);
                default:
                    log.warn("Unsupported API key: {}", request.getApiKey());
                    return createErrorResponse(request, (short) 35); // UNSUPPORTED_VERSION
            }
        } catch (Exception e) {
            log.error("Error handling request: apiKey={}, correlationId={}", 
                    request.getApiKey(), request.getCorrelationId(), e);
            return createErrorResponse(request, (short) 1); // UNKNOWN
        }
    }
    
    private KafkaMessage handleApiVersions(KafkaMessage request) {
        log.info("Handling API_VERSIONS request");
        
        // Create API versions response
        ByteBuffer responsePayload = ByteBuffer.allocate(1024);
        
        // Error code (0 = success)
        responsePayload.putShort((short) 0);
        
        // API versions array
        responsePayload.putInt(4); // Number of APIs
        
        // API_VERSIONS
        responsePayload.putShort(KafkaApiKeys.API_VERSIONS);
        responsePayload.putShort((short) 0); // Min version
        responsePayload.putShort((short) 3); // Max version
        
        // METADATA
        responsePayload.putShort(KafkaApiKeys.METADATA);
        responsePayload.putShort((short) 0);
        responsePayload.putShort((short) 12);
        
        // PRODUCE
        responsePayload.putShort(KafkaApiKeys.PRODUCE);
        responsePayload.putShort((short) 0);
        responsePayload.putShort((short) 9);
        
        // FETCH
        responsePayload.putShort(KafkaApiKeys.FETCH);
        responsePayload.putShort((short) 0);
        responsePayload.putShort((short) 12);
        
        responsePayload.flip();
        
        return new KafkaMessage(
            KafkaApiKeys.API_VERSIONS,
            request.getApiVersion(),
            request.getCorrelationId(),
            request.getClientId(),
            responsePayload
        );
    }
    
    private KafkaMessage handleMetadata(KafkaMessage request) {
        log.info("Handling METADATA request");
        
        // Parse the request to get requested topics
        List<String> requestedTopics = parseMetadataRequest(request.getPayload());
        log.info("Requested topics: {}", requestedTopics);
        
        ByteBuffer responsePayload = ByteBuffer.allocate(2048);
        
        // Error code (0 = success)
        responsePayload.putShort((short) 0);
        
        // Throttle time
        responsePayload.putInt(0);
        
        // Brokers array
        responsePayload.putInt(1); // Number of brokers
        responsePayload.putInt(0); // Node ID
        responsePayload.putShort((short) "localhost".length());
        responsePayload.put("localhost".getBytes());
        responsePayload.putInt(19092); // Port
        responsePayload.putShort((short) 0); // Rack (null)
        
        // Cluster ID
        responsePayload.putShort((short) 0); // Null string
        
        // Controller ID
        responsePayload.putInt(0);
        
        // Topics array - return metadata for requested topics
        List<KafkaTopicMetadata> availableTopics = topicMetadata.get("localhost:19092");
        List<KafkaTopicMetadata> responseTopics = new ArrayList<>();
        
        if (requestedTopics.isEmpty()) {
            // If no topics requested, return all available topics
            responseTopics = availableTopics;
        } else {
            // Return metadata for requested topics
            for (String requestedTopic : requestedTopics) {
                KafkaTopicMetadata topic = findTopic(availableTopics, requestedTopic);
                if (topic != null) {
                    responseTopics.add(topic);
                } else {
                    // Topic not found, add with error code
                    responseTopics.add(new KafkaTopicMetadata(requestedTopic, 0, 0));
                }
            }
        }
        
        responsePayload.putInt(responseTopics.size());
        
        for (KafkaTopicMetadata topic : responseTopics) {
            if (topic.getPartitionCount() > 0) {
                // Topic exists
                responsePayload.putShort((short) 0); // Error code
                responsePayload.putShort((short) topic.getName().length());
                responsePayload.put(topic.getName().getBytes());
                responsePayload.put((byte) 0); // Is internal
                
                // Partitions array
                responsePayload.putInt(topic.getPartitionCount()); // Number of partitions
                for (int i = 0; i < topic.getPartitionCount(); i++) {
                    responsePayload.putShort((short) 0); // Error code
                    responsePayload.putInt(i); // Partition ID
                    responsePayload.putInt(0); // Leader ID
                    responsePayload.putInt(1); // Replica count
                    responsePayload.putInt(0); // Replica array - broker ID 0
                    responsePayload.putInt(1); // ISR count
                    responsePayload.putInt(0); // ISR array - broker ID 0
                    responsePayload.putInt(0); // Offline replica count
                }
            } else {
                // Topic doesn't exist
                responsePayload.putShort((short) 3); // UNKNOWN_TOPIC_OR_PARTITION
                responsePayload.putShort((short) topic.getName().length());
                responsePayload.put(topic.getName().getBytes());
                responsePayload.put((byte) 0); // Is internal
                responsePayload.putInt(0); // Number of partitions
            }
        }
        
        responsePayload.flip();
        
        return new KafkaMessage(
            KafkaApiKeys.METADATA,
            request.getApiVersion(),
            request.getCorrelationId(),
            request.getClientId(),
            responsePayload
        );
    }
    
    private List<String> parseMetadataRequest(ByteBuffer payload) {
        List<String> topics = new ArrayList<>();
        if (payload == null) {
            return topics;
        }
        
        try {
            // Skip client ID length and client ID
            if (payload.remaining() >= 2) {
                short clientIdLength = payload.getShort();
                if (clientIdLength > 0 && payload.remaining() >= clientIdLength) {
                    payload.position(payload.position() + clientIdLength);
                }
            }
            
            // Read topics array
            if (payload.remaining() >= 4) {
                int topicsCount = payload.getInt();
                for (int i = 0; i < topicsCount && payload.remaining() >= 2; i++) {
                    short topicNameLength = payload.getShort();
                    if (topicNameLength > 0 && payload.remaining() >= topicNameLength) {
                        byte[] topicNameBytes = new byte[topicNameLength];
                        payload.get(topicNameBytes);
                        String topicName = new String(topicNameBytes, StandardCharsets.UTF_8);
                        topics.add(topicName);
                        log.info("Parsed requested topic: {}", topicName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing metadata request", e);
        }
        
        return topics;
    }
    
    private KafkaTopicMetadata findTopic(List<KafkaTopicMetadata> topics, String topicName) {
        for (KafkaTopicMetadata topic : topics) {
            if (topic.getName().equals(topicName)) {
                return topic;
            }
        }
        return null;
    }
    
    private KafkaMessage handleProduce(KafkaMessage request) {
        log.info("Handling PRODUCE request");
        
        try {
            // Parse produce request
            ProduceRequest produceRequest = parseProduceRequest(request.getPayload());
            log.info("Parsed produce request: topic={}, partition={}, records={}", 
                    produceRequest.getTopic(), produceRequest.getPartition(), produceRequest.getRecords().size());
            
            // Forward to EventMesh producer
            if (producer != null) {
                for (ProduceRecord record : produceRequest.getRecords()) {
                    try {
                        // Create CloudEvent from Kafka record
                        io.cloudevents.CloudEvent cloudEvent = io.cloudevents.core.builder.CloudEventBuilder.v1()
                            .withId(record.getKey() != null ? record.getKey() : java.util.UUID.randomUUID().toString())
                            .withSource(java.net.URI.create("kafka://localhost:19092"))
                            .withType("org.apache.kafka.produce")
                            .withSubject(produceRequest.getTopic())
                            .withData(record.getValue().getBytes(StandardCharsets.UTF_8))
                            .withExtension("kafka-topic", produceRequest.getTopic())
                            .withExtension("kafka-partition", produceRequest.getPartition())
                            .withExtension("kafka-key", record.getKey())
                            .build();
                        
                        producer.publish(cloudEvent, null);
                        log.info("Successfully forwarded message to EventMesh producer: topic={}, key={}", 
                                produceRequest.getTopic(), record.getKey());
                    } catch (Exception e) {
                        log.error("Error forwarding message to EventMesh producer", e);
                    }
                }
            }
            
            // Create response
            ByteBuffer responsePayload = ByteBuffer.allocate(1024);
            
            // Response array
            responsePayload.putInt(1); // Number of topics
            
            // Topic name
            responsePayload.putShort((short) produceRequest.getTopic().length());
            responsePayload.put(produceRequest.getTopic().getBytes());
            
            // Partition responses
            responsePayload.putInt(1); // Number of partitions
            responsePayload.putInt(produceRequest.getPartition()); // Partition ID
            responsePayload.putShort((short) 0); // Error code
            responsePayload.putLong(System.currentTimeMillis()); // Base offset
            responsePayload.putLong(System.currentTimeMillis()); // Log append time
            responsePayload.putLong(0); // Log start offset
            
            // Throttle time
            responsePayload.putInt(0);
            
            responsePayload.flip();
            
            return new KafkaMessage(
                KafkaApiKeys.PRODUCE,
                request.getApiVersion(),
                request.getCorrelationId(),
                request.getClientId(),
                responsePayload
            );
        } catch (Exception e) {
            log.error("Error handling produce request", e);
            return createErrorResponse(request, (short) 1); // UNKNOWN
        }
    }
    
    private ProduceRequest parseProduceRequest(ByteBuffer payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload is null");
        }
        
        try {
            // Skip acks, timeout, topic count
            payload.position(payload.position() + 6);
            
            // Read topic name
            short topicNameLength = payload.getShort();
            byte[] topicNameBytes = new byte[topicNameLength];
            payload.get(topicNameBytes);
            String topicName = new String(topicNameBytes, StandardCharsets.UTF_8);
            
            // Read partition count
            int partitionCount = payload.getInt();
            if (partitionCount != 1) {
                throw new UnsupportedOperationException("Only single partition supported");
            }
            
            // Read partition ID
            int partitionId = payload.getInt();
            
            // Skip message set size
            int messageSetSize = payload.getInt();
            
            // Read records
            List<ProduceRecord> records = new ArrayList<>();
            int endPosition = payload.position() + messageSetSize;
            
            while (payload.position() < endPosition && payload.remaining() >= 8) {
                long offset = payload.getLong();
                
                // Read message size
                int messageSize = payload.getInt();
                if (messageSize <= 0 || payload.remaining() < messageSize) {
                    break;
                }
                
                // Skip CRC
                payload.position(payload.position() + 4);
                
                // Read magic byte
                byte magic = payload.get();
                
                // Read attributes
                byte attributes = payload.get();
                
                // Read key length
                int keyLength = payload.getInt();
                String key = null;
                if (keyLength > 0) {
                    byte[] keyBytes = new byte[keyLength];
                    payload.get(keyBytes);
                    key = new String(keyBytes, StandardCharsets.UTF_8);
                }
                
                // Read value length
                int valueLength = payload.getInt();
                String value = null;
                if (valueLength > 0) {
                    byte[] valueBytes = new byte[valueLength];
                    payload.get(valueBytes);
                    value = new String(valueBytes, StandardCharsets.UTF_8);
                }
                
                records.add(new ProduceRecord(key, value));
                log.debug("Parsed record: key={}, value={}", key, value);
            }
            
            return new ProduceRequest(topicName, partitionId, records);
        } catch (Exception e) {
            log.error("Error parsing produce request", e);
            throw new RuntimeException("Failed to parse produce request", e);
        }
    }
    
    private KafkaMessage handleFetch(KafkaMessage request) {
        log.info("Handling FETCH request");
        
        try {
            // Parse fetch request
            FetchRequest fetchRequest = parseFetchRequest(request.getPayload());
            log.info("Parsed fetch request: topic={}, partition={}, offset={}", 
                    fetchRequest.getTopic(), fetchRequest.getPartition(), fetchRequest.getOffset());
            
            // For now, return empty response (no messages available)
            // In a real implementation, this would fetch messages from EventMesh storage
            ByteBuffer responsePayload = ByteBuffer.allocate(1024);
            
            // Throttle time
            responsePayload.putInt(0);
            
            // Response array
            responsePayload.putInt(1); // Number of topics
            
            // Topic name
            responsePayload.putShort((short) fetchRequest.getTopic().length());
            responsePayload.put(fetchRequest.getTopic().getBytes());
            
            // Partition responses
            responsePayload.putInt(1); // Number of partitions
            responsePayload.putInt(fetchRequest.getPartition()); // Partition ID
            responsePayload.putShort((short) 0); // Error code
            responsePayload.putLong(fetchRequest.getOffset()); // High watermark
            responsePayload.putLong(fetchRequest.getOffset()); // Last stable offset
            responsePayload.putLong(0); // Log start offset
            responsePayload.putInt(0); // Aborted transactions count
            responsePayload.putInt(0); // Preferred read replica
            responsePayload.putInt(0); // Number of records (empty)
            
            responsePayload.flip();
            
            return new KafkaMessage(
                KafkaApiKeys.FETCH,
                request.getApiVersion(),
                request.getCorrelationId(),
                request.getClientId(),
                responsePayload
            );
        } catch (Exception e) {
            log.error("Error handling fetch request", e);
            return createErrorResponse(request, (short) 1); // UNKNOWN
        }
    }
    
    private FetchRequest parseFetchRequest(ByteBuffer payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload is null");
        }
        
        try {
            // Skip replica ID, max wait time, min bytes
            payload.position(payload.position() + 8);
            
            // Read max bytes
            int maxBytes = payload.getInt();
            
            // Skip isolation level, session ID, session epoch
            payload.position(payload.position() + 7);
            
            // Read topic count
            int topicCount = payload.getInt();
            if (topicCount != 1) {
                throw new UnsupportedOperationException("Only single topic supported");
            }
            
            // Read topic name
            short topicNameLength = payload.getShort();
            byte[] topicNameBytes = new byte[topicNameLength];
            payload.get(topicNameBytes);
            String topicName = new String(topicNameBytes, StandardCharsets.UTF_8);
            
            // Read partition count
            int partitionCount = payload.getInt();
            if (partitionCount != 1) {
                throw new UnsupportedOperationException("Only single partition supported");
            }
            
            // Read partition ID
            int partitionId = payload.getInt();
            
            // Read current leader epoch
            int currentLeaderEpoch = payload.getInt();
            
            // Read fetch offset
            long fetchOffset = payload.getLong();
            
            // Skip log start offset, max bytes
            payload.position(payload.position() + 12);
            
            return new FetchRequest(topicName, partitionId, fetchOffset);
        } catch (Exception e) {
            log.error("Error parsing fetch request", e);
            throw new RuntimeException("Failed to parse fetch request", e);
        }
    }
    
    private KafkaMessage createErrorResponse(KafkaMessage request, short errorCode) {
        if (request == null) {
            log.error("Cannot create error response for null request");
            return null;
        }
        
        log.warn("Creating error response: apiKey={}, correlationId={}, errorCode={}", 
                request.getApiKey(), request.getCorrelationId(), errorCode);
        
        ByteBuffer responsePayload = ByteBuffer.allocate(4);
        responsePayload.putShort(errorCode);
        responsePayload.putShort((short) 0); // Additional error info
        responsePayload.flip();
        
        return new KafkaMessage(
            request.getApiKey(),
            request.getApiVersion(),
            request.getCorrelationId(),
            request.getClientId(),
            responsePayload
        );
    }
    
    private static class KafkaTopicMetadata {
        private final String name;
        private final int partitionCount;
        private final int replicationFactor;
        
        public KafkaTopicMetadata(String name, int partitionCount, int replicationFactor) {
            this.name = name;
            this.partitionCount = partitionCount;
            this.replicationFactor = replicationFactor;
        }
        
        public String getName() { return name; }
        public int getPartitionCount() { return partitionCount; }
        public int getReplicationFactor() { return replicationFactor; }
    }
    
    private static class ProduceRequest {
        private final String topic;
        private final int partition;
        private final List<ProduceRecord> records;
        
        public ProduceRequest(String topic, int partition, List<ProduceRecord> records) {
            this.topic = topic;
            this.partition = partition;
            this.records = records;
        }
        
        public String getTopic() { return topic; }
        public int getPartition() { return partition; }
        public List<ProduceRecord> getRecords() { return records; }
    }
    
    private static class ProduceRecord {
        private final String key;
        private final String value;
        
        public ProduceRecord(String key, String value) {
            this.key = key;
            this.value = value;
        }
        
        public String getKey() { return key; }
        public String getValue() { return value; }
    }
    
    private static class FetchRequest {
        private final String topic;
        private final int partition;
        private final long offset;
        
        public FetchRequest(String topic, int partition, long offset) {
            this.topic = topic;
            this.partition = partition;
            this.offset = offset;
        }
        
        public String getTopic() { return topic; }
        public int getPartition() { return partition; }
        public long getOffset() { return offset; }
    }
}
