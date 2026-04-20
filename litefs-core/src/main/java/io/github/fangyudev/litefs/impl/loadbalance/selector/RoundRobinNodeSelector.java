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

package io.github.fangyudev.litefs.impl.loadbalance.selector;

import io.github.fangyudev.litefs.model.FileUploadRequest;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.spi.NodeSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询节点选择器
 * 
 * <p>按顺序循环选择在线节点，保证请求均匀分发到各个节点。
 * 这是最简单、最常用的负载均衡策略，适用于节点性能相近的场景。</p>
 * 
 * <h3>选择策略：</h3>
 * <ol>
 *   <li>过滤掉离线（非 ONLINE）状态的节点</li>
 *   <li>过滤掉空间不足的节点（当请求包含文件大小时）</li>
 *   <li>使用原子计数器记录当前轮询位置</li>
 *   <li>按轮询顺序选择下一个节点</li>
 * </ol>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>节点性能相近 - 各节点处理能力相当</li>
 *   <li>存储容量均匀 - 各节点空间大小相近</li>
 *   <li>小文件频繁上传 - 需要均匀分散请求</li>
 *   <li>高并发场景 - 无锁竞争，性能高</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * NodeSelector selector = new RoundRobinNodeSelector();
 * 
 * // 多次选择会轮询不同节点
 * List<StorageNode> nodes = serviceRegistry.discover();
 * 
 * StorageNode node1 = selector.select(nodes, null);  // 第1个节点
 * StorageNode node2 = selector.select(nodes, null);  // 第2个节点
 * StorageNode node3 = selector.select(nodes, null);  // 第3个节点
 * StorageNode node4 = selector.select(nodes, null);  // 回到第1个节点（循环）
 * }</pre>
 * 
 * <h3>线程安全：</h3>
 * <p>使用 {@link AtomicInteger} 记录轮询位置，保证多线程环境下的正确性。
 * 即使在高并发场景下，也能保证节点选择的均匀性。</p>
 * 
 * <h3>与其他策略的对比：</h3>
 * <table border="1">
 *   <tr><th>策略</th><th>优点</th><th>缺点</th><th>适用场景</th></tr>
 *   <tr>
 *     <td>RoundRobinNodeSelector</td>
 *     <td>负载均匀、简单高效</td>
 *     <td>不考虑节点差异</td>
 *     <td>节点性能相近</td>
 *   </tr>
 *   <tr>
 *     <td>CapacityNodeSelector</td>
 *     <td>避免空间不足</td>
 *     <td>可能导致负载不均</td>
 *     <td>大文件、容量不均</td>
 *   </tr>
 * </table>
 * 
 * @see NodeSelector
 * @see CapacityNodeSelector
 * @see StorageNode
 */
public class RoundRobinNodeSelector implements NodeSelector {

    /** 轮询计数器，记录当前选择位置 */
    private final AtomicInteger index = new AtomicInteger(0);

    /**
     * 根据轮询策略选择存储节点
     * 
     * <p>选择逻辑：</p>
     * <ol>
     *   <li>调用 {@link #filterAvailable} 过滤出符合条件的候选节点</li>
     *   <li>如果没有候选节点，返回 null</li>
     *   <li>使用原子计数器获取当前轮询位置</li>
     *   <li>对候选节点数量取模，得到最终选择的节点索引</li>
     * </ol>
     * 
     * <p>注意：计数器会持续递增，不会重置。使用 Math.abs 确保索引为正数，
     * 并通过取模运算实现循环轮询。</p>
     * 
     * @param nodes 可用节点列表，可以为null
     * @param request 上传请求，包含文件大小等信息，可以为null
     * @return 选中的节点，如果没有符合条件的节点则返回null
     */
    @Override
    public StorageNode select(List<StorageNode> nodes, FileUploadRequest request) {
        List<StorageNode> candidates = filterAvailable(nodes, request);
        if (candidates.isEmpty()) {
            return null;
        }
        int pos = Math.abs(index.getAndIncrement());
        return candidates.get(pos % candidates.size());
    }

    /**
     * 过滤出符合条件的候选节点
     * 
     * <p>过滤条件：</p>
     * <ul>
     *   <li>节点不为 null</li>
     *   <li>节点状态为 ONLINE</li>
     *   <li>节点有足够的存储空间（当请求包含文件大小时）</li>
     * </ul>
     * 
     * <p>当节点的 availableSpace <= 0 时，视为空间未知，允许继续参与选择。
     * 这适用于没有报告空间信息的节点。</p>
     * 
     * @param nodes 原始节点列表
     * @param request 上传请求，用于获取文件大小
     * @return 过滤后的候选节点列表，不会返回null
     */
    private List<StorageNode> filterAvailable(List<StorageNode> nodes, FileUploadRequest request) {
        List<StorageNode> candidates = new ArrayList<>();
        if (nodes == null) {
            return candidates;
        }
        long fileSize = request != null ? request.getFileSize() : 0;
        for (StorageNode node : nodes) {
            if (node == null || node.getStatus() != NodeStatus.ONLINE) {
                continue;
            }
            if (fileSize > 0 && node.getAvailableSpace() > 0 && node.getAvailableSpace() < fileSize) {
                continue;
            }
            candidates.add(node);
        }
        return candidates;
    }
}
