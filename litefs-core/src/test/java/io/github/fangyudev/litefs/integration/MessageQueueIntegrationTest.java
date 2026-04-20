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

package io.github.fangyudev.litefs.integration;

import io.github.fangyudev.litefs.impl.queue.InMemoryMessageQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MessageQueueIntegrationTest {

    private InMemoryMessageQueue messageQueue;

    @BeforeEach
    void setUp() {
        messageQueue = new InMemoryMessageQueue();
    }

    @Test
    void testSendAndReceive() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> receivedMessage = new AtomicReference<>();

        messageQueue.subscribe("test-topic", (topic, message) -> {
            receivedMessage.set(message);
            latch.countDown();
        });

        messageQueue.send("test-topic", "Hello World");

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Should receive message within 1 second");
        assertEquals("Hello World", receivedMessage.get());
    }

    @Test
    void testMultipleSubscribers() throws InterruptedException {
        int subscriberCount = 5;
        CountDownLatch latch = new CountDownLatch(subscriberCount);
        List<String> receivedMessages = new ArrayList<>();

        for (int i = 0; i < subscriberCount; i++) {
            messageQueue.subscribe("broadcast-topic", (topic, message) -> {
                synchronized (receivedMessages) {
                    receivedMessages.add((String) message);
                }
                latch.countDown();
            });
        }

        messageQueue.send("broadcast-topic", "Broadcast Message");

        assertTrue(latch.await(1, TimeUnit.SECONDS), "All subscribers should receive message");
        assertEquals(subscriberCount, receivedMessages.size());
        assertTrue(receivedMessages.stream().allMatch(m -> "Broadcast Message".equals(m)));
    }

    @Test
    void testDelayedMessage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Long> receivedTime = new AtomicReference<>();

        messageQueue.subscribe("delayed-topic", (topic, message) -> {
            receivedTime.set(System.currentTimeMillis());
            latch.countDown();
        });

        long sendTime = System.currentTimeMillis();
        messageQueue.sendDelay("delayed-topic", "Delayed Message", 500);

        assertFalse(latch.await(200, TimeUnit.MILLISECONDS), "Should not receive message before delay");

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Should receive message after delay");
        assertNotNull(receivedTime.get());
        assertTrue(receivedTime.get() - sendTime >= 500, "Message should be delayed by at least 500ms");
    }

    @Test
    void testZeroDelaySendsImmediately() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        messageQueue.subscribe("immediate-topic", (topic, message) -> {
            latch.countDown();
        });

        messageQueue.sendDelay("immediate-topic", "Immediate", 0);

        assertTrue(latch.await(100, TimeUnit.MILLISECONDS), "Zero delay should send immediately");
    }

    @Test
    void testNegativeDelaySendsImmediately() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        messageQueue.subscribe("negative-delay-topic", (topic, message) -> {
            latch.countDown();
        });

        messageQueue.sendDelay("negative-delay-topic", "Negative", -100);

        assertTrue(latch.await(100, TimeUnit.MILLISECONDS), "Negative delay should send immediately");
    }

    @Test
    void testNoSubscriberMessageDropped() {
        AtomicInteger counter = new AtomicInteger(0);

        messageQueue.subscribe("other-topic", (topic, message) -> {
            counter.incrementAndGet();
        });

        messageQueue.send("no-subscriber-topic", "This should be dropped");

        assertEquals(0, counter.get(), "Message without subscriber should be dropped silently");
    }

    @Test
    void testMultipleTopics() throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<String> message1 = new AtomicReference<>();
        AtomicReference<String> message2 = new AtomicReference<>();

        messageQueue.subscribe("topic-1", (topic, msg) -> {
            message1.set((String) msg);
            latch1.countDown();
        });

        messageQueue.subscribe("topic-2", (topic, msg) -> {
            message2.set((String) msg);
            latch2.countDown();
        });

        messageQueue.send("topic-1", "Message for topic 1");
        messageQueue.send("topic-2", "Message for topic 2");

        assertTrue(latch1.await(1, TimeUnit.SECONDS));
        assertTrue(latch2.await(1, TimeUnit.SECONDS));

        assertEquals("Message for topic 1", message1.get());
        assertEquals("Message for topic 2", message2.get());
    }

    @Test
    void testComplexMessageObject() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TestMessage> receivedMessage = new AtomicReference<>();

        messageQueue.subscribe("complex-topic", (topic, message) -> {
            if (message instanceof TestMessage) {
                receivedMessage.set((TestMessage) message);
                latch.countDown();
            }
        });

        TestMessage sentMessage = new TestMessage("test-id", "test-content", 12345L);
        messageQueue.send("complex-topic", sentMessage);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(receivedMessage.get());
        assertEquals("test-id", receivedMessage.get().getId());
        assertEquals("test-content", receivedMessage.get().getContent());
        assertEquals(12345L, receivedMessage.get().getTimestamp());
    }

    @Test
    void testConcurrentMessageSending() throws InterruptedException {
        int messageCount = 100;
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger receivedCount = new AtomicInteger(0);

        messageQueue.subscribe("concurrent-topic", (topic, message) -> {
            receivedCount.incrementAndGet();
            latch.countDown();
        });

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    messageQueue.send("concurrent-topic", "Message-" + threadIndex + "-" + j);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join(5000);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All messages should be received");
        assertEquals(messageCount, receivedCount.get());
    }

    @Test
    void testSequentialMessageOrder() throws InterruptedException {
        List<Integer> receivedOrder = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(5);

        messageQueue.subscribe("order-topic", (topic, message) -> {
            synchronized (receivedOrder) {
                receivedOrder.add((Integer) message);
            }
            latch.countDown();
        });

        for (int i = 0; i < 5; i++) {
            messageQueue.send("order-topic", i);
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(5, receivedOrder.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, receivedOrder.get(i), "Messages should be received in order: " + receivedOrder);
        }
    }

    @Test
    void testMessageWithNullPayload() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> receivedMessage = new AtomicReference<>();

        messageQueue.subscribe("null-topic", (topic, message) -> {
            receivedMessage.set(message);
            latch.countDown();
        });

        messageQueue.send("null-topic", null);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNull(receivedMessage.get());
    }

    @Test
    void testMultipleDelayedMessages() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        List<Long> receivedTimes = new ArrayList<>();

        messageQueue.subscribe("multi-delay-topic", (topic, message) -> {
            synchronized (receivedTimes) {
                receivedTimes.add(System.currentTimeMillis());
            }
            latch.countDown();
        });

        long startTime = System.currentTimeMillis();
        messageQueue.sendDelay("multi-delay-topic", "msg1", 100);
        messageQueue.sendDelay("multi-delay-topic", "msg2", 200);
        messageQueue.sendDelay("multi-delay-topic", "msg3", 300);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "All delayed messages should be received");
        assertEquals(3, receivedTimes.size());

        assertTrue(receivedTimes.get(0) - startTime >= 100);
        assertTrue(receivedTimes.get(1) - startTime >= 200);
        assertTrue(receivedTimes.get(2) - startTime >= 300);
    }

    static class TestMessage {
        private final String id;
        private final String content;
        private final long timestamp;

        public TestMessage(String id, String content, long timestamp) {
            this.id = id;
            this.content = content;
            this.timestamp = timestamp;
        }

        public String getId() {
            return id;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
