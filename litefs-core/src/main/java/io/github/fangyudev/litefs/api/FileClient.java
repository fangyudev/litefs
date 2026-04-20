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

package io.github.fangyudev.litefs.api;

import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileQuery;
import io.github.fangyudev.litefs.model.InitMultipartUploadResult;
import io.github.fangyudev.litefs.model.MultipartUpload;
import io.github.fangyudev.litefs.model.PartInfo;
import io.github.fangyudev.litefs.model.UploadOptions;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文件客户端接口，提供文件的上传、下载、删除、URL生成等核心操作。
 */
public interface FileClient {

    // ==================== 文件操作 ====================

    /**
     * 上传文件（简单上传）。
     * 
     * <p><b>注意：</b>此方法会将整个文件读入内存，适合小文件（建议5MB以下）。
     * 大文件请使用分片上传API。</p>
     * 
     * @param inputStream 文件输入流
     * @param fileName 文件名
     * @param metadata 文件元数据，可为null
     * @return 文件ID
     */
    String upload(InputStream inputStream, String fileName, Map<String, String> metadata);

    /**
     * 上传文件（带选项）。
     * 
     * <p>使用示例：</p>
     * <pre>{@code
     * String fileId = fileClient.upload(inputStream, "photo.jpg", metadata,
     *     UploadOptions.builder()
     *         .generateThumbnail(true)
     *         .build());
     * }</pre>
     * 
     * @param inputStream 文件输入流
     * @param fileName 文件名
     * @param metadata 文件元数据，可为null
     * @param options 上传选项，可为null
     * @return 文件ID
     */
    String upload(InputStream inputStream, String fileName, Map<String, String> metadata, UploadOptions options);

    /**
     * 下载文件。
     * 
     * @param fileId 文件ID
     * @return 文件输入流，调用者负责关闭流
     * @throws RuntimeException 文件不存在或已删除时抛出
     */
    InputStream download(String fileId);

    /**
     * 删除文件（物理删除）。
     * 
     * <p>同时删除所有副本和缩略图。操作不可逆。</p>
     * 
     * @param fileId 文件ID
     * @throws RuntimeException 文件不存在时抛出
     */
    void delete(String fileId);

    /**
     * 复制文件，生成新的文件ID。
     * 
     * <p>新文件将遵循系统的副本策略，自动复制到其他节点。</p>
     * 
     * @param fileId 源文件ID
     * @return 新文件ID
     * @throws RuntimeException 源文件不存在或已删除时抛出
     */
    String copy(String fileId);

    /**
     * 复制文件，生成新的文件ID。
     * 
     * <p><b>copy 与 replica 的区别：</b></p>
     * <ul>
     *   <li>copy：创建独立的新文件（新fileId）</li>
     *   <li>replica：同一文件的冗余副本（相同fileId）</li>
     * </ul>
     * 
     * @param fileId 源文件ID
     * @param replicate 是否触发副本复制
     * @return 新文件ID
     * @throws RuntimeException 源文件不存在或已删除时抛出
     */
    String copy(String fileId, boolean replicate);

    /**
     * 重命名文件，只修改文件的显示名称。
     * 
     * <p>LiteFS 的物理存储路径基于 fileId 生成，与文件名无关。</p>
     * 
     * @param fileId 文件ID
     * @param newFileName 新文件名
     * @throws RuntimeException 文件不存在时抛出
     */
    void rename(String fileId, String newFileName);

    // ==================== 元数据操作 ====================

    /**
     * 获取文件元数据。
     * 
     * @param fileId 文件ID
     * @return 文件元数据，不存在则返回null
     */
    FileMetadata getMetadata(String fileId);

    /**
     * 更新文件元数据，将新的元数据合并到现有元数据中。
     * 
     * @param fileId 文件ID
     * @param metadata 要更新的元数据
     * @throws RuntimeException 文件不存在时抛出
     */
    void updateMetadata(String fileId, Map<String, String> metadata);

    /**
     * 查询文件列表。
     * 
     * @param query 查询条件
     * @return 文件元数据列表
     */
    List<FileMetadata> listFiles(FileQuery query);

    // ==================== URL操作 ====================

    /**
     * 获取文件访问URL（永久有效）。
     * 
     * @param fileId 文件ID
     * @return 访问URL
     * @throws RuntimeException 文件不存在或已删除时抛出
     */
    String getUrl(String fileId);

    /**
     * 获取文件访问URL（临时有效），带签名，过期后无法访问。
     * 
     * <p>URL过期校验需要在网关层面实现。</p>
     * 
     * @param fileId 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 带签名的访问URL
     * @throws RuntimeException 文件不存在或已删除时抛出
     */
    String getUrl(String fileId, long expireSeconds);

    /**
     * 获取文件下载URL，会触发浏览器下载行为。
     * 
     * @param fileId 文件ID
     * @return 下载URL
     * @throws RuntimeException 文件不存在或已删除时抛出
     */
    String getDownloadUrl(String fileId);

    // ==================== 缩略图操作 ====================

    /**
     * 获取缩略图访问URL。
     * 
     * <p>如果文件没有缩略图，返回 null。</p>
     * 
     * @param fileId 原图文件ID
     * @return 缩略图URL，如果没有缩略图则返回 null
     */
    String getThumbnailUrl(String fileId);

    /**
     * 下载缩略图。
     * 
     * <p>如果文件没有缩略图，返回 null。</p>
     * 
     * @param fileId 原图文件ID
     * @return 缩略图输入流，调用者负责关闭流
     */
    InputStream downloadThumbnail(String fileId);

    // ==================== 分块上传 ====================

    /**
     * 初始化分块上传。
     * 
     * <p><b>分布式环境：</b>返回结果包含目标节点ID，所有后续的分片上传和合并操作
     * 都必须路由到该目标节点执行。</p>
     * 
     * @param fileName 文件名
     * @param fileSize 文件总大小
     * @param metadata 文件元数据，可为null
     * @return 初始化结果，包含uploadId和targetNodeId
     */
    InitMultipartUploadResult initMultipartUpload(String fileName, long fileSize, Map<String, String> metadata);

    /**
     * 上传分块。
     * 
     * @param uploadId 上传ID
     * @param partNumber 分块序号，从1开始
     * @param inputStream 分块数据
     * @return 分块ETag，用于完成上传时校验
     * @throws RuntimeException 上传ID不存在时抛出
     */
    String uploadPart(String uploadId, int partNumber, InputStream inputStream);

    /**
     * 完成分块上传，将所有分块合并为完整文件。
     * 
     * @param uploadId 上传ID
     * @param parts 分块信息列表
     * @return 文件ID
     * @throws RuntimeException 上传ID不存在时抛出
     */
    String completeMultipartUpload(String uploadId, List<PartInfo> parts);

    /**
     * 取消分块上传，清理已上传的分块数据。
     * 
     * @param uploadId 上传ID
     */
    void abortMultipartUpload(String uploadId);

    /**
     * 获取分块上传会话信息，包括已上传的分片列表。
     * 
     * <p>用于断点续传场景：前端可查询已上传的分片，避免重复上传。
     * 也适用于前端刷新页面、跨设备续传等场景。</p>
     * 
     * <p><b>分布式环境：</b>会话信息存储在共享存储（Redis）中，
     * 任何节点都可以直接查询，无需转发到目标节点。
     * 这与 uploadPart/completeMultipartUpload 不同，后者需要操作物理文件所以必须转发。</p>
     * 
     * @param uploadId 上传ID
     * @return 上传会话信息，不存在或已过期则返回null
     */
    MultipartUpload getMultipartUpload(String uploadId);
}
