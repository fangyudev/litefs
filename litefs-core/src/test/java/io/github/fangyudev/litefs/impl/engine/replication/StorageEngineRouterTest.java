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

package io.github.fangyudev.litefs.impl.engine.replication;

import io.github.fangyudev.litefs.impl.store.engine.remote.DistributedStorageEngineRouter;
import io.github.fangyudev.litefs.model.NodeStatus;
import io.github.fangyudev.litefs.model.StorageNode;
import io.github.fangyudev.litefs.impl.store.engine.local.SingleNodeStorageEngineRouter;
import io.github.fangyudev.litefs.spi.ServiceRegistry;
import io.github.fangyudev.litefs.spi.StorageEngine;
import io.github.fangyudev.litefs.spi.StorageEngineRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StorageEngineRouter 实现正确性测试
 * 
 * <p>验证所有实现都满足接口契约：</p>
 * <ul>
 *   <li>当 nodeId 为 null 或等于本地节点ID时，必须返回本地存储引擎实例</li>
 *   <li>当 nodeId 为其他节点时，返回远程存储引擎代理</li>
 * </ul>
 */
class StorageEngineRouterTest {

    private static final String LOCAL_NODE_ID = "node-1";
    private static final String REMOTE_NODE_ID = "node-2";
    
    private StorageEngine localStorageEngine;
    private ServiceRegistry serviceRegistry;

    @BeforeEach
    void setUp() {
        localStorageEngine = new MockStorageEngine();
        serviceRegistry = new MockServiceRegistry();
    }

    @Test
    @DisplayName("SingleNodeStorageEngineRouter: 获取本地节点应返回本地存储引擎")
    void testSingleNodeRouterGetLocalNode() {
        StorageEngineRouter router = new SingleNodeStorageEngineRouter(LOCAL_NODE_ID, localStorageEngine);
        
        StorageEngine engine = router.getEngine(LOCAL_NODE_ID);
        
        assertSame(localStorageEngine, engine, "获取本地节点应返回同一个本地存储引擎实例");
    }

    @Test
    @DisplayName("SingleNodeStorageEngineRouter: nodeId为null应返回本地存储引擎")
    void testSingleNodeRouterGetNullNode() {
        StorageEngineRouter router = new SingleNodeStorageEngineRouter(LOCAL_NODE_ID, localStorageEngine);
        
        StorageEngine engine = router.getEngine(null);
        
        assertSame(localStorageEngine, engine, "nodeId为null应返回本地存储引擎实例");
    }

    @Test
    @DisplayName("SingleNodeStorageEngineRouter: 获取远程节点应抛出异常")
    void testSingleNodeRouterGetRemoteNode() {
        StorageEngineRouter router = new SingleNodeStorageEngineRouter(LOCAL_NODE_ID, localStorageEngine);
        
        assertThrows(IllegalArgumentException.class, 
            () -> router.getEngine(REMOTE_NODE_ID),
            "单节点路由器获取远程节点应抛出IllegalArgumentException");
    }

    @Test
    @DisplayName("SingleNodeStorageEngineRouter: getLocalNodeId应返回正确的节点ID")
    void testSingleNodeRouterGetLocalNodeId() {
        StorageEngineRouter router = new SingleNodeStorageEngineRouter(LOCAL_NODE_ID, localStorageEngine);
        
        assertEquals(LOCAL_NODE_ID, router.getLocalNodeId());
    }

    @Test
    @DisplayName("DistributedStorageEngineRouter: 获取本地节点应返回本地存储引擎")
    void testDistributedRouterGetLocalNode() {
        StorageEngineRouter router = new DistributedStorageEngineRouter(
            LOCAL_NODE_ID, localStorageEngine, serviceRegistry);
        
        StorageEngine engine = router.getEngine(LOCAL_NODE_ID);
        
        assertSame(localStorageEngine, engine, "获取本地节点应返回同一个本地存储引擎实例");
    }

    @Test
    @DisplayName("DistributedStorageEngineRouter: nodeId为null应返回本地存储引擎")
    void testDistributedRouterGetNullNode() {
        StorageEngineRouter router = new DistributedStorageEngineRouter(
            LOCAL_NODE_ID, localStorageEngine, serviceRegistry);
        
        StorageEngine engine = router.getEngine(null);
        
        assertSame(localStorageEngine, engine, "nodeId为null应返回本地存储引擎实例");
    }

