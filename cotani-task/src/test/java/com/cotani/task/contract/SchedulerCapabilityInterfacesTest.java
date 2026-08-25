package com.cotani.task.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.AsyncCloseable;
import com.cotani.task.api.AsyncTaskExecutor;
import com.cotani.task.api.DelayedTaskScheduler;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.SchedulerDiagnostics;
import com.cotani.task.api.SchedulerOptions;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskChainFactory;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.internal.scheduler.ModernPaperTaskScheduler;
import com.cotani.task.metrics.TaskMetrics;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SchedulerCapabilityInterfacesTest {
    @Test
    void realSchedulerIsSubstitutableForNarrowCapabilities() {
        PlatformScheduler platform = Mockito.mock(PlatformScheduler.class);
        TaskExceptionHandler exceptionHandler = Mockito.mock(TaskExceptionHandler.class);
        TaskMetrics metrics = Mockito.mock(TaskMetrics.class);
        SchedulerTask task = Mockito.mock(SchedulerTask.class);
        var asyncMetadata = ArgumentCaptor.forClass(TaskMetadata.class);
        when(platform.runAsync(asyncMetadata.capture(), any(Runnable.class))).thenReturn(task);
        when(platform.runAsyncLater(any(TaskMetadata.class), any(Runnable.class), any(Duration.class)))
                .thenReturn(task);
        PaperTaskScheduler scheduler =
                ModernPaperTaskScheduler.create(platform, exceptionHandler, SchedulerOptions.defaults(), metrics);

        AsyncTaskExecutor executor = scheduler;
        DelayedTaskScheduler delays = scheduler;
        TaskChainFactory chains = scheduler;
        SchedulerDiagnostics diagnostics = scheduler;
        AsyncCloseable closeable = scheduler;

        assertSame(task, executor.async("contract-task", () -> {}));
        assertSame(task, delays.asyncLater(() -> {}, Duration.ZERO));
        assertSame(metrics, diagnostics.metrics());
        assertSame(exceptionHandler, diagnostics.exceptionHandler());
        assertEquals("contract-task", asyncMetadata.getValue().name());
        assertEquals(ExecutionTarget.async(), asyncMetadata.getValue().target());
        assertEquals(
                "value",
                chains.chain(CompletableFuture.completedFuture("value"))
                        .toCompletionStage()
                        .toCompletableFuture()
                        .getNow(null));
        closeable.closeAsync();
        verify(platform).cancelOwnedTasks();
    }
}
