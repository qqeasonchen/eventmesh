# Raw Kafka 端到端测试

## 概述

`RawKafkaE2EIT` 是一个全面的 Kafka 端到端测试套件，用于验证 EventMesh 的 Raw Kafka 协议实现。该测试使用 TestContainers 来启动真实的 Kafka 容器，确保测试环境的真实性和可靠性。

## 测试场景

### 1. 基本消息透传测试 (`testKafkaRawProtocolPassthrough`)
- **目的**: 验证基本的消息生产和消费功能
- **测试内容**: 
  - 生产单条消息到 Kafka
  - 消费并验证消息内容
  - 确保消息正确传输

### 2. 批量消息处理测试 (`testKafkaRawProtocolBatchMessages`)
- **目的**: 验证批量消息处理能力
- **测试内容**:
  - 批量生产多条消息
  - 验证所有消息都被正确处理
  - 测试消息去重和完整性

### 3. 大消息处理测试 (`testKafkaRawProtocolLargeMessage`)
- **目的**: 验证大消息的处理能力
- **测试内容**:
  - 生产包含大量内容的消息
  - 验证大消息的完整传输
  - 测试内存使用和性能

### 4. 并发消息处理测试 (`testKafkaRawProtocolConcurrentMessages`)
- **目的**: 验证并发处理能力
- **测试内容**:
  - 多线程并发生产消息
  - 验证所有并发消息都被正确处理
  - 测试系统在高并发下的稳定性

### 5. 消息顺序性测试 (`testKafkaRawProtocolMessageOrdering`)
- **目的**: 验证消息的顺序性保证
- **测试内容**:
  - 按顺序生产消息
  - 验证消费时的消息顺序
  - 测试分区内顺序保证

### 6. 消息持久化测试 (`testKafkaRawProtocolMessageRetention`)
- **目的**: 验证消息的持久化能力
- **测试内容**:
  - 生产消息后等待一段时间
  - 验证消息仍然可以被消费
  - 测试消息的持久性

### 7. 消息压缩测试 (`testKafkaRawProtocolMessageCompression`)
- **目的**: 验证消息压缩功能
- **测试内容**:
  - 使用 gzip 压缩生产消息
  - 验证压缩消息的正确消费
  - 测试压缩效果

## 运行要求

### 依赖
- Java 8+
- Gradle
- Docker (用于 TestContainers)
- TestContainers 依赖

### 配置
测试会自动启动 Kafka 容器，无需额外配置。容器配置：
- Kafka 版本: 7.2.1
- 端口: 自动分配
- Topic: `eventmesh-e2e-test`

## 运行测试

### 运行所有测试
```bash
./gradlew :eventmesh-runtime:test --tests RawKafkaE2EIT
```

### 运行特定测试
```bash
./gradlew :eventmesh-runtime:test --tests RawKafkaE2EIT.testKafkaRawProtocolPassthrough
```

### 调试模式运行
```bash
./gradlew :eventmesh-runtime:test --tests RawKafkaE2EIT --debug
```

## 测试结果解读

### 成功指标
- 所有测试方法通过
- 消息生产和消费正常
- 无异常或错误日志

### 失败排查
1. **Docker 问题**: 确保 Docker 正在运行
2. **端口冲突**: 检查是否有端口被占用
3. **内存不足**: 确保有足够的内存运行 Kafka 容器
4. **网络问题**: 检查网络连接

## 扩展测试

### 添加新的测试场景
1. 在 `RawKafkaE2EIT` 类中添加新的 `@Test` 方法
2. 使用 `produceMessage()` 和 `consumeAndVerify()` 辅助方法
3. 添加适当的断言验证

### 自定义配置
可以修改以下常量来自定义测试：
- `TOPIC`: 测试使用的 topic 名称
- `GROUP_ID`: 消费者组 ID
- Kafka 容器版本和配置

## 性能测试

该测试套件也可以用于性能测试：
- 调整 `messageCount` 和 `threadCount` 参数
- 监控内存和 CPU 使用情况
- 分析消息处理延迟

## 注意事项

1. **资源清理**: 测试会自动清理 Kafka 容器
2. **并发安全**: 每个测试使用独立的消费者组
3. **超时设置**: 测试包含适当的超时机制
4. **错误处理**: 包含完善的异常处理逻辑

## 相关文档

- [EventMesh Raw Protocol 文档](../docs/raw-protocol.md)
- [Kafka 集成指南](../docs/kafka-integration.md)
- [测试最佳实践](../docs/testing-best-practices.md) 

