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

import io.github.fangyudev.litefs.impl.loadbalance.selector.CapacityNodeSelector;
import io.github.fangyudev.litefs.model.FileUploadRequest;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.StorageNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CapacityNodeSelectorTest {

    private CapacityNodeSelector selector;

    @BeforeEach
    void setUp() {
        selector = new CapacityNodeSelector();
    }

    @Test
    void testSelectMostAvailableSpace() {
        StorageNode smallNode = createNode("small", NodeStatus.ONLINE, 1000, 100);
        StorageNode mediumNode = createNode("medium", NodeStatus.ONLINE, 1000, 500);
        StorageNode largeNode = createNode("large", NodeStatus.ONLINE, 1000, 900);
        List<StorageNode> nodes = Arrays.asList(smallNode, mediumNode, largeNode);

        StorageNode selected = selector.select(nodes, null);

        assertEquals(largeNode, selected);
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

        StorageNode selected = selector.select(nodes, request);

        assertEquals(largeNode, selected);
    }

    @Test
    void testSelectNoEnoughSpace() {
        StorageNode smallNode = createNode("small", NodeStatus.ONLINE, 1000, 100);
        StorageNode mediumNode = createNode("medium", NodeStatus.ONLINE, 1000, 200);
        List<StorageNode> nodes = Arrays.asList(smallNode, mediumNode);

        FileUploadRequest request = FileUploadRequest.builder()
                .fileName("test.bin")
                .fileSize(500)
                .build();

        StorageNode selected = selector.select(nodes, request);

        assertNull(selected);
    }

    @Test
    void testSelectWithOfflineNodes() {
        StorageNode offlineNode = createNode("offline", NodeStatus.OFFLINE, 1000, 900);
        StorageNode onlineNode = createNode("online", NodeStatus.ONLINE, 1000, 500);
        List<StorageNode> nodes = Arrays.asList(offlineNode, onlineNode);

        StorageNode selected = selector.select(nodes, null);

        assertEquals(onlineNode, selected);
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
        List<StorageNode> nodes = Arrays.asList(null, node1, null);

        StorageNode selected = selector.select(nodes, null);

        assertEquals(node1, selected);
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

    @Test
    void testSelectSingleNode() {
        StorageNode node = createNode("node1", NodeStatus.ONLINE, 1000, 500);
        List<StorageNode> nodes = Collections.singletonList(node);

        StorageNode selected = selector.select(nodes, null);

        assertEquals(node, selected);
    }

    @Test
    void testSelectConsistency() {
        StorageNode smallNode = createNode("small", NodeStatus.ONLINE, 1000, 100);
        StorageNode largeNode = createNode("large", NodeStatus.ONLINE, 1000, 900);
        List<StorageNode> nodes = Arrays.asList(smallNode, largeNode);

        for (int i = 0; i < 10; i++) {
            StorageNode selected = selector.select(nodes, null);
            assertEquals(largeNode, selected);
        }
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
