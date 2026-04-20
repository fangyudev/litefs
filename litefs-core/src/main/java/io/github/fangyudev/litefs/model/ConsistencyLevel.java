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

/**
 * 一致性级别枚举
 * 
 * <p>定义文件写入时的一致性保证级别，控制副本同步的时机。
 * 不同的一致性级别在可用性和可靠性之间做出不同的权衡。</p>
 * 
 * <h3>一致性级别对比：</h3>
 * <table border="1">
 *   <tr><th>级别</th><th>写入返回时机</th><th>可靠性</th><th>性能</th><th>适用场景</th></tr>
 *   <tr>
 *     <td>EVENTUAL（最终一致性）</td>
 *     <td>主节点写入成功即返回</td>
 *     <td>中等</td>
 *     <td>高</td>
 *     <td>图片、视频、日志</td>
 *   </tr>
 *   <tr>
 *     <td>STRONG（强一致性）</td>
 *     <td>所有副本同步成功后返回</td>
 *     <td>高</td>
 *     <td>低</td>
 *     <td>合同、订单、金融数据</td>
 *   </tr>
 * </table>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建复制服务时指定一致性级别
 * ReplicationService service = new ReplicationService(
 *     serviceRegistry,
 *     replicaPlacer,
 *     replicaStore,
 *     storageRouter,
 *     ReplicationStrategy.standard(),
 *     messageQueue,
 *     ConsistencyLevel.EVENTUAL,  // 使用最终一致性
 *     "litefs.replication"
 * );
 * 
 * // 根据文件类型选择一致性级别
 * ConsistencyLevel level = isImportantFile ? ConsistencyLevel.STRONG : ConsistencyLevel.EVENTUAL;
 * }</pre>
 * 
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>EVENTUAL 模式需要配置消息队列才能生效，否则会降级为 STRONG</li>
 *   <li>STRONG 模式会阻塞等待所有副本同步完成，写入延迟较高</li>
 *   <li>在节点故障时，STRONG 模式可能导致写入失败</li>
 * </ul>
 * 
 * @see ReplicationService
 */
public enum ConsistencyLevel {
    /**
     * 最终一致性
     * 
     * <p>主节点写入成功后立即返回，副本通过消息队列异步同步。
     * 这种模式写入性能高，但在主节点故障且副本未同步完成时可能丢失数据。</p>
     * 
     * <p>特点：</p>
     * <ul>
     *   <li>写入延迟低</li>
     *   <li>需要消息队列支持</li>
     *   <li>适合非关键数据</li>
     * </ul>
     */
    EVENTUAL,

    /**
     * 强一致性
     * 
     * <p>所有副本同步成功后才返回写入成功。这种模式可靠性高，
     * 但写入延迟较高，且在部分节点不可用时可能写入失败。</p>
     * 
     * <p>特点：</p>
     * <ul>
     *   <li>写入延迟高</li>
     *   <li>数据可靠性高</li>
     *   <li>适合关键数据</li>
     * </ul>
     */
    STRONG
}
