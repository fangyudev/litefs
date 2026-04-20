# LiteFS API 使用指南

> 本文档详细介绍 LiteFS 的 API 使用方法，帮助开发者快速集成和使用 LiteFS 文件存储组件。

## 目录

- [快速开始](#快速开始)
- [核心 API](#核心-api)
- [文件操作](#文件操作)
- [分片上传](#分片上传)
- [元数据管理](#元数据管理)
- [URL 生成](#url-生成)
- [分布式特性](#分布式特性)
- [SPI 扩展](#spi-扩展)
- [配置参考](#配置参考)
- [最佳实践](#最佳实践)

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.fangyudev</groupId>
    <artifactId>litefs-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置文件

```yaml
# application.yml
litefs:
  enabled: true
  node-id: node-1
  storage:
    type: local
    path: ./data/files
    metadata-type: h2
    jdbc-url: jdbc:h2:./data/litefs;AUTO_SERVER=TRUE
  access:
    url-type: direct              # gateway / direct
    gateway:
      base-url: http://localhost:8080
      path-prefix: /api/files
    direct:
      path-prefix: /api/files
    signed:
      enabled: true
      secret-key: your-secret-key
      default-expire: 3600
```

### 3. 使用示例

```java
@Service
public class FileService {

    @Autowired
    private FileClient fileClient;

    public String uploadFile(MultipartFile file) throws IOException {
        return fileClient.upload(
            file.getInputStream(),
            file.getOriginalFilename(),
            Map.of("uploadedBy", "user-123")
        );
    }

    public InputStream downloadFile(String fileId) {
        return fileClient.download(fileId);
    }

    public String getFileUrl(String fileId) {
        return fileClient.getUrl(fileId);
    }
}
```

---

## 核心 API

### FileClient 接口

`FileClient` 是 LiteFS 的核心接口，提供所有文件操作功能。

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
  MultipartUpload getMultipartUpload(String uploadId);
}
```

### UrlGenerator 接口

`UrlGenerator` 用于生成文件访问 URL。

```java
public interface UrlGenerator {
    String generateUrl(String fileId);
    String generateSignedUrl(String fileId, long expireSeconds);
    String generateDownloadUrl(String fileId);
    String generateThumbnailUrl(String thumbnailId);
    boolean validateSignature(String fileId, String token, long expireTime);
}
```

---

## 文件操作

### 简单上传

适合小文件（建议 5MB 以下）：

```java
// 基本上传
String fileId = fileClient.upload(
    inputStream,
    "document.pdf",
    null
);

// 带元数据上传
String fileId = fileClient.upload(
    inputStream,
    "document.pdf",
    Map.of(
        "category", "contract",
        "department", "sales",
        "uploadedBy", "user-123"
    )
);
```

### UploadOptions 参数说明

`UploadOptions` 用于在上传时动态覆盖配置文件的默认设置，提供更灵活的上传控制：

| 参数 | 说明 | 何时使用配置文件默认值 |
|------|------|----------------------|
| `generateThumbnail` | 是否生成缩略图 | 值为 `null` 时使用 `litefs.thumbnail.enabled` |
| `thumbnailSize` | 缩略图尺寸名称 | 值为 `null` 时使用 `litefs.thumbnail.default-size` |
| `visibility` | 文件访问权限 | 不设置时默认为 `PRIVATE` |
| `expireTime` | 过期时间戳（毫秒） | 配合 `visibility=TEMPORARY` 使用 |
| `customMetadata` | 用户自定义元数据 | - |

**使用场景**：

```java
// 场景1：临时文件上传（覆盖默认的私有权限）
String fileId = fileClient.upload(
    inputStream,
    "temp-report.pdf",
    metadata,
    UploadOptions.builder()
        .visibility(FileVisibility.TEMPORARY)
        .expireTime(System.currentTimeMillis() + 3600_000) // 1小时后过期
        .build()
);

// 场景2：动态控制缩略图生成（覆盖配置文件设置）
String fileId = fileClient.upload(
    inputStream,
    "photo.jpg",
    metadata,
    UploadOptions.builder()
        .generateThumbnail(true)   // 即使配置文件禁用了缩略图，也强制生成
        .thumbnailSize("large")     // 使用 large 尺寸而非配置文件中的默认尺寸
        .build()
);

// 场景3：组合使用多个选项
String fileId = fileClient.upload(
    inputStream,
    "document.pdf",
    metadata,
    UploadOptions.builder()
        .visibility(FileVisibility.PUBLIC)        // 公开访问
        .generateThumbnail(false)                 // 不生成缩略图
        .customMetadata("source", "mobile-app")   // 添加自定义元数据
        .build()
);
```

### 上传图片并生成缩略图

LiteFS 支持在上传图片时自动生成缩略图：

```java
// 方式1：使用默认配置（需要在配置文件中启用）
String fileId = fileClient.upload(inputStream, "photo.jpg", metadata);

// 方式2：指定生成缩略图
String fileId = fileClient.upload(
    inputStream,
    "photo.jpg",
    metadata,
    UploadOptions.builder()
        .generateThumbnail(true)
        .build()
);

// 方式3：指定缩略图尺寸
String fileId = fileClient.upload(
    inputStream,
    "photo.jpg",
    metadata,
    UploadOptions.builder()
        .generateThumbnail(true)
        .thumbnailSize("medium")  // small/medium/large
        .build()
);

// 方式4：强制不生成缩略图
String fileId = fileClient.upload(
    inputStream,
    "photo.jpg",
    metadata,
    UploadOptions.builder()
        .generateThumbnail(false)
        .build()
);
```

**缩略图配置**：

```yaml
litefs:
  thumbnail:
    enabled: true                      # 是否启用缩略图生成
    default-size: small                # 默认尺寸
    sizes:                             # 预定义尺寸（长边优先）
      small:
        max-edge: 200                  # 最长边 200px
      medium:
        max-edge: 400                  # 最长边 400px
      large:
        max-edge: 800                  # 最长边 800px
```

**缩略图特点**：
- 采用**长边优先**策略，保持原图宽高比
- 缩略图保持原图格式（JPEG/PNG/GIF 等）
- 异步生成，不阻塞上传主流程
- 仅对图片类型文件生效（contentType 以 "image/" 开头）

### 文件下载

```java
// 下载文件
try (InputStream is = fileClient.download(fileId)) {
    // 处理文件流
    Files.copy(is, Paths.get("output.pdf"));
}
```

### 文件删除

```java
// 删除文件（同时删除关联的缩略图）
fileClient.delete(fileId);
```

**注意**：删除文件时会自动删除关联的缩略图，保持数据一致性。

### 文件复制

```java
// 创建文件副本，返回新文件ID
String newFileId = fileClient.copy(fileId);
```

### 文件重命名

```java
// 更改文件显示名称（不影响物理存储）
fileClient.rename(fileId, "new-document-name.pdf");
```

---

## 分片上传

分片上传适合大文件，前端分片后并行上传。

### 完整流程

```java
// 1. 初始化分片上传
String fileName = "large-video.mp4";
long fileSize = 1024 * 1024 * 500; // 500MB
InitMultipartUploadResult initResult = fileClient.initMultipartUpload(fileName, fileSize, null);
String uploadId = initResult.getUploadId();
// 分布式环境下，后续操作需路由到 initResult.getTargetNodeId() 节点执行

// 2. 上传各个分片（前端分片后并行上传）
List<PartInfo> parts = new ArrayList<>();
int partNumber = 1;
for (FilePart part : fileParts) {
    String eTag = fileClient.uploadPart(uploadId, partNumber, part.getInputStream());
    parts.add(PartInfo.builder()
        .partNumber(partNumber)
        .eTag(eTag)
        .size(part.getSize())
        .build());
    partNumber++;
}

// 3. 完成分片上传（自动按 partNumber 排序合并）
String fileId = fileClient.completeMultipartUpload(uploadId, parts);
```

### 取消分片上传

```java
// 取消上传，清理已上传的分片
fileClient.abortMultipartUpload(uploadId);
```

### 查询已上传分片

分片上传过程中，可以通过 `getMultipartUpload` 查询已上传的分片列表，实现断点续传。

```java
// 查询上传会话信息
MultipartUpload upload = fileClient.getMultipartUpload(uploadId);
if (upload == null) {
    // 会话不存在或已过期，需要重新初始化上传
    return;
}

// 获取已上传的分片列表
Map<Integer, PartInfo> uploadedParts = upload.getParts();
// uploadedParts.keySet() 包含所有已上传的分片序号
// 可据此跳过已上传的分片，继续上传剩余分片

// 获取会话元信息
String fileName = upload.getFileName();
long fileSize = upload.getFileSize();
long expireTime = upload.getExpireTime();
```

**适用场景**：
- 前端刷新页面后恢复上传进度
- 跨设备/跨浏览器续传
- 上传中断后确认已完成的分片

### 分片上传过期清理

LiteFS 支持自动清理过期的分片上传会话，但需要手动启用定时清理任务。

#### 配置方式

```yaml
litefs:
  multipart:
    cleanup-enabled: true        # 启用定时清理（默认 false）
    cleanup-interval: 3600000    # 清理间隔（默认 1 小时，单位：毫秒）
```

#### 清理规则

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 会话有效期 | 24 小时 | 初始化后 24 小时内未完成的上传会话将被标记为过期 |
| 清理间隔 | 1 小时 | 每小时扫描一次过期会话（需启用 cleanup-enabled） |

**清理内容**：
- 过期的分片上传会话记录
- 已上传的物理分块文件

**注意事项**：
- 分片上传应在 24 小时内完成，否则会话和已上传的分片将被自动清理
- **应用关闭时会自动清理所有未完成的分片上传**（无需启用定时任务）
- Redis 存储模式下，会话记录通过 TTL 自动过期，定时任务主要负责清理物理文件

### 前端分片示例

```javascript
// 前端 JavaScript 分片上传示例
async function uploadLargeFile(file) {
    const chunkSize = 5 * 1024 * 1024; // 5MB per chunk
    const totalChunks = Math.ceil(file.size / chunkSize);

    // 1. 初始化上传
    const uploadId = await initMultipartUpload(file.name, file.size);

    // 2. 并行上传分片
    const parts = [];
    for (let i = 0; i < totalChunks; i++) {
        const start = i * chunkSize;
        const end = Math.min(start + chunkSize, file.size);
        const chunk = file.slice(start, end);

        const formData = new FormData();
        formData.append('uploadId', uploadId);
        formData.append('partNumber', i + 1);
        formData.append('data', chunk);

        const response = await fetch('/api/files/part', {
            method: 'POST',
            body: formData
        });
        const result = await response.json();
        parts.push({ partNumber: i + 1, eTag: result.eTag });
    }

    // 3. 完成上传
    const fileId = await completeMultipartUpload(uploadId, parts);
    return fileId;
}
```

---

## 元数据管理

### 获取元数据

```java
FileMetadata metadata = fileClient.getMetadata(fileId);
if (metadata != null) {
    System.out.println("文件名: " + metadata.getFileName());
    System.out.println("大小: " + metadata.getFileSize() + " bytes");
    System.out.println("类型: " + metadata.getContentType());
    System.out.println("校验和: " + metadata.getChecksum());
    System.out.println("状态: " + metadata.getStatus());
}
```

### 更新元数据

```java
// 添加或更新自定义元数据
fileClient.updateMetadata(fileId, Map.of(
    "description", "合同文档",
    "version", "2.0",
    "approved", "true"
));
```

### 查询文件列表

```java
// 构建查询条件
FileQuery query = FileQuery.builder()
    .fileName("report")           // 文件名模糊匹配
    .contentType("application/pdf") // MIME 类型
    .minSize(1024L)               // 最小 1KB
    .maxSize(10 * 1024 * 1024L)   // 最大 10MB
    .startTime(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) // 最近7天
    .page(1)
    .pageSize(20)
    .build();

List<FileMetadata> files = fileClient.listFiles(query);
```

### FileMetadata 结构

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 文件唯一标识 |
| fileName | String | 原始文件名 |
| contentType | String | MIME 类型 |
| fileSize | long | 文件大小（字节） |
| checksum | String | MD5 校验和 |
| storageNodeId | String | 存储节点 ID |
| storagePath | String | 存储路径 |
| metadata | Map<String, String> | 自定义元数据 |
| status | FileStatus | 文件状态（PENDING/COMMITTED/DELETED） |
| createTime | long | 创建时间戳 |
| updateTime | long | 更新时间戳 |
| expireTime | Long | 过期时间戳（null 表示永不过期） |
| thumbnailId | String | 缩略图文件 ID |

---

## URL 生成

LiteFS 支持两种 URL 生成方式：

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| **gateway** | 通过统一网关访问 | 有网关/API Gateway 的部署环境 |
| **direct** | 直接访问文件所在节点 | 无网关，节点直接对外暴露服务 |

### 网关模式（gateway）

```yaml
litefs:
  access:
    url-type: gateway
    gateway:
      base-url: https://gateway.example.com
      path-prefix: /api/files
```

```java
// 生成永久有效的访问 URL
String url = fileClient.getUrl(fileId);
// 结果: https://gateway.example.com/api/files/abc123
```

### 直接访问模式（direct）

```yaml
litefs:
  access:
    url-type: direct
    direct:
      path-prefix: /api/files
```

```java
// 根据文件所在节点动态生成 URL
String url = fileClient.getUrl(fileId);
// 结果: http://192.168.1.10:8080/api/files/abc123
```


### 永久 URL

```java
// 生成永久有效的访问 URL
String url = fileClient.getUrl(fileId);
// 结果: http://localhost:8080/api/files/abc123
```

### 临时签名 URL

```java
// 生成 1 小时有效的签名 URL
String signedUrl = fileClient.getUrl(fileId, 3600);
// 结果: http://localhost:8080/api/files/abc123?token=xxx&expire=xxx
```

### 下载 URL

```java
// 生成触发浏览器下载的 URL
String downloadUrl = fileClient.getDownloadUrl(fileId);
// 结果: http://localhost:8080/api/files/abc123?download=true
```

### 缩略图 URL

```java
// 获取缩略图访问 URL
String thumbnailUrl = fileClient.getThumbnailUrl(fileId);
if (thumbnailUrl != null) {
    System.out.println("缩略图URL: " + thumbnailUrl);
} else {
    // 没有缩略图，显示默认图片或下载原图
}
```

### 缩略图下载

```java
// 下载缩略图
InputStream thumbnail = fileClient.downloadThumbnail(fileId);
if (thumbnail != null) {
    Files.copy(thumbnail, Paths.get("thumbnail.jpg"));
} else {
    // 没有缩略图，处理默认情况
}
```

### 签名验证

在网关层验证签名 URL：

```java
@RestController
public class FileController {

    @Autowired
    private GatewayUrlGenerator urlGenerator;

    @GetMapping("/api/files/{fileId}")
    public ResponseEntity<?> getFile(
            @PathVariable String fileId,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) Long expire) {

        // 验证签名
        if (token != null && expire != null) {
            if (!urlGenerator.validateSignature(fileId, token, expire)) {
                return ResponseEntity.status(403).body("Invalid or expired URL");
            }
        }

        // 返回文件
        // ...
    }
}
```

---

## 分布式特性

### 服务注册

#### 静态配置

```yaml
litefs:
  registry:
    type: static
    static:
      nodes:
        - id: node-1
          host: 192.168.1.1
          port: 8080
        - id: node-2
          host: 192.168.1.2
          port: 8080
```

#### Nacos 注册中心

```yaml
litefs:
  registry:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: ""
      group: DEFAULT_GROUP
```

> **注意**：当前版本仅内置 `StaticServiceRegistry` 和 `NacosServiceRegistry` 实现。配置 `consul` 类型时会回退到静态注册并输出警告。如需其他注册中心（如 Consul、Eureka），可通过 SPI 扩展实现 `ServiceRegistry` 接口。

### 负载均衡

LiteFS 支持通过 YAML 配置节点选择策略和副本放置策略：

```yaml
litefs:
  remote:
    enabled: true
  load-balance:
    node-selector: round-robin    # round-robin / capacity
    replica-placer: balanced      # balanced / locality
```

#### 节点选择器（node-selector）

| 配置值 | 策略 | 适用场景 |
|--------|------|----------|
| `round-robin`（默认） | 轮询选择，均匀分发请求 | 节点性能相近、小文件频繁上传 |
| `capacity` | 容量优先，选择空闲空间最多的节点 | 大文件上传、存储容量不均匀的集群 |

#### 副本放置器（replica-placer）

| 配置值 | 策略 | 适用场景 |
|--------|------|----------|
| `balanced`（默认） | 平衡分布，选择负载较低的节点 | 节点性能相近、需要均衡存储负载 |
| `locality` | 就近放置，优先选择同区域的节点 | 多区域部署、对访问延迟敏感 |

也可以通过自定义 Bean 覆盖默认配置：

```java
@Bean
public NodeSelector nodeSelector() {
    return new CapacityNodeSelector();
}

@Bean
public ReplicaPlacer replicaPlacer() {
    return new LocalityReplicaPlacer();
}
```

### 副本复制

```yaml
litefs:
  replication:
    enabled: true
    default-strategy: STANDARD  # NONE/MINIMAL/STANDARD/HIGH/ALL_NODES
    consistency: EVENTUAL       # EVENTUAL/STRONG
    queue:
      type: redis
      topic: litefs.replication
      redis:
        host: 127.0.0.1
        port: 6379
```

### 远程访问

```yaml
litefs:
  remote:
    enabled: true           # 启用分布式模式
    connect-timeout: 5000
    read-timeout: 30000
```

**设计说明**：LiteFS 使用 HTTP 协议进行跨节点通信，简单易用，无额外依赖，支持流式传输。

---

## SPI 扩展

### 存储引擎扩展

LiteFS 采用 SPI 设计，可以通过实现 `StorageEngine` 接口扩展支持其他存储后端。

```java
public class MyStorageEngine implements StorageEngine {

    @Override
    public String write(String fileId, InputStream data) {
        // 实现写入逻辑
    }

    @Override
    public InputStream read(String fileId) {
        // 实现读取逻辑
    }

    @Override
    public void delete(String fileId) {
        // 实现删除逻辑
    }

    @Override
    public boolean exists(String fileId) {
        // 实现存在性检查
    }

    @Override
    public long getSize(String fileId) {
        // 实现获取大小
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

注册到 Spring 容器：

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

### 元数据存储扩展

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

### 服务注册扩展

```java
public class MyServiceRegistry implements ServiceRegistry {

    @Override
    public void register(StorageNode node) {
        // 注册节点
    }

    @Override
    public void deregister(String nodeId) {
        // 注销节点
    }

    @Override
    public List<StorageNode> discover() {
        // 发现所有节点
    }

    @Override
    public StorageNode get(String nodeId) {
        // 获取指定节点信息
    }

    @Override
    public void heartbeat(String nodeId) {
        // 发送心跳
    }

    @Override
    public void init() {
        // 初始化注册中心
    }

    @Override
    public void shutdown() {
        // 关闭注册中心
    }
}
```

---

## 配置参考

### 完整配置示例

```yaml
litefs:
  enabled: true
  node-id: node-1

  storage:
    type: local
    path: ./data/files
    metadata-type: h2              # h2 / mysql
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

  replication:
    enabled: true
    default-strategy: STANDARD
    consistency: EVENTUAL
    queue:
      type: redis                   # redis / memory
      topic: litefs.replication
      redis:
        host: 127.0.0.1
        port: 6379
        database: 0
        timeout-ms: 2000
        key-prefix: "litefs:queue:"

  remote:
    enabled: false
    connect-timeout: 5000
    read-timeout: 30000

  load-balance:
    node-selector: round-robin
    replica-placer: balanced
```

### 单机模式 vs 分布式模式

> **注意**：默认配置是为单机模式快速开发和测试设计的，**生产环境请使用分布式模式**。

#### 单机模式（默认）

单机模式是默认配置，无需配置即可使用。以下配置均为默认值，可省略：

```yaml
litefs:
  storage:
    type: local
    path: ./data/files
  access:
    url-type: direct              # 默认值，自动使用 localhost:server.port
```

**单机模式特点**：
- 无需配置注册中心
- 无需配置消息队列
- URL 默认为 `direct` 模式，自动使用 `http://localhost:{server.port}/api/files/{fileId}`

#### 分布式模式

启用分布式模式需要配置 `remote.enabled=true`：

```yaml
litefs:
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  
  registry:
    type: nacos  # 或 static
    nacos:
      server-addr: 127.0.0.1:8848
  
  replication:
    enabled: true
    queue:
      type: redis              # redis / memory
      redis:
        host: 127.0.0.1
        port: 6379
```

**分布式模式特点**：
- 需要配置注册中心（Static / Nacos）
- 需要配置消息队列（Redis / 内存）
- 支持跨节点文件访问和复制

### 存储引擎配置

#### 本地存储（默认）

```yaml
litefs:
  storage:
    type: local
    path: ./data/files
```

#### 扩展存储引擎

通过实现 `StorageEngine` 接口，可以扩展支持：
- AWS S3
- MinIO
- 阿里云 OSS
- 腾讯云 COS
- 七牛云
- 其他对象存储服务

---

## 最佳实践

### 1. 文件大小选择

| 文件大小 | 推荐方式 | 说明 |
|---------|---------|------|
| < 5MB | 简单上传 | 一次性上传，简单高效 |
| 5MB - 100MB | 分片上传 | 前端分片，并行上传 |
| > 100MB | 分片上传 + 断点续传 | 支持中断恢复 |

### 2. 元数据设计

```java
// 推荐的元数据结构
Map<String, String> metadata = Map.of(
    "businessId", "order-123",      // 业务关联 ID
    "businessType", "order",        // 业务类型
    "uploadedBy", "user-456",       // 上传者
    "description", "订单合同",       // 描述
    "version", "1.0"                // 版本号
);
```

### 3. URL 安全

```java
// 敏感文件使用临时签名 URL
String url = fileClient.getUrl(fileId, 300); // 5分钟有效

// 公开文件使用永久 URL
String url = fileClient.getUrl(fileId);
```

### 4. 错误处理

```java
try {
    String fileId = fileClient.upload(inputStream, fileName, metadata);
} catch (Exception e) {
    log.error("文件上传失败: {}", fileName, e);
    // 根据异常类型处理
    if (e instanceof IllegalStateException) {
        // 存储空间不足
    } else if (e instanceof IllegalArgumentException) {
        // 参数错误
    }
}
```

### 5. 资源释放

```java
// 确保关闭流
try (InputStream is = fileClient.download(fileId)) {
    // 处理文件
} catch (IOException e) {
    log.error("文件下载失败", e);
}
```

### 6. 分布式部署

```yaml
# 生产环境推荐配置
litefs:
  node-id: ${NODE_ID:node-1}
  storage:
    type: local
    metadata-type: mysql           # 使用 MySQL 集群
  replication:
    enabled: true
    consistency: EVENTUAL          # 高性能场景
    queue:
      type: redis                  # 高吞吐场景使用 Redis
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  load-balance:
    node-selector: round-robin     # 轮询负载均衡
    replica-placer: balanced       # 平衡副本分布
```

---

## 常见问题

### Q: 如何实现断点续传？

A: 分片上传时保存 uploadId，中断后调用 `getMultipartUpload(uploadId)` 查询已上传的分片列表，跳过已完成的分片，继续上传剩余分片。


### Q: 如何实现文件过期自动清理？

A: 设置 expireTime，定时任务扫描过期文件并删除。

### Q: 单机模式和分布式模式如何选择？

A:
- 单机模式：适合开发测试、小规模应用
- 分布式模式：适合生产环境、高可用需求

### Q: 如何扩展支持其他存储后端？

A: 实现 `StorageEngine` 接口，参考 `LocalStorageEngine` 的实现。

---

## 相关文档

- [架构设计](../developer-guide/ARCHITECTURE.md) - 系统架构和设计决策
- [配置参考手册](CONFIGURATION.md) - 所有配置项说明
- [最佳实践](BEST_PRACTICES.md) - 生产环境推荐用法
- [常见问题](FAQ.md) - 更多问题解答
- [项目进度](../internal/PROGRESS.md) - 功能完成情况
