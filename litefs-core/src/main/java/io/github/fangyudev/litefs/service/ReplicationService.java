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

import io.github.fangyudev.litefs.model.*;
import io.github.fangyudev.litefs.spi.MessageQueue;
import io.github.fangyudev.litefs.spi.ReplicaMetadataStore;
import io.github.fangyudev.litefs.spi.ReplicaPlacer;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据复制与同步服务
 * 
 * <p>负责根据复制策略选择目标节点，执行从主节点到副本节点的文件拷贝，
 * 并记录副本元数据。支持同步复制和异步复制两种模式。</p>
 * 
 * <h3>核心职责：</h3>
 * <ul>
 *   <li><b>策略解析</b> - 从文件元数据或请求参数中解析复制策略</li>
 *   <li><b>节点选择</b> - 使用 {@link ReplicaPlacer} 选择副本目标节点</li>
 *   <li><b>数据复制</b> - 从主节点读取文件，写入到副本节点</li>
 *   <li><b>元数据记录</b> - 使用 {@link ReplicaMetadataStore} 记录副本信息</li>
 * </ul>
 * 
 * <h3>一致性级别：</h3>
 * <table border="1">
 *   <tr><th>级别</th><th>说明</th><th>适用场景</th></tr>
 *   <tr>
 *     <td>STRONG（强一致性）</td>
 *     <td>同步复制，写入完成后才返回</td>
 *     <td>金融、订单等关键数据</td>
 *   </tr>
 *   <tr>
 *     <td>EVENTUAL（最终一致性）</td>
 *     <td>异步复制，通过消息队列延迟执行</td>
 *     <td>图片、视频等非关键数据</td>
 *   </tr>
 * </table>
 * 
 * <h3>复制流程：</h3>
 * <pre>
 * 文件上传 → 触发复制 → 解析策略 → 选择节点 → 执行复制 → 记录元数据
 *     ↓
 * [EVENTUAL] → 发送到消息队列 → 异步执行复制
 * [STRONG]   → 同步执行复制 → 返回结果
 * </pre>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建复制服务
 * ReplicationService replicationService = new ReplicationService(
 *     serviceRegistry,        // 服务注册中心
 *     replicaPlacer,          // 副本放置器
 *     replicaMetadataStore,   // 副本元数据存储
 *     storageEngineRouter,    // 存储引擎路由器
 *     ReplicationStrategy.standard(),  // 默认策略
 *     messageQueue,           // 消息队列（可选）
 *     ConsistencyLevel.EVENTUAL,       // 一致性级别
 *     "litefs.replication"    // 消息主题
 * );
 * 
 * // 触发复制（通常由 FileClient 内部调用）
 * FileMetadata metadata = ...; // 文件元数据
 * List<ReplicaInfo> replicas = replicationService.replicate(metadata, null, request);
 * 
 * // 查询文件的副本列表
 * List<ReplicaInfo> replicas = replicationService.listReplicas(fileId);
 * }</pre>
 * 
 * <h3>策略解析优先级：</h3>
 * <ol>
 *   <li>方法参数传入的策略</li>
 *   <li>文件元数据中的 "replication" 或 "replication.strategy" 字段</li>
 *   <li>服务默认策略</li>
 * </ol>
 * 
 * @see ReplicationStrategy
 * @see ConsistencyLevel
 * @see ReplicaPlacer
 * @see ReplicaMetadataStore
 * @see MessageQueue
 */
