# LiteFS 性能调优指南

> 本文档提供 LiteFS 性能优化策略与最佳配置建议

## 性能优化概览

LiteFS 从以下维度进行了性能优化：

| 优化维度 | 组件 | 说明 |
|----------|------|------|
| I/O 缓冲区 | `IoUtils` | 默认 32KB，大文件 256KB 缓冲区 |
| 元数据缓存 | `MetadataCache` | 支持 Local（LRU + TTL）和 Redis 分布式缓存 |
| 批量操作 | `BatchMetadataStore` | JDBC Batch + 事务批量提交 |
| 并发处理 | `FileOperationExecutor` | 基于 CPU 核心数的线程池 |

## I/O 缓冲区优化

默认的 8KB 缓冲区对大文件传输效率较低，LiteFS 优化为：
- **默认缓冲区**: 32KB — 大多数场景的最佳平衡点
- **大文件缓冲区**: 256KB — 进一步减少 I/O 次数

**性能提升**: 小文件传输提升约 20-30%，大文件传输提升约 50-100%

```java
// 普通文件复制
IoUtils.copy(inputStream, outputStream);

// 大文件复制（自动使用 256KB 缓冲区）
IoUtils.copyLarge(inputStream, outputStream);
```

## 元数据缓存

### 单机模式 — 本地缓存

```yaml
litefs:
  cache:
    enabled: true
    type: local
    max-size: 10000
    ttl: 300000
```

### 分布式模式 — Redis 缓存

```yaml
litefs:
  cache:
    enabled: true
    type: redis
    ttl: 300000
    redis:
      key-prefix: litefs:meta:
```

> **注意**: 分布式模式下 `cache.type=local` 会被自动禁用，避免各节点缓存不一致导致数据覆盖。

### 核心特性

| 特性 | 说明 |
|------|------|
| Cache Aside 模式 | 读：miss 回填缓存；写：写 DB + 删缓存 |
| LRU 淘汰 | 缓存满时淘汰最久未使用的条目（local 模式） |
| TTL 过期 | 支持设置缓存条目生存时间 |
| 分布式共享 | Redis 模式下多节点共享缓存数据 |
| 优雅降级 | Redis 不可用时自动禁用缓存 |

## 批量操作优化

使用 JDBC Batch 和事务批量提交，大幅提升批量操作性能：

| 操作 | 逐条执行 | 批量执行 | 提升 |
|------|---------|---------|------|
| 插入 1000 条 | ~5000ms | ~200ms | 25x |
| 删除 1000 条 | ~3000ms | ~100ms | 30x |

```java
BatchMetadataStore batchStore = new BatchMetadataStore(delegate, jdbcUrl, user, pass);
batchStore.saveBatch(metadataList);
```

## 并发处理优化

```java
// 自动根据 CPU 核心数配置
FileOperationExecutor executor = new FileOperationExecutor(4, 100);

// 并行执行多个文件操作
List<CompletableFuture<String>> futures = new ArrayList<>();
for (String fileId : fileIds) {
    futures.add(executor.submit(() -> fileClient.copy(fileId)));
}
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
executor.shutdown();
```

**线程池配置策略**:
- 核心线程数 = CPU 核心数
- 最大线程数 = CPU 核心数 × 2
- 队列容量 = 可配置（默认 1000）
- 拒绝策略 = CallerRunsPolicy（调用者执行）

## 性能优化选择指南

| 问题 | 推荐方案 |
|------|----------|
| 热点文件访问慢 | 使用 MetadataCache |
| 大文件传输慢 | 使用 IoUtils.copyLarge() |
| 批量操作耗时长 | 使用 BatchMetadataStore |
| 多文件操作串行执行慢 | 使用 FileOperationExecutor |
| 高并发场景 | 组合使用：缓存 + 线程池 + 批量操作 |

## 相关文档

- [配置参考手册](CONFIGURATION.md) - 缓存等配置项说明
- [最佳实践](BEST_PRACTICES.md) - 生产环境推荐用法
- [常见问题](FAQ.md) - 性能优化专题
