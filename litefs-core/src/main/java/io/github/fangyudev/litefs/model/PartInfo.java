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
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 分块信息
 * 用于分块上传时记录每个分块的元数据
 */
public class PartInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分块序号，从1开始 */
    private int partNumber;
    
    /** 分块校验值(ETag)，用于校验分块完整性 */
    private String eTag;
    
    /** 分块大小(字节) */
    private long size;
    
    /** 分块在存储引擎中的路径 */
    private String storagePath;
    
    /** 分块存储的节点ID（用于分布式环境读取正确的节点） */
    private String storageNodeId;
    
    /** 上传时间(时间戳) */
    private long uploadTime;

    /**
     * 默认构造函数
     * 初始化上传时间为当前时间
     */
    public PartInfo() {
        this.uploadTime = System.currentTimeMillis();
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    @JsonProperty("eTag")
    public String getETag() {
        return eTag;
    }

    @JsonProperty("eTag")
    public void setETag(String eTag) {
        this.eTag = eTag;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getStorageNodeId() {
        return storageNodeId;
    }

    public void setStorageNodeId(String storageNodeId) {
        this.storageNodeId = storageNodeId;
    }

    public long getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(long uploadTime) {
        this.uploadTime = uploadTime;
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
     * 用于便捷地创建PartInfo实例
     */
    public static class Builder {
        private final PartInfo partInfo = new PartInfo();

        public Builder partNumber(int partNumber) {
            partInfo.setPartNumber(partNumber);
            return this;
        }

        public Builder eTag(String eTag) {
            partInfo.setETag(eTag);
            return this;
        }

        public Builder size(long size) {
            partInfo.setSize(size);
            return this;
        }

        public Builder storagePath(String storagePath) {
            partInfo.setStoragePath(storagePath);
            return this;
        }

        public Builder storageNodeId(String storageNodeId) {
            partInfo.setStorageNodeId(storageNodeId);
            return this;
        }

        public PartInfo build() {
            return partInfo;
        }
    }
}
