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

package io.github.fangyudev.litefs.controller;

import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.MultipartUpload;
import io.github.fangyudev.litefs.model.PartInfo;
import io.github.fangyudev.litefs.spi.MetadataStore;
import io.github.fangyudev.litefs.spi.MultipartUploadStore;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.util.ChecksumUtils;
import io.github.fangyudev.litefs.util.ContentTypeUtils;
import io.github.fangyudev.litefs.util.FileIdGeneratorUtil;
import io.github.fangyudev.litefs.util.IoUtils;
import io.github.fangyudev.litefs.model.FileStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 存储节点内部 API 控制器
 * 
 * <p>提供内部 API 供其他节点通过 HTTP 方式访问本节点的存储引擎。
 * 这是分布式存储跨节点访问的关键组件。</p>
 * 
 * <h3>API 端点：</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>/internal/files/{fileId}</td><td>读取文件</td></tr>
 *   <tr><td>POST</td><td>/internal/files/{fileId}</td><td>写入文件</td></tr>
 *   <tr><td>DELETE</td><td>/internal/files/{fileId}</td><td>删除文件</td></tr>
 *   <tr><td>HEAD</td><td>/internal/files/{fileId}</td><td>检查文件是否存在</td></tr>
 * </table>
 * 
 * <h3>安全考虑：</h3>
 * <ul>
 *   <li>内部 API 应该只允许内网访问</li>
 *   <li>建议通过网关或防火墙限制访问</li>
 *   <li>请求头中包含 X-Internal-Request 标识</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 其他节点通过 HTTP 访问
 * GET http://node-host:port/internal/files/abc123
 * 
 * // 响应
 * Content-Type: application/octet-stream
 * Content-Length: 1024
 * 
 * [文件二进制数据]
 * }</pre>
 * 
 * @see io.github.fangyudev.litefs.remote.HttpRemoteStorageEngine
 */
@RestController
@RequestMapping("/internal")
public class InternalStorageController {

    private static final Logger log = LoggerFactory.getLogger(InternalStorageController.class);

    /** 存储引擎 */
    private final StorageEngine storageEngine;

    /** 分片上传会话存储 */
    private final MultipartUploadStore multipartUploadStore;

    /** 元数据存储 */
    private final MetadataStore metadataStore;

    /** 当前节点ID */
    private final String nodeId;

    /**
     * 构造函数
     *
     * @param storageEngine 存储引擎
     * @param multipartUploadStore 分片上传会话存储（可为null，仅文件操作时使用）
     * @param metadataStore 元数据存储（可为null，仅分片上传完成时使用）
     * @param nodeId 当前节点ID
     */
    public InternalStorageController(StorageEngine storageEngine,
                                     MultipartUploadStore multipartUploadStore,
                                     MetadataStore metadataStore,
                                     String nodeId) {
        this.storageEngine = storageEngine;
        this.multipartUploadStore = multipartUploadStore;
        this.metadataStore = metadataStore;
        this.nodeId = nodeId;
    }

    /**
     * 简化构造函数（仅文件操作）
     *
     * @param storageEngine 存储引擎
     */
    public InternalStorageController(StorageEngine storageEngine) {
        this(storageEngine, null, null, null);
    }

    // ==================== 文件操作端点 ====================

