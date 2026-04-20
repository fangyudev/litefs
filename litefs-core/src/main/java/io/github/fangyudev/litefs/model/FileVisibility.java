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
 * 文件访问权限类型
 * 
 * <p>定义文件的访问权限级别：</p>
 * <ul>
 *   <li>PRIVATE - 私有，需要签名验证才能访问</li>
 *   <li>PUBLIC - 公开，任何人都可以直接访问</li>
 *   <li>TEMPORARY - 临时，有效期内公开访问，过期后变为私有</li>
 * </ul>
 */
public enum FileVisibility {
    
    /**
     * 私有文件
     * 必须携带有效签名才能访问
     */
    PRIVATE,
    
    /**
     * 公开文件
     * 任何人都可以直接访问，无需签名
     */
    PUBLIC,
    
    /**
     * 临时文件
     * 在 expireTime 前可公开访问，过期后变为私有
     */
    TEMPORARY
}
