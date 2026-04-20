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

package io.github.fangyudev.litefs.builder;

import io.github.fangyudev.litefs.api.FileClient;
import io.github.fangyudev.litefs.api.UrlGenerator;
import io.github.fangyudev.litefs.impl.client.FileClientImpl;
import io.github.fangyudev.litefs.impl.url.GatewayUrlGenerator;
import io.github.fangyudev.litefs.service.FailoverService;
import io.github.fangyudev.litefs.impl.store.metadata.H2MetadataStore;
import io.github.fangyudev.litefs.model.ConsistencyLevel;
import io.github.fangyudev.litefs.model.ReplicationStrategy;
import io.github.fangyudev.litefs.impl.loadbalance.placement.BalancedReplicaPlacer;
import io.github.fangyudev.litefs.impl.store.replica.InMemoryReplicaMetadataStore;
import io.github.fangyudev.litefs.service.ReplicationService;
import io.github.fangyudev.litefs.impl.store.engine.local.SingleNodeStorageEngineRouter;
import io.github.fangyudev.litefs.impl.store.engine.local.LocalStorageEngine;
import io.github.fangyudev.litefs.impl.store.multipart.LocalMultipartUploadStore;
import io.github.fangyudev.litefs.spi.MessageQueue;
import io.github.fangyudev.litefs.spi.MetadataStore;
import io.github.fangyudev.litefs.spi.MultipartUploadStore;
import io.github.fangyudev.litefs.spi.NodeSelector;
import io.github.fangyudev.litefs.spi.ReplicaMetadataStore;
import io.github.fangyudev.litefs.spi.ReplicaPlacer;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;


/**
 * FileClient构建器，用于便捷地创建和配置FileClient实例。
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 使用默认配置
 * FileClient client = FileClientBuilder.createDefault();
 * 
 * // 自定义配置
 * FileClient client = FileClientBuilder.builder()
 *     .storagePath("./data/files")
 *     .jdbcUrl("jdbc:h2:./data/litefs")
 *     .gatewayBaseUrl("http://localhost:8080")
 *     .secretKey("my-secret-key")
 *     .build();
 * }</pre>
 */
public class FileClientBuilder {


    private StorageEngine storageEngine;
    private MetadataStore metadataStore;
    private UrlGenerator urlGenerator;
    private String nodeId = "node-1";
    private String storagePath = "./data/files";
    private String jdbcUrl = "jdbc:h2:./data/litefs;AUTO_SERVER=TRUE";
    private String gatewayBaseUrl = "http://localhost:8080";
    private String gatewayPathPrefix = "/api/files";
    private String secretKey = "default-secret-key";
    private ServiceRegistry serviceRegistry;
    private NodeSelector nodeSelector;
    private ReplicaPlacer replicaPlacer;
    private ReplicaMetadataStore replicaMetadataStore;
    private StorageEngineRouter storageEngineRouter;
    private ReplicationService replicationService;
    private FailoverService failoverService;
    private ReplicationStrategy defaultReplicationStrategy = ReplicationStrategy.standard();
    private boolean replicationEnabled = true;
    private boolean singleNodeReplicationEnabled = false;
    private ConsistencyLevel consistencyLevel = ConsistencyLevel.EVENTUAL;
    private MessageQueue messageQueue;
    private String replicationTopic = "litefs.replication";
    private MultipartUploadStore multipartUploadStore;

    /**
     * 设置自定义存储引擎。
     */
    public FileClientBuilder storageEngine(StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
        return this;
    }

    /**
     * 设置自定义元数据存储。
     */
    public FileClientBuilder metadataStore(MetadataStore metadataStore) {
        this.metadataStore = metadataStore;
        return this;
    }

    /**
     * 设置自定义URL生成器。
     */
    public FileClientBuilder urlGenerator(UrlGenerator urlGenerator) {
        this.urlGenerator = urlGenerator;
        return this;
    }

    /**
     * 设置节点ID。
     */
    public FileClientBuilder nodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    /**
     * 设置本地存储路径。
     */
    public FileClientBuilder storagePath(String storagePath) {
        this.storagePath = storagePath;
        return this;
    }

