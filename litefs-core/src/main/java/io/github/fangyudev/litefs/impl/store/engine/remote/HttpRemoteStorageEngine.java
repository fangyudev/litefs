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

package io.github.fangyudev.litefs.impl.store.engine.remote;

import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import io.github.fangyudev.litefs.model.StorageNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 远程存储引擎代理
 * 
 * <p>通过 HTTP 协议访问远程节点的存储引擎。这是默认的跨节点访问方式，
 * 无需额外依赖，简单易用。</p>
 * 
 * <h3>工作原理：</h3>
 * <pre>
 * 本地节点 ──HTTP请求──> 远程节点
 *          /internal/files/{fileId}
 * </pre>
 * 
 * <h3>API 端点：</h3>
 * <ul>
 *   <li>GET /internal/files/{fileId} - 读取文件</li>
 *   <li>POST /internal/files/{fileId} - 写入文件</li>
 *   <li>DELETE /internal/files/{fileId} - 删除文件</li>
 *   <li>HEAD /internal/files/{fileId} - 检查文件是否存在</li>
 * </ul>
 * 
 * <h3>连接配置：</h3>
 * <ul>
 *   <li>连接超时：5秒（可配置）</li>
 *   <li>读取超时：30秒（可配置）</li>
 *   <li>支持连接复用</li>
 * </ul>
 * 
 * @see StorageEngine
 * @see DistributedStorageEngineRouter
 */
public class HttpRemoteStorageEngine implements StorageEngine {

    private static final Logger log = LoggerFactory.getLogger(HttpRemoteStorageEngine.class);

    private static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    private static final int DEFAULT_READ_TIMEOUT = 30000;
    private static final String INTERNAL_API_PATH = "/internal/files";

    /** 远程节点ID */
    private final String nodeId;
    
    /** 服务注册中心，用于获取节点地址 */
    private final ServiceRegistry serviceRegistry;
    
    /** 连接超时（毫秒） */
    private final int connectTimeout;
    
    /** 读取超时（毫秒） */
    private final int readTimeout;
    
    /** 节点地址缓存，避免频繁查询注册中心 */
    private volatile String cachedNodeUrl;
    
    /** 缓存时间戳 */
    private volatile long cacheTimestamp;
    
    /** 缓存有效期（5分钟） */
    private static final long CACHE_TTL = 5 * 60 * 1000;

