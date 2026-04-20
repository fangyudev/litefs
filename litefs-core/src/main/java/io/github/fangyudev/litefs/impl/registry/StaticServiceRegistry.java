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

package io.github.fangyudev.litefs.impl.registry;

import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.spi.ServiceRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态服务注册中心实现
 * 
 * <p>节点列表在启动时通过配置文件固定，运行期间可手动注册/反注册节点，
 * 但不提供自动服务发现功能。</p>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>小规模集群部署，节点数量固定且较少变化</li>
 *   <li>节点IP和端口在部署时已确定</li>
 *   <li>不需要动态扩缩容的场景</li>
 *   <li>开发测试环境</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 方式一：启动时指定初始节点列表
 * List<StorageNode> nodes = Arrays.asList(
 *     StorageNode.builder().id("node-1").host("192.168.1.1").port(8080).build(),
 *     StorageNode.builder().id("node-2").host("192.168.1.2").port(8080).build()
 * );
 * ServiceRegistry registry = new StaticServiceRegistry(nodes);
 * 
 * // 方式二：启动后手动注册
 * ServiceRegistry registry = new StaticServiceRegistry();
 * registry.register(StorageNode.builder()
 *     .id("node-1")
 *     .host("192.168.1.1")
 *     .port(8080)
 *     .totalSpace(1024L * 1024 * 1024 * 100)  // 100GB
 *     .build());
 * }</pre>
 * 
 * <h3>特点：</h3>
 * <ul>
 *   <li>简单易用，无需外部依赖（如Nacos、Consul）</li>
 *   <li>节点信息存储在内存中，重启后需要重新注册</li>
 *   <li>不支持自动健康检查，依赖手动心跳或外部监控</li>
 *   <li>线程安全，使用 ConcurrentHashMap 存储</li>
 * </ul>
 * 
 * <h3>与 Nacos/Consul 的对比：</h3>
 * <table border="1">
 *   <tr><th>特性</th><th>StaticServiceRegistry</th><th>Nacos/Consul</th></tr>
 *   <tr><td>自动发现</td><td>不支持</td><td>支持</td></tr>
 *   <tr><td>健康检查</td><td>不支持</td><td>支持</td></tr>
 *   <tr><td>持久化</td><td>不支持</td><td>支持</td></tr>
 *   <tr><td>外部依赖</td><td>无</td><td>需要部署</td></tr>
 * </table>
 * 
 * @see ServiceRegistry
 * @see StorageNode
 */
public class StaticServiceRegistry implements ServiceRegistry {

    /** 节点存储：key为节点ID，value为节点信息 */
    private final Map<String, StorageNode> nodes = new ConcurrentHashMap<>();

    /**
     * 构造函数：使用初始节点列表创建注册中心
     * 
     * @param initialNodes 初始节点列表，可以为null或空列表
     */
    public StaticServiceRegistry(List<StorageNode> initialNodes) {
        if (initialNodes != null) {
            for (StorageNode node : initialNodes) {
                if (node != null && node.getId() != null) {
                    nodes.put(node.getId(), node);
                }
            }
        }
    }

    /**
     * 默认构造函数：创建空的注册中心
     * 后续通过 {@link #register(StorageNode)} 方法添加节点
     */
    public StaticServiceRegistry() {
        this(Collections.emptyList());
    }

    /**
     * 注册节点到服务注册中心
     * 
     * <p>注册时会自动设置：</p>
     * <ul>
     *   <li>节点状态为 ONLINE</li>
     *   <li>注册时间为当前时间</li>
     *   <li>心跳时间为当前时间</li>
     * </ul>
     * 
     * <p>如果节点ID已存在，将覆盖原有节点信息。</p>
     * 
     * @param node 要注册的节点，不能为null且必须有有效的ID
     */
    @Override
    public void register(StorageNode node) {
        if (node == null || node.getId() == null) {
            return;
        }
        node.setStatus(NodeStatus.ONLINE);
        node.setRegisterTime(System.currentTimeMillis());
        node.setLastHeartbeat(node.getRegisterTime());
        nodes.put(node.getId(), node);
    }

    /**
     * 从注册中心移除节点
     * 
     * @param nodeId 要移除的节点ID，为null时不执行任何操作
     */
    @Override
    public void deregister(String nodeId) {
        if (nodeId == null) {
            return;
        }
        nodes.remove(nodeId);
    }

    /**
     * 发现当前可用的在线节点列表
     * 
     * <p>只返回状态为 ONLINE 的节点，不包含离线或异常节点。</p>
     * 
     * @return 在线节点列表，不会返回null
     */
    @Override
    public List<StorageNode> discover() {
        List<StorageNode> available = new ArrayList<>();
        for (StorageNode node : nodes.values()) {
            if (node != null && node.getStatus() == NodeStatus.ONLINE) {
                available.add(node);
            }
        }
        return available;
    }

    /**
     * 根据节点ID获取节点信息
     * 
     * @param nodeId 节点ID
     * @return 节点信息，不存在则返回null
     */
    @Override
    public StorageNode get(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        return nodes.get(nodeId);
    }

    /**
     * 刷新节点心跳
     * 
     * <p>更新节点的最后心跳时间，并将节点状态设为 ONLINE。
     * 在静态模式下，不做复杂的健康检查，心跳主要用于：</p>
     * <ul>
     *   <li>表示节点仍然存活</li>
     *   <li>更新节点的活跃时间</li>
     * </ul>
     * 
     * @param nodeId 要刷新心跳的节点ID
     */
    @Override
    public void heartbeat(String nodeId) {
        StorageNode node = nodes.get(nodeId);
        if (node != null) {
            node.setLastHeartbeat(System.currentTimeMillis());
            node.setStatus(NodeStatus.ONLINE);
        }
    }

    /**
     * 初始化注册中心
     * 
     * <p>静态注册中心无需初始化，此方法为空实现。</p>
     */
    @Override
    public void init() {
    }

    /**
     * 关闭注册中心，释放资源
     * 
     * <p>清空所有节点信息。</p>
     */
    @Override
    public void shutdown() {
        nodes.clear();
    }
}
