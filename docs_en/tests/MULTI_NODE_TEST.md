# LiteFS Multi-Node Deployment Guide

This document introduces LiteFS multi-node deployment methods, including development testing environment and production environment scenarios.

---

## Important Notes

### Single Machine Multi-Node vs Production Multi-Node

| Deployment | Applicable Scenario | Registry | Metadata Storage | Multipart Upload Storage |
|---------|---------|---------|-----------|-------------|
| **Single machine multi-node** | Development testing | Static config | H2 / MySQL | Memory (no cross-node support) |
| **Production multi-node** | Production environment | Nacos | MySQL | Redis |

> **Warning**: Single machine multi-node deployment (running multiple nodes on the same machine) **is only suitable for development testing**, not recommended for production environments.

---

## Part 1: Single Machine Multi-Node Deployment (Development Testing)

Run multiple LiteFS instances on the same machine for development and functional testing.

### Environment Requirements

- JDK 17+
- Maven 3.6+
- (Optional) MySQL 8.0+ - if you need to test MySQL mode

### 1. Build Project

```bash
cd litefs
mvn clean install -DskipTests -pl litefs-core,litefs-spring-boot-starter,litefs-example
```

### 2. Create Configuration Files

Create three node configuration files in `litefs-example/src/main/resources/`:

**application-node1.yml** (Node 1 Config):

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
        web-allow-others: true   # Allow other machines to access

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

**application-node2.yml** (Node 2 Config):

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

**application-node3.yml** (Node 3 Config):

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

### 3. Start Nodes

Open three terminal windows and start three nodes respectively:

```bash
# Terminal 1 - Start Node 1
cd litefs-example
mvn spring-boot:run -Dspring-boot.run.profiles=node1

# Terminal 2 - Start Node 2
cd litefs-example
mvn spring-boot:run -Dspring-boot.run.profiles=node2

# Terminal 3 - Start Node 3
cd litefs-example
mvn spring-boot:run -Dspring-boot.run.profiles=node3
```

### 4. Access Testing

| Node | Address |
|------|------|
| Node-1 | http://localhost:8081/quickstart/index.html |
| Node-2 | http://localhost:8082/quickstart/index.html |
| Node-3 | http://localhost:8083/quickstart/index.html |

### Single Machine Multi-Node Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Single Machine Multi-Node Mode (Dev/Test)     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Node-1 (8081)    Node-2 (8082)    Node-3 (8083)              │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐               │
│   │ File     │     │ File     │     │ File     │               │
│   │ Storage  │     │ Storage  │     │ Storage  │  ← Separate storage
│   │ node1/   │     │ node2/   │     │ node3/   │               │
│   └──────────┘     └──────────┘     └──────────┘               │
│        │                │                │                      │
│        └────────────────┼────────────────┘                      │
│                         │                                       │
│              ┌──────────┴──────────┐                           │
│              │  Shared H2 Database │  ← AUTO_SERVER Mode        │
│              │  data/shared/litefs │                           │
│              └─────────────────────┘                           │
│                                                                  │
│  ⚠️ Limitations:                                                │
│  - Multipart upload does not support cross-node (uses memory)    │
│  - All nodes on same machine, no high availability              │
│  - For development testing only                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Part 2: Production Multi-Node Deployment

For production environment, it is recommended to deploy on multiple physical machines/virtual machines, and use Nacos registry to implement service discovery.

### Production Environment Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Production Environment Architecture               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│    ┌─────────────┐                                                       │
│    │  Nginx/Gateway │  ← Load balancing layer                           │
│    └──────┬──────┘                                                       │
│           │                                                              │
│    ┌──────┴──────┬──────────────┐                                        │
│    │             │              │                                        │
│    ▼             ▼              ▼                                        │
│ ┌──────┐    ┌──────┐     ┌──────┐                                      │
│ │Node-1│    │Node-2│     │Node-3│  ← LiteFS node layer                  │
│ │:8080 │    │:8080 │     │:8080 │                                       │
│ └──┬───┘    └──┬───┘     └──┬───┘                                       │
│    │           │            │                                            │
│    └───────────┼────────────┘                                            │
│                │                                                         │
│    ┌───────────┴───────────┐                                             │
│    │                       │                                             │
│    ▼                       ▼                                             │
│ ┌────────┐            ┌────────┐                                        │
│ │  MySQL │◄──────────►│ Redis  │  ← Shared infrastructure layer          │
│ │(metadata)│           │(cache/ │                                        │
│ └────────┘            │ queue) │                                        │
│                       └────────┘                                        │
│                                                                          │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │                     Nacos Registry                         │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1. Prepare Infrastructure

