package org.apache.eventmesh.api.storage;

import io.cloudevents.CloudEvent;

public interface EventStorage {
    void store(CloudEvent event) throws Exception;
}