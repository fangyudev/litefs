# LiteFS SPI Extension Guide

> This document introduces how to extend LiteFS storage engine, metadata storage and other components through SPI mechanism

## Overview

LiteFS uses SPI (Service Provider Interface) design, all core components can be replaced or extended by implementing interfaces.

## Extensible SPI Interfaces

| SPI Interface | Description | Built-in Implementations |
|----------|------|----------|
| `StorageEngine` | Storage engine | `LocalStorageEngine`, `HttpRemoteStorageEngine` |
| `MetadataStore` | Metadata storage | `H2MetadataStore`, `MySqlMetadataStore`, `BatchMetadataStore` |
| `ServiceRegistry` | Service registry | `StaticServiceRegistry`, `NacosServiceRegistry` |
| `StorageEngineRouter` | Storage engine routing | `SingleNodeStorageEngineRouter`, `DistributedStorageEngineRouter` |
| `MetadataCacheProvider` | Metadata cache provider | `LocalMetadataCacheProvider`, `RedisMetadataCacheProvider` |
| `MultipartUploadStore` | Multipart upload storage | `LocalMultipartUploadStore`, `RedisMultipartUploadStore` |
| `ReplicaMetadataStore` | Replica metadata storage | `InMemoryReplicaMetadataStore`, `H2ReplicaMetadataStore`, `MySqlReplicaMetadataStore` |
| `NodeSelector` | Node selector | `RoundRobinNodeSelector`, `CapacityNodeSelector` |
| `ReplicaPlacer` | Replica placement strategy | `BalancedReplicaPlacer`, `LocalityReplicaPlacer` |
| `MessageQueue` | Message queue | `InMemoryMessageQueue`, `RedisMessageQueue` |
| `UrlGenerator` | URL generator | `GatewayUrlGenerator`, `DirectUrlGenerator` |

## Extend Storage Engine

### 1. Implement StorageEngine Interface

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

### 2. Register to Spring Container

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

### 3. Implementation Notes

- All methods must be thread-safe
- `append()` method is used for multipart upload merging, needs to support append write
- Recommended to use immutable objects (final fields) to ensure thread safety
- Exceptions should be wrapped as `RuntimeException` with clear error messages

## Extend Metadata Storage

### 1. Implement MetadataStore Interface

```java
public class MyMetadataStore implements MetadataStore {

    @Override
    public void save(FileMetadata metadata) {
        // Save metadata
    }

    @Override
    public FileMetadata get(String fileId) {
        // Get metadata
    }

    @Override
    public List<FileMetadata> query(FileQuery query) {
        // Query metadata list
    }

    // ... other methods
}
```

### 2. Register to Spring Container

```java
@Bean
@ConditionalOnMissingBean
public MetadataStore metadataStore() {
    return new MyMetadataStore();
}
```

## Extend Service Registry

### 1. Implement ServiceRegistry Interface

```java
public class MyServiceRegistry implements ServiceRegistry {

    @Override
    public void register(StorageNode node) {
        // Register node
    }

    @Override
    public List<StorageNode> discover() {
        // Discover all nodes
    }

    @Override
    public void heartbeat(String nodeId) {
        // Send heartbeat
    }

    // ... other methods
}
```

### 2. Register to Spring Container

```java
@Bean
@ConditionalOnMissingBean
public ServiceRegistry serviceRegistry() {
    return new MyServiceRegistry();
}
```

## Thread Safety Best Practices

### Method 1: Use Immutable Objects

```java
public class S3StorageEngine implements StorageEngine {
    // All fields are final, naturally thread-safe
    private final AmazonS3 s3Client;
    private final String bucketName;

    public S3StorageEngine(AmazonS3 s3Client, String bucketName) {
        this.s3Client = Objects.requireNonNull(s3Client);
        this.bucketName = Objects.requireNonNull(bucketName);
    }
}
```

### Method 2: Use Concurrent Data Structures

```java
public class CachedStorageEngine implements StorageEngine {
    // Use ConcurrentHashMap to ensure thread safety
    private final ConcurrentHashMap<String, FileMetadata> cache = new ConcurrentHashMap<>();
}
```

## Exception Handling Specification

All exceptions should be wrapped as `RuntimeException` with clear error messages:

```java
@Override
public long store(String fileId, InputStream inputStream) {
    try {
        // Business logic
        return doStore(fileId, inputStream);
    } catch (IOException e) {
        throw new RuntimeException("Failed to store file: " + fileId, e);
    } catch (Exception e) {
        // Catch all exceptions to avoid leaking implementation details
        throw new RuntimeException("Unexpected error during store", e);
    }
}
```

## Related Documents

- [Architecture Design](ARCHITECTURE.md) - System architecture and design decisions
- [API Usage Guide](../user-guide/API_GUIDE.md) - SPI extension related API usage
- [Learning Guide](LEARNING_GUIDE.md) - Code reading path
