# EventMesh 原始协议支持配置参考

## 配置概览

EventMesh 原始协议支持提供了丰富的配置选项，可以根据不同的使用场景进行优化。所有配置项都以 `eventmesh.raw` 开头。

## 基础配置

### 启用/禁用原始协议支持

```properties
# 启用原始协议支持（默认：true）
eventmesh.raw.protocol.enabled=true

# 禁用原始协议支持
eventmesh.raw.protocol.enabled=false
```

### 网络配置

```properties
# 监听地址（默认：0.0.0.0）
eventmesh.raw.protocol.host=0.0.0.0

# 监听端口（默认：9092）
eventmesh.raw.protocol.port=9092

# 连接超时时间（毫秒，默认：30000）
eventmesh.raw.protocol.connectionTimeoutMs=30000

# 保活时间（毫秒，默认：60000）
eventmesh.raw.protocol.keepaliveMs=60000

# 最大连接数（默认：1000）
eventmesh.raw.protocol.maxConnections=1000

# 最大消息大小（字节，默认：1MB）
eventmesh.raw.protocol.maxMessageSize=1048576
```

## 协议特定配置

### Kafka 原始协议配置

```properties
# 启用 Kafka 原始协议（默认：true）
eventmesh.raw.kafka.enabled=true

# Kafka 监听端口（默认：9092）
eventmesh.raw.kafka.port=9092

# Kafka 最大消息大小（字节，默认：1MB）
eventmesh.raw.kafka.maxMessageSize=1048576

# Kafka 请求超时时间（毫秒，默认：30000）
eventmesh.raw.kafka.requestTimeoutMs=30000

# Kafka 会话超时时间（毫秒，默认：10000）
eventmesh.raw.kafka.sessionTimeoutMs=10000

# Kafka 心跳间隔（毫秒，默认：3000）
eventmesh.raw.kafka.heartbeatIntervalMs=3000
```

### Pulsar 原始协议配置

```properties
# 启用 Pulsar 原始协议（默认：true）
eventmesh.raw.pulsar.enabled=true

# Pulsar 监听端口（默认：6650）
eventmesh.raw.pulsar.port=6650

# Pulsar 最大消息大小（字节，默认：5MB）
eventmesh.raw.pulsar.maxMessageSize=5242880

# Pulsar 连接超时时间（毫秒，默认：10000）
eventmesh.raw.pulsar.connectionTimeoutMs=10000

# Pulsar 操作超时时间（毫秒，默认：30000）
eventmesh.raw.pulsar.operationTimeoutMs=30000

# Pulsar 保活间隔（毫秒，默认：30000）
eventmesh.raw.pulsar.keepAliveIntervalMs=30000
```

### RocketMQ 原始协议配置

```properties
# 启用 RocketMQ 原始协议（默认：true）
eventmesh.raw.rocketmq.enabled=true

# RocketMQ 监听端口（默认：9876）
eventmesh.raw.rocketmq.port=9876

# RocketMQ 最大消息大小（字节，默认：4MB）
eventmesh.raw.rocketmq.maxMessageSize=4194304

# RocketMQ 发送消息超时时间（毫秒，默认：3000）
eventmesh.raw.rocketmq.sendMsgTimeoutMs=3000

# RocketMQ 心跳间隔（毫秒，默认：30000）
eventmesh.raw.rocketmq.heartbeatBrokerIntervalMs=30000

# RocketMQ 消费者偏移量持久化间隔（毫秒，默认：5000）
eventmesh.raw.rocketmq.persistConsumerOffsetIntervalMs=5000
```

## 性能优化配置

### 传输优化

```properties
# 启用传输优化（默认：true）
eventmesh.raw.transmission.optimization.enabled=true

# 批量处理大小（默认：1000）
eventmesh.raw.transmission.batch.size=1000

# 传输超时时间（毫秒，默认：5000）
eventmesh.raw.transmission.timeout.ms=5000

# 启用零拷贝（默认：true）
eventmesh.raw.transmission.zeroCopy.enabled=true

# 启用异步处理（默认：true）
eventmesh.raw.transmission.asyncProcessing.enabled=true
```

### 连接池配置

```properties
# 连接池大小（默认：100）
eventmesh.raw.connection.pool.size=100

# 连接池最大等待时间（毫秒，默认：5000）
eventmesh.raw.connection.pool.maxWaitMs=5000

# 连接池最小空闲连接数（默认：10）
eventmesh.raw.connection.pool.minIdle=10

# 连接池最大空闲连接数（默认：50）
eventmesh.raw.connection.pool.maxIdle=50
```

