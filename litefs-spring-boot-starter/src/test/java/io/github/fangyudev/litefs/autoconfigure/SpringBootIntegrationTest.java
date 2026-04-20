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

package io.github.fangyudev.litefs.autoconfigure;

import io.github.fangyudev.litefs.api.FileClient;
import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileQuery;
import io.github.fangyudev.litefs.model.InitMultipartUploadResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SpringBootIntegrationTest.TestConfig.class)
@ActiveProfiles("test")
class SpringBootIntegrationTest {

    @Autowired
    private FileClient fileClient;

    private static final String TEST_CONTENT = "Hello, LiteFS Spring Boot Integration Test!";

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Test
    void testFileClientInjected() {
        assertNotNull(fileClient, "FileClient should be injected by Spring");
    }

    @Test
    void testUploadAndDownload() throws IOException {
        InputStream uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(uploadStream, "springboot-test.txt", Map.of("source", "springboot-test"));

        assertNotNull(fileId, "File ID should not be null");

        try (InputStream downloadStream = fileClient.download(fileId)) {
            String content = new String(downloadStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TEST_CONTENT, content, "Downloaded content should match uploaded content");
        }
    }

    @Test
    void testMetadataOperations() {
        InputStream uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(uploadStream, "metadata-test.txt", Map.of("key1", "value1", "key2", "value2"));

        FileMetadata metadata = fileClient.getMetadata(fileId);

        assertNotNull(metadata, "Metadata should not be null");
        assertEquals("metadata-test.txt", metadata.getFileName());
        assertEquals("text/plain", metadata.getContentType());
        assertEquals(TEST_CONTENT.length(), metadata.getFileSize());
        assertEquals("value1", metadata.getMetadata("key1"));
        assertEquals("value2", metadata.getMetadata("key2"));
    }

    @Test
    void testUpdateMetadata() {
        InputStream uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(uploadStream, "update-meta.txt", Map.of("original", "yes"));

        fileClient.updateMetadata(fileId, Map.of("updated", "true", "newKey", "newValue"));

        FileMetadata metadata = fileClient.getMetadata(fileId);
        assertEquals("yes", metadata.getMetadata("original"));
        assertEquals("true", metadata.getMetadata("updated"));
        assertEquals("newValue", metadata.getMetadata("newKey"));
    }

    @Test
    void testListFiles() {
        for (int i = 0; i < 3; i++) {
            InputStream uploadStream = new ByteArrayInputStream(("Content " + i).getBytes(StandardCharsets.UTF_8));
            fileClient.upload(uploadStream, "list-test-" + i + ".txt", Map.of("test", "list"));
        }

        FileQuery query = FileQuery.builder()
                .fileName("list-test")
                .pageSize(10)
                .build();

        List<FileMetadata> files = fileClient.listFiles(query);

        assertTrue(files.size() >= 3, "Should find at least 3 files");
    }

    @Test
    void testCopyFile() throws IOException {
        InputStream uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String originalId = fileClient.upload(uploadStream, "copy-original.txt", null);

        String copiedId = fileClient.copy(originalId);

        assertNotEquals(originalId, copiedId, "Copied file ID should be different");

        try (InputStream downloadStream = fileClient.download(copiedId)) {
            String content = new String(downloadStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TEST_CONTENT, content, "Copied content should match original");
        }
    }

    @Test
    void testDeleteFile() {
        InputStream uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(uploadStream, "delete-test.txt", null);

        assertNotNull(fileClient.getMetadata(fileId));

        fileClient.delete(fileId);

        FileMetadata metadata = fileClient.getMetadata(fileId);
        assertNotNull(metadata, "Metadata should still exist after soft delete");
    }

    @Test
    void testUrlGeneration() {
        InputStream uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(uploadStream, "url-test.txt", null);

        String url = fileClient.getUrl(fileId);
        assertNotNull(url);
        assertTrue(url.contains(fileId));

        String signedUrl = fileClient.getUrl(fileId, 3600);
        assertNotNull(signedUrl);
        assertTrue(signedUrl.contains("token="));
        assertTrue(signedUrl.contains("expire="));

        String downloadUrl = fileClient.getDownloadUrl(fileId);
        assertNotNull(downloadUrl);
        assertTrue(downloadUrl.contains("/download"));
    }

    @Test
    void testMultipartUpload() throws IOException {
        InitMultipartUploadResult result = fileClient.initMultipartUpload("multipart-springboot.txt", 100, Map.of("type", "multipart"));
        String uploadId = result.getUploadId();
        assertNotNull(uploadId);

        String eTag1 = fileClient.uploadPart(uploadId, 1, new ByteArrayInputStream("Part1".getBytes(StandardCharsets.UTF_8)));
        String eTag2 = fileClient.uploadPart(uploadId, 2, new ByteArrayInputStream("Part2".getBytes(StandardCharsets.UTF_8)));

        assertNotNull(eTag1);
        assertNotNull(eTag2);

        String fileId = fileClient.completeMultipartUpload(uploadId, List.of(
                io.github.fangyudev.litefs.model.PartInfo.builder().partNumber(1).eTag(eTag1).build(),
                io.github.fangyudev.litefs.model.PartInfo.builder().partNumber(2).eTag(eTag2).build()
        ));

        assertNotNull(fileId);

        try (InputStream downloadStream = fileClient.download(fileId)) {
            String content = new String(downloadStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("Part1Part2", content);
        }
    }
}
