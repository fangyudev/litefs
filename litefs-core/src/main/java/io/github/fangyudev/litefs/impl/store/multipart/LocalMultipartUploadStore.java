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

package io.github.fangyudev.litefs.impl.store.multipart;

import io.github.fangyudev.litefs.model.MultipartUpload;
import io.github.fangyudev.litefs.model.PartInfo;
import io.github.fangyudev.litefs.spi.MultipartUploadStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地内存分块上传会话存储
 * 
 * <p>基于 ConcurrentHashMap 的本地内存存储实现，仅适用于单机模式。</p>
 * 
 * <h3>特性：</h3>
 * <ul>
 *   <li><b>高性能</b> - 内存操作，无网络开销</li>
 *   <li><b>线程安全</b> - 使用 ConcurrentHashMap 保证并发安全</li>
 *   <li><b>单机限定</b> - 不支持跨节点共享，仅适用于单机部署</li>
 * </ul>
 * 
 * <h3>警告：</h3>
 * <p>在分布式模式下使用此实现会导致 "Upload not found" 错误，
 * 因为不同节点无法共享上传会话状态。</p>
 */
public class LocalMultipartUploadStore implements MultipartUploadStore {

    private static final Logger log = LoggerFactory.getLogger(LocalMultipartUploadStore.class);

    private final Map<String, MultipartUpload> uploads = new ConcurrentHashMap<>();

    @Override
    public void init() {
        log.info("Local multipart upload store initialized (standalone mode only)");
    }

    @Override
    public void save(MultipartUpload upload) {
        if (upload == null || upload.getUploadId() == null) {
            return;
        }
        uploads.put(upload.getUploadId(), upload);
    }

    @Override
    public MultipartUpload get(String uploadId) {
        if (uploadId == null) {
            return null;
        }
        return uploads.get(uploadId);
    }

    @Override
    public void delete(String uploadId) {
        if (uploadId == null) {
            return;
        }
        uploads.remove(uploadId);
    }

    @Override
    public boolean addPart(String uploadId, int partNumber, PartInfo partInfo) {
        if (uploadId == null || partInfo == null) {
            return false;
        }
        MultipartUpload upload = uploads.get(uploadId);
        if (upload == null) {
            return false;
        }
        if (upload.isExpired()) {
            return false;
        }
        upload.addPart(partNumber, partInfo);
        return true;
    }

    @Override
    public List<String> getExpiredUploadIds() {
        List<String> expiredIds = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, MultipartUpload> entry : uploads.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredIds.add(entry.getKey());
            }
        }
        return expiredIds;
    }

    @Override
    public boolean exists(String uploadId) {
        if (uploadId == null) {
            return false;
        }
        return uploads.containsKey(uploadId);
    }
}
