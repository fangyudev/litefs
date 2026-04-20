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
import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.spi.MetadataStore;
import io.github.fangyudev.litefs.spi.ServiceRegistry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 直接访问URL生成器
 * 
 * <p>根据文件所在节点生成直接访问该节点的URL，无需通过网关。
 * 适用于没有统一网关入口的场景。</p>
 * 
 * <h3>URL格式：</h3>
 * <ul>
 *   <li>普通URL: http://{nodeHost}:{nodePort}{pathPrefix}/{fileId}</li>
 *   <li>签名URL: http://{nodeHost}:{nodePort}{pathPrefix}/{fileId}?token={signature}&expire={timestamp}</li>
 *   <li>下载URL: http://{nodeHost}:{nodePort}{pathPrefix}/{fileId}/download</li>
 *   <li>缩略图URL: http://{nodeHost}:{nodePort}{pathPrefix}/{thumbnailId}/thumbnail</li>
 * </ul>
 * 
 * <h3>工作原理：</h3>
 * <pre>
 * getUrl(fileId)
 *     ↓
 * 查询 FileMetadata 获取 nodeId
 *     ↓
 * 从 ServiceRegistry 获取节点地址 (host:port)
 *     ↓
 * 拼接生成 URL
 * </pre>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * DirectUrlGenerator generator = new DirectUrlGenerator(
 *     metadataStore,
 *     serviceRegistry,
 *     "/api/files",
 *     "my-secret-key"
 * );
 * 
 * String url = generator.generateUrl("file123");
 * // 输出: http://192.168.1.10:8080/api/files/file123
 * }</pre>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>没有统一网关的部署环境</li>
 *   <li>节点直接对外暴露服务</li>
 *   <li>需要精确控制访问哪个节点</li>
 * </ul>
 * 
 * @see GatewayUrlGenerator
 * @see UrlGenerator
 */
public class DirectUrlGenerator implements UrlGenerator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final MetadataStore metadataStore;
    private final ServiceRegistry serviceRegistry;
    private final String pathPrefix;
    private final String secretKey;

    /**
     * 构造函数
     * 
     * @param metadataStore 元数据存储（用于查询文件所在节点）
     * @param serviceRegistry 服务注册中心（用于获取节点地址）
     * @param pathPrefix URL路径前缀（如 /api/files）
     * @param secretKey 签名密钥
     */
    public DirectUrlGenerator(MetadataStore metadataStore,
                              ServiceRegistry serviceRegistry,
                              String pathPrefix,
                              String secretKey) {
        this.metadataStore = metadataStore;
        this.serviceRegistry = serviceRegistry;
        this.pathPrefix = pathPrefix;
        this.secretKey = secretKey;
    }

    /**
     * 构造函数（使用默认密钥）
     * 
     * @param metadataStore 元数据存储
     * @param serviceRegistry 服务注册中心
     * @param pathPrefix URL路径前缀
     */
    public DirectUrlGenerator(MetadataStore metadataStore,
                              ServiceRegistry serviceRegistry,
                              String pathPrefix) {
        this(metadataStore, serviceRegistry, pathPrefix, "default-secret-key");
    }

    /**
     * 生成普通访问URL
     * 
     * <p>根据文件所在节点动态生成URL。</p>
     * 
     * @param fileId 文件ID
     * @return 访问URL
     * @throws IllegalStateException 当文件不存在或节点不可达时抛出
     */

    @Override
    public String generateUrl(String fileId) {
        String nodeAddress = getNodeAddress(fileId);
        return "http://" + nodeAddress + pathPrefix + "/" + fileId;
    }

    /**
     * 生成带签名的临时访问URL
     * 
     * @param fileId 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 带签名的访问URL
     */
    @Override
    public String generateSignedUrl(String fileId, long expireSeconds) {
        String nodeAddress = getNodeAddress(fileId);
        long expireTime = System.currentTimeMillis() / 1000 + expireSeconds;
        String signature = sign(fileId + ":" + expireTime);

        return String.format("http://%s%s/%s?token=%s&expire=%d",
                nodeAddress, pathPrefix, fileId, signature, expireTime);
    }

    /**
     * 生成下载URL
     * 
     * @param fileId 文件ID
     * @return 下载URL
     */
    @Override
    public String generateDownloadUrl(String fileId) {
        String nodeAddress = getNodeAddress(fileId);
        return "http://" + nodeAddress + pathPrefix + "/" + fileId + "/download";
    }

    /**
     * 生成缩略图访问URL
     * 
     * @param thumbnailId 缩略图文件ID
     * @return 缩略图访问URL
     */
    @Override
    public String generateThumbnailUrl(String thumbnailId) {
        String nodeAddress = getNodeAddress(thumbnailId);
        return "http://" + nodeAddress + pathPrefix + "/" + thumbnailId + "/thumbnail";
    }

    /**
     * 验证签名URL是否有效
     * 
     * @param fileId 文件ID
     * @param token 签名token
     * @param expireTime 过期时间戳
     * @return 有效返回true，过期或签名不匹配返回false
     */
    public boolean validateSignature(String fileId, String token, long expireTime) {
        if (System.currentTimeMillis() / 1000 > expireTime) {
            return false;
        }
        String expected = sign(fileId + ":" + expireTime);
        return expected.equals(token);
    }

    /**
     * 获取文件所在节点的地址
     * 
     * @param fileId 文件ID
     * @return 节点地址（host:port格式）
     * @throws IllegalStateException 当文件不存在或节点不可达时抛出
     */
    private String getNodeAddress(String fileId) {
        FileMetadata metadata = metadataStore.get(fileId);
        if (metadata == null) {
            throw new IllegalStateException("File not found: " + fileId);
        }

        String nodeId = metadata.getStorageNodeId();
        if (nodeId == null) {
            throw new IllegalStateException("File has no node assigned: " + fileId);
        }

        StorageNode node = serviceRegistry.get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Node not found in registry: " + nodeId);
        }

        return node.getHost() + ":" + node.getPort();
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
     * 获取路径前缀
     * 
     * @return 路径前缀
     */
    public String getPathPrefix() {
        return pathPrefix;
    }
}
