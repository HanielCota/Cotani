package com.cotani.task.internal.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.task.api.SchedulerTask;
import com.cotani.task.persistence.PersistentTask;
import com.cotani.task.persistence.PersistentTaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PersistentTaskCoordinatorTest {
    @Test
    void explicitCancellationRemovesPersistedRecoveryRecord() {
        PersistentTaskStore store = Mockito.mock(PersistentTaskStore.class);
        NamedAsyncTaskScheduler scheduler = Mockito.mock(NamedAsyncTaskScheduler.class);
        SchedulerTask setupTask = Mockito.mock(SchedulerTask.class);
        SchedulerTask delayedTask = Mockito.mock(SchedulerTask.class);
        when(scheduler.execute(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return setupTask;
        });
        when(scheduler.schedule(anyString(), any(Runnable.class), any(Duration.class)))
                .thenReturn(delayedTask);
        var coordinator = new PersistentTaskCoordinator(store, scheduler);

        var handle = coordinator.persistAndRun("backup", Duration.ofSeconds(1), new byte[] {1}, _ -> {});
        handle.cancel();

        var task = ArgumentCaptor.forClass(PersistentTask.class);
        verify(store).save(task.capture());
        verify(store).markCompleted(task.getValue());
        verify(delayedTask).cancel();
    }

    @Test
    void persistentMetadataUsesInjectedClock() {
        PersistentTaskStore store = Mockito.mock(PersistentTaskStore.class);
        NamedAsyncTaskScheduler scheduler = Mockito.mock(NamedAsyncTaskScheduler.class);
        SchedulerTask setupTask = Mockito.mock(SchedulerTask.class);
        when(scheduler.execute(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return setupTask;
        });
        when(scheduler.schedule(anyString(), any(Runnable.class), any(Duration.class)))
                .thenReturn(Mockito.mock(SchedulerTask.class));
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        var coordinator = new PersistentTaskCoordinator(store, scheduler, Clock.fixed(now, ZoneOffset.UTC));

        coordinator.persistAndRun("backup", Duration.ofSeconds(1), new byte[] {1}, _ -> {});

        var task = ArgumentCaptor.forClass(PersistentTask.class);
        verify(store).save(task.capture());
        Assertions.assertEquals(now, task.getValue().scheduledAt());
    }
}
