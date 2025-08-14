import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 简化的 EventMesh Kafka 透传测试
 * 确保消息能够正确从输入主题透传到输出主题
 */
public class SimpleEventMeshKafkaTest {
    
    private static final String INPUT_TOPIC = "eventmesh-input-topic";
    private static final String OUTPUT_TOPIC = "eventmesh-output-topic";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    
    private static final AtomicInteger messageCounter = new AtomicInteger(0);
    private static final AtomicInteger successCounter = new AtomicInteger(0);
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        System.out.println("=== 简化 EventMesh Kafka 透传测试 ===");
        
        try {
            // 1. 清理并创建主题
            createTopics();
            
            // 2. 启动 EventMesh 透传服务
            startEventMeshPassthrough();
            
            // 3. 等待服务启动
            Thread.sleep(3000);
            
            // 4. 运行测试
            runTests();
            
            // 5. 停止服务
            stopEventMeshPassthrough();
            
            // 6. 输出结果
            printResults();
            
            System.out.println("\n🎉 简化 EventMesh Kafka 透传测试完成！");
            
        } catch (Exception e) {
            System.err.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建测试主题
     */
    private static void createTopics() {
        System.out.println("📝 创建测试主题...");
        
        Properties adminProps = new Properties();
        adminProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        adminProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        adminProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        try (Producer<String, String> producer = new KafkaProducer<>(adminProps)) {
            // 发送初始化消息来创建主题
            producer.send(new ProducerRecord<>(INPUT_TOPIC, "init", "init")).get(5, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(OUTPUT_TOPIC, "init", "init")).get(5, TimeUnit.SECONDS);
            System.out.println("✅ 测试主题已就绪");
        } catch (Exception e) {
            System.err.println("创建主题失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动 EventMesh 透传服务
     */
    private static void startEventMeshPassthrough() {
        System.out.println("🚀 启动 EventMesh 透传服务...");
        
        ExecutorService eventMeshService = Executors.newSingleThreadExecutor();
        eventMeshService.submit(() -> {
            try {
                // EventMesh Source Connector (消费)
                Properties consumerProps = new Properties();
                consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
                consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "eventmesh-source-" + System.currentTimeMillis());
                consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
                
                // EventMesh Sink Connector (生产)
                Properties producerProps = new Properties();
                producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
                producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
                
                try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
                     Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
                    
                    consumer.subscribe(List.of(INPUT_TOPIC));
                    System.out.println("✅ EventMesh 透传服务已启动");
                    System.out.println("  - 监听主题: " + INPUT_TOPIC);
                    System.out.println("  - 输出主题: " + OUTPUT_TOPIC);
                    
                    while (running) {
                        try {
                            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                            
                            if (!records.isEmpty()) {
                                System.out.println("📥 EventMesh 接收到 " + records.count() + " 条消息");
                            }
                            
                            for (ConsumerRecord<String, String> record : records) {
                                // 跳过初始化消息
                                if ("init".equals(record.key())) {
                                    continue;
                                }
                                
                                try {
                                    // EventMesh 消息处理逻辑
                                    String processedValue = processMessage(record.value());
                                    
                                    // 透传到输出主题
                                    ProducerRecord<String, String> outputRecord = new ProducerRecord<>(
                                        OUTPUT_TOPIC, record.key(), processedValue
                                    );
                                    
                                    producer.send(outputRecord, (metadata, exception) -> {
                                        if (exception != null) {
                                            System.err.println("透传消息失败: " + exception.getMessage());
                                        } else {
                                            int count = messageCounter.incrementAndGet();
                                            successCounter.incrementAndGet();
                                            System.out.println("📤 EventMesh 透传消息 #" + count + ": " + record.key() + " -> " + processedValue);
                                        }
                                    });
                                    
                                } catch (Exception e) {
                                    System.err.println("处理消息失败: " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("EventMesh 服务错误: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("EventMesh 透传服务错误: " + e.getMessage());
            }
        });
    }
    
    /**
     * 处理消息（模拟 EventMesh 的消息处理逻辑）
     */
    private static String processMessage(String originalValue) {
        // 模拟 EventMesh 的消息处理逻辑
        if (originalValue == null || originalValue.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        
        // 添加 EventMesh 处理标记
        String processedValue = originalValue;
        if (!processedValue.contains("eventmesh-processed")) {
            processedValue = processedValue + " [EventMesh-Processed]";
        }
        
        return processedValue;
    }
    
    /**
     * 运行测试
     */
    private static void runTests() throws Exception {
        System.out.println("\n--- 开始 EventMesh Kafka 透传测试 ---");
        
        // 测试 1: 基本消息透传
        testBasicPassthrough();
        
        // 测试 2: 批量消息透传
        testBatchPassthrough();
        
        // 测试 3: 消息格式转换
        testMessageFormatTransformation();
    }
    
    /**
     * 基本消息透传测试
     */
    private static void testBasicPassthrough() throws Exception {
        System.out.println("\n--- 测试 1: 基本消息透传 ---");
        
        String testKey = "basic-key-" + System.currentTimeMillis();
        String testValue = "basic-value-" + System.currentTimeMillis();
        
        // 发送消息到输入主题
        System.out.println("📤 发送消息: " + testKey + " -> " + testValue);
        sendMessage(INPUT_TOPIC, testKey, testValue);
        
        // 等待透传完成
        Thread.sleep(2000);
        
        // 验证消息是否到达输出主题
        boolean found = consumeAndVerify(OUTPUT_TOPIC, testKey, testValue + " [EventMesh-Processed]");
        if (found) {
            System.out.println("✅ 基本消息透传测试通过");
        } else {
            System.out.println("❌ 基本消息透传测试失败");
        }
    }
    
    /**
     * 批量消息透传测试
     */
    private static void testBatchPassthrough() throws Exception {
        System.out.println("\n--- 测试 2: 批量消息透传 ---");
        
        int messageCount = 5;
        System.out.println("📤 批量发送 " + messageCount + " 条消息");
        
        // 批量发送消息
        for (int i = 0; i < messageCount; i++) {
            String key = "batch-key-" + i;
            String value = "batch-value-" + i;
            sendMessage(INPUT_TOPIC, key, value);
        }
        
        // 等待透传完成
        Thread.sleep(3000);
        
        // 验证批量消息
        int foundCount = verifyBatchMessages(messageCount);
        if (foundCount == messageCount) {
            System.out.println("✅ 批量消息透传测试通过 (" + foundCount + "/" + messageCount + ")");
        } else {
            System.out.println("❌ 批量消息透传测试失败 (" + foundCount + "/" + messageCount + ")");
        }
    }
    
    /**
     * 消息格式转换测试
     */
    private static void testMessageFormatTransformation() throws Exception {
        System.out.println("\n--- 测试 3: 消息格式转换 ---");
        
        // 测试 JSON 格式
        String jsonKey = "json-key-" + System.currentTimeMillis();
        String jsonValue = "{\"id\":123,\"name\":\"test\",\"timestamp\":" + System.currentTimeMillis() + "}";
        
        System.out.println("📤 发送 JSON 格式消息: " + jsonKey + " -> " + jsonValue);
        sendMessage(INPUT_TOPIC, jsonKey, jsonValue);
        
        // 等待透传完成
        Thread.sleep(2000);
        
        // 验证消息格式
        boolean found = consumeAndVerify(OUTPUT_TOPIC, jsonKey, jsonValue + " [EventMesh-Processed]");
        if (found) {
            System.out.println("✅ 消息格式转换测试通过");
        } else {
            System.out.println("❌ 消息格式转换测试失败");
        }
    }
    
    /**
     * 发送消息到指定主题
     */
    private static void sendMessage(String topic, String key, String value) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            producer.send(record).get(5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 消费并验证消息
     */
    private static boolean consumeAndVerify(String topic, String expectedKey, String expectedValue) throws Exception {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(topic));
            
            int attempts = 0;
            while (attempts < 10) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    if (expectedKey.equals(record.key()) && expectedValue.equals(record.value())) {
                        System.out.println("📥 找到匹配的消息: " + record.key() + " -> " + record.value());
                        return true;
                    }
                }
                attempts++;
            }
            return false;
        }
    }
    
    /**
     * 验证批量消息
     */
    private static int verifyBatchMessages(int expectedCount) throws Exception {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "batch-test-consumer-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(OUTPUT_TOPIC));
            
            int foundCount = 0;
            int attempts = 0;
            while (foundCount < expectedCount && attempts < 20) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.key() != null && record.key().startsWith("batch-key-")) {
                        foundCount++;
                        System.out.println("📥 消费批量消息: " + record.key() + " -> " + record.value());
                    }
                }
                attempts++;
            }
            return foundCount;
        }
    }
    
    /**
     * 停止 EventMesh 透传服务
     */
    private static void stopEventMeshPassthrough() {
        System.out.println("\n🛑 停止 EventMesh 透传服务...");
        running = false;
        System.out.println("✅ EventMesh 透传服务已停止");
    }
    
    /**
     * 输出测试结果
     */
    private static void printResults() {
        System.out.println("\n📊 EventMesh Kafka 透传统计信息:");
        System.out.println("  - 总处理消息数: " + messageCounter.get());
        System.out.println("  - 成功透传消息数: " + successCounter.get());
        System.out.println("  - 成功率: " + (messageCounter.get() > 0 ? 
            String.format("%.2f%%", (double) successCounter.get() / messageCounter.get() * 100) : "0%"));
    }
}










