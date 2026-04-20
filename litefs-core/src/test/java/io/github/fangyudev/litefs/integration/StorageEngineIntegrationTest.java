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

import io.github.fangyudev.litefs.impl.store.engine.local.LocalStorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StorageEngineIntegrationTest {

    @TempDir
    Path tempDir;

    private LocalStorageEngine storageEngine;
    private String storagePath;

    @BeforeEach
    void setUp() {
        storagePath = tempDir.resolve("storage").toString();
        storageEngine = new LocalStorageEngine(storagePath);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (storageEngine != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                        }
                    });
        }
    }

    @Test
    void testBasicWriteAndRead() throws IOException {
        String fileId = "testfile12345678";
        String content = "Hello, Storage Engine!";

        String path = storageEngine.write(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(path);
        assertTrue(storageEngine.exists(fileId));

        try (InputStream is = storageEngine.read(fileId)) {
            String readContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content, readContent);
        }
    }

    @Test
    void testDelete() {
        String fileId = "deletefile1234567";
        storageEngine.write(fileId, new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        assertTrue(storageEngine.exists(fileId));

        storageEngine.delete(fileId);

        assertFalse(storageEngine.exists(fileId));
    }

    @Test
    void testExists() {
        String fileId = "existfile123456789";
        assertFalse(storageEngine.exists(fileId));

        storageEngine.write(fileId, new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        assertTrue(storageEngine.exists(fileId));
    }

    @Test
    void testGetSize() {
        String fileId = "sizefile123456789";
        String content = "Content for size test";

        storageEngine.write(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        long size = storageEngine.getSize(fileId);
        assertEquals(content.length(), size);
    }

    @Test
    void testGetSizeNonExistent() {
        long size = storageEngine.getSize("nonexistent12345");
        assertEquals(-1, size);
    }

    @Test
    void testAppend() throws IOException {
        String fileId = "appendfile1234567";
        String part1 = "First part ";
        String part2 = "Second part";

        storageEngine.write(fileId, new ByteArrayInputStream(part1.getBytes(StandardCharsets.UTF_8)));
        long newSize = storageEngine.append(fileId, new ByteArrayInputStream(part2.getBytes(StandardCharsets.UTF_8)));

        assertEquals(part1.length() + part2.length(), newSize);

        try (InputStream is = storageEngine.read(fileId)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(part1 + part2, content);
        }
    }

    @Test
    void testAppendToNewFile() throws IOException {
        String fileId = "appendnew123456789";
        String content = "Appended to new file";

        long size = storageEngine.append(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertEquals(content.length(), size);
        assertTrue(storageEngine.exists(fileId));

        try (InputStream is = storageEngine.read(fileId)) {
            String readContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content, readContent);
        }
    }

    @Test
    void testGetStoragePath() {
        String fileId = "storagepath123456";

        String path = storageEngine.getStoragePath(fileId);

        assertNotNull(path);
        assertTrue(path.contains(fileId));
    }

    @Test
    void testOverwrite() throws IOException {
        String fileId = "overwrite123456789";
        String original = "Original content";
        String newContent = "New content that replaces original";

        storageEngine.write(fileId, new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8)));
        storageEngine.write(fileId, new ByteArrayInputStream(newContent.getBytes(StandardCharsets.UTF_8)));

        try (InputStream is = storageEngine.read(fileId)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(newContent, content);
        }
    }

    @Test
    void testLargeFile() throws IOException {
        String fileId = "largefile123456789";
        int size = 10 * 1024 * 1024;
        byte[] largeContent = new byte[size];
        for (int i = 0; i < size; i++) {
            largeContent[i] = (byte) (i % 256);
        }

        storageEngine.write(fileId, new ByteArrayInputStream(largeContent));

        assertEquals(size, storageEngine.getSize(fileId));

        try (InputStream is = storageEngine.read(fileId)) {
            byte[] readContent = is.readAllBytes();
            assertEquals(size, readContent.length);
            for (int i = 0; i < size; i++) {
                assertEquals((byte) (i % 256), readContent[i], "Mismatch at position " + i);
            }
        }
    }

    @Test
    void testConcurrentWrites() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> fileIds = new ArrayList<>();

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    String fileId = "concurrent" + String.format("%016d", index);
                    String content = "Content from thread " + index;
                    storageEngine.write(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
                    synchronized (fileIds) {
                        fileIds.add(fileId);
                    }
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
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        assertEquals(threadCount, successCount.get());

        for (String fileId : fileIds) {
            assertTrue(storageEngine.exists(fileId));
        }
    }

    @Test
    void testConcurrentReads() throws InterruptedException {
        String fileId = "concurrentread123";
        String content = "Content for concurrent read test";
        storageEngine.write(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    try (InputStream is = storageEngine.read(fileId)) {
                        String readContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        if (content.equals(readContent)) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        assertEquals(threadCount, successCount.get());
    }

    @Test
    void testMultipleAppends() throws IOException {
        String fileId = "multiappend1234567";
        StringBuilder expected = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            String part = "Part " + i + " ";
            expected.append(part);
            storageEngine.append(fileId, new ByteArrayInputStream(part.getBytes(StandardCharsets.UTF_8)));
        }

        try (InputStream is = storageEngine.read(fileId)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(expected.toString(), content);
        }
    }

    @Test
    void testReadNonExistentFile() {
        assertThrows(RuntimeException.class, () -> storageEngine.read("nonexistent1234567"));
    }

    @Test
    void testDeleteNonExistentFile() {
        assertDoesNotThrow(() -> storageEngine.delete("nonexistent1234567"));
    }

    @Test
    void testWriteWithInvalidFileId() {
        assertThrows(IllegalArgumentException.class, () -> 
            storageEngine.write("ab", new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> 
            storageEngine.write(null, new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void testReadWithInvalidFileId() {
        assertThrows(IllegalArgumentException.class, () -> storageEngine.read(null));
        assertThrows(IllegalArgumentException.class, () -> storageEngine.read(""));
    }

    @Test
    void testGetStoragePathWithInvalidFileId() {
        assertThrows(IllegalArgumentException.class, () -> storageEngine.getStoragePath("ab"));
        assertThrows(IllegalArgumentException.class, () -> storageEngine.getStoragePath(null));
    }

    @Test
    void testBinaryContent() throws IOException {
        String fileId = "binaryfile12345678";
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte) i;
        }

        storageEngine.write(fileId, new ByteArrayInputStream(binaryData));

        try (InputStream is = storageEngine.read(fileId)) {
            byte[] readData = is.readAllBytes();
            assertArrayEquals(binaryData, readData);
        }
    }

    @Test
    void testEmptyFile() throws IOException {
        String fileId = "emptyfile123456789";
        byte[] emptyContent = new byte[0];

        storageEngine.write(fileId, new ByteArrayInputStream(emptyContent));

        assertTrue(storageEngine.exists(fileId));
        assertEquals(0, storageEngine.getSize(fileId));

        try (InputStream is = storageEngine.read(fileId)) {
            byte[] readData = is.readAllBytes();
            assertEquals(0, readData.length);
        }
    }

    @Test
    void testUnicodeContent() throws IOException {
        String fileId = "unicode123456789012";
        String unicodeContent = "你好世界 🌍 Hello World مرحبا";

        storageEngine.write(fileId, new ByteArrayInputStream(unicodeContent.getBytes(StandardCharsets.UTF_8)));

        try (InputStream is = storageEngine.read(fileId)) {
            String readContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(unicodeContent, readContent);
        }
    }

    @Test
    void testStoragePathHierarchy() {
        String fileId = "abcdefgh12345678";

        storageEngine.write(fileId, new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)));

        Path expectedPath = tempDir.resolve("storage").resolve("ab").resolve("cd").resolve(fileId);
        assertTrue(Files.exists(expectedPath));
    }

    @Test
    void testGetBasePath() {
        assertEquals(storagePath, storageEngine.getBasePath());
    }

    @Test
    void testMultipleFilesInSameDirectory() throws IOException {
        String[] fileIds = {
            "abcdefgh12345678",
            "abcdefgh12345679",
            "abcdefgh12345680"
        };

        for (int i = 0; i < fileIds.length; i++) {
            String content = "Content " + i;
            storageEngine.write(fileIds[i], new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }

        for (int i = 0; i < fileIds.length; i++) {
            assertTrue(storageEngine.exists(fileIds[i]));
            try (InputStream is = storageEngine.read(fileIds[i])) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals("Content " + i, content);
            }
        }
    }

    @Test
    void testFilePersistence() throws IOException {
        String fileId = "persist123456789012";
        String content = "Persistent content";

        storageEngine.write(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        LocalStorageEngine newEngine = new LocalStorageEngine(storagePath);
        assertTrue(newEngine.exists(fileId));

        try (InputStream is = newEngine.read(fileId)) {
            String readContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content, readContent);
        }
    }
}
