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

package org.apache.eventmesh.runtime.boot;

import static org.apache.eventmesh.common.Constants.GRPC;
import static org.apache.eventmesh.common.Constants.HTTP;
import static org.apache.eventmesh.common.Constants.TCP;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.AssertUtils;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.core.protocol.http.producer.ProducerTopicManager;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.runtime.metrics.EventMeshMetricsManager;
import org.apache.eventmesh.runtime.metrics.MetricsManager;
import org.apache.eventmesh.runtime.storage.StorageResource;
import org.apache.eventmesh.runtime.trace.Trace;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshServer {

    @Getter
    private final Acl acl;

    @Getter
    @Setter
    private MetaStorage metaStorage;

    @Getter
    private static Trace trace;

    private final StorageResource storageResource;

    @Getter
    private ServiceState serviceState;

    @Getter
    private ProducerTopicManager producerTopicManager;

    @Getter
    private ProducerManager producerManager;

    @Getter
    private final CommonConfiguration configuration;

    //  private transient ClientManageController clientManageController;

    private static final List<EventMeshBootstrap> BOOTSTRAP_LIST = new CopyOnWriteArrayList<>();

    private static final String SERVER_STATE_MSG = "server state:{}";
    private static final String HTTP = "HTTP";
    private static final String TCP = "TCP";
    private static final String GRPC = "GRPC";
    private static final String RAW = "RAW";

    private static final ConfigService configService = ConfigService.getInstance();

    private static final Logger log = LoggerFactory.getLogger(EventMeshServer.class);

    @Getter
    private EventMeshTCPServer eventMeshTCPServer = null;

    @Getter
    private EventMeshHTTPServer eventMeshHTTPServer = null;

    @Getter
    private EventMeshGrpcServer eventMeshGrpcServer = null;

    @Getter
    private EventMeshAdminServer eventMeshAdminServer = null;

    private EventMeshMetricsManager eventMeshMetricsManager;

    public EventMeshMetricsManager getEventMeshMetricsManager() {
        return eventMeshMetricsManager;
    }

    // Manual getter methods since Lombok @Getter might not be working properly
    public Acl getAcl() {
        return acl;
    }

    public MetaStorage getMetaStorage() {
        return metaStorage;
    }

    public CommonConfiguration getConfiguration() {
        return configuration;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public EventMeshGrpcServer getEventMeshGrpcServer() {
        return eventMeshGrpcServer;
    }

    public EventMeshAdminServer getEventMeshAdminServer() {
        return eventMeshAdminServer;
    }

    public ProducerManager getProducerManager() {
        return producerManager;
    }

    public EventMeshServer() {

        // Initialize configuration
        this.configuration = configService.buildConfigInstance(CommonConfiguration.class);
        AssertUtils.notNull(this.configuration, "configuration is null");

        // Initialize acl, registry, trace and storageResource
        if (this.configuration.isEventMeshServerSecurityEnable()) {
            this.acl = Acl.getInstance(this.configuration.getEventMeshSecurityPluginType());
        } else {
            this.acl = null;
            log.info("Security is disabled, skipping ACL plugin loading");
        }
        if (this.configuration.isEventMeshServerMetaStorageEnable()) {
            this.metaStorage = MetaStorage.getInstance(this.configuration.getEventMeshMetaStoragePluginType());
        } else {
            this.metaStorage = null;
            log.info("MetaStorage is disabled, skipping MetaStorage plugin loading");
        }
        trace = Trace.getInstance(this.configuration.getEventMeshTracePluginType(), this.configuration.isEventMeshServerTraceEnable());
        this.storageResource = StorageResource.getInstance(this.configuration.getEventMeshStoragePluginType());

        // Initialize BOOTSTRAP_LIST based on protocols provided in configuration
        final List<String> provideServerProtocols = configuration.getEventMeshProvideServerProtocols();
        for (String provideServerProtocol : provideServerProtocols) {
            switch (provideServerProtocol.toUpperCase()) {
                case HTTP:
                    BOOTSTRAP_LIST.add(new EventMeshHttpBootstrap(this));
                    break;
                case TCP:
                    BOOTSTRAP_LIST.add(new EventMeshTcpBootstrap(this));
                    break;
                case GRPC:
                    BOOTSTRAP_LIST.add(new EventMeshGrpcBootstrap(this));
                    break;
                case RAW:
                    BOOTSTRAP_LIST.add(new EventMeshRawBootstrap(this));
                    break;
                default: // nothing to do
            }
        }

        // If no protocols are provided, initialize BOOTSTRAP_LIST with default protocols
        if (BOOTSTRAP_LIST.isEmpty()) {
            BOOTSTRAP_LIST.add(new EventMeshTcpBootstrap(this));
        }

        // HTTP Admin Server enabled only if explicitly configured via admin port > 0
        try {
            org.apache.eventmesh.runtime.configuration.EventMeshAdminConfiguration adminConf =
                ConfigService.getInstance().buildConfigInstance(
                    org.apache.eventmesh.runtime.configuration.EventMeshAdminConfiguration.class);
            if (adminConf != null && adminConf.getEventMeshServerAdminPort() > 0) {
                BOOTSTRAP_LIST.add(new EventMeshAdminBootstrap(this));
            } else {
                log.info("Admin HTTP server disabled (admin port <= 0)");
            }
        } catch (Throwable t) {
            // Fallback to disabled on config access issues
            log.warn("Admin HTTP server disabled due to configuration error", t);
        }

        List<String> metricsPluginTypes = configuration.getEventMeshMetricsPluginType();
        if (CollectionUtils.isNotEmpty(metricsPluginTypes)) {
            List<MetricsRegistry> metricsRegistries = metricsPluginTypes.stream().map(metric -> MetricsPluginFactory.getMetricsRegistry(metric))
                .collect(Collectors.toList());
            eventMeshMetricsManager = new EventMeshMetricsManager(metricsRegistries);
        }

        this.producerManager = new org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager(this);
    }

    public void init() throws Exception {
        storageResource.init();
        if (configuration.isEventMeshServerSecurityEnable()) {
            acl.init();
        }
        if (configuration.isEventMeshServerMetaStorageEnable()) {
            metaStorage.init();
        }
        if (configuration.isEventMeshServerTraceEnable()) {
            trace.init();
        }

        // server init
        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.init();
            if (eventMeshBootstrap instanceof EventMeshTcpBootstrap) {
                eventMeshTCPServer = ((EventMeshTcpBootstrap) eventMeshBootstrap).getEventMeshTcpServer();
            }
            if (eventMeshBootstrap instanceof EventMeshHttpBootstrap) {
                eventMeshHTTPServer = ((EventMeshHttpBootstrap) eventMeshBootstrap).getEventMeshHttpServer();
            }
            if (eventMeshBootstrap instanceof EventMeshGrpcBootstrap) {
                eventMeshGrpcServer = ((EventMeshGrpcBootstrap) eventMeshBootstrap).getEventMeshGrpcServer();
            }
            if (eventMeshBootstrap instanceof EventMeshAdminBootstrap) {
                eventMeshAdminServer = ((EventMeshAdminBootstrap) eventMeshBootstrap).getEventMeshAdminServer();
            }
        }

        if (Objects.nonNull(eventMeshTCPServer)) {
            MetricsManager metricsManager = eventMeshTCPServer.getEventMeshTcpMetricsManager();
            addMetricsManagerAndMetrics(metricsManager);
        }

        if (Objects.nonNull(eventMeshGrpcServer)) {
            MetricsManager metricsManager = eventMeshGrpcServer.getEventMeshGrpcMetricsManager();
            addMetricsManagerAndMetrics(metricsManager);
        }

        if (Objects.nonNull(eventMeshHTTPServer)) {
            MetricsManager metricsManager = eventMeshHTTPServer.getEventMeshHttpMetricsManager();
            addMetricsManagerAndMetrics(metricsManager);
        }

        if (Objects.nonNull(eventMeshMetricsManager)) {
            eventMeshMetricsManager.init();
        }

        producerTopicManager = new ProducerTopicManager(this);
        producerTopicManager.init();

        serviceState = ServiceState.INITED;
        log.info(SERVER_STATE_MSG, serviceState);
    }

    private void addMetricsManagerAndMetrics(MetricsManager metricsManager) {
        if (Objects.nonNull(metricsManager)) {
            this.eventMeshMetricsManager.addMetricManager(metricsManager);
            this.eventMeshMetricsManager.addMetrics(metricsManager.getMetrics());
        }
    }

    public void start() throws Exception {
        if (Objects.nonNull(configuration)) {
            if (configuration.isEventMeshServerSecurityEnable()) {
                acl.start();
            }
            // registry start
            if (configuration.isEventMeshServerMetaStorageEnable()) {
                metaStorage.start();
            }
        }
        // server start
        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.start();
        }

        producerTopicManager.start();

        serviceState = ServiceState.RUNNING;
        log.info(SERVER_STATE_MSG, serviceState);
    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPPING;
        log.info(SERVER_STATE_MSG, serviceState);

        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.shutdown();
        }

        if (configuration != null && configuration.isEventMeshServerMetaStorageEnable()) {
            metaStorage.shutdown();
        }

        storageResource.release();

        if (configuration != null && configuration.isEventMeshServerSecurityEnable()) {
            acl.shutdown();
        }

        if (configuration != null && configuration.isEventMeshServerTraceEnable()) {
            trace.shutdown();
        }
        producerTopicManager.shutdown();
        ConfigurationContextUtil.clear();

        serviceState = ServiceState.STOPPED;
        log.info(SERVER_STATE_MSG, serviceState);
    }
}
