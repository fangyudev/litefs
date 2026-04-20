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

package io.github.fangyudev.litefs.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class IoUtilsTest {

    @Test
    void testCopy() throws IOException {
        String content = "Hello, World!";
        InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        long bytesCopied = IoUtils.copy(input, output);

        assertEquals(content.length(), bytesCopied);
        assertEquals(content, output.toString(StandardCharsets.UTF_8.name()));
    }

    @Test
    void testCopyEmpty() throws IOException {
        InputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        long bytesCopied = IoUtils.copy(input, output);

        assertEquals(0, bytesCopied);
        assertEquals(0, output.size());
    }

    @Test
    void testCopyLargeData() throws IOException {
        byte[] largeData = new byte[1024 * 1024];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        InputStream input = new ByteArrayInputStream(largeData);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        long bytesCopied = IoUtils.copy(input, output);

        assertEquals(largeData.length, bytesCopied);
        assertArrayEquals(largeData, output.toByteArray());
    }

    @Test
    void testToByteArray() throws IOException {
        String content = "Test content for toByteArray";
        InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        byte[] result = IoUtils.toByteArray(input);

        assertEquals(content, new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void testToByteArrayEmpty() throws IOException {
        InputStream input = new ByteArrayInputStream(new byte[0]);

        byte[] result = IoUtils.toByteArray(input);

        assertEquals(0, result.length);
    }

    @Test
    void testCloseQuietlyWithNull() {
        AutoCloseable nullCloseable = null;
        assertDoesNotThrow(() -> IoUtils.closeQuietly(nullCloseable));
    }

    @Test
    void testCloseQuietlyWithValidCloseable() {
        InputStream input = new ByteArrayInputStream(new byte[0]);
        assertDoesNotThrow(() -> IoUtils.closeQuietly((AutoCloseable) input));
    }

    @Test
    void testCloseQuietlyWithException() {
        AutoCloseable closeable = () -> {
            throw new RuntimeException("Test exception");
        };
        assertDoesNotThrow(() -> IoUtils.closeQuietly(closeable));
    }

    @Test
    void testCloseQuietlyMultiple() {
        InputStream input1 = new ByteArrayInputStream(new byte[0]);
        InputStream input2 = new ByteArrayInputStream(new byte[0]);
        assertDoesNotThrow(() -> IoUtils.closeQuietly(input1, input2));
    }

    @Test
    void testCloseQuietlyMultipleWithNull() {
        InputStream input1 = new ByteArrayInputStream(new byte[0]);
        assertDoesNotThrow(() -> IoUtils.closeQuietly(input1, null, input1));
    }
}
