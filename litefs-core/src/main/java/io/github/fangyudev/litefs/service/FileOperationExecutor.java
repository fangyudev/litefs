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

package io.github.fangyudev.litefs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件操作线程池
 * 
 * <p>为文件操作提供专用的线程池，支持并行处理多个文件操作。</p>
 * 
 * <h3>特性：</h3>
 * <ul>
 *   <li><b>线程池隔离</b> - 文件操作使用独立线程池，不影响业务线程</li>
 *   <li><b>动态调整</b> - 根据CPU核心数自动配置线程池大小</li>
 *   <li><b>任务监控</b> - 提供任务执行统计和监控</li>
 *   <li><b>优雅关闭</b> - 支持等待任务完成后关闭</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * FileOperationExecutor executor = new FileOperationExecutor(4, 100);
 * 
 * // 并行执行多个文件复制
 * List<CompletableFuture<String>> futures = new ArrayList<>();
 * for (String fileId : fileIds) {
 *     futures.add(executor.submit(() -> fileClient.copy(fileId)));
 * }
 * 
 * // 等待所有任务完成
 * CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
 * 
 * // 关闭线程池
 * executor.shutdown();
 * }</pre>
 */
public class FileOperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(FileOperationExecutor.class);

    /** 默认核心线程数 */
    private static final int DEFAULT_CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /** 默认最大线程数 */
    private static final int DEFAULT_MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    /** 默认队列容量 */
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    /** 默认空闲线程存活时间（秒） */
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    /** 线程池 */
    private final ThreadPoolExecutor executor;

    /** 线程工厂 */
    private static class FileOperationThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String namePrefix;

        FileOperationThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + counter.incrementAndGet());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * 默认构造函数
     * 使用CPU核心数自动配置线程池大小
     */
    public FileOperationExecutor() {
        this(DEFAULT_CORE_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * 构造函数
     * 
     * @param corePoolSize 核心线程数
     * @param queueCapacity 队列容量
     */
    public FileOperationExecutor(int corePoolSize, int queueCapacity) {
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                DEFAULT_MAX_POOL_SIZE,
                DEFAULT_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new FileOperationThreadFactory("litefs-file-op"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("FileOperationExecutor initialized: coreSize={}, maxSize={}, queueCapacity={}",
                corePoolSize, DEFAULT_MAX_POOL_SIZE, queueCapacity);
    }

    /**
     * 提交任务
     * 
     * @param task 任务
     * @param <T> 返回类型
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * 提交无返回值任务
     * 
     * @param task 任务
     * @return CompletableFuture
     */
    public CompletableFuture<Void> submit(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }

    /**
     * 并行执行多个任务
     * 
     * @param tasks 任务列表
     * @param <T> 返回类型
     * @return 所有任务的结果列表
     */
    public <T> CompletableFuture<List<T>> submitAll(List<Callable<T>> tasks) {
        List<CompletableFuture<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<T> results = new ArrayList<>();
                    for (CompletableFuture<T> future : futures) {
                        try {
                            results.add(future.join());
                        } catch (CompletionException e) {
                            log.warn("Task execution failed", e.getCause());
                            results.add(null);
                        }
                    }
                    return results;
                });
    }

    /**
     * 执行任务（无返回值，无等待）
     * 
     * @param task 任务
     */
    public void execute(Runnable task) {
        executor.execute(task);
    }

    /**
     * 优雅关闭
     * 等待所有任务完成后关闭
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("FileOperationExecutor shutdown");
    }

    /**
     * 立即关闭
     */
    public void shutdownNow() {
        executor.shutdownNow();
        log.info("FileOperationExecutor shutdown immediately");
    }

    /**
     * 获取活跃线程数
     */
    public int getActiveCount() {
        return executor.getActiveCount();
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * 获取已完成任务数
     */
    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    /**
     * 获取线程池状态
     */
    public String getStatus() {
        return String.format("FileOperationExecutor{active=%d, queue=%d, completed=%d, poolSize=%d}",
                getActiveCount(), getQueueSize(), getCompletedTaskCount(), executor.getPoolSize());
    }

    /**
     * 是否已关闭
     */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * 是否已终止
     */
    public boolean isTerminated() {
        return executor.isTerminated();
    }
}
