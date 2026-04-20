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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 文件ID生成器
 * 使用SHA-256算法生成唯一的文件标识符
 *
 * <p>生成的ID特点：</p>
 * <ul>
 *   <li>64位十六进制字符串</li>
 *   <li>全局唯一</li>
 *   <li>不可预测</li>
 * </ul>
 */
public final class FileIdGeneratorUtil {

    /** 哈希算法 */
    private static final String ALGORITHM = "SHA-256";

    /** 私有构造函数，防止实例化 */
    private FileIdGeneratorUtil() {
    }

    /**
     * 生成文件ID
     * 使用UUID和时间戳组合生成
     *
     * @return 文件ID
     */
    public static String generate() {
        String uuid = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        String source = uuid + "-" + timestamp;
        return hash(source);
    }

    /**
     * 生成文件ID（包含文件名）
     *
     * @param fileName 文件名
     * @return 文件ID
     */
    public static String generate(String fileName) {
        String uuid = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        String source = uuid + "-" + fileName + "-" + timestamp;
        return hash(source);
    }

    /**
     * 对输入字符串进行SHA-256哈希
     *
     * @param input 输入字符串
     * @return 十六进制哈希值
     */
    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
