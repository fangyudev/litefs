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
import io.github.fangyudev.litefs.model.FileQuery;
import io.github.fangyudev.litefs.spi.MetadataCacheProvider;
import io.github.fangyudev.litefs.spi.MetadataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 元数据缓存装饰器（Cache Aside Pattern）
 * 
 * <p>为 MetadataStore 提供缓存层，采用标准的 Cache Aside 模式：</p>
 * <ul>
 *   <li><b>读操作</b> - 先查缓存，miss 则读 DB 并回填缓存</li>
 *   <li><b>写操作</b> - 更新 DB 后删除缓存（而非更新缓存），避免并发竞争导致脏数据</li>
 * </ul>
 * 
 * <p>缓存提供者通过 {@link MetadataCacheProvider} 接口注入，可为 null（禁用缓存时等同纯透传）。</p>
 * 
 * <h3>支持的缓存类型：</h3>
 * <ul>
 *   <li>{@code LocalMetadataCacheProvider} - 本地缓存（LRU + TTL），仅限单节点</li>
 *   <li>{@code RedisMetadataCacheProvider} - Redis 分布式缓存，多节点共享</li>
 * </ul>
 * 
 * <h3>Spring Boot 配置方式：</h3>
 * <pre>
 * litefs:
 *   cache:
 *     enabled: true
 *     type: redis          # local | redis
 *     max-size: 10000      # local 专用
 *     ttl: 300000          # 两种模式均支持
 * </pre>
 */
public class MetadataCache implements MetadataStore {

    private static final Logger log = LoggerFactory.getLogger(MetadataCache.class);

    private final MetadataStore delegate;
    private final MetadataCacheProvider cacheProvider;

    /**
     * 构造函数
     * 
     * @param delegate 底层元数据存储
     * @param cacheProvider 缓存提供者，可为 null（禁用缓存）
     */
    public MetadataCache(MetadataStore delegate, MetadataCacheProvider cacheProvider) {
        this.delegate = delegate;
        this.cacheProvider = cacheProvider;
    }

    @Override
    public void init() {
        delegate.init();
        if (cacheProvider != null) {
            cacheProvider.init();
        }
    }

    @Override
    public void shutdown() {
        if (cacheProvider != null) {
            cacheProvider.shutdown();
        }
        delegate.shutdown();
    }

    @Override
    public void save(FileMetadata metadata) {
        if (metadata == null || metadata.getId() == null) {
            return;
        }
        delegate.save(metadata);
        if (cacheProvider != null) {
            cacheProvider.remove(metadata.getId());
        }
    }

    @Override
    public FileMetadata get(String fileId) {
        if (fileId == null) {
            return null;
        }
        if (cacheProvider != null) {
            FileMetadata cached = cacheProvider.get(fileId);
            if (cached != null) {
                return cached;
            }
        }
        FileMetadata metadata = delegate.get(fileId);
        if (cacheProvider != null && metadata != null) {
            cacheProvider.put(fileId, metadata);
        }
        return metadata;
    }

    @Override
    public void delete(String fileId) {
        if (fileId == null) {
            return;
        }
        delegate.delete(fileId);
        if (cacheProvider != null) {
            cacheProvider.remove(fileId);
        }
    }

    @Override
    public void update(FileMetadata metadata) {
        if (metadata == null || metadata.getId() == null) {
            return;
        }
        delegate.update(metadata);
        if (cacheProvider != null) {
            cacheProvider.remove(metadata.getId());
        }
    }

    @Override
    public List<FileMetadata> query(FileQuery query) {
        return delegate.query(query);
    }

    @Override
    public long count(FileQuery query) {
        return delegate.count(query);
    }

    @Override
    public boolean exists(String fileId) {
        if (fileId == null) {
            return false;
        }
        if (cacheProvider != null) {
            FileMetadata cached = cacheProvider.get(fileId);
            if (cached != null) {
                return true;
            }
        }
        return delegate.exists(fileId);
    }
}
