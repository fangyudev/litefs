# LiteFS Troubleshooting

> This document helps you diagnose and solve common problems when using LiteFS

## Problem Diagnosis Flow

1. Check application logs for exception stack traces
2. Check if configuration file is correct
3. Check if storage path has write permissions
4. Check if database connection is normal
5. Check if network connection is normal

## Common Problems

### Problem 1: Startup error - database not found

**Symptoms**:
- Application fails to start
- Logs show database connection error

**Causes**:
- `storage.jdbc-url` configuration error
- Database service not started

**Solutions**:
1. Check if `storage.jdbc-url` configuration is correct
2. Ensure H2 or MySQL service is running properly
3. H2 mode will automatically create database, check path permissions

### Problem 2: File upload failed

**Symptoms**:
- Error when uploading file
- Logs show storage exception

**Causes**:
- Storage path has no write permission
- Insufficient disk space

**Solutions**:
1. Check `storage.path` directory permissions
2. Check if disk space is sufficient
3. Check if file size exceeds limit

### Problem 3: Distributed nodes cannot communicate

**Symptoms**:
- Cross-node file access fails
- Replica replication not working

**Causes**:
- `remote.enabled` not set to `true`
- Network not reachable between nodes
- Registry configuration error

**Solutions**:
1. Confirm `litefs.remote.enabled=true`
2. Check network connectivity between nodes
3. Check registry configuration

### Problem 4: Cache data inconsistent

**Symptoms**:
- Different nodes read different metadata

**Causes**:
- Using local cache in distributed mode

**Solutions**:
- Distributed mode must use Redis cache
- `cache.type=local` will be automatically disabled in distributed mode

## Related Documents

- [FAQ](../user-guide/FAQ.md) - More Q&A
- [Deployment Guide](DEPLOYMENT.md) - Deployment configuration
- [Configuration Reference](../user-guide/CONFIGURATION.md) - Configuration options
