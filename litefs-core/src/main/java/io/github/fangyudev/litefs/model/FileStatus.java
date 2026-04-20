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
 * 文件状态枚举
 * 用于表示文件在系统中的生命周期状态
 */
public enum FileStatus {
    /**
     * 上传中 - 文件正在上传，尚未完成
     */
    PENDING,
    
    /**
     * 已提交 - 文件上传完成，可以正常访问
     */
    COMMITTED,
    
    /**
     * 已删除 - 文件已被标记删除，不可访问
     */
    DELETED
}
