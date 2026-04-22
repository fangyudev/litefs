# LiteFS Learning Guide

> This document is a code reading guide for LiteFS distributed file storage project, helping developers quickly understand project architecture and core implementation.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Learning Path](#learning-path)
- [Core Concepts](#core-concepts)
- [Implementation Details](#implementation-details)
- [Distributed Features](#distributed-features)
- [Spring Boot Integration](#spring-boot-integration)
- [Test Code](#test-code)
- [Design Patterns](#design-patterns)
- [Quick Start: Understand Core Flow in 30 Minutes](#quick-start-understand-core-flow-in-30-minutes)

---

## Project Overview

LiteFS is a fully functional distributed file storage system, supporting multi-node storage, load balancing, failover, data replication and other distributed features. Also provides embedded deployment capability, through lightweight SDK design, allowing users to seamlessly integrate distributed file storage functions into business applications.

### Core Features

- **Lightweight Design**: Minimized dependencies, supports embedded deployment
- **Pluggable Architecture**: Implements middleware pluggability through SPI mechanism
- **Reuse Existing Infrastructure**: Don't reinvent the wheel, reuse user system's middleware
- **Distributed Features**: Supports multi-node storage, load balancing, failover

### Module Structure

```
litefs/
├── litefs-core/                    # Core module - API, models, SPI interfaces, built-in implementations
├── litefs-spring-boot-starter/     # Spring Boot Starter - Auto configuration
├── litefs-example/                 # Usage examples
└── docs/                           # Documentation directory
```

---

## Learning Path

```
┌─────────────────────────────────────────────────────────────┐
│                    Learning Path                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Read Overview → Understand project focus and core features│
│     [Project Overview](#project-overview)                    │
│                                                              │
│  2. Read Interfaces → Understand system capability boundaries│
│     FileClient + StorageEngine + MetadataStore               │
│                                                              │
│  3. Read Models → Understand data structures                 │
│     FileMetadata + StorageNode                               │
│                                                              │
│  4. Read Implementations → Understand core logic            │
│     LocalStorageEngine → H2MetadataStore → FileClientImpl   │
│                                                              │
│  5. Read Tests → Verify understanding                       │
│     FileClientIntegrationTest                                │
│                                                              │
│  6. Read Extensions → Understand distributed features       │
│     ServiceRegistry + NodeSelector + ReplicationService      │
│                                                              │
│  7. Read Integration → Understand Spring Boot integration    │
│     LiteFsAutoConfiguration                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Concepts

### Phase 1: Understand Core Interfaces

Start from interfaces to understand capabilities provided by LiteFS.

#### 1. Core API Interfaces

| File | Location | Learning Focus |
|------|------|----------|
| `FileClient.java` | `litefs-core/api/` | All method signatures for file operations |
| `UrlGenerator.java` | `litefs-core/api/` | URL generation interface |

**FileClient Core Methods**:

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
    MultipartUpload getMultipartUpload(String uploadId);  // Query uploaded parts
}
```

#### 2. SPI Extension Interfaces

Understand LiteFS pluggable architecture:

| File | Learning Focus |
|------|----------|
| `StorageEngine.java` | Storage engine interface - most core SPI |
| `MetadataStore.java` | Metadata storage interface |
| `ServiceRegistry.java` | Service registry interface |
| `NodeSelector.java` | Node selector (load balancing) |
| `ReplicaPlacer.java` | Replica placement strategy |
| `MessageQueue.java` | Message queue interface |

**StorageEngine Core Methods**:

```java
public interface StorageEngine {
    String write(String fileId, InputStream data);    // Write file
    InputStream read(String fileId);                   // Read file
    void delete(String fileId);                        // Delete file
    boolean exists(String fileId);                     // Check existence
    long getSize(String fileId);                       // Get size
    long append(String fileId, InputStream data);      // Append write
    String getStoragePath(String fileId);              // Get storage path
}
```

#### 3. Data Models

Understand core data structures:

| File | Learning Focus |
|------|----------|
| `FileMetadata.java` | File metadata structure |
| `FileQuery.java` | Query condition builder |
| `StorageNode.java` | Storage node model |
| `PartInfo.java` | Part information |
| `ReplicationStrategy.java` | Replica strategy |

**FileMetadata Core Fields**:

```java
public class FileMetadata {
    private String id;                    // File unique identifier
    private String fileName;              // Original filename
    private String contentType;           // MIME type
    private long fileSize;                // File size (bytes)
    private String checksum;              // MD5 checksum
    private String storageNodeId;         // Storage node ID
    private String storagePath;           // Storage path
    private Map<String, String> metadata; // Custom metadata
    private FileStatus status;            // File status
    private long createTime;              // Creation timestamp
    private long updateTime;              // Update timestamp
    private Long expireTime;              // Expiration timestamp
}
```

---

## Implementation Details

### Phase 2: Understand Core Implementations

#### 1. Storage Engine Implementation

Start from local storage engine to understand how files are stored:

**File Location**: `litefs-core/impl/store/engine/local/LocalStorageEngine.java`

**Key Methods**:

| Method | Description |
|------|------|
| `write()` | File write, uses file ID hash for hierarchical directory storage |
| `read()` | File read, supports direct storage path input |
| `append()` | Append write, used for multipart upload merge |
| `delete()` | Physically delete file |
| `exists()` | Check if file exists |
| `getSize()` | Get file size |

**Storage Path Design**:

```
data/files/
├── ab/                      # First level directory (first 2 chars of file ID)
│   └── cd/                  # Second level directory (chars 3-4 of file ID)
│       └── abcd...          # Actual file (named with full file ID)
└── ...
```

#### 2. Metadata Storage Implementation

Understand how file metadata is managed:

**File Locations**:
- `litefs-core/impl/store/metadata/h2/H2MetadataStore.java`
- `litefs-core/impl/store/metadata/mysql/MySqlMetadataStore.java`

**Database Table Structure**:

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

**Key Methods**:

| Method | Description |
|------|------|
| `init()` | Initialize database table |
| `save()` | Save metadata |
| `get()` | Get single metadata |
| `query()` | Conditional query |
| `update()` | Update metadata |
| `delete()` | Delete metadata |

#### 3. Client Implementation (Core Business Logic)

This is the most important file, connecting all components:

**File Location**: `litefs-core/impl/client/FileClientImpl.java`

**Reading Suggestion**: Read method by method to understand how each operation coordinates components:

```
upload()     → Create metadata → Store file → Update status
download()   → Query metadata → Read file
delete()     → Mark deleted → Cleanup storage
copy()       → Read original file → Write new file → Create metadata
```

**Core Flow Diagram**:

```
┌─────────────────────────────────────────────────────────────┐
│                      upload() Flow                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Generate file ID (FileIdGeneratorUtil)                   │
│            ↓                                                 │
│  2. Detect Content-Type (ContentTypeUtils)                   │
│            ↓                                                 │
│  3. Calculate checksum (ChecksumUtils)                        │
│            ↓                                                 │
│  4. Store file (StorageEngine.write)                         │
│            ↓                                                 │
│  5. Save metadata (MetadataStore.save)                       │
│            ↓                                                 │
│  6. Trigger replication (ReplicationService)                 │
│            ↓                                                 │
│  7. Return file ID                                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Distributed Features

### Phase 3: Understand Distributed Features

#### 1. Service Registry & Discovery

**File Location**: `litefs-core/registry/`

| Implementation | Description | Applicable Scenario |
|------|------|----------|
| `StaticServiceRegistry` | Static configuration (default) | Fixed small-scale cluster, development testing |
| `NacosServiceRegistry` | Nacos registry center | Production environment |

**Core Methods**:

```java
public interface ServiceRegistry {
    void register(StorageNode node);        // Register node
    void deregister(String nodeId);         // Deregister node
    List<StorageNode> discover();           // Discover all nodes
    StorageNode get(String nodeId);         // Get specified node info
    void heartbeat(String nodeId);          // Send heartbeat
    void init();                            // Initialize
    void shutdown();                        // Shutdown
}
```

#### 2. Load Balancing

**File Location**: `litefs-core/loadbalance/`

| Implementation | Description |
|------|------|
| `RoundRobinNodeSelector` | Round-robin strategy |
| `CapacityNodeSelector` | Capacity priority strategy |

**Selection Strategy** (recommended via YAML configuration):

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

#### 3. Data Replication

**File Locations**: `litefs-core/replication/` and `litefs-core/placement/`

| File | Description |
|------|------|
| `ReplicationService.java` | Replication service core |
| `BalancedReplicaPlacer.java` | Balanced distribution strategy |
| `LocalityReplicaPlacer.java` | Locality placement strategy |
| `H2ReplicaMetadataStore.java` | H2 replica metadata storage |
| `MySqlReplicaMetadataStore.java` | MySQL replica metadata storage |

**Replica Placement Strategy** (recommended via YAML configuration):

```yaml
litefs:
  load-balance:
    replica-placer: balanced  # balanced / locality
```

**Replica Strategy**:

```java
public class ReplicationStrategy {
    public static ReplicationStrategy none();      // No replica (0 extra replicas)
    public static ReplicationStrategy minimal();   // Minimal replica (1 extra replica)
    public static ReplicationStrategy standard();  // Standard replica (2 extra replicas)
    public static ReplicationStrategy high();      // High replica (4 extra replicas, 5 total)
    public static ReplicationStrategy allNodes();  // All nodes
    public static ReplicationStrategy custom(int replicas); // Custom
}
```

#### 4. Remote Access

**File Location**: `litefs-core/impl/remote/`

| File | Description |
|------|------|
| `HttpRemoteStorageEngine.java` | HTTP method to access remote nodes |

**Design Note**: LiteFS is positioned as a lightweight component, HTTP is sufficient for file transfer needs. The bottleneck in file transfer scenarios is disk IO and network bandwidth, protocol overhead difference is negligible.

#### 5. Failover

**File Location**: `litefs-core/failover/FailoverService.java`

**Working Principle**:

```
┌─────────────────────────────────────────────────────────────┐
│                      Failover Flow                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Detect node failure (heartbeat timeout)                  │
│            ↓                                                 │
│  2. Mark node as unavailable                                 │
│            ↓                                                 │
│  3. Select alternative node (node with replica)             │
│            ↓                                                 │
│  4. Redirect request to alternative node                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Spring Boot Integration

### Phase 4: Understand Spring Boot Integration

#### Auto Configuration

**File Location**: `litefs-spring-boot-starter/src/main/java/io/github/fangyudev/litefs/autoconfigure/`

| File | Learning Focus |
|------|----------|
| `LiteFsAutoConfiguration.java` | Bean auto-assembly logic |
| `LiteFsProperties.java` | Configuration property binding |

**Auto-assembled Beans**:

```java
@Bean StorageEngine storageEngine()           // Storage engine
@Bean MetadataStore metadataStore()           // Metadata storage
@Bean UrlGenerator urlGenerator()             // URL generator
@Bean StorageEngineRouter storageRouter()     // Storage routing
@Bean FileClient fileClient()                 // File client
@Bean ReplicationService replicationService()  // Replication service (optional)
```

**Configuration Properties**:

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

## Test Code

### Phase 5: Understand Features Through Tests

Test code is the best documentation!

**Test Directory Structure**:

```
litefs-core/src/test/
├── storage/
│   └── LocalStorageEngineTest.java      # Storage engine test
├── metadata/
│   ├── H2MetadataStoreTest.java         # H2 metadata test
│   └── MySqlMetadataStoreTest.java      # MySQL metadata test
├── loadbalance/
│   ├── RoundRobinNodeSelectorTest.java  # Round-robin selector test
│   └── CapacityNodeSelectorTest.java    # Capacity selector test
├── registry/
│   └── StaticServiceRegistryTest.java   # Static registry test
└── util/
    ├── FileIdGeneratorUtilTest.java     # ID generation test
    ├── ChecksumUtilsTest.java           # Checksum test
    └── IoUtilsTest.java                 # I/O utility test

litefs-spring-boot-starter/src/test/
└── SpringBootIntegrationTest.java       # Spring Boot integration test
```

**Recommended Reading Order**:

1. `LocalStorageEngineTest.java` - Understand storage engine
2. `H2MetadataStoreTest.java` - Understand metadata storage
3. `SpringBootIntegrationTest.java` - Understand Spring Boot integration

---

## Design Patterns

LiteFS uses a lot of design patterns. Pay attention to these when reading code:

### 1. Builder Pattern

**Application Location**: `FileMetadata.Builder`, `FileQuery.Builder`, `FileClientBuilder`

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

### 2. Strategy Pattern

**Application Location**: `StorageEngine`, `MetadataStore`, `NodeSelector`, `ReplicaPlacer`

```java
// Different storage engine implementations
StorageEngine engine = new LocalStorageEngine(path);
StorageEngine engine = new MyCustomStorageEngine();

// Different node selection strategies (recommended via YAML configuration)
// litefs.load-balance.node-selector: round-robin / capacity
NodeSelector selector = new RoundRobinNodeSelector();
NodeSelector selector = new CapacityNodeSelector();
```

### 3. Decorator Pattern

**Application Location**: `MetadataCache` (Cache Aside Pattern)

```java
// Spring Boot configuration (recommended)
// application.yml:
// litefs:
//   cache:
//     enabled: true
//     type: redis           # Recommended for distributed environment
//     ttl: 300000

// Manual creation (non-Spring environment)
MetadataStore underlyingStore = new H2MetadataStore(jdbcUrl);
MetadataCacheProvider cacheProvider = new LocalMetadataCacheProvider(10000, 300000);
MetadataCache cache = new MetadataCache(underlyingStore, cacheProvider);
```

### 4. Factory Method Pattern

**Application Location**: `FileClientBuilder.build()`

### 5. Facade Pattern

**Application Location**: `FileClient` unified interface, `NodeManager` node management facade

### 6. Proxy Pattern

**Application Location**: `HttpRemoteStorageEngine`

---

## Quick Start: Understand Core Flow in 30 Minutes

If you want to get started quickly, it is recommended to only read these 5 files:

### 1. FileClient.java (5 minutes)

Understand what LiteFS can do - interface definition for all file operations.

**Location**: `litefs-core/src/main/java/io/github/fangyudev/litefs/api/FileClient.java`

### 2. FileMetadata.java (5 minutes)

Understand data structure - what information file metadata contains.

**Location**: `litefs-core/src/main/java/io/github/fangyudev/litefs/model/FileMetadata.java`

### 3. LocalStorageEngine.java (10 minutes)

Understand how files are stored - most basic storage engine implementation.

**Location**: `litefs-core/src/main/java/io/github/fangyudev/litefs/impl/store/engine/local/LocalStorageEngine.java`

**Focus Points**:
- How file path is generated (multi-level directory based on file ID hash)
- How read/write operations are implemented
- How append write is implemented (multipart upload merge)

### 4. H2MetadataStore.java (5 minutes)

Understand how metadata is managed - simplest metadata storage implementation.

**Location**: `litefs-core/src/main/java/io/github/fangyudev/litefs/impl/store/metadata/h2/H2MetadataStore.java`

**Focus Points**:
- Table structure design
- CRUD operation implementation
- Query condition builder

### 5. FileClientImpl.java (5 minutes)

Understand business flow - how all components collaborate.

**Location**: `litefs-core/src/main/java/io/github/fangyudev/litefs/impl/client/FileClientImpl.java`

**Focus Points**:
- `upload()` method - complete upload flow
- `download()` method - complete download flow
- How components are coordinated

---

## Learning Suggestions

### 1. Run it first

Run `litefs-example` first to understand features through actual operation:

```bash
cd litefs-example
mvn spring-boot:run
```

Access http://localhost:8080/quickstart/index.html to view example page.

### 2. Debug while reading

Set breakpoints in IDE to track code execution flow:

- Set breakpoint at `FileClientImpl.upload()`
- Upload a file
- Step through and observe the entire flow

### 3. From simple to complex

Understand standalone mode first, then distributed mode:

```
Standalone mode: FileClient + LocalStorageEngine + H2MetadataStore
    ↓
Distributed mode: ServiceRegistry + NodeSelector + ReplicationService
```

### 4. Focus on extension points

LiteFS design goal is extensibility, focus on SPI interfaces:

- How to implement your own storage engine?
- How to implement your own metadata storage?
- How to implement your own service registry?

See [SPI Extension Guide](SPI_DEVELOPER_GUIDE.md) for details.

---

## Related Documents

- [Architecture Design](ARCHITECTURE.md) - System architecture and design decisions
- [API Usage Guide](../user-guide/API_GUIDE.md) - Detailed API usage
- [FAQ](../user-guide/FAQ.md) - Q&A