public class ReplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationService.class);
    
    /** 默认消息队列主题 */
    private static final String DEFAULT_TOPIC = "litefs.replication";

    /** 服务注册中心，用于发现可用节点 */
    private final ServiceRegistry serviceRegistry;
    
    /** 副本放置器，用于选择目标节点 */
    private final ReplicaPlacer replicaPlacer;
    
    /** 副本元数据存储，用于记录副本信息 */
    private final ReplicaMetadataStore replicaStore;
    
    /** 存储引擎路由器，用于获取各节点的存储引擎 */
    private final StorageEngineRouter storageRouter;
    
    /** 默认复制策略 */
    private final ReplicationStrategy defaultStrategy;
    
    /** 消息队列，用于异步复制（可为null） */
    private final MessageQueue messageQueue;
    
    /** 一致性级别 */
    private final ConsistencyLevel consistencyLevel;
    
    /** 消息队列主题 */
    private final String topic;

    /**
     * 简化构造函数：使用默认配置
     * 
     * <p>一致性级别默认为 EVENTUAL，但如果 messageQueue 为 null，
     * 会自动降级为 STRONG（同步复制）。</p>
     * 
     * @param serviceRegistry 服务注册中心
     * @param replicaPlacer 副本放置器
     * @param replicaStore 副本元数据存储
     * @param storageRouter 存储引擎路由器
     * @param defaultStrategy 默认复制策略
     */
    public ReplicationService(ServiceRegistry serviceRegistry,
                              ReplicaPlacer replicaPlacer,
                              ReplicaMetadataStore replicaStore,
                              StorageEngineRouter storageRouter,
                              ReplicationStrategy defaultStrategy) {
        this(serviceRegistry, replicaPlacer, replicaStore, storageRouter, defaultStrategy,
            null, ConsistencyLevel.EVENTUAL, DEFAULT_TOPIC);
    }

    /**
     * 完整构造函数
     * 
     * <p>如果 messageQueue 不为 null，会自动订阅消息主题，
     * 接收并处理异步复制任务。</p>
     * 
     * @param serviceRegistry 服务注册中心
     * @param replicaPlacer 副本放置器
     * @param replicaStore 副本元数据存储
     * @param storageRouter 存储引擎路由器
     * @param defaultStrategy 默认复制策略
     * @param messageQueue 消息队列，为 null 时使用同步复制
     * @param consistencyLevel 一致性级别
     * @param topic 消息队列主题
     */
    public ReplicationService(ServiceRegistry serviceRegistry,
                              ReplicaPlacer replicaPlacer,
                              ReplicaMetadataStore replicaStore,
                              StorageEngineRouter storageRouter,
                              ReplicationStrategy defaultStrategy,
                              MessageQueue messageQueue,
                              ConsistencyLevel consistencyLevel,
                              String topic) {
        this.serviceRegistry = serviceRegistry;
        this.replicaPlacer = replicaPlacer;
        this.replicaStore = replicaStore;
        this.storageRouter = storageRouter;
        this.defaultStrategy = defaultStrategy != null ? defaultStrategy : ReplicationStrategy.standard();
        this.messageQueue = messageQueue;
        ConsistencyLevel resolved = consistencyLevel != null ? consistencyLevel : ConsistencyLevel.EVENTUAL;
        this.consistencyLevel = (this.messageQueue == null) ? ConsistencyLevel.STRONG : resolved;
        this.topic = (topic == null || topic.isBlank()) ? DEFAULT_TOPIC : topic;
        if (this.messageQueue != null) {
            this.messageQueue.subscribe(this.topic, this::handleMessage);
        }
    }


    /**
     * 触发文件复制
     * 
     * <p>根据一致性级别选择同步或异步复制方式：</p>
     * <ul>
     *   <li><b>EVENTUAL + 消息队列可用</b> - 异步复制，投递任务到消息队列</li>
     *   <li><b>STRONG 或 无消息队列</b> - 同步复制，立即执行</li>
     * </ul>
     * 
     * <h3>策略解析优先级：</h3>
     * <ol>
     *   <li>方法参数 strategy</li>
     *   <li>文件元数据中的 "replication" 字段</li>
     *   <li>服务默认策略 defaultStrategy</li>
     * </ol>
     * 
     * @param metadata 文件元数据，包含文件ID、存储位置等信息
     * @param strategy 复制策略，为 null 时从文件元数据或默认策略解析
     * @param request 上传请求上下文，用于节点选择
     * @return 副本信息列表；异步复制时返回空列表
     */
    public List<ReplicaInfo> replicate(FileMetadata metadata, ReplicationStrategy strategy, FileUploadRequest request) {
        if (metadata == null) {
            log.warn("replicate: metadata is null, skipping replication");
            return Collections.emptyList();
        }
        
        ReplicationStrategy effectiveStrategy = strategy != null ? strategy : resolveStrategy(metadata.getMetadata());
        if (effectiveStrategy == null || effectiveStrategy.getReplicas() == 0) {
            log.info("replicate: strategy {} requires 0 replicas, skipping replication for file {}", 
                effectiveStrategy, metadata.getId());
            return Collections.emptyList();
        }

        log.info("replicate: starting replication for file {}, strategy={}, replicas={}, consistency={}, messageQueue={}", 
            metadata.getId(), effectiveStrategy.getType(), effectiveStrategy.getReplicas(), consistencyLevel, 
            messageQueue != null ? "available" : "null");

        if (consistencyLevel == ConsistencyLevel.EVENTUAL && messageQueue != null) {
            ReplicationTask task = ReplicationTask.of(metadata, effectiveStrategy, request);
            messageQueue.send(topic, task);
            log.info("replicate: sent replication task to message queue for file {}", metadata.getId());
            return Collections.emptyList();
        }

        log.info("replicate: executing synchronous replication for file {}", metadata.getId());
        return replicateSync(metadata, effectiveStrategy, request);
    }

    /**
     * 同步执行复制
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>从服务注册中心获取所有可用节点</li>
     *   <li>使用副本放置器选择目标节点</li>
     *   <li>排除主节点（已有文件）</li>
     *   <li>逐个执行复制：读取源文件 → 写入目标节点</li>
     *   <li>记录副本元数据</li>
     * </ol>
     * 
     * <p>注意：复制失败不会抛出异常，而是记录 FAILED 状态，
     * 继续处理其他节点。这样可以保证部分节点失败不影响整体流程。</p>
     * 
     * @param metadata 文件元数据
     * @param effectiveStrategy 生效的复制策略
     * @param request 上传请求上下文
     * @return 副本信息列表，包含每个副本的状态
     */
    public List<ReplicaInfo> replicateSync(FileMetadata metadata, ReplicationStrategy effectiveStrategy, FileUploadRequest request) {
        if (metadata == null || effectiveStrategy == null || effectiveStrategy.getReplicas() == 0) {
            log.warn("replicateSync: invalid parameters, metadata={}, strategy={}, replicas={}", 
                metadata != null, effectiveStrategy != null, 
                effectiveStrategy != null ? effectiveStrategy.getReplicas() : -1);
            return Collections.emptyList();
        }

        String primaryNodeId = metadata.getStorageNodeId();
        log.info("[REPLICATE] Starting replication - fileId={}, primaryNode={}, strategy={}, replicas={}", 
            metadata.getId(), primaryNodeId, effectiveStrategy.getType(), effectiveStrategy.getReplicas());
        
        List<StorageNode> allNodes = serviceRegistry != null ? serviceRegistry.discover() : Collections.emptyList();
        log.info("[REPLICATE] Discovered {} nodes from ServiceRegistry", allNodes.size());
        
        // 详细打印每个节点的信息
        for (int i = 0; i < allNodes.size(); i++) {
            StorageNode node = allNodes.get(i);
            log.info("[REPLICATE]   Node #{}: id={}, host={}, port={}, status={}", 
                i + 1, node.getId(), node.getHost(), node.getPort(), node.getStatus());
        }
        
        // 排除主节点，只考虑其他节点作为副本候选
        List<StorageNode> candidateNodes = new ArrayList<>();
        for (StorageNode node : allNodes) {
            if (node != null && node.getId() != null && 
                (primaryNodeId == null || !primaryNodeId.equals(node.getId()))) {
                candidateNodes.add(node);
            }
        }
        log.info("[REPLICATE] After excluding primary node: {} candidate nodes", candidateNodes.size());
        
        // 从候选节点中选择目标节点
        List<StorageNode> selected;
        if (replicaPlacer != null) {
            selected = replicaPlacer.selectNodes(candidateNodes, effectiveStrategy, request);
        } else {
            selected = new ArrayList<>(candidateNodes);
        }
        
        if (selected == null || selected.isEmpty()) {
            log.warn("[REPLICATE] ✗ No nodes selected for replication after excluding primary node");
            return Collections.emptyList();
        }
        
        log.info("[REPLICATE] ReplicaPlacer selected {} nodes", selected.size());
        
        // 选中的节点已经是排除主节点后的候选节点，直接作为目标节点
        List<StorageNode> targets = new ArrayList<>(selected);

        if (effectiveStrategy.getReplicas() != -1) {
            int desired = effectiveStrategy.getReplicas();
            if (targets.size() > desired) {
                log.debug("[REPLICATE] Limiting targets from {} to {} (strategy requirement)", 
                    targets.size(), desired);
                targets = targets.subList(0, desired);
            }
        }

        log.info("[REPLICATE] Final target nodes for replication: {}", 
            targets.stream().map(n -> n.getId() + "(" + n.getHost() + ":" + n.getPort() + ")").toList());

        List<ReplicaInfo> replicas = new ArrayList<>();
        for (StorageNode node : targets) {
            ReplicaInfo info = ReplicaInfo.builder()
                .fileId(metadata.getId())
                .nodeId(node.getId())
                .status(ReplicaInfo.ReplicaStatus.SYNCING)
                .syncTime(System.currentTimeMillis())
                .build();

            try {
                StorageEngine source = storageRouter.getEngine(primaryNodeId);
                StorageEngine target = storageRouter.getEngine(node.getId());
                
                log.info("Replicating file {} from node {} to node {}", metadata.getId(), primaryNodeId, node.getId());
                
                try (InputStream input = source.read(metadata.getId())) {
                    if (input == null) {
                        log.error("Source input stream is null for file: {}", metadata.getId());
                        throw new RuntimeException("Source input stream is null");
                    }
                    
                    String storagePath = target.write(metadata.getId(), input);
                    info.setStoragePath(storagePath);
                    info.setChecksum(metadata.getChecksum());
                    info.setStatus(ReplicaInfo.ReplicaStatus.SYNCED);
                    info.setSyncTime(System.currentTimeMillis());
                    
                    log.info("Replication successful: {} -> {} on node {}", metadata.getId(), storagePath, node.getId());
                }
            } catch (Exception e) {
                log.error("Replication failed for file {} to node {}: {}", metadata.getId(), node.getId(), e.getMessage(), e);
                info.setStatus(ReplicaInfo.ReplicaStatus.FAILED);
            }
            replicas.add(info);
        }

        if (replicaStore != null) {
            replicaStore.saveAll(replicas);
            log.info("replicateSync: saved {} replica metadata records", replicas.size());
        } else {
            log.warn("replicateSync: replicaStore is null, replica metadata not saved");
        }
        return replicas;
    }


    /**
     * 处理消息队列中的复制任务
     * 
     * <p>当使用异步复制时，复制任务会被投递到消息队列，
     * 此方法作为消息消费者处理这些任务。</p>
     * 
     * @param topic 消息主题
     * @param message 消息内容，期望是 ReplicationTask 类型
     */
    private void handleMessage(String topic, Object message) {
        if (message == null) {
            return;
        }
        if (message instanceof ReplicationTask task) {
            replicateSync(task.getMetadata(), task.getStrategy(), task.getRequest());
            return;
        }
        log.warn("Unsupported replication message type: {}", message.getClass().getName());
    }

    /**
     * 查询文件的所有副本信息
     * 
     * <p>从副本元数据存储中查询指定文件的所有副本记录，
     * 包括副本所在节点、同步状态、同步时间等信息。</p>
     * 
     * @param fileId 文件ID
     * @return 副本信息列表，不存在则返回空列表
     */
    public List<ReplicaInfo> listReplicas(String fileId) {
        return replicaStore != null ? replicaStore.listByFileId(fileId) : Collections.emptyList();
    }

    /**
     * 删除文件的所有副本元数据记录
     * 
     * <p>当文件被删除时，调用此方法清理该文件关联的所有副本元数据记录。
     * 副本元数据是附属数据，随主文件删除而删除，采用物理删除策略。</p>
     * 
     * @param fileId 文件ID
     */
    public void deleteReplicas(String fileId) {
        if (replicaStore != null && fileId != null) {
            replicaStore.deleteByFileId(fileId);
            log.info("deleteReplicas: deleted replica metadata for file {}", fileId);
        }
    }


    /**
     * 从文件元数据解析复制策略
     * 
     * <p>支持以下格式：</p>
     * <ul>
     *   <li><b>枚举字符串</b> - NONE, MINIMAL, STANDARD, HIGH, ALL_NODES, ALL</li>
     *   <li><b>数字</b> - 直接指定副本总数，如 "3" 表示3副本</li>
     * </ul>
     * 
     * <p>元数据字段名支持 "replication" 或 "replication.strategy"。</p>
     * 
     * @param metadata 文件元数据Map
     * @return 解析出的复制策略，解析失败返回默认策略
     */
    public ReplicationStrategy resolveStrategy(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return defaultStrategy;
        }
        String raw = metadata.getOrDefault("replication", metadata.get("replication.strategy"));
        if (raw == null || raw.isBlank()) {
            return defaultStrategy;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "NONE":
                return ReplicationStrategy.none();
            case "MINIMAL":
                return ReplicationStrategy.minimal();
            case "STANDARD":
                return ReplicationStrategy.standard();
            case "HIGH":
                return ReplicationStrategy.high();
            case "ALL_NODES":
            case "ALL":
                return ReplicationStrategy.allNodes();
            default:
                try {
                    int replicas = Integer.parseInt(raw.trim());
                    return ReplicationStrategy.custom(replicas);
                } catch (NumberFormatException ignored) {
                    return defaultStrategy;
                }
        }
    }
}
