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

package io.github.fangyudev.litefs.impl.store.replica;

import io.github.fangyudev.litefs.model.ReplicaInfo;
import io.github.fangyudev.litefs.spi.ReplicaMetadataStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存副本元数据存储
 * 使用内存Map存储副本信息，适用于单机开发和测试场景
 * 
 * <p><b>警告：此类不适用于生产环境！</b></p>
 * 
 * <p>特点：</p>
 * <ul>
 *   <li>数据存储在JVM内存中，重启后数据丢失</li>
 *   <li>无法在多节点间共享副本信息</li>
 *   <li>适合单机部署或单元测试</li>
 *   <li>线程安全，使用ConcurrentHashMap实现</li>
 * </ul>
 * 
 * <p>数据结构：</p>
 * <pre>
 * replicas: Map&lt;fileId, Map&lt;nodeId, ReplicaInfo&gt;&gt;
 * 
 * 示例数据：
 * {
 *   "file-abc123": {
 *     "node-1": ReplicaInfo(status=SYNCED, syncTime=1234567890),
 *     "node-2": ReplicaInfo(status=SYNCED, syncTime=1234567890)
 *   },
 *   "file-def456": {
 *     "node-1": ReplicaInfo(status=SYNCING, syncTime=1234567890)
 *   }
 * }
 * </pre>
 * 
 * <p>生产环境替代方案：</p>
 * <ul>
 *   <li>使用数据库（MySQL、PostgreSQL）持久化存储</li>
 *   <li>使用分布式KV存储（Redis、Etcd）实现多节点共享</li>
 * </ul>
 * 
 * @see ReplicaMetadataStore
 */
public class InMemoryReplicaMetadataStore implements ReplicaMetadataStore {

    /**
     * 副本数据存储
     * 外层Map的key是文件ID，内层Map的key是节点ID
     */
    private final Map<String, Map<String, ReplicaInfo>> replicas = new ConcurrentHashMap<>();

    /**
     * 保存单个副本信息
     * 如果该文件在该节点上已有副本记录，则覆盖
     * 
     * @param info 副本信息，不能为null且必须包含fileId和nodeId
     */
    @Override
    public void save(ReplicaInfo info) {
        if (info == null || info.getFileId() == null || info.getNodeId() == null) {
            return;
        }
        replicas.computeIfAbsent(info.getFileId(), key -> new ConcurrentHashMap<>())
            .put(info.getNodeId(), info);
    }

    /**
     * 批量保存副本信息
     * 遍历列表逐个调用save方法
     * 
     * @param infos 副本信息列表
     */
    @Override
    public void saveAll(List<ReplicaInfo> infos) {
        if (infos == null) {
            return;
        }
        for (ReplicaInfo info : infos) {
            save(info);
        }
    }

    /**
     * 查询文件的所有副本
     * 
     * @param fileId 文件ID
     * @return 副本信息列表，不存在则返回空列表
     */
    @Override
    public List<ReplicaInfo> listByFileId(String fileId) {
        if (fileId == null) {
            return Collections.emptyList();
        }
        Map<String, ReplicaInfo> map = replicas.get(fileId);
        if (map == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(map.values());
    }

    /**
     * 查询节点上的所有副本
     * 需要遍历所有文件的副本记录
     * 
     * @param nodeId 节点ID
     * @return 该节点上的所有副本信息
     */
    @Override
    public List<ReplicaInfo> listByNodeId(String nodeId) {
        if (nodeId == null) {
            return Collections.emptyList();
        }
        List<ReplicaInfo> result = new ArrayList<>();
        for (Map<String, ReplicaInfo> map : replicas.values()) {
            ReplicaInfo info = map.get(nodeId);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 获取指定文件在指定节点上的副本信息
     * 
     * @param fileId 文件ID
     * @param nodeId 节点ID
     * @return 副本信息，不存在返回null
     */
    @Override
    public ReplicaInfo get(String fileId, String nodeId) {
        if (fileId == null || nodeId == null) {
            return null;
        }
        Map<String, ReplicaInfo> map = replicas.get(fileId);
        return map != null ? map.get(nodeId) : null;
    }

    /**
     * 更新副本状态
     * 直接修改内存中的ReplicaInfo对象
     * 
     * @param fileId 文件ID
     * @param nodeId 节点ID
     * @param status 新状态
     * @param syncTime 同步时间戳
     * @param checksum 校验和，null表示不更新
     */
    @Override
    public void updateStatus(String fileId, String nodeId, ReplicaInfo.ReplicaStatus status, long syncTime, String checksum) {
        ReplicaInfo info = get(fileId, nodeId);
        if (info == null) {
            return;
        }
        info.setStatus(status);
        info.setSyncTime(syncTime);
        if (checksum != null) {
            info.setChecksum(checksum);
        }
    }

    /**
     * 删除文件的所有副本记录
     * 
     * @param fileId 文件ID
     */
    @Override
    public void deleteByFileId(String fileId) {
        if (fileId == null) {
            return;
        }
        replicas.remove(fileId);
    }

    /**
     * 删除节点上的所有副本记录
     * 需要遍历所有文件，移除该节点对应的副本
     * 
     * @param nodeId 节点ID
     */
    @Override
    public void deleteByNodeId(String nodeId) {
        if (nodeId == null) {
            return;
        }
        for (Map<String, ReplicaInfo> map : replicas.values()) {
            map.remove(nodeId);
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
        replicas.clear();
    }
}
