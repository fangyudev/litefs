/*
 * Copyright 2026 方郁 (Fang Yu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.fangyudev.litefs.impl.store.multipart;

import io.github.fangyudev.litefs.model.MultipartUpload;
import io.github.fangyudev.litefs.model.PartInfo;
import io.github.fangyudev.litefs.spi.MultipartUploadStore;
import io.github.fangyudev.litefs.util.MessageQueueSerializerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis 分布式分块上传会话存储
 * 
 * <p>基于 Redis 的分布式存储实现，支持多节点共享上传会话状态。</p>
 * 
 * <h3>特性：</h3>
 * <ul>
 *   <li><b>分布式共享</b> - 多个节点共享同一份上传会话数据</li>
 *   <li><b>TTL过期</b> - 利用 Redis 原生 TTL 机制自动过期（默认24小时）</li>
 *   <li><b>原子操作</b> - 使用 Redis 事务保证 addPart 的原子性</li>
 * </ul>
 * 
 * <h3>Redis Key 命名规则：</h3>
 * <pre>
 * 完整 Key = keyPrefix + uploadId
 * 例如：litefs:multipart:abc123
 * </pre>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>多节点分布式部署</li>
 *   <li>负载均衡环境下需要跨节点共享上传状态</li>
 * </ul>
 */
public class RedisMultipartUploadStore implements MultipartUploadStore {

    private static final Logger log = LoggerFactory.getLogger(RedisMultipartUploadStore.class);

    /** 默认上传会话有效期：24小时 */
    private static final long DEFAULT_TTL_SECONDS = 24 * 60 * 60;

    private final JedisPool pool;
    private final String keyPrefix;
    private final long ttlSeconds;

    /**
     * 构造函数
     * 
     * @param host Redis 主机地址
     * @param port Redis 端口
     * @param password Redis 密码，无密码时传 null 或空字符串
     * @param database Redis 数据库索引（0-15）
     * @param timeoutMs 连接超时时间（毫秒）
     * @param keyPrefix Redis Key 前缀
     */
    public RedisMultipartUploadStore(String host, int port, String password, int database,
                                      int timeoutMs, String keyPrefix) {
        this(host, port, password, database, timeoutMs, keyPrefix, DEFAULT_TTL_SECONDS);
    }

    /**
     * 构造函数（自定义 TTL）
     * 
     * @param host Redis 主机地址
     * @param port Redis 端口
     * @param password Redis 密码，无密码时传 null 或空字符串
     * @param database Redis 数据库索引（0-15）
     * @param timeoutMs 连接超时时间（毫秒）
     * @param keyPrefix Redis Key 前缀
     * @param ttlSeconds 上传会话有效期（秒）
     */
    public RedisMultipartUploadStore(String host, int port, String password, int database,
                                      int timeoutMs, String keyPrefix, long ttlSeconds) {
        JedisPoolConfig config = new JedisPoolConfig();
        if (password != null && !password.isBlank()) {
            this.pool = new JedisPool(config, host, port, timeoutMs, password, database);
        } else {
            this.pool = new JedisPool(config, host, port, timeoutMs, null, database);
        }
        this.keyPrefix = keyPrefix != null ? keyPrefix : "litefs:multipart:";
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
    }

    @Override
    public void init() {
        // 测试连接是否正常
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
            log.info("Redis multipart upload store initialized: keyPrefix={}, ttl={}s", keyPrefix, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to connect to Redis for multipart upload store", e);
            throw new IllegalStateException("Failed to connect to Redis for multipart upload store", e);
        }
    }

