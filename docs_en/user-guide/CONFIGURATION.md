# LiteFS Configuration Reference

> This document details all configuration options for LiteFS

## Configuration Overview

| Config Prefix | Description | Detailed Documentation |
|----------|------|----------|
| `litefs.enabled` | Whether to enable LiteFS | [Basic Configuration](#basic-configuration) |
| `litefs.node-id` | Node unique identifier | [Basic Configuration](#basic-configuration) |
| `litefs.storage.*` | Storage engine configuration | [Storage Configuration](#storage-configuration) |
| `litefs.access.*` | URL access configuration | [Access Configuration](#access-configuration) |
| `litefs.replication.*` | Replica replication configuration | [Replication Configuration](#replication-configuration) |
| `litefs.remote.*` | Remote access configuration | [Remote Configuration](#remote-configuration) |
| `litefs.cache.*` | Cache configuration | [Cache Configuration](#cache-configuration) |
| `litefs.load-balance.*` | Load balancing configuration | [Load Balancing Configuration](#load-balancing-configuration) |
| `litefs.thumbnail.*` | Thumbnail configuration | [Thumbnail Configuration](#thumbnail-configuration) |

## Detailed Configuration

### Basic Configuration

- **Config Key**: `litefs.enabled`
- **Default**: `true`
- **Type**: `Boolean`
- **Description**: Whether to enable LiteFS auto-configuration

---

- **Config Key**: `litefs.node-id`
- **Default**: `node-1`
- **Type**: `String`
- **Description**: Unique identifier for current node, must be different in distributed environment

### Storage Configuration

- **Config Key**: `litefs.storage.type`
- **Default**: `local`
- **Type**: `String`
- **Description**: Storage engine type, currently only supports `local`

---

- **Config Key**: `litefs.storage.path`
- **Default**: `./data/files`
- **Type**: `String`
- **Description**: File storage path

---

- **Config Key**: `litefs.storage.metadata-type`
- **Default**: `h2`
- **Type**: `String` (Optional values: `h2` / `mysql`)
- **Description**: Metadata storage type

---

- **Config Key**: `litefs.storage.jdbc-url`
- **Default**: None
- **Type**: `String`
- **Description**: Database connection URL
- **Example**:
  ```yaml
  litefs:
    storage:
      jdbc-url: jdbc:h2:./data/litefs;AUTO_SERVER=TRUE
  ```

---

- **Config Key**: `litefs.storage.jdbc-username`
- **Default**: `sa`
- **Type**: `String`
- **Description**: Database username

---

- **Config Key**: `litefs.storage.jdbc-password`
- **Default**: `""`
- **Type**: `String`
- **Description**: Database password

### Access Configuration

- **Config Key**: `litefs.access.url-type`
- **Default**: `direct`
- **Type**: `String` (Optional values: `gateway` / `direct`)
- **Description**: URL generation mode, `gateway` accesses through unified gateway, `direct` directly accesses the node where the file is located

---

- **Config Key**: `litefs.access.gateway.base-url`
- **Type**: `String`
- **Description**: Gateway base URL

---

- **Config Key**: `litefs.access.gateway.path-prefix`
- **Type**: `String`
- **Description**: Gateway path prefix

---

- **Config Key**: `litefs.access.signed.enabled`
- **Default**: `true`
- **Type**: `Boolean`
- **Description**: Whether to enable signed URL

---

- **Config Key**: `litefs.access.signed.secret-key`
- **Type**: `String`
- **Description**: Signature secret key

---

- **Config Key**: `litefs.access.signed.default-expire`
- **Default**: `3600`
- **Type**: `Long`
- **Description**: Signed URL default expiration time (seconds)

### Replication Configuration

- **Config Key**: `litefs.replication.enabled`
- **Default**: `true`
- **Type**: `Boolean`
- **Description**: Whether to enable replica replication (Note: In single-node mode, even if enabled, it will automatically skip)

---

- **Config Key**: `litefs.replication.default-strategy`
- **Default**: `STANDARD`
- **Type**: `String` (Optional values: `NONE` / `MINIMAL` / `STANDARD` / `HIGH` / `ALL_NODES`)
- **Description**: Default replica strategy

---

- **Config Key**: `litefs.replication.consistency`
- **Default**: `EVENTUAL`
- **Type**: `String` (Optional values: `EVENTUAL` / `STRONG`)
- **Description**: Consistency level

---

- **Config Key**: `litefs.replication.queue.type`
- **Default**: None (uses in-memory queue when not configured)
- **Type**: `String` (Optional values: `redis` / `memory`)
- **Description**: Message queue type. `redis` uses Redis pub/sub; `memory` uses in-process queue (single-node only)

### Remote Configuration

- **Config Key**: `litefs.remote.enabled`
- **Default**: `false`
- **Type**: `Boolean`
- **Description**: Whether to enable distributed mode

---

- **Config Key**: `litefs.remote.connect-timeout`
- **Default**: `5000`
- **Type**: `Integer`
- **Description**: Connection timeout (milliseconds)

---

- **Config Key**: `litefs.remote.read-timeout`
- **Default**: `30000`
- **Type**: `Integer`
- **Description**: Read timeout (milliseconds)

### Cache Configuration

- **Config Key**: `litefs.cache.enabled`
- **Default**: `false`
- **Type**: `Boolean`
- **Description**: Whether to enable metadata cache

---

- **Config Key**: `litefs.cache.type`
- **Default**: `local`
- **Type**: `String` (Optional values: `local` / `redis`)
- **Description**: Cache type. `local` will be automatically disabled in distributed mode

---

- **Config Key**: `litefs.cache.max-size`
- **Default**: `10000`
- **Type**: `Integer`
- **Description**: Maximum number of local cache entries (local mode only)

---

- **Config Key**: `litefs.cache.ttl`
- **Default**: `300000`
- **Type**: `Long`
- **Description**: Cache expiration time (milliseconds)

### Load Balancing Configuration

- **Config Key**: `litefs.load-balance.node-selector`
- **Default**: `round-robin`
- **Type**: `String` (Optional values: `round-robin` / `capacity`)
- **Description**: Node selection strategy

---

- **Config Key**: `litefs.load-balance.replica-placer`
- **Default**: `balanced`
- **Type**: `String` (Optional values: `balanced` / `locality`)
- **Description**: Replica placement strategy

### Thumbnail Configuration

- **Config Key**: `litefs.thumbnail.enabled`
- **Default**: `false`
- **Type**: `Boolean`
- **Description**: Whether to enable thumbnail generation

---

- **Config Key**: `litefs.thumbnail.default-size`
- **Default**: `small`
- **Type**: `String`
- **Description**: Default thumbnail size

## Complete Configuration Example

```yaml
litefs:
  enabled: true
  node-id: node-1

  storage:
    type: local
    path: ./data/files
    metadata-type: h2
    jdbc-url: jdbc:h2:./data/litefs;AUTO_SERVER=TRUE
    jdbc-username: sa
    jdbc-password: ""

  access:
    url-type: direct
    gateway:
      base-url: http://localhost:8080
      path-prefix: /api/files
    signed:
      enabled: true
      secret-key: your-secret-key
      default-expire: 3600

  cache:
    enabled: true
    type: local
    max-size: 10000
    ttl: 300000

  replication:
    enabled: true
    default-strategy: STANDARD
    consistency: EVENTUAL
    queue:
      type: redis
      topic: litefs.replication
      redis:
        host: 127.0.0.1
        port: 6379

  remote:
    enabled: false
    connect-timeout: 5000
    read-timeout: 30000

  load-balance:
    node-selector: round-robin
    replica-placer: balanced

  thumbnail:
    enabled: true
    default-size: small
    sizes:
      small:
        max-edge: 200
      medium:
        max-edge: 400
      large:
        max-edge: 800
```

## Related Documents

- [Quick Start](../getting-started/QUICK_START.md) - 5-minute getting started
- [Performance Tuning Guide](PERFORMANCE_TUNING.md) - Performance optimization suggestions
