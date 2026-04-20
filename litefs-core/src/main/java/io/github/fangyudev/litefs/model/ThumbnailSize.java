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
 * 缩略图尺寸配置
 * 
 * <p>采用长边优先策略，保持原图宽高比。</p>
 * 
 * <p>工作原理：</p>
 * <ul>
 *   <li>指定最长边的像素值</li>
 *   <li>缩放时自动保持原图宽高比</li>
 *   <li>长边缩放到目标值，短边等比缩放</li>
 * </ul>
 * 
 * <p>示例：</p>
 * <pre>
 * 原图：1920 x 1080（横向图片）
 * maxEdge = 200
 * 
 * 计算过程：
 * 长边 1920 → 200
 * 短边 1080 → 1080 * (200/1920) = 112.5 ≈ 113
 * 
 * 结果：200 x 113
 * </pre>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 创建小尺寸配置（最长边 200px）
 * ThumbnailSize small = new ThumbnailSize(200);
 * 
 * // 创建中等尺寸配置（最长边 400px）
 * ThumbnailSize medium = new ThumbnailSize(400);
 * </pre>
 */
public class ThumbnailSize {

    /**
     * 最长边长度（像素）
     * 
     * <p>缩略图的最长边将被缩放到此值，另一边等比缩放。</p>
     * <p>取值范围：1-4096</p>
     */
    private int maxEdge;

    /**
     * 默认构造函数
     */
    public ThumbnailSize() {
    }

    /**
     * 构造函数
     * 
     * @param maxEdge 最长边长度（像素），取值范围 1-4096
     * @throws IllegalArgumentException 如果 maxEdge 不在有效范围内
     */
    public ThumbnailSize(int maxEdge) {
        validateMaxEdge(maxEdge);
        this.maxEdge = maxEdge;
    }

    /**
     * 获取最长边长度
     * 
     * @return 最长边长度（像素）
     */
    public int getMaxEdge() {
        return maxEdge;
    }

    /**
     * 设置最长边长度
     * 
     * @param maxEdge 最长边长度（像素），取值范围 1-4096
     * @throws IllegalArgumentException 如果 maxEdge 不在有效范围内
     */
    public void setMaxEdge(int maxEdge) {
        validateMaxEdge(maxEdge);
        this.maxEdge = maxEdge;
    }

    /**
     * 验证 maxEdge 参数的有效性
     * 
     * @param maxEdge 待验证的值
     * @throws IllegalArgumentException 如果值不在有效范围内
     */
    private void validateMaxEdge(int maxEdge) {
        if (maxEdge < 1 || maxEdge > 4096) {
            throw new IllegalArgumentException(
                "maxEdge must be between 1 and 4096, but was: " + maxEdge);
        }
    }

    @Override
    public String toString() {
        return "ThumbnailSize{" +
            "maxEdge=" + maxEdge +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThumbnailSize that = (ThumbnailSize) o;
        return maxEdge == that.maxEdge;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(maxEdge);
    }
}
