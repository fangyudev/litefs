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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 校验和工具类，提供MD5校验和计算功能。
 */
public final class ChecksumUtils {

    private static final String MD5_ALGORITHM = "MD5";
    private static final int BUFFER_SIZE = 8192;

    private ChecksumUtils() {
    }

    /**
     * 计算输入流的MD5校验和。
     */
    public static String md5Hex(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return toHexString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * 计算字符串的MD5校验和。
     */
    public static String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHexString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * 计算字节数组的MD5校验和。
     */
    public static String md5Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] hash = digest.digest(data);
            return toHexString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串。
     */
    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
