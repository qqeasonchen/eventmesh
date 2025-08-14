package org.apache.eventmesh.runtime.core.protocol.raw.kafka;

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.protocol.kafka.raw.message.RawKafkaMessage;

public interface RawKafkaMessageHandler {
    void handleDirectTransmission(RawKafkaMessage message, String protocolType);
    void handleWithConversion(CloudEvent cloudEvent, String protocolType);
}
 
 
 
 
 
 
 