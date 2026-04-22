# LiteFS Architecture Design

> This document introduces LiteFS system architecture and core design decisions

## Project Focus

**A lightweight distributed file storage component**, not a complete microservices system. It should:
- Serve as an embeddable component of the user system
- Integrate into user system through SDK/client library
- Reuse existing middleware in user system (Nacos, Redis, etc.)
- Focus on core functions of file storage, retrieval, and management
- Not be responsible for non-core functions like API gateway, authentication/authorization, etc.

## Core Architecture

LiteFS core architecture consists of the following layers:

### API Layer

Unified interface `FileClient` that exposes all file operation capabilities.

### SPI Layer

Pluggable extension interfaces, including:
- `StorageEngine` — Storage engine
- `MetadataStore` — Metadata storage
- `ServiceRegistry` — Service registry
- `StorageEngineRouter` — Storage engine routing
- `MetadataCacheProvider` — Metadata cache provider
- `MultipartUploadStore` — Multipart upload storage
- `ReplicaMetadataStore` — Replica metadata storage
- `NodeSelector` — Node selection
- `ReplicaPlacer` — Replica placement
- `MessageQueue` — Message queue

### Implementation Layer

Built-in implementations of each SPI interface, such as `LocalStorageEngine`, `H2MetadataStore`, etc.

### Integration Layer

Spring Boot Starter auto-configuration, assembles all components together.

## Design Decisions

### Why use HTTP instead of gRPC for remote communication?

LiteFS is positioned as a lightweight component, HTTP is sufficient for file transfer needs:
- File transfer bottleneck is disk IO and network bandwidth, protocol overhead difference is negligible
- HTTP is simple and easy to use, no additional dependencies
- Supports streaming transmission
- Naturally compatible with Spring ecosystem

### Why use SPI design?

Through SPI mechanism, users can:
- Customize storage engine (e.g., connect to S3, OSS)
- Customize metadata storage (e.g., connect to MongoDB)
- Customize service registry (e.g., connect to Consul)
- Customize message queue (e.g., connect to Kafka)

See [SPI Extension Guide](SPI_DEVELOPER_GUIDE.md) for details.

## Related Documents

- [Learning Guide](LEARNING_GUIDE.md) - Code reading and learning path
- [SPI Extension Guide](SPI_DEVELOPER_GUIDE.md) - Custom extension development
