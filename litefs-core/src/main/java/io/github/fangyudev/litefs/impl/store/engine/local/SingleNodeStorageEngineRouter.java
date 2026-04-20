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

package io.github.fangyudev.litefs.impl.store.engine.local;

import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;

/**
 * 单节点存储引擎路由器
 * 
 * <p>只管理一个本地存储引擎，适用于单机部署场景。
 * 在单机部署模式下，所有文件都存储在本地存储引擎中。</p>
 * 
 * <h3>工作原理：</h3>
 * <p>此路由器忽略节点ID参数，始终返回同一个存储引擎实例。
 * 如果尝试获取其他节点的存储引擎，将抛出异常。</p>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>单机应用 - 所有文件存储在本地文件系统</li>
 *   <li>开发测试 - 简化配置，无需搭建分布式环境</li>
 *   <li>嵌入式场景 - 作为库嵌入到其他应用中</li>
 * </ul>
 * 
 * <h3>限制：</h3>
 * <ul>
 *   <li>无法访问其他节点的存储引擎</li>
 *   <li>不支持分布式副本同步</li>
 *   <li>不支持跨节点故障转移</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建本地存储引擎
 * StorageEngine localStorage = new LocalStorageEngine("/data/files");
 * 
 * // 创建单节点路由器
 * StorageEngineRouter router = new SingleNodeStorageEngineRouter("node-1", localStorage);
 * 
 * // 获取存储引擎（无论传入什么nodeId，都返回本地存储引擎）
 * StorageEngine engine = router.getEngine("node-1");
 * StorageEngine engine2 = router.getEngine(null);  // 同样返回本地存储引擎
 * 
 * // 读取文件
 * InputStream data = engine.read(fileId);
 * }</pre>
 * 
 * <h3>与分布式路由器的对比：</h3>
 * <table border="1">
 *   <tr><th>特性</th><th>SingleNodeStorageEngineRouter</th><th>分布式路由器</th></tr>
 *   <tr><td>节点数量</td><td>1个</td><td>多个</td></tr>
 *   <tr><td>跨节点访问</td><td>不支持</td><td>支持</td></tr>
 *   <tr><td>副本同步</td><td>不支持</td><td>支持</td></tr>
 *   <tr><td>故障转移</td><td>不支持</td><td>支持</td></tr>
 * </table>
 * 
 * @see StorageEngineRouter
 * @see StorageEngine
 * @see io.github.fangyudev.litefs.storage.LocalStorageEngine
 */
public class SingleNodeStorageEngineRouter implements StorageEngineRouter {

    /** 本地节点ID，用于标识当前进程所属的存储节点 */
    private final String localNodeId;
    
    /** 本地存储引擎实例 */
    private final StorageEngine storageEngine;

    /**
     * 构造函数
     * 
     * @param localNodeId 本地节点ID，用于标识当前节点
     * @param storageEngine 本地存储引擎实例
     */
    public SingleNodeStorageEngineRouter(String localNodeId, StorageEngine storageEngine) {
        this.localNodeId = localNodeId;
        this.storageEngine = storageEngine;
    }

    /**
     * 获取存储引擎
     * 
     * <p>在单节点模式下，只能获取本地节点的存储引擎。
     * 如果请求其他节点的存储引擎，将抛出异常。</p>
     * 
     * <h3>参数说明：</h3>
     * <ul>
     *   <li>nodeId == null - 返回本地存储引擎</li>
     *   <li>nodeId == localNodeId - 返回本地存储引擎</li>
     *   <li>nodeId != localNodeId - 抛出 IllegalArgumentException</li>
     * </ul>
     * 
     * @param nodeId 节点ID，null或本地节点ID返回本地存储引擎
     * @return 本地存储引擎实例
     * @throws IllegalArgumentException 当请求非本地节点的存储引擎时抛出
     */
    @Override
    public StorageEngine getEngine(String nodeId) {
        if (nodeId == null || nodeId.equals(localNodeId)) {
            return storageEngine;
        }
        throw new IllegalArgumentException("No storage engine configured for node: " + nodeId);
    }

    /**
     * 获取本地节点ID
     * 
     * <p>用于标识当前进程所属的存储节点。</p>
     * 
     * @return 本地节点ID
     */
    @Override
    public String getLocalNodeId() {
        return localNodeId;
    }
}
