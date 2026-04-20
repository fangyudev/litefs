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

package io.github.fangyudev.litefs.impl.registry;

import io.github.fangyudev.litefs.impl.registry.StaticServiceRegistry;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.StorageNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StaticServiceRegistryTest {

    private StaticServiceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StaticServiceRegistry();
    }

    @Test
    void testRegister() {
        StorageNode node = createNode("node1");

        registry.register(node);

        StorageNode retrieved = registry.get("node1");
        assertNotNull(retrieved);
        assertEquals("node1", retrieved.getId());
        assertEquals(NodeStatus.ONLINE, retrieved.getStatus());
    }

    @Test
    void testRegisterNull() {
        assertDoesNotThrow(() -> registry.register(null));
    }

    @Test
    void testRegisterNullId() {
        StorageNode node = new StorageNode();
        assertDoesNotThrow(() -> registry.register(node));
    }

    @Test
    void testDeregister() {
        registry.register(createNode("node1"));

        registry.deregister("node1");

        assertNull(registry.get("node1"));
    }

    @Test
    void testDeregisterNull() {
        assertDoesNotThrow(() -> registry.deregister(null));
    }

    @Test
    void testDeregisterNonExistent() {
        assertDoesNotThrow(() -> registry.deregister("nonexistent"));
    }

    @Test
    void testDiscover() {
        registry.register(createNode("node1"));
        registry.register(createNode("node2"));
        registry.register(createNode("node3"));

        List<StorageNode> nodes = registry.discover();

        assertEquals(3, nodes.size());
    }

    @Test
    void testDiscoverOnlyOnline() {
        StorageNode onlineNode = createNode("online");
        registry.register(onlineNode);

        StorageNode offlineNode = createNode("offline");
        registry.register(offlineNode);
        offlineNode.setStatus(NodeStatus.OFFLINE);

        List<StorageNode> nodes = registry.discover();

        assertEquals(1, nodes.size());
        assertEquals("online", nodes.get(0).getId());
    }

    @Test
    void testDiscoverEmpty() {
        List<StorageNode> nodes = registry.discover();
        assertTrue(nodes.isEmpty());
    }

    @Test
    void testGet() {
        registry.register(createNode("node1"));

        StorageNode node = registry.get("node1");

        assertNotNull(node);
        assertEquals("node1", node.getId());
    }

    @Test
    void testGetNull() {
        assertNull(registry.get(null));
    }

    @Test
    void testGetNonExistent() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void testHeartbeat() throws InterruptedException {
        StorageNode node = createNode("node1");
        registry.register(node);
        long initialHeartbeat = node.getLastHeartbeat();

        Thread.sleep(10);
        registry.heartbeat("node1");

        StorageNode updated = registry.get("node1");
        assertTrue(updated.getLastHeartbeat() > initialHeartbeat);
    }

    @Test
    void testHeartbeatNonExistent() {
        assertDoesNotThrow(() -> registry.heartbeat("nonexistent"));
    }

    @Test
    void testInitialNodes() {
        StorageNode node1 = createNode("initial1");
        StorageNode node2 = createNode("initial2");
        StaticServiceRegistry registryWithNodes = new StaticServiceRegistry(Arrays.asList(node1, node2));

        List<StorageNode> nodes = registryWithNodes.discover();

        assertEquals(2, nodes.size());
    }

    @Test
    void testInitialNodesNull() {
        StaticServiceRegistry registryWithNull = new StaticServiceRegistry(null);
        assertTrue(registryWithNull.discover().isEmpty());
    }

    @Test
    void testInitialNodesEmpty() {
        StaticServiceRegistry registryEmpty = new StaticServiceRegistry(Collections.emptyList());
        assertTrue(registryEmpty.discover().isEmpty());
    }

    @Test
    void testShutdown() {
        registry.register(createNode("node1"));
        registry.register(createNode("node2"));

        registry.shutdown();

        assertTrue(registry.discover().isEmpty());
    }

    @Test
    void testInit() {
        assertDoesNotThrow(() -> registry.init());
    }

    @Test
    void testRegisterOverwrite() {
        registry.register(createNode("node1"));

        StorageNode updatedNode = StorageNode.builder()
                .id("node1")
                .host("updated-host")
                .port(9090)
                .totalSpace(2000)
                .usedSpace(0)
                .build();
        registry.register(updatedNode);

        StorageNode retrieved = registry.get("node1");
        assertEquals("updated-host", retrieved.getHost());
        assertEquals(9090, retrieved.getPort());
    }

    private StorageNode createNode(String id) {
        return StorageNode.builder()
                .id(id)
                .host("localhost")
                .port(8080)
                .totalSpace(1000)
                .usedSpace(0)
                .build();
    }
}
