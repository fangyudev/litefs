# LiteFS 部署指南

> 本文档介绍 LiteFS 的单机与分布式部署方案

## 单机部署

单机模式是默认配置，无需额外配置：

```yaml
litefs:
  storage:
    type: local
    path: ./data/files
  access:
    url-type: direct
```

## 分布式部署

详见 [多节点测试部署指南](../tests/MULTI_NODE_TEST.md)。

### 前置条件

- 注册中心（Static/Nacos）
- 消息队列（Redis/内存）
- 共享数据库（MySQL 推荐）

### 配置示例

```yaml
litefs:
  node-id: ${NODE_ID:node-1}
  storage:
    type: local
    metadata-type: mysql
    jdbc-url: jdbc:mysql://localhost:3306/litefs
    jdbc-username: root
    jdbc-password: password
  remote:
    enabled: true
  registry:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
  replication:
    enabled: true
    queue:
      type: redis
      redis:
        host: 127.0.0.1
        port: 6379
```

## TODO

> 此文档待完善，将包含以下内容：
> - Docker 部署方案
> - Kubernetes 部署方案
> - 生产环境配置建议
> - 安全配置

## 相关文档

- [配置参考手册](../user-guide/CONFIGURATION.md) - 所有配置项说明
- [多节点测试](../tests/MULTI_NODE_TEST.md) - 多节点测试部署
- [故障排查](TROUBLESHOOTING.md) - 常见问题诊断
