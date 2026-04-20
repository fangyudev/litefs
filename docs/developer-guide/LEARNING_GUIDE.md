# LiteFS 项目学习指南

> 本文档为 LiteFS 分布式文件存储项目的代码阅读指南，帮助开发者快速理解项目架构和核心实现。

---

## 目录

- [项目概述](#项目概述)
- [学习路径](#学习路径)
- [核心概念](#核心概念)
- [实现细节](#实现细节)
- [分布式特性](#分布式特性)
- [Spring Boot 集成](#spring-boot-集成)
- [测试代码](#测试代码)
- [设计模式](#设计模式)
- [快速入门](#快速入门30分钟理解核心流程)

---

## 项目概述

LiteFS 是一个功能完整的分布式文件存储系统，支持多节点存储、负载均衡、故障转移、数据复制等分布式特性。同时提供嵌入式部署能力，通过轻量级 SDK 设计，方便用户将分布式文件存储功能无缝集成到业务应用中。

### 核心特性

- **轻量级设计**：最小化依赖，支持嵌入式部署
- **可插拔架构**：通过 SPI 机制实现中间件的可插拔
- **复用现有基础设施**：不重复造轮子，复用用户系统的中间件
- **分布式特性**：支持多节点存储、负载均衡、故障转移

### 模块结构

```
litefs/
├── litefs-core/                    # 核心模块 - API、模型、SPI 接口、内置实现
├── litefs-spring-boot-starter/     # Spring Boot Starter - 自动配置
├── litefs-example/                 # 使用示例
└── docs/                           # 文档目录
```

---

## 学习路径

```
┌─────────────────────────────────────────────────────────────┐
│                    学习路径图                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 读概述 → 了解项目定位和核心特性                          │
│     [项目概述](#项目概述)                                      │
│                                                             │
│  2. 读接口 → 理解系统能力边界                                │
│     FileClient + StorageEngine + MetadataStore              │
│                                                             │
│  3. 读模型 → 理解数据结构                                    │
│     FileMetadata + StorageNode                              │
│                                                             │
│  4. 读实现 → 理解核心逻辑                                    │
│     LocalStorageEngine → H2MetadataStore → FileClientImpl   │
│                                                             │
│  5. 读测试 → 验证理解                                        │
│     FileClientIntegrationTest                               │
│                                                             │
│  6. 读扩展 → 理解分布式特性                                  │
│     ServiceRegistry + NodeSelector + ReplicationService     │
│                                                             │
│  7. 读集成 → 理解 Spring Boot 集成                           │
│     LiteFsAutoConfiguration                                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心概念

### 第一阶段：理解核心接口

从接口开始，理解 LiteFS 对外提供的能力。

#### 1. 核心 API 接口

| 文件 | 位置 | 学习重点 |
|------|------|----------|
| `FileClient.java` | `litefs-core/api/` | 文件操作的所有方法签名 |
| `UrlGenerator.java` | `litefs-core/api/` | URL 生成接口 |

**FileClient 核心方法**：

```java
public interface FileClient {
    // 文件操作
    String upload(InputStream inputStream, String fileName, Map<String, String> metadata);
    String upload(InputStream inputStream, String fileName, Map<String, String> metadata, UploadOptions options);
    InputStream download(String fileId);
    void delete(String fileId);
    String copy(String fileId);
    String copy(String fileId, boolean replicate);
    void rename(String fileId, String newFileName);

    // 元数据操作
    FileMetadata getMetadata(String fileId);
    void updateMetadata(String fileId, Map<String, String> metadata);
    List<FileMetadata> listFiles(FileQuery query);

    // URL 操作
    String getUrl(String fileId);
    String getUrl(String fileId, long expireSeconds);
    String getDownloadUrl(String fileId);

    // 缩略图操作
    String getThumbnailUrl(String fileId);
    InputStream downloadThumbnail(String fileId);

    // 分块上传
    InitMultipartUploadResult initMultipartUpload(String fileName, long fileSize, Map<String, String> metadata);
    String uploadPart(String uploadId, int partNumber, InputStream inputStream);
    String completeMultipartUpload(String uploadId, List<PartInfo> parts);
    void abortMultipartUpload(String uploadId);
    MultipartUpload getMultipartUpload(String uploadId);  // 查询已上传分片
}
```

#### 2. SPI 扩展接口

理解 LiteFS 的可插拔架构：

| 文件 | 学习重点 |
|------|----------|
| `StorageEngine.java` | 存储引擎接口 - 最核心的 SPI |
| `MetadataStore.java` | 元数据存储接口 |
| `ServiceRegistry.java` | 服务注册接口 |
| `NodeSelector.java` | 节点选择器（负载均衡） |
| `ReplicaPlacer.java` | 副本放置策略 |
| `MessageQueue.java` | 消息队列接口 |

**StorageEngine 核心方法**：

```java
public interface StorageEngine {
    String write(String fileId, InputStream data);    // 写入文件
    InputStream read(String fileId);                   // 读取文件
    void delete(String fileId);                        // 删除文件
    boolean exists(String fileId);                     // 检查存在
    long getSize(String fileId);                       // 获取大小
    long append(String fileId, InputStream data);      // 追加写入
    String getStoragePath(String fileId);              // 获取存储路径
}
```

#### 3. 数据模型

理解核心数据结构：

| 文件 | 学习重点 |
|------|----------|
| `FileMetadata.java` | 文件元数据结构 |
| `FileQuery.java` | 查询条件构建 |
| `StorageNode.java` | 存储节点模型 |
| `PartInfo.java` | 分片信息 |
| `ReplicationStrategy.java` | 副本策略 |

**FileMetadata 核心字段**：

```java
public class FileMetadata {
    private String id;                    // 文件唯一标识
    private String fileName;              // 原始文件名
    private String contentType;           // MIME 类型
    private long fileSize;                // 文件大小（字节）
    private String checksum;              // MD5 校验和
    private String storageNodeId;         // 存储节点 ID
    private String storagePath;           // 存储路径
    private Map<String, String> metadata; // 自定义元数据
    private FileStatus status;            // 文件状态
    private long createTime;              // 创建时间戳
    private long updateTime;              // 更新时间戳
    private Long expireTime;              // 过期时间戳
}
```

---

## 实现细节

### 第二阶段：理解核心实现

#### 1. 存储引擎实现

从本地存储引擎开始，理解文件如何被存储：

**文件位置**：`litefs-core/impl/store/engine/local/LocalStorageEngine.java`

**关键方法**：

| 方法 | 说明 |
|------|------|
| `write()` | 文件写入，使用文件ID哈希目录分层存储 |
| `read()` | 文件读取，支持直接传入存储路径 |
| `append()` | 追加写入，用于分片上传合并 |
| `delete()` | 物理删除文件 |
| `exists()` | 检查文件是否存在 |
| `getSize()` | 获取文件大小 |

**存储路径设计**：

```
data/files/
├── ab/                      # 第一级目录（文件ID前2位）
│   └── cd/                  # 第二级目录（文件ID第3-4位）
│       └── abcd...          # 实际文件（以完整文件ID命名）
└── ...
```

#### 2. 元数据存储实现

理解文件元数据如何管理：

**文件位置**：
- `litefs-core/impl/store/metadata/h2/H2MetadataStore.java`
- `litefs-core/impl/store/metadata/mysql/MySqlMetadataStore.java`

**数据库表结构**：

```sql
CREATE TABLE file_metadata (
    id VARCHAR(64) PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128),
    file_size BIGINT NOT NULL,
    checksum VARCHAR(64),
    storage_node_id VARCHAR(64),
    storage_path VARCHAR(512),
    metadata TEXT,
    status VARCHAR(16) NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    expire_time BIGINT
);
```

**关键方法**：

| 方法 | 说明 |
|------|------|
| `init()` | 初始化数据库表 |
| `save()` | 保存元数据 |
| `get()` | 获取单个元数据 |
| `query()` | 条件查询 |
| `update()` | 更新元数据 |
| `delete()` | 删除元数据 |

#### 3. 客户端实现（核心业务逻辑）

这是最重要的文件，串联所有组件：

**文件位置**：`litefs-core/impl/client/FileClientImpl.java`

**阅读建议**：按方法逐个阅读，理解每个操作如何协调各个组件：

```
upload()     → 创建元数据 → 存储文件 → 更新状态
download()   → 查询元数据 → 读取文件
delete()     → 标记删除 → 清理存储
copy()       → 读取原文件 → 写入新文件 → 创建元数据
```

**核心流程图**：

```
┌─────────────────────────────────────────────────────────────┐
│                      upload() 流程                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 生成文件 ID (FileIdGeneratorUtil)                       │
│            ↓                                                │
│  2. 检测 Content-Type (ContentTypeUtils)                    │
│            ↓                                                │
│  3. 计算校验和 (ChecksumUtils)                              │
│            ↓                                                │
│  4. 存储文件 (StorageEngine.write)                          │
│            ↓                                                │
│  5. 保存元数据 (MetadataStore.save)                         │
│            ↓                                                │
│  6. 触发复制 (ReplicationService)                           │
│            ↓                                                │
│  7. 返回文件 ID                                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 分布式特性

### 第三阶段：理解分布式特性

#### 1. 服务注册与发现

**文件位置**：`litefs-core/registry/`

| 实现 | 说明 | 适用场景 |
|------|------|----------|
| `StaticServiceRegistry` | 静态配置（默认） | 固定节点的小规模集群、开发测试 |
| `NacosServiceRegistry` | Nacos 注册中心 | 生产环境 |

**核心方法**：

```java
public interface ServiceRegistry {
    void register(StorageNode node);        // 注册节点
    void deregister(String nodeId);         // 注销节点
    List<StorageNode> discover();           // 发现所有节点
    StorageNode get(String nodeId);         // 获取指定节点信息
    void heartbeat(String nodeId);          // 发送心跳
    void init();                            // 初始化
    void shutdown();                        // 关闭
}
```

#### 2. 负载均衡

**文件位置**：`litefs-core/loadbalance/`

| 实现 | 说明 |
|------|------|
| `RoundRobinNodeSelector` | 轮询策略 |
| `CapacityNodeSelector` | 容量优先策略 |

**选择策略**（推荐通过 YAML 配置）：

```yaml
litefs:
  load-balance:
    node-selector: round-robin  # round-robin / capacity
```

```java
public interface NodeSelector {
    StorageNode select(List<StorageNode> nodes, FileUploadRequest request);
}
```

#### 3. 数据复制

**文件位置**：`litefs-core/replication/` 和 `litefs-core/placement/`

| 文件 | 说明 |
|------|------|
| `ReplicationService.java` | 复制服务核心 |
| `BalancedReplicaPlacer.java` | 平衡分布策略 |
| `LocalityReplicaPlacer.java` | 就近放置策略 |
| `H2ReplicaMetadataStore.java` | H2 副本元数据存储 |
| `MySqlReplicaMetadataStore.java` | MySQL 副本元数据存储 |

**副本放置策略**（推荐通过 YAML 配置）：

```yaml
litefs:
  load-balance:
    replica-placer: balanced  # balanced / locality
```

**副本策略**：

```java
public class ReplicationStrategy {
    public static ReplicationStrategy none();      // 无副本 (0个额外副本)
    public static ReplicationStrategy minimal();   // 最小副本 (1个额外副本)
    public static ReplicationStrategy standard();  // 标准副本 (2个额外副本)
    public static ReplicationStrategy high();      // 高副本 (4个额外副本，共5份)
    public static ReplicationStrategy allNodes();  // 所有节点
    public static ReplicationStrategy custom(int replicas); // 自定义
}
```

#### 4. 远程访问

**文件位置**：`litefs-core/impl/remote/`

| 文件 | 说明 |
|------|------|
| `HttpRemoteStorageEngine.java` | HTTP 方式访问远程节点 |

**设计说明**：LiteFS 定位为轻量级组件，HTTP 已能满足文件传输需求。文件传输场景的瓶颈在磁盘 IO 和网络带宽，协议开销差异可忽略。

#### 5. 故障转移

**文件位置**：`litefs-core/failover/FailoverService.java`

**工作原理**：

```
┌─────────────────────────────────────────────────────────────┐
│                      故障转移流程                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 检测节点故障（心跳超时）                                 │
│            ↓                                                │
│  2. 标记节点为不可用                                        │
│            ↓                                                │
│  3. 选择替代节点（有副本的节点）                            │
│            ↓                                                │
│  4. 重定向请求到替代节点                                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Spring Boot 集成

### 第四阶段：理解 Spring Boot 集成

#### 自动配置

**文件位置**：`litefs-spring-boot-starter/src/main/java/io/github/fangyudev/litefs/autoconfigure/`

| 文件 | 学习重点 |
|------|----------|
| `LiteFsAutoConfiguration.java` | Bean 自动装配逻辑 |
| `LiteFsProperties.java` | 配置属性绑定 |

**自动装配的 Bean**：

```java
@Bean StorageEngine storageEngine()           // 存储引擎
@Bean MetadataStore metadataStore()           // 元数据存储
@Bean UrlGenerator urlGenerator()             // URL 生成器
@Bean StorageEngineRouter storageRouter()     // 存储路由
@Bean FileClient fileClient()                 // 文件客户端
@Bean ReplicationService replicationService() // 复制服务（可选）
```

**配置属性**：

```yaml
litefs:
  enabled: true
  node-id: node-1
  storage:
    type: local
    path: ./data/files
    metadata-type: h2
    jdbc-url: jdbc:h2:./data/litefs
  access:
    url-type: direct
    gateway:
      base-url: http://localhost:8080
      path-prefix: /api/files
  replication:
    enabled: true
    default-strategy: STANDARD
  remote:
    enabled: false
```

---

## 测试代码

### 第五阶段：通过测试理解功能

测试代码是最好的文档！

**测试目录结构**：

```
litefs-core/src/test/
├── storage/
│   └── LocalStorageEngineTest.java      # 存储引擎测试
├── metadata/
│   ├── H2MetadataStoreTest.java         # H2 元数据测试
│   └── MySqlMetadataStoreTest.java      # MySQL 元数据测试
├── loadbalance/
│   ├── RoundRobinNodeSelectorTest.java  # 轮询选择器测试
│   └── CapacityNodeSelectorTest.java    # 容量选择器测试
├── registry/
│   └── StaticServiceRegistryTest.java   # 静态注册测试
└── util/
    ├── FileIdGeneratorUtilTest.java     # ID 生成测试
    ├── ChecksumUtilsTest.java           # 校验和测试
    └── IoUtilsTest.java                 # I/O 工具测试

litefs-spring-boot-starter/src/test/
└── SpringBootIntegrationTest.java       # Spring Boot 集成测试
```

**推荐阅读顺序**：

1. `LocalStorageEngineTest.java` - 理解存储引擎
2. `H2MetadataStoreTest.java` - 理解元数据存储
3. `SpringBootIntegrationTest.java` - 理解 Spring Boot 集成

---

## 设计模式

LiteFS 使用了大量设计模式，阅读代码时注意体会：

### 1. 构建器模式 (Builder Pattern)

**应用位置**：`FileMetadata.Builder`, `FileQuery.Builder`, `FileClientBuilder`

```java
FileMetadata metadata = FileMetadata.builder()
    .id(fileId)
    .fileName("document.pdf")
    .fileSize(1024)
    .build();

FileClient client = FileClientBuilder.create()
    .storageEngine(storageEngine)
    .metadataStore(metadataStore)
    .urlGenerator(urlGenerator)
    .build();
```

### 2. 策略模式 (Strategy Pattern)

**应用位置**：`StorageEngine`, `MetadataStore`, `NodeSelector`, `ReplicaPlacer`

```java
// 不同的存储引擎实现
StorageEngine engine = new LocalStorageEngine(path);
StorageEngine engine = new MyCustomStorageEngine();

// 不同的节点选择策略（推荐通过 YAML 配置）
// litefs.load-balance.node-selector: round-robin / capacity
NodeSelector selector = new RoundRobinNodeSelector();
NodeSelector selector = new CapacityNodeSelector();
```

### 3. 装饰器模式 (Decorator Pattern)

**应用位置**：`MetadataCache`（Cache Aside Pattern）

```java
// Spring Boot 配置（推荐）
// application.yml:
// litefs:
//   cache:
//     enabled: true
//     type: redis           # 分布式环境推荐
//     ttl: 300000

// 手动创建（非 Spring 环境）
MetadataStore underlyingStore = new H2MetadataStore(jdbcUrl);
MetadataCacheProvider cacheProvider = new LocalMetadataCacheProvider(10000, 300000);
MetadataCache cache = new MetadataCache(underlyingStore, cacheProvider);
```

### 4. 工厂方法模式 (Factory Method Pattern)

**应用位置**：`FileClientBuilder.build()`

### 5. 外观模式 (Facade Pattern)

**应用位置**：`FileClient` 统一接口，`NodeManager` 节点管理门面

### 6. 代理模式 (Proxy Pattern)

**应用位置**：`HttpRemoteStorageEngine`

---

## 快速入门：30分钟理解核心流程

如果想快速上手，建议只读这5个文件：

### 1. FileClient.java (5分钟)

理解 LiteFS 能做什么 - 所有文件操作的接口定义。

**位置**：`litefs-core/src/main/java/io/github/fangyudev/litefs/api/FileClient.java`

### 2. FileMetadata.java (5分钟)

理解数据结构 - 文件元数据包含哪些信息。

**位置**：`litefs-core/src/main/java/io/github/fangyudev/litefs/model/FileMetadata.java`

### 3. LocalStorageEngine.java (10分钟)

理解文件怎么存 - 最基础的存储引擎实现。

**位置**：`litefs-core/src/main/java/io/github/fangyudev/litefs/impl/store/engine/local/LocalStorageEngine.java`

**关注点**：
- 文件路径如何生成（基于文件ID哈希的多级目录）
- 读写操作如何实现
- 追加写入如何实现（分片上传合并）

### 4. H2MetadataStore.java (5分钟)

理解元数据怎么管 - 最简单的元数据存储实现。

**位置**：`litefs-core/src/main/java/io/github/fangyudev/litefs/impl/store/metadata/h2/H2MetadataStore.java`

**关注点**：
- 表结构设计
- CRUD 操作实现
- 查询条件构建

### 5. FileClientImpl.java (5分钟)

理解业务流程 - 所有组件如何协作。

**位置**：`litefs-core/src/main/java/io/github/fangyudev/litefs/impl/client/FileClientImpl.java`

**关注点**：
- `upload()` 方法 - 完整的上传流程
- `download()` 方法 - 完整的下载流程
- 各组件如何被协调使用

---

## 学习建议

### 1. 先跑起来

先运行 `litefs-example`，通过实际操作理解功能：

```bash
cd litefs-example
mvn spring-boot:run
```

访问 http://localhost:8080/quickstart/index.html 查看示例页面。

### 2. 边读边调试

在 IDE 中设置断点，跟踪代码执行流程：

- 在 `FileClientImpl.upload()` 设置断点
- 上传一个文件
- 单步执行，观察整个流程

### 3. 从简单到复杂

先理解单机模式，再理解分布式模式：

```
单机模式：FileClient + LocalStorageEngine + H2MetadataStore
    ↓
分布式模式：ServiceRegistry + NodeSelector + ReplicationService
```

### 4. 关注扩展点

LiteFS 的设计目标是可扩展，关注 SPI 接口：

- 如何实现自己的存储引擎？
- 如何实现自己的元数据存储？
- 如何实现自己的服务注册中心？

详见 [SPI 扩展开发指南](SPI_DEVELOPER_GUIDE.md)。

---

## 相关文档

- [架构设计](ARCHITECTURE.md) - 系统架构和设计决策
- [API 使用指南](../user-guide/API_GUIDE.md) - API 详细使用说明
- [常见问题](../user-guide/FAQ.md) - 问题解答
