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

package io.github.fangyudev.litefs.impl.engine.storage;

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

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageEngineTest {

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
    void testWrite() {
        String fileId = "abcdef1234567890";
        String content = "Hello, World!";
        InputStream data = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = storageEngine.write(fileId, data);

        assertNotNull(result);
        assertTrue(result.contains(fileId));
        assertTrue(storageEngine.exists(fileId));
    }

    @Test
    void testWriteCreatesMultiLevelDirectory() {
        String fileId = "abcdefgh12345678";
        String content = "Test content";
        InputStream data = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        storageEngine.write(fileId, data);

        Path expectedPath = tempDir.resolve("storage").resolve("ab").resolve("cd").resolve(fileId);
        assertTrue(Files.exists(expectedPath));
    }

    @Test
    void testRead() {
        String fileId = "testread12345678";
        String content = "Read test content";
        storageEngine.write(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        try (InputStream result = storageEngine.read(fileId)) {
            String readContent = new String(result.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content, readContent);
        } catch (IOException e) {
            fail("Failed to read file", e);
        }
    }

    @Test
    void testReadNotFound() {
        String fileId = "notexist12345678";
        assertThrows(RuntimeException.class, () -> storageEngine.read(fileId));
    }

    @Test
    void testDelete() {
        String fileId = "todelete123456789";
        String content = "To be deleted";
        storageEngine.write(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertTrue(storageEngine.exists(fileId));

        storageEngine.delete(fileId);

        assertFalse(storageEngine.exists(fileId));
    }

    @Test
    void testDeleteNonExistent() {
        String fileId = "nonexistent1234";
        assertDoesNotThrow(() -> storageEngine.delete(fileId));
    }

    @Test
    void testExists() {
        String fileId = "existtest12345678";
        assertFalse(storageEngine.exists(fileId));

        storageEngine.write(fileId, new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)));

        assertTrue(storageEngine.exists(fileId));
    }

    @Test
    void testGetSize() {
        String fileId = "sizetest12345678";
        String content = "Size test content";
        storageEngine.write(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        long size = storageEngine.getSize(fileId);

        assertEquals(content.length(), size);
    }

    @Test
    void testGetSizeNonExistent() {
        long size = storageEngine.getSize("nonexistent1234");
        assertEquals(-1, size);
    }

    @Test
    void testAppend() {
        String fileId = "appendtest1234567";
        String content1 = "First part ";
        String content2 = "Second part";

        storageEngine.write(fileId, new ByteArrayInputStream(content1.getBytes(StandardCharsets.UTF_8)));
        storageEngine.append(fileId, new ByteArrayInputStream(content2.getBytes(StandardCharsets.UTF_8)));

        try (InputStream result = storageEngine.read(fileId)) {
            String readContent = new String(result.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content1 + content2, readContent);
        } catch (IOException e) {
            fail("Failed to read file", e);
        }
    }

    @Test
    void testAppendToNewFile() {
        String fileId = "appendnew12345678";
        String content = "Appended content";

        long newSize = storageEngine.append(fileId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertEquals(content.length(), newSize);
        assertTrue(storageEngine.exists(fileId));
    }

    @Test
    void testGetStoragePath() {
        String fileId = "abcdefgh12345678";

        String storagePath = storageEngine.getStoragePath(fileId);

        assertEquals("ab/cd/abcdefgh12345678", storagePath);
    }

    @Test
    void testGetStoragePathInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> storageEngine.getStoragePath("ab"));
        assertThrows(IllegalArgumentException.class, () -> storageEngine.getStoragePath(null));
    }

    @Test
    void testWriteInvalidFileId() {
        InputStream data = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> storageEngine.write("ab", data));
    }

    @Test
    void testReadInvalidFileId() {
        assertThrows(IllegalArgumentException.class, () -> storageEngine.read(null));
        assertThrows(IllegalArgumentException.class, () -> storageEngine.read(""));
    }

    @Test
    void testGetBasePath() {
        assertEquals(storagePath, storageEngine.getBasePath());
    }

    @Test
    void testOverwrite() {
        String fileId = "overwrite12345678";
        String content1 = "Original content";
        String content2 = "New content";

        storageEngine.write(fileId, new ByteArrayInputStream(content1.getBytes(StandardCharsets.UTF_8)));
        storageEngine.write(fileId, new ByteArrayInputStream(content2.getBytes(StandardCharsets.UTF_8)));

        try (InputStream result = storageEngine.read(fileId)) {
            String readContent = new String(result.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content2, readContent);
        } catch (IOException e) {
            fail("Failed to read file", e);
        }
    }

    @Test
    void testLargeFile() {
        String fileId = "largefile12345678";
        byte[] largeContent = new byte[1024 * 1024];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }

        storageEngine.write(fileId, new ByteArrayInputStream(largeContent));

        assertEquals(largeContent.length, storageEngine.getSize(fileId));
    }
}
