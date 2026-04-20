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
 * 存储节点
 * 表示一个文件存储节点，包含节点的连接信息和存储容量信息
 */
public class StorageNode {

    /** 节点唯一标识符 */
    private String id;
    
    /** 节点主机地址 */
    private String host;
    
    /** 节点端口 */
    private int port;
    
    /** 节点所在区域/机房 */
    private String zone;
    
    /** 总存储容量(字节) */
    private long totalSpace;
    
    /** 已用存储空间(字节) */
    private long usedSpace;
    
    /** 可用存储空间(字节) */
    private long availableSpace;
    
    /** 节点状态 */
    private NodeStatus status;
    
    /** 注册时间(时间戳) */
    private long registerTime;
    
    /** 最后心跳时间(时间戳) */
    private long lastHeartbeat;

    /**
     * 默认构造函数
     * 初始化状态为ONLINE，注册时间和心跳时间为当前时间
     */
    public StorageNode() {
        this.status = NodeStatus.ONLINE;
        this.registerTime = System.currentTimeMillis();
        this.lastHeartbeat = this.registerTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public long getTotalSpace() {
        return totalSpace;
    }

    public void setTotalSpace(long totalSpace) {
        this.totalSpace = totalSpace;
    }

    public long getUsedSpace() {
        return usedSpace;
    }

    /**
     * 设置已用空间，同时计算可用空间
     * @param usedSpace 已用空间(字节)
     */
    public void setUsedSpace(long usedSpace) {
        this.usedSpace = usedSpace;
        this.availableSpace = totalSpace - usedSpace;
    }

    public long getAvailableSpace() {
        return availableSpace;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public long getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(long registerTime) {
        this.registerTime = registerTime;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    /**
     * 计算存储使用率
     * @return 使用率(0-1之间的小数)
     */
    public double getUsageRatio() {
        if (totalSpace <= 0) {
            return 0;
        }
        return (double) usedSpace / totalSpace;
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
     * 用于便捷地创建StorageNode实例
     */
    public static class Builder {
        private final StorageNode node = new StorageNode();

        public Builder id(String id) {
            node.setId(id);
            return this;
        }

        public Builder host(String host) {
            node.setHost(host);
            return this;
        }

        public Builder port(int port) {
            node.setPort(port);
            return this;
        }

        public Builder zone(String zone) {
            node.setZone(zone);
            return this;
        }

        public Builder totalSpace(long totalSpace) {
            node.setTotalSpace(totalSpace);
            return this;
        }

        public Builder usedSpace(long usedSpace) {
            node.setUsedSpace(usedSpace);
            return this;
        }

        public Builder status(NodeStatus status) {
            node.setStatus(status);
            return this;
        }

        public StorageNode build() {
            return node;
        }
    }
}
