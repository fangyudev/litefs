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
 * 文件上传选项，用于控制文件上传行为。
 */
public class UploadOptions {

    /** 是否生成缩略图，null表示使用默认设置。 */
    private Boolean generateThumbnail;

    /** 缩略图尺寸名称，null表示使用默认尺寸。 */
    private String thumbnailSize;

    /** 用户自定义元数据。 */
    private Map<String, String> customMetadata;

    /** 文件访问权限。 */
    private FileVisibility visibility;

    /** 过期时间戳（毫秒）。 */
    private Long expireTime;

    public UploadOptions() {
        this.customMetadata = new HashMap<>();
    }

    private UploadOptions(Builder builder) {
        this.generateThumbnail = builder.generateThumbnail;
        this.thumbnailSize = builder.thumbnailSize;
        this.customMetadata = builder.customMetadata != null 
            ? new HashMap<>(builder.customMetadata) 
            : new HashMap<>();
        this.visibility = builder.visibility;
        this.expireTime = builder.expireTime;
    }

    public Boolean getGenerateThumbnail() {
        return generateThumbnail;
    }

    public void setGenerateThumbnail(Boolean generateThumbnail) {
        this.generateThumbnail = generateThumbnail;
    }

    public String getThumbnailSize() {
        return thumbnailSize;
    }

    public void setThumbnailSize(String thumbnailSize) {
        this.thumbnailSize = thumbnailSize;
    }

    public Map<String, String> getCustomMetadata() {
        return customMetadata;
    }

    public void setCustomMetadata(Map<String, String> customMetadata) {
        this.customMetadata = customMetadata != null 
            ? new HashMap<>(customMetadata) 
            : new HashMap<>();
    }

    public FileVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(FileVisibility visibility) {
        this.visibility = visibility;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * 创建构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器类。
     */
    public static class Builder {

        private Boolean generateThumbnail;
        private String thumbnailSize;
        private Map<String, String> customMetadata = new HashMap<>();
        private FileVisibility visibility;
        private Long expireTime;

        public Builder generateThumbnail(Boolean generateThumbnail) {
            this.generateThumbnail = generateThumbnail;
            return this;
        }

        public Builder thumbnailSize(String thumbnailSize) {
            this.thumbnailSize = thumbnailSize;
            return this;
        }

        public Builder customMetadata(String key, String value) {
            this.customMetadata.put(key, value);
            return this;
        }

        public Builder customMetadata(Map<String, String> metadata) {
            if (metadata != null) {
                this.customMetadata.putAll(metadata);
            }
            return this;
        }

        public Builder visibility(FileVisibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder expireTime(Long expireTime) {
            this.expireTime = expireTime;
            return this;
        }

        public UploadOptions build() {
            return new UploadOptions(this);
        }
    }

    @Override
    public String toString() {
        return "UploadOptions{" +
            "generateThumbnail=" + generateThumbnail +
            ", thumbnailSize='" + thumbnailSize + '\'' +
            ", customMetadata=" + customMetadata +
            ", visibility=" + visibility +
            ", expireTime=" + expireTime +
            '}';
    }
}
