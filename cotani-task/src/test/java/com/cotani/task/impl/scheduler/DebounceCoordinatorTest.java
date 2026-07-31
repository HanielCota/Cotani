package com.cotani.task.impl.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DebounceCoordinatorTest {
    @Test
    void supersededGenerationCannotExecuteOrRemoveReplacement() {
        NamedAsyncTaskScheduler scheduler = Mockito.mock(NamedAsyncTaskScheduler.class);
        SchedulerTask first = Mockito.mock(SchedulerTask.class);
        SchedulerTask second = Mockito.mock(SchedulerTask.class);
        var runnables = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.schedule(anyString(), runnables.capture(), any(Duration.class)))
                .thenReturn(first, second);
        var coordinator = new DebounceCoordinator(scheduler);
        var executions = new AtomicInteger();

        coordinator.debounce("reload", executions::incrementAndGet, Duration.ofMillis(10));
        coordinator.debounce("reload", executions::incrementAndGet, Duration.ofMillis(10));
        runnables.getAllValues().getFirst().run();
        runnables.getAllValues().getLast().run();

        assertEquals(1, executions.get());
        verify(first).cancel();
    }

    @Test
    void executionBeforeDelegateAttachmentIsHandled() {
        NamedAsyncTaskScheduler scheduler = Mockito.mock(NamedAsyncTaskScheduler.class);
        SchedulerTask completed = Mockito.mock(SchedulerTask.class);
        when(scheduler.schedule(anyString(), any(Runnable.class), any(Duration.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return completed;
                });
        var coordinator = new DebounceCoordinator(scheduler);
        var executions = new AtomicInteger();

        coordinator.debounce("reload", executions::incrementAndGet, Duration.ZERO);
        coordinator.debounce("reload", executions::incrementAndGet, Duration.ZERO);

        assertEquals(2, executions.get());
        verify(completed, times(2)).cancel();
    }
}