#### 1.1 MySQL Database

Create database:

```sql
CREATE DATABASE litefs DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 1.2 Redis Service

For multipart upload session storage and metadata cache:

```bash
# Recommend Redis 6.0+
redis-server --port 6379
```

#### 1.3 Nacos Registry (Recommended)

```bash
# Start Nacos (standalone mode example)
sh startup.sh -m standalone
```

### 2. Production Environment Configuration

#### 2.1 Use Nacos Registry

**application-prod.yml** (Common config for all nodes):

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
  node-id: node-1                    # Each node must configure unique ID

  storage:
    type: local
    path: /data/litefs/files        # Use separate disk for production
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
      secret-key: ${LITEFS_SECRET_KEY}  # Read from environment variable
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
    type: nacos                     # Use Nacos registry
    # nacos config will auto-read spring.cloud.nacos.discovery

  multipart:
    store-type: redis               # Distributed mode must use redis
    cleanup-enabled: true
    cleanup-interval: 3600000
    redis:
      host: redis-server
      port: 6379
      password: ${REDIS_PASSWORD}

  cache:
    enabled: true
    type: redis                     # Multi-node shared cache
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

#### 2.2 Static Registry Configuration (Without Nacos)

If you are not using a service registry temporarily, you can manually configure all nodes:

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

### 3. Deployment Steps

#### 3.1 Package Application

```bash
cd litefs
mvn clean package -DskipTests -pl litefs-core,litefs-spring-boot-starter,litefs-example
```

#### 3.2 Deploy to Each Node

```bash
# Copy jar to each server
scp litefs-example/target/litefs-example-*.jar user@node1:/opt/litefs/
scp litefs-example/target/litefs-example-*.jar user@node2:/opt/litefs/
scp litefs-example/target/litefs-example-*.jar user@node3:/opt/litefs/

# Copy config files
scp application-prod.yml user@node1:/opt/litefs/config/
scp application-prod.yml user@node2:/opt/litefs/config/
scp application-prod.yml user@node3:/opt/litefs/config/
```

#### 3.3 Start Services

Execute on each node respectively:

```bash
cd /opt/litefs

# Set environment variables
export LITEFS_SECRET_KEY="your-production-secret-key"
export REDIS_PASSWORD="your-redis-password"

# Start application
java -jar litefs-example-*.jar --spring.config.location=config/application-prod.yml
```

Or use systemd to manage:

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

### 4. Configure Nginx Load Balancing

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

## Key Configuration Comparison

### Development Testing vs Production Environment

| Config | Development Testing | Production Environment |
|-------|---------|---------|
| `remote.enabled` | `true` | `true` |
| `storage.metadata-type` | `h2` / `mysql` | `mysql` |
| `storage.path` | `${user.dir}/data/...` | `/data/litefs/files` |
| `registry.type` | `static` | `nacos` |
| `multipart.store-type` | `local` (no multipart support) | `redis` |
| `cache.enabled` | `false` | `true` |
| `cache.type` | `local` | `redis` |
| `replication.queue.type` | `memory` | `redis` |
| `access.url-type` | `direct` | `gateway` |

---

## Common Problems

### 1. Multipart upload failed: "Upload not found"

**Cause**: Using `multipart.store-type=local` in distributed mode

**Solution**:

```yaml
litefs:
  multipart:
    store-type: redis   # Distributed mode must use redis
```

### 2. Inter-node replication failed

**Check**:
- Is `remote.enabled` set to `true`
- Can each node access each other (is firewall port open)
- Is node address in `registry` config correct

### 3. Metadata inconsistent

**Check**:
- Are all nodes using the same MySQL database
- Is `storage.jdbc-url` configured correctly

### 4. Cache inconsistent

**Symptoms**: After uploading a file, other nodes cannot access immediately

**Solution**: Enable Redis cache

```yaml
litefs:
  cache:
    enabled: true
    type: redis
```

---

## Configuration File List

### Development Testing

| File | Description |
|------|------|
| `application-node1.yml` | Node 1 H2 config |
| `application-node2.yml` | Node 2 H2 config |
| `application-node3.yml` | Node 3 H2 config |

### Production Environment

| File | Description |
|------|------|
| `application-prod.yml` | Production environment common config |
| `litefs.service` | systemd service file |
| `nginx-litefs.conf` | Nginx load balancing config |

---

## Related Documents

- [API Documentation](../API.md)
- [Architecture Design](../developer-guide/ARCHITECTURE.md)
- [SPI Development Guide](../developer-guide/SPI_DEVELOPER_GUIDE.md)
