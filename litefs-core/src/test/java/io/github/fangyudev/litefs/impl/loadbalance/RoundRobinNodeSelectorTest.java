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

package io.github.fangyudev.litefs.impl.loadbalance;

import io.github.fangyudev.litefs.impl.loadbalance.selector.RoundRobinNodeSelector;
import io.github.fangyudev.litefs.model.FileUploadRequest;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.StorageNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinNodeSelectorTest {

    private RoundRobinNodeSelector selector;

    @BeforeEach
    void setUp() {
        selector = new RoundRobinNodeSelector();
    }

    @Test
    void testSelectSingleNode() {
        StorageNode node = createNode("node1", NodeStatus.ONLINE, 1000, 500);
        List<StorageNode> nodes = Collections.singletonList(node);

        StorageNode selected = selector.select(nodes, null);

        assertEquals(node, selected);
    }

    @Test
    void testSelectRoundRobin() {
        StorageNode node1 = createNode("node1", NodeStatus.ONLINE, 1000, 500);
        StorageNode node2 = createNode("node2", NodeStatus.ONLINE, 1000, 500);
        StorageNode node3 = createNode("node3", NodeStatus.ONLINE, 1000, 500);
        List<StorageNode> nodes = Arrays.asList(node1, node2, node3);

        StorageNode selected1 = selector.select(nodes, null);
        StorageNode selected2 = selector.select(nodes, null);
        StorageNode selected3 = selector.select(nodes, null);
        StorageNode selected4 = selector.select(nodes, null);

        assertNotEquals(selected1, selected2);
        assertNotEquals(selected2, selected3);
        assertEquals(selected1, selected4);
    }

    @Test
    void testSelectWithOfflineNodes() {
        StorageNode onlineNode = createNode("online", NodeStatus.ONLINE, 1000, 500);
        StorageNode offlineNode = createNode("offline", NodeStatus.OFFLINE, 1000, 500);
        List<StorageNode> nodes = Arrays.asList(onlineNode, offlineNode);

        for (int i = 0; i < 10; i++) {
            StorageNode selected = selector.select(nodes, null);
            assertEquals(onlineNode, selected);
        }
    }

    @Test
    void testSelectWithFileSizeCheck() {
        StorageNode smallNode = createNode("small", NodeStatus.ONLINE, 1000, 100);
        StorageNode largeNode = createNode("large", NodeStatus.ONLINE, 1000, 1000);
        List<StorageNode> nodes = Arrays.asList(smallNode, largeNode);

        FileUploadRequest request = FileUploadRequest.builder()
                .fileName("test.bin")
                .fileSize(500)
                .build();

        for (int i = 0; i < 10; i++) {
            StorageNode selected = selector.select(nodes, request);
            assertEquals(largeNode, selected);
        }
    }

    @Test
    void testSelectEmptyList() {
        StorageNode selected = selector.select(Collections.emptyList(), null);
        assertNull(selected);
    }

    @Test
    void testSelectNullList() {
        StorageNode selected = selector.select(null, null);
        assertNull(selected);
    }

    @Test
    void testSelectAllOffline() {
        StorageNode offline1 = createNode("offline1", NodeStatus.OFFLINE, 1000, 500);
        StorageNode offline2 = createNode("offline2", NodeStatus.OFFLINE, 1000, 500);
        List<StorageNode> nodes = Arrays.asList(offline1, offline2);

        StorageNode selected = selector.select(nodes, null);
        assertNull(selected);
    }

    @Test
    void testSelectWithNullNodes() {
        StorageNode node1 = createNode("node1", NodeStatus.ONLINE, 1000, 500);
        StorageNode node2 = null;
        StorageNode node3 = createNode("node3", NodeStatus.ONLINE, 1000, 500);
        List<StorageNode> nodes = new ArrayList<>();
        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);

        StorageNode selected = selector.select(nodes, null);
        assertNotNull(selected);
        assertTrue(selected == node1 || selected == node3);
    }

    @Test
    void testSelectWithUnknownSpace() {
        StorageNode unknownSpaceNode = createNode("unknown", NodeStatus.ONLINE, 0, 0);
        StorageNode knownSpaceNode = createNode("known", NodeStatus.ONLINE, 1000, 500);
        List<StorageNode> nodes = Arrays.asList(unknownSpaceNode, knownSpaceNode);

        FileUploadRequest request = FileUploadRequest.builder()
                .fileName("test.bin")
                .fileSize(100)
                .build();

        StorageNode selected = selector.select(nodes, request);
        assertNotNull(selected);
    }

    private StorageNode createNode(String id, NodeStatus status, long totalSpace, long availableSpace) {
        return StorageNode.builder()
                .id(id)
                .host("localhost")
                .port(8080)
                .status(status)
                .totalSpace(totalSpace)
                .usedSpace(totalSpace - availableSpace)
                .build();
    }
}
