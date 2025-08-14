/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.grpc.processor;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.metrics.api.MetricsRegistry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestCloudEventProcessor extends AbstractPublishCloudEventProcessor {

    private final Producer producer;
    private final AuthService authService;
    private final MetricsRegistry metricsRegistry;

    public RequestCloudEventProcessor(Producer producer, AuthService authService, MetricsRegistry metricsRegistry) {
        super(producer, authService, metricsRegistry);
        this.producer = producer;
        this.authService = authService;
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public void handleCloudEvent(CloudEvent message, EventEmitter<CloudEvent> emitter) throws Exception {
        String protocolType = EventMeshCloudEventUtils.getProtocolType(message);
        ProtocolAdaptor<ProtocolTransportObject> grpcCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        io.cloudevents.CloudEvent cloudEvent = grpcCommandProtocolAdaptor.toCloudEvent(new EventMeshCloudEventWrapper(message));

        String seqNum = EventMeshCloudEventUtils.getSeqNum(message);
        String uniqueId = EventMeshCloudEventUtils.getUniqueId(message);
        String topic = EventMeshCloudEventUtils.getSubject(message);
        String producerGroup = EventMeshCloudEventUtils.getProducerGroup(message);
        int ttl = Integer.parseInt(EventMeshCloudEventUtils.getTtl(message));
        
        // 由于缺少 eventMeshGrpcServer，以下逻辑需根据 producer/metricsRegistry 适配
        // TODO: 需要根据新架构补充 request-reply 逻辑
        throw new UnsupportedOperationException("gRPC RequestCloudEventProcessor request-reply is not supported: missing EventMeshGrpcServer context");
    }
}
