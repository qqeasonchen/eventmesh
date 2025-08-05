package org.apache.eventmesh.api.storage;

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.api.SendCallback;

public interface EventDispatcher {
    void dispatch(CloudEvent event, SendCallback callback) throws Exception;
}