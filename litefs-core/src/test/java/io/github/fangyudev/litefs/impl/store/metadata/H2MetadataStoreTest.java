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

package io.github.fangyudev.litefs.impl.store.metadata;

import io.github.fangyudev.litefs.impl.store.metadata.H2MetadataStore;
import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileQuery;
import io.github.fangyudev.litefs.model.FileStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class H2MetadataStoreTest {

    @TempDir
    Path tempDir;

    private H2MetadataStore metadataStore;
    private String jdbcUrl;

    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:" + tempDir.resolve("test-db").toString() + ";AUTO_SERVER=TRUE";
        metadataStore = new H2MetadataStore(jdbcUrl);
        metadataStore.init();
    }

    @AfterEach
    void tearDown() {
        if (metadataStore != null) {
            metadataStore.shutdown();
        }
    }

    @Test
    void testSave() {
        FileMetadata metadata = createTestMetadata("file1", "test.txt");

        metadataStore.save(metadata);

        FileMetadata saved = metadataStore.get("file1");
        assertNotNull(saved);
        assertEquals("test.txt", saved.getFileName());
        assertEquals("text/plain", saved.getContentType());
    }

    @Test
    void testSaveWithMetadata() {
        FileMetadata metadata = FileMetadata.builder()
                .id("file2")
                .fileName("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024)
                .storageNodeId("node1")
                .storagePath("/path/to/file")
                .metadata(Map.of("key1", "value1", "key2", "value2"))
                .status(FileStatus.COMMITTED)
                .build();

        metadataStore.save(metadata);

        FileMetadata saved = metadataStore.get("file2");
        assertNotNull(saved);
        assertEquals("value1", saved.getMetadata("key1"));
        assertEquals("value2", saved.getMetadata("key2"));
    }

    @Test
    void testGetNotFound() {
        FileMetadata result = metadataStore.get("nonexistent");
        assertNull(result);
    }

    @Test
    void testUpdate() {
        FileMetadata metadata = createTestMetadata("file3", "original.txt");
        metadataStore.save(metadata);

        metadata.setFileName("updated.txt");
        metadata.setFileSize(2048);
        metadata.setStatus(FileStatus.COMMITTED);
        metadataStore.update(metadata);

        FileMetadata updated = metadataStore.get("file3");
        assertNotNull(updated);
        assertEquals("updated.txt", updated.getFileName());
        assertEquals(2048, updated.getFileSize());
    }

    @Test
    void testDelete() {
        FileMetadata metadata = createTestMetadata("file4", "delete.txt");
        metadataStore.save(metadata);

        assertTrue(metadataStore.exists("file4"));

        metadataStore.delete("file4");

        assertFalse(metadataStore.exists("file4"));
    }

    @Test
    void testExists() {
        FileMetadata metadata = createTestMetadata("file5", "exist.txt");
        metadataStore.save(metadata);

        assertTrue(metadataStore.exists("file5"));
        assertFalse(metadataStore.exists("nonexistent"));
    }

    @Test
    void testQueryByFileName() {
        metadataStore.save(createTestMetadata("q1", "report-2024.pdf"));
        metadataStore.save(createTestMetadata("q2", "report-2023.pdf"));
        metadataStore.save(createTestMetadata("q3", "document.docx"));

        FileQuery query = FileQuery.builder()
                .fileName("report")
                .pageSize(10)
                .build();

        List<FileMetadata> results = metadataStore.query(query);

        assertEquals(2, results.size());
    }

    @Test
    void testQueryByContentType() {
        FileMetadata pdf1 = createTestMetadata("ct1", "file1.pdf");
        pdf1.setContentType("application/pdf");
        pdf1.setStatus(FileStatus.COMMITTED);
        metadataStore.save(pdf1);

        FileMetadata pdf2 = createTestMetadata("ct2", "file2.pdf");
        pdf2.setContentType("application/pdf");
        pdf2.setStatus(FileStatus.COMMITTED);
        metadataStore.save(pdf2);

        FileMetadata txt = createTestMetadata("ct3", "file3.txt");
        txt.setStatus(FileStatus.COMMITTED);
        metadataStore.save(txt);

        FileQuery query = FileQuery.builder()
                .contentType("application/pdf")
                .pageSize(10)
                .build();

        List<FileMetadata> results = metadataStore.query(query);

        assertEquals(2, results.size());
    }

    @Test
    void testQueryBySize() {
        FileMetadata small = createTestMetadata("sz1", "small.txt");
        small.setFileSize(100);
        small.setStatus(FileStatus.COMMITTED);
        metadataStore.save(small);

        FileMetadata medium = createTestMetadata("sz2", "medium.txt");
        medium.setFileSize(500);
        medium.setStatus(FileStatus.COMMITTED);
        metadataStore.save(medium);

        FileMetadata large = createTestMetadata("sz3", "large.txt");
        large.setFileSize(1000);
        large.setStatus(FileStatus.COMMITTED);
        metadataStore.save(large);

        FileQuery query = FileQuery.builder()
                .minSize(200L)
                .maxSize(800L)
                .pageSize(10)
                .build();

        List<FileMetadata> results = metadataStore.query(query);

        assertEquals(1, results.size());
        assertEquals("medium.txt", results.get(0).getFileName());
    }

    @Test
    void testQueryPagination() {
        for (int i = 0; i < 15; i++) {
            FileMetadata metadata = createTestMetadata("page" + i, "file" + i + ".txt");
            metadata.setStatus(FileStatus.COMMITTED);
            metadataStore.save(metadata);
        }

        FileQuery query1 = FileQuery.builder()
                .pageSize(5)
                .build();
        List<FileMetadata> page1 = metadataStore.query(query1);
        assertEquals(5, page1.size());

        FileQuery query2 = FileQuery.builder()
                .page(2)
                .pageSize(5)
                .build();
        List<FileMetadata> page2 = metadataStore.query(query2);
        assertEquals(5, page2.size());
    }

    @Test
    void testCount() {
        metadataStore.save(createTestMetadata("cnt1", "report1.pdf"));
        metadataStore.save(createTestMetadata("cnt2", "report2.pdf"));
        metadataStore.save(createTestMetadata("cnt3", "other.txt"));

        FileQuery query = FileQuery.builder()
                .fileName("report")
                .build();

        long count = metadataStore.count(query);

        assertEquals(2, count);
    }

    @Test
    void testExpireTime() {
        FileMetadata metadata = createTestMetadata("expire1", "expire.txt");
        metadata.setExpireTime(System.currentTimeMillis() + 3600000);
        metadataStore.save(metadata);

        FileMetadata saved = metadataStore.get("expire1");
        assertNotNull(saved.getExpireTime());
    }

    @Test
    void testThumbnailId() {
        FileMetadata metadata = createTestMetadata("thumb1", "image.jpg");
        metadata.setThumbnailId("thumb123");
        metadataStore.save(metadata);

        FileMetadata saved = metadataStore.get("thumb1");
        assertEquals("thumb123", saved.getThumbnailId());
    }

    private FileMetadata createTestMetadata(String id, String fileName) {
        return FileMetadata.builder()
                .id(id)
                .fileName(fileName)
                .contentType("text/plain")
                .fileSize(100)
                .storageNodeId("node1")
                .storagePath("/path/" + id)
                .status(FileStatus.COMMITTED)
                .build();
    }
}
