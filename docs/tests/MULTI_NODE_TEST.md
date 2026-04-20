# LiteFS 多节点部署指南

本文档介绍 LiteFS 的多节点部署方式，包括开发测试环境和生产环境两种场景。

---

## 重要说明

### 单机多节点 vs 生产多节点

| 部署方式 | 适用场景 | 注册中心 | 元数据存储 | 分片上传存储 |
|---------|---------|---------|-----------|-------------|
| **单机多节点** | 开发测试 | 静态配置 | H2 / MySQL | 内存（不支持跨节点） |
| **生产多节点** | 生产环境 | Nacos | MySQL | Redis |

> **警告**：单机多节点部署（同一台机器上运行多个节点）**仅适用于开发测试**，不建议用于生产环境。

---

## 第一部分：单机多节点部署（开发测试）

在同一台机器上启动多个 LiteFS 实例，用于开发和功能测试。

### 环境要求

- JDK 17+
- Maven 3.6+
- (可选) MySQL 8.0+ - 如需测试 MySQL 模式

### 1. 编译项目

```bash
cd litefs
mvn clean install -DskipTests -pl litefs-core,litefs-spring-boot-starter,litefs-example
```

### 2. 创建配置文件

在 `litefs-example/src/main/resources/` 目录下创建三个节点的配置文件：

**application-node1.yml**（节点1配置）：

```yaml
server:
  port: 8081

spring:
  application:
    name: litefs-node-1
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 100MB
  h2:
    console:
      enabled: true
      path: /h2-console
      settings:
        web-allow-others: true   # 允许其他机器访问

litefs:
  enabled: true
  node-id: node-1
  
  storage:
    type: local
    path: ${user.dir}/data/node1/files
    metadata-type: h2
    jdbc-url: jdbc:h2:${user.dir}/data/shared/litefs;AUTO_SERVER=TRUE
    jdbc-username: sa
    jdbc-password: ""
  
  access:
    url-type: direct
    direct:
      path-prefix: /api/files
    signed:
      enabled: true
      secret-key: test-secret-key-for-3-nodes
      default-expire: 3600
  
  replication:
    enabled: true
    default-strategy: STANDARD
    consistency: EVENTUAL
  
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  
  registry:
    type: static
    static-config:
      nodes:
        - id: node-1
          host: localhost
          port: 8081
          total-space: 10737418240
        - id: node-2
          host: localhost
          port: 8082
          total-space: 10737418240
        - id: node-3
          host: localhost
          port: 8083
          total-space: 10737418240
```

**application-node2.yml**（节点2配置）：

```yaml
server:
  port: 8082

spring:
  application:
    name: litefs-node-2
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 100MB
  h2:
    console:
      enabled: true
      path: /h2-console
      settings:
        web-allow-others: true

litefs:
  enabled: true
  node-id: node-2
  
  storage:
    type: local
    path: ${user.dir}/data/node2/files
    metadata-type: h2
    jdbc-url: jdbc:h2:${user.dir}/data/shared/litefs;AUTO_SERVER=TRUE
    jdbc-username: sa
    jdbc-password: ""
  
  access:
    url-type: direct
    direct:
      path-prefix: /api/files
    signed:
      enabled: true
      secret-key: test-secret-key-for-3-nodes
      default-expire: 3600
  
  replication:
    enabled: true
    default-strategy: STANDARD
    consistency: EVENTUAL
  
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  
  registry:
    type: static
    static-config:
      nodes:
        - id: node-1
          host: localhost
          port: 8081
          total-space: 10737418240
        - id: node-2
          host: localhost
          port: 8082
          total-space: 10737418240
        - id: node-3
          host: localhost
          port: 8083
          total-space: 10737418240
```

**application-node3.yml**（节点3配置）：

```yaml
server:
  port: 8083

spring:
  application:
    name: litefs-node-3
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 100MB
  h2:
    console:
      enabled: true
      path: /h2-console
      settings:
        web-allow-others: true

litefs:
  enabled: true
  node-id: node-3
  
  storage:
    type: local
    path: ${user.dir}/data/node3/files
    metadata-type: h2
    jdbc-url: jdbc:h2:${user.dir}/data/shared/litefs;AUTO_SERVER=TRUE
    jdbc-username: sa
    jdbc-password: ""
  
  access:
    url-type: direct
    direct:
      path-prefix: /api/files
    signed:
      enabled: true
      secret-key: test-secret-key-for-3-nodes
      default-expire: 3600
  
  replication:
    enabled: true
    default-strategy: STANDARD
    consistency: EVENTUAL
  
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  
  registry:
    type: static
    static-config:
      nodes:
        - id: node-1
          host: localhost
          port: 8081
          total-space: 10737418240
        - id: node-2
          host: localhost
          port: 8082
          total-space: 10737418240
        - id: node-3
          host: localhost
          port: 8083
          total-space: 10737418240
```

### 3. 启动节点

打开三个终端窗口，分别启动三个节点：

