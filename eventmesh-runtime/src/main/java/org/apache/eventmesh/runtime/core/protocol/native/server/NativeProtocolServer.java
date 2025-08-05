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

package org.apache.eventmesh.runtime.core.protocol.native.server;

import org.apache.eventmesh.runtime.core.protocol.native.NativeProtocolManager;
import org.apache.eventmesh.runtime.core.protocol.native.handler.NativeProtocolHandler;
import org.apache.eventmesh.runtime.core.protocol.native.handler.NativeProtocolHandlerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

/**
 * Native protocol server for handling native client connections.
 * Supports Kafka, Pulsar, and RocketMQ native clients.
 */
public class NativeProtocolServer {

    private static final Logger log = LoggerFactory.getLogger(NativeProtocolServer.class);

    private final NativeProtocolManager protocolManager;
    private final NativeProtocolHandlerFactory handlerFactory;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final ExecutorService executorService;
    
    private Channel serverChannel;
    private volatile boolean started = false;

    public NativeProtocolServer(NativeProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
        this.handlerFactory = new NativeProtocolHandlerFactory(protocolManager);
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * Start the native protocol server
     */
    public void start() throws Exception {
        if (started) {
            log.warn("Native protocol server is already started");
            return;
        }

        log.info("Starting Native Protocol Server");

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                            .addLast(new ByteArrayDecoder())
                            .addLast(new ByteArrayEncoder())
                            .addLast(new NativeProtocolHandler(handlerFactory));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

            // Start server on default port (9092 for Kafka compatibility)
            ChannelFuture future = bootstrap.bind(9092).sync();
            serverChannel = future.channel();
            
            log.info("Native Protocol Server started on port 9092");
            started = true;

            // Wait for server to close
            serverChannel.closeFuture().sync();
        } catch (Exception e) {
            log.error("Failed to start Native Protocol Server", e);
            throw e;
        } finally {
            shutdown();
        }
    }

    /**
     * Start server on specific port
     */
    public void start(int port) throws Exception {
        if (started) {
            log.warn("Native protocol server is already started");
            return;
        }

        log.info("Starting Native Protocol Server on port {}", port);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                            .addLast(new ByteArrayDecoder())
                            .addLast(new ByteArrayEncoder())
                            .addLast(new NativeProtocolHandler(handlerFactory));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            
            log.info("Native Protocol Server started on port {}", port);
            started = true;

            // Wait for server to close
            serverChannel.closeFuture().sync();
        } catch (Exception e) {
            log.error("Failed to start Native Protocol Server on port {}", port, e);
            throw e;
        } finally {
            shutdown();
        }
    }

    /**
     * Start server on specific address and port
     */
    public void start(String host, int port) throws Exception {
        if (started) {
            log.warn("Native protocol server is already started");
            return;
        }

        log.info("Starting Native Protocol Server on {}:{}", host, port);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                            .addLast(new ByteArrayDecoder())
                            .addLast(new ByteArrayEncoder())
                            .addLast(new NativeProtocolHandler(handlerFactory));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

            ChannelFuture future = bootstrap.bind(new InetSocketAddress(host, port)).sync();
            serverChannel = future.channel();
            
            log.info("Native Protocol Server started on {}:{}", host, port);
            started = true;

            // Wait for server to close
            serverChannel.closeFuture().sync();
        } catch (Exception e) {
            log.error("Failed to start Native Protocol Server on {}:{}", host, port, e);
            throw e;
        } finally {
            shutdown();
        }
    }

    /**
     * Shutdown the server
     */
    public void shutdown() {
        if (!started) {
            log.warn("Native protocol server is not started");
            return;
        }

        log.info("Shutting down Native Protocol Server");

        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (Exception e) {
            log.error("Error closing server channel", e);
        }

        try {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            executorService.shutdown();
        } catch (Exception e) {
            log.error("Error shutting down event loop groups", e);
        }

        started = false;
        log.info("Native Protocol Server shutdown completed");
    }

    /**
     * Check if server is started
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Get server statistics
     */
    public NativeProtocolServerStats getStats() {
        return new NativeProtocolServerStats(
            started,
            serverChannel != null && serverChannel.isActive(),
            protocolManager.getStats()
        );
    }

    /**
     * Server statistics
     */
    public static class NativeProtocolServerStats {
        private final boolean isStarted;
        private final boolean isActive;
        private final NativeProtocolManager.NativeProtocolManagerStats managerStats;

        public NativeProtocolServerStats(boolean isStarted, boolean isActive, 
                                       NativeProtocolManager.NativeProtocolManagerStats managerStats) {
            this.isStarted = isStarted;
            this.isActive = isActive;
            this.managerStats = managerStats;
        }

        public boolean isStarted() { return isStarted; }
        public boolean isActive() { return isActive; }
        public NativeProtocolManager.NativeProtocolManagerStats getManagerStats() { return managerStats; }
    }
} 