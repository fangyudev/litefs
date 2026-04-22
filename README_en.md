# LiteFS

A lightweight distributed file storage component built for microservices architecture.

---

## 🌐 Choose Language / 选择语言

- [中文](README.md)
- [English](README_en.md)

---

## 💡 Why LiteFS?

In the early days of my startup, I struggled to find a **commercially safe** distributed file system. Most popular solutions either had license risks (GPL, AGPL) or high maintenance costs. So I decided to build my own — that's how LiteFS came to be.

Times have changed, but this problem still troubles many startups and SMBs. Considering that many companies have limited technical expertise, the barrier to building their own system remains high. That's why I chose to open-source LiteFS, hoping to contribute to the open-source community.

LiteFS follows the **minimal dependency principle**, fully utilizing existing infrastructure like Redis and MySQL in distributed systems — no reinventing the wheel. The codebase is lightweight and uses **SPI architecture** to abstract and load core components on demand, making it easy to extend.

## 🚀 Key Features

- **Minimal Dependencies** - Core package under 1MB, SPI architecture for on-demand component loading, fully utilizes existing infrastructure like Redis and MySQL in microservices
- **Embeddable in Business Services** - Runs as part of your business service, no separate deployment required, more flexible development and simpler operations
- **Enterprise-Friendly License** - Apache 2.0 license, no commercial risks, safe for production environments
- **Simple Integration** - Spring Boot Starter included, out-of-the-box, easily embedded into business services
- **Pure Java Implementation** - 100% Java code, deep integration with Java ecosystem, excellent development experience
- **Distributed Architecture** - Multi-node cluster support, automatic data sharding and load balancing
- **High Availability** - Built-in failover mechanism, automatic recovery from node failures
- **Flexible Storage Engine** - Local filesystem storage support, HTTP protocol for inter-node communication
- **Rich API** - Complete file operations, metadata management, URL generation, thumbnails, multipart uploads and more enterprise features
- **Secure & Reliable** - Complete permission control, data validation, and transmission encryption

## 🎯 Main Functions

### 📤 File Operations
- **Upload** - Simple upload / upload with options (permissions, expiration, custom metadata), options can override global config, flexible adaptation for different scenarios within the same system
- **Download** - Failover support, automatic switch to healthy replica
- **Delete** - Physical deletion, clears replicas and thumbnails simultaneously
- **Copy** - Generate new file ID, inherit original file attributes
- **Rename** - Only modifies display name, does not affect storage path

### 📋 Metadata Management
- **Get/Update** - Basic file info and custom metadata
- **Query** - Pagination, multi-condition filtering (filename, type, size, time range)

### 🔗 URL & Access Control
- **Permanent URL** - Direct access
- **Signed URL** - Temporary access URL with expiration
- **Download URL** - Triggers browser download
- **Permission Control** - PUBLIC / PRIVATE / TEMPORARY

### 🖼️ Thumbnails
- **Auto Generation** - Async generation on image upload
- **Multiple Sizes** - small (200px) / medium (400px) / large (800px)
- **Long Edge Priority** - Maintains aspect ratio

### 📦 Multipart Upload
- **Resume Support** - Pause and resume
- **Parallel Upload** - Multiple parts upload simultaneously
- **Auto Cleanup** - Expired sessions auto-cleanup

### 🔄 Distributed Capabilities
- **Replica Strategy** - NONE / MINIMAL(2) / STANDARD(3) / HIGH(5) / ALL_NODES
- **Consistency** - Synchronous replication (strong) / Asynchronous replication (high performance)
- **Failover** - Automatic switch to healthy replica on primary node failure
- **Load Balancing** - Round-robin / Capacity priority node selection
- **Service Discovery** - Static config / Nacos service registry

### 🛠️ Storage Backend (SPI Extensible)
- **File Storage** - Local filesystem
- **Metadata** - H2 database / MySQL
- **Cache** - Local cache / Redis
- **Message Queue** - In-memory queue / Redis

### ✅ Data Security
- **MD5 Validation** - Auto validation on upload
- **Permission Isolation** - Private files require signature verification

## 📊 Comparison with Mainstream Storage Solutions

