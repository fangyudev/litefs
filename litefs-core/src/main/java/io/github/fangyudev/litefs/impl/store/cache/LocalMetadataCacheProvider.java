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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 本地元数据缓存提供者
 * 
 * <p>基于 ConcurrentHashMap 的 JVM 本地缓存实现，支持 LRU 淘汰和 TTL 过期。</p>
 * 
 * <h3>特性：</h3>
 * <ul>
 *   <li><b>LRU淘汰</b> - 当缓存达到最大容量时，淘汰最久未使用的条目</li>
 *   <li><b>TTL过期</b> - 支持设置缓存条目的生存时间</li>
 *   <li><b>定时清理</b> - 后台线程定期清理过期条目</li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>单节点部署</li>
 *   <li>开发/测试环境</li>
 *   <li>不需要跨节点缓存一致性的场景</li>
 * </ul>
 */
public class LocalMetadataCacheProvider implements MetadataCacheProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalMetadataCacheProvider.class);

    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final int maxSize;
    private final long ttlMillis;
    private final ScheduledExecutorService scheduler;

    public LocalMetadataCacheProvider(int maxSize, long ttlMillis) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        this.cache = new ConcurrentHashMap<>(maxSize > 0 ? maxSize : 1024);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metadata-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::evictExpired, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public FileMetadata get(String fileId) {
        if (fileId == null) return null;
        CacheEntry entry = cache.get(fileId);
        if (entry == null) return null;
        if (entry.isExpired()) {
            cache.remove(fileId, entry);
            return null;
        }
        entry.updateAccessTime();
        return entry.getMetadata();
    }

    @Override
    public void put(String fileId, FileMetadata metadata) {
        if (fileId == null || metadata == null) return;
        if (maxSize > 0 && cache.size() >= maxSize) {
            evictLRU();
        }
        cache.put(fileId, new CacheEntry(metadata, ttlMillis));
    }

    @Override
    public void remove(String fileId) {
        if (fileId != null) {
            cache.remove(fileId);
        }
    }

    @Override
    public void init() {
        log.info("Local metadata cache initialized: maxSize={}, ttl={}ms", maxSize, ttlMillis);
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private void evictLRU() {
        String oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().getLastAccessTime() < oldestAccess) {
                oldestAccess = entry.getValue().getLastAccessTime();
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    private void evictExpired() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private static class CacheEntry {
        private final FileMetadata metadata;
        private final long expireTime;
        private volatile long lastAccessTime;

        CacheEntry(FileMetadata metadata, long ttlMillis) {
            this.metadata = metadata;
            this.expireTime = System.currentTimeMillis() + ttlMillis;
            this.lastAccessTime = System.currentTimeMillis();
        }

        FileMetadata getMetadata() {
            return metadata;
        }

        long getLastAccessTime() {
            return lastAccessTime;
        }

        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
