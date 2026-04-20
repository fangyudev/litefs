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

package io.github.fangyudev.litefs.impl.client;

import io.github.fangyudev.litefs.api.FileClient;
import io.github.fangyudev.litefs.api.UrlGenerator;
import io.github.fangyudev.litefs.service.FailoverService;
import io.github.fangyudev.litefs.model.*;
import io.github.fangyudev.litefs.service.ReplicationService;
import io.github.fangyudev.litefs.impl.store.engine.local.SingleNodeStorageEngineRouter;
import io.github.fangyudev.litefs.service.ThumbnailService;
import io.github.fangyudev.litefs.spi.MetadataStore;
import io.github.fangyudev.litefs.spi.MultipartUploadStore;
import io.github.fangyudev.litefs.spi.NodeSelector;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;
import io.github.fangyudev.litefs.util.ChecksumUtils;
import io.github.fangyudev.litefs.util.ContentTypeUtils;
import io.github.fangyudev.litefs.util.FileIdGeneratorUtil;
import io.github.fangyudev.litefs.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * FileClient 接口的默认实现，提供文件的上传、下载、删除等核心功能。
 * 
 * <p>该类是线程安全的，可在多线程环境中使用。</p>
 */
public class FileClientImpl implements FileClient {

    private static final Logger log = LoggerFactory.getLogger(FileClientImpl.class);

    private final StorageEngine storageEngine;
    private final MetadataStore metadataStore;
    private final UrlGenerator urlGenerator;
    private final String nodeId;
    private final StorageEngineRouter storageRouter;
    private final ServiceRegistry serviceRegistry;
    private final NodeSelector nodeSelector;
    private final ReplicationService replicationService;
    private final FailoverService failoverService;
    private final ThumbnailService thumbnailService;
    private final ThumbnailConfig thumbnailConfig;
    private final MultipartUploadStore multipartUploadStore;
    private final ScheduledExecutorService cleanupScheduler;

    /** 清理间隔（毫秒），默认1小时 */
    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 60 * 60 * 1000;

    /**
     * 构造函数
     *
     * @param storageEngine 存储引擎
     * @param metadataStore 元数据存储
     * @param urlGenerator URL生成器
     * @param nodeId 节点ID
     */
    public FileClientImpl(StorageEngine storageEngine, MetadataStore metadataStore,
                         UrlGenerator urlGenerator, String nodeId) {
        this(storageEngine, metadataStore, urlGenerator, nodeId, null, null, null, null, null, null, null, null, false, DEFAULT_CLEANUP_INTERVAL_MS);
    }

    /**
     * 分布式构造函数
     *
     * @param storageEngine 存储引擎
     * @param metadataStore 元数据存储
     * @param urlGenerator URL生成器
     * @param nodeId 节点ID
     * @param storageRouter 存储引擎路由
     * @param serviceRegistry 服务注册中心
     * @param nodeSelector 节点选择器
     * @param replicationService 复制与同步服务
     * @param failoverService 故障转移服务
     */
    public FileClientImpl(StorageEngine storageEngine, MetadataStore metadataStore,
                         UrlGenerator urlGenerator, String nodeId,
                         StorageEngineRouter storageRouter,
                         ServiceRegistry serviceRegistry,
                         NodeSelector nodeSelector,
                         ReplicationService replicationService,
                         FailoverService failoverService) {
        this(storageEngine, metadataStore, urlGenerator, nodeId, storageRouter, serviceRegistry, 
            nodeSelector, replicationService, failoverService, null, null, null, false, DEFAULT_CLEANUP_INTERVAL_MS);
    }

    /**
     * 完整构造函数
     *
     * @param storageEngine 存储引擎
     * @param metadataStore 元数据存储
     * @param urlGenerator URL生成器
     * @param nodeId 节点ID
     * @param storageRouter 存储引擎路由
     * @param serviceRegistry 服务注册中心
     * @param nodeSelector 节点选择器
     * @param replicationService 复制与同步服务
     * @param failoverService 故障转移服务
     * @param thumbnailService 缩略图生成服务
     * @param thumbnailConfig 缩略图配置
     */
    public FileClientImpl(StorageEngine storageEngine, MetadataStore metadataStore,
                         UrlGenerator urlGenerator, String nodeId,
                         StorageEngineRouter storageRouter,
                         ServiceRegistry serviceRegistry,
                         NodeSelector nodeSelector,
                         ReplicationService replicationService,
                         FailoverService failoverService,
                         ThumbnailService thumbnailService,
                         ThumbnailConfig thumbnailConfig) {
        this(storageEngine, metadataStore, urlGenerator, nodeId, storageRouter, serviceRegistry,
            nodeSelector, replicationService, failoverService, thumbnailService, thumbnailConfig, null, false, DEFAULT_CLEANUP_INTERVAL_MS);
    }

    /**
     * 完整构造函数（带分片上传清理配置）
     *
     * @param storageEngine 存储引擎
     * @param metadataStore 元数据存储
     * @param urlGenerator URL生成器
     * @param nodeId 节点ID
     * @param storageRouter 存储引擎路由
     * @param serviceRegistry 服务注册中心
     * @param nodeSelector 节点选择器
     * @param replicationService 复制与同步服务
     * @param failoverService 故障转移服务
     * @param thumbnailService 缩略图生成服务
     * @param thumbnailConfig 缩略图配置
     * @param multipartCleanupEnabled 是否启用分片上传清理调度器
     * @param multipartCleanupInterval 清理间隔（毫秒）
     * @deprecated 请使用带 MultipartUploadStore 参数的构造函数
     */
    @Deprecated
    public FileClientImpl(StorageEngine storageEngine, MetadataStore metadataStore,
                         UrlGenerator urlGenerator, String nodeId,
                         StorageEngineRouter storageRouter,
                         ServiceRegistry serviceRegistry,
                         NodeSelector nodeSelector,
                         ReplicationService replicationService,
                         FailoverService failoverService,
                         ThumbnailService thumbnailService,
                         ThumbnailConfig thumbnailConfig,
                         boolean multipartCleanupEnabled,
                         long multipartCleanupInterval) {
        this(storageEngine, metadataStore, urlGenerator, nodeId, storageRouter, serviceRegistry,
            nodeSelector, replicationService, failoverService, thumbnailService, thumbnailConfig,
            null, multipartCleanupEnabled, multipartCleanupInterval);
    }

