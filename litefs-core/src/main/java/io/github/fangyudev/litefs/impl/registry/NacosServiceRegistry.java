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

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos 服务注册中心实现
 * 
 * <p>基于阿里云 Nacos 实现服务注册与发现，支持动态节点管理、健康检查和自动发现。</p>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>大规模集群部署，节点数量动态变化</li>
 *   <li>需要自动服务发现和健康检查</li>
 *   <li>已有 Nacos 作为注册中心的基础设施</li>
 *   <li>生产环境，需要高可用服务治理</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建配置
 * NacosServiceRegistryConfig config = NacosServiceRegistryConfig.builder()
 *     .serverAddr("127.0.0.1:8848")
 *     .namespace("litefs-namespace")
 *     .group("LITEFS_GROUP")
 *     .serviceName("litefs-storage")
 *     .build();
 * 
 * // 创建注册中心
 * ServiceRegistry registry = new NacosServiceRegistry(config);
 * registry.init();
 * 
 * // 注册节点
 * StorageNode node = StorageNode.builder()
 *     .id("node-1")
 *     .host("192.168.1.1")
 *     .port(8080)
 *     .totalSpace(1024L * 1024 * 1024 * 100)
 *     .build();
 * registry.register(node);
 * 
 * // 发现服务
 * List<StorageNode> nodes = registry.discover();
 * }</pre>
 * 
 * <h3>配置说明：</h3>
 * <table border="1">
 *   <tr><th>配置项</th><th>说明</th><th>默认值</th></tr>
 *   <tr><td>serverAddr</td><td>Nacos 服务地址</td><td>必填</td></tr>
 *   <tr><td>namespace</td><td>命名空间ID</td><td>public</td></tr>
 *   <tr><td>group</td><td>分组名称</td><td>DEFAULT_GROUP</td></tr>
 *   <tr><td>serviceName</td><td>服务名称</td><td>litefs-storage</td></tr>
 *   <tr><td>clusterName</td><td>集群名称</td><td>DEFAULT</td></tr>
 *   <tr><td>weight</td><td>服务权重</td><td>1.0</td></tr>
 * </table>
 * 
 * <h3>特点：</h3>
 * <ul>
 *   <li>自动服务发现：节点自动注册和发现</li>
 *   <li>健康检查：Nacos 自动进行健康检查</li>
 *   <li>元数据存储：节点容量信息存储在元数据中</li>
 *   <li>高可用：支持 Nacos 集群部署</li>
 * </ul>
 * 
 * @see ServiceRegistry
 * @see StaticServiceRegistry
 * @see StorageNode
 */
