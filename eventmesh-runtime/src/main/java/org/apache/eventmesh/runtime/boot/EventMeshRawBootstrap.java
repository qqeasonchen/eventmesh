package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.runtime.core.protocol.raw.kafka.RawKafkaConnector;
import org.apache.eventmesh.runtime.core.protocol.raw.kafka.RawKafkaMessageHandler;
import org.apache.eventmesh.runtime.core.protocol.raw.RawConnector;
import org.apache.eventmesh.runtime.core.protocol.raw.RawMessageHandler;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.protocol.kafka.wire.message.KafkaMessage;
import org.apache.eventmesh.protocol.kafka.wire.message.KafkaProtocolHandler;
import org.apache.eventmesh.protocol.kafka.wire.message.KafkaMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

/**
 * Bootstrap for RAW protocol server
 */
public class EventMeshRawBootstrap implements EventMeshBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EventMeshRawBootstrap.class);

    private final EventMeshServer eventMeshServer;
    private RawConnector rawConnector;
    private KafkaProtocolHandler kafkaProtocolHandler;
    private boolean isStarted = false;
    
    // Network server components
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private int port = 19092; // Default RAW Kafka port

    public EventMeshRawBootstrap(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
    }

    @Override
    public void init() throws Exception {
        log.info("==================EventMeshRawBootstrap Initialing==================");
        
        // Initialize RAW Kafka connector
        // Get a default producer from ProducerManager
        Producer producer = eventMeshServer.getProducerManager().getEventMeshProducer("default");
        RawMessageHandler messageHandler = new RawMessageHandler() {
            @Override
            public void handleMessage(org.apache.eventmesh.common.protocol.ProtocolTransportObject message) {
                log.info("Handling RAW message: {}", message);
                // TODO: Implement message handling logic
            }
            
            @Override
            public void handleCloudEvent(io.cloudevents.CloudEvent cloudEvent) {
                log.info("Handling CloudEvent: {}", cloudEvent.getSubject());
                // TODO: Implement message handling logic
            }
        };
        
        this.rawConnector = new RawKafkaConnector(producer, messageHandler);
        
        // Initialize Kafka protocol handler
        this.kafkaProtocolHandler = new KafkaProtocolHandler(producer);
        
        // Initialize network server components
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
        
        log.info("==================EventMeshRawBootstrap Initialed==================");
    }

    @Override
    public void start() throws Exception {
        log.info("==================EventMeshRawBootstrap Starting==================");
        
        if (rawConnector != null) {
            rawConnector.start();
        }
        
        // Start network server
        startNetworkServer();
        
        isStarted = true;
        log.info("==================EventMeshRawBootstrap Started==================");
    }

    @Override
    public void shutdown() throws Exception {
        log.info("==================EventMeshRawBootstrap Shutdown==================");
        
        // Shutdown network server
        if (serverChannel != null) {
            serverChannel.close().sync();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        if (rawConnector != null) {
            rawConnector.shutdown();
        }
        
        isStarted = false;
        log.info("==================EventMeshRawBootstrap Shutdown==================");
    }

    private void startNetworkServer() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                                                    protected void initChannel(SocketChannel ch) throws Exception {
                                    ch.pipeline()
                                        .addLast(new KafkaMessageDecoder())
                                        .addLast(new KafkaMessageEncoder())
                                        .addLast(new KafkaServerHandler(kafkaProtocolHandler));
                                }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        ChannelFuture future = bootstrap.bind(new InetSocketAddress(port)).sync();
        serverChannel = future.channel();
        log.info("RAW Kafka server started on port {}", port);
    }

    public boolean isStarted() {
        return isStarted;
    }
    
        /**
     * Decoder for Kafka wire protocol messages
     */
    private static class KafkaMessageDecoder extends ByteToMessageDecoder {
                        @Override
                protected void decode(ChannelHandlerContext ctx, ByteBuf in, java.util.List<Object> out) throws Exception {
                    System.out.println("DEBUG: KafkaMessageDecoder.decode called, readable bytes: " + in.readableBytes());
                    
                    if (in.readableBytes() < 4) {
                        System.out.println("DEBUG: Not enough bytes for message length");
                        return;
                    }

                    in.markReaderIndex();
                    int messageLength = in.readInt();
                    System.out.println("DEBUG: Message length from buffer: " + messageLength);

                    if (in.readableBytes() < messageLength) {
                        System.out.println("DEBUG: Not enough bytes for message body, readable: " + in.readableBytes() + ", needed: " + messageLength);
                        in.resetReaderIndex();
                        return;
                    }

                    // Read the entire message
                    byte[] messageData = new byte[messageLength];
                    in.readBytes(messageData);
                    System.out.println("DEBUG: Read message data, size: " + messageData.length);

                    // Decode Kafka message
                    ByteBuffer buffer = ByteBuffer.wrap(messageData);
                    KafkaMessage kafkaMessage = KafkaMessageCodec.decode(buffer);

                    if (kafkaMessage != null) {
                        System.out.println("DEBUG: Successfully decoded Kafka message, adding to output");
                        out.add(kafkaMessage);
                    } else {
                        System.out.println("DEBUG: Failed to decode Kafka message");
                    }
                }
    }

    /**
     * Encoder for Kafka wire protocol messages
     */
    private static class KafkaMessageEncoder extends MessageToByteEncoder<KafkaMessage> {
        @Override
        protected void encode(ChannelHandlerContext ctx, KafkaMessage msg, ByteBuf out) throws Exception {
            ByteBuffer buffer = KafkaMessageCodec.encode(msg);
            out.writeBytes(buffer);
        }
    }

    /**
     * Handler for Kafka wire protocol server
     */
    private static class KafkaServerHandler extends ChannelInboundHandlerAdapter {
        private final KafkaProtocolHandler kafkaProtocolHandler;

        public KafkaServerHandler(KafkaProtocolHandler kafkaProtocolHandler) {
            this.kafkaProtocolHandler = kafkaProtocolHandler;
        }

                            @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        System.out.println("DEBUG: Received message in KafkaServerHandler: " + msg.getClass().getName());
                        
                        if (msg instanceof KafkaMessage) {
                            KafkaMessage request = (KafkaMessage) msg;
                            log.info("Received Kafka request: {}", request);
                            System.out.println("DEBUG: Processing Kafka request: " + request);

                            // Handle the request and send response
                            KafkaMessage response = kafkaProtocolHandler.handleRequest(request);
                            if (response != null) {
                                System.out.println("DEBUG: Sending Kafka response: " + response);
                                ctx.writeAndFlush(response);
                            } else {
                                System.out.println("DEBUG: No response generated for request");
                            }
                        } else {
                            System.out.println("DEBUG: Received non-Kafka message: " + msg);
                            log.warn("Received non-Kafka message: {}", msg);
                        }
                    }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("Kafka client connected: {}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("Kafka client disconnected: {}", ctx.channel().remoteAddress());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Error in Kafka server handler", cause);
            ctx.close();
        }
    }
}
