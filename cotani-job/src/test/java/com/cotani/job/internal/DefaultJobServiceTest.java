package com.cotani.job.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.job.api.JobExecutionContext;
import com.cotani.job.api.JobFailure;
import com.cotani.job.api.JobRequest;
import com.cotani.job.api.JobRetryPolicy;
import com.cotani.job.api.JobServiceOptions;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskChain;
import com.cotani.task.persistence.PersistentTask;
import com.cotani.task.persistence.PersistentTaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

@SuppressWarnings("unchecked")
class DefaultJobServiceTest {
    @Test
    void schedulesPersistsAndExecutesAJob() {
        var fixture = new Fixture();
        var executions = new ArrayList<Integer>();
        var service = fixture.service();
        service.registerHandler("backup", context -> {
            executions.add(context.attempt());
            return CompletableFuture.completedFuture(null);
        });

        var handle = service.scheduleAsync(JobRequest.once("backup", new byte[] {1}, Duration.ZERO))
                .toCompletableFuture()
                .join();

        assertEquals(1, fixture.store.saved.size());
        fixture.runNextTimer();

        assertEquals(List.of(1), executions);
        assertTrue(
                fixture.store.completed.contains(fixture.store.saved.getFirst().id()));
        assertEquals(
                handle.id(),
                JobEnvelope.decode(fixture.store.saved.getFirst().payload()).jobId());
    }

    @Test
    void retriesFailedHandlersWithTheSameLogicalJobId() {
        var fixture = new Fixture();
        var attempts = new ArrayList<Integer>();
        var failures = new CopyOnWriteArrayList<JobFailure>();
        var service = fixture.service(new JobServiceOptions(Duration.ofSeconds(5), failures::add));
        service.registerHandler("sync", context -> {
            attempts.add(context.attempt());
            return CompletableFuture.failedFuture(new IllegalStateException("transient"));
        });

        var request = JobRequest.once("sync", new byte[0], Duration.ZERO)
                .withRetryPolicy(new JobRetryPolicy(2, Duration.ZERO, Duration.ZERO, 1.0d));
        var handle = service.scheduleAsync(request).toCompletableFuture().join();
        fixture.runNextTimer();
        fixture.runNextTimer();

        assertEquals(List.of(1, 2), attempts);
        assertEquals(
                List.of(true, false),
                failures.stream().map(JobFailure::willRetry).toList());
        assertEquals(handle.id(), failures.getFirst().jobId());
    }

    @Test
    void recoversPendingJobsAfterServiceRestart() {
        var fixture = new Fixture();
        var executions = new ArrayList<Integer>();
        var first = fixture.service();
        first.registerHandler("restart", context -> {
            executions.add(context.attempt());
            return CompletableFuture.completedFuture(null);
        });
        first.scheduleAsync(JobRequest.once("restart", new byte[0], Duration.ofMinutes(1)))
                .toCompletableFuture()
                .join();
        first.closeAsync().toCompletableFuture().join();

        var recovered = fixture.service();
        recovered.registerHandler("restart", context -> {
            executions.add(context.attempt());
            return CompletableFuture.completedFuture(null);
        });
        var handles = recovered.recoverPendingAsync().toCompletableFuture().join();
        fixture.runNextTimer();

        assertEquals(1, handles.size());
        assertEquals(List.of(1), executions);
    }

    @Test
    void durableCancellationRemovesThePendingRecord() {
        var fixture = new Fixture();
        var service = fixture.service();
        service.registerHandler("cancel", context -> CompletableFuture.completedFuture(null));
        var handle = service.scheduleAsync(JobRequest.once("cancel", new byte[0], Duration.ofHours(1)))
                .toCompletableFuture()
                .join();

        assertTrue(service.cancelAsync(handle.id()).toCompletableFuture().join());
        assertEquals(1, fixture.store.completed.size());
        assertTrue(handle.cancelled());
    }

    @Test
    void cancellationDuringExecutionWaitsBeforeRemovingTheRecord() {
        var fixture = new Fixture();
        var handlerCompletion = new CompletableFuture<Void>();
        var service = fixture.service();
        service.registerHandler("running", context -> handlerCompletion);
        var handle = service.scheduleAsync(JobRequest.once("running", new byte[0], Duration.ZERO))
                .toCompletableFuture()
                .join();

        fixture.runNextTimer();
        var cancellation = service.cancelAsync(handle.id()).toCompletableFuture();

        assertFalse(cancellation.isDone());
        assertTrue(fixture.store.completed.isEmpty());

        handlerCompletion.complete(null);

        assertTrue(cancellation.join());
        assertEquals(1, fixture.store.completed.size());
    }

