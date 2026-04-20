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
import java.util.Collections;
import java.util.Map;


/**
 * 文件上传请求上下文，用于节点选择与副本放置策略的决策。
 */
public class FileUploadRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件名。 */
    private String fileName;
    
    /** 文件大小（字节）。 */
    private long fileSize;
    
    /** 内容类型（MIME类型）。 */
    private String contentType;
    
    /** 用户自定义元数据。 */
    private Map<String, String> metadata = Collections.emptyMap();
    
    /** 客户端所在区域/机房。 */
    private String clientZone;

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

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : Collections.emptyMap();
    }

    public String getClientZone() {
        return clientZone;
    }

    public void setClientZone(String clientZone) {
        this.clientZone = clientZone;
    }

    /**
     * 创建Builder实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器模式。
     */
    public static class Builder {
        private final FileUploadRequest request = new FileUploadRequest();

        public Builder fileName(String fileName) {
            request.setFileName(fileName);
            return this;
        }

        public Builder fileSize(long fileSize) {
            request.setFileSize(fileSize);
            return this;
        }

        public Builder contentType(String contentType) {
            request.setContentType(contentType);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            request.setMetadata(metadata);
            return this;
        }

        public Builder clientZone(String clientZone) {
            request.setClientZone(clientZone);
            return this;
        }

        public FileUploadRequest build() {
            return request;
        }
    }
}
