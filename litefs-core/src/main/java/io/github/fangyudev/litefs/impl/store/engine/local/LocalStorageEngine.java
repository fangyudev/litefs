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

package io.github.fangyudev.litefs.impl.store.engine.local;

import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * 本地文件存储引擎
 * 将文件存储在本地文件系统中
 * 
 * <p>存储结构：</p>
 * <pre>
 * basePath/
 *   ├── ab/                      # 第一级目录（文件ID前2位）
 *   │   └── cd/                  # 第二级目录（文件ID第3-4位）
 *   │       └── abcd...          # 实际文件（以完整文件ID命名）
 *   └── ...
 * </pre>
 * 
 * <p>特点：</p>
 * <ul>
 *   <li>使用多级目录结构，避免单目录文件过多</li>
 *   <li>写入时使用临时文件，保证原子性</li>
 *   <li>支持追加写入，用于分片上传场景</li>
 *   <li>支持直接路径访问（用于分块上传场景）</li>
 * </ul>
 */
public class LocalStorageEngine implements StorageEngine {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageEngine.class);

    /** 子目录层级深度 */
    private static final int SUBDIR_DEPTH = 2;
    
    /** 每级子目录名称长度 */
    private static final int SUBDIR_LENGTH = 2;

    /** 存储根路径 */
    private final String basePath;

    /**
     * 构造函数
     * 
     * @param basePath 存储根路径
     */
    public LocalStorageEngine(String basePath) {
        this.basePath = basePath;
        initStorageDirectory();
    }

    /**
     * 初始化存储目录
     * 如果目录不存在则创建
     */
    private void initStorageDirectory() {
        Path path = Paths.get(basePath);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                log.info("Created storage directory: {}", basePath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create storage directory: " + basePath, e);
            }
        }
    }

    /**
     * 写入文件（覆盖或新建）
     * 使用临时文件保证写入的原子性
     * 
     * @param fileId 文件ID
     * @param data 文件数据输入流
     * @return 存储路径
     */
    @Override
    public String write(String fileId, InputStream data) {
        String storagePath = getStoragePath(fileId);
        Path filePath = Paths.get(basePath, storagePath);

        try {
            // 确保父目录存在
            Path parentDir = filePath.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 先写入临时文件
            Path tempFile = Paths.get(basePath, storagePath + ".tmp");
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                IoUtils.copy(data, out);
            }

            // 原子性移动到目标位置
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);

            log.debug("File written: {}", storagePath);
            return storagePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + storagePath, e);
        }
    }

    /**
     * 追加写入文件
     * 用于分片上传场景，将数据追加到已有文件末尾
     * 
     * <p>流式写入，不会将整个文件加载到内存。</p>
     * 
     * @param fileId 文件ID
     * @param data 要追加的数据输入流
     * @return 追加后的文件大小（字节）
     */
    @Override
    public long append(String fileId, InputStream data) {
        Path filePath = resolvePath(fileId);

        try {
            // 确保父目录存在
            Path parentDir = filePath.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 使用追加模式打开文件
            long bytesWritten;
            try (OutputStream out = Files.newOutputStream(filePath, 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.APPEND)) {
                bytesWritten = IoUtils.copy(data, out);
            }

            long newSize = Files.size(filePath);
            log.debug("Appended {} bytes to file: {} (total: {})", bytesWritten, fileId, newSize);
            return newSize;
        } catch (IOException e) {
            throw new RuntimeException("Failed to append to file: " + fileId, e);
        }
    }

    /**
     * 读取文件
     * 支持通过文件ID或直接路径访问
     * 
     * @param fileId 文件ID或存储路径
     * @return 文件输入流
     * @throws RuntimeException 文件不存在时抛出
     */
    @Override
    public InputStream read(String fileId) {
        Path filePath = resolvePath(fileId);

        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found: " + fileId);
        }

        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + fileId, e);
        }
    }

    /**
     * 删除文件
     * 
     * @param fileId 文件ID或存储路径
     */
    @Override
    public void delete(String fileId) {
        Path filePath = resolvePath(fileId);

        if (Files.exists(filePath)) {
            try {
                Files.delete(filePath);
                log.debug("File deleted: {}", fileId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete file: " + fileId, e);
            }
        }
    }

    /**
     * 检查文件是否存在
     * 
     * @param fileId 文件ID或存储路径
     * @return 存在返回true
     */
    @Override
    public boolean exists(String fileId) {
        Path filePath = resolvePath(fileId);
        return Files.exists(filePath);
    }

    /**
     * 获取文件大小
     * 
     * @param fileId 文件ID或存储路径
     * @return 文件大小（字节），不存在返回-1
     */
    @Override
    public long getSize(String fileId) {
        Path filePath = resolvePath(fileId);

        if (!Files.exists(filePath)) {
            return -1;
        }

        try {
            return Files.size(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get file size: " + fileId, e);
        }
    }

    /**
     * 获取文件存储路径
     * 根据文件ID计算多级目录结构
     * 
     * @param fileId 文件ID
     * @return 存储路径（相对路径）
     */
    @Override
    public String getStoragePath(String fileId) {
        if (fileId == null || fileId.length() < SUBDIR_DEPTH * SUBDIR_LENGTH) {
            throw new IllegalArgumentException("Invalid file ID: " + fileId);
        }

        StringBuilder path = new StringBuilder();
        // 构建多级目录
        for (int i = 0; i < SUBDIR_DEPTH; i++) {
            int start = i * SUBDIR_LENGTH;
            int end = start + SUBDIR_LENGTH;
            path.append(fileId, start, end).append('/');
        }
        path.append(fileId);

        return path.toString();
    }

    /**
     * 解析文件路径
     * 支持文件ID和直接路径两种方式
     * 
     * @param fileId 文件ID或存储路径
     * @return 绝对路径
     */
    private Path resolvePath(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            throw new IllegalArgumentException("Invalid file ID: " + fileId);
        }
        
        // 如果包含路径分隔符或下划线，视为直接路径
        if (fileId.contains("/") || fileId.contains("_")) {
            return Paths.get(basePath, fileId);
        }
        
        // 否则按文件ID处理
        return Paths.get(basePath, getStoragePath(fileId));
    }

    /**
     * 获取存储根路径
     * 
     * @return 存储根路径
     */
    public String getBasePath() {
        return basePath;
    }
}
