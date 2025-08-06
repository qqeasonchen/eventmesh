# EventMesh 原始协议支持快速入门

## 5分钟快速体验

### 1. 启动 EventMesh

```bash
# 下载并启动 EventMesh
wget https://downloads.apache.org/eventmesh/1.9.0/apache-eventmesh-1.9.0-bin.tar.gz
tar -xzf apache-eventmesh-1.9.0-bin.tar.gz
cd apache-eventmesh-1.9.0

# 配置原始协议支持
cat >> conf/eventmesh.properties << EOF
# 启用原始协议支持
eventmesh.raw.protocol.enabled=true
eventmesh.raw.kafka.enabled=true
eventmesh.raw.kafka.port=9092
eventmesh.raw.pulsar.enabled=true
eventmesh.raw.pulsar.port=6650
eventmesh.raw.rocketmq.enabled=true
eventmesh.raw.rocketmq.port=9876
EOF

# 启动 EventMesh
./bin/eventmesh-start.sh
```

### 2. 测试 Kafka 原始客户端

```java
// 创建测试文件 KafkaTest.java
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import java.util.Properties;
import java.util.Arrays;

public class KafkaTest {
    public static void main(String[] args) {
        // 生产者
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        producer.send(new ProducerRecord<>("test-topic", "Hello EventMesh!"));
        producer.close();
        
        // 消费者
        props.put("group.id", "test-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("test-topic"));
        
        ConsumerRecords<String, String> records = consumer.poll(1000);
        for (ConsumerRecord<String, String> record : records) {
            System.out.println("Received: " + record.value());
        }
        consumer.close();
    }
}
```

### 3. 测试 Pulsar 原始客户端

```java
// 创建测试文件 PulsarTest.java
import org.apache.pulsar.client.api.*;

public class PulsarTest {
    public static void main(String[] args) throws Exception {
        PulsarClient client = PulsarClient.builder()
            .serviceUrl("pulsar://localhost:6650")
            .build();
        
        // 生产者
        Producer<String> producer = client.newProducer(Schema.STRING)
            .topic("test-topic")
            .create();
        producer.send("Hello EventMesh!");
        producer.close();
        
        // 消费者
        Consumer<String> consumer = client.newConsumer(Schema.STRING)
            .topic("test-topic")
            .subscriptionName("test-sub")
            .subscribe();
        
        Message<String> msg = consumer.receive();
        System.out.println("Received: " + msg.getValue());
        consumer.acknowledge(msg);
        consumer.close();
        client.close();
    }
}
```

### 4. 测试 RocketMQ 原始客户端

```java
// 创建测试文件 RocketMQTest.java
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

public class RocketMQTest {
    public static void main(String[] args) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("test-group");
        producer.setNamesrvAddr("localhost:9876");
        producer.start();
        
        Message msg = new Message("test-topic", "Hello EventMesh!".getBytes());
        SendResult result = producer.send(msg);
        System.out.println("Send result: " + result);
        
        producer.shutdown();
    }
}
```

### 5. 验证连接状态

```bash
# 检查端口监听
netstat -tlnp | grep -E "(9092|6650|9876)"

# 查看 EventMesh 日志
tail -f logs/eventmesh.log | grep "RawProtocol"

# 查看连接统计
curl -s http://localhost:10106/actuator/raw-protocol/stats | jq
```

## 常见问题

### Q: 客户端连接失败怎么办？
A: 检查 EventMesh 是否正常启动，端口是否正确配置，防火墙是否开放相应端口。

### Q: 如何查看协议识别日志？
A: 使用命令 `grep "Detected protocol type" logs/eventmesh.log` 查看协议识别情况。

### Q: 如何优化性能？
A: 启用协议透传优化：`eventmesh.raw.transmission.optimization.enabled=true`

### Q: 支持哪些客户端版本？
A: Kafka 0.10.0+、Pulsar 2.0.0+、RocketMQ 4.0.0+ 完全支持。

## 下一步

- 📖 阅读 [完整使用指南](raw-protocol-support.md)
- 🔧 查看 [配置参考](raw-protocol-config.md)
- 🚀 了解 [性能优化](raw-protocol-performance.md)
- 🛡️ 学习 [安全配置](raw-protocol-security.md) 