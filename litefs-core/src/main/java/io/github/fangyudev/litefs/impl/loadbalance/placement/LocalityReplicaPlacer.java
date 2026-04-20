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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 就近放置副本放置器
 * 
 * <p>优先把副本放在与客户端同一可用区/机房的节点上，减少读写延迟。
 * 这种策略适合多区域部署的场景，可以显著降低跨区域访问的网络延迟。</p>
 * 
 * <h3>放置策略：</h3>
 * <ol>
 *   <li>过滤掉离线或空间不足的节点</li>
 *   <li>如果策略指定了特定节点（SPECIFIED），只返回白名单中的节点</li>
 *   <li>如果策略要求全量复制（-1），返回所有候选节点</li>
 *   <li>如果请求中包含客户端区域信息（clientZone），优先选择同区域节点</li>
 *   <li>同区域节点不足时，从其他区域补齐</li>
 * </ol>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>多区域部署 - 节点分布在不同机房/区域</li>
 *   <li>对访问延迟敏感 - 需要就近访问</li>
 *   <li>区域亲和性要求 - 数据优先存储在用户所在区域</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * ReplicaPlacer placer = new LocalityReplicaPlacer();
 * 
 * // 创建上传请求，指定客户端区域
 * FileUploadRequest request = FileUploadRequest.builder()
 *     .fileName("document.pdf")
 *     .fileSize(1024 * 1024)
 *     .clientZone("cn-east-1")  // 客户端所在区域
 *     .build();
 * 
 * // 获取所有可用节点
 * List<StorageNode> allNodes = serviceRegistry.discover();
 * 
 * // 定义复制策略（2副本）
 * ReplicationStrategy strategy = ReplicationStrategy.standard();
 * 
 * // 选择目标节点
 * List<StorageNode> targets = placer.selectNodes(allNodes, strategy, request);
 * // 结果：优先选择 cn-east-1 区域的节点，不足时从其他区域补齐
 * }</pre>
 * 
 * <h3>区域匹配逻辑：</h3>
 * <pre>
 * 假设有以下节点：
 * - node-1: zone=cn-east-1, 可用空间 100GB
 * - node-2: zone=cn-east-1, 可用空间 80GB
 * - node-3: zone=cn-north-1, 可用空间 200GB
 * - node-4: zone=cn-north-1, 可用空间 150GB
 * 
 * 客户端区域：cn-east-1
 * 策略：3副本
 * 
 * 选择结果：
 * 1. node-1 (同区域)
 * 2. node-2 (同区域)
 * 3. node-3 (其他区域补齐)
 * </pre>
 * 
 * <h3>与其他策略的对比：</h3>
 * <table border="1">
 *   <tr><th>策略</th><th>优点</th><th>缺点</th><th>适用场景</th></tr>
 *   <tr>
 *     <td>LocalityReplicaPlacer</td>
 *     <td>就近访问、低延迟</td>
 *     <td>可能负载不均</td>
 *     <td>多区域部署</td>
 *   </tr>
 *   <tr>
 *     <td>BalancedReplicaPlacer</td>
 *     <td>负载均衡</td>
 *     <td>不考虑延迟</td>
 *     <td>跨区域部署</td>
 *   </tr>
 * </table>
 * 
 * @see ReplicaPlacer
 * @see BalancedReplicaPlacer
 * @see ReplicationStrategy
 * @see FileUploadRequest#getClientZone()
 */
public class LocalityReplicaPlacer implements ReplicaPlacer {

    /**
     * 根据就近放置策略选择副本目标节点
     * 
     * <p>选择逻辑：</p>
     * <ol>
     *   <li>调用 {@link #filterAvailable} 过滤出符合条件的候选节点</li>
     *   <li>如果策略类型是 SPECIFIED，只返回白名单中存在的节点</li>
     *   <li>如果副本数为 -1（全节点复制），返回所有候选节点</li>
     *   <li>如果请求中包含客户端区域信息（clientZone）：
     *     <ul>
     *       <li>优先选择同区域的节点</li>
     *       <li>同区域节点不足时，从其他区域补齐</li>
     *     </ul>
     *   </li>
     *   <li>如果没有区域信息，直接取前 N 个候选节点</li>
     * </ol>
     * 
     * @param allNodes 所有可用节点列表
     * @param strategy 副本策略，包含副本数量和放置类型
     * @param request 上传请求上下文，包含客户端区域等信息
     * @return 选中的目标节点列表
     */
    @Override
    public List<StorageNode> selectNodes(List<StorageNode> allNodes,
                                        ReplicationStrategy strategy,
                                        FileUploadRequest request) {
        if (allNodes == null || allNodes.isEmpty() || strategy == null) {
            return new ArrayList<>();
        }

        List<StorageNode> candidates = filterAvailable(allNodes, request);
        if (strategy.getPlacement() == ReplicationStrategy.PlacementType.SPECIFIED) {
            return filterSpecified(candidates, strategy.getSpecifiedNodes());
        }

        if (strategy.getReplicas() == -1) {
            return new ArrayList<>(candidates);
        }

        int needed = strategy.getReplicas();
        String zone = request != null ? request.getClientZone() : null;
        if (zone == null || zone.isEmpty()) {
            return candidates.stream().limit(needed).collect(Collectors.toList());
        }

        List<StorageNode> sameZone = candidates.stream()
            .filter(n -> zone.equals(n.getZone()))
            .collect(Collectors.toList());
        List<StorageNode> otherZone = candidates.stream()
            .filter(n -> !zone.equals(n.getZone()))
            .collect(Collectors.toList());

        List<StorageNode> result = new ArrayList<>();
        result.addAll(sameZone.stream().limit(needed).collect(Collectors.toList()));
        if (result.size() < needed) {
            result.addAll(otherZone.stream().limit(needed - result.size()).collect(Collectors.toList()));
        }
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
