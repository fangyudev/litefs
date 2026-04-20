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

import io.github.fangyudev.litefs.model.ReplicaInfo;

import java.util.List;

/**
 * 副本元数据存储接口（SPI扩展点）
 * 用于持久化存储副本的元数据信息
 * 
 * <p>在分布式存储系统中，需要记录每个文件副本的位置和状态。
 * ReplicaMetadataStore 提供了副本元数据的增删改查操作。</p>
 * 
 * <p>内置实现：</p>
 * <ul>
 *   <li>{@link io.github.fangyudev.litefs.replication.InMemoryReplicaMetadataStore} - 
 *       内存存储，适用于单机开发测试，重启后数据丢失</li>
 * </ul>
 * 
 * <p>可扩展实现：</p>
 * <ul>
 *   <li>MySQL副本元数据存储 - 使用MySQL数据库持久化</li>
 *   <li>Redis副本元数据存储 - 使用Redis缓存提高性能</li>
 *   <li>Etcd副本元数据存储 - 使用Etcd实现分布式一致性</li>
 * </ul>
 * 
 * <p>数据模型：</p>
 * <pre>
 * 文件ID (fileId) + 节点ID (nodeId) -> 副本信息 (ReplicaInfo)
 * 
 * 一个文件可以有多个副本，分布在不同节点上：
 * - fileId: "abc123"
 *   - nodeId: "node-1" -> ReplicaInfo(status=SYNCED, ...)
 *   - nodeId: "node-2" -> ReplicaInfo(status=SYNCED, ...)
 *   - nodeId: "node-3" -> ReplicaInfo(status=SYNCING, ...)
 * </pre>
 */
public interface ReplicaMetadataStore {

    /**
     * 保存单个副本信息
     * 如果已存在则覆盖
     * 
     * @param info 副本信息
     */
    void save(ReplicaInfo info);

    /**
     * 批量保存副本信息
     * 用于一次性创建多个副本记录
     * 
     * @param infos 副本信息列表
     */
    void saveAll(List<ReplicaInfo> infos);

    /**
     * 查询文件的所有副本
     * 用于故障转移时查找可用副本
     * 
     * @param fileId 文件ID
     * @return 副本信息列表
     */
    List<ReplicaInfo> listByFileId(String fileId);

    /**
     * 查询节点上的所有副本
     * 用于节点下线时处理副本迁移
     * 
     * @param nodeId 节点ID
     * @return 副本信息列表
     */
    List<ReplicaInfo> listByNodeId(String nodeId);

    /**
     * 获取指定文件在指定节点上的副本信息
     * 
     * @param fileId 文件ID
     * @param nodeId 节点ID
     * @return 副本信息，不存在返回null
     */
    ReplicaInfo get(String fileId, String nodeId);

    /**
     * 更新副本状态
     * 用于同步过程中更新副本状态
     * 
     * @param fileId 文件ID
     * @param nodeId 节点ID
     * @param status 新状态
     * @param syncTime 同步时间戳
     * @param checksum 校验和（可选，null表示不更新）
     */
    void updateStatus(String fileId, String nodeId, ReplicaInfo.ReplicaStatus status, long syncTime, String checksum);

    /**
     * 删除文件的所有副本记录
     * 用于文件删除时清理副本元数据
     * 
     * @param fileId 文件ID
     */
    void deleteByFileId(String fileId);

    /**
     * 删除节点上的所有副本记录
     * 用于节点下线时清理副本元数据
     * 
     * @param nodeId 节点ID
     */
    void deleteByNodeId(String nodeId);

    /**
     * 初始化存储
     * 创建必要的表结构或连接池
     */
    void init();

    /**
     * 关闭存储
     * 释放资源，关闭连接
     */
    void shutdown();
}
