# LiteFS 最佳实践

> 本文档提供 LiteFS 在生产环境中的推荐用法

## 文件大小选择

| 文件大小 | 推荐方式 | 说明 |
|---------|---------|------|
| < 5MB | 简单上传 | 一次性上传，简单高效 |
| 5MB - 100MB | 分片上传 | 前端分片，并行上传 |
| &gt; 100MB | 分片上传 + 断点续传 | 支持中断恢复 |

## 元数据设计

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

## URL 安全

```java
// 敏感文件使用临时签名 URL
String url = fileClient.getUrl(fileId, 300); // 5分钟有效

// 公开文件使用永久 URL
String url = fileClient.getUrl(fileId);
```

## 错误处理

```java
try {
    String fileId = fileClient.upload(inputStream, fileName, metadata);
} catch (Exception e) {
    log.error("文件上传失败: {}", fileName, e);
    // 建议记录错误详情并根据业务场景决定是否重试
    // 常见错误：文件不存在、网络异常、存储不可用等
}
```

## 资源释放

```java
// 确保关闭流
try (InputStream is = fileClient.download(fileId)) {
    // 处理文件
} catch (IOException e) {
    log.error("文件下载失败", e);
}
```

## 分布式部署推荐配置

```yaml
# 生产环境推荐配置
litefs:
  node-id: ${NODE_ID:node-1}
  storage:
    type: local
    metadata-type: mysql           # 使用 MySQL 集群
  cache:
    enabled: true
    type: redis                    # 分布式缓存
    ttl: 300000
  replication:
    enabled: true
    consistency: EVENTUAL          # 高性能场景
    queue:
      type: redis                  # 高吞吐场景
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  load-balance:
    node-selector: round-robin     # 轮询负载均衡
    replica-placer: balanced       # 平衡副本分布
```

## 缓存最佳实践

### 单机模式

```yaml
litefs:
  cache:
    enabled: true
    type: local
    max-size: 10000
    ttl: 300000
```

### 分布式模式

```yaml
# 必须使用 Redis 缓存，local 会被自动禁用
litefs:
  cache:
    enabled: true
    type: redis
    ttl: 300000
```

> **注意**: 分布式模式下使用本地缓存会导致数据不一致，系统会自动禁用。

## 相关文档

- [API 使用指南](API_GUIDE.md) - API 详细使用说明
- [配置参考手册](CONFIGURATION.md) - 所有配置项说明
- [性能调优指南](PERFORMANCE_TUNING.md) - 性能优化策略
