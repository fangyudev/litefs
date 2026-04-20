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
import io.github.fangyudev.litefs.util.MessageQueueSerializerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis 消息队列实现
 * 
 * <p>基于 Redis List 数据结构和 BLPOP 命令实现的消息队列，
 * 适用于分布式环境，支持跨进程消息传递。</p>
 * 
 * <h3>工作原理：</h3>
 * <ul>
 *   <li><b>发送消息</b> - 使用 RPUSH 将消息推入队列右端</li>
 *   <li><b>消费消息</b> - 使用 BLPOP 阻塞式从队列左端弹出消息</li>
 *   <li><b>延迟消息</b> - 使用 ScheduledExecutorService 延迟发送</li>
 *   <li><b>序列化</b> - 使用 Java 序列化将对象转换为字节数组</li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>分布式系统 - 多个进程需要共享消息队列</li>
 *   <li>生产环境 - Redis 提供消息持久化能力</li>
 *   <li>已有 Redis - 复用现有 Redis 基础设施</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建 Redis 消息队列
 * MessageQueue queue = new RedisMessageQueue(
 *     "localhost", 6379,     // Redis 地址
 *     "password",            // 密码（可为 null）
 *     0,                     // 数据库索引
 *     3000,                  // 连接超时（毫秒）
 *     "litefs:queue:"        // key 前缀
 * );
 * 
 * // 订阅主题（会启动后台消费线程）
 * queue.subscribe("litefs.replication", (topic, message) -> {
 *     if (message instanceof ReplicationTask task) {
 *         // 处理复制任务
 *     }
 * });
 * 
 * // 发送消息
 * queue.send("litefs.replication", replicationTask);
 * 
 * // 发送延迟消息
 * queue.sendDelay("litefs.replication", task, 5000);
 * }</pre>
 * 
 * <h3>Redis Key 命名规则：</h3>
 * <pre>
 * 完整 Key = keyPrefix + topic
 * 例如：litefs:queue:litefs.replication
 * </pre>
 * 
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>订阅操作会启动一个守护线程进行消费，进程退出时线程会自动终止</li>
 *   <li>消费失败会自动重试，间隔 1 秒</li>
 *   <li>消息使用 Java 序列化，确保消息对象实现 Serializable</li>
 *   <li>BLPOP 是阻塞操作，每个主题需要一个独立的消费线程</li>
 * </ul>
 * 
 * <h3>与其他实现的对比：</h3>
 * <table border="1">
 *   <tr><th>特性</th><th>RedisMessageQueue</th><th>InMemoryMessageQueue</th></tr>
 *   <tr><td>跨进程</td><td>支持</td><td>不支持</td></tr>
 *   <tr><td>持久化</td><td>支持</td><td>不支持</td></tr>
 *   <tr><td>外部依赖</td><td>Redis</td><td>无</td></tr>
 *   <tr><td>性能</td><td>高</td><td>极高</td></tr>
 * </table>
 * 
 * @see MessageQueue
 * @see InMemoryMessageQueue
 * @see MessageQueueSerializer
 */
public class RedisMessageQueue implements MessageQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageQueue.class);

    /** Redis 连接池 */
    private final JedisPool pool;
    
    /** Redis Key 前缀，用于区分不同应用 */
    private final String keyPrefix;
    
    /** 延迟消息调度器 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "litefs-redis-mq-delay");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 构造函数
     * 
     * <p>创建 Redis 连接池，支持密码认证和数据库选择。</p>
     * 
     * @param host Redis 主机地址
     * @param port Redis 端口
     * @param password Redis 密码，无密码时传 null 或空字符串
     * @param database Redis 数据库索引（0-15）
     * @param timeoutMs 连接超时时间（毫秒）
     * @param keyPrefix Redis Key 前缀，用于区分不同应用
     */
    public RedisMessageQueue(String host, int port, String password, int database, int timeoutMs, String keyPrefix) {
        JedisPoolConfig config = new JedisPoolConfig();
        if (password != null && !password.isBlank()) {
            this.pool = new JedisPool(config, host, port, timeoutMs, password, database);
        } else {
            this.pool = new JedisPool(config, host, port, timeoutMs, null, database);
        }
        this.keyPrefix = keyPrefix != null ? keyPrefix : "litefs:queue:";
    }

    /**
     * 发送消息到指定主题
     * 
     * <p>使用 RPUSH 命令将消息推入 Redis List 队列。
     * 消息会被序列化为字节数组存储。</p>
     * 
     * @param topic 主题名称
     * @param message 消息内容，必须实现 Serializable 接口
     */
    @Override
    public void send(String topic, Object message) {
        String key = keyPrefix + topic;
        byte[] payload = MessageQueueSerializerUtil.serialize(message);
        try (Jedis jedis = pool.getResource()) {
            jedis.rpush(key.getBytes(), payload);
        }
    }

    /**
     * 发送延迟消息
     * 
     * <p>使用调度器在指定延迟后发送消息。
     * 延迟时间为 0 或负数时，立即发送。</p>
     * 
     * <p>注意：延迟消息在发送前存储在本地内存中，
     * 如果进程在延迟期间退出，消息会丢失。</p>
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
     * <p>启动一个后台守护线程，使用 BLPOP 阻塞式消费消息。
     * 消费到消息后，会调用监听器的 onMessage 方法处理。</p>
     * 
     * <p>错误处理：</p>
     * <ul>
     *   <li>消费失败时会记录日志并等待 1 秒后重试</li>
     *   <li>线程被中断时会退出消费循环</li>
     * </ul>
     * 
     * <p>注意：每个主题会启动一个独立的消费线程。
     * 如果需要订阅多个主题，建议控制主题数量以避免线程过多。</p>
     * 
     * @param topic 主题名称
     * @param listener 消息监听器，不能为 null
     */
    @Override
    public void subscribe(String topic, MessageListener listener) {
        Objects.requireNonNull(listener, "listener");
        String key = keyPrefix + topic;
        Thread consumer = new Thread(() -> {
            while (true) {
                try (Jedis jedis = pool.getResource()) {
                    List<byte[]> result = jedis.blpop(30, key.getBytes());
                    if (result != null && result.size() >= 2) {
                        byte[] payload = result.get(1);
                        Object message = MessageQueueSerializerUtil.deserialize(payload);
                        listener.onMessage(topic, message);
                    }
                } catch (Exception e) {
                    log.warn("Redis queue consume error, will retry", e);
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "litefs-redis-mq-consumer-" + topic);
        consumer.setDaemon(true);
        consumer.start();
    }
}
