# LiteFS 第一个应用

> 本文档帮助您创建第一个使用 LiteFS 的 Spring Boot 应用

## 前置条件

- JDK 17+
- Maven 3.6+
- Spring Boot 3.x

## 创建应用

### 步骤 1: 创建 Spring Boot 项目

使用 Spring Initializr 或 IDE 创建一个 Spring Boot 项目。

### 步骤 2: 添加依赖

在 `pom.xml` 中添加 LiteFS 依赖：

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

    <!-- MySQL 驱动（生产环境推荐） -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>8.0.33</version>
    </dependency>

    <!-- Lombok（可选） -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### 步骤 3: 配置文件

创建 `application.yml`，单机模式使用默认配置即可：

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
      enabled: true              # 启用 H2 控制台（开发测试用）
      path: /h2-console          # 控制台访问路径

# LiteFS 配置（单机模式使用默认值，无需配置）
# litefs:
#   storage:
#     path: ./data/files
```

### 步骤 4: 创建启动类

```java
@SpringBootApplication
public class MyLiteFsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyLiteFsApplication.class, args);
    }
}
```

### 步骤 5: 创建文件控制器

创建文件上传下载接口：

```java
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileClient fileClient;

    @Autowired
    private UrlGenerator urlGenerator;

    /**
     * 上传文件
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
     * 下载文件
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
     * 获取文件访问 URL
     */
    @GetMapping("/{fileId}/url")
    public ResponseEntity<Map<String, String>> getUrl(
            @PathVariable String fileId,
            @RequestParam(defaultValue = "3600") long expire) {
        
        String url = fileClient.getUrl(fileId, expire);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        fileClient.delete(fileId);
        return ResponseEntity.noContent().build();
    }
}
```

### 步骤 6: 运行应用

```bash
mvn spring-boot:run
```

应用启动后访问：
- API 基础路径：`http://localhost:8080/api/files`
- H2 控制台：`http://localhost:8080/h2-console`（开发测试用）

## 完整示例

完整示例代码请参考 [litefs-example](../../litefs-example/) 模块，包含：

- **简单上传下载**：基本的文件操作
- **分片上传**：大文件上传支持
- **签名 URL**：临时访问控制
- **缩略图生成**：图片处理
- **文件权限管理**：公开/私有/临时文件

### 运行示例

```bash
# 编译项目
cd litefs
mvn clean install -DskipTests

# 启动示例应用
cd litefs-example
mvn spring-boot:run

# 访问示例页面
# 浏览器打开: http://localhost:8080/quickstart/index.html
```

## 下一步

- [API 使用指南](../user-guide/API_GUIDE.md) - 完整的 API 使用文档
- [配置参考](../user-guide/CONFIGURATION.md) - 所有配置项说明
- [最佳实践](../user-guide/BEST_PRACTICES.md) - 生产环境推荐用法
