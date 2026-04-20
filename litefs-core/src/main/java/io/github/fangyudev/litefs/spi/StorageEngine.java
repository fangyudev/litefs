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

package io.github.fangyudev.litefs.spi;

import java.io.InputStream;

/**
 * 存储引擎 SPI 接口，定义文件存储的基本操作。
 * 
 * <p><b>内置实现：</b></p>
 * <ul>
 *   <li>LocalStorageEngine - 本地文件系统存储</li>
 * </ul>
 */
public interface StorageEngine {

    /**
     * 写入文件（覆盖或新建）。
     * 
     * @param fileId 文件ID
     * @param data 文件数据输入流
     * @return 存储路径
     */
    String write(String fileId, InputStream data);

    /**
     * 追加写入文件，用于分片上传场景。
     * 
     * <p>如果文件不存在，则创建新文件。</p>
     * 
     * @param fileId 文件ID
     * @param data 要追加的数据输入流
     * @return 追加后的文件大小（字节）
     */
    long append(String fileId, InputStream data);

    /**
     * 读取文件。
     * 
     * @param fileId 文件ID或存储路径
     * @return 文件输入流，调用者负责关闭
     * @throws RuntimeException 文件不存在时抛出
     */
    InputStream read(String fileId);

    /**
     * 删除文件。
     * 
     * @param fileId 文件ID或存储路径
     */
    void delete(String fileId);

    /**
     * 检查文件是否存在。
     * 
     * @param fileId 文件ID或存储路径
     * @return 存在返回true，否则返回false
     */
    boolean exists(String fileId);

    /**
     * 获取文件大小。
     * 
     * @param fileId 文件ID或存储路径
     * @return 文件大小（字节），不存在返回-1
     */
    long getSize(String fileId);

    /**
     * 获取文件存储路径。
     * 
     * @param fileId 文件ID
     * @return 存储路径
     */
    String getStoragePath(String fileId);
}
