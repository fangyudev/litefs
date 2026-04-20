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
 * 分块上传初始化结果
 * 
 * <p>包含上传会话ID和目标节点信息，用于分布式环境下的请求路由。</p>
 * 
 * <h3>使用场景：</h3>
 * <pre>
 * // 初始化上传
 * InitMultipartUploadResult result = fileClient.initMultipartUpload("video.mp4", fileSize, null);
 * 
 * // 获取 uploadId 和目标节点
 * String uploadId = result.getUploadId();
 * String targetNodeId = result.getTargetNodeId();
 * 
 * // 将后续请求路由到目标节点
 * // 分片上传请求 -> targetNodeId
 * // 完成上传请求 -> targetNodeId
 * </pre>
 */
public class InitMultipartUploadResult {

    /** 上传会话ID */
    private final String uploadId;

    /** 目标存储节点ID（所有分片和合并操作都在此节点执行） */
    private final String targetNodeId;

    /**
     * 构造函数
     * 
     * @param uploadId 上传会话ID
     * @param targetNodeId 目标存储节点ID
     */
    public InitMultipartUploadResult(String uploadId, String targetNodeId) {
        this.uploadId = uploadId;
        this.targetNodeId = targetNodeId;
    }

    public String getUploadId() {
        return uploadId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    @Override
    public String toString() {
        return "InitMultipartUploadResult{" +
                "uploadId='" + uploadId + '\'' +
                ", targetNodeId='" + targetNodeId + '\'' +
                '}';
    }
}
