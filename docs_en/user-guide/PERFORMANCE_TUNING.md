# LiteFS Performance Tuning Guide

> This document provides LiteFS performance optimization strategies and best configuration recommendations

## Performance Optimization Overview

LiteFS has been optimized in the following dimensions:

| Optimization Dimension | Component | Description |
|----------|------|------|
| I/O Buffer | `IoUtils` | Default 32KB, 256KB for large files |
| Metadata Cache | `MetadataCache` | Supports Local (LRU + TTL) and Redis distributed cache |
| Batch Operations | `BatchMetadataStore` | JDBC Batch + transaction batch commit |
| Concurrent Processing | `FileOperationExecutor` | Thread pool based on CPU cores |

## I/O Buffer Optimization

The default 8KB buffer is inefficient for large file transfers. LiteFS optimizes to:
- **Default buffer**: 32KB — Best balance for most scenarios
- **Large file buffer**: 256KB — Further reduces I/O operations

**Performance Improvement**: ~20-30% for small file transfer, ~50-100% for large file transfer

```java
// Normal file copy
IoUtils.copy(inputStream, outputStream);

// Large file copy (auto uses 256KB buffer)
IoUtils.copyLarge(inputStream, outputStream);
```

## Metadata Cache

### Standalone Mode — Local Cache

```yaml
litefs:
  cache:
    enabled: true
    type: local
    max-size: 10000
    ttl: 300000
```

### Distributed Mode — Redis Cache

```yaml
litefs:
  cache:
    enabled: true
    type: redis
    ttl: 300000
    redis:
      key-prefix: litefs:meta:
```

> **Note**: In distributed mode, `cache.type=local` will be automatically disabled to avoid data inconsistency caused by cache not being synchronized across nodes.

### Core Features

| Feature | Description |
|------|------|
| Cache Aside Pattern | Read: miss fills cache; Write: write DB + delete cache |
| LRU Eviction | When cache is full, evict least recently used entries (local mode) |
| TTL Expiration | Support setting cache entry survival time |
| Distributed Sharing | Redis mode shares cache data across multiple nodes |
| Graceful Degradation | Automatically disable cache when Redis is unavailable |

## Batch Operation Optimization

Using JDBC Batch and transaction batch commit greatly improves batch operation performance:

| Operation | Per-item Execution | Batch Execution | Improvement |
|------|---------|---------|------|
| Insert 1000 items | ~5000ms | ~200ms | 25x |
| Delete 1000 items | ~3000ms | ~100ms | 30x |

```java
BatchMetadataStore batchStore = new BatchMetadataStore(delegate, jdbcUrl, user, pass);
batchStore.saveBatch(metadataList);
```

## Concurrent Processing Optimization

```java
// Auto-configured based on CPU cores
FileOperationExecutor executor = new FileOperationExecutor(4, 100);

// Execute multiple file operations in parallel
List<CompletableFuture<String>> futures = new ArrayList<>();
for (String fileId : fileIds) {
    futures.add(executor.submit(() -> fileClient.copy(fileId)));
}
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
executor.shutdown();
```

**Thread Pool Configuration Strategy**:
- Core threads = CPU cores
- Max threads = CPU cores × 2
- Queue capacity = configurable (default 1000)
- Rejection policy = CallerRunsPolicy (caller executes)

## Performance Optimization Selection Guide

| Problem | Recommended Solution |
|------|----------|
| Hot file access slow | Use MetadataCache |
| Large file transfer slow | Use IoUtils.copyLarge() |
| Batch operation time-consuming | Use BatchMetadataStore |
| Multi-file operations serial execution slow | Use FileOperationExecutor |
| High concurrency scenario | Combine: cache + thread pool + batch operations |

## Related Documents

- [Configuration Reference](CONFIGURATION.md) - Cache and other configuration options
- [Best Practices](BEST_PRACTICES.md) - Recommended production usage
- [FAQ](FAQ.md) - Performance optimization topic
