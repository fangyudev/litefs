# LiteFS SPI 扩展开发指南

> 本文档介绍如何通过 SPI 机制扩展 LiteFS 的存储引擎、元数据存储等组件

## 概述

LiteFS 采用 SPI（Service Provider Interface）设计，所有核心组件都可以通过实现接口来替换或扩展。

## 可扩展的 SPI 接口

| SPI 接口 | 说明 | 内置实现 |
|----------|------|----------|
| `StorageEngine` | 存储引擎 | `LocalStorageEngine`, `HttpRemoteStorageEngine` |
| `MetadataStore` | 元数据存储 | `H2MetadataStore`, `MySqlMetadataStore`, `BatchMetadataStore` |
| `ServiceRegistry` | 服务注册 | `StaticServiceRegistry`, `NacosServiceRegistry` |
| `StorageEngineRouter` | 存储引擎路由 | `SingleNodeStorageEngineRouter`, `DistributedStorageEngineRouter` |
| `MetadataCacheProvider` | 元数据缓存提供者 | `LocalMetadataCacheProvider`, `RedisMetadataCacheProvider` |
| `MultipartUploadStore` | 分片上传存储 | `LocalMultipartUploadStore`, `RedisMultipartUploadStore` |
| `ReplicaMetadataStore` | 副本元数据存储 | `InMemoryReplicaMetadataStore`, `H2ReplicaMetadataStore`, `MySqlReplicaMetadataStore` |
| `NodeSelector` | 节点选择器 | `RoundRobinNodeSelector`, `CapacityNodeSelector` |
| `ReplicaPlacer` | 副本放置策略 | `BalancedReplicaPlacer`, `LocalityReplicaPlacer` |
| `MessageQueue` | 消息队列 | `InMemoryMessageQueue`, `RedisMessageQueue` |
| `UrlGenerator` | URL 生成器 | `GatewayUrlGenerator`, `DirectUrlGenerator` |

## 扩展存储引擎

### 1. 实现 StorageEngine 接口

```java
public class MyStorageEngine implements StorageEngine {

    @Override
    public String write(String fileId, InputStream data) {
        // 实现文件写入逻辑
    }

    @Override
    public InputStream read(String fileId) {
        // 实现文件读取逻辑
    }

    @Override
    public void delete(String fileId) {
        // 实现文件删除逻辑
    }

    @Override
    public boolean exists(String fileId) {
        // 实现文件存在检查
    }

    @Override
    public long getSize(String fileId) {
        // 实现获取文件大小
    }

    @Override
    public String getStoragePath(String fileId) {
        // 返回存储路径
    }

    @Override
    public long append(String fileId, InputStream data) {
        // 实现追加写入（用于分片上传）
    }
}
```

### 2. 注册到 Spring 容器

```java
@Configuration
public class MyStorageConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public StorageEngine storageEngine() {
        return new MyStorageEngine();
    }
}
```

### 3. 实现要点

- 所有方法必须是线程安全的
- `append()` 方法用于分片上传的合并，需要支持追加写入
- 推荐使用不可变对象（final 字段）保证线程安全
- 异常应包装为 `RuntimeException`，提供清晰的错误信息

## 扩展元数据存储

### 1. 实现 MetadataStore 接口

```java
public class MyMetadataStore implements MetadataStore {

    @Override
    public void save(FileMetadata metadata) {
        // 保存元数据
    }

    @Override
    public FileMetadata get(String fileId) {
        // 获取元数据
    }

    @Override
    public List<FileMetadata> query(FileQuery query) {
        // 查询元数据列表
    }

    // ... 其他方法
}
```

### 2. 注册到 Spring 容器

```java
@Bean
@ConditionalOnMissingBean
public MetadataStore metadataStore() {
    return new MyMetadataStore();
}
```

## 扩展服务注册

### 1. 实现 ServiceRegistry 接口

```java
public class MyServiceRegistry implements ServiceRegistry {

    @Override
    public void register(StorageNode node) {
        // 注册节点
    }

    @Override
    public List<StorageNode> discover() {
        // 发现所有节点
    }

    @Override
    public void heartbeat(String nodeId) {
        // 发送心跳
    }

    // ... 其他方法
}
```

### 2. 注册到 Spring 容器

```java
@Bean
@ConditionalOnMissingBean
public ServiceRegistry serviceRegistry() {
    return new MyServiceRegistry();
}
```

## 线程安全最佳实践

### 方案 1: 使用不可变对象

```java
public class S3StorageEngine implements StorageEngine {
    // 所有字段都是 final，天然线程安全
    private final AmazonS3 s3Client;
    private final String bucketName;
    
    public S3StorageEngine(AmazonS3 s3Client, String bucketName) {
        this.s3Client = Objects.requireNonNull(s3Client);
        this.bucketName = Objects.requireNonNull(bucketName);
    }
}
```

### 方案 2: 使用并发数据结构

```java
public class CachedStorageEngine implements StorageEngine {
    // 使用 ConcurrentHashMap 保证线程安全
    private final ConcurrentHashMap<String, FileMetadata> cache = new ConcurrentHashMap<>();
}
```

## 异常处理规范

所有异常应包装为 `RuntimeException`，提供清晰的错误信息：

```java
@Override
public long store(String fileId, InputStream inputStream) {
    try {
        // 业务逻辑
        return doStore(fileId, inputStream);
    } catch (IOException e) {
        throw new RuntimeException("Failed to store file: " + fileId, e);
    } catch (Exception e) {
        // 捕获所有异常，避免泄漏实现细节
        throw new RuntimeException("Unexpected error during store", e);
    }
}
```

## 相关文档

- [架构设计](ARCHITECTURE.md) - 系统架构和设计决策
- [API 使用指南](../user-guide/API_GUIDE.md) - SPI 扩展相关 API 使用说明
- [学习指南](LEARNING_GUIDE.md) - 代码阅读路径
