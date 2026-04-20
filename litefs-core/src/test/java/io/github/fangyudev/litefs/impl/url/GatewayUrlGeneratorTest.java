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

package io.github.fangyudev.litefs.impl.url;

import io.github.fangyudev.litefs.impl.url.GatewayUrlGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayUrlGeneratorTest {

    private GatewayUrlGenerator generator;
    private static final String BASE_URL = "http://localhost:8080";
    private static final String PATH_PREFIX = "/api/files";
    private static final String SECRET_KEY = "test-secret-key";

    @BeforeEach
    void setUp() {
        generator = new GatewayUrlGenerator(BASE_URL, PATH_PREFIX, SECRET_KEY);
    }

    @Test
    void testGenerateUrl() {
        String fileId = "abc123";

        String url = generator.generateUrl(fileId);

        assertEquals("http://localhost:8080/api/files/abc123", url);
    }

    @Test
    void testGenerateUrlWithDifferentFileId() {
        String url = generator.generateUrl("xyz789");

        assertEquals("http://localhost:8080/api/files/xyz789", url);
    }

    @Test
    void testGenerateSignedUrl() {
        String fileId = "file123";
        long expireSeconds = 3600;

        String url = generator.generateSignedUrl(fileId, expireSeconds);

        assertTrue(url.startsWith(BASE_URL + PATH_PREFIX + "/" + fileId));
        assertTrue(url.contains("token="));
        assertTrue(url.contains("expire="));
    }

    @Test
    void testGenerateSignedUrlContainsCorrectFormat() {
        String fileId = "testfile";
        String url = generator.generateSignedUrl(fileId, 7200);

        assertTrue(url.matches(".*token=[A-Za-z0-9_-]+.*"));
        assertTrue(url.matches(".*expire=\\d+.*"));
    }

    @Test
    void testGenerateDownloadUrl() {
        String fileId = "download123";

        String url = generator.generateDownloadUrl(fileId);

        assertEquals("http://localhost:8080/api/files/download123/download", url);
    }

    @Test
    void testValidateSignatureValid() {
        String fileId = "validate123";
        long expireSeconds = 3600;

        String url = generator.generateSignedUrl(fileId, expireSeconds);
        String token = extractToken(url);
        long expireTime = extractExpire(url);

        assertTrue(generator.validateSignature(fileId, token, expireTime));
    }

    @Test
    void testValidateSignatureExpired() {
        String fileId = "expired123";
        long expireTime = System.currentTimeMillis() / 1000 - 1;

        String token = "sometoken";

        assertFalse(generator.validateSignature(fileId, token, expireTime));
    }

    @Test
    void testValidateSignatureWrongToken() {
        String fileId = "wrongtoken123";
        long expireSeconds = 3600;

        String url = generator.generateSignedUrl(fileId, expireSeconds);
        long expireTime = extractExpire(url);

        assertFalse(generator.validateSignature(fileId, "wrongtoken", expireTime));
    }

    @Test
    void testValidateSignatureWrongFileId() {
        String fileId = "correct123";
        long expireSeconds = 3600;

        String url = generator.generateSignedUrl(fileId, expireSeconds);
        String token = extractToken(url);
        long expireTime = extractExpire(url);

        assertFalse(generator.validateSignature("wrong123", token, expireTime));
    }

    @Test
    void testConstructorWithDefaultKey() {
        GatewayUrlGenerator defaultKeyGenerator = new GatewayUrlGenerator(BASE_URL, PATH_PREFIX);

        String url = defaultKeyGenerator.generateUrl("test");
        assertEquals("http://localhost:8080/api/files/test", url);
    }

    @Test
    void testGetBaseUrl() {
        assertEquals(BASE_URL, generator.getBaseUrl());
    }

    @Test
    void testGetPathPrefix() {
        assertEquals(PATH_PREFIX, generator.getPathPrefix());
    }

    @Test
    void testSignedUrlContainsTokenAndExpire() {
        String fileId = "unique123";

        String url = generator.generateSignedUrl(fileId, 3600);

        assertTrue(url.contains("token="));
        assertTrue(url.contains("expire="));
        assertTrue(url.startsWith(BASE_URL + PATH_PREFIX + "/" + fileId));
    }

    private String extractToken(String url) {
        int tokenStart = url.indexOf("token=") + 6;
        int tokenEnd = url.indexOf("&", tokenStart);
        return url.substring(tokenStart, tokenEnd);
    }

    private long extractExpire(String url) {
        int expireStart = url.indexOf("expire=") + 7;
        return Long.parseLong(url.substring(expireStart));
    }
}
