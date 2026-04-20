# LiteFS 常见问题解答 (FAQ)

> 本文档收集了用户常见的疑问和解答，帮助您更好地理解 LiteFS 的设计理念和使用场景。

---

## 目录

1. [LiteFS 和 FastDFS 有什么区别？](#litefs-和-fastdfs-有什么区别)
2. [什么场景适合用 LiteFS？](#什么场景适合用-litefs)
3. [LiteFS 支持哪些存储后端？](#litefs-支持哪些存储后端)
4. [LiteFS 支持分布式部署吗？](#litefs-支持分布式部署吗)
5. [如何扩展存储引擎？](#如何扩展存储引擎)
6. [性能优化专题](#性能优化专题)
   - [LiteFS 做了哪些性能优化？](#litefs-做了哪些性能优化)
   - [如何选择合适的优化策略？](#如何选择合适的优化策略)
   - [性能优化的最佳实践](#性能优化的最佳实践)

---

## LiteFS 和 FastDFS 有什么区别？

### 解答

| 特性 | LiteFS | FastDFS |
|------|--------|---------|
| 定位 | 嵌入式 SDK | 独立服务 |
| 部署方式 | 集成到应用中 | 需要独立部署 |
| 依赖 | 复用现有中间件 | 需要独立部署 |
| 存储后端 | 可插拔（本地存储，可扩展） | 固定本地存储 |
| Spring 集成 | 原生支持 | 需要客户端配置 |
| 维护状态 | 活跃开发 | 维护模式 |

**LiteFS 更轻量，适合嵌入到应用中；FastDFS 是独立服务，需要额外运维。**

---

## 什么场景适合用 LiteFS？

### 解答

#### 适合的场景

1. **需要文件管理功能的应用**
   - CMS 内容管理系统
   - 电商平台的商品图片管理
   - 文档管理系统
   - 企业网盘
   - 企业各种管理系统，如：ERP、CRM等

2. **需要快速集成的项目**
   - Spring Boot 应用
   - 不想重复开发文件管理功能

3. **需要分布式部署**
   - 多节点存储
   - 高可用要求

4. **私有化部署需求**
   - 数据合规要求
   - 不能使用公有云存储

---

## LiteFS 支持哪些存储后端？

### 解答

当前版本内置支持：

| 存储引擎 | 说明 | 适用场景 |
|---------|------|---------|
| LocalStorageEngine | 本地文件系统 | 开发测试、单机部署、私有化部署 |
| HttpRemoteStorageEngine | HTTP 远程存储代理 | 分布式部署、跨节点访问 |

**扩展支持：**

LiteFS 采用 SPI 设计，可以通过实现 `StorageEngine` 接口扩展支持其他存储后端：
- AWS S3
- MinIO
- 阿里云 OSS
- 腾讯云 COS
- 其他对象存储服务

详见 [SPI 扩展开发指南](../developer-guide/SPI_DEVELOPER_GUIDE.md)。

---

## LiteFS 支持分布式部署吗？

### 解答

**支持！** LiteFS 提供完整的分布式特性：

1. **服务注册与发现**
   - Static（静态配置）：手动配置节点信息，适用于开发测试
   - Nacos：自动发现可用节点，生产环境推荐使用

2. **负载均衡**
   - 容量优先策略
   - 轮询策略

3. **数据复制**
   - 同步复制
   - 异步复制
   - 多副本策略

4. **故障转移**
   - 自动检测节点故障
   - 自动切换到健康节点

5. **跨节点访问**
   - HTTP 方式（简单易用，无额外依赖，支持流式传输）

6. **URL 生成**
   - 网关模式：通过统一网关访问文件
   - 直接访问模式：根据文件所在节点动态生成 URL

详见 [部署指南](../operations/DEPLOYMENT.md) 和 [API 使用指南](API_GUIDE.md)。

---

## 如何扩展存储引擎？

### 解答

LiteFS 采用 SPI（Service Provider Interface）设计，您可以轻松扩展支持其他存储后端。

#### 实现步骤

**1. 实现 StorageEngine 接口**

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

**2. 注册到 Spring 容器**

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

**3. 配置使用**

```yaml
litefs:
  storage:
    type: my-storage  # 自定义类型
```

#### 扩展示例

您可以参考 `LocalStorageEngine` 的实现来开发自己的存储引擎，详见 [SPI 扩展开发指南](../developer-guide/SPI_DEVELOPER_GUIDE.md)。

---

## 还有其他问题？

如果您有其他问题，欢迎：
- 提交 [GitHub Issue](https://github.com/fangyudev/litefs/issues)
- 查阅 [架构设计](../developer-guide/ARCHITECTURE.md)

---

## 性能优化专题

### LiteFS 做了哪些性能优化？

#### 解答

LiteFS 从多个维度进行了性能优化，主要包括以下几个方面：

---

#### 1. I/O 缓冲区优化

**问题：** 默认的 8KB 缓冲区对于大文件传输效率较低。

**优化方案：**
```java
// 优化前：8KB 缓冲区
private static final int DEFAULT_BUFFER_SIZE = 8192;

// 优化后：32KB 默认缓冲区 + 256KB 大文件缓冲区
private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;    // 32KB
private static final int LARGE_BUFFER_SIZE = 256 * 1024;     // 256KB
```

**优化原理：**
- 更大的缓冲区意味着更少的系统调用次数
- 32KB 是大多数场景的最佳平衡点（内存占用 vs 性能）
- 256KB 适合大文件场景，进一步减少 I/O 次数

**性能提升：**
- 小文件传输：提升约 20-30%
- 大文件传输：提升约 50-100%

**使用示例：**
```java
// 普通文件复制
IoUtils.copy(inputStream, outputStream);

// 大文件复制（自动使用 256KB 缓冲区）
IoUtils.copyLarge(inputStream, outputStream);

// 指定缓冲区大小
IoUtils.copy(inputStream, outputStream, 64 * 1024);
```

---

#### 2. 元数据缓存机制

**问题：** 频繁访问热点文件时，每次都需要查询数据库，响应慢、数据库压力大。

**优化方案：** 使用可插拔缓存装饰器，支持本地缓存（LRU + TTL）和 Redis 分布式缓存。

**Spring Boot 配置：**

```yaml
# 单机模式 - 使用本地缓存
litefs:
  cache:
    enabled: true
    type: local           # 仅限单节点
    max-size: 10000       # 最大缓存条目数
    ttl: 300000           # 过期时间（毫秒），5分钟

# 分布式模式 - 使用 Redis 缓存
litefs:
  cache:
    enabled: true
    type: redis           # 分布式环境推荐
    ttl: 300000           # 过期时间（毫秒），5分钟
    # redis 配置未填写时自动复用 replication.queue.redis
    redis:
      key-prefix: litefs:meta:
```

> **注意：** 分布式模式下 `cache.type=local` 会被自动禁用，避免各节点缓存不一致导致数据覆盖。未配置 Redis 时缓存不生效。

**核心特性：**

| 特性 | 说明 |
|-----|------|
| Cache Aside 模式 | 读：miss 回填缓存；写：写 DB + 删缓存，避免并发脏数据 |
| LRU 淘汰 | 缓存满时淘汰最久未使用的条目（local 模式） |
| TTL 过期 | 支持设置缓存条目生存时间 |
| 分布式共享 | Redis 模式下多节点共享缓存数据 |
| 降级 | 分布式模式 + Redis 不可用时自动禁用缓存，不会降级为本地缓存 |

---

#### 3. 批量操作优化

**问题：** 批量插入/删除文件时，逐条操作数据库效率极低。

**优化方案：** 使用 JDBC Batch 和事务批量提交

```java
// 优化前：逐条插入
for (FileMetadata metadata : metadataList) {
    metadataStore.save(metadata);  // 每次一条 SQL
}

// 优化后：批量插入
BatchMetadataStore batchStore = new BatchMetadataStore(delegate, jdbcUrl, user, pass);
batchStore.saveBatch(metadataList);  // 一次性提交多条 SQL
```

**性能对比：**
| 操作 | 逐条执行 | 批量执行 | 提升 |
|-----|---------|---------|------|
| 插入 1000 条 | ~5000ms | ~200ms | 25x |
| 删除 1000 条 | ~3000ms | ~100ms | 30x |

---

#### 4. 并发处理优化

**问题：** 串行处理多个文件操作时，CPU 利用率低，总耗时长。

**优化方案：** 提供专用的文件操作线程池

```java
// 创建线程池（自动根据 CPU 核心数配置）
FileOperationExecutor executor = new FileOperationExecutor(4, 100);

// 并行执行多个文件操作
List<CompletableFuture<String>> futures = new ArrayList<>();
for (String fileId : fileIds) {
    futures.add(executor.submit(() -> fileClient.copy(fileId)));
}

// 等待所有任务完成
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

// 关闭线程池
executor.shutdown();
```

**线程池配置：**
- 核心线程数 = CPU 核心数
- 最大线程数 = CPU 核心数 × 2
- 队列容量 = 可配置（默认 1000）
- 拒绝策略 = CallerRunsPolicy（调用者执行）

**使用场景：**
- 批量文件复制
- 批量文件删除
- 批量缩略图生成
- 并行数据迁移

---

### 如何选择合适的优化策略？

#### 解答

| 问题 | 推荐方案 |
|------|----------|
| 热点文件访问慢 | 使用 MetadataCache |
| 大文件传输慢 | 使用 IoUtils.copyLarge() |
| 批量操作耗时长 | 使用 BatchMetadataStore |
| 多文件操作串行执行慢 | 使用 FileOperationExecutor |
| 高并发场景 | 组合使用：缓存 + 线程池 + 批量操作 |

详见 [性能调优指南](PERFORMANCE_TUNING.md)。

---

### 性能优化的最佳实践

#### 解答

**1. 合理设置缓存类型和大小**
```yaml
# 单机模式：使用本地缓存
litefs:
  cache:
    enabled: true
    type: local
    max-size: 10000
    ttl: 300000

# 分布式模式：必须使用 Redis 缓存
litefs:
  cache:
    enabled: true
    type: redis
    ttl: 300000
```

**2. 分布式环境注意事项**
```yaml
# 错误：分布式模式下使用本地缓存会导致数据不一致
litefs:
  remote:
    enabled: true
  cache:
    type: local          # 会被自动禁用！

# 正确：分布式模式下使用 Redis 缓存
litefs:
  remote:
    enabled: true
  cache:
    type: redis
```

**3. 选择合适的批量大小**

**4. 线程池大小调优**
```java
// I/O 密集型任务：线程数 = CPU 核心数 × 2
// CPU 密集型任务：线程数 = CPU 核心数 + 1
FileOperationExecutor executor = new FileOperationExecutor(
    Runtime.getRuntime().availableProcessors() * 2, 
    100
);
```

**5. 组合使用优化组件**

详见 [最佳实践](BEST_PRACTICES.md) 和 [性能调优指南](PERFORMANCE_TUNING.md)。
