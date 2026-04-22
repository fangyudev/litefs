# LiteFS FAQ

> This document collects common user questions and answers, helping you better understand LiteFS design philosophy and use cases.

---

## Table of Contents

1. [What's the difference between LiteFS and FastDFS?](#whats-the-difference-between-litefs-and-fastdfs)
2. [What scenarios is LiteFS suitable for?](#what-scenarios-is-litefs-suitable-for)
3. [What storage backends does LiteFS support?](#what-storage-backends-does-litefs-support)
4. [Does LiteFS support distributed deployment?](#does-litefs-support-distributed-deployment)
5. [How to extend storage engine?](#how-to-extend-storage-engine)
6. [Performance Optimization Topics](#performance-optimization-topics)
   - [What performance optimizations has LiteFS done?](#what-performance-optimizations-has-litefs-done)
   - [How to choose the right optimization strategy?](#how-to-choose-the-right-optimization-strategy)
   - [Best practices for performance optimization](#best-practices-for-performance-optimization)

---

## What's the difference between LiteFS and FastDFS?

### Answer

| Feature | LiteFS | FastDFS |
|------|--------|---------|
| Focus | Embedded SDK | Standalone service |
| Deployment | Integrated into application | Requires standalone deployment |
| Dependencies | Reuses existing middleware | Requires standalone deployment |
| Storage Backend | Pluggable (local storage, extensible) | Fixed local storage |
| Spring Integration | Native support | Requires client configuration |
| Maintenance Status | Active development | Maintenance mode |

**LiteFS is more lightweight, suitable for embedding into applications; FastDFS is a standalone service requiring additional operations.**

---

## What scenarios is LiteFS suitable for?

### Answer

#### Suitable scenarios

1. **Applications requiring file management**
   - CMS content management systems
   - E-commerce platform product image management
   - Document management systems
   - Enterprise cloud drives
   - Various enterprise management systems, such as: ERP, CRM, etc.

2. **Projects requiring quick integration**
   - Spring Boot applications
   - Don't want to repeatedly develop file management functions

3. **Requiring distributed deployment**
   - Multi-node storage
   - High availability requirements

4. **Private deployment requirements**
   - Data compliance requirements
   - Cannot use public cloud storage

---

## What storage backends does LiteFS support?

### Answer

Current version built-in support:

| Storage Engine | Description | Applicable Scenario |
|---------|------|---------|
| LocalStorageEngine | Local filesystem | Development testing, standalone deployment, private deployment |
| HttpRemoteStorageEngine | HTTP remote storage proxy | Distributed deployment, cross-node access |

**Extension support:**

LiteFS uses SPI design, you can extend support for other storage backends by implementing `StorageEngine` interface:
- AWS S3
- MinIO
- Alibaba Cloud OSS
- Tencent Cloud COS
- Other object storage services

See [SPI Extension Guide](../developer-guide/SPI_DEVELOPER_GUIDE.md) for details.

---

## Does LiteFS support distributed deployment?

### Answer

**Yes!** LiteFS provides complete distributed features:

1. **Service Registration & Discovery**
   - Static (manual configuration): Manually configure node information, suitable for development testing
   - Nacos: Auto-discover available nodes, recommended for production

2. **Load Balancing**
   - Capacity priority strategy
   - Round-robin strategy

3. **Data Replication**
   - Synchronous replication
   - Asynchronous replication
   - Multi-replica strategy

4. **Failover**
   - Auto-detect node failures
   - Auto-switch to healthy nodes

5. **Cross-node Access**
   - HTTP method (simple and easy to use, no additional dependencies, supports streaming transmission)

6. **URL Generation**
   - Gateway mode: Access files through unified gateway
   - Direct access mode: Dynamically generate URL based on file's node

See [Deployment Guide](../operations/DEPLOYMENT.md) and [API Usage Guide](API_GUIDE.md) for details.

---

## How to extend storage engine?

### Answer

LiteFS uses SPI (Service Provider Interface) design, you can easily extend to support other storage backends.

#### Implementation Steps

**1. Implement StorageEngine Interface**

```java
public class MyStorageEngine implements StorageEngine {

    @Override
    public String write(String fileId, InputStream data) {
        // Implement file write logic
    }

    @Override
    public InputStream read(String fileId) {
        // Implement file read logic
    }

    @Override
    public void delete(String fileId) {
        // Implement file delete logic
    }

    @Override
    public boolean exists(String fileId) {
        // Implement file existence check
    }

    @Override
    public long getSize(String fileId) {
        // Implement get file size
    }

    @Override
    public String getStoragePath(String fileId) {
        // Return storage path
    }

    @Override
    public long append(String fileId, InputStream data) {
        // Implement append write (for multipart upload)
    }
}
```

**2. Register to Spring Container**

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

**3. Configure to use**

```yaml
litefs:
  storage:
    type: my-storage  # Custom type
```

#### Extension Examples

You can refer to `LocalStorageEngine` implementation to develop your own storage engine. See [SPI Extension Guide](../developer-guide/SPI_DEVELOPER_GUIDE.md) for details.

---

## Still have other questions?

If you have other questions, welcome to:
- Submit [GitHub Issue](https://github.com/fangyudev/litefs/issues)
- Check [Architecture Design](../developer-guide/ARCHITECTURE.md)

---

## Performance Optimization Topics

### What performance optimizations has LiteFS done?

#### Answer

LiteFS has optimized performance from multiple dimensions:

---

#### 1. I/O Buffer Optimization

**Problem:** Default 8KB buffer is inefficient for large file transfers.

**Optimization:**
```java
// Before optimization: 8KB buffer
private static final int DEFAULT_BUFFER_SIZE = 8192;

// After optimization: 32KB default buffer + 256KB large file buffer
private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;    // 32KB
private static final int LARGE_BUFFER_SIZE = 256 * 1024;     // 256KB
```

**Optimization Principle:**
- Larger buffer means fewer system calls
- 32KB is the best balance point for most scenarios (memory usage vs performance)
- 256KB is suitable for large file scenarios, further reduces I/O operations

**Performance Improvement:**
- Small file transfer: ~20-30% improvement
- Large file transfer: ~50-100% improvement

**Usage Example:**
```java
// Normal file copy
IoUtils.copy(inputStream, outputStream);

// Large file copy (auto uses 256KB buffer)
IoUtils.copyLarge(inputStream, outputStream);

// Specify buffer size
IoUtils.copy(inputStream, outputStream, 64 * 1024);
```

---

#### 2. Metadata Cache Mechanism

**Problem:** When frequently accessing hot files, each access requires database query, slow response, high database pressure.

**Optimization:** Use pluggable cache decorator, supports local cache (LRU + TTL) and Redis distributed cache.

**Spring Boot Configuration:**

```yaml
# Standalone mode - use local cache
litefs:
  cache:
    enabled: true
    type: local           # Single node only
    max-size: 10000       # Max cache entries
    ttl: 300000           # Expiration time (milliseconds), 5 minutes

# Distributed mode - use Redis cache
litefs:
  cache:
    enabled: true
    type: redis           # Recommended for distributed environment
    ttl: 300000           # Expiration time (milliseconds), 5 minutes
    # Redis config auto reuses replication.queue.redis when not filled
    redis:
      key-prefix: litefs:meta:
```

> **Note:** In distributed mode, `cache.type=local` will be automatically disabled to avoid data inconsistency caused by cache not being synchronized across nodes. Cache won't take effect when Redis is not configured.

**Core Features:**

| Feature | Description |
|-----|------|
| Cache Aside Pattern | Read: miss fills cache; Write: write DB + delete cache, avoid concurrent dirty data |
| LRU Eviction | When cache is full, evict least recently used entries (local mode) |
| TTL Expiration | Support setting cache entry survival time |
| Distributed Sharing | Redis mode shares cache data across multiple nodes |
| Degradation | Auto-disable cache when Redis is unavailable in distributed mode, won't degrade to local cache |

---

#### 3. Batch Operation Optimization

**Problem:** Batch inserting/deleting files, per-item database operation is extremely inefficient.

**Optimization:** Use JDBC Batch and transaction batch commit

```java
// Before optimization: per-item insert
for (FileMetadata metadata : metadataList) {
    metadataStore.save(metadata);  // One SQL per item
}

// After optimization: batch insert
BatchMetadataStore batchStore = new BatchMetadataStore(delegate, jdbcUrl, user, pass);
batchStore.saveBatch(metadataList);  // Submit multiple SQLs at once
```

**Performance Comparison:**
| Operation | Per-item Execution | Batch Execution | Improvement |
|-----|---------|---------|------|
| Insert 1000 items | ~5000ms | ~200ms | 25x |
| Delete 1000 items | ~3000ms | ~100ms | 30x |

---

#### 4. Concurrent Processing Optimization

**Problem:** Serial processing of multiple file operations, low CPU utilization, long total time.

**Optimization:** Provide dedicated file operation thread pool

```java
// Create thread pool (auto-configured based on CPU cores)
FileOperationExecutor executor = new FileOperationExecutor(4, 100);

// Execute multiple file operations in parallel
List<CompletableFuture<String>> futures = new ArrayList<>();
for (String fileId : fileIds) {
    futures.add(executor.submit(() -> fileClient.copy(fileId)));
}

// Wait for all tasks to complete
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

// Shutdown thread pool
executor.shutdown();
```

**Thread Pool Configuration:**
- Core threads = CPU cores
- Max threads = CPU cores × 2
- Queue capacity = configurable (default 1000)
- Rejection policy = CallerRunsPolicy (caller executes)

**Use Cases:**
- Batch file copy
- Batch file delete
- Batch thumbnail generation
- Parallel data migration

---

### How to choose the right optimization strategy?

#### Answer

| Problem | Recommended Solution |
|------|----------|
| Hot file access slow | Use MetadataCache |
| Large file transfer slow | Use IoUtils.copyLarge() |
| Batch operation time-consuming | Use BatchMetadataStore |
| Multi-file operations serial execution slow | Use FileOperationExecutor |
| High concurrency scenario | Combine: cache + thread pool + batch operations |

See [Performance Tuning Guide](PERFORMANCE_TUNING.md) for details.

---

### Best practices for performance optimization

#### Answer

**1. Reasonably set cache type and size**
```yaml
# Standalone mode: use local cache
litefs:
  cache:
    enabled: true
    type: local
    max-size: 10000
    ttl: 300000

# Distributed mode: must use Redis cache
litefs:
  cache:
    enabled: true
    type: redis
    ttl: 300000
```

**2. Distributed environment notes**
```yaml
# Error: using local cache in distributed mode will cause data inconsistency
litefs:
  remote:
    enabled: true
  cache:
    type: local          # Will be automatically disabled!

# Correct: use Redis cache in distributed mode
litefs:
  remote:
    enabled: true
  cache:
    type: redis
```

**3. Choose appropriate batch size**

**4. Thread pool size tuning**
```java
// I/O intensive tasks: threads = CPU cores × 2
// CPU intensive tasks: threads = CPU cores + 1
FileOperationExecutor executor = new FileOperationExecutor(
    Runtime.getRuntime().availableProcessors() * 2,
    100
);
```

**5. Combine use of optimization components**

See [Best Practices](BEST_PRACTICES.md) and [Performance Tuning Guide](PERFORMANCE_TUNING.md) for details.