    /**
     * 设置H2数据库JDBC URL。
     */
    public FileClientBuilder jdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        return this;
    }

    /**
     * 设置网关基础URL。
     */
    public FileClientBuilder gatewayBaseUrl(String gatewayBaseUrl) {
        this.gatewayBaseUrl = gatewayBaseUrl;
        return this;
    }

    /**
     * 设置网关路径前缀。
     */
    public FileClientBuilder gatewayPathPrefix(String gatewayPathPrefix) {
        this.gatewayPathPrefix = gatewayPathPrefix;
        return this;
    }

    /**
     * 设置签名密钥。
     */
    public FileClientBuilder secretKey(String secretKey) {
        this.secretKey = secretKey;
        return this;
    }

    /**
     * 设置服务注册中心。
     */
    public FileClientBuilder serviceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        return this;
    }

    /**
     * 设置节点选择器。
     */
    public FileClientBuilder nodeSelector(NodeSelector nodeSelector) {
        this.nodeSelector = nodeSelector;
        return this;
    }

    /**
     * 设置副本放置策略。
     */
    public FileClientBuilder replicaPlacer(ReplicaPlacer replicaPlacer) {
        this.replicaPlacer = replicaPlacer;
        return this;
    }

    /**
     * 设置副本元数据存储。
     */
    public FileClientBuilder replicaMetadataStore(ReplicaMetadataStore replicaMetadataStore) {
        this.replicaMetadataStore = replicaMetadataStore;
        return this;
    }

    /**
     * 设置存储引擎路由。
     */
    public FileClientBuilder storageEngineRouter(StorageEngineRouter storageEngineRouter) {
        this.storageEngineRouter = storageEngineRouter;
        return this;
    }

    /**
     * 设置复制服务。
     */
    public FileClientBuilder replicationService(ReplicationService replicationService) {
        this.replicationService = replicationService;
        return this;
    }

    /**
     * 设置故障转移服务。
     */
    public FileClientBuilder failoverService(FailoverService failoverService) {
        this.failoverService = failoverService;
        return this;
    }

    /**
     * 设置默认副本策略。
     */
    public FileClientBuilder defaultReplicationStrategy(ReplicationStrategy defaultReplicationStrategy) {
        this.defaultReplicationStrategy = defaultReplicationStrategy;
        return this;
    }

    /**
     * 设置是否启用副本复制。
     */
    public FileClientBuilder replicationEnabled(boolean replicationEnabled) {
        this.replicationEnabled = replicationEnabled;
        return this;
    }

    /**
     * 设置单机模式是否启用副本复制。
     */
    public FileClientBuilder singleNodeReplicationEnabled(boolean singleNodeReplicationEnabled) {
        this.singleNodeReplicationEnabled = singleNodeReplicationEnabled;
        return this;
    }

    /**
     * 设置一致性级别。
     */
    public FileClientBuilder consistencyLevel(ConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
        return this;
    }

    /**
     * 设置消息队列。
     */
    public FileClientBuilder messageQueue(MessageQueue messageQueue) {
        this.messageQueue = messageQueue;
        return this;
    }

    /**
     * 设置副本复制主题。
     */
    public FileClientBuilder replicationTopic(String replicationTopic) {
        this.replicationTopic = replicationTopic;
        return this;
    }

    /**
     * 设置分块上传会话存储。
     * 
     * <p>单机模式默认使用 {@link LocalMultipartUploadStore}（基于内存）；
     * 分布式模式必须使用 Redis 等共享存储实现（如 {@link io.github.fangyudev.litefs.impl.store.multipart.RedisMultipartUploadStore}），
     * 否则分块上传将因跨节点会话不可见而失败。</p>
     */
    public FileClientBuilder multipartUploadStore(MultipartUploadStore multipartUploadStore) {
        this.multipartUploadStore = multipartUploadStore;
        return this;
    }

    /**
     * 构建FileClient实例。
     * 
     * @return FileClient实例
     */
    public FileClient build() {
        // 使用默认存储引擎
        if (storageEngine == null) {
            storageEngine = new LocalStorageEngine(storagePath);
        }

        // 使用默认元数据存储
        if (metadataStore == null) {
            metadataStore = new H2MetadataStore(jdbcUrl);
            metadataStore.init();
        }

        // 使用默认URL生成器
        if (urlGenerator == null) {
            urlGenerator = new GatewayUrlGenerator(gatewayBaseUrl, gatewayPathPrefix, secretKey);
        }

        if (storageEngineRouter == null) {
            storageEngineRouter = new SingleNodeStorageEngineRouter(nodeId, storageEngine);
        }

        boolean distributedEnabled = serviceRegistry != null
            || nodeSelector != null
            || replicaPlacer != null
            || replicaMetadataStore != null
            || replicationService != null
            || failoverService != null;

        // 使用默认分块上传会话存储
        if (multipartUploadStore == null) {
            if (distributedEnabled) {
                throw new IllegalStateException(
                    "MultipartUploadStore must be configured in distributed mode. "
                    + "LocalMultipartUploadStore (in-memory) does not support cross-node session sharing, "
                    + "which will cause multipart upload to fail. "
                    + "Please use RedisMultipartUploadStore or other shared storage implementation "
                    + "via FileClientBuilder.multipartUploadStore().");
            }
            multipartUploadStore = new LocalMultipartUploadStore();
        }

        boolean replicationActive = replicationEnabled && (distributedEnabled || singleNodeReplicationEnabled);

        if (replicationActive) {
            if (replicaMetadataStore == null) {
                replicaMetadataStore = new InMemoryReplicaMetadataStore();
            }
            if (replicaPlacer == null) {
                replicaPlacer = new BalancedReplicaPlacer();
            }
            if (replicationService == null) {
                replicationService = new ReplicationService(serviceRegistry, replicaPlacer,
                    replicaMetadataStore, storageEngineRouter, defaultReplicationStrategy,
                    messageQueue, consistencyLevel, replicationTopic);
            }
            if (failoverService == null) {
                failoverService = new FailoverService(serviceRegistry, replicaMetadataStore, storageEngineRouter);
            }
        } else {
            replicationService = null;
        }

        return new FileClientImpl(storageEngine, metadataStore, urlGenerator, nodeId,
            storageEngineRouter, serviceRegistry, nodeSelector, replicationService, failoverService,
            null, null, multipartUploadStore, true, 0);
    }

    /**
     * 创建Builder实例。
     */
    public static FileClientBuilder builder() {
        return new FileClientBuilder();
    }

    /**
     * 使用默认配置创建FileClient。
     */
    public static FileClient createDefault() {
        return builder().build();
    }
}
