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

import io.github.fangyudev.litefs.impl.queue.InMemoryMessageQueue;
import io.github.fangyudev.litefs.impl.queue.RedisMessageQueue;

/**
 * 消息队列 SPI 接口，用于异步副本复制的消息传递。
 * 
 * <p><b>内置实现：</b></p>
 * <ul>
 *   <li>{@link InMemoryMessageQueue} - 内存队列，适用于单机开发测试</li>
 *   <li>{@link RedisMessageQueue} - Redis队列，适用于分布式环境</li>
 * </ul>
 * 
 * <p><b>实现要求：</b></p>
 * <ul>
 *   <li>支持消息持久化，避免消息丢失</li>
 *   <li>建议实现消费确认机制，保证消息至少被消费一次</li>
 *   <li>消息对象需要支持序列化</li>
 * </ul>
 * 
 * @see MessageListener
 * @see InMemoryMessageQueue
 * @see RedisMessageQueue
 */
public interface MessageQueue {

    /**
     * 发送消息到指定主题。
     * 
     * @param topic 主题名称
     * @param message 消息内容，必须可序列化
     */
    void send(String topic, Object message);

    /**
     * 发送延迟消息。
     * 
     * @param topic 主题名称
     * @param message 消息内容
     * @param delayMs 延迟时间（毫秒）
     */
    void sendDelay(String topic, Object message, long delayMs);

    /**
     * 订阅主题。
     * 
     * <p>监听器的 onMessage 方法应该尽快返回，避免阻塞消费线程。</p>
     * 
     * @param topic 主题名称
     * @param listener 消息监听器
     */
    void subscribe(String topic, MessageListener listener);
}