```bash
# 终端1 - 启动节点1
cd litefs-example
mvn spring-boot:run -Dspring-boot.run.profiles=node1

# 终端2 - 启动节点2
cd litefs-example
mvn spring-boot:run -Dspring-boot.run.profiles=node2

# 终端3 - 启动节点3
cd litefs-example
mvn spring-boot:run -Dspring-boot.run.profiles=node3
```

### 4. 访问测试

| 节点 | 地址 |
|------|------|
| Node-1 | http://localhost:8081/quickstart/index.html |
| Node-2 | http://localhost:8082/quickstart/index.html |
| Node-3 | http://localhost:8083/quickstart/index.html |

### 单机多节点架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     单机多节点模式（开发测试）                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Node-1 (8081)    Node-2 (8082)    Node-3 (8083)              │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐               │
│   │ 文件存储 │     │ 文件存储 │     │ 文件存储 │               │
│   │ node1/   │     │ node2/   │     │ node3/   │  ← 独立存储   │
│   └──────────┘     └──────────┘     └──────────┘               │
│        │                │                │                      │
│        └────────────────┼────────────────┘                      │
│                         │                                       │
│              ┌──────────┴──────────┐                           │
│              │  共享 H2 数据库      │  ← AUTO_SERVER 模式       │
│              │  data/shared/litefs │                           │
│              └─────────────────────┘                           │
│                                                                 │
│  ⚠️ 限制：                                                       │
│  - 分片上传不支持跨节点（使用内存存储）                          │
│  - 节点都在同一台机器，无高可用性                                │
│  - 仅用于开发测试                                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## 第二部分：生产环境多节点部署

生产环境推荐使用多台物理机/虚拟机部署，配合 Nacos 注册中心实现服务发现。

### 生产环境架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         生产环境部署架构                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│    ┌─────────────┐                                                      │
│    │  Nginx/网关  │  ← 负载均衡层                                        │
│    └──────┬──────┘                                                      │
│           │                                                             │
│    ┌──────┴──────┬──────────────┐                                       │
│    │             │              │                                       │
│    ▼             ▼              ▼                                       │
│ ┌──────┐    ┌──────┐     ┌──────┐                                      │
│ │Node-1│    │Node-2│     │Node-3│  ← LiteFS 节点层                     │
│ │:8080 │    │:8080 │     │:8080 │                                      │
│ └──┬───┘    └──┬───┘     └──┬───┘                                      │
│    │           │            │                                           │
│    └───────────┼────────────┘                                           │
│                │                                                        │
│    ┌───────────┴───────────┐                                            │
│    │                       │                                            │
│    ▼                       ▼                                            │
│ ┌────────┐            ┌────────┐                                       │
│ │  MySQL │◄──────────►│ Redis  │  ← 共享基础设施层                      │
│ │(元数据)│            │(缓存/   │                                       │
│ └────────┘            │ 队列)   │                                       │
│                       └────────┘                                       │
│                                                                         │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │                     Nacos 注册中心                         │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1. 基础设施准备

#### 1.1 MySQL 数据库

创建数据库：

```sql
CREATE DATABASE litefs DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 1.2 Redis 服务

用于分片上传会话存储和元数据缓存：

```bash
# 建议使用 Redis 6.0+
redis-server --port 6379
```

#### 1.3 Nacos 注册中心（推荐）

```bash
# 启动 Nacos（单机模式示例）
sh startup.sh -m standalone
```

### 2. 生产环境配置

#### 2.1 使用 Nacos 注册中心

**application-prod.yml**（各节点通用配置）：

```yaml
server:
  port: 8080

spring:
  application:
    name: litefs-service
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 100MB
  cloud:
    nacos:
      discovery:
        server-addr: nacos-server:8848
        namespace: prod
        group: DEFAULT_GROUP

litefs:
  enabled: true
  node-id: node-1                    # 每个节点必须配置唯一 ID
  
  storage:
    type: local
    path: /data/litefs/files        # 生产环境使用独立磁盘
    metadata-type: mysql
    jdbc-url: jdbc:mysql://mysql-server:3306/litefs?useSSL=true&serverTimezone=Asia/Shanghai
    jdbc-username: litefs_user
    jdbc-password: your_secure_password
    table-prefix: litefs_
  
  access:
    url-type: gateway
    gateway:
      base-url: https://files.example.com
      path-prefix: /api/files
    signed:
      enabled: true
      secret-key: ${LITEFS_SECRET_KEY}  # 从环境变量读取
      default-expire: 3600
  
  replication:
    enabled: true
    default-strategy: STANDARD
    consistency: EVENTUAL
    queue:
      type: redis
      topic: litefs.replication
      redis:
        host: redis-server
        port: 6379
        password: ${REDIS_PASSWORD}
        database: 0
  
  remote:
    enabled: true
    connect-timeout: 5000
    read-timeout: 30000
  
  registry:
    type: nacos                     # 使用 Nacos 注册中心
    # nacos 配置会自动读取 spring.cloud.nacos.discovery
  
  multipart:
    store-type: redis               # 分布式模式必须使用 redis
    cleanup-enabled: true
    cleanup-interval: 3600000
    redis:
      host: redis-server
      port: 6379
      password: ${REDIS_PASSWORD}
  
  cache:
    enabled: true
    type: redis                     # 多节点共享缓存
    ttl: 300000
    redis:
      host: redis-server
      port: 6379
      password: ${REDIS_PASSWORD}
      key-prefix: "litefs:meta:"
  
  thumbnail:
    enabled: true
    default-size: medium
