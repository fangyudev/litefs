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

package io.github.fangyudev.litefs.api;

/**
 * URL生成器接口
 * 用于生成文件访问、下载等URL
 * 
 * <p>支持生成以下类型的URL：</p>
 * <ul>
 *   <li>普通访问URL - 永久有效</li>
 *   <li>签名URL - 临时有效，带过期时间和签名</li>
 *   <li>下载URL - 触发浏览器下载</li>
 * </ul>
 */
public interface UrlGenerator {

    /**
     * 生成文件访问URL（永久有效）
     * 
     * @param fileId 文件ID
     * @return 访问URL
     */
    String generateUrl(String fileId);

    /**
     * 生成带签名的临时访问URL
     * URL包含过期时间和签名，过期后无法访问
     * 
     * @param fileId 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 带签名的访问URL
     */
    String generateSignedUrl(String fileId, long expireSeconds);

    /**
     * 生成文件下载URL
     * 返回的URL会触发浏览器下载行为
     * 
     * @param fileId 文件ID
     * @return 下载URL
     */
    String generateDownloadUrl(String fileId);

    /**
     * 生成缩略图访问URL
     * 
     * <p>用于生成缩略图的访问地址。缩略图与原图使用不同的文件ID，
     * 因此需要单独生成URL。</p>
     * 
     * @param thumbnailId 缩略图文件ID（注意：不是原图ID）
     * @return 缩略图访问URL
     */
    String generateThumbnailUrl(String thumbnailId);

    /**
     * 验证签名URL是否有效
     * 
     * @param fileId 文件ID
     * @param token 签名token
     * @param expireTime 过期时间戳（秒级）
     * @return 有效返回true，过期或签名不匹配返回false
     */
    boolean validateSignature(String fileId, String token, long expireTime);
}
