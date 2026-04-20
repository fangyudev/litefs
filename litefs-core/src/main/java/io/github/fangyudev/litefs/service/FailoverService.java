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

package io.github.fangyudev.litefs.service;

import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.ReplicaInfo;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.spi.ReplicaMetadataStore;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * 故障转移服务
 * 
 * <p>提供文件读取的高可用能力，当主节点不可用时，自动切换到健康的副本节点读取。
 * 这是分布式存储系统中保证数据可用性的核心组件。</p>
 * 
 * <h3>故障转移流程：</h3>
 * <pre>
 * 读取请求
 *    ↓
 * 尝试主节点读取
 *    ↓
 * [成功] → 返回数据
 *    ↓
 * [失败] → 遍历副本列表
 *    ↓
 * 检查副本状态（SYNCED）
 *    ↓
 * 检查节点状态（ONLINE）
 *    ↓
 * 尝试副本节点读取
 *    ↓
 * [成功] → 返回数据
 * [全部失败] → 抛出异常
 * </pre>
 * 
 * <h3>副本选择策略：</h3>
 * <ol>
 *   <li>只选择状态为 {@link ReplicaInfo.ReplicaStatus#SYNCED} 的副本</li>
 *   <li>只选择节点状态为 {@link NodeStatus#ONLINE} 的副本</li>
 *   <li>按副本列表顺序依次尝试</li>
 * </ol>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>高可用读取 - 主节点故障时自动切换</li>
 *   <li>计划内维护 - 节点下线期间仍可读取</li>
 *   <li>网络分区 - 部分节点不可达时降级读取</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建故障转移服务
 * FailoverService failoverService = new FailoverService(
 *     serviceRegistry,        // 服务注册中心
 *     replicaMetadataStore,   // 副本元数据存储
 *     storageEngineRouter     // 存储引擎路由器
 * );
 * 
 * // 读取文件（自动故障转移）
 * FileMetadata metadata = metadataStore.get(fileId);
 * try {
 *     InputStream data = failoverService.readWithFailover(metadata);
 *     // 处理数据...
 * } catch (Exception e) {
 *     // 所有副本都不可用
 * }
 * 
 * // 处理节点故障
 * failoverService.onNodeFailure("node-1");  // 标记该节点上的副本为失败
 * }</pre>
 * 
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>故障转移只保证读取可用性，不保证数据一致性</li>
 *   <li>如果使用异步复制，副本可能有短暂延迟</li>
 *   <li>节点恢复后需要手动或自动触发副本重建</li>
 * </ul>
 * 
 * @see ReplicationService
 * @see ReplicaInfo
 * @see ServiceRegistry
 */
public class FailoverService {

    /** 服务注册中心，用于检查节点状态 */
    private final ServiceRegistry serviceRegistry;
    
    /** 副本元数据存储，用于查询文件的副本列表 */
    private final ReplicaMetadataStore replicaStore;
    
    /** 存储引擎路由器，用于获取各节点的存储引擎 */
    private final StorageEngineRouter storageRouter;

    /**
     * 构造函数
     * 
     * @param serviceRegistry 服务注册中心，用于检查节点在线状态
     * @param replicaStore 副本元数据存储，用于查询副本列表
     * @param storageRouter 存储引擎路由器，用于获取存储引擎
     */
    public FailoverService(ServiceRegistry serviceRegistry,
                           ReplicaMetadataStore replicaStore,
                           StorageEngineRouter storageRouter) {
        this.serviceRegistry = serviceRegistry;
        this.replicaStore = replicaStore;
        this.storageRouter = storageRouter;
    }

    /**
     * 带故障转移的文件读取
     * 
     * <p>读取流程：</p>
     * <ol>
     *   <li>首先尝试从主节点读取文件</li>
     *   <li>如果主节点读取失败，查询文件的副本列表</li>
     *   <li>遍历副本列表，跳过以下副本：
     *     <ul>
     *       <li>状态不是 SYNCED 的副本（可能正在同步或已失败）</li>
     *       <li>节点状态不是 ONLINE 的副本</li>
     *     </ul>
     *   </li>
     *   <li>尝试从符合条件的副本节点读取</li>
     *   <li>如果所有副本都不可用，抛出异常</li>
     * </ol>
     * 
     * <p>异常处理：</p>
     * <ul>
     *   <li>主节点异常会被保留，作为最终异常的 cause</li>
     *   <li>副本节点异常会被忽略，继续尝试下一个副本</li>
     * </ul>
     * 
     * @param metadata 文件元数据，包含文件ID和主节点ID
     * @return 文件输入流，调用者负责关闭
     * @throws RuntimeException 当主节点和所有副本都不可用时抛出
     */
    public InputStream readWithFailover(FileMetadata metadata) {
        if (metadata == null) {
            throw new RuntimeException("File metadata is null");
        }
        String fileId = metadata.getId();
        String primaryNodeId = metadata.getStorageNodeId();

        try {
            StorageEngine primary = storageRouter.getEngine(primaryNodeId);
            return primary.read(fileId);
        } catch (Exception primaryEx) {
            List<ReplicaInfo> replicas = replicaStore != null
                ? replicaStore.listByFileId(fileId)
                : Collections.emptyList();
            for (ReplicaInfo replica : replicas) {
                if (replica == null || replica.getNodeId() == null) {
                    continue;
                }
                if (replica.getStatus() != ReplicaInfo.ReplicaStatus.SYNCED) {
                    continue;
                }
                if (!isNodeOnline(replica.getNodeId())) {
                    continue;
                }
                try {
                    StorageEngine engine = storageRouter.getEngine(replica.getNodeId());
                    return engine.read(fileId);
                } catch (Exception ignored) {
                }
            }
            throw new RuntimeException("File not available: " + fileId, primaryEx);
        }
    }

    /**
     * 处理节点故障
     * 
     * <p>当检测到节点故障时调用此方法，将该节点上的所有副本状态标记为 FAILED。
     * 这有助于：</p>
     * <ul>
     *   <li>后续读取时跳过这些副本</li>
     *   <li>触发副本重建流程</li>
     *   <li>统计节点健康状态</li>
     * </ul>
     * 
     * <h3>使用场景：</h3>
     * <pre>{@code
     * // 定时健康检查
     * scheduler.scheduleAtFixedRate(() -> {
     *     for (StorageNode node : serviceRegistry.discover()) {
     *         if (!isHealthy(node)) {
     *             failoverService.onNodeFailure(node.getId());
     *         }
     *     }
     * }, 1, 1, TimeUnit.MINUTES);
     * }</pre>
     * 
     * @param nodeId 故障节点的ID
     */
    public void onNodeFailure(String nodeId) {
        if (nodeId == null || replicaStore == null) {
            return;
        }
        List<ReplicaInfo> replicas = replicaStore.listByNodeId(nodeId);
        for (ReplicaInfo replica : replicas) {
            replicaStore.updateStatus(replica.getFileId(), nodeId,
                ReplicaInfo.ReplicaStatus.FAILED, System.currentTimeMillis(), null);
        }
    }

    /**
     * 检查节点是否在线
     * 
     * <p>从服务注册中心查询节点状态。如果没有注册中心或节点ID为空，
     * 保守地认为节点在线（避免误判导致无法读取）。</p>
     * 
     * @param nodeId 节点ID
     * @return true 表示节点在线，false 表示节点离线或不存在
     */
    private boolean isNodeOnline(String nodeId) {
        if (serviceRegistry == null || nodeId == null) {
            return true;
        }
        StorageNode node = serviceRegistry.get(nodeId);
        return node != null && node.getStatus() == NodeStatus.ONLINE;
    }
}