    /**
     * 读取文件
     *
     * <p>使用流式响应，适合大文件传输。通过直接写入 HttpServletResponse
     * 的 OutputStream 实现同步流式传输，避免异步问题。</p>
     *
     * @param fileId 文件ID
     * @param request HTTP 请求
     * @param response HTTP 响应
     */
    @GetMapping("/files/{fileId}")
    public void readFile(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.info("Internal read request for file: {} from {}", fileId, request.getRemoteAddr());
        
        boolean exists = storageEngine.exists(fileId);
        log.info("File {} exists: {}", fileId, exists);
        
        if (!exists) {
            log.warn("File not found: {}", fileId);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try (InputStream input = storageEngine.read(fileId)) {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileId + "\"");
            
            long fileSize = storageEngine.getSize(fileId);
            if (fileSize > 0) {
                response.setContentLengthLong(fileSize);
            }
            
            // 同步流式写入，避免异步问题
            try (java.io.OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
            
            log.info("File read successfully: {} ({} bytes)", fileId, fileSize > 0 ? fileSize : "unknown");
        } catch (Exception e) {
            log.error("Failed to read file: {}", fileId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 写入文件
     *
     * <p>接收上传的文件数据并存储。</p>
     *
     * @param fileId 文件ID
     * @param request HTTP 请求
     * @return 写入结果，包含存储路径
     */
    @PostMapping("/files/{fileId}")
    public ResponseEntity<WriteResult> writeFile(
            @PathVariable String fileId,
            HttpServletRequest request) {
        
        log.info("Internal write request for file: {} from {}", fileId, request.getRemoteAddr());
        
        try (InputStream data = request.getInputStream()) {
            String storagePath = storageEngine.write(fileId, data);
            log.info("File written successfully: {} -> {}", fileId, storagePath);
            return ResponseEntity.ok(new WriteResult(storagePath));
        } catch (Exception e) {
            log.error("Failed to write file: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 写入结果
     */
    public record WriteResult(String storagePath) {}

    /**
     * 删除文件
     *
     * @param fileId 文件ID
     * @param request HTTP 请求
     * @return 删除结果
     */
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String fileId,
            HttpServletRequest request) {
        
        log.debug("Internal delete request for file: {} from {}", fileId, request.getRemoteAddr());
        
        try {
            storageEngine.delete(fileId);
            log.debug("File deleted successfully: {}", fileId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete file: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 检查文件是否存在
     *
     * <p>HEAD 请求只返回状态码，不返回文件内容。</p>
     *
     * @param fileId 文件ID
     * @param request HTTP 请求
     * @return 200 表示存在，404 表示不存在
     */
    @RequestMapping(value = "/files/{fileId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(
            @PathVariable String fileId,
            HttpServletRequest request) {
        
        log.debug("Internal HEAD request for file: {} from {}", fileId, request.getRemoteAddr());
        
        if (!storageEngine.exists(fileId)) {
            return ResponseEntity.notFound().build();
        }

        long fileSize = storageEngine.getSize(fileId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        if (fileSize > 0) {
            headers.setContentLength(fileSize);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .build();
    }

    // ==================== 分片上传端点 ====================

    /**
     * 获取分片上传会话信息（内部转发调用）
     *
     * <p>返回上传会话信息，包括已上传的分片列表。用于断点续传场景。</p>
     *
     * @param uploadId 上传会话ID
     * @return 上传会话信息
     */
    @GetMapping("/multipart/{uploadId}")
    public ResponseEntity<MultipartUpload> getMultipartUpload(
            @PathVariable String uploadId,
            HttpServletRequest request) {

        log.info("Internal multipart get: uploadId={}, from={}", uploadId, request.getRemoteAddr());

        if (multipartUploadStore == null) {
            log.error("MultipartUploadStore not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        MultipartUpload upload = multipartUploadStore.get(uploadId);
        if (upload == null) {
            log.warn("Upload session not found: {}", uploadId);
            return ResponseEntity.notFound().build();
        }

        // 检查是否过期
        if (upload.isExpired()) {
            log.warn("Upload session expired: {}", uploadId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(upload);
    }

    /**
     * 上传分片（内部转发调用）
     *
     * <p>当请求被路由到错误节点时，目标节点通过此接口接收转发的分片数据。</p>
     *
     * @param uploadId 上传会话ID
     * @param partNumber 分片序号
     * @param request HTTP 请求
     * @return 分片ETag
     */
    @PostMapping("/multipart/{uploadId}/part/{partNumber}")
    public ResponseEntity<PartUploadResult> uploadPart(
            @PathVariable String uploadId,
            @PathVariable int partNumber,
            HttpServletRequest request) {

        log.info("Internal multipart upload: uploadId={}, partNumber={}, from={}",
            uploadId, partNumber, request.getRemoteAddr());

        if (multipartUploadStore == null) {
            log.error("MultipartUploadStore not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        MultipartUpload upload = multipartUploadStore.get(uploadId);
        if (upload == null) {
            log.warn("Upload session not found: {}", uploadId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new PartUploadResult("Upload session not found"));
        }

        // 检查是否过期
        if (upload.isExpired()) {
            log.warn("Upload session expired: {}", uploadId);
            return ResponseEntity.status(HttpStatus.GONE)
                .body(new PartUploadResult("Upload session expired"));
        }

        try {
            // 读取分片数据
            byte[] data = IoUtils.toByteArray(request.getInputStream());
            String eTag = ChecksumUtils.md5Hex(data);

            // 存储分片到本地存储引擎
            String partId = uploadId + "_part_" + partNumber;
            String storagePath = storageEngine.write(partId, new ByteArrayInputStream(data));

            // 记录分片信息
            PartInfo partInfo = PartInfo.builder()
                .partNumber(partNumber)
                .eTag(eTag)
                .size(data.length)
                .storagePath(storagePath)
                .storageNodeId(nodeId)
                .build();

            // 更新会话中的分片信息
            boolean added = multipartUploadStore.addPart(uploadId, partNumber, partInfo);
            if (!added) {
                throw new RuntimeException("Failed to add part to upload session: " + uploadId);
            }

            log.info("Part uploaded via internal API: uploadId={}, part={}, size={}, eTag={}",
                uploadId, partNumber, data.length, eTag);

            return ResponseEntity.ok(new PartUploadResult(eTag));
        } catch (IOException e) {
            log.error("Failed to upload part: uploadId={}, partNumber={}", uploadId, partNumber, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new PartUploadResult("Failed to upload part: " + e.getMessage()));
        }
    }

    /**
     * 完成分片上传（内部转发调用）
     *
     * <p>当请求被路由到错误节点时，目标节点通过此接口完成文件合并。</p>
     *
     * @param uploadId 上传会话ID
     * @param request HTTP 请求
     * @return 生成的文件ID
     */
    @PostMapping("/multipart/{uploadId}/complete")
    public ResponseEntity<CompleteUploadResult> completeMultipartUpload(
            @PathVariable String uploadId,
            @RequestBody CompleteUploadRequest completeRequest,
            HttpServletRequest request) {

        log.info("Internal multipart complete: uploadId={}, parts={}, from={}",
            uploadId, completeRequest.getParts().size(), request.getRemoteAddr());

        if (multipartUploadStore == null || metadataStore == null) {
            log.error("MultipartUploadStore or MetadataStore not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        MultipartUpload upload = multipartUploadStore.get(uploadId);
        log.info("upload:{}", upload);
        if (upload == null) {
            log.warn("Upload session not found: {}", uploadId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new CompleteUploadResult(null, "Upload session not found"));
        }

        List<PartInfo> parts = completeRequest.getParts();
        log.info("parts to string {}", parts.toString());
        // 验证所有分片都已上传
        for (PartInfo part : parts) {
            log.info("part ETag: {}",part.getETag());
            PartInfo storedPart = upload.getParts().get(part.getPartNumber());
            log.info("stored part ETag: {}",storedPart.getETag());
            if (storedPart == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new CompleteUploadResult(null, "Part not found: " + part.getPartNumber()));
            }
            if (!storedPart.getETag().equals(part.getETag())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new CompleteUploadResult(null, "Part ETag mismatch: " + part.getPartNumber()));
            }
        }

        // 按partNumber排序
        List<PartInfo> sortedParts = parts.stream()
            .sorted(Comparator.comparingInt(PartInfo::getPartNumber))
            .collect(Collectors.toList());

        // 生成文件ID并合并分片
        String fileId = FileIdGeneratorUtil.generate(upload.getFileName());
        long totalSize = 0;
        List<String> partPathsToDelete = new ArrayList<>();

        try {
            // 流式追加写入
            for (PartInfo part : sortedParts) {
                PartInfo storedPart = upload.getParts().get(part.getPartNumber());
                try (InputStream partStream = storageEngine.read(storedPart.getStoragePath())) {
                    long newSize = storageEngine.append(fileId, partStream);
                    totalSize = newSize;
                    partPathsToDelete.add(storedPart.getStoragePath());
                }
            }
        } catch (IOException e) {
            // 合并失败，清理已创建的文件
            try {
                storageEngine.delete(fileId);
            } catch (Exception ignored) {}
            log.error("Failed to merge parts: uploadId={}", uploadId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new CompleteUploadResult(null, "Failed to merge parts: " + e.getMessage()));
        }

        // 计算checksum
        String checksum = calculateChecksum(fileId);
        String contentType = ContentTypeUtils.getContentType(upload.getFileName());

        // 创建文件元数据
        FileMetadata fileMetadata = FileMetadata.builder()
            .id(fileId)
            .fileName(upload.getFileName())
            .contentType(contentType)
            .fileSize(totalSize)
            .checksum(checksum)
            .storageNodeId(nodeId)
            .storagePath(storageEngine.getStoragePath(fileId))
            .metadata(upload.getMetadata())
            .status(FileStatus.COMMITTED)
            .build();

        metadataStore.save(fileMetadata);

        // 删除所有分片
        for (String partPath : partPathsToDelete) {
            try {
                storageEngine.delete(partPath);
            } catch (Exception e) {
                log.warn("Failed to delete part: {}", partPath, e);
            }
        }

        // 删除上传会话
        multipartUploadStore.delete(uploadId);

        log.info("Multipart upload completed via internal API: uploadId={} -> fileId={}, size={}",
            uploadId, fileId, totalSize);

        return ResponseEntity.ok(new CompleteUploadResult(fileId, null));
    }

    /**
     * 取消分片上传（内部转发调用）
     *
     * @param uploadId 上传会话ID
     * @param request HTTP 请求
     * @return 操作结果
     */
    @DeleteMapping("/multipart/{uploadId}")
    public ResponseEntity<Void> abortMultipartUpload(
            @PathVariable String uploadId,
            HttpServletRequest request) {

        log.info("Internal multipart abort: uploadId={}, from={}", uploadId, request.getRemoteAddr());

        if (multipartUploadStore == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        MultipartUpload upload = multipartUploadStore.get(uploadId);
        if (upload == null) {
            return ResponseEntity.notFound().build();
        }

        // 删除所有分片
        for (PartInfo part : upload.getParts().values()) {
            try {
                storageEngine.delete(part.getStoragePath());
            } catch (Exception e) {
                log.warn("Failed to delete part: {}", part.getStoragePath(), e);
            }
        }

        // 删除会话
        multipartUploadStore.delete(uploadId);

        log.info("Multipart upload aborted via internal API: {}", uploadId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 计算文件校验和
     */
    private String calculateChecksum(String fileId) {
        try (InputStream in = storageEngine.read(fileId)) {
            return ChecksumUtils.md5Hex(IoUtils.toByteArray(in));
        } catch (Exception e) {
            log.warn("Failed to calculate checksum for file: {}", fileId, e);
            return null;
        }
    }

    // ==================== 响应DTO ====================

    /**
     * 分片上传结果
     */
    public record PartUploadResult(String eTag) {}

    /**
     * 完成上传请求
     */
    public static class CompleteUploadRequest {
        private List<PartInfo> parts;

        public List<PartInfo> getParts() {
            return parts;
        }

        public void setParts(List<PartInfo> parts) {
            this.parts = parts;
        }
    }

    /**
     * 完成上传结果
     */
    public record CompleteUploadResult(String fileId, String error) {}
}
