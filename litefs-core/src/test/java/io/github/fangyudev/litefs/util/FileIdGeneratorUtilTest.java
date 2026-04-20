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

import static org.junit.jupiter.api.Assertions.*;

class FileIdGeneratorUtilTest {

    @Test
    void testGenerate() {
        String fileId = FileIdGeneratorUtil.generate();

        assertNotNull(fileId);
        assertEquals(64, fileId.length());
        assertTrue(fileId.matches("[0-9a-f]+"));
    }

    @Test
    void testGenerateUniqueness() {
        String id1 = FileIdGeneratorUtil.generate();
        String id2 = FileIdGeneratorUtil.generate();

        assertNotEquals(id1, id2);
    }

    @Test
    void testGenerateWithFileName() {
        String fileName = "test.txt";
        String fileId = FileIdGeneratorUtil.generate(fileName);

        assertNotNull(fileId);
        assertEquals(64, fileId.length());
        assertTrue(fileId.matches("[0-9a-f]+"));
    }

    @Test
    void testGenerateWithFileNameUniqueness() {
        String fileName = "test.txt";
        String id1 = FileIdGeneratorUtil.generate(fileName);
        String id2 = FileIdGeneratorUtil.generate(fileName);

        assertNotEquals(id1, id2);
    }

    @Test
    void testGenerateWithDifferentFileNames() {
        String id1 = FileIdGeneratorUtil.generate("file1.txt");
        String id2 = FileIdGeneratorUtil.generate("file2.txt");

        assertNotEquals(id1, id2);
    }

    @Test
    void testGenerateWithNullFileName() {
        String fileId = FileIdGeneratorUtil.generate(null);

        assertNotNull(fileId);
        assertEquals(64, fileId.length());
    }

    @Test
    void testGenerateWithEmptyFileName() {
        String fileId = FileIdGeneratorUtil.generate("");

        assertNotNull(fileId);
        assertEquals(64, fileId.length());
    }

    @Test
    void testGenerateWithChineseFileName() {
        String fileId = FileIdGeneratorUtil.generate("测试文件.txt");

        assertNotNull(fileId);
        assertEquals(64, fileId.length());
    }

    @Test
    void testGenerateMultiple() {
        for (int i = 0; i < 100; i++) {
            String fileId = FileIdGeneratorUtil.generate();
            assertNotNull(fileId);
            assertEquals(64, fileId.length());
        }
    }
}
