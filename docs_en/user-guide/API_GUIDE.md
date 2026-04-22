# LiteFS API Usage Guide

> This document provides detailed API usage for LiteFS, helping developers quickly integrate and use LiteFS file storage components.

## Table of Contents

- [Quick Start](#quick-start)
- [Core API](#core-api)
- [File Operations](#file-operations)
- [Multipart Upload](#multipart-upload)
- [Metadata Management](#metadata-management)
- [URL Generation](#url-generation)
- [Distributed Features](#distributed-features)
- [SPI Extensions](#spi-extensions)
- [Configuration Reference](#configuration-reference)
- [Best Practices](#best-practices)

---

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>io.github.fangyudev</groupId>
    <artifactId>litefs-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configuration File

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

### 3. Usage Example

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

## Core API

### FileClient Interface

`FileClient` is the core interface of LiteFS, providing all file operation capabilities.

```java
public interface FileClient {
    // File operations
    String upload(InputStream inputStream, String fileName, Map<String, String> metadata);
    String upload(InputStream inputStream, String fileName, Map<String, String> metadata, UploadOptions options);
    InputStream download(String fileId);
    void delete(String fileId);
    String copy(String fileId);
    String copy(String fileId, boolean replicate);
    void rename(String fileId, String newFileName);

    // Metadata operations
    FileMetadata getMetadata(String fileId);
    void updateMetadata(String fileId, Map<String, String> metadata);
    List<FileMetadata> listFiles(FileQuery query);

    // URL operations
    String getUrl(String fileId);
    String getUrl(String fileId, long expireSeconds);
    String getDownloadUrl(String fileId);

    // Thumbnail operations
    String getThumbnailUrl(String fileId);
    InputStream downloadThumbnail(String fileId);

    // Multipart upload
    InitMultipartUploadResult initMultipartUpload(String fileName, long fileSize, Map<String, String> metadata);
    String uploadPart(String uploadId, int partNumber, InputStream inputStream);
    String completeMultipartUpload(String uploadId, List<PartInfo> parts);
    void abortMultipartUpload(String uploadId);
    MultipartUpload getMultipartUpload(String uploadId);
}
```

### UrlGenerator Interface

`UrlGenerator` is used to generate file access URLs.

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

## File Operations

### Simple Upload

Suitable for small files (recommended under 5MB):

```java
// Basic upload
String fileId = fileClient.upload(
    inputStream,
    "document.pdf",
    null
);

// Upload with metadata
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

### UploadOptions Parameter Description

`UploadOptions` is used to dynamically override default configuration settings during upload, providing more flexible upload control:

| Parameter | Description | When to Use Config Default |
|------|------|----------------------|
| `generateThumbnail` | Whether to generate thumbnail | Uses `litefs.thumbnail.enabled` when `null` |
| `thumbnailSize` | Thumbnail size name | Uses `litefs.thumbnail.default-size` when `null` |
| `visibility` | File access permission | Defaults to `PRIVATE` when not set |
| `expireTime` | Expiration timestamp (milliseconds) | Used with `visibility=TEMPORARY` |
| `customMetadata` | User-defined metadata | - |

**Usage Scenarios**:

```java
// Scenario 1: Temporary file upload (override default private permission)
String fileId = fileClient.upload(
    inputStream,
    "temp-report.pdf",
    metadata,
    UploadOptions.builder()
        .visibility(FileVisibility.TEMPORARY)
        .expireTime(System.currentTimeMillis() + 3600_000) // Expires in 1 hour
        .build()
);

// Scenario 2: Dynamic thumbnail generation control (override config file setting)
String fileId = fileClient.upload(
    inputStream,
    "photo.jpg",
    metadata,
    UploadOptions.builder()
        .generateThumbnail(true)   // Force generation even if disabled in config
        .thumbnailSize("large")     // Use large size instead of default in config
        .build()
);

// Scenario 3: Combined usage of multiple options
String fileId = fileClient.upload(
    inputStream,
    "document.pdf",
    metadata,
    UploadOptions.builder()
        .visibility(FileVisibility.PUBLIC)        // Public access
        .generateThumbnail(false)                 // No thumbnail generation
        .customMetadata("source", "mobile-app")   // Add custom metadata
        .build()
);
```

### Upload Image with Thumbnail

LiteFS supports automatic thumbnail generation when uploading images:

```java
// Method 1: Use default config (needs to be enabled in config file)
String fileId = fileClient.upload(inputStream, "photo.jpg", metadata);

// Method 2: Specify thumbnail generation
String fileId = fileClient.upload(
    inputStream,
    "photo.jpg",
    metadata,
    UploadOptions.builder()
        .generateThumbnail(true)
        .build()
);

// Method 3: Specify thumbnail size
String fileId = fileClient.upload(
    inputStream,
    "photo.jpg",
    metadata,
    UploadOptions.builder()
        .generateThumbnail(true)
        .thumbnailSize("medium")  // small/medium/large
        .build()
);

// Method 4: Force no thumbnail
String fileId = fileClient.upload(
    inputStream,
    "photo.jpg",
    metadata,
    UploadOptions.builder()
        .generateThumbnail(false)
        .build()
);
```

**Thumbnail Configuration**:

```yaml
litefs:
  thumbnail:
    enabled: true                      # Whether to enable thumbnail generation
    default-size: small                # Default size
    sizes:                             # Predefined sizes (long edge priority)
      small:
        max-edge: 200                  # Longest edge 200px
      medium:
        max-edge: 400                  # Longest edge 400px
      large:
        max-edge: 800                  # Longest edge 800px
```

**Thumbnail Features**:
- Uses **long edge priority** strategy, maintaining original aspect ratio
- Thumbnails maintain original format (JPEG/PNG/GIF, etc.)
- Async generation, does not block upload main flow
- Only applies to image type files (contentType starts with "image/")

### File Download

```java
// Download file
try (InputStream is = fileClient.download(fileId)) {
    // Handle file stream
    Files.copy(is, Paths.get("output.pdf"));
}
```

### File Delete

```java
// Delete file (also deletes associated thumbnails)
fileClient.delete(fileId);
```

**Note**: Deleting a file automatically deletes associated thumbnails, maintaining data consistency.

### File Copy

```java
// Create file copy, returns new file ID
String newFileId = fileClient.copy(fileId);
```

### File Rename

```java
// Change file display name (does not affect physical storage)
fileClient.rename(fileId, "new-document-name.pdf");
```

---

## Multipart Upload

Multipart upload is suitable for large files, frontend splits and uploads in parallel.

### Complete Flow

```java
// 1. Initialize multipart upload
String fileName = "large-video.mp4";
long fileSize = 1024 * 1024 * 500; // 500MB
InitMultipartUploadResult initResult = fileClient.initMultipartUpload(fileName, fileSize, null);
String uploadId = initResult.getUploadId();
// In distributed environment, subsequent operations need to be routed to initResult.getTargetNodeId() node

// 2. Upload each part (frontend splits and uploads in parallel)
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

// 3. Complete multipart upload (automatically sorted and merged by partNumber)
String fileId = fileClient.completeMultipartUpload(uploadId, parts);
```

### Cancel Multipart Upload

```java
// Cancel upload, cleanup uploaded parts
fileClient.abortMultipartUpload(uploadId);
```

### Query Uploaded Parts

During multipart upload, you can query the list of uploaded parts through `getMultipartUpload` to implement resumable upload.

```java
// Query upload session info
MultipartUpload upload = fileClient.getMultipartUpload(uploadId);
if (upload == null) {
    // Session does not exist or expired, need to reinitialize upload
    return;
}

// Get list of uploaded parts
Map<Integer, PartInfo> uploadedParts = upload.getParts();
// uploadedParts.keySet() contains all uploaded part numbers
// Can skip already uploaded parts based on this, continue uploading remaining parts

// Get session metadata
String fileName = upload.getFileName();
long fileSize = upload.getFileSize();
long expireTime = upload.getExpireTime();
```

**Use Cases**:
- Frontend page refresh to restore upload progress
- Cross-device/cross-browser resumable upload
- Confirm completed parts after upload interruption

### Multipart Upload Expiration Cleanup

LiteFS supports automatic cleanup of expired multipart upload sessions, but manual enable of scheduled cleanup task is required.

#### Configuration Method

```yaml
litefs:
  multipart:
    cleanup-enabled: true        # Enable scheduled cleanup (default false)
    cleanup-interval: 3600000    # Cleanup interval (default 1 hour, unit: milliseconds)
```

#### Cleanup Rules

| Configuration | Default | Description |
|--------|--------|------|
| Session validity | 24 hours | Upload sessions not completed within 24 hours after initialization will be marked as expired |
| Cleanup interval | 1 hour | Scan for expired sessions every hour (requires cleanup-enabled) |

**Cleanup Content**:
- Expired multipart upload session records
- Uploaded physical part files

**Notes**:
- Multipart upload should be completed within 24 hours, otherwise session and uploaded parts will be automatically cleaned
- **Application shutdown automatically cleans all incomplete multipart uploads** (no scheduled task needed)
- In Redis storage mode, session records automatically expire through TTL, scheduled task mainly cleans physical files

### Frontend Multipart Example

```javascript
// Frontend JavaScript multipart upload example
async function uploadLargeFile(file) {
    const chunkSize = 5 * 1024 * 1024; // 5MB per chunk
    const totalChunks = Math.ceil(file.size / chunkSize);

    // 1. Initialize upload
    const uploadId = await initMultipartUpload(file.name, file.size);

    // 2. Upload parts in parallel
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

    // 3. Complete upload
    const fileId = await completeMultipartUpload(uploadId, parts);
    return fileId;
}
```

---

## Metadata Management

### Get Metadata

```java
FileMetadata metadata = fileClient.getMetadata(fileId);
if (metadata != null) {
    System.out.println("File name: " + metadata.getFileName());
    System.out.println("Size: " + metadata.getFileSize() + " bytes");
    System.out.println("Type: " + metadata.getContentType());
    System.out.println("Checksum: " + metadata.getChecksum());
    System.out.println("Status: " + metadata.getStatus());
}
```

### Update Metadata

```java
// Add or update custom metadata
fileClient.updateMetadata(fileId, Map.of(
    "description", "Contract document",
    "version", "2.0",
    "approved", "true"
));
```

### Query File List

```java
// Build query conditions
FileQuery query = FileQuery.builder()
    .fileName("report")           // Filename fuzzy match
    .contentType("application/pdf") // MIME type
    .minSize(1024L)               // Min 1KB
    .maxSize(10 * 1024 * 1024L)   // Max 10MB
    .startTime(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) // Last 7 days
    .page(1)
    .pageSize(20)
    .build();

List<FileMetadata> files = fileClient.listFiles(query);
```

### FileMetadata Structure

| Field | Type | Description |
|------|------|------|
| id | String | File unique identifier |
| fileName | String | Original filename |
| contentType | String | MIME type |
| fileSize | long | File size (bytes) |
| checksum | String | MD5 checksum |
| storageNodeId | String | Storage node ID |
| storagePath | String | Storage path |
| metadata | Map<String, String> | Custom metadata |
| status | FileStatus | File status (PENDING/COMMITTED/DELETED) |
| createTime | long | Creation timestamp |
| updateTime | long | Update timestamp |
| expireTime | Long | Expiration timestamp (null means never expires) |
| thumbnailId | String | Thumbnail file ID |

---

## URL Generation

LiteFS supports two URL generation methods:

| Type | Description | Applicable Scenario |
|------|------|----------|
| **gateway** | Access through unified gateway | Deployment environment with gateway/API Gateway |
| **direct** | Access file所在节点 directly | No gateway, nodes directly expose service |

### Gateway Mode (gateway)

```yaml
litefs:
  access:
    url-type: gateway
    gateway:
      base-url: https://gateway.example.com
      path-prefix: /api/files
```

```java
// Generate permanently valid access URL
String url = fileClient.getUrl(fileId);
// Result: https://gateway.example.com/api/files/abc123
```

### Direct Access Mode (direct)

```yaml
litefs:
  access:
    url-type: direct
    direct:
      path-prefix: /api/files
```

```java
// Dynamically generate URL based on file's node
String url = fileClient.getUrl(fileId);
// Result: http://192.168.1.10:8080/api/files/abc123
```

### Permanent URL

```java
// Generate permanently valid access URL
String url = fileClient.getUrl(fileId);
// Result: http://localhost:8080/api/files/abc123
```

### Temporary Signed URL

```java
// Generate 1-hour valid signed URL
String signedUrl = fileClient.getUrl(fileId, 3600);
// Result: http://localhost:8080/api/files/abc123?token=xxx&expire=xxx
```

### Download URL

```java
// Generate URL that triggers browser download
String downloadUrl = fileClient.getDownloadUrl(fileId);
// Result: http://localhost:8080/api/files/abc123?download=true
```

### Thumbnail URL

```java
// Get thumbnail access URL
String thumbnailUrl = fileClient.getThumbnailUrl(fileId);
if (thumbnailUrl != null) {
    System.out.println("Thumbnail URL: " + thumbnailUrl);
} else {
    // No thumbnail, show default image or download original
}
```

### Thumbnail Download

```java
// Download thumbnail
InputStream thumbnail = fileClient.downloadThumbnail(fileId);
if (thumbnail != null) {
    Files.copy(thumbnail, Paths.get("thumbnail.jpg"));
} else {
    // No thumbnail, handle default case
}
```

### Signature Verification

Verify signed URL at gateway layer:

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

        // Verify signature
        if (token != null && expire != null) {
            if (!urlGenerator.validateSignature(fileId, token, expire)) {
                return ResponseEntity.status(403).body("Invalid or expired URL");
            }
        }

        // Return file
        // ...
    }
}
```

---

## Distributed Features

### Service Registry

#### Static Configuration

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

#### Nacos Service Registry

```yaml
litefs:
  registry:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: ""
      group: DEFAULT_GROUP
```

> **Note**: Current version only includes `StaticServiceRegistry` and `NacosServiceRegistry` implementations. Configuring `consul` type will fall back to static registration and output warning. If you need other registries (such as Consul, Eureka), you can implement `ServiceRegistry` interface through SPI extension.

### Load Balancing

LiteFS supports node selection strategy and replica placement strategy configuration through YAML:

```yaml
litefs:
  remote:
    enabled: true
  load-balance:
    node-selector: round-robin    # round-robin / capacity
    replica-placer: balanced      # balanced / locality
```

#### Node Selector (node-selector)

| Config Value | Strategy | Applicable Scenario |
|--------|------|----------|
| `round-robin` (default) | Round-robin selection, evenly distribute requests | Nodes have similar performance, frequent small file uploads |
| `capacity` | Capacity priority, select node with most free space | Large file uploads, uneven storage capacity cluster |

#### Replica Placer (replica-placer)

| Config Value | Strategy | Applicable Scenario |
|--------|------|----------|
| `balanced` (default) | Balanced distribution, select lower load node | Nodes have similar performance, need balanced storage load |
| `locality` | Locality placement, prefer same region nodes | Multi-region deployment, latency-sensitive |

You can also override default config through custom Bean:

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

### Replica Replication

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

### Remote Access

```yaml
litefs:
  remote:
    enabled: true           # Enable distributed mode
    connect-timeout: 5000
    read-timeout: 30000
```

**Design Note**: LiteFS uses HTTP protocol for cross-node communication, simple and easy to use, no additional dependencies, supports streaming transmission.

---

## SPI Extensions

### Storage Engine Extension

LiteFS uses SPI design, you can extend to support other storage backends by implementing `StorageEngine` interface.

```java
public class MyStorageEngine implements StorageEngine {

    @Override
    public String write(String fileId, InputStream data) {
        // Implement write logic
    }

    @Override
    public InputStream read(String fileId) {
        // Implement read logic
    }

    @Override
    public void delete(String fileId) {
        // Implement delete logic
    }

    @Override
    public boolean exists(String fileId) {
        // Implement existence check
    }

    @Override
    public long getSize(String fileId) {
        // Implement get size
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

Register to Spring container:

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

### Metadata Store Extension

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

### Service Registry Extension

```java
public class MyServiceRegistry implements ServiceRegistry {

    @Override
    public void register(StorageNode node) {
        // Register node
    }

    @Override
    public void deregister(String nodeId) {
        // Deregister node
    }

    @Override
    public List<StorageNode> discover() {
        // Discover all nodes
    }

    @Override
    public StorageNode get(String nodeId) {
        // Get specified node info
    }

    @Override
    public void heartbeat(String nodeId) {
        // Send heartbeat
    }

    @Override
    public void init() {
        // Initialize registry
    }

    @Override
    public void shutdown() {
        // Shutdown registry
    }
}
```

---

## Configuration Reference

### Complete Configuration Example

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

### Standalone Mode vs Distributed Mode

> **Note**: Default configuration is designed for standalone mode quick development and testing, **please use distributed mode for production**.

#### Standalone Mode (Default)

Standalone mode is the default configuration, no configuration needed to use. All following are default values, can be omitted:

```yaml
litefs:
  storage:
    type: local
    path: ./data/files
  access:
    url-type: direct              # Default, auto use localhost:server.port
```

**Standalone Mode Features**:
- No registry configuration needed
- No message queue configuration needed
- URL defaults to `direct` mode, auto use `http://localhost:{server.port}/api/files/{fileId}`

#### Distributed Mode

Enabling distributed mode requires `remote.enabled=true`:

```yaml
litefs:
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000

  registry:
    type: nacos  # or static
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

**Distributed Mode Features**:
- Need to configure registry (Static / Nacos)
- Need to configure message queue (Redis / Memory)
- Support cross-node file access and replication

### Storage Engine Configuration

#### Local Storage (Default)

```yaml
litefs:
  storage:
    type: local
    path: ./data/files
```

#### Extended Storage Engine

By implementing `StorageEngine` interface, you can extend support for:
- AWS S3
- MinIO
- Alibaba Cloud OSS
- Tencent Cloud COS
- Qiniu Cloud
- Other object storage services

---

## Best Practices

### 1. File Size Selection

| File Size | Recommended Method | Description |
|---------|---------|------|
| < 5MB | Simple upload | One-time upload, simple and efficient |
| 5MB - 100MB | Multipart upload | Frontend split, parallel upload |
| > 100MB | Multipart upload + Resume | Support interruption recovery |

### 2. Metadata Design

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

### 3. URL Security

```java
// Sensitive files use temporary signed URL
String url = fileClient.getUrl(fileId, 300); // 5 minutes valid

// Public files use permanent URL
String url = fileClient.getUrl(fileId);
```

### 4. Error Handling

```java
try {
    String fileId = fileClient.upload(inputStream, fileName, metadata);
} catch (Exception e) {
    log.error("File upload failed: {}", fileName, e);
    // Handle based on exception type
    if (e instanceof IllegalStateException) {
        // Insufficient storage space
    } else if (e instanceof IllegalArgumentException) {
        // Parameter error
    }
}
```

### 5. Resource Release

```java
// Ensure stream is closed
try (InputStream is = fileClient.download(fileId)) {
    // Handle file
} catch (IOException e) {
    log.error("File download failed", e);
}
```

### 6. Distributed Deployment

```yaml
# Production environment recommended configuration
litefs:
  node-id: ${NODE_ID:node-1}
  storage:
    type: local
    metadata-type: mysql           # Use MySQL cluster
  replication:
    enabled: true
    consistency: EVENTUAL          # High performance scenario
    queue:
      type: redis                  # High throughput scenario use Redis
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  load-balance:
    node-selector: round-robin     # Round-robin load balancing
    replica-placer: balanced       # Balanced replica distribution
```

---

## Common Questions

### Q: How to implement resumable upload?

A: When multipart upload, save uploadId, after interruption call `getMultipartUpload(uploadId)` to query list of uploaded parts, skip completed parts, continue uploading remaining parts.

### Q: How to implement automatic file expiration cleanup?

A: Set expireTime, scheduled task scans and deletes expired files.

### Q: How to choose between standalone mode and distributed mode?

A:
- Standalone mode: Suitable for development testing, small-scale applications
- Distributed mode: Suitable for production environments, high availability requirements

### Q: How to extend support for other storage backends?

A: Implement `StorageEngine` interface, refer to `LocalStorageEngine` implementation.

---

## Related Documents

- [Architecture Design](../developer-guide/ARCHITECTURE.md) - System architecture and design decisions
- [Configuration Reference](CONFIGURATION.md) - All configuration options
- [Best Practices](BEST_PRACTICES.md) - Recommended production usage
- [FAQ](FAQ.md) - More Q&A