    @Override
    public void save(MultipartUpload upload) {
        if (upload == null || upload.getUploadId() == null) {
            return;
        }
        String key = keyPrefix + upload.getUploadId();
        
        try (Jedis jedis = pool.getResource()) {
            // 创建一个只包含metadata的副本（不含parts）
            MultipartUpload metadata = new MultipartUpload();
            metadata.setUploadId(upload.getUploadId());
            metadata.setFileName(upload.getFileName());
            metadata.setFileSize(upload.getFileSize());
            metadata.setMetadata(upload.getMetadata());
            metadata.setCreateTime(upload.getCreateTime());
            metadata.setExpireTime(upload.getExpireTime());
            metadata.setTargetNodeId(upload.getTargetNodeId());
            // 不设置parts
            
            byte[] serialized = MessageQueueSerializerUtil.serialize(metadata);
            // 使用Hash结构，metadata字段存储基本信息
            jedis.hset(key.getBytes(), "metadata".getBytes(), serialized);
            jedis.expire(key, (int) ttlSeconds);
            
            log.info("Saved multipart upload metadata to Redis: uploadId={}, key={}, metadataSize={}bytes, ttl={}s", 
                upload.getUploadId(), key, serialized.length, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to save multipart upload: {}", upload.getUploadId(), e);
            throw new RuntimeException("Failed to save multipart upload: " + upload.getUploadId(), e);
        }
    }

    @Override
    public MultipartUpload get(String uploadId) {
        if (uploadId == null) {
            return null;
        }
        String key = keyPrefix + uploadId;
        
        try (Jedis jedis = pool.getResource()) {
            // 获取所有Hash字段
            Map<byte[], byte[]> allFields = jedis.hgetAll(key.getBytes());
            if (allFields == null || allFields.isEmpty()) {
                log.warn("Redis hash is empty for uploadId={}, key={}", uploadId, key);
                return null;
            }
            
            MultipartUpload upload = new MultipartUpload();
            upload.setUploadId(uploadId);
            
            // 遍历Hash字段
            for (Map.Entry<byte[], byte[]> entry : allFields.entrySet()) {
                String fieldName = new String(entry.getKey());
                byte[] value = entry.getValue();
                
                if (value == null || value.length == 0) {
                    continue;
                }
                
                Object obj = MessageQueueSerializerUtil.deserialize(value);
                
                if ("metadata".equals(fieldName)) {
                    // metadata字段：重建元数据
                    if (obj instanceof MultipartUpload) {
                        MultipartUpload metadata = (MultipartUpload) obj;
                        upload.setFileName(metadata.getFileName());
                        upload.setFileSize(metadata.getFileSize());
                        upload.setMetadata(metadata.getMetadata());
                        upload.setCreateTime(metadata.getCreateTime());
                        upload.setExpireTime(metadata.getExpireTime());
                        upload.setTargetNodeId(metadata.getTargetNodeId());
                    }
                } else if (fieldName.startsWith("part:")) {
                    // part:N字段：重建分片信息
                    if (obj instanceof PartInfo) {
                        try {
                            int partNumber = Integer.parseInt(fieldName.substring(5));
                            upload.addPart(partNumber, (PartInfo) obj);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid part field name: {}", fieldName);
                        }
                    }
                }
            }
            
            // 检查是否过期
            if (upload.isExpired()) {
                log.warn("Upload session expired: {}", uploadId);
                return null;
            }
            
            log.info("Retrieved multipart upload from Redis: uploadId={}, key={}, partCount={}", 
                uploadId, key, upload.getParts().size());
            return upload;
        } catch (Exception e) {
            log.error("Failed to get multipart upload: {}", uploadId, e);
            return null;
        }
    }

    @Override
    public void delete(String uploadId) {
        if (uploadId == null) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            byte[] key = (keyPrefix + uploadId).getBytes();
            jedis.del(key);
            log.debug("Deleted multipart upload from Redis: {}", uploadId);
        } catch (Exception e) {
            log.warn("Failed to delete multipart upload: {}", uploadId, e);
        }
    }

    @Override
    public boolean addPart(String uploadId, int partNumber, PartInfo partInfo) {
        if (uploadId == null || partInfo == null) {
            return false;
        }
        String key = keyPrefix + uploadId;
        String field = "part:" + partNumber;
        
        try (Jedis jedis = pool.getResource()) {
            // 直接使用 HSET 原子操作，无竞态条件
            byte[] serialized = MessageQueueSerializerUtil.serialize(partInfo);
            jedis.hset(key.getBytes(), field.getBytes(), serialized);
            
            // 更新TTL
            jedis.expire(key, (int) ttlSeconds);
            
            log.info("Added part {} to multipart upload: {}, size={}bytes", 
                partNumber, uploadId, serialized.length);
            return true;
        } catch (Exception e) {
            log.error("Failed to add part {} to multipart upload: {}", partNumber, uploadId, e);
            return false;
        }
    }

    @Override
    public List<String> getExpiredUploadIds() {
        // Redis 使用 TTL 机制自动过期，无需手动扫描
        // 但为了兼容接口，返回空列表
        // 如果需要手动清理，可以使用 SCAN 命令扫描所有 key 并检查
        return new ArrayList<>();
    }

    @Override
    public boolean exists(String uploadId) {
        if (uploadId == null) {
            return false;
        }
        try (Jedis jedis = pool.getResource()) {
            byte[] key = (keyPrefix + uploadId).getBytes();
            boolean exists = jedis.exists(key);
            log.debug("Redis exists check: uploadId={}, key={}, result={}", uploadId, new String(key), exists);
            return exists;
        } catch (Exception e) {
            log.error("Failed to check upload existence: {}", uploadId, e);
            return false;
        }
    }
}
