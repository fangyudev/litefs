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

import io.github.fangyudev.litefs.service.ReplicationService;

import java.io.Serializable;

/**
 * 副本复制任务
 * 
 * <p>封装一次副本复制操作所需的所有信息，用于在消息队列中传递。
 * 当使用异步复制模式（最终一致性）时，复制操作会被封装成此任务对象，
 * 投递到消息队列中异步执行。</p>
 * 
 * <h3>数据流：</h3>
 * <pre>
 * 文件上传 → 创建 ReplicationTask → 发送到消息队列 → 消费者接收 → 执行复制
 * </pre>
 * 
 * <h3>字段说明：</h3>
 * <ul>
 *   <li>{@code metadata} - 文件元数据，包含文件ID、存储位置等信息</li>
 *   <li>{@code strategy} - 复制策略，指定副本数量和放置策略</li>
 *   <li>{@code request} - 上传请求上下文，用于选择目标节点</li>
 * </ul>
 * 
 * <h3>序列化说明：</h3>
 * <p>实现 {@link Serializable} 接口，支持在消息队列中传输。
 * 注意：所有字段都必须是可序列化的。</p>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建复制任务
 * ReplicationTask task = ReplicationTask.of(
 *     fileMetadata,
 *     ReplicationStrategy.standard(),
 *     uploadRequest
 * );
 * 
 * // 发送到消息队列
 * messageQueue.send("litefs.replication", task);
 * 
 * // 消费者处理
 * public void onMessage(String topic, Object message) {
 *     if (message instanceof ReplicationTask task) {
 *         replicationService.replicateSync(task.getMetadata(), task.getStrategy(), task.getRequest());
 *     }
 * }
 * }</pre>
 * 
 * @see ReplicationService
 * @see ReplicationStrategy
 * @see FileMetadata
 */
public class ReplicationTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件元数据，包含文件ID、大小、校验和、存储位置等信息 */
    private FileMetadata metadata;
    
    /** 复制策略，指定副本数量、放置策略等 */
    private ReplicationStrategy strategy;
    
    /** 上传请求上下文，包含文件大小、客户端区域等信息，用于节点选择 */
    private FileUploadRequest request;

    /**
     * 默认构造函数
     * 
     * <p>用于反序列化，业务代码应使用 {@link #of(FileMetadata, ReplicationStrategy, FileUploadRequest)} 方法。</p>
     */
    public ReplicationTask() {
    }

    /**
     * 构造函数
     * 
     * @param metadata 文件元数据
     * @param strategy 复制策略
     * @param request 上传请求上下文
     */
    public ReplicationTask(FileMetadata metadata, ReplicationStrategy strategy, FileUploadRequest request) {
        this.metadata = metadata;
        this.strategy = strategy;
        this.request = request;
    }

    /**
     * 获取文件元数据
     * 
     * @return 文件元数据
     */
    public FileMetadata getMetadata() {
        return metadata;
    }

    /**
     * 设置文件元数据
     * 
     * @param metadata 文件元数据
     */
    public void setMetadata(FileMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * 获取复制策略
     * 
     * @return 复制策略
     */
    public ReplicationStrategy getStrategy() {
        return strategy;
    }

    /**
     * 设置复制策略
     * 
     * @param strategy 复制策略
     */
    public void setStrategy(ReplicationStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * 获取上传请求上下文
     * 
     * @return 上传请求上下文
     */
    public FileUploadRequest getRequest() {
        return request;
    }

    /**
     * 设置上传请求上下文
     * 
     * @param request 上传请求上下文
     */
    public void setRequest(FileUploadRequest request) {
        this.request = request;
    }

    /**
     * 静态工厂方法：创建复制任务
     * 
     * <p>推荐使用此方法创建任务对象，比构造函数更简洁。</p>
     * 
     * @param metadata 文件元数据
     * @param strategy 复制策略
     * @param request 上传请求上下文
     * @return 新建的复制任务对象
     */
    public static ReplicationTask of(FileMetadata metadata, ReplicationStrategy strategy, FileUploadRequest request) {
        return new ReplicationTask(metadata, strategy, request);
    }
}
