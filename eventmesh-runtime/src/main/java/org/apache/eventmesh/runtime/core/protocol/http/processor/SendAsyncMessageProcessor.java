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

package org.apache.eventmesh.runtime.core.protocol.http.processor;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.metrics.http.HttpMetrics;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;
import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.metrics.api.MetricsRegistry;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.api.trace.Span;

public class SendAsyncMessageProcessor extends AbstractHttpRequestProcessor {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private static final Logger HTTP_LOGGER = LoggerFactory.getLogger(EventMeshConstants.PROTOCOL_HTTP);

    private static final Logger CMD_LOGGER = LoggerFactory.getLogger(EventMeshConstants.CMD);

    private static final Logger ACL_LOGGER = LoggerFactory.getLogger(EventMeshConstants.ACL);

    private Producer producer;
    private AuthService authService;
    private MetricsRegistry metricsRegistry;

    public SendAsyncMessageProcessor() {
        this.producer = null;
        this.authService = null;
        this.metricsRegistry = null;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        // 1. 获取请求和基础参数
        HttpCommand request = asyncContext.getRequest();
        String localAddress = IPUtils.getLocalAddress();
        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        CMD_LOGGER.info("cmd={}|{}|client2eventMesh|from={}|to={}",
                RequestCode.get(Integer.valueOf(request.getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                remoteAddr, localAddress);

        SendMessageRequestHeader sendMessageRequestHeader = (SendMessageRequestHeader) request.getHeader();
        // TODO: 获取配置参数（如 eventMeshHttpConfiguration）
        // EventMeshHTTPConfiguration eventMeshHttpConfiguration = ...;
        // SendMessageResponseHeader sendMessageResponseHeader = ...;

        // 2. 协议类型判断
        String protocolType = sendMessageRequestHeader.getProtocolType();
        String protocolVersion = sendMessageRequestHeader.getProtocolVersion();
        String targetProtocolType = getTargetProtocolType(request);

        // 3. 判断是否可直接透传
        if (ProtocolPluginFactory.canTransmitDirectly(protocolType, targetProtocolType)) {
            // 直接透传，避免 CloudEvent 转换
            ProtocolTransportObject transmittedMsg = ProtocolPluginFactory.transmitDirectly(protocolType, targetProtocolType, request);
            // TODO: 构造 responseHeader
            handleDirectTransmission(transmittedMsg, ctx, asyncContext, null);
            return;
        }

        // 4. 标准 CloudEvent 转换流程
        ProtocolAdaptor<ProtocolTransportObject> httpCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent event = httpCommandProtocolAdaptor.toCloudEvent(request);

        // 5. 鉴权（如有）
        if (authService != null) {
            // TODO: 兼容新版 AuthService 接口
            // if (!authService.authenticate(event)) {
            //     // 返回鉴权失败响应
            //     return;
            // }
        }

        // 6. 限流（如有）
        if (metricsRegistry != null) {
            // TODO: 兼容新版 MetricsRegistry 接口
            // if (!metricsRegistry.tryAcquire(...)) {
            //     // 返回限流失败响应
            //     return;
            // }
        }

        // 7. 消息校验
        if (!ObjectUtils.allNotNull(event, event.getSource(), event.getSpecVersion())
                || StringUtils.isAnyBlank(event.getId(), event.getType(), event.getSubject())) {
            // TODO: 返回协议头错误响应
            return;
        }
        // 8. 异步发送
        if (producer != null) {
            producer.publish(event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // TODO: 返回成功响应
                }
                @Override
                public void onException(OnExceptionContext context) {
                    // TODO: 返回失败响应
                }
            });
        } else {
            // TODO: 返回 producer 未配置响应
        }
    }

    /**
     * Get target protocol type from request or configuration.
     */
    private String getTargetProtocolType(HttpCommand request) {
        // SendMessageRequestHeader header = (SendMessageRequestHeader) request.getHeader();
        // if (header.getProperties() != null && header.getProperty("target_protocol_type") != null) {
        //     return (String) header.getProperty("target_protocol_type");
        // }
        // return header.getProtocolType();
        return null;
    }

    /**
     * Handle direct transmission without CloudEvent conversion.
     */
    private void handleDirectTransmission(ProtocolTransportObject transmittedMsg, 
                                        ChannelHandlerContext ctx, 
                                        AsyncContext<HttpCommand> asyncContext,
                                        SendMessageResponseHeader responseHeader) throws Exception {
        // 仅做基础透传响应，后续可扩展
        if (transmittedMsg instanceof HttpCommand) {
            HttpCommand response = (HttpCommand) transmittedMsg;
            asyncContext.onComplete(response, null);
        }
    }

    @Override
    public Executor executor() {
        return null;
    }

    private void spanWithException(CloudEvent event, String protocolVersion, EventMeshRetCode retCode) {
        Span excepSpan = TraceUtils.prepareServerSpan(EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
            EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, false);
        TraceUtils.finishSpanWithException(excepSpan, EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
            retCode.getErrMsg(), null);
    }
}

