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

package io.github.fangyudev.litefs.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * 消息队列序列化工具类
 *
 * <p>提供对象与字节数组之间的转换功能，用于消息队列的消息传输。
 * 使用 Java 原生序列化机制实现。</p>
 *
 * <h3>工作原理：</h3>
 * <pre>
 * 发送消息：
 * Object → ObjectOutputStream → byte[] → 消息队列
 *
 * 接收消息：
 * 消息队列 → byte[] → ObjectInputStream → Object
 * </pre>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>Redis 消息队列 - 消息需要序列化为字节数组存储</li>
 *   <li>RabbitMQ 消息队列 - 消息需要序列化为字节数组传输</li>
 *   <li>RocketMQ 消息队列 - 消息需要序列化为字节数组传输</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 序列化对象
 * ReplicationTask task = ReplicationTask.of(metadata, strategy, request);
 * byte[] data = MessageQueueSerializerUtil.serialize(task);
 *
 * // 反序列化对象
 * Object obj = MessageQueueSerializerUtil.deserialize(data);
 * if (obj instanceof ReplicationTask task) {
 *     // 处理任务
 * }
 * }</pre>
 *
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>被序列化的对象必须实现 {@link java.io.Serializable} 接口</li>
 *   <li>对象内部引用的所有对象也必须可序列化</li>
 *   <li>序列化后的数据包含 Java 类型信息，可能导致版本兼容问题</li>
 *   <li>对于跨语言场景，建议改用 JSON 等通用格式</li>
 * </ul>
 *
 * <h3>性能考虑：</h3>
 * <p>Java 原生序列化性能一般，如果对性能有较高要求，可以考虑：</p>
 * <ul>
 *   <li>Kryo - 高性能 Java 序列化框架</li>
 *   <li>Protobuf - Google 的跨语言序列化框架</li>
 *   <li>FST - 快速序列化工具</li>
 * </ul>
 *
 * @see java.io.Serializable
 * @see java.io.ObjectOutputStream
 * @see java.io.ObjectInputStream
 */
public final class MessageQueueSerializerUtil {

    /**
     * 私有构造函数，防止实例化
     */
    private MessageQueueSerializerUtil() {
    }

    /**
     * 将对象序列化为字节数组
     *
     * <p>使用 Java 原生序列化机制将对象转换为字节数组。
     * 对象必须实现 {@link java.io.Serializable} 接口。</p>
     *
     * <p>序列化过程：</p>
     * <ol>
     *   <li>创建 ByteArrayOutputStream 作为输出缓冲区</li>
     *   <li>创建 ObjectOutputStream 包装输出流</li>
     *   <li>调用 writeObject 将对象写入流</li>
     *   <li>将缓冲区内容转换为字节数组返回</li>
     * </ol>
     *
     * @param value 要序列化的对象，为 null 时返回空数组
     * @return 序列化后的字节数组
     * @throws IllegalStateException 序列化失败时抛出
     */
    public static byte[] serialize(Object value) {
        if (value == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bout)) {
            oos.writeObject(value);
            oos.flush();
            return bout.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize message", e);
        }
    }

    /**
     * 将字节数组反序列化为对象
     *
     * <p>使用 Java 原生反序列化机制将字节数组还原为对象。
     * 返回的对象类型由序列化时的类型决定。</p>
     *
     * <p>反序列化过程：</p>
     * <ol>
     *   <li>创建 ByteArrayInputStream 包装输入数据</li>
     *   <li>创建 ObjectInputStream 包装输入流</li>
     *   <li>调用 readObject 从流中读取对象</li>
     *   <li>返回读取的对象</li>
     * </ol>
     *
     * <p>注意：反序列化时，对象的类定义必须在当前类路径中可用，
     * 否则会抛出 ClassNotFoundException。</p>
     *
     * @param data 序列化后的字节数组，为 null 或空数组时返回 null
     * @return 反序列化后的对象
     * @throws IllegalStateException 反序列化失败时抛出
     */
    public static Object deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bin)) {
            return ois.readObject();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize message", e);
        }
    }
}
