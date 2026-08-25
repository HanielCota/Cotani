package com.cotani.task.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cotani.task.api.*;
import com.cotani.task.internal.scheduler.ModernPaperTaskScheduler;
import com.cotani.task.metrics.TaskMetrics;
import com.cotani.task.persistence.PersistentTask;
import com.cotani.task.persistence.PersistentTaskStore;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModernPaperTaskSchedulerTest {
    private final PlatformScheduler platformScheduler = mock(PlatformScheduler.class);
    private final TaskExceptionHandler exceptionHandler = mock(TaskExceptionHandler.class);
    private final TaskMetrics metrics = mock(TaskMetrics.class);
    private final SchedulerOptions options = SchedulerOptions.defaults();
    private final ModernPaperTaskScheduler scheduler =
            ModernPaperTaskScheduler.create(platformScheduler, exceptionHandler, options, metrics);

    @Test
    void debounceReturnsTask() {
        AtomicBoolean executed = new AtomicBoolean(false);

        when(platformScheduler.runAsyncLater(any(TaskMetadata.class), any(Runnable.class), any(Duration.class)))
                .thenReturn(mock(SchedulerTask.class));

        SchedulerTask task = scheduler.debounce("save-config", () -> executed.set(true), Duration.ofMillis(100));

        assertNotNull(task);
    }

    @Test
    void debounceCancelsPreviousTask() {
        SchedulerTask first = mock(SchedulerTask.class);
        SchedulerTask second = mock(SchedulerTask.class);

        when(platformScheduler.runAsyncLater(any(TaskMetadata.class), any(Runnable.class), any(Duration.class)))
                .thenReturn(first)
                .thenReturn(second);

        scheduler.debounce("event", () -> {}, Duration.ofMillis(50));
        scheduler.debounce("event", () -> {}, Duration.ofMillis(50));

        verify(first, times(1)).cancel();
    }

    @Test
    void supersededDebounceCannotExecuteAfterReplacement() {
        SchedulerTask first = mock(SchedulerTask.class);
        SchedulerTask second = mock(SchedulerTask.class);
        ArgumentCaptor<Runnable> runnables = ArgumentCaptor.forClass(Runnable.class);
        AtomicInteger executions = new AtomicInteger();

        when(platformScheduler.runAsyncLater(any(TaskMetadata.class), runnables.capture(), any(Duration.class)))
                .thenReturn(first)
                .thenReturn(second);

        scheduler.debounce("event", executions::incrementAndGet, Duration.ZERO);
        scheduler.debounce("event", executions::incrementAndGet, Duration.ZERO);
        runnables.getAllValues().getFirst().run();
        runnables.getAllValues().getLast().run();

        assertEquals(1, executions.get());
    }

    @Test
    void zeroDelayDebounceCanRunBeforeSchedulerReturns() {
        AtomicInteger executions = new AtomicInteger();
        SchedulerTask completed = mock(SchedulerTask.class);
        when(platformScheduler.runAsyncLater(any(TaskMetadata.class), any(Runnable.class), any(Duration.class)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(1).run();
                    return completed;
                });

        scheduler.debounce("immediate", executions::incrementAndGet, Duration.ZERO);
        scheduler.debounce("immediate", executions::incrementAndGet, Duration.ZERO);

        assertEquals(2, executions.get());
        verify(completed, times(2)).cancel();
    }

    @Test
    void cancellingPersistedTaskRemovesItsRecoveryRecord() {
        var store = mock(PersistentTaskStore.class);
        var persistentScheduler =
                ModernPaperTaskScheduler.create(platformScheduler, exceptionHandler, options, metrics, store);
        var setupTask = mock(SchedulerTask.class);
        var delayedTask = mock(SchedulerTask.class);
        when(platformScheduler.runAsync(any(TaskMetadata.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(1).run();
                    return setupTask;
                });
        when(platformScheduler.runAsyncLater(any(TaskMetadata.class), any(Runnable.class), any(Duration.class)))
                .thenReturn(delayedTask);

        var handle = persistentScheduler.persistAndRun("backup", Duration.ofMinutes(1), new byte[] {1, 2, 3}, _ -> {});
        handle.cancel();

        var task = ArgumentCaptor.forClass(PersistentTask.class);
        verify(store).save(task.capture());
        verify(store).markCompleted(task.getValue());
        verify(delayedTask).cancel();
    }

    @Test
    void cancellationBeforePersistenceIsRemovedAfterSave() {
        var store = mock(PersistentTaskStore.class);
        var persistentScheduler =
                ModernPaperTaskScheduler.create(platformScheduler, exceptionHandler, options, metrics, store);
        var setupTask = mock(SchedulerTask.class);
        var saveRunnable = ArgumentCaptor.forClass(Runnable.class);
        when(platformScheduler.runAsync(any(TaskMetadata.class), saveRunnable.capture()))
                .thenReturn(setupTask);

        var handle = persistentScheduler.persistAndRun("backup", Duration.ofMinutes(1), new byte[] {1, 2, 3}, _ -> {});
        handle.cancel();
        saveRunnable.getValue().run();

        var task = ArgumentCaptor.forClass(PersistentTask.class);
        verify(store).save(task.capture());
        verify(store).markCompleted(task.getValue());
        verify(platformScheduler, never())
                .runAsyncLater(any(TaskMetadata.class), any(Runnable.class), any(Duration.class));
    }

    @Test
    void closeAsyncCoalescesAndRejectsNewTasks() {
        var first = scheduler.closeAsync();
        var second = scheduler.closeAsync();

        assertSame(first, second);
        assertTrue(first.toCompletableFuture().isDone());
        assertThrows(RejectedExecutionException.class, () -> scheduler.async(() -> {}));
        verify(platformScheduler).cancelOwnedTasks();
    }
}
