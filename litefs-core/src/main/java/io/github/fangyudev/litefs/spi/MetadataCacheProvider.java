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

package io.github.fangyudev.litefs.spi;

import io.github.fangyudev.litefs.model.FileMetadata;

/**
 * 元数据缓存提供者 SPI 接口
 * 
 * <p>定义元数据缓存的抽象操作，支持多种缓存后端（本地缓存、Redis 等）。</p>
 * 
 * <h3>内置实现：</h3>
 * <ul>
 *   <li>{@code LocalMetadataCacheProvider} - 基于 ConcurrentHashMap 的本地缓存（LRU + TTL）</li>
 *   <li>{@code RedisMetadataCacheProvider} - 基于 Redis 的分布式缓存</li>
 * </ul>
 * 
 * <p>使用方式：通过 {@code MetadataCache} 装饰器委托调用，配合 Cache Aside Pattern 使用。</p>
 */
public interface MetadataCacheProvider {

    /**
     * 获取缓存的元数据
     * 
     * @param fileId 文件ID
     * @return 缓存的元数据，未命中返回 null
     */
    FileMetadata get(String fileId);

    /**
     * 写入缓存
     * 
     * @param fileId 文件ID
     * @param metadata 文件元数据
     */
    void put(String fileId, FileMetadata metadata);

    /**
     * 删除缓存
     * 
     * @param fileId 文件ID
     */
    void remove(String fileId);

    /**
     * 初始化缓存提供者
     */
    void init();

    /**
     * 关闭缓存提供者，释放资源
     */
    void shutdown();
}
