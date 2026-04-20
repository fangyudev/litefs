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

import io.github.fangyudev.litefs.model.StorageNode;

import java.util.List;

/**
 * 服务注册接口
 * 用于存储节点的注册、发现和心跳管理
 * 
 * <p>内置实现：</p>
 * <ul>
 *   <li>StaticServiceRegistry - 静态配置的节点列表（默认实现）</li>
 * </ul>
 * 
 * <p>可扩展实现：</p>
 * <ul>
 *   <li>NacosServiceRegistry - 阿里云Nacos注册中心</li>
 *   <li>ConsulServiceRegistry - HashiCorp Consul注册中心</li>
 *   <li>EurekaServiceRegistry - Netflix Eureka注册中心</li>
 * </ul>
 * 
 * <p>配置示例：</p>
 * <pre>
 * # 使用静态注册中心（默认）
 * litefs:
 *   registry:
 *     type: static
 *     static:
 *       nodes:
 *         - id: node-1
 *           host: 192.168.1.1
 *           port: 8080
 * 
 * # 使用 Nacos（自动复用 spring.cloud.nacos 配置）
 * litefs:
 *   registry:
 *     type: nacos
 * 
 * # 使用 Eureka（需要配置 Spring Cloud Eureka）
 * litefs:
 *   registry:
 *     type: eureka
 * </pre>
 */
public interface ServiceRegistry {

    /**
     * 注册存储节点
     * 
     * @param node 存储节点信息
     */
    void register(StorageNode node);

    /**
     * 注销存储节点
     * 
     * @param nodeId 节点ID
     */
    void deregister(String nodeId);

    /**
     * 发现所有可用节点
     * 
     * @return 存储节点列表
     */
    List<StorageNode> discover();

    /**
     * 获取指定节点信息
     * 
     * @param nodeId 节点ID
     * @return 存储节点信息，不存在则返回null
     */
    StorageNode get(String nodeId);

    /**
     * 发送心跳
     * 更新节点的最后心跳时间
     * 
     * @param nodeId 节点ID
     */
    void heartbeat(String nodeId);

    /**
     * 初始化注册中心
     */
    void init();

    /**
     * 关闭注册中心
     * 释放资源
     */
    void shutdown();
}
