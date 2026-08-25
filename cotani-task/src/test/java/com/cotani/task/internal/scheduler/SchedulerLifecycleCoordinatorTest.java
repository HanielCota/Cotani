package com.cotani.task.internal.scheduler;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PlatformScheduler;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SchedulerLifecycleCoordinatorTest {
    @Test
    void concurrentCloseCallsCoalesceAndRejectLaterMetadata() {
        PlatformScheduler platform = Mockito.mock(PlatformScheduler.class);
        Runnable cancelInternal = Mockito.mock(Runnable.class);
        var lifecycle = new SchedulerLifecycleCoordinator(platform, true);

        var first = lifecycle.closeAsync(cancelInternal);
        var second = lifecycle.closeAsync(cancelInternal);

        assertSame(first, second);
        assertTrue(first.toCompletableFuture().isDone());
        assertThrows(RejectedExecutionException.class, () -> lifecycle.metadata("late-task", ExecutionTarget.async()));
        verify(cancelInternal, times(1)).run();
        verify(platform, times(1)).cancelOwnedTasks();
    }

    @Test
    void synchronousCancellationFailureCompletesCloseAndStillCancelsOwnedTasks() {
        PlatformScheduler platform = mock(PlatformScheduler.class);
        var lifecycle = new SchedulerLifecycleCoordinator(platform, true);
        Runnable failingCancellation = () -> {
            throw new IllegalStateException("internal cancellation failed");
        };

        var first = lifecycle.closeAsync(failingCancellation);
        var second = lifecycle.closeAsync(failingCancellation);

        assertSame(first, second);
        assertTrue(first.toCompletableFuture().isCompletedExceptionally());
        verify(platform).cancelOwnedTasks();
    }
}
