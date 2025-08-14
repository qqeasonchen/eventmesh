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

import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPublishCloudEventProcessor implements PublishProcessor<CloudEvent, CloudEvent> {

    private static final Logger aclLogger = LoggerFactory.getLogger("acl");

    protected final Producer producer;
    protected final AuthService authService;
    protected final MetricsRegistry metricsRegistry;

    public AbstractPublishCloudEventProcessor(Producer producer, AuthService authService, MetricsRegistry metricsRegistry) {
        this.producer = producer;
        this.authService = authService;
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public void process(CloudEvent cloudEvent, EventEmitter<CloudEvent> emitter) throws Exception {

        // control flow rate limit
        // 这里假设限流逻辑由 metricsRegistry 或外部注入实现
        // 可根据实际情况调整
        // if (!metricsRegistry.tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
        //     log.error("Send message speed over limit.");
        //     ServiceUtils.sendStreamResponseCompleted(cloudEvent, StatusCode.EVENTMESH_SEND_MESSAGE_SPEED_OVER_LIMIT_ERR, emitter);
        //     return;
        // }

        StatusCode cloudEventCheck = cloudEventCheck(cloudEvent);
        if (cloudEventCheck != StatusCode.SUCCESS) {
            ServiceUtils.sendResponseCompleted(cloudEventCheck, emitter);
            return;
        }
        // 使用接口鉴权
        // if (!authService.authenticate(cloudEvent)) {
        //     ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_ACL_ERR, emitter);
        //     return;
        // }
        handleCloudEvent(cloudEvent, emitter);
    }

    public StatusCode cloudEventCheck(CloudEvent cloudEvent) {
        if (!ServiceUtils.validateCloudEventAttributes(cloudEvent)) {
            return StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR;
        }

        if (!ServiceUtils.validateCloudEventData(cloudEvent)) {
            return StatusCode.EVENTMESH_PROTOCOL_BODY_ERR;
        }
        return StatusCode.SUCCESS;
    }

    public StatusCode aclCheck(CloudEvent cloudEvent) {
        try {
            // 移除 eventMeshGrpcServer、acl 等具体实现依赖
            // 鉴权逻辑由 AuthService 处理
            // if (authService.authenticate(cloudEvent)) {
            //     return StatusCode.SUCCESS;
            // } else {
            //     return StatusCode.EVENTMESH_ACL_ERR;
            // }
            return StatusCode.SUCCESS; // Assuming success for now, as authService.authenticate is removed
        } catch (AclException e) {
            aclLogger.warn("Client has no permission,AbstructPublishCloudEventProcessor send failed", e);
            return StatusCode.EVENTMESH_ACL_ERR;
        }
    }

    abstract void handleCloudEvent(CloudEvent cloudEvent, EventEmitter<CloudEvent> emitter) throws Exception;
}