## 安全配置

### SSL/TLS 配置

```properties
# 启用 SSL/TLS（默认：false）
eventmesh.raw.security.ssl.enabled=false

# SSL 密钥库路径
eventmesh.raw.security.ssl.keyStorePath=/path/to/keystore.jks

# SSL 密钥库密码
eventmesh.raw.security.ssl.keyStorePassword=password

# SSL 信任库路径
eventmesh.raw.security.ssl.trustStorePath=/path/to/truststore.jks

# SSL 信任库密码
eventmesh.raw.security.ssl.trustStorePassword=password

# SSL 协议版本（默认：TLS）
eventmesh.raw.security.ssl.protocol=TLS
```

### 认证配置

```properties
# 启用认证（默认：false）
eventmesh.raw.security.authentication.enabled=false

# 认证类型（none, sasl, oauth2，默认：none）
eventmesh.raw.security.authentication.type=none

# SASL 机制（PLAIN, SCRAM-SHA-256, SCRAM-SHA-512，默认：PLAIN）
eventmesh.raw.security.sasl.mechanism=PLAIN

# SASL 用户名
eventmesh.raw.security.sasl.username=user

# SASL 密码
eventmesh.raw.security.sasl.password=password
```

## 监控配置

### 指标监控

```properties
# 启用指标监控（默认：true）
eventmesh.raw.monitoring.metrics.enabled=true

# 指标报告间隔（毫秒，默认：60000）
eventmesh.raw.monitoring.metrics.reportIntervalMs=60000

# 启用详细指标（默认：false）
eventmesh.raw.monitoring.metrics.detailed.enabled=false
```

## 配置示例

### 开发环境配置

```properties
# 开发环境 - 基础配置
eventmesh.raw.protocol.enabled=true
eventmesh.raw.protocol.host=0.0.0.0
eventmesh.raw.protocol.port=9092
eventmesh.raw.protocol.maxConnections=100

# 开发环境 - 协议配置
eventmesh.raw.kafka.enabled=true
eventmesh.raw.kafka.port=9092
eventmesh.raw.pulsar.enabled=true
eventmesh.raw.pulsar.port=6650
eventmesh.raw.rocketmq.enabled=true
eventmesh.raw.rocketmq.port=9876

# 开发环境 - 性能配置
eventmesh.raw.transmission.optimization.enabled=true
eventmesh.raw.transmission.batch.size=100
eventmesh.raw.connection.pool.size=20

# 开发环境 - 监控配置
eventmesh.raw.monitoring.metrics.enabled=true
eventmesh.raw.monitoring.metrics.detailed.enabled=true
```

### 生产环境配置

```properties
# 生产环境 - 基础配置
eventmesh.raw.protocol.enabled=true
eventmesh.raw.protocol.host=0.0.0.0
eventmesh.raw.protocol.port=9092
eventmesh.raw.protocol.maxConnections=1000
eventmesh.raw.protocol.maxMessageSize=1048576

# 生产环境 - 协议配置
eventmesh.raw.kafka.enabled=true
eventmesh.raw.kafka.port=9092
eventmesh.raw.kafka.maxMessageSize=1048576
eventmesh.raw.kafka.requestTimeoutMs=30000

eventmesh.raw.pulsar.enabled=true
eventmesh.raw.pulsar.port=6650
eventmesh.raw.pulsar.maxMessageSize=5242880
eventmesh.raw.pulsar.operationTimeoutMs=30000

eventmesh.raw.rocketmq.enabled=true
eventmesh.raw.rocketmq.port=9876
eventmesh.raw.rocketmq.maxMessageSize=4194304
eventmesh.raw.rocketmq.sendMsgTimeoutMs=3000

# 生产环境 - 性能配置
eventmesh.raw.transmission.optimization.enabled=true
eventmesh.raw.transmission.batch.size=2000
eventmesh.raw.transmission.zeroCopy.enabled=true
eventmesh.raw.transmission.asyncProcessing.enabled=true
eventmesh.raw.connection.pool.size=200
eventmesh.raw.connection.pool.maxIdle=100

# 生产环境 - 安全配置
eventmesh.raw.security.ssl.enabled=true
eventmesh.raw.security.ssl.keyStorePath=/etc/eventmesh/keystore.jks
eventmesh.raw.security.ssl.keyStorePassword=${KEYSTORE_PASSWORD}
eventmesh.raw.security.ssl.trustStorePath=/etc/eventmesh/truststore.jks
eventmesh.raw.security.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD}

eventmesh.raw.security.authentication.enabled=true
eventmesh.raw.security.authentication.type=sasl
eventmesh.raw.security.sasl.mechanism=SCRAM-SHA-256
eventmesh.raw.security.sasl.username=${SASL_USERNAME}
eventmesh.raw.security.sasl.password=${SASL_PASSWORD}

# 生产环境 - 监控配置
eventmesh.raw.monitoring.metrics.enabled=true
eventmesh.raw.monitoring.metrics.reportIntervalMs=30000
eventmesh.raw.monitoring.metrics.detailed.enabled=false
```

