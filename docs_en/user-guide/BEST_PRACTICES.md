# LiteFS Best Practices

> This document provides recommended usage of LiteFS in production environments

## File Size Selection

| File Size | Recommended Method | Description |
|---------|---------|------|
| < 5MB | Simple upload | One-time upload, simple and efficient |
| 5MB - 100MB | Multipart upload | Frontend split, parallel upload |
| > 100MB | Multipart upload + Resume | Support interruption recovery |

## Metadata Design

```java
// Recommended metadata structure
Map<String, String> metadata = Map.of(
    "businessId", "order-123",      // Business关联 ID
    "businessType", "order",        // Business type
    "uploadedBy", "user-456",       // Uploader
    "description", "Order contract",       // Description
    "version", "1.0"                // Version number
);
```

## URL Security

```java
// Sensitive files use temporary signed URL
String url = fileClient.getUrl(fileId, 300); // 5 minutes valid

// Public files use permanent URL
String url = fileClient.getUrl(fileId);
```

## Error Handling

```java
try {
    String fileId = fileClient.upload(inputStream, fileName, metadata);
} catch (Exception e) {
    log.error("File upload failed: {}", fileName, e);
    // It is recommended to record error details and decide whether to retry based on business scenario
    // Common errors: file not exists, network exception, storage unavailable, etc.
}
```

## Resource Release

```java
// Ensure stream is closed
try (InputStream is = fileClient.download(fileId)) {
    // Handle file
} catch (IOException e) {
    log.error("File download failed", e);
}
```

## Distributed Deployment Recommended Configuration

```yaml
# Production environment recommended configuration
litefs:
  node-id: ${NODE_ID:node-1}
  storage:
    type: local
    metadata-type: mysql           # Use MySQL cluster
  cache:
    enabled: true
    type: redis                    # Distributed cache
    ttl: 300000
  replication:
    enabled: true
    consistency: EVENTUAL          # High performance scenario
    queue:
      type: redis                  # High throughput scenario
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  load-balance:
    node-selector: round-robin     # Round-robin load balancing
    replica-placer: balanced       # Balanced replica distribution
```

## Cache Best Practices

### Standalone Mode

```yaml
litefs:
  cache:
    enabled: true
    type: local
    max-size: 10000
    ttl: 300000
```

### Distributed Mode

```yaml
# Must use Redis cache, local will be automatically disabled
litefs:
  cache:
    enabled: true
    type: redis
    ttl: 300000
```

> **Note**: Using local cache in distributed mode will cause data inconsistency, system will automatically disable it.

## Related Documents

- [API Usage Guide](API_GUIDE.md) - Detailed API usage
- [Configuration Reference](CONFIGURATION.md) - All configuration options
- [Performance Tuning Guide](PERFORMANCE_TUNING.md) - Performance optimization strategies
