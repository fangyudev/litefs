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

import io.github.fangyudev.litefs.impl.store.engine.local.SingleNodeStorageEngineRouter;

/**
 * 存储引擎路由器接口（SPI扩展点）
 * 根据节点ID获取对应的存储引擎实例
 * 
 * <p>在分布式存储系统中，文件可能分布在多个节点上，每个节点有自己的存储引擎。
 * StorageEngineRouter 提供了根据节点ID获取存储引擎的能力。</p>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>文件上传 - 选择目标节点后，获取该节点的存储引擎写入数据</li>
 *   <li>文件下载 - 根据文件元数据中的节点ID，获取对应存储引擎读取数据</li>
 *   <li>副本同步 - 从源节点读取数据，写入目标节点</li>
 *   <li>故障转移 - 主节点不可用时，从副本节点读取数据</li>
 * </ul>
 * 
 * <p>内置实现：</p>
 * <ul>
 *   <li>{@link SingleNodeStorageEngineRouter} -
 *       单节点路由器，适用于单机部署</li>
 * </ul>
 * 
 * <p>可扩展实现：</p>
 * <ul>
 *   <li>远程存储引擎路由器 - 通过网络访问其他节点的存储引擎</li>
 *   <li>缓存路由器 - 缓存远程存储引擎的连接，提高性能</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 获取文件所在节点的存储引擎
 * StorageEngine engine = router.getEngine(metadata.getStorageNodeId());
 * 
 * // 读取文件
 * InputStream data = engine.read(fileId);
 * </pre>
 */
public interface StorageEngineRouter {

    /**
     * 根据节点ID获取存储引擎
     * 
     * <p><b>实现要求（重要）：</b></p>
     * <ul>
     *   <li>当 nodeId 为 null 或等于 {@link #getLocalNodeId()} 时，<b>必须</b>返回本地存储引擎实例</li>
     *   <li>当 nodeId 为其他节点时，返回远程存储引擎代理</li>
     *   <li>不满足上述要求的实现将被视为错误实现</li>
     * </ul>
     * 
     * <p>注意：在单机模式下，只能获取本地节点的存储引擎；
     * 在分布式模式下，可能返回远程存储引擎的代理。</p>
     *
     * @param nodeId 节点ID，null表示本地节点
     * @return 存储引擎实例
     * @throws IllegalArgumentException 当节点不存在或无法访问时抛出
     */
    StorageEngine getEngine(String nodeId);

    /**
     * 获取本地节点ID
     * 用于标识当前进程所属的存储节点
     * 
     * @return 本地节点ID
     */
    String getLocalNodeId();
}
