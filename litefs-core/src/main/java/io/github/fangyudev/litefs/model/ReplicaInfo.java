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

package io.github.fangyudev.litefs.model;

/**
 * 副本信息
 * 记录文件副本在各个存储节点上的状态和元数据
 * 
 * <p>在分布式存储系统中，一个文件通常会在多个节点上保存副本以提高可靠性。
 * ReplicaInfo 用于跟踪每个副本的同步状态、校验信息等。</p>
 * 
 * <p>副本状态流转：</p>
 * <pre>
 * PENDING（等待同步） -> SYNCING（同步中） -> SYNCED（已同步）
 *                          |
 *                          v
 *                      FAILED（同步失败）
 * </pre>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>ReplicationService 创建副本时记录副本信息</li>
 *   <li>FailoverService 根据副本状态选择可用副本</li>
 *   <li>监控告警系统检测副本健康状态</li>
 * </ul>
 */
public class ReplicaInfo {

    /** 文件ID，关联到 FileMetadata.id */
    private String fileId;
    
    /** 存储节点ID，标识副本所在的节点 */
    private String nodeId;
    
    /** 副本同步状态 */
    private ReplicaStatus status;
    
    /** 最后同步时间（时间戳） */
    private long syncTime;
    
    /** 副本校验和，用于验证数据一致性 */
    private String checksum;
    
    /** 副本在存储引擎中的路径 */
    private String storagePath;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public ReplicaStatus getStatus() {
        return status;
    }

    public void setStatus(ReplicaStatus status) {
        this.status = status;
    }

    public long getSyncTime() {
        return syncTime;
    }

    public void setSyncTime(long syncTime) {
        this.syncTime = syncTime;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    /**
     * 副本状态枚举
     * 表示副本在同步过程中的各个阶段
     */
    public enum ReplicaStatus {
        /** 等待同步 - 副本已计划但尚未开始同步 */
        PENDING,
        
        /** 同步中 - 正在从主节点复制数据 */
        SYNCING,
        
        /** 已同步 - 副本数据同步完成，可用于读取 */
        SYNCED,
        
        /** 同步失败 - 同步过程中发生错误 */
        FAILED
    }

    /**
     * 创建Builder实例
     * @return Builder对象
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器模式
     * 用于便捷地创建ReplicaInfo实例
     */
    public static class Builder {
        private final ReplicaInfo info = new ReplicaInfo();

        /**
         * 设置文件ID
         * @param fileId 文件ID
         * @return this
         */
        public Builder fileId(String fileId) {
            info.setFileId(fileId);
            return this;
        }

        /**
         * 设置节点ID
         * @param nodeId 节点ID
         * @return this
         */
        public Builder nodeId(String nodeId) {
            info.setNodeId(nodeId);
            return this;
        }

        /**
         * 设置副本状态
         * @param status 副本状态
         * @return this
         */
        public Builder status(ReplicaStatus status) {
            info.setStatus(status);
            return this;
        }

        /**
         * 设置同步时间
         * @param syncTime 同步时间戳
         * @return this
         */
        public Builder syncTime(long syncTime) {
            info.setSyncTime(syncTime);
            return this;
        }

        /**
         * 设置校验和
         * @param checksum 校验和（MD5）
         * @return this
         */
        public Builder checksum(String checksum) {
            info.setChecksum(checksum);
            return this;
        }

        /**
         * 设置存储路径
         * @param storagePath 存储路径
         * @return this
         */
        public Builder storagePath(String storagePath) {
            info.setStoragePath(storagePath);
            return this;
        }

        /**
         * 构建ReplicaInfo实例
         * @return ReplicaInfo实例
         */
        public ReplicaInfo build() {
            return info;
        }
    }
}
