package com.cotani.task.internal.scheduler;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.TaskMetadata;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

final class SchedulerLifecycleCoordinator {
    private final PlatformScheduler platformScheduler;
    private final boolean cancelOwnedTasks;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Void>> closeFuture = new AtomicReference<>();

    SchedulerLifecycleCoordinator(PlatformScheduler platformScheduler, boolean cancelOwnedTasks) {
        this.platformScheduler = Objects.requireNonNull(platformScheduler, "platformScheduler");
        this.cancelOwnedTasks = cancelOwnedTasks;
    }

    TaskMetadata metadata(String name, ExecutionTarget target) {
        if (closed.get()) {
            throw new RejectedExecutionException("PaperTaskScheduler is closed.");
        }

        return TaskMetadata.named(name, target);
    }

    CompletionStage<Void> closeAsync(Runnable cancelInternalTasks) {
        var existing = closeFuture.get();

        if (existing != null) {
            return existing;
        }

        var promise = new CompletableFuture<Void>();

        if (!closeFuture.compareAndSet(null, promise)) {
            return Objects.requireNonNull(closeFuture.get(), "closeFuture");
        }

        @Nullable Throwable cancellationFailure = beginClose(cancelInternalTasks);
        final CompletionStage<Void> platformClose;
        try {
            platformClose = Objects.requireNonNull(startPlatformCloseAsync(), "platform close returned null");
        } catch (Exception startupFailure) {
            promise.completeExceptionally(
                    Objects.requireNonNull(mergeFailures(cancellationFailure, startupFailure), "close failure"));

            return promise;
        }

        var _ = platformClose.whenComplete((_, failure) -> {
            @Nullable Throwable closeFailure = mergeFailures(cancellationFailure, failure);

            if (closeFailure == null) {
                promise.complete(null);
                return;
            }
            promise.completeExceptionally(closeFailure);
        });

        return promise;
    }

    void close(Runnable cancelInternalTasks) {
        if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "PaperTaskScheduler.close() blocks; use closeAsync() on the server thread.");
        }

        var promise = new CompletableFuture<Void>();

        if (!closeFuture.compareAndSet(null, promise)) {
            return;
        }
        @Nullable Throwable failure = beginClose(cancelInternalTasks);

        if (platformScheduler instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception closeFailure) {
                failure = mergeFailures(failure, closeFailure);
            }
        }
        if (failure == null) {
            promise.complete(null);
            return;
        }
        promise.completeExceptionally(failure);
        rethrowCloseFailure(failure);
    }

    private @Nullable Throwable beginClose(Runnable cancelInternalTasks) {
        if (!closed.compareAndSet(false, true)) {
            return null;
        }

        @Nullable Throwable failure = null;

        try {
            Objects.requireNonNull(cancelInternalTasks, "cancelInternalTasks").run();
        } catch (Exception cancellationFailure) {
            failure = cancellationFailure;
        }
        if (cancelOwnedTasks) {
            try {
                platformScheduler.cancelOwnedTasks();
            } catch (Exception cancellationFailure) {
                failure = mergeFailures(failure, cancellationFailure);
            }
        }
        return failure;
    }

    private CompletionStage<Void> startPlatformCloseAsync() {
        if (platformScheduler instanceof PaperPlatformScheduler paperScheduler) {
            return paperScheduler.closeAsync();
        }
        if (platformScheduler instanceof AutoCloseable closeable) {
            return closeOnDedicatedThread(closeable);
        }

        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("ReferenceEquality") // Throwable forbids suppressing the same instance.
    private static @Nullable Throwable mergeFailures(@Nullable Throwable first, @Nullable Throwable next) {
        if (first == null) {
            return next;
        }
        if (next != null && next != first) {
            first.addSuppressed(next);
        }

        return first;
    }

    private static void rethrowCloseFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }

        throw new IllegalStateException("Could not close scheduler resources", failure);
    }

    private static CompletionStage<Void> closeOnDedicatedThread(AutoCloseable closeable) {
        var promise = new CompletableFuture<Void>();
        Thread.ofPlatform().daemon(true).name("cotani-task-platform-shutdown").start(() -> {
            try {
                closeable.close();
                promise.complete(null);
            } catch (Exception failure) {
                promise.completeExceptionally(failure);
            }
        });

        return promise;
    }
}