### 高性能配置

```properties
# 高性能 - 基础配置
eventmesh.raw.protocol.enabled=true
eventmesh.raw.protocol.maxConnections=5000
eventmesh.raw.protocol.maxMessageSize=2097152

# 高性能 - 协议配置
eventmesh.raw.kafka.enabled=true
eventmesh.raw.kafka.maxMessageSize=2097152
eventmesh.raw.kafka.requestTimeoutMs=10000

eventmesh.raw.pulsar.enabled=true
eventmesh.raw.pulsar.maxMessageSize=10485760
eventmesh.raw.pulsar.operationTimeoutMs=15000

eventmesh.raw.rocketmq.enabled=true
eventmesh.raw.rocketmq.maxMessageSize=8388608
eventmesh.raw.rocketmq.sendMsgTimeoutMs=1000

# 高性能 - 性能配置
eventmesh.raw.transmission.optimization.enabled=true
eventmesh.raw.transmission.batch.size=5000
eventmesh.raw.transmission.timeout.ms=2000
eventmesh.raw.transmission.zeroCopy.enabled=true
eventmesh.raw.transmission.asyncProcessing.enabled=true
eventmesh.raw.connection.pool.size=500
eventmesh.raw.connection.pool.maxIdle=200
eventmesh.raw.connection.pool.minIdle=50

# 高性能 - 监控配置
eventmesh.raw.monitoring.metrics.enabled=true
eventmesh.raw.monitoring.metrics.reportIntervalMs=15000
eventmesh.raw.monitoring.metrics.detailed.enabled=true
```

## 配置验证

### 配置检查命令

```bash
# 检查配置语法
./bin/eventmesh-config-validate.sh

# 检查端口占用
netstat -tlnp | grep -E "(9092|6650|9876)"

# 检查配置文件
grep -E "^eventmesh\.raw" conf/eventmesh.properties
```

### 配置热重载

```properties
# 启用配置热重载（默认：false）
eventmesh.raw.config.hotReload.enabled=false

# 配置重载间隔（毫秒，默认：30000）
eventmesh.raw.config.hotReload.intervalMs=30000
```

## 配置最佳实践

### 1. 性能调优

- **批量大小**: 根据消息大小和网络延迟调整批量处理大小
- **连接池**: 根据并发连接数调整连接池大小
- **超时设置**: 根据网络环境调整超时时间

### 2. 安全配置

- **生产环境**: 必须启用 SSL/TLS 和认证
- **密钥管理**: 使用环境变量或密钥管理服务存储敏感信息
- **访问控制**: 配置适当的访问控制策略

### 3. 监控配置

- **指标间隔**: 根据监控需求调整指标报告间隔
- **详细指标**: 仅在需要调试时启用详细指标
- **告警配置**: 设置适当的告警阈值

### 4. 故障排查

```properties
# 调试模式配置
logging.level.org.apache.eventmesh.runtime.core.protocol.raw=DEBUG
logging.level.org.apache.eventmesh.protocol=DEBUG
logging.level.org.apache.eventmesh.runtime.core.protocol.raw.handler=TRACE
```

## 配置参考表

| 配置项 | 默认值 | 说明 | 推荐值 |
|--------|--------|------|--------|
| `eventmesh.raw.protocol.enabled` | `true` | 启用原始协议支持 | `true` |
| `eventmesh.raw.protocol.maxConnections` | `1000` | 最大连接数 | 根据并发量调整 |
| `eventmesh.raw.transmission.batch.size` | `1000` | 批量处理大小 | 1000-5000 |
| `eventmesh.raw.connection.pool.size` | `100` | 连接池大小 | 100-500 |
| `eventmesh.raw.security.ssl.enabled` | `false` | 启用 SSL/TLS | 生产环境 `true` |
| `eventmesh.raw.monitoring.metrics.enabled` | `true` | 启用指标监控 | `true` | 