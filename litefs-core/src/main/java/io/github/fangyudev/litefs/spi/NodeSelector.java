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

import io.github.fangyudev.litefs.impl.loadbalance.selector.CapacityNodeSelector;
import io.github.fangyudev.litefs.impl.loadbalance.selector.RoundRobinNodeSelector;
import io.github.fangyudev.litefs.model.FileUploadRequest;
import io.github.fangyudev.litefs.model.StorageNode;

import java.util.List;

/**
 * 节点选择器接口（SPI扩展点）
 * 用于在上传文件时选择合适的存储节点
 * 
 * <p>在分布式存储系统中，文件上传时需要选择一个主节点来存储文件。
 * 节点选择器根据负载均衡策略选择最合适的节点。</p>
 * 
 * <p>内置实现：</p>
 * <ul>
 *   <li>{@link RoundRobinNodeSelector} -
 *       轮询选择，简单均匀分发请求</li>
 *   <li>{@link CapacityNodeSelector} -
 *       容量优先，选择空闲空间最多的节点</li>
 * </ul>
 * 
 * <p>可扩展实现：</p>
 * <ul>
 *   <li>加权轮询选择器 - 根据节点性能分配权重</li>
 *   <li>就近节点选择器 - 优先选择与客户端同区域的节点</li>
 *   <li>一致性哈希选择器 - 相同文件名总是选择相同节点</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class MyNodeSelector implements NodeSelector {
 *     &#64;Override
 *     public StorageNode select(List&lt;StorageNode&gt; nodes, FileUploadRequest request) {
 *         // 自定义选择逻辑
 *         return nodes.get(0);
 *     }
 * }
 * </pre>
 */
public interface NodeSelector {

    /**
     * 从可用节点列表中选择一个存储节点
     * 
     * <p>选择算法应考虑以下因素：</p>
     * <ul>
     *   <li>节点状态 - 只选择在线节点</li>
     *   <li>存储容量 - 确保有足够空间存储文件</li>
     *   <li>负载均衡 - 避免某些节点过载</li>
     *   <li>文件特征 - 可根据文件大小、类型等选择合适节点</li>
     * </ul>
     *
     * @param nodes 可用节点列表（可能包含离线节点，需要过滤）
     * @param request 上传请求上下文，包含文件大小、类型等信息
     * @return 选中的节点，若无可用节点返回null
     */
    StorageNode select(List<StorageNode> nodes, FileUploadRequest request);
}
