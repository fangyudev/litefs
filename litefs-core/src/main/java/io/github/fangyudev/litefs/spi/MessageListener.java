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

package io.github.fangyudev.litefs.spi;

/**
 * 消息监听器
 * 
 * <p>函数式接口，用于接收消息队列中的消息。</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * messageQueue.subscribe("replication-topic", (topic, message) -> {
 *     ReplicationTask task = (ReplicationTask) message;
 *     processReplication(task);
 * });
 * </pre>
 */
@FunctionalInterface
public interface MessageListener {

    /**
     * 接收消息
     * @param topic 主题
     * @param message 消息体
     */
    void onMessage(String topic, Object message);
}