```

#### 2.2 静态注册中心配置（无 Nacos）

如果暂时不使用服务注册中心，可以手动配置所有节点：

```yaml
litefs:
  registry:
    type: static
    static-config:
      nodes:
        - id: node-1
          host: 192.168.1.101
          port: 8080
          total-space: 107374182400    # 100GB
        - id: node-2
          host: 192.168.1.102
          port: 8080
          total-space: 107374182400
        - id: node-3
          host: 192.168.1.103
          port: 8080
          total-space: 107374182400
```

### 3. 部署步骤

#### 3.1 打包应用

```bash
cd litefs
mvn clean package -DskipTests -pl litefs-core,litefs-spring-boot-starter,litefs-example
```

#### 3.2 部署到各节点

```bash
# 复制 jar 包到各服务器
scp litefs-example/target/litefs-example-*.jar user@node1:/opt/litefs/
scp litefs-example/target/litefs-example-*.jar user@node2:/opt/litefs/
scp litefs-example/target/litefs-example-*.jar user@node3:/opt/litefs/

# 复制配置文件
scp application-prod.yml user@node1:/opt/litefs/config/
scp application-prod.yml user@node2:/opt/litefs/config/
scp application-prod.yml user@node3:/opt/litefs/config/
```

#### 3.3 启动服务

各节点分别执行：

```bash
cd /opt/litefs

# 设置环境变量
export LITEFS_SECRET_KEY="your-production-secret-key"
export REDIS_PASSWORD="your-redis-password"

# 启动应用
java -jar litefs-example-*.jar --spring.config.location=config/application-prod.yml
```

或使用 systemd 管理：

```ini
# /etc/systemd/system/litefs.service
[Unit]
Description=LiteFS Service
After=network.target

[Service]
Type=simple
User=litefs
Group=litefs
WorkingDirectory=/opt/litefs
Environment="LITEFS_SECRET_KEY=your-production-secret-key"
Environment="REDIS_PASSWORD=your-redis-password"
ExecStart=/usr/bin/java -jar /opt/litefs/litefs-example-*.jar --spring.config.location=/opt/litefs/config/application-prod.yml
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
systemctl enable litefs
systemctl start litefs
```

### 4. 配置 Nginx 负载均衡

```nginx
upstream litefs_backend {
    least_conn;
    server 192.168.1.101:8080 weight=1;
    server 192.168.1.102:8080 weight=1;
    server 192.168.1.103:8080 weight=1;
}

server {
    listen 80;
    server_name files.example.com;
    
    client_max_body_size 100M;
    
    location / {
        proxy_pass http://litefs_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 5s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

---

## 关键配置对比

### 开发测试 vs 生产环境

| 配置项 | 开发测试 | 生产环境 |
|-------|---------|---------|
| `remote.enabled` | `true` | `true` |
| `storage.metadata-type` | `h2` / `mysql` | `mysql` |
| `storage.path` | `${user.dir}/data/...` | `/data/litefs/files` |
| `registry.type` | `static` | `nacos` |
| `multipart.store-type` | `local`（不支持分片） | `redis` |
| `cache.enabled` | `false` | `true` |
| `cache.type` | `local` | `redis` |
| `replication.queue.type` | `memory` | `redis` |
| `access.url-type` | `direct` | `gateway` |

---

## 常见问题

### 1. 分片上传失败："Upload not found"

**原因**：分布式模式下使用了 `multipart.store-type=local`

**解决**：

```yaml
litefs:
  multipart:
    store-type: redis   # 分布式模式必须使用 redis
```

### 2. 节点间复制失败

**检查**：
- `remote.enabled` 是否为 `true`
- 各节点是否能互相访问（防火墙是否开放端口）
- `registry` 中配置的节点地址是否正确

### 3. 元数据不一致

**检查**：
- 所有节点是否使用同一个 MySQL 数据库
- `storage.jdbc-url` 配置是否正确

### 4. 缓存不一致

**症状**：上传文件后，其他节点无法立即访问

**解决**：启用 Redis 缓存

```yaml
litefs:
  cache:
    enabled: true
    type: redis
```

---

## 配置文件清单

### 开发测试

| 文件 | 说明 |
|------|------|
| `application-node1.yml` | 节点1 H2配置 |
| `application-node2.yml` | 节点2 H2配置 |
| `application-node3.yml` | 节点3 H2配置 |

### 生产环境

| 文件 | 说明 |
|------|------|
| `application-prod.yml` | 生产环境通用配置 |
| `litefs.service` | systemd 服务文件 |
| `nginx-litefs.conf` | Nginx 负载均衡配置 |

---

## 相关文档

- [API 文档](../API.md)
- [架构设计](../developer-guide/ARCHITECTURE.md)
- [SPI 开发指南](../developer-guide/SPI_DEVELOPER_GUIDE.md)