    /**
     * 完整构造函数（带分片上传存储和清理配置）
     *
     * @param storageEngine 存储引擎
     * @param metadataStore 元数据存储
     * @param urlGenerator URL生成器
     * @param nodeId 节点ID
     * @param storageRouter 存储引擎路由
     * @param serviceRegistry 服务注册中心
     * @param nodeSelector 节点选择器
     * @param replicationService 复制与同步服务
     * @param failoverService 故障转移服务
     * @param thumbnailService 缩略图生成服务
     * @param thumbnailConfig 缩略图配置
     * @param multipartUploadStore 分片上传会话存储
     * @param multipartCleanupEnabled 是否启用分片上传清理调度器
     * @param multipartCleanupInterval 清理间隔（毫秒）
     */
    public FileClientImpl(StorageEngine storageEngine, MetadataStore metadataStore,
                         UrlGenerator urlGenerator, String nodeId,
                         StorageEngineRouter storageRouter,
                         ServiceRegistry serviceRegistry,
                         NodeSelector nodeSelector,
                         ReplicationService replicationService,
                         FailoverService failoverService,
                         ThumbnailService thumbnailService,
                         ThumbnailConfig thumbnailConfig,
                         MultipartUploadStore multipartUploadStore,
                         boolean multipartCleanupEnabled,
                         long multipartCleanupInterval) {
        this.storageEngine = storageEngine;
        this.metadataStore = metadataStore;
        this.urlGenerator = urlGenerator;
        this.nodeId = nodeId;
        this.storageRouter = storageRouter != null
            ? storageRouter
            : new SingleNodeStorageEngineRouter(nodeId, storageEngine);
        this.serviceRegistry = serviceRegistry;
        this.nodeSelector = nodeSelector;
        this.replicationService = replicationService;
        this.failoverService = failoverService;
        this.thumbnailService = thumbnailService;
        this.thumbnailConfig = thumbnailConfig != null ? thumbnailConfig : new ThumbnailConfig();
        this.multipartUploadStore = multipartUploadStore;
        
        // 初始化分块上传清理调度器（仅在启用时创建）
        if (multipartCleanupEnabled) {
            this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "litefs-multipart-cleanup");
                t.setDaemon(true);
                return t;
            });
            
            long interval = multipartCleanupInterval > 0 ? multipartCleanupInterval : DEFAULT_CLEANUP_INTERVAL_MS;
            this.cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredMultipartUploads,
                interval,
                interval,
                TimeUnit.MILLISECONDS
            );
            
            log.info("Multipart upload cleanup scheduler started, interval: {}ms", interval);
        } else {
            this.cleanupScheduler = null;
        }
    }


    // ==================== 文件操作 ====================

    /**
     * 上传文件
     * 将文件数据写入存储引擎，并保存元数据
     * 
     * <p>注意：此方法会将整个文件读入内存，适合小文件（建议5MB以下）。
     * 大文件请使用分片上传API。</p>
     */
    @Override
    public String upload(InputStream inputStream, String fileName, Map<String, String> metadata) {
        return upload(inputStream, fileName, metadata, null);
    }

    /**
     * 上传文件（带选项）
     * 
     * <p>支持控制缩略图生成等行为。</p>
     * 
     * @param inputStream 文件输入流
     * @param fileName 文件名
     * @param metadata 文件元数据
     * @param options 上传选项
     * @return 文件ID
     */
    @Override
    public String
    upload(InputStream inputStream, String fileName, Map<String, String> metadata, UploadOptions options) {
        // 生成文件ID
        String fileId = FileIdGeneratorUtil.generate(fileName);
        
        // 读取文件数据
        byte[] data;
        try {
            data = IoUtils.toByteArray(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream", e);
        }

        // 计算校验和
        String checksum = ChecksumUtils.md5Hex(data);
        String contentType = ContentTypeUtils.getContentType(fileName);

        FileUploadRequest uploadRequest = FileUploadRequest.builder()
                .fileName(fileName)
                .fileSize(data.length)
                .contentType(contentType)
                .metadata(metadata)
                .build();

        String targetNodeId = selectTargetNodeId(uploadRequest);
        StorageEngine targetEngine = storageRouter.getEngine(targetNodeId);

        // 写入存储引擎
        String storagePath = targetEngine.write(fileId, new ByteArrayInputStream(data));

        // 构建元数据
        FileMetadata.Builder metadataBuilder = FileMetadata.builder()
                .id(fileId)
                .fileName(fileName)
                .contentType(contentType)
                .fileSize(data.length)
                .checksum(checksum)
                .storageNodeId(targetNodeId)
                .storagePath(storagePath)
                .metadata(metadata != null ? metadata : new HashMap<>())
                .status(FileStatus.COMMITTED);

        // 处理上传选项中的 visibility 和 expireTime
        if (options != null) {
            if (options.getVisibility() != null) {
                metadataBuilder.visibility(options.getVisibility());
            }
            if (options.getExpireTime() != null) {
                metadataBuilder.expireTime(options.getExpireTime());
            }
        }

        FileMetadata fileMetadata = metadataBuilder.build();

        // 保存元数据
        metadataStore.save(fileMetadata);

        // 触发副本复制
        if (replicationService != null) {
            replicationService.replicate(fileMetadata, null, uploadRequest);
        }

        // 异步生成缩略图（如果需要）
        generateThumbnailAsync(fileId, contentType, options);

        log.info("File uploaded: {} -> {} ({} bytes)", fileName, fileId, data.length);
        return fileId;
    }

    /**
     * 下载文件
     * 从存储引擎读取文件数据
     */
    @Override
    public InputStream download(String fileId) {
        FileMetadata metadata = metadataStore.get(fileId);
        if (metadata == null) {
            throw new RuntimeException("File not found: " + fileId);
        }

        if (metadata.getStatus() == FileStatus.DELETED) {
            throw new RuntimeException("File has been deleted: " + fileId);
        }

        if (failoverService != null) {
            return failoverService.readWithFailover(metadata);
        }

        return storageRouter.getEngine(metadata.getStorageNodeId()).read(fileId);
    }

    /**
     * 删除文件
     * 物理删除文件数据，元数据标记为已删除状态，同时删除副本
     * 
     * <p>如果文件有缩略图，会同时删除缩略图，保持数据一致性。</p>
     */
    @Override
    public void delete(String fileId) {
        FileMetadata metadata = metadataStore.get(fileId);
        if (metadata == null) {
            throw new RuntimeException("File not found: " + fileId);
        }

        // 先删除缩略图（如果存在）
        deleteThumbnailIfExists(metadata);

        // 删除物理文件
        StorageEngine primaryEngine = storageRouter.getEngine(metadata.getStorageNodeId());
        primaryEngine.delete(fileId);

        if (replicationService != null) {
            List<ReplicaInfo> replicas = replicationService.listReplicas(fileId);
            for (ReplicaInfo replica : replicas) {
                if (replica == null || replica.getNodeId() == null) {
                    continue; 
                }
                try {
                    storageRouter.getEngine(replica.getNodeId()).delete(fileId);
                } catch (Exception ignored) {
                    // 删除副本失败不影响主流程
                }
            }
            // 删除副本元数据记录
            replicationService.deleteReplicas(fileId);
        }

        // 更新元数据状态
        metadata.setStatus(FileStatus.DELETED);
        metadata.setUpdateTime(System.currentTimeMillis());
        metadataStore.update(metadata);

        log.info("File deleted: {}", fileId);
    }

    /**
     * 删除缩略图（如果存在）
     * 
     * <p>删除原图时自动删除关联的缩略图，保持数据一致性。</p>
     * 
     * @param metadata 原图元数据
     */
    private void deleteThumbnailIfExists(FileMetadata metadata) {
        String thumbnailId = metadata.getThumbnailId();
        if (thumbnailId == null || thumbnailId.isEmpty()) {
            return;
        }

        try {
            // 获取缩略图元数据
            FileMetadata thumbnailMetadata = metadataStore.get(thumbnailId);
            if (thumbnailMetadata != null) {
                // 删除缩略图物理文件
                StorageEngine thumbnailEngine = storageRouter.getEngine(thumbnailMetadata.getStorageNodeId());
                thumbnailEngine.delete(thumbnailId);
                
                // 删除缩略图元数据
                metadataStore.delete(thumbnailId);
                log.info("Thumbnail deleted: {}", thumbnailId);
            }
        } catch (Exception e) {
            // 删除缩略图失败只记录警告，不影响原图删除
            log.warn("Failed to delete thumbnail: {}", thumbnailId, e);
        }
    }

    /**
     * 复制文件
     * 创建文件的完整副本，生成新的文件ID
     * 新文件将遵循系统的副本策略，自动复制到其他节点
     */
    @Override
    public String copy(String fileId) {
        return copy(fileId, true);
    }

    /**
     * 复制文件
     * 创建文件的完整副本，生成新的文件ID
     * 
     * @param fileId 源文件ID
     * @param replicate 是否触发副本复制
     */
    @Override
    public String copy(String fileId, boolean replicate) {
        FileMetadata sourceMetadata = metadataStore.get(fileId);
        if (sourceMetadata == null) {
            throw new RuntimeException("File not found: " + fileId);
        }

        if (sourceMetadata.getStatus() == FileStatus.DELETED) {
            throw new RuntimeException("Source file has been deleted: " + fileId);
        }

        // 生成新文件ID
        String newFileId = FileIdGeneratorUtil.generate(sourceMetadata.getFileName());

        String targetNodeId = selectTargetNodeId(FileUploadRequest.builder()
                .fileName(sourceMetadata.getFileName())
                .fileSize(sourceMetadata.getFileSize())
                .contentType(sourceMetadata.getContentType())
                .metadata(sourceMetadata.getMetadata())
                .build());
        StorageEngine targetEngine = storageRouter.getEngine(targetNodeId);

        String storagePath;
        // 复制文件数据
        try (InputStream sourceStream = download(fileId)) {
            byte[] data = IoUtils.toByteArray(sourceStream);
            storagePath = targetEngine.write(newFileId, new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy file: " + fileId, e);
        }

        // 创建新元数据（继承原文件的权限设置）
        FileMetadata newMetadata = FileMetadata.builder()
                .id(newFileId)
                .fileName(sourceMetadata.getFileName())
                .contentType(sourceMetadata.getContentType())
                .fileSize(sourceMetadata.getFileSize())
                .checksum(sourceMetadata.getChecksum())
                .storageNodeId(targetNodeId)
                .storagePath(storagePath)
                .metadata(new HashMap<>(sourceMetadata.getMetadata()))
                .status(FileStatus.COMMITTED)
                .visibility(sourceMetadata.getVisibility())
                .expireTime(sourceMetadata.getExpireTime())
                .build();

        metadataStore.save(newMetadata);

        if (replicate && replicationService != null) {
            FileUploadRequest uploadRequest = FileUploadRequest.builder()
                    .fileName(newMetadata.getFileName())
                    .fileSize(newMetadata.getFileSize())
                    .contentType(newMetadata.getContentType())
                    .metadata(newMetadata.getMetadata())
                    .build();
            replicationService.replicate(newMetadata, null, uploadRequest);
        }

        log.info("File copied: {} -> {} (replicate={})", fileId, newFileId, replicate);
        return newFileId;
    }

    /**
     * 重命名文件
     * 只修改文件的显示名称和内容类型，不影响物理存储
     * 
     * <p>说明：</p>
     * <ul>
     *   <li>修改文件名（fileName）</li>
     *   <li>根据新文件名自动更新内容类型（contentType）</li>
     *   <li>更新修改时间（updateTime）</li>
     *   <li>不移动物理文件，因为存储路径基于 fileId 生成</li>
     * </ul>
     * 
     * @param fileId 文件ID
     * @param newFileName 新文件名
     * @throws RuntimeException 文件不存在时抛出
     */
    @Override
    public void rename(String fileId, String newFileName) {
        FileMetadata metadata = metadataStore.get(fileId);
        if (metadata == null) {
            throw new RuntimeException("File not found: " + fileId);
        }

        metadata.setFileName(newFileName);
        metadata.setContentType(ContentTypeUtils.getContentType(newFileName));
        metadata.setUpdateTime(System.currentTimeMillis());
        metadataStore.update(metadata);

        log.info("File renamed: {} -> {}", fileId, newFileName);
    }

    // ==================== 元数据操作 ====================

    /**
     * 获取文件元数据
     * 
     * @param fileId 文件ID
     * @return 文件元数据，不存在则返回null
     */
    @Override
    public FileMetadata getMetadata(String fileId) {
        return metadataStore.get(fileId);
    }

    /**
     * 更新文件元数据
     * 将新元数据合并到现有元数据中
     */
    @Override
    public void updateMetadata(String fileId, Map<String, String> metadata) {
        FileMetadata fileMetadata = metadataStore.get(fileId);
        if (fileMetadata == null) {
            throw new RuntimeException("File not found: " + fileId);
        }

        // 调试日志：记录更新前的文件名
        String oldFileName = fileMetadata.getFileName();
        log.debug("updateMetadata START - fileId={}, fileName before update={}", fileId, oldFileName);

        // 处理特殊的系统字段
        Map<String, String> customMetadata = new HashMap<>(metadata);
        
        // 记录权限变更，用于同步更新缩略图
        FileVisibility newVisibility = null;
        Long newExpireTime = null;
        
        // 处理 visibility
        String visibilityStr = customMetadata.remove("visibility");
        if (visibilityStr != null) {
            try {
                newVisibility = FileVisibility.valueOf(visibilityStr.toUpperCase());
                fileMetadata.setVisibility(newVisibility);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid visibility value: {}", visibilityStr);
            }
        }
        
        // 处理 expireTime
        String expireTimeStr = customMetadata.remove("expireTime");
        if (expireTimeStr != null) {
            try {
                newExpireTime = Long.parseLong(expireTimeStr);
                fileMetadata.setExpireTime(newExpireTime);
            } catch (NumberFormatException e) {
                // 忽略无效的 expireTime 值
            }
        }

        // 更新剩余的自定义元数据
        fileMetadata.getMetadata().putAll(customMetadata);
        fileMetadata.setUpdateTime(System.currentTimeMillis());
        
        // 调试日志：记录即将保存的文件名
        log.debug("updateMetadata BEFORE SAVE - fileId={}, fileName to save={}", fileId, fileMetadata.getFileName());
        
        metadataStore.update(fileMetadata);
        
        // 调试日志：记录保存后重新读取的文件名
        FileMetadata afterUpdate = metadataStore.get(fileId);
        log.debug("updateMetadata AFTER SAVE - fileId={}, fileName in db={}", fileId, 
            afterUpdate != null ? afterUpdate.getFileName() : "null");

        // 同步更新缩略图权限
        if (newVisibility != null || newExpireTime != null) {
            String thumbnailId = fileMetadata.getThumbnailId();
            if (thumbnailId != null && !thumbnailId.isEmpty()) {
                syncThumbnailVisibility(thumbnailId, newVisibility, newExpireTime);
            }
        }

        log.info("Metadata updated: {}", fileId);
    }

    /**
     * 同步更新缩略图的权限设置
     * 当原图权限变更时，缩略图权限应保持一致
     * 
     * @param thumbnailId 缩略图文件ID
     * @param visibility 新的访问权限（可为null，表示不变）
     * @param expireTime 新的过期时间（可为null，表示不变）
     */
    private void syncThumbnailVisibility(String thumbnailId, FileVisibility visibility, Long expireTime) {
        FileMetadata thumbnailMetadata = metadataStore.get(thumbnailId);
        if (thumbnailMetadata == null) {
            log.warn("Thumbnail metadata not found: {}", thumbnailId);
            return;
        }
        
        boolean updated = false;
        if (visibility != null) {
            thumbnailMetadata.setVisibility(visibility);
            updated = true;
        }
        if (expireTime != null) {
            thumbnailMetadata.setExpireTime(expireTime);
            updated = true;
        }
        
        if (updated) {
            thumbnailMetadata.setUpdateTime(System.currentTimeMillis());
            metadataStore.update(thumbnailMetadata);
            log.info("Thumbnail visibility synced: thumbnailId={}", thumbnailId);
        }
    }

    @Override
    public List<FileMetadata> listFiles(FileQuery query) {
        return metadataStore.query(query);
    }

    // ==================== URL操作 ====================

    @Override
    public String getUrl(String fileId) {
        validateFileExists(fileId);
        return urlGenerator.generateUrl(fileId);
    }

    @Override
    public String getUrl(String fileId, long expireSeconds) {
        validateFileExists(fileId);
        return urlGenerator.generateSignedUrl(fileId, expireSeconds);
    }

    @Override
    public String getDownloadUrl(String fileId) {
        validateFileExists(fileId);
        return urlGenerator.generateDownloadUrl(fileId);
    }

    // ==================== 缩略图操作 ====================

    /**
     * 获取缩略图访问URL
     * 
     * <p>如果文件没有缩略图，返回 null。</p>
     * 
     * @param fileId 原图文件ID
     * @return 缩略图URL，如果没有缩略图则返回 null
     */
    @Override
    public String getThumbnailUrl(String fileId) {
        FileMetadata metadata = metadataStore.get(fileId);
        if (metadata == null) {
            return null;
        }
        
        String thumbnailId = metadata.getThumbnailId();
        if (thumbnailId == null || thumbnailId.isEmpty()) {
            return null;
        }
        
        return urlGenerator.generateThumbnailUrl(thumbnailId);
    }

    /**
     * 下载缩略图
     * 
     * <p>如果文件没有缩略图，返回 null。</p>
     * 
     * @param fileId 原图文件ID
     * @return 缩略图输入流，如果没有缩略图则返回 null
     */
    @Override
    public InputStream downloadThumbnail(String fileId) {
        FileMetadata metadata = metadataStore.get(fileId);
        if (metadata == null) {
            return null;
        }
        
        String thumbnailId = metadata.getThumbnailId();
        if (thumbnailId == null || thumbnailId.isEmpty()) {
            return null;
        }
        
        // 下载缩略图（使用原图的 download 方法，传入缩略图ID）
        return download(thumbnailId);
    }

    // ==================== 分块上传 ====================

    /**
     * 初始化分块上传
     * 创建上传会话，有效期24小时
     * 
     * <p>使用场景：前端需要上传大文件时，先调用此方法获取uploadId，
     * 然后前端将文件分片，逐个或并行上传各个分片。</p>
     */
    @Override
    public InitMultipartUploadResult initMultipartUpload(String fileName, long fileSize, Map<String, String> metadata) {
        if (multipartUploadStore == null) {
            throw new IllegalStateException(
                "MultipartUploadStore is not configured. "
                + "For standalone mode, use LocalMultipartUploadStore; "
                + "for distributed mode, use RedisMultipartUploadStore or other shared storage. "
                + "Please configure it via FileClientBuilder.multipartUploadStore().");
        }
        String uploadId = FileIdGeneratorUtil.generate(fileName);
        
        // 选择目标存储节点（所有分片和合并操作都在此节点执行）
        String targetNodeId = nodeId;
        if (nodeSelector != null && serviceRegistry != null) {
            try {
                FileUploadRequest uploadRequest = FileUploadRequest.builder()
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .metadata(metadata)
                    .build();
                StorageNode selected = nodeSelector.select(serviceRegistry.discover(), uploadRequest);
                if (selected != null && selected.getId() != null) {
                    targetNodeId = selected.getId();
                }
            } catch (Exception e) {
                log.warn("Failed to select node for multipart upload, using local node: {}", e.getMessage());
            }
        }
        
        MultipartUpload upload = new MultipartUpload();
        upload.setUploadId(uploadId);
        upload.setFileName(fileName);
        upload.setFileSize(fileSize);
        upload.setMetadata(metadata != null ? metadata : new HashMap<>());
        upload.setCreateTime(System.currentTimeMillis());
        upload.setExpireTime(System.currentTimeMillis() + 24 * 60 * 60 * 1000); // 24小时有效期
        upload.setTargetNodeId(targetNodeId);

        multipartUploadStore.save(upload);
        
        // 验证保存成功
        MultipartUpload saved = multipartUploadStore.get(uploadId);
        if (saved == null) {
            log.error("CRITICAL: Multipart upload save verification failed! uploadId={}, storeType={}", 
                uploadId, multipartUploadStore.getClass().getSimpleName());
        } else {
            log.info("Multipart upload initialized: uploadId={}, targetNodeId={}, fileSize={}, store={}", 
                uploadId, targetNodeId, fileSize, multipartUploadStore.getClass().getSimpleName());
        }
        
        return new InitMultipartUploadResult(uploadId, targetNodeId);
    }

    /**
     * 上传分块
     * 每个分块独立存储，完成后合并
     * 
     * <p>注意：分块数据会被临时存储，直到调用 completeMultipartUpload 合并。</p>
     */
    @Override
    public String uploadPart(String uploadId, int partNumber, InputStream inputStream) {
        if (multipartUploadStore == null) {
            throw new IllegalStateException(
                "MultipartUploadStore is not configured. "
                + "For standalone mode, use LocalMultipartUploadStore; "
                + "for distributed mode, use RedisMultipartUploadStore or other shared storage. "
                + "Please configure it via FileClientBuilder.multipartUploadStore().");
        }
        log.debug("uploadPart called: uploadId={}, partNumber={}, storeType={}", 
            uploadId, partNumber, multipartUploadStore.getClass().getSimpleName());
        
        MultipartUpload upload = multipartUploadStore.get(uploadId);
        if (upload == null) {
            // 尝试检查 Redis 中是否存在这个 key
            boolean exists = multipartUploadStore.exists(uploadId);
            log.error("Upload not found: uploadId={}, exists={}, store={}", 
                uploadId, exists, multipartUploadStore.getClass().getSimpleName());
            throw new RuntimeException("Upload not found: " + uploadId);
        }

        // 检查是否过期
        long now = System.currentTimeMillis();
        long expireTime = upload.getExpireTime();
        long createTime = upload.getCreateTime();
        boolean isExpired = upload.isExpired();
        
        log.info("Upload session time check: uploadId={}, createTime={}, expireTime={}, now={}, isExpired={}, remainingMs={}", 
            uploadId, createTime, expireTime, now, isExpired, (expireTime - now));
        
        if (isExpired) {
            // 会话已过期，先清理已上传的分块文件
            log.warn("Upload session expired, cleaning up: uploadId={}, createTime={}, expireTime={}, now={}", 
                uploadId, createTime, expireTime, now);
            cleanupMultipartUploadParts(upload);
            multipartUploadStore.delete(uploadId);
            throw new RuntimeException("Upload session expired: " + uploadId);
        }

        // 检查是否在正确的节点上执行（分布式环境下需要路由到目标节点）
        String targetNodeId = upload.getTargetNodeId() != null ? upload.getTargetNodeId() : nodeId;
        if (!nodeId.equals(targetNodeId)) {
            log.info("Upload part request on wrong node, forwarding to target: uploadId={}, targetNodeId={}, currentNode={}",
                uploadId, targetNodeId, nodeId);
            // 自动转发到目标节点
            return forwardUploadPart(targetNodeId, uploadId, partNumber, inputStream);
        }

        try {
            byte[] data = IoUtils.toByteArray(inputStream);
            String eTag = ChecksumUtils.md5Hex(data);
            
            // 存储分块到目标节点（已验证是当前节点）
            String partId = uploadId + "_part_" + partNumber;
            StorageEngine targetEngine = storageRouter.getEngine(targetNodeId);
            String storagePath = targetEngine.write(partId, new ByteArrayInputStream(data));

            // 记录分块信息
            PartInfo partInfo = PartInfo.builder()
                    .partNumber(partNumber)
                    .eTag(eTag)
                    .size(data.length)
                    .storagePath(storagePath)
                    .storageNodeId(targetNodeId)
                    .build();

            // 更新会话中的分块信息
            boolean added = multipartUploadStore.addPart(uploadId, partNumber, partInfo);
            if (!added) {
                // 添加失败，可能会话已过期或被删除
                throw new RuntimeException("Failed to add part to upload session: " + uploadId);
            }

            log.debug("Part uploaded: {} - part {} ({} bytes)", uploadId, partNumber, data.length);
            return eTag;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload part: " + partNumber, e);
        }
    }

    /**
     * 完成分块上传
     * 按分片序号顺序流式合并所有分块，创建完整文件
     * 
     * <p>修复说明：</p>
     * <ul>
     *   <li>按partNumber排序后再合并，确保顺序正确</li>
     *   <li>使用流式追加写入，避免内存溢出</li>
     *   <li>合并完成后计算整体文件的checksum</li>
     * </ul>
     */
    @Override
    public String completeMultipartUpload(String uploadId, List<PartInfo> parts) {
        if (multipartUploadStore == null) {
            throw new IllegalStateException(
                "MultipartUploadStore is not configured. "
                + "For standalone mode, use LocalMultipartUploadStore; "
                + "for distributed mode, use RedisMultipartUploadStore or other shared storage. "
                + "Please configure it via FileClientBuilder.multipartUploadStore().");
        }
        MultipartUpload upload = multipartUploadStore.get(uploadId);
        if (upload == null) {
            throw new RuntimeException("Upload not found: " + uploadId);
        }

        // 检查是否在正确的节点上执行（分布式环境下需要路由到目标节点）
        String targetNodeId = upload.getTargetNodeId() != null ? upload.getTargetNodeId() : nodeId;
        if (!nodeId.equals(targetNodeId)) {
            log.info("Complete multipart upload request on wrong node, forwarding to target: uploadId={}, targetNodeId={}, currentNode={}",
                uploadId, targetNodeId, nodeId);
            // 自动转发到目标节点
            return forwardCompleteMultipartUpload(targetNodeId, uploadId, parts);
        }

        // 验证所有分片都已上传
        for (PartInfo part : parts) {
            PartInfo storedPart = upload.getParts().get(part.getPartNumber());
            if (storedPart == null) {
                throw new RuntimeException("Part not found: " + part.getPartNumber());
            }
            // 验证ETag
            if (!storedPart.getETag().equals(part.getETag())) {
                throw new RuntimeException("Part ETag mismatch: " + part.getPartNumber());
            }
        }

        // 按partNumber排序，确保合并顺序正确
        List<PartInfo> sortedParts = parts.stream()
                .sorted(Comparator.comparingInt(PartInfo::getPartNumber))
                .collect(Collectors.toList());

        // 生成文件ID，使用目标节点的存储引擎
        String fileId = FileIdGeneratorUtil.generate(upload.getFileName());
        StorageEngine targetEngine = storageRouter.getEngine(targetNodeId);
        long totalSize = 0;
        List<String> partPathsToDelete = new ArrayList<>();

        try {
            // 流式追加写入，避免内存溢出
            // 所有分片都在目标节点上，直接读取合并
            for (PartInfo part : sortedParts) {
                PartInfo storedPart = upload.getParts().get(part.getPartNumber());
                
                // 读取分片并追加写入目标文件
                try (InputStream partStream = targetEngine.read(storedPart.getStoragePath())) {
                    long newSize = targetEngine.append(fileId, partStream);
                    totalSize = newSize;
                    partPathsToDelete.add(storedPart.getStoragePath());
                }
            }
        } catch (IOException e) {
            // 合并失败，清理已创建的文件
            try {
                targetEngine.delete(fileId);
            } catch (Exception ignored) {}
            throw new RuntimeException("Failed to merge parts", e);
        }

        // 计算整体文件的checksum（合并完成后读取文件计算）
        String checksum = calculateChecksum(targetNodeId, fileId);
        String contentType = ContentTypeUtils.getContentType(upload.getFileName());
        
        // 创建文件元数据
        FileMetadata fileMetadata = FileMetadata.builder()
                .id(fileId)
                .fileName(upload.getFileName())
                .contentType(contentType)
                .fileSize(totalSize)
                .checksum(checksum)
                .storageNodeId(targetNodeId)
                .storagePath(targetEngine.getStoragePath(fileId))
                .metadata(upload.getMetadata())
                .status(FileStatus.COMMITTED)
                .build();

        metadataStore.save(fileMetadata);

        // 触发副本复制（与普通上传保持一致）
        if (replicationService != null) {
            FileUploadRequest uploadRequest = FileUploadRequest.builder()
                    .fileName(upload.getFileName())
                    .fileSize(totalSize)
                    .contentType(contentType)
                    .metadata(upload.getMetadata())
                    .build();
            replicationService.replicate(fileMetadata, null, uploadRequest);
        }

        // 删除所有分片
        for (String partPath : partPathsToDelete) {
            try {
                targetEngine.delete(partPath);
            } catch (Exception e) {
                log.warn("Failed to delete part: {}", partPath, e);
            }
        }

        multipartUploadStore.delete(uploadId);

        log.info("Multipart upload completed: {} -> {} ({} bytes, checksum: {})", 
                uploadId, fileId, totalSize, checksum);
        return fileId;
    }

    /**
     * 取消分块上传
     * 清理已上传的分块数据
     */
    @Override
    public void abortMultipartUpload(String uploadId) {
        MultipartUpload upload = multipartUploadStore.get(uploadId);
        if (upload == null) {
            return;
        }

        // 使用目标节点的存储引擎删除分片
        String targetNodeId = upload.getTargetNodeId() != null ? upload.getTargetNodeId() : nodeId;
        StorageEngine targetEngine = storageRouter.getEngine(targetNodeId);
        
        // 删除所有分块
        for (PartInfo part : upload.getParts().values()) {
            try {
                targetEngine.delete(part.getStoragePath());
            } catch (Exception e) {
                log.warn("Failed to delete part: {}", part.getStoragePath(), e);
            }
        }

        multipartUploadStore.delete(uploadId);
        log.info("Multipart upload aborted: {}", uploadId);
    }

    /**
     * 获取分块上传会话信息
     * 
     * <p>返回完整的上传会话信息，包括已上传的分片列表。
     * 用于断点续传场景，前端可查询已上传的分片避免重复上传。</p>
     * 
     * <p>会话信息存储在 MultipartUploadStore（Redis 或本地内存）中，
     * 任何节点都可以直接查询，无需转发到目标节点。
     * 这与 uploadPart/completeMultipartUpload 不同，后者需要操作物理文件所以必须转发。</p>
     */
    @Override
    public MultipartUpload getMultipartUpload(String uploadId) {
        MultipartUpload upload = multipartUploadStore.get(uploadId);
        if (upload == null) {
            return null;
        }

        // 检查是否过期（Redis 模式下 TTL 会自动清理，但本地模式需要手动检查）
        if (upload.isExpired()) {
            log.warn("Upload session expired: {}", uploadId);
            return null;
        }

        return upload;
    }

    /**
     * 清理过期的分块上传会话
     * 
     * <p>定时任务，每小时执行一次，清理以下内容：</p>
     * <ul>
     *   <li>过期的分块上传会话（超过24小时未完成）</li>
     *   <li>已上传的物理分块文件</li>
     * </ul>
     * 
     * <p>清理流程：</p>
     * <ol>
     *   <li>从存储中获取所有过期的会话ID</li>
     *   <li>逐个处理：删除物理分块文件</li>
     *   <li>移除会话记录</li>
     * </ol>
     * 
     * <p>注意：对于 Redis 存储，会话通过 TTL 自动过期，此方法主要用于清理遗留的物理文件。</p>
     */
    private void cleanupExpiredMultipartUploads() {
        int cleanedCount = 0;
        int cleanedParts = 0;

        try {
            List<String> expiredUploadIds = multipartUploadStore.getExpiredUploadIds();
            
            for (String uploadId : expiredUploadIds) {
                MultipartUpload upload = multipartUploadStore.get(uploadId);
                if (upload == null) {
                    continue;
                }
                
                // 只清理本地节点上的分片（分布式环境下每个节点只清理自己的分片）
                String targetNodeId = upload.getTargetNodeId() != null ? upload.getTargetNodeId() : nodeId;
                if (!nodeId.equals(targetNodeId)) {
                    // 跳过其他节点的会话，让目标节点自己清理
                    continue;
                }
                
                // 删除物理分块文件
                StorageEngine localEngine = storageRouter.getEngine(nodeId);
                for (PartInfo part : upload.getParts().values()) {
                    try {
                        localEngine.delete(part.getStoragePath());
                        cleanedParts++;
                    } catch (Exception e) {
                        log.warn("Failed to delete expired part: {}", part.getStoragePath(), e);
                    }
                }
                
                // 移除会话记录
                multipartUploadStore.delete(uploadId);
                cleanedCount++;
                
                log.info("Cleaned up expired multipart upload: {}, parts: {}", uploadId, upload.getParts().size());
            }
            
            if (cleanedCount > 0) {
                log.info("Multipart upload cleanup completed: {} sessions, {} parts cleaned", cleanedCount, cleanedParts);
            }
        } catch (Exception e) {
            log.error("Error during multipart upload cleanup", e);
        }
    }

    /**
     * 关闭文件客户端
     * 
     * <p>释放资源，包括：</p>
     * <ul>
     *   <li>停止分块上传清理调度器</li>
     *   <li>清理所有未完成的分块上传</li>
     * </ul>
     */
    public void shutdown() {
        // 停止清理调度器
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 清理所有未完成的分块上传（从存储中获取过期的会话ID）
        for (String uploadId : multipartUploadStore.getExpiredUploadIds()) {
            try {
                abortMultipartUpload(uploadId);
            } catch (Exception e) {
                log.warn("Failed to abort multipart upload during shutdown: {}", uploadId, e);
            }
        }
        
        log.info("FileClient shutdown completed");
    }

    /**
     * 清理分块上传的物理分块文件
     * 
     * @param upload 分块上传会话
     */
    private void cleanupMultipartUploadParts(MultipartUpload upload) {
        if (upload == null || upload.getParts() == null || upload.getParts().isEmpty()) {
            return;
        }
        
        // 使用目标节点的存储引擎删除分片
        String targetNodeId = upload.getTargetNodeId() != null ? upload.getTargetNodeId() : nodeId;
        StorageEngine targetEngine = storageRouter.getEngine(targetNodeId);
        
        for (PartInfo part : upload.getParts().values()) {
            if (part.getStoragePath() != null) {
                try {
                    targetEngine.delete(part.getStoragePath());
                    log.debug("Deleted expired part: {}", part.getStoragePath());
                } catch (Exception e) {
                    log.warn("Failed to delete expired part: {}", part.getStoragePath(), e);
                }
            }
        }
    }

    // ==================== 私有方法 ====================

    private String selectTargetNodeId(FileUploadRequest request) {
        if (serviceRegistry == null || nodeSelector == null) {
            return nodeId;
        }
        try {
            StorageNode selected = nodeSelector.select(serviceRegistry.discover(), request);
            if (selected != null && selected.getId() != null) {
                return selected.getId();
            }
        } catch (Exception ignored) {
            // 回退到本地节点
        }
        return nodeId;
    }

    /**
     * 验证文件是否存在且未删除
     */
    private void validateFileExists(String fileId) {
        FileMetadata metadata = metadataStore.get(fileId);
        if (metadata == null) {
            throw new RuntimeException("File not found: " + fileId);
        }
        if (metadata.getStatus() == FileStatus.DELETED) {
            throw new RuntimeException("File has been deleted: " + fileId);
        }
    }

    /**
     * 从路径中提取文件名
     */
    private String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "unnamed";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    /**
     * 计算文件的MD5校验和
     * 流式计算，避免内存溢出
     * 
     * @param fileId 文件ID
     * @return MD5十六进制字符串
     */
    private String calculateChecksum(String nodeId, String fileId) {
        try (InputStream in = storageRouter.getEngine(nodeId).read(fileId)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(in, md);

            // 读取整个文件以计算校验和
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // 只读取，不保存数据
            }

            byte[] digest = md.digest();
            return ChecksumUtils.toHexString(digest);
        } catch (Exception e) {
            log.warn("Failed to calculate checksum for file: {}", fileId, e);
            return null;
        }
    }

    // ==================== 缩略图生成 ====================

    /**
     * 异步生成缩略图
     * 
     * <p>判断是否需要生成缩略图，如果需要则异步生成。</p>
     * 
     * <p>判断逻辑：</p>
     * <ol>
     *   <li>检查是否为图片类型（contentType 以 "image/" 开头）</li>
     *   <li>检查上传选项中的 generateThumbnail 参数</li>
     *   <li>如果未指定，使用配置文件中的默认设置</li>
     * </ol>
     * 
     * @param fileId 文件ID
     * @param contentType 内容类型
     * @param options 上传选项
     */
    private void generateThumbnailAsync(String fileId, String contentType, UploadOptions options) {
        // 1. 检查缩略图服务是否可用
        if (thumbnailService == null) {
            return;
        }

        // 2. 检查是否为图片类型
        if (!isImageType(contentType)) {
            log.debug("非图片类型，跳过缩略图生成: fileId={}, contentType={}", fileId, contentType);
            return;
        }

        // 3. 判断是否需要生成缩略图
        if (!shouldGenerateThumbnail(options)) {
            log.debug("缩略图生成已禁用: fileId={}", fileId);
            return;
        }

        // 4. 获取缩略图尺寸
        int maxEdge = getThumbnailMaxEdge(options);
        log.info("开始异步生成缩略图: fileId={}, maxEdge={}", fileId, maxEdge);

        // 5. 异步生成缩略图
        thumbnailService.generateAsync(fileId, maxEdge, thumbnailId -> {
            if (thumbnailId != null) {
                log.info("缩略图生成成功: fileId={}, thumbnailId={}", fileId, thumbnailId);
            }
        });
    }

    /**
     * 判断是否需要生成缩略图
     * 
     * @param options 上传选项
     * @return 是否需要生成缩略图
     */
    private boolean shouldGenerateThumbnail(UploadOptions options) {
        // 如果选项中明确指定了是否生成
        if (options != null && options.getGenerateThumbnail() != null) {
            return options.getGenerateThumbnail();
        }
        // 使用配置文件中的默认设置
        return thumbnailConfig.isEnabled();
    }

    /**
     * 获取缩略图的最长边尺寸
     * 
     * @param options 上传选项
     * @return 最长边尺寸（像素）
     */
    private int getThumbnailMaxEdge(UploadOptions options) {
        String sizeName = null;
        
        // 从选项中获取尺寸名称
        if (options != null && options.getThumbnailSize() != null) {
            sizeName = options.getThumbnailSize();
        }
        
        // 使用默认尺寸
        if (sizeName == null || sizeName.isEmpty()) {
            sizeName = thumbnailConfig.getDefaultSize();
        }

        // 查找尺寸配置
        ThumbnailSize size = thumbnailConfig.getSizes().get(sizeName);
        if (size != null) {
            return size.getMaxEdge();
        }

        // 默认返回 200px
        return 200;
    }

    /**
     * 检查是否为图片类型
     * 
     * @param contentType 内容类型
     * @return 是否为图片类型
     */
    private boolean isImageType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.startsWith("image/");
    }

    // ==================== 内部转发方法 ====================

    /**
     * 转发分片上传请求到目标节点
     *
     * <p>当请求被路由到错误节点时，自动转发到正确的目标节点。</p>
     *
     * @param targetNodeId 目标节点ID
     * @param uploadId 上传会话ID
     * @param partNumber 分片序号
     * @param inputStream 分片数据
     * @return 分片ETag
     */
    private String forwardUploadPart(String targetNodeId, String uploadId, int partNumber, InputStream inputStream) {
        if (serviceRegistry == null) {
            throw new RuntimeException("Cannot forward request: ServiceRegistry not available");
        }

        StorageNode targetNode = serviceRegistry.get(targetNodeId);
        if (targetNode == null) {
            throw new RuntimeException("Cannot forward request: Target node not found: " + targetNodeId);
        }

        String url = String.format("http://%s:%d/internal/multipart/%s/part/%d",
            targetNode.getHost(), targetNode.getPort(), uploadId, partNumber);

        log.info("Forwarding upload part to target node: {}", url);

        try {
            // 获取超时配置
            int connectTimeout = getConnectTimeout();
            int readTimeout = getReadTimeout();

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("X-Internal-Request", "true");

            // 发送分片数据
            byte[] data = IoUtils.toByteArray(inputStream);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                // 解析响应获取ETag
                String response = readResponseBody(conn);
                String eTag = parseETagFromResponse(response);
                log.info("Forward upload part succeeded: uploadId={}, part={}, eTag={}", uploadId, partNumber, eTag);
                return eTag;
            } else {
                String errorMsg = readErrorBody(conn);
                throw new RuntimeException("Forward upload part failed: " + responseCode + " - " + errorMsg);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to forward upload part to node: " + targetNodeId, e);
        }
    }

    /**
     * 转发完成上传请求到目标节点
     *
     * <p>当请求被路由到错误节点时，自动转发到正确的目标节点。</p>
     *
     * @param targetNodeId 目标节点ID
     * @param uploadId 上传会话ID
     * @param parts 分片信息列表
     * @return 生成的文件ID
     */
    private String forwardCompleteMultipartUpload(String targetNodeId, String uploadId, List<PartInfo> parts) {
        if (serviceRegistry == null) {
            throw new RuntimeException("Cannot forward request: ServiceRegistry not available");
        }

        StorageNode targetNode = serviceRegistry.get(targetNodeId);
        if (targetNode == null) {
            throw new RuntimeException("Cannot forward request: Target node not found: " + targetNodeId);
        }

        String url = String.format("http://%s:%d/internal/multipart/%s/complete",
            targetNode.getHost(), targetNode.getPort(), uploadId);

        log.info("Forwarding complete multipart upload to target node: {}", url);

        try {
            int connectTimeout = getConnectTimeout();
            int readTimeout = getReadTimeout();

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Internal-Request", "true");

            // 构建请求体
            StringBuilder jsonBody = new StringBuilder("{\"parts\":[");
            for (int i = 0; i < parts.size(); i++) {
                PartInfo part = parts.get(i);
                if (i > 0) jsonBody.append(",");
                jsonBody.append(String.format(
                    "{\"partNumber\":%d,\"eTag\":\"%s\",\"size\":%d,\"storagePath\":\"%s\",\"storageNodeId\":\"%s\"}",
                    part.getPartNumber(), part.getETag(), part.getSize(),
                    escapeJson(part.getStoragePath()), escapeJson(part.getStorageNodeId())
                ));
            }
            jsonBody.append("]}");

            log.info("Forward complete multipart upload body: {}", jsonBody.toString());

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                String response = readResponseBody(conn);
                String fileId = parseFileIdFromResponse(response);
                log.info("Forward complete multipart upload succeeded: uploadId={} -> fileId={}", uploadId, fileId);
                return fileId;
            } else {
                String errorMsg = readErrorBody(conn);
                throw new RuntimeException("Forward complete multipart upload failed: " + responseCode + " - " + errorMsg);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to forward complete multipart upload to node: " + targetNodeId, e);
        }
    }

    /**
     * 获取连接超时配置
     */
    private int getConnectTimeout() {
        // 默认5秒
        return 5000;
    }

    /**
     * 获取读取超时配置
     */
    private int getReadTimeout() {
        // 默认30秒
        return 30000;
    }

    /**
     * 读取响应体
     */
    private String readResponseBody(java.net.HttpURLConnection conn) throws IOException {
        try (java.io.InputStream is = conn.getInputStream();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * 读取错误响应体
     */
    private String readErrorBody(java.net.HttpURLConnection conn) {
        try (java.io.InputStream is = conn.getErrorStream();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            if (is == null) return "No error message";
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Failed to read error: " + e.getMessage();
        }
    }

    /**
     * 从响应中解析ETag
     */
    private String parseETagFromResponse(String response) {
        int eTagIndex = response.indexOf("\"eTag\"");
        if (eTagIndex == -1) {
            return null;
        }
        int valueStart = response.indexOf("\"", eTagIndex + 7) + 1;
        int valueEnd = response.indexOf("\"", valueStart);
        if (valueStart > 0 && valueEnd > valueStart) {
            return response.substring(valueStart, valueEnd);
        }
        return null;
    }

    /**
     * 从响应中解析文件ID
     */
    private String parseFileIdFromResponse(String response) {
        int fileIdIndex = response.indexOf("\"fileId\"");
        if (fileIdIndex == -1) {
            return null;
        }
        int valueStart = response.indexOf("\"", fileIdIndex + 9) + 1;
        int valueEnd = response.indexOf("\"", valueStart);
        if (valueStart > 0 && valueEnd > valueStart) {
            return response.substring(valueStart, valueEnd);
        }
        return null;
    }

    /**
     * 转义JSON字符串
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 缩略图配置类
     * 
     * <p>用于存储缩略图相关的配置信息。</p>
     */
    public static class ThumbnailConfig {
        
        /** 是否启用缩略图生成 */
        private boolean enabled = false;
        
        /** 默认缩略图尺寸名称 */
        private String defaultSize = "small";
        
        /** 预定义尺寸列表 */
        private Map<String, ThumbnailSize> sizes = new HashMap<>();

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
        public ThumbnailConfig() {
            sizes.put("small", new ThumbnailSize(200));
            sizes.put("medium", new ThumbnailSize(400));
            sizes.put("large", new ThumbnailSize(800));
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

        public Map<String, ThumbnailSize> getSizes() {
            return sizes;
        }

        public void setSizes(Map<String, ThumbnailSize> sizes) {
            this.sizes = sizes;
        }
    }
}
