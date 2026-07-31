package com.cotani.task.impl.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.RetryPolicy;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TaskRetryExecutorTest {
    @Test
    void recreatesEveryRetryAttempt() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        when(scheduler.asyncLater(anyString(), any(Runnable.class), any(Duration.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return SchedulerTask.noop();
                });
        var createdAttempts = new AtomicInteger();
        var executor = new TaskRetryExecutor<String>(
                RetryPolicy.fixed(3, Duration.ZERO),
                scheduler,
                () -> createdAttempts.incrementAndGet() == 1
                        ? CompletableFuture.failedFuture(new IllegalStateException("second failure"))
                        : CompletableFuture.completedFuture("ok"));

        var result = executor.execute(CompletableFuture.failedFuture(new IllegalStateException("first failure")));

        assertEquals("ok", result.getNow(null));
        assertEquals(2, createdAttempts.get());
    }

    @Test
    void cancellationCancelsPendingDelay() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        SchedulerTask pending = Mockito.mock(SchedulerTask.class);
        when(scheduler.asyncLater(anyString(), any(Runnable.class), any(Duration.class)))
                .thenReturn(pending);
        var executor = new TaskRetryExecutor<String>(
                RetryPolicy.fixed(3, Duration.ofSeconds(1)),
                scheduler,
                () -> CompletableFuture.completedFuture("unused"));

        var result = executor.execute(CompletableFuture.failedFuture(new IllegalStateException("first failure")));
        result.cancel(true);

        verify(pending).cancel();
    }

    @Test
    void nullSchedulerHandleFailsInsteadOfLeavingRetryPending() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        when(scheduler.asyncLater(anyString(), any(Runnable.class), any(Duration.class)))
                .thenReturn(null);
        var executor = new TaskRetryExecutor<String>(
                RetryPolicy.fixed(2, Duration.ZERO), scheduler, () -> CompletableFuture.completedFuture("unused"));

        var result = executor.execute(CompletableFuture.failedFuture(new IllegalStateException("first failure")));

        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    void fatalFactoryFailureCompletesRetryResult() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        when(scheduler.asyncLater(anyString(), any(Runnable.class), any(Duration.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return SchedulerTask.noop();
                });
        var executor = new TaskRetryExecutor<String>(RetryPolicy.fixed(2, Duration.ZERO), scheduler, () -> {
            throw new AssertionError("factory failed");
        });

        var result = executor.execute(CompletableFuture.failedFuture(new IllegalStateException("first failure")));

        assertTrue(result.isCompletedExceptionally());
    }
}