public class NacosServiceRegistry implements ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(NacosServiceRegistry.class);

    private static final String DEFAULT_SERVICE_NAME = "litefs-storage";
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    private static final String DEFAULT_CLUSTER = "DEFAULT";
    private static final double DEFAULT_WEIGHT = 1.0;

    private final NacosServiceRegistryConfig config;
    private NamingService namingService;
    private volatile boolean initialized = false;

    private final Map<String, StorageNode> localCache = new HashMap<>();

    /**
     * 构造函数
     * 
     * @param config Nacos 配置
     */
    public NacosServiceRegistry(NacosServiceRegistryConfig config) {
        this.config = config;
    }

    /**
     * 构造函数（使用默认配置）
     * 
     * @param serverAddr Nacos 服务地址
     */
    public NacosServiceRegistry(String serverAddr) {
        this(NacosServiceRegistryConfig.builder().serverAddr(serverAddr).build());
    }

    /**
     * 初始化 Nacos 注册中心
     * 
     * <p>创建 NamingService 客户端连接。</p>
     */
    @Override
    public void init() {
        if (initialized) {
            return;
        }
        
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", config.getServerAddr());
            
            if (config.getNamespace() != null && !config.getNamespace().isEmpty()) {
                properties.setProperty("namespace", config.getNamespace());
            }
            
            if (config.getUsername() != null && !config.getUsername().isEmpty()) {
                properties.setProperty("username", config.getUsername());
            }
            
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                properties.setProperty("password", config.getPassword());
            }
            
            if (config.getClusterName() != null && !config.getClusterName().isEmpty()) {
                properties.setProperty("clusterName", config.getClusterName());
            }

            namingService = NacosFactory.createNamingService(properties);
            initialized = true;
            
            log.info("NacosServiceRegistry initialized successfully, serverAddr: {}", config.getServerAddr());
        } catch (NacosException e) {
            throw new RuntimeException("Failed to initialize NacosServiceRegistry", e);
        }
    }

    /**
     * 注册存储节点到 Nacos
     * 
     * <p>将节点信息注册为 Nacos 服务实例，容量信息存储在元数据中。</p>
     * 
     * @param node 存储节点信息
     */
    @Override
    public void register(StorageNode node) {
        ensureInitialized();
        
        if (node == null || node.getId() == null) {
            log.warn("[NACOS-REGISTER] Attempted to register null node or node with null ID");
            return;
        }

        log.info("[NACOS-REGISTER] Registering node to Nacos - nodeId={}, host={}, port={}", 
            node.getId(), node.getHost(), node.getPort());
        
        try {
            Instance instance = createInstance(node);
            
            log.info("[NACOS-REGISTER] Created Nacos Instance - instanceId={}, ip={}, port={}, serviceName={}, group={}, cluster={}", 
                instance.getInstanceId(), instance.getIp(), instance.getPort(), 
                getServiceName(), getGroup(), instance.getClusterName());
            
            namingService.registerInstance(
                getServiceName(),
                getGroup(),
                instance
            );
            
            localCache.put(node.getId(), node);
            node.setStatus(NodeStatus.ONLINE);
            node.setRegisterTime(System.currentTimeMillis());
            node.setLastHeartbeat(node.getRegisterTime());
            
            log.info("[NACOS-REGISTER] ✓ Successfully registered to Nacos: nodeId={}, host={}:{}, service={}/{}", 
                node.getId(), node.getHost(), node.getPort(), getGroup(), getServiceName());
        } catch (NacosException e) {
            log.error("[NACOS-REGISTER] ✗ Failed to register node to Nacos: nodeId={}, error={}", 
                node.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to register node to Nacos: " + node.getId(), e);
        }
    }

    /**
     * 从 Nacos 注销存储节点
     * 
     * @param nodeId 节点ID
     */
    @Override
    public void deregister(String nodeId) {
        ensureInitialized();
        
        if (nodeId == null) {
            return;
        }

        StorageNode node = localCache.get(nodeId);
        if (node == null) {
            return;
        }

        try {
            namingService.deregisterInstance(
                getServiceName(),
                getGroup(),
                node.getHost(),
                node.getPort(),
                getClusterName()
            );
            
            localCache.remove(nodeId);
            
            log.info("Deregistered storage node from Nacos: nodeId={}", nodeId);
        } catch (NacosException e) {
            throw new RuntimeException("Failed to deregister node from Nacos: " + nodeId, e);
        }
    }

    /**
     * 从 Nacos 发现所有可用的存储节点
     * 
     * <p>查询 Nacos 中注册的健康实例，并转换为 StorageNode 对象。</p>
     * 
     * @return 可用的存储节点列表
     */
    @Override
    public List<StorageNode> discover() {
        ensureInitialized();
        
        List<StorageNode> nodes = new ArrayList<>();
        
        try {
            List<Instance> instances = namingService.selectInstances(
                getServiceName(),
                getGroup(),
                true
            );
            
            for (Instance instance : instances) {
                StorageNode node = convertToStorageNode(instance);
                if (node != null) {
                    nodes.add(node);
                }
            }
            
        } catch (NacosException e) {
            log.error("Failed to discover nodes from Nacos", e);
        }
        
        return nodes;
    }

    /**
     * 根据 ID 获取存储节点信息
     * 
     * <p>优先从本地缓存获取，如果不存在则从 Nacos 查询。</p>
     * 
     * @param nodeId 节点ID
     * @return 节点信息，不存在则返回 null
     */
    @Override
    public StorageNode get(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        
        StorageNode cachedNode = localCache.get(nodeId);
        if (cachedNode != null) {
            return cachedNode;
        }
        
        List<StorageNode> nodes = discover();
        for (StorageNode node : nodes) {
            if (nodeId.equals(node.getId())) {
                return node;
            }
        }
        
        return null;
    }

    /**
     * 发送心跳
     * 
     * <p>Nacos 客户端会自动发送心跳，此方法主要用于更新本地缓存中的心跳时间。</p>
     * 
     * @param nodeId 节点ID
     */
    @Override
    public void heartbeat(String nodeId) {
        StorageNode node = localCache.get(nodeId);
        if (node != null) {
            node.setLastHeartbeat(System.currentTimeMillis());
            node.setStatus(NodeStatus.ONLINE);
        }
    }

    /**
     * 关闭注册中心
     * 
     * <p>注销所有本地缓存的节点，并关闭 NamingService。</p>
     */
    @Override
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        try {
            for (String nodeId : localCache.keySet()) {
                try {
                    deregister(nodeId);
                } catch (Exception e) {
                    log.warn("Failed to deregister node during shutdown: {}", nodeId, e);
                }
            }
            
            namingService.shutDown();
            initialized = false;
            
            log.info("NacosServiceRegistry shutdown successfully");
        } catch (NacosException e) {
            log.error("Failed to shutdown NacosServiceRegistry", e);
        }
    }

    /**
     * 创建 Nacos 实例
     */
    private Instance createInstance(StorageNode node) {
        Instance instance = new Instance();
        instance.setInstanceId(node.getId());
        instance.setIp(node.getHost());
        instance.setPort(node.getPort());
        instance.setServiceName(getServiceName());
        instance.setClusterName(getClusterName());
        instance.setWeight(config.getWeight() > 0 ? config.getWeight() : DEFAULT_WEIGHT);
        instance.setHealthy(true);
        instance.setEnabled(true);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("nodeId", node.getId());
        metadata.put("zone", node.getZone() != null ? node.getZone() : "");
        metadata.put("totalSpace", String.valueOf(node.getTotalSpace()));
        metadata.put("usedSpace", String.valueOf(node.getUsedSpace()));
        metadata.put("availableSpace", String.valueOf(node.getAvailableSpace()));
        instance.setMetadata(metadata);
        
        return instance;
    }

    /**
     * 将 Nacos 实例转换为 StorageNode
     */
    private StorageNode convertToStorageNode(Instance instance) {
        Map<String, String> metadata = instance.getMetadata();
        
        StorageNode node = new StorageNode();
        node.setId(metadata.getOrDefault("nodeId", instance.getInstanceId()));
        node.setHost(instance.getIp());
        node.setPort(instance.getPort());
        node.setStatus(instance.isHealthy() ? NodeStatus.ONLINE : NodeStatus.OFFLINE);
        
        String zone = metadata.get("zone");
        if (zone != null && !zone.isEmpty()) {
            node.setZone(zone);
        }
        
        try {
            String totalSpace = metadata.get("totalSpace");
            if (totalSpace != null && !totalSpace.isEmpty()) {
                node.setTotalSpace(Long.parseLong(totalSpace));
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse totalSpace for node: {}", node.getId());
        }
        
        try {
            String usedSpace = metadata.get("usedSpace");
            if (usedSpace != null && !usedSpace.isEmpty()) {
                node.setUsedSpace(Long.parseLong(usedSpace));
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse usedSpace for node: {}", node.getId());
        }
        
        return node;
    }

    private String getServiceName() {
        return config.getServiceName() != null ? config.getServiceName() : DEFAULT_SERVICE_NAME;
    }

    private String getGroup() {
        return config.getGroup() != null ? config.getGroup() : DEFAULT_GROUP;
    }

    private String getClusterName() {
        return config.getClusterName() != null ? config.getClusterName() : DEFAULT_CLUSTER;
    }

    private void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }

    /**
     * Nacos 服务注册中心配置
     */
    public static class NacosServiceRegistryConfig {
        private String serverAddr;
        private String namespace;
        private String group;
        private String serviceName;
        private String clusterName;
        private String username;
        private String password;
        private double weight;

        private NacosServiceRegistryConfig() {}

        public String getServerAddr() {
            return serverAddr;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getGroup() {
            return group;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getClusterName() {
            return clusterName;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public double getWeight() {
            return weight;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final NacosServiceRegistryConfig config = new NacosServiceRegistryConfig();

            public Builder serverAddr(String serverAddr) {
                config.serverAddr = serverAddr;
                return this;
            }

            public Builder namespace(String namespace) {
                config.namespace = namespace;
                return this;
            }

            public Builder group(String group) {
                config.group = group;
                return this;
            }

            public Builder serviceName(String serviceName) {
                config.serviceName = serviceName;
                return this;
            }

            public Builder clusterName(String clusterName) {
                config.clusterName = clusterName;
                return this;
            }

            public Builder username(String username) {
                config.username = username;
                return this;
            }

            public Builder password(String password) {
                config.password = password;
                return this;
            }

            public Builder weight(double weight) {
                config.weight = weight;
                return this;
            }

            public NacosServiceRegistryConfig build() {
                return config;
            }
        }
    }
}
