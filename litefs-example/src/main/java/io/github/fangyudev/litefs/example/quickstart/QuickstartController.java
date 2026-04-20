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

package io.github.fangyudev.litefs.example.quickstart;

import io.github.fangyudev.litefs.api.FileClient;
import io.github.fangyudev.litefs.api.UrlGenerator;
import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileQuery;
import io.github.fangyudev.litefs.model.FileStatus;
import io.github.fangyudev.litefs.model.FileVisibility;
import io.github.fangyudev.litefs.model.InitMultipartUploadResult;
import io.github.fangyudev.litefs.model.PartInfo;
import io.github.fangyudev.litefs.model.UploadOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件网关控制器 - 快速入门示例
 * 
 * <p>演示功能：</p>
 * <ul>
 *   <li>简单文件上传/下载</li>
 *   <li>分片上传（大文件）</li>
 *   <li>签名URL生成与验证</li>
 *   <li>文件元数据管理</li>
 *   <li>文件列表查询</li>
 * </ul>
 * 
 * @see <a href="doc/quickstart-guide.md">快速入门指南</a>
 */
@RestController
@RequestMapping("/api/files")
public class QuickstartController {

    @Autowired
    private FileClient fileClient;

    @Autowired
    private UrlGenerator urlGenerator;

    // ==================== 文件上传 ====================

