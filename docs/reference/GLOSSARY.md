# LiteFS 术语表

> 本文档解释 LiteFS 项目中使用的术语和概念

## 核心术语

| 术语 | 说明 |
|------|------|
| FileClient | LiteFS 的核心 API 接口，提供所有文件操作功能 |
| StorageEngine | 存储引擎 SPI 接口，定义文件读写删除等操作 |
| MetadataStore | 元数据存储 SPI 接口，管理文件元数据 |
| ServiceRegistry | 服务注册 SPI 接口，管理存储节点注册与发现 |
| NodeSelector | 节点选择器，决定上传文件存储到哪个节点 |
| ReplicaPlacer | 副本放置策略，决定文件副本存储到哪些节点 |
| UrlGenerator | URL 生成器，生成文件访问 URL |
| SPI | Service Provider Interface，可插拔扩展接口 |

## 部署模式

| 术语 | 说明 |
|------|------|
| 单机模式 | `litefs.remote.enabled=false`，单节点运行 |
| 分布式模式 | `litefs.remote.enabled=true`，多节点协作 |
| 网关模式 | URL 通过统一网关访问 |
| 直接访问模式 | URL 直接访问文件所在节点 |

## 副本策略

| 术语 | 说明 |
|------|------|
| NONE | 无副本 |
| MINIMAL | 最小副本（1个） |
| STANDARD | 标准副本（2个） |
| HIGH | 高副本（3个） |
| ALL_NODES | 所有节点 |

## 一致性

| 术语 | 说明 |
|------|------|
| EVENTUAL | 最终一致性，异步复制 |
| STRONG | 强一致性，同步复制 |

## 相关文档

- [架构设计](../developer-guide/ARCHITECTURE.md) - 系统架构说明
- [API 使用指南](../user-guide/API_GUIDE.md) - API 使用说明