| Feature | LiteFS | FastDFS | MinIO |
|------|--------|---------|-------|
| **Core Focus** | Lightweight distributed file storage (embeddable) | High-performance file storage (standalone service) | Object storage service (standalone service) |
| **License** | Apache 2.0 (No commercial risk) | GPL v3 (Commercial risk) | AGPL v3 (Strong "infectious" nature) |
| | - Free commercial use<br>- No need to open source modifications<br>- Only need to keep copyright notice | - Commercial use allowed, but with strict restrictions<br>- Must open source modifications<br>- May be restricted when combined with other software | - Strictest open source license<br>- Network use requires open source<br>- Modifications or calls require open source<br>- Major domestic and foreign companies avoid using it |
| **Deployment** | Embedded mode, as part of business service | Standalone deployment of Tracker/Storage cluster | Standalone object storage service deployment |
| **Integration Difficulty** | Extremely low (Spring Boot auto-configuration) | Medium (requires client configuration) | Medium (requires API integration) |
| **Development Language** | Pure Java | C language | Go language |
| **Learning Curve** | Gentle (Java native API) | Steep (custom protocol) | Medium (S3 protocol) |
| **Operations Complexity** | Extremely low (shared with business service) | High (manual management of Groups/nodes, no native monitoring) | Medium (command-line operations, no enterprise management interface) |
| **Resource Usage** | Low (shared with business service) | Medium (standalone cluster) | Medium (standalone service) |
| **Development Flexibility** | High (embeddable in business service, tightly integrated with business code) | Low (standalone service, cross-service calls) | Low (standalone service, cross-service calls) |
| **Data Consistency** | Strong/Eventual (configurable) | Eventual (async replication) | Strong (Erasure Coding) |
| **Extensibility** | Modular SPI design, easy to extend | Complex extension, requires core code modification | Flexible extension, based on plugin system |
| **Use Cases** | SMB microservices, fast integration projects, enterprise internal systems | Large file storage systems | Cloud-native object storage, big data scenarios |
| **Core Advantages** | Lightweight embedding, secure protocol, Java ecosystem integration | High performance, suitable for large files | Cloud-native, S3 compatible |

## 🖼️ Preview

LiteFS provides a complete example project. After starting, you can experience file upload, chunked upload, file management, and URL generation through the web interface:

![LiteFS Example Interface](docs/images/screencapture-example.png)

## 📖 Quick Start

> **📝 Note:** LiteFS is preparing to release to Maven Central Repository. Before that, you can install and use it locally.

### 1. Local Installation (Temporary)

```bash
# Clone the code
git clone https://github.com/fangyudev/litefs.git
# or
git clone https://gitee.com/fangyudev/litefs.git

cd litefs

# Build and install to local Maven repository
mvn clean install -DskipTests
```

After installation, you can add the dependency to your project.

### 2. Add Dependency

```xml
<!-- Spring Boot Project -->
<dependency>
    <groupId>io.github.fangyudev</groupId>
    <artifactId>litefs-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Non-Spring Boot Project -->
<dependency>
    <groupId>io.github.fangyudev</groupId>
    <artifactId>litefs-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. Enable Storage Service

```java
// Spring Boot Project - No annotations needed, auto-enabled
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**Note:** LiteFS uses Spring Boot 3.x auto-configuration. As long as `litefs-spring-boot-starter` is in the classpath, it will be automatically enabled.

> In special cases (such as needing to enable after setting `litefs.enabled=false`), you can manually add `@EnableFileStorage` annotation to force enable.

### 4. Simple Usage

```java
@Autowired
private FileClient fileClient;

// Upload file
FileUploadRequest request = FileUploadRequest.builder()
    .file(file)
    .visibility(FileVisibility.PUBLIC)
    .build();
String fileId = fileClient.upload(request);

// Get file URL
String fileUrl = fileClient.getFileUrl(fileId);
```

## 📚 Detailed Documentation

- **[Full Documentation Center](docs_en/README.md)** - Installation, configuration, API guide, best practices
- **[Quick Start](docs_en/getting-started/QUICK_START.md)** - 5-minute getting started guide
- **[API Reference](docs_en/user-guide/API_GUIDE.md)** - Detailed API usage documentation
- **[Architecture Design](docs_en/developer-guide/ARCHITECTURE.md)** - System architecture and design philosophy

## 🔧 Tech Stack

- **Core Language**: Java 17+
- **Build Tool**: Maven
- **Spring Boot**: 3.0+

## 📄 License

This project uses **Apache License 2.0** open source license, no commercial risks, free for commercial projects.

## 🌟 Why Choose LiteFS?

LiteFS is designed specifically for **Java microservices** scenarios, solving these pain points:
- Don't want to introduce standalone file storage services, increasing operations burden
- Need a secure and compliant storage solution, avoiding license risks
- Want lightweight code, few dependencies, easy to maintain

---

<div align="center">
  <p>💡 Lightweight Storage, Unlimited Possibilities</p>
  <p>Made with ❤️ by <a href="https://github.com/fangyudev">方郁 (fangyu)</a></p>
</div>
