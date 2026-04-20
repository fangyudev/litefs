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
 * 节点状态枚举
 * 用于表示存储节点的在线状态
 */
public enum NodeStatus {
    /**
     * 在线 - 节点正常工作，可以接收读写请求
     */
    ONLINE,
    
    /**
     * 离线 - 节点不可用，无法接收请求
     */
    OFFLINE
}
