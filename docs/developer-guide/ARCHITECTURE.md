# LiteFS 架构设计

> 本文档介绍 LiteFS 的系统架构和核心设计决策

## 项目定位

**轻量级分布式文件存储组件**，而非完整的微服务系统。它应该：
- 作为用户系统的一个可嵌入组件
- 通过 SDK/客户端库集成到用户系统中
- 复用用户系统已有的中间件（Nacos、Redis等）
- 专注于文件存储、检索、管理的核心功能
- 不负责 API 网关、认证授权等非核心功能


## 核心架构

LiteFS 的核心架构由以下几个层次组成：

### API 层

对外暴露的统一接口 `FileClient`，提供所有文件操作能力。

### SPI 层

可插拔的扩展接口，包括：
- `StorageEngine` — 存储引擎
- `MetadataStore` — 元数据存储
- `ServiceRegistry` — 服务注册
- `StorageEngineRouter` — 存储引擎路由
- `MetadataCacheProvider` — 元数据缓存提供者
- `MultipartUploadStore` — 分片上传存储
- `ReplicaMetadataStore` — 副本元数据存储
- `NodeSelector` — 节点选择
- `ReplicaPlacer` — 副本放置
- `MessageQueue` — 消息队列

### 实现层

各 SPI 接口的内置实现，如 `LocalStorageEngine`、`H2MetadataStore` 等。

### 集成层

Spring Boot Starter 自动配置，将所有组件组装在一起。

## 设计决策

### 为什么使用 HTTP 而不是 gRPC 进行远程通信？

LiteFS 定位为轻量级组件，HTTP 已能满足文件传输需求：
- 文件传输的瓶颈在磁盘 IO 和网络带宽，协议开销差异可忽略
- HTTP 简单易用，无需额外依赖
- 支持流式传输
- 与 Spring 生态天然兼容

### 为什么采用 SPI 设计？

通过 SPI 机制，用户可以：
- 自定义存储引擎（如对接 S3、OSS）
- 自定义元数据存储（如对接 MongoDB）
- 自定义服务注册（如对接 Consul）
- 自定义消息队列（如对接 Kafka）

详见 [SPI 扩展开发指南](SPI_DEVELOPER_GUIDE.md)。

## 相关文档

- [学习指南](LEARNING_GUIDE.md) - 代码阅读与学习路径
- [SPI 扩展开发指南](SPI_DEVELOPER_GUIDE.md) - 自定义扩展开发
