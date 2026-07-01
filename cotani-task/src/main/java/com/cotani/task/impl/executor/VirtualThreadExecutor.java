package com.cotani.task.impl.executor;

import com.cotani.task.api.TaskMetadata;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class VirtualThreadExecutor implements AutoCloseable {

    private static final String THREAD_NAME = "cotani-task-";
    private static final String DELAYED_THREAD_NAME = THREAD_NAME + "delayed";
    private static final int DEFAULT_MAX_CONCURRENT = 256;

    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService delayedExecutor;
    private final Semaphore semaphore;
    private final int maxConcurrent;

    public static VirtualThreadExecutor create(int maxConcurrent, boolean useVirtualThreads) {
        return new VirtualThreadExecutor(maxConcurrent, useVirtualThreads);
    }

    public VirtualThreadExecutor() {
        this(DEFAULT_MAX_CONCURRENT, true);
    }

    public VirtualThreadExecutor(int maxConcurrent) {
        this(maxConcurrent, true);
    }

    private VirtualThreadExecutor(int maxConcurrent, boolean useVirtualThreads) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("maxConcurrent must be positive");
        }

        this.maxConcurrent = maxConcurrent;
        this.semaphore = new Semaphore(maxConcurrent);
        this.taskExecutor = createTaskExecutor(useVirtualThreads);
        this.delayedExecutor = createDelayedExecutor();
    }

    public Future<Void> submit(TaskMetadata metadata, Runnable runnable) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");

        return taskExecutor.submit(new NamedThrottledTask(metadata, runnable, semaphore), null);
    }

    @SuppressWarnings("unchecked")
    public Future<Void> schedule(TaskMetadata metadata, Runnable runnable, long delayMillis) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");

        return (Future<Void>) delayedExecutor.schedule(
                new NamedThrottledTask(metadata, runnable, semaphore), delayMillis, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    public Future<Void> scheduleAtFixedRate(
            TaskMetadata metadata, Runnable runnable, long initialDelayMillis, long periodMillis) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");

        return (Future<Void>) delayedExecutor.scheduleAtFixedRate(
                new NamedThrottledTask(metadata, runnable, semaphore),
                initialDelayMillis,
                periodMillis,
                TimeUnit.MILLISECONDS);
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public boolean isShutdown() {
        return taskExecutor.isShutdown() || delayedExecutor.isShutdown();
    }

    @Override
    public void close() {
        shutdown(taskExecutor);
        shutdown(delayedExecutor);
    }

    private static ExecutorService createTaskExecutor(boolean useVirtualThreads) {
        if (useVirtualThreads) {
            ThreadFactory factory = Thread.ofVirtual().name(THREAD_NAME, 0).factory();

            return Executors.newThreadPerTaskExecutor(factory);
        }

        ThreadFactory factory = Thread.ofPlatform().name(THREAD_NAME).factory();

        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), factory);
    }

    private static ScheduledExecutorService createDelayedExecutor() {
        ThreadFactory factory = Thread.ofPlatform().name(DELAYED_THREAD_NAME).factory();

        return Executors.newSingleThreadScheduledExecutor(factory);
    }

    private void shutdown(ExecutorService executor) {
        executor.shutdownNow();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Executor did not terminate within timeout");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException("Interrupted while waiting for executor shutdown", interrupted);
        }
    }

    private static final class NamedThrottledTask implements Runnable {

        private final TaskMetadata metadata;
        private final Runnable delegate;
        private final Semaphore semaphore;

        NamedThrottledTask(TaskMetadata metadata, Runnable delegate, Semaphore semaphore) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.semaphore = Objects.requireNonNull(semaphore, "semaphore");
        }

        @Override
        public void run() {
            try {
                semaphore.acquire();

                try {
                    runNamed();
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();

                throw new RuntimeException("Interrupted while waiting for execution permit", interrupted);
            }
        }

        private void runNamed() {
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
