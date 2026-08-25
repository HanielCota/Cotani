package com.cotani.cleanup.internal;

import com.cotani.api.InternalApi;
import com.cotani.cleanup.api.CleanupExecutor;
import com.cotani.cleanup.api.CleanupMode;
import com.cotani.cleanup.api.CleanupPolicy;
import com.cotani.cleanup.api.CleanupRemovalResult;
import com.cotani.cleanup.api.CleanupReport;
import com.cotani.cleanup.api.CleanupRequest;
import com.cotani.cleanup.api.CleanupScan;
import com.cotani.cleanup.api.CleanupService;
import com.cotani.cleanup.api.CleanupServiceOptions;
import com.cotani.cleanup.api.event.CleanupCompletedEvent;
import com.cotani.cleanup.api.event.CleanupStartedEvent;
import com.cotani.event.api.EventBus;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Serializes cleanup operations to prevent overlapping scans and bounded-world backpressure. */
@InternalApi
public final class DefaultCleanupService implements CleanupService {
    private static final Logger LOGGER = Logger.getLogger(DefaultCleanupService.class.getName());

    private final CleanupExecutor executor;
    private final EventBus eventBus;
    private final CleanupServiceOptions options;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private CompletionStage<Void> tail = completedVoid();
    private int pendingOperations;
    private @Nullable CompletableFuture<Void> closeStage;

    private DefaultCleanupService(
            CleanupExecutor executor, EventBus eventBus, CleanupServiceOptions options, Clock clock) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DefaultCleanupService create(
            CleanupExecutor executor, EventBus eventBus, CleanupServiceOptions options, Clock clock) {
        return new DefaultCleanupService(executor, eventBus, options, clock);
    }

    @Override
    public CleanupRequest newRequest(CleanupPolicy policy, String reason) {
        return CleanupRequest.create(
                Objects.requireNonNull(policy, "policy"), Objects.requireNonNull(reason, "reason"), clock);
    }

    @Override
    public CompletionStage<CleanupReport> previewAsync(CleanupRequest request) {
        return submit(Objects.requireNonNull(request, "request"), CleanupMode.PREVIEW);
    }

    @Override
    public CompletionStage<CleanupReport> executeAsync(CleanupRequest request) {
        return submit(Objects.requireNonNull(request, "request"), CleanupMode.EXECUTE);
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            closeStage =
                    tail.toCompletableFuture().thenApply(ignored -> (Void) null).toCompletableFuture();
            return closeStage;
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close cleanup service", unwrap(failure));
            }
        });
    }

    private CompletionStage<CleanupReport> submit(CleanupRequest request, CleanupMode mode) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(new IllegalStateException("Cleanup service is closed"));
            }
            if (pendingOperations >= options.maxPendingOperations()) {
                return failed(new RejectedExecutionException("Cleanup operation queue is full"));
            }

            pendingOperations++;
            var predecessor = tail;
            CompletionStage<CleanupReport> durable;
            try {
                durable = predecessor
                        .handle((ignored, failure) -> null)
                        .thenCompose(ignored -> runOperation(request, mode));
            } catch (RuntimeException failure) {
                pendingOperations--;
                return failed(failure);
            }
            tail = durable.handle((ignored, failure) -> (Void) null);
            durable.whenComplete((ignored, failure) -> {
                synchronized (lifecycleLock) {
                    pendingOperations--;
                }
            });
            CompletionStage<CleanupReport> visible;
            try {
                visible = options.withOperationTimeout(durable);
            } catch (RuntimeException failure) {
                return failed(failure);
            }
            var result = new CompletableFuture<CleanupReport>();
            visible.whenComplete((report, failure) -> {
                if (failure == null) {
                    result.complete(report);
                } else {
                    result.completeExceptionally(unwrap(failure));
                }
            });
            return result;
        }
    }

    private CompletionStage<CleanupReport> runOperation(CleanupRequest request, CleanupMode mode) {
        var startedAt = clock.instant();
        publishBestEffort(new CleanupStartedEvent(request, mode), "cleanup started");

        CompletionStage<CleanupScan> scan;
        try {
            scan = Objects.requireNonNull(executor.scanAsync(request.policy()), "executor returned null scan stage");
        } catch (RuntimeException failure) {
            return failed(failure);
        }

        if (mode == CleanupMode.PREVIEW) {
            return scan.thenApply(result -> completePreview(request, startedAt, result));
        }

        return scan.thenCompose(result -> removeAsync(request.policy(), result))
                .thenApply(result -> completeExecution(request, startedAt, result.scan(), result.removal()));
    }

    private CompletionStage<ExecutionResult> removeAsync(CleanupPolicy policy, CleanupScan scan) {
        try {
            var removal = Objects.requireNonNull(
                    executor.removeAsync(policy, scan.candidates()), "executor returned null removal stage");
            return removal.thenApply(result -> new ExecutionResult(scan, result));
        } catch (RuntimeException failure) {
            return failed(failure);
        }
    }

    private CleanupReport completePreview(CleanupRequest request, java.time.Instant startedAt, CleanupScan scan) {
        var report = CleanupReport.preview(request, startedAt, clock.instant(), scan);
        publishBestEffort(new CleanupCompletedEvent(report), "cleanup completion");
        return report;
    }

    private CleanupReport completeExecution(
            CleanupRequest request, java.time.Instant startedAt, CleanupScan scan, CleanupRemovalResult removal) {
        var report = CleanupReport.executed(request, startedAt, clock.instant(), scan, removal);
        publishBestEffort(new CleanupCompletedEvent(report), "cleanup completion");
        return report;
    }

    private <T extends com.cotani.event.api.CotaniEvent> void publishBestEffort(T event, String eventName) {
        try {
            var stage = Objects.requireNonNull(eventBus.publishAsync(event), "event bus returned null stage");
            options.withEventTimeout(stage).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    LOGGER.log(Level.WARNING, "Failed to publish " + eventName + " event", unwrap(failure));
                }
            });
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to publish " + eventName + " event", failure);
        }
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record ExecutionResult(CleanupScan scan, CleanupRemovalResult removal) {}
}
