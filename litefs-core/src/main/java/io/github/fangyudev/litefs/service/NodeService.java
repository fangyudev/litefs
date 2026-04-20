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

import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.spi.ServiceRegistry;

import java.util.List;

/**
 * 节点管理服务
 * 
 * <p>对外暴露节点注册、下线、查询、心跳等便捷方法，是服务注册中心的门面（Facade）。
 * 封装了 {@link ServiceRegistry} 的底层操作，提供更友好的 API。</p>
 * 
 * <h3>核心职责：</h3>
 * <ul>
 *   <li><b>节点注册</b> - 将新节点加入集群</li>
 *   <li><b>节点下线</b> - 从集群中移除节点</li>
 *   <li><b>节点查询</b> - 获取在线节点列表或单个节点信息</li>
 *   <li><b>心跳维护</b> - 刷新节点心跳，保持节点在线状态</li>
 *   <li><b>容量更新</b> - 更新节点的存储容量和使用情况</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建节点管理器
 * NodeService nodeManager = new NodeService(serviceRegistry);
 * 
 * // 注册新节点
 * StorageNode node = StorageNode.builder()
 *     .id("node-1")
 *     .host("192.168.1.100")
 *     .port(8080)
 *     .zone("cn-east-1")
 *     .totalSpace(1024L * 1024 * 1024 * 500)  // 500GB
 *     .usedSpace(0)
 *     .build();
 * nodeManager.register(node);
 * 
 * // 定时发送心跳（建议每5-10秒）
 * scheduler.scheduleAtFixedRate(() -> {
 *     nodeManager.heartbeat("node-1");
 * }, 5, 5, TimeUnit.SECONDS);
 * 
 * // 更新节点容量（建议定时统计）
 * long totalSpace = fileStore.getTotalSpace();
 * long usedSpace = fileStore.getUsableSpace();
 * nodeManager.updateUsage("node-1", totalSpace, usedSpace);
 * 
 * // 查询在线节点
 * List<StorageNode> onlineNodes = nodeManager.listOnlineNodes();
 * 
 * // 节点下线
 * nodeManager.deregister("node-1");
 * }</pre>
 * 
 * <h3>与 ServiceRegistry 的关系：</h3>
 * <pre>
 * NodeService（门面层）
 *     ↓ 委托
 * ServiceRegistry（SPI层）
 *     ↓ 实现
 * StaticServiceRegistry / NacosServiceRegistry / ConsulServiceRegistry
 * </pre>
 * 
 * <h3>最佳实践：</h3>
 * <ul>
 *   <li><b>心跳频率</b> - 建议 5-10 秒发送一次心跳</li>
 *   <li><b>容量更新</b> - 建议 30-60 秒更新一次容量信息</li>
 *   <li><b>优雅下线</b> - 节点关闭前先调用 deregister，避免客户端访问失败</li>
 * </ul>
 * 
 * @see ServiceRegistry
 * @see StorageNode
 */
public class NodeService {

    /** 服务注册中心，用于存储和管理节点信息 */
    private final ServiceRegistry serviceRegistry;

    /**
     * 构造函数
     * 
     * @param serviceRegistry 服务注册中心实现
     */
    public NodeService(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * 注册节点到服务注册中心
     * 
     * <p>注册后，该节点将出现在 {@link #listOnlineNodes()} 的结果中，
     * 可以被文件客户端选择用于存储文件。</p>
     * 
     * <p>注册时会自动设置：</p>
     * <ul>
     *   <li>节点状态为 ONLINE</li>
     *   <li>注册时间为当前时间</li>
     *   <li>心跳时间为当前时间</li>
     * </ul>
     * 
     * @param node 要注册的节点信息
     */
    public void register(StorageNode node) {
        serviceRegistry.register(node);
    }

    /**
     * 将节点从注册中心移除
     * 
     * <p>移除后，该节点将不再出现在在线节点列表中。
     * 建议在节点关闭前调用此方法，实现优雅下线。</p>
     * 
     * @param nodeId 要移除的节点ID
     */
    public void deregister(String nodeId) {
        serviceRegistry.deregister(nodeId);
    }

    /**
     * 获取当前在线节点列表
     * 
     * <p>只返回状态为 ONLINE 的节点，不包含离线或异常节点。</p>
     * 
     * @return 在线节点列表
     */
    public List<StorageNode> listOnlineNodes() {
        return serviceRegistry.discover();
    }

    /**
     * 根据节点ID查询节点信息
     * 
     * @param nodeId 节点ID
     * @return 节点信息，不存在则返回 null
     */
    public StorageNode getNode(String nodeId) {
        return serviceRegistry.get(nodeId);
    }

    /**
     * 刷新节点心跳
     * 
     * <p>更新节点的最后心跳时间，表示节点仍然存活。
     * 建议定时调用（如每 5 秒），防止节点被判定为离线。</p>
     * 
     * <p>心跳的作用：</p>
     * <ul>
     *   <li>保持节点在线状态</li>
     *   <li>某些注册中心（如 Nacos）会根据心跳判断节点健康</li>
     *   <li>用于监控节点的活跃程度</li>
     * </ul>
     * 
     * @param nodeId 要刷新心跳的节点ID
     */
    public void heartbeat(String nodeId) {
        serviceRegistry.heartbeat(nodeId);
    }

    /**
     * 更新节点的存储容量和使用情况
     * 
     * <p>定期更新节点的容量信息，可以让负载均衡器做出更准确的决策。
     * 建议定时调用（如每 30-60 秒）。</p>
     * 
     * <h3>使用示例：</h3>
     * <pre>{@code
     * // 从文件系统获取容量信息
     * File dataDir = new File("/data/files");
     * long totalSpace = dataDir.getTotalSpace();
     * long freeSpace = dataDir.getFreeSpace();
     * long usedSpace = totalSpace - freeSpace;
     * 
     * // 更新到注册中心
     * nodeManager.updateUsage("node-1", totalSpace, usedSpace);
     * }</pre>
     * 
     * <h3>容量信息的作用：</h3>
     * <ul>
     *   <li>容量优先的节点选择器会优先选择空闲空间多的节点</li>
     *   <li>可以监控集群的存储容量使用情况</li>
     *   <li>用于容量预警和扩容决策</li>
     * </ul>
     * 
     * @param nodeId 节点ID
     * @param totalSpace 总存储空间（字节）
     * @param usedSpace 已使用空间（字节）
     */
    public void updateUsage(String nodeId, long totalSpace, long usedSpace) {
        StorageNode node = serviceRegistry.get(nodeId);
        if (node == null) {
            return;
        }
        node.setTotalSpace(totalSpace);
        node.setUsedSpace(usedSpace);
    }
}
