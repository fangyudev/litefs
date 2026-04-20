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

import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.impl.registry.StaticServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServiceRegistryIntegrationTest {

    private StaticServiceRegistry staticRegistry;

    @BeforeEach
    void setUp() {
        staticRegistry = new StaticServiceRegistry();
        staticRegistry.init();
    }

    @AfterEach
    void tearDown() {
        if (staticRegistry != null) {
            staticRegistry.shutdown();
        }
    }

    @Test
    void testStaticRegistryBasicOperations() {
        StorageNode node = StorageNode.builder()
                .id("test-node-1")
                .host("192.168.1.100")
                .port(8080)
                .zone("zone-a")
                .totalSpace(1024L * 1024 * 1024 * 100)
                .usedSpace(1024L * 1024 * 1024 * 20)
                .build();

        staticRegistry.register(node);

        StorageNode retrieved = staticRegistry.get("test-node-1");
        assertNotNull(retrieved);
        assertEquals("test-node-1", retrieved.getId());
        assertEquals("192.168.1.100", retrieved.getHost());
        assertEquals(8080, retrieved.getPort());
        assertEquals(NodeStatus.ONLINE, retrieved.getStatus());
    }

    @Test
    void testStaticRegistryDiscover() {
        for (int i = 1; i <= 5; i++) {
            StorageNode node = StorageNode.builder()
                    .id("node-" + i)
                    .host("192.168.1." + (100 + i))
                    .port(8080 + i)
                    .zone(i % 2 == 0 ? "zone-a" : "zone-b")
                    .totalSpace(1024L * 1024 * 1024 * 100)
                    .usedSpace(0)
                    .build();
            staticRegistry.register(node);
        }

        List<StorageNode> nodes = staticRegistry.discover();
        assertEquals(5, nodes.size());

        for (StorageNode node : nodes) {
            assertEquals(NodeStatus.ONLINE, node.getStatus());
        }
    }

    @Test
    void testStaticRegistryDeregister() {
        StorageNode node = StorageNode.builder()
                .id("temp-node")
                .host("localhost")
                .port(9000)
                .build();

        staticRegistry.register(node);
        assertNotNull(staticRegistry.get("temp-node"));

        staticRegistry.deregister("temp-node");
        assertNull(staticRegistry.get("temp-node"));

        List<StorageNode> nodes = staticRegistry.discover();
        assertTrue(nodes.stream().noneMatch(n -> "temp-node".equals(n.getId())));
    }

    @Test
    void testStaticRegistryHeartbeat() throws InterruptedException {
        StorageNode node = StorageNode.builder()
                .id("heartbeat-node")
                .host("localhost")
                .port(8080)
                .build();

        staticRegistry.register(node);
        long initialHeartbeat = staticRegistry.get("heartbeat-node").getLastHeartbeat();

        Thread.sleep(100);

        staticRegistry.heartbeat("heartbeat-node");
        long newHeartbeat = staticRegistry.get("heartbeat-node").getLastHeartbeat();

        assertTrue(newHeartbeat > initialHeartbeat);
    }

    @Test
    void testStaticRegistryOfflineNodeNotDiscovered() {
        StorageNode onlineNode = StorageNode.builder()
                .id("online-node")
                .host("localhost")
                .port(8080)
                .build();

        StorageNode offlineNode = StorageNode.builder()
                .id("offline-node")
                .host("localhost")
                .port(8081)
                .build();

        staticRegistry.register(onlineNode);
        staticRegistry.register(offlineNode);

        staticRegistry.get("offline-node").setStatus(NodeStatus.OFFLINE);

        List<StorageNode> discovered = staticRegistry.discover();
        assertEquals(1, discovered.size());
        assertEquals("online-node", discovered.get(0).getId());
    }

    @Test
    void testStaticServiceRegistryWithInitialNodes() {
        List<StorageNode> initialNodes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            initialNodes.add(StorageNode.builder()
                    .id("static-node-" + i)
                    .host("10.0.0." + i)
                    .port(9000 + i)
                    .totalSpace(1024L * 1024 * 1024 * 50)
                    .usedSpace(0)
                    .build());
        }

        StaticServiceRegistry registry = new StaticServiceRegistry(initialNodes);
        registry.init();

        try {
            List<StorageNode> discovered = registry.discover();
            assertEquals(3, discovered.size());

            for (StorageNode node : discovered) {
                assertEquals(NodeStatus.ONLINE, node.getStatus());
            }

            StorageNode node = registry.get("static-node-1");
            assertNotNull(node);
            assertEquals("10.0.0.1", node.getHost());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void testStaticServiceRegistryWithNullList() {
        StaticServiceRegistry registry = new StaticServiceRegistry(null);
        registry.init();

        try {
            List<StorageNode> discovered = registry.discover();
            assertTrue(discovered.isEmpty());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void testStaticServiceRegistryRegisterAddsNode() {
        StaticServiceRegistry registry = new StaticServiceRegistry(null);
        registry.init();

        try {
            StorageNode newNode = StorageNode.builder()
                    .id("added-node")
                    .host("192.168.1.200")
                    .port(8888)
                    .build();

            registry.register(newNode);

            StorageNode retrieved = registry.get("added-node");
            assertNotNull(retrieved);
            assertEquals("added-node", retrieved.getId());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void testConcurrentRegistration() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    StorageNode node = StorageNode.builder()
                            .id("concurrent-node-" + index)
                            .host("localhost")
                            .port(8000 + index)
                            .build();
                    staticRegistry.register(node);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));

        assertEquals(threadCount, successCount.get());
        assertEquals(threadCount, staticRegistry.discover().size());
    }

    @Test
    void testConcurrentDiscovery() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            StorageNode node = StorageNode.builder()
                    .id("discovery-node-" + i)
                    .host("localhost")
                    .port(8000 + i)
                    .build();
            staticRegistry.register(node);
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        List<List<StorageNode>> results = new ArrayList<>();

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    List<StorageNode> nodes = staticRegistry.discover();
                    synchronized (results) {
                        results.add(nodes);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));

        for (List<StorageNode> result : results) {
            assertEquals(20, result.size());
        }
    }

    @Test
    void testNodeCapacityCalculation() {
        StorageNode node = StorageNode.builder()
                .id("capacity-node")
                .host("localhost")
                .port(8080)
                .totalSpace(1024L * 1024 * 1024 * 100)
                .usedSpace(1024L * 1024 * 1024 * 25)
                .build();

        staticRegistry.register(node);

        StorageNode retrieved = staticRegistry.get("capacity-node");
        assertNotNull(retrieved);

        long expectedAvailable = 1024L * 1024 * 1024 * 75;
        assertEquals(expectedAvailable, retrieved.getAvailableSpace());

        double expectedUsageRatio = 0.25;
        assertEquals(expectedUsageRatio, retrieved.getUsageRatio(), 0.001);
    }

    @Test
    void testNodeOverwrite() {
        StorageNode node1 = StorageNode.builder()
                .id("overwrite-node")
                .host("192.168.1.100")
                .port(8080)
                .build();

        staticRegistry.register(node1);
        assertEquals("192.168.1.100", staticRegistry.get("overwrite-node").getHost());

        StorageNode node2 = StorageNode.builder()
                .id("overwrite-node")
                .host("192.168.1.200")
                .port(9090)
                .build();

        staticRegistry.register(node2);

        StorageNode retrieved = staticRegistry.get("overwrite-node");
        assertEquals("192.168.1.200", retrieved.getHost());
        assertEquals(9090, retrieved.getPort());
    }

    @Test
    void testRegisterNullNode() {
        int initialCount = staticRegistry.discover().size();

        staticRegistry.register(null);

        assertEquals(initialCount, staticRegistry.discover().size());
    }

    @Test
    void testRegisterNodeWithNullId() {
        int initialCount = staticRegistry.discover().size();

        StorageNode node = new StorageNode();
        node.setHost("localhost");
        node.setPort(8080);
        staticRegistry.register(node);

        assertEquals(initialCount, staticRegistry.discover().size());
    }

    @Test
    void testDeregisterNullId() {
        StorageNode node = StorageNode.builder()
                .id("test-node")
                .host("localhost")
                .port(8080)
                .build();

        staticRegistry.register(node);
        int count = staticRegistry.discover().size();

        staticRegistry.deregister(null);

        assertEquals(count, staticRegistry.discover().size());
    }

    @Test
    void testGetNullId() {
        assertNull(staticRegistry.get(null));
    }

    @Test
    void testHeartbeatNonExistentNode() {
        assertDoesNotThrow(() -> staticRegistry.heartbeat("non-existent-node"));
    }

    @Test
    void testShutdownClearsNodes() {
        for (int i = 0; i < 5; i++) {
            StorageNode node = StorageNode.builder()
                    .id("shutdown-node-" + i)
                    .host("localhost")
                    .port(8000 + i)
                    .build();
            staticRegistry.register(node);
        }

        assertFalse(staticRegistry.discover().isEmpty());

        staticRegistry.shutdown();

        assertTrue(staticRegistry.discover().isEmpty());
    }

    @Test
    void testRegistryAfterShutdown() {
        staticRegistry.shutdown();

        StorageNode node = StorageNode.builder()
                .id("after-shutdown-node")
                .host("localhost")
                .port(8080)
                .build();

        staticRegistry.register(node);

        List<StorageNode> nodes = staticRegistry.discover();
        assertEquals(1, nodes.size());
    }

    @Test
    void testMultipleZones() {
        String[] zones = {"zone-a", "zone-b", "zone-c"};
        for (int i = 0; i < 9; i++) {
            StorageNode node = StorageNode.builder()
                    .id("zone-node-" + i)
                    .host("localhost")
                    .port(8000 + i)
                    .zone(zones[i % 3])
                    .build();
            staticRegistry.register(node);
        }

        List<StorageNode> nodes = staticRegistry.discover();
        assertEquals(9, nodes.size());

        for (String zone : zones) {
            long count = nodes.stream().filter(n -> zone.equals(n.getZone())).count();
            assertEquals(3, count, "Each zone should have 3 nodes");
        }
    }
}
