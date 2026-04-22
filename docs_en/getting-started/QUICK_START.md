# LiteFS Quick Start

> This document helps you get started with LiteFS in 5 minutes

## Prerequisites

- JDK 17+
- Maven 3.6+
- Spring Boot 3.x project

## Quick Start

### Step 1: Add Dependency

```xml
<dependency>
    <groupId>io.github.fangyudev</groupId>
    <artifactId>litefs-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: Add Configuration

> 💡 Tip: All default configurations are designed for **standalone mode** quick development testing, out-of-the-box, no need to modify `application.yml`.

#### Standalone Mode (Development & Testing)

No configuration needed, all defaults are used:
- Local file storage: `./data/files`
- H2 embedded database: `./data/litefs`
- Direct access mode: No gateway needed
- Local memory cache/queue: No external dependencies

#### Production Environment Recommended Configuration

Production environment needs to modify the following key configurations (only list items that need modification, others keep defaults):

```yaml
litefs:
  # Must modify: Signature key (please use a complex key)
  access:
    signed:
      secret-key: your-complex-secret-key-at-least-32-chars

  # Recommended: MySQL metadata storage
  storage:
    metadata-type: mysql
    jdbc-url: jdbc:mysql://your-db-host:3306/litefs
    jdbc-username: your-username
    jdbc-password: your-password

  # Recommended: Redis distributed cache (improves performance)
  cache:
    enabled: true
    type: redis
    redis:
      host: your-redis-host
      password: your-redis-password

  # Distributed mode needs to be enabled
  remote:
    enabled: true

  # Distributed mode required: Redis message queue
  replication:
    queue:
      type: redis
      redis:
        host: your-redis-host
        password: your-redis-password

  # Distributed mode required: Redis multipart storage
  multipart:
    store-type: redis
    redis:
      host: your-redis-host
      password: your-redis-password
```

> ⚠️ **Security Warning**: `access.signed.secret-key` **must** be changed to a complex key in production environment, otherwise signature verification is meaningless!

### Step 3: Use FileClient

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

## Run Example Project

After cloning the code, you can run the example project to experience complete features:

```bash
cd litefs-example
mvn spring-boot:run
```

After successful startup, access `http://localhost:8080/quickstart/index.html` in your browser to see the following example interface:

![Example Interface](../images/screencapture-example.png)

## Next Steps

- [Installation & Configuration](INSTALLATION.md) - Detailed installation and configuration guide
- [API Usage Guide](../user-guide/API_GUIDE.md) - Complete API documentation
- [Configuration Reference](../user-guide/CONFIGURATION.md) - All configuration options
- [FAQ](../user-guide/FAQ.md) - Q&A

## Common Questions

### How to switch to MySQL?
Modify `metadata-type: mysql` and configure `jdbc-url`, `jdbc-username`, `jdbc-password`.

### How to configure gateway access?
Default is direct access mode. If you need to access through gateway, add configuration:
```yaml
litefs:
  access:
    url-type: gateway
    gateway:
      base-url: http://your-gateway.com
      path-prefix: /api/files
```
