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

package io.github.fangyudev.litefs.impl.store.engine.remote;

import io.github.fangyudev.litefs.impl.store.engine.local.SingleNodeStorageEngineRouter;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式存储引擎路由器
 * 
 * <p>支持跨节点访问存储引擎，是分布式存储的核心组件。
 * 通过 HTTP 协议创建远程存储引擎代理。</p>
 * 
 * <h3>工作原理：</h3>
 * <pre>
 * getEngine(nodeId)
 *      ↓
 * nodeId == localNodeId ? → 返回本地存储引擎
 *      ↓
 * nodeId != localNodeId ? → 返回远程存储引擎代理（HTTP）
 * </pre>
 * 
 * <h3>访问模式：</h3>
 * <ul>
 *   <li><b>HTTP</b> - 唯一方式，简单易用，无额外依赖，支持流式传输</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建分布式存储引擎路由器
 * StorageEngineRouter router = new DistributedStorageEngineRouter(
 *     "node-1",                  // 本地节点ID
 *     localStorageEngine,        // 本地存储引擎
 *     serviceRegistry,           // 服务注册中心
 *     5000,                      // 连接超时
 *     30000                      // 读取超时
 * );
 * 
 * // 获取本地存储引擎
 * StorageEngine local = router.getEngine("node-1");
 * 
 * // 获取远程存储引擎（自动创建代理）
 * StorageEngine remote = router.getEngine("node-2");
 * 
 * // 读取远程文件
 * InputStream data = remote.read(fileId);
 * }</pre>
 * 
 * <h3>缓存策略：</h3>
 * <p>远程存储引擎代理会被缓存，避免重复创建。当节点地址变更时，
 * 代理会自动刷新地址缓存。</p>
 * 
 * <h3>与 SingleNodeStorageEngineRouter 的区别：</h3>
 * <ul>
 *   <li>{@code SingleNodeStorageEngineRouter} - 单机模式，只返回本地存储引擎</li>
 *   <li>{@code DistributedStorageEngineRouter} - 分布式模式，返回本地或远程存储引擎</li>
 * </ul>
 * 
 * @see StorageEngineRouter
 * @see HttpRemoteStorageEngine
 * @see SingleNodeStorageEngineRouter
 */
public class DistributedStorageEngineRouter implements StorageEngineRouter {

    /** 本地节点ID */
    private final String localNodeId;
    
    /** 本地存储引擎 */
    private final StorageEngine localStorageEngine;
    
    /** 服务注册中心 */
    private final ServiceRegistry serviceRegistry;
    /** 连接超时（毫秒） */
    private final int connectTimeout;
    
    /** 读取超时（毫秒） */
    private final int readTimeout;
    
    /** 远程存储引擎缓存：nodeId -> StorageEngine */
    private final Map<String, StorageEngine> remoteEngineCache = new ConcurrentHashMap<>();

    public DistributedStorageEngineRouter(String localNodeId,
                                          StorageEngine localStorageEngine,
                                          ServiceRegistry serviceRegistry) {
        this(localNodeId, localStorageEngine, serviceRegistry, 5000, 30000);
    }

    public DistributedStorageEngineRouter(String localNodeId,
                                          StorageEngine localStorageEngine,
                                          ServiceRegistry serviceRegistry,
                                          int connectTimeout,
                                          int readTimeout) {
        this.localNodeId = localNodeId;
        this.localStorageEngine = localStorageEngine;
        this.serviceRegistry = serviceRegistry;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * 根据节点ID获取存储引擎
     * 
     * <p>选择逻辑：</p>
     * <ol>
     *   <li>如果 nodeId 为 null 或等于本地节点ID，返回本地存储引擎</li>
     *   <li>否则从缓存中查找远程存储引擎</li>
     *   <li>缓存未命中则根据访问模式创建新的远程存储引擎代理</li>
     * </ol>
     * 
     * @param nodeId 节点ID
     * @return 存储引擎实例
     * @throws IllegalArgumentException 当节点不存在时抛出
     */
    @Override
    public StorageEngine getEngine(String nodeId) {
        if (nodeId == null || nodeId.equals(localNodeId)) {
            return localStorageEngine;
        }
        
        return remoteEngineCache.computeIfAbsent(nodeId, this::createRemoteEngine);
    }

    /**
     * 获取本地节点ID
     * 
     * @return 本地节点ID
     */
    @Override
    public String getLocalNodeId() {
        return localNodeId;
    }

    private StorageEngine createRemoteEngine(String nodeId) {
        return new HttpRemoteStorageEngine(nodeId, serviceRegistry, connectTimeout, readTimeout);
    }

    /**
     * 清除远程存储引擎缓存
     * 
     * <p>当节点地址变更或节点下线时调用此方法。</p>
     */
    public void clearCache() {
        remoteEngineCache.clear();
    }

    /**
     * 清除指定节点的缓存
     * 
     * @param nodeId 节点ID
     */
    public void invalidateCache(String nodeId) {
        remoteEngineCache.remove(nodeId);
    }
}
