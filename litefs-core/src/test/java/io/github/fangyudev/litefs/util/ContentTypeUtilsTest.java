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

class ContentTypeUtilsTest {

    @Test
    void testGetContentTypeJpg() {
        assertEquals("image/jpeg", ContentTypeUtils.getContentType("test.jpg"));
        assertEquals("image/jpeg", ContentTypeUtils.getContentType("test.jpeg"));
    }

    @Test
    void testGetContentTypePng() {
        assertEquals("image/png", ContentTypeUtils.getContentType("test.png"));
    }

    @Test
    void testGetContentTypeGif() {
        assertEquals("image/gif", ContentTypeUtils.getContentType("test.gif"));
    }

    @Test
    void testGetContentTypePdf() {
        assertEquals("application/pdf", ContentTypeUtils.getContentType("document.pdf"));
    }

    @Test
    void testGetContentTypeTxt() {
        assertEquals("text/plain", ContentTypeUtils.getContentType("readme.txt"));
    }

    @Test
    void testGetContentTypeHtml() {
        assertEquals("text/html", ContentTypeUtils.getContentType("index.html"));
        assertEquals("text/html", ContentTypeUtils.getContentType("index.htm"));
    }

    @Test
    void testGetContentTypeJson() {
        assertEquals("application/json", ContentTypeUtils.getContentType("data.json"));
    }

    @Test
    void testGetContentTypeMp4() {
        assertEquals("video/mp4", ContentTypeUtils.getContentType("video.mp4"));
    }

    @Test
    void testGetContentTypeMp3() {
        assertEquals("audio/mpeg", ContentTypeUtils.getContentType("audio.mp3"));
    }

    @Test
    void testGetContentTypeZip() {
        assertEquals("application/zip", ContentTypeUtils.getContentType("archive.zip"));
    }

    @Test
    void testGetContentTypeUnknown() {
        assertEquals("application/octet-stream", ContentTypeUtils.getContentType("test.xyz"));
        assertEquals("application/octet-stream", ContentTypeUtils.getContentType("test.unknown"));
    }

    @Test
    void testGetContentTypeNoExtension() {
        assertEquals("application/octet-stream", ContentTypeUtils.getContentType("testfile"));
    }

    @Test
    void testGetContentTypeNull() {
        assertEquals("application/octet-stream", ContentTypeUtils.getContentType(null));
    }

    @Test
    void testGetContentTypeEmpty() {
        assertEquals("application/octet-stream", ContentTypeUtils.getContentType(""));
    }

    @Test
    void testGetContentTypeDotOnly() {
        assertEquals("application/octet-stream", ContentTypeUtils.getContentType("test."));
    }

    @Test
    void testGetContentTypeCaseInsensitive() {
        assertEquals("image/jpeg", ContentTypeUtils.getContentType("test.JPG"));
        assertEquals("image/png", ContentTypeUtils.getContentType("test.PNG"));
        assertEquals("application/pdf", ContentTypeUtils.getContentType("test.PDF"));
    }

    @Test
    void testIsImage() {
        assertTrue(ContentTypeUtils.isImage("image/jpeg"));
        assertTrue(ContentTypeUtils.isImage("image/png"));
        assertTrue(ContentTypeUtils.isImage("image/gif"));
        assertTrue(ContentTypeUtils.isImage("image/webp"));
        assertFalse(ContentTypeUtils.isImage("application/pdf"));
        assertFalse(ContentTypeUtils.isImage("video/mp4"));
    }

    @Test
    void testIsImageNull() {
        assertFalse(ContentTypeUtils.isImage(null));
    }

    @Test
    void testIsVideo() {
        assertTrue(ContentTypeUtils.isVideo("video/mp4"));
        assertTrue(ContentTypeUtils.isVideo("video/x-msvideo"));
        assertTrue(ContentTypeUtils.isVideo("video/quicktime"));
        assertFalse(ContentTypeUtils.isVideo("image/jpeg"));
        assertFalse(ContentTypeUtils.isVideo("audio/mpeg"));
    }

    @Test
    void testIsVideoNull() {
        assertFalse(ContentTypeUtils.isVideo(null));
    }

    @Test
    void testIsAudio() {
        assertTrue(ContentTypeUtils.isAudio("audio/mpeg"));
        assertTrue(ContentTypeUtils.isAudio("audio/wav"));
        assertFalse(ContentTypeUtils.isAudio("video/mp4"));
        assertFalse(ContentTypeUtils.isAudio("image/jpeg"));
    }

    @Test
    void testIsAudioNull() {
        assertFalse(ContentTypeUtils.isAudio(null));
    }

    @Test
    void testMultipleDots() {
        assertEquals("image/jpeg", ContentTypeUtils.getContentType("test.file.jpg"));
        assertEquals("application/pdf", ContentTypeUtils.getContentType("my.document.final.pdf"));
    }
}