    @Test
    @DisplayName("DistributedStorageEngineRouter: 获取远程节点应返回不同的存储引擎实例")
    void testDistributedRouterGetRemoteNode() {
        StorageEngineRouter router = new DistributedStorageEngineRouter(
            LOCAL_NODE_ID, localStorageEngine, serviceRegistry);
        
        StorageEngine remoteEngine = router.getEngine(REMOTE_NODE_ID);
        
        assertNotSame(localStorageEngine, remoteEngine, "获取远程节点应返回不同的存储引擎实例");
        assertNotNull(remoteEngine, "远程存储引擎不应为null");
    }

    @Test
    @DisplayName("DistributedStorageEngineRouter: 多次获取同一远程节点应返回缓存的实例")
    void testDistributedRouterCacheRemoteNode() {
        StorageEngineRouter router = new DistributedStorageEngineRouter(
            LOCAL_NODE_ID, localStorageEngine, serviceRegistry);
        
        StorageEngine engine1 = router.getEngine(REMOTE_NODE_ID);
        StorageEngine engine2 = router.getEngine(REMOTE_NODE_ID);
        
        assertSame(engine1, engine2, "多次获取同一远程节点应返回缓存的实例");
    }

    @Test
    @DisplayName("DistributedStorageEngineRouter: getLocalNodeId应返回正确的节点ID")
    void testDistributedRouterGetLocalNodeId() {
        StorageEngineRouter router = new DistributedStorageEngineRouter(
            LOCAL_NODE_ID, localStorageEngine, serviceRegistry);
        
        assertEquals(LOCAL_NODE_ID, router.getLocalNodeId());
    }

    @Test
    @DisplayName("接口契约验证: 本地节点和null应返回同一实例")
    void testInterfaceContract() {
        StorageEngineRouter router = new DistributedStorageEngineRouter(
            LOCAL_NODE_ID, localStorageEngine, serviceRegistry);
        
        StorageEngine engine1 = router.getEngine(LOCAL_NODE_ID);
        StorageEngine engine2 = router.getEngine(null);
        
        assertSame(engine1, engine2, "getEngine(localNodeId) 和 getEngine(null) 应返回同一实例");
    }

    /**
     * Mock 存储引擎实现
     */
    private static class MockStorageEngine implements StorageEngine {
        @Override
        public String write(String fileId, InputStream data) {
            return fileId;
        }

        @Override
        public long append(String fileId, InputStream data) {
            return 0;
        }

        @Override
        public InputStream read(String fileId) {
            return null;
        }

        @Override
        public void delete(String fileId) {
        }

        @Override
        public boolean exists(String fileId) {
            return false;
        }

        @Override
        public long getSize(String fileId) {
            return 0;
        }

        @Override
        public String getStoragePath(String fileId) {
            return fileId;
        }
    }

    /**
     * Mock 服务注册中心实现
     */
    private static class MockServiceRegistry implements ServiceRegistry {
        @Override
        public void register(StorageNode node) {
        }

        @Override
        public void deregister(String nodeId) {
        }

        @Override
        public List<StorageNode> discover() {
            return Arrays.asList(
                StorageNode.builder()
                    .id(LOCAL_NODE_ID)
                    .host("localhost")
                    .port(8081)
                    .status(NodeStatus.ONLINE)
                    .totalSpace(1000000)
                    .usedSpace(0)
                    .build(),
                StorageNode.builder()
                    .id(REMOTE_NODE_ID)
                    .host("localhost")
                    .port(8082)
                    .status(NodeStatus.ONLINE)
                    .totalSpace(1000000)
                    .usedSpace(0)
                    .build()
            );
        }

        @Override
        public StorageNode get(String nodeId) {
            if (LOCAL_NODE_ID.equals(nodeId)) {
                return StorageNode.builder()
                    .id(LOCAL_NODE_ID)
                    .host("localhost")
                    .port(8081)
                    .status(NodeStatus.ONLINE)
                    .totalSpace(1000000)
                    .usedSpace(0)
                    .build();
            }
            if (REMOTE_NODE_ID.equals(nodeId)) {
                return StorageNode.builder()
                    .id(REMOTE_NODE_ID)
                    .host("localhost")
                    .port(8082)
                    .status(NodeStatus.ONLINE)
                    .totalSpace(1000000)
                    .usedSpace(0)
                    .build();
            }
            return null;
        }

        @Override
        public void heartbeat(String nodeId) {
        }

        @Override
        public void init() {
        }

        @Override
        public void shutdown() {
        }
    }
}
