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

import io.github.fangyudev.litefs.model.MultipartUpload;
import io.github.fangyudev.litefs.model.PartInfo;

import java.util.List;

/**
 * 分块上传会话存储 SPI
 * 
 * <p>用于存储和管理分块上传会话，支持多种存储后端：</p>
 * <ul>
 *   <li>local - 本地内存存储，仅适用于单机模式</li>
 *   <li>redis - Redis 分布式存储，适用于分布式模式</li>
 * </ul>
 * 
 * <p>实现类需要保证线程安全。</p>
 */
public interface MultipartUploadStore {

    /**
     * 初始化存储
     * 例如：建立连接、创建表等
     */
    void init();

    /**
     * 保存上传会话
     * 
     * @param upload 上传会话
     */
    void save(MultipartUpload upload);

    /**
     * 获取上传会话
     * 
     * @param uploadId 上传会话ID
     * @return 上传会话，不存在时返回 null
     */
    MultipartUpload get(String uploadId);

    /**
     * 删除上传会话
     * 
     * @param uploadId 上传会话ID
     */
    void delete(String uploadId);

    /**
     * 添加分块信息到指定上传会话
     * 
     * <p>此操作需要原子性，防止并发覆盖</p>
     * 
     * @param uploadId 上传会话ID
     * @param partNumber 分块序号
     * @param partInfo 分块信息
     * @return true 表示添加成功，false 表示会话不存在或已过期
     */
    boolean addPart(String uploadId, int partNumber, PartInfo partInfo);

    /**
     * 获取所有已过期的上传会话ID列表
     * 
     * @return 过期的上传会话ID列表
     */
    List<String> getExpiredUploadIds();

    /**
     * 检查上传会话是否存在
     * 
     * @param uploadId 上传会话ID
     * @return true 表示存在
     */
    boolean exists(String uploadId);
}
