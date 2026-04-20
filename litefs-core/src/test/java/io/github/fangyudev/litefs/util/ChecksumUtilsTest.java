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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumUtilsTest {

    @Test
    void testMd5HexString() {
        String input = "Hello, World!";
        String result = ChecksumUtils.md5Hex(input);

        assertNotNull(result);
        assertEquals(32, result.length());
        assertEquals("65a8e27d8879283831b664bd8b7f0ad4", result);
    }

    @Test
    void testMd5HexEmptyString() {
        String result = ChecksumUtils.md5Hex("");

        assertNotNull(result);
        assertEquals(32, result.length());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", result);
    }

    @Test
    void testMd5HexByteArray() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String result = ChecksumUtils.md5Hex(data);

        assertNotNull(result);
        assertEquals(32, result.length());
        assertEquals("65a8e27d8879283831b664bd8b7f0ad4", result);
    }

    @Test
    void testMd5HexInputStream() throws IOException {
        String content = "Hello, World!";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = ChecksumUtils.md5Hex(inputStream);

        assertNotNull(result);
        assertEquals(32, result.length());
        assertEquals("65a8e27d8879283831b664bd8b7f0ad4", result);
    }

    @Test
    void testMd5HexInputStreamEmpty() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        String result = ChecksumUtils.md5Hex(inputStream);

        assertNotNull(result);
        assertEquals(32, result.length());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", result);
    }

    @Test
    void testToHexString() {
        byte[] bytes = {0x00, 0x01, 0x0a, 0x0f, (byte) 0xff};

        String result = ChecksumUtils.toHexString(bytes);

        assertEquals("00010a0fff", result);
    }

    @Test
    void testToHexStringEmpty() {
        byte[] bytes = new byte[0];

        String result = ChecksumUtils.toHexString(bytes);

        assertEquals("", result);
    }

    @Test
    void testConsistency() throws IOException {
        String content = "Test consistency across methods";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(bytes);

        String result1 = ChecksumUtils.md5Hex(content);
        String result2 = ChecksumUtils.md5Hex(bytes);
        String result3 = ChecksumUtils.md5Hex(inputStream);

        assertEquals(result1, result2);
        assertEquals(result2, result3);
    }

    @Test
    void testChineseCharacters() {
        String chinese = "你好，世界！";

        String result = ChecksumUtils.md5Hex(chinese);

        assertNotNull(result);
        assertEquals(32, result.length());
    }

    @Test
    void testLargeData() throws IOException {
        byte[] largeData = new byte[1024 * 1024];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        String result1 = ChecksumUtils.md5Hex(largeData);
        String result2 = ChecksumUtils.md5Hex(new ByteArrayInputStream(largeData));

        assertEquals(result1, result2);
    }
}
