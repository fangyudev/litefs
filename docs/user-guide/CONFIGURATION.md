# LiteFS 配置参考手册

> 本文档详细说明 LiteFS 的所有配置项

## 配置概览

| 配置前缀 | 说明 | 详细文档 |
|----------|------|----------|
| `litefs.enabled` | 是否启用 LiteFS | [基本配置](#基本配置) |
| `litefs.node-id` | 节点唯一标识 | [基本配置](#基本配置) |
| `litefs.storage.*` | 存储引擎配置 | [存储配置](#存储配置) |
| `litefs.access.*` | URL 访问配置 | [访问配置](#访问配置) |
| `litefs.replication.*` | 副本复制配置 | [复制配置](#复制配置) |
| `litefs.remote.*` | 远程访问配置 | [远程配置](#远程配置) |
| `litefs.cache.*` | 缓存配置 | [缓存配置](#缓存配置) |
| `litefs.load-balance.*` | 负载均衡配置 | [负载均衡配置](#负载均衡配置) |
| `litefs.thumbnail.*` | 缩略图配置 | [缩略图配置](#缩略图配置) |

## 详细配置说明

### 基本配置

- **配置键**: `litefs.enabled`
- **默认值**: `true`
- **类型**: `Boolean`
- **说明**: 是否启用 LiteFS 自动配置

---

- **配置键**: `litefs.node-id`
- **默认值**: `node-1`
- **类型**: `String`
- **说明**: 当前节点的唯一标识，分布式环境下每个节点必须不同

### 存储配置

- **配置键**: `litefs.storage.type`
- **默认值**: `local`
- **类型**: `String`
- **说明**: 存储引擎类型，当前仅支持 `local`

---

- **配置键**: `litefs.storage.path`
- **默认值**: `./data/files`
- **类型**: `String`
- **说明**: 文件存储路径

---

- **配置键**: `litefs.storage.metadata-type`
- **默认值**: `h2`
- **类型**: `String`（可选值: `h2` / `mysql`）
- **说明**: 元数据存储类型

---

- **配置键**: `litefs.storage.jdbc-url`
- **默认值**: 无
- **类型**: `String`
- **说明**: 数据库连接地址
- **示例**:
  ```yaml
  litefs:
    storage:
      jdbc-url: jdbc:h2:./data/litefs;AUTO_SERVER=TRUE
  ```

---

- **配置键**: `litefs.storage.jdbc-username`
- **默认值**: `sa`
- **类型**: `String`
- **说明**: 数据库用户名

---

- **配置键**: `litefs.storage.jdbc-password`
- **默认值**: `""`
- **类型**: `String`
- **说明**: 数据库密码

### 访问配置

- **配置键**: `litefs.access.url-type`
- **默认值**: `direct`
- **类型**: `String`（可选值: `gateway` / `direct`）
- **说明**: URL 生成模式，`gateway` 通过统一网关访问，`direct` 直接访问文件所在节点

---

- **配置键**: `litefs.access.gateway.base-url`
- **类型**: `String`
- **说明**: 网关基础 URL

---

- **配置键**: `litefs.access.gateway.path-prefix`
- **类型**: `String`
- **说明**: 网关路径前缀

---

- **配置键**: `litefs.access.signed.enabled`
- **默认值**: `true`
- **类型**: `Boolean`
- **说明**: 是否启用签名 URL

---

- **配置键**: `litefs.access.signed.secret-key`
- **类型**: `String`
- **说明**: 签名密钥

---

- **配置键**: `litefs.access.signed.default-expire`
- **默认值**: `3600`
- **类型**: `Long`
- **说明**: 签名 URL 默认过期时间（秒）

### 复制配置

- **配置键**: `litefs.replication.enabled`
- **默认值**: `true`
- **类型**: `Boolean`
- **说明**: 是否启用副本复制（注意：单节点模式下即使启用也会自动跳过）

---

- **配置键**: `litefs.replication.default-strategy`
- **默认值**: `STANDARD`
- **类型**: `String`（可选值: `NONE` / `MINIMAL` / `STANDARD` / `HIGH` / `ALL_NODES`）
- **说明**: 默认副本策略

---

- **配置键**: `litefs.replication.consistency`
- **默认值**: `EVENTUAL`
- **类型**: `String`（可选值: `EVENTUAL` / `STRONG`）
- **说明**: 一致性级别

---

- **配置键**: `litefs.replication.queue.type`
- **默认值**: 空（不配置时使用内存队列）
- **类型**: `String`（可选值: `redis` / `memory`）
- **说明**: 消息队列类型。`redis` 使用 Redis 发布/订阅；`memory` 使用进程内队列（仅限单节点）

### 远程配置

- **配置键**: `litefs.remote.enabled`
- **默认值**: `false`
- **类型**: `Boolean`
- **说明**: 是否启用分布式模式

---

- **配置键**: `litefs.remote.connect-timeout`
- **默认值**: `5000`
- **类型**: `Integer`
- **说明**: 连接超时时间（毫秒）

---

- **配置键**: `litefs.remote.read-timeout`
- **默认值**: `30000`
- **类型**: `Integer`
- **说明**: 读取超时时间（毫秒）

### 缓存配置

- **配置键**: `litefs.cache.enabled`
- **默认值**: `false`
- **类型**: `Boolean`
- **说明**: 是否启用元数据缓存

---

- **配置键**: `litefs.cache.type`
- **默认值**: `local`
- **类型**: `String`（可选值: `local` / `redis`）
- **说明**: 缓存类型。分布式模式下 `local` 会被自动禁用

---

- **配置键**: `litefs.cache.max-size`
- **默认值**: `10000`
- **类型**: `Integer`
- **说明**: 本地缓存最大条目数（仅 local 模式）

---

- **配置键**: `litefs.cache.ttl`
- **默认值**: `300000`
- **类型**: `Long`
- **说明**: 缓存过期时间（毫秒）

### 负载均衡配置

- **配置键**: `litefs.load-balance.node-selector`
- **默认值**: `round-robin`
- **类型**: `String`（可选值: `round-robin` / `capacity`）
- **说明**: 节点选择策略

---

- **配置键**: `litefs.load-balance.replica-placer`
- **默认值**: `balanced`
- **类型**: `String`（可选值: `balanced` / `locality`）
- **说明**: 副本放置策略

### 缩略图配置

- **配置键**: `litefs.thumbnail.enabled`
- **默认值**: `false`
- **类型**: `Boolean`
- **说明**: 是否启用缩略图生成

---

- **配置键**: `litefs.thumbnail.default-size`
- **默认值**: `small`
- **类型**: `String`
- **说明**: 默认缩略图尺寸

## 完整配置示例

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

## 相关文档

- [快速开始](../getting-started/QUICK_START.md) - 5 分钟快速上手
- [性能调优指南](PERFORMANCE_TUNING.md) - 性能优化建议
