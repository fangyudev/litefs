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

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 副本策略
 * 定义文件的副本数量和放置策略，用于控制数据冗余和可靠性
 * 
 * <p>在分布式存储系统中，不同类型的文件对可靠性要求不同：</p>
 * <ul>
 *   <li>合同、证书等重要文件 - 需要高可靠性，多副本存储</li>
 *   <li>用户头像、商品图片 - 标准副本即可</li>
 *   <li>临时文件、缓存 - 单副本或不复制</li>
 * </ul>
 * 
 * <p>策略类型说明：</p>
 * <table border="1">
 *   <tr><th>类型</th><th>额外副本数</th><th>总份数</th><th>适用场景</th></tr>
 *   <tr><td>NONE</td><td>0</td><td>1</td><td>临时文件，无需冗余</td></tr>
 *   <tr><td>MINIMAL</td><td>1</td><td>2</td><td>普通文件，基本冗余</td></tr>
 *   <tr><td>STANDARD</td><td>2</td><td>3</td><td>默认策略，平衡可靠性和成本</td></tr>
 *   <tr><td>HIGH</td><td>4</td><td>5</td><td>重要文件，高可靠性</td></tr>
 *   <tr><td>ALL_NODES</td><td>-1</td><td>N</td><td>关键数据，全节点复制</td></tr>
 *   <tr><td>CUSTOM</td><td>自定义</td><td>自定义</td><td>特殊需求场景</td></tr>
 * </table>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 使用预定义策略
 * ReplicationStrategy strategy = ReplicationStrategy.standard();
 * 
 * // 使用自定义副本数
 * ReplicationStrategy strategy = ReplicationStrategy.custom(4);
 * 
 * // 指定特定节点
 * ReplicationStrategy strategy = ReplicationStrategy.specified(
 *     Arrays.asList("node-1", "node-2", "node-3")
 * );
 * </pre>
 */
public class ReplicationStrategy implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 副本策略类型 */
    private final StrategyType type;
    
    /** 额外副本数量（不含原始文件），-1表示全节点复制，0表示不复制 */
    private final int replicas;
    
    /** 放置策略类型 */
    private final PlacementType placement;
    
    /** 指定节点列表（仅当placement=SPECIFIED时使用） */
    private final List<String> specifiedNodes;

    /**
     * 构造函数
     * 
     * @param type 策略类型
     * @param replicas 副本数量
     * @param placement 放置策略
     * @param specifiedNodes 指定节点列表
     */
    public ReplicationStrategy(StrategyType type, int replicas, PlacementType placement, List<String> specifiedNodes) {
        this.type = type;
        this.replicas = replicas;
        this.placement = placement;
        this.specifiedNodes = specifiedNodes != null ? specifiedNodes : Collections.emptyList();
    }

    public StrategyType getType() {
        return type;
    }

    public int getReplicas() {
        return replicas;
    }

    public PlacementType getPlacement() {
        return placement;
    }

    public List<String> getSpecifiedNodes() {
        return specifiedNodes;
    }

    /**
     * 策略类型枚举
     * 定义预定义的副本策略类型
     */
    public enum StrategyType {
        /** 无额外副本 - 适用于临时文件 */
        NONE,
        
        /** 最小副本（1个额外副本，共2份） - 适用于普通文件 */
        MINIMAL,
        
        /** 标准副本（2个额外副本，共3份） - 默认策略 */
        STANDARD,
        
        /** 高可靠（4个额外副本，共5份） - 适用于重要文件 */
        HIGH,
        
        /** 全节点复制 - 适用于关键数据 */
        ALL_NODES,
        
        /** 自定义副本数 */
        CUSTOM
    }

    /**
     * 放置策略枚举
     * 定义副本节点的选择方式
     */
    public enum PlacementType {
        /** 平衡分布 - 尽量分散到不同节点，提高容错能力 */
        BALANCED,
        
        /** 就近放置 - 优先选择就近节点，降低访问延迟 */
        LOCALITY,
        
        /** 指定节点 - 用户显式指定存储节点 */
        SPECIFIED
    }

    /**
     * 创建无额外副本策略（不复制）
     * 适用于临时文件、缓存等不需要冗余的场景
     * 文件只存储在原始节点，无额外副本
     * 
     * @return 无额外副本策略
     */
    public static ReplicationStrategy none() {
        return new ReplicationStrategy(StrategyType.NONE, 0, PlacementType.BALANCED, null);
    }

    /**
     * 创建最小副本策略（1个额外副本，共2份）
     * 适用于普通文件，提供基本的数据冗余
     * 
     * @return 最小副本策略
     */
    public static ReplicationStrategy minimal() {
        return new ReplicationStrategy(StrategyType.MINIMAL, 1, PlacementType.BALANCED, null);
    }

    /**
     * 创建标准副本策略（2个额外副本，共3份）
     * 默认策略，平衡可靠性和存储成本
     * 
     * @return 标准副本策略
     */
    public static ReplicationStrategy standard() {
        return new ReplicationStrategy(StrategyType.STANDARD, 2, PlacementType.BALANCED, null);
    }

    /**
     * 创建高可靠策略（4个额外副本，共5份）
     * 适用于合同、证书等重要文件
     * 
     * @return 高可靠策略
     */
    public static ReplicationStrategy high() {
        return new ReplicationStrategy(StrategyType.HIGH, 4, PlacementType.BALANCED, null);
    }

    /**
     * 创建全节点复制策略
     * 在所有可用节点上保存副本，适用于关键数据
     * 
     * @return 全节点复制策略
     */
    public static ReplicationStrategy allNodes() {
        return new ReplicationStrategy(StrategyType.ALL_NODES, -1, PlacementType.BALANCED, null);
    }

    /**
     * 创建自定义副本数策略
     * 
     * @param replicas 副本数量
     * @return 自定义副本策略
     */
    public static ReplicationStrategy custom(int replicas) {
        return new ReplicationStrategy(StrategyType.CUSTOM, replicas, PlacementType.BALANCED, null);
    }

    /**
     * 创建指定节点策略
     * 将副本放置在用户指定的节点上
     * 
     * @param nodes 目标节点ID列表
     * @return 指定节点策略
     */
    public static ReplicationStrategy specified(List<String> nodes) {
        return new ReplicationStrategy(StrategyType.CUSTOM, nodes != null ? nodes.size() : 0, PlacementType.SPECIFIED, nodes);
    }
}
