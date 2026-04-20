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

import java.util.HashMap;
import java.util.Map;

/**
 * 文件查询条件。
 */
public class FileQuery {

    /** 文件状态。 */
    private FileStatus status;
    
    /** 文件名（支持模糊匹配）。 */
    private String fileName;
    
    /** 内容类型（MIME类型）。 */
    private String contentType;
    
    /** 最小文件大小（字节）。 */
    private Long minSize;
    
    /** 最大文件大小（字节）。 */
    private Long maxSize;
    
    /** 用户自定义元数据过滤条件。 */
    private Map<String, String> metadata;
    
    /** 创建时间起始（时间戳）。 */
    private Long startTime;
    
    /** 创建时间结束（时间戳）。 */
    private Long endTime;
    
    /** 页码，从1开始。 */
    private int page = 1;
    
    /** 每页大小，默认20，最大100。 */
    private int pageSize = 20;

    /**
     * 默认构造函数
     * 初始化元数据Map
     */
    public FileQuery() {
        this.metadata = new HashMap<>();
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
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

    public Long getMinSize() {
        return minSize;
    }

    public void setMinSize(Long minSize) {
        this.minSize = minSize;
    }

    public Long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Long maxSize) {
        this.maxSize = maxSize;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public int getPage() {
        return page;
    }

    /**
     * 设置页码，最小值为1
     * @param page 页码
     */
    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页大小，范围1-100
     * @param pageSize 每页大小
     */
    public void setPageSize(int pageSize) {
        this.pageSize = Math.max(1, Math.min(100, pageSize));
    }

    /**
     * 计算分页偏移量。
     */
    public int getOffset() {
        return (page - 1) * pageSize;
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
        private final FileQuery query = new FileQuery();

        public Builder status(FileStatus status) {
            query.setStatus(status);
            return this;
        }

        public Builder fileName(String fileName) {
            query.setFileName(fileName);
            return this;
        }

        public Builder contentType(String contentType) {
            query.setContentType(contentType);
            return this;
        }

        public Builder minSize(Long minSize) {
            query.setMinSize(minSize);
            return this;
        }

        public Builder maxSize(Long maxSize) {
            query.setMaxSize(maxSize);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            query.setMetadata(metadata);
            return this;
        }

        public Builder startTime(Long startTime) {
            query.setStartTime(startTime);
            return this;
        }

        public Builder endTime(Long endTime) {
            query.setEndTime(endTime);
            return this;
        }

        public Builder page(int page) {
            query.setPage(page);
            return this;
        }

        public Builder pageSize(int pageSize) {
            query.setPageSize(pageSize);
            return this;
        }

        public FileQuery build() {
            return query;
        }
    }
}
