package org.apache.eventmesh.runtime.core.protocol.raw.pulsar;

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.protocol.pulsar.raw.message.RawPulsarMessage;

public interface PulsarRawMessageHandler {
    void handleDirectTransmission(RawPulsarMessage message, String protocolType);
    void handleWithConversion(CloudEvent cloudEvent, String protocolType);
}
 
 
 
 
 
 
 