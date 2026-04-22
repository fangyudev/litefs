# LiteFS Deployment Guide

> This document introduces LiteFS standalone and distributed deployment solutions

## Standalone Deployment

Standalone mode is the default configuration, no additional configuration needed:

```yaml
litefs:
  storage:
    type: local
    path: ./data/files
  access:
    url-type: direct
```

## Distributed Deployment

See [Multi-Node Test Deployment Guide](../tests/MULTI_NODE_TEST.md).

### Prerequisites

- Registry center (Static/Nacos)
- Message queue (Redis/Memory)
- Shared database (MySQL recommended)

### Configuration Example

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

> This document is to be completed and will include:
> - Docker deployment solution
> - Kubernetes deployment solution
> - Production environment configuration suggestions
> - Security configuration

## Related Documents

- [Configuration Reference](../user-guide/CONFIGURATION.md) - All configuration options
- [Multi-Node Test](../tests/MULTI_NODE_TEST.md) - Multi-node test deployment
- [Troubleshooting](TROUBLESHOOTING.md) - Common problem diagnosis
