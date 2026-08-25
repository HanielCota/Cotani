package com.cotani.task.impl.executor;

import com.cotani.api.InternalApi;
import com.cotani.task.api.SchedulerOptions;
import com.cotani.task.api.TaskMetadata;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@InternalApi
public final class VirtualThreadExecutor implements AutoCloseable {
    private static final String THREAD_NAME = "cotani-task-";
    private static final String DELAYED_THREAD_NAME = THREAD_NAME + "delayed";
    private static final String METADATA_PARAM = "metadata";
    private static final String RUNNABLE_PARAM = "runnable";
    private static final int DEFAULT_MAX_CONCURRENT = 256;
    private static final int BOUNDED_QUEUE_CAPACITY = 4096;
    private static final int DELAYED_POOL_SIZE = 4;

    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService delayedExecutor;
    private final boolean nameThreads;
    private final Duration shutdownTimeout;
    private final AtomicReference<CompletableFuture<Void>> closeFuture = new AtomicReference<>();

    private VirtualThreadExecutor() {
        this(DEFAULT_MAX_CONCURRENT, true);
    }

    private VirtualThreadExecutor(int maxConcurrent) {
        this(maxConcurrent, true);
    }

    private VirtualThreadExecutor(int maxConcurrent, boolean useVirtualThreads) {
        this(maxConcurrent, useVirtualThreads, Duration.ofSeconds(5));
    }

    private VirtualThreadExecutor(int maxConcurrent, boolean useVirtualThreads, Duration shutdownTimeout) {
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

    public static VirtualThreadExecutor create() {
        return new VirtualThreadExecutor();
    }

    public static VirtualThreadExecutor create(int maxConcurrent) {
        return new VirtualThreadExecutor(maxConcurrent);
    }

    public static VirtualThreadExecutor create(int maxConcurrent, boolean useVirtualThreads, Duration shutdownTimeout) {
        return new VirtualThreadExecutor(maxConcurrent, useVirtualThreads, shutdownTimeout);
    }

    private static ExecutorService createTaskExecutor(int maxConcurrent, boolean useVirtualThreads) {
        ThreadFactory factory = useVirtualThreads
                ? Thread.ofVirtual().name(THREAD_NAME, 0).factory()
                : Thread.ofPlatform().name(THREAD_NAME, 0).factory();

        return new ThreadPoolExecutor(
                maxConcurrent,
                maxConcurrent,
                0L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(BOUNDED_QUEUE_CAPACITY),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static ScheduledExecutorService createDelayedExecutor() {
        ThreadFactory factory = Thread.ofPlatform().name(DELAYED_THREAD_NAME).factory();
        return new ScheduledThreadPoolExecutor(DELAYED_POOL_SIZE, factory);
    }

    public Future<Void> submit(TaskMetadata metadata, Runnable runnable) {
        Objects.requireNonNull(metadata, METADATA_PARAM);
        Objects.requireNonNull(runnable, RUNNABLE_PARAM);

        return taskExecutor.submit(new NamedTask(metadata, runnable, nameThreads), null);
    }

    @SuppressWarnings("unchecked")
    public Future<Void> schedule(TaskMetadata metadata, Runnable runnable, long delayMillis) {
        Objects.requireNonNull(metadata, METADATA_PARAM);
        Objects.requireNonNull(runnable, RUNNABLE_PARAM);

        return (Future<Void>) delayedExecutor.schedule(
                new NamedTask(metadata, runnable, nameThreads), delayMillis, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    public Future<Void> scheduleAtFixedRate(
            TaskMetadata metadata, Runnable runnable, long initialDelayMillis, long periodMillis) {
        Objects.requireNonNull(metadata, METADATA_PARAM);
        Objects.requireNonNull(runnable, RUNNABLE_PARAM);

        return (Future<Void>) delayedExecutor.scheduleAtFixedRate(
                new NamedTask(metadata, runnable, nameThreads),
                initialDelayMillis,
                periodMillis,
                TimeUnit.MILLISECONDS);
    }

    public boolean isShutdown() {
        return taskExecutor.isShutdown() || delayedExecutor.isShutdown();
    }

    public CompletionStage<Void> closeAsync() {
        var existing = closeFuture.get();

        if (existing != null) {
            return existing;
        }

        var promise = new CompletableFuture<Void>();

        if (!closeFuture.compareAndSet(null, promise)) {
            return Objects.requireNonNull(closeFuture.get(), "closeFuture");
        }

        taskExecutor.shutdown();
        delayedExecutor.shutdown();

        Thread.ofPlatform().daemon(true).name(THREAD_NAME + "shutdown").start(() -> {
            try {
                shutdownExecutors();
                promise.complete(null);
            } catch (Throwable failure) {
                promise.completeExceptionally(failure);
            }
        });

        return promise;
    }

    @Override
    public void close() {
        closeAsync();
    }

    private void shutdownExecutors() {
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
