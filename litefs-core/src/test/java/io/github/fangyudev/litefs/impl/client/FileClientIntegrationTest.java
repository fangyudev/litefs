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
import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileQuery;
import io.github.fangyudev.litefs.model.InitMultipartUploadResult;
import io.github.fangyudev.litefs.model.PartInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileClientIntegrationTest {

    private FileClient fileClient;
    private static final String TEST_CONTENT = "Hello, LiteFS! This is a test file content.";

    @BeforeEach
    void setUp() {
        fileClient = FileClientBuilder.builder()
                .storagePath("./target/test-files")
                .jdbcUrl("jdbc:h2:./target/test-litefs;AUTO_SERVER=TRUE")
                .gatewayBaseUrl("http://localhost:8080")
                .gatewayPathPrefix("/api/files")
                .secretKey("test-secret-key")
                .nodeId("test-node-1")
                .build();
    }

    @Test
    void testUpload() {
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(inputStream, "test.txt", Map.of("category", "test"));

        assertNotNull(fileId);
        System.out.println("Upload test passed. File ID: " + fileId);
    }

    @Test
    void testDownload() {
        InputStream uploadStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(uploadStream, "download-test.txt", Map.of("type", "download"));

        try (InputStream downloadStream = fileClient.download(fileId)) {
            String content = new String(downloadStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TEST_CONTENT, content);
            System.out.println("Download test passed. Content: " + content);
        } catch (IOException e) {
            fail("Failed to download file", e);
        }
    }

    @Test
    void testGetMetadata() {
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(inputStream, "metadata-test.txt", Map.of("key1", "value1"));

        FileMetadata metadata = fileClient.getMetadata(fileId);

        assertNotNull(metadata);
        assertEquals("metadata-test.txt", metadata.getFileName());
        assertEquals("text/plain", metadata.getContentType());
        assertEquals(TEST_CONTENT.length(), metadata.getFileSize());
        assertEquals("value1", metadata.getMetadata("key1"));
        System.out.println("Metadata test passed. FileName: " + metadata.getFileName());
    }

    @Test
    void testDelete() {
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(inputStream, "delete-test.txt", null);

        assertNotNull(fileClient.getMetadata(fileId));

        fileClient.delete(fileId);

        FileMetadata metadata = fileClient.getMetadata(fileId);
        assertNotNull(metadata);
        System.out.println("Delete test passed.");
    }

    @Test
    void testCopy() {
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String originalId = fileClient.upload(inputStream, "copy-test.txt", Map.of("original", "true"));

        String copiedId = fileClient.copy(originalId);

        assertNotEquals(originalId, copiedId);

        FileMetadata copiedMetadata = fileClient.getMetadata(copiedId);
        assertNotNull(copiedMetadata);
        assertEquals("copy-test.txt", copiedMetadata.getFileName());
        System.out.println("Copy test passed. Original: " + originalId + ", Copied: " + copiedId);
    }

    @Test
    void testUpdateMetadata() {
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(inputStream, "update-meta-test.txt", Map.of("old", "value"));

        fileClient.updateMetadata(fileId, Map.of("new", "value2"));

        FileMetadata metadata = fileClient.getMetadata(fileId);
        assertEquals("value", metadata.getMetadata("old"));
        assertEquals("value2", metadata.getMetadata("new"));
        System.out.println("Update metadata test passed.");
    }

    @Test
    void testListFiles() {
        for (int i = 0; i < 5; i++) {
            InputStream inputStream = new ByteArrayInputStream(("Test content " + i).getBytes(StandardCharsets.UTF_8));
            fileClient.upload(inputStream, "list-test-" + i + ".txt", Map.of("batch", "list"));
        }

        FileQuery query = FileQuery.builder()
                .fileName("list-test")
                .pageSize(10)
                .build();

        List<FileMetadata> files = fileClient.listFiles(query);

        assertTrue(files.size() >= 5);
        System.out.println("List files test passed. Found " + files.size() + " files.");
    }

    @Test
    void testGetUrl() {
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(inputStream, "url-test.txt", null);

        String url = fileClient.getUrl(fileId);
        assertNotNull(url);
        assertTrue(url.contains(fileId));
        System.out.println("Get URL test passed. URL: " + url);
    }

    @Test
    void testGetSignedUrl() {
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(inputStream, "signed-url-test.txt", null);

        String signedUrl = fileClient.getUrl(fileId, 3600);
        assertNotNull(signedUrl);
        assertTrue(signedUrl.contains("token="));
        assertTrue(signedUrl.contains("expire="));
        System.out.println("Get signed URL test passed. URL: " + signedUrl);
    }

    @Test
    void testGetDownloadUrl() {
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
        String fileId = fileClient.upload(inputStream, "download-url-test.txt", null);

        String downloadUrl = fileClient.getDownloadUrl(fileId);
        assertNotNull(downloadUrl);
        assertTrue(downloadUrl.contains("/download"));
        System.out.println("Get download URL test passed. URL: " + downloadUrl);
    }

    @Test
    void testMultipartUpload() {
        InitMultipartUploadResult result = fileClient.initMultipartUpload("multipart-test.txt", 100, Map.of("type", "multipart"));
        String uploadId = result.getUploadId();
        assertNotNull(uploadId);

        String eTag1 = fileClient.uploadPart(uploadId, 1, new ByteArrayInputStream("Part1".getBytes(StandardCharsets.UTF_8)));
        String eTag2 = fileClient.uploadPart(uploadId, 2, new ByteArrayInputStream("Part2".getBytes(StandardCharsets.UTF_8)));

        assertNotNull(eTag1);
        assertNotNull(eTag2);

        String fileId = fileClient.completeMultipartUpload(uploadId, List.of(
                PartInfo.builder().partNumber(1).eTag(eTag1).build(),
                PartInfo.builder().partNumber(2).eTag(eTag2).build()
        ));

        assertNotNull(fileId);
        System.out.println("Multipart upload test passed. File ID: " + fileId);
    }
}
