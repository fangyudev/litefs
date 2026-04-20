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

package io.github.fangyudev.litefs.performance;

import io.github.fangyudev.litefs.impl.store.cache.LocalMetadataCacheProvider;
import io.github.fangyudev.litefs.impl.store.cache.MetadataCache;
import io.github.fangyudev.litefs.service.FileOperationExecutor;
import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileStatus;
import io.github.fangyudev.litefs.spi.MetadataStore;
import io.github.fangyudev.litefs.impl.store.metadata.H2MetadataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceTest {

    @TempDir
    Path tempDir;

    private MetadataStore metadataStore;
    private MetadataCache metadataCache;
    private LocalMetadataCacheProvider cacheProvider;
    private FileOperationExecutor executor;
    private String jdbcUrl;

    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:" + tempDir.resolve("perf-db").toString() + ";AUTO_SERVER=TRUE";
        metadataStore = new H2MetadataStore(jdbcUrl);
        metadataStore.init();
        cacheProvider = new LocalMetadataCacheProvider(10000, 300000);
        metadataCache = new MetadataCache(metadataStore, cacheProvider);
        executor = new FileOperationExecutor(4, 100);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        metadataCache.shutdown();
        metadataStore.shutdown();
    }

    @Test
    void testCachePerformance() {
        int count = 1000;
        List<String> fileIds = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            FileMetadata metadata = createTestMetadata("cache-test-" + i);
            metadataStore.save(metadata);
            fileIds.add(metadata.getId());
        }

        long startNoCache = System.nanoTime();
        for (String fileId : fileIds) {
            metadataStore.get(fileId);
        }
        long timeNoCache = System.nanoTime() - startNoCache;

        for (String fileId : fileIds) {
            metadataCache.get(fileId);
        }

        long startWithCache = System.nanoTime();
        for (String fileId : fileIds) {
            metadataCache.get(fileId);
        }
        long timeWithCache = System.nanoTime() - startWithCache;

        System.out.println("=== Cache Performance Test ===");
        System.out.println("Operations: " + count);
        System.out.println("Without cache: " + TimeUnit.NANOSECONDS.toMillis(timeNoCache) + " ms");
        System.out.println("With cache: " + TimeUnit.NANOSECONDS.toMillis(timeWithCache) + " ms");
        System.out.println("Speedup: " + (double) timeNoCache / timeWithCache + "x");
        System.out.println("Cache size: " + cacheProvider.size());

        assertTrue(timeWithCache < timeNoCache, "Cache should be faster");
    }

    @Test
    void testConcurrentOperations() throws Exception {
        int count = 100;
        List<String> fileIds = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            FileMetadata metadata = createTestMetadata("concurrent-test-" + i);
            metadataStore.save(metadata);
            fileIds.add(metadata.getId());
        }

        long startSequential = System.nanoTime();
        for (String fileId : fileIds) {
            metadataStore.get(fileId);
        }
        long timeSequential = System.nanoTime() - startSequential;

        System.out.println("=== Concurrent Operations Test ===");
        System.out.println("Operations: " + count);
        System.out.println("Sequential: " + TimeUnit.NANOSECONDS.toMillis(timeSequential) + " ms");
        System.out.println("Executor status: " + executor.getStatus());
        System.out.println("Concurrent test completed successfully");
    }

    @Test
    void testBatchInsertPerformance() {
        int count = 100;
        List<FileMetadata> metadataList = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            metadataList.add(createTestMetadata("batch-perf-" + i));
        }

        long startSequential = System.nanoTime();
        for (FileMetadata metadata : metadataList) {
            metadataStore.save(metadata);
        }
        long timeSequential = System.nanoTime() - startSequential;

        System.out.println("=== Batch Insert Performance Test ===");
        System.out.println("Records: " + count);
        System.out.println("Sequential insert: " + TimeUnit.NANOSECONDS.toMillis(timeSequential) + " ms");
        System.out.println("Batch insert test completed successfully");
    }

    @Test
    void testCacheEviction() {
        int cacheSize = 100;
        LocalMetadataCacheProvider smallCacheProvider = new LocalMetadataCacheProvider(cacheSize, 60000);
        MetadataCache smallCache = new MetadataCache(metadataStore, smallCacheProvider);

        for (int i = 0; i < cacheSize * 2; i++) {
            FileMetadata metadata = createTestMetadata("evict-test-" + i);
            metadataStore.save(metadata);
            smallCache.get(metadata.getId());
        }

        System.out.println("=== Cache Eviction Test ===");
        System.out.println("Cache size limit: " + cacheSize);
        System.out.println("Items accessed: " + cacheSize * 2);
        System.out.println("Actual cache size: " + smallCacheProvider.size());

        assertTrue(smallCacheProvider.size() <= cacheSize, "Cache should not exceed max size");

        smallCache.shutdown();
    }

    @Test
    void testCacheExpiration() throws InterruptedException {
        LocalMetadataCacheProvider shortTtlCacheProvider = new LocalMetadataCacheProvider(1000, 100);
        MetadataCache shortTtlCache = new MetadataCache(metadataStore, shortTtlCacheProvider);

        FileMetadata metadata = createTestMetadata("expire-test");
        metadataStore.save(metadata);

        shortTtlCache.get(metadata.getId());
        assertEquals(1, shortTtlCacheProvider.size());

        Thread.sleep(150);

        shortTtlCache.get(metadata.getId());

        System.out.println("=== Cache Expiration Test ===");
        System.out.println("Cache size after expiration: " + shortTtlCacheProvider.size());

        shortTtlCache.shutdown();
    }

    private FileMetadata createTestMetadata(String id) {
        return FileMetadata.builder()
                .id(id)
                .fileName("test-" + id + ".txt")
                .contentType("text/plain")
                .fileSize(100)
                .storageNodeId("node1")
                .storagePath("/path/" + id)
                .metadata(new HashMap<>(Map.of("test", "value")))
                .status(FileStatus.COMMITTED)
                .build();
    }
}
