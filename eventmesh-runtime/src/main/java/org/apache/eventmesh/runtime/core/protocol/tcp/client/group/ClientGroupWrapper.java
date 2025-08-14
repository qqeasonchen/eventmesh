package org.apache.eventmesh.runtime.core.protocol.tcp.client.group;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

public class ClientGroupWrapper {
    private final String sysId;
    private String group;
    private final ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();

    public ClientGroupWrapper(String sysId, String group) {
        this.sysId = sysId;
        this.group = group;
    }

    public String getSysId() {
        return sysId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public ConcurrentHashMap<String, Session> getSessionMap() {
        return sessionMap;
    }

    // 补全缺失方法
    public java.util.concurrent.atomic.AtomicBoolean getProducerStarted() { throw new UnsupportedOperationException(); }
    public void startClientGroupProducer() { throw new UnsupportedOperationException(); }
    public boolean addGroupProducerSession(Session session) { throw new UnsupportedOperationException(); }
    public java.util.concurrent.atomic.AtomicBoolean getInited4Broadcast() { throw new UnsupportedOperationException(); }
    public void initClientGroupBroadcastConsumer() { throw new UnsupportedOperationException(); }
    public java.util.concurrent.atomic.AtomicBoolean getInited4Persistent() { throw new UnsupportedOperationException(); }
    public void initClientGroupPersistentConsumer() { throw new UnsupportedOperationException(); }
    public boolean addGroupConsumerSession(Session session) { throw new UnsupportedOperationException(); }
    public java.util.concurrent.atomic.AtomicBoolean getStarted4Persistent() { throw new UnsupportedOperationException(); }
    public void startClientGroupPersistentConsumer() { throw new UnsupportedOperationException(); }
    public java.util.concurrent.atomic.AtomicBoolean getStarted4Broadcast() { throw new UnsupportedOperationException(); }
    public void startClientGroupBroadcastConsumer() { throw new UnsupportedOperationException(); }
    public void removeGroupConsumerSession(Session session) { throw new UnsupportedOperationException(); }
    public void removeGroupProducerSession(Session session) { throw new UnsupportedOperationException(); }
    public void removeSubscription(Object item, Session session) { throw new UnsupportedOperationException(); }
    public boolean hasSubscription(String topic) { throw new UnsupportedOperationException(); }
    public void unsubscribe(Object item) { throw new UnsupportedOperationException(); }
    public java.util.Set<Session> getGroupConsumerSessions() { return java.util.Collections.emptySet(); }
    public java.util.Set<Session> getGroupProducerSessions() { return java.util.Collections.emptySet(); }
    public org.apache.eventmesh.runtime.core.protocol.tcp.client.group.dispatch.DownstreamDispatchStrategy getDownstreamDispatchStrategy() { return null; }
    public void shutdownBroadCastConsumer() { throw new UnsupportedOperationException(); }
    public void shutdownPersistentConsumer() { throw new UnsupportedOperationException(); }
    public void shutdownProducer() { throw new UnsupportedOperationException(); }
    public java.util.Map<String, java.util.Map<String, Session>> getTopic2sessionInGroupMapping() { return java.util.Collections.emptyMap(); }
    public Object getEventMeshTcpMetricsManager() {
        return new Object() {
            public void eventMesh2clientMsgNumIncrement(String addr) {}
        };
    }
    public Object getTcpRetryer() {
        return new Object() {
            public Object newTimeout(Object ctx, long delay, java.util.concurrent.TimeUnit unit) { return null; }
        };
    }
    public void subscribe(Object item) { throw new UnsupportedOperationException(); }
    public void addSubscription(Object item, Session session) { throw new UnsupportedOperationException(); }
}

