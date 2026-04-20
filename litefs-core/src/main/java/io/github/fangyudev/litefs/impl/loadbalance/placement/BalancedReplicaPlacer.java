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

package io.github.fangyudev.litefs.impl.loadbalance.placement;

import io.github.fangyudev.litefs.model.FileUploadRequest;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.ReplicationStrategy;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.spi.ReplicaPlacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平衡分布副本放置器
 * 
 * <p>按节点的空间使用情况进行排序，让数据尽量均衡地分布到负载较低的节点。
 * 这种策略可以避免某些节点过载，实现存储资源的均衡利用。</p>
 * 
 * <h3>放置策略：</h3>
 * <ol>
 *   <li>过滤掉离线或空间不足的节点</li>
 *   <li>如果策略指定了特定节点（SPECIFIED），只返回白名单中的节点</li>
 *   <li>如果策略要求全量复制（-1），返回所有候选节点</li>
 *   <li>否则按已用空间从小到大排序（空闲多的优先），再按可用空间降序排序</li>
 *   <li>截取策略要求的副本数量</li>
 * </ol>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>节点性能相近 - 各节点处理能力相当</li>
 *   <li>需要均衡存储负载 - 避免某些节点过载</li>
 *   <li>对访问延迟不敏感 - 不考虑就近访问</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * ReplicaPlacer placer = new BalancedReplicaPlacer();
 * 
 * // 获取所有可用节点
 * List<StorageNode> allNodes = serviceRegistry.discover();
 * 
 * // 定义复制策略（3副本）
 * ReplicationStrategy strategy = ReplicationStrategy.standard();
 * 
 * // 选择目标节点
 * List<StorageNode> targets = placer.selectNodes(allNodes, strategy, request);
 * // 结果：选择已用空间最少的3个节点
 * }</pre>
 * 
 * <h3>与其他策略的对比：</h3>
 * <table border="1">
 *   <tr><th>策略</th><th>优点</th><th>缺点</th><th>适用场景</th></tr>
 *   <tr>
 *     <td>BalancedReplicaPlacer</td>
 *     <td>负载均衡</td>
 *     <td>不考虑延迟</td>
 *     <td>跨区域部署</td>
 *   </tr>
 *   <tr>
 *     <td>LocalityReplicaPlacer</td>
 *     <td>就近访问</td>
 *     <td>可能负载不均</td>
 *     <td>多区域部署</td>
 *   </tr>
 * </table>
 * 
 * @see ReplicaPlacer
 * @see LocalityReplicaPlacer
 * @see ReplicationStrategy
 */
public class BalancedReplicaPlacer implements ReplicaPlacer {

    private static final Logger log = LoggerFactory.getLogger(BalancedReplicaPlacer.class);

    /**
     * 根据平衡分布策略选择副本目标节点
     * 
     * <p>选择逻辑：</p>
     * <ol>
     *   <li>调用 {@link #filterAvailable} 过滤出符合条件的候选节点</li>
     *   <li>如果策略类型是 SPECIFIED，只返回白名单中存在的节点</li>
     *   <li>如果副本数为 -1（全节点复制），返回所有候选节点</li>
     *   <li>否则按已用空间升序排序（空闲多的优先），相同则按可用空间降序排序</li>
     *   <li>截取前 N 个节点（N = 策略要求的副本数）</li>
     * </ol>
     * 
     * @param allNodes 所有可用节点列表
     * @param strategy 副本策略，包含副本数量和放置类型
     * @param request 上传请求上下文，包含文件大小等信息
     * @return 选中的目标节点列表
     */
    @Override
    public List<StorageNode> selectNodes(List<StorageNode> allNodes,
                                        ReplicationStrategy strategy,
                                        FileUploadRequest request) {
        if (allNodes == null || allNodes.isEmpty() || strategy == null) {
            log.debug("selectNodes: allNodes is null/empty or strategy is null, returning empty list");
            return new ArrayList<>();
        }

        List<StorageNode> candidates = filterAvailable(allNodes, request);
        log.debug("selectNodes: filtered {} candidates from {} all nodes", candidates.size(), allNodes.size());
        
        if (strategy.getPlacement() == ReplicationStrategy.PlacementType.SPECIFIED) {
            List<StorageNode> result = filterSpecified(candidates, strategy.getSpecifiedNodes());
            log.debug("selectNodes: SPECIFIED placement, returning {} nodes", result.size());
            return result;
        }

        if (strategy.getReplicas() == -1) {
            log.debug("selectNodes: replicas=-1 (all nodes), returning {} candidates", candidates.size());
            return new ArrayList<>(candidates);
        }

        int needed = strategy.getReplicas();
        List<StorageNode> result = candidates.stream()
            .sorted(Comparator.comparingLong(StorageNode::getUsedSpace)
                .thenComparing(Comparator.comparingLong(StorageNode::getAvailableSpace).reversed()))
            .limit(needed)
            .collect(Collectors.toList());
        log.debug("selectNodes: need {} replicas, returning {} nodes", needed, result.size());
        return result;
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
     * @param nodes 原始节点列表
     * @param request 上传请求，用于获取文件大小
     * @return 过滤后的候选节点列表
     */
    private List<StorageNode> filterAvailable(List<StorageNode> nodes, FileUploadRequest request) {
        List<StorageNode> candidates = new ArrayList<>();
        long fileSize = request != null ? request.getFileSize() : 0;
        for (StorageNode node : nodes) {
            if (node == null) {
                log.debug("filterAvailable: skipping null node");
                continue;
            }
            if (node.getStatus() != NodeStatus.ONLINE) {
                log.debug("filterAvailable: skipping node {} with status {}", node.getId(), node.getStatus());
                continue;
            }
            // 只在 availableSpace 被明确设置时才检查空间
            if (fileSize > 0 && node.getAvailableSpace() > 0 && node.getAvailableSpace() < fileSize) {
                log.debug("filterAvailable: skipping node {} due to insufficient space (available={}, needed={})", 
                    node.getId(), node.getAvailableSpace(), fileSize);
                continue;
            }
            log.debug("filterAvailable: adding candidate node {} (totalSpace={}, usedSpace={}, availableSpace={})", 
                node.getId(), node.getTotalSpace(), node.getUsedSpace(), node.getAvailableSpace());
            candidates.add(node);
        }
        return candidates;
    }

    /**
     * 在候选节点中，仅保留策略白名单指定的节点
     * 
     * <p>按照白名单的顺序返回节点，不在白名单中的节点会被过滤掉。</p>
     * 
     * @param nodes 候选节点列表
     * @param specifiedNodes 白名单节点ID列表
     * @return 过滤后的节点列表
     */
    private List<StorageNode> filterSpecified(List<StorageNode> nodes, List<String> specifiedNodes) {
        if (specifiedNodes == null || specifiedNodes.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, StorageNode> map = new HashMap<>();
        for (StorageNode node : nodes) {
            map.put(node.getId(), node);
        }
        List<StorageNode> result = new ArrayList<>();
        for (String nodeId : specifiedNodes) {
            StorageNode node = map.get(nodeId);
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }
}
