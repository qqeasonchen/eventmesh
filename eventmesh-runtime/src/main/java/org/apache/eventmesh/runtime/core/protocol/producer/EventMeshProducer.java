package org.apache.eventmesh.runtime.core.protocol.producer;

import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.Constants;
import java.util.UUID;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.metrics.EventMeshMetricsManager;

import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class EventMeshProducer implements Producer {

    private final String producerGroup;
    private final EventMeshServer eventMeshServer;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final EventMeshMetricsManager metricsManager;

    public EventMeshProducer(String producerGroup, EventMeshServer eventMeshServer) {
        this.producerGroup = producerGroup;
        this.eventMeshServer = eventMeshServer;
        this.metricsManager = eventMeshServer.getEventMeshMetricsManager();
    }

    @Override
    public void init(Properties properties) throws Exception {
        log.info("EventMeshProducer init, producerGroup: {}", producerGroup);
    }

    @Override
    public void start() throws Exception {
        if (started.compareAndSet(false, true)) {
            log.info("EventMeshProducer started, producerGroup: {}", producerGroup);
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (started.compareAndSet(true, false)) {
            log.info("EventMeshProducer shutdown, producerGroup: {}", producerGroup);
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isClosed() {
        return !started.get();
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        if (!isStarted()) {
            throw new IllegalStateException("EventMeshProducer is not started");
        }

        try {
            // 记录发送消息的指标
            if (metricsManager != null) {
                metricsManager.recordSendMsg();
            }

            // 这里应该调用实际的存储插件来发送消息
            // 暂时使用简单的日志记录
            String topic = cloudEvent.getSubject();
            String content = cloudEvent.getData() == null ? "" : 
                new String(cloudEvent.getData().toBytes(), Constants.DEFAULT_CHARSET);
            
            log.info("EventMeshProducer publish message, topic: {}, content: {}", topic, content);
            
            // 模拟成功发送
            if (sendCallback != null) {
                sendCallback.onSuccess(new SendResult());
            }
            
        } catch (Exception e) {
            log.error("EventMeshProducer publish failed", e);
            if (sendCallback != null) {
                sendCallback.onException(new OnExceptionContext(e));
            }
            throw e;
        }
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        try {
            publish(cloudEvent, null);
        } catch (Exception e) {
            log.error("EventMeshProducer sendOneway failed", e);
        }
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        if (!isStarted()) {
            throw new IllegalStateException("EventMeshProducer is not started");
        }

        try {
            log.info("EventMeshProducer request message, topic: {}", cloudEvent.getSubject());
            
            // 这里应该实现请求-响应逻辑
            // 暂时使用简单的日志记录
            if (rrCallback != null) {
                // 模拟响应
                rrCallback.onSuccess(cloudEvent);
            }
            
        } catch (Exception e) {
            log.error("EventMeshProducer request failed", e);
            if (rrCallback != null) {
                rrCallback.onException(e);
            }
            throw e;
        }
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        if (!isStarted()) {
            throw new IllegalStateException("EventMeshProducer is not started");
        }

        try {
            log.info("EventMeshProducer reply message, topic: {}", cloudEvent.getSubject());
            
            // 这里应该实现回复逻辑
            if (sendCallback != null) {
                sendCallback.onSuccess(new SendResult());
            }
            
            return true;
        } catch (Exception e) {
            log.error("EventMeshProducer reply failed", e);
            if (sendCallback != null) {
                sendCallback.onException(new OnExceptionContext(e));
            }
            return false;
        }
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        log.info("EventMeshProducer checkTopicExist, topic: {}", topic);
        // 这里应该检查主题是否存在
    }

    @Override
    public void setExtFields() {
        log.info("EventMeshProducer setExtFields");
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public EventMeshServer getEventMeshServer() {
        return eventMeshServer;
    }

    // 内部类用于回调
    public static class SendResult {
        private String messageId;
        private String topic;
        private long sendTime;

        public SendResult() {
            this.messageId = UUID.randomUUID().toString();
            this.sendTime = System.currentTimeMillis();
        }

        public String getMessageId() {
            return messageId;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public long getSendTime() {
            return sendTime;
        }
    }

    public static class OnExceptionContext {
        private final Exception exception;

        public OnExceptionContext(Exception exception) {
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }
    }
}
