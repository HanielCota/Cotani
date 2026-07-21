package com.cotani.task.impl.executor;

import com.cotani.task.api.SchedulerOptions;
import com.cotani.task.api.TaskMetadata;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Async executor backed by either virtual threads or a fixed platform thread pool.
 *
 * <p>When virtual threads are enabled, concurrency is not artificially capped: virtual threads are
 * cheap and the JDK schedules them on the available carrier threads. A fixed platform pool is used
 * otherwise, with {@code maxConcurrent} threads.
 */
public final class VirtualThreadExecutor implements AutoCloseable {

    private static final String THREAD_NAME = "cotani-task-";
    private static final String DELAYED_THREAD_NAME = THREAD_NAME + "delayed";
    private static final int DEFAULT_MAX_CONCURRENT = 256;

    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService delayedExecutor;
    private final boolean nameThreads;
    private final Duration shutdownTimeout;

    public VirtualThreadExecutor() {
        this(DEFAULT_MAX_CONCURRENT, true);
    }

    public VirtualThreadExecutor(int maxConcurrent) {
        this(maxConcurrent, true);
    }

    public VirtualThreadExecutor(int maxConcurrent, boolean useVirtualThreads) {
        this(maxConcurrent, useVirtualThreads, Duration.ofSeconds(5));
    }

    public VirtualThreadExecutor(int maxConcurrent, boolean useVirtualThreads, Duration shutdownTimeout) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("maxConcurrent must be positive");
        }

        this.nameThreads = true;
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        this.taskExecutor = createTaskExecutor(maxConcurrent, useVirtualThreads);
        this.delayedExecutor = createDelayedExecutor();
    }

    public static VirtualThreadExecutor create(int maxConcurrent, boolean useVirtualThreads) {
        return new VirtualThreadExecutor(maxConcurrent, useVirtualThreads);
    }

    public static VirtualThreadExecutor create(int maxConcurrent, boolean useVirtualThreads, SchedulerOptions options) {
        return new VirtualThreadExecutor(maxConcurrent, useVirtualThreads, options.defaultShutdownTimeout());
    }

    private static ExecutorService createTaskExecutor(int maxConcurrent, boolean useVirtualThreads) {
        if (useVirtualThreads) {
            ThreadFactory factory = Thread.ofVirtual().name(THREAD_NAME, 0).factory();
            return Executors.newThreadPerTaskExecutor(factory);
        }

        ThreadFactory factory = Thread.ofPlatform().name(THREAD_NAME).factory();
        return Executors.newFixedThreadPool(maxConcurrent, factory);
    }

    private static ScheduledExecutorService createDelayedExecutor() {
        ThreadFactory factory = Thread.ofPlatform().name(DELAYED_THREAD_NAME).factory();
        return Executors.newSingleThreadScheduledExecutor(factory);
    }

    public Future<Void> submit(TaskMetadata metadata, Runnable runnable) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");

        return taskExecutor.submit(new NamedTask(metadata, runnable, nameThreads), null);
    }

    @SuppressWarnings("unchecked")
    public Future<Void> schedule(TaskMetadata metadata, Runnable runnable, long delayMillis) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");

        return (Future<Void>) delayedExecutor.schedule(
                new NamedTask(metadata, runnable, nameThreads), delayMillis, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    public Future<Void> scheduleAtFixedRate(
            TaskMetadata metadata, Runnable runnable, long initialDelayMillis, long periodMillis) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");

        return (Future<Void>) delayedExecutor.scheduleAtFixedRate(
                new NamedTask(metadata, runnable, nameThreads),
                initialDelayMillis,
                periodMillis,
                TimeUnit.MILLISECONDS);
    }

    public boolean isShutdown() {
        return taskExecutor.isShutdown() || delayedExecutor.isShutdown();
    }

    @Override
    public void close() {
        shutdown(taskExecutor);
        shutdown(delayedExecutor);
    }

    private void shutdown(ExecutorService executor) {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record NamedTask(TaskMetadata metadata, Runnable delegate, boolean nameThread) implements Runnable {

        @Override
        public void run() {
            if (!nameThread) {
                delegate.run();
                return;
            }

            Thread currentThread = Thread.currentThread();
            String originalName = currentThread.getName();

            try {
                currentThread.setName(THREAD_NAME + metadata.name());
                delegate.run();
            } finally {
                currentThread.setName(originalName);
            }
        }
    }
}
