package com.cotani.task.impl.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.impl.dispatch.TaskErrorReporter;
import com.cotani.task.impl.dispatch.TaskRunner;
import com.cotani.task.metrics.TaskMetrics;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class TargetTaskDispatcherTest {

    @Test
    void preservesNameTargetAndMetricsWrapping() {
        PlatformScheduler platform = Mockito.mock(PlatformScheduler.class);
        TaskExceptionHandler exceptionHandler = Mockito.mock(TaskExceptionHandler.class);
        TaskMetrics metrics = Mockito.mock(TaskMetrics.class);
        SchedulerTask task = Mockito.mock(SchedulerTask.class);
        var metadata = ArgumentCaptor.forClass(TaskMetadata.class);
        var runnable = ArgumentCaptor.forClass(Runnable.class);
        when(platform.runAsync(metadata.capture(), runnable.capture())).thenReturn(task);
        var dispatcher = new TargetTaskDispatcher(
                platform,
                new TaskRunner(exceptionHandler, metrics),
                new TaskErrorReporter(exceptionHandler),
                TaskMetadata::named);

        dispatcher.async("cache-refresh", () -> {});
        runnable.getValue().run();

        assertEquals("cache-refresh", metadata.getValue().name());
        assertEquals(ExecutionTarget.async(), metadata.getValue().target());
        verify(metrics).record(any(TaskMetadata.class), org.mockito.ArgumentMatchers.eq(true), any());
    }

    @Test
    void supplyFailureRecordsOneFailureWithoutASecondSuccessMetric() {
        PlatformScheduler platform = Mockito.mock(PlatformScheduler.class);
        TaskExceptionHandler exceptionHandler = Mockito.mock(TaskExceptionHandler.class);
        TaskMetrics metrics = Mockito.mock(TaskMetrics.class);
        var runnable = ArgumentCaptor.forClass(Runnable.class);
        when(platform.runAsync(any(TaskMetadata.class), runnable.capture()))
                .thenReturn(Mockito.mock(SchedulerTask.class));
        var dispatcher = new TargetTaskDispatcher(
                platform,
                new TaskRunner(exceptionHandler, metrics),
                new TaskErrorReporter(exceptionHandler),
                TaskMetadata::named);
        var failure = new IllegalStateException("supply failed");

        var result = dispatcher.supply(ExecutionTarget.async(), "load", () -> {
            throw failure;
        });
        runnable.getValue().run();

        org.junit.jupiter.api.Assertions.assertTrue(result.isCompletedExceptionally());
        verify(metrics, times(1)).record(any(TaskMetadata.class), eq(false), any(Duration.class));
        verify(exceptionHandler, times(1)).handle(any(), eq(failure));
    }
}
