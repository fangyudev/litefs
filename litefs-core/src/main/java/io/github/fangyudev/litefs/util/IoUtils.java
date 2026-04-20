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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * IO工具类，提供流操作相关的工具方法。
 */
public final class IoUtils {

    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;
    private static final int LARGE_BUFFER_SIZE = 256 * 1024;

    private IoUtils() {
    }

    /**
     * 复制输入流到输出流。
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        return copy(input, output, DEFAULT_BUFFER_SIZE);
    }

    /**
     * 复制输入流到输出流（大文件优化）。
     */
    public static long copyLarge(InputStream input, OutputStream output) throws IOException {
        return copy(input, output, LARGE_BUFFER_SIZE);
    }

    /**
     * 复制输入流到输出流（指定缓冲区大小）。
     */
    public static long copy(InputStream input, OutputStream output, int bufferSize) throws IOException {
        byte[] buffer = new byte[bufferSize];
        long total = 0;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            total += bytesRead;
        }
        return total;
    }

    /**
     * 将输入流转换为字节数组。
     */
    public static byte[] toByteArray(InputStream input) throws IOException {
        return toByteArray(input, DEFAULT_BUFFER_SIZE);
    }

    /**
     * 将输入流转换为字节数组（预估大小）。
     */
    public static byte[] toByteArray(InputStream input, int estimatedSize) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(estimatedSize, DEFAULT_BUFFER_SIZE));
        copy(input, output);
        return output.toByteArray();
    }

    /**
     * 安静地关闭可关闭对象。
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 批量关闭多个可关闭对象。
     */
    public static void closeQuietly(AutoCloseable... closeables) {
        if (closeables != null) {
            for (AutoCloseable closeable : closeables) {
                closeQuietly(closeable);
            }
        }
    }

    /**
     * 包装输入流为缓冲输入流。
     */
    public static BufferedInputStream buffer(InputStream input) {
        if (input instanceof BufferedInputStream) {
            return (BufferedInputStream) input;
        }
        return new BufferedInputStream(input, DEFAULT_BUFFER_SIZE);
    }

    /**
     * 包装输出流为缓冲输出流。
     */
    public static BufferedOutputStream buffer(OutputStream output) {
        if (output instanceof BufferedOutputStream) {
            return (BufferedOutputStream) output;
        }
        return new BufferedOutputStream(output, DEFAULT_BUFFER_SIZE);
    }
}
