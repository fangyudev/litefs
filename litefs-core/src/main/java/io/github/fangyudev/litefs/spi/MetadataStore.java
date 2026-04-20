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

import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileQuery;

import java.util.List;

/**
 * 元数据存储 SPI 接口，定义文件元数据的持久化操作。
 * 
 * <p><b>内置实现：</b></p>
 * <ul>
 *   <li>H2MetadataStore - H2嵌入式数据库</li>
 * </ul>
 */
public interface MetadataStore {

    /**
     * 保存文件元数据。
     * 
     * @param metadata 文件元数据
     */
    void save(FileMetadata metadata);

    /**
     * 获取文件元数据。
     * 
     * @param fileId 文件ID
     * @return 文件元数据，不存在则返回null
     */
    FileMetadata get(String fileId);

    /**
     * 删除文件元数据。
     * 
     * @param fileId 文件ID
     */
    void delete(String fileId);

    /**
     * 更新文件元数据。
     * 
     * @param metadata 文件元数据
     */
    void update(FileMetadata metadata);

    /**
     * 查询文件列表。
     * 
     * @param query 查询条件
     * @return 文件元数据列表
     */
    List<FileMetadata> query(FileQuery query);

    /**
     * 统计文件数量。
     * 
     * @param query 查询条件
     * @return 文件数量
     */
    long count(FileQuery query);

    /**
     * 检查文件是否存在。
     * 
     * @param fileId 文件ID
     * @return 存在返回true，否则返回false
     */
    boolean exists(String fileId);

    /**
     * 初始化存储。
     */
    void init();

    /**
     * 关闭存储。
     */
    void shutdown();
}
