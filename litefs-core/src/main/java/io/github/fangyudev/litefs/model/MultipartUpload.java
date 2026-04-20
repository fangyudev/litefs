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
 * 分块上传会话
 * 用于跟踪分块上传状态
 * 
 * <p>在分布式环境下，此对象会被序列化存储到共享存储（如Redis）中，
 * 以便所有节点都能访问同一个上传会话。</p>
 */
public class MultipartUpload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上传会话ID */
    private String uploadId;

    /** 文件名 */
    private String fileName;

    /** 文件总大小 */
    private long fileSize;

    /** 用户自定义元数据 */
    private Map<String, String> metadata;

    /** 会话创建时间（时间戳） */
    private long createTime;

    /** 会话过期时间（时间戳） */
    private long expireTime;

    /** 目标存储节点ID（所有分片和合并操作都在此节点执行） */
    private String targetNodeId;

    /** 已上传的分块信息，key为分块序号 */
    private Map<Integer, PartInfo> parts = new HashMap<>();

    public MultipartUpload() {
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public Map<Integer, PartInfo> getParts() {
        return parts;
    }

    public void setParts(Map<Integer, PartInfo> parts) {
        this.parts = parts;
    }

    public void addPart(int partNumber, PartInfo partInfo) {
        this.parts.put(partNumber, partInfo);
    }

    /**
     * 判断会话是否已过期
     * @return true 表示已过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }
}
