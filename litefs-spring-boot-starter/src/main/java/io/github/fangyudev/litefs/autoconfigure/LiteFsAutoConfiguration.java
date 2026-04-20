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

package io.github.fangyudev.litefs.autoconfigure;

import io.github.fangyudev.litefs.api.FileClient;
import io.github.fangyudev.litefs.api.UrlGenerator;
import io.github.fangyudev.litefs.impl.client.FileClientImpl;
import io.github.fangyudev.litefs.service.FileOperationExecutor;
import io.github.fangyudev.litefs.controller.InternalStorageController;
import io.github.fangyudev.litefs.service.FailoverService;
import io.github.fangyudev.litefs.impl.store.metadata.H2MetadataStore;
import io.github.fangyudev.litefs.impl.store.metadata.MySqlMetadataStore;
import io.github.fangyudev.litefs.impl.store.cache.LocalMetadataCacheProvider;
import io.github.fangyudev.litefs.impl.store.cache.MetadataCache;
import io.github.fangyudev.litefs.impl.store.cache.RedisMetadataCacheProvider;
import io.github.fangyudev.litefs.impl.store.multipart.LocalMultipartUploadStore;
import io.github.fangyudev.litefs.impl.store.multipart.RedisMultipartUploadStore;
import io.github.fangyudev.litefs.model.ReplicationStrategy;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.impl.registry.NacosServiceRegistry;
import io.github.fangyudev.litefs.impl.registry.StaticServiceRegistry;
import io.github.fangyudev.litefs.impl.queue.InMemoryMessageQueue;
import io.github.fangyudev.litefs.impl.queue.RedisMessageQueue;
import io.github.fangyudev.litefs.impl.store.engine.remote.DistributedStorageEngineRouter;
import io.github.fangyudev.litefs.impl.store.replica.H2ReplicaMetadataStore;
import io.github.fangyudev.litefs.impl.store.replica.MySqlReplicaMetadataStore;
import io.github.fangyudev.litefs.service.ReplicationService;
import io.github.fangyudev.litefs.impl.store.engine.local.SingleNodeStorageEngineRouter;
import io.github.fangyudev.litefs.spi.MessageQueue;
import io.github.fangyudev.litefs.spi.MetadataCacheProvider;
import io.github.fangyudev.litefs.spi.MultipartUploadStore;
import io.github.fangyudev.litefs.spi.MetadataStore;
import io.github.fangyudev.litefs.spi.NodeSelector;
import io.github.fangyudev.litefs.spi.ReplicaMetadataStore;
import io.github.fangyudev.litefs.spi.ReplicaPlacer;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;
import io.github.fangyudev.litefs.impl.store.engine.local.LocalStorageEngine;
import io.github.fangyudev.litefs.impl.loadbalance.selector.CapacityNodeSelector;
import io.github.fangyudev.litefs.impl.loadbalance.selector.RoundRobinNodeSelector;
import io.github.fangyudev.litefs.impl.loadbalance.placement.BalancedReplicaPlacer;
import io.github.fangyudev.litefs.impl.loadbalance.placement.LocalityReplicaPlacer;
import io.github.fangyudev.litefs.service.ThumbnailService;
import io.github.fangyudev.litefs.impl.url.DirectUrlGenerator;
import io.github.fangyudev.litefs.impl.url.GatewayUrlGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@AutoConfiguration
@EnableConfigurationProperties(LiteFsProperties.class)
@ConditionalOnProperty(prefix = "litefs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiteFsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LiteFsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public StorageEngine storageEngine(LiteFsProperties properties) {
        String type = properties.getStorage().getType();
        String path = properties.getStorage().getPath();

        if ("local".equalsIgnoreCase(type)) {
            log.info("Initializing local storage engine at: {}", path);
            return new LocalStorageEngine(path);
        }

        throw new IllegalArgumentException("Unsupported storage type: " + type + ". Currently only 'local' is supported.");
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataStore metadataStore(LiteFsProperties properties,
                                        ObjectProvider<DataSource> springDataSourceProvider) {
        LiteFsProperties.Storage storage = properties.getStorage();
        String metadataType = storage.getMetadataType();
        String tablePrefix = storage.getTablePrefix();

        DataSource dataSource;
        if (storage.shouldUseSpringDatasource()) {
            DataSource springDataSource = springDataSourceProvider.getIfAvailable();
            if (springDataSource == null) {
                log.warn("use-spring-datasource is true but Spring DataSource not available, falling back to H2");
                H2MetadataStore store = new H2MetadataStore(
                    storage.getJdbcUrl(), storage.getJdbcUsername(), storage.getJdbcPassword(), tablePrefix);
                store.init();
                return wrapWithCache(store, properties);
            }
            dataSource = springDataSource;
            log.info("Using Spring DataSource for metadata store with table prefix: {}", tablePrefix);
        } else {
            dataSource = createLitefsDataSource(storage);
            log.info("Using independent DataSource for metadata store: {}", storage.getJdbcUrl());
        }

        MetadataStore store;
        if ("mysql".equalsIgnoreCase(metadataType)) {
            store = new MySqlMetadataStore(dataSource, tablePrefix);
        } else {
            store = new H2MetadataStore(dataSource, tablePrefix);
        }
        store.init();
        return wrapWithCache(store, properties);
    }

    private MetadataStore wrapWithCache(MetadataStore delegate, LiteFsProperties properties) {
        LiteFsProperties.Cache cacheConfig = properties.getCache();
        if (cacheConfig.isEnabled()) {
            MetadataCacheProvider provider = createCacheProvider(cacheConfig, properties);
            if (provider != null) {
                provider.init();
                return new MetadataCache(delegate, provider);
            }
        }
        return delegate;
    }

    private MetadataCacheProvider createCacheProvider(LiteFsProperties.Cache cacheConfig, LiteFsProperties properties) {
        String type = cacheConfig.getType();
        boolean distributed = properties.getRemote().isEnabled();

        if (distributed && "local".equalsIgnoreCase(type)) {
            log.warn("Local cache is not allowed in distributed mode (cache.type=local + remote.enabled=true). " +
                     "Metadata cache is DISABLED to prevent data inconsistency between nodes. " +
                     "Please use cache.type=redis or disable cache entirely.");
            return null;
        }

        if ("redis".equalsIgnoreCase(type)) {
            try {
                LiteFsProperties.CacheRedis redisConfig = cacheConfig.getRedis();
                LiteFsProperties.Replication.Queue.Redis queueRedis = properties.getReplication().getQueue().getRedis();
                String host = (redisConfig.getHost() != null && !redisConfig.getHost().isBlank()) ? redisConfig.getHost() : queueRedis.getHost();
                int port = redisConfig.getPort() > 0 ? redisConfig.getPort() : queueRedis.getPort();
                String password = (redisConfig.getPassword() != null && !redisConfig.getPassword().isBlank()) ? redisConfig.getPassword() : queueRedis.getPassword();
                int database = redisConfig.getDatabase() >= 0 ? redisConfig.getDatabase() : queueRedis.getDatabase();
                int timeoutMs = redisConfig.getTimeoutMs() > 0 ? redisConfig.getTimeoutMs() : queueRedis.getTimeoutMs();
                String keyPrefix = (redisConfig.getKeyPrefix() != null && !redisConfig.getKeyPrefix().isBlank()) ? redisConfig.getKeyPrefix() : "litefs:meta:";
                log.info("Using Redis metadata cache: {}:{}", host, port);
                return new RedisMetadataCacheProvider(host, port, password, database, timeoutMs, keyPrefix, cacheConfig.getTtl());
            } catch (NoClassDefFoundError e) {
                if (distributed) {
                    log.warn("Redis client (jedis) not found but distributed mode is enabled. " +
                             "Metadata cache is DISABLED to prevent data inconsistency. " +
                             "Please add jedis dependency to use cache.type=redis.");
                    return null;
                }
                log.warn("Redis client (jedis) not found, falling back to local cache in standalone mode. " +
                         "Please add jedis dependency if you want to use Redis cache.");
            }
        }

        log.info("Using local metadata cache: maxSize={}, ttl={}ms", cacheConfig.getMaxSize(), cacheConfig.getTtl());
        return new LocalMetadataCacheProvider(cacheConfig.getMaxSize(), cacheConfig.getTtl());
    }

    private DataSource createLitefsDataSource(LiteFsProperties.Storage storage) {
        return DataSourceBuilder.create()
            .url(storage.getJdbcUrl())
            .username(storage.getJdbcUsername())
            .password(storage.getJdbcPassword())
            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceRegistry serviceRegistry(LiteFsProperties properties, Environment env) {
        LiteFsProperties.Registry registry = properties.getRegistry();
        String type = registry.getType();

        ServiceRegistry serviceRegistry;

        if ("nacos".equalsIgnoreCase(type)) {
            try {
                serviceRegistry = createNacosRegistry(registry, env);
            } catch (NoClassDefFoundError e) {
                log.warn("Nacos client not found, falling back to static registry. " +
                         "Please add nacos-client dependency if you want to use Nacos.");
                serviceRegistry = createStaticRegistry(registry);
            }
        } else if ("consul".equalsIgnoreCase(type)) {
            try {
                serviceRegistry = createConsulRegistry(registry, env);
            } catch (NoClassDefFoundError e) {
                log.warn("Consul client not found, falling back to static registry. " +
                         "Please add consul-client dependency if you want to use Consul.");
                serviceRegistry = createStaticRegistry(registry);
            }
        } else {
            serviceRegistry = createStaticRegistry(registry);
        }

        // 自动注册当前节点到服务注册中心
        String nodeId = properties.getNodeId();
        String host = resolveNodeHost(properties, env);
        int port = env.getProperty("server.port", Integer.class, 8080);

        // 获取本地存储路径的磁盘空间信息
        java.io.File storePath = new java.io.File(properties.getStorage().getPath());
        long totalSpace = storePath.getTotalSpace();
        long freeSpace = storePath.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;

        StorageNode localNode = StorageNode.builder()
            .id(nodeId)
            .host(host)
            .port(port)
            .totalSpace(totalSpace)
            .usedSpace(usedSpace)
            .build();

        log.info("Node storage info - totalSpace={}GB, usedSpace={}GB, freeSpace={}GB",
            totalSpace / 1024 / 1024 / 1024, usedSpace / 1024 / 1024 / 1024, freeSpace / 1024 / 1024 / 1024);
        serviceRegistry.register(localNode);

        String modeDesc = properties.getRemote().isEnabled() ? "distributed" : "standalone";
        log.info("Auto-registered local node for {} mode: {} -> {}:{}", modeDesc, nodeId, host, port);

        return serviceRegistry;
    }

    /**
     * 智能解析节点 IP 地址
     * 
     * 优先级：
     * 1. litefs.access.direct.host 配置（手动指定）
     * 2. spring.cloud.client.ip-address（Spring Cloud 自动发现）
     * 3. spring.cloud.nacos.discovery.ip（Nacos 配置）
     * 4. 本机 IP 自动检测
     */
    private String resolveNodeHost(LiteFsProperties properties, Environment env) {
        log.info("[IP-RESOLVE] Starting node host resolution...");
        
        // 1. 优先使用手动配置的 direct.host
        String host = properties.getAccess().getDirect().getHost();
        log.info("[IP-RESOLVE] Step 1 - Checking litefs.access.direct.host: value='{}', isEmpty={}", 
            host, host == null || host.isEmpty());
        if (host != null && !host.isEmpty()) {
            log.info("[IP-RESOLVE] ✓ Using configured direct.host: {}", host);
            return host;
        }
        
        // 2. 使用 Spring Cloud 自动发现的 IP（需要 spring-cloud-commons 依赖）
        host = env.getProperty("spring.cloud.client.ip-address");
        log.info("[IP-RESOLVE] Step 2 - Checking spring.cloud.client.ip-address: value='{}', isNotLocal={}", 
            host, host != null && !"localhost".equals(host) && !"127.0.0.1".equals(host));
        if (host != null && !host.isEmpty() && !"localhost".equals(host) && !"127.0.0.1".equals(host)) {
            log.info("[IP-RESOLVE] ✓ Auto-detected host from spring.cloud.client.ip-address: {}", host);
            return host;
        }
        
        // 3. 使用 Nacos 配置的 IP
        host = env.getProperty("spring.cloud.nacos.discovery.ip");
        log.info("[IP-RESOLVE] Step 3 - Checking spring.cloud.nacos.discovery.ip: value='{}', isEmpty={}", 
            host, host == null || host.isEmpty());
        if (host != null && !host.isEmpty()) {
            log.info("[IP-RESOLVE] ✓ Using nacos.discovery.ip: {}", host);
            return host;
        }
        
        // 4. 从网卡自动获取本机 IP
        log.info("[IP-RESOLVE] Step 4 - Attempting to detect local IP from network interfaces...");
        host = getLocalIpAddress();
        log.info("[IP-RESOLVE] Step 4 result: detected IP='{}'", host);
        if (host != null && !host.isEmpty()) {
            log.info("[IP-RESOLVE] ✓ Auto-detected local IP from network interface: {}", host);
            return host;
        }
        
        // 5. 兜底返回 localhost
        log.warn("[IP-RESOLVE] ✗ Could not determine node host from any source, falling back to localhost. " +
                 "Please configure litefs.access.direct.host or ensure Spring Cloud is properly configured.");
        return "localhost";
    }

    /**
     * 获取本机 IP 地址（智能检测，支持虚拟机和容器环境）
     * 
     * 实现类似 Spring Cloud InetUtils 的机制：
     * 1. 通过网卡名称前缀过滤虚拟接口（docker、veth、virbr等）
     * 2. 优先选择标准物理网卡（eth、ens、enp、em、wlan等）
     * 3. 提供 fallback 机制确保总能在虚拟机/容器环境获取到真实 IP
     */
    /**
     * 通过 ip 命令获取 static IP（preferred_lft=forever）
     * 解决同一网卡多 IP 场景下，static IP 应优先于 DHCP IP 的问题
     */
    private String getStaticIpFromCommandLine() {
        try {
            java.util.List<String> preferredPrefixes = java.util.Arrays.asList("eth", "ens", "enp", "em", "wlan");
            java.lang.ProcessBuilder pb = new java.lang.ProcessBuilder("ip", "-4", "addr", "show");
            pb.redirectErrorStream(true);
            java.lang.Process process = pb.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            String line;
            String currentInterface = null;
            String staticIp = null;
            String dynamicIp = null;
            
            while ((line = reader.readLine()) != null) {
                // 解析网卡名称行，如 "2: ens160: <BROADCAST,MULTICAST,UP,LOWER_UP>..."
                if (line.contains(":")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        currentInterface = parts[1].trim().split("@")[0];
                    }
                }
                
                if (currentInterface == null) continue;
                
                // 创建 final 副本以便在 lambda 中使用
                final String interfaceName = currentInterface;
                boolean isPreferred = preferredPrefixes.stream()
                    .anyMatch(prefix -> interfaceName.startsWith(prefix));
                if (!isPreferred) continue;
                
                // 更新 currentInterface 用于日志输出
                currentInterface = interfaceName;
                
                // 解析 IP 地址行，检查 preferred_lft
                if (line.trim().startsWith("inet ")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        String ip = parts[1].split("/")[0];
                        boolean isStatic = line.contains("preferred_lft forever");
                        
                        log.info("[IP-DETECT-CMD] Interface '{}' - IP: {}, static: {}", currentInterface, ip, isStatic);
                        
                        if (isStatic) {
                            staticIp = ip;
                        } else if (dynamicIp == null) {
                            dynamicIp = ip;
                        }
                    }
                }
            }
            
            reader.close();
            process.waitFor();
            
            String result = staticIp != null ? staticIp : dynamicIp;
            if (result != null) {
                log.info("[IP-DETECT-CMD] Selected IP: {} (static={})", result, staticIp != null);
            }
            return result;
            
        } catch (Exception e) {
            log.warn("[IP-DETECT-CMD] Failed to get IP from command line: {}", e.getMessage());
            return null;
        }
    }

    private String getLocalIpAddress() {
        log.info("[IP-DETECT] Starting network interface IP detection...");
        
        // 第一步：优先通过 ip 命令获取 static IP（preferred_lft=forever）
        String staticIp = getStaticIpFromCommandLine();
        if (staticIp != null) {
            log.info("[IP-DETECT] ✓ Using static IP from command line: {}", staticIp);
            return staticIp;
        }
        
        // 第二步：使用 Java API 遍历网卡（原有逻辑）
        try {
            // 需要忽略的虚拟接口名称前缀
            java.util.List<String> ignoreInterfaces = java.util.Arrays.asList(
                "docker", "veth", "virbr", "br-", "lo", "tun", "utun", 
                "vnic", "vnet", "vmnet", "vEthernet", "Hyper-V"
            );
            log.info("[IP-DETECT] Ignore list: {}", ignoreInterfaces);
            
            // 优先选择的物理网卡名称前缀（按优先级排序）
            java.util.List<String> preferredInterfaces = java.util.Arrays.asList(
                "eth", "ens", "enp", "em", "wlan", "en0", "en1", "en2"
            );
            log.info("[IP-DETECT] Preferred list: {}", preferredInterfaces);
            
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                java.net.NetworkInterface.getNetworkInterfaces();
            
            if (interfaces == null) {
                log.error("[IP-DETECT] getNetworkInterfaces() returned null!");
                return null;
            }
            
            String fallbackIp = null;
            String lastResortIp = null;
            int interfaceCount = 0;
            int skippedLoopback = 0;
            int skippedDown = 0;
            int skippedVirtual = 0;
            int ipv4Count = 0;
            
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                interfaceCount++;
                String interfaceName = networkInterface.getName();
                
                // 跳过回环接口和未启用的接口
                if (networkInterface.isLoopback()) {
                    log.info("[IP-DETECT] Interface #{}: '{}' - SKIPPED (loopback)", interfaceCount, interfaceName);
                    skippedLoopback++;
                    continue;
                }
                if (!networkInterface.isUp()) {
                    log.info("[IP-DETECT] Interface #{}: '{}' - SKIPPED (not up)", interfaceCount, interfaceName);
                    skippedDown++;
                    continue;
                }
                
                // 检查是否在忽略列表中（docker、虚拟网桥等）
                boolean shouldIgnore = ignoreInterfaces.stream()
                    .anyMatch(ignore -> interfaceName.startsWith(ignore) || 
                                       interfaceName.toLowerCase().contains(ignore.toLowerCase()));
                if (shouldIgnore) {
                    log.info("[IP-DETECT] Interface #{}: '{}' - SKIPPED (virtual interface, matches ignore list)", 
                        interfaceCount, interfaceName);
                    skippedVirtual++;
                    continue;
                }
                
                // 遍历该网卡的 IP 地址
                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                int addrCount = 0;
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    addrCount++;
                    
                    // 只处理 IPv4 地址
                    if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()) {
                        ipv4Count++;
                        String ip = address.getHostAddress();
                        
                        // 检查是否是优选网卡
                        boolean isPreferred = preferredInterfaces.stream()
                            .anyMatch(prefix -> interfaceName.startsWith(prefix));
                        
                        log.info("[IP-DETECT] Interface #{}: '{}' - FOUND IPv4: {} (isPreferred={}, addrCount={})", 
                            interfaceCount, interfaceName, ip, isPreferred, addrCount);
                        
                        if (isPreferred) {
                            log.info("[IP-DETECT] ✓ Found preferred interface {} with IP: {}", interfaceName, ip);
                            return ip;
                        }
                        
                        // 记录第一个可用的非优选 IP 作为 fallback
                        if (fallbackIp == null) {
                            fallbackIp = ip;
                            log.info("[IP-DETECT] Recording as fallback: interface '{}' with IP: {}", interfaceName, ip);
                        }
                        
                        // 记录最后的备选 IP（即使看起来像虚拟接口）
                        lastResortIp = ip;
                    } else {
                        log.debug("[IP-DETECT] Interface #{}: '{}' - Address #{}: {} (not IPv4 or loopback)", 
                            interfaceCount, interfaceName, addrCount, address);
                    }
                }
                
                if (addrCount == 0) {
                    log.info("[IP-DETECT] Interface #{}: '{}' - NO ADDRESSES found", interfaceCount, interfaceName);
                }
            }
            
            log.info("[IP-DETECT] Scan complete. Total interfaces: {}, Skipped: loopback={}, down={}, virtual={}, Valid IPv4 found: {}", 
                interfaceCount, skippedLoopback, skippedDown, skippedVirtual, ipv4Count);
            
            // 返回策略：优先返回优选 IP，其次 fallback，最后 last resort
            if (fallbackIp != null) {
                log.info("[IP-DETECT] ✓ Using fallback IP: {}", fallbackIp);
                return fallbackIp;
            }
            
            if (lastResortIp != null) {
                log.warn("[IP-DETECT] ⚠ Using last resort IP (may be from virtual interface): {}", lastResortIp);
                return lastResortIp;
            }
            
            log.error("[IP-DETECT] ✗ No valid IPv4 address found from network interfaces");
        } catch (Exception e) {
            log.error("[IP-DETECT] ✗ Failed to get local IP address", e);
        }
        return null;
    }

    private ServiceRegistry createStaticRegistry(LiteFsProperties.Registry registry) {
        java.util.List<StorageNode> nodes = new java.util.ArrayList<>();
        for (LiteFsProperties.Registry.NodeConfig nodeConfig : registry.getStaticConfig().getNodes()) {
            long totalSpace = nodeConfig.getTotalSpace() > 0 ? nodeConfig.getTotalSpace() : 0;
            StorageNode node = StorageNode.builder()
                .id(nodeConfig.getId())
                .host(nodeConfig.getHost())
                .port(nodeConfig.getPort())
                .totalSpace(totalSpace)
                .usedSpace(0)  // 明确设置 usedSpace，这样 availableSpace 会被自动计算
                .build();
            nodes.add(node);
            log.debug("Created node: id={}, host={}:{}, totalSpace={}, availableSpace={}", 
                node.getId(), node.getHost(), node.getPort(), node.getTotalSpace(), node.getAvailableSpace());
        }
        log.info("Initializing static service registry with {} nodes", nodes.size());
        return new StaticServiceRegistry(nodes);
    }

    private ServiceRegistry createNacosRegistry(LiteFsProperties.Registry registry, Environment env) {
        LiteFsProperties.Registry.NacosRegistry nacos = registry.getNacos();
        
        String serverAddr = nacos.getServerAddr();
        String namespace = nacos.getNamespace();
        String group = nacos.getGroup();
        
        if ("127.0.0.1:8848".equals(serverAddr)) {
            serverAddr = env.getProperty("spring.cloud.nacos.discovery.server-addr", serverAddr);
        }
        if (namespace == null || namespace.isEmpty()) {
            namespace = env.getProperty("spring.cloud.nacos.discovery.namespace", "");
        }
        if ("DEFAULT_GROUP".equals(group)) {
            group = env.getProperty("spring.cloud.nacos.discovery.group", group);
        }
        
        log.info("Initializing Nacos service registry at: {}", serverAddr);
        NacosServiceRegistry.NacosServiceRegistryConfig config = NacosServiceRegistry.NacosServiceRegistryConfig.builder()
            .serverAddr(serverAddr)
            .namespace(namespace)
            .group(group)
            .build();
        return new NacosServiceRegistry(config);
    }

    private ServiceRegistry createConsulRegistry(LiteFsProperties.Registry registry, Environment env) {
        LiteFsProperties.Registry.ConsulRegistry consul = registry.getConsul();
        
        String host = consul.getHost();
        int port = consul.getPort();
        
        if ("127.0.0.1".equals(host)) {
            host = env.getProperty("spring.cloud.consul.host", host);
        }
        if (port == 8500) {
            port = env.getProperty("spring.cloud.consul.port", Integer.class, port);
        }
        
        log.info("Initializing Consul service registry at: {}:{}", host, port);
        log.warn("ConsulServiceRegistry is not yet implemented, falling back to static registry");
        return createStaticRegistry(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "litefs.remote", name = "enabled", havingValue = "true")
    public ReplicaMetadataStore replicaMetadataStore(LiteFsProperties properties,
                                                      ObjectProvider<DataSource> springDataSourceProvider) {
        LiteFsProperties.Storage storage = properties.getStorage();
        String metadataType = storage.getMetadataType();
        String tablePrefix = storage.getTablePrefix();

        DataSource dataSource;
        if (storage.shouldUseSpringDatasource()) {
            DataSource springDataSource = springDataSourceProvider.getIfAvailable();
            if (springDataSource == null) {
                log.warn("use-spring-datasource is true but Spring DataSource not available, falling back to H2");
                H2ReplicaMetadataStore store = new H2ReplicaMetadataStore(
                    storage.getJdbcUrl(), storage.getJdbcUsername(), storage.getJdbcPassword(), tablePrefix);
                store.init();
                return store;
            }
            dataSource = springDataSource;
            log.info("Using Spring DataSource for replica metadata store with table prefix: {}", tablePrefix);
        } else {
            dataSource = createLitefsDataSource(storage);
            log.info("Using independent DataSource for replica metadata store: {}", storage.getJdbcUrl());
        }

        if ("mysql".equalsIgnoreCase(metadataType)) {
            MySqlReplicaMetadataStore store = new MySqlReplicaMetadataStore(dataSource, tablePrefix);
            store.init();
            return store;
        }

        H2ReplicaMetadataStore store = new H2ReplicaMetadataStore(dataSource, tablePrefix);
        store.init();
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "litefs.remote", name = "enabled", havingValue = "true")
    public MessageQueue messageQueue(LiteFsProperties properties) {
        LiteFsProperties.Replication.Queue queue = properties.getReplication().getQueue();
        String type = queue.getType();
        if (type == null || type.isBlank()) {
            return null;
        }
        if ("redis".equalsIgnoreCase(type)) {
            LiteFsProperties.Replication.Queue.Redis redis = queue.getRedis();
            return new RedisMessageQueue(redis.getHost(), redis.getPort(), redis.getPassword(),
                redis.getDatabase(), redis.getTimeoutMs(), redis.getKeyPrefix());
        }
        if ("memory".equalsIgnoreCase(type)) {
            return new InMemoryMessageQueue();
        }
        throw new IllegalArgumentException("Unsupported message queue type: " + type + ". Supported types: redis, memory");
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageEngineRouter storageEngineRouter(StorageEngine storageEngine,
                                                   ObjectProvider<ServiceRegistry> serviceRegistryProvider,
                                                   LiteFsProperties properties) {
        String nodeId = properties.getNodeId();
        LiteFsProperties.Remote remote = properties.getRemote();

        if (remote.isEnabled()) {
            ServiceRegistry serviceRegistry = serviceRegistryProvider.getIfAvailable();
            if (serviceRegistry == null) {
                log.warn("Remote access enabled but ServiceRegistry not available, falling back to single node mode");
                return new SingleNodeStorageEngineRouter(nodeId, storageEngine);
            }

            log.info("Initializing distributed storage engine router (HTTP mode)");
            
            return new DistributedStorageEngineRouter(
                nodeId,
                storageEngine,
                serviceRegistry,
                remote.getConnectTimeout(),
                remote.getReadTimeout()
            );
        }

        log.info("Initializing single node storage engine router");
        return new SingleNodeStorageEngineRouter(nodeId, storageEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ServiceRegistry.class, StorageEngineRouter.class})
    @ConditionalOnProperty(prefix = "litefs.replication", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ReplicationService replicationService(ObjectProvider<ServiceRegistry> serviceRegistryProvider,
                                                 ObjectProvider<ReplicaPlacer> replicaPlacerProvider,
                                                 ObjectProvider<ReplicaMetadataStore> replicaMetadataStoreProvider,
                                                 ObjectProvider<StorageEngineRouter> storageRouterProvider,
                                                 ObjectProvider<MessageQueue> messageQueueProvider,
                                                 LiteFsProperties properties) {
        ServiceRegistry serviceRegistry = serviceRegistryProvider.getIfAvailable();
        StorageEngineRouter storageRouter = storageRouterProvider.getIfAvailable();
        ReplicaPlacer replicaPlacer = replicaPlacerProvider.getIfAvailable();
        ReplicaMetadataStore replicaMetadataStore = replicaMetadataStoreProvider.getIfAvailable();
        MessageQueue messageQueue = messageQueueProvider.getIfAvailable();

        ReplicationStrategy defaultStrategy = parseStrategy(properties.getReplication().getDefaultStrategy());
        return new ReplicationService(serviceRegistry, replicaPlacer, replicaMetadataStore, storageRouter,
            defaultStrategy, messageQueue, properties.getReplication().getConsistency(),
            properties.getReplication().getQueue().getTopic());
    }

    @Bean
    @ConditionalOnMissingBean
    public UrlGenerator urlGenerator(LiteFsProperties properties,
                                     MetadataStore metadataStore,
                                     ObjectProvider<ServiceRegistry> serviceRegistryProvider) {
        LiteFsProperties.Access access = properties.getAccess();
        String urlType = access.getUrlType();

        if ("gateway".equalsIgnoreCase(urlType)) {
            String baseUrl = access.getGateway().getBaseUrl();
            String pathPrefix = access.getGateway().getPathPrefix();
            String secretKey = access.getSigned().getSecretKey();

            log.info("Initializing gateway URL generator: {}{}", baseUrl, pathPrefix);
            return new GatewayUrlGenerator(baseUrl, pathPrefix, secretKey);
        }

        if ("direct".equalsIgnoreCase(urlType)) {
            ServiceRegistry serviceRegistry = serviceRegistryProvider.getIfAvailable();
            if (serviceRegistry == null) {
                log.warn("Direct URL type requires ServiceRegistry, falling back to gateway URL generator");
                return new GatewayUrlGenerator(
                    access.getGateway().getBaseUrl(),
                    access.getGateway().getPathPrefix(),
                    access.getSigned().getSecretKey()
                );
            }

            String pathPrefix = access.getDirect().getPathPrefix();
            String secretKey = access.getSigned().getSecretKey();

            log.info("Initializing direct URL generator with pathPrefix: {}", pathPrefix);
            return new DirectUrlGenerator(metadataStore, serviceRegistry, pathPrefix, secretKey);
        }

        throw new IllegalArgumentException("Unsupported URL type: " + urlType + ". Supported types: gateway, direct");
    }

    @Bean
    @ConditionalOnMissingBean
    public FileOperationExecutor fileOperationExecutor() {
        log.info("Initializing file operation executor");
        return new FileOperationExecutor(
            Runtime.getRuntime().availableProcessors(),
            100
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "litefs.thumbnail", name = "enabled", havingValue = "true")
    public ThumbnailService thumbnailService(StorageEngineRouter storageRouter,
                                              MetadataStore metadataStore,
                                              FileOperationExecutor fileOperationExecutor) {
        log.info("Initializing thumbnail service");
        return new ThumbnailService(storageRouter, metadataStore, fileOperationExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "litefs.remote", name = "enabled", havingValue = "true")
    public NodeSelector nodeSelector(LiteFsProperties properties) {
        String selectorType = properties.getLoadBalance().getNodeSelector();
        
        if ("capacity".equalsIgnoreCase(selectorType)) {
            log.info("Initializing capacity-based node selector");
            return new CapacityNodeSelector();
        }
        
        log.info("Initializing round-robin node selector");
        return new RoundRobinNodeSelector();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "litefs.remote", name = "enabled", havingValue = "true")
    public ReplicaPlacer replicaPlacer(LiteFsProperties properties) {
        String placerType = properties.getLoadBalance().getReplicaPlacer();
        
        if ("locality".equalsIgnoreCase(placerType)) {
            log.info("Initializing locality-based replica placer");
            return new LocalityReplicaPlacer();
        }
        
        log.info("Initializing balanced replica placer");
        return new BalancedReplicaPlacer();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ServiceRegistry.class, StorageEngineRouter.class, ReplicaMetadataStore.class})
    @ConditionalOnProperty(prefix = "litefs.remote", name = "enabled", havingValue = "true")
    public FailoverService failoverService(ServiceRegistry serviceRegistry,
                                           ReplicaMetadataStore replicaMetadataStore,
                                           StorageEngineRouter storageEngineRouter) {
        log.info("Initializing failover service");
        return new FailoverService(serviceRegistry, replicaMetadataStore, storageEngineRouter);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartUploadStore multipartUploadStore(LiteFsProperties properties) {
        LiteFsProperties.Multipart multipart = properties.getMultipart();
        String storeType = multipart.getStoreType();
        boolean isDistributed = properties.getRemote().isEnabled();

        // 分布式模式强制校验：必须使用 Redis 存储
        if (isDistributed && !"redis".equalsIgnoreCase(storeType)) {
            String errorMsg = String.format(
                "分布式模式（remote.enabled=true）下分片上传必须使用 Redis 存储，当前配置为 '%s'。\n" +
                "请在配置文件中添加：\n" +
                "  litefs:\n" +
                "    multipart:\n" +
                "      store-type: redis\n" +
                "Redis 连接配置将自动复用 replication.queue.redis 的设置。\n" +
                "如需单独配置 Redis，请设置 litefs.multipart.redis.*",
                storeType
            );
            log.error(errorMsg);
            throw new BeanCreationException("multipartUploadStore", errorMsg);
        }

        if ("redis".equalsIgnoreCase(storeType)) {
            try {
                LiteFsProperties.MultipartRedis multipartRedis = multipart.getRedis();
                LiteFsProperties.Replication.Queue.Redis queueRedis = properties.getReplication().getQueue().getRedis();

                // 复用 replication.queue.redis 的配置
                String host = (multipartRedis.getHost() != null && !multipartRedis.getHost().isBlank())
                    ? multipartRedis.getHost() : queueRedis.getHost();
                int port = multipartRedis.getPort() > 0 ? multipartRedis.getPort() : queueRedis.getPort();
                String password = (multipartRedis.getPassword() != null && !multipartRedis.getPassword().isBlank())
                    ? multipartRedis.getPassword() : queueRedis.getPassword();
                int database = multipartRedis.getDatabase() >= 0 ? multipartRedis.getDatabase() : queueRedis.getDatabase();
                int timeoutMs = multipartRedis.getTimeoutMs() > 0 ? multipartRedis.getTimeoutMs() : queueRedis.getTimeoutMs();
                String keyPrefix = (multipartRedis.getKeyPrefix() != null && !multipartRedis.getKeyPrefix().isBlank())
                    ? multipartRedis.getKeyPrefix() : "litefs:multipart:";

                log.info("Initializing Redis multipart upload store: {}:{}", host, port);
                RedisMultipartUploadStore store = new RedisMultipartUploadStore(
                    host, port, password, database, timeoutMs, keyPrefix);
                store.init();
                return store;
            } catch (NoClassDefFoundError e) {
                String errorMsg = "分布式模式下分片上传必须使用 Redis 存储，但未找到 jedis 依赖。" +
                    "请添加 jedis 依赖到项目中，或将 litefs.multipart.store-type 设置为 local（仅限单机模式）。";
                log.error(errorMsg);
                throw new BeanCreationException("multipartUploadStore", errorMsg, e);
            } catch (Exception e) {
                String errorMsg = "分布式模式下分片上传必须使用 Redis 存储，但 Redis 连接失败。" +
                    "请检查 Redis 配置是否正确。" +
                    "错误详情: " + e.getMessage();
                log.error(errorMsg);
                throw new BeanCreationException("multipartUploadStore", errorMsg, e);
            }
        }

        // 本地存储（仅单机模式）
        log.info("Initializing local multipart upload store (standalone mode only)");
        LocalMultipartUploadStore store = new LocalMultipartUploadStore();
        store.init();
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    public FileClient fileClient(StorageEngine storageEngine,
                                 MetadataStore metadataStore,
                                 UrlGenerator urlGenerator,
                                 LiteFsProperties properties,
                                 ObjectProvider<StorageEngineRouter> storageRouterProvider,
                                 ObjectProvider<ServiceRegistry> serviceRegistryProvider,
                                 ObjectProvider<NodeSelector> nodeSelectorProvider,
                                 ObjectProvider<ReplicaPlacer> replicaPlacerProvider,
                                 ObjectProvider<ReplicaMetadataStore> replicaMetadataStoreProvider,
                                 ObjectProvider<ReplicationService> replicationServiceProvider,
                                 ObjectProvider<FailoverService> failoverServiceProvider,
                                 ObjectProvider<ThumbnailService> thumbnailServiceProvider,
                                 MultipartUploadStore multipartUploadStore) {
        String nodeId = properties.getNodeId();

        StorageEngineRouter storageRouter = storageRouterProvider.getIfAvailable();
        ServiceRegistry serviceRegistry = serviceRegistryProvider.getIfAvailable();
        NodeSelector nodeSelector = nodeSelectorProvider.getIfAvailable();
        ReplicaPlacer replicaPlacer = replicaPlacerProvider.getIfAvailable();
        ReplicaMetadataStore replicaMetadataStore = replicaMetadataStoreProvider.getIfAvailable();
        ReplicationService replicationService = replicationServiceProvider.getIfAvailable();
        FailoverService failoverService = failoverServiceProvider.getIfAvailable();
        ThumbnailService thumbnailService = thumbnailServiceProvider.getIfAvailable();

        FileClientImpl.ThumbnailConfig thumbnailConfig = buildThumbnailConfig(properties);

        boolean multipartCleanupEnabled = properties.getMultipart().isCleanupEnabled();
        long multipartCleanupInterval = properties.getMultipart().getCleanupInterval();

        boolean distributedEnabled = storageRouter != null
            || serviceRegistry != null
            || nodeSelector != null
            || replicaPlacer != null
            || replicaMetadataStore != null
            || replicationService != null
            || failoverService != null;

        log.info("Initializing FileClient with nodeId: {}, thumbnailEnabled: {}, multipartCleanupEnabled: {}, multipartStoreType: {}", 
            nodeId, thumbnailConfig.isEnabled(), multipartCleanupEnabled, properties.getMultipart().getStoreType());
        if (distributedEnabled) {
            return new FileClientImpl(storageEngine, metadataStore, urlGenerator, nodeId,
                storageRouter, serviceRegistry, nodeSelector, replicationService, failoverService,
                thumbnailService, thumbnailConfig, multipartUploadStore,
                multipartCleanupEnabled, multipartCleanupInterval);
        }
        return new FileClientImpl(storageEngine, metadataStore, urlGenerator, nodeId,
            null, null, null, null, null, thumbnailService, thumbnailConfig, multipartUploadStore,
            multipartCleanupEnabled, multipartCleanupInterval);
    }

    private FileClientImpl.ThumbnailConfig buildThumbnailConfig(LiteFsProperties properties) {
        FileClientImpl.ThumbnailConfig config = new FileClientImpl.ThumbnailConfig();
        
        LiteFsProperties.Thumbnail thumbnailProps = properties.getThumbnail();
        if (thumbnailProps != null) {
            config.setEnabled(thumbnailProps.isEnabled());
            config.setDefaultSize(thumbnailProps.getDefaultSize());
            
            if (thumbnailProps.getSizes() != null) {
                config.setSizes(new java.util.HashMap<>(thumbnailProps.getSizes()));
            }
        }
        
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "litefs.remote", name = "enabled", havingValue = "true")
    public InternalStorageController internalStorageController(StorageEngine storageEngine,
                                                               MultipartUploadStore multipartUploadStore,
                                                               MetadataStore metadataStore,
                                                               LiteFsProperties properties) {
        log.info("Initializing internal storage API controller");
        return new InternalStorageController(storageEngine, multipartUploadStore, metadataStore, properties.getNodeId());
    }

    @Bean
    @ConditionalOnMissingBean
    public LiteFsSecurityChecker liteFsSecurityChecker(LiteFsProperties properties, Environment environment) {
        return new LiteFsSecurityChecker(properties, environment);
    }

    private ReplicationStrategy parseStrategy(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReplicationStrategy.standard();
        }
        String normalized = raw.trim().toUpperCase();
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
                    return ReplicationStrategy.standard();
                }
        }
    }

}
