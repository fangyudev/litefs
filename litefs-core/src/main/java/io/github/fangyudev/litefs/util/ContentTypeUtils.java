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

package io.github.fangyudev.litefs.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 内容类型工具类，根据文件扩展名获取对应的MIME类型。
 */
public final class ContentTypeUtils {

    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = new HashMap<>();

    static {
        // 图片类型
        EXTENSION_TO_CONTENT_TYPE.put("jpg", "image/jpeg");
        EXTENSION_TO_CONTENT_TYPE.put("jpeg", "image/jpeg");
        EXTENSION_TO_CONTENT_TYPE.put("png", "image/png");
        EXTENSION_TO_CONTENT_TYPE.put("gif", "image/gif");
        EXTENSION_TO_CONTENT_TYPE.put("webp", "image/webp");
        EXTENSION_TO_CONTENT_TYPE.put("bmp", "image/bmp");
        EXTENSION_TO_CONTENT_TYPE.put("svg", "image/svg+xml");
        EXTENSION_TO_CONTENT_TYPE.put("ico", "image/x-icon");
        
        // 文档类型
        EXTENSION_TO_CONTENT_TYPE.put("pdf", "application/pdf");
        EXTENSION_TO_CONTENT_TYPE.put("doc", "application/msword");
        EXTENSION_TO_CONTENT_TYPE.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        EXTENSION_TO_CONTENT_TYPE.put("xls", "application/vnd.ms-excel");
        EXTENSION_TO_CONTENT_TYPE.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        EXTENSION_TO_CONTENT_TYPE.put("ppt", "application/vnd.ms-powerpoint");
        EXTENSION_TO_CONTENT_TYPE.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        EXTENSION_TO_CONTENT_TYPE.put("txt", "text/plain");
        
        // 网页类型
        EXTENSION_TO_CONTENT_TYPE.put("html", "text/html");
        EXTENSION_TO_CONTENT_TYPE.put("htm", "text/html");
        EXTENSION_TO_CONTENT_TYPE.put("css", "text/css");
        EXTENSION_TO_CONTENT_TYPE.put("js", "application/javascript");
        EXTENSION_TO_CONTENT_TYPE.put("json", "application/json");
        EXTENSION_TO_CONTENT_TYPE.put("xml", "application/xml");
        
        // 压缩类型
        EXTENSION_TO_CONTENT_TYPE.put("zip", "application/zip");
        EXTENSION_TO_CONTENT_TYPE.put("rar", "application/x-rar-compressed");
        EXTENSION_TO_CONTENT_TYPE.put("7z", "application/x-7z-compressed");
        EXTENSION_TO_CONTENT_TYPE.put("tar", "application/x-tar");
        EXTENSION_TO_CONTENT_TYPE.put("gz", "application/gzip");
        
        // 音频类型
        EXTENSION_TO_CONTENT_TYPE.put("mp3", "audio/mpeg");
        EXTENSION_TO_CONTENT_TYPE.put("wav", "audio/wav");
        
        // 视频类型
        EXTENSION_TO_CONTENT_TYPE.put("mp4", "video/mp4");
        EXTENSION_TO_CONTENT_TYPE.put("avi", "video/x-msvideo");
        EXTENSION_TO_CONTENT_TYPE.put("mov", "video/quicktime");
        EXTENSION_TO_CONTENT_TYPE.put("wmv", "video/x-ms-wmv");
        EXTENSION_TO_CONTENT_TYPE.put("flv", "video/x-flv");
        EXTENSION_TO_CONTENT_TYPE.put("mkv", "video/x-matroska");
    }

    private ContentTypeUtils() {
    }

    /**
     * 根据文件名获取内容类型。
     */
    public static String getContentType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "application/octet-stream";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex >= fileName.length() - 1) {
            return "application/octet-stream";
        }
        String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        return EXTENSION_TO_CONTENT_TYPE.getOrDefault(extension, "application/octet-stream");
    }

    /**
     * 判断是否为图片类型。
     */
    public static boolean isImage(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.startsWith("image/");
    }

    /**
     * 判断是否为视频类型。
     */
    public static boolean isVideo(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.startsWith("video/");
    }

    /**
     * 判断是否为音频类型。
     */
    public static boolean isAudio(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.startsWith("audio/");
    }
}
