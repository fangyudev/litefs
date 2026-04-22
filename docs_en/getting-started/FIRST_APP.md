# LiteFS First Application

> This document helps you create your first LiteFS Spring Boot application

## Prerequisites

- JDK 17+
- Maven 3.6+
- Spring Boot 3.x

## Create Application

### Step 1: Create Spring Boot Project

Use Spring Initializr or your IDE to create a Spring Boot project.

### Step 2: Add Dependency

Add LiteFS dependency in `pom.xml`:

```xml
<dependencies>
    <!-- LiteFS Starter -->
    <dependency>
        <groupId>io.github.fangyudev</groupId>
        <artifactId>litefs-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- MySQL Driver (Recommended for production) -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>8.0.33</version>
    </dependency>

    <!-- Lombok (Optional) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Step 3: Configuration File

Create `application.yml`, standalone mode uses default configuration:

```yaml
server:
  port: 8080

spring:
  application:
    name: my-litefs-app
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 100MB
  h2:
    console:
      enabled: true              # Enable H2 console (for development testing)
      path: /h2-console          # Console access path

# LiteFS Configuration (standalone mode uses defaults, no config needed)
# litefs:
#   storage:
#     path: ./data/files
```

### Step 4: Create Main Class

```java
@SpringBootApplication
public class MyLiteFsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyLiteFsApplication.class, args);
    }
}
```

### Step 5: Create File Controller

Create file upload/download interface:

```java
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileClient fileClient;

    @Autowired
    private UrlGenerator urlGenerator;

    /**
     * Upload file
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file) throws IOException {

        String fileId = fileClient.upload(
            file.getInputStream(),
            file.getOriginalFilename(),
            Map.of("uploadedBy", "user-123")
        );

        return ResponseEntity.ok(Map.of(
            "fileId", fileId,
            "fileName", file.getOriginalFilename()
        ));
    }

    /**
     * Download file
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String fileId) {

        FileMetadata metadata = fileClient.getMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }

        InputStream stream = fileClient.download(fileId);

        StreamingResponseBody body = out -> {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            stream.close();
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.getFileName() + "\"")
                .body(body);
    }

    /**
     * Get file access URL
     */
    @GetMapping("/{fileId}/url")
    public ResponseEntity<Map<String, String>> getUrl(
            @PathVariable String fileId,
            @RequestParam(defaultValue = "3600") long expire) {

        String url = fileClient.getUrl(fileId, expire);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Delete file
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        fileClient.delete(fileId);
        return ResponseEntity.noContent().build();
    }
}
```

### Step 6: Run Application

```bash
mvn spring-boot:run
```

After application starts, access:
- API base path: `http://localhost:8080/api/files`
- H2 console: `http://localhost:8080/h2-console` (for development testing)

## Complete Example

For complete example code, please refer to [litefs-example](../../litefs-example/) module, including:

- **Simple Upload/Download**: Basic file operations
- **Multipart Upload**: Large file upload support
- **Signed URL**: Temporary access control
- **Thumbnail Generation**: Image processing
- **File Permission Management**: Public/private/temporary files

### Run Example

```bash
# Build project
cd litefs
mvn clean install -DskipTests

# Start example application
cd litefs-example
mvn spring-boot:run

# Access example page
# Open in browser: http://localhost:8080/quickstart/index.html
```

## Next Steps

- [API Usage Guide](../user-guide/API_GUIDE.md) - Complete API documentation
- [Configuration Reference](../user-guide/CONFIGURATION.md) - All configuration options
- [Best Practices](../user-guide/BEST_PRACTICES.md) - Recommended production usage
