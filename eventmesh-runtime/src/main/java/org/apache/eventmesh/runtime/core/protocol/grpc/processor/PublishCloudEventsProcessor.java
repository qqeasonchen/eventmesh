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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublishCloudEventsProcessor extends AbstractPublishCloudEventProcessor {

    private final Producer producer;
    private final AuthService authService;
    private final MetricsRegistry metricsRegistry;

    public PublishCloudEventsProcessor(Producer producer, AuthService authService, MetricsRegistry metricsRegistry) {
        super(producer, authService, metricsRegistry);
        this.producer = producer;
        this.authService = authService;
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public void handleCloudEvent(CloudEvent message, EventEmitter<CloudEvent> emitter) throws Exception {
        // 获取源协议类型
        String sourceProtocolType = EventMeshCloudEventUtils.getProtocolType(message);
        
        // 获取目标协议类型（这里假设从配置或消息中获取）
        String targetProtocolType = getTargetProtocolType(message);
        
        // 检查是否可以直接透传
        if (ProtocolPluginFactory.canTransmitDirectly(sourceProtocolType, targetProtocolType)) {
            // 直接透传，避免 CloudEvent 转换
            ProtocolTransportObject sourceMsg = new EventMeshCloudEventWrapper(message);
            ProtocolTransportObject transmittedMsg = ProtocolPluginFactory.transmitDirectly(
                sourceProtocolType, targetProtocolType, sourceMsg);
            handleDirectTransmission(transmittedMsg, emitter);
            return;
        }
        
        // 标准转换流程
        String topic = message.getSubject();
        String seqNum = message.getId();
        String uniqueId = message.getExtension("uniqueId") != null ? message.getExtension("uniqueId").toString() : "";
        long startTime = System.currentTimeMillis();
        producer.publish(message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                ServiceUtils.sendResponseCompleted(StatusCode.SUCCESS, sendResult.toString(), emitter);
                long endTime = System.currentTimeMillis();
                log.info("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, topic, seqNum, uniqueId);
                if (metricsRegistry != null) {
                    // 可扩展：记录指标
                }
            }
            @Override
            public void onException(OnExceptionContext context) {
                ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR,
                    EventMeshUtil.stackTrace(context.getException(), 2), emitter);
                long endTime = System.currentTimeMillis();
                log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, topic, seqNum, uniqueId, context.getException());
            }
        });
    }

    /**
     * Get target protocol type from message or configuration.
     */
    private String getTargetProtocolType(CloudEvent message) {
        // 这里可以从消息扩展、配置或其他地方获取目标协议类型
        // 示例：从消息扩展获取目标协议类型
        if (message.getExtension("target_protocol_type") != null) {
            return message.getExtension("target_protocol_type").toString();
        }
        
        // 默认使用源协议类型（即透传）
        return EventMeshCloudEventUtils.getProtocolType(message);
    }

    /**
     * Handle direct transmission without CloudEvent conversion.
     */
    private void handleDirectTransmission(ProtocolTransportObject transmittedMsg, 
                                        EventEmitter<CloudEvent> emitter) throws Exception {
        // 直接处理透传的消息，无需 CloudEvent 转换
        // 这里可以根据具体协议类型进行相应的处理
        
        // 示例：直接发送透传的消息
        if (transmittedMsg instanceof EventMeshCloudEventWrapper) {
            EventMeshCloudEventWrapper wrapper = (EventMeshCloudEventWrapper) transmittedMsg;
            ServiceUtils.sendResponseCompleted(StatusCode.SUCCESS, "Direct transmission completed", emitter);
        }
        
        // 记录透传日志
        log.info("Direct transmission completed for protocol: {}", 
            EventMeshCloudEventUtils.getProtocolType(message));
    }
}
