package com.cotani.task.scheduler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.SchedulerOptions;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.impl.scheduler.ModernPaperTaskScheduler;
import com.cotani.task.metrics.TaskMetrics;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ModernPaperTaskSchedulerTest {

    private final PlatformScheduler platformScheduler = mock(PlatformScheduler.class);
    private final TaskExceptionHandler exceptionHandler = mock(TaskExceptionHandler.class);
    private final TaskMetrics metrics = mock(TaskMetrics.class);
    private final SchedulerOptions options = SchedulerOptions.defaults();
    private final ModernPaperTaskScheduler scheduler =
            new ModernPaperTaskScheduler(platformScheduler, exceptionHandler, options, metrics);

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
}
