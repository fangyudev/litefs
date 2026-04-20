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

import io.github.fangyudev.litefs.model.ConsistencyLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiteFS 配置属性类
 * 
 * <p>配置前缀：litefs</p>
 * 
 * <p>示例配置：</p>
 * <pre>
 * litefs:
 *   enabled: true
 *   node-id: node-1
 *   storage:
 *     type: local
 *     path: ./data/files
 *   access:
 *     url-type: gateway
 * </pre>
 */
@ConfigurationProperties(prefix = "litefs")
public class LiteFsProperties {

    /**
     * 是否启用 LiteFS
     * <p>默认值：true</p>
     * <p>设置为 false 时，LiteFS 自动配置将不会生效</p>
     */
    private boolean enabled = true;

    /**
     * 存储配置
     * <p>配置文件存储路径、元数据存储等</p>
     */
    private Storage storage = new Storage();

    /**
     * 访问配置
     * <p>配置 URL 生成方式、签名等</p>
     */
    private Access access = new Access();

    /**
     * 副本复制配置
     * <p>配置数据复制策略、一致性级别、消息队列等</p>
     */
    private Replication replication = new Replication();

    /**
     * 远程访问配置
     * <p>配置跨节点访问方式（HTTP/gRPC/Dubbo）</p>
     */
    private Remote remote = new Remote();

    /**
     * 缩略图配置
     * <p>配置图片缩略图生成功能</p>
     */
    private Thumbnail thumbnail = new Thumbnail();

    /**
     * 分片上传配置
     * <p>配置分片上传清理调度器等</p>
     */
    private Multipart multipart = new Multipart();

    /**
     * 服务注册中心配置
     * <p>配置服务注册中心类型和参数</p>
     */
    private Registry registry = new Registry();

    /**
     * 负载均衡配置
     * <p>配置节点选择策略等</p>
     */
    private LoadBalance loadBalance = new LoadBalance();

    /**
     * 元数据缓存配置
     * <p>配置元数据缓存功能，减少数据库访问次数</p>
     */
    private Cache cache = new Cache();

    /**
     * 当前节点 ID
     * <p>默认值：node-1</p>
     * <p>在分布式环境中，每个节点应该有唯一的 ID</p>
     */
    private String nodeId = "node-1";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    public Replication getReplication() {
        return replication;
    }

    public void setReplication(Replication replication) {
        this.replication = replication;
    }

    public Remote getRemote() {
        return remote;
    }

    public void setRemote(Remote remote) {
        this.remote = remote;
    }

    public Thumbnail getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Thumbnail thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Multipart getMultipart() {
        return multipart;
    }

