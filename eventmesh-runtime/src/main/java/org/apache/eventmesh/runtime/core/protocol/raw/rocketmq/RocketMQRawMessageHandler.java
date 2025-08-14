package org.apache.eventmesh.runtime.core.protocol.raw.rocketmq;

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.protocol.rocketmq.raw.message.RawRocketMQMessage;

public interface RocketMQRawMessageHandler {
    void handleDirectTransmission(RawRocketMQMessage message, String protocolType);
    void handleWithConversion(CloudEvent cloudEvent, String protocolType);
}
 
 
 
 
 
 
 