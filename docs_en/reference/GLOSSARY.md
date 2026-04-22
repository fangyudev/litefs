# LiteFS Glossary

> This document explains terminology and concepts used in the LiteFS project

## Core Terms

| Term | Description |
|------|------|
| FileClient | Core API interface of LiteFS, provides all file operation capabilities |
| StorageEngine | Storage engine SPI interface, defines file read/write/delete operations |
| MetadataStore | Metadata storage SPI interface, manages file metadata |
| ServiceRegistry | Service registry SPI interface, manages storage node registration and discovery |
| NodeSelector | Node selector, decides which node to store uploaded files |
| ReplicaPlacer | Replica placement strategy, decides which nodes to store file replicas |
| UrlGenerator | URL generator, generates file access URLs |
| SPI | Service Provider Interface, pluggable extension interface |

## Deployment Modes

| Term | Description |
|------|------|
| Standalone Mode | `litefs.remote.enabled=false`, single node operation |
| Distributed Mode | `litefs.remote.enabled=true`, multi-node collaboration |
| Gateway Mode | URL access through unified gateway |
| Direct Access Mode | URL directly accesses the node where the file is located |

## Replica Strategies

| Term | Description |
|------|------|
| NONE | No replica |
| MINIMAL | Minimal replica (1) |
| STANDARD | Standard replica (2) |
| HIGH | High replica (3) |
| ALL_NODES | All nodes |

## Consistency

| Term | Description |
|------|------|
| EVENTUAL | Eventual consistency, async replication |
| STRONG | Strong consistency, sync replication |

## Related Documents

- [Architecture Design](../developer-guide/ARCHITECTURE.md) - System architecture
- [API Usage Guide](../user-guide/API_GUIDE.md) - API usage
