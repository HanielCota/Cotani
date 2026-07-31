package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.cache.policy.CacheSettings;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CacheAutosaveCoordinatorTest {
    @Test
    void coalescesConcurrentTicksAndCancelsOwnedTask() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        SchedulerTask scheduledTask = Mockito.mock(SchedulerTask.class);
        var runnable = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.asyncTimer(runnable.capture(), any(Duration.class), any(Duration.class)))
                .thenReturn(scheduledTask);
        var pending = new CompletableFuture<Void>();
        var executions = new AtomicInteger();
        var coordinator = new CacheAutosaveCoordinator(scheduler, CacheSettings.playerData(), () -> {
            executions.incrementAndGet();
            return pending;
        });

        runnable.getValue().run();
        runnable.getValue().run();
        assertEquals(1, executions.get());

        pending.complete(null);
        runnable.getValue().run();
        assertEquals(2, executions.get());

        coordinator.cancelAndAwait();
        verify(scheduledTask).cancel();
    }

    @Test
    void cancellationWaitsForInFlightAutosaveAndRejectsLaterTicks() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        SchedulerTask scheduledTask = Mockito.mock(SchedulerTask.class);
        var runnable = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.asyncTimer(runnable.capture(), any(Duration.class), any(Duration.class)))
                .thenReturn(scheduledTask);
        var pending = new CompletableFuture<Void>();
        var executions = new AtomicInteger();
        var coordinator = new CacheAutosaveCoordinator(scheduler, CacheSettings.playerData(), () -> {
            executions.incrementAndGet();
            return pending;
        });

        runnable.getValue().run();
        var idle = coordinator.cancelAndAwait();
        runnable.getValue().run();

        assertEquals(1, executions.get());
        assertFalse(idle.toCompletableFuture().isDone());
        pending.complete(null);
        assertTrue(idle.toCompletableFuture().isDone());
        verify(scheduledTask).cancel();
    }
}
