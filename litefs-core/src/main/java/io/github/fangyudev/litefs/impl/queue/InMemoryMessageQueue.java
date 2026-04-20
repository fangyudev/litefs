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

package io.github.fangyudev.litefs.impl.queue;

import io.github.fangyudev.litefs.spi.MessageListener;
import io.github.fangyudev.litefs.spi.MessageQueue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存消息队列实现
 * 
 * <p>基于 JVM 内存实现的简单消息队列，适用于单机开发测试场景。
 * 不依赖任何外部组件，启动即可使用。</p>
 * 
 * <h3>工作原理：</h3>
 * <ul>
 *   <li><b>发送消息</b> - 直接调用订阅者的 onMessage 方法，同步执行</li>
 *   <li><b>延迟消息</b> - 使用 ScheduledExecutorService 延迟发送</li>
 *   <li><b>订阅主题</b> - 将监听器注册到内部 Map 中</li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>本地开发测试 - 无需搭建 Redis/RabbitMQ 等中间件</li>
 *   <li>单元测试 - 快速执行，无外部依赖</li>
 *   <li>单机应用 - 只有一个进程，不需要跨进程通信</li>
 * </ul>
 * 
 * <h3>不适用场景：</h3>
 * <ul>
 *   <li>生产环境 - 进程重启后消息丢失</li>
 *   <li>分布式系统 - 无法跨进程传递消息</li>
 *   <li>高可靠场景 - 没有消息持久化和确认机制</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建内存消息队列
 * MessageQueue queue = new InMemoryMessageQueue();
 * 
 * // 订阅主题
 * queue.subscribe("litefs.replication", (topic, message) -> {
 *     System.out.println("Received: " + message);
 * });
 * 
 * // 发送消息（同步调用监听器）
 * queue.send("litefs.replication", "Hello World");
 * 
 * // 发送延迟消息（5秒后送达）
 * queue.sendDelay("litefs.replication", "Delayed", 5000);
 * }</pre>
 * 
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>消息发送是同步的，会阻塞直到所有监听器处理完成</li>
 *   <li>没有消息确认机制，监听器抛出异常会导致消息丢失</li>
 *   <li>延迟消息使用守护线程，进程退出时可能丢失</li>
 * </ul>
 * 
 * @see MessageQueue
 * @see RedisMessageQueue
 * @see RabbitMessageQueue
 */
public class InMemoryMessageQueue implements MessageQueue {

    /** 主题订阅者映射：key为主题名，value为该主题的监听器列表 */
    private final Map<String, List<MessageListener>> listeners = new ConcurrentHashMap<>();
    
    /** 延迟消息调度器，使用守护线程 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "litefs-inmemory-mq");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 发送消息到指定主题
     * 
     * <p>同步调用该主题下所有监听器的 onMessage 方法。
     * 如果没有订阅者，消息会被静默丢弃。</p>
     * 
     * <p>注意：此方法是同步执行的，会阻塞直到所有监听器处理完成。
     * 如果某个监听器抛出异常，后续监听器仍会继续执行。</p>
     * 
     * @param topic 主题名称
     * @param message 消息内容，可以是任意可序列化对象
     */
    @Override
    public void send(String topic, Object message) {
        List<MessageListener> topicListeners = listeners.get(topic);
        if (topicListeners == null || topicListeners.isEmpty()) {
            return;
        }
        for (MessageListener listener : topicListeners) {
            listener.onMessage(topic, message);
        }
    }

    /**
     * 发送延迟消息
     * 
     * <p>使用调度器在指定延迟后发送消息。
     * 延迟时间为 0 或负数时，立即发送。</p>
     * 
     * <p>注意：延迟消息存储在内存中，进程重启会丢失。</p>
     * 
     * @param topic 主题名称
     * @param message 消息内容
     * @param delayMs 延迟时间（毫秒）
     */
    @Override
    public void sendDelay(String topic, Object message, long delayMs) {
        if (delayMs <= 0) {
            send(topic, message);
            return;
        }
        scheduler.schedule(() -> send(topic, message), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 订阅主题
     * 
     * <p>将监听器注册到指定主题。一个主题可以有多个监听器。
     * 消息发送时，所有监听器都会收到消息。</p>
     * 
     * <p>使用 CopyOnWriteArrayList 保证线程安全，
     * 支持在遍历过程中动态添加监听器。</p>
     * 
     * @param topic 主题名称
     * @param listener 消息监听器
     */
    @Override
    public void subscribe(String topic, MessageListener listener) {
        listeners.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
    }
}