    /**
     * 简单上传
     * 适合小文件（建议5MB以下）
     * 
     * @param file 上传的文件
     * @param category 文件分类（可选）
     * @param generateThumbnail 是否生成缩略图（可选，仅对图片类型生效）
     * @param thumbnailSize 缩略图尺寸（可选，可选值：small/medium/large，动态参数优先级大于配置文件）
     * @param visibility 文件访问权限（可选，可选值：private/public/temporary，默认 private）
     * @param expireSeconds 过期时间秒数（可选，仅 visibility=temporary 时有效）
     */
    @PostMapping
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "generateThumbnail", required = false) Boolean generateThumbnail,
            @RequestParam(name = "thumbnailSize", required = false) String thumbnailSize,
            @RequestParam(name = "visibility", required = false) String visibility,
            @RequestParam(name = "expireSeconds", required = false) Long expireSeconds) throws Exception {

        Map<String, String> metadata = new HashMap<>();
        if (category != null) {
            metadata.put("category", category);
        }

        // 构建上传选项
        UploadOptions.Builder optionsBuilder = UploadOptions.builder();
        
        if (generateThumbnail != null) {
            optionsBuilder.generateThumbnail(generateThumbnail);
        }
        if (thumbnailSize != null) {
            optionsBuilder.thumbnailSize(thumbnailSize);
        }
        
        // 处理 visibility
        if (visibility != null) {
            try {
                FileVisibility fileVisibility = FileVisibility.valueOf(visibility.toUpperCase());
                optionsBuilder.visibility(fileVisibility);
                
                // 如果是临时文件，设置过期时间
                if (fileVisibility == FileVisibility.TEMPORARY && expireSeconds != null) {
                    optionsBuilder.expireTime(System.currentTimeMillis() + expireSeconds * 1000);
                }
            } catch (IllegalArgumentException e) {
                // 忽略无效的 visibility 值
            }
        }

        String fileId = fileClient.upload(
                file.getInputStream(),
                file.getOriginalFilename(),
                metadata,
                optionsBuilder.build()
        );

        return ResponseEntity.ok(new UploadResponse(fileId, file.getOriginalFilename()));
    }

    // ==================== URL生成 ====================

    /**
     * 获取文件访问URL
     * 
     * @param fileId 文件ID
     * @param expire 过期时间秒数（可选，不传或传0则返回永久URL）
     * @param download 是否返回下载URL（可选，默认 false）
     */
    @GetMapping("/{fileId}/url")
    public ResponseEntity<UrlResponse> getUrl(
            @PathVariable("fileId") String fileId,
            @RequestParam(name = "expire", required = false) Long expire,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        
        FileMetadata metadata = fileClient.getMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }

        String url;
        long expireSeconds = -1;
        
        if (download) {
            url = fileClient.getDownloadUrl(fileId);
        } else if (expire != null && expire > 0) {
            url = fileClient.getUrl(fileId, expire);
            expireSeconds = expire;
        } else {
            url = fileClient.getUrl(fileId);
        }
        
        return ResponseEntity.ok(new UrlResponse(url, expireSeconds));
    }

    /**
     * 获取缩略图URL
     * 
     * @param fileId 原图文件ID
     */
    @GetMapping("/{fileId}/thumbnail-url")
    public ResponseEntity<UrlResponse> getThumbnailUrl(@PathVariable("fileId") String fileId) {
        
        String thumbnailUrl = fileClient.getThumbnailUrl(fileId);
        if (thumbnailUrl == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(new UrlResponse(thumbnailUrl, -1));
    }

    // ==================== 文件访问 ====================

    /**
     * 访问文件（支持签名校验）
     * 
     * <p>安全策略：</p>
     * <ul>
     *   <li>PUBLIC - 公开文件，直接访问</li>
     *   <li>TEMPORARY - 临时文件，有效期内公开访问，过期后需要签名</li>
     *   <li>PRIVATE - 私有文件，需要签名验证</li>
     * </ul>
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<StreamingResponseBody> accessFile(
            @PathVariable("fileId") String fileId,
            @RequestParam(name = "token", required = false) String token,
            @RequestParam(name = "expire", required = false) Long expire) {

        FileMetadata metadata = fileClient.getMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }

        FileVisibility visibility = metadata.getVisibility();
        if (visibility == null) {
            visibility = FileVisibility.PRIVATE;
        }

        // 公开文件：直接访问
        if (visibility == FileVisibility.PUBLIC) {
            return streamFile(metadata, false);
        }

        // 临时文件：检查是否过期
        if (visibility == FileVisibility.TEMPORARY) {
            if (metadata.getExpireTime() != null && 
                System.currentTimeMillis() < metadata.getExpireTime()) {
                return streamFile(metadata, false);
            }
        }

        // 私有文件或过期临时文件：验证签名
        if (token != null || expire != null) {
            if (token == null || expire == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (!urlGenerator.validateSignature(fileId, token, expire)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return streamFile(metadata, false);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * 下载文件（强制下载）
     * 遵循相同的权限规则
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable("fileId") String fileId,
            @RequestParam(name = "token", required = false) String token,
            @RequestParam(name = "expire", required = false) Long expire) {
        
        FileMetadata metadata = fileClient.getMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }

        FileVisibility visibility = metadata.getVisibility();
        if (visibility == null) {
            visibility = FileVisibility.PRIVATE;
        }

        // 公开文件：直接下载
        if (visibility == FileVisibility.PUBLIC) {
            return streamFile(metadata, true);
        }

        // 临时文件：检查是否过期
        if (visibility == FileVisibility.TEMPORARY) {
            if (metadata.getExpireTime() != null && 
                System.currentTimeMillis() < metadata.getExpireTime()) {
                return streamFile(metadata, true);
            }
        }

        // 私有文件或过期临时文件：验证签名
        if (token != null || expire != null) {
            if (token == null || expire == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (!urlGenerator.validateSignature(fileId, token, expire)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return streamFile(metadata, true);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * 流式返回文件
     */
    private ResponseEntity<StreamingResponseBody> streamFile(FileMetadata metadata, boolean forceDownload) {
        InputStream fileStream = fileClient.download(metadata.getId());
        HttpHeaders headers = buildHeaders(metadata, forceDownload);

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream in = fileStream) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        };

        return ResponseEntity.ok()
                .headers(headers)
                .body(responseBody);
    }

    /**
     * 获取缩略图
     * 
     * <p>如果文件没有缩略图（非图片类型或未生成缩略图），返回 404。</p>
     * <p>缩略图继承原图的访问权限。</p>
     * <p>注意：此接口支持两种访问方式：</p>
     * <ul>
     *   <li>传入原图ID：返回原图对应的缩略图</li>
     *   <li>传入缩略图ID：直接返回缩略图文件</li>
     * </ul>
     */
    @GetMapping("/{fileId}/thumbnail")
    public ResponseEntity<StreamingResponseBody> getThumbnail(
            @PathVariable("fileId") String fileId,
            @RequestParam(name = "token", required = false) String token,
            @RequestParam(name = "expire", required = false) Long expire) {
        
        FileMetadata metadata = fileClient.getMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }

        // 判断是原图ID还是缩略图ID
        String thumbnailId = metadata.getThumbnailId();
        String actualFileId;
        FileMetadata sourceMetadata;
        
        if (thumbnailId != null && !thumbnailId.isEmpty()) {
            // 这是原图，有缩略图
            actualFileId = thumbnailId;
            sourceMetadata = metadata;
        } else if (metadata.getFileName() != null && metadata.getFileName().startsWith("thumb_")) {
            // 这可能就是缩略图文件本身，直接返回
            actualFileId = fileId;
            sourceMetadata = metadata;
        } else {
            // 这是原图但没有缩略图
            return ResponseEntity.notFound().build();
        }

        // 权限检查（缩略图继承原图权限）
        FileVisibility visibility = sourceMetadata.getVisibility();
        if (visibility == null) {
            visibility = FileVisibility.PRIVATE;
        }

        // 公开文件：直接访问
        if (visibility == FileVisibility.PUBLIC) {
            return streamThumbnailById(actualFileId, sourceMetadata);
        }

        // 临时文件：检查是否过期
        if (visibility == FileVisibility.TEMPORARY) {
            if (sourceMetadata.getExpireTime() != null && 
                System.currentTimeMillis() < sourceMetadata.getExpireTime()) {
                return streamThumbnailById(actualFileId, sourceMetadata);
            }
        }

        // 私有文件或过期临时文件：验证签名
        if (token != null || expire != null) {
            if (token == null || expire == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            // 使用原图ID验证签名（如果是缩略图ID访问，用缩略图ID验证）
            if (!urlGenerator.validateSignature(fileId, token, expire)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return streamThumbnailById(actualFileId, sourceMetadata);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * 通过缩略图ID流式返回缩略图
     */
    private ResponseEntity<StreamingResponseBody> streamThumbnailById(String thumbnailId, FileMetadata sourceMetadata) {
        InputStream thumbnailStream = fileClient.download(thumbnailId);
        if (thumbnailStream == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = buildHeaders(sourceMetadata, false);

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream in = thumbnailStream) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        };

        return ResponseEntity.ok()
                .headers(headers)
                .body(responseBody);
    }

    // ==================== 元数据管理 ====================

    /**
     * 获取文件元数据
     */
    @GetMapping("/{fileId}/metadata")
    public ResponseEntity<FileMetadata> getMetadata(@PathVariable("fileId") String fileId) {
        FileMetadata metadata = fileClient.getMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }

    /**
     * 更新文件元数据
     * 将新的元数据合并到现有元数据中
     * 
     * @param fileId 文件ID
     * @param metadata 要更新的元数据
     */
    @PutMapping("/{fileId}/metadata")
    public ResponseEntity<Void> updateMetadata(
            @PathVariable("fileId") String fileId,
            @RequestBody Map<String, String> metadata) {
        
        fileClient.updateMetadata(fileId, metadata);
        return ResponseEntity.noContent().build();
    }

    /**
     * 复制文件
     * 创建文件的副本，生成新的文件ID
     * 
     * @param fileId 源文件ID
     * @param replicate 是否触发副本复制（可选，默认 false）
     */
    @PostMapping("/{fileId}/copy")
    public ResponseEntity<UploadResponse> copyFile(
            @PathVariable("fileId") String fileId,
            @RequestParam(name = "replicate", defaultValue = "false") boolean replicate) {
        
        String newFileId = fileClient.copy(fileId, replicate);
        return ResponseEntity.ok(new UploadResponse(newFileId, null));
    }

    /**
     * 重命名文件
     * 只修改文件的显示名称，不影响物理存储
     * 
     * @param fileId 文件ID
     * @param request 包含新文件名的请求体
     */
    @PutMapping("/{fileId}/name")
    public ResponseEntity<Void> renameFile(
            @PathVariable("fileId") String fileId,
            @RequestBody RenameRequest request) {
        
        fileClient.rename(fileId, request.newFileName());
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新文件访问权限
     * 
     * @param fileId 文件ID
     * @param request 权限更新请求
     */
    @PutMapping("/{fileId}/visibility")
    public ResponseEntity<Void> updateVisibility(
            @PathVariable("fileId") String fileId,
            @RequestBody VisibilityRequest request) {
        
        FileMetadata metadata = fileClient.getMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> updateData = new HashMap<>();
        updateData.put("visibility", request.visibility().name());
        if (request.expireSeconds() != null && request.visibility() == FileVisibility.TEMPORARY) {
            long expireTime = System.currentTimeMillis() + request.expireSeconds() * 1000;
            updateData.put("expireTime", String.valueOf(expireTime));
        }
        fileClient.updateMetadata(fileId, updateData);
        
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable("fileId") String fileId) {
        try {
            fileClient.delete(fileId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 查询文件列表
     * 
     * @param status 文件状态（可选，默认 COMMITTED）
     */
    @GetMapping
    public ResponseEntity<List<FileMetadata>> listFiles(
            @RequestParam(name = "fileName", required = false) String fileName,
            @RequestParam(name = "contentType", required = false) String contentType,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(name = "status", required = false) FileStatus status) {

        FileQuery query = FileQuery.builder()
                .status(status != null ? status : FileStatus.COMMITTED)
                .fileName(fileName)
                .contentType(contentType)
                .page(page)
                .pageSize(pageSize)
                .build();

        List<FileMetadata> files = fileClient.listFiles(query);
        return ResponseEntity.ok(files);
    }

    // ==================== 分片上传 ====================

    /**
     * 初始化分片上传
     */
    @PostMapping("/multipart/init")
    public ResponseEntity<MultipartUploadResponse> initMultipartUpload(
            @RequestParam(name = "fileName") String fileName,
            @RequestParam(name = "fileSize") long fileSize) {
        
        InitMultipartUploadResult result = fileClient.initMultipartUpload(fileName, fileSize, null);
        return ResponseEntity.ok(new MultipartUploadResponse(result.getUploadId(), result.getTargetNodeId()));
    }

    /**
     * 上传分片
     */
    @PostMapping("/multipart/{uploadId}/part/{partNumber}")
    public ResponseEntity<PartUploadResponse> uploadPart(
            @PathVariable("uploadId") String uploadId,
            @PathVariable("partNumber") int partNumber,
            @RequestParam("file") MultipartFile file) throws Exception {

        String eTag = fileClient.uploadPart(uploadId, partNumber, file.getInputStream());
        return ResponseEntity.ok(new PartUploadResponse(partNumber, eTag));
    }

    /**
     * 完成分片上传
     */
    @PostMapping("/multipart/{uploadId}/complete")
    public ResponseEntity<UploadResponse> completeMultipartUpload(
            @PathVariable("uploadId") String uploadId,
            @RequestBody List<PartInfoRequest> parts) {

        List<PartInfo> partInfos = parts.stream()
                .map(p -> PartInfo.builder()
                        .partNumber(p.partNumber())
                        .eTag(p.eTag())
                        .build())
                .collect(Collectors.toList());

        String fileId = fileClient.completeMultipartUpload(uploadId, partInfos);
        return ResponseEntity.ok(new UploadResponse(fileId, null));
    }

    /**
     * 取消分片上传
     */
    @DeleteMapping("/multipart/{uploadId}")
    public ResponseEntity<Void> abortMultipartUpload(@PathVariable("uploadId") String uploadId) {
        fileClient.abortMultipartUpload(uploadId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建响应头
     * 使用 ContentDisposition 正确处理文件名编码（包括中文和扩展名）
     */
    private HttpHeaders buildHeaders(FileMetadata metadata, boolean forceDownload) {
        HttpHeaders headers = new HttpHeaders();
        
        // 设置 Content-Type
        String contentType = metadata.getContentType();
        if (contentType != null) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }

        // 设置 Content-Length
        headers.setContentLength(metadata.getFileSize());

        // 设置 Content-Disposition（正确处理文件名和扩展名）
        String fileName = metadata.getFileName();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "download";
        }
        
        ContentDisposition disposition;
        if (forceDownload || !isImage(contentType)) {
            // 强制下载
            disposition = ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build();
        } else {
            // 图片直接显示
            disposition = ContentDisposition.inline()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build();
        }
        headers.setContentDisposition(disposition);

        return headers;
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    // ==================== 响应DTO ====================

    public record UrlResponse(String url, long expireSeconds) {}

    public record UploadResponse(String fileId, String fileName) {}

    public record MultipartUploadResponse(String uploadId, String targetNodeId) {}

    public record PartUploadResponse(int partNumber, String eTag) {}

    public record PartInfoRequest(int partNumber, String eTag) {}

    public record RenameRequest(String newFileName) {}

    public record VisibilityRequest(FileVisibility visibility, Long expireSeconds) {}
}
