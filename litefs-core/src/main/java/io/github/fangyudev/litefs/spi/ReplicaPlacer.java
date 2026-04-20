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

import io.github.fangyudev.litefs.model.FileUploadRequest;
import io.github.fangyudev.litefs.model.ReplicationStrategy;
import io.github.fangyudev.litefs.model.StorageNode;

import java.util.List;

/**
 * 副本放置策略接口（SPI扩展点）
 * 用于选择副本的目标存储节点
 * 
 * <p>在分布式存储系统中，为了提高数据可靠性，文件通常会在多个节点上保存副本。
 * 副本放置策略决定这些副本应该放在哪些节点上。</p>
 * 
 * <p>内置实现：</p>
 * <ul>
 *   <li>{@link io.github.fangyudev.litefs.replication.BalancedReplicaPlacer} - 
 *       平衡分布，尽量分散到不同节点，提高容错能力</li>
 *   <li>{@link io.github.fangyudev.litefs.replication.LocalityReplicaPlacer} - 
 *       就近放置，优先选择与客户端同区域的节点，降低访问延迟</li>
 * </ul>
 * 
 * <p>可扩展实现：</p>
 * <ul>
 *   <li>机架感知放置器 - 确保副本分布在不同机架</li>
 *   <li>成本优化放置器 - 优先使用成本较低的存储节点</li>
 *   <li>性能优先放置器 - 优先选择性能最好的节点</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class MyReplicaPlacer implements ReplicaPlacer {
 *     &#64;Override
 *     public List&lt;StorageNode&gt; selectNodes(List&lt;StorageNode&gt; allNodes,
 *             ReplicationStrategy strategy, FileUploadRequest request) {
 *         // 自定义放置逻辑
 *         return allNodes.subList(0, strategy.getReplicas());
 *     }
 * }
 * </pre>
 */
public interface ReplicaPlacer {

    /**
     * 选择副本目标节点
     * 
     * <p>选择算法应考虑以下因素：</p>
     * <ul>
     *   <li>副本数量 - 根据策略确定需要的节点数</li>
     *   <li>节点状态 - 只选择在线节点</li>
     *   <li>存储容量 - 确保有足够空间存储文件</li>
     *   <li>容错能力 - 副本应尽量分散，避免单点故障</li>
     *   <li>访问延迟 - 可考虑就近放置</li>
     * </ul>
     *
     * @param allNodes 全部可用节点列表
     * @param strategy 副本策略，包含副本数量和放置类型
     * @param request 上传请求上下文，包含文件大小、客户端区域等信息
     * @return 副本节点列表，数量由策略决定
     */
    List<StorageNode> selectNodes(List<StorageNode> allNodes,
                                 ReplicationStrategy strategy,
                                 FileUploadRequest request);
}
