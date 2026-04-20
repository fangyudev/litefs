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

package io.github.fangyudev.litefs.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件元数据
 * 存储文件的基本信息，包括文件名、大小、校验和、存储位置等
 */
public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件唯一标识符 */
    private String id;
    
    /** 原始文件名 */
    private String fileName;
    
    /** 内容类型(MIME类型) */
    private String contentType;
    
    /** 文件大小(字节) */
    private long fileSize;
    
    /** 文件校验和(MD5) */
    private String checksum;
    
    /** 存储节点ID */
    private String storageNodeId;
    
    /** 存储路径 */
    private String storagePath;
    
    /** 用户自定义元数据 */
    private Map<String, String> metadata;
    
    /** 文件状态 */
    private FileStatus status;
    
    /** 创建时间(时间戳) */
    private long createTime;
    
    /** 更新时间(时间戳) */
    private long updateTime;
    
    /** 过期时间(时间戳)，null表示永不过期 */
    private Long expireTime;
    
    /** 缩略图文件ID */
    private String thumbnailId;

    /** 文件访问权限 */
    private FileVisibility visibility;

    /**
     * 默认构造函数
     * 初始化元数据Map、状态为PENDING、创建时间和更新时间为当前时间、权限为私有
     */
    public FileMetadata() {
        this.metadata = new HashMap<>();
        this.status = FileStatus.PENDING;
        this.visibility = FileVisibility.PRIVATE;
        this.createTime = System.currentTimeMillis();
        this.updateTime = this.createTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getStorageNodeId() {
        return storageNodeId;
    }

    public void setStorageNodeId(String storageNodeId) {
        this.storageNodeId = storageNodeId;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    public String getThumbnailId() {
        return thumbnailId;
    }

    public void setThumbnailId(String thumbnailId) {
        this.thumbnailId = thumbnailId;
    }

    public FileVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(FileVisibility visibility) {
        this.visibility = visibility;
    }

    /**
     * 添加单个元数据键值对
     * @param key 键
     * @param value 值
     */
    public void addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * 获取指定键的元数据值
     * @param key 键
     * @return 元数据值，不存在则返回null
     */
    public String getMetadata(String key) {
        return this.metadata != null ? this.metadata.get(key) : null;
    }

    /**
     * 创建Builder实例
     * @return Builder对象
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器模式
     * 用于便捷地创建FileMetadata实例
     */
    public static class Builder {
        private final FileMetadata metadata = new FileMetadata();

        public Builder id(String id) {
            metadata.setId(id);
            return this;
        }

        public Builder fileName(String fileName) {
            metadata.setFileName(fileName);
            return this;
        }

        public Builder contentType(String contentType) {
            metadata.setContentType(contentType);
            return this;
        }

        public Builder fileSize(long fileSize) {
            metadata.setFileSize(fileSize);
            return this;
        }

        public Builder checksum(String checksum) {
            metadata.setChecksum(checksum);
            return this;
        }

        public Builder storageNodeId(String storageNodeId) {
            metadata.setStorageNodeId(storageNodeId);
            return this;
        }

        public Builder storagePath(String storagePath) {
            metadata.setStoragePath(storagePath);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata.setMetadata(metadata);
            return this;
        }

        public Builder status(FileStatus status) {
            metadata.setStatus(status);
            return this;
        }

        public Builder expireTime(Long expireTime) {
            metadata.setExpireTime(expireTime);
            return this;
        }

        public Builder visibility(FileVisibility visibility) {
            metadata.setVisibility(visibility);
            return this;
        }

        public FileMetadata build() {
            return metadata;
        }
    }
}
