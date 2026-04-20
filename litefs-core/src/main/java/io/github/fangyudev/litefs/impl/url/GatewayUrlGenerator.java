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

package io.github.fangyudev.litefs.impl.url;

import io.github.fangyudev.litefs.api.UrlGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 网关URL生成器
 * 生成通过网关访问文件的URL
 * 
 * <p>URL格式：</p>
 * <ul>
 *   <li>普通URL: {baseUrl}{pathPrefix}/{fileId}</li>
 *   <li>签名URL: {baseUrl}{pathPrefix}/{fileId}?token={signature}&expire={timestamp}</li>
 *   <li>下载URL: {baseUrl}{pathPrefix}/{fileId}/download</li>
 *   <li>缩略图URL: {baseUrl}{pathPrefix}/{fileId}/thumbnail?w={width}&h={height}</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * GatewayUrlGenerator generator = new GatewayUrlGenerator(
 *     "http://localhost:8080", 
 *     "/api/files", 
 *     "my-secret-key"
 * );
 * 
 * String url = generator.generateUrl("file123");
 * String signedUrl = generator.generateSignedUrl("file123", 3600);
 * </pre>
 */
public class GatewayUrlGenerator implements UrlGenerator {

    /** HMAC-SHA256算法名称 */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /** 网关基础URL */
    private final String baseUrl;
    
    /** 路径前缀 */
    private final String pathPrefix;
    
    /** 签名密钥 */
    private final String secretKey;

    /**
     * 构造函数
     * 
     * @param baseUrl 网关基础URL（如 http://localhost:8080）
     * @param pathPrefix 路径前缀（如 /api/files）
     * @param secretKey 签名密钥
     */
    public GatewayUrlGenerator(String baseUrl, String pathPrefix, String secretKey) {
        this.baseUrl = baseUrl;
        this.pathPrefix = pathPrefix;
        this.secretKey = secretKey;
    }

    /**
     * 构造函数（使用默认密钥）
     * 
     * @param baseUrl 网关基础URL
     * @param pathPrefix 路径前缀
     */
    public GatewayUrlGenerator(String baseUrl, String pathPrefix) {
        this(baseUrl, pathPrefix, "default-secret-key");
    }

    /**
     * 生成普通访问URL
     * 
     * @param fileId 文件ID
     * @return 访问URL
     */
    @Override
    public String generateUrl(String fileId) {
        return baseUrl + pathPrefix + "/" + fileId;
    }

    /**
     * 生成带签名的临时访问URL
     * URL包含过期时间和签名，过期后无法访问
     * 
     * @param fileId 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 带签名的访问URL
     */
    @Override
    public String generateSignedUrl(String fileId, long expireSeconds) {
        long expireTime = System.currentTimeMillis() / 1000 + expireSeconds;
        String signature = sign(fileId + ":" + expireTime);

        return String.format("%s%s/%s?token=%s&expire=%d",
                baseUrl, pathPrefix, fileId, signature, expireTime);
    }

    /**
     * 生成下载URL
     * 返回的URL会触发浏览器下载行为
     * 
     * @param fileId 文件ID
     * @return 下载URL
     */
    @Override
    public String generateDownloadUrl(String fileId) {
        return baseUrl + pathPrefix + "/" + fileId + "/download";
    }

    /**
     * 生成缩略图访问URL
     * 
     * <p>缩略图URL格式：{baseUrl}{pathPrefix}/{thumbnailId}/thumbnail</p>
     * 
     * @param thumbnailId 缩略图文件ID
     * @return 缩略图访问URL
     */
    @Override
    public String generateThumbnailUrl(String thumbnailId) {
        return baseUrl + pathPrefix + "/" + thumbnailId + "/thumbnail";
    }

    /**
     * 验证签名URL是否有效
     * 
     * @param fileId 文件ID
     * @param token 签名token
     * @param expireTime 过期时间戳
     * @return 有效返回true，过期或签名不匹配返回false
     */
    public boolean

    validateSignature(String fileId, String token, long expireTime) {
        // 检查是否过期
        if (System.currentTimeMillis() / 1000 > expireTime) {
            return false;
        }

        // 验证签名
        String expected = sign(fileId + ":" + expireTime);
        return expected.equals(token);
    }

    /**
     * 使用HMAC-SHA256生成签名
     * 
     * @param data 待签名数据
     * @return Base64 URL安全编码的签名
     */
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign data", e);
        }
    }

    /**
     * 获取网关基础URL
     * 
     * @return 基础URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 获取路径前缀
     * 
     * @return 路径前缀
     */
    public String getPathPrefix() {
        return pathPrefix;
    }
}