## 概述

`RawKafkaE2EIT` 是一个全面的 Kafka 端到端测试套件，用于验证 EventMesh 的 Raw Kafka 协议实现。该测试使用 TestContainers 来启动真实的 Kafka 容器，确保测试环境的真实性和可靠性。

## 测试场景

### 1. 基本消息透传测试 (`testKafkaRawProtocolPassthrough`)
- **目的**: 验证基本的消息生产和消费功能
- **测试内容**: 
  - 生产单条消息到 Kafka
  - 消费并验证消息内容
  - 确保消息正确传输

### 2. 批量消息处理测试 (`testKafkaRawProtocolBatchMessages`)
- **目的**: 验证批量消息处理能力
- **测试内容**:
  - 批量生产多条消息
  - 验证所有消息都被正确处理
  - 测试消息去重和完整性

### 3. 大消息处理测试 (`testKafkaRawProtocolLargeMessage`)
- **目的**: 验证大消息的处理能力
- **测试内容**:
  - 生产包含大量内容的消息
  - 验证大消息的完整传输
  - 测试内存使用和性能

### 4. 并发消息处理测试 (`testKafkaRawProtocolConcurrentMessages`)
- **目的**: 验证并发处理能力
- **测试内容**:
  - 多线程并发生产消息
  - 验证所有并发消息都被正确处理
  - 测试系统在高并发下的稳定性

### 5. 消息顺序性测试 (`testKafkaRawProtocolMessageOrdering`)
- **目的**: 验证消息的顺序性保证
- **测试内容**:
  - 按顺序生产消息
  - 验证消费时的消息顺序
  - 测试分区内顺序保证

### 6. 消息持久化测试 (`testKafkaRawProtocolMessageRetention`)
- **目的**: 验证消息的持久化能力
- **测试内容**:
  - 生产消息后等待一段时间
  - 验证消息仍然可以被消费
  - 测试消息的持久性

### 7. 消息压缩测试 (`testKafkaRawProtocolMessageCompression`)
- **目的**: 验证消息压缩功能
- **测试内容**:
  - 使用 gzip 压缩生产消息
  - 验证压缩消息的正确消费
  - 测试压缩效果

## 运行要求

### 依赖
- Java 8+
- Gradle
- Docker (用于 TestContainers)
- TestContainers 依赖

### 配置
测试会自动启动 Kafka 容器，无需额外配置。容器配置：
- Kafka 版本: 7.2.1
- 端口: 自动分配
- Topic: `eventmesh-e2e-test`

## 运行测试

### 运行所有测试
```bash
./gradlew :eventmesh-runtime:test --tests RawKafkaE2EIT
```

### 运行特定测试
```bash
./gradlew :eventmesh-runtime:test --tests RawKafkaE2EIT.testKafkaRawProtocolPassthrough
```

### 调试模式运行
```bash
./gradlew :eventmesh-runtime:test --tests RawKafkaE2EIT --debug
```

## 测试结果解读

### 成功指标
- 所有测试方法通过
- 消息生产和消费正常
- 无异常或错误日志

### 失败排查
1. **Docker 问题**: 确保 Docker 正在运行
2. **端口冲突**: 检查是否有端口被占用
3. **内存不足**: 确保有足够的内存运行 Kafka 容器
4. **网络问题**: 检查网络连接

## 扩展测试

### 添加新的测试场景
1. 在 `RawKafkaE2EIT` 类中添加新的 `@Test` 方法
2. 使用 `produceMessage()` 和 `consumeAndVerify()` 辅助方法
3. 添加适当的断言验证

### 自定义配置
可以修改以下常量来自定义测试：
- `TOPIC`: 测试使用的 topic 名称
- `GROUP_ID`: 消费者组 ID
- Kafka 容器版本和配置

## 性能测试

该测试套件也可以用于性能测试：
- 调整 `messageCount` 和 `threadCount` 参数
- 监控内存和 CPU 使用情况
- 分析消息处理延迟

## 注意事项

1. **资源清理**: 测试会自动清理 Kafka 容器
2. **并发安全**: 每个测试使用独立的消费者组
3. **超时设置**: 测试包含适当的超时机制
4. **错误处理**: 包含完善的异常处理逻辑

## 相关文档

- [EventMesh Raw Protocol 文档](../docs/raw-protocol.md)
- [Kafka 集成指南](../docs/kafka-integration.md)
- [测试最佳实践](../docs/testing-best-practices.md) 
 