    /**
     * 构造函数（使用默认超时配置）
     * 
     * @param nodeId 远程节点ID
     * @param serviceRegistry 服务注册中心
     */
    public HttpRemoteStorageEngine(String nodeId, ServiceRegistry serviceRegistry) {
        this(nodeId, serviceRegistry, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    /**
     * 构造函数（自定义超时配置）
     * 
     * @param nodeId 远程节点ID
     * @param serviceRegistry 服务注册中心
     * @param connectTimeout 连接超时（毫秒）
     * @param readTimeout 读取超时（毫秒）
     */
    public HttpRemoteStorageEngine(String nodeId, ServiceRegistry serviceRegistry,
                                   int connectTimeout, int readTimeout) {
        this.nodeId = nodeId;
        this.serviceRegistry = serviceRegistry;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * 读取远程文件
     * 
     * <p>通过 HTTP GET 请求从远程节点读取文件内容。</p>
     * 
     * <p>使用流式传输，适合大文件。通过先读取响应头确认连接成功后，
     * 再返回输入流，避免连接提前关闭的问题。</p>
     * 
     * @param fileId 文件ID
     * @return 文件输入流
     * @throws RuntimeException 读取失败时抛出
     */
    @Override
    public InputStream read(String fileId) {
        try {
            String url = getNodeUrl() + INTERNAL_API_PATH + "/" + fileId;
            log.info("HTTP GET request to read file: {}", url);
            
            HttpURLConnection conn = createConnection(url, "GET");
            
            int responseCode = conn.getResponseCode();
            log.info("HTTP response code: {} for file: {}", responseCode, fileId);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                long contentLength = conn.getContentLengthLong();
                log.info("Reading file from remote node {} for file: {}, size: {} bytes", nodeId, fileId, contentLength);
                
                // 直接返回输入流，流式传输
                // 注意：调用者需要负责关闭流，这里不能关闭连接
                return new HttpInputStreamWrapper(conn, conn.getInputStream());
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new RuntimeException("File not found: " + fileId);
            } else {
                throw new RuntimeException("Failed to read file from remote node: " + responseCode);
            }
        } catch (IOException e) {
            invalidateCache();
            throw new RuntimeException("Failed to read file from remote node: " + nodeId, e);
        }
    }

    /**
     * HTTP 输入流包装器
     * 
     * <p>包装 HttpURLConnection 的输入流，确保流关闭时同时关闭连接。</p>
     */
    private static class HttpInputStreamWrapper extends InputStream {
        private final HttpURLConnection connection;
        private final InputStream inputStream;
        private boolean closed = false;

        public HttpInputStreamWrapper(HttpURLConnection connection, InputStream inputStream) {
            this.connection = connection;
            this.inputStream = inputStream;
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return inputStream.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                try {
                    inputStream.close();
                } finally {
                    connection.disconnect();
                }
            }
        }

        @Override
        public int available() throws IOException {
            return inputStream.available();
        }
    }

    /**
     * 写入文件到远程节点
     * 
     * <p>通过 HTTP POST 请求将文件写入远程节点。</p>
     * 
     * @param fileId 文件ID
     * @param data 文件数据
     * @return 存储路径
     * @throws RuntimeException 写入失败时抛出
     */
    @Override
    public String write(String fileId, InputStream data) {
        try {
            String url = getNodeUrl() + INTERNAL_API_PATH + "/" + fileId;
            log.info("HTTP POST request to write file: {}", url);
            
            HttpURLConnection conn = createConnection(url, "POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            
            long totalBytes = 0;
            try (OutputStream os = conn.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = data.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
            }
            
            log.info("Sent {} bytes to remote node {} for file: {}", totalBytes, nodeId, fileId);
            
            int responseCode = conn.getResponseCode();
            log.info("HTTP response code: {} for write file: {}", responseCode, fileId);
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                String storagePath = parseStoragePathFromResponse(conn);
                String resultPath = storagePath != null ? storagePath : fileId;
                log.info("File written successfully to remote node {}, storagePath: {}", nodeId, resultPath);
                return resultPath;
            } else {
                throw new RuntimeException("Failed to write file to remote node: " + responseCode);
            }
        } catch (IOException e) {
            invalidateCache();
            throw new RuntimeException("Failed to write file to remote node: " + nodeId, e);
        }
    }

    /**
     * 从响应中解析存储路径
     * 
     * <p>响应格式：{"storagePath":"ab/cd/abcd..."}</p>
     * 
     * @param conn HTTP 连接
     * @return 存储路径，解析失败返回 null
     */
    private String parseStoragePathFromResponse(HttpURLConnection conn) {
        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            String response = baos.toString(StandardCharsets.UTF_8);
            
            int pathIndex = response.indexOf("\"storagePath\"");
            if (pathIndex == -1) {
                return null;
            }
            
            int valueStart = response.indexOf("\"", pathIndex + 14) + 1;
            int valueEnd = response.indexOf("\"", valueStart);
            if (valueStart > 0 && valueEnd > valueStart) {
                return response.substring(valueStart, valueEnd);
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 删除远程文件
     * 
     * <p>通过 HTTP DELETE 请求删除远程节点上的文件。</p>
     * 
     * @param fileId 文件ID
     */
    @Override
    public void delete(String fileId) {
        try {
            String url = getNodeUrl() + INTERNAL_API_PATH + "/" + fileId;
            HttpURLConnection conn = createConnection(url, "DELETE");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                throw new RuntimeException("Failed to delete file from remote node: " + responseCode);
            }
        } catch (IOException e) {
            invalidateCache();
            throw new RuntimeException("Failed to delete file from remote node: " + nodeId, e);
        }
    }

    /**
     * 检查远程文件是否存在
     * 
     * <p>通过 HTTP HEAD 请求检查文件是否存在。</p>
     * 
     * @param fileId 文件ID
     * @return true 表示文件存在
     */
    @Override
    public boolean exists(String fileId) {
        try {
            String url = getNodeUrl() + INTERNAL_API_PATH + "/" + fileId;
            HttpURLConnection conn = createConnection(url, "HEAD");
            
            int responseCode = conn.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            invalidateCache();
            return false;
        }
    }

    /**
     * 获取远程节点上的文件大小
     * 
     * @param fileId 文件ID
     * @return 文件大小（字节）
     */
    @Override
    public long getSize(String fileId) {
        try {
            String url = getNodeUrl() + INTERNAL_API_PATH + "/" + fileId;
            HttpURLConnection conn = createConnection(url, "HEAD");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String contentLength = conn.getHeaderField("Content-Length");
                if (contentLength != null) {
                    return Long.parseLong(contentLength);
                }
            }
            return -1;
        } catch (IOException e) {
            invalidateCache();
            return -1;
        }
    }

    /**
     * 获取文件存储路径
     * 
     * <p>对于远程存储，存储路径就是文件ID本身。</p>
     * 
     * @param fileId 文件ID
     * @return 存储路径
     */
    @Override
    public String getStoragePath(String fileId) {
        return fileId;
    }

    /**
     * 追加写入文件
     * 
     * <p>远程存储暂不支持追加写入，会抛出异常。</p>
     * 
     * @param fileId 文件ID
     * @param data 要追加的数据
     * @return 追加后的文件大小
     * @throws UnsupportedOperationException 不支持追加写入
     */
    @Override
    public long append(String fileId, InputStream data) {
        throw new UnsupportedOperationException("Append operation is not supported for remote storage engine");
    }

    /**
     * 获取节点 URL
     * 
     * <p>优先使用缓存，缓存过期后重新从注册中心获取。</p>
     * 
     * @return 节点 URL
     */
    private String getNodeUrl() {
        long now = System.currentTimeMillis();
        if (cachedNodeUrl != null && (now - cacheTimestamp) < CACHE_TTL) {
            log.debug("[HTTP-REMOTE] Using cached URL for node {}: {}", nodeId, cachedNodeUrl);
            return cachedNodeUrl;
        }
        
        log.info("[HTTP-REMOTE] Fetching node info from ServiceRegistry for nodeId: {}", nodeId);
        StorageNode node = serviceRegistry.get(nodeId);
        if (node == null) {
            log.error("[HTTP-REMOTE] ✗ Node not found in registry: nodeId={}", nodeId);
            throw new RuntimeException("Node not found in registry: " + nodeId);
        }
        
        log.info("[HTTP-REMOTE] Retrieved node info - nodeId={}, host={}, port={}", 
            node.getId(), node.getHost(), node.getPort());
        
        cachedNodeUrl = "http://" + node.getHost() + ":" + node.getPort();
        cacheTimestamp = now;
        
        log.info("[HTTP-REMOTE] ✓ Constructed URL for node {}: {}", nodeId, cachedNodeUrl);
        return cachedNodeUrl;
    }

    /**
     * 使缓存失效
     */
    private void invalidateCache() {
        cachedNodeUrl = null;
        cacheTimestamp = 0;
    }

    /**
     * 创建 HTTP 连接
     * 
     * @param url URL
     * @param method HTTP 方法
     * @return HTTP 连接
     * @throws IOException IO 异常
     */
    private HttpURLConnection createConnection(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestProperty("X-Node-Id", nodeId);
        conn.setRequestProperty("X-Internal-Request", "true");
        return conn;
    }
}
