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

package io.github.fangyudev.litefs.impl.client;

import io.github.fangyudev.litefs.api.FileClient;
import io.github.fangyudev.litefs.builder.FileClientBuilder;
import io.github.fangyudev.litefs.impl.loadbalance.selector.RoundRobinNodeSelector;
import io.github.fangyudev.litefs.impl.loadbalance.placement.BalancedReplicaPlacer;
import io.github.fangyudev.litefs.service.ReplicationService;
import io.github.fangyudev.litefs.impl.store.engine.local.SingleNodeStorageEngineRouter;
import io.github.fangyudev.litefs.impl.store.engine.local.LocalStorageEngine;
import io.github.fangyudev.litefs.impl.loadbalance.selector.CapacityNodeSelector;
import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.ReplicaInfo;
import io.github.fangyudev.litefs.model.ReplicationStrategy;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.impl.registry.StaticServiceRegistry;
import io.github.fangyudev.litefs.spi.NodeSelector;
import io.github.fangyudev.litefs.spi.ReplicaMetadataStore;
import io.github.fangyudev.litefs.spi.ReplicaPlacer;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;
import io.github.fangyudev.litefs.impl.store.replica.H2ReplicaMetadataStore;
import io.github.fangyudev.litefs.impl.store.replica.InMemoryReplicaMetadataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DistributedModeIntegrationTest {

    @TempDir
    Path tempDir;

    private StaticServiceRegistry serviceRegistry;
    private FileClient node1Client;
    private FileClient node2Client;
    private FileClient node3Client;

    private static final String TEST_CONTENT = "Distributed file storage test content!";

    @BeforeEach
    void setUp() {
        serviceRegistry = new StaticServiceRegistry();
        serviceRegistry.init();

        StorageNode node1 = StorageNode.builder()
                .id("node-1")
                .host("localhost")
                .port(8081)
                .zone("zone-a")
                .totalSpace(1024L * 1024 * 1024 * 100)
                .usedSpace(1024L * 1024 * 1024 * 10)
                .status(NodeStatus.ONLINE)
                .build();

        StorageNode node2 = StorageNode.builder()
                .id("node-2")
                .host("localhost")
                .port(8082)
                .zone("zone-a")
                .totalSpace(1024L * 1024 * 1024 * 200)
                .usedSpace(1024L * 1024 * 1024 * 50)
                .status(NodeStatus.ONLINE)
                .build();

        StorageNode node3 = StorageNode.builder()
                .id("node-3")
                .host("localhost")
                .port(8083)
                .zone("zone-b")
                .totalSpace(1024L * 1024 * 1024 * 150)
                .usedSpace(1024L * 1024 * 1024 * 30)
                .status(NodeStatus.ONLINE)
                .build();

        serviceRegistry.register(node1);
        serviceRegistry.register(node2);
        serviceRegistry.register(node3);

        String storagePath1 = tempDir.resolve("storage-node1").toString();
        String storagePath2 = tempDir.resolve("storage-node2").toString();
        String storagePath3 = tempDir.resolve("storage-node3").toString();

        String jdbcUrl1 = "jdbc:h2:" + tempDir.resolve("litefs-node1").toString() + ";AUTO_SERVER=TRUE";
        String jdbcUrl2 = "jdbc:h2:" + tempDir.resolve("litefs-node2").toString() + ";AUTO_SERVER=TRUE";
        String jdbcUrl3 = "jdbc:h2:" + tempDir.resolve("litefs-node3").toString() + ";AUTO_SERVER=TRUE";

        node1Client = FileClientBuilder.builder()
                .storagePath(storagePath1)
                .jdbcUrl(jdbcUrl1)
                .gatewayBaseUrl("http://localhost:8080")
                .gatewayPathPrefix("/api/files")
                .secretKey("test-secret-key")
                .nodeId("node-1")
                .build();

        node2Client = FileClientBuilder.builder()
                .storagePath(storagePath2)
                .jdbcUrl(jdbcUrl2)
                .gatewayBaseUrl("http://localhost:8080")
                .gatewayPathPrefix("/api/files")
                .secretKey("test-secret-key")
                .nodeId("node-2")
                .build();

        node3Client = FileClientBuilder.builder()
                .storagePath(storagePath3)
                .jdbcUrl(jdbcUrl3)
                .gatewayBaseUrl("http://localhost:8080")
                .gatewayPathPrefix("/api/files")
                .secretKey("test-secret-key")
                .nodeId("node-3")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (serviceRegistry != null) {
            serviceRegistry.shutdown();
        }
    }

    @Test
    void testServiceRegistryDiscoverNodes() {
        List<StorageNode> nodes = serviceRegistry.discover();

        assertEquals(3, nodes.size(), "Should discover 3 nodes");

        for (StorageNode node : nodes) {
            assertEquals(NodeStatus.ONLINE, node.getStatus());
            assertTrue(node.getAvailableSpace() > 0);
        }
    }

    @Test
    void testServiceRegistryGetNode() {
        StorageNode node = serviceRegistry.get("node-1");

        assertNotNull(node);
        assertEquals("node-1", node.getId());
        assertEquals("localhost", node.getHost());
        assertEquals(8081, node.getPort());
        assertEquals("zone-a", node.getZone());
    }

    @Test
    void testServiceRegistryHeartbeat() {
        serviceRegistry.heartbeat("node-1");

        StorageNode node = serviceRegistry.get("node-1");
        assertNotNull(node);
        assertTrue(node.getLastHeartbeat() > 0);
        assertEquals(NodeStatus.ONLINE, node.getStatus());
    }

    @Test
    void testServiceRegistryDeregister() {
        serviceRegistry.deregister("node-3");

        List<StorageNode> nodes = serviceRegistry.discover();
        assertEquals(2, nodes.size());

        assertNull(serviceRegistry.get("node-3"));
    }

    @Test
    void testRoundRobinNodeSelector() {
        NodeSelector selector = new RoundRobinNodeSelector();

        List<StorageNode> nodes = serviceRegistry.discover();

        StorageNode selected1 = selector.select(nodes, null);
        StorageNode selected2 = selector.select(nodes, null);
        StorageNode selected3 = selector.select(nodes, null);
        StorageNode selected4 = selector.select(nodes, null);

        assertNotNull(selected1);
        assertNotNull(selected2);
        assertNotNull(selected3);
        assertNotNull(selected4);

        assertNotEquals(selected1.getId(), selected2.getId());
        assertNotEquals(selected2.getId(), selected3.getId());
        assertEquals(selected1.getId(), selected4.getId());
    }

    @Test
    void testCapacityNodeSelector() {
        NodeSelector selector = new CapacityNodeSelector();

        List<StorageNode> nodes = serviceRegistry.discover();

        StorageNode selected = selector.select(nodes, null);

        assertNotNull(selected);
        assertEquals("node-2", selected.getId(), "Should select node with most available space");
    }

    @Test
    void testBalancedReplicaPlacer() {
        ReplicaPlacer placer = new BalancedReplicaPlacer();

        List<StorageNode> nodes = serviceRegistry.discover();
        ReplicationStrategy strategy = ReplicationStrategy.standard();

        List<StorageNode> selected = placer.selectNodes(nodes, strategy, null);

        assertNotNull(selected);
        assertEquals(2, selected.size(), "Standard strategy should select 2 nodes (2 extra replicas)");
    }

    @Test
    void testReplicaMetadataStore() {
        ReplicaMetadataStore store = new InMemoryReplicaMetadataStore();

        ReplicaInfo replica = ReplicaInfo.builder()
                .fileId("file-123")
                .nodeId("node-1")
                .storagePath("/data/file-123")
                .status(ReplicaInfo.ReplicaStatus.SYNCED)
                .checksum("abc123")
                .syncTime(System.currentTimeMillis())
                .build();

        store.saveAll(List.of(replica));

        List<ReplicaInfo> replicas = store.listByFileId("file-123");

        assertEquals(1, replicas.size());
        assertEquals("node-1", replicas.get(0).getNodeId());
        assertEquals(ReplicaInfo.ReplicaStatus.SYNCED, replicas.get(0).getStatus());
    }

    @Test
    void testH2ReplicaMetadataStore() {
        String jdbcUrl = "jdbc:h2:" + tempDir.resolve("replica-store").toString() + ";AUTO_SERVER=TRUE";
        H2ReplicaMetadataStore store = new H2ReplicaMetadataStore(jdbcUrl, null, null);
        store.init();

        try {
            ReplicaInfo replica = ReplicaInfo.builder()
                    .fileId("file-h2-test")
                    .nodeId("node-1")
                    .storagePath("/data/file-h2-test")
                    .status(ReplicaInfo.ReplicaStatus.SYNCED)
                    .checksum("checksum-h2")
                    .syncTime(System.currentTimeMillis())
                    .build();

            store.saveAll(List.of(replica));

            List<ReplicaInfo> replicas = store.listByFileId("file-h2-test");

            assertEquals(1, replicas.size());
            assertEquals("node-1", replicas.get(0).getNodeId());
            assertEquals(ReplicaInfo.ReplicaStatus.SYNCED, replicas.get(0).getStatus());
        } finally {
            store.shutdown();
        }
    }

    @Test
    void testSingleNodeStorageEngineRouter() {
        LocalStorageEngine engine = new LocalStorageEngine(tempDir.resolve("router-test").toString());
        StorageEngineRouter router = new SingleNodeStorageEngineRouter("node-1", engine);

        assertEquals("node-1", router.getLocalNodeId());
        assertNotNull(router.getEngine("node-1"));
        assertSame(engine, router.getEngine("node-1"));
    }

    @Test
    void testReplicationServiceBasicFlow() {
        LocalStorageEngine engine = new LocalStorageEngine(tempDir.resolve("repl-test").toString());
        StorageEngineRouter router = new SingleNodeStorageEngineRouter("node-1", engine);
        ReplicaMetadataStore replicaStore = new InMemoryReplicaMetadataStore();
        ReplicaPlacer placer = new BalancedReplicaPlacer();

        ReplicationService replicationService = new ReplicationService(
                serviceRegistry,
                placer,
                replicaStore,
                router,
                ReplicationStrategy.minimal()
        );

        FileMetadata metadata = FileMetadata.builder()
                .id("test-file-id")
                .fileName("test.txt")
                .fileSize(100L)
                .storageNodeId("node-1")
                .storagePath("/data/test-file-id")
                .build();

        List<ReplicaInfo> replicas = replicationService.replicate(metadata, ReplicationStrategy.minimal(), null);

        assertNotNull(replicas);
    }

    @Test
    void testFileUploadOnDifferentNodes() throws IOException {
        InputStream uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId1 = node1Client.upload(uploadStream, "node1-test.txt", Map.of("node", "node-1"));

        uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId2 = node2Client.upload(uploadStream, "node2-test.txt", Map.of("node", "node-2"));

        uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId3 = node3Client.upload(uploadStream, "node3-test.txt", Map.of("node", "node-3"));

        assertNotNull(fileId1);
        assertNotNull(fileId2);
        assertNotNull(fileId3);

        assertNotEquals(fileId1, fileId2);
        assertNotEquals(fileId2, fileId3);

        FileMetadata meta1 = node1Client.getMetadata(fileId1);
        FileMetadata meta2 = node2Client.getMetadata(fileId2);
        FileMetadata meta3 = node3Client.getMetadata(fileId3);

        assertEquals("node-1", meta1.getStorageNodeId());
        assertEquals("node-2", meta2.getStorageNodeId());
        assertEquals("node-3", meta3.getStorageNodeId());
    }

    @Test
    void testNodeOfflineScenario() {
        StorageNode node1 = serviceRegistry.get("node-1");
        node1.setStatus(NodeStatus.OFFLINE);

        List<StorageNode> availableNodes = serviceRegistry.discover();

        assertEquals(2, availableNodes.size(), "Should only discover 2 online nodes");

        for (StorageNode node : availableNodes) {
            assertEquals(NodeStatus.ONLINE, node.getStatus());
        }
    }

    @Test
    void testReplicationStrategyParsing() {
        ReplicationService replicationService = new ReplicationService(
                serviceRegistry,
                null,
                null,
                null,
                ReplicationStrategy.standard()
        );

        assertEquals(0, replicationService.resolveStrategy(Map.of("replication", "NONE")).getReplicas());
        assertEquals(1, replicationService.resolveStrategy(Map.of("replication", "MINIMAL")).getReplicas());
        assertEquals(2, replicationService.resolveStrategy(Map.of("replication", "STANDARD")).getReplicas());
        assertEquals(4, replicationService.resolveStrategy(Map.of("replication", "HIGH")).getReplicas());
        assertEquals(-1, replicationService.resolveStrategy(Map.of("replication", "ALL_NODES")).getReplicas());
        assertEquals(5, replicationService.resolveStrategy(Map.of("replication", "5")).getReplicas());
        assertEquals(2, replicationService.resolveStrategy(null).getReplicas());
        assertEquals(2, replicationService.resolveStrategy(Map.of()).getReplicas());
    }

    @Test
    void testZoneAwarePlacement() {
        StorageNode node1 = serviceRegistry.get("node-1");
        StorageNode node2 = serviceRegistry.get("node-2");
        StorageNode node3 = serviceRegistry.get("node-3");

        assertEquals("zone-a", node1.getZone());
        assertEquals("zone-a", node2.getZone());
        assertEquals("zone-b", node3.getZone());

        List<StorageNode> nodes = serviceRegistry.discover();
        long zoneACount = nodes.stream().filter(n -> "zone-a".equals(n.getZone())).count();
        long zoneBCount = nodes.stream().filter(n -> "zone-b".equals(n.getZone())).count();

        assertEquals(2, zoneACount);
        assertEquals(1, zoneBCount);
    }

    @Test
    void testConcurrentFileOperations() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        String[] fileIds = new String[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    InputStream uploadStream = new ByteArrayInputStream(
                            ("Concurrent content " + index).getBytes(StandardCharsets.UTF_8));
                    fileIds[index] = node1Client.upload(uploadStream, "concurrent-" + index + ".txt",
                            Map.of("thread", String.valueOf(index)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join(5000);
        }

        for (String fileId : fileIds) {
            assertNotNull(fileId, "All uploads should succeed");
            assertNotNull(node1Client.getMetadata(fileId));
        }
    }
}
