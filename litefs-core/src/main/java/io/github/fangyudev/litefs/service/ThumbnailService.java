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

import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileStatus;
import io.github.fangyudev.litefs.spi.MetadataStore;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;
import io.github.fangyudev.litefs.util.ChecksumUtils;
import io.github.fangyudev.litefs.util.FileIdGeneratorUtil;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * 缩略图生成服务
 * 
 * <p>负责异步生成图片缩略图，采用长边优先策略保持宽高比。</p>
 * 
 * <h3>工作原理</h3>
 * <ol>
 *   <li>接收原图文件ID和目标尺寸</li>
 *   <li>从存储引擎读取原图</li>
 *   <li>使用 Thumbnailator 库按长边优先策略缩放</li>
 *   <li>保持原图格式保存缩略图</li>
 *   <li>更新原图元数据中的 thumbnailId 字段</li>
 * </ol>
 * 
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>异步处理</b>：缩略图生成在独立线程中执行，不阻塞主流程</li>
 *   <li><b>失败隔离</b>：缩略图生成失败不影响原图上传成功</li>
 *   <li><b>格式保持</b>：缩略图保持原图格式（JPEG/PNG/GIF 等）</li>
 *   <li><b>质量固定</b>：压缩质量固定为 0.85，平衡文件大小和画质</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>
 * // 异步生成缩略图
 * thumbnailService.generateAsync("file-123", 200, thumbnailId -> {
 *     log.info("缩略图生成完成: {}", thumbnailId);
 * });
 * 
 * // 在 FileClient 中使用
 * if (shouldGenerateThumbnail(contentType, options)) {
 *     thumbnailService.generateAsync(fileId, getThumbnailSize(options), 
 *         thumbnailId -> updateThumbnailId(fileId, thumbnailId));
 * }
 * </pre>
 * 
 * @see io.github.fangyudev.litefs.model.ThumbnailSize
 * @see FileOperationExecutor
 */
