# LiteFS 快速入门示例

本示例演示 LiteFS 的核心功能，帮助你快速上手。

## 功能演示

| 功能 | 说明 | API |
|------|------|-----|
| 简单上传 | 适合5MB以下的小文件 | `POST /api/files` |
| 分片上传 | 适合大文件，前端分片并行上传 | `POST /api/files/multipart/*` |
| 文件访问 | 支持签名校验的文件访问 | `GET /api/files/{fileId}` |
| 文件下载 | 强制触发浏览器下载 | `GET /api/files/{fileId}/download` |
| 签名URL | 生成临时访问URL | `GET /api/files/{fileId}/url` |
| 文件列表 | 查询文件列表 | `GET /api/files` |
| 元数据管理 | 获取/删除文件 | `GET/DELETE /api/files/{fileId}` |

## 快速开始

### 1. 启动示例应用

```bash
# 编译项目
cd litefs
mvn clean install -DskipTests

# 启动示例应用
cd litefs-example
mvn spring-boot:run
```

### 2. 访问示例页面

打开浏览器访问：http://localhost:8080/quickstart/index.html

## API 使用示例

### 简单上传

```bash
# 上传文件
curl -X POST http://localhost:8080/api/files \
  -F "file=@/path/to/your/file.jpg"

# 响应
{
  "fileId": "abc123...",
  "fileName": "file.jpg"
}
```

### 分片上传

```bash
# 1. 初始化分片上传
curl -X POST "http://localhost:8080/api/files/multipart/init?fileName=large.zip&fileSize=104857600"

# 响应
{
  "uploadId": "upload-abc123..."
}

# 2. 上传分片（每个分片最大5MB）
curl -X POST "http://localhost:8080/api/files/multipart/upload-abc123.../part/1" \
  -F "file=@part1.bin"

# 响应
{
  "partNumber": 1,
  "eTag": "d41d8cd98f00b204e9800998ecf8427e"
}

# 3. 完成上传
curl -X POST "http://localhost:8080/api/files/multipart/upload-abc123.../complete" \
  -H "Content-Type: application/json" \
  -d '[{"partNumber":1,"eTag":"d41d8cd98f00b204e9800998ecf8427e"}]'

# 响应
{
  "fileId": "file-xyz789...",
  "fileName": null
}
```

### 获取签名URL

```bash
# 获取1小时有效的签名URL
curl "http://localhost:8080/api/files/abc123.../url?expire=3600"

# 响应
{
  "url": "http://localhost:8080/api/files/abc123...?token=xxx&expire=xxx",
  "expireSeconds": 3600
}
```

### 访问文件

```bash
# 直接访问（公开）
curl http://localhost:8080/api/files/abc123...

# 签名访问（临时有效）
curl "http://localhost:8080/api/files/abc123...?token=xxx&expire=xxx"
```

## 前端集成示例

### JavaScript 简单上传

```javascript
async function uploadFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch('/api/files', {
        method: 'POST',
        body: formData
    });
    
    const result = await response.json();
    console.log('文件ID:', result.fileId);
    return result.fileId;
}
```

### JavaScript 分片上传

```javascript
async function multipartUpload(file) {
    const PART_SIZE = 5 * 1024 * 1024; // 5MB
    
    // 1. 初始化
    const initRes = await fetch(
        `/api/files/multipart/init?fileName=${file.name}&fileSize=${file.size}`,
        { method: 'POST' }
    );
    const { uploadId } = await initRes.json();
    
    // 2. 上传分片
    const totalParts = Math.ceil(file.size / PART_SIZE);
    const parts = [];
    
    for (let i = 0; i < totalParts; i++) {
        const start = i * PART_SIZE;
        const end = Math.min(start + PART_SIZE, file.size);
        const partBlob = file.slice(start, end);
        
        const formData = new FormData();
        formData.append('file', partBlob);
        
        const partRes = await fetch(
            `/api/files/multipart/${uploadId}/part/${i + 1}`,
            { method: 'POST', body: formData }
        );
        const { eTag } = await partRes.json();
        parts.push({ partNumber: i + 1, eTag });
    }
    
    // 3. 完成上传
    const completeRes = await fetch(
        `/api/files/multipart/${uploadId}/complete`,
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(parts)
        }
    );
    
    return await completeRes.json();
}
```

### 显示图片

```javascript
// 方式1：临时URL（推荐）
async function showImageWithSignedUrl(fileId) {
    const response = await fetch(`/api/files/${fileId}/url?expire=3600`);
    const { url } = await response.json();
    
    document.getElementById('img').src = url;
}

// 方式2：公开URL
function showImageWithPublicUrl(fileId) {
    document.getElementById('img').src = `/api/files/${fileId}`;
}
```

## 配置说明

`application.yml` 配置：

```yaml
litefs:
  enabled: true
  node-id: example-node-1
  storage:
    type: local                    # 存储类型：local
    path: ./data/files             # 本地存储路径
    jdbc-url: jdbc:h2:./data/litefs;AUTO_SERVER=TRUE
  access:
    url-type: gateway              # URL类型：gateway
    gateway:
      base-url: http://localhost:8080
      path-prefix: /api/files
    signed:
      enabled: true
      secret-key: your-secret-key  # 签名密钥
      default-expire: 3600         # 默认过期时间（秒）
```

## 注意事项

1. **简单上传**适合小文件（建议5MB以下），大文件请使用分片上传
2. **签名URL**有过期时间，过期后无法访问
3. **分片上传**需要前端完成分片，后端只负责接收和合并
4. 生产环境建议配置 Nginx 缓存以提高性能

## 相关链接

- [LiteFS 设计文档](../../docs/DESIGN.md)
- [API 文档](../../docs/API.md)
