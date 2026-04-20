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

package io.github.fangyudev.litefs.impl.store.cache;

import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.spi.MetadataCacheProvider;
import io.github.fangyudev.litefs.util.MessageQueueSerializerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis 元数据缓存提供者
 * 
 * <p>基于 Redis 的分布式缓存实现，多节点共享缓存数据，保证缓存一致性。</p>
 * 
 * <h3>特性：</h3>
 * <ul>
 *   <li><b>分布式共享</b> - 多个节点共享同一份缓存数据，避免本地缓存不一致问题</li>
 *   <li><b>TTL过期</b> - 利用 Redis 原生 TTL 机制自动过期</li>
 *   <li><b>序列化</b> - 复用 MessageQueueSerializerUtil（Java 原生序列化）</li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>多节点分布式部署</li>
 *   <li>需要跨节点缓存一致性的场景</li>
 * </ul>
 * 
 * <h3>Redis Key 命名规则：</h3>
 * <pre>
 * 完整 Key = keyPrefix + fileId
 * 例如：litefs:meta:abc123
 * </pre>
 */
public class RedisMetadataCacheProvider implements MetadataCacheProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisMetadataCacheProvider.class);

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
     * @param ttlMillis 缓存过期时间（毫秒）
     */
    public RedisMetadataCacheProvider(String host, int port, String password, int database,
                                       int timeoutMs, String keyPrefix, long ttlMillis) {
        JedisPoolConfig config = new JedisPoolConfig();
        if (password != null && !password.isBlank()) {
            this.pool = new JedisPool(config, host, port, timeoutMs, password, database);
        } else {
            this.pool = new JedisPool(config, host, port, timeoutMs, null, database);
        }
        this.keyPrefix = keyPrefix != null ? keyPrefix : "litefs:meta:";
        this.ttlSeconds = Math.max(ttlMillis / 1000, 1);
    }

    @Override
    public FileMetadata get(String fileId) {
        if (fileId == null) return null;
        try (Jedis jedis = pool.getResource()) {
            byte[] data = jedis.get((keyPrefix + fileId).getBytes());
            if (data == null || data.length == 0) return null;
            Object obj = MessageQueueSerializerUtil.deserialize(data);
            if (obj instanceof FileMetadata) {
                return (FileMetadata) obj;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get metadata from Redis cache: {}", fileId, e);
            return null;
        }
    }

    @Override
    public void put(String fileId, FileMetadata metadata) {
        if (fileId == null || metadata == null) return;
        try (Jedis jedis = pool.getResource()) {
            byte[] data = MessageQueueSerializerUtil.serialize(metadata);
            jedis.setex((keyPrefix + fileId).getBytes(), ttlSeconds, data);
        } catch (Exception e) {
            log.warn("Failed to put metadata to Redis cache: {}", fileId, e);
        }
    }

    @Override
    public void remove(String fileId) {
        if (fileId == null) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del((keyPrefix + fileId).getBytes());
        } catch (Exception e) {
            log.warn("Failed to remove metadata from Redis cache: {}", fileId, e);
        }
    }

    @Override
    public void init() {
        log.info("Redis metadata cache initialized: keyPrefix={}, ttl={}s", keyPrefix, ttlSeconds);
    }

    @Override
    public void shutdown() {
        pool.close();
    }
}