public class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);

    /**
     * 缩略图压缩质量
     * 
     * <p>固定值 0.85，平衡文件大小和画质。</p>
     * <p>取值范围：0.0（最低质量）~ 1.0（最高质量）</p>
     */
    private static final float QUALITY = 0.85f;

    /**
     * 存储引擎路由器
     * <p>用于根据节点ID获取存储引擎，读取原图和保存缩略图</p>
     */
    private final StorageEngineRouter storageRouter;

    /**
     * 元数据存储
     * <p>用于更新原图的 thumbnailId 字段</p>
     */
    private final MetadataStore metadataStore;

    /**
     * 异步执行器
     * <p>用于异步执行缩略图生成任务</p>
     */
    private final FileOperationExecutor executor;

    /**
     * 构造函数
     * 
     * @param storageRouter 存储引擎路由器
     * @param metadataStore 元数据存储
     * @param executor 异步执行器
     */
    public ThumbnailService(StorageEngineRouter storageRouter, 
                            MetadataStore metadataStore,
                            FileOperationExecutor executor) {
        this.storageRouter = storageRouter;
        this.metadataStore = metadataStore;
        this.executor = executor;
    }

    /**
     * 异步生成缩略图
     * 
     * <p>在独立线程中执行缩略图生成，不阻塞调用线程。</p>
     * <p>生成完成后调用回调函数，传入缩略图文件ID。</p>
     * 
     * <p>注意：</p>
     * <ul>
     *   <li>如果生成失败，回调函数不会被调用</li>
     *   <li>失败信息会记录到日志</li>
     *   <li>失败不会影响原图上传成功</li>
     * </ul>
     * 
     * @param sourceFileId 原图文件ID
     * @param maxEdge 最长边长度（像素）
     * @param callback 生成完成回调（可选，可为 null）
     */
    public void generateAsync(String sourceFileId, int maxEdge, Consumer<String> callback) {
        log.info("提交缩略图生成任务: sourceFileId={}, maxEdge={}", sourceFileId, maxEdge);

        executor.submit(() -> {
            try {
                String thumbnailId = generateSync(sourceFileId, maxEdge);
                if (thumbnailId != null && callback != null) {
                    callback.accept(thumbnailId);
                }
            } catch (Exception e) {
                log.error("缩略图生成失败: sourceFileId={}, maxEdge={}, error={}", 
                    sourceFileId, maxEdge, e.getMessage(), e);
            }
        });
    }

    /**
     * 同步生成缩略图（内部方法）
     * 
     * <p>实际执行缩略图生成的方法，在异步线程中调用。</p>
     * 
     * @param sourceFileId 原图文件ID
     * @param maxEdge 最长边长度（像素）
     * @return 缩略图文件ID，失败返回 null
     */
    private String generateSync(String sourceFileId, int maxEdge) {
        log.info("开始生成缩略图: sourceFileId={}, maxEdge={}", sourceFileId, maxEdge);

        try {
            // 1. 获取原图元数据
            FileMetadata sourceMetadata = metadataStore.get(sourceFileId);
            if (sourceMetadata == null) {
                log.warn("原图元数据不存在: {}", sourceFileId);
                return null;
            }

            // 2. 检查是否为图片类型
            String contentType = sourceMetadata.getContentType();
            if (!isImageType(contentType)) {
                log.info("非图片类型，跳过缩略图生成: fileId={}, contentType={}", 
                    sourceFileId, contentType);
                return null;
            }

            // 3. 获取原图所在节点的存储引擎
            String storageNodeId = sourceMetadata.getStorageNodeId();
            StorageEngine storageEngine = storageRouter.getEngine(storageNodeId);

            // 4. 读取原图并生成缩略图
            String thumbnailId = FileIdGeneratorUtil.generate();
            byte[] thumbnailData;

            try (InputStream sourceStream = storageEngine.read(sourceFileId)) {
                if (sourceStream == null) {
                    log.warn("原图不存在: {}", sourceFileId);
                    return null;
                }

                // 5. 生成缩略图
                thumbnailData = createThumbnail(sourceStream, maxEdge, contentType);
            }

            // 6. 保存缩略图（保存到与原图相同的节点）
            InputStream thumbnailStream = new ByteArrayInputStream(thumbnailData);
            storageEngine.write(thumbnailId, thumbnailStream);

            // 7. 计算缩略图校验和
            String checksum = ChecksumUtils.md5Hex(thumbnailData);

            // 8. 保存缩略图元数据（继承原图的权限设置）
            FileMetadata thumbnailMetadata = FileMetadata.builder()
                .id(thumbnailId)
                .fileName("thumb_" + sourceMetadata.getFileName())
                .contentType(contentType)
                .fileSize(thumbnailData.length)
                .checksum(checksum)
                .storageNodeId(storageNodeId)
                .storagePath(storageEngine.getStoragePath(thumbnailId))
                .status(FileStatus.COMMITTED)
                .visibility(sourceMetadata.getVisibility())
                .expireTime(sourceMetadata.getExpireTime())
                .build();
            metadataStore.save(thumbnailMetadata);

            // 7. 更新原图的 thumbnailId
            sourceMetadata.setThumbnailId(thumbnailId);
            sourceMetadata.setUpdateTime(System.currentTimeMillis());
            metadataStore.update(sourceMetadata);

            log.info("缩略图生成完成: sourceFileId={}, thumbnailId={}, size={}bytes", 
                sourceFileId, thumbnailId, thumbnailData.length);

            return thumbnailId;

        } catch (Exception e) {
            log.error("缩略图生成异常: sourceFileId={}, error={}", 
                sourceFileId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建缩略图数据
     * 
     * <p>使用 Thumbnailator 库进行图片缩放，保持宽高比。</p>
     * 
     * @param sourceStream 原图输入流
     * @param maxEdge 最长边长度
     * @param contentType 内容类型
     * @return 缩略图字节数组
     * @throws Exception 缩放失败时抛出
     */
    private byte[] createThumbnail(InputStream sourceStream, int maxEdge, String contentType) 
            throws Exception {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // 使用 Thumbnailator 进行缩放
        // size(maxEdge, maxEdge) 表示最长边不超过 maxEdge，自动保持宽高比
        Thumbnails.of(sourceStream)
            .size(maxEdge, maxEdge)
            .outputQuality(QUALITY)
            .outputFormat(getOutputFormat(contentType))
            .toOutputStream(outputStream);

        return outputStream.toByteArray();
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

    /**
     * 获取输出格式
     * 
     * <p>根据内容类型确定缩略图格式，保持原图格式。</p>
     * 
     * @param contentType 内容类型
     * @return 输出格式（如 jpg、png、gif）
     */
    private String getOutputFormat(String contentType) {
        if (contentType == null) {
            return "jpg";
        }

        // 提取格式部分，如 "image/jpeg" -> "jpeg"
        String format = contentType.toLowerCase();
        if (format.contains("/")) {
            format = format.substring(format.indexOf("/") + 1);
        }

        // 标准化格式名称
        switch (format) {
            case "jpeg":
            case "jpg":
                return "jpg";
            case "png":
                return "png";
            case "gif":
                return "gif";
            case "webp":
                return "webp";
            case "bmp":
                return "bmp";
            default:
                return "jpg";
        }
    }
}