    public void setMultipart(Multipart multipart) {
        this.multipart = multipart;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public LoadBalance getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(LoadBalance loadBalance) {
        this.loadBalance = loadBalance;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 判断复制功能是否应该生效
     * 
     * <p>智能默认值：当远程访问未启用时，复制功能不会生效</p>
     * 
     * @return true 表示复制功能应该生效
     */
    public boolean isReplicationEffectivelyEnabled() {
        return replication.isEnabled() && remote.isEnabled();
    }

    /**
     * 判断是否为单机模式
     * 
     * @return true 表示单机模式
     */
    public boolean isStandaloneMode() {
        return !remote.isEnabled();
    }

    /**
     * 存储配置类
     * 
     * <p>配置文件存储和元数据存储的相关参数</p>
     */
    public static class Storage {
        
        /**
         * 存储引擎类型
         * <p>默认值：local</p>
         * <p>当前仅支持 local（本地文件存储）</p>
         * <p>可通过实现 StorageEngine 接口扩展支持其他类型</p>
         */
        private String type = "local";
        
        /**
         * 文件存储路径
         * <p>默认值：./data/files</p>
         * <p>本地存储引擎使用的文件存储根目录</p>
         */
        private String path = "./data/files";

        /**
         * 元数据存储类型
         * <p>默认值：h2</p>
         * <p>可选值：h2（嵌入式数据库）、mysql</p>
         */
        private String metadataType = "h2";
        
        /**
         * 元数据数据库 JDBC 连接 URL
         * <p>默认值：jdbc:h2:./data/litefs;AUTO_SERVER=TRUE</p>
         * <p>H2 示例：jdbc:h2:./data/litefs;AUTO_SERVER=TRUE</p>
         * <p>MySQL 示例：jdbc:mysql://localhost:3306/litefs</p>
         */
        private String jdbcUrl = "jdbc:h2:./data/litefs;AUTO_SERVER=TRUE";

        /**
         * 元数据数据库用户名
         * <p>默认值：sa</p>
         */
        private String jdbcUsername = "sa";

        /**
         * 元数据数据库密码
         * <p>默认值：空字符串</p>
         */
        private String jdbcPassword = "";

        /**
         * 是否使用 Spring 数据源
         * <p>默认值：null（自动判断）</p>
         * <p>设为 true 时，复用 Spring 配置的主数据源</p>
         * <p>设为 false 时，使用独立的 JDBC 配置</p>
         * <p>为 null 时，自动判断：若未配置 jdbc-url 且 metadata-type 不是 h2，则使用 Spring 数据源</p>
         */
        private Boolean useSpringDatasource = null;

        /**
         * 表名前缀
         * <p>默认值：litefs_</p>
         * <p>当使用 Spring 数据源时，通过前缀区分 LiteFS 表和业务表</p>
         */
        private String tablePrefix = "litefs_";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getMetadataType() {
            return metadataType;
        }

        public void setMetadataType(String metadataType) {
            this.metadataType = metadataType;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getJdbcUsername() {
            return jdbcUsername;
        }

        public void setJdbcUsername(String jdbcUsername) {
            this.jdbcUsername = jdbcUsername;
        }

        public String getJdbcPassword() {
            return jdbcPassword;
        }

        public void setJdbcPassword(String jdbcPassword) {
            this.jdbcPassword = jdbcPassword;
        }

        public Boolean getUseSpringDatasource() {
            return useSpringDatasource;
        }

        public void setUseSpringDatasource(Boolean useSpringDatasource) {
            this.useSpringDatasource = useSpringDatasource;
        }

        public String getTablePrefix() {
            return tablePrefix;
        }

        public void setTablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
        }

        /**
         * 判断是否应该使用 Spring 数据源
         * 
         * @return true 表示使用 Spring 数据源
         */
        public boolean shouldUseSpringDatasource() {
            if (useSpringDatasource != null) {
                return useSpringDatasource;
            }
            return !isJdbcConfigured() && !"h2".equals(metadataType);
        }

        /**
         * 判断是否已配置独立的 JDBC 连接
         * 
         * @return true 表示已配置独立 JDBC
         */
        public boolean isJdbcConfigured() {
            return jdbcUrl != null && !jdbcUrl.isEmpty() 
                && !jdbcUrl.equals("jdbc:h2:./data/litefs;AUTO_SERVER=TRUE");
        }
    }

    /**
     * 副本复制配置类
     * 
     * <p>配置数据复制策略、一致性级别和消息队列</p>
     */
    public static class Replication {
        
        /**
         * 是否启用副本复制功能
         * <p>默认值：true</p>
         * <p>启用后，文件将根据策略复制到多个节点</p>
         * <p>注意：单节点模式下（remote.enabled=false），复制功能会自动跳过</p>
         */
        private boolean enabled = true;

        /**
         * 默认副本策略
         * <p>默认值：STANDARD</p>
         * <p>可选值：</p>
         * <ul>
         *   <li>NONE - 无副本</li>
         *   <li>MINIMAL - 最小副本（1个副本）</li>
         *   <li>STANDARD - 标准副本（2个副本）</li>
         *   <li>HIGH - 高副本（3个副本）</li>
         *   <li>ALL_NODES - 所有节点都保存副本</li>
         *   <li>数字 - 自定义副本数量</li>
         * </ul>
         */
        private String defaultStrategy = "STANDARD";

        /**
         * 一致性级别
         * <p>默认值：EVENTUAL（最终一致性）</p>
         * <p>可选值：</p>
         * <ul>
         *   <li>EVENTUAL - 最终一致性，性能更高</li>
         *   <li>STRONG - 强一致性，数据更可靠</li>
         * </ul>
         */
        private ConsistencyLevel consistency = ConsistencyLevel.EVENTUAL;

        /**
         * 消息队列配置
         * <p>用于异步复制任务的消息传递</p>
         */
        private Queue queue = new Queue();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultStrategy() {
            return defaultStrategy;
        }

        public void setDefaultStrategy(String defaultStrategy) {
            this.defaultStrategy = defaultStrategy;
        }

        public ConsistencyLevel getConsistency() {
            return consistency;
        }

        public void setConsistency(ConsistencyLevel consistency) {
            this.consistency = consistency;
        }

        public Queue getQueue() {
            return queue;
        }

        public void setQueue(Queue queue) {
            this.queue = queue;
        }

        /**
         * 消息队列配置类
         * 
         * <p>支持 Redis、内存队列等消息队列</p>
         */
        public static class Queue {
            
            /**
             * 消息队列类型
             * <p>默认值：空字符串（使用内存队列或不启用）</p>
             * <p>可选值：redis、memory</p>
             */
            private String type = "";

            /**
             * 消息队列主题
             * <p>默认值：litefs.replication</p>
             * <p>复制任务消息将发送到此主题</p>
             */
            private String topic = "litefs.replication";

            /**
             * Redis 消息队列配置
             */
            private Redis redis = new Redis();

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getTopic() {
                return topic;
            }

            public void setTopic(String topic) {
                this.topic = topic;
            }

            public Redis getRedis() {
                return redis;
            }

            public void setRedis(Redis redis) {
                this.redis = redis;
            }

            /**
             * Redis 消息队列配置类
             */
            public static class Redis {
                
                /**
                 * Redis 服务器地址
                 * <p>默认值：127.0.0.1</p>
                 */
                private String host = "127.0.0.1";
                
                /**
                 * Redis 服务器端口
                 * <p>默认值：6379</p>
                 */
                private int port = 6379;
                
                /**
                 * Redis 密码
                 * <p>默认值：空字符串（无密码）</p>
                 */
                private String password = "";
                
                /**
                 * Redis 数据库索引
                 * <p>默认值：0</p>
                 */
                private int database = 0;
                
                /**
                 * 连接超时时间（毫秒）
                 * <p>默认值：2000</p>
                 */
                private int timeoutMs = 2000;
                
                /**
                 * 消息队列 Key 前缀
                 * <p>默认值：litefs:queue:</p>
                 */
                private String keyPrefix = "litefs:queue:";

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

                public String getPassword() {
                    return password;
                }

                public void setPassword(String password) {
                    this.password = password;
                }

                public int getDatabase() {
                    return database;
                }

                public void setDatabase(int database) {
                    this.database = database;
                }

                public int getTimeoutMs() {
                    return timeoutMs;
                }

                public void setTimeoutMs(int timeoutMs) {
                    this.timeoutMs = timeoutMs;
                }

                public String getKeyPrefix() {
                    return keyPrefix;
                }

                public void setKeyPrefix(String keyPrefix) {
                    this.keyPrefix = keyPrefix;
                }
            }
        }
    }

    /**
     * 访问配置类
     * 
     * <p>配置文件访问 URL 的生成方式</p>
     * 
     * <h3>URL 类型：</h3>
     * <ul>
     *   <li><b>gateway</b> - 通过统一网关访问，适合有网关的部署环境</li>
     *   <li><b>direct</b> - 直接访问文件所在节点，适合无网关的部署环境</li>
     * </ul>
     */
    public static class Access {
        
        /**
         * URL 生成类型
         * <p>默认值：gateway</p>
         * <p>可选值：</p>
         * <ul>
         *   <li>gateway - 通过网关访问（需要配置 gateway.baseUrl）</li>
         *   <li>direct - 直接访问节点（根据文件所在节点动态生成URL）</li>
         * </ul>
         * <p>默认值：direct（开发测试友好）</p>
         */
        private String urlType = "direct";

        /**
         * 网关 URL 配置
         */
        private Gateway gateway = new Gateway();

        /**
         * 直接访问配置
         */
        private Direct direct = new Direct();

        /**
         * 签名 URL 配置
         */
        private Signed signed = new Signed();

        public String getUrlType() {
            return urlType;
        }

        public void setUrlType(String urlType) {
            this.urlType = urlType;
        }

        public Gateway getGateway() {
            return gateway;
        }

        public void setGateway(Gateway gateway) {
            this.gateway = gateway;
        }

        public Direct getDirect() {
            return direct;
        }

        public void setDirect(Direct direct) {
            this.direct = direct;
        }

        public Signed getSigned() {
            return signed;
        }

        public void setSigned(Signed signed) {
            this.signed = signed;
        }

        /**
         * 网关 URL 配置类
         * 
         * <p>配置通过网关访问文件的 URL</p>
         */
        public static class Gateway {
            
            /**
             * 网关基础 URL
             * <p>默认值：http://localhost:8080</p>
             * <p>示例：http://gateway.example.com</p>
             */
            private String baseUrl = "http://localhost:8080";
            
            /**
             * URL 路径前缀
             * <p>默认值：/api/files</p>
             * <p>完整的文件访问 URL 为：baseUrl + pathPrefix + /{fileId}</p>
             */
            private String pathPrefix = "/api/files";

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getPathPrefix() {
                return pathPrefix;
            }

            public void setPathPrefix(String pathPrefix) {
                this.pathPrefix = pathPrefix;
            }
        }

        /**
         * 直接访问配置类
         * 
         * <p>配置直接访问文件所在节点的 URL 生成方式。</p>
         * <p>适用于没有统一网关的部署环境。</p>
         */
        public static class Direct {
            
            /**
             * URL 路径前缀
             * <p>默认值：/api/files</p>
             * <p>完整的文件访问 URL 为：http://{nodeHost}:{nodePort} + pathPrefix + /{fileId}</p>
             */
            private String pathPrefix = "/api/files";
            
            /**
             * 单机模式下的节点主机地址
             * <p>默认值：null（未配置时自动解析本机 IP）</p>
             * <p>单机模式下自动注册当前节点时使用此地址</p>
             * <p>端口号从 server.port 自动获取</p>
             */
           private String host = null;

            public String getPathPrefix() {
                return pathPrefix;
            }

            public void setPathPrefix(String pathPrefix) {
                this.pathPrefix = pathPrefix;
            }

            public String getHost() {
                return host;
            }

            public void setHost(String host) {
                this.host = host;
            }
        }

        /**
         * 签名 URL 配置类
         * 
         * <p>配置临时签名 URL 的生成参数</p>
         */
        public static class Signed {
            
            /**
             * 是否启用签名 URL
             * <p>默认值：true</p>
             * <p>启用后，生成的临时 URL 将包含签名验证</p>
             */
            private boolean enabled = true;
            
            /**
             * 签名密钥
             * <p>默认值：default-secret-key</p>
             * <p>用于生成和验证签名 URL 的 HMAC-SHA256 密钥</p>
             * <p>生产环境请务必修改为复杂密钥</p>
             */
            private String secretKey = "default-secret-key";
            
            /**
             * 默认过期时间（秒）
             * <p>默认值：3600（1小时）</p>
             * <p>签名 URL 的默认有效期</p>
             */
            private long defaultExpire = 3600;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public long getDefaultExpire() {
                return defaultExpire;
            }

            public void setDefaultExpire(long defaultExpire) {
                this.defaultExpire = defaultExpire;
            }
        }
    }

    /**
     * 远程访问配置类
     * 
     * <p>配置跨节点访问文件的方式</p>
     * <p>启用后，当文件不在本地节点时，会自动从远程节点获取</p>
     * <p>使用 HTTP 协议进行跨节点通信，简单易用，无额外依赖</p>
     */
    public static class Remote {
        
        /**
         * 是否启用远程访问
         * <p>默认值：false</p>
         * <p>启用分布式模式，支持跨节点文件访问</p>
         */
        private boolean enabled = false;

        /**
         * 连接超时时间（毫秒）
         * <p>默认值：5000（5秒）</p>
         */
        private int connectTimeout = 5000;

        /**
         * 读取超时时间（毫秒）
         * <p>默认值：30000（30秒）</p>
         */
        private int readTimeout = 30000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    /**
     * 缩略图配置类
     * 
     * <p>配置图片缩略图生成功能，采用长边优先策略保持宽高比。</p>
     * 
     * <p>示例配置：</p>
     * <pre>
     * litefs:
     *   thumbnail:
     *     enabled: true
     *     default-size: small
     *     sizes:
     *       small:
     *         max-edge: 200
     *       medium:
     *         max-edge: 400
     *       large:
     *         max-edge: 800
     * </pre>
     */
    public static class Thumbnail {

        /**
         * 是否启用缩略图生成功能
         * <p>默认值：false</p>
         * <p>启用后，上传图片时会自动生成缩略图</p>
         * <p>注意：仅对图片类型文件生效（contentType 以 "image/" 开头）</p>
         */
        private boolean enabled = false;

        /**
         * 默认缩略图尺寸名称
         * <p>默认值：small</p>
         * <p>当上传时未指定尺寸时使用此默认值</p>
         */
        private String defaultSize = "small";

        /**
         * 预定义尺寸列表
         * <p>默认包含 small、medium、large 三种尺寸</p>
         * <p>可以自定义添加更多尺寸</p>
         */
        private java.util.Map<String, io.github.fangyudev.litefs.model.ThumbnailSize> sizes = new java.util.HashMap<>();

        /**
         * 默认构造函数
         * 
         * <p>初始化默认的缩略图尺寸：</p>
         * <ul>
         *   <li>small - 最长边 200px</li>
         *   <li>medium - 最长边 400px</li>
         *   <li>large - 最长边 800px</li>
         * </ul>
         */
        public Thumbnail() {
            sizes.put("small", new io.github.fangyudev.litefs.model.ThumbnailSize(200));
            sizes.put("medium", new io.github.fangyudev.litefs.model.ThumbnailSize(400));
            sizes.put("large", new io.github.fangyudev.litefs.model.ThumbnailSize(800));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultSize() {
            return defaultSize;
        }

        public void setDefaultSize(String defaultSize) {
            this.defaultSize = defaultSize;
        }

        public java.util.Map<String, io.github.fangyudev.litefs.model.ThumbnailSize> getSizes() {
            return sizes;
        }

        public void setSizes(java.util.Map<String, io.github.fangyudev.litefs.model.ThumbnailSize> sizes) {
            this.sizes = sizes;
        }
    }

    /**
     * 服务注册中心配置类
     * 
     * <p>配置服务注册中心的类型和参数</p>
     * 
     * <p>示例配置：</p>
     * <pre>
     * litefs:
     *   registry:
     *     type: static
     *     static:
     *       nodes:
     *         - id: node-1
     *           host: 192.168.1.1
     *           port: 8080
     * </pre>
     */
    public static class Registry {

        /**
         * 注册中心类型
         * <p>默认值：static</p>
         * <p>可选值：</p>
         * <ul>
         *   <li>static - 静态配置（默认，适合开发测试）</li>
         *   <li>nacos - Nacos 注册中心</li>
         *   <li>consul - Consul 注册中心</li>
         * </ul>
         * <p>注意：nacos、consul 会自动复用 Spring Cloud 的配置</p>
         */
        private String type = "static";

        /**
         * 静态注册中心配置
         */
        private StaticRegistry staticConfig = new StaticRegistry();

        /**
         * Nacos 注册中心配置
         * <p>如果不配置，会自动读取 spring.cloud.nacos.discovery.* 的配置</p>
         */
        private NacosRegistry nacos = new NacosRegistry();

        /**
         * Consul 注册中心配置
         * <p>如果不配置，会自动读取 spring.cloud.consul.discovery.* 的配置</p>
         */
        private ConsulRegistry consul = new ConsulRegistry();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public StaticRegistry getStaticConfig() {
            return staticConfig;
        }

        public void setStaticConfig(StaticRegistry staticConfig) {
            this.staticConfig = staticConfig;
        }

        public NacosRegistry getNacos() {
            return nacos;
        }

        public void setNacos(NacosRegistry nacos) {
            this.nacos = nacos;
        }

        public ConsulRegistry getConsul() {
            return consul;
        }

        public void setConsul(ConsulRegistry consul) {
            this.consul = consul;
        }

        /**
         * 静态注册中心配置类
         */
        public static class StaticRegistry {

            /**
             * 静态节点列表
             */
            private java.util.List<NodeConfig> nodes = new java.util.ArrayList<>();

            public java.util.List<NodeConfig> getNodes() {
                return nodes;
            }

            public void setNodes(java.util.List<NodeConfig> nodes) {
                this.nodes = nodes;
            }
        }

        /**
         * 节点配置类
         */
        public static class NodeConfig {

            private String id;
            private String host;
            private int port;
            private long totalSpace;

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

            public long getTotalSpace() {
                return totalSpace;
            }

            public void setTotalSpace(long totalSpace) {
                this.totalSpace = totalSpace;
            }
        }

        /**
         * Nacos 注册中心配置类
         */
        public static class NacosRegistry {

            private String serverAddr = "127.0.0.1:8848";
            private String namespace = "";
            private String group = "DEFAULT_GROUP";

            public String getServerAddr() {
                return serverAddr;
            }

            public void setServerAddr(String serverAddr) {
                this.serverAddr = serverAddr;
            }

            public String getNamespace() {
                return namespace;
            }

            public void setNamespace(String namespace) {
                this.namespace = namespace;
            }

            public String getGroup() {
                return group;
            }

            public void setGroup(String group) {
                this.group = group;
            }
        }

        /**
         * Consul 注册中心配置类
         */
        public static class ConsulRegistry {

            private String host = "127.0.0.1";
            private int port = 8500;

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
        }
    }

    /**
     * 负载均衡配置类
     * 
     * <p>配置节点选择策略和副本放置策略</p>
     * 
     * <p>示例配置：</p>
     * <pre>
     * litefs:
     *   load-balance:
     *     node-selector: round-robin
     *     replica-placer: balanced
     * </pre>
     */
    public static class LoadBalance {

        /**
         * 节点选择器类型
         * <p>默认值：round-robin</p>
         * <p>可选值：</p>
         * <ul>
         *   <li>round-robin - 轮询选择，请求均匀分发到各节点</li>
         *   <li>capacity - 容量优先，选择空闲空间最多的节点</li>
         * </ul>
         */
        private String nodeSelector = "round-robin";

        /**
         * 副本放置器类型
         * <p>默认值：balanced</p>
         * <p>可选值：</p>
         * <ul>
         *   <li>balanced - 平衡分布，选择负载较低的节点</li>
         *   <li>locality - 就近放置，优先选择同区域的节点</li>
         * </ul>
         */
        private String replicaPlacer = "balanced";

        public String getNodeSelector() {
            return nodeSelector;
        }

        public void setNodeSelector(String nodeSelector) {
            this.nodeSelector = nodeSelector;
        }

        public String getReplicaPlacer() {
            return replicaPlacer;
        }

        public void setReplicaPlacer(String replicaPlacer) {
            this.replicaPlacer = replicaPlacer;
        }
    }

    /**
     * 分片上传配置类
     * 
     * <p>配置分片上传存储类型和清理调度器等参数</p>
     * 
     * <p>示例配置：</p>
     * <pre>
     * litefs:
     *   multipart:
     *     store-type: redis      # local | redis
     *     cleanup-enabled: true
     *     cleanup-interval: 3600000
     * </pre>
     * 
     * <h3>存储类型选择：</h3>
     * <ul>
     *   <li><b>local</b> - 本地内存存储，仅限单节点模式（remote.enabled=false）</li>
     *   <li><b>redis</b> - Redis 分布式存储，多节点共享，分布式模式（remote.enabled=true）必须使用</li>
     * </ul>
     * 
     * <h3>重要警告：</h3>
     * <p>分布式模式（remote.enabled=true）下，store-type 必须为 redis，
     * 否则应用启动时会报错。这是为了防止出现 "Upload not found" bug。</p>
     */
    public static class Multipart {

        /**
         * 分片上传会话存储类型
         * <p>默认值：local（单机模式）</p>
         * <p>可选值：</p>
         * <ul>
         *   <li>local - 本地内存存储，仅适用于单机模式</li>
         *   <li>redis - Redis 分布式存储，适用于分布式模式</li>
         * </ul>
         * <p><b>分布式模式强制要求：</b>当 remote.enabled=true 时，必须设置为 redis</p>
         */
        private String storeType = "local";

        /**
         * 是否启用分片上传清理调度器
         * <p>默认值：false</p>
         * <p>启用后，会定时清理过期的分片上传会话</p>
         * <p>如果不需要分片上传功能，可以保持关闭以节省资源</p>
         */
        private boolean cleanupEnabled = false;

        /**
         * 清理间隔（毫秒）
         * <p>默认值：3600000（1小时）</p>
         * <p>仅当 cleanup-enabled=true 时生效</p>
         */
        private long cleanupInterval = 3600000;

        /**
         * Redis 分片上传存储配置
         * <p>如果未单独配置，会自动复用 replication.queue.redis 的连接参数</p>
         */
        private MultipartRedis redis = new MultipartRedis();

        public String getStoreType() {
            return storeType;
        }

        public void setStoreType(String storeType) {
            this.storeType = storeType;
        }

        public boolean isCleanupEnabled() {
            return cleanupEnabled;
        }

        public void setCleanupEnabled(boolean cleanupEnabled) {
            this.cleanupEnabled = cleanupEnabled;
        }

        public long getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(long cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }

        public MultipartRedis getRedis() {
            return redis;
        }

        public void setRedis(MultipartRedis redis) {
            this.redis = redis;
        }
    }

    /**
     * Redis 分片上传存储配置类
     * 
     * <p>配置 Redis 分布式存储的连接参数。</p>
     * <p>如果未单独配置，会自动复用 replication.queue.redis 的连接参数。</p>
     */
    public static class MultipartRedis {

        /**
         * Redis 服务器地址
         * <p>默认值：空（自动复用 replication.queue.redis.host）</p>
         */
        private String host = "";

        /**
         * Redis 服务器端口
         * <p>默认值：0（自动复用 replication.queue.redis.port）</p>
         */
        private int port = 0;

        /**
         * Redis 密码
         * <p>默认值：空（自动复用 replication.queue.redis.password）</p>
         */
        private String password = "";

        /**
         * Redis 数据库索引
         * <p>默认值：-1（自动复用 replication.queue.redis.database）</p>
         */
        private int database = -1;

        /**
         * 连接超时时间（毫秒）
         * <p>默认值：0（自动复用 replication.queue.redis.timeoutMs）</p>
         */
        private int timeoutMs = 0;

        /**
         * 分片上传 Key 前缀
         * <p>默认值：litefs:multipart:</p>
         */
        private String keyPrefix = "litefs:multipart:";

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

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    /**
     * 元数据缓存配置类
     * 
     * <p>配置元数据缓存功能，减少数据库访问次数，提升访问性能。</p>
     * <p>支持本地缓存（LRU + TTL）和 Redis 分布式缓存两种模式。</p>
     * <p>采用 Cache Aside Pattern（写 DB + 删缓存）避免并发竞争导致脏数据。</p>
     * 
     * <p>示例配置：</p>
     * <pre>
     * litefs:
     *   cache:
     *     enabled: true
     *     type: redis          # local | redis
     *     max-size: 10000      # local 专用
     *     ttl: 300000          # 两种模式均支持
     *     redis:
     *       host: 127.0.0.1
     *       port: 6379
     *       key-prefix: "litefs:meta:"
     * </pre>
     * 
     * <h3>缓存类型选择：</h3>
     * <ul>
     *   <li><b>local</b> - 本地缓存，仅限单节点，不需要额外依赖</li>
     *   <li><b>redis</b> - Redis 分布式缓存，多节点共享，需要 jedis 依赖</li>
     * </ul>
     */
    public static class Cache {

        /**
         * 是否启用元数据缓存
         * <p>默认值：false</p>
         * <p>多节点环境下建议使用 type=redis，单节点可使用 type=local</p>
         */
        private boolean enabled = false;

        /**
         * 缓存类型
         * <p>默认值：local</p>
         * <p>可选值：</p>
         * <ul>
         *   <li>local - 本地缓存（LRU + TTL），仅限单节点</li>
         *   <li>redis - Redis 分布式缓存，多节点共享</li>
         * </ul>
         */
        private String type = "local";

        /**
         * 最大缓存条目数（仅 local 模式生效）
         * <p>默认值：10000</p>
         * <p>当缓存达到最大容量时，会淘汰最久未使用的条目（LRU）</p>
         */
        private int maxSize = 10000;

        /**
         * 缓存过期时间（毫秒）
         * <p>默认值：300000（5分钟）</p>
         * <p>缓存条目在指定时间后自动过期，需要重新从数据库加载</p>
         */
        private long ttl = 300000;

        /**
         * Redis 缓存配置
         */
        private CacheRedis redis = new CacheRedis();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public long getTtl() {
            return ttl;
        }

        public void setTtl(long ttl) {
            this.ttl = ttl;
        }

        public CacheRedis getRedis() {
            return redis;
        }

        public void setRedis(CacheRedis redis) {
            this.redis = redis;
        }
    }

    /**
     * Redis 缓存配置类
     * 
     * <p>配置 Redis 分布式缓存的连接参数。</p>
     * <p>如果未单独配置，会自动复用 replication.queue.redis 的连接参数。</p>
     */
    public static class CacheRedis {

        /**
         * Redis 服务器地址
         * <p>默认值：空（自动复用 replication.queue.redis.host）</p>
         */
        private String host = "";

        /**
         * Redis 服务器端口
         * <p>默认值：0（自动复用 replication.queue.redis.port）</p>
         */
        private int port = 0;

        /**
         * Redis 密码
         * <p>默认值：空（自动复用 replication.queue.redis.password）</p>
         */
        private String password = "";

        /**
         * Redis 数据库索引
         * <p>默认值：-1（自动复用 replication.queue.redis.database）</p>
         */
        private int database = -1;

        /**
         * 连接超时时间（毫秒）
         * <p>默认值：0（自动复用 replication.queue.redis.timeoutMs）</p>
         */
        private int timeoutMs = 0;

        /**
         * 缓存 Key 前缀
         * <p>默认值：litefs:meta:</p>
         */
        private String keyPrefix = "litefs:meta:";

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

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }
}
