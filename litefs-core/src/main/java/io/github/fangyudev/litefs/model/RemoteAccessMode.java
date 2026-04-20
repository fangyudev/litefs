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

/**
 * 远程访问模式枚举
 * 
 * <p>定义跨节点访问存储引擎的通信方式。</p>
 * 
 * <h3>当前支持：</h3>
 * <table border="1">
 *   <tr><th>模式</th><th>性能</th><th>依赖</th><th>适用场景</th></tr>
 *   <tr>
 *     <td>HTTP</td>
 *     <td>中等</td>
 *     <td>无额外依赖</td>
 *     <td>默认方案，简单易用，支持流式传输</td>
 *   </tr>
 * </table>
 * 
 * <p><b>设计说明：</b>LiteFS 定位为轻量级组件，HTTP 已能满足文件传输需求。
 * 文件传输场景的瓶颈在磁盘 IO 和网络带宽，协议开销差异可忽略。</p>
 */
public enum RemoteAccessMode {

    /**
     * HTTP 方式（默认且唯一）
     * 
     * <p>使用 HTTP 协议访问远程节点的 REST API。
     * 简单易用，无需额外依赖，适合大多数场景。</p>
     * 
     * <p>特点：</p>
     * <ul>
     *   <li>基于 HTTP/1.1 协议</li>
     *   <li>支持流式传输，适合大文件</li>
     *   <li>零额外依赖（JDK 内置 HttpURLConnection）</li>
     *   <li>易于调试和监控</li>
     *   <li>跨语言支持广泛</li>
     * </ul>
     */
    HTTP
}