    @Test
    void recurringOccurrencesHaveDistinctExecutionIds() {
        var fixture = new Fixture();
        var executions = new ArrayList<JobExecutionContext>();
        var service = fixture.service();
        service.registerHandler("recurring", context -> {
            executions.add(context);
            return CompletableFuture.completedFuture(null);
        });

        service.scheduleAsync(JobRequest.recurring("recurring", new byte[0], Duration.ZERO, Duration.ofMillis(1)))
                .toCompletableFuture()
                .join();
        fixture.runNextTimer();
        fixture.runNextTimer();

        assertEquals(2, executions.size());
        assertEquals(executions.getFirst().jobId(), executions.get(1).jobId());
        assertNotEquals(executions.getFirst().executionId(), executions.get(1).executionId());
        assertEquals(
                List.of(1, 1),
                executions.stream().map(JobExecutionContext::attempt).toList());
    }

    @Test
    void recoveryDispatchesOnlyTheConfiguredBatch() {
        var fixture = new Fixture();
        var first = fixture.service(new JobServiceOptions(Duration.ofSeconds(5), _ -> {}, 1));
        first.registerHandler("batch", context -> CompletableFuture.completedFuture(null));
        first.scheduleAsync(JobRequest.once("batch", new byte[0], Duration.ofHours(1)))
                .toCompletableFuture()
                .join();
        first.scheduleAsync(JobRequest.once("batch", new byte[0], Duration.ofHours(2)))
                .toCompletableFuture()
                .join();
        first.closeAsync().toCompletableFuture().join();

        var recovered = fixture.service(new JobServiceOptions(Duration.ofSeconds(5), _ -> {}, 1));
        recovered.registerHandler("batch", context -> CompletableFuture.completedFuture(null));

        var handles = recovered.recoverPendingAsync().toCompletableFuture().join();

        assertEquals(1, handles.size());
    }

    @Test
    void timeoutDoesNotStartARetryBeforeTheOriginalHandlerCompletes() {
        var fixture = new Fixture();
        var attempts = new ArrayList<Integer>();
        var handlerCompletion = new CompletableFuture<Void>();
        var service = fixture.service(new JobServiceOptions(Duration.ofMillis(1), _ -> {}));
        service.registerHandler("timeout", context -> {
            attempts.add(context.attempt());
            return handlerCompletion;
        });
        var request = JobRequest.once("timeout", new byte[0], Duration.ZERO)
                .withRetryPolicy(new JobRetryPolicy(2, Duration.ZERO, Duration.ZERO, 1.0d));
        service.scheduleAsync(request).toCompletableFuture().join();

        fixture.runNextTimer();
        await(Duration.ofMillis(50));

        assertEquals(List.of(1), attempts);
        assertTrue(fixture.timers.isEmpty());

        handlerCompletion.completeExceptionally(new IllegalStateException("after timeout"));
        fixture.runNextTimer();

        assertEquals(List.of(1, 2), attempts);
    }

    private static void await(Duration duration) {
        var completed = new CompletableFuture<Void>();
        CompletableFuture.delayedExecutor(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> completed.complete(null));
        completed.join();
    }

    private static final class Fixture {
        private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        private final InMemoryStore store = new InMemoryStore();
        private final List<TestTimer> timers = new CopyOnWriteArrayList<>();

        private Fixture() {
            when(scheduler.supplyAsync(any(Supplier.class))).thenAnswer((Answer<Object>) invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                TaskChain<Object> chain = mock(TaskChain.class);
                when(chain.toCompletionStage()).thenReturn(CompletableFuture.completedFuture(supplier.get()));
                return chain;
            });
            when(scheduler.asyncLater(anyString(), any(Runnable.class), any(Duration.class)))
                    .thenAnswer(invocation -> {
                        var timer = new TestTimer(invocation.getArgument(1));
                        timers.add(timer);
                        return timer;
                    });
        }

        private DefaultJobService service() {
            return service(JobServiceOptions.defaults());
        }

        private DefaultJobService service(JobServiceOptions options) {
            return DefaultJobService.create(
                    scheduler, store, options, Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC));
        }

        private void runNextTimer() {
            while (!timers.isEmpty()) {
                var timer = timers.removeFirst();
                if (!timer.cancelled()) {
                    timer.run();
                    return;
                }
            }
        }
    }

    private static final class TestTimer implements SchedulerTask {
        private final Runnable runnable;
        private boolean cancelled;

        private TestTimer(Runnable runnable) {
            this.runnable = runnable;
        }

        private void run() {
            runnable.run();
        }

        @Override
        public boolean cancel() {
            var changed = !cancelled;
            cancelled = true;
            return changed;
        }

        @Override
        public boolean cancelled() {
            return cancelled;
        }
    }

    private static final class InMemoryStore implements PersistentTaskStore {
        private final List<PersistentTask> saved = new CopyOnWriteArrayList<>();
        private final List<UUID> completed = new CopyOnWriteArrayList<>();

        @Override
        public synchronized void save(PersistentTask task) {
            saved.add(task);
        }

        @Override
        public synchronized List<PersistentTask> loadPending() {
            return saved.stream().filter(task -> !completed.contains(task.id())).toList();
        }

        @Override
        public synchronized void markCompleted(PersistentTask task) {
            completed.add(task.id());
        }
    }
}
