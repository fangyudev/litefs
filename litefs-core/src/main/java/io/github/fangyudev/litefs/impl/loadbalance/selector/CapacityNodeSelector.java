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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 按可用容量优先的节点选择器
 * 
 * <p>优先选择空闲空间最多的在线节点，适合大文件写入场景。
 * 这种策略可以确保新文件写入到有足够空间的节点，避免因空间不足导致的写入失败。</p>
 * 
 * <h3>选择策略：</h3>
 * <ol>
 *   <li>过滤掉离线（非 ONLINE）状态的节点</li>
 *   <li>过滤掉空间不足的节点（当请求包含文件大小时）</li>
 *   <li>按可用空间降序排序，空间最多的优先</li>
 *   <li>可用空间相同时，按使用率升序排序（使用率低的优先）</li>
 * </ol>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>大文件上传 - 确保有足够空间存储</li>
 *   <li>存储容量不均匀的集群 - 自动选择空间充足的节点</li>
 *   <li>需要避免空间不足错误的场景</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * NodeSelector selector = new CapacityNodeSelector();
 * 
 * // 创建上传请求（包含文件大小）
 * FileUploadRequest request = FileUploadRequest.builder()
 *     .fileName("video.mp4")
 *     .fileSize(1024L * 1024 * 1024)  // 1GB
 *     .build();
 * 
 * // 选择节点
 * List<StorageNode> nodes = serviceRegistry.discover();
 * StorageNode selected = selector.select(nodes, request);
 * 
 * if (selected != null) {
 *     // 使用 selected 节点存储文件
 * }
 * }</pre>
 * 
 * <h3>与其他策略的对比：</h3>
 * <table border="1">
 *   <tr><th>策略</th><th>优点</th><th>缺点</th><th>适用场景</th></tr>
 *   <tr>
 *     <td>CapacityNodeSelector</td>
 *     <td>避免空间不足</td>
 *     <td>可能导致负载不均</td>
 *     <td>大文件、容量不均</td>
 *   </tr>
 *   <tr>
 *     <td>RoundRobinNodeSelector</td>
 *     <td>负载均匀</td>
 *     <td>可能选到空间不足的节点</td>
 *     <td>小文件、容量均匀</td>
 *   </tr>
 * </table>
 * 
 * @see NodeSelector
 * @see RoundRobinNodeSelector
 * @see StorageNode
 */
public class CapacityNodeSelector implements NodeSelector {

    /**
     * 根据容量优先策略选择存储节点
     * 
     * <p>选择逻辑：</p>
     * <ol>
     *   <li>过滤掉为 null 或离线（非 ONLINE）的节点</li>
     *   <li>如果请求中包含文件大小（fileSize > 0），过滤掉空间不足的节点</li>
     *   <li>按可用空间降序排序，可用空间最多的节点优先</li>
     *   <li>可用空间相同时，按使用率升序排序（使用率低的优先）</li>
     * </ol>
     * 
     * <p>注意：当节点的 availableSpace <= 0 时，视为空间未知，允许继续参与选择。
     * 这适用于没有报告空间信息的节点。</p>
     * 
     * @param nodes 可用节点列表，可以为null
     * @param request 上传请求，包含文件大小等信息，可以为null
     * @return 选中的节点，如果没有符合条件的节点则返回null
     */
    @Override
    public StorageNode select(List<StorageNode> nodes, FileUploadRequest request) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        long fileSize = request != null ? request.getFileSize() : 0;
        return nodes.stream()
            .filter(Objects::nonNull)
            .filter(node -> node.getStatus() == NodeStatus.ONLINE)
            .filter(node -> fileSize <= 0 || node.getAvailableSpace() <= 0 || node.getAvailableSpace() >= fileSize)
            .max(Comparator.comparingLong(StorageNode::getAvailableSpace)
                .thenComparing(Comparator.comparingDouble(StorageNode::getUsageRatio).reversed()))
            .orElse(null);
    }
}